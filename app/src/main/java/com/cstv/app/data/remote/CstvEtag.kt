package com.cstv.app.data.remote

object CstvEtag {
    fun bare(value: String?): String? = value?.trim()?.let { raw ->
        val weak = raw.removePrefix("W/")
        weak.removePrefix("\"").removeSuffix("\"").ifBlank { null }
    }
    fun header(value: String?): String? = bare(value)?.let { "\"$it\"" }
}
