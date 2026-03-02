//GrowthImpactActivity
package com.aninaw

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.aninaw.checkin.Emotion
import com.aninaw.checkin.EmotionRepository
import com.aninaw.views.EmotionalWeatherView
import com.aninaw.views.RadarRhythmView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.max

class GrowthImpactActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TREE_STAGE = "extra_tree_stage"
        const val EXTRA_DAYS_ELAPSED = "extra_days_elapsed"

        private const val PREFS_NAME = "aninaw_prefs"
        private const val PREF_REMINDERS_ENABLED = "pref_checkin_reminders_enabled"
        private const val UNIQUE_WORK_NAME = "daily_checkin_reminder"
    }

    private lateinit var emotionRepo: EmotionRepository

    private lateinit var btnBack: View
    private lateinit var titleText: TextView

    private lateinit var weatherView: EmotionalWeatherView
    private lateinit var textSelectedDate: TextView
    private lateinit var textSelectedMood: TextView

    // NOTE: Rhythm completions are stored in "AninawPrefs" in RhythmActivity.
    private val RHYTHM_PREFS = "AninawPrefs"
    private val DAY_MS = 86_400_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_growth_impact)

        emotionRepo = EmotionRepository(this)

        btnBack = findViewById(R.id.btnBack)
        titleText = findViewById(R.id.textTitle)

        weatherView = findViewById(R.id.emotionalWeatherView)
        textSelectedDate = findViewById(R.id.textSelectedDate)
        textSelectedMood = findViewById(R.id.textSelectedMood)

        titleText.text = "Your Growth and Impact"
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Emotional Weather
        val records = emotionRepo.loadCurrentMonthSeason()
        weatherView.setData(records)

        weatherView.onDayTapped = { idx ->
            val r = records.getOrNull(idx)
            if (r == null) {
                textSelectedDate.text = ""
                textSelectedMood.text = ""
            } else {
                textSelectedDate.text = r.ymd
                textSelectedMood.text = moodLabel(r.primary, r.secondary, r.entriesCount)
            }
        }

        records.lastOrNull()?.let { last ->
            textSelectedDate.text = last.ymd
            textSelectedMood.text = moodLabel(last.primary, last.secondary, last.entriesCount)
        }

        // Growth Story + Rhythm Radar + Proof + Milestones
        bindEmbeddedGrowthStory()
    }

    private fun moodLabel(p: Emotion?, s: Emotion?, count: Int): String {
        if (count <= 0) return "No record"
        if (s == null || s == p) return "${p?.name ?: "No record"} ($count check-ins)"
        return "${p?.name ?: "?"} + ${s.name} ($count check-ins)"
    }

    // -----------------------------
    // Reminders (MVP) - unchanged
    // -----------------------------
    private fun readRemindersEnabled(): Boolean {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(PREF_REMINDERS_ENABLED, true)
    }

    private fun persistRemindersEnabled(enabled: Boolean) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_REMINDERS_ENABLED, enabled)
            .apply()
    }

    private fun ensureReminderScheduled() {
        NotificationHelper.ensureCheckInChannel(this)

        val request = PeriodicWorkRequestBuilder<DailyCheckInWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(2, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun cancelReminder() {
        WorkManager.getInstance(applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun needsNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    private fun hasNotificationPermission(): Boolean {
        if (!needsNotificationPermission()) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    // -----------------------------
    // Crisis Resources (MVP dialog) - unchanged
    // -----------------------------
    private fun showCrisisResourcesDialog() {
        val items = listOf(
            CrisisItem(
                name = "NCMH Crisis Hotline",
                numbers = listOf("1553", "0917-899-8727", "(02) 7989-8727")
            ),
            CrisisItem(
                name = "Hopeline PH",
                numbers = listOf("(02) 8804-4673", "0917-558-4673", "0918-873-4673")
            ),
            CrisisItem(
                name = "Philippine Red Cross",
                numbers = listOf("143")
            )
        )

        val labels = items.map { it.name }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("Crisis Resources")
            .setMessage("If you're in immediate danger, call local emergency services.\n\nChoose a resource:")
            .setItems(labels) { _, which ->
                showCrisisNumbersSheet(items[which])
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showCrisisNumbersSheet(item: CrisisItem) {
        val choices = item.numbers.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(item.name)
            .setItems(choices) { _, which ->
                val number = item.numbers[which]
                showNumberActions(item.name, number)
            }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun showNumberActions(label: String, number: String) {
        val actions = arrayOf("Call", "Copy")

        MaterialAlertDialogBuilder(this)
            .setTitle(number)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> dialNumber(number)
                    1 -> copyText("$label: $number")
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun dialNumber(number: String) {
        val uri = Uri.parse("tel:${number.replace(" ", "")}")
        val intent = Intent(Intent.ACTION_DIAL, uri)
        startActivity(intent)
    }

    private fun copyText(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Crisis resource", text))
        Toast.makeText(this, "Copied.", Toast.LENGTH_SHORT).show()
    }

    private data class CrisisItem(
        val name: String,
        val numbers: List<String>
    )

    // -----------------------------
    // Rhythm helpers (single source of truth)
    // -----------------------------
    private fun epochDayNow(): Long = System.currentTimeMillis() / DAY_MS

    private fun getCompletionDays(domain: String): Set<Long> {
        val sp = getSharedPreferences(RHYTHM_PREFS, Context.MODE_PRIVATE)
        val raw = sp.getString("rhythm_completion_days_$domain", "") ?: ""
        if (raw.isBlank()) return emptySet()
        return raw.split(",").mapNotNull { it.toLongOrNull() }.toSet()
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

    // -----------------------------
    // Embedded Growth Story (based on Rhythm completions in SharedPrefs)
    // -----------------------------
    private fun bindEmbeddedGrowthStory() {
        val domains = listOf("BODY", "MIND", "SOUL", "ENV")

        fun epochDayMonthStart(): Long {
            val cal = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis / DAY_MS
        }

        fun longestStreak(days: Set<Long>): Int {
            if (days.isEmpty()) return 0
            val sorted = days.sorted()
            var best = 1
            var cur = 1
            for (i in 1 until sorted.size) {
                if (sorted[i] == sorted[i - 1] + 1) {
                    cur++
                    best = max(best, cur)
                } else cur = 1
            }
            return best
        }

        fun comebackCount(days: Set<Long>, gapDays: Int): Int {
            if (days.isEmpty()) return 0
            val sorted = days.sorted()
            var count = 0
            for (i in 1 until sorted.size) {
                val gap = sorted[i] - sorted[i - 1]
                if (gap >= gapDays.toLong() + 1L) count++
            }
            return count
        }

        data class WindowStats(val activeDays: Int, val longestStreak: Int)

        fun windowStats(days: Set<Long>, window: LongRange): WindowStats {
            val within = days.filter { it in window }.sorted()
            return WindowStats(
                activeDays = within.size,
                longestStreak = longestStreak(within.toSet())
            )
        }

        fun buildStory(
            activeDaysThisMonth: Int,
            thenDays: Int,
            nowDays: Int,
            nowStreak: Int,
            totalCompletions: Int,
            comebacks: Int
        ): String {
            val improvement = nowDays - thenDays
            return when {
                thenDays == 0 && nowDays == 0 ->
                    "You haven’t recorded any rhythms yet. When you do, this page will turn your small actions into a story you can actually see."
                improvement >= 3 ->
                    "You showed up for yourself $activeDaysThisMonth days this month. In your first two weeks, you completed $thenDays active days. In the last two weeks, you completed $nowDays. That’s momentum. Your best recent streak is $nowStreak, with $totalCompletions rhythms completed overall."
                improvement in 1..2 ->
                    "You’re showing up more often now. This month you logged $activeDaysThisMonth active days. Recently you reached $nowDays active days in two weeks, with a best streak of $nowStreak."
                improvement == 0 && nowDays > 0 ->
                    "You’ve built a steady rhythm. This month: $activeDaysThisMonth active days. Your recent pace is consistent, and your best streak lately is $nowStreak."
                else -> {
                    val comebackLine =
                        if (comebacks > 0) "You also came back $comebacks time(s), and that counts."
                        else "Even slower seasons are still part of the work."
                    "Your pace changed recently, but you still have progress behind you: $totalCompletions rhythms completed overall. $comebackLine"
                }
            }
        }

        fun buildMilestones(allDays: Set<Long>, totalCompletions: Int, bestStreak: Int, comebacks: Int): List<String> {
            val out = mutableListOf<String>()
            if (allDays.isNotEmpty()) out.add("First rhythm completed")
            if (totalCompletions >= 10) out.add("10 rhythms completed")
            if (totalCompletions >= 25) out.add("25 rhythms completed")
            if (totalCompletions >= 50) out.add("50 rhythms completed")
            if (bestStreak >= 3) out.add("First 3-day momentum")
            if (bestStreak >= 7) out.add("One full week momentum")
            if (comebacks >= 1) out.add("You came back after a break")
            return out.take(6)
        }

        // ---- Compute from prefs ----
        val completionByDomain = domains.associateWith { getCompletionDays(it) }
        val allDays = completionByDomain.values.flatten().toSet()

        val today = epochDayNow()
        val monthStart = epochDayMonthStart()

        val activeDaysThisMonth = allDays.count { it in monthStart..today }
        val totalCompletions = completionByDomain.values.sumOf { it.size }
        val bestStreakAllTime = longestStreak(allDays)
        val comebacks = comebackCount(allDays, gapDays = 3)

        val earliest = allDays.minOrNull()
        val thenWindow = if (earliest != null) (earliest..(earliest + 13)) else (today..today)
        val nowWindow = ((today - 13)..today)

        val then = windowStats(allDays, thenWindow)
        val now = windowStats(allDays, nowWindow)

        // ---- Weekly Radar ----
        val bodyW = weeklyScore("BODY")
        val mindW = weeklyScore("MIND")
        val soulW = weeklyScore("SOUL")
        val envW = weeklyScore("ENV")

        findViewById<RadarRhythmView>(R.id.radarRhythmView)
            .setValues(bodyW, mindW, soulW, envW)

        findViewById<TextView>(R.id.textRhythmInsight).text =
            buildRhythmInsight(bodyW, mindW, soulW, envW)

        // ---- Bind views ----
        findViewById<TextView>(R.id.storyText).text =
            buildStory(
                activeDaysThisMonth,
                then.activeDays,
                now.activeDays,
                now.longestStreak,
                totalCompletions,
                comebacks
            )

        findViewById<TextView>(R.id.thenDays).text = then.activeDays.toString()
        findViewById<TextView>(R.id.thenStreak).text = then.longestStreak.toString()
        findViewById<TextView>(R.id.nowDays).text = now.activeDays.toString()
        findViewById<TextView>(R.id.nowStreak).text = now.longestStreak.toString()

        findViewById<TextView>(R.id.milestonesText).text =
            buildMilestones(allDays, totalCompletions, bestStreakAllTime, comebacks)
                .joinToString("\n") { "• $it" }

        val proofRecycler = findViewById<RecyclerView>(R.id.proofRecycler)
        proofRecycler.layoutManager = GridLayoutManager(this, 2)
        val adapter = ProofCardAdapter()
        proofRecycler.adapter = adapter

        adapter.submit(
            listOf(
                ProofCardUi(activeDaysThisMonth.toString(), "Days showed up", "This month"),
                ProofCardUi(bestStreakAllTime.toString(), "Longest momentum", "Personal best"),
                ProofCardUi(totalCompletions.toString(), "Rhythms completed", "All time"),
                ProofCardUi(comebacks.toString(), "Comebacks", "Returned after gaps")
            )
        )
    }
}