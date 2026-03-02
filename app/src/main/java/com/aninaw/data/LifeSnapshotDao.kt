package com.aninaw.data.lifesnapshot

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LifeSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LifeSnapshotEntity)

    @Query("SELECT * FROM life_snapshots WHERE id = 1 LIMIT 1")
    suspend fun getBaseline(): LifeSnapshotEntity?

    @Query("SELECT * FROM life_snapshots WHERE id = 2 LIMIT 1")
    suspend fun getCurrent(): LifeSnapshotEntity?

    @Query("SELECT * FROM life_snapshots ORDER BY epochDay ASC")
    suspend fun listAll(): List<LifeSnapshotEntity>
}