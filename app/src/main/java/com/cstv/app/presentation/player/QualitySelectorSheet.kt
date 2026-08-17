package com.cstv.app.presentation.player

import androidx.compose.runtime.Composable
import com.cstv.app.domain.model.LiveVariant
import com.cstv.app.R
import com.cstv.app.domain.model.displayQuality
import androidx.compose.ui.res.stringResource

/** F40 selector. It deliberately reuses the already TV-safe version-sheet container. */
data class QualityOption(val variant: LiveVariant, val isActive: Boolean, val enabled: Boolean = true, val disabledReason: String? = null)

@Composable
fun QualitySelectorSheet(
    options: List<QualityOption>,
    onSelect: (QualityOption) -> Unit,
    onDismiss: () -> Unit,
    isTv: Boolean,
    isSwitching: Boolean
) {
    val automaticFallback = stringResource(R.string.player_quality_automatic)
    VersionSelectorSheet(
        options = options.map { option ->
            VersionOption(
                option.variant.stream.streamId,
                option.variant.displayQuality(automaticFallback).let { label ->
                    option.variant.stream.num.takeIf { it > 0 }?.let { "$label ($it)" } ?: label
                },
                option.isActive,
                option.enabled,
                option.disabledReason
            )
        },
        isSwitching = isSwitching,
        onSelect = { selected -> options.firstOrNull { it.variant.stream.streamId == selected.id && it.enabled }?.let(onSelect) },
        onDismiss = onDismiss,
        isTv = isTv
    )
}
