package com.cstv.app.domain.usecase

import com.cstv.app.data.local.storage.AppUpdatePreferences
import com.cstv.app.domain.model.AppUpdateCheckOrigin
import com.cstv.app.domain.model.AppUpdateCheckResult
import com.cstv.app.domain.model.AppUpdateDecision
import com.cstv.app.domain.model.AppUpdateRelease
import com.cstv.app.domain.model.SemanticVersion
import com.cstv.app.domain.repository.AppUpdateRepository
import com.cstv.app.domain.util.TimeProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

private const val INSTALLED_VERSION_CODE = 17_706 // v1.77.6

private class FakeTimeProvider(var now: Long = 0L) : TimeProvider {
    override fun nowMillis(): Long = now
}

class CheckForAppUpdateUseCaseTest {

    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    private val repository: AppUpdateRepository = mock()
    private val preferences: AppUpdatePreferences = mock()
    private val time = FakeTimeProvider(now = 10_000_000L)

    private lateinit var useCase: CheckForAppUpdateUseCase

    @Before
    fun setUp() {
        useCase = CheckForAppUpdateUseCase(repository, preferences, time)
        whenever(preferences.getKnownRelease()).thenReturn(null)
        whenever(preferences.getPostponedVersionCode()).thenReturn(null)
        whenever(preferences.getIgnoredVersionCode()).thenReturn(null)
    }

    private fun release(tag: String, sizeBytes: Long = 1_000L): AppUpdateRelease = AppUpdateRelease(
        version = SemanticVersion.parse(tag)!!,
        assetDownloadUrl = "https://github.com/scilone/cstv/releases/download/$tag/app-release.apk",
        assetName = "app-release.apk",
        assetSizeBytes = sizeBytes
    )

    @Test
    fun `CA1 newer optional version is proposed`() = runBlocking {
        whenever(preferences.getLastAutomaticCheckAt()).thenReturn(null) // due
        whenever(repository.fetchLatestRelease()).thenReturn(AppUpdateCheckResult.ReleaseFound(release("v1.78.0")))

        val decision = useCase(AppUpdateCheckOrigin.AUTOMATIC, INSTALLED_VERSION_CODE)

        assertTrue(decision is AppUpdateDecision.OptionalUpdate)
        assertEquals(17_800, (decision as AppUpdateDecision.OptionalUpdate).release.version.versionCode)
    }

    @Test
    fun `CA2 identical or older version is not proposed`() = runBlocking {
        whenever(preferences.getLastAutomaticCheckAt()).thenReturn(null)
        whenever(repository.fetchLatestRelease()).thenReturn(AppUpdateCheckResult.ReleaseFound(release("v1.77.6")))

        assertEquals(AppUpdateDecision.UpToDate, useCase(AppUpdateCheckOrigin.AUTOMATIC, INSTALLED_VERSION_CODE))
    }

    @Test
    fun `CA3 major bump is required`() = runBlocking {
        whenever(preferences.getLastAutomaticCheckAt()).thenReturn(null)
        whenever(repository.fetchLatestRelease()).thenReturn(AppUpdateCheckResult.ReleaseFound(release("v2.0.0")))

        val decision = useCase(AppUpdateCheckOrigin.AUTOMATIC, INSTALLED_VERSION_CODE)

        assertTrue(decision is AppUpdateDecision.RequiredUpdate)
        verify(preferences).saveKnownRelease(any())
    }

    @Test
    fun `CA4 minor bump is not required`() = runBlocking {
        whenever(preferences.getLastAutomaticCheckAt()).thenReturn(null)
        whenever(repository.fetchLatestRelease()).thenReturn(AppUpdateCheckResult.ReleaseFound(release("v1.78.0")))

        val decision = useCase(AppUpdateCheckOrigin.AUTOMATIC, INSTALLED_VERSION_CODE)

        assertTrue(decision is AppUpdateDecision.OptionalUpdate)
    }

    @Test
    fun `CA5 automatic check under 24h is skipped`() = runBlocking {
        whenever(preferences.getLastAutomaticCheckAt()).thenReturn(time.now - 1_000L)

        val decision = useCase(AppUpdateCheckOrigin.AUTOMATIC, INSTALLED_VERSION_CODE)

        assertEquals(AppUpdateDecision.NoCheckNeeded, decision)
        verifyNoInteractions(repository)
    }

    @Test
    fun `CA6 automatic check past 24h is performed`() = runBlocking {
        whenever(preferences.getLastAutomaticCheckAt()).thenReturn(time.now - CheckForAppUpdateUseCase.AUTOMATIC_CHECK_INTERVAL_MS - 1)
        whenever(repository.fetchLatestRelease()).thenReturn(AppUpdateCheckResult.NoUsableRelease)

        val decision = useCase(AppUpdateCheckOrigin.AUTOMATIC, INSTALLED_VERSION_CODE)

        assertEquals(AppUpdateDecision.UpToDate, decision)
        verify(repository).fetchLatestRelease()
        Unit
    }

    @Test
    fun `CA7 postponed version is not proposed before the deadline, and is after`() = runBlocking {
        whenever(preferences.getLastAutomaticCheckAt()).thenReturn(null)
        whenever(repository.fetchLatestRelease()).thenReturn(AppUpdateCheckResult.ReleaseFound(release("v1.78.0")))
        whenever(preferences.getPostponedVersionCode()).thenReturn(17_800)
        whenever(preferences.getPostponedUntilMillis()).thenReturn(time.now + 1_000L)

        assertEquals(AppUpdateDecision.UpToDate, useCase(AppUpdateCheckOrigin.AUTOMATIC, INSTALLED_VERSION_CODE))

        time.now += 2_000L // le report est maintenant expiré
        val decision = useCase(AppUpdateCheckOrigin.AUTOMATIC, INSTALLED_VERSION_CODE)
        assertTrue(decision is AppUpdateDecision.OptionalUpdate)
    }

    @Test
    fun `CA8 ignored version is never proposed, a later one is`() = runBlocking {
        whenever(preferences.getLastAutomaticCheckAt()).thenReturn(null)
        whenever(preferences.getIgnoredVersionCode()).thenReturn(17_800)
        whenever(repository.fetchLatestRelease()).thenReturn(AppUpdateCheckResult.ReleaseFound(release("v1.78.0")))

        assertEquals(AppUpdateDecision.UpToDate, useCase(AppUpdateCheckOrigin.AUTOMATIC, INSTALLED_VERSION_CODE))

        whenever(repository.fetchLatestRelease()).thenReturn(AppUpdateCheckResult.ReleaseFound(release("v1.79.0")))
        val decision = useCase(AppUpdateCheckOrigin.AUTOMATIC, INSTALLED_VERSION_CODE)
        assertTrue(decision is AppUpdateDecision.OptionalUpdate)
    }

    @Test
    fun `CA9 manual check ignores throttle, postpone and ignore`() = runBlocking {
        whenever(preferences.getLastAutomaticCheckAt()).thenReturn(time.now) // vérifié à l'instant
        whenever(preferences.getPostponedVersionCode()).thenReturn(17_800)
        whenever(preferences.getPostponedUntilMillis()).thenReturn(time.now + 1_000_000L)
        whenever(repository.fetchLatestRelease()).thenReturn(AppUpdateCheckResult.ReleaseFound(release("v1.78.0")))

        val decision = useCase(AppUpdateCheckOrigin.MANUAL, INSTALLED_VERSION_CODE)

        assertTrue(decision is AppUpdateDecision.OptionalUpdate)
        verify(repository).fetchLatestRelease()
        Unit
    }

    @Test
    fun `CA10 manual check with no newer version reports up to date`() = runBlocking {
        whenever(repository.fetchLatestRelease()).thenReturn(AppUpdateCheckResult.NoUsableRelease)

        assertEquals(AppUpdateDecision.UpToDate, useCase(AppUpdateCheckOrigin.MANUAL, INSTALLED_VERSION_CODE))
    }

    @Test
    fun `CA11 manual check network failure reports CheckFailed`() = runBlocking {
        whenever(repository.fetchLatestRelease()).thenReturn(AppUpdateCheckResult.Failure("timeout"))

        assertEquals(AppUpdateDecision.CheckFailed, useCase(AppUpdateCheckOrigin.MANUAL, INSTALLED_VERSION_CODE))
    }

    @Test
    fun `CA12 automatic check failure is silent and does not update the timestamp`() = runBlocking {
        whenever(preferences.getLastAutomaticCheckAt()).thenReturn(null)
        whenever(repository.fetchLatestRelease()).thenReturn(AppUpdateCheckResult.Failure("timeout"))

        val decision = useCase(AppUpdateCheckOrigin.AUTOMATIC, INSTALLED_VERSION_CODE)

        assertEquals(AppUpdateDecision.CheckFailed, decision)
        verify(preferences, never()).setLastAutomaticCheckAt(any())
    }

    @Test
    fun `CA13 a known required release reappears at every cold start without network`() = runBlocking {
        whenever(preferences.getLastAutomaticCheckAt()).thenReturn(time.now) // throttle actif, sans effet ici
        whenever(preferences.getKnownRelease()).thenReturn(release("v2.0.0"))

        val decision = useCase(AppUpdateCheckOrigin.AUTOMATIC, INSTALLED_VERSION_CODE)

        assertTrue(decision is AppUpdateDecision.RequiredUpdate)
        verifyNoInteractions(repository)
    }

    @Test
    fun `an installed major update prunes the stale known release`() = runBlocking {
        whenever(preferences.getKnownRelease()).thenReturn(release("v2.0.0"))
        whenever(preferences.getLastAutomaticCheckAt()).thenReturn(null)
        whenever(repository.fetchLatestRelease()).thenReturn(AppUpdateCheckResult.NoUsableRelease)

        useCase(AppUpdateCheckOrigin.AUTOMATIC, installedVersionCode = 20_000) // v2.0.0 déjà installée

        verify(preferences).clearKnownRelease()
    }

    @Test
    fun `postpone and ignore delegate to preferences`() {
        useCase.postpone(17_800)
        verify(preferences).setPostponed(eq(17_800), any())

        useCase.ignore(17_800)
        verify(preferences).setIgnoredVersionCode(17_800)
    }
}
