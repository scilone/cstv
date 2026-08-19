package com.cstv.app.data.security

import android.content.SharedPreferences
import com.cstv.app.domain.util.MonotonicClock
import com.cstv.app.domain.util.TimeProvider
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Résultat d'une tentative de vérification du PIN parental (F44). */
sealed interface PinVerificationResult {
    /** PIN correct : compteurs et niveau de blocage remis à zéro. */
    data object Correct : PinVerificationResult

    /** PIN incorrect, pas encore de blocage déclenché par cette tentative. */
    data object Incorrect : PinVerificationResult

    /** PIN incorrect et cette tentative vient de déclencher un blocage. */
    data class JustLocked(val remainingMillis: Long) : PinVerificationResult

    /** Déjà bloqué : la tentative n'a même pas été comparée. */
    data class Locked(val remainingMillis: Long) : PinVerificationResult
}

/**
 * Stockage chiffré app-privé du PIN parental à 4 chiffres (F44, §8.3),
 * distinct des préférences UI et de `CredentialsManager`. Ni le PIN en clair
 * ni son hash ne sortent de cette classe (aucun log, aucune sauvegarde
 * cloud/snapshot de profil). `sharedPreferences` est le
 * `EncryptedSharedPreferences` fourni par `AppModule` (Android Keystore) — un
 * `Map`/`SharedPreferences` en mémoire suffit en test, cette classe n'a aucune
 * dépendance directe à `Context`.
 *
 * Modèle de menace assumé : protéger d'un enfant, pas d'un appareil rooté
 * (§8.3/§9.3) — pas de résistance visée contre l'extraction du Keystore.
 */
