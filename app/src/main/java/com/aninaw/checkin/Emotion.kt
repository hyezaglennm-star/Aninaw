package com.aninaw.checkin

enum class Emotion {
    CALM, STEADY, FOGGY, MIXED,
    HEAVY, TENSE, TIRED, UNSURE,
    UNDISCLOSED,
    HAPPY, SHY, NEUTRAL, ANXIOUS, SAD;

    companion object {
        fun fromLabel(raw: String?): Emotion {
            val t = (raw ?: "").trim()
            val normalized = if (t.equals("Okay", ignoreCase = true)) "Steady" else t

            return when (normalized.lowercase()) {
                "calm" -> CALM
                "steady" -> STEADY
                "foggy" -> FOGGY
                "mixed" -> MIXED
                "heavy" -> HEAVY
                "tense" -> TENSE
                "tired" -> TIRED
                "unsure" -> UNSURE
                "undisclosed" -> UNDISCLOSED
                "happy" -> HAPPY
                "shy" -> SHY
                "neutral" -> NEUTRAL
                "anxious" -> ANXIOUS
                "sad" -> SAD
                else -> UNDISCLOSED
            }
        }
    }
}
