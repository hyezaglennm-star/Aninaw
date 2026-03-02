package com.aninaw.data.systems

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "systems")
data class SystemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String, // emoji or small label like "🌿"
    val scheduleType: String, // DAILY | SPECIFIC_DAYS | FLEXIBLE
    val daysMask: Int,        // only used when SPECIFIC_DAYS, else 0
    val createdAt: Long,
    val updatedAt: Long
)