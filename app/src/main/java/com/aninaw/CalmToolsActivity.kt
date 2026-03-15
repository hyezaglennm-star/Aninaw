//CalmToolsActivity.kt
package com.aninaw

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import android.widget.TextView

class CalmToolsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calm_tools)

        findViewById<TextView>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, CalmToolsHistoryActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        com.aninaw.util.BackButton.bind(this)

        bindCards()
    }

    private fun bindCards() {
        findViewById<MaterialCardView?>(R.id.cardStretch)?.setOnClickListener {
            openQuickStretch()
        }

        findViewById<MaterialCardView?>(R.id.cardGrounding)?.setOnClickListener {
            val intent = Intent(this, Grounding54321Activity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        findViewById<MaterialCardView?>(R.id.cardBreathing)?.setOnClickListener {
            val intent = Intent(this, MomentActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        findViewById<MaterialCardView?>(R.id.cardJournal)?.setOnClickListener {
            val intent = Intent(this, JournalEditorActivity::class.java).apply {
                putExtra(JournalEditorActivity.EXTRA_PROMPT, getString(R.string.tool_journal_sub))
                putExtra(JournalEditorActivity.EXTRA_TYPE, "QUICK")
            }
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        findViewById<MaterialCardView?>(R.id.cardCbt)?.setOnClickListener {
            val intent = Intent(this, UntangleActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun openQuickStretch() {
        val intent = Intent(this, QuickStretchActivity::class.java)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}


