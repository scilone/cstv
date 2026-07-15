package com.poc.iptvxtream.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.poc.iptvxtream.data.local.storage.AppAccentColor

@Composable
fun IptvXtreamTheme(
    accentColor: AppAccentColor = AppAccentColor.LAVANDE,
    content: @Composable () -> Unit
) {
    val primaryColor = when (accentColor) {
        AppAccentColor.LAVANDE -> AccentLavande
        AppAccentColor.BLEU_ROYAL -> AccentBlue
        AppAccentColor.SARCELLE -> AccentTeal
        AppAccentColor.AMBRE -> AccentAmber
    }

    val dynamicColorScheme = darkColorScheme(
        primary = primaryColor,
        onPrimary = Color.Black,
        primaryContainer = primaryColor,
        onPrimaryContainer = Color.Black,
        secondary = Surface3,
        onSecondary = TextPrimary,
        background = DarkBackground,
        onBackground = TextPrimary,
        surface = Surface1,
        onSurface = TextPrimary,
        surfaceVariant = Surface2,
        onSurfaceVariant = TextSecondary,
        outline = TextSecondary
    )

    MaterialTheme(
        colorScheme = dynamicColorScheme,
        typography = AppTypography,
        content = content
    )
}

fun Modifier.mobileBackground(): Modifier = composed {
    Modifier.drawBehind {
        val width = size.width
        val height = size.height
        val radius = width * 1.2f
        val center = Offset(width * 0.5f, height * -0.08f)
        val gradient = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF1A1330),
                0.44f to Color(0xFF0B0B12),
                1.0f to Color(0xFF060608)
            ),
            center = center,
            radius = radius
        )
        drawRect(brush = gradient, size = size)
    }
}
