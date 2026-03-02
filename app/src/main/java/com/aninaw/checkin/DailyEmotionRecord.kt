//DailyEmotionRecord.kt
package com.aninaw.checkin

data class DailyEmotionRecord(
    val ymd: String,
    val primary: Emotion? = null,
    val secondary: Emotion? = null,
    val entriesCount: Int = 0
)
