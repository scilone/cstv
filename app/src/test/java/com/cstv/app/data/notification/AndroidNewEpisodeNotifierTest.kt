package com.cstv.app.data.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests des gardes métier F12 extraites en logique pure de
 * [AndroidNewEpisodeNotifier] (F12-R2). Le reste de la classe dépend de
 * `Context`/`NotificationManagerCompat`/`UiModeManager`, non mockables en
 * JVM sans Robolectric (absent du projet) : seule cette logique isolée est
 * couverte par un test automatisé, conformément à la stratégie de tests.
 */
class AndroidNewEpisodeNotifierTest {

    @Test
    fun neverShowsNotificationOnTv() {
        assertFalse(AndroidNewEpisodeNotifier.shouldShowNotification(isTvDevice = true, notificationsEnabled = true))
        assertFalse(AndroidNewEpisodeNotifier.shouldShowNotification(isTvDevice = true, notificationsEnabled = false))
    }

    @Test
    fun neverShowsNotificationWhenSystemNotificationsDisabled() {
        assertFalse(AndroidNewEpisodeNotifier.shouldShowNotification(isTvDevice = false, notificationsEnabled = false))
    }

    @Test
    fun showsNotificationOnMobileWithNotificationsEnabled() {
        assertTrue(AndroidNewEpisodeNotifier.shouldShowNotification(isTvDevice = false, notificationsEnabled = true))
    }

    @Test
    fun notificationIdIsStableForSameProfileAndSeries() {
        val a = AndroidNewEpisodeNotifier.buildNotificationId(profileId = 1, seriesId = 42)
        val b = AndroidNewEpisodeNotifier.buildNotificationId(profileId = 1, seriesId = 42)
        assertTrue(a == b)
    }

    @Test
    fun notificationIdIsUniquePerProfileAndSeriesPair() {
        val base = AndroidNewEpisodeNotifier.buildNotificationId(profileId = 1, seriesId = 42)
        val otherSeries = AndroidNewEpisodeNotifier.buildNotificationId(profileId = 1, seriesId = 43)
        val otherProfile = AndroidNewEpisodeNotifier.buildNotificationId(profileId = 2, seriesId = 42)

        assertNotEquals(base, otherSeries)
        assertNotEquals(base, otherProfile)
        assertNotEquals(otherSeries, otherProfile)
    }
}
