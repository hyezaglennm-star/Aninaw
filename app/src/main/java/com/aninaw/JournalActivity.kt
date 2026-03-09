package com.aninaw

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aninaw.data.AninawDb
import com.aninaw.data.treering.TreeRingMemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class JournalActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_journal)
        
        findViewById<android.view.View>(R.id.btnBack).setOnClickListener {
            finish()
        }

        val prefs = getSharedPreferences("AninawPrefs", Context.MODE_PRIVATE)
        val nickname = prefs.getString("user_nickname", "Friend")
        findViewById<TextView>(R.id.tvGreeting).text = "Hi, $nickname"
        
        setupCalendarStrip()

        findViewById<android.view.View>(R.id.cardMorning).setOnClickListener {
            openEditor("Morning Reflection", "MORNING")
        }

        findViewById<android.view.View>(R.id.cardCoreFeelings).setOnClickListener {
            startActivity(Intent(this, EmotionStatsActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        findViewById<android.view.View>(R.id.cardQuickPause).setOnClickListener {
            openEditor("Pause & Reflect", "QUICK")
        }
        
        findViewById<android.view.View>(R.id.cardQuickIntention).setOnClickListener {
            openEditor("Set Intentions", "INTENTION")
        }

        updatePauseAndNoticePrompt()

        findViewById<android.view.View>(R.id.cardPromptOne).setOnClickListener {
            val body = findViewById<TextView>(R.id.tvPromptOneBody).text?.toString() ?: "Pause and notice"
            openEditor(body, "QUICK")
        }
    }
    
    private fun setupCalendarStrip() {
        val container = findViewById<LinearLayout>(R.id.calendarStrip)
        val today = LocalDate.now()
        // Show 3 days before and 3 days after, or just current week.
        // Let's do past 3 days, today, next 3 days
        val startDay = today.minusDays(3)
        
        for (i in 0 until 7) {
            val date = startDay.plusDays(i.toLong())
            val view = LayoutInflater.from(this).inflate(R.layout.item_calendar_day_mock, container, false)
            
            val tvDayName = view.findViewById<TextView>(R.id.tvDayName)
            val tvDayNumber = view.findViewById<TextView>(R.id.tvDayNumber)
            
            tvDayName.text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            tvDayNumber.text = date.dayOfMonth.toString()
            
            if (date.isEqual(today)) {
                // Highlight today
                tvDayNumber.background = ContextCompat.getDrawable(this, R.drawable.bg_calendar_selected)
                tvDayNumber.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                tvDayName.setTextColor(ContextCompat.getColor(this, R.color.aninaw_text_primary))
                tvDayName.typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            
            // Add click listener? For now just visual.
            container.addView(view)
            
            // Layout params weight
            val params = view.layoutParams as LinearLayout.LayoutParams
            params.weight = 1f
            view.layoutParams = params
        }
    }

    private fun openEditor(prompt: String, type: String) {
        val intent = Intent(this, JournalEditorActivity::class.java).apply {
            putExtra(JournalEditorActivity.EXTRA_PROMPT, prompt)
            putExtra(JournalEditorActivity.EXTRA_TYPE, type)
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private enum class EmoCategory { STRESS, SAD, ANGER, CALM, OTHER }

    private fun categorizeEmotionLabel(raw: String?): EmoCategory {
        val t = (raw ?: "").trim().lowercase()
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

    private fun computeReflectionLine(mood: String?, intensity: Float?, recentCats: List<EmoCategory>): String {
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
            level <= 3 -> when ((mood ?: "").lowercase()) {
                "calm", "happy", "loved", "steady", "okay" -> "Write whatever is on your mind right now."
                "heavy", "difficult", "sad", "tired" -> "Write a few words about what today has been like so far."
                else -> "Notice what is here, right now, without judgment."
            }
            else -> "Notice what is here, right now, without judgment."
        }
    }

    private fun updatePauseAndNoticePrompt() {
        lifecycleScope.launch {
            val db = AninawDb.getDatabase(this@JournalActivity)
            val ringRepo = TreeRingMemoryRepository(db)
            val today = LocalDate.now()
            val todayIso = today.toString()

            val latestCheckIn = withContext(Dispatchers.IO) {
                val list = ringRepo.getRange(today, today)
                list.maxByOrNull { it.timestamp ?: 0L }
            }

            val latestJournal = withContext(Dispatchers.IO) {
                runCatching { db.journalDao().getLatestForDate(todayIso) }.getOrNull()
            }

            val mood: String? = when {
                latestJournal != null && (latestCheckIn?.timestamp ?: 0L) < (latestJournal.timestamp) -> latestJournal.mood
                else -> latestCheckIn?.emotion
            }
            val intensity: Float? = latestCheckIn?.intensity

            val start = today.minusDays(6)
            val recentLogs = withContext(Dispatchers.IO) { ringRepo.getRange(start, today) }
            val cats = recentLogs.map { categorizeEmotionLabel(it.emotion) }

            val line = computeReflectionLine(mood, intensity, cats)
            findViewById<TextView>(R.id.tvPromptOneBody).text = line
        }
    }
}
