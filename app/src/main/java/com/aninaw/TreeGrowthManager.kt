//TreeGrowthManager.kt
package com.aninaw

import android.content.Context
import android.os.Build
import java.time.LocalDate
import kotlin.math.pow

class TreeGrowthManager(context: Context) {

    private val prefs = context.getSharedPreferences("AninawPrefs", Context.MODE_PRIVATE)

    // Use the SAME keys the home screen TreeGrowthEngine reads
    private val KEY_FIRST_USE_EPOCH_DAY = "first_use_epoch_day"
    private val KEY_GROWTH_01 = "tree_growth_01"
    private val KEY_LAST_GROWTH_EPOCH_DAY = "tree_last_growth_epoch_day"

    // Tracks if a bonus happened today (for your "daily growth only once" visual logic)
    private val KEY_BONUS_DAY_PREFIX = "tree_bonus_day_" // + epochDay -> boolean
    private fun keyBonusDay(day: Long) = "$KEY_BONUS_DAY_PREFIX$day"

    private fun markBonusDoneToday() {
        val today = epochDayNow()
        prefs.edit().putBoolean(keyBonusDay(today), true).apply()
    }

    // -----------------------------
// STEP-BASED TIMELINE (your real growth logic)
// -----------------------------
    private val KEY_TIMELINE_STEP = "tree_timeline_step"          // Int: 1..364 (represents Day 1..364)
    private val KEY_TIMELINE_LAST_EPOCH = "tree_timeline_last_epoch_day"
    private val KEY_TIMELINE_MISS_STREAK = "tree_timeline_miss_streak"

    private val BASELINE_DELAY_DAYS = 5

    private fun bonusDoneOnDay(day: Long): Boolean {
        return prefs.getBoolean(keyBonusDay(day), false)
    }

    /**
     * Advances timeline step by at most 1 per calendar day processed.
     * Rule:
     * - If bonus done that day => step++
     * - Else => missStreak++ ; if missStreak reaches 5 => step++ and reset missStreak
     * Day 1 (install day) becomes step=1 by default (sprout).
     */
    private fun applyTimelineIfNeeded() {
        ensureFirstUse()

        val today = epochDayNow()
        val firstUse = prefs.getLong(KEY_FIRST_USE_EPOCH_DAY, today)

        // Step meaning:
        // step=1 => Day 1 (Sprout)
        // step=2 => Day 2 (Sapling fade-in)
        // ...
        var step = prefs.getInt(KEY_TIMELINE_STEP, 0)
        if (step <= 0) {
            // On first ever run, lock Day 1 immediately
            step = 1
            prefs.edit().putInt(KEY_TIMELINE_STEP, step).apply()
        }

        val last = prefs.getLong(KEY_TIMELINE_LAST_EPOCH, firstUse)
        if (today <= last) return

        var miss = prefs.getInt(KEY_TIMELINE_MISS_STREAK, 0).coerceAtLeast(0)

        // process day-by-day since last
        for (d in (last + 1)..today) {
            if (step >= 364) break

            if (bonusDoneOnDay(d)) {
                step += 1
                miss = 0
            } else {
                miss += 1
                if (miss >= BASELINE_DELAY_DAYS) {
                    step += 1
                    miss = 0
                }
            }
        }

        prefs.edit()
            .putInt(KEY_TIMELINE_STEP, step.coerceIn(1, 364))
            .putInt(KEY_TIMELINE_MISS_STREAK, miss.coerceIn(0, BASELINE_DELAY_DAYS - 1))
            .putLong(KEY_TIMELINE_LAST_EPOCH, today)
            .apply()
    }

    /** Call this from UI to get the timeline Day number (1..364). */
    fun getTimelineDay(): Int {
        applyTimelineIfNeeded()
        return prefs.getInt(KEY_TIMELINE_STEP, 1).coerceIn(1, 364)
    }


    // “Once per day” gates so users can’t spam growth
    private fun keyDone(prefix: String, day: Long) = "${prefix}_$day"
    private val PREFIX_DID_CHECKIN = "did_full_checkin"
    private val PREFIX_DID_QUICKCHECKIN = "did_quick_checkin"
    private val PREFIX_DID_RHYTHM = "did_any_rhythm"

    // ---- TUNING ----
    // Slow minimum growth
    private val BASELINE_PER_DAY = 0.003f

    // Bonuses (instant)
    private val BONUS_FULL_CHECKIN = 0.020f
    private val BONUS_QUICK_CHECKIN = 0.012f
    private val BONUS_RHYTHM = 0.010f

    // Diminishing returns near 1.0 (bigger = slows more near the top)
    private val DIMINISH_EXP = 1.25f

    fun onHabitCompleted(count: Int = 1) {
        ensureFirstUse()
        applyBaselineIfNeeded()

        val today = epochDayNow()
        val gateKey = "did_habit_$today"
        if (prefs.getBoolean(gateKey, false)) return

        prefs.edit().putBoolean(gateKey, true).apply()

        // ✅ mark that user earned a bonus today (for your daily-growth visual rules)
        markBonusDoneToday()

        // If you want "count" to matter, scale it:
        val rawBonus = BONUS_RHYTHM * count // or create BONUS_HABIT if you want it separate
        addBonusNow(rawBonus)
    }

