//RhythmActivity.kt
package com.aninaw

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.aninaw.data.AninawDb
import com.aninaw.data.rhythm.RhythmDao
import com.aninaw.data.rhythm.RhythmEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime
import java.util.Calendar
import com.aninaw.data.rhythm.RhythmSeeder

class RhythmActivity : AppCompatActivity() {

    private val currentPhaseKey = "W61_64_MINIMAL_STRENGTH"

    // includeId -> domain
    private val includeDomain = mapOf(
        R.id.cardBodyItem to "BODY",
        R.id.cardMindItem to "MIND",
        R.id.cardSoulItem to "SOUL",
        R.id.cardNatureItem to "ENV"
    )

    private data class CardRefs(
        val includeRoot: View,
        val cardRoot: View,
        val cover: View,
        val icon: ImageView,
        val label: TextView,
        val openStack: View,
        val textHabit: TextView,
        val checkHit: View,
        val checkIcon: ImageView,
    )

    private val cards = mutableMapOf<Int, CardRefs>()
    private val openState = mutableMapOf<Int, Boolean>()

    private lateinit var db: AninawDb
    private lateinit var rhythmDao: RhythmDao

    private val handler = Handler(Looper.getMainLooper())

    private val PRESS_SCALE = 0.97f
    private val PRESS_DOWN_MS = 80L
    private val PRESS_UP_MS = 120L

    private val DOT_COUNT = 5
    private val DAY_MS = 86_400_000L

    // -----------------------------
    // Done today storage
    // -----------------------------
    private fun keyDoneToday(domain: String) = "rhythm_done_${domain}_${todayKey()}"

    private fun isDoneToday(domain: String): Boolean {
        val sp = getSharedPreferences("AninawPrefs", Context.MODE_PRIVATE)
        return sp.getBoolean(keyDoneToday(domain), false)
    }

    private fun setDoneToday(domain: String, done: Boolean) {
        val sp = getSharedPreferences("AninawPrefs", Context.MODE_PRIVATE)
        sp.edit().putBoolean(keyDoneToday(domain), done).apply()
    }

    private fun keyOpenState(includeId: Int) = "rhythm_${includeId}_is_open"

    private fun loadOpenState(includeId: Int) {
        val sp = getSharedPreferences("AninawPrefs", Context.MODE_PRIVATE)
        openState[includeId] = sp.getBoolean(keyOpenState(includeId), false)
    }

    private fun saveOpenState(includeId: Int) {
        val sp = getSharedPreferences("AninawPrefs", Context.MODE_PRIVATE)
        sp.edit().putBoolean(keyOpenState(includeId), openState[includeId] == true).apply()
    }

    // -----------------------------
    // Lifecycle
    // -----------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = AninawDb.getDatabase(this)
        rhythmDao = db.rhythmDao()

        setContentView(R.layout.rhythm)


