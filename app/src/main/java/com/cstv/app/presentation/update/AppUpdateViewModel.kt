package com.cstv.app.presentation.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cstv.app.BuildConfig
import com.cstv.app.di.IptvLog
import com.cstv.app.domain.model.AppUpdateCheckOrigin
import com.cstv.app.domain.model.AppUpdateDecision
import com.cstv.app.domain.model.AppUpdateRelease
import com.cstv.app.domain.repository.AppUpdateRepository
import com.cstv.app.domain.update.PackageInstallResult
import com.cstv.app.domain.update.PackageInstallerGateway
import com.cstv.app.domain.usecase.CheckForAppUpdateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "APPUPDATE_VM"

/**
 * Machine d'états partagée (F35, §4.8/§4.9). Créé une seule fois à la racine
 * de `MainActivity` : le contrôle automatique depuis l'Accueil et le
 * contrôle manuel depuis les Paramètres convergent vers la même instance, le
 * même téléchargement et le même dialogue.
 *
 * Ne connaît ni Retrofit ni `SharedPreferences` — tout passe par
 * [CheckForAppUpdateUseCase] et [AppUpdateRepository].
 */
@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val checkForAppUpdateUseCase: CheckForAppUpdateUseCase,
    private val repository: AppUpdateRepository,
    private val installerGateway: PackageInstallerGateway
) : ViewModel() {

    private val _state = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Idle)
    val state: StateFlow<AppUpdateUiState> = _state.asStateFlow()

    /** Job unique : sérialise vérification et téléchargement (§4.9). */
    private var operationJob: Job? = null

    /** Garde par démarrage à froid : une rotation/recomposition ne relance jamais un second contrôle automatique. */
    private var hasCheckedAutomaticallyThisColdStart = false

    /** Release en attente de l'autorisation « sources inconnues », reprise au retour dans l'app (§4.6). */
    private var pendingPermissionRelease: AppUpdateRelease? = null

    init {
        // Issues arrivant après une confirmation système ouverte par
        // `install()` (Aborted/échec) : Success n'a jamais d'observateur, le
        // processus est remplacé avant que ce flux ne puisse le relayer.
        viewModelScope.launch {
            installerGateway.installResults.collect(::onInstallResult)
        }
    }

    // --- Vérification ---

    /** Appelé au plus une fois par démarrage à froid, une fois les gates CSTV/Xtream/profil résolus (§4.8). */
    fun checkAutomatically() {
        if (hasCheckedAutomaticallyThisColdStart) return
        hasCheckedAutomaticallyThisColdStart = true
        runCheck(AppUpdateCheckOrigin.AUTOMATIC)
    }

    /** RG8 : ignore le throttle, le report et la version ignorée. */
    fun checkManually() {
        runCheck(AppUpdateCheckOrigin.MANUAL)
    }

    private fun runCheck(origin: AppUpdateCheckOrigin) {
        // Un contrôle manuel pendant un contrôle en cours rejoint le résultat
        // courant (état partagé) plutôt que de lancer un second appel (§4.9).
        if (operationJob?.isActive == true) return
        operationJob = viewModelScope.launch {
            _state.value = AppUpdateUiState.Checking(origin)
            when (val decision = checkForAppUpdateUseCase(origin, BuildConfig.VERSION_CODE)) {
                AppUpdateDecision.NoCheckNeeded -> _state.value = AppUpdateUiState.Idle
                AppUpdateDecision.CheckFailed -> _state.value = if (origin == AppUpdateCheckOrigin.MANUAL) {
                    AppUpdateUiState.Error(release = null, required = false, kind = AppUpdateErrorKind.CHECK)
                } else {
                    AppUpdateUiState.Idle
                }
                AppUpdateDecision.UpToDate -> _state.value =
                    if (origin == AppUpdateCheckOrigin.MANUAL) AppUpdateUiState.UpToDate else AppUpdateUiState.Idle
                is AppUpdateDecision.OptionalUpdate -> _state.value = AppUpdateUiState.Available(decision.release, required = false)
                is AppUpdateDecision.RequiredUpdate -> _state.value = AppUpdateUiState.Available(decision.release, required = true)
            }
        }
    }

    // --- Téléchargement / installation ---

    /** Depuis `Available` : vérifie l'autorisation, télécharge puis installe. */
    fun install() {
        val (release, required) = availableRelease() ?: return
        if (operationJob?.isActive == true) return
        operationJob = viewModelScope.launch { proceedToInstall(release, required) }
    }

    private suspend fun proceedToInstall(release: AppUpdateRelease, required: Boolean) {
        if (!installerGateway.canRequestInstallPackages()) {
            pendingPermissionRelease = release
            _state.value = if (installerGateway.canOpenPermissionSettings()) {
                AppUpdateUiState.PermissionRequired(release, required)
            } else {
                AppUpdateUiState.PermissionUnavailable(release, required)
            }
            return
        }
        downloadAndInstall(release, required)
    }

    private suspend fun downloadAndInstall(release: AppUpdateRelease, required: Boolean) {
        _state.value = AppUpdateUiState.Downloading(release, required, percent = 0)
        val apkFile = try {
            repository.downloadApk(release) { percent ->
                _state.value = AppUpdateUiState.Downloading(release, required, percent)
            }
        } catch (e: CancellationException) {
            _state.value = AppUpdateUiState.Available(release, required)
            return
        } catch (e: Exception) {
            IptvLog.e(TAG, "Téléchargement de mise à jour impossible", e)
            _state.value = AppUpdateUiState.Error(release, required, AppUpdateErrorKind.DOWNLOAD)
            return
        }

        _state.value = AppUpdateUiState.ReadyToInstall(release, required)
        when (installerGateway.install(apkFile)) {
            PackageInstallResult.AwaitingUserConfirmation, PackageInstallResult.Success -> Unit // §4.6/§4.9
            PackageInstallResult.Aborted -> _state.value = AppUpdateUiState.Available(release, required)
            is PackageInstallResult.Failed -> _state.value = AppUpdateUiState.Error(release, required, AppUpdateErrorKind.INSTALL)
        }
    }

    /** Issue arrivée après le retour de `install()` (dialogue système annulé ou échec confirmé). */
    private fun onInstallResult(result: PackageInstallResult) {
        val current = _state.value as? AppUpdateUiState.ReadyToInstall ?: return
        when (result) {
            PackageInstallResult.Aborted -> _state.value = AppUpdateUiState.Available(current.release, current.required)
            is PackageInstallResult.Failed -> _state.value = AppUpdateUiState.Error(current.release, current.required, AppUpdateErrorKind.INSTALL)
            else -> Unit
        }
    }

    /** Annule la `Job` de téléchargement en cours ; le fichier partiel est supprimé par le repository (§4.9). */
    fun cancelDownload() {
        operationJob?.cancel()
    }

    /** Reprend automatiquement le téléchargement si l'autorisation vient d'être accordée (retour des réglages, §4.6). */
    fun onAppResumed() {
        val release = pendingPermissionRelease ?: return
        val current = _state.value as? AppUpdateUiState.PermissionRequired ?: return
        if (!installerGateway.canRequestInstallPackages()) return
        pendingPermissionRelease = null
        operationJob = viewModelScope.launch { downloadAndInstall(release, current.required) }
    }

    fun openPermissionSettings() {
        installerGateway.openPermissionSettings()
    }

    // --- Décisions utilisateur ---

    /** RG4 : marque « Plus tard » pour la version affichée. */
    fun postponeUpdate() {
        val (release, _) = availableRelease() ?: return
        postponeReleaseAndClear(release)
    }

    /** RG5 : marque « Ignorer cette version ». */
    fun ignoreUpdate() {
        val (release, _) = availableRelease() ?: return
        checkForAppUpdateUseCase.ignore(release.version.versionCode)
        pendingPermissionRelease = null
        _state.value = AppUpdateUiState.Idle
    }

    /**
     * Fermeture implicite (Retour, clic extérieur) ou bouton « Fermer »/« Annuler »
     * d'une variante **non obligatoire** — traitée comme « Plus tard » (§4.5)
     * dès qu'un dialogue optionnel connaît une Release, quel que soit l'état
     * où la fermeture a lieu (F35-R7 : avant ce correctif, se fermer à
     * l'étape autorisation/erreur laissait la version réapparaître au
     * prochain démarrage au lieu d'être silencée 24 h). `pendingPermissionRelease`
     * est nettoyée sur toutes les sorties terminales.
     * N'a aucun effet sur une variante obligatoire (non annulable).
     */
    fun dismiss() {
        when (val current = _state.value) {
            is AppUpdateUiState.Available -> if (!current.required) postponeReleaseAndClear(current.release)
            is AppUpdateUiState.PermissionRequired -> if (!current.required) postponeReleaseAndClear(current.release)
            is AppUpdateUiState.PermissionUnavailable -> if (!current.required) postponeReleaseAndClear(current.release)
            is AppUpdateUiState.Error -> if (!current.required) {
                val release = current.release
                if (release != null) postponeReleaseAndClear(release) else clearToIdle()
            }
            AppUpdateUiState.UpToDate -> _state.value = AppUpdateUiState.Idle
            else -> Unit
        }
    }

    private fun postponeReleaseAndClear(release: AppUpdateRelease) {
        checkForAppUpdateUseCase.postpone(release.version.versionCode)
        clearToIdle()
    }

    private fun clearToIdle() {
        pendingPermissionRelease = null
        _state.value = AppUpdateUiState.Idle
    }

    /** Bouton « Réessayer » sur `Error` : relance la vérification (erreur de contrôle) ou le téléchargement. */
    fun retry() {
        val current = _state.value as? AppUpdateUiState.Error ?: return
        val release = current.release
        if (release == null) {
            checkManually()
        } else {
            if (operationJob?.isActive == true) return
            operationJob = viewModelScope.launch { downloadAndInstall(release, current.required) }
        }
    }

    private fun availableRelease(): Pair<AppUpdateRelease, Boolean>? = when (val s = _state.value) {
        is AppUpdateUiState.Available -> s.release to s.required
        else -> null
    }
}
