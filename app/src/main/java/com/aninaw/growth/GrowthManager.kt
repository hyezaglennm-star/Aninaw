package com.aninaw.growth

import com.aninaw.data.AninawDb
import com.aninaw.data.growth.GrowthEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

data class GrowthUi(
    val points: Int,
    val streakDays: Int,
    val growth01: Float,          // 0..1 for HybridTreeView
    val narrative: String,
    val milestone: String? = null
)

object GrowthManager {
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    // Tune this. 100 points = full grown.
    private fun pointsToGrowth(points: Int): Float {
        return (points / 100f).coerceIn(0f, 1f)
    }

    private fun baseNarrative(points: Int, streak: Int): String = when {
        streak >= 30 -> "You’ve been choosing yourself."
        streak >= 7  -> "Your rhythm is holding."
        points % 10 == 0 -> "The roots are getting stronger."
        else -> "You showed up."
    }

    private fun milestoneLine(points: Int, streak: Int): String? = when {
        streak == 7  -> "Seven days of showing up. Quiet power."
        streak == 30 -> "Thirty days. This is becoming you."
        points == 100 -> "A hundred moments of effort. Real change."
        else -> null
    }

    suspend fun recordCheckIn(db: AninawDb): GrowthUi {
        val dao = db.growthDao()
        val today = LocalDate.now()
        val todayYmd = today.format(fmt)

        val current = dao.get() ?: GrowthEntity()

        val last = current.lastCompletedYmd?.let { LocalDate.parse(it, fmt) }

        val newStreak = when {
            last == null -> 1
            last.isEqual(today) -> current.streakDays
            last.plusDays(1).isEqual(today) -> current.streakDays + 1
            else -> 1
        }

        // only +1 point per day (prevents spam tapping = fake growth)
        val newPoints = if (last?.isEqual(today) == true) current.points else current.points + 1

        val updated = current.copy(
            points = newPoints,
            streakDays = newStreak,
            lastCompletedYmd = todayYmd
        )

        dao.upsert(updated)

        val narrative = baseNarrative(newPoints, newStreak)
        val milestone = milestoneLine(newPoints, newStreak)

        return GrowthUi(
            points = newPoints,
            streakDays = newStreak,
            growth01 = pointsToGrowth(newPoints),
            narrative = narrative,
            milestone = milestone
        )
    }

    suspend fun load(db: AninawDb): GrowthUi {
        val g = db.growthDao().get() ?: GrowthEntity()

        return GrowthUi(
            points = g.points,
            streakDays = g.streakDays,
            growth01 = pointsToGrowth(g.points),
            narrative = baseNarrative(g.points, g.streakDays),
            milestone = milestoneLine(g.points, g.streakDays)
        )
    }
    }