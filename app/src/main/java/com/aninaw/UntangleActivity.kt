//UntangleActivity.kt

package com.aninaw

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aninaw.checkin.Emotion
import com.aninaw.checkin.EmotionRepository
import com.aninaw.untangle.UntangleEntryEntity
import com.aninaw.untangle.UntanglePrefs
import com.aninaw.untangle.UntangleRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import android.content.Intent
import com.aninaw.data.AninawDb
import com.aninaw.data.treering.TreeRingMemoryRepository
import com.aninaw.data.calmhistory.CalmToolHistoryEntity

class UntangleActivity : AppCompatActivity() {

    private var startedAt: Long = 0L
    private lateinit var stepContainer: FrameLayout
    private lateinit var btnContinue: MaterialButton
    private lateinit var tvStep: TextView

    private val draft = UntangleDraft()
    private var currentStep = 0
    private val totalSteps = 5

    private lateinit var emotionRepo: EmotionRepository
    private lateinit var untangleRepo: UntangleRepository
    private lateinit var untanglePrefs: UntanglePrefs

    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE
    private var capacityMode: String = "STEADY"

    private fun wireRerateSlider(v: View) {
        val slider = v.findViewById<Slider>(R.id.sliderIntensity)
        val label = v.findViewById<TextView>(R.id.textIntensityLabel)

        // Restore previously saved value (if you add it to draft)
        slider.value = draft.intensityAfter.toFloat()

        fun updateLabel(value: Float) {
            label.text = "Intensity: ${value.toInt()}/100"
        }

        updateLabel(slider.value)

        slider.addOnChangeListener { _, value, _ ->
            draft.intensityAfter = value.toInt()
            updateLabel(value)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_untangle)

        startedAt = System.currentTimeMillis()

        capacityMode = intent.getStringExtra("CAPACITY_MODE") ?: "STEADY"

        emotionRepo = EmotionRepository(this)
        untangleRepo = UntangleRepository(this)
        untanglePrefs = UntanglePrefs(this)

        stepContainer = findViewById(R.id.stepContainer)
        btnContinue = findViewById(R.id.btnContinue)
        tvStep = findViewById(R.id.tvStep)

        com.aninaw.util.BackButton.bind(this)

        btnContinue.setOnClickListener {
            collectStepInputs()
            goNext()
        }

        renderStep()
    }

    override fun onBackPressed() {
        if (currentStep > 0) {
            currentStep--
            renderStep()
        } else {
            super.onBackPressed()
        }
    }

    private fun goNext() {
        if (currentStep < totalSteps - 1) {
            currentStep++
            renderStep()
        } else {
            saveAllThree()
        }
    }

    private fun renderStep() {
        tvStep.text = "${currentStep + 1} / $totalSteps"
        stepContainer.removeAllViews()

        val layoutId = when (currentStep) {
            0 -> R.layout.step_moment
            1 -> R.layout.step_feelings
            2 -> R.layout.step_alternative
            3 -> R.layout.step_balanced
            else -> R.layout.step_rerate
        }

        val v = layoutInflater.inflate(layoutId, stepContainer, false)
        stepContainer.addView(v)

        restoreStepInputs(v)

        if (currentStep == 1) {
            wireFeelingsSlider(v)
        }
        if (currentStep == 4) {
            wireRerateSlider(v)
        }

        updateButtonLabel()
    }

    private fun updateButtonLabel() {
        btnContinue.text = if (currentStep == totalSteps - 1) "Save Reflection" else "Continue"
        btnContinue.isEnabled = true
        btnContinue.alpha = 1f
    }

    private fun collectStepInputs(): Boolean {
        val v = stepContainer.getChildAt(0) ?: return true

        when (currentStep) {
            0 -> {
                draft.situation =
                    v.findViewById<EditText>(R.id.etSituation).text?.toString()?.trim().orEmpty()

                val cg = v.findViewById<ChipGroup>(R.id.chipCategory)
                val checkedId = cg.checkedChipId
                if (checkedId != View.NO_ID) {
                    val chip = v.findViewById<Chip>(checkedId)
                    draft.category = chip.text.toString()
                }
            }

            1 -> {
                val cg = v.findViewById<ChipGroup>(R.id.chipOverallEmotion)
                val slider = v.findViewById<Slider>(R.id.sliderEmotionIntensity)

                val checkedId = cg.checkedChipId
                draft.overallEmotion =
                    if (checkedId != View.NO_ID) {
                        val chip = v.findViewById<Chip>(checkedId)
                        Emotion.fromLabel(chip.text.toString()).name
                    } else {
                        ""
                    }

                draft.intensityBefore = slider.value.toInt()

                draft.feelingsBefore =
                    v.findViewById<EditText>(R.id.etFeelingsBefore).text?.toString()?.trim().orEmpty()

                draft.bodyBefore =
                    v.findViewById<EditText>(R.id.etBodyBefore).text?.toString()?.trim().orEmpty()
            }

            2 -> {
                draft.alternativeThought =
                    v.findViewById<EditText>(R.id.etAlternative).text?.toString()?.trim().orEmpty()
            }

            3 -> {
                draft.balancedThought =
                    v.findViewById<EditText>(R.id.etBalanced).text?.toString()?.trim().orEmpty()
            }

            4 -> {
                val slider = v.findViewById<Slider>(R.id.sliderIntensity)
                draft.intensityAfter = slider.value.toInt()
            }
        }

        return true
    }

    private fun wireFeelingsSlider(v: View) {
        val cg = v.findViewById<ChipGroup>(R.id.chipOverallEmotion)
        val slider = v.findViewById<Slider>(R.id.sliderEmotionIntensity)
        val guide = v.findViewById<TextView>(R.id.tvIntensityGuide)

        // Restore value
        slider.value = draft.intensityBefore.toFloat()

        fun fadeIn() {
            if (slider.visibility == View.VISIBLE) return

            guide.alpha = 0f
            slider.alpha = 0f

            guide.visibility = View.VISIBLE
            slider.visibility = View.VISIBLE

            guide.animate().alpha(1f).setDuration(180L).start()
            slider.animate().alpha(1f).setDuration(180L).start()
        }

        fun fadeOut() {
            if (slider.visibility != View.VISIBLE) return

            guide.animate()
                .alpha(0f)
                .setDuration(160L)
                .withEndAction { guide.visibility = View.GONE }
                .start()

            slider.animate()
                .alpha(0f)
                .setDuration(160L)
                .withEndAction { slider.visibility = View.GONE }
                .start()
        }

        fun updateVisibility() {
            if (cg.checkedChipId != View.NO_ID) fadeIn() else fadeOut()
        }

        // Initial state (when entering / returning to this step)
        updateVisibility()

        // Show only when user selects an emotion
        cg.setOnCheckedChangeListener { _, _ ->
            updateVisibility()

            val checkedId = cg.checkedChipId
            draft.overallEmotion =
                if (checkedId != View.NO_ID) {
                    val chip = v.findViewById<Chip>(checkedId)
                    Emotion.fromLabel(chip.text.toString()).name
                } else {
                    ""
                }
        }

        // Keep draft updated while dragging
        slider.addOnChangeListener { _, value, _ ->
            draft.intensityBefore = value.toInt()
        }
    }

    private fun restoreStepInputs(v: View) {
        when (currentStep) {
            0 -> {
                v.findViewById<EditText>(R.id.etSituation).setText(draft.situation)
                val cg = v.findViewById<ChipGroup>(R.id.chipCategory)
                for (i in 0 until cg.childCount) {
                    val c = cg.getChildAt(i) as? Chip ?: continue
                    c.isChecked = (c.text.toString() == draft.category)
                }
            }

            1 -> {
                v.findViewById<EditText>(R.id.etFeelingsBefore).setText(draft.feelingsBefore)
                v.findViewById<EditText>(R.id.etBodyBefore).setText(draft.bodyBefore)

                val cg = v.findViewById<ChipGroup>(R.id.chipOverallEmotion)

                if (draft.overallEmotion.isBlank()) {
                    cg.clearCheck()
                } else {
                    val target = draft.overallEmotion
                    for (i in 0 until cg.childCount) {
                        val chip = cg.getChildAt(i) as? Chip ?: continue
                        chip.isChecked = (Emotion.fromLabel(chip.text.toString()).name == target)
                    }
                }

                val slider = v.findViewById<Slider>(R.id.sliderEmotionIntensity)
                slider.value = draft.intensityBefore.toFloat()
            }

            2 -> {
                v.findViewById<EditText>(R.id.etAlternative).setText(draft.alternativeThought)
            }

            3 -> {
                v.findViewById<EditText>(R.id.etBalanced).setText(draft.balancedThought)
            }

            4 -> {
                val slider = v.findViewById<Slider>(R.id.sliderIntensity)
                val label = v.findViewById<TextView>(R.id.textIntensityLabel)

                slider.value = draft.intensityAfter.toFloat()
                label.text = "Intensity: ${draft.intensityAfter}/100"
            }
        }
    }

