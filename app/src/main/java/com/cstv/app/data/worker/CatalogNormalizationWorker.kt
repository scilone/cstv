package com.cstv.app.data.worker

import android.content.Context
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cstv.app.data.local.db.AppDatabase
import com.cstv.app.data.local.entity.LiveStreamEntity
import com.cstv.app.data.local.entity.SeriesStreamEntity
import com.cstv.app.data.local.entity.VodStreamEntity
import com.cstv.app.data.local.entity.withParsedTitle
import com.cstv.app.domain.model.MediaTitleKind
import com.cstv.app.domain.model.MediaTitleParser
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.cstv.app.di.IptvLog
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * T21 post-migration backfill. `linkKey = ''` is the persisted progress state:
 * committed pages are skipped after a process death, while a partial page is safely retried.
 */
class CatalogNormalizationWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CatalogNormalizationWorkerEntryPoint {
        fun database(): AppDatabase
    }

    override suspend fun doWork(): Result = try {
        val database = EntryPointAccessors.fromApplication(
            applicationContext,
            CatalogNormalizationWorkerEntryPoint::class.java
        ).database()
        normalizeLive(database)
        normalizeVod(database)
        normalizeSeries(database)
        Result.success()
    } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        IptvLog.e("T21", "Rattrapage local des titres impossible", exception)
        Result.retry()
    }

    private suspend fun normalizeLive(database: AppDatabase) {
        drainPages(
            load = { database.liveTvDao().getUnnormalizedStreams(PAGE_SIZE) },
            normalize = { normalizeRows(it, ::normalizeLiveRow, ::fallbackLiveRow) },
            persist = { rows -> database.withTransaction { rows.forEach { row ->
                database.liveTvDao().applyNormalization(row.streamId, row.cleanTitle, row.linkKey, row.languageTag, row.languageRaw, row.qualityTag, row.qualityRaw)
            } } }
        )
    }

    private suspend fun normalizeVod(database: AppDatabase) {
        drainPages(
            load = { database.vodDao().getUnnormalizedStreams(PAGE_SIZE) },
            normalize = { normalizeRows(it, ::normalizeVodRow, ::fallbackVodRow) },
            persist = { rows -> database.withTransaction { rows.forEach { row ->
                database.vodDao().applyNormalization(row.streamId, row.cleanTitle, row.linkKey, row.languageTag, row.languageRaw, row.qualityTag, row.qualityRaw)
            } } }
        )
    }

    private suspend fun normalizeSeries(database: AppDatabase) {
        drainPages(
            load = { database.seriesDao().getUnnormalizedStreams(PAGE_SIZE) },
            normalize = { normalizeRows(it, ::normalizeSeriesRow, ::fallbackSeriesRow) },
            persist = { rows -> database.withTransaction { rows.forEach { row ->
                database.seriesDao().applyNormalization(row.seriesId, row.cleanTitle, row.linkKey, row.languageTag, row.languageRaw, row.qualityTag, row.qualityRaw)
            } } }
        )
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "catalog_title_normalization"
        internal const val PAGE_SIZE = 500

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<CatalogNormalizationWorker>()
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        internal fun normalizeLiveRow(row: LiveStreamEntity): LiveStreamEntity = row.withParsedTitle(
            MediaTitleParser.parse(row.name, MediaTitleKind.LIVE, providerId = row.streamId)
        )

        internal fun normalizeVodRow(row: VodStreamEntity): VodStreamEntity = row.withParsedTitle(
            MediaTitleParser.parse(row.name, MediaTitleKind.VOD, row.releaseYear, row.streamId)
        )

        internal fun normalizeSeriesRow(row: SeriesStreamEntity): SeriesStreamEntity = row.withParsedTitle(
            MediaTitleParser.parse(row.name, MediaTitleKind.SERIES, row.releaseYear, row.seriesId)
        )

        internal fun <T> normalizeRows(
            rows: List<T>,
            transform: (T) -> T,
            fallback: (T) -> T
        ): List<T> = rows.map { row -> runCatching { transform(row) }.getOrElse { fallback(row) } }

        private fun fallbackLiveRow(row: LiveStreamEntity): LiveStreamEntity = row.copy(
            cleanTitle = row.name, linkKey = "invalid:live:${row.streamId}",
            languageTag = null, languageRaw = null, qualityTag = null, qualityRaw = null
        )

        private fun fallbackVodRow(row: VodStreamEntity): VodStreamEntity = row.copy(
            cleanTitle = row.name, linkKey = "invalid:vod:${row.streamId}",
            languageTag = null, languageRaw = null, qualityTag = null, qualityRaw = null
        )

        private fun fallbackSeriesRow(row: SeriesStreamEntity): SeriesStreamEntity = row.copy(
            cleanTitle = row.name, linkKey = "invalid:series:${row.seriesId}",
            languageTag = null, languageRaw = null, qualityTag = null, qualityRaw = null
        )

        /** Isolated loop so interruption/resume is testable without Android WorkManager. */
        internal suspend fun <T> drainPages(
            load: suspend () -> List<T>,
            normalize: suspend (List<T>) -> List<T>,
            persist: suspend (List<T>) -> Unit
        ) {
            while (true) {
                val page = load()
                if (page.isEmpty()) return
                persist(normalize(page))
            }
        }
    }
}
