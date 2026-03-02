package com.aninaw

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class WelcomeActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    private val PREFS = "welcome_prefs"
    private val KEY_SEEN = "welcome_seen_once"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

        // If already shown once, skip instantly
        if (prefs.getBoolean(KEY_SEEN, false)) {
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
            return
        }

        // Mark as seen immediately (so even if app closes mid-animation, it won't replay)
        prefs.edit().putBoolean(KEY_SEEN, true).apply()

        setContentView(R.layout.activity_welcome)

        val root = findViewById<View>(android.R.id.content)

        // Start transparent
        root.alpha = 0f

        // Fade in
        root.animate()
            .alpha(1f)
            .setDuration(600L)
            .withEndAction {
                // Stay 2 seconds
                handler.postDelayed({
                    // Fade out
                    root.animate()
                        .alpha(0f)
                        .setDuration(600L)
                        .withEndAction {
                            startActivity(Intent(this, MainActivity::class.java))
                            overridePendingTransition(0, 0)
                            finish()
                        }
                        .start()
                }, 2000L)
            }
            .start()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}