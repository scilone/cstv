package com.cstv.app.presentation.bootstrap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cstv.app.R
import com.cstv.app.domain.sync.CatalogSection
import com.cstv.app.domain.sync.CatalogStatus
import com.cstv.app.domain.sync.CatalogSyncManager
import com.cstv.app.domain.sync.SyncFailureKind
import com.cstv.app.domain.sync.SyncState
import com.cstv.app.domain.sync.SyncTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Adapte l'état durable du catalogue au gate de premier remplissage. Il ne
 * connaît ni Room ni le réseau : le [CatalogSyncManager] reste l'unique
 * orchestrateur des synchronisations.
 */
@HiltViewModel
class CatalogBootstrapViewModel @Inject constructor(
    private val catalogSyncManager: CatalogSyncManager
) : ViewModel() {

    private val mutableState = MutableStateFlow(CatalogBootstrapUiState())
    val state: StateFlow<CatalogBootstrapUiState> = mutableState.asStateFlow()
    private var startupRequestedForCurrentIncompleteCatalog = false

    init {
        viewModelScope.launch {
            combine(catalogSyncManager.catalogStatus, catalogSyncManager.syncState) { status, syncState ->
                status.toBootstrapState(syncState)
            }.collect {
                mutableState.value = it
                if (!it.blocking) startupRequestedForCurrentIncompleteCatalog = false
            }
        }
    }

    /**
     * Appelé à l'entrée du gate. Une erreur déjà affichée ne déclenche jamais
     * une boucle : seule [retry] ou le gestionnaire de reconnexion peuvent
     * reprendre une synchronisation.
     */
    fun startIfNeeded() {
        val current = state.value
        if (startupRequestedForCurrentIncompleteCatalog ||
            !current.blocking || current.isSyncing || current.offline || current.failure != null
        ) return
        startupRequestedForCurrentIncompleteCatalog = true
        viewModelScope.launch { catalogSyncManager.syncNow(SyncTrigger.STARTUP) }
    }

    /** Action explicite utilisateur, valable également après un échec AUTH. */
    fun retry() {
        if (!state.value.blocking || state.value.isSyncing) return
        startupRequestedForCurrentIncompleteCatalog = false
        viewModelScope.launch { catalogSyncManager.syncNow(SyncTrigger.STARTUP) }
    }
}

private fun CatalogStatus.toBootstrapState(syncState: SyncState): CatalogBootstrapUiState {
    val running = syncState as? SyncState.Running
    // `lastFailureKind` est durable pour le diagnostic du manager. L'écran ne
    // l'affiche que pour l'échec réellement en cours : au prochain démarrage,
    // une connectivité redevenue disponible doit pouvoir lancer RG4.
    val failure = (syncState as? SyncState.Failed)?.kind
    val isCatalogStep = running?.section in CATALOG_SECTIONS
    val stepIndex = if (isCatalogStep) (running!!.done + 1).coerceIn(1, CATALOG_STEP_COUNT) else 0

    return CatalogBootstrapUiState(
        isResolved = true,
        blocking = !isComplete,
        stepLabelRes = running?.section?.toStepLabelRes(),
        stepIndex = stepIndex,
        failure = failure,
        offline = !isNetworkOnline || failure == SyncFailureKind.NETWORK,
        isSyncing = isSyncing
    )
}

private fun String.toStepLabelRes(): Int? = when (this) {
    CatalogSection.LIVE_CATEGORIES -> R.string.catalog_bootstrap_live_categories
    CatalogSection.LIVE_STREAMS -> R.string.catalog_bootstrap_live_streams
    CatalogSection.VOD_CATEGORIES -> R.string.catalog_bootstrap_vod_categories
    CatalogSection.VOD_STREAMS -> R.string.catalog_bootstrap_vod_streams
    CatalogSection.SERIES_CATEGORIES -> R.string.catalog_bootstrap_series_categories
    CatalogSection.SERIES_STREAMS -> R.string.catalog_bootstrap_series_streams
    else -> null
}

private val CATALOG_SECTIONS = setOf(
    CatalogSection.LIVE_CATEGORIES,
    CatalogSection.LIVE_STREAMS,
    CatalogSection.VOD_CATEGORIES,
    CatalogSection.VOD_STREAMS,
    CatalogSection.SERIES_CATEGORIES,
    CatalogSection.SERIES_STREAMS
)
