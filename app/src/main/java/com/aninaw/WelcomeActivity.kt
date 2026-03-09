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

        // If already shown once, skip instantly but check logic
        if (prefs.getBoolean(KEY_SEEN, false)) {
            navigateNext()
            return
        }

        // Mark as seen immediately
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
                            navigateNext()
                        }
                        .start()
                }, 2000L)
            }
            .start()
    }

    private fun navigateNext() {
        // Always show the check-in screen as per user request
        val intent = Intent(this, DailyCheckInActivity::class.java)
        startActivity(intent)
        overridePendingTransition(0, 0)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
