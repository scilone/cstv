package com.cstv.app.presentation.player.core

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.util.Consumer
import androidx.media3.common.VideoSize
import androidx.media3.ui.PlayerView

@Composable
fun rememberPipState(playerViewRef: PlayerView?): Boolean {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() as? ComponentActivity }
    
    var isInPipMode by remember(activity) {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                activity?.isInPictureInPictureMode == true
            } else {
                false
            }
        )
    }

    DisposableEffect(activity, playerViewRef) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return@DisposableEffect onDispose {}
        }
        val pipListener = Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
            // Le SurfaceView interne ne se relayout pas toujours au changement
            // de taille PiP. Le cycle est requis à l'entrée comme à la sortie.
            playerViewRef?.let { view ->
                view.visibility = android.view.View.INVISIBLE
                view.post { view.visibility = android.view.View.VISIBLE }
            }
        }
        activity.addOnPictureInPictureModeChangedListener(pipListener)
        onDispose {
            activity.removeOnPictureInPictureModeChangedListener(pipListener)
        }
    }

    return isInPipMode
}

fun enterPictureInPicture(activity: ComponentActivity?, videoSize: VideoSize) {
    if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val builder = android.app.PictureInPictureParams.Builder()
        val width = videoSize.width
        val height = videoSize.height
        val ratio = if (width > 0 && height > 0) width.toFloat() / height else null
        val aspectRatio = if (ratio != null && ratio in 0.4184f..2.39f) {
            android.util.Rational(width, height)
        } else {
            android.util.Rational(16, 9)
        }
        activity.enterPictureInPictureMode(builder.setAspectRatio(aspectRatio).build())
    } else {
        @Suppress("DEPRECATION")
        activity.enterPictureInPictureMode()
    }
}

@Composable
fun KeepScreenOnEffect() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
