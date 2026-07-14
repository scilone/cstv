package com.poc.iptvxtream.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LiveEpgProgram(
    val title: String,
    val description: String?,
    val startTimestamp: Long, // Unix epoch seconds
    val endTimestamp: Long    // Unix epoch seconds
) {
    // Helper to calculate progress fraction (0f to 1f) at current time
    fun getProgressFraction(): Float {
        val now = System.currentTimeMillis() / 1000L
        if (now < startTimestamp) return 0f
        if (now > endTimestamp) return 1f
        val total = endTimestamp - startTimestamp
        if (total <= 0) return 0f
        return (now - startTimestamp).toFloat() / total.toFloat()
    }

    /** "HH:mm - HH:mm" dans le fuseau horaire de l'appareil (Phase 32). */
    fun formattedTimeRange(): String {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val start = formatter.format(Date(startTimestamp * 1000L))
        val end = formatter.format(Date(endTimestamp * 1000L))
        return "$start - $end"
    }
}
