package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cstv.app.data.local.entity.DownloadedMediaEntity
import kotlinx.coroutines.flow.Flow

/**
 * T20: download joined to its media identity and current catalogue entry. `title`/`coverUrl`/
 * `containerExtension` come from `vod_streams` for a movie; from `series_episodes` +
 * `series_streams` for an episode (whose display title -- "Série — SxxEyy" -- and subtitle --
 * episode name -- are built by [com.cstv.app.data.repository.DownloadRepositoryImpl] from the raw
 * parts here via the existing pure `EpisodeLabel.format`, not duplicated as SQL string formatting).
 */
data class DownloadListRow(
    val mediaUid: Long,
    val providerId: Int,
    val kind: String,
    val status: String,
    val percent: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val createdAt: Long,
    val title: String?,
    val episodeTitle: String?,
    val coverUrl: String?,
    val containerExtension: String?,
    val seriesId: Int?,
    val seasonNum: Int?,
    val episodeNum: Int?,
)

/** T20 (§4.5) : projection minimale pour la réconciliation, cf. [DownloadDao.findOrphaned]. */
data class OrphanedDownloadRow(val mediaUid: Long, val kind: String, val providerId: Int)

private const val DOWNLOAD_LIST_QUERY = """
    SELECT dm.mediaUid AS mediaUid, r.providerId AS providerId, r.kind AS kind, dm.status AS status,
           dm.percent AS percent, dm.bytesDownloaded AS bytesDownloaded, dm.totalBytes AS totalBytes, dm.createdAt AS createdAt,
           s.name AS title, NULL AS episodeTitle, s.streamIcon AS coverUrl, s.containerExtension AS containerExtension,
           NULL AS seriesId, NULL AS seasonNum, NULL AS episodeNum
      FROM downloaded_media dm JOIN media_refs r ON r.mediaUid = dm.mediaUid
      JOIN vod_streams s ON s.streamId = r.providerId
     WHERE r.accountKey = :accountKey AND r.kind = 'movie'
    UNION ALL
    SELECT dm.mediaUid, r.providerId, r.kind, dm.status, dm.percent, dm.bytesDownloaded, dm.totalBytes, dm.createdAt,
           ss.name, e.title, e.movieImage, e.containerExtension, e.seriesId, e.seasonNum, e.episodeNum
      FROM downloaded_media dm JOIN media_refs r ON r.mediaUid = dm.mediaUid
      JOIN series_episodes e ON e.episodeId = r.providerId
      JOIN series_streams ss ON ss.seriesId = e.seriesId
     WHERE r.accountKey = :accountKey AND r.kind = 'episode'
     ORDER BY createdAt DESC
"""

@Dao
interface DownloadDao {

    // Observe la liste complète (écran Téléchargements + état des boutons dans
    // les détails). Réémet à chaque upsert de progression/statut ou changement du catalogue joint.
    @Query(DOWNLOAD_LIST_QUERY)
    fun observeAll(accountKey: String): Flow<List<DownloadListRow>>

    @Query(DOWNLOAD_LIST_QUERY)
    suspend fun getAll(accountKey: String): List<DownloadListRow>

    @Query(
        "SELECT dm.* FROM downloaded_media dm JOIN media_refs r ON r.mediaUid = dm.mediaUid " +
            "WHERE r.accountKey = :accountKey AND r.kind = :kind AND r.providerId = :providerId LIMIT 1"
    )
    suspend fun getByRef(accountKey: String, kind: String, providerId: Int): DownloadedMediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadedMediaEntity)

    @Query(
        "UPDATE downloaded_media SET status = :status, percent = :percent, bytesDownloaded = :bytesDownloaded, totalBytes = :totalBytes " +
            "WHERE mediaUid = (SELECT mediaUid FROM media_refs WHERE accountKey = :accountKey AND kind = :kind AND providerId = :providerId LIMIT 1)"
    )
    suspend fun updateProgress(accountKey: String, kind: String, providerId: Int, status: String, percent: Int, bytesDownloaded: Long, totalBytes: Long)

    @Query(
        "DELETE FROM downloaded_media WHERE mediaUid = (" +
            "SELECT mediaUid FROM media_refs WHERE accountKey = :accountKey AND kind = :kind AND providerId = :providerId LIMIT 1)"
    )
    suspend fun delete(accountKey: String, kind: String, providerId: Int)

    /** T20 (§4.5): réconciliation — le `(kind, providerId)` de l'identité est nécessaire pour
     *  reconstruire le `DownloadContentId` media3 (`CatalogReconciler`) avant suppression. */
    @Query(
        "SELECT dm.mediaUid AS mediaUid, r.kind AS kind, r.providerId AS providerId " +
            "FROM downloaded_media dm JOIN media_refs r ON r.mediaUid = dm.mediaUid " +
            "WHERE r.accountKey = :accountKey AND status != 'ORPHANED' AND (" +
            "(r.kind = 'movie' AND NOT EXISTS (SELECT 1 FROM vod_streams s WHERE s.streamId = r.providerId)) OR " +
            "(r.kind = 'episode' AND NOT EXISTS (SELECT 1 FROM series_episodes e WHERE e.episodeId = r.providerId)))"
    )
    suspend fun findOrphaned(accountKey: String): List<OrphanedDownloadRow>

    @Query("UPDATE downloaded_media SET status = 'ORPHANED' WHERE mediaUid = :mediaUid")
    suspend fun markOrphaned(mediaUid: Long)

    @Query("DELETE FROM downloaded_media WHERE mediaUid = :mediaUid")
    suspend fun deleteByMediaUid(mediaUid: Long)

    @Query("SELECT COALESCE(SUM(bytesDownloaded), 0) FROM downloaded_media")
    suspend fun getTotalBytesDownloaded(): Long
}
