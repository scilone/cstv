package com.poc.iptvxtream.domain.model

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
}