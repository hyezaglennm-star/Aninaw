package com.aninaw.data.lifesnapshot

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "life_snapshots")
data class LifeSnapshotEntity(
    @PrimaryKey val id: Int,          // 1 = BASELINE, 2 = CURRENT (fixed)
    val epochDay: Long,
    val kind: String,                 // "BASELINE" or "CURRENT"
    val emotionalRegulation: Int,
    val habitAwareness: Int,
    val healthyRhythm: Int,
    val selfLeadership: Int,
    val reflectionTagsCsv: String? = null,
    val reflectionNote: String? = null
)