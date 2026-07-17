package com.poc.iptvxtream.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.poc.iptvxtream.data.local.entity.DownloadedMediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    // Observe la liste complète (écran Téléchargements + état des boutons dans
    // les détails). Réémet à chaque upsert de progression/statut.
    @Query("SELECT * FROM downloaded_media ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadedMediaEntity>>

    @Query("SELECT * FROM downloaded_media ORDER BY createdAt DESC")
    suspend fun getAll(): List<DownloadedMediaEntity>

    @Query("SELECT * FROM downloaded_media WHERE contentId = :contentId LIMIT 1")
    suspend fun getByContentId(contentId: String): DownloadedMediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadedMediaEntity)

    @Query("UPDATE downloaded_media SET status = :status, percent = :percent, bytesDownloaded = :bytesDownloaded, totalBytes = :totalBytes WHERE contentId = :contentId")
    suspend fun updateProgress(contentId: String, status: String, percent: Int, bytesDownloaded: Long, totalBytes: Long)

    @Query("DELETE FROM downloaded_media WHERE contentId = :contentId")
    suspend fun delete(contentId: String)

    @Query("SELECT COALESCE(SUM(bytesDownloaded), 0) FROM downloaded_media")
    suspend fun getTotalBytesDownloaded(): Long
}
