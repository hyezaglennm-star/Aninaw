//JournalActivity.kt
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

        findViewById<TextView>(R.id.tvSeeAllMain).setOnClickListener {
            startActivity(Intent(this, JournalHistoryActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
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

    override fun onResume() {
        super.onResume()
        updatePauseAndNoticePrompt()
    }

    private fun updatePauseAndNoticePrompt() {
        // "Pause and Notice" now prioritizes LATEST JOURNAL MOOD for Adaptive Intelligence.
        // It does NOT use the Quick Check-in (Home Screen) mood.
        lifecycleScope.launch {
            val db = AninawDb.getDatabase(this@JournalActivity)
            val todayIso = LocalDate.now().toString()

            val latestJournal = withContext(Dispatchers.IO) {
                runCatching { db.journalDao().getLatestForDate(todayIso) }.getOrNull()
            }
            
            val mood = latestJournal?.mood
            
            // If no journal mood yet, fall back to random generic prompts
            if (mood.isNullOrBlank()) {
                 val prompts = listOf(
                     "What is one feeling or thought that has been quietly asking for your attention today?",
                     "If your body could speak right now, what would it say?",
                     "What is one thing you can soften around right now?",
                     "Where is your breath landing in your body?",
                     "What is the texture of your mind right now?"
                 )
                 findViewById<TextView>(R.id.tvPromptOneBody).text = prompts.random()
            } else {
                // Adaptive Logic based on Journal Mood
                // We mock intensity/history here or fetch if needed, but primary trigger is Journal Mood.
                val ringRepo = TreeRingMemoryRepository(db)
                val today = LocalDate.now()
                val start = today.minusDays(6)
                
                // Get recent history for context (optional but good for "Adaptive")
                val recentLogs = withContext(Dispatchers.IO) { ringRepo.getRange(start, today) }
                val cats = recentLogs.map { com.aninaw.prompts.AdaptivePromptEngine.categorizeEmotionLabel(it.emotion) }
                
                // Use Journal Mood data for "Pause and Notice" message
                val line = com.aninaw.prompts.AdaptivePromptEngine.computeReflectionLine(mood, 0.5f, cats)
                findViewById<TextView>(R.id.tvPromptOneBody).text = line
            }
        }
    }
}
