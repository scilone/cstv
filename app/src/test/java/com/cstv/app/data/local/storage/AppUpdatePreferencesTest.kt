package com.cstv.app.data.local.storage

import android.content.Context
import android.content.SharedPreferences
import com.cstv.app.domain.model.AppUpdateRelease
import com.cstv.app.domain.model.SemanticVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AppUpdatePreferencesTest {

    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    @Mock private lateinit var context: Context
    @Mock private lateinit var sharedPreferences: SharedPreferences
    @Mock private lateinit var editor: SharedPreferences.Editor

    private lateinit var preferences: AppUpdatePreferences

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(context.getSharedPreferences(any(), any())).thenReturn(sharedPreferences)
        whenever(sharedPreferences.edit()).thenReturn(editor)
        whenever(editor.putInt(any(), any())).thenReturn(editor)
        whenever(editor.putLong(any(), any())).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)
        whenever(editor.remove(any())).thenReturn(editor)
        preferences = AppUpdatePreferences(context)
    }

    @Test
    fun `no automatic check recorded yet returns null`() {
        whenever(sharedPreferences.getLong(eq("last_automatic_check_at"), any())).thenReturn(-1L)
        assertNull(preferences.getLastAutomaticCheckAt())
    }

    @Test
    fun `setLastAutomaticCheckAt persists value`() {
        preferences.setLastAutomaticCheckAt(123L)
        verify(editor).putLong("last_automatic_check_at", 123L)
        verify(editor).apply()
    }

    @Test
    fun `postpone persists version and deadline`() {
        preferences.setPostponed(17_800, 999L)
        verify(editor).putInt("postponed_version_code", 17_800)
        verify(editor).putLong("postponed_until", 999L)
    }

    @Test
    fun `ignored version code round trips`() {
        whenever(sharedPreferences.getInt(eq("ignored_version_code"), any())).thenReturn(17_800)
        assertEquals(17_800, preferences.getIgnoredVersionCode())
    }

    @Test
    fun `no ignored version returns null`() {
        whenever(sharedPreferences.getInt(eq("ignored_version_code"), any())).thenReturn(-1)
        assertNull(preferences.getIgnoredVersionCode())
    }

    @Test
    fun `known release round trips through preferences`() {
        val release = AppUpdateRelease(
            version = SemanticVersion(2, 0, 0),
            assetDownloadUrl = "https://github.com/scilone/cstv/releases/download/v2.0.0/app-release.apk",
            assetName = "app-release.apk",
            assetSizeBytes = 1_000L
        )
        preferences.saveKnownRelease(release)
        verify(editor).putInt("known_release_major", 2)
        verify(editor).putInt("known_release_minor", 0)
        verify(editor).putInt("known_release_patch", 0)
        verify(editor).putString("known_release_url", release.assetDownloadUrl)
        verify(editor).putString("known_release_asset_name", release.assetName)
        verify(editor).putLong("known_release_asset_size", 1_000L)
    }

    @Test
    fun `getKnownRelease reconstructs release from stored scalars`() {
        whenever(sharedPreferences.getInt(eq("known_release_major"), any())).thenReturn(2)
        whenever(sharedPreferences.getInt(eq("known_release_minor"), any())).thenReturn(0)
        whenever(sharedPreferences.getInt(eq("known_release_patch"), any())).thenReturn(0)
        whenever(sharedPreferences.getString(eq("known_release_url"), anyOrNull())).thenReturn("https://x/app.apk")
        whenever(sharedPreferences.getString(eq("known_release_asset_name"), anyOrNull())).thenReturn("app.apk")
        whenever(sharedPreferences.getLong(eq("known_release_asset_size"), any())).thenReturn(1_000L)

        val release = preferences.getKnownRelease()
        assertEquals(SemanticVersion(2, 0, 0), release?.version)
        assertEquals("app.apk", release?.assetName)
    }

    @Test
    fun `getKnownRelease absent returns null`() {
        whenever(sharedPreferences.getInt(eq("known_release_major"), any())).thenReturn(-1)
        assertNull(preferences.getKnownRelease())
    }

    @Test
    fun `clearKnownRelease removes every known-release key`() {
        preferences.clearKnownRelease()
        verify(editor).remove("known_release_major")
        verify(editor).remove("known_release_minor")
        verify(editor).remove("known_release_patch")
        verify(editor).remove("known_release_url")
        verify(editor).remove("known_release_asset_name")
        verify(editor).remove("known_release_asset_size")
    }
}
