//GrowthStoryActivity.kt
package com.aninaw

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar
import kotlin.math.max
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent
import com.aninaw.data.AninawDb
import com.aninaw.data.lifesnapshot.LifeSnapshotRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.aninaw.checkin.Emotion
import com.aninaw.checkin.EmotionRepository

class GrowthStoryActivity : AppCompatActivity() {


    private val DAY_MS = 86_400_000L

    private lateinit var emotionRepo: EmotionRepository

    private val PREFS = "AninawPrefs"
    private val KEY_LIFE_BASELINE_EPOCH = "life_snapshot_baseline_epochDay"
    private val domains = listOf("BODY", "MIND", "SOUL", "ENV")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_growth_story)

        setHeader(
            title = "Your Growth Story",
            subtitle = "Track your progress overtime."
        )

        com.aninaw.util.BackButton.bind(this)

        emotionRepo = EmotionRepository(this)

        val weatherView = findViewById<com.aninaw.views.EmotionalWeatherView>(R.id.emotionalWeatherView)
        val textSelectedDate = findViewById<TextView>(R.id.textSelectedDate)
        val textSelectedMood = findViewById<TextView>(R.id.textSelectedMood)

        val raw = emotionRepo.loadCurrentMonthSeason()
        val records = raw.filter { it.entriesCount > 0 }

        weatherView.setData(records)

