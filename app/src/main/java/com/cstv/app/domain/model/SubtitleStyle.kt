package com.cstv.app.domain.model

/**
 * Préférences d'apparence des sous-titres du player film/série (Phase 26).
 *
 * Les valeurs numériques (fraction de taille, couleurs ARGB, alpha du fond) sont
 * portées par les enums eux-mêmes afin de rester du pur Kotlin testable ; la
 * conversion vers `CaptionStyleCompat`/`SubtitleView` de Media3 se fait côté
 * présentation (voir SubtitleStyle.toCaptionStyleCompat / textFraction).
 */

enum class SubtitleTextSize(val fraction: Float, val label: String) {
    // Media3 SubtitleView.DEFAULT_TEXT_SIZE_FRACTION vaut 0.0533f (= MEDIUM).
    SMALL(0.04f, "Petite"),
    MEDIUM(0.0533f, "Moyenne"),
    LARGE(0.08f, "Grande")
}

enum class SubtitleTextColor(val argb: Long, val label: String) {
    WHITE(0xFFFFFFFFL, "Blanc"),
    YELLOW(0xFFFFEB3BL, "Jaune"),
    CYAN(0xFF00E5FFL, "Cyan"),
    GREEN(0xFF00E676L, "Vert")
}

enum class SubtitleBackground(val alpha: Int, val label: String) {
    NONE(0x00, "Aucun"),
    SEMI(0x80, "Semi"),
    SOLID(0xCC, "Opaque");

    /** Couleur ARGB du fond : noir avec l'alpha choisi. */
    val argb: Long
        get() = (alpha.toLong() shl 24)
}

data class SubtitleStyle(
    val size: SubtitleTextSize = SubtitleTextSize.MEDIUM,
    val textColor: SubtitleTextColor = SubtitleTextColor.WHITE,
    val background: SubtitleBackground = SubtitleBackground.NONE
)
