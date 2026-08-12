package com.cstv.app.domain.model

/**
 * Version SemVer dérivée du tag d'une Release GitHub (F35).
 *
 * `versionCode` reprend la formule déjà utilisée par le script de release
 * (`major*10_000 + minor*100 + patch`, voir `app/build.gradle.kts`) : une
 * simple comparaison entière contre `BuildConfig.VERSION_CODE` suffit, sans
 * manifeste de version supplémentaire à maintenir. La construction directe
 * reste publique (fixtures de test) ; toute donnée **externe** doit passer
 * par [parse], seul point qui garantit l'absence de débordement.
 */
data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) {
    val displayName: String get() = "$major.$minor.$patch"
    val versionCode: Int get() = major * MAJOR_MULTIPLIER + minor * MINOR_MULTIPLIER + patch

    companion object {
        private const val MAJOR_MULTIPLIER = 10_000
        private const val MINOR_MULTIPLIER = 100
        private const val COMPONENT_MAX = 99
        private val TAG_PATTERN = Regex("""^v(\d+)\.(\d+)\.(\d+)$""")

        /**
         * Parse strict `vX.Y.Z` : trois composantes numériques, `minor`/`patch`
         * dans 0–99 (marge du calcul `versionCode`). Toute autre valeur — y
         * compris un débordement d'`Int` lors du calcul — produit `null`,
         * jamais une exception (cas limite §3.8 / CA15 de F35).
         */
        fun parse(tag: String?): SemanticVersion? {
            if (tag.isNullOrBlank()) return null
            val match = TAG_PATTERN.matchEntire(tag) ?: return null
            val (majorStr, minorStr, patchStr) = match.destructured
            val major = majorStr.toIntOrNull() ?: return null
            val minor = minorStr.toIntOrNull() ?: return null
            val patch = patchStr.toIntOrNull() ?: return null
            if (minor > COMPONENT_MAX || patch > COMPONENT_MAX) return null
            val versionCodeLong = major.toLong() * MAJOR_MULTIPLIER + minor.toLong() * MINOR_MULTIPLIER + patch
            if (versionCodeLong > Int.MAX_VALUE) return null
            return SemanticVersion(major, minor, patch)
        }
    }
}

/** Origine d'une vérification : conditionne l'application des règles de silence (RG2/RG4/RG5/RG8). */
enum class AppUpdateCheckOrigin { AUTOMATIC, MANUAL }

/** Release exploitable : version, et le seul asset `.apk` retenu. */
data class AppUpdateRelease(
    val version: SemanticVersion,
    val assetDownloadUrl: String,
    val assetName: String,
    val assetSizeBytes: Long
)

/** Résultat brut d'interrogation de la Release GitHub (couche repository). */
sealed class AppUpdateCheckResult {
    data class ReleaseFound(val release: AppUpdateRelease) : AppUpdateCheckResult()

    /** JSON lisible mais tag non conforme, Release brouillon/pré-release, ou aucun asset `.apk` exploitable (CA14/CA15). */
    data object NoUsableRelease : AppUpdateCheckResult()

    /** Réseau indisponible, HTTP non réussi, timeout ou JSON illisible. */
    data class Failure(val reason: String) : AppUpdateCheckResult()
}

/** Décision applicative issue de [com.cstv.app.domain.usecase.CheckForAppUpdateUseCase] (§4.3). */
sealed class AppUpdateDecision {
    /** Vérification automatique non due (RG2) ou repoussée/ignorée (RG4/RG5). */
    data object NoCheckNeeded : AppUpdateDecision()
    data object UpToDate : AppUpdateDecision()
    data class OptionalUpdate(val release: AppUpdateRelease) : AppUpdateDecision()
    data class RequiredUpdate(val release: AppUpdateRelease) : AppUpdateDecision()
    data object CheckFailed : AppUpdateDecision()
}