@Singleton
class ParentalPinStore @Inject constructor(
    @Named("parentalPinPrefs") private val sharedPreferences: SharedPreferences,
    private val timeProvider: TimeProvider,
    private val monotonicClock: MonotonicClock,
) {
    // Protection en mémoire, additionnelle à `lockedUntilWallMs` (persisté) :
    // un retour d'horloge murale ne suffit pas seul à lever le blocage tant
    // que le process n'a pas redémarré (§8.3).
    @Volatile private var inProcessLockStartElapsedMs: Long? = null
    @Volatile private var inProcessLockDurationMs: Long = 0

    fun hasPin(): Boolean = sharedPreferences.contains(KEY_HASH)

    /** Crée le PIN initial. Ne doit être appelé que si [hasPin] est faux. */
    fun createPin(pin: String) {
        require(isValidFormat(pin)) { "PIN must be exactly 4 digits." }
        writePin(pin)
        resetLockoutState()
    }

    /**
     * Remplace le PIN sans vérifier l'ancien : réservé au flow de
     * réinitialisation par OTP (§8.5), appelé seulement après réauthentification
     * fraîche du compte CSTV.
     */
    fun replacePin(newPin: String) {
        require(isValidFormat(newPin)) { "PIN must be exactly 4 digits." }
        writePin(newPin)
        resetLockoutState()
    }

    fun verifyPin(pin: String): PinVerificationResult {
        remainingLockMillis()?.let { remaining -> return PinVerificationResult.Locked(remaining) }
        if (!hasPin()) return PinVerificationResult.Incorrect

        val matches = isValidFormat(pin) && constantTimeEquals(deriveHash(pin, currentSalt(), currentIterations()), currentHash())
        if (matches) {
            resetLockoutState()
            return PinVerificationResult.Correct
        }

        val failures = sharedPreferences.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        if (failures >= CONSECUTIVE_FAILURES_TO_LOCK) {
            val previousLevelMs = sharedPreferences.getLong(KEY_LOCK_LEVEL_MS, 0L)
            val nextLevelMs = if (previousLevelMs <= 0L) INITIAL_LOCK_MS else (previousLevelMs * 2).coerceAtMost(MAX_LOCK_MS)
            lockFor(nextLevelMs)
            sharedPreferences.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCK_LEVEL_MS, nextLevelMs)
                .apply()
            return PinVerificationResult.JustLocked(nextLevelMs)
        }

        sharedPreferences.edit().putInt(KEY_FAILED_ATTEMPTS, failures).apply()
        return PinVerificationResult.Incorrect
    }

    /** `null` si aucun blocage n'est actif ; sinon la durée restante en ms. */
    fun remainingLockMillis(): Long? {
        val wallRemaining = sharedPreferences.getLong(KEY_LOCKED_UNTIL_WALL_MS, 0L) - timeProvider.nowMillis()
        val lockStart = inProcessLockStartElapsedMs
        val elapsedRemaining = if (lockStart != null) {
            inProcessLockDurationMs - (monotonicClock.elapsedRealtimeMillis() - lockStart)
        } else {
            Long.MIN_VALUE
        }
        val remaining = maxOf(wallRemaining, elapsedRemaining)
        return remaining.takeIf { it > 0 }
    }

    private fun lockFor(durationMs: Long) {
        sharedPreferences.edit().putLong(KEY_LOCKED_UNTIL_WALL_MS, timeProvider.nowMillis() + durationMs).apply()
        inProcessLockStartElapsedMs = monotonicClock.elapsedRealtimeMillis()
        inProcessLockDurationMs = durationMs
    }

    private fun resetLockoutState() {
        sharedPreferences.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCK_LEVEL_MS, 0L)
            .putLong(KEY_LOCKED_UNTIL_WALL_MS, 0L)
            .apply()
        inProcessLockStartElapsedMs = null
        inProcessLockDurationMs = 0
    }

    private fun writePin(pin: String) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = deriveHash(pin, salt, PBKDF2_ITERATIONS)
        sharedPreferences.edit()
            .putString(KEY_SALT, salt.toHex())
            .putString(KEY_HASH, hash.toHex())
            .putInt(KEY_VERSION, PIN_PARAMS_VERSION)
            .putInt(KEY_ITERATIONS, PBKDF2_ITERATIONS)
            .apply()
    }

    private fun currentSalt(): ByteArray = (sharedPreferences.getString(KEY_SALT, "") ?: "").hexToBytes()

    private fun currentHash(): ByteArray = (sharedPreferences.getString(KEY_HASH, "") ?: "").hexToBytes()

    private fun currentIterations(): Int = sharedPreferences.getInt(KEY_ITERATIONS, PBKDF2_ITERATIONS)

    private fun isValidFormat(pin: String): Boolean = pin.length == 4 && pin.all { it.isDigit() }

    companion object {
        private const val KEY_SALT = "pin_salt"
        private const val KEY_HASH = "pin_hash"
        private const val KEY_VERSION = "pin_params_version"
        private const val KEY_ITERATIONS = "pin_iterations"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCK_LEVEL_MS = "lock_level_ms"
        private const val KEY_LOCKED_UNTIL_WALL_MS = "locked_until_wall_ms"

        private const val SALT_BYTES = 16 // 128 bits
        private const val PBKDF2_ITERATIONS = 120_000
        private const val PIN_PARAMS_VERSION = 1
        private const val CONSECUTIVE_FAILURES_TO_LOCK = 5
        internal const val INITIAL_LOCK_MS = 30_000L
        internal const val MAX_LOCK_MS = 15 * 60 * 1000L

        internal fun deriveHash(pin: String, salt: ByteArray, iterations: Int): ByteArray {
            val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        }

        internal fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
            if (a.size != b.size) return false
            var diff = 0
            for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
            return diff == 0
        }

        // Pas de java.util.Base64 (API 26+, minSdk 21 sans desugaring) ni
        // android.util.Base64 (non disponible en test JVM local) : encodage
        // hexadécimal manuel, portable partout.
        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

        private fun String.hexToBytes(): ByteArray {
            if (isEmpty()) return ByteArray(0)
            return ByteArray(length / 2) { i -> ((this[i * 2].digitToInt(16) shl 4) + this[i * 2 + 1].digitToInt(16)).toByte() }
        }
    }
}
