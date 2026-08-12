package com.cstv.app.domain.usecase

import com.cstv.app.domain.repository.ProfileRepository
import com.cstv.app.domain.sync.CloudSyncManager
import com.cstv.app.domain.sync.CloudSyncStatus
import com.cstv.app.domain.sync.SyncNamespace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MarkPlaybackSyncUseCaseTest {
    @get:Rule val globalTimeout: Timeout = Timeout.seconds(60)

    @Mock private lateinit var profiles: ProfileRepository
    @Mock private lateinit var cloudSync: CloudSyncManager

    private lateinit var useCase: MarkPlaybackSyncUseCase

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        doReturn(73).`when`(profiles).currentProfileId()
        whenever(cloudSync.status).thenReturn(MutableStateFlow<CloudSyncStatus>(CloudSyncStatus.Idle))
        useCase = MarkPlaybackSyncUseCase(profiles, cloudSync)
    }

    @Test
    fun invoke_marksOnlyTheActiveProfilePlaybackNamespaceDirty() = runTest {
        useCase()

        verify(cloudSync).markDirty(73, SyncNamespace.PLAYBACK)
    }
}
