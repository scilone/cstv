package com.cstv.app.domain.usecase

import com.cstv.app.data.security.ParentalPinStore
import com.cstv.app.domain.util.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PIN oublié (F44 §8.5) : réutilise le flow OTP existant en mode
 * réauthentification. Le backend ne stocke aucun PIN — il atteste seulement
 * que l'utilisateur contrôle toujours l'email du compte. Une vérification
 * réussie ouvre une fenêtre de fraîcheur de 5 minutes pendant laquelle
 * [resetPin] est autorisé ; hors de cette fenêtre (ou sans vérification),
 * `resetPin` échoue et le PIN actuel reste intact.
 */
@Singleton
class ParentalPinResetUseCase @Inject constructor(
    private val verifyOtpUseCase: VerifyOtpUseCase,
    private val pinStore: ParentalPinStore,
    private val timeProvider: TimeProvider,
) {
    @Volatile private var reauthenticatedAtMillis: Long? = null

    /**
     * Échec réseau/backend indisponible ou OTP invalide : `Result.failure`,
     * aucune fenêtre de fraîcheur ouverte, le PIN existant reste intact.
     */
    suspend fun verifyReauthentication(email: String, code: String): Result<Unit> =
        verifyOtpUseCase(email, code).map { reauthenticatedAtMillis = timeProvider.nowMillis() }

    fun isReauthenticationFresh(): Boolean {
        val at = reauthenticatedAtMillis ?: return false
        return timeProvider.nowMillis() - at in 0..FRESHNESS_WINDOW_MS
    }

    /**
     * `false` si la fenêtre de fraîcheur est expirée ou n'a jamais été ouverte
     * — le PIN n'est alors jamais modifié. Consomme la fraîcheur dans tous les
     * cas de succès : une seule réinitialisation par vérification OTP.
     */
    fun resetPin(newPin: String): Boolean {
        if (!isReauthenticationFresh()) return false
        pinStore.replacePin(newPin)
        reauthenticatedAtMillis = null
        return true
    }

    companion object {
        internal const val FRESHNESS_WINDOW_MS = 5L * 60 * 1000
    }
}
