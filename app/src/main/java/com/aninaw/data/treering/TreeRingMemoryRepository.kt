//TreeRingMemoryRepository.kt
package com.aninaw.data.treering

import com.aninaw.data.AninawDb
import java.time.LocalDate

class TreeRingMemoryRepository(private val db: AninawDb) {

    suspend fun logQuickCheckIn(
        date: LocalDate,
        emotion: String,
        intensity: Float,
        capacity: String,
        note: String?
    ) {
        val payload = """
            {
              "emotion":${json(emotion)},
              "intensity":${intensity},
              "capacity":${json(capacity)},
              "note":${json(note)}
            }
        """.trimIndent()

        db.treeRingMemoryDao().insert(
            TreeRingMemoryEntity(
                date = date.toString(),
                timestamp = System.currentTimeMillis(),
                type = "QUICK",
                emotion = emotion,
                intensity = intensity,
                capacity = capacity,
                note = note,
                payloadJson = payload
            )
        )
    }

    suspend fun logFullReflection(
        date: LocalDate,
        emotion: String,
        intensity: Float,
        capacity: String,
        payloadJson: String
    ) {
        db.treeRingMemoryDao().insert(
            TreeRingMemoryEntity(
                date = date.toString(),
                timestamp = System.currentTimeMillis(),
                type = "FULL",
                emotion = emotion,
                intensity = intensity,
                capacity = capacity,
                note = null,
                payloadJson = payloadJson
            )
        )
    }

    suspend fun getLogsForDay(date: LocalDate) =
        db.treeRingMemoryDao().getByDate(date.toString())

    suspend fun getRange(start: LocalDate, end: LocalDate): List<TreeRingMemoryEntity> {
        return db.treeRingMemoryDao().getRange(
            startIso = start.toString(),
            endIso = end.toString()
        )
    }

    private fun json(v: String?): String {
        if (v == null) return "null"
        return "\"" + v.replace("\"","\\\"") + "\""
    }
}