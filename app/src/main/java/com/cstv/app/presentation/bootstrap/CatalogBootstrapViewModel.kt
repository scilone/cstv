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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
        observeRecoverableFailures()
    }

    /**
     * Reprise automatique après un échec récupérable.
     *
     * L'écran annonce « la préparation reprendra automatiquement », mais la
     * seule reprise existante était celle du `CatalogSyncManager` sur un retour
     * de réseau — un front montant de `NetworkMonitor.isOnline`. Quand le
     * transport n'a jamais été coupé et que c'est le panel qui refuse (compte au
     * plafond de connexions simultanées, quota momentané), ce front ne se
     * produit jamais : le gate restait indéfiniment sur « Réessayer », et un
     * appareil qu'on associait pendant une lecture en cours sur un autre ne
     * passait tout simplement pas la première synchronisation. Le message
     * promettait une reprise qui n'arrivait pas.
     *
     * La relance est donc portée ici, avec un délai qui double à chaque tentative
     * — le panel a besoin qu'on cesse de le solliciter, pas qu'on insiste.
     * `AUTH` en est exclu : rejouer des identifiants refusés ne produit que du
     * trafic refusé et rapproche du bannissement (même règle que
     * `shouldResumeCatalogAfterReconnect`). Un appareil réellement hors ligne en
     * est exclu aussi : il n'y a rien à réessayer tant que le transport est
     * coupé, et le manager reprend déjà la main au retour du réseau.
     *
     * Le sondage est adossé à `subscriptionCount` : rien n'est planifié quand
     * l'écran n'observe pas — ni en production, ni sur le scheduler virtuel des
     * tests (voir AGENTS.md, « Boucles infinies de tests »).
     */
    private fun observeRecoverableFailures() {
        viewModelScope.launch {
            mutableState.subscriptionCount
                .map { it > 0 }
                .distinctUntilChanged()
                .collectLatest { isObserved ->
                    if (!isObserved) return@collectLatest
                    var delayMillis = FIRST_RETRY_DELAY_MILLIS
                    while (true) {
                        delay(delayMillis)
                        delayMillis = (delayMillis * 2).coerceAtMost(MAX_RETRY_DELAY_MILLIS)
                        val current = mutableState.value
                        if (!current.isRetryable) {
                            delayMillis = FIRST_RETRY_DELAY_MILLIS
                            continue
                        }
                        catalogSyncManager.syncNow(SyncTrigger.STARTUP)
                    }
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

    private companion object {
        const val FIRST_RETRY_DELAY_MILLIS = 15_000L
        const val MAX_RETRY_DELAY_MILLIS = 120_000L
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
