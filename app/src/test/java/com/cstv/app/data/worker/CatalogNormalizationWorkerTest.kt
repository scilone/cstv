package com.cstv.app.data.worker

import com.cstv.app.data.local.entity.VodStreamEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogNormalizationWorkerTest {
    @Test
    fun `drain pages resumes from committed pages and is idempotent`() = runTest {
        val pending = mutableListOf(
            VodStreamEntity(1, "Film VF HD", null, null, null, "1", 0),
            VodStreamEntity(2, "Film MULTI 4K", null, null, null, "1", 0)
        )
        val committed = mutableListOf<VodStreamEntity>()

        CatalogNormalizationWorker.drainPages(
            load = { pending.take(1) },
            normalize = { it.map(CatalogNormalizationWorker::normalizeVodRow) },
            persist = { page ->
                committed += page
                pending.removeAll { raw -> page.any { it.streamId == raw.streamId } }
            }
        )

        assertEquals(2, committed.size)
        assertTrue(committed.all { it.linkKey.isNotBlank() })
        assertEquals(committed[0].linkKey, committed[1].linkKey)
        CatalogNormalizationWorker.drainPages(
            load = { pending.take(1) }, normalize = { it }, persist = { error("nothing should be persisted") }
        )
    }

    @Test
    fun `a malformed row falls back without aborting its committed page`() = runTest {
        val rows = listOf("valid", "broken")
        val normalized = CatalogNormalizationWorker.normalizeRows(
            rows,
            transform = { value -> if (value == "broken") error("bad row") else value.uppercase() },
            fallback = { value -> "fallback:$value" }
        )

        assertEquals(listOf("VALID", "fallback:broken"), normalized)
    }
}
