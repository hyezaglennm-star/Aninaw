package com.aninaw.data.calmhistory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CalmToolHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CalmToolHistoryEntity)

    @Query("SELECT * FROM calm_tool_history ORDER BY completedAt DESC")
    suspend fun getAllHistory(): List<CalmToolHistoryEntity>
}