package com.aninaw.data.systems

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "system_blocks",
    foreignKeys = [
        ForeignKey(
            entity = SystemEntity::class,
            parentColumns = ["id"],
            childColumns = ["systemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("systemId")]
)
data class BlockEntity(
    @PrimaryKey val id: String,
    val systemId: String,
    val type: String,         // ACTION | HABIT | REMINDER | NOTE
    val title: String,
    val note: String?,
    val timeMinutes: Int?,    // optional (for ACTION)
    val remindAt: Long?,      // optional (for REMINDER)
    val sortIndex: Int,       // ordering
    val isChecked: Boolean,   // for ACTION/HABIT simple state
    val createdAt: Long,
    val updatedAt: Long
)