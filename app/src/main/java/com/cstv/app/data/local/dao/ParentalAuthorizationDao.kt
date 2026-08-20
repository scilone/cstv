package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cstv.app.data.local.entity.ParentalMediaAuthorizationEntity

/** F45 : autorisation joignant uniquement son identité (aucune métadonnée catalogue nécessaire). */
data class ParentalAuthorizationRow(val providerId: Int, val kind: String, val grantedAt: Long)

@Dao
interface ParentalAuthorizationDao {
    @Query(
        "SELECT COUNT(*) > 0 FROM parental_media_authorizations pa JOIN media_refs r ON r.mediaUid = pa.mediaUid " +
            "WHERE pa.profileId = :profileId AND r.accountKey = :accountKey AND r.kind = :kind AND r.providerId = :providerId"
    )
    suspend fun isAuthorized(profileId: Int, accountKey: String, kind: String, providerId: Int): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ParentalMediaAuthorizationEntity)

    @Query("DELETE FROM parental_media_authorizations WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Int)

    /** F45 (§4.6, cloud sync) : projection sans métadonnée catalogue. */
    @Query(
        "SELECT r.providerId AS providerId, r.kind AS kind, pa.grantedAt AS grantedAt FROM parental_media_authorizations pa " +
            "JOIN media_refs r ON r.mediaUid = pa.mediaUid WHERE pa.profileId = :profileId AND r.accountKey = :accountKey"
    )
    suspend fun wireRows(profileId: Int, accountKey: String): List<ParentalAuthorizationRow>
}
