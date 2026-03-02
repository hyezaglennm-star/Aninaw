//UntanglePrefs.kt
package com.aninaw.untangle

import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class UntanglePrefs(context: Context) {

    private val prefs = context.getSharedPreferences("AninawPrefs", Context.MODE_PRIVATE)
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    private val KEY_LATEST = "untangle_latest"
    private val KEY_DAY_PREFIX = "untangle_day_" // untangle_day_YYYY-MM-DD -> count

    fun incrementTodayCount() {
        val ymd = LocalDate.now().format(fmt)
        val key = KEY_DAY_PREFIX + ymd
        val next = (prefs.getInt(key, 0) + 1).coerceAtMost(50)
        prefs.edit().putInt(key, next).apply()
    }

    fun saveLatest(summary: String) {
        // keep it tiny: quick “last reflection” preview
        prefs.edit().putString(KEY_LATEST, summary.take(600)).apply()
    }

    fun loadLatest(): String? = prefs.getString(KEY_LATEST, null)

    fun getDayCount(ymd: String): Int = prefs.getInt(KEY_DAY_PREFIX + ymd, 0)
}
