package com.cstv.app.domain.update

import java.io.File
import kotlinx.coroutines.flow.Flow

/**
 * Passerelle d'installation Android (F35). Interface mockable : le
 * ViewModel n'a pas à connaître `PackageInstaller` ni les réglages système
 * (§5.5) — implémentation réelle : `AndroidPackageInstaller`.
 */
interface PackageInstallerGateway {

    /** `false` avant API 26 : aucune autorisation par application n'existe (§4.6). */
    fun canRequestInstallPackages(): Boolean

    /** `false` si l'écran système d'autorisation n'est pas résolvable sur cet appareil. */
    fun canOpenPermissionSettings(): Boolean

    /** Ouvre l'écran système d'autorisation « sources inconnues ». */
    fun openPermissionSettings()

    /**
     * Lance l'installation. Le retour n'implique pas la fin du processus
     * d'installation : un succès (`Success`) termine le processus applicatif
     * avant même que l'appelant ne puisse réagir dans la pratique ; un
     * abandon ou un échec **découverts après confirmation système**
     * remontent en asynchrone via [installResults], puisque l'utilisateur
     * peut agir bien après le retour de cette fonction suspendue.
     */
    suspend fun install(apkFile: File): PackageInstallResult

    /** Issues arrivant après une confirmation système ouverte par [install] (`Aborted`/`Failed`). */
    val installResults: Flow<PackageInstallResult>
}

sealed class PackageInstallResult {
    /** Confirmation système ouverte ; l'application n'a plus la main (RG16). */
    data object AwaitingUserConfirmation : PackageInstallResult()
    data object Success : PackageInstallResult()

    /** Annulation du dialogue système : pas une erreur (§3.8). */
    data object Aborted : PackageInstallResult()
    data class Failed(val reason: String) : PackageInstallResult()
}
