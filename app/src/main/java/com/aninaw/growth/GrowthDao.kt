package com.aninaw.data.growth

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GrowthDao {
    @Query("SELECT * FROM growth WHERE id = 1")
    suspend fun get(): GrowthEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: GrowthEntity)
}