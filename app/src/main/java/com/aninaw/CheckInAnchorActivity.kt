//CheckInAnchorActivity.kt
package com.aninaw

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.aninaw.checkin.EmotionRepository
import com.aninaw.data.AninawDb
import com.aninaw.data.treering.TreeRingMemoryRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

class CheckInAnchorActivity : AppCompatActivity() {

    private lateinit var repo: EmotionRepository
    private var selectedEmotion: String? = null

    private val emotions = listOf(
        Pair("Calm", R.drawable.happy_mood),
        Pair("Okay", R.drawable.okay_mood),
        Pair("Tense", R.drawable.tense_mood1),
        Pair("Heavy", R.drawable.sad_mood),
        Pair("Tired", R.drawable.neutral_mood),
        Pair("Anxious", R.drawable.tense_mood1),
        Pair("Sad", R.drawable.sad_mood),
        Pair("Angry", R.drawable.tense_mood1),
        Pair("Grateful", R.drawable.happy_mood),
        Pair("Numb", R.drawable.neutral_mood)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_check_in_anchor)

        setHeader(
            title = "Daily Check-in",
            subtitle = "Pause and record how you feel today."
        )

        com.aninaw.util.BackButton.bind(this)

        setupQuickCheckInGrid()

        findViewById<View>(R.id.optionStartCheckIn).setOnClickListener {
            goToCheckIn()
        }

        findViewById<View>(R.id.optionBreathe).setOnClickListener {
            startActivity(
                Intent(this, MomentActivity::class.java).apply {
                    putExtra("STATE_TYPE", "PROTECTED")
                    putExtra("STATE_STEADY", false)
                }
            )
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        repo = EmotionRepository(this)
    }

    private fun setupQuickCheckInGrid() {
        val recycler = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerEmotions)
        recycler.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 5)

        val layoutDetails = findViewById<View>(R.id.layoutDetails)
        val btnSave = findViewById<MaterialButton>(R.id.btnSave)
        
        val adapter = MoodAdapter(emotions) { label ->
            selectedEmotion = label
            layoutDetails.visibility = View.VISIBLE
            // Optionally scroll down if needed, or just let layout expand
        }
        recycler.adapter = adapter

        val toggleIntensity = findViewById<MaterialButtonToggleGroup>(R.id.toggleIntensity)
        
        // Defaults
        toggleIntensity.check(R.id.btnIntensityMedium)

        // Tint
        val checkedColor = Color.parseColor("#CFE8D6")
        val uncheckedColor = Color.parseColor("#FFFFFF")
        setupToggleTint(toggleIntensity, checkedColor, uncheckedColor)

        btnSave.setOnClickListener {
            saveCheckIn(toggleIntensity)
        }
    }

    private fun saveCheckIn(toggleIntensity: MaterialButtonToggleGroup) {
        val emotion = selectedEmotion ?: return

        val intensityFloat = when (toggleIntensity.checkedButtonId) {
            R.id.btnIntensityLight -> 0.35f
            R.id.btnIntensityStrong -> 0.90f
            else -> 0.60f
        }
        
        // Intensity int for QuickCheckIn logic if needed, but here we save directly to repo
        // The original bottom sheet logic used 1/2/3 mapping. 
        // Here we can just use the float logic directly.

        val db = AninawDb.getDatabase(this)
        val ringRepo = TreeRingMemoryRepository(db)

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                ringRepo.logQuickCheckIn(
                    date = LocalDate.now(),
                    emotion = emotion,
                    intensity = intensityFloat,
                    capacity = "STEADY", // Defaulting for quick grid flow to keep it simple
                    note = null
                )
            }

            runCatching {
                val emoEnum = com.aninaw.checkin.Emotion.fromLabel(emotion)
                repo.addTodayEntry(emoEnum)
            }

            val gm = TreeGrowthManager(this@CheckInAnchorActivity)
            gm.onQuickCheckInCompleted()
            markTreeBonusForTomorrow()

            withContext(Dispatchers.Main) {
                val home = Intent(this@CheckInAnchorActivity, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_CHECKIN_COMPLETED, true)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(home)
                finish()
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }
    }

    private fun setupToggleTint(
        group: MaterialButtonToggleGroup,
        checkedColor: Int,
        uncheckedColor: Int
    ) {
        fun refresh() {
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                if (child is MaterialButton) {
                    val isChecked = child.id == group.checkedButtonId
                    child.setBackgroundColor(if (isChecked) checkedColor else uncheckedColor)
                }
            }
        }
        group.addOnButtonCheckedListener { _, _, _ -> refresh() }
        refresh()
    }

    private fun markTreeBonusForTomorrow() {
        val sp = getSharedPreferences("AninawPrefs", Context.MODE_PRIVATE)
        val todayEpochDay = System.currentTimeMillis() / 86_400_000L
        val nextEpochDay = todayEpochDay + 1L
        sp.edit().putBoolean("tree_bonus_day_$nextEpochDay", true).apply()
    }

    private fun setHeader(title: String, subtitle: String) {
        findViewById<TextView>(R.id.tvScreenTitle).text = title
        findViewById<TextView>(R.id.tvScreenSubtitle).text = subtitle
    }

    private fun goToCheckIn() {
        startActivity(
            Intent(this, UntangleActivity::class.java).apply {
                putExtra("CAPACITY_MODE", "STEADY")
            }
        )
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    // Adapter class copied here for simplicity in this Activity
    inner class MoodAdapter(
        private val items: List<Pair<String, Int>>,
        private val onClick: (String) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<MoodAdapter.MoodViewHolder>() {

        private var selectedPos = -1

        inner class MoodViewHolder(v: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(v) {
            val img: ImageView = v.findViewById(R.id.imgMood)
            val txt: TextView = v.findViewById(R.id.tvMoodLabel)
            val container: View = v
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MoodViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_mood_grid, parent, false)
            return MoodViewHolder(v)
        }

        override fun onBindViewHolder(holder: MoodViewHolder, position: Int) {
            val (label, resId) = items[position]
            holder.txt.text = label
            holder.img.setImageResource(resId)
            
            val isSelected = (position == selectedPos)
            holder.container.alpha = if (isSelected || selectedPos == -1) 1.0f else 0.4f
            holder.container.scaleX = if (isSelected) 1.1f else 1.0f
            holder.container.scaleY = if (isSelected) 1.1f else 1.0f

            holder.container.setOnClickListener {
                val prev = selectedPos
                selectedPos = holder.adapterPosition
                notifyItemChanged(prev)
                notifyItemChanged(selectedPos)
                onClick(label)
            }
        }

        override fun getItemCount() = items.size
    }
}
