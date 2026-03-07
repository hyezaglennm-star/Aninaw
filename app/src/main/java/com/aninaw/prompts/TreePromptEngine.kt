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
                "Take a deep breath and let this feeling settle into your bones; moments like this are precious reminders of your inner light.",
                "Your positive energy is a gift to yourself and others. Consider how you might share this warmth today.",
                "You are growing in ways you might not see yet. This feeling is proof your roots are deepening.",
                "Pause for a moment and truly savor this feeling. Let it fill you up completely."
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
                "You’ve been carrying a heavy load, and it’s okay to set it down. Asking for support is a brave step toward healing.",
                "This feeling seems overwhelming, but you don't have to navigate it alone. Is there a trusted friend you can reach out to?",
                "Remember that you do not have to walk this path alone. Reaching out is an act of kindness to yourself.",
                "Seeking help is a profound strength, not a failure. Please consider letting someone in to help you carry this."
            ).randomFresh() 
        }

        // --- 2. Inactivity Rules ---
        
        // IF user returns after 7+ days of inactivity
        // THEN suggest simple emotional check-in first
        if (profile.daysSinceCheckIn >= 7) {
            return listOf(
                "Welcome back; it takes courage to return. There is no pressure, just start small.",
                "No matter how much time has passed, this space is here for you. Let go of guilt and simply notice how you are.",
                "It is so good to see you again. How are you holding up today?",
                "Consider this a fresh start. You don't need to catch up on anything."
            ).randomFresh()
        }

        // IF user has not opened the app for 3 days
        // THEN show gentle welcome-back prompt
        if (profile.daysSinceCheckIn >= 3) {
            return listOf(
                "A gentle welcome back to you. Take a moment to settle in and breathe.",
                "There is no need to rush or catch up. The only moment that matters is right now.",
                "Remember that you can always return. Let yourself arrive gently, without expectation.",
                "Softly stepping back in is a beautiful way to resume. Be proud of yourself for showing up today."
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
                     "Take a gentle pause to ask: How am I truly feeling right now? There is no right or wrong answer.",
                     "The world moves fast, but you can choose to slow down. Take a quiet moment to check in with yourself.",
                     "Close your eyes and look inward. What is alive in you right now?",
                     "Pause and simply notice your breath flowing in and out. How does your body feel in this stillness?"
                 ).randomFresh()
             }
        }

        // IF user completed today's check-in
        // THEN suggest one reflection or habit activity
        if (profile.hasCheckInToday) {
             // We can be more specific here based on habits
             if (profile.streakDays > 5) {
                 return "You have already taken time for yourself today. Keep this gentle rhythm going."
             }
             // Or fall through to show positive reinforcement or growth prompts
        }

        // --- 4. Emotion-Based Rules (Latest) ---
        
        // (Positive check moved to top)

        // IF user logs anger or frustration (Check last emotion)
        // THEN suggest release or clarity prompts
        if (lastEmo in setOf("ANGRY", "FRUSTRATED", "ANGER", "CONFUSED")) {
             return listOf(
                "Sometimes holding on hurts more than letting go. Try to release what you cannot control.",
                "Anger often stands guard over something precious. Ask yourself: what is my anger trying to protect?",
                "Get it all out—write it down or speak it to the air. Once it's out, you might find it easier to let go.",
                "Even the fiercest storms eventually give way to clear skies. Allow yourself to feel this."
            ).randomFresh()
        }

        // IF user logs stress or anxiety 3 or more times in recent entries (OR is currently stressed)
        // THEN prioritize calming or grounding prompts
        // (Checking current emotion first for immediate relevance)
        if (lastEmo in setOf("STRESSED", "ANXIOUS", "TENSE", "WORRIED", "NERVOUS")) {
            return listOf(
                "Take a deep, slow breath and let your shoulders drop. Ground yourself in this moment.",
                "Everything else can wait; focus entirely on your breath. Let it anchor you to the here and now.",
                "Feel your feet firmly planted on the ground. You are steady, present, and you can handle this.",
                "Inhale slowly counting to four, and exhale deeply counting to six. You are doing the best you can."
            ).randomFresh()
        }

        // IF user logs sadness repeatedly (>=3) OR is currently sad
        // THEN show supportive reflection prompts
        if (lastEmo in setOf("SAD", "SADNESS", "HEAVY", "TIRED")) {
            return listOf(
                "Please be extra gentle with yourself today. Treat yourself with the kindness you would offer a friend.",
                "It is completely okay to feel this way. Sadness is natural; just let it be.",
                "This is just one day, and tomorrow is a fresh start. You are strong and capable.",
                "Remember that rest is not laziness; it is a vital part of healing. Give yourself permission to rest."
            ).randomFresh()
        }

        // --- 5. Emotion Intensity Rules ---

        // IF emotion intensity is high (4–5) -> >= 0.8
        // THEN prioritize grounding tools or calming exercises
        if (intensity >= 0.8f) {
            return listOf(
                "Breathe. Just breathe. You are stronger than this moment.",
                "The storm may be loud, but you can find a quiet center. Ground yourself in this moment.",
                "You are safe here; this feeling cannot hurt you. Know that you are okay.",
                "Imagine this emotion as a wave in the ocean. Let it pass through you."
            ).randomFresh()
        }

        // IF emotion intensity is low to moderate (1–3) -> < 0.6
        // THEN allow deeper reflection prompts
        if (intensity < 0.6f && intensity > 0f) {
             // Combine with reflection activity rules if applicable
             if (profile.avgReflectionLength > 50) {
                 return listOf(
                     "What specifically is bringing this feeling to the surface? Understanding the root can bring clarity.",
                     "Try to describe the texture of this emotion. Giving it a shape can make it easier to understand.",
                     "Ask yourself: What does this feeling need from me right now? Listen to what it says.",
                     "In the quiet of this moment, listen to the small voice inside you. What is it whispering today?"
                 ).randomFresh()
             }
             
             return listOf(
                "Reflect on one thing you have learned about yourself recently. Every emotion has a lesson to teach us.",
                "Scan your body from head to toe. How does your body feel right now?",
                "Amidst everything, what really matters to you today? Reconnecting with values provides a compass.",
                "Take a moment to notice what you’re carrying today. Is there anything you can set down?"
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
                "You've been feeling ${mostFrequent.key.lowercase()} quite a bit lately. Ask yourself: what is this feeling trying to tell me?",
                "Notice this pattern with curiosity rather than judgment. Patterns often show us something important.",
                "Is there a rhythm to this feeling? Observing the flow can help you navigate it better.",
                "What does this recurring feeling need from you? Listen closely."
            ).randomFresh()
        }

        // --- 7. Habit Completion Rules ---
        
        // IF user completes habits consistently (streak > 3)
        // THEN show positive reinforcement message
        if (profile.streakDays >= 3) {
            return listOf(
                "You are building incredible momentum. Every day you show up, you cast a vote for who you want to become.",
                "Your consistency is showing. These daily actions are weaving a strong fabric of resilience.",
                "Never underestimate the power of small steps. You are making progress, one day at a time.",
                "Keep going. You are growing in ways that may be invisible now but will be undeniable later."
            ).randomFresh()
        }

        // IF user misses habits repeatedly (streak broken recently, low points)
        // THEN suggest smaller or easier habits
        if (profile.streakDays < 2 && profile.totalCheckIns > 5) {
             return listOf(
                "It is completely okay to start small again. Be kind to yourself as you find your footing.",
                "You don't need to do it all. One small habit, done with intention, is enough.",
                "Be gentle with your rhythm. Listen to what your body and mind need today.",
                "Focus on just one thing today. Mastering one small act of care is a victory."
            ).randomFresh()
        }

        // --- 8. Reflection Activity Rules ---
        
        // IF user rarely writes reflections (avg length < 10)
        // THEN show short simple prompts
        if (profile.avgReflectionLength < 10 && profile.totalCheckIns > 3) {
            return listOf(
                "If you could only use one word to describe today, what would it be? Sometimes, a single word is enough.",
                "Name one feeling that is present for you right now. Just naming it is enough.",
                "Just a quick note is all it takes. Capture a fleeting thought before it passes.",
                "Simple is good. A simple truth is powerful."
            ).randomFresh()
        }

        // --- 9. Progress Encouragement / Tree Rules ---
        
        // IF user interacts with the app consistently (streak > 0)
        // THEN show encouraging message
        if (profile.streakDays > 0) {
            return listOf(
                "With every check-in, your tree is growing stronger. You are nurturing something beautiful.",
                "Remember that roots take time to deepen. You are building a foundation for the future.",
                "Every time you check in, you nourish your mind and spirit. It's like watering a plant.",
                "Slow growth is still growth. Trust your own pace; you are exactly where you need to be."
            ).randomFresh()
        }

        // IF user shows inconsistent activity (streak == 0)
        // THEN show compassionate encouragement
        return listOf(
            "It is never too late to begin again. Each moment offers a new beginning.",
            "Your tree waits for you patiently. You are always welcome here.",
            "Rest is an essential part of growth. Allow yourself the grace to rest when you need it.",
            "Start exactly where you are. You just need to take one small step."
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
