package com.cstv.app.domain.model

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Parses the UTC ISO-8601 timestamp emitted by the CSTV API on minSdk 21 too. */
object CstvTimestampParser {
    fun parseMillis(value: String): Long = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }.parse(value)?.time ?: throw ParseException("Empty date", 0)
    } catch (error: ParseException) {
        throw IllegalArgumentException("Invalid CSTV timestamp", error)
    }
}
