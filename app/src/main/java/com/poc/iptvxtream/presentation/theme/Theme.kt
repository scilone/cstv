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

private val DarkColorScheme = darkColorScheme(
    primary = AccentLavande,
    onPrimary = Color.White,
    primaryContainer = AccentLavande,
    onPrimaryContainer = Color.White,
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

@Composable
fun IptvXtreamTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
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
                0.0f to DarkBackgroundGradientStart,
                0.44f to DarkBackgroundGradientEnd,
                1.0f to DarkBackground
            ),
            center = center,
            radius = radius
        )
        drawRect(brush = gradient, size = size)
    }
}
