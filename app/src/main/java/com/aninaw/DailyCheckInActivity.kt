package com.aninaw

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.aninaw.data.AninawDb
import com.aninaw.data.treering.TreeRingMemoryRepository
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class DailyCheckInActivity : AppCompatActivity() {

    private var selectedMood: String? = null
    private var selectedIntensity: Float = 0.5f // Default medium

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daily_checkin)

        val btnConfirm = findViewById<MaterialButton>(R.id.btnConfirm)
        val tvMoodLabel = findViewById<TextView>(R.id.tvMoodLabel)
        val btnSkip = findViewById<TextView>(R.id.btnSkip)

        val moods = listOf(
            findViewById<ImageView>(R.id.btnMood1) to "Difficult",
            findViewById<ImageView>(R.id.btnMood2) to "Heavy",
            findViewById<ImageView>(R.id.btnMood3) to "Neutral",
            findViewById<ImageView>(R.id.btnMood4) to "Okay",
            findViewById<ImageView>(R.id.btnMood5) to "Calm"
        )

        fun selectMood(view: ImageView, label: String) {
            selectedMood = label
            tvMoodLabel.text = label
            tvMoodLabel.setTextColor(0xFF3F4A45.toInt()) // Darker color
            btnConfirm.isEnabled = true
            btnConfirm.alpha = 1.0f

            moods.forEach { (v, _) ->
                if (v == view) {
                    v.alpha = 1.0f
                    v.scaleX = 1.2f
                    v.scaleY = 1.2f
                    // v.setBackgroundResource(R.drawable.bg_tile_soft_selector) // Optional: remove background if we just want the icon to pop
                    v.isSelected = true
                } else {
                    v.alpha = 0.4f
                    v.scaleX = 1.0f
                    v.scaleY = 1.0f
                    // v.background = null
                    v.isSelected = false
                }
            }
        }

        moods.forEach { (view, label) ->
            view.setOnClickListener { selectMood(view, label) }
        }

        btnConfirm.setOnClickListener {
            val mood = selectedMood ?: return@setOnClickListener
            saveAndProceed(mood)
        }

        btnSkip.setOnClickListener {
            proceed()
        }
    }

    private fun saveAndProceed(mood: String) {
        val db = AninawDb.getDatabase(this)
        val ringRepo = TreeRingMemoryRepository(db)
        
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // Log quick check-in
                ringRepo.logQuickCheckIn(
                    date = LocalDate.now(),
                    emotion = mood,
                    intensity = selectedIntensity,
                    capacity = "STEADY", // Default
                    note = null
                )
            }

            // Tree Growth
            TreeGrowthManager(this@DailyCheckInActivity).onQuickCheckInCompleted()

            withContext(Dispatchers.Main) {
                Toast.makeText(this@DailyCheckInActivity, "Check-in saved", Toast.LENGTH_SHORT).show()
                proceed(mood)
            }
        }
    }

    private fun proceed(mood: String? = null) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(MainActivity.EXTRA_CHECKIN_COMPLETED, true)
            if (mood != null) {
                putExtra(MainActivity.EXTRA_CHECKIN_MOOD, mood)
            }
        }
        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
