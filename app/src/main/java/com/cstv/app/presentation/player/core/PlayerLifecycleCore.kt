package com.cstv.app.presentation.player.core

import android.os.Build
import android.view.View
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

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
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
        val settledWindowRelayout = Runnable {
            playerViewRef?.let { view ->
                view.requestLayout()
                view.videoSurfaceView?.requestLayout()
                view.invalidate()
            }
        }
        val pipListener = Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
            // Le SurfaceView interne ne se relayout pas toujours au changement
            // de taille PiP. Le premier cycle réagit immédiatement ; le second
            // attend que l'animation PiP ait atteint ses dimensions finales.
            playerViewRef?.let { view ->
                view.removeCallbacks(settledWindowRelayout)
                view.visibility = View.INVISIBLE
                view.requestLayout()
                view.post {
                    view.visibility = View.VISIBLE
                    view.requestLayout()
                    view.videoSurfaceView?.requestLayout()
                    view.postDelayed(settledWindowRelayout, PIP_SETTLED_RELAYOUT_DELAY_MS)
                }
            }
        }
        activity.addOnPictureInPictureModeChangedListener(pipListener)
        onDispose {
            playerViewRef?.removeCallbacks(settledWindowRelayout)
            activity.removeOnPictureInPictureModeChangedListener(pipListener)
        }
    }

    return isInPipMode
}

private const val PIP_SETTLED_RELAYOUT_DELAY_MS = 300L

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
