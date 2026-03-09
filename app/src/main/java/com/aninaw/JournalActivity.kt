package com.aninaw

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
}
