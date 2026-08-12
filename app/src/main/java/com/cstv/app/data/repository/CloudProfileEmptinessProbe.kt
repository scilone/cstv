package com.cstv.app.data.repository

import com.cstv.app.data.remote.api.CstvObjectsApiService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `null` means that emptiness could not be established safely. Callers must
 * then keep profiles distinct instead of guessing from their display name.
 */
interface CloudProfileEmptinessProbe {
    suspend fun isEmpty(remoteProfileId: String): Boolean?
}

@Singleton
class CstvCloudProfileEmptinessProbe @Inject constructor(
    private val objects: CstvObjectsApiService
) : CloudProfileEmptinessProbe {
    override suspend fun isEmpty(remoteProfileId: String): Boolean? = try {
        val response = objects.listObjects(remoteProfileId)
        response.body()?.takeIf { response.isSuccessful }?.objects?.isEmpty()
    } catch (_: java.io.IOException) {
        null
    }
}
