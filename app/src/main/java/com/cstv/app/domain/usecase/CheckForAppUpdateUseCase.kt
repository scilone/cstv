package com.cstv.app.domain.usecase

import com.cstv.app.data.local.storage.AppUpdatePreferences
import com.cstv.app.domain.model.AppUpdateCheckOrigin
import com.cstv.app.domain.model.AppUpdateCheckResult
import com.cstv.app.domain.model.AppUpdateDecision
import com.cstv.app.domain.model.AppUpdateRelease
import com.cstv.app.domain.repository.AppUpdateRepository
import com.cstv.app.domain.util.TimeProvider
import javax.inject.Inject

/**
 * Applique RG1–RG9 (F35) : décide si/quand proposer une mise à jour, et si
 * elle est obligatoire. Ne fait ni réseau, ni UI, ni accès fichier — le
 * réseau est délégué à [AppUpdateRepository], la persistance des décisions à
 * [AppUpdatePreferences].
 */
class CheckForAppUpdateUseCase @Inject constructor(
    private val repository: AppUpdateRepository,
    private val preferences: AppUpdatePreferences,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(origin: AppUpdateCheckOrigin, installedVersionCode: Int): AppUpdateDecision {
        // Une mise à jour installée par un autre moyen rend obsolète la
        // dernière Release majeure connue : on l'oublie avant toute décision.
        pruneObsoleteKnownRelease(installedVersionCode)

        if (origin == AppUpdateCheckOrigin.AUTOMATIC) {
            // RG7 : une majeure déjà connue se réaffiche à chaque démarrage,
            // sans nouvel appel réseau, avant même le throttle RG2.
            knownRequiredRelease(installedVersionCode)?.let { return AppUpdateDecision.RequiredUpdate(it) }
            if (!isAutomaticCheckDue()) return AppUpdateDecision.NoCheckNeeded
        }
        // RG8 : la recherche manuelle ignore le cache, le throttle et les
        // règles de silence — elle interroge toujours le réseau.

        return when (val result = repository.fetchLatestRelease()) {
            is AppUpdateCheckResult.Failure -> {
                // RG15 : silencieux, horodatage inchangé (réessai au prochain démarrage).
                AppUpdateDecision.CheckFailed
            }
            AppUpdateCheckResult.NoUsableRelease -> {
                markAutomaticCheckDone(origin)
                AppUpdateDecision.UpToDate
            }
            is AppUpdateCheckResult.ReleaseFound -> handleReleaseFound(result.release, origin, installedVersionCode)
        }
    }

    /** RG4 : reporte l'invitation pour ce `versionCode` jusqu'au lendemain. */
    fun postpone(versionCode: Int) {
        preferences.setPostponed(versionCode, timeProvider.nowMillis() + POSTPONE_DURATION_MS)
    }

    /** RG5 : masque définitivement l'invitation pour ce seul `versionCode`. */
    fun ignore(versionCode: Int) {
        preferences.setIgnoredVersionCode(versionCode)
    }

    private fun handleReleaseFound(
        release: AppUpdateRelease,
        origin: AppUpdateCheckOrigin,
        installedVersionCode: Int
    ): AppUpdateDecision {
        markAutomaticCheckDone(origin)

        if (release.version.versionCode <= installedVersionCode) {
            // RG3 : les rétrogradations sont ignorées.
            preferences.clearKnownRelease()
            return AppUpdateDecision.UpToDate
        }

        if (isMajorUpdate(release.version.versionCode, installedVersionCode)) {
            // RG6/RG7 : obligatoire, ignore report et version ignorée.
            preferences.saveKnownRelease(release)
            return AppUpdateDecision.RequiredUpdate(release)
        }

        if (origin == AppUpdateCheckOrigin.AUTOMATIC) {
            if (isIgnored(release.version.versionCode)) return AppUpdateDecision.UpToDate
            if (isPostponedFor(release.version.versionCode)) return AppUpdateDecision.UpToDate
        }
        return AppUpdateDecision.OptionalUpdate(release)
    }

    /** RG8 : seule une vérification automatique fait avancer l'horodatage RG2. */
    private fun markAutomaticCheckDone(origin: AppUpdateCheckOrigin) {
        if (origin == AppUpdateCheckOrigin.AUTOMATIC) {
            preferences.setLastAutomaticCheckAt(timeProvider.nowMillis())
        }
    }

    private fun isAutomaticCheckDue(): Boolean {
        val last = preferences.getLastAutomaticCheckAt() ?: return true
        val now = timeProvider.nowMillis()
        // Horloge reculée depuis la dernière vérification : traité comme expiré,
        // pour ne pas désactiver indéfiniment les contrôles (§4.5).
        if (now < last) return true
        return now - last >= AUTOMATIC_CHECK_INTERVAL_MS
    }

    private fun isIgnored(versionCode: Int): Boolean = preferences.getIgnoredVersionCode() == versionCode

    private fun isPostponedFor(versionCode: Int): Boolean {
        if (preferences.getPostponedVersionCode() != versionCode) return false
        val until = preferences.getPostponedUntilMillis() ?: return false
        val remaining = until - timeProvider.nowMillis()
        if (remaining <= 0) return false // report expiré normalement.
        // Horloge reculée après le report : ne bloque pas indéfiniment.
        if (remaining > POSTPONE_DURATION_MS) return false
        return true
    }

    private fun pruneObsoleteKnownRelease(installedVersionCode: Int) {
        val known = preferences.getKnownRelease() ?: return
        if (known.version.versionCode <= installedVersionCode) preferences.clearKnownRelease()
    }

    private fun knownRequiredRelease(installedVersionCode: Int): AppUpdateRelease? {
        val known = preferences.getKnownRelease() ?: return null
        if (known.version.versionCode <= installedVersionCode) return null
        return known.takeIf { isMajorUpdate(it.version.versionCode, installedVersionCode) }
    }

    /** RG6 : rupture de compatibilité = numéro MAJEUR distant supérieur au numéro majeur installé. */
    private fun isMajorUpdate(remoteVersionCode: Int, installedVersionCode: Int): Boolean =
        remoteVersionCode / MAJOR_DIVISOR > installedVersionCode / MAJOR_DIVISOR

    companion object {
        const val AUTOMATIC_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
        const val POSTPONE_DURATION_MS = 24L * 60 * 60 * 1000
        private const val MAJOR_DIVISOR = 10_000
    }
}
