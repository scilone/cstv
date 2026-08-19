package com.cstv.app.domain.util

/**
 * Durée écoulée depuis un instant arbitraire, insensible à un changement de
 * l'horloge murale de l'appareil (`SystemClock.elapsedRealtime()` en
 * production). Utilisée par `ParentalPinStore` (F44) en complément de
 * [TimeProvider] pour limiter le contournement de la temporisation
 * anti-bruteforce par retour d'horloge — voir §8.3 du ticket F44.
 */
interface MonotonicClock {
    fun elapsedRealtimeMillis(): Long
}
