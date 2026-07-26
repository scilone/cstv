package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cstv.app.data.local.entity.CatalogSyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogSyncStateDao {

    @Query("SELECT * FROM catalog_sync_state")
    suspend fun getAll(): List<CatalogSyncStateEntity>

    @Query("SELECT * FROM catalog_sync_state")
    fun observeAll(): Flow<List<CatalogSyncStateEntity>>

    @Query("SELECT * FROM catalog_sync_state WHERE section = :section LIMIT 1")
    suspend fun getSection(section: String): CatalogSyncStateEntity?

    @Query("SELECT accountKey FROM catalog_sync_state LIMIT 1")
    suspend fun getAccountKey(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: CatalogSyncStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(states: List<CatalogSyncStateEntity>)

    @Query("UPDATE catalog_sync_state SET accountKey = :accountKey")
    suspend fun updateAccountKey(accountKey: String)

    @Query("DELETE FROM catalog_sync_state")
    suspend fun clear()

    /**
     * Un succès horodate la section et efface son dernier échec ; le compteur
     * d'éléments sert à distinguer une section vraiment peuplée d'une section
     * simplement tentée.
     */
    suspend fun markSuccess(section: String, accountKey: String, at: Long, itemCount: Int) {
        val current = getSection(section)
        upsert(
            (current ?: CatalogSyncStateEntity(section = section, accountKey = accountKey)).copy(
                accountKey = accountKey,
                lastSuccessAt = at,
                lastAttemptAt = at,
                lastFailureAt = 0L,
                lastFailureKind = null,
                itemCount = itemCount
            )
        )
    }

    /**
     * Un échec ne renouvelle jamais [CatalogSyncStateEntity.lastSuccessAt] :
     * c'est la garantie que la fraîcheur affichée reste celle d'une donnée
     * réellement obtenue.
     */
    suspend fun markFailure(section: String, accountKey: String, at: Long, kind: String) {
        val current = getSection(section)
        upsert(
            (current ?: CatalogSyncStateEntity(section = section, accountKey = accountKey)).copy(
                accountKey = accountKey,
                lastAttemptAt = at,
                lastFailureAt = at,
                lastFailureKind = kind
            )
        )
    }
}
