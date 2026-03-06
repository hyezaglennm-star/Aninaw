// TreeRingActivity.kt
package com.aninaw

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aninaw.data.AninawDb
import com.aninaw.data.treering.TreeRingMemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat

class TreeRingActivity : AppCompatActivity() {

    private var currentMemoryList: List<DailyMemory> = emptyList()
    private lateinit var rvMemories: RecyclerView
    private lateinit var adapter: TreeRingAdapter

    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

    companion object {
        const val EXTRA_TREE_STAGE = "extra_tree_stage"
        const val EXTRA_DAYS_ELAPSED = "extra_days_elapsed"
    }

    private val prefs by lazy { getSharedPreferences("AninawPrefs", Context.MODE_PRIVATE) }

    private val KEY_RING_AFF_LAST_SHOWN_DAY = "ring_aff_last_shown_day"
    private val KEY_RING_AFF_PREFIX = "ring_aff_day_" // persisted per absolute dayIndex

    // Keep aligned with TreeRingView cap (180)
    private val MAX_VISIBLE_RINGS = 180

    private val baseAffirmations = listOf(
        "You were here.",
        "You carried yourself through this time.",
        "This season held you.",
        "You continued.",
        "You breathed through this.",
        "You did not vanish.",
        "You stayed alive in this chapter.",
        "You were becoming, even quietly.",
        "You made it through the hours.",
        "You kept going in small ways.",
        "You stayed with yourself.",
        "You remained."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        // Overlay feel: dim the home screen behind
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0.45f)

        setContentView(R.layout.activity_tree_ring)

        // Make top UI safe from status bar / notch
        val root = findViewById<View>(R.id.treeRingOverlayRoot)
        root.setOnApplyWindowInsetsListener { v, insets ->
            val top = insets.systemWindowInsetTop
            v.setPadding(v.paddingLeft, top, v.paddingRight, v.paddingBottom)
            insets
        }
        root.requestApplyInsets()

        com.aninaw.util.BackButton.bind(this)

        val stageName = intent?.getStringExtra(EXTRA_TREE_STAGE)
            ?.takeIf { it.isNotBlank() }
            ?: "SAPLING"

        val daysElapsed = (intent?.getIntExtra(EXTRA_DAYS_ELAPSED, 0) ?: 0).coerceAtLeast(0)

        val totalRings = (daysElapsed + 1).coerceAtLeast(1)
        val visibleRingCount = min(totalRings, MAX_VISIBLE_RINGS)

        // ✅ Define these ONCE, before scrubber uses them
        val end = LocalDate.now()
        val start = end.minusDays((visibleRingCount - 1).toLong())

        val startDayIndex = (daysElapsed - (visibleRingCount - 1)).coerceAtLeast(0)
        val visibleDaysElapsed = visibleRingCount - 1

        val affirmations = List(visibleRingCount) { "" }

        val ringView = findViewById<TreeRingView>(R.id.treeRingView)
        ringView.bind(stageName, visibleDaysElapsed, affirmations)

        // Also handle taps on the main ring visualization
        ringView.onRingTapped = { mem ->
             TreeRingDetailFragment.newInstance(mem.dateIso)
                .show(supportFragmentManager, "TreeRingDetail")
        }

        // ✅ Load tree memory for the visible range (date-based)
        val db = AninawDb.getDatabase(this)
        val repo = TreeRingMemoryRepository(db)

        lifecycleScope.launch {
            val bestPerDay = withContext(Dispatchers.IO) {
                val logs = repo.getRange(start, end)
                logs.groupBy { it.date }.mapValues { (_, list) ->
                    list.sortedWith(
                        compareBy<com.aninaw.data.treering.TreeRingMemoryEntity> { it.type != "FULL" }
                            .thenByDescending { it.timestamp }
                    ).first()
                }
            }

            val memoryList = (0 until visibleRingCount).map { i ->
                val date = start.plusDays(i.toLong())
                val iso = date.toString()
                val e = bestPerDay[iso]   // ✅ FIXED (was "map[iso]")

                DailyMemory(
                    dateIso = iso,
                    hasCheckIn = (e != null),
                    emotion = e?.emotion,
                    intensity = e?.intensity,
                    note = e?.note,
                    isQuick = (e?.type == "QUICK"),

                    payloadJson = e?.payloadJson,
                    capacity = e?.capacity,
                    type = e?.type,
                    timestamp = e?.timestamp
                )
            }

            currentMemoryList = memoryList
            ringView.setDailyMemory(memoryList)
        }

        // Suppress any affirmation pulse text on the ring
    }

    private fun maybePulseTodayAffirmation(
        ringView: TreeRingView,
        todayRingIndex: Int,
        todayText: String
    ) {
        if (todayText.isBlank()) return

        val today = todayKey()
        val lastShown = prefs.getString(KEY_RING_AFF_LAST_SHOWN_DAY, null)
        if (lastShown == today) return

        prefs.edit().putString(KEY_RING_AFF_LAST_SHOWN_DAY, today).apply()
        ringView.showDailyAffirmationPulse(todayText, todayRingIndex.coerceAtLeast(0))
    }

    private fun buildAffirmationsForRings(startDayIndex: Int, ringCount: Int): List<String> {
        // One affirmation per visible ring, inner -> outer.
        return (0 until ringCount).map { i ->
            val dayIndex = startDayIndex + i
            getOrCreateAffirmationForDay(dayIndex)
        }
    }

    private fun showTreeRingMemory(mem: DailyMemory) {
        TreeRingMemoryBottomSheet
            .newInstance(mem)
            .show(supportFragmentManager, "TreeRingMemory")
    }

    private fun getOrCreateAffirmationForDay(dayIndex: Int): String {
        val key = "$KEY_RING_AFF_PREFIX$dayIndex"

        // Absolute rule: day 0 always uses the first affirmation.
        if (dayIndex == 0) {
            val first = baseAffirmations.firstOrNull().orEmpty()
            prefs.edit().putString(key, first).apply()
            return first
        }

        val existing = prefs.getString(key, null)
        if (!existing.isNullOrBlank()) return existing

        val picked = pickAffirmationForDay(dayIndex)
        prefs.edit().putString(key, picked).apply()
        return picked
    }

    private fun pickAffirmationForDay(dayIndex: Int): String {
        if (baseAffirmations.isEmpty()) return ""
        if (dayIndex == 0) return baseAffirmations.first()

        val idx = stableIndex(dayIndex, baseAffirmations.size)
        return baseAffirmations[idx]
    }

    private fun stableIndex(dayIndex: Int, size: Int): Int {
        if (size <= 0) return 0

        var x = 1125899907L
        x = x * 31L + dayIndex.toLong()
        x = x xor (x shl 13)
        x = x xor (x shr 7)
        x = x xor (x shl 17)

        val positive = x and Long.MAX_VALUE
        return (positive % size.toLong()).toInt()
    }

    private fun todayKey(): String {
        val cal = Calendar.getInstance()
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(y, m, d)
    }
}
