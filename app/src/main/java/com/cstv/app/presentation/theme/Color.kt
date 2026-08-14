package com.cstv.app.presentation.theme

import androidx.compose.ui.graphics.Color

// Base theme colors
val DarkBackground = Color(0xFF060608)
val DarkBackgroundGradientStart = Color(0xFF1A1330)
val DarkBackgroundGradientEnd = Color(0xFF0B0B12)

val Surface1 = Color(0xFF0F0F13)
val Surface2 = Color(0xFF16161D)
val Surface3 = Color(0xFF1E1E24)

/**
 * Aplat des contrôles posés *sur* une carte `Surface3` (B27) : boutons de
 * paramètres, options de tri, éléments focalisables TV. Sur `Surface3`, un
 * contrôle en `Surface3` disparaît dans sa carte — il faut un cran au-dessus.
 * Valeur déjà employée en dur dans une dizaine d'écrans, nommée ici.
 */
val Surface4 = Color(0xFF2C2C35)

val AccentLavande = Color(0xFF9C86FF) // primary
val AccentLavandeHover = Color(0xFFB3A3FF)

val TextPrimary = Color(0xFFF6F6FA)
val TextSecondary = Color(0xFF9A9AA8)

// Optional alternative accents for Phase 54
val AccentBlue = Color(0xFF0070F3)
val AccentTeal = Color(0xFF2BB8A6)
val AccentAmber = Color(0xFFE5A13A)
val FavoriteGold = Color(0xFFFFB300)

// Rating (F7) - sober red/green. Red reused from Settings (logout/danger) for homogeneity.
val RatingLike = Color(0xFF66BB6A)
val RatingDislike = Color(0xFFCF6679)

// Contenu foncé contrasté sur un aplat RatingDislike (bouton déconnexion TV, F32).
val OnRatingDislike = Color(0xFF1A0D10)
