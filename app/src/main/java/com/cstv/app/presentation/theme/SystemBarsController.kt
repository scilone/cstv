package com.cstv.app.presentation.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private val IMMERSIVE_PLAYER_ROUTES = setOf(
    "live_player",
    "vod_player",
    "series_player"
)

internal fun isImmersivePlayerRoute(route: String?): Boolean = route in IMMERSIVE_PLAYER_ROUTES

/** Applies the window policy shared by every mobile and TV surface. */
@Composable
fun SystemBarsController(isTv: Boolean, isPlayerRoute: Boolean) {
    val view = LocalView.current
    val activity = view.context.findActivity() ?: return

    LaunchedEffect(isTv, isPlayerRoute) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false

        if (isTv || isPlayerRoute) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
