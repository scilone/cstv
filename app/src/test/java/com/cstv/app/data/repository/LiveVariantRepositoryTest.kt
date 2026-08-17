package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.LiveTvDao
import com.cstv.app.data.local.entity.LiveStreamEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LiveVariantRepositoryTest {
    private fun entity(id: Int, key: String = "tf1", quality: String? = "HD", num: Int = id) =
        LiveStreamEntity(id, "TF1", null, null, num, "1", 0L, linkKey = key, qualityTag = quality)

    @Test fun `resolves variants from stream id even when caller had no link key`() = runBlocking {
        val dao: LiveTvDao = mock()
        whenever(dao.getStreamById(10)).thenReturn(entity(10))
        whenever(dao.getStreamsByLinkKey("tf1")).thenReturn(listOf(entity(2, quality = "SD"), entity(1, quality = "HD")))
        val variants = LiveVariantRepository(dao).variantsFor(10)
        assertEquals(listOf(1, 2), variants.map { it.stream.streamId })
    }

    @Test fun `blank database key never creates a variant group`() = runBlocking {
        val dao: LiveTvDao = mock()
        whenever(dao.getStreamById(10)).thenReturn(entity(10, key = ""))
        assertTrue(LiveVariantRepository(dao).variantsFor(10).isEmpty())
    }
}
