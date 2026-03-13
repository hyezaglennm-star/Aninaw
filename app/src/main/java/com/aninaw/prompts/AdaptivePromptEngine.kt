package com.aninaw.prompts

import java.util.Locale

object AdaptivePromptEngine {

    enum class EmoCategory { STRESS, SAD, ANGER, CALM, OTHER }

    fun categorizeEmotionLabel(raw: String?): EmoCategory {
        val t = (raw ?: "").trim().lowercase(Locale.getDefault())
        return when {
            t.contains("tense") || t.contains("anx") || t.contains("stress") || t.contains("overwhelm") || t.contains("shy") -> EmoCategory.STRESS
            t.contains("sad") || t.contains("heavy") || t.contains("fog") || t.contains("tired") || t.contains("difficult") -> EmoCategory.SAD
            t.contains("anger") || t.contains("angry") || t.contains("frustrat") -> EmoCategory.ANGER
            t.contains("calm") || t.contains("steady") || t.contains("happy") || t.contains("love") || t.contains("grateful") || t == "okay" || t == "steady" -> EmoCategory.CALM
            else -> EmoCategory.OTHER
        }
    }

    private fun intensityToLevel01to5(v: Float?): Int {
        val x = (v ?: 0.5f).coerceIn(0f, 1f)
        return when {
            x >= 0.8f -> 5
            x >= 0.6f -> 4
            x >= 0.4f -> 3
            x >= 0.2f -> 2
            else -> 1
        }
    }

    fun computeReflectionLine(mood: String?, intensity: Float?, recentCats: List<EmoCategory>): String {
        val level = intensityToLevel01to5(intensity)
        val m = (mood ?: "").lowercase(Locale.getDefault())

        // 1. Prioritize HIGH INTENSITY current emotion
        if (level >= 5) {
            return "Take slow breaths for one minute. Let your shoulders relax."
        }
        if (level >= 4) {
            return "Your body might need a short reset."
        }

        // 2. Then check CURRENT mood specifically (before checking history)
        // This fixes the bug where "Calm" users got "Slow down" messages due to history
        if (m.contains("calm") || m.contains("happy") || m.contains("love") || m.contains("grateful")) {
            return "Name one thing you’re grateful for today."
        }
        if (m.contains("steady") || m == "okay") {
            return "Write whatever is on your mind right now."
        }
        if (m.contains("sad") || m.contains("heavy") || m.contains("tired") || m.contains("difficult")) {
            return "Be gentle with yourself today. What needs care?"
        }
        if (m.contains("tense") || m.contains("anx") || m.contains("stress")) {
            return "Slow down. What’s one thing you can control right now?"
        }

        // 3. Fallback to HISTORY patterns only if current mood is vague or missing
        val stressCount = recentCats.count { it == EmoCategory.STRESS }
        val sadCount = recentCats.count { it == EmoCategory.SAD }
        
        return when {
            stressCount >= 3 -> "Slow down. What’s one thing you can control right now?"
            sadCount >= 3 -> "Be gentle with yourself today. What needs care?"
            else -> "Notice what is here, right now, without judgment."
        }
    }
}
