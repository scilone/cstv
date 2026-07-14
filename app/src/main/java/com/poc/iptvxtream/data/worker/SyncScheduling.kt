package com.poc.iptvxtream.data.worker

import java.util.Calendar

/**
 * Calcule le délai initial avant la prochaine occurrence d'une heure fixe
 * (Phase 22), pour que le rafraîchissement automatique en arrière-plan se
 * déclenche à heure fixe (par défaut 6h du matin) plutôt qu'à une heure
 * relative au moment où l'utilisateur active le réglage. Les exécutions
 * suivantes du PeriodicWorkRequest héritent de cette heure de départ.
 */
object SyncScheduling {
    const val DEFAULT_HOUR = 6
    const val DEFAULT_MINUTE = 0

    /**
     * @param now instant de référence (permet un test déterministe)
     * @return délai en millisecondes jusqu'à la prochaine occurrence de
     * targetHour:targetMinute (aujourd'hui si pas encore passée, sinon demain).
     */
    fun initialDelayMillis(
        now: Calendar,
        targetHour: Int = DEFAULT_HOUR,
        targetMinute: Int = DEFAULT_MINUTE
    ): Long {
        val target = now.clone() as Calendar
        target.set(Calendar.HOUR_OF_DAY, targetHour)
        target.set(Calendar.MINUTE, targetMinute)
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)
        if (!target.after(now)) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
