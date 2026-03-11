package com.aninaw

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import com.aninaw.data.treering.TreeRingMemoryRepository

class TreeRingAnimationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tree_ring_animation)

        val container = findViewById<FrameLayout>(R.id.ringsContainer)
        val tvCount = findViewById<TextView>(R.id.tvRingCount)

        // Get current day count (rings)
        val prefs = getSharedPreferences("AninawPrefs", MODE_PRIVATE)
        val firstUse = prefs.getLong("first_use_epoch_day", -1)
        val today = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            java.time.LocalDate.now().toEpochDay()
        } else {
            System.currentTimeMillis() / 86_400_000L
        }
        
        val dayCount = if (firstUse != -1L) (today - firstUse + 1).coerceAtLeast(1) else 1
        tvCount.text = "Day $dayCount"

        // Animate rings
        // We'll create 3-5 rings that scale up from center
        val rings = 5
        val animators = mutableListOf<Animator>()

        for (i in 0 until rings) {
            val ring = ImageView(this).apply {
                setImageResource(R.drawable.ring_arc_background) // Reuse existing drawable or shape
                alpha = 0f
                scaleX = 0.2f
                scaleY = 0.2f
                layoutParams = FrameLayout.LayoutParams(600 + (i * 100), 600 + (i * 100)).apply {
                    gravity = android.view.Gravity.CENTER
                }
            }
            container.addView(ring)

            val scaleX = ObjectAnimator.ofFloat(ring, View.SCALE_X, 1f + (i * 0.1f))
            val scaleY = ObjectAnimator.ofFloat(ring, View.SCALE_Y, 1f + (i * 0.1f))
            val alpha = ObjectAnimator.ofFloat(ring, View.ALPHA, 0f, 0.4f - (i * 0.05f))
            
            val set = AnimatorSet().apply {
                playTogether(scaleX, scaleY, alpha)
                duration = 2000L
                startDelay = (i * 200).toLong()
                interpolator = DecelerateInterpolator()
            }
            animators.add(set)
        }
        
        // Text fade in
        val textAlpha = ObjectAnimator.ofFloat(tvCount, View.ALPHA, 0f, 1f).apply {
            duration = 1000
            startDelay = 500
        }
        animators.add(textAlpha)

        val all = AnimatorSet()
        all.playTogether(animators)
        all.doOnEnd {
            // Proceed to timeline
            startActivity(Intent(this, DaysTimelineActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
        all.start()
    }
}