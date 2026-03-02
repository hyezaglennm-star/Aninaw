//RhythmDao.kt
package com.aninaw.data.rhythm

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RhythmDao {

    @Query("SELECT * FROM rhythms WHERE phaseKey = :phaseKey AND domain = :domain")
    suspend fun getByPhaseAndDomain(phaseKey: String, domain: String): List<RhythmEntity>

    @Query("SELECT text FROM rhythms WHERE phaseKey = :phaseKey AND domain = :domain")
    suspend fun getTextsByPhaseAndDomain(phaseKey: String, domain: String): List<String>

    @Query("SELECT COUNT(*) FROM rhythms")
    suspend fun countAll(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RhythmEntity>)
}
