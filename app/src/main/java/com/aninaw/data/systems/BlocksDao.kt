package com.aninaw.data.systems

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BlocksDao {

    @Query("SELECT * FROM system_blocks WHERE systemId = :systemId ORDER BY sortIndex ASC")
    fun observeBlocks(systemId: String): Flow<List<BlockEntity>>

    @Query("SELECT * FROM system_blocks WHERE systemId = :systemId ORDER BY sortIndex ASC")
    suspend fun getBlocksOnce(systemId: String): List<BlockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBlock(block: BlockEntity)

    @Delete
    suspend fun deleteBlock(block: BlockEntity)

    @Query("UPDATE system_blocks SET sortIndex = :sortIndex, updatedAt = :updatedAt WHERE id = :blockId")
    suspend fun updateSortIndex(blockId: String, sortIndex: Int, updatedAt: Long)

    @Query("SELECT MAX(sortIndex) FROM system_blocks WHERE systemId = :systemId")
    suspend fun getMaxSortIndex(systemId: String): Int?
}