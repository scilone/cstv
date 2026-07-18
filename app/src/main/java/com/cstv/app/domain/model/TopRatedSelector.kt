package com.cstv.app.domain.model

object TopRatedSelector {
    fun <T> selectTop10(
        items: List<T>,
        ratingExtractor: (T) -> String?,
        addedExtractor: (T) -> String?
    ): List<T> {
        data class ParsedItem(val item: T, val rating: Double, val added: Long)

        return items.map { item ->
            val ratingStr = ratingExtractor(item)
            val ratingVal = ratingStr?.toDoubleOrNull() ?: -1.0
            val addedStr = addedExtractor(item)
            val addedVal = addedStr?.toLongOrNull() ?: 0L
            ParsedItem(item, ratingVal, addedVal)
        }
        .filter { it.rating >= 8.0 }
        .sortedByDescending { it.added }
        .take(10)
        .map { it.item }
    }
}
