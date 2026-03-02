//UntangleDao.kt
package com.aninaw.untangle

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UntangleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: UntangleEntryEntity): Long

    @Query("SELECT * FROM untangle_entries ORDER BY createdAtMs DESC LIMIT :limit")
    suspend fun latest(limit: Int = 50): List<UntangleEntryEntity>

    @Query("SELECT * FROM untangle_entries WHERE ymd = :ymd ORDER BY createdAtMs DESC")
    suspend fun byDay(ymd: String): List<UntangleEntryEntity>
}
