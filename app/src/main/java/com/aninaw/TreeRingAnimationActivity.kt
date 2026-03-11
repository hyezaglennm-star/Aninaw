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

        val ringsView = findViewById<com.aninaw.TreeRingView>(R.id.ringsView)
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

        ringsView.post {
            // Configure view for passive display
            ringsView.setRings(dayCount.toInt(), emptyList()) 
            ringsView.startIntroAnimation(2000L) // 2s unfold
        }

        // Text fade in
        val textAlpha = ObjectAnimator.ofFloat(tvCount, View.ALPHA, 0f, 1f).apply {
            duration = 1000
            startDelay = 500
        }
        
        val all = AnimatorSet()
        all.play(textAlpha)
        
        // Wait for animation + slight pause before transitioning
        all.duration = 2500 
        all.doOnEnd {
            // Proceed to timeline
            startActivity(Intent(this, DaysTimelineActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
        all.start()
    }
}