package com.poc.iptvxtream.domain.model

data class SearchResult(
    val liveResults: List<LiveStream> = emptyList(),
    val vodResults: List<VodStream> = emptyList(),
    val seriesResults: List<SeriesStream> = emptyList()
) {
    val isEmpty: Boolean
        get() = liveResults.isEmpty() && vodResults.isEmpty() && seriesResults.isEmpty()
}
