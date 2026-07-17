package com.poc.iptvxtream.domain.model

object TopRatedSelector {
    fun <T> selectTop10(
        items: List<T>,
        ratingExtractor: (T) -> String?,
        addedExtractor: (T) -> String?
    ): List<T> {
        data class ParsedItem(val item: T, val rating: Double, val added: Long)

        val parsedItems = items.map { item ->
            val ratingStr = ratingExtractor(item)
            val ratingVal = ratingStr?.toDoubleOrNull() ?: -1.0
            val addedStr = addedExtractor(item)
            val addedVal = addedStr?.toLongOrNull() ?: 0L
            ParsedItem(item, ratingVal, addedVal)
        }

        val result = mutableListOf<ParsedItem>()

        // Tier 1: rating > 8.0, sorted by added desc
        val tier1 = parsedItems.filter { it.rating > 8.0 }.sortedByDescending { it.added }
        result.addAll(tier1.take(10))

        // Tier 2: rating > 7.0 and <= 8.0, sorted by added desc
        if (result.size < 10) {
            val tier2 = parsedItems.filter { it.rating > 7.0 && it.rating <= 8.0 }.sortedByDescending { it.added }
            result.addAll(tier2.take(10 - result.size))
        }

        // Tier 3: rating > 6.0 and <= 7.0, sorted by added desc
        if (result.size < 10) {
            val tier3 = parsedItems.filter { it.rating > 6.0 && it.rating <= 7.0 }.sortedByDescending { it.added }
            result.addAll(tier3.take(10 - result.size))
        }

        // Tier 4: rating > 5.0 and <= 6.0, sorted by added desc
        if (result.size < 10) {
            val tier4 = parsedItems.filter { it.rating > 5.0 && it.rating <= 6.0 }.sortedByDescending { it.added }
            result.addAll(tier4.take(10 - result.size))
        }

        // Tier 5: rating > 0.0 and <= 5.0, sorted by added desc
        if (result.size < 10) {
            val tier5 = parsedItems.filter { it.rating > 0.0 && it.rating <= 5.0 }.sortedByDescending { it.added }
            result.addAll(tier5.take(10 - result.size))
        }

        // Tier 6: remaining items, sorted by added desc
        if (result.size < 10) {
            val selectedItems = result.map { it.item }.toSet()
            val tier6 = parsedItems.filter { it.item !in selectedItems }.sortedByDescending { it.added }
            result.addAll(tier6.take(10 - result.size))
        }

        return result.map { it.item }
    }
}
