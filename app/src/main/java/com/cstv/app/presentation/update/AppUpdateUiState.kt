package com.cstv.app.presentation.update

import com.cstv.app.domain.model.AppUpdateCheckOrigin
import com.cstv.app.domain.model.AppUpdateRelease

/** Erreur générique, sans exposer de message système brut (§4.6/§4.11). */
enum class AppUpdateErrorKind { CHECK, DOWNLOAD, INSTALL }

/**
 * État fini du dialogue de mise à jour (F35, §4.3), sans booléens
 * contradictoires. `required` reste vrai dans les états permission,
 * téléchargement et erreur d'une mise à jour obligatoire : le dialogue peut
 * ainsi remplacer Annuler/Fermer par Quitter sans déduire cette règle du
 * texte affiché.
 */
sealed class AppUpdateUiState {
    data object Idle : AppUpdateUiState()
    data class Checking(val origin: AppUpdateCheckOrigin) : AppUpdateUiState()
    data class Available(val release: AppUpdateRelease, val required: Boolean) : AppUpdateUiState()
    data class PermissionRequired(val release: AppUpdateRelease, val required: Boolean) : AppUpdateUiState()
    data class PermissionUnavailable(val release: AppUpdateRelease, val required: Boolean) : AppUpdateUiState()
    data class Downloading(val release: AppUpdateRelease, val required: Boolean, val percent: Int) : AppUpdateUiState()
    data class ReadyToInstall(val release: AppUpdateRelease, val required: Boolean) : AppUpdateUiState()
    data class Error(val release: AppUpdateRelease?, val required: Boolean, val kind: AppUpdateErrorKind) : AppUpdateUiState()

    /** Retour manuel transitoire : « Votre application est à jour. » (§3.4). */
    data object UpToDate : AppUpdateUiState()
}
