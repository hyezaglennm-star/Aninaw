package com.aninaw.data.journal

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: JournalEntity)

    @Delete
    suspend fun delete(entry: JournalEntity)

    @Query("SELECT * FROM journal_entries WHERE date = :date ORDER BY timestamp DESC")
    fun getEntriesByDate(date: String): Flow<List<JournalEntity>>

    @Query("SELECT * FROM journal_entries ORDER BY timestamp DESC LIMIT 20")
    fun getRecentEntries(): Flow<List<JournalEntity>>

    @Query("SELECT COUNT(*) FROM journal_entries WHERE date = :date")
    suspend fun getCountForDate(date: String): Int

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): JournalEntity?

    @Query("SELECT mood, COUNT(*) as count FROM journal_entries WHERE mood IS NOT NULL GROUP BY mood")
    suspend fun getMoodCounts(): List<MoodCount>

    @Query("SELECT COUNT(*) FROM journal_entries")
    suspend fun getTotalEntries(): Int
}

data class MoodCount(
    val mood: String,
    val count: Int
)
