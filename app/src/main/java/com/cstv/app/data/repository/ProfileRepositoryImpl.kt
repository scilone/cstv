package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.FavoritesDao
import com.cstv.app.data.local.dao.LiveTvDao
import com.cstv.app.data.local.dao.ProfileDao
import com.cstv.app.data.local.dao.CategoryPreferenceDao
import com.cstv.app.data.local.dao.TrackPreferenceDao
import com.cstv.app.data.local.dao.VodDao
import com.cstv.app.data.local.dao.MediaRatingDao
import com.cstv.app.data.local.dao.SeriesWatchStateDao
import com.cstv.app.data.local.dao.ProfileSyncStateDao
import com.cstv.app.data.local.db.AppDatabase
import com.cstv.app.data.local.entity.ProfileEntity
import com.cstv.app.data.local.storage.ProfileManager
import com.cstv.app.domain.model.Profile
import com.cstv.app.domain.model.StartupProfileResolution
import com.cstv.app.domain.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import androidx.room.withTransaction

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val profileDao: ProfileDao,
    private val profileManager: ProfileManager,
    private val favoritesDao: FavoritesDao,
    private val vodDao: VodDao,
    private val liveTvDao: LiveTvDao,
    private val trackPreferenceDao: TrackPreferenceDao,
    private val categoryPreferenceDao: CategoryPreferenceDao,
    private val mediaRatingDao: MediaRatingDao,
    private val seriesWatchStateDao: SeriesWatchStateDao,
    private val profileSyncStateDao: ProfileSyncStateDao? = null,
    private val cstvProfileGateway: CstvProfileGateway? = null,
    private val database: AppDatabase? = null
) : ProfileRepository {

    override fun observeProfiles(): Flow<List<Profile>> =
        profileDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getProfiles(): List<Profile> =
        profileDao.getAll().map { it.toDomain() }

    override suspend fun ensureInitialized(): List<Profile> {
        var profiles = profileDao.getAll()
        if (profiles.isEmpty()) {
            val id = profileDao.insert(
                ProfileEntity(name = "Profil 1", avatarId = 0, createdAt = System.currentTimeMillis())
            ).toInt()
            profileManager.setActiveProfileId(id)
            profiles = profileDao.getAll()
        } else if (profiles.none { it.id == profileManager.currentProfileId() }) {
            // Aucun profil actif valide (première init ou profil supprimé) :
            // retomber sur le premier profil existant.
            profileManager.setActiveProfileId(profiles.first().id)
        }
        return profiles.map { it.toDomain() }
    }

    override suspend fun createProfile(name: String, avatarId: Int): Profile {
        val remote = cstvProfileGateway?.create(name, avatarId)
        val id = profileDao.insert(
            ProfileEntity(name = remote?.name ?: name, avatarId = remote?.avatarId ?: avatarId, createdAt = remote?.createdAtMillis ?: System.currentTimeMillis(), remoteId = remote?.id)
        ).toInt()
        return profileDao.getById(id)!!.toDomain()
    }

    override suspend fun renameProfile(id: Int, name: String) {
        val existing = profileDao.getById(id) ?: return
        val remote = cstvProfileGateway?.let { gateway -> existing.remoteId?.let { gateway.update(it, name = name) } }
        profileDao.update(existing.copy(name = remote?.name ?: name, avatarId = remote?.avatarId ?: existing.avatarId))
    }

    override suspend fun updateAvatar(id: Int, avatarId: Int) {
        val existing = profileDao.getById(id) ?: return
        val remote = cstvProfileGateway?.let { gateway -> existing.remoteId?.let { gateway.update(it, avatarId = avatarId) } }
        profileDao.update(existing.copy(name = remote?.name ?: existing.name, avatarId = remote?.avatarId ?: avatarId))
    }

    override suspend fun updateMaxAgeRating(id: Int, maxAgeRating: Int?) {
        val existing = profileDao.getById(id) ?: return
        val remote = cstvProfileGateway?.let { gateway ->
            existing.remoteId?.let { gateway.update(it, maxAgeRatingProvided = true, maxAgeRating = maxAgeRating) }
        }
        profileDao.update(existing.copy(maxAgeRating = remote?.maxAgeRating ?: maxAgeRating))
    }

    override suspend fun deleteProfile(id: Int): Boolean {
        if (profileDao.count() <= 1) return false

        cstvProfileGateway?.let { gateway ->
            profileDao.getById(id)?.remoteId?.let { gateway.delete(it) }
        }

        suspend fun deleteLocalProfile() {
            favoritesDao.deleteAllForProfile(id)
            vodDao.deleteAllPlaybackForProfile(id)
            liveTvDao.deleteRecentlyWatchedForProfile(id)
            trackPreferenceDao.deleteAllForProfile(id)
            categoryPreferenceDao.deleteAllForProfile(id)
            mediaRatingDao.deleteAllForProfile(id)
            seriesWatchStateDao.deleteAllForProfile(id)
            profileSyncStateDao?.deleteForProfile(id)
            profileDao.deleteById(id)
        }
        database?.withTransaction { deleteLocalProfile() } ?: deleteLocalProfile()

        // Si on supprimait le profil actif, basculer sur un autre.
        if (profileManager.currentProfileId() == id) {
            profileDao.getAll().firstOrNull()?.let { profileManager.setActiveProfileId(it.id) }
        }

        // Le profil de démarrage automatique ne peut pas survivre à sa propre suppression.
        if (profileManager.currentAutoStartProfileId() == id) {
            profileManager.setAutoStartProfileId(ProfileManager.NO_PROFILE)
        }
        return true
    }

    override fun currentProfileId(): Int = profileManager.currentProfileId()

    override fun setActiveProfile(id: Int) = profileManager.setActiveProfileId(id)

    override val activeProfileId: Flow<Int> = profileManager.activeProfileId

    override val autoStartProfileId: Flow<Int> = profileManager.autoStartProfileId

    override fun currentAutoStartProfileId(): Int = profileManager.currentAutoStartProfileId()

    override suspend fun setAutoStartProfile(id: Int?) {
        profileManager.setAutoStartProfileId(id ?: ProfileManager.NO_PROFILE)
    }

    override suspend fun resolveStartupProfile(): StartupProfileResolution {
        return try {
            val profiles = ensureInitialized()
            val autoStartId = profileManager.currentAutoStartProfileId()
            when {
                autoStartId == ProfileManager.NO_PROFILE ->
                    StartupProfileResolution(profiles, needsSelection = profiles.size > 1)

                profiles.any { it.id == autoStartId } -> {
                    profileManager.setActiveProfileId(autoStartId)
                    StartupProfileResolution(profiles, needsSelection = false)
                }

                else -> {
                    profileManager.setAutoStartProfileId(ProfileManager.NO_PROFILE)
                    StartupProfileResolution(profiles, needsSelection = profiles.size > 1)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            StartupProfileResolution(emptyList(), needsSelection = true)
        }
    }

    override suspend fun purgeAllProfiles() {
        suspend fun purge() {
            profileDao.getAll().forEach { profile ->
                favoritesDao.deleteAllForProfile(profile.id)
                vodDao.deleteAllPlaybackForProfile(profile.id)
                liveTvDao.deleteRecentlyWatchedForProfile(profile.id)
                trackPreferenceDao.deleteAllForProfile(profile.id)
                categoryPreferenceDao.deleteAllForProfile(profile.id)
                mediaRatingDao.deleteAllForProfile(profile.id)
                seriesWatchStateDao.deleteAllForProfile(profile.id)
                profileSyncStateDao?.deleteForProfile(profile.id)
            }
            profileDao.deleteAll()
            profileManager.setActiveProfileId(ProfileManager.NO_PROFILE)
            profileManager.setAutoStartProfileId(ProfileManager.NO_PROFILE)
        }
        database?.withTransaction { purge() } ?: purge()
    }

    private fun ProfileEntity.toDomain() = Profile(id, name, avatarId, createdAt, remoteId, maxAgeRating)
}