    private fun saveAllThree() {
        val now = System.currentTimeMillis()
        val ymd = LocalDate.now().format(fmt)

        // fallback to STEADY only at save-time
        val emo = runCatching {
            if (draft.overallEmotion.isBlank()) Emotion.STEADY else Emotion.valueOf(draft.overallEmotion)
        }.getOrElse { Emotion.STEADY }

        emotionRepo.addTodayEntry(emo)

        val summary = buildString {
            append("[$ymd] ")
            append(draft.category).append(": ")
            append(draft.situation.take(120))
            append(" | Balanced: ").append(draft.balancedThought.take(120))
            append(" | Emotion: ").append(emo.name)
        }
        untanglePrefs.saveLatest(summary)
        untanglePrefs.incrementTodayCount()

        val entity = UntangleEntryEntity(
            createdAtMs = now,
            ymd = ymd,
            category = draft.category,
            situation = draft.situation,
            automaticThought ="",
            feelingsBefore = draft.feelingsBefore,
            bodyBefore = draft.bodyBefore,
            alternativeThought = draft.alternativeThought,
            balancedThought = draft.balancedThought,
            feelingsAfter = draft.feelingsAfter,
            nextStep = draft.nextStep,
            overallEmotion = emo.name
        )

        lifecycleScope.launch {

            withContext(Dispatchers.IO) {

                // 1️⃣ Save untangle reflection
                untangleRepo.insert(entity)

                // 2️⃣ Log FULL reflection to tree ring history
                val db = AninawDb.getDatabase(this@UntangleActivity)
                val ringRepo = TreeRingMemoryRepository(db)

                val payload = """
{
  "category": ${json(draft.category)},
  "situation": ${json(draft.situation)},
  "alternativeThought": ${json(draft.alternativeThought)},
  "balancedThought": ${json(draft.balancedThought)},

  "overallEmotion": ${json(emo.name)},
  "intensityBefore": ${draft.intensityBefore},
  "intensityAfter": ${draft.intensityAfter},

  "feelingsBefore": ${json(draft.feelingsBefore)},
  "bodyBefore": ${json(draft.bodyBefore)},

  "feelingsAfter": ${json(draft.feelingsAfter)},
  "nextStep": ${json(draft.nextStep)}
}
""".trimIndent()

                ringRepo.logFullReflection(
                    date = LocalDate.now(),
                    emotion = emo.name,
                    intensity = (draft.intensityAfter / 100f).coerceIn(0f, 1f),
                    capacity = capacityMode,
                    payloadJson = payload
                )

                db.calmToolHistoryDao().insert(
                    CalmToolHistoryEntity(
                        toolType = "cbt",
                        toolTitle = "CBT Reflection",
                        completedAt = System.currentTimeMillis(),
                        durationSeconds = ((System.currentTimeMillis() - startedAt) / 1000L).toInt(),
                        completionState = "completed"
                    )
                )

            }
// 3️⃣ Trigger tree growth bonus (only after save succeeded)
            TreeGrowthManager(this@UntangleActivity).onCheckInCompleted()

// 4️⃣ Go back home and tell MainActivity a check-in was completed
            val home = Intent(this@UntangleActivity, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_CHECKIN_COMPLETED, true)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

            startActivity(home)
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun json(s: String?): String {
        val t = (s ?: "")
        val escaped = buildString(t.length + 16) {
            for (ch in t) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }
        return "\"$escaped\""
    }
}
