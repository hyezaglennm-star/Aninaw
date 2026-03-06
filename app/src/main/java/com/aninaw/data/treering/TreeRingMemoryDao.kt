//TreeRingMemoryDao.kt
package com.aninaw.data.treering

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TreeRingMemoryDao {

    @Insert
    suspend fun insert(entry: TreeRingMemoryEntity)

    @Query("""
        SELECT * FROM tree_ring_log 
        WHERE date BETWEEN :startIso AND :endIso 
        ORDER BY timestamp ASC
    """)
    suspend fun getRange(startIso: String, endIso: String): List<TreeRingMemoryEntity>

    @Query("""
        SELECT * FROM tree_ring_log 
        WHERE date = :iso 
        ORDER BY timestamp ASC
    """)
    suspend fun getByDate(iso: String): List<TreeRingMemoryEntity>

    @Query("SELECT * FROM tree_ring_log ORDER BY timestamp DESC LIMIT 1")
    fun getLatestLog(): kotlinx.coroutines.flow.Flow<TreeRingMemoryEntity?>
}