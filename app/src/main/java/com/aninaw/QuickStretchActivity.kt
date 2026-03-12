package com.aninaw

import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    private lateinit var imgStretch: android.widget.ImageView
    private lateinit var pulseField: View
    
    private lateinit var btnPause: MaterialButton
    private lateinit var btnNext: MaterialButton

    // State
    private var currentStep = 0 // 0=Intro, 1=Shoulder, 2=Neck, 3=Chest, 4=Breath, 5=Done
    private var isPaused = false
    private var isStepActive = false
    private var timer: CountDownTimer? = null
    private var timeLeftMillis = 0L

    // Breathing Animation
    private var breathLoopRunning = false
    private var breathAnimator: ValueAnimator? = null
    private var gd: GradientDrawable? = null
    private val ease = AccelerateDecelerateInterpolator()
    private var inhaleR = 0f
    private var exhaleR = 0f
    private val alphaMin = 0.88f
    private val alphaMax = 1.00f
    private var scaleMin = 0.985f
    private var scaleMax = 1.07f
    
    // Config
    // (Title, Desc, DurationSec, ImageRes, Phase2ImageRes?)
    private val steps = listOf(
        Step("Shoulder roll", "Roll your shoulders slowly back and down. No force.", 15, R.drawable.shoulder_first5, R.drawable.shoulder_6s),
        Step("Neck stretch", "Tilt your head gently to the left. Breathe.", 10, R.drawable.neck_stretch_left, R.drawable.neck_stretch),
        Step("Chest stretch", "Open your arms wide. Lift your chest slightly.", 15, R.drawable.chest_stretch, null),
        Step("Breathing", "Take three slow breaths. In through nose, out through mouth.", 20, R.drawable.initial_position, null)
    )

    data class Step(
        val title: String, 
        val desc: String, 
        val duration: Int, 
        val imgRes: Int,
        val phase2ImgRes: Int? // If non-null, switch to this halfway
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
        imgStretch = findViewById(R.id.imgStretch)
        pulseField = findViewById(R.id.pulseField)
        
        btnPause = findViewById(R.id.btnPause)
        btnNext = findViewById(R.id.btnNext)

        // Setup GradientDrawable for breathing
        gd = (ContextCompat.getDrawable(this, R.drawable.soft_pulse_field)!!.mutate() as GradientDrawable)
        pulseField.background = gd
        pulseField.backgroundTintList = null
        pulseField.backgroundTintMode = null
        // pulseField.setLayerType(View.LAYER_TYPE_SOFTWARE, null) // Optional, sometimes helps with gradient radius
    }

    private fun setupListeners() {
        btnPause.setOnClickListener {
            if (currentStep == 0) {
                // "Skip" behavior on intro
                finish()
            } else if (currentStep <= steps.size) {
                if (!isStepActive) {
                    // "Skip" behavior: skip this step entirely
                    startStep(currentStep + 1)
                } else {
                    // "Pause" behavior during step
                    togglePause()
                }
            }
        }

        btnNext.setOnClickListener {
            if (currentStep == 0) {
                // "Start" behavior
                startStep(1)
            } else if (currentStep <= steps.size) {
                if (!isStepActive) {
                    // "Start" behavior: begin the timer
                    activateStep()
                } else {
                    // "Next" behavior: finish step early
                    startStep(currentStep + 1)
                }
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
        
        imgStretch.setImageResource(R.drawable.initial_position)
        imgStretch.visibility = View.VISIBLE
        stopBreathAnimation()

        btnPause.text = "Skip"
        btnNext.text = "Start"
    }

    private fun startStep(stepIndex: Int) {
        if (stepIndex > steps.size) {
            showCompletionState()
            return
        }

        currentStep = stepIndex
        isStepActive = false
        val step = steps[stepIndex - 1]

        tvStepTitle.text = step.title
        tvStepDesc.text = step.desc
        
        // Update image (start with phase 1)
        imgStretch.setImageResource(step.imgRes)
        
        // Check if this is the Breathing step
        if (step.title == "Breathing") {
            imgStretch.visibility = View.GONE
            pulseField.visibility = View.VISIBLE
            pulseField.alpha = 1f
            
            // Prepare breathing circle but don't animate yet
            breathLoopRunning = false
            breathAnimator?.cancel()
            breathAnimator = null
            
            pulseField.post {
                computeRadiiIfNeeded()
                if (inhaleR > 0) {
                    gd?.gradientRadius = inhaleR
                    gd?.invalidateSelf()
                    
                    pulseField.scaleX = scaleMin
                    pulseField.scaleY = scaleMin
                    pulseField.alpha = alphaMin
                }
            }
        } else {
            imgStretch.visibility = View.VISIBLE
            pulseField.visibility = View.GONE
            stopBreathAnimation()
        }
        
        // UI updates
        tvTimer.visibility = View.VISIBLE
        tvTimerLabel.visibility = View.VISIBLE
        layoutProgress.visibility = View.VISIBLE
        tvCompletion.visibility = View.GONE
        
        // "Before" state buttons
        btnPause.text = "Skip"
        btnNext.text = "Start"
        
        updateProgressDots(stepIndex)
        updateTimerUI(step.duration * 1000L)
    }

    private fun activateStep() {
        if (currentStep < 1 || currentStep > steps.size) return
        
        isStepActive = true
        val step = steps[currentStep - 1]
        
        btnPause.text = "Pause"
        btnNext.text = "Next"
        
        if (step.title == "Breathing") {
            startBreathAnimation()
        }
        
        startTimer(step.duration * 1000L, step)
    }

    private fun showCompletionState() {
        currentStep = 5 // Done state
        timer?.cancel()
        stopBreathAnimation()
        
        tvStepTitle.text = "All done"
        tvStepDesc.visibility = View.GONE
        tvTimer.visibility = View.GONE
        tvTimerLabel.visibility = View.GONE
        layoutProgress.visibility = View.GONE
        
        // Show relaxed image
        imgStretch.setImageResource(R.drawable.shoulder_6s) // Reuse relaxed pose or initial
        imgStretch.visibility = View.VISIBLE
        
        tvCompletion.text = "Nice. Your body should feel lighter."
        tvCompletion.visibility = View.VISIBLE
        
        btnPause.visibility = View.GONE
        btnNext.text = "Done"
    }

    private fun startTimer(millis: Long, step: Step) {
        timer?.cancel()
        timeLeftMillis = millis
        isPaused = false
        btnPause.text = "Pause"

        val totalDuration = step.duration * 1000L
        
        timer = object : CountDownTimer(millis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftMillis = millisUntilFinished
                updateTimerUI(millisUntilFinished)
                
                // Check for phase switch (e.g. half time)
                // Use totalDuration to determine halfway point, regardless of resume
                val elapsed = totalDuration - millisUntilFinished
                val halfTime = totalDuration / 2
                
                if (step.phase2ImgRes != null && elapsed >= halfTime) {
                    // Switch to second phase image (e.g. right side)
                    imgStretch.setImageResource(step.phase2ImgRes)
                    
                    // Optional: Update text for Neck Stretch specifically
                    if (step.title.contains("Neck")) {
                        tvStepDesc.text = "Now gently tilt to the right."
                    }
                }
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
            // Resume with current step config
            if (currentStep > 0 && currentStep <= steps.size) {
                 startTimer(timeLeftMillis, steps[currentStep - 1])
                 if (steps[currentStep - 1].title == "Breathing") {
                     resumeBreathAnimation()
                 }
            }
        } else {
            timer?.cancel()
            isPaused = true
            btnPause.text = "Resume"
            if (currentStep > 0 && currentStep <= steps.size && steps[currentStep - 1].title == "Breathing") {
                pauseBreathAnimation()
            }
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

    // --- Breathing Animation Logic ---

    private fun computeRadiiIfNeeded() {
        if (inhaleR > 0f && exhaleR > 0f) return
        val size = pulseField.width.coerceAtMost(pulseField.height).toFloat()
        if (size <= 0f) return
        inhaleR = size * 0.30f
        exhaleR = size * 0.70f
    }

    private fun startBreathAnimation() {
        pulseField.post {
            computeRadiiIfNeeded()
            if (inhaleR <= 0f || exhaleR <= 0f) return@post

            breathLoopRunning = true
            breathAnimator?.cancel()

            // Simple Inhale-Exhale loop (4s in, 6s out like Calm & Release)
            breathAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 10000L // 10s cycle
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = null // Linear traversal of the 0..1 timeline, we apply easing inside

                addUpdateListener { a ->
                    val progress = a.animatedValue as Float
                    // 0..0.4 = Inhale (4s), 0.4..1.0 = Exhale (6s)
                    
                    val t: Float
                    val isInhale = progress <= 0.4f
                    
                    if (isInhale) {
                        // Normalize 0..0.4 to 0..1
                        val localP = progress / 0.4f
                        t = ease.getInterpolation(localP)
                    } else {
                        // Normalize 0.4..1.0 to 1..0 (exhale goes back down)
                        val localP = (progress - 0.4f) / 0.6f
                        t = ease.getInterpolation(1f - localP)
                    }

                    // Apply visual changes
                    // Radius
                    val r = inhaleR + (exhaleR - inhaleR) * t
                    gd?.gradientRadius = r
                    gd?.invalidateSelf()

                    // Alpha & Scale
                    pulseField.alpha = alphaMin + (alphaMax - alphaMin) * t
                    val s = scaleMin + (scaleMax - scaleMin) * t
                    pulseField.scaleX = s
                    pulseField.scaleY = s
                    pulseField.invalidate()
                }
                start()
            }
        }
    }

    private fun stopBreathAnimation() {
        breathLoopRunning = false
        breathAnimator?.cancel()
        breathAnimator = null
        pulseField.visibility = View.GONE
    }

    private fun pauseBreathAnimation() {
        breathAnimator?.pause()
    }

    private fun resumeBreathAnimation() {
        breathAnimator?.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        stopBreathAnimation()
    }
}

