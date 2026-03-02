// MomentActivity.kt
package com.aninaw

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.content.Intent

class MomentActivity : AppCompatActivity() {

    private lateinit var pulseField: View
    private lateinit var choicePanel: View
    private lateinit var btnSteadyFocused: Button
    private lateinit var btnCalmRelease: Button

    private lateinit var textHaloLine: TextView
    private lateinit var textSofterHint: TextView
    private lateinit var btnBack: ImageButton

    private val ease = AccelerateDecelerateInterpolator()

    // --- Clean mapping ---
    private enum class BreathMode { BOX, FOUR_SIX }
    private var selectedMode: BreathMode = BreathMode.FOUR_SIX

    // Has user chosen a mode yet?
    private var userHasChosen = false
    private var ending = false

    // Visual bounds
    private val alphaMin = 0.88f
    private val alphaMax = 1.00f
    private var scaleMin = 0.985f
    private var scaleMax = 1.07f

    // Timing (set by mode)
    private var inhaleMs = 4000L
    private var exhaleMs = 6000L
    private var holdInhaleMs = 0L
    private var holdExhaleMs = 0L

    // Gradient radius
    private var inhaleR = 0f
    private var exhaleR = 0f

    // Breath loop
    private var breathLoopRunning = false
    private var breathSet: AnimatorSet? = null
    private var gd: GradientDrawable? = null

    // --- 30s session cap ---
    private val sessionHandler = Handler(Looper.getMainLooper())
    private val SESSION_MS = 30_000L
    private var sessionActive = false

    private val endSessionRunnable = Runnable {
        if (!ending && sessionActive) {
            endBreathSessionAndPrompt()
        }
    }
    // Fade-out after choice + idle
    private val choiceHandler = Handler(Looper.getMainLooper())
    private val CHOICE_IDLE_FADE_MS = 1100L

    private val fadeChoicesRunnable = Runnable {
        if (!ending && userHasChosen && choicePanel.visibility == View.VISIBLE) {
            fadeOutChoicesAndHint()
        }
    }
    private fun showChoicesAndHint() {
        choiceHandler.removeCallbacks(fadeChoicesRunnable)

        // Bring them back
        choicePanel.visibility = View.VISIBLE
        textSofterHint.visibility = View.VISIBLE

        choicePanel.animate().cancel()
        textSofterHint.animate().cancel()

        // Start from transparent if currently hidden
        if (choicePanel.alpha <= 0f) choicePanel.alpha = 0f
        if (textSofterHint.alpha <= 0f) textSofterHint.alpha = 0f

        val dur = 220L
        choicePanel.animate()
            .alpha(1f)
            .setDuration(dur)
            .setInterpolator(ease)
            .start()

        textSofterHint.animate()
            .alpha(1f)
            .setDuration(dur)
            .setInterpolator(ease)
            .start()

        // If user already chose, re-arm the idle fade
        if (userHasChosen && !ending) {
            scheduleChoiceFade()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_moment)

        setHeader(
            title = "Pause",
            subtitle = "Slow down. Breathe with the gentle pulse."
        )

        com.aninaw.util.BackButton.bind(this)

        pulseField = findViewById(R.id.pulseField)
        choicePanel = findViewById(R.id.choicePanel)
        btnSteadyFocused = findViewById(R.id.btnSteadyFocused)
        btnCalmRelease = findViewById(R.id.btnCalmRelease)

        textHaloLine = findViewById(R.id.textHaloLine)
        textSofterHint = findViewById(R.id.textSofterHint)
        btnBack = findViewById(R.id.btnBack)

        // Force exact drawable instance for gradientRadius animation
        gd = (ContextCompat.getDrawable(this, R.drawable.soft_pulse_field)!!.mutate() as GradientDrawable)
        pulseField.background = gd
        pulseField.backgroundTintList = null
        pulseField.backgroundTintMode = null
        pulseField.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        // Initial UI: choices shown immediately; halo hidden until user chooses
        textHaloLine.alpha = 0f
        textHaloLine.visibility = View.VISIBLE

        textSofterHint.alpha = 1f
        textSofterHint.visibility = View.VISIBLE
        textSofterHint.text = "Choose a pace below.\nThen let the rhythm carry you."

        choicePanel.alpha = 1f
        choicePanel.visibility = View.VISIBLE

        // Default selection is Calm & Release (4–6)
        setMode(BreathMode.FOUR_SIX, restart = false)
        updateChoiceUI(BreathMode.FOUR_SIX)

        btnSteadyFocused.setOnClickListener {
            onUserChoseMode(BreathMode.BOX)
        }

        btnCalmRelease.setOnClickListener {
            onUserChoseMode(BreathMode.FOUR_SIX)
        }

        // Reset fade timer on any tap AFTER choosing (only matters while panel is visible)
        val root = findViewById<View>(android.R.id.content)
        root.setOnTouchListener { _, event ->
            if (!ending && event.action == MotionEvent.ACTION_DOWN) {

                // If panel is hidden, tapping anywhere should bring it back
                if (choicePanel.visibility != View.VISIBLE) {
                    showChoicesAndHint()
                } else if (userHasChosen) {
                    // If panel is visible and they already chose, reset the fade timer
                    scheduleChoiceFade()
                }
            }
            false
        }


        btnBack.setOnClickListener { softExit() }

        // Compute radii after layout
        pulseField.post {
            computeRadiiIfNeeded()
            gd?.let {
                it.gradientRadius = inhaleR.coerceAtLeast(1f)
                it.invalidateSelf()
            }
            pulseField.apply {
                alpha = alphaMin
                scaleX = scaleMin
                scaleY = scaleMin
                invalidate()
            }
        }
    }

    private fun setHeader(title: String, subtitle: String) {
        findViewById<TextView>(R.id.tvScreenTitle).text = title
        findViewById<TextView>(R.id.tvScreenSubtitle).text = subtitle
    }

    override fun onStart() {
        super.onStart()
        ending = false

        // Ensure breathing restarts when returning
        pulseField.post {
            if (!ending) {
                computeRadiiIfNeeded()
                startBreath()
            }
        }
    }

    override fun onStop() {
        choiceHandler.removeCallbacks(fadeChoicesRunnable)
        cancelBreathSession()
        stopBreath()
        super.onStop()
    }

    // -------------------------
    // Choice behavior
    // -------------------------
    private fun onUserChoseMode(mode: BreathMode) {
        userHasChosen = true

        setMode(mode, restart = true)
        updateChoiceUI(mode)

        // After choosing: halo line fades in
        textHaloLine.text = "We’ll move gently."
        fadeIn(textHaloLine, 700L)

        // Start idle fade countdown (panel + hint fade out together)
        scheduleChoiceFade()
        startBreathSession30s()
    }

    private fun scheduleChoiceFade() {
        choiceHandler.removeCallbacks(fadeChoicesRunnable)
        choiceHandler.postDelayed(fadeChoicesRunnable, CHOICE_IDLE_FADE_MS)
    }

    private fun fadeOutChoicesAndHint() {
        val dur = 520L

        choicePanel.animate().cancel()
        textSofterHint.animate().cancel()

        choicePanel.animate()
            .alpha(0f)
            .setDuration(dur)
            .setInterpolator(ease)
            .withEndAction { choicePanel.visibility = View.GONE }
            .start()

        textSofterHint.animate()
            .alpha(0f)
            .setDuration(dur)
            .setInterpolator(ease)
            .withEndAction { textSofterHint.visibility = View.GONE }
            .start()
    }

    private fun updateChoiceUI(mode: BreathMode) {
        val selectedAlpha = 1.0f
        val unselectedAlpha = 0.65f
        btnSteadyFocused.alpha = if (mode == BreathMode.BOX) selectedAlpha else unselectedAlpha
        btnCalmRelease.alpha = if (mode == BreathMode.FOUR_SIX) selectedAlpha else unselectedAlpha
    }

    // -------------------------
    // Mode mapping (clean fix)
    // -------------------------
    private fun setMode(mode: BreathMode, restart: Boolean) {
        selectedMode = mode

        when (mode) {
            BreathMode.FOUR_SIX -> {
                // Calm & Release: inhale 4, exhale 6
                inhaleMs = 4000L
                exhaleMs = 6000L
                holdInhaleMs = 0L
                holdExhaleMs = 0L
            }
            BreathMode.BOX -> {
                // Steady & Focused: 4–4–4–4
                inhaleMs = 4000L
                holdInhaleMs = 4000L
                exhaleMs = 4000L
                holdExhaleMs = 4000L
            }
        }

        if (restart && !ending) {
            stopBreath()
            pulseField.post {
                if (!ending) startBreath()
            }
        }
    }

    private fun computeRadiiIfNeeded() {
        if (inhaleR > 0f && exhaleR > 0f) return
        val size = pulseField.width.coerceAtMost(pulseField.height).toFloat()
        if (size <= 0f) return
        inhaleR = size * 0.30f
        exhaleR = size * 0.70f
    }

    // -------------------------
    // Gentle exit
    // -------------------------
    private fun softExit() {
        if (ending) return
        ending = true
        cancelBreathSession()

        choiceHandler.removeCallbacks(fadeChoicesRunnable)
        stopBreath()

        val root = findViewById<View>(android.R.id.content)
        root.animate().cancel()
        root.animate()
            .alpha(0f)
            .setDuration(650L)
            .setInterpolator(ease)
            .withEndAction { finish() }
            .start()
    }

    // -------------------------
    // Breathing loop (visual inhale = expand)
    // -------------------------
    private fun startBreath() {
        computeRadiiIfNeeded()
        if (inhaleR <= 0f || exhaleR <= 0f) return

        breathLoopRunning = true
        breathSet?.cancel()

        // INHALE = expand
        val inhaleExpand = ValueAnimator.ofFloat(inhaleR, exhaleR).apply {
            duration = inhaleMs
            interpolator = ease
            addUpdateListener { a ->
                val r = a.animatedValue as Float
                val t = ((r - inhaleR) / (exhaleR - inhaleR)).coerceIn(0f, 1f)

                gd?.gradientRadius = r
                gd?.invalidateSelf()

                pulseField.alpha = alphaMin + (alphaMax - alphaMin) * t
                val s = scaleMin + (scaleMax - scaleMin) * t
                pulseField.scaleX = s
                pulseField.scaleY = s
                pulseField.invalidate()
            }
        }

        // HOLD after inhale (BOX only)
        val holdAfterInhale = ValueAnimator.ofFloat(exhaleR, exhaleR).apply {
            duration = holdInhaleMs
            interpolator = ease
            addUpdateListener {
                gd?.gradientRadius = exhaleR
                gd?.invalidateSelf()
                pulseField.alpha = alphaMax
                pulseField.scaleX = scaleMax
                pulseField.scaleY = scaleMax
                pulseField.invalidate()
            }
        }

        // EXHALE = contract
        val exhaleContract = ValueAnimator.ofFloat(exhaleR, inhaleR).apply {
            duration = exhaleMs
            interpolator = ease
            addUpdateListener { a ->
                val r = a.animatedValue as Float
                val t = ((exhaleR - r) / (exhaleR - inhaleR)).coerceIn(0f, 1f)

                gd?.gradientRadius = r
                gd?.invalidateSelf()

                pulseField.alpha = alphaMax - (alphaMax - alphaMin) * t
                val s = scaleMax - (scaleMax - scaleMin) * t
                pulseField.scaleX = s
                pulseField.scaleY = s
                pulseField.invalidate()
            }
        }

        // HOLD after exhale (BOX only)
        val holdAfterExhale = ValueAnimator.ofFloat(inhaleR, inhaleR).apply {
            duration = holdExhaleMs
            interpolator = ease
            addUpdateListener {
                gd?.gradientRadius = inhaleR
                gd?.invalidateSelf()
                pulseField.alpha = alphaMin
                pulseField.scaleX = scaleMin
                pulseField.scaleY = scaleMin
                pulseField.invalidate()
            }
        }

        val anims = ArrayList<Animator>(4)
        anims.add(inhaleExpand)
        if (holdInhaleMs > 0L) anims.add(holdAfterInhale)
        anims.add(exhaleContract)
        if (holdExhaleMs > 0L) anims.add(holdAfterExhale)

        breathSet = AnimatorSet().apply {
            playSequentially(anims)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (breathLoopRunning && !ending) startBreath()
                }
            })
            start()
        }
    }

    private fun stopBreath() {
        breathLoopRunning = false
        breathSet?.cancel()
        breathSet = null

        pulseField.animate().cancel()
        pulseField.apply {
            alpha = alphaMin
            scaleX = scaleMin
            scaleY = scaleMin
        }

        gd?.let {
            it.gradientRadius = inhaleR.coerceAtLeast(1f)
            it.invalidateSelf()
        }
        pulseField.invalidate()
    }

    private fun startBreathSession30s() {
        sessionActive = true
        sessionHandler.removeCallbacks(endSessionRunnable)
        sessionHandler.postDelayed(endSessionRunnable, SESSION_MS)
    }

    private fun cancelBreathSession() {
        sessionActive = false
        sessionHandler.removeCallbacks(endSessionRunnable)
    }

    private fun endBreathSessionAndPrompt() {
        // Stop counting
        sessionActive = false
        sessionHandler.removeCallbacks(endSessionRunnable)

        // Stop loop + gently settle visuals
        breathLoopRunning = false
        breathSet?.cancel()
        breathSet = null

        // Hide choice UI while dialog is up (optional, but cleaner)
        choiceHandler.removeCallbacks(fadeChoicesRunnable)
        choicePanel.visibility = View.GONE
        textSofterHint.visibility = View.GONE

        // Settle the pulse (short, soft, controlled)
        val settleDur = 420L
        pulseField.animate().cancel()

        // settle gradient radius too
        val fromR = gd?.gradientRadius ?: inhaleR
        val settleRadius = ValueAnimator.ofFloat(fromR, inhaleR.coerceAtLeast(1f)).apply {
            duration = settleDur
            interpolator = ease
            addUpdateListener { a ->
                gd?.gradientRadius = a.animatedValue as Float
                gd?.invalidateSelf()
            }
        }

        val settleView = pulseField.animate()
            .alpha(alphaMin)
            .scaleX(scaleMin)
            .scaleY(scaleMin)
            .setDuration(settleDur)
            .setInterpolator(ease)

        settleView.withEndAction {
            showPauseFinishedDialog()
        }.start()

        settleRadius.start()
    }

    private fun showPauseFinishedDialog() {
        if (ending) return

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Pause Finished")
            .setMessage("Notice how you feel.")
            .setCancelable(true)
            .setPositiveButton("Continue to check-in") { d, _ ->
                d.dismiss()

                val intent = Intent(this, UntangleActivity::class.java)
                startActivity(intent)

                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }
            .setNeutralButton("Repeat") { d, _ ->
                d.dismiss()
                // Bring choices back (optional) or keep hidden
                // showChoicesAndHint()

                // Restart breath loop + restart 30s timer
                if (!ending) {
                    stopBreath() // resets visuals cleanly
                    startBreath()
                    startBreathSession30s()
                }
            }
            .setNegativeButton("Close") { d, _ ->
                d.dismiss()
                softExit()
            }
            .create()

        dialog.show()
    }
    private fun fadeIn(v: View, dur: Long) {
        v.visibility = View.VISIBLE
        v.animate().cancel()
        v.animate()
            .alpha(1f)
            .setDuration(dur)
            .setInterpolator(ease)
            .start()
    }
}
