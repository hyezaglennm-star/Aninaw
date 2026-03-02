package com.aninaw.growth

import android.content.Context
import com.aninaw.data.AninawDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TreeGrowthManager(private val context: Context) {

    suspend fun onCheckInCompleted(): GrowthUi {
        val db = AninawDb.getDatabase(context)
        return withContext(Dispatchers.IO) {
            GrowthManager.recordCheckIn(db)
        }
    }
}