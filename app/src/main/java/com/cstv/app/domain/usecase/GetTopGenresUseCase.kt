package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.GenreParser
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.VodRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetTopGenresUseCase @Inject constructor(
    private val vodRepository: VodRepository,
    private val seriesRepository: SeriesRepository
) {
    suspend operator fun invoke(): List<String> = withContext(Dispatchers.Default) {
        val vodStreams = try {
            vodRepository.getCachedVodStreams("all")
        } catch (e: Exception) {
            emptyList()
        }
        val seriesStreams = try {
            seriesRepository.getCachedSeriesStreams("all")
        } catch (e: Exception) {
            emptyList()
        }
        
        val parsedGenres = (vodStreams.mapNotNull { it.genre } + seriesStreams.mapNotNull { it.genre })
            .flatMap { GenreParser.parseGenres(it) }

        val normalizedToCount = mutableMapOf<String, Int>()
        val normalizedToOriginalCasingCounts = mutableMapOf<String, MutableMap<String, Int>>()

        for (genre in parsedGenres) {
            val norm = GenreParser.normalize(genre)
            normalizedToCount[norm] = (normalizedToCount[norm] ?: 0) + 1
            val casingCounts = normalizedToOriginalCasingCounts.getOrPut(norm) { mutableMapOf() }
            casingCounts[genre] = (casingCounts[genre] ?: 0) + 1
        }

        val topNormalized = normalizedToCount.entries
            .sortedByDescending { it.value }
            .take(20)
            .map { it.key }

        topNormalized.map { norm ->
            val casingMap = normalizedToOriginalCasingCounts[norm]
            casingMap?.maxByOrNull { it.value }?.key ?: norm
        }
    }
}