        setHeader(
            title = "Rhythm",
            subtitle = "Strengthen your daily habits step by step."
        )
        findViewById<View>(R.id.incBackRhythm).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }


        lifecycleScope.launch { ensureHasSomeRhythmsForDev() }

        // Build refs for each include
        includeDomain.keys.forEach { includeId ->
            val root = findViewById<View>(includeId)

            val refs = CardRefs(
                includeRoot = root,
                cardRoot = root.findViewById(R.id.cardRoot),
                cover = root.findViewById(R.id.cover),
                icon = root.findViewById(R.id.icon),
                label = root.findViewById(R.id.label),
                openStack = root.findViewById(R.id.openStack),
                textHabit = root.findViewById(R.id.textHabit),
                checkHit = root.findViewById(R.id.checkHit),
                checkIcon = root.findViewById(R.id.checkIcon),
            )

            cards[includeId] = refs

            refs.checkHit.isFocusable = false
            refs.checkHit.isFocusableInTouchMode = false

            ensureTodayState(includeId)
            loadOpenState(includeId)

            bindStaticUi(includeId, refs)

            // ✅ SAFETY: make decorative layers never intercept taps
            makeNonBlocking(refs.cover)
            makeNonBlocking(refs.icon)
            makeNonBlocking(refs.label)
            // openStack contains interactive checkHit, so DO NOT disable it.

            // ✅ Press animation stays on the visual card surface
            applyPressScale(refs.cardRoot, refs.checkHit)

            // Put click on the actual visible tappable surface (cardRoot)
            refs.cardRoot.isClickable = true
            refs.cardRoot.isFocusable = true
            refs.cardRoot.setOnClickListener {
                lifecycleScope.launch { toggleOpen(includeId) }
            }

// Parent shouldn't compete for taps
            refs.includeRoot.isClickable = false
            refs.includeRoot.isFocusable = false

            // check button remains its own interaction
            refs.checkHit.setOnClickListener {
                onTapComplete(includeId)
            }
            refreshCardUi(includeId)
        }

        // restore visuals
        restoreAllCardsVisualState()

        updateHeaderProgress()
    }

    private fun setHeader(title: String, subtitle: String) {
        findViewById<TextView>(R.id.tvScreenTitle).text = title
        findViewById<TextView>(R.id.tvScreenSubtitle).text = subtitle
    }

    private fun updateHeaderProgress() {
        val doneCount = includeDomain.values.count { isDoneToday(it) }
        findViewById<TextView>(R.id.textProgress)?.text = "$doneCount of 4 done today"
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }


    // ✅ Prevent any view from intercepting taps meant for the card
    private fun makeNonBlocking(v: View?) {
        v ?: return
        v.isClickable = false
        v.isFocusable = false
        v.isFocusableInTouchMode = false
        v.isLongClickable = false
    }

    // -----------------------------
    // Bind fixed labels/icons per card
    // -----------------------------
    private fun bindStaticUi(includeId: Int, refs: CardRefs) {
        when (includeId) {
            R.id.cardNatureItem -> {
                refs.label.text = "Nature"
                refs.icon.setImageResource(R.drawable.ic_nature)
            }
            R.id.cardSoulItem -> {
                refs.label.text = "Soul"
                refs.icon.setImageResource(R.drawable.ic_heart_smile)
            }
            R.id.cardMindItem -> {
                refs.label.text = "Mind"
                refs.icon.setImageResource(R.drawable.ic_strong)
            }
            R.id.cardBodyItem -> {
                refs.label.text = "Body"
                refs.icon.setImageResource(R.drawable.ic_body)
            }
        }

        // subtle tone tint on the cover
        val base = 0xFFE9E5DD.toInt()
        val domain = includeDomain[includeId] ?: return
        val tinted = when (domain) {
            "BODY" -> adjustColor(base, +0.03f, +0.03f)
            "MIND" -> adjustColor(base, +0.02f, -0.01f)
            "SOUL" -> adjustColor(base, +0.01f, +0.00f)
            "ENV" -> adjustColor(base, +0.02f, +0.02f)
            else -> base
        }
        refs.cover.background?.mutate()?.let { bg ->
            bg.setTint(tinted)
            refs.cover.background = bg
        }
    }

    // -----------------------------
    // Open / Close logic (Option A behavior)
    // -----------------------------
    private suspend fun toggleOpen(includeId: Int) {
        val refs = cards[includeId] ?: return
        val domain = includeDomain[includeId] ?: return

        ensureTodayState(includeId)

        // if done today: never open
        if (isDoneToday(domain)) {
            softPulse(refs.checkIcon)
            showCountdownOnce(domain) // ✅ only on tap
            return
        }

        val isOpen = openState[includeId] == true
        if (isOpen) {
            closeCard(refs)
            openState[includeId] = false
            saveOpenState(includeId)
            refreshCardUi(includeId)
            return
        }

        ensureDailyRhythm(includeId, domain)
        val dailyText = getDailyText(includeId) ?: "No rhythm available."

        openCard(refs, dailyText)

        openState[includeId] = true
        saveOpenState(includeId)
        refreshCardUi(includeId)
    }

    private fun markTreeBonusForTomorrow() {
        val sp = getSharedPreferences("AninawPrefs", Context.MODE_PRIVATE)

        val todayEpochDay: Long = Calendar.getInstance().timeInMillis / DAY_MS
        val nextEpochDay = todayEpochDay + 1L

        sp.edit().putBoolean("tree_bonus_day_$nextEpochDay", true).apply()
    }
    private fun onTapComplete(includeId: Int) {
        val refs = cards[includeId] ?: return
        val domain = includeDomain[includeId] ?: return

        // only makes sense if open
        if (openState[includeId] != true) return

        if (isDoneToday(domain)) {
            softPulse(refs.checkIcon)
            showCountdownOnce(domain) // ✅ only on tap
            return
        }

        setDoneToday(domain, true)

// ✅ TREE GROWTH: count daily rhythm completion once
        // ✅ TREE VISUAL: completion today grows the tree NEXT day
        markTreeBonusForTomorrow()

        recordCompletion(domain)

        // Visual: white filled circle + Done fades in
        applyCheckCompletedVisual(refs)

        // lock taps
        refs.checkHit.isClickable = false
        refs.checkHit.isFocusable = false

        // ❌ Do NOT auto-show countdown after completion (you asked: only when user taps completed card)

        // close after 1 second
        handler.postDelayed({
            closeCard(refs)
            openState[includeId] = false
            saveOpenState(includeId)
            refreshCardUi(includeId)
        }, 1000L)
    }

    private fun openCard(refs: CardRefs, text: String) {

        refs.textHabit.animate().cancel()
        refs.textHabit.text = text
        refs.textHabit.alpha = 1f

        refs.cover.animate().alpha(0f).setDuration(180).withEndAction {
            refs.cover.visibility = View.GONE
            refs.cover.alpha = 0.6f
        }.start()

        refs.icon.animate().alpha(0f).setDuration(180).withEndAction {
            refs.icon.visibility = View.GONE
            refs.icon.alpha = 1f
        }.start()

        refs.label.animate().alpha(0f).setDuration(160).start()

        refs.openStack.visibility = View.VISIBLE
        refs.textHabit.text = text
        refs.openStack.alpha = 0f
        refs.openStack.animate().alpha(1f).setDuration(200).start()
    }

    private fun closeCard(refs: CardRefs) {
        refs.openStack.animate().alpha(0f).setDuration(140).withEndAction {
            refs.openStack.visibility = View.GONE
            refs.openStack.alpha = 1f
        }.start()

        refs.cover.visibility = View.VISIBLE
        refs.cover.alpha = 0f
        refs.cover.animate().alpha(0.6f).setDuration(180).start()

        refs.icon.visibility = View.VISIBLE
        refs.icon.alpha = 0f
        refs.icon.animate().alpha(1f).setDuration(180).start()

        refs.label.animate().alpha(0.85f).setDuration(180).start()
    }

    private fun refreshCardUi(includeId: Int) {
        val refs = cards[includeId] ?: return
        val domain = includeDomain[includeId] ?: return

        val isOpen = openState[includeId] == true
        val done = isDoneToday(domain)

        refs.openStack.visibility = if (isOpen) View.VISIBLE else View.GONE
        if (!isOpen) return

        if (done) {
            applyCheckCompletedVisual(refs)
            refs.checkHit.isClickable = false
            refs.checkHit.isFocusable = false
        } else {
            // reset check visuals for not-done
            refs.checkHit.background = null
            refs.checkIcon.alpha = 0.55f


            refs.checkHit.isClickable = true
            refs.checkHit.isFocusable = true
        }
    }

    private fun applyCheckCompletedVisual(refs: CardRefs) {

        // 1) Fill the check circle
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFFFFFFFF.toInt())
        }
        refs.checkHit.background = bg
        refs.checkIcon.alpha = 0.95f

        // 2) Fade habit text out
        refs.textHabit.animate().cancel()
        refs.textHabit.animate()
            .alpha(0f)
            .setDuration(150L)
            .withEndAction {

                // 3) Replace with "Done"
                refs.textHabit.text = "Done"
                refs.textHabit.alpha = 0f

                refs.textHabit.animate()
                    .alpha(1f)
                    .setDuration(180L)
                    .start()
            }
            .start()
    }
    // ✅ One-shot countdown: shows ONLY when user taps a completed card
    private fun showCountdownOnce(domain: String) {
        val now = LocalDateTime.now()
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
        val secondsRemaining = Duration.between(now, midnight).seconds.coerceAtLeast(0)

        val hours = secondsRemaining / 3600
        val minutes = (secondsRemaining % 3600) / 60
        val seconds = secondsRemaining % 60

        val label = when (domain) {
            "BODY" -> "Body"
            "MIND" -> "Mind"
            "SOUL" -> "Soul"
            "ENV" -> "Nature"
            else -> "Rhythm"
        }

        Toast.makeText(
            this,
            "✅ $label done today. New rhythm in %02d:%02d:%02d".format(hours, minutes, seconds),
            Toast.LENGTH_SHORT
        ).show()
    }

    // -----------------------------
    // Press scale
    // -----------------------------
    private fun applyPressScale(card: View, ignoreTapView: View? = null) {
        val ignoreRect = android.graphics.Rect()

        card.setOnTouchListener { v, event ->
            if (ignoreTapView != null) {
                ignoreTapView.getHitRect(ignoreRect)
                val x = event.x.toInt()
                val y = event.y.toInt()
                if (ignoreRect.contains(x, y)) return@setOnTouchListener false
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()
                    v.animate()
                        .scaleX(PRESS_SCALE)
                        .scaleY(PRESS_SCALE)
                        .setDuration(PRESS_DOWN_MS)
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().cancel()
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(PRESS_UP_MS)
                        .start()
                }
            }
            false
        }
    }

    private fun softPulse(v: View) {
        v.animate().cancel()
        v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(130).start()
        }.start()
    }

    // -----------------------------
    // Restore visuals on rotation/process death
    // -----------------------------
    private fun restoreAllCardsVisualState() {
        includeDomain.keys.forEach { includeId ->
            val refs = cards[includeId] ?: return@forEach
            val isOpen = openState[includeId] == true
            val domain = includeDomain[includeId] ?: return@forEach

            // If done today, never restore open. Force close.
            if (isDoneToday(domain)) {
                openState[includeId] = false
                saveOpenState(includeId)
            }

            if (isOpen && !isDoneToday(domain)) {
                lifecycleScope.launch {
                    ensureDailyRhythm(includeId, domain)
                    val dailyText = getDailyText(includeId) ?: "No rhythm available."
                    openCard(refs, dailyText)
                    refreshCardUi(includeId)
                }
            } else {
                closeCard(refs)
            }
        }
    }

    // -----------------------------
    // Completion history
    // -----------------------------
    private fun recordCompletion(domain: String) {
        val sp = getSharedPreferences("AninawPrefs", Context.MODE_PRIVATE)
        val today = epochDayNow()
        val set = getCompletionDays(domain).toMutableSet()
        set.add(today)
        sp.edit().putString(keyCompletionDays(domain), set.joinToString(",")).apply()
    }

    private fun getCompletionDays(domain: String): Set<Long> {
        val sp = getSharedPreferences("AninawPrefs", Context.MODE_PRIVATE)
        val raw = sp.getString(keyCompletionDays(domain), "") ?: ""
        if (raw.isBlank()) return emptySet()
        return raw.split(",").mapNotNull { it.toLongOrNull() }.toSet()
    }

    private fun keyCompletionDays(domain: String) = "rhythm_completion_days_$domain"

    // -----------------------------
    // Daily rhythm text (one per include per day)
    // -----------------------------
    private fun keyDay(includeId: Int) = "rhythm_${includeId}_day"
    private fun keyDailyText(includeId: Int) = "rhythm_${includeId}_daily_text"

    private fun ensureTodayState(includeId: Int) {
        val sp = getSharedPreferences("AninawPrefs", Context.MODE_PRIVATE)
        val today = todayKey()
        val savedDay = sp.getString(keyDay(includeId), null)

        if (savedDay != today) {
            sp.edit()
                .putString(keyDay(includeId), today)
                .remove(keyDailyText(includeId))
                .remove(keyOpenState(includeId))
                .apply()
        }
    }

    private fun getDailyText(includeId: Int): String? {
        val sp = getSharedPreferences("AninawPrefs", Context.MODE_PRIVATE)
        return sp.getString(keyDailyText(includeId), null)
    }

    private fun saveDaily(includeId: Int, text: String) {
        val sp = getSharedPreferences("AninawPrefs", Context.MODE_PRIVATE)
        sp.edit().putString(keyDailyText(includeId), text).apply()
    }

    private suspend fun ensureDailyRhythm(includeId: Int, domain: String) {
        val existing = getDailyText(includeId)
        if (!existing.isNullOrBlank()) return
        val chosen = pickFreshRhythmEntity(currentPhaseKey, domain, emptySet()) ?: return
        saveDaily(includeId, chosen.text)
    }

    // -----------------------------
    // Color tone helper
    // -----------------------------
    private fun adjustColor(color: Int, lightnessDelta: Float, warmDelta: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[2] = (hsl[2] + lightnessDelta).coerceIn(0f, 1f)
        hsl[0] = (hsl[0] - (warmDelta * 10f)).coerceIn(0f, 360f)
        return ColorUtils.HSLToColor(hsl)
    }

    // -----------------------------
    // Date helpers
    // -----------------------------
    private fun epochDayNow(): Long = Calendar.getInstance().timeInMillis / DAY_MS
    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    private fun todayKey(): String {
        val cal = Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    // -----------------------------
    // Pool picking
    // -----------------------------
    private fun keyUsedLong(phaseKey: String, domain: String) = "rhythm_used_long_${phaseKey}_$domain"

    private fun getUsedLong(phaseKey: String, domain: String): MutableSet<Long> {
        val sp = getSharedPreferences("AninawPrefs", Context.MODE_PRIVATE)
        val raw = sp.getString(keyUsedLong(phaseKey, domain), "") ?: ""
        if (raw.isBlank()) return mutableSetOf()
        return raw.split(",").mapNotNull { it.toLongOrNull() }.toMutableSet()
    }

    private fun saveUsedLong(phaseKey: String, domain: String, used: Set<Long>) {
        val sp = getSharedPreferences("AninawPrefs", Context.MODE_PRIVATE)
        sp.edit().putString(keyUsedLong(phaseKey, domain), used.joinToString(",")).apply()
    }

    private suspend fun pickFreshRhythmEntity(
        phaseKey: String,
        domain: String,
        excludeToday: Set<Long>
    ): RhythmEntity? {
        val pool = withContext(Dispatchers.IO) {
            rhythmDao.getByPhaseAndDomain(phaseKey, domain)
        }
        if (pool.isEmpty()) return null

        val usedLong = getUsedLong(phaseKey, domain)
        var available = pool.filter { it.id !in usedLong && it.id !in excludeToday }

        if (available.isEmpty()) {
            usedLong.clear()
            saveUsedLong(phaseKey, domain, usedLong)
            available = pool.filter { it.id !in excludeToday }
        }
        if (available.isEmpty()) available = pool

        val chosen = available.random()
        usedLong.add(chosen.id)
        saveUsedLong(phaseKey, domain, usedLong)
        return chosen
    }

    private suspend fun ensureHasSomeRhythmsForDev() = withContext(Dispatchers.IO) {
        val phase = currentPhaseKey
        val domains = listOf("BODY", "MIND", "SOUL", "ENV")

        for (domain in domains) {
            val existingTexts = rhythmDao
                .getTextsByPhaseAndDomain(phase, domain)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()

            val seedTexts = RhythmSeeder
                .textsFor(domain)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()

            val missing = seedTexts
                .filter { it !in existingTexts }
                .map { txt -> RhythmEntity(phaseKey = phase, domain = domain, text = txt) }

            if (missing.isNotEmpty()) {
                rhythmDao.insertAll(missing)
            }
        }
    }
}
