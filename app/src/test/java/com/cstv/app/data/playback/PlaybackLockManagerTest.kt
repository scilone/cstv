package com.cstv.app.data.playback

import com.cstv.app.data.local.storage.DeviceIdentityProvider
import com.cstv.app.domain.model.PlaybackLockResult
import com.cstv.app.domain.model.PlaybackLockState
import com.cstv.app.domain.repository.PlaybackLockRepository
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlaybackLockManagerTest {
    @Test fun `keeps one lock through reacquisition then cancels heartbeat on release`() = runTest {
        val repository = object : PlaybackLockRepository {
            var acquisitions = 0
            var heartbeats = 0
            var releases = 0
            override suspend fun acquire(deviceId: String, deviceName: String?, takeover: Boolean): PlaybackLockResult {
                acquisitions++
                return PlaybackLockResult.Acquired("token", heartbeatSeconds = 30, ttlSeconds = 90)
            }
            override suspend fun heartbeat(token: String): PlaybackLockResult { heartbeats++; return PlaybackLockResult.Acquired(token, 30, 90) }
            override suspend fun release(token: String) { releases++ }
        }
        val identity = object : DeviceIdentityProvider {
            override fun deviceId() = "11111111-1111-4111-8111-111111111111"
            override fun deviceName() = "Test TV"
        }
        val manager = PlaybackLockManager(repository, identity, this)

        assertTrue(manager.acquire() is PlaybackLockResult.Acquired)
        assertTrue(manager.acquire() is PlaybackLockResult.Acquired)
        assertEquals(1, repository.acquisitions)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(1, repository.heartbeats)

        manager.release()
        runCurrent()
        assertEquals(1, repository.releases)
        assertEquals(PlaybackLockState.Idle, manager.state.value)
    }

    @Test fun `preserves server ttl and fails open on an acquisition timeout`() = runTest {
        val repository = object : PlaybackLockRepository {
            override suspend fun acquire(deviceId: String, deviceName: String?, takeover: Boolean): PlaybackLockResult = kotlinx.coroutines.awaitCancellation()
            override suspend fun heartbeat(token: String): PlaybackLockResult = PlaybackLockResult.Unavailable
            override suspend fun release(token: String) = Unit
        }
        val identity = object : DeviceIdentityProvider { override fun deviceId() = "11111111-1111-4111-8111-111111111111"; override fun deviceName() = null }
        val manager = PlaybackLockManager(repository, identity, this)
        assertSame(PlaybackLockResult.Unavailable, manager.acquire())
        assertEquals(PlaybackLockState.FailOpen, manager.state.value)
        manager.reset()
    }

    @Test fun `moves to revoked when heartbeat loses ownership`() = runTest {
        val holder = com.cstv.app.domain.model.PlaybackLockHolder("Other TV", 12)
        val repository = object : PlaybackLockRepository {
            override suspend fun acquire(deviceId: String, deviceName: String?, takeover: Boolean) = PlaybackLockResult.Acquired("token", 1, 73)
            override suspend fun heartbeat(token: String) = PlaybackLockResult.Revoked(holder)
            override suspend fun release(token: String) = Unit
        }
        val identity = object : DeviceIdentityProvider { override fun deviceId() = "11111111-1111-4111-8111-111111111111"; override fun deviceName() = "Test TV" }
        val manager = PlaybackLockManager(repository, identity, this)
        val acquired = manager.acquire() as PlaybackLockResult.Acquired
        assertEquals(73L, acquired.ttlSeconds)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(PlaybackLockState.Revoked(holder), manager.state.value)
        manager.reset()
    }
}
