package com.cstv.app.presentation.livetv

import androidx.compose.runtime.Immutable
import com.cstv.app.domain.model.FavoriteItem
import com.cstv.app.domain.model.LiveCategory
import com.cstv.app.domain.model.LiveEpgProgram
import com.cstv.app.domain.model.LiveStream

@Immutable
data class LiveStreamUiState(
    val stream: LiveStream,
    val currentProgram: LiveEpgProgram?
)

@Immutable
class LiveStreamList(items: List<LiveStreamUiState>) {
    val items: List<LiveStreamUiState> = items.toList()

    override fun equals(other: Any?): Boolean = other is LiveStreamList && items == other.items
    override fun hashCode(): Int = items.hashCode()
    override fun toString(): String = "LiveStreamList(items=$items)"
}

@Immutable
class LiveCategoryList(items: List<LiveCategory>) {
    val items: List<LiveCategory> = items.toList()

    override fun equals(other: Any?): Boolean = other is LiveCategoryList && items == other.items
    override fun hashCode(): Int = items.hashCode()
    override fun toString(): String = "LiveCategoryList(items=$items)"
}

@Immutable
class FavoriteList(items: List<FavoriteItem>) {
    val items: List<FavoriteItem> = items.toList()

    override fun equals(other: Any?): Boolean = other is FavoriteList && items == other.items
    override fun hashCode(): Int = items.hashCode()
    override fun toString(): String = "FavoriteList(items=$items)"
}

fun List<LiveStream>.toUiState(epgPrograms: Map<Int, LiveEpgProgram> = emptyMap()): LiveStreamList {
    return LiveStreamList(
        map { stream ->
            LiveStreamUiState(stream, epgPrograms[stream.streamId])
        }
    )
}

/**
 * Pure generator of stable, unique, and namespaced keys for the Live TV grid.
 * Fixes M1 and C2 issues of key instability, Paging load-around, and key collisions.
 */
object LiveTvGridKeyGenerator {
    fun generateKey(
        index: Int,
        automaticQualityMode: Boolean,
        streamIdAt: (Int) -> Int?
    ): String {
        val id = streamIdAt(index)
        return if (automaticQualityMode) {
            "stream_$id"
        } else {
            if (id != null) "stream_$id" else "placeholder_$index"
        }
    }
}
