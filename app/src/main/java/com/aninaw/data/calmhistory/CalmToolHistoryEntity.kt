package com.aninaw.data.calmhistory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calm_tool_history")
data class CalmToolHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val toolType: String,
    val toolTitle: String,
    val completedAt: Long,
    val durationSeconds: Int?,
    val completionState: String
)