package com.aninaw

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class QuickStretchActivity : AppCompatActivity() {

    // UI
    private lateinit var tvTitle: TextView
    private lateinit var tvHint: TextView
    private lateinit var tvStepTitle: TextView
    private lateinit var tvStepDesc: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvTimerLabel: TextView
    private lateinit var tvCompletion: TextView
    private lateinit var layoutProgress: LinearLayout
    
    private lateinit var btnPause: MaterialButton
    private lateinit var btnNext: MaterialButton

    // State
    private var currentStep = 0 // 0=Intro, 1=Shoulder, 2=Neck, 3=Chest, 4=Breath, 5=Done
    private var isPaused = false
    private var timer: CountDownTimer? = null
    private var timeLeftMillis = 0L
    
    // Config
    private val steps = listOf(
        // (Title, Desc, DurationSec)
        Triple("Shoulder roll", "Roll your shoulders slowly back and down. No force.", 15),
        Triple("Neck stretch", "Tilt your head gently to one side. Breathe into the space.", 15),
        Triple("Chest stretch", "Open your arms wide. Lift your chest slightly.", 15),
        Triple("Breathing", "Take three slow breaths. In through nose, out through mouth.", 20)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_stretch)

        com.aninaw.util.BackButton.bind(this)

        bindViews()
        setupListeners()
        
        // Start at Intro state
        showIntroState()
    }

    private fun bindViews() {
        tvTitle = findViewById(R.id.tvTitle)
        tvHint = findViewById(R.id.tvHint)
        tvStepTitle = findViewById(R.id.tvStepTitle)
        tvStepDesc = findViewById(R.id.tvStepDesc)
        tvTimer = findViewById(R.id.tvTimer)
        tvTimerLabel = findViewById(R.id.tvTimerLabel)
        tvCompletion = findViewById(R.id.tvCompletion)
        layoutProgress = findViewById(R.id.layoutProgress)
        
        btnPause = findViewById(R.id.btnPause)
        btnNext = findViewById(R.id.btnNext)
    }

    private fun setupListeners() {
        btnPause.setOnClickListener {
            if (currentStep == 0) {
                // "Skip" behavior on intro
                finish()
            } else {
                togglePause()
            }
        }

        btnNext.setOnClickListener {
            if (currentStep == 0) {
                // "Start" behavior
                startStep(1)
            } else if (currentStep <= steps.size) {
                // "Next" behavior
                startStep(currentStep + 1)
            } else {
                // "Done" behavior
                finish()
            }
        }
    }

    private fun showIntroState() {
        currentStep = 0
        tvStepTitle.text = "Ready to reset?"
        tvStepDesc.text = "This takes about 1 minute. Follow along gently."
        tvTimer.visibility = View.GONE
        tvTimerLabel.visibility = View.GONE
        layoutProgress.visibility = View.GONE
        tvCompletion.visibility = View.GONE
        
        btnPause.text = "Skip"
        btnNext.text = "Start"
    }

    private fun startStep(stepIndex: Int) {
        if (stepIndex > steps.size) {
            showCompletionState()
            return
        }

        currentStep = stepIndex
        val (title, desc, duration) = steps[stepIndex - 1]

        tvStepTitle.text = title
        tvStepDesc.text = desc
        
        // UI updates
        tvTimer.visibility = View.VISIBLE
        tvTimerLabel.visibility = View.VISIBLE
        layoutProgress.visibility = View.VISIBLE
        tvCompletion.visibility = View.GONE
        
        btnPause.text = "Pause"
        btnNext.text = "Next"
        
        updateProgressDots(stepIndex)
        startTimer(duration * 1000L)
    }

    private fun showCompletionState() {
        currentStep = 5 // Done state
        timer?.cancel()
        
        tvStepTitle.text = "All done"
        tvStepDesc.visibility = View.GONE
        tvTimer.visibility = View.GONE
        tvTimerLabel.visibility = View.GONE
        layoutProgress.visibility = View.GONE
        
        tvCompletion.text = "Nice. Your body should feel lighter."
        tvCompletion.visibility = View.VISIBLE
        
        btnPause.visibility = View.GONE
        btnNext.text = "Done"
    }

    private fun startTimer(millis: Long) {
        timer?.cancel()
        timeLeftMillis = millis
        isPaused = false
        btnPause.text = "Pause"

        timer = object : CountDownTimer(millis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftMillis = millisUntilFinished
                updateTimerUI(millisUntilFinished)
            }

            override fun onFinish() {
                timeLeftMillis = 0
                updateTimerUI(0)
                // Auto-advance or wait for user? Let's wait for user "Next" or show "Done" text
                btnNext.text = "Next"
            }
        }.start()
    }

    private fun togglePause() {
        if (isPaused) {
            startTimer(timeLeftMillis)
        } else {
            timer?.cancel()
            isPaused = true
            btnPause.text = "Resume"
        }
    }

    private fun updateTimerUI(millis: Long) {
        val sec = (millis / 1000).toInt()
        tvTimer.text = sec.toString()
        tvTimerLabel.text = "$sec seconds"
    }

    private fun updateProgressDots(step: Int) {
        for (i in 0 until layoutProgress.childCount) {
            val dot = layoutProgress.getChildAt(i)
            val bg = if (i == step - 1) R.drawable.bg_progress_dot_active else R.drawable.bg_progress_dot_inactive
            dot.setBackgroundResource(bg)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}

