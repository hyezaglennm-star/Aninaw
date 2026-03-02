//UntangleEntryEntity.kt
package com.aninaw.untangle

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "untangle_entries")
data class UntangleEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtMs: Long,
    val ymd: String,

    val category: String,
    val situation: String,
    val automaticThought: String,

    // store as compact strings so you don't fight Room converters yet
    val feelingsBefore: String,   // e.g. "Anxious:80,Rejected:60"
    val bodyBefore: String,       // e.g. "tight chest,tense shoulders"

    val alternativeThought: String,
    val balancedThought: String,

    val feelingsAfter: String,    // e.g. "Anxious:45,Rejected:30"
    val nextStep: String,

    // attach to Emotion system
    val overallEmotion: String    // e.g. "STEADY"
)
