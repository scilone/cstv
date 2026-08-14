package com.cstv.app.domain.repository

import com.cstv.app.domain.model.PlaybackLockResult

interface PlaybackLockRepository {
    suspend fun acquire(deviceId: String, deviceName: String?, takeover: Boolean): PlaybackLockResult
    suspend fun heartbeat(token: String): PlaybackLockResult
    suspend fun release(token: String)
}
