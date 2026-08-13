package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "db_maintenance")
data class DbMaintenanceEntity(@PrimaryKey val task: String, val requestedAt: Long)
