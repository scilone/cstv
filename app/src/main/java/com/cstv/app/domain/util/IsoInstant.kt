package com.cstv.app.domain.util

import java.util.Calendar
import java.util.TimeZone

/**
 * F45-R5 : parseur ISO-8601 écrit à la main plutôt que `java.time` (indisponible sous API 26 sans
 * désucrage — absent de ce projet, `minSdk = 21`, box Android TV anciennes incluses) ou
 * `SimpleDateFormat` (le pattern d'offset à deux-points `XXX` exige lui aussi l'API 24+). Le
 * format en entrée est fixe et connu : `DateTimeImmutable::ATOM` côté backend PHP
 * (`cache.updatedAt`/`cache.refreshAfter`, §8.7), toujours `yyyy-MM-ddTHH:mm:ss±HH:MM` (ou
 * suffixe `Z`) — pas un parseur ISO-8601 général.
 */
object IsoInstant {
    fun parseMillis(iso: String?): Long? {
        if (iso == null || iso.length < 19) return null
        return try {
            val year = iso.substring(0, 4).toInt()
            val month = iso.substring(5, 7).toInt()
            val day = iso.substring(8, 10).toInt()
            val hour = iso.substring(11, 13).toInt()
            val minute = iso.substring(14, 16).toInt()
            val second = iso.substring(17, 19).toInt()
            val offsetMinutes = parseOffsetMinutes(iso.substring(19))
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.clear()
            calendar.set(year, month - 1, day, hour, minute, second)
            calendar.timeInMillis - offsetMinutes * 60_000L
        } catch (error: NumberFormatException) {
            null
        } catch (error: IndexOutOfBoundsException) {
            null
        }
    }

    private fun parseOffsetMinutes(offset: String): Int {
        if (offset.isEmpty() || offset == "Z") return 0
        val sign = if (offset[0] == '-') -1 else 1
        val digits = offset.drop(1).replace(":", "")
        if (digits.length < 4) return 0
        val hours = digits.substring(0, 2).toInt()
        val minutes = digits.substring(2, 4).toInt()
        return sign * (hours * 60 + minutes)
    }
}
