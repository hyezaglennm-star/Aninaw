package com.aninaw.data.systems

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SystemsDao {

    @Query("SELECT * FROM systems ORDER BY updatedAt DESC")
    fun observeSystems(): Flow<List<SystemEntity>>

    @Query("SELECT * FROM systems ORDER BY updatedAt DESC")
    suspend fun getSystemsOnce(): List<SystemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSystem(system: SystemEntity)

    @Delete
    suspend fun deleteSystem(system: SystemEntity)

    @Query("SELECT * FROM systems WHERE id = :id LIMIT 1")
    suspend fun getSystem(id: String): SystemEntity?
}