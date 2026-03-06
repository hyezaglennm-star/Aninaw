package com.aninaw.prompts

import com.aninaw.data.AninawDb
import java.time.LocalDate
import kotlin.math.abs

data class PromptProfile(
    val hasCheckInToday: Boolean,
    val daysSinceCheckIn: Int,
    val lastEmotion: String?,
    val lastIntensity01: Float?,  // 0..1
    val trend: Trend,
    val capacity: String?,
    val lastType: String?,
    val recentEmotions: List<String> = emptyList(),
    val recentIntensities: List<Float> = emptyList(),
    val streakDays: Int = 0,
    val avgReflectionLength: Int = 0,
    val totalCheckIns: Int = 0
)

enum class Trend { UP, DOWN, FLAT, UNKNOWN }

enum class TreeStage { SEED, SPROUT, YOUNG_TREE, GROWING_TREE, MATURE_TREE, FLOURISHING_TREE }
object TreePromptEngine {

    suspend fun buildProfile(db: AninawDb): PromptProfile {
        val repo = com.aninaw.data.treering.TreeRingMemoryRepository(db)
        val end = LocalDate.now()
        val start = end.minusDays(14)

        // Fetch logs (returns ASC timestamp)
        val logs = repo.getRange(start, end)
            .sortedByDescending { it.timestamp }

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

        val recentEmotions = logs.mapNotNull { it.emotion }
        val recentIntensities = logs.mapNotNull { it.intensity }
        
        // Growth / Streak Data
        val growth = db.growthDao().get()
        val streak = growth?.streakDays ?: 0
        val totalPoints = growth?.points ?: 0

        // Avg Reflection Length
        val notes = logs.mapNotNull { it.note }.filter { it.isNotBlank() }
        val avgLen = if (notes.isNotEmpty()) notes.sumOf { it.length } / notes.size else 0

        return PromptProfile(
            hasCheckInToday = (today != null),
            daysSinceCheckIn = daysSince,
            lastEmotion = latest?.emotion,
            lastIntensity01 = latest?.intensity,
            trend = trend,
            capacity = latest?.capacity,
            lastType = latest?.type,
            recentEmotions = recentEmotions,
            recentIntensities = recentIntensities,
            streakDays = streak,
            avgReflectionLength = avgLen,
            totalCheckIns = totalPoints // using points as proxy for consistency
        )
    }

