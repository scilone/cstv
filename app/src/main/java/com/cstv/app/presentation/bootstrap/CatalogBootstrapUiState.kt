package com.cstv.app.presentation.bootstrap

import androidx.annotation.StringRes
import com.cstv.app.domain.sync.SyncFailureKind

/** État rendu par le gate du tout premier remplissage du catalogue (F38). */
data class CatalogBootstrapUiState(
    /** Évite de composer le catalogue avant la première lecture du statut. */
    val isResolved: Boolean = false,
    val blocking: Boolean = false,
    @StringRes val stepLabelRes: Int? = null,
    val stepIndex: Int = 0,
    val stepCount: Int = CATALOG_STEP_COUNT,
    val failure: SyncFailureKind? = null,
    val offline: Boolean = false,
    val isSyncing: Boolean = false
) {
    val progressFraction: Float
        get() = if (stepCount == 0) 0f else stepIndex.toFloat() / stepCount

    val showActions: Boolean
        get() = offline || failure != null

    /** Une phase hors des six sections reste visible, sans feindre une étape. */
    val showIndeterminateProgress: Boolean
        get() = isResolved && isSyncing && stepLabelRes == null

    /**
     * Le gate peut relancer seul la préparation (voir
     * `CatalogBootstrapViewModel.observeRecoverableFailures`).
     *
     * `AUTH` en est exclu : rejouer des identifiants refusés ne produit que du
     * trafic refusé. Un appareil hors ligne aussi : il n'y a rien à réessayer
     * tant que le transport est coupé, et le `CatalogSyncManager` reprend déjà
     * la main au retour du réseau.
     */
    val isRetryable: Boolean
        get() = isResolved && blocking && !isSyncing && !offline && failure != SyncFailureKind.AUTH
}

const val CATALOG_STEP_COUNT = 6

/** Le gate ne peut jamais masquer la route de connexion ni le gate profil. */
internal fun shouldShowCatalogBootstrap(
    hasLoggedInUser: Boolean,
    profileGateResolved: Boolean,
    state: CatalogBootstrapUiState
): Boolean = hasLoggedInUser && profileGateResolved && (!state.isResolved || state.blocking)
