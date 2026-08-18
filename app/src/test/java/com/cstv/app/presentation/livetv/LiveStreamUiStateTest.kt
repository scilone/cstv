package com.cstv.app.presentation.livetv

import com.cstv.app.domain.model.LiveEpgProgram
import com.cstv.app.domain.model.LiveStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveStreamUiStateTest {

    @Test
    fun `toUiState maps LiveStream list to LiveStreamList with correct EPG programs`() {
        // Given
        val stream1 = LiveStream(
            streamId = 1,
            name = "Stream 1",
            streamIcon = "icon1",
            epgChannelId = "epg1",
            num = 10,
            categoryId = "cat1"
        )
        val stream2 = LiveStream(
            streamId = 2,
            name = "Stream 2",
            streamIcon = "icon2",
            epgChannelId = "epg2",
            num = 20,
            categoryId = "cat2"
        )
        val streams = listOf(stream1, stream2)

        val now = System.currentTimeMillis() / 1000L
        val program1 = LiveEpgProgram(
            title = "Program 1",
            description = "Desc 1",
            startTimestamp = now,
            endTimestamp = now + 3600
        )
        val epgPrograms = mapOf(1 to program1)

        // When
        val uiStateList = streams.toUiState(epgPrograms)

        // Then
        assertEquals(2, uiStateList.items.size)
        
        assertEquals(stream1, uiStateList.items[0].stream)
        assertEquals(program1, uiStateList.items[0].currentProgram)

        assertEquals(stream2, uiStateList.items[1].stream)
        assertNull(uiStateList.items[1].currentProgram)
    }

    @Test
    fun `high volume conversion performance test with 3000 streams`() {
        // Given
        val streams = List(3500) { index ->
            LiveStream(
                streamId = index,
                name = "Stream $index",
                streamIcon = "icon$index",
                epgChannelId = "epg$index",
                num = index,
                categoryId = "cat${index % 10}"
            )
        }

        // Warm up the JVM JIT compiler to ensure accurate performance timing
        repeat(10) {
            streams.toUiState()
        }

        // When - measure the time of conversion on the JVM
        val start = System.nanoTime()
        val uiStateList = streams.toUiState()
        val durationMs = (System.nanoTime() - start) / 1_000_000L

        // Then
        assertEquals(3500, uiStateList.items.size)
        // High volume conversion must be very fast (typically < 15ms on any modern host after warm-up)
        assertTrue("Conversion took too long: ${durationMs}ms", durationMs < 15L)
    }

    @Test
    fun `epg decoupling ensures referential equality of streams inside list`() {
        // Given
        val streams = List(10) { index ->
            LiveStream(
                streamId = index,
                name = "Stream $index",
                streamIcon = "icon$index",
                epgChannelId = "epg$index",
                num = index,
                categoryId = "cat1"
            )
        }

        // When - convert streams without EPG to get the stable list
        val uiStateList1 = streams.toUiState()
        val uiStateList2 = streams.toUiState()

        // Then
        assertEquals(uiStateList1, uiStateList2)
        assertEquals(uiStateList1.items[0].stream, uiStateList2.items[0].stream)
        assertNull(uiStateList1.items[0].currentProgram)
        assertNull(uiStateList2.items[0].currentProgram)
    }

    @Test
    fun `grid key generator produces stable unique and namespaced keys`() {
        // Case 1: Automatic Quality Mode (uses streamId prefixed with stream_)
        val keyAuto = LiveTvGridKeyGenerator.generateKey(
            index = 2,
            automaticQualityMode = true,
            streamIdAt = { 42 }
        )
        assertEquals("stream_42", keyAuto)

        // Case 2: Paging Mode with loaded element (uses streamId prefixed with stream_)
        val keyPagedLoaded = LiveTvGridKeyGenerator.generateKey(
            index = 5,
            automaticQualityMode = false,
            streamIdAt = { 101 }
        )
        assertEquals("stream_101", keyPagedLoaded)

        // Case 3: Paging Mode with placeholder (uses index prefixed with placeholder_ to prevent collision)
        val keyPagedPlaceholder = LiveTvGridKeyGenerator.generateKey(
            index = 7,
            automaticQualityMode = false,
            streamIdAt = { null }
        )
        assertEquals("placeholder_7", keyPagedPlaceholder)
    }
}
