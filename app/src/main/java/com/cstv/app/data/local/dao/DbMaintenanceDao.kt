package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cstv.app.data.local.entity.DbMaintenanceEntity

@Dao
interface DbMaintenanceDao {
    @Query("SELECT * FROM db_maintenance WHERE task = :task LIMIT 1")
    suspend fun get(task: String): DbMaintenanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun request(entry: DbMaintenanceEntity)

    @Query("DELETE FROM db_maintenance WHERE task = :task")
    suspend fun complete(task: String)
}
