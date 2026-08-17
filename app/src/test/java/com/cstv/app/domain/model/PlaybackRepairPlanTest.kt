package com.cstv.app.domain.model

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class PlaybackRepairPlanTest {
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    private val gson = Gson()

    @Test
    fun trackFingerprint_jsonRoundTrip_preservesAllFields() {
        val fingerprint = TrackFingerprint(
            trackKind = TrackKind.AUDIO,
            language = "fra",
            mimeType = "audio/eac3",
            codecs = "ec-3",
            channelCount = 6,
            roleFlags = 0,
            label = "VFF"
        )

        val json = fingerprint.toJson(gson)
        val restored = TrackFingerprint.fromJson(gson, json)

        assertEquals(fingerprint, restored)
    }

    @Test
    fun trackFingerprint_jsonRoundTrip_nullableFieldsNull() {
        val fingerprint = TrackFingerprint(
            trackKind = TrackKind.VIDEO,
            language = null,
            mimeType = null,
            codecs = null,
            channelCount = null,
            roleFlags = 0,
            label = null
        )

        val restored = TrackFingerprint.fromJson(gson, fingerprint.toJson(gson))

        assertEquals(fingerprint, restored)
    }

    @Test
    fun trackFingerprint_fromJson_nullInput_returnsNull() {
        assertNull(TrackFingerprint.fromJson(gson, null))
    }

    @Test
    fun trackFingerprint_fromJson_malformedInput_returnsNull() {
        assertNull(TrackFingerprint.fromJson(gson, "{not json"))
    }

    @Test
    fun playbackRepairPlan_default_hasNoTrackAdjustments() {
        val default = PlaybackRepairPlan.DEFAULT

        assertEquals(DecoderStrategy.DEFAULT, default.decoderStrategy)
        assertNull(default.disabledTrack)
        assertNull(default.preferredAudio)
    }
}
