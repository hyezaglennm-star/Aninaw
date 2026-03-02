package com.aninaw.prompts

import com.aninaw.data.treering.TreeRingMemoryRepository
import java.time.LocalDate
import kotlin.math.abs

data class PromptProfile(
    val hasCheckInToday: Boolean,
    val daysSinceCheckIn: Int,
    val lastEmotion: String?,
    val lastIntensity01: Float?,  // 0..1
    val trend: Trend,
    val capacity: String?,
    val lastType: String?
)

enum class Trend { UP, DOWN, FLAT, UNKNOWN }

enum class TreeStage { SEED, SPROUT, YOUNG_TREE, GROWING_TREE, MATURE_TREE, FLOURISHING_TREE }
object TreePromptEngine {

    suspend fun buildProfile(repo: TreeRingMemoryRepository): PromptProfile {
        val end = LocalDate.now()
        val start = end.minusDays(14)

        // Your DAO range likely returns ascending or unspecified; we normalize here.
        val logs = repo.getRange(start, end)
            .sortedByDescending { it.date } // ISO yyyy-MM-dd sorts lexicographically

        val todayIso = end.toString()
        val today = logs.firstOrNull { it.date == todayIso }
        val latest = logs.firstOrNull()

        val daysSince = if (latest == null) 999 else {
            val lastDay = LocalDate.parse(latest.date)
            (end.toEpochDay() - lastDay.toEpochDay()).toInt().coerceAtLeast(0)
        }

        // Trend from the last 3 intensity values (newest -> older)
        val last3 = logs.mapNotNull { it.intensity }.take(3)
        val trend = computeTrend(last3)

        return PromptProfile(
            hasCheckInToday = (today != null),
            daysSinceCheckIn = daysSince,
            lastEmotion = latest?.emotion,
            lastIntensity01 = latest?.intensity,
            trend = trend,
            capacity = latest?.capacity,
            lastType = latest?.type
        )
    }

    fun pickPrompt(profile: PromptProfile, daySeed: Int, stage: TreeStage): String {
        val intensity = profile.lastIntensity01 ?: 0f
        val cap = profile.capacity?.uppercase()

        val basePool: List<String> = when {
            // 0) Capacity override
            cap in setOf("LOW", "FRAGILE") -> listOf(
                "Keep it small. Stabilize first.",
                "Just one gentle minute. That’s enough.",
                "You don’t have to fix anything today."
            )

            // 1) Checked in today
            profile.hasCheckInToday -> listOf(
                "Keep it gentle. You already showed up today.",
                "Hold what you learned. No need to push.",
                "Today counts. Rest can be part of the work."
            )

            // 2) Long gap
            profile.daysSinceCheckIn >= 3 -> listOf(
                "No catching up. Just one small check-in.",
                "If you’re able, return softly for a minute.",
                "You can come back without explaining everything."
            )

            // 3) High intensity
            intensity >= 0.75f -> listOf(
                "Breathe. Name one feeling. That’s enough.",
                "Keep it simple. Notice what hurts, then soften.",
                "Safety first. One small step only."
            )

            // 4) Trend rising
            profile.trend == Trend.UP -> listOf(
                "Your load is rising. Choose one lighter step.",
                "Soften the day. One grounded minute is progress.",
                "Try a smaller check-in today."
            )

            // 5) Trend easing
            profile.trend == Trend.DOWN -> listOf(
                "You’re easing. Protect that softness.",
                "Keep the rhythm that’s helping you.",
                "Small steadiness is working."
            )

            // 6) Emotion hint
            profile.lastEmotion?.uppercase() in setOf("ANXIOUS", "WORRIED", "NERV0US", "NERVOUS") -> listOf(
                "Slow down. What’s one thing you can control?",
                "One breath longer than usual.",
                "Name the worry. Shrink it to one sentence."
            )

            else -> listOf(
                "One minute with yourself.",
                "Notice what you’re carrying today.",
                "A quiet check-in can reset the day."
            )
        }

        val stagePool = stageLines(stage)

        // Keep tree lines subtle when the user is fragile/high intensity
        val stageTake = when {
            cap in setOf("LOW", "FRAGILE") -> 2
            intensity >= 0.75f -> 2
            profile.daysSinceCheckIn >= 3 -> 3
            else -> 4
        }

        val blended = (basePool + stagePool.take(stageTake)).distinct()

        val idx = stableIndex(daySeed, blended.size)
        return blended[idx]
    }

    private fun stageLines(stage: TreeStage): List<String> = when (stage) {
        TreeStage.SEED -> listOf(
            "Alive in the dark.",
            "Waiting, intact.",
            "Holding its own life.",
            "Alive beneath the surface.",
            "Whole, even unseen.",
            "Quiet and intact.",
            "Carrying its own future.",
            "Still, but living."
        )

        TreeStage.SPROUT -> listOf(
            "Alive and upright.",
            "Small, but breathing.",
            "Rooted and reaching.",
            "A living rise.",
            "Thin, but real.",
            "Fresh to the air.",
            "Holding its first light.",
            "Soft and standing."
        )

        TreeStage.YOUNG_TREE -> listOf(
            "Alive in the wind.",
            "Growing into its ground.",
            "Standing, still soft.",
            "Alive in its own space.",
            "Growing into height.",
            "Roots pressing downward.",
            "Learning the air.",
            "Standing without noise."
        )

        TreeStage.GROWING_TREE -> listOf(
            "Alive through weather.",
            "Rings forming within.",
            "Rooted, widening.",
            "Alive in motion.",
            "Expanding quietly.",
            "Holding weight now.",
            "Stretching through seasons.",
            "Thickening with time."
        )

        TreeStage.MATURE_TREE -> listOf(
            "Fully alive.",
            "Deep in the soil.",
            "Steady in its place.",
            "Alive and grounded.",
            "Full in its trunk.",
            "Deep where it stands.",
            "Carrying its years.",
            "Present and rooted."
        )

        TreeStage.FLOURISHING_TREE -> listOf(
            "Alive and open.",
            "Branches wide.",
            "Present in the light.",
            "Alive in fullness.",
            "Branches alive with movement.",
            "Open to the sky.",
            "Wide and steady.",
            "Breathing in its place."
        )
    }

    private fun computeTrend(last: List<Float>): Trend {
        if (last.size < 2) return Trend.UNKNOWN
        val newest = last[0]
        val prev = last[1]
        val diff = newest - prev

        return when {
            diff >= 0.12f -> Trend.UP
            diff <= -0.12f -> Trend.DOWN
            abs(diff) < 0.12f -> Trend.FLAT
            else -> Trend.UNKNOWN
        }
    }

    private fun stableIndex(seed: Int, size: Int): Int {
        if (size <= 1) return 0
        var x = 1125899907L
        x = x * 31L + seed.toLong()
        x = x xor (x shl 13)
        x = x xor (x shr 7)
        x = x xor (x shl 17)
        val positive = x and Long.MAX_VALUE
        return (positive % size.toLong()).toInt()
    }
}