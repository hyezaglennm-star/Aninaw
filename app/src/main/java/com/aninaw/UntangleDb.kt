//UntangleDb.kt
package com.aninaw.untangle

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [UntangleEntryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class UntangleDb : RoomDatabase() {

    abstract fun dao(): UntangleDao

    companion object {
        @Volatile private var INSTANCE: UntangleDb? = null

        fun get(context: Context): UntangleDb {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    UntangleDb::class.java,
                    "aninaw_untangle.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
