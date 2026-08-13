package com.cstv.app.data.sync

import com.cstv.app.data.local.dao.CategoryRefDao
import com.cstv.app.data.local.dao.MediaRefDao
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

/**
 * T20-6 : le rattachement délègue intégralement à `UPDATE ... WHERE
 * accountKey = ''` (déjà idempotent en base) — ces tests couvrent la seule
 * logique propre au binder : ne jamais appeler la mise à jour avec une clé
 * vide/blanche, appeler les deux DAO sinon.
 */
class MediaRefAccountBinderTest {
    @get:Rule val globalTimeout: Timeout = Timeout.seconds(60)

    @Mock private lateinit var mediaRefDao: MediaRefDao
    @Mock private lateinit var categoryRefDao: CategoryRefDao

    private lateinit var binder: MediaRefAccountBinder

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        binder = MediaRefAccountBinder(mediaRefDao, categoryRefDao)
    }

    @Test
    fun `binding to a real account key rebinds both identity tables`() = runTest {
        binder.bindTo("account-key")

        verify(mediaRefDao).bindUnassigned("account-key")
        verify(categoryRefDao).bindUnassigned("account-key")
    }

    @Test
    fun `binding to a blank key touches nothing -- no accountKey is ever invented`() = runTest {
        binder.bindTo("")

        verifyNoInteractions(mediaRefDao)
        verifyNoInteractions(categoryRefDao)
    }

    @Test
    fun `binding to a whitespace-only key is treated the same as empty`() = runTest {
        binder.bindTo("   ")

        verify(mediaRefDao, never()).bindUnassigned(org.mockito.kotlin.any())
        verify(categoryRefDao, never()).bindUnassigned(org.mockito.kotlin.any())
    }
}
