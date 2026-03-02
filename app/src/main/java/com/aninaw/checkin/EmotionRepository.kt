//EmotionRepository.kt
package com.aninaw.checkin

import android.content.Context
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class EmotionRepository(context: Context) {

    private val prefs = context.getSharedPreferences("AninawPrefs", Context.MODE_PRIVATE)

    // Old storage (primary|secondary) kept for backward compatibility
    private val KEY_DAY_PREFIX = "emotion_day_" // emotion_day_YYYY-MM-DD

    // New storage: multiple entries per day
    private val KEY_ENTRIES_PREFIX = "emotion_entries_" // emotion_entries_YYYY-MM-DD

    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    // ---------------------------------------------
    // Public API (new)
    // ---------------------------------------------

    /**
     * Append a timestamped emotion entry for today.
     * Supports multiple emotions per day.
     */
    fun addTodayEntry(emotion: Emotion) {
        val today = LocalDate.now().format(fmt)
        addEntryForDay(today, emotion)
    }

    /**
     * Append a timestamped emotion entry for a given day (YYYY-MM-DD).
     */
    fun addEntryForDay(ymd: String, emotion: Emotion) {
        if (emotion == Emotion.UNDISCLOSED) return

        val key = KEY_ENTRIES_PREFIX + ymd
        val existingRaw = prefs.getString(key, null)

        val entries = parseEntries(existingRaw).toMutableList()
        entries.add(EmotionEntry(System.currentTimeMillis(), emotion))

        // Keep it sane: cap per-day entries so prefs don’t explode
        val capped = if (entries.size > 200) entries.takeLast(200) else entries

        prefs.edit().putString(key, encodeEntries(capped)).apply()
    }

    /**
     * Load raw entries for a given day.
     * If no new-format entries exist, fallback to old primary|secondary format and convert.
     */
    fun loadEntriesForDay(ymd: String): List<EmotionEntry> {
        val newRaw = prefs.getString(KEY_ENTRIES_PREFIX + ymd, null)
        val parsedNew = parseEntries(newRaw)
        if (parsedNew.isNotEmpty()) return parsedNew

        val oldRaw = prefs.getString(KEY_DAY_PREFIX + ymd, null)
        val (p, s) = parseOldPrimarySecondary(oldRaw)

        val now = System.currentTimeMillis()
        val out = ArrayList<EmotionEntry>(2)
        if (p != null) out.add(EmotionEntry(now - 2, p))
        if (s != null) out.add(EmotionEntry(now - 1, s))
        return out
    }

    /**
     * Daily summaries for the CURRENT MONTH from day 1 up to TODAY.
     * Index 0 = Day 1 (left-most), last index = Today (right-most).
     */
    fun loadCurrentMonthSeason(): List<DailyEmotionRecord> {
        val today = LocalDate.now()
        val ym = YearMonth.from(today)
        val start = ym.atDay(1)
        val end = today

        val days = ChronoUnit.DAYS.between(start, end).toInt() + 1
        val out = ArrayList<DailyEmotionRecord>(days)

        for (i in 0 until days) {
            val date = start.plusDays(i.toLong())
            val ymd = date.format(fmt)

            val entries = loadEntriesForDay(ymd)
            val summary = summarize(entries)

            out.add(
                DailyEmotionRecord(
                    ymd = ymd,
                    primary = summary.primary,
                    secondary = summary.secondary,
                    entriesCount = summary.count
                )
            )
        }
        return out
    }

    fun countReflectedDays(records: List<DailyEmotionRecord>): Int {
        return records.count { it.entriesCount > 0 }
    }

    // ---------------------------------------------
    // Backward compatible API (old names)
    // ---------------------------------------------

    /**
     * Previously overwrote today as primary|secondary.
     * Now it APPENDS an entry for today.
     */
    fun upsertToday(newEmotion: Emotion) {
        addTodayEntry(newEmotion)
    }

    /**
     * Previously overwrote a day as primary|secondary.
     * Now it APPENDS an entry for that day.
     */
    fun upsertForDay(ymd: String, newEmotion: Emotion) {
        addEntryForDay(ymd, newEmotion)
    }

    // ---------------------------------------------
    // Models / encoding
    // ---------------------------------------------

    data class EmotionEntry(val ts: Long, val emotion: Emotion)

    private data class DaySummary(val primary: Emotion?, val secondary: Emotion?, val count: Int)

    // New format: "ts|EMOTION;ts|EMOTION;..."
    private fun encodeEntries(list: List<EmotionEntry>): String {
        return list.joinToString(";") { "${it.ts}|${it.emotion.name}" }
    }

    private fun parseEntries(raw: String?): List<EmotionEntry> {
        if (raw.isNullOrBlank()) return emptyList()

        val parts = raw.split(";")
        val out = ArrayList<EmotionEntry>(parts.size)

        for (p in parts) {
            val seg = p.split("|")
            if (seg.size != 2) continue
            val ts = seg[0].toLongOrNull() ?: continue
            val emo = runCatching { Emotion.valueOf(seg[1]) }.getOrNull() ?: continue
            if (emo == Emotion.UNDISCLOSED) continue
            out.add(EmotionEntry(ts, emo))
        }
        return out
    }

    private fun summarize(entries: List<EmotionEntry>): DaySummary {
        if (entries.isEmpty()) return DaySummary(null, null, 0)

        // frequency
        val freq = LinkedHashMap<Emotion, Int>()
        entries.forEach { e ->
            freq[e.emotion] = (freq[e.emotion] ?: 0) + 1
        }

        // tie-breaker: recency (later index wins ties)
        val lastIndex = HashMap<Emotion, Int>()
        entries.forEachIndexed { idx, e -> lastIndex[e.emotion] = idx }

        val sorted = freq.entries.sortedWith(
            compareByDescending<Map.Entry<Emotion, Int>> { it.value }
                .thenByDescending { lastIndex[it.key] ?: -1 }
        )

        val primary = sorted.getOrNull(0)?.key
        val secondary = sorted.getOrNull(1)?.key?.takeIf { it != primary }

        return DaySummary(primary, secondary, entries.size)
    }

    // Old format parser: "PRIMARY|SECONDARY"
    private fun parseOldPrimarySecondary(raw: String?): Pair<Emotion?, Emotion?> {
        if (raw.isNullOrBlank()) return null to null
        val parts = raw.split("|")
        val p = runCatching { Emotion.valueOf(parts[0]) }.getOrNull()
        val s = if (parts.size > 1) runCatching { Emotion.valueOf(parts[1]) }.getOrNull() else null
        return p to s
    }
}
