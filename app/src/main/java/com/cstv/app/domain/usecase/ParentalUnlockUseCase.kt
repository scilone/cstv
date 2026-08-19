package com.cstv.app.domain.usecase

import com.cstv.app.data.security.ParentalPinStore
import com.cstv.app.data.security.PinVerificationResult
import com.cstv.app.domain.model.OneShotPlaybackGrantStore
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
) {
    fun hasPin(): Boolean = pinStore.hasPin()

    fun remainingLockMillis(): Long? = pinStore.remainingLockMillis()

    fun unlock(pin: String, mediaUid: String): ParentalUnlockResult = when (val verification = pinStore.verifyPin(pin)) {
        PinVerificationResult.Correct -> {
            val nonce = grantStore.issue(profileRepository.currentProfileId(), mediaUid)
            ParentalUnlockResult.Unlocked(nonce)
        }
        PinVerificationResult.Incorrect -> ParentalUnlockResult.Incorrect
        is PinVerificationResult.JustLocked -> ParentalUnlockResult.Locked(verification.remainingMillis)
        is PinVerificationResult.Locked -> ParentalUnlockResult.Locked(verification.remainingMillis)
    }
}
