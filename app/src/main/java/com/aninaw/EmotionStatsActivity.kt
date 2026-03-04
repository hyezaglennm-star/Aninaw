package com.aninaw

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aninaw.data.AninawDb
import com.aninaw.data.journal.MoodCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EmotionStatsActivity : AppCompatActivity() {

    private lateinit var tvCount: TextView
    private lateinit var viewHappy: View
    private lateinit var viewSad: View
    private lateinit var viewCalm: View
    private lateinit var viewAnxious: View
    
    private lateinit var textHappy: TextView
    private lateinit var textSad: TextView
    private lateinit var textCalm: TextView
    private lateinit var textAnxious: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emotion_stats)

        tvCount = findViewById(R.id.tvCount)
        
        // Bars
        viewHappy = findViewById(R.id.barHappy)
        viewSad = findViewById(R.id.barSad)
        viewCalm = findViewById(R.id.barCalm)
        viewAnxious = findViewById(R.id.barAnxious)
        
        // Percent Texts
        textHappy = findViewById(R.id.textHappyPercent)
        textSad = findViewById(R.id.textSadPercent)
        textCalm = findViewById(R.id.textCalmPercent)
        textAnxious = findViewById(R.id.textAnxiousPercent)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnCreate).setOnClickListener {
            startActivity(Intent(this, JournalEditorActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    override fun onResume() {
        super.onResume()
        loadStats()
    }

    private fun loadStats() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AninawDb.getDatabase(this@EmotionStatsActivity)
            val total = db.journalDao().getTotalEntries()
            val moodCounts = db.journalDao().getMoodCounts()
            
            withContext(Dispatchers.Main) {
                updateUI(total, moodCounts)
            }
        }
    }

    private fun updateUI(total: Int, moodCounts: List<MoodCount>) {
        tvCount.text = total.toString()

        if (total == 0) {
            // Reset to 0
            updateBar(viewHappy, textHappy, 0)
            updateBar(viewSad, textSad, 0)
            updateBar(viewCalm, textCalm, 0)
            updateBar(viewAnxious, textAnxious, 0)
            return
        }
        
        // Calculate percentages
        // Note: Moods stored as "Happy", "Sad", "Calm", "Anxious" (or others like "Tired" which we might ignore or map)
        var countHappy = 0
        var countSad = 0
        var countCalm = 0
        var countAnxious = 0

        moodCounts.forEach { 
            when(it.mood) {
                "Happy" -> countHappy = it.count
                "Sad" -> countSad = it.count
                "Calm" -> countCalm = it.count
                "Anxious" -> countAnxious = it.count
                // "Tired" could map to Sad or be ignored for this specific chart
            }
        }

        // Calculate total of THESE 4 emotions to normalize the chart to 100% of displayed emotions?
        // Or relative to ALL entries? Usually relative to displayed or total entries.
        // Let's use total entries to show "coverage".
        
        updateBar(viewHappy, textHappy, (countHappy * 100) / total)
        updateBar(viewSad, textSad, (countSad * 100) / total)
        updateBar(viewCalm, textCalm, (countCalm * 100) / total)
        updateBar(viewAnxious, textAnxious, (countAnxious * 100) / total)
    }

    private fun updateBar(bar: View, text: TextView, percent: Int) {
        text.text = "$percent%"
        
        // Max height for 100% is roughly 160dp in our layout, let's say 150dp max visual
        // We'll set height via layout params.
        // Actually, in the XML we used fixed heights. Let's make them dynamic.
        val density = resources.displayMetrics.density
        val maxHeightDp = 140 // max height for the bar
        val heightDp = (maxHeightDp * percent) / 100
        val finalHeight = if (heightDp < 10) 10 else heightDp // Min height
        
        val params = bar.layoutParams
        params.height = (finalHeight * density).toInt()
        bar.layoutParams = params
    }
}
