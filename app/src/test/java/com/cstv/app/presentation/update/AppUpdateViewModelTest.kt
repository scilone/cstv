package com.cstv.app.presentation.update

import com.cstv.app.domain.model.AppUpdateCheckOrigin
import com.cstv.app.domain.model.AppUpdateDecision
import com.cstv.app.domain.model.AppUpdateRelease
import com.cstv.app.domain.model.SemanticVersion
import com.cstv.app.domain.repository.AppUpdateRepository
import com.cstv.app.domain.update.PackageInstallResult
import com.cstv.app.domain.update.PackageInstallerGateway
import com.cstv.app.domain.usecase.CheckForAppUpdateUseCase
import com.cstv.app.domain.model.AppUpdateCheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

private fun release(tag: String = "v1.78.0") = AppUpdateRelease(
    version = SemanticVersion.parse(tag)!!,
    assetDownloadUrl = "https://github.com/scilone/cstv/releases/download/$tag/app-release.apk",
    assetName = "app-release.apk",
    assetSizeBytes = 1_000L
)

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateViewModelTest {

    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    private val testDispatcher = StandardTestDispatcher()

    private val checkForAppUpdateUseCase: CheckForAppUpdateUseCase = mock()
    private val repository: AppUpdateRepository = mock()
    private val installerGateway: PackageInstallerGateway = mock()
    private val installResults = MutableSharedFlow<PackageInstallResult>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(installerGateway.installResults).thenReturn(installResults)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repo: AppUpdateRepository = repository) = AppUpdateViewModel(checkForAppUpdateUseCase, repo, installerGateway)

    /** Suspend indéfiniment, en respectant l'annulation structurée (contrairement à un mock synchrone). */
    private class HangingDownloadRepository : AppUpdateRepository {
        override suspend fun fetchLatestRelease(): AppUpdateCheckResult = AppUpdateCheckResult.Failure("unused")
        override suspend fun downloadApk(release: AppUpdateRelease, onProgress: suspend (Int) -> Unit): File =
            awaitCancellation()
    }

    @Test
    fun `checkManually exposes an optional update as Available`() = runTest {
        whenever(checkForAppUpdateUseCase.invoke(any(), any()))
            .thenReturn(AppUpdateDecision.OptionalUpdate(release()))

        val vm = viewModel()
        vm.checkManually()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state is AppUpdateUiState.Available)
        assertEquals(false, (state as AppUpdateUiState.Available).required)
    }

    @Test
    fun `checkAutomatically only runs once per cold start`() = runTest {
        whenever(checkForAppUpdateUseCase.invoke(any(), any())).thenReturn(AppUpdateDecision.NoCheckNeeded)

        val vm = viewModel()
        vm.checkAutomatically()
        advanceUntilIdle()
        vm.checkAutomatically()
        advanceUntilIdle()

        org.mockito.kotlin.verify(checkForAppUpdateUseCase, org.mockito.kotlin.times(1)).invoke(any(), any())
    }

    @Test
    fun `install downloads then reaches ReadyToInstall on AwaitingUserConfirmation`() = runTest {
        val target = release()
        whenever(checkForAppUpdateUseCase.invoke(any(), any())).thenReturn(AppUpdateDecision.OptionalUpdate(target))
        whenever(installerGateway.canRequestInstallPackages()).thenReturn(true)
        whenever(repository.downloadApk(any(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val onProgress = invocation.getArgument<suspend (Int) -> Unit>(1)
            runBlocking { onProgress(100) }
            File("fake-apk-path")
        }
        whenever(installerGateway.install(any())).thenReturn(PackageInstallResult.AwaitingUserConfirmation)

        val vm = viewModel()
        vm.checkManually()
        advanceUntilIdle()
        vm.install()
        advanceUntilIdle()

        assertTrue(vm.state.value is AppUpdateUiState.ReadyToInstall)
    }

    @Test
    fun `install requests permission when installs are not allowed`() = runTest {
        val target = release()
        whenever(checkForAppUpdateUseCase.invoke(any(), any())).thenReturn(AppUpdateDecision.OptionalUpdate(target))
        whenever(installerGateway.canRequestInstallPackages()).thenReturn(false)
        whenever(installerGateway.canOpenPermissionSettings()).thenReturn(true)

        val vm = viewModel()
        vm.checkManually()
        advanceUntilIdle()
        vm.install()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state is AppUpdateUiState.PermissionRequired)
    }

    @Test
    fun `a permission settings screen unavailable on this device falls back`() = runTest {
        val target = release()
        whenever(checkForAppUpdateUseCase.invoke(any(), any())).thenReturn(AppUpdateDecision.OptionalUpdate(target))
        whenever(installerGateway.canRequestInstallPackages()).thenReturn(false)
        whenever(installerGateway.canOpenPermissionSettings()).thenReturn(false)

        val vm = viewModel()
        vm.checkManually()
        advanceUntilIdle()
        vm.install()
        advanceUntilIdle()

        assertTrue(vm.state.value is AppUpdateUiState.PermissionUnavailable)
    }

    @Test
    fun `cancelDownload returns to Available and does not install`() = runTest {
        val target = release()
        whenever(checkForAppUpdateUseCase.invoke(any(), any())).thenReturn(AppUpdateDecision.OptionalUpdate(target))
        whenever(installerGateway.canRequestInstallPackages()).thenReturn(true)

        // Utilise un faux repository plutôt qu'un mock : le mock renvoie sa
        // valeur stubée immédiatement (pas de vraie suspension), alors que
        // `cancelDownload()` doit être testé pendant que le téléchargement
        // est réellement en cours (annulation structurée).
        val vm = viewModel(repo = HangingDownloadRepository())
        vm.checkManually()
        advanceUntilIdle()
        vm.install()
        advanceUntilIdle()
        vm.cancelDownload()
        advanceUntilIdle()

        assertTrue(vm.state.value is AppUpdateUiState.Available)
        org.mockito.kotlin.verify(installerGateway, org.mockito.kotlin.never()).install(any())
    }

    @Test
    fun `postponeUpdate delegates to the use case and clears the dialog`() = runTest {
        val target = release()
        whenever(checkForAppUpdateUseCase.invoke(any(), any())).thenReturn(AppUpdateDecision.OptionalUpdate(target))

        val vm = viewModel()
        vm.checkManually()
        advanceUntilIdle()
        vm.postponeUpdate()

        org.mockito.kotlin.verify(checkForAppUpdateUseCase).postpone(target.version.versionCode)
        assertEquals(AppUpdateUiState.Idle, vm.state.value)
    }

    @Test
    fun `F35-R7 dismissing an optional permission request postpones the release`() = runTest {
        val target = release()
        whenever(checkForAppUpdateUseCase.invoke(any(), any())).thenReturn(AppUpdateDecision.OptionalUpdate(target))
        whenever(installerGateway.canRequestInstallPackages()).thenReturn(false)
        whenever(installerGateway.canOpenPermissionSettings()).thenReturn(true)

        val vm = viewModel()
        vm.checkManually()
        advanceUntilIdle()
        vm.install()
        advanceUntilIdle()
        assertTrue(vm.state.value is AppUpdateUiState.PermissionRequired)

        vm.dismiss()

        // Avant correctif : passait directement à Idle sans reporter -- la
        // même version réapparaissait dès le prochain démarrage au lieu
        // d'être silencée 24 h (§4.5).
        org.mockito.kotlin.verify(checkForAppUpdateUseCase).postpone(target.version.versionCode)
        assertEquals(AppUpdateUiState.Idle, vm.state.value)
    }

    @Test
    fun `F35-R7 dismissing a permission-unavailable fallback postpones the release`() = runTest {
        val target = release()
        whenever(checkForAppUpdateUseCase.invoke(any(), any())).thenReturn(AppUpdateDecision.OptionalUpdate(target))
        whenever(installerGateway.canRequestInstallPackages()).thenReturn(false)
        whenever(installerGateway.canOpenPermissionSettings()).thenReturn(false)

        val vm = viewModel()
        vm.checkManually()
        advanceUntilIdle()
        vm.install()
        advanceUntilIdle()
        assertTrue(vm.state.value is AppUpdateUiState.PermissionUnavailable)

        vm.dismiss()

        org.mockito.kotlin.verify(checkForAppUpdateUseCase).postpone(target.version.versionCode)
        assertEquals(AppUpdateUiState.Idle, vm.state.value)
    }

    @Test
    fun `F35-R7 dismissing an optional download error postpones the release`() = runTest {
        val target = release()
        whenever(checkForAppUpdateUseCase.invoke(any(), any())).thenReturn(AppUpdateDecision.OptionalUpdate(target))
        whenever(installerGateway.canRequestInstallPackages()).thenReturn(true)
        whenever(repository.downloadApk(any(), any())).thenAnswer { throw java.io.IOException("boom") }

        val vm = viewModel()
        vm.checkManually()
        advanceUntilIdle()
        vm.install()
        advanceUntilIdle()
        assertTrue(vm.state.value is AppUpdateUiState.Error)

        vm.dismiss()

        org.mockito.kotlin.verify(checkForAppUpdateUseCase).postpone(target.version.versionCode)
        assertEquals(AppUpdateUiState.Idle, vm.state.value)
    }

    @Test
    fun `a mandatory permission request is not cleared by dismiss`() = runTest {
        val target = release("v2.0.0")
        whenever(checkForAppUpdateUseCase.invoke(any(), any())).thenReturn(AppUpdateDecision.RequiredUpdate(target))
        whenever(installerGateway.canRequestInstallPackages()).thenReturn(false)
        whenever(installerGateway.canOpenPermissionSettings()).thenReturn(true)

        val vm = viewModel()
        vm.checkManually()
        advanceUntilIdle()
        vm.install()
        advanceUntilIdle()
        assertTrue(vm.state.value is AppUpdateUiState.PermissionRequired)

        vm.dismiss()

        org.mockito.kotlin.verify(checkForAppUpdateUseCase, org.mockito.kotlin.never()).postpone(any())
        assertTrue(vm.state.value is AppUpdateUiState.PermissionRequired)
    }

    @Test
    fun `an install abort received asynchronously returns to Available`() = runTest {
        val target = release()
        whenever(checkForAppUpdateUseCase.invoke(any(), any())).thenReturn(AppUpdateDecision.OptionalUpdate(target))
        whenever(installerGateway.canRequestInstallPackages()).thenReturn(true)
        whenever(repository.downloadApk(any(), any())).thenReturn(File("fake-apk-path"))
        whenever(installerGateway.install(any())).thenReturn(PackageInstallResult.AwaitingUserConfirmation)

        val vm = viewModel()
        vm.checkManually()
        advanceUntilIdle()
        vm.install()
        advanceUntilIdle()
        assertTrue(vm.state.value is AppUpdateUiState.ReadyToInstall)

        installResults.emit(PackageInstallResult.Aborted)
        advanceUntilIdle()

        assertTrue(vm.state.value is AppUpdateUiState.Available)
    }
}
