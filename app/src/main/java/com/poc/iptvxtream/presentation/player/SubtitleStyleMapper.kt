package com.poc.iptvxtream.presentation.player

import android.graphics.Color
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.poc.iptvxtream.domain.model.SubtitleStyle

/**
 * Conversion des préférences de sous-titres (Phase 26) vers les capacités de
 * style natives de Media3 (CaptionStyleCompat + SubtitleView), plutôt qu'un
 * rendu de sous-titres fait à la main.
 */

@UnstableApi
fun SubtitleStyle.toCaptionStyleCompat(): CaptionStyleCompat {
    return CaptionStyleCompat(
        textColor.argb.toInt(),
        background.argb.toInt(),
        Color.TRANSPARENT,
        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
        Color.BLACK,
        null
    )
}

/** Applique le style choisi à la SubtitleView du player, en désactivant les
 *  styles embarqués pour que le réglage utilisateur soit toujours respecté. */
@UnstableApi
fun SubtitleView.applySubtitleStyle(style: SubtitleStyle) {
    setApplyEmbeddedStyles(false)
    setApplyEmbeddedFontSizes(false)
    setStyle(style.toCaptionStyleCompat())
    setFractionalTextSize(style.size.fraction)
}