// 👇 Paste it HERE
        if (records.isEmpty()) {
            textSelectedDate.text = ""
            textSelectedMood.text = "No check-ins yet"
        } else {
            records.lastOrNull()?.let { last ->
                textSelectedDate.text = last.ymd
                textSelectedMood.text = moodLabel(last.primary, last.secondary, last.entriesCount)
            }
        }

        weatherView.onDayTapped = { idx ->
            val r = records.getOrNull(idx)
            if (r != null) {
                textSelectedDate.text = r.ymd
                textSelectedMood.text = moodLabel(r.primary, r.secondary, r.entriesCount)
            }
        }

        records.lastOrNull()?.let { last ->
            textSelectedDate.text = last.ymd
            textSelectedMood.text = moodLabel(last.primary, last.secondary, last.entriesCount)
        }

        records.lastOrNull()?.let { last ->
            textSelectedDate.text = last.ymd
            textSelectedMood.text = moodLabel(last.primary, last.secondary, last.entriesCount)
        }

        val storyText = findViewById<TextView>(R.id.storyText)
        val thenDays = findViewById<TextView>(R.id.thenDays)
        val thenStreak = findViewById<TextView>(R.id.thenStreak)
        val nowDays = findViewById<TextView>(R.id.nowDays)
        val nowStreak = findViewById<TextView>(R.id.nowStreak)
        val milestonesText = findViewById<TextView>(R.id.milestonesText)

        val proofRecycler = findViewById<RecyclerView>(R.id.proofRecycler)
        proofRecycler.layoutManager = GridLayoutManager(this, 2)
        val proofAdapter = ProofCardAdapter()
        proofRecycler.adapter = proofAdapter

        // ---------- Load completion history from SharedPreferences ----------
        val completionByDomain: Map<String, Set<Long>> =
            domains.associateWith { getCompletionDays(it) }

        val allCompletionDays: Set<Long> =
            completionByDomain.values.flatten().toSet()

        val today = epochDayNow()

        // Active days this month (union days within current month)
        val monthStart = epochDayMonthStart()
        val activeDaysThisMonth = allCompletionDays.count { it in monthStart..today }

        // Total completions (sum per domain, counts multiple domains same day)
        val totalCompletions = completionByDomain.values.sumOf { it.size }

        // Longest streak (based on union active days)
        val longestStreakAllTime = longestStreak(allCompletionDays)

        // Comebacks: gap >= 3 days then return (based on union)
        val comebacks = comebackCount(allCompletionDays, gapDays = 3)

        // Then vs Now windows:
        // THEN = first 14 days starting from earliest recorded completion day
        // NOW  = last 14 days ending today
        val earliest = allCompletionDays.minOrNull()

        val thenWindow = if (earliest != null) (earliest..(earliest + 13)) else (today..today)
        val nowWindow = ((today - 13)..today)

        val thenStats = windowStats(allCompletionDays, thenWindow)
        val nowStats = windowStats(allCompletionDays, nowWindow)

        thenDays.text = thenStats.activeDays.toString()
        thenStreak.text = thenStats.longestStreak.toString()
        nowDays.text = nowStats.activeDays.toString()
        nowStreak.text = nowStats.longestStreak.toString()

        // ---------- Rhythm Radar Binding ----------
        val bodyW = weeklyScore("BODY")
        val mindW = weeklyScore("MIND")
        val soulW = weeklyScore("SOUL")
        val envW = weeklyScore("ENV")

        findViewById<com.aninaw.views.RadarRhythmView>(R.id.radarRhythmView)
            .setValues(bodyW, mindW, soulW, envW)

        findViewById<TextView>(R.id.textRhythmInsight).text =
            buildRhythmInsight(bodyW, mindW, soulW, envW)

        // Story text (balanced: emotional + proof)
        storyText.text = buildStory(
            activeDaysThisMonth = activeDaysThisMonth,
            thenDays = thenStats.activeDays,
            nowDays = nowStats.activeDays,
            nowStreak = nowStats.longestStreak,
            totalCompletions = totalCompletions,
            comebacks = comebacks
        )

        // Proof cards
        proofAdapter.submit(
            listOf(
                ProofCardUi(activeDaysThisMonth.toString(), "Days showed up", "This month"),
                ProofCardUi(longestStreakAllTime.toString(), "Longest momentum", "Personal best"),
                ProofCardUi(totalCompletions.toString(), "Rhythms completed", "All time"),
                ProofCardUi(comebacks.toString(), "Comebacks", "Returned after gaps")
            )
        )

        // Milestones (computed)
        val milestones = buildMilestones(allCompletionDays, totalCompletions, longestStreakAllTime, comebacks)
        milestonesText.text = milestones.joinToString("\n") { "• $it" }

        loadLifeSnapshotSection()
    }

    private fun setHeader(title: String, subtitle: String) {
        findViewById<TextView>(R.id.tvScreenTitle).text = title
        findViewById<TextView>(R.id.tvScreenSubtitle).text = subtitle
    }

    private fun loadLifeSnapshotSection() {
        val textBaselineDate = findViewById<TextView>(R.id.textBaselineDate)
        val textBaselineSummary = findViewById<TextView>(R.id.textBaselineSummary)
        val btnBaselineAction = findViewById<View>(R.id.btnBaselineAction)

        val textCurrentState = findViewById<TextView>(R.id.textCurrentState)
        val progressToUnlock =
            findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.progressToUnlock)
        val btnCurrentAction = findViewById<View>(R.id.btnCurrentAction)
        val btnCompare = findViewById<View>(R.id.btnCompare)

        btnCompare.visibility = View.GONE
        btnCurrentAction.isEnabled = false
        btnCurrentAction.alpha = 0.55f

        val db = AninawDb.getDatabase(this)
        val lifeRepo = LifeSnapshotRepository(db.lifeSnapshotDao())
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val todayEpoch = epochDayNow()

        lifecycleScope.launch {
            val baseline = withContext(Dispatchers.IO) { lifeRepo.baseline() }
            val current = withContext(Dispatchers.IO) { lifeRepo.latestCurrent() }

            if (baseline == null) {
                textBaselineDate.text = "Not set yet"
                textBaselineSummary.text = "Save your starting season once. It becomes your anchor point."

                btnBaselineAction.isEnabled = true
                btnBaselineAction.alpha = 1f
                (btnBaselineAction as? com.google.android.material.button.MaterialButton)?.text = "Save Starting Season"
                btnBaselineAction.setOnClickListener {
                    startActivity(
                        Intent(this@GrowthStoryActivity, com.aninaw.lifesnapshot.LifeSnapshotActivity::class.java)
                            .putExtra("KIND", "BASELINE")
                    )
                }

                textCurrentState.text = "Available after 30 days of steady growth."
                progressToUnlock.max = 30
                progressToUnlock.progress = 0
                btnCurrentAction.isEnabled = false
                btnCurrentAction.alpha = 0.55f
                btnCompare.visibility = View.GONE
                return@launch
            }

            val baselineEpoch = baseline.epochDay
            prefs.edit().putLong(KEY_LIFE_BASELINE_EPOCH, baselineEpoch).apply()

            textBaselineDate.text = "Saved on " + prettyDateFromEpochDay(baselineEpoch)
            textBaselineSummary.text = summaryLine(baseline)

            (btnBaselineAction as? com.google.android.material.button.MaterialButton)?.text = "View Starting Season"
            btnBaselineAction.isEnabled = true
            btnBaselineAction.alpha = 1f
            btnBaselineAction.setOnClickListener {
                startActivity(
                    Intent(this@GrowthStoryActivity, com.aninaw.lifesnapshot.LifeSnapshotActivity::class.java)
                        .putExtra("KIND", "BASELINE")
                )
            }

            val daysSince = (todayEpoch - baselineEpoch).toInt().coerceAtLeast(0)
            progressToUnlock.max = 30
            progressToUnlock.progress = daysSince.coerceAtMost(30)

            val unlocked = daysSince >= 30
            if (!unlocked) {
                textCurrentState.text = "Available after 30 days of steady growth. Day $daysSince of 30."
                btnCurrentAction.isEnabled = false
                btnCurrentAction.alpha = 0.55f
                btnCompare.visibility = View.GONE
            } else {
                btnCurrentAction.isEnabled = true
                btnCurrentAction.alpha = 1f

                btnCurrentAction.setOnClickListener {
                    startActivity(
                        Intent(this@GrowthStoryActivity, com.aninaw.lifesnapshot.LifeSnapshotActivity::class.java)
                            .putExtra("KIND", "CURRENT")
                    )
                }

                if (current == null) {
                    textCurrentState.text = "You’ve spent 30 days with Aninaw. Reflect on your current season when you’re ready."
                    btnCompare.visibility = View.GONE
                } else {
                    textCurrentState.text = "Saved on " + prettyDateFromEpochDay(current.epochDay)
                    btnCompare.visibility = View.VISIBLE
                    btnCompare.setOnClickListener {
                        startActivity(
                            Intent(this@GrowthStoryActivity, com.aninaw.lifesnapshot.LifeSnapshotCompareActivity::class.java)
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadLifeSnapshotSection()
    }

    private fun moodLabel(p: com.aninaw.checkin.Emotion?, s: com.aninaw.checkin.Emotion?, count: Int): String {
        if (count <= 0) return "No record"
        if (s == null || s == p) return "${p?.name ?: "No record"} ($count check-ins)"
        return "${p?.name ?: "?"} + ${s.name} ($count check-ins)"
    }

    // -----------------------------
    // Data reading (SharedPrefs)
    // -----------------------------
    private fun keyCompletionDays(domain: String) = "rhythm_completion_days_$domain"

    private fun getCompletionDays(domain: String): Set<Long> {
        val sp = getSharedPreferences("AninawPrefs", Context.MODE_PRIVATE)
        val raw = sp.getString(keyCompletionDays(domain), "") ?: ""
        if (raw.isBlank()) return emptySet()
        return raw.split(",").mapNotNull { it.toLongOrNull() }.toSet()
    }

    // -----------------------------
    // Calculations
    // -----------------------------
    private data class WindowStats(val activeDays: Int, val longestStreak: Int)

    private fun windowStats(days: Set<Long>, window: LongRange): WindowStats {
        val within = days.filter { it in window }.sorted()
        return WindowStats(
            activeDays = within.size,
            longestStreak = longestStreak(within.toSet())
        )
    }

    private fun longestStreak(days: Set<Long>): Int {
        if (days.isEmpty()) return 0
        val sorted = days.sorted()
        var best = 1
        var cur = 1
        for (i in 1 until sorted.size) {
            if (sorted[i] == sorted[i - 1] + 1) {
                cur++
                best = max(best, cur)
            } else {
                cur = 1
            }
        }
        return best
    }

    private fun comebackCount(days: Set<Long>, gapDays: Int): Int {
        if (days.isEmpty()) return 0
        val sorted = days.sorted()
        var count = 0
        for (i in 1 until sorted.size) {
            val gap = sorted[i] - sorted[i - 1]
            if (gap >= gapDays.toLong() + 1L) count++
        }
        return count
    }

    private fun weeklyScore(domain: String): Float {
        val completed = getCompletionDays(domain)
        val today = epochDayNow()
        val last7 = (0..6).map { today - it }.toSet()
        val count = last7.count { it in completed }
        return count / 7f
    }

    private fun buildRhythmInsight(body: Float, mind: Float, soul: Float, env: Float): String {

        val pairs = listOf(
            "Body" to body,
            "Mind" to mind,
            "Soul" to soul,
            "Environment" to env
        )

        val sorted = pairs.sortedByDescending { it.second }
        val top = sorted.first()
        val low = sorted.last()

        val total = body + mind + soul + env

        return when {
            total == 0f ->
                "This week looks quiet across all areas. Start with one small rhythm tomorrow."

            (top.second - low.second) >= 0.5f ->
                "Your rhythm leaned toward ${top.first}. ${low.first} may need gentle attention next week."

            else ->
                "Your rhythm was fairly balanced this week. Keep it soft and steady."
        }
    }

    private fun buildMilestones(
        allDays: Set<Long>,
        totalCompletions: Int,
        longestStreak: Int,
        comebacks: Int
    ): List<String> {
        val out = mutableListOf<String>()

        val firstDay = allDays.minOrNull()
        if (firstDay != null) out.add("First rhythm completed")

        if (totalCompletions >= 10) out.add("10 rhythms completed")
        if (totalCompletions >= 25) out.add("25 rhythms completed")
        if (totalCompletions >= 50) out.add("50 rhythms completed")

        if (longestStreak >= 3) out.add("First 3-day momentum")
        if (longestStreak >= 7) out.add("One full week momentum")

        if (comebacks >= 1) out.add("You came back after a break")

        // Keep it short and meaningful
        return out.take(6)
    }

    private fun buildStory(
        activeDaysThisMonth: Int,
        thenDays: Int,
        nowDays: Int,
        nowStreak: Int,
        totalCompletions: Int,
        comebacks: Int
    ): String {
        val improvement = nowDays - thenDays

        return when {
            thenDays == 0 && nowDays == 0 -> "You haven’t recorded any rhythms yet. When you do, this page will turn your small actions into a story you can actually see."
            improvement >= 3 ->
                "You showed up for yourself $activeDaysThisMonth days this month. In your first two weeks, you completed $thenDays active days. In the last two weeks, you completed $nowDays. That’s momentum. Your current best streak is $nowStreak, with $totalCompletions rhythms completed overall."
            improvement in 1..2 ->
                "You’re showing up more often now. This month you logged $activeDaysThisMonth active days. Recently you reached $nowDays active days in two weeks, with a best streak of $nowStreak. Keep that gentle consistency."
            improvement == 0 && nowDays > 0 ->
                "You’ve built a steady rhythm. This month: $activeDaysThisMonth active days. Your current two-week pace is consistent, and your best streak lately is $nowStreak. Consistency is a skill and you’re practicing it."
            else -> {
                val comebackLine = if (comebacks > 0) "You also came back $comebacks time(s), and that counts." else "Even slower seasons are still part of the work."
                "Your pace changed recently, but you still have progress behind you: $totalCompletions rhythms completed overall. $comebackLine"
            }
        }
    }

    // -----------------------------
    // Date helpers
    // -----------------------------
    private fun epochDayNow(): Long = System.currentTimeMillis() / DAY_MS

    private fun epochDayMonthStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis / DAY_MS
    }

    private fun prettyDateFromEpochDay(epochDay: Long): String {
        val ms = epochDay * DAY_MS
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return sdf.format(Date(ms))
    }

    private fun summaryLine(s: com.aninaw.data.lifesnapshot.LifeSnapshotEntity): String {
        fun dots(v: Int): String {
            val clamped = v.coerceIn(1, 5)
            return "●".repeat(clamped) + "○".repeat(5 - clamped)
        }
        return "Emotions ${dots(s.emotionalRegulation)} · Habits ${dots(s.habitAwareness)} · Rhythm ${dots(s.healthyRhythm)} · Self ${dots(s.selfLeadership)}"
    }
}