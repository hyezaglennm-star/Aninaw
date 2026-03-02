package com.aninaw.lifesnapshot

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aninaw.R
import com.aninaw.data.AninawDb
import com.aninaw.data.lifesnapshot.LifeSnapshotEntity
import com.aninaw.data.lifesnapshot.LifeSnapshotRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LifeSnapshotCompareActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_life_snapshot_compare)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        val text = findViewById<TextView>(R.id.textCompare)

        val db = AninawDb.getDatabase(this)
        val repo = LifeSnapshotRepository(db.lifeSnapshotDao())

        lifecycleScope.launch {
            val baseline = withContext(Dispatchers.IO) { repo.baseline() }
            val current = withContext(Dispatchers.IO) { repo.latestCurrent() }

            if (baseline == null || current == null) {
                text.text = "Comparison becomes available once you have both a Starting Season and a Current Season."
                return@launch
            }

            text.text = buildCompare(baseline, current)
        }
    }

    private fun buildCompare(b: LifeSnapshotEntity, c: LifeSnapshotEntity): String {
        fun row(label: String, bv: Int, cv: Int): String {
            val diff = cv - bv
            val sign = if (diff > 0) "+$diff" else diff.toString()
            return "$label\nBeginning: ${dots(bv)}\nNow: ${dots(cv)}  ($sign)\n"
        }

        return listOf(
            row("Emotional Regulation", b.emotionalRegulation, c.emotionalRegulation),
            row("Habit Awareness", b.habitAwareness, c.habitAwareness),
            row("Healthy Rhythm", b.healthyRhythm, c.healthyRhythm),
            row("Self-Leadership", b.selfLeadership, c.selfLeadership)
        ).joinToString("\n")
    }

    private fun dots(v: Int): String {
        val x = v.coerceIn(1, 5)
        return "●".repeat(x) + "○".repeat(5 - x)
    }
}