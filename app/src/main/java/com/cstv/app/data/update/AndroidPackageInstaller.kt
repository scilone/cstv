package com.cstv.app.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.cstv.app.BuildConfig
import com.cstv.app.di.IptvLog
import com.cstv.app.domain.update.PackageInstallResult
import com.cstv.app.domain.update.PackageInstallerGateway
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "APPUPDATE_INSTALL"
private const val SESSION_NAME = "cstv_update"

/**
 * Adaptateur Android mince (F35, §4.6) : autorisation « sources inconnues »
 * et session `PackageInstaller`. Aucune décision métier — voir §5.5.
 *
 * `PackageInstaller.Session` plutôt que l'intent APK historique
 * (`ACTION_INSTALL_PACKAGE`, déprécié depuis API 29) : API moderne
 * disponible dès le minSdk 21 du projet, résultat structuré, aucun besoin de
 * `FileProvider`.
 */
@Singleton
class AndroidPackageInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resultBus: UpdateInstallResultBus
) : PackageInstallerGateway {

    override fun canRequestInstallPackages(): Boolean {
        // Sous API 26, il n'existe pas d'autorisation par application : c'est
        // le réglage global « sources inconnues » qui s'applique (§4.6).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    override fun canOpenPermissionSettings(): Boolean =
        permissionSettingsIntent().resolveActivity(context.packageManager) != null

    override fun openPermissionSettings() {
        val intent = permissionSettingsIntent()
        if (intent.resolveActivity(context.packageManager) == null) return
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun permissionSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${BuildConfig.APPLICATION_ID}"))

    override suspend fun install(apkFile: File): PackageInstallResult = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        var sessionId: Int? = null
        try {
            sessionId = createAndWriteSession(installer, apkFile)
            installer.openSession(sessionId).use { session ->
                session.commit(confirmationPendingIntentSender(sessionId))
            }
            // Le statut réel (confirmation, abandon, échec) arrive de façon
            // asynchrone via UpdateInstallResultReceiver → resultBus.
            PackageInstallResult.AwaitingUserConfirmation
        } catch (e: Exception) {
            IptvLog.e(TAG, "Installation de la mise à jour impossible", e)
            // F35-R8 : une session créée mais jamais commitée reste "staging"
            // côté système jusqu'à nettoyage OS — l'abandonner explicitement
            // évite d'accumuler des sessions orphelines après des échecs répétés.
            sessionId?.let { id -> runCatching { installer.abandonSession(id) } }
            PackageInstallResult.Failed(e.message ?: "install_error")
        }
    }

    private fun createAndWriteSession(installer: PackageInstaller, apkFile: File): Int {
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(BuildConfig.APPLICATION_ID)
            // F35-R8 : le §4.6 exige une confirmation utilisateur explicite dès
            // l'API 31 plutôt que de dépendre du comportement implicite de la
            // plateforme (susceptible de changer entre versions/OEM).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apkFile.inputStream().use { input ->
                    session.openWrite(SESSION_NAME, 0, apkFile.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
        return sessionId
    }

    /** `IntentSender` mutable obligatoire pour recevoir les extras du résultat (targetSdk 35, §4.12). */
    private fun confirmationPendingIntentSender(sessionId: Int) =
        PendingIntent.getBroadcast(
            context,
            sessionId,
            Intent(context, UpdateInstallResultReceiver::class.java).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        ).intentSender

    override val installResults: Flow<PackageInstallResult> get() = resultBus.results
}
