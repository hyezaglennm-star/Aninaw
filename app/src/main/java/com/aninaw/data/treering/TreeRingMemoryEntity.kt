    //TreeRingMemoryEntity.kt
    package com.aninaw.data.treering

    import androidx.room.Entity
    import androidx.room.PrimaryKey

    @Entity(tableName = "tree_ring_log")
    data class TreeRingMemoryEntity(

        @PrimaryKey(autoGenerate = true)
        val id: Long = 0L,

        val date: String,              // yyyy-MM-dd
        val timestamp: Long,           // exact time

        val type: String,              // "QUICK" or "FULL"

        val emotion: String?,
        val intensity: Float?,
        val capacity: String?,

        val note: String?,

        val payloadJson: String?       // full structured data
    )