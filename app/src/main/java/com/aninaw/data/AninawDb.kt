    //AninawDb.kt
    package com.aninaw.data

    import android.content.Context
    import androidx.room.Database
    import androidx.room.RoomDatabase
    import androidx.room.Room
    import com.aninaw.data.rhythm.RhythmDao
    import com.aninaw.data.rhythm.RhythmEntity
    import com.aninaw.data.treering.TreeRingMemoryDao
    import com.aninaw.data.treering.TreeRingMemoryEntity
    import com.aninaw.data.lifesnapshot.LifeSnapshotDao
    import com.aninaw.data.lifesnapshot.LifeSnapshotEntity
    import com.aninaw.data.systems.SystemEntity
    import com.aninaw.data.systems.BlockEntity
    import com.aninaw.data.systems.SystemsDao
    import com.aninaw.data.systems.BlocksDao
    import com.aninaw.data.growth.GrowthDao
    import com.aninaw.data.growth.GrowthEntity
    import com.aninaw.data.journal.JournalDao
    import com.aninaw.data.journal.JournalEntity
    import com.aninaw.data.calmhistory.CalmToolHistoryDao
    import com.aninaw.data.calmhistory.CalmToolHistoryEntity


    @Database(
        entities = [
            RhythmEntity::class,
            TreeRingMemoryEntity::class,
            LifeSnapshotEntity::class,
            SystemEntity::class,
            BlockEntity::class,
            GrowthEntity::class,
            JournalEntity::class,
            CalmToolHistoryEntity::class,
        ],
        version = 10,
        exportSchema = true
    )
    abstract class AninawDb : RoomDatabase() {

        abstract fun calmToolHistoryDao(): CalmToolHistoryDao
        abstract fun growthDao(): GrowthDao
        abstract fun systemsDao(): SystemsDao
        abstract fun blocksDao(): BlocksDao
        abstract fun rhythmDao(): RhythmDao
        abstract fun treeRingMemoryDao(): TreeRingMemoryDao
        abstract fun journalDao(): JournalDao

        abstract fun lifeSnapshotDao(): LifeSnapshotDao

        companion object {
            @Volatile private var INSTANCE: AninawDb? = null

            fun getDatabase(context: Context): AninawDb {
                return INSTANCE ?: synchronized(this) {
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AninawDb::class.java,
                        "aninaw.db"
                    )
                        // You already have this; fine during dev.
                        .fallbackToDestructiveMigration()
                        .build()

                    INSTANCE = instance
                    instance
                }
            }
        }
    }