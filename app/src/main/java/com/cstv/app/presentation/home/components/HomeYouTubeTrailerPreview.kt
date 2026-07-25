package com.cstv.app.presentation.home.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.cstv.app.presentation.debug.DebugLog
import kotlinx.coroutines.delay

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
    posterUrl: String? = null,
    onRevealed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    androidx.compose.runtime.key(videoId) {
        var webView by remember { mutableStateOf<WebView?>(null) }
        // Le lecteur mobile YouTube affiche de gros boutons centraux (play/seek)
        // pendant ~3-4 s au démarrage, que `controls=0` ne supprime pas et que
        // l'overscan ne peut pas clipper (ils sont au centre). On garde donc le
        // poster par-dessus la WebView le temps qu'ils s'estompent, puis fondu.
        var revealed by remember(videoId) { mutableStateOf(false) }
        LaunchedEffect(videoId) {
            delay(REVEAL_DELAY_MS)
            revealed = true
            onRevealed()
        }
        val coverAlpha by animateFloatAsState(
            targetValue = if (revealed) 0f else 1f,
            animationSpec = tween(durationMillis = 500),
            label = "trailerCoverAlpha"
        )
        Box(modifier = Modifier.fillMaxSize()) {
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
                        // UA desktop : YouTube sert alors le lecteur desktop, qui
                        // respecte réellement controls=0 (pas de gros boutons centraux
                        // play/seek du lecteur mobile, non supprimables autrement).
                        userAgentString = DESKTOP_USER_AGENT
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

        // Poster de couverture : masque les boutons centraux du lecteur au
        // démarrage, puis disparaît en fondu une fois qu'ils se sont estompés.
        if (coverAlpha > 0f && posterUrl != null) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(coverAlpha)
            )
        }
        }
    }
}

// UA desktop pour obtenir le lecteur YouTube desktop (respecte controls=0).
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

// Court cover poster au démarrage : masque juste le flash de chargement/buffering
// avant que l'image vidéo n'apparaisse. Les contrôles sont déjà supprimés par l'UA
// desktop + controls=0, donc plus besoin d'attendre leur disparition.
private const val REVEAL_DELAY_MS = 2000L

// Referer externe (non-youtube) présenté à l'IFrame embed. YouTube exige un
// Referer valide depuis fin 2025 ; toute origine https externe convient pour une
// vidéo dont l'intégration n'est pas restreinte.
private const val REFERER_BASE_URL = "https://cstv.app"

// Débordement (px CSS) haut/bas de l'iframe pour clipper le chrome YouTube
// (titre en haut, contrôles en bas). ~72px couvre le bandeau de titre sur les
// tailles usuelles de l'aperçu tout en gardant le crop invisible sous le scrim.
private const val OVERSCAN_PX = 120

/**
 * Page wrapper minimale : un `<iframe>` YouTube plein écran, autoplay muet, sans
 * contrôles, en boucle (loop impose playlist=<id> pour une vidéo seule), inline.
 * `enablejsapi=1` permet le contrôle du son par postMessage.
 */
private fun buildWrapperHtml(videoId: String, muted: Boolean): String {
    val muteParam = if (muted) 1 else 0
    // '&' échappés en '&amp;' : garantit que TOUS les paramètres (dont controls=0)
    // sont conservés par le parseur HTML de la WebView.
    val src = "https://www.youtube.com/embed/$videoId" +
        "?autoplay=1&amp;mute=$muteParam&amp;controls=0&amp;playsinline=1&amp;rel=0&amp;fs=0" +
        "&amp;modestbranding=1&amp;iv_load_policy=3&amp;loop=1&amp;playlist=$videoId&amp;enablejsapi=1"
    // Rendu "cover" + overscan pour masquer TOUT le chrome YouTube que `controls=0`
    // ne suffit pas à enlever (titre/partage en haut, barre de contrôle en bas) :
    // - cover : l'iframe est dimensionnée en 16:9 sur la HAUTEUR du conteneur
    //   (via vh), donc la vidéo remplit la hauteur sans letterbox ; les côtés
    //   débordent et sont clippés (overflow:hidden), comme un poster en Crop.
    //   Sans cela, la vidéo est letterboxée et la barre de contrôle, ancrée au bas
    //   de la vidéo (au milieu du conteneur), reste visible.
    // - overscan : OVERSCAN_PX de débordement haut/bas place le chrome hors cadre.
    // pointer-events:none empêche toute interaction (la navigation fiche passe par
    // un overlay du carrousel).
    val h = "calc(100vh + ${OVERSCAN_PX * 2}px)"
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <style>
          html,body{margin:0;padding:0;height:100%;width:100%;background:#000;overflow:hidden}
          .wrap{position:fixed;top:0;left:0;right:0;bottom:0;overflow:hidden}
          iframe{
            position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);
            height:$h;
            width:calc($h * 16 / 9);
            min-width:calc(100vw + ${OVERSCAN_PX * 2}px);
            border:0;display:block;pointer-events:none;
          }
        </style>
        </head>
        <body>
        <div class="wrap">
        <iframe src="$src"
          allow="autoplay; encrypted-media; picture-in-picture"
          referrerpolicy="unsafe-url"
          allowfullscreen></iframe>
        </div>
        </body>
        </html>
    """.trimIndent()
}
