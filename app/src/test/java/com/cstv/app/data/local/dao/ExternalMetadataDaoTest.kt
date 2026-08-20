package com.cstv.app.data.local.dao

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F45-R12 : [isHigherPriority] porte la décision de promotion utilisée par
 * `ExternalMetadataDao.upsertRequestIfHigherPriority` (méthode `@Transaction` regroupant lecture de
 * priorité + upsert en une seule transaction Room, non mockable de façon fiable côté suspend
 * default method — voir historique). Elle seule doit décider qu'une entrée `DETAIL_OPEN` promeut
 * une entrée `MISSING_METADATA`/`NEW_IPTV_MEDIA` déjà en file, sans jamais laisser une priorité
 * égale ou inférieure l'écraser (contrat qui corrige la fenêtre de course de l'ancien
 * `SELECT` + `UPSERT` séparés dans `ExternalMetadataHydrationScheduler`).
 */
class ExternalMetadataDaoTest {

    @Test
    fun `nothing queued always promotes`() {
        assertTrue(isHigherPriority(existingPriority = null, newPriority = 1))
    }

    @Test
    fun `a strictly higher priority promotes`() {
        assertTrue(isHigherPriority(existingPriority = 1, newPriority = 3)) // MISSING_METADATA -> DETAIL_OPEN
    }

    @Test
    fun `an equal priority never overwrites the queued request`() {
        assertFalse(isHigherPriority(existingPriority = 3, newPriority = 3))
    }

    @Test
    fun `a lower priority never overwrites the queued request`() {
        assertFalse(isHigherPriority(existingPriority = 3, newPriority = 1)) // DETAIL_OPEN déjà en file, fond n'écrase pas
    }
}
