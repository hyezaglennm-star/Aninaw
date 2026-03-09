package com.aninaw

import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import com.aninaw.data.AninawDb
import com.aninaw.data.treering.TreeRingMemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class DaysTimelineActivity : AppCompatActivity() {

    private val dayFormatter = DateTimeFormatter.ofPattern("MMMM d", Locale.getDefault())
    private val dot = "•"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_days_timeline)

        com.aninaw.util.BackButton.bind(this)

        findViewById<TextView>(R.id.tvHeaderTitle).text = "Your days"
        findViewById<TextView>(R.id.tvHeaderSub).text = "Each ring quietly holds a day."

        lifecycleScope.launch {
            val db = AninawDb.getDatabase(this@DaysTimelineActivity)
            val repo = TreeRingMemoryRepository(db)

            val end = LocalDate.now()
            val start = end.minusDays(13) // last 14 days

            val logs = withContext(Dispatchers.IO) { repo.getRange(start, end) }
            val byDay = logs.groupBy { it.date }
                .mapValues { (_, list) ->
                    list.sortedByDescending { it.timestamp ?: 0L }.first()
                }

            // Weekly summary (last 7 days)
            val weekStart = end.minusDays(6)
            val weekDays = (0..6).map { weekStart.plusDays(it.toLong()).toString() }
            val weekCounts = weekDays
                .mapNotNull { byDay[it]?.emotion }
                .groupingBy { normalize(it) }
                .eachCount()

            val weekLine = weekCounts.entries
                .sortedByDescending { it.value }
                .joinToString("  $dot  ") { "${it.value} ${it.key}" }
                .ifBlank { "No check-ins yet" }
            findViewById<TextView>(R.id.tvWeekSummary).text = "This week: $weekLine"

            // Day X of your tree
            val firstUse = getSharedPreferences("AninawPrefs", MODE_PRIVATE)
                .getLong("first_use_epoch_day", epochDayNow())
            val dayNum = ((epochDayNow() - firstUse) + 1).coerceAtLeast(1)
            findViewById<TextView>(R.id.tvDayOfTree).text = "Day $dayNum of your tree"

            // Build day list
            val listDays = findViewById<LinearLayout>(R.id.listDays)
            listDays.removeAllViews()

            for (i in 0..13) {
                val date = end.minusDays(i.toLong())
                val card = layoutInflater.inflate(R.layout.item_day_entry, listDays, false)
                val tvDate = card.findViewById<TextView>(R.id.tvItemDate)
                val tvMood = card.findViewById<TextView>(R.id.tvItemMood)
                val viewMoodDot = card.findViewById<View>(R.id.viewMoodDot)
                val tvMeta = card.findViewById<TextView>(R.id.tvItemMeta)
                val tvNote = card.findViewById<TextView>(R.id.tvItemNote)
                val tvTags = card.findViewById<TextView>(R.id.tvItemTags)

                val todayMark = if (i == 0) " • Today" else ""
                tvDate.text = "${date.format(dayFormatter)}$todayMark"

                val iso = date.toString()
                val entry = byDay[iso]
                
                if (entry != null) {
                    val emo = entry.emotion ?: "Calm"
                    
                    val icon = when {
                        emo.contains("Happy", true) || emo.contains("Calm", true) || emo.contains("Grateful", true) -> "😌"
                        emo.contains("Love", true) || emo.contains("Loved", true) -> "🥰"
                        emo.contains("Okay", true) || emo.contains("Steady", true) -> "🙂"
                        emo.contains("Shy", true) || emo.contains("Anxious", true) || emo.contains("Stress", true) -> "😐"
                        emo.contains("Difficult", true) || emo.contains("Tense", true) -> "😟"
                        emo.contains("Sad", true) || emo.contains("Heavy", true) || emo.contains("Tired", true) -> "😔"
                        emo.contains("Neutral", true) -> "😐"
                        else -> "🌱"
                    }
                    tvMood.text = "$icon $emo"
                    
                    // Set dot color
                    val colorRes = when(emo) {
                        "Happy", "Calm", "Grateful" -> 0xFF8BA691.toInt() // green
                        "Okay", "Steady", "Loved" -> 0xFFD4B072.toInt() // gold
                        "Heavy", "Sad", "Tired", "Difficult" -> 0xFF8C9BA5.toInt() // blue-grey
                        "Shy", "Anxious", "Tense" -> 0xFFBFA093.toInt() // dusty rose
                        else -> 0xFF9AA49F.toInt() // grey
                    }
                    viewMoodDot.background.setTint(colorRes)
                    viewMoodDot.visibility = View.VISIBLE

                    val timeStr = entry.timestamp?.let { ts ->
                        android.text.format.DateFormat.format("h:mm a", ts).toString()
                    } ?: ""
                    tvMeta.text = "Checked in $timeStr"

                    val line = likertMessage(entry.intensity)
                    tvNote.text = line
                    tvNote.visibility = View.VISIBLE
                    
                    // Tags (placeholder until DB supports tags)
                    // tvTags.text = "Used: Breathing • Quick stretch"
                    // tvTags.visibility = View.VISIBLE
                    tvTags.visibility = View.GONE

                } else {
                    tvMood.text = "No check-in recorded"
                    viewMoodDot.visibility = View.GONE
                    tvMeta.text = "This day passed quietly."
                    tvNote.visibility = View.GONE
                    tvTags.visibility = View.GONE
                }

                listDays.addView(card)
                (card.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin =
                    (resources.displayMetrics.density * 10).toInt()
            }
        }
    }

    private fun normalize(label: String?): String {
        val t = (label ?: "").trim().lowercase(Locale.getDefault())
        return when {
            t.contains("calm") -> "Calm"
            t.contains("okay") || t.contains("steady") -> "Okay"
            t.contains("heavy") || t.contains("difficult") || t.contains("sad") -> "Heavy"
            t.contains("happy") || t.contains("love") || t.contains("loved") -> "Calm"
            else -> label ?: "Calm"
        }
    }

    private fun likertMessage(intensity: Float?): String {
        val v = (intensity ?: 0.5f).coerceIn(0f, 1f)
        val level = when {
            v >= 0.8f -> 5
            v >= 0.6f -> 4
            v >= 0.4f -> 3
            v >= 0.2f -> 2
            else -> 1
        }
        return when (level) {
            1 -> "You stayed present today."
            2 -> "This day may have felt harder to carry."
            3 -> "This day passed quietly."
            4 -> "You kept a steady pace today."
            else -> "A calm moment found you today."
        }
    }

    private fun epochDayNow(): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LocalDate.now().toEpochDay()
        } else {
            System.currentTimeMillis() / 86_400_000L
        }
    }
}
