package com.cstv.app.domain.usecase

import com.cstv.app.data.repository.MediaClassificationKind
import com.cstv.app.data.security.ParentalPinStore
import com.cstv.app.data.security.PinVerificationResult
import com.cstv.app.domain.model.OneShotPlaybackGrantStore
import com.cstv.app.domain.repository.ParentalAuthorizationRepository
import com.cstv.app.domain.repository.ProfileRepository
import javax.inject.Inject

/** Résultat exposé aux ViewModels d'écran de refus (F44, §8.6). */
sealed interface ParentalUnlockResult {
    /** PIN correct : [nonce] doit être fourni à `CanPlayContentUseCase`/téléchargement pour ce média. */
    data class Unlocked(val nonce: String) : ParentalUnlockResult
    data object Incorrect : ParentalUnlockResult
    data class Locked(val remainingMillis: Long) : ParentalUnlockResult
}

/**
 * Point d'entrée unique des écrans de saisie PIN pour débloquer une lecture
 * ou un téléchargement ponctuel (F44 §8.4). Un PIN correct crée un
 * [OneShotPlaybackGrantStore] grant, jamais persisté, lié au profil actif et
 * au média demandé.
 */
class ParentalUnlockUseCase @Inject constructor(
    private val pinStore: ParentalPinStore,
    private val grantStore: OneShotPlaybackGrantStore,
    private val profileRepository: ProfileRepository,
    private val parentalAuthorizationRepository: ParentalAuthorizationRepository,
) {
    fun hasPin(): Boolean = pinStore.hasPin()

    fun remainingLockMillis(): Long? = pinStore.remainingLockMillis()

    /**
     * [rememberTarget] : non nul quand l'utilisateur a coché « toujours
     * autoriser ce contenu sur ce profil » (F45, évolution F44) — un PIN
     * correct pose alors, en plus du grant one-shot habituel, une autorisation
     * permanente pour ce profil et cette œuvre (série entière pour un
     * épisode), synchronisée dans le cloud.
     */
    suspend fun unlock(pin: String, mediaUid: String, rememberTarget: ParentalAuthorizationTarget? = null): ParentalUnlockResult =
        when (val verification = pinStore.verifyPin(pin)) {
            PinVerificationResult.Correct -> {
                val profileId = profileRepository.currentProfileId()
                val nonce = grantStore.issue(profileId, mediaUid)
                if (rememberTarget != null) {
                    parentalAuthorizationRepository.authorize(profileId, rememberTarget.kind, rememberTarget.providerId)
                }
                ParentalUnlockResult.Unlocked(nonce)
            }
            PinVerificationResult.Incorrect -> ParentalUnlockResult.Incorrect
            is PinVerificationResult.JustLocked -> ParentalUnlockResult.Locked(verification.remainingMillis)
            is PinVerificationResult.Locked -> ParentalUnlockResult.Locked(verification.remainingMillis)
        }
}
