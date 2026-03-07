package com.aninaw

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class CalmToolsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calm_tools)

        // Back Button
        findViewById<View>(R.id.btnBack)?.setOnClickListener {
            finish()
        }

        // 1) Stretch
        findViewById<View>(R.id.cardStretch)?.setOnClickListener {
            startActivity(Intent(this, QuickStretchActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        findViewById<View>(R.id.btnStartStretch)?.setOnClickListener {
            startActivity(Intent(this, QuickStretchActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // 2) Grounding
        findViewById<View>(R.id.cardGrounding)?.setOnClickListener {
            startActivity(Intent(this, GroundingActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // 3) Breathe -> MomentActivity
        findViewById<View>(R.id.cardBreathing)?.setOnClickListener {
            startActivity(Intent(this, MomentActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // 4) Journal -> OneMinuteJournalActivity
        findViewById<View>(R.id.cardJournal)?.setOnClickListener {
            startActivity(Intent(this, OneMinuteJournalActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // 5) CBT -> UntangleActivity
        findViewById<View>(R.id.cardCbt)?.setOnClickListener {
            startActivity(Intent(this, UntangleActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}
