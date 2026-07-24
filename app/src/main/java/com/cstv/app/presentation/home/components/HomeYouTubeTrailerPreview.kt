package com.cstv.app.presentation.home.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.cstv.app.presentation.debug.DebugLog

/**
 * Aperçu trailer via l'IFrame YouTube chargée DIRECTEMENT comme page (origine
 * réelle youtube.com), à la manière d'un `<iframe src="youtube.com/embed/…">`
 * sur le web. On n'utilise plus `android-youtube-player` : cette lib injecte
 * l'IFrame API dans un document `loadDataWithBaseURL` à origine opaque, sans
 * Referer -> l'IFrame renvoie l'erreur 153 (« missing HTTP referer », affichée
 * UNKNOWN) depuis le durcissement YouTube de fin 2025. Charger l'URL embed en
 * vraie page https envoie un Referer valide et débloque la lecture.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun HomeYouTubeTrailerPreview(
    videoId: String,
    muted: Boolean,
    onPlaybackError: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.runtime.key(videoId) {
        var webView by remember { mutableStateOf<WebView?>(null) }
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webView = this
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                    with(settings) {
                        javaScriptEnabled = true
                        // Autorise l'autoplay muet sans geste utilisateur.
                        mediaPlaybackRequiresUserGesture = false
                        domStorageEnabled = true
                    }
                    // WebChromeClient requis pour la lecture vidéo HTML5.
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            DebugLog.log("F10Trailer", "webview onPageFinished")
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            // Ne remonte que l'échec du document principal (embed), pas
                            // celui d'une sous-ressource annexe.
                            if (request?.isForMainFrame == true) {
                                DebugLog.log("F10Trailer", "webview onReceivedError ${error?.errorCode} ${error?.description}")
                                onPlaybackError()
                            }
                        }
                    }
                    loadUrl(buildEmbedUrl(videoId, muted))
                }
            },
            update = { view ->
                // Bascule muet/son sans recharger : agit sur l'élément <video> de la page.
                val js = if (muted) {
                    "(function(){var v=document.querySelector('video');if(v)v.muted=true;})()"
                } else {
                    "(function(){var v=document.querySelector('video');if(v){v.muted=false;v.play();}})()"
                }
                view.evaluateJavascript(js, null)
            },
            modifier = Modifier.fillMaxSize()
        )
        DisposableEffect(Unit) {
            onDispose {
                webView?.apply {
                    loadUrl("about:blank")
                    stopLoading()
                    destroy()
                }
            }
        }
    }
}

/**
 * URL d'embed YouTube équivalente à l'iframe web : autoplay muet, sans contrôles,
 * en boucle (loop nécessite playlist=<id> pour une vidéo seule), inline sur mobile.
 */
private fun buildEmbedUrl(videoId: String, muted: Boolean): String {
    val muteParam = if (muted) 1 else 0
    return "https://www.youtube.com/embed/$videoId" +
        "?autoplay=1&mute=$muteParam&controls=0&playsinline=1&rel=0&fs=0" +
        "&modestbranding=1&iv_load_policy=3&loop=1&playlist=$videoId"
}
