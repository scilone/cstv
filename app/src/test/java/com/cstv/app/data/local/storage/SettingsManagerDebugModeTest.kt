package com.cstv.app.data.local.storage

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

class SettingsManagerDebugModeTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var sharedPreferences: SharedPreferences

    @Mock
    private lateinit var editor: SharedPreferences.Editor

    private lateinit var settingsManager: SettingsManager

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(context.getSharedPreferences(any(), any())).thenReturn(sharedPreferences)
        whenever(sharedPreferences.edit()).thenReturn(editor)
        whenever(editor.putBoolean(any(), any())).thenReturn(editor)
        
        settingsManager = SettingsManager(context)
    }

    @Test
    fun test_getDebugModeEnabled_default_returnsFalse() {
        whenever(sharedPreferences.getBoolean(eq("debug_mode_enabled"), any())).thenReturn(false)
        val enabled = settingsManager.getDebugModeEnabled()
        assertEquals(false, enabled)
    }

    @Test
    fun test_setDebugModeEnabled_persistsValue() {
        settingsManager.setDebugModeEnabled(true)
        verify(editor).putBoolean("debug_mode_enabled", true)
        verify(editor).apply()
    }

    @Test
    fun test_getDebugModeEnabled_returnsPersistedValue() {
        whenever(sharedPreferences.getBoolean(eq("debug_mode_enabled"), any())).thenReturn(true)
        val enabled = settingsManager.getDebugModeEnabled()
        assertEquals(true, enabled)
    }
}
