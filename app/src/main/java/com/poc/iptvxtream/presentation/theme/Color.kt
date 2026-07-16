package com.poc.iptvxtream.presentation.theme

import androidx.compose.ui.graphics.Color

// Base theme colors
val DarkBackground = Color(0xFF060608)
val DarkBackgroundGradientStart = Color(0xFF1A1330)
val DarkBackgroundGradientEnd = Color(0xFF0B0B12)

val Surface1 = Color(0xFF0F0F13)
val Surface2 = Color(0xFF16161D)
val Surface3 = Color(0xFF1E1E24)

val AccentLavande = Color(0xFF9C86FF) // primary
val AccentLavandeHover = Color(0xFFB3A3FF)

val TextPrimary = Color(0xFFF6F6FA)
val TextSecondary = Color(0xFF9A9AA8)

// Optional alternative accents for Phase 54
val AccentBlue = Color(0xFF0070F3)
val AccentTeal = Color(0xFF2BB8A6)
val AccentAmber = Color(0xFFE5A13A)

// --- Tokens ajoutés lors de la migration des couleurs en dur (audit #8) ---
// Valeurs strictement identiques aux anciens littéraux : aucun changement de
// rendu, seule la source de vérité change. Indépendants du MaterialTheme,
// utilisables aussi par la branche Android TV.

// Badges de type de média (Home, recherche)
val BadgeLiveRed = Color(0xFFE50914)
val BadgeSeriesPurple = Color(0xFF8A2BE2)
// AccentBlue (ci-dessus) sert de badge "movie".

// Erreurs / actions destructives
val ErrorRose = Color(0xFFCF6679)
val ErrorBright = Color(0xFFFF6B6B)
val ErrorContainerDark = Color(0xFF3F1616)
val ErrorOnContainer = Color(0xFFFFB4B4)

// Surfaces interactives (focus TV, contrôles, sliders)
val SurfaceFocused = Color(0xFF2C2C35)
val SurfaceElevated = Color(0xFF2A2A35)
val SurfaceFocusedAlt = Color(0xFF23232D)
val SurfaceSlider = Color(0xFF2A2A33)
val DividerDark = Color(0xFF4A4A54)

// Textes complémentaires (nuances entre TextPrimary et TextSecondary)
val TextBright = Color(0xFFF4F4F7)
val TextBrightAlt = Color(0xFFF2F2F6)
val TextSoft = Color(0xFFC7C7D1)
val TextSoftAlt = Color(0xFFC9C9D4)
val TextDim = Color(0xFF7A7A86)
val TextMuted = Color(0xFF63636F)
val TextOnAccent = Color(0xFF0E0E12)

// Overlays / scrims
val ScrimHeavy = Color(0xCC000000)
val ScrimMedium = Color(0x99000000)
val ScrimLight = Color(0x80000000)
val PlayerScrim = Color(0xE60F0F13)
val WhiteOverlay20 = Color(0x33FFFFFF)
val WhiteOverlay25 = Color(0x40FFFFFF)
val TrackSelectedAmber = Color(0x33FFB300)
