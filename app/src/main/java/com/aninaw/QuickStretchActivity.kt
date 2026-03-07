package com.aninaw

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aninaw.data.AninawDb
import com.aninaw.growth.GrowthManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class QuickStretchActivity : AppCompatActivity() {

    private lateinit var tvStepCounter: TextView
    private lateinit var tvStepTitle: TextView
    private lateinit var tvStepDesc: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvMotivation: TextView
    private lateinit var btnStart: MaterialButton
    private lateinit var btnNext: MaterialButton
    private lateinit var imgStretch: ImageView

    private var currentStepIndex = 0
    private var isRunning = false
    private var timer: CountDownTimer? = null

    private data class Step(
        val title: String,
        val desc: String,
        val durationSeconds: Long,
        val iconRes: Int
    )

    // Define steps
    private val steps = listOf(
        Step("Shoulder Roll", "Roll your shoulders slowly backwards. No force.", 15, R.drawable.ic_stretch),
        Step("Neck Tilt", "Gently tilt your head to one side. Breathe.", 15, R.drawable.ic_stretch),
        Step("Overhead Reach", "Reach up high. Lengthen your spine.", 15, R.drawable.ic_stretch),
        Step("Shake It Out", "Shake your hands and arms gently.", 10, R.drawable.ic_stretch)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_stretch)

        // Bind views
        tvStepCounter = findViewById(R.id.tvStepCounter)
        tvStepTitle = findViewById(R.id.tvStepTitle)
        tvStepDesc = findViewById(R.id.tvStepDesc)
        tvTimer = findViewById(R.id.tvTimer)
        tvMotivation = findViewById(R.id.tvMotivation)
        btnStart = findViewById(R.id.btnStart)
        btnNext = findViewById(R.id.btnNext)
        imgStretch = findViewById(R.id.imgStretch)

        // Back button
        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }

        // Start Button
        btnStart.setOnClickListener {
            if (!isRunning) startTimer()
        }

        // Next Button
        btnNext.setOnClickListener {
            nextStep()
        }

        // Initialize first step
        loadStep(0)
    }

    private fun loadStep(index: Int) {
        currentStepIndex = index
        val step = steps[index]

        tvStepCounter.text = "Step ${index + 1} of ${steps.size}"
        tvStepTitle.text = step.title
        tvStepDesc.text = step.desc
        tvTimer.text = formatTime(step.durationSeconds)
        imgStretch.setImageResource(step.iconRes)

        // Reset state
        btnStart.visibility = View.VISIBLE
        btnStart.text = "Start"
        btnStart.isEnabled = true
        
        btnNext.visibility = View.GONE
        tvMotivation.visibility = View.GONE
        
        timer?.cancel()
        isRunning = false
    }

    private fun startTimer() {
        isRunning = true
        btnStart.isEnabled = false // Disable start while running
        
        tvMotivation.visibility = View.VISIBLE
        tvMotivation.text = getRandomMotivation()
        
        val duration = steps[currentStepIndex].durationSeconds * 1000
        
        timer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // Round up
                tvTimer.text = formatTime((millisUntilFinished + 999) / 1000)
            }

            override fun onFinish() {
                tvTimer.text = "00:00"
                onStepFinished()
            }
        }.start()
    }

    private fun onStepFinished() {
        isRunning = false
        btnStart.visibility = View.GONE // Hide start button when done
        
        btnNext.visibility = View.VISIBLE
        if (currentStepIndex < steps.size - 1) {
            btnNext.text = "Next Step"
        } else {
            btnNext.text = "Finish"
        }
    }

    private fun nextStep() {
        if (currentStepIndex < steps.size - 1) {
            loadStep(currentStepIndex + 1)
        } else {
            finishActivityWithGrowth()
        }
    }

    private fun finishActivityWithGrowth() {
        // Record completion in database
        lifecycleScope.launch {
            val db = AninawDb.getDatabase(this@QuickStretchActivity)
            GrowthManager.recordCheckIn(db) // Counts as activity/check-in for growth
            
            // Finish activity
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun formatTime(seconds: Long): String {
        return String.format("00:%02d", seconds)
    }

    private fun getRandomMotivation(): String {
        return listOf(
            "Breathe deeply.",
            "Relax your shoulders.",
            "Feel the tension melt away.",
            "You are doing great.",
            "Listen to your body.",
            "Gentle movements.",
            "Stay present.",
            "Just for you."
        ).random()
    }
    
    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}
