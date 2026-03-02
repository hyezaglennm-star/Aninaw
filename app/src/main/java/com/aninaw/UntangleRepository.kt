//UntangleRepository.kt
package com.aninaw.untangle

import android.content.Context

class UntangleRepository(context: Context) {
    private val dao = UntangleDb.get(context).dao()

    suspend fun insert(entry: UntangleEntryEntity): Long = dao.insert(entry)
    suspend fun latest(limit: Int = 50) = dao.latest(limit)
    suspend fun byDay(ymd: String) = dao.byDay(ymd)
}
