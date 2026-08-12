package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.ProfileDao
import com.cstv.app.data.local.entity.ProfileEntity
import com.cstv.app.data.remote.CstvErrorMapper
import com.cstv.app.data.remote.api.CstvApiService
import com.cstv.app.data.remote.dto.CstvProfileCreateDto
import com.cstv.app.data.remote.dto.CstvProfileUpdateDto
import com.cstv.app.domain.model.CstvAccount
import com.cstv.app.domain.model.CstvError
import com.cstv.app.domain.model.CstvProfile
import javax.inject.Inject
import javax.inject.Singleton

/** Idempotent identity reconciliation. It never matches profiles merely by name or avatar. */
@Singleton
class ProfileCloudReconciler @Inject constructor(
    private val profileDao: ProfileDao,
    private val api: CstvApiService,
    private val errors: CstvErrorMapper,
    private val emptinessProbe: CloudProfileEmptinessProbe
) {
    suspend fun reconcile(account: CstvAccount): Result<Unit> = runCatching {
        val cloudProfiles = account.profiles.sortedBy { it.createdAtMillis }
        val cloudById = cloudProfiles.associateBy { it.id }
        val local = profileDao.getAll()
        local.filter { it.remoteId != null }.forEach { localProfile ->
            cloudById[localProfile.remoteId]?.let { remote -> profileDao.update(localProfile.copy(name = remote.name, avatarId = remote.avatarId, createdAt = remote.createdAtMillis)) }
        }
        val associated = profileDao.getAll()
        val knownRemoteIds = associated.mapNotNull { it.remoteId }.toSet()
        val loneCloudProfile = cloudProfiles.singleOrNull()
        val firstLocalWithoutRemote = associated.firstOrNull { it.remoteId == null }
        if (loneCloudProfile?.name == "Profil 1" && firstLocalWithoutRemote != null &&
            emptinessProbe.isEmpty(loneCloudProfile.id) == true
        ) {
            val update = api.updateProfile(
                loneCloudProfile.id,
                CstvProfileUpdateDto(firstLocalWithoutRemote.name, firstLocalWithoutRemote.avatarId)
            )
            if (!update.isSuccessful) throw CstvException(errors.from(update))
            profileDao.setRemoteId(firstLocalWithoutRemote.id, loneCloudProfile.id)
        }
        val afterAutomaticMatch = profileDao.getAll()
        val remoteIdsAfterMatch = afterAutomaticMatch.mapNotNull { it.remoteId }.toSet()
        cloudProfiles.filterNot { it.id in remoteIdsAfterMatch }.forEach { remote ->
            profileDao.insert(ProfileEntity(name = remote.name, avatarId = remote.avatarId, createdAt = remote.createdAtMillis, remoteId = remote.id))
        }
        profileDao.getAllWithoutRemoteId().forEach { profile ->
            val response = api.createProfile(CstvProfileCreateDto(profile.name, profile.avatarId))
            val remote = response.body()
            if (!response.isSuccessful || remote == null) throw CstvException(errors.from(response))
            profileDao.setRemoteId(profile.id, remote.id)
        }
    }
}
