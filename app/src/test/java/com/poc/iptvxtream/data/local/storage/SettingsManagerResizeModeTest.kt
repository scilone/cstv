package com.poc.iptvxtream.data.local.storage

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

class SettingsManagerResizeModeTest {

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
        whenever(editor.putString(any(), any())).thenReturn(editor)
        
        settingsManager = SettingsManager(context)
    }

    @Test
    fun test_getResizeMode_default_returnsFit() {
        whenever(sharedPreferences.getString(eq("player_resize_mode"), any())).thenReturn(null)
        val mode = settingsManager.getResizeMode()
        assertEquals(ResizeMode.FIT, mode)
    }

    @Test
    fun test_setResizeMode_persistsName() {
        settingsManager.setResizeMode(ResizeMode.ZOOM)
        verify(editor).putString("player_resize_mode", "ZOOM")
        verify(editor).apply()
    }

    @Test
    fun test_getResizeMode_returnsPersistedValue() {
        whenever(sharedPreferences.getString(eq("player_resize_mode"), any())).thenReturn("FILL")
        val mode = settingsManager.getResizeMode()
        assertEquals(ResizeMode.FILL, mode)
    }

    @Test
    fun test_getResizeMode_fallbackOnException_returnsFit() {
        whenever(sharedPreferences.getString(eq("player_resize_mode"), any())).thenReturn("INVALID_MODE")
        val mode = settingsManager.getResizeMode()
        assertEquals(ResizeMode.FIT, mode)
    }
}