    fun pickPrompt(profile: PromptProfile, daySeed: Int, stage: TreeStage): String {
        val intensity = profile.lastIntensity01 ?: 0f
        val cap = profile.capacity?.uppercase()
        val lastEmo = profile.lastEmotion?.uppercase() ?: ""
        
        // --- 0. PRIORITY: Latest Positive Emotion (Override all risks) ---
        // IF user logs calm or positive emotions (Check last emotion)
        // THEN show gratitude or growth reflection prompts
        if (lastEmo in setOf("CALM", "HAPPY", "JOYFUL", "GRATEFUL", "ENERGIZED", "STEADY", "POSITIVE")) {
             return listOf(
                "What are you grateful for right now?",
                "How can you share this light?",
                "Growth is happening.",
                "Savor this moment."
            ).randomFresh()
        }

        // --- 1. Emotional Risk Awareness (Safety) ---
        // IF high-intensity negative emotions appear multiple times within a short period (>=3 times with intensity >= 0.8)
        // AND the user is NOT currently in a positive state (handled above)
        // THEN show gentle suggestion to seek outside support
        val highRiskCount = profile.recentEmotions.zip(profile.recentIntensities)
            .count { (emo, int) -> 
                emo.uppercase() in setOf("SAD", "ANXIOUS", "STRESSED", "TENSE", "HEAVY", "ANGRY") && int >= 0.8f 
            }
        
        if (highRiskCount >= 3) {
            // Use current timestamp as seed to make it change with every check-in/load
            // instead of once per day (daySeed).
            return listOf(
                "You’ve been carrying a heavy load. It’s okay to ask for support.",
                "This feels big. Is there someone you can talk to?",
                "You don't have to do this alone.",
                "Gentle reminder: seeking help is a strength."
            ).randomFresh() 
        }

        // --- 2. Inactivity Rules ---
        
        // IF user returns after 7+ days of inactivity
        // THEN suggest simple emotional check-in first
        if (profile.daysSinceCheckIn >= 7) {
            return listOf(
                "Welcome back. Start small with just one feeling.",
                "No pressure. Just notice how you are right now.",
                "It's good to see you. How are you holding up?",
                "A fresh start. One breath is enough."
            ).randomFresh()
        }

        // IF user has not opened the app for 3 days
        // THEN show gentle welcome-back prompt
        if (profile.daysSinceCheckIn >= 3) {
            return listOf(
                "Gentle welcome back.",
                "No catching up needed. Just be here.",
                "You can always return.",
                "Softly stepping back in."
            ).randomFresh()
        }

        // --- 3. Daily Check-in Rules ---

        // IF user has not checked in today (and not inactive long term)
        // THEN prioritize emotional check-in prompt
        if (!profile.hasCheckInToday) {
             // This falls through to the specific emotion/intensity logic if available from *yesterday*,
             // OR triggers the default "How are you?" if no recent data.
             // But we can force a generic prompt if it's been > 1 day but < 3 days.
             if (profile.daysSinceCheckIn >= 1) {
                 return listOf(
                     "How are you feeling today?",
                     "Take a moment to check in.",
                     "What's alive in you right now?",
                     "Pause and notice your breath."
                 ).randomFresh()
             }
        }

        // IF user completed today's check-in
        // THEN suggest one reflection or habit activity
        if (profile.hasCheckInToday) {
             // We can be more specific here based on habits
             if (profile.streakDays > 5) {
                 return "You've checked in. Keep the rhythm going."
             }
             // Or fall through to show positive reinforcement or growth prompts
        }

        // --- 4. Emotion-Based Rules (Latest) ---
        
        // (Positive check moved to top)

        // IF user logs anger or frustration (Check last emotion)
        // THEN suggest release or clarity prompts
        if (lastEmo in setOf("ANGRY", "FRUSTRATED", "ANGER", "CONFUSED")) {
             return listOf(
                "Release what you cannot control.",
                "What does your anger want to protect?",
                "Write it down, then let it go.",
                "Clarity comes after the storm."
            ).randomFresh()
        }

        // IF user logs stress or anxiety 3 or more times in recent entries (OR is currently stressed)
        // THEN prioritize calming or grounding prompts
        // (Checking current emotion first for immediate relevance)
        if (lastEmo in setOf("STRESSED", "ANXIOUS", "TENSE", "WORRIED", "NERVOUS")) {
            return listOf(
                "Take a deep breath. Ground yourself.",
                "Focus on your breath for a moment.",
                "Feel your feet on the ground.",
                "Inhale slowly, exhale deeply."
            ).randomFresh()
        }

        // IF user logs sadness repeatedly (>=3) OR is currently sad
        // THEN show supportive reflection prompts
        if (lastEmo in setOf("SAD", "SADNESS", "HEAVY", "TIRED")) {
            return listOf(
                "Be gentle with yourself today.",
                "It's okay to feel this way.",
                "Your feelings are valid.",
                "Rest is also productive."
            ).randomFresh()
        }

        // --- 5. Emotion Intensity Rules ---

        // IF emotion intensity is high (4–5) -> >= 0.8
        // THEN prioritize grounding tools or calming exercises
        if (intensity >= 0.8f) {
            return listOf(
                "Breathe. Just breathe.",
                "Ground yourself in this moment.",
                "You are safe here.",
                "Let the intensity pass like a wave."
            ).randomFresh()
        }

        // IF emotion intensity is low to moderate (1–3) -> < 0.6
        // THEN allow deeper reflection prompts
        if (intensity < 0.6f && intensity > 0f) {
             // Combine with reflection activity rules if applicable
             if (profile.avgReflectionLength > 50) {
                 return listOf(
                     "What specifically is bringing this feeling up?",
                     "Describe the texture of this emotion.",
                     "What does this feeling need from you?",
                     "Listen to the quiet voice inside."
                 ).randomFresh()
             }
             
             return listOf(
                "What is one thing you learned about yourself?",
                "How does your body feel right now?",
                "What really matters to you today?",
                "Notice what you’re carrying today."
            ).randomFresh()
        }

        // --- 6. Repeated Emotion Pattern ---
        
        // IF the same negative emotion appears 3–4 times recently
        // THEN show a pattern reflection prompt
        val negativeEmotions = setOf("SAD", "ANXIOUS", "ANGRY", "STRESSED", "TENSE", "HEAVY", "CONFUSED", "TIRED")
        val mostFrequent = profile.recentEmotions
            .filter { it.uppercase() in negativeEmotions }
            .groupingBy { it.uppercase() }
            .eachCount()
            .maxByOrNull { it.value }
            
        if (mostFrequent != null && mostFrequent.value >= 3) {
             return listOf(
                "You've been feeling ${mostFrequent.key.lowercase()} lately. What is it telling you?",
                "Notice the pattern. Be curious.",
                "Is there a rhythm to this feeling?",
                "What does this recurring feeling need?"
            ).randomFresh()
        }

        // --- 7. Habit Completion Rules ---
        
        // IF user completes habits consistently (streak > 3)
        // THEN show positive reinforcement message
        if (profile.streakDays >= 3) {
            return listOf(
                "You are building momentum.",
                "Your consistency is showing.",
                "Small steps add up.",
                "Keep going. You are growing."
            ).randomFresh()
        }

        // IF user misses habits repeatedly (streak broken recently, low points)
        // THEN suggest smaller or easier habits
        if (profile.streakDays < 2 && profile.totalCheckIns > 5) {
             return listOf(
                "It's okay to start small again.",
                "One small habit is enough.",
                "Be gentle with your rhythm.",
                "Focus on just one thing today."
            ).randomFresh()
        }

        // --- 8. Reflection Activity Rules ---
        
        // IF user rarely writes reflections (avg length < 10)
        // THEN show short simple prompts
        if (profile.avgReflectionLength < 10 && profile.totalCheckIns > 3) {
            return listOf(
                "One word describes today.",
                "Name one feeling.",
                "Just a quick note.",
                "Simple is good."
            ).randomFresh()
        }

        // --- 9. Progress Encouragement / Tree Rules ---
        
        // IF user interacts with the app consistently (streak > 0)
        // THEN show encouraging message
        if (profile.streakDays > 0) {
            return listOf(
                "Your tree is growing stronger.",
                "Roots take time to deepen.",
                "Every check-in nourishes you.",
                "Slow growth is still growth."
            ).randomFresh()
        }

        // IF user shows inconsistent activity (streak == 0)
        // THEN show compassionate encouragement
        return listOf(
            "It’s never too late to begin again.",
            "Your tree waits for you.",
            "Rest is part of growth.",
            "Start where you are."
        ).randomFresh()
    }

    private fun stageLines(stage: TreeStage): List<String> = emptyList()

    private fun List<String>.randomFresh(): String {
        if (isEmpty()) return ""
        return this.random()
    }

    private fun List<String>.randomStable(seed: Int): String {
        if (isEmpty()) return ""
        val idx = stableIndex(seed, size)
        return this[idx]
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