    fun getGrowth(): Float {
        ensureFirstUse()
        applyBaselineIfNeeded()
        return prefs.getFloat(KEY_GROWTH_01, 0f).coerceIn(0f, 1f)
    }

    fun onCheckInCompleted() {
        ensureFirstUse()
        applyBaselineIfNeeded()

        val today = epochDayNow()
        val gateKey = keyDone(PREFIX_DID_CHECKIN, today)
        if (prefs.getBoolean(gateKey, false)) return

        prefs.edit().putBoolean(gateKey, true).apply()

        markBonusDoneToday()              // ✅ add this
        addBonusNow(BONUS_FULL_CHECKIN)
    }

    fun isQuickCheckInDoneToday(): Boolean {
        val today = epochDayNow()
        val gateKey = keyDone(PREFIX_DID_QUICKCHECKIN, today)
        return prefs.getBoolean(gateKey, false)
    }

    fun onQuickCheckInCompleted() {
        ensureFirstUse()
        applyBaselineIfNeeded()

        val today = epochDayNow()
        val gateKey = keyDone(PREFIX_DID_QUICKCHECKIN, today)
        if (prefs.getBoolean(gateKey, false)) return

        prefs.edit().putBoolean(gateKey, true).apply()

        markBonusDoneToday()              // ✅ add this
        addBonusNow(BONUS_QUICK_CHECKIN)
    }

    fun onRhythmCompleted() {
        ensureFirstUse()
        applyBaselineIfNeeded()

        val today = epochDayNow()
        val gateKey = keyDone(PREFIX_DID_RHYTHM, today)
        if (prefs.getBoolean(gateKey, false)) return

        prefs.edit().putBoolean(gateKey, true).apply()

        markBonusDoneToday()              // ✅ add this
        addBonusNow(BONUS_RHYTHM)
    }

    private fun addBonusNow(rawBonus: Float) {
        val current = prefs.getFloat(KEY_GROWTH_01, 0f).coerceIn(0f, 1f)
        val remaining = (1f - current).coerceIn(0f, 1f)

        val scaledBonus = rawBonus * remaining.pow(DIMINISH_EXP)
        val next = (current + scaledBonus).coerceIn(0f, 1f)

        prefs.edit().putFloat(KEY_GROWTH_01, next).apply()
    }

    private fun applyBaselineIfNeeded() {
        val today = epochDayNow()

        var firstUse = prefs.getLong(KEY_FIRST_USE_EPOCH_DAY, -1L)
        if (firstUse == -1L) {
            firstUse = today
            prefs.edit()
                .putLong(KEY_FIRST_USE_EPOCH_DAY, firstUse)
                .putLong(KEY_LAST_GROWTH_EPOCH_DAY, today)
                .apply()
            return
        }

        val lastDay = prefs.getLong(KEY_LAST_GROWTH_EPOCH_DAY, firstUse)
        val delta = (today - lastDay).toInt()
        if (delta <= 0) return

        val current = prefs.getFloat(KEY_GROWTH_01, 0f).coerceIn(0f, 1f)
        val next = (current + BASELINE_PER_DAY * delta).coerceIn(0f, 1f)

        prefs.edit()
            .putFloat(KEY_GROWTH_01, next)
            .putLong(KEY_LAST_GROWTH_EPOCH_DAY, today)
            .apply()
    }

    // --------------------------------------------
    // DEBUGGING
    // --------------------------------------------
    fun debugAddGrowth(amount: Float) {
        ensureFirstUse()
        val current = prefs.getFloat(KEY_GROWTH_01, 0f)
        val next = (current + amount).coerceIn(0f, 1f)
        prefs.edit().putFloat(KEY_GROWTH_01, next).apply()
        
        android.util.Log.d("TreeGrowth", "Debug growth added: +$amount. New total: $next")
    }

    fun debugResetGrowth() {
        ensureFirstUse()
        prefs.edit()
            .putFloat(KEY_GROWTH_01, 0f)
            .putInt(KEY_TIMELINE_STEP, 1)
            .apply()
        android.util.Log.d("TreeGrowth", "Debug growth reset to 0.")
    }
    
    fun debugGetStats(): String {
        ensureFirstUse()
        val growth = prefs.getFloat(KEY_GROWTH_01, 0f)
        val step = prefs.getInt(KEY_TIMELINE_STEP, 1)
        val firstUse = prefs.getLong(KEY_FIRST_USE_EPOCH_DAY, -1)
        val daysActive = if (firstUse != -1L) epochDayNow() - firstUse + 1 else 0
        
        return "Growth: ${(growth * 100).toInt()}%\nStep (Day): $step\nActive Days: $daysActive"
    }

    private fun ensureFirstUse() {
        val existing = prefs.getLong(KEY_FIRST_USE_EPOCH_DAY, -1L)
        if (existing != -1L) return
        val today = epochDayNow()
        prefs.edit()
            .putLong(KEY_FIRST_USE_EPOCH_DAY, today)
            .putLong(KEY_LAST_GROWTH_EPOCH_DAY, today)
            .apply()
    }

    private fun epochDayNow(): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LocalDate.now().toEpochDay()
        } else {
            System.currentTimeMillis() / 86_400_000L
        }
    }
}
