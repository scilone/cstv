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
 * Aperçu trailer répliquant EXACTEMENT une intégration web :
 * une page wrapper (servie depuis un baseUrl https réel, non-youtube) contenant
 * un `<iframe src="youtube.com/embed/…">`. C'est la combinaison qui manquait :
 * l'IFrame API de `android-youtube-player` (abandonnée ici) tournait dans un
 * document à origine opaque -> pas de Referer -> erreur 153 ; charger l'URL embed
 * en navigation top-level est aussi refusé par YouTube (« erreur de configuration
 * du lecteur »). Ici l'iframe émet un `Referer: <baseUrl>` externe valide, comme
 * un `<iframe>` sur un vrai site, ce qui débloque la lecture.
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
                            if (request?.isForMainFrame == true) {
                                DebugLog.log("F10Trailer", "webview onReceivedError ${error?.errorCode} ${error?.description}")
                                onPlaybackError()
                            }
                        }
                    }
                    loadDataWithBaseURL(REFERER_BASE_URL, buildWrapperHtml(videoId, muted), "text/html", "utf-8", null)
                }
            },
            update = { view ->
                // Bascule muet/son via l'IFrame API (postMessage vers l'iframe embed).
                val func = if (muted) "mute" else "unMute"
                view.evaluateJavascript(
                    "(function(){var f=document.querySelector('iframe');" +
                        "if(f&&f.contentWindow){f.contentWindow.postMessage(" +
                        "JSON.stringify({event:'command',func:'$func',args:[]}),'*');}})()",
                    null
                )
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

// Referer externe (non-youtube) présenté à l'IFrame embed. YouTube exige un
// Referer valide depuis fin 2025 ; toute origine https externe convient pour une
// vidéo dont l'intégration n'est pas restreinte.
private const val REFERER_BASE_URL = "https://cstv.app"

/**
 * Page wrapper minimale : un `<iframe>` YouTube plein écran, autoplay muet, sans
 * contrôles, en boucle (loop impose playlist=<id> pour une vidéo seule), inline.
 * `enablejsapi=1` permet le contrôle du son par postMessage.
 */
private fun buildWrapperHtml(videoId: String, muted: Boolean): String {
    val muteParam = if (muted) 1 else 0
    val src = "https://www.youtube.com/embed/$videoId" +
        "?autoplay=1&mute=$muteParam&controls=0&playsinline=1&rel=0&fs=0" +
        "&modestbranding=1&iv_load_policy=3&loop=1&playlist=$videoId&enablejsapi=1"
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <style>html,body{margin:0;padding:0;height:100%;width:100%;background:#000;overflow:hidden}iframe{border:0;width:100%;height:100%;display:block}</style>
        </head>
        <body>
        <iframe src="$src"
          allow="autoplay; encrypted-media; picture-in-picture"
          referrerpolicy="unsafe-url"
          allowfullscreen></iframe>
        </body>
        </html>
    """.trimIndent()
}
