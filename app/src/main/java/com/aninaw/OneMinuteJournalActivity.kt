package com.aninaw

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aninaw.data.AninawDb
import com.aninaw.data.journal.JournalEntity
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class OneMinuteJournalActivity : AppCompatActivity() {

    private lateinit var tvTimer: TextView
    private lateinit var etJournal: EditText
    private lateinit var btnSave: MaterialButton
    private lateinit var btnDone: MaterialButton
    
    private var timer: CountDownTimer? = null
    private val START_TIME_IN_MILLIS: Long = 60000
    private var timeLeftInMillis: Long = START_TIME_IN_MILLIS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_one_minute_journal)

        tvTimer = findViewById(R.id.tvTimer)
        etJournal = findViewById(R.id.etJournal)
        btnSave = findViewById(R.id.btnSave)
        btnDone = findViewById(R.id.btnDone)

        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            saveEntry()
        }

        btnDone.setOnClickListener {
            finish()
        }

        startTimer()
    }

    private fun startTimer() {
        timer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateCountDownText()
            }

            override fun onFinish() {
                timeLeftInMillis = 0
                updateCountDownText()
                // Optionally, we could disable editing or just show "Time's up"
            }
        }.start()
    }

    private fun updateCountDownText() {
        val minutes = (timeLeftInMillis / 1000) / 60
        val seconds = (timeLeftInMillis / 1000) % 60
        val timeFormatted = String.format("%02d:%02d", minutes, seconds)
        tvTimer.text = timeFormatted
    }

    private fun saveEntry() {
        val content = etJournal.text.toString().trim()
        if (content.isBlank()) {
            Toast.makeText(this, "Journal is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val db = AninawDb.getDatabase(this)
        val entry = JournalEntity(
            date = LocalDate.now().toString(),
            timestamp = System.currentTimeMillis(),
            type = "ONE_MINUTE",
            prompt = "What has been on your mind today?",
            content = content,
            mood = null
        )

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                db.journalDao().insert(entry)
            }
            Toast.makeText(this@OneMinuteJournalActivity, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}
