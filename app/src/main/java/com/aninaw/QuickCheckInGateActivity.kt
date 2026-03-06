package com.aninaw

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aninaw.checkin.Emotion
import com.aninaw.checkin.EmotionRepository
import com.aninaw.data.AninawDb
import com.aninaw.data.treering.TreeRingMemoryRepository
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class QuickCheckInGateActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_quick_checkin_gate)

        val btnConfirm = findViewById<MaterialButton>(R.id.btnConfirm)
        val tvSelectedEmotion = findViewById<android.widget.TextView>(R.id.tvSelectedEmotion)
        val tvSkip = findViewById<android.widget.TextView>(R.id.tvSkip)

        var selectedEmotion: String? = null

        val faces: Map<ImageView, String> = mapOf(
            findViewById<ImageView>(R.id.faceHappy) to "Happy",
            findViewById<ImageView>(R.id.faceShy) to "Shy",
            findViewById<ImageView>(R.id.faceNeutral) to "Neutral",
            findViewById<ImageView>(R.id.faceAnxious) to "Anxious",
            findViewById<ImageView>(R.id.faceSad) to "Sad"
        )

        fun updateSelection(selectedView: ImageView) {
            faces.keys.forEach { face ->
                if (face == selectedView) {
                    face.alpha = 1.0f
                    face.scaleX = 1.2f
                    face.scaleY = 1.2f
                    selectedEmotion = faces[face]
                    tvSelectedEmotion.text = selectedEmotion
                    tvSelectedEmotion.visibility = View.VISIBLE
                } else {
                    face.alpha = 0.5f
                    face.scaleX = 0.9f
                    face.scaleY = 0.9f
                }
            }
            btnConfirm.isEnabled = true
        }

        faces.keys.forEach { face ->
            face.setOnClickListener { updateSelection(face) }
        }

        tvSkip.setOnClickListener { goHome(false) }

        btnConfirm.setOnClickListener {
            val emotion = selectedEmotion ?: return@setOnClickListener

            val (intensity, capacity) = when (emotion) {
                "Happy" -> 2 to "STEADY"
                "Shy" -> 2 to "STEADY"
                "Neutral" -> 1 to "STEADY"
                "Anxious" -> 3 to "LOW"
                "Sad" -> 2 to "LOW"
                else -> 2 to "STEADY"
            }

            val intensityFloat = when (intensity) {
                1 -> 0.35f
                3 -> 0.90f
                else -> 0.60f
            }

            val db = AninawDb.getDatabase(this)
            val ringRepo = TreeRingMemoryRepository(db)

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    ringRepo.logQuickCheckIn(
                        date = LocalDate.now(),
                        emotion = emotion,
                        intensity = intensityFloat,
                        capacity = capacity,
                        note = null
                    )
                }

                runCatching {
                    val emoEnum = Emotion.fromLabel(emotion)
                    EmotionRepository(this@QuickCheckInGateActivity).addTodayEntry(emoEnum)
                }

                markTreeBonusForTomorrow()
                goHome(true)
            }
        }
    }

    private fun goHome(checkinCompleted: Boolean) {
        val intent = Intent(this, MainActivity::class.java)
        if (checkinCompleted) {
            intent.putExtra(MainActivity.EXTRA_CHECKIN_COMPLETED, true)
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private fun markTreeBonusForTomorrow() {
        val sp = getSharedPreferences("AninawPrefs", MODE_PRIVATE)
        val todayEpochDay = System.currentTimeMillis() / 86_400_000L
        val nextEpochDay = todayEpochDay + 1L
        sp.edit().putBoolean("tree_bonus_day_$nextEpochDay", true).apply()
    }
}
