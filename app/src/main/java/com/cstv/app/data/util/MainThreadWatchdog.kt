package com.cstv.app.data.util

import android.os.Handler
import android.os.Looper
import com.cstv.app.di.IptvLog
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Diagnostic des freezes remontés (2026-08-18) : lenteurs et écran qui ne
 * réagit plus à la télécommande, sans crash ni ANR système enregistré (le
 * rapport diagnostic ne montre jamais rien à l'endroit du blocage). Un ANR
 * Android ne se déclenche qu'après ~5s d'input non traité côté système ; un
 * blocage plus court, ou qui se résout juste avant, ne laisse aucune trace.
 *
 * Ce watchdog poste un ping sur le thread principal toutes les [INTERVAL_MS]
 * depuis un thread dédié, hors thread principal. Si le ping met plus de
 * [THRESHOLD_MS] à revenir, le thread principal était occupé par autre chose
 * pendant ce temps : on logue la durée du blocage et un dump de sa pile pour
 * voir CE QUI le retenait (recomposition Compose, requête Room synchrone,
 * inflate, GC…), puis la durée totale une fois débloqué.
 *
 * Coût : un thread daemon quasi inactif (sleep la majorité du temps) et un
 * `Handler.post` par seconde — négligeable sur un appareil à 4 cœurs.
 */
object MainThreadWatchdog {

    private const val TAG = "WATCHDOG"
    private const val INTERVAL_MS = 1_000L
    private const val THRESHOLD_MS = 700L
    private const val POLL_MS = 200L
    private const val GIVE_UP_MS = 30_000L

    @Volatile
    private var started = false

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val sequence = AtomicLong(0)
    private val acked = AtomicLong(0)

    fun start() {
        if (started) return
        started = true
        thread(name = "main-thread-watchdog", isDaemon = true, priority = Thread.MIN_PRIORITY) {
            while (true) {
                try {
                    Thread.sleep(INTERVAL_MS)
                } catch (interrupted: InterruptedException) {
                    return@thread
                }
                pingOnce()
            }
        }
    }

    private fun pingOnce() {
        val id = sequence.incrementAndGet()
        val sentAt = System.currentTimeMillis()
        mainHandler.post { acked.set(id) }

        var waited = 0L
        var reportedBlock = false
        while (acked.get() < id) {
            Thread.sleep(POLL_MS)
            waited += POLL_MS
            if (waited >= THRESHOLD_MS && !reportedBlock) {
                reportedBlock = true
                val stack = Looper.getMainLooper().thread.stackTrace
                    .joinToString("\n") { "    at $it" }
                IptvLog.w(TAG, "Thread principal bloqué depuis ${waited}ms :\n$stack")
            }
            // Vrai deadlock (jamais débloqué) : arrête de sonder ce ping et
            // repart sur le suivant plutôt que de tourner indéfiniment.
            if (waited > GIVE_UP_MS) return
        }
        if (reportedBlock) {
            val total = System.currentTimeMillis() - sentAt
            IptvLog.w(TAG, "Thread principal débloqué après ${total}ms")
        }
    }
}
