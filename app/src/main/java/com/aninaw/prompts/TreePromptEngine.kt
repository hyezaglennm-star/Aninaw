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
                "Take a deep breath and let this feeling settle into your bones. Moments like this are precious reminders of the light that exists within you. What specific part of this joy are you most grateful for right now?",
                "Your positive energy is a gift, not just to yourself but to everyone around you. Consider how you might share this warmth today—perhaps a smile, a kind word, or simply holding onto this inner light.",
                "You are growing in ways you might not even see yet. This feeling is proof that your roots are deepening and your branches are reaching higher. Trust the process and celebrate this beautiful progress.",
                "Pause for a moment and truly savor this feeling. Let it fill you up completely, knowing that you deserve this peace and happiness. This is a moment to cherish and remember."
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
                "You’ve been carrying such a heavy load lately, and I want you to know it’s completely okay to set it down. Please remember that asking for support isn't a weakness; it's a brave step toward healing.",
                "This feeling seems overwhelming right now, but you don't have to navigate this storm by yourself. Is there a trusted friend, family member, or professional you can reach out to? You deserve to be heard.",
                "Please remember that you do not have to walk this path alone. There are hands ready to hold yours and hearts willing to listen. Reaching out is an act of kindness to yourself.",
                "A gentle reminder from your tree: seeking help is a profound strength, not a failure. You are valuable, and your well-being matters deeply. Please consider letting someone in to help you carry this."
            ).randomFresh() 
        }

        // --- 2. Inactivity Rules ---
        
        // IF user returns after 7+ days of inactivity
        // THEN suggest simple emotional check-in first
        if (profile.daysSinceCheckIn >= 7) {
            return listOf(
                "Welcome back. It takes courage to return, and I'm so glad you're here. There is absolutely no pressure to do everything at once; just start small by naming one thing you're feeling right now.",
                "No matter how much time has passed, this space is always here for you without judgment. Let go of any guilt and simply notice how you are in this present moment. You are exactly where you need to be.",
                "It is so good to see you again. Life can be a whirlwind, and I hope you've been treating yourself with kindness. How are you holding up today? Take your time.",
                "Consider this a fresh start, a clean slate. You don't need to catch up on anything. Just one conscious breath is enough to reconnect with yourself. You are doing just fine."
            ).randomFresh()
        }

        // IF user has not opened the app for 3 days
        // THEN show gentle welcome-back prompt
        if (profile.daysSinceCheckIn >= 3) {
            return listOf(
                "A gentle welcome back to you. Your tree has been waiting patiently, and it's wonderful to have you return. Take a moment to settle in and breathe.",
                "There is no need to rush or catch up on missed days. The only moment that matters is right now. Just be here, fully present with yourself and your feelings.",
                "Remember that you can always return, no matter how far you drift. This is your safe harbor. Let yourself arrive gently, without expectation.",
                "Softly stepping back in is a beautiful way to resume your journey. Be proud of yourself for showing up today. That small act is a victory in itself."
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
                     "Take a gentle pause in your day to ask yourself: How am I truly feeling right now? There is no right or wrong answer, just the truth of your experience.",
                     "The world moves fast, but you can choose to slow down. Take a quiet moment to check in with yourself. Your feelings are valid and deserve your attention.",
                     "Close your eyes for a second and look inward. What is alive in you right now? Whatever it is, welcome it with curiosity and kindness.",
                     "Pause and simply notice your breath flowing in and out. It’s a constant anchor you can always return to. How does your body and mind feel in this stillness?"
                 ).randomFresh()
             }
        }

        // IF user completed today's check-in
        // THEN suggest one reflection or habit activity
        if (profile.hasCheckInToday) {
             // We can be more specific here based on habits
             if (profile.streakDays > 5) {
                 return "You have already taken time for yourself today, and that is a wonderful act of self-care. Keep this gentle rhythm going, knowing that every check-in strengthens your inner growth."
             }
             // Or fall through to show positive reinforcement or growth prompts
        }

        // --- 4. Emotion-Based Rules (Latest) ---
        
        // (Positive check moved to top)

        // IF user logs anger or frustration (Check last emotion)
        // THEN suggest release or clarity prompts
        if (lastEmo in setOf("ANGRY", "FRUSTRATED", "ANGER", "CONFUSED")) {
             return listOf(
                "Sometimes holding on hurts more than letting go. Try to release what you cannot control, and trust that you have the strength to handle what remains.",
                "Anger often stands guard over something precious. Ask yourself: what is my anger trying to protect? Understanding its message can bring you a sense of peace.",
                "Get it all out—write it down, speak it to the air, or scribble it on paper. Once it's out of your head, you might find it easier to let it go and breathe freely again.",
                "Even the fiercest storms eventually give way to clear skies. Allow yourself to feel this, knowing that clarity and calm are waiting for you on the other side."
            ).randomFresh()
        }

        // IF user logs stress or anxiety 3 or more times in recent entries (OR is currently stressed)
        // THEN prioritize calming or grounding prompts
        // (Checking current emotion first for immediate relevance)
        if (lastEmo in setOf("STRESSED", "ANXIOUS", "TENSE", "WORRIED", "NERVOUS")) {
            return listOf(
                "Take a deep, slow breath and let your shoulders drop. Ground yourself in this moment, knowing that you are safe and that this feeling will pass.",
                "Everything else can wait. For just this moment, focus entirely on your breath. Watch the rise and fall of your chest, and let it anchor you to the here and now.",
                "Feel your feet firmly planted on the ground. You are supported by the earth beneath you. You are steady, you are present, and you can handle this.",
                "Inhale slowly, counting to four, and exhale deeply, counting to six. Let your breath create a space of calm within you. You are doing the best you can."
            ).randomFresh()
        }

        // IF user logs sadness repeatedly (>=3) OR is currently sad
        // THEN show supportive reflection prompts
        if (lastEmo in setOf("SAD", "SADNESS", "HEAVY", "TIRED")) {
            return listOf(
                "Please be extra gentle with yourself today. Treat yourself with the same kindness and compassion you would offer to a dear friend who is hurting.",
                "It is completely okay to feel this way. Sadness is a natural part of being human, and you don't need to fix it right away. Just let it be.",
                "This is just one day, and tomorrow you have a chance to make it all better. And the truth is, you will make your situation better because you are strong and incredibly capable.",
                "Remember that rest is not laziness; it is a vital part of healing. Giving yourself permission to rest is one of the most productive things you can do right now."
            ).randomFresh()
        }

        // --- 5. Emotion Intensity Rules ---

        // IF emotion intensity is high (4–5) -> >= 0.8
        // THEN prioritize grounding tools or calming exercises
        if (intensity >= 0.8f) {
            return listOf(
                "Breathe. Just breathe. Deep, steady breaths. You are stronger than this moment, and you will make it through, one breath at a time.",
                "The storm may be loud, but you can find a quiet center. Ground yourself in this moment by noticing five things you can see and four things you can touch.",
                "You are safe here. This feeling is intense, but it cannot hurt you. Wrap yourself in a sense of safety and know that you are okay.",
                "Imagine this intense emotion as a wave in the ocean. It rises and crashes, but eventually, it recedes. Let it pass through you, knowing the water will settle again."
            ).randomFresh()
        }

        // IF emotion intensity is low to moderate (1–3) -> < 0.6
        // THEN allow deeper reflection prompts
        if (intensity < 0.6f && intensity > 0f) {
             // Combine with reflection activity rules if applicable
             if (profile.avgReflectionLength > 50) {
                 return listOf(
                     "If you look a little deeper, what specifically is bringing this feeling to the surface? Understanding the root can be the first step toward greater clarity.",
                     "Try to describe the texture of this emotion. Is it heavy, sharp, or flowing? Giving it a shape or name can sometimes make it easier to understand.",
                     "Ask yourself gently: What does this feeling need from me right now? It might need comfort, action, or simply to be heard. Listen to what it says.",
                     "In the quiet of this moment, listen to the small voice inside you. It often holds the wisdom we need most. What is it whispering to you today?"
                 ).randomFresh()
             }
             
             return listOf(
                "Reflect on one thing you have learned about yourself recently. Every emotion, even the quiet ones, has a lesson to teach us about who we are.",
                "Scan your body from head to toe. How does your body feel right now? Often, our physical sensations hold the key to understanding our emotional state.",
                "Amidst everything going on, what really matters to you today? Reconnecting with your core values can provide a compass when things feel uncertain.",
                "Take a moment to notice what you’re carrying today. Is there anything you can set down, even just for a little while, to lighten your step?"
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
                "You've been feeling ${mostFrequent.key.lowercase()} quite a bit lately. Instead of pushing it away, ask yourself: what is this feeling trying to tell me?",
                "Notice this pattern with curiosity rather than judgment. Patterns often emerge to show us something important about our lives or our needs.",
                "Is there a rhythm to this feeling? Does it arrive at certain times or in certain places? Observing the flow can help you navigate it better.",
                "What does this recurring feeling need from you? Perhaps it's asking for a change, a boundary, or simply more compassion. Listen closely."
            ).randomFresh()
        }

        // --- 7. Habit Completion Rules ---
        
        // IF user completes habits consistently (streak > 3)
        // THEN show positive reinforcement message
        if (profile.streakDays >= 3) {
            return listOf(
                "You are building incredible momentum. Every day you show up, you are casting a vote for the person you want to become. Be proud of this.",
                "Your consistency is showing, and it is beautiful to witness. These daily actions are weaving a strong fabric of resilience and self-care.",
                "Never underestimate the power of small steps. They add up to mountains climbed and oceans crossed. You are making progress, one day at a time.",
                "Keep going. You are growing in ways that may be invisible now but will be undeniable later. Trust in the power of your own persistence."
            ).randomFresh()
        }

        // IF user misses habits repeatedly (streak broken recently, low points)
        // THEN suggest smaller or easier habits
        if (profile.streakDays < 2 && profile.totalCheckIns > 5) {
             return listOf(
                "It is completely okay to start small again. In fact, starting small is often the bravest way to begin. Be kind to yourself as you find your footing.",
                "You don't need to do it all. One small habit, done with intention, is enough to shift your day. Focus on quality over quantity.",
                "Be gentle with your rhythm. Life is not a straight line, and neither is growth. Listen to what your body and mind need today.",
                "Focus on just one thing today. Let go of the rest. Mastering one small act of care is a victory worth celebrating."
            ).randomFresh()
        }

        // --- 8. Reflection Activity Rules ---
        
        // IF user rarely writes reflections (avg length < 10)
        // THEN show short simple prompts
        if (profile.avgReflectionLength < 10 && profile.totalCheckIns > 3) {
            return listOf(
                "If you could only use one word to describe today, what would it be? Sometimes, a single word can hold a universe of meaning.",
                "Name one feeling that is present for you right now. You don't need to explain it or fix it—just naming it is enough.",
                "Just a quick note is all it takes. Capture a fleeting thought or emotion before it passes. Your future self will thank you.",
                "Simple is good. You don't need profound paragraphs to make a meaningful reflection. A simple truth is powerful."
            ).randomFresh()
        }

        // --- 9. Progress Encouragement / Tree Rules ---
        
        // IF user interacts with the app consistently (streak > 0)
        // THEN show encouraging message
        if (profile.streakDays > 0) {
            return listOf(
                "With every check-in, your tree is growing stronger and more resilient. You are nurturing something beautiful within yourself.",
                "Remember that roots take time to deepen. What you are building is a foundation that will support you for years to come.",
                "Every time you check in, you nourish your mind and spirit. It's like watering a plant—the effects are cumulative and life-giving.",
                "Slow growth is still growth. In nature, the mightiest trees grow slowly. Trust your own pace; you are exactly where you need to be."
            ).randomFresh()
        }

        // IF user shows inconsistent activity (streak == 0)
        // THEN show compassionate encouragement
        return listOf(
            "It is never, ever too late to begin again. Each moment offers a new beginning, a chance to reset and choose kindness for yourself.",
            "Your tree waits for you patiently. It doesn't judge your absence; it only celebrates your return. You are always welcome here.",
            "Rest is an essential part of growth. Winter is necessary for spring to bloom. Allow yourself the grace to rest when you need it.",
            "Start exactly where you are. You don't need to be perfect or 'ready.' You just need to take one small step. That is enough."
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
