package com.cstv.app.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.cstv.app.di.IptvLog
import com.cstv.app.domain.update.PackageInstallResult
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val TAG = "APPUPDATE_INSTALL"

/**
 * Reçoit le statut de la session `PackageInstaller` (F35, §4.6). `exported
 * ="false"` dans le manifeste et `PendingIntent` explicite créé par
 * l'application : aucun tiers ne peut déclencher ce receiver.
 *
 * `STATUS_PENDING_USER_ACTION` ouvre la confirmation système fournie par
 * Android — l'application n'a alors plus la main (RG16). Les issues
 * terminales atteignables en pratique (`STATUS_FAILURE_ABORTED`, échecs)
 * sont relayées via [UpdateInstallResultBus] : `STATUS_SUCCESS` survient
 * après remplacement du processus et n'a donc jamais d'observateur côté
 * application.
 */
@AndroidEntryPoint
class UpdateInstallResultReceiver : BroadcastReceiver() {

    @Inject lateinit var resultBus: UpdateInstallResultBus

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> openConfirmation(context, intent)
            PackageInstaller.STATUS_SUCCESS -> resultBus.tryEmit(PackageInstallResult.Success)
            PackageInstaller.STATUS_FAILURE_ABORTED -> resultBus.tryEmit(PackageInstallResult.Aborted)
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                IptvLog.e(TAG, "Échec d'installation de la mise à jour (statut $status) : $message")
                resultBus.tryEmit(PackageInstallResult.Failed(message.ifBlank { "status_$status" }))
            }
        }
    }

    private fun openConfirmation(context: Context, intent: Intent) {
        val confirmationIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
        if (confirmationIntent == null) {
            resultBus.tryEmit(PackageInstallResult.Failed("confirmation_intent_missing"))
            return
        }
        try {
            context.startActivity(confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            IptvLog.e(TAG, "Impossible d'ouvrir la confirmation système d'installation", e)
            resultBus.tryEmit(PackageInstallResult.Failed(e.message ?: "confirmation_intent_failed"))
        }
    }
}
