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

        val stressCount = recentCats.count { it == EmoCategory.STRESS }
        val sadCount = recentCats.count { it == EmoCategory.SAD }
        val angerCount = recentCats.count { it == EmoCategory.ANGER }
        val calmCount = recentCats.count { it == EmoCategory.CALM }

        return when {
            level >= 5 -> "Take slow breaths for one minute. Let your shoulders relax."
            level >= 4 -> "Your body might need a short reset."
            stressCount >= 3 -> "Slow down. What’s one thing you can control right now?"
            sadCount >= 3 -> "Be gentle with yourself today. What needs care?"
            angerCount >= 3 -> "Release some tension—try a short shake, walk, or exhale."
            calmCount >= 3 -> "Name one thing you’re grateful for today."
            level <= 3 -> when ((mood ?: "").lowercase(Locale.getDefault())) {
                "calm", "happy", "loved", "steady", "okay" -> "Write whatever is on your mind right now."
                "heavy", "difficult", "sad", "tired" -> "Write a few words about what today has been like so far."
                else -> "Notice what is here, right now, without judgment."
            }
            else -> "Notice what is here, right now, without judgment."
        }
    }
}
