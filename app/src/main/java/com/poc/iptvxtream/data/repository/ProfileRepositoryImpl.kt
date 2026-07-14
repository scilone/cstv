package com.poc.iptvxtream.data.repository

import com.poc.iptvxtream.data.local.dao.FavoritesDao
import com.poc.iptvxtream.data.local.dao.LiveTvDao
import com.poc.iptvxtream.data.local.dao.ProfileDao
import com.poc.iptvxtream.data.local.dao.VodDao
import com.poc.iptvxtream.data.local.entity.ProfileEntity
import com.poc.iptvxtream.data.local.storage.ProfileManager
import com.poc.iptvxtream.domain.model.Profile
import com.poc.iptvxtream.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val profileDao: ProfileDao,
    private val profileManager: ProfileManager,
    private val favoritesDao: FavoritesDao,
    private val vodDao: VodDao,
    private val liveTvDao: LiveTvDao
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
        val id = profileDao.insert(
            ProfileEntity(name = name, avatarId = avatarId, createdAt = System.currentTimeMillis())
        ).toInt()
        return profileDao.getById(id)!!.toDomain()
    }

    override suspend fun renameProfile(id: Int, name: String) {
        val existing = profileDao.getById(id) ?: return
        profileDao.update(existing.copy(name = name))
    }

    override suspend fun updateAvatar(id: Int, avatarId: Int) {
        val existing = profileDao.getById(id) ?: return
        profileDao.update(existing.copy(avatarId = avatarId))
    }

    override suspend fun deleteProfile(id: Int): Boolean {
        if (profileDao.count() <= 1) return false

        // Nettoyage des données spécifiques au profil.
        favoritesDao.deleteAllForProfile(id)
        vodDao.deleteAllPlaybackForProfile(id)
        liveTvDao.deleteRecentlyWatchedForProfile(id)
        profileDao.deleteById(id)

        // Si on supprimait le profil actif, basculer sur un autre.
        if (profileManager.currentProfileId() == id) {
            profileDao.getAll().firstOrNull()?.let { profileManager.setActiveProfileId(it.id) }
        }
        return true
    }

    override fun currentProfileId(): Int = profileManager.currentProfileId()

    override fun setActiveProfile(id: Int) = profileManager.setActiveProfileId(id)

    override val activeProfileId: Flow<Int> = profileManager.activeProfileId

    private fun ProfileEntity.toDomain() = Profile(id, name, avatarId, createdAt)
}
