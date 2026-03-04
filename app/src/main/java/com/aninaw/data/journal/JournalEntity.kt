package com.aninaw.data.journal

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "journal_entries")
data class JournalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,             // yyyy-MM-dd
    val timestamp: Long,
    val type: String,             // MORNING, EVENING, QUICK, FREESTYLE
    val prompt: String,           // e.g. "What are you grateful for?"
    val content: String,
    val mood: String? = null,     // e.g. "Happy", "Sad"
    val tags: String? = null      // e.g. "Personal,Family"
)
