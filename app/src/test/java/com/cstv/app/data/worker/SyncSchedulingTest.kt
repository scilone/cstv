package com.cstv.app.data.worker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SyncSchedulingTest {

    private fun calendarAt(hour: Int, minute: Int, second: Int = 0): Calendar {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.JANUARY, 15, hour, minute, second)
            set(Calendar.MILLISECOND, 0)
        }
    }

    @Test
    fun test_initialDelayMillis_beforeTargetHour_targetsToday() {
        val now = calendarAt(3, 0) // 03:00, target 06:00
        val delay = SyncScheduling.initialDelayMillis(now, targetHour = 6, targetMinute = 0)

        assertEquals(3 * 60 * 60 * 1000L, delay) // 3h jusqu'à 06:00 aujourd'hui
    }

    @Test
    fun test_initialDelayMillis_afterTargetHour_targetsTomorrow() {
        val now = calendarAt(8, 0) // 08:00, target 06:00 déjà passée
        val delay = SyncScheduling.initialDelayMillis(now, targetHour = 6, targetMinute = 0)

        assertEquals(22 * 60 * 60 * 1000L, delay) // 22h jusqu'à 06:00 demain
    }

    @Test
    fun test_initialDelayMillis_exactlyAtTargetHour_targetsTomorrow() {
        // Pile à l'heure cible : ne redéclenche pas immédiatement, vise le lendemain.
        val now = calendarAt(6, 0)
        val delay = SyncScheduling.initialDelayMillis(now, targetHour = 6, targetMinute = 0)

        assertEquals(24 * 60 * 60 * 1000L, delay)
    }

    @Test
    fun test_initialDelayMillis_withMinutes_beforeTarget() {
        val now = calendarAt(5, 45) // target 06:15
        val delay = SyncScheduling.initialDelayMillis(now, targetHour = 6, targetMinute = 15)

        assertEquals(30 * 60 * 1000L, delay)
    }

    @Test
    fun test_initialDelayMillis_usesDefaultSixAm() {
        val now = calendarAt(0, 0)
        val delay = SyncScheduling.initialDelayMillis(now)

        assertEquals(6 * 60 * 60 * 1000L, delay)
    }
}
