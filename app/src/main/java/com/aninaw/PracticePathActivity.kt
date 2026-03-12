package com.aninaw

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PracticePathActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DOMAIN = "extra_practice_domain" // BODY, MIND, SOUL, ENV
        private const val PREFS_NAME = "AninawPrefs"
        private const val KEY_DEBUG_TREE_DAY = "debug_tree_day"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_practice_path)

        com.aninaw.util.BackButton.bind(this)

        val domain = intent.getStringExtra(EXTRA_DOMAIN) ?: "BODY"

        val titleView = findViewById<TextView>(R.id.tvPracticeTitle)
        val subtitleView = findViewById<TextView>(R.id.tvPracticeSubtitle)
        val hintView = findViewById<TextView>(R.id.tvHint)

        val title = when (domain) {
            "BODY" -> "Body Practice Path"
            "MIND" -> "Mind Practice Path"
            "SOUL" -> "Soul Practice Path"
            "ENV" -> "Nature Practice Path"
            else -> "Practice Path"
        }
        titleView.text = title

        val subtitle = "21 small sessions. Continue at your own rhythm."
        subtitleView.text = subtitle

        hintView.text = "Tap a day to preview growth."

        updateProgressUI()
        bindSessionTiles(domain)
    }

    private fun updateProgressUI() {
        val treeManager = TreeGrowthManager(this)
        val currentDay = treeManager.getTimelineDay() // 1..364
        
        // Update "X sessions practiced"
        val practiced = (currentDay - 1).coerceAtLeast(0)
        val tvProgress = findViewById<TextView>(R.id.tvProgress)
        tvProgress?.text = "$practiced sessions practiced"

        // Update tiles 1..21
        val ids = intArrayOf(
            R.id.tile1, R.id.tile2, R.id.tile3, R.id.tile4, R.id.tile5, R.id.tile6, R.id.tile7,
            R.id.tile8, R.id.tile9, R.id.tile10, R.id.tile11, R.id.tile12, R.id.tile13,
            R.id.tile14, R.id.tile15, R.id.tile16, R.id.tile17, R.id.tile18, R.id.tile19,
            R.id.tile20, R.id.tile21
        )

        val colorPrimary = androidx.core.content.ContextCompat.getColor(this, R.color.aninaw_text_primary)
        val colorSecondary = androidx.core.content.ContextCompat.getColor(this, R.color.aninaw_text_secondary)
        val colorPurple = androidx.core.content.ContextCompat.getColor(this, R.color.purple_700)

        ids.forEachIndexed { index, id ->
            val tile = findViewById<TextView?>(id) ?: return@forEachIndexed
            val dayNum = index + 1

            when {
                dayNum < currentDay -> {
                    // Done
                    tile.setBackgroundResource(R.drawable.bg_session_tile_done)
                    tile.setTextColor(colorPrimary)
                }
                dayNum == currentDay -> {
                    // Current
                    tile.setBackgroundResource(R.drawable.bg_session_tile_current)
                    tile.setTextColor(colorPurple)
                }
                else -> {
                    // Future
                    tile.setBackgroundResource(R.drawable.bg_session_tile)
                    tile.setTextColor(colorSecondary)
                }
            }
        }
    }

    private fun bindSessionTiles(domain: String) {
        val ids = intArrayOf(
            R.id.tile1, R.id.tile2, R.id.tile3, R.id.tile4, R.id.tile5, R.id.tile6, R.id.tile7,
            R.id.tile8, R.id.tile9, R.id.tile10, R.id.tile11, R.id.tile12, R.id.tile13,
            R.id.tile14, R.id.tile15, R.id.tile16, R.id.tile17, R.id.tile18, R.id.tile19,
            R.id.tile20, R.id.tile21
        )

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        ids.forEachIndexed { index, id ->
            val tile = findViewById<TextView?>(id) ?: return@forEachIndexed
            val dayNumber = index + 1

            tile.setOnClickListener {
                prefs.edit().putInt(KEY_DEBUG_TREE_DAY, dayNumber).apply()

                val label = "Day $dayNumber"
                val title = when (domain) {
                    "BODY" -> "Body day $dayNumber"
                    "MIND" -> "Mind day $dayNumber"
                    "SOUL" -> "Soul day $dayNumber"
                    "ENV" -> "Nature day $dayNumber"
                    else -> "Day $dayNumber"
                }
                val desc = "Growth preview for day $dayNumber in your $domain rhythm."

                SessionPreviewBottomSheet.newInstance(
                    label = label,
                    title = title,
                    description = desc
                ).show(supportFragmentManager, "session_preview")
            }
        }
    }
}

