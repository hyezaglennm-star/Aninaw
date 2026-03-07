package com.aninaw.lifesnapshot

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aninaw.R
import com.aninaw.data.AninawDb
import com.aninaw.data.lifesnapshot.LifeSnapshotEntity
import com.aninaw.data.lifesnapshot.LifeSnapshotRepository
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LifeSnapshotActivity : AppCompatActivity() {

    private val DAY_MS = 86_400_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_life_snapshot)

          com.aninaw.util.BackButton.bind(this)

        val kind = intent.getStringExtra("KIND") ?: "BASELINE"
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        val sEmotion = findViewById<Slider>(R.id.sliderEmotion)
        val sHabits = findViewById<Slider>(R.id.sliderHabits)
        val sRhythm = findViewById<Slider>(R.id.sliderRhythm)
        val sLead = findViewById<Slider>(R.id.sliderLeadership)

        val db = AninawDb.getDatabase(this)
        val repo = LifeSnapshotRepository(db.lifeSnapshotDao())

        // ✅ LOAD existing values so sliders reflect what user already saved
        lifecycleScope.launch {
            val existing = withContext(Dispatchers.IO) {
                when (kind) {
                    "BASELINE" -> repo.baseline()
                    "CURRENT" -> repo.latestCurrent()
                    else -> null
                }
            }

            existing?.let {
                sEmotion.value = it.emotionalRegulation.toFloat()
                sHabits.value = it.habitAwareness.toFloat()
                sRhythm.value = it.healthyRhythm.toFloat()
                sLead.value = it.selfLeadership.toFloat()
            }
        }

        // ✅ SAVE using fixed IDs (Option A)
        findViewById<View>(R.id.btnSave).setOnClickListener {

            val draft = LifeSnapshotEntity(
                id = if (kind == "BASELINE") 1 else 2,
                epochDay = epochDayNow(),
                kind = kind,
                emotionalRegulation = sEmotion.value.toInt(),
                habitAwareness = sHabits.value.toInt(),
                healthyRhythm = sRhythm.value.toInt(),
                selfLeadership = sLead.value.toInt(),
                reflectionTagsCsv = null,
                reflectionNote = null
            )

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    if (kind == "BASELINE") repo.saveBaseline(draft) else repo.saveCurrent(draft)
                }
                finish()
            }
        }
    }

    private fun epochDayNow(): Long = System.currentTimeMillis() / DAY_MS
}