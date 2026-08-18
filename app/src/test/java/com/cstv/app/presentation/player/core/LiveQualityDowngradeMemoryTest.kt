package com.cstv.app.presentation.player.core

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

/**
 * Révision produit F40 du 2026-08-18 : un repli automatique reste actif d'un zapping à l'autre —
 * y compris après redémarrage de l'app (persisté en `SharedPreferences`, pas en mémoire process) —
 * jusqu'à confirmation explicite que la meilleure qualité refonctionne, ou jusqu'à expiration de
 * la fenêtre de rappel (au moins une journée).
 */
class LiveQualityDowngradeMemoryTest {
    @Mock private lateinit var context: Context
    @Mock private lateinit var prefs: SharedPreferences
    @Mock private lateinit var editor: SharedPreferences.Editor

    // Store en mémoire simulant le SharedPreferences réel, pour que get* lise ce que put* a écrit.
    private val ints = mutableMapOf<String, Int>()
    private val longs = mutableMapOf<String, Long>()

    private lateinit var memory: LiveQualityDowngradeMemory

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        ints.clear(); longs.clear()
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.putInt(any(), any())).thenAnswer { inv ->
            ints[inv.getArgument(0)] = inv.getArgument(1); editor
        }
        whenever(editor.putLong(any(), any())).thenAnswer { inv ->
            longs[inv.getArgument(0)] = inv.getArgument(1); editor
        }
        whenever(editor.remove(any())).thenAnswer { inv ->
            val key = inv.getArgument<String>(0); ints.remove(key); longs.remove(key); editor
        }
        whenever(editor.apply()).then { }
        whenever(prefs.getInt(any(), any())).thenAnswer { inv -> ints[inv.getArgument(0)] ?: inv.getArgument(1) }
        whenever(prefs.getLong(any(), any())).thenAnswer { inv -> longs[inv.getArgument(0)] ?: inv.getArgument(1) }
        memory = LiveQualityDowngradeMemory(context)
    }

    @Test
    fun `no memory means the top quality is probed`() {
        assertTrue(memory.shouldProbeTop("tf1", 0))
        assertNull(memory.rememberedStreamId("tf1", 0))
    }

    @Test
    fun `a recorded downgrade is remembered on the next zap`() {
        memory.recordDowngrade("tf1", streamId = 5, nowMs = 1_000)

        assertEquals(5, memory.rememberedStreamId("tf1", nowMs = 2_000))
        assertTrue("le repli mémorisé ne doit pas retenter le haut", !memory.shouldProbeTop("tf1", nowMs = 2_000))
    }

    @Test
    fun `confirming the top quality clears the memorized downgrade`() {
        memory.recordDowngrade("tf1", streamId = 5, nowMs = 1_000)

        memory.confirmTopHealthy("tf1")

        assertNull(memory.rememberedStreamId("tf1", nowMs = 2_000))
        assertTrue(memory.shouldProbeTop("tf1", nowMs = 2_000))
    }

    @Test
    fun `retour utilisateur 2026-08-18 - the recall window lasts at least one day`() {
        val oneDayMs = 24 * 60 * 60 * 1000L
        memory.recordDowngrade("tf1", streamId = 5, nowMs = 0)

        assertEquals("toujours mémorisé juste avant 24h", 5, memory.rememberedStreamId("tf1", nowMs = oneDayMs - 1))
        assertNull("expiré au-delà de 24h", memory.rememberedStreamId("tf1", nowMs = oneDayMs))
    }

    @Test
    fun `channels are tracked independently`() {
        memory.recordDowngrade("tf1", streamId = 5, nowMs = 0)

        assertNull(memory.rememberedStreamId("m6", nowMs = 0))
    }

    @Test
    fun `a blank link key is never recorded`() {
        memory.recordDowngrade("", streamId = 5, nowMs = 0)

        assertNull(memory.rememberedStreamId("", nowMs = 0))
    }
}
