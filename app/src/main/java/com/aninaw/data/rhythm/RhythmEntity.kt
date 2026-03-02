//RhythmEntity
package com.aninaw.data.rhythm

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rhythms")
data class RhythmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phaseKey: String,      // e.g. "W61_64_MINIMAL_STRENGTH"
    val domain: String,        // "BODY" | "MIND" | "SOUL" | "ENV"
    val text: String           // the rhythm line
)
