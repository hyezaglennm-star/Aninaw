package com.aninaw.data.growth

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "growth")
data class GrowthEntity(
    @PrimaryKey val id: Int = 1,
    val points: Int = 0,
    val streakDays: Int = 0,
    val lastCompletedYmd: String? = null // yyyy-MM-dd
)