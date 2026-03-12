// MainActivity.kt
package com.aninaw

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.aninaw.data.AninawDb
import com.aninaw.views.HybridTreeView
import com.google.android.material.card.MaterialCardView
import java.util.Calendar
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import android.app.TimePickerDialog
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.aninaw.prompts.TreeStage
import androidx.lifecycle.lifecycleScope

import com.aninaw.growth.GrowthManager

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.addCallback
import android.graphics.Rect
import android.graphics.RectF
import com.aninaw.views.SpotlightHoleView
import com.aninaw.prompts.TreePromptEngine

class MainActivity : AppCompatActivity() {

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // no need to do anything; if denied, reminders won't show notifications
        }

    // --------------------------------------------
    // Growth signals (so other screens can nudge growth safely)
    // --------------------------------------------
    companion object {
        const val EXTRA_HABIT_COMPLETED = "extra_habit_completed"
        const val EXTRA_CHECKIN_COMPLETED = "extra_checkin_completed"
        const val EXTRA_CHECKIN_MOOD = "EXTRA_CHECKIN_MOOD"

        const val KEY_NICKNAME = "user_nickname"
        const val NICKNAME_MAX_LEN = 16

        const val PREF_REMIND_DAYS_MASK = "checkin_reminders_days_mask"
        const val DEFAULT_DAYS_MASK = 0b1111111 // every day
    }

    private val KEY_PROGRESS_TREE_INTRO_SHOWN = "has_seen_progress_tree_intro"

    // --------------------------------------------
    // DEBUG: FORCE PROCEDURAL TREE PREVIEW
    // --------------------------------------------
    // Turn this OFF if you want real slow growth + bonuses to show.
    private val DEBUG_FORCE_PROCEDURAL_GROWTH = false
    private val DEBUG_TREE_TIMELAPSE = false

    // --------------------------------------------
    // TIMING
    // --------------------------------------------
    private val INACTIVITY_DELAY_MS = 3000L
    private val BUBBLE_VISIBLE_MS = 3000L
    private val BUBBLE_FADE_MS = 600L

    private val SLOW_FADE_DURATION = 5000L
    private val OVERLAY_FADE_DURATION = 8000L

    private val CONTROLS_AUTOHIDE_MS = 5000L
    private val handler = Handler(Looper.getMainLooper())

    private val MIN_HOME_FADE_ALPHA = 0.22f

    private fun softAlpha(view: View, target: Float, dur: Long = SoftMotion.DUR_SHORT) {
        view.animate()
            .alpha(target)
            .setDuration(dur)
            .setInterpolator(SoftMotion.INTERP)
            .start()
    }
    private fun View.hwLayerOn() { setLayerType(View.LAYER_TYPE_HARDWARE, null) }
    private fun View.hwLayerOff() { setLayerType(View.LAYER_TYPE_NONE, null) }

    // --------------------------------------------
    // STATE
    // --------------------------------------------
    private var ambientRunning = false
    private var introRunning = false
    private var isInteracting = false

    private lateinit var switchReminders: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var textReminderStatus: TextView

    private lateinit var btnPickReminderTime: MaterialButton

    private lateinit var chipDays: com.google.android.material.chip.ChipGroup
    private lateinit var chipMon: com.google.android.material.chip.Chip
    private lateinit var chipTue: com.google.android.material.chip.Chip
    private lateinit var chipWed: com.google.android.material.chip.Chip
    private lateinit var chipThu: com.google.android.material.chip.Chip
    private lateinit var chipFri: com.google.android.material.chip.Chip
    private lateinit var chipSat: com.google.android.material.chip.Chip
    private lateinit var chipSun: com.google.android.material.chip.Chip

    private lateinit var cardCrisisResources: View
    private lateinit var btnPlantTree: View

    // Nickname drawer controls
    private lateinit var cardNickname: View
    private lateinit var textNicknameStatus: TextView

    private lateinit var tvPlantTreePartnerHint: TextView

    private var settingProgrammaticChange = false
    private var pendingEnableAfterPermission = false
    private var blinkRunnable: Runnable? = null
    private var leafRunnable: Runnable? = null

    private var showBubbleRunnable: Runnable? = null
    private var hideBubbleRunnable: Runnable? = null

    private var bubbleShownThisLaunch = false
    private var bubbleArmed = false

    private var firefliesSpawned = false

    private val prefs by lazy { getSharedPreferences("AninawPrefs", Context.MODE_PRIVATE) }

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var btnMenu: View

    // --------------------------------------------
    // MOOD CARD
    // --------------------------------------------
    private lateinit var cardMood: View
    private lateinit var imgMoodIcon: ImageView
    private lateinit var tvMoodTitle: TextView
    private lateinit var tvMoodBody: TextView

    // --------------------------------------------
    // GREETING (TOP OF CANOPY)
    // --------------------------------------------
    private lateinit var greetingGroup: View
    private lateinit var textTimeGreeting: TextView
    private lateinit var textGreetingSub: TextView
    private var greetingBobbingAnimator: ObjectAnimator? = null

    // --------------------------------------------
    // DB / PROMPTS
    // --------------------------------------------
    private lateinit var db: AninawDb
    private var promptSystemReady = false

    // --------------------------------------------
    // FIREFLIES (placeholders)
    // --------------------------------------------
    private val FIREFLIES_COUNT = 5
    private val FIREFLIES_SIZE_DP = 8f
    private val FIREFLIES_EDGE_PADDING_DP = 24f
    private val FIREFLIES_BOTTOM_SAFE_DP = 140f

    private var reinforcementShowing = false

    // --------------------------------------------
    // SOUND (placeholders)
    // --------------------------------------------
    private var soundPool: SoundPool? = null
    private var sfxTap: Int = 0
    private var sfxComplete: Int = 0
    private var sfxLeaf: Int = 0

    private var controlsAutohideRunnable: Runnable? = null

    enum class LiwanagPose(@LayoutRes val layoutRes: Int) {
        LEANING(R.layout.liwanag_pose_leaning),
        LOOKING_UP(R.layout.liwanag_pose_looking_up),
        READING(R.layout.liwanag_pose_reading),
        LOOKING_LEFT(R.layout.liwanag_pose_looking_left),
        LYING(R.layout.liwanag_pose_lying)
    }

    private fun updateDayCounterText() {
        // Make sure first_use_epoch_day exists
        ensureFirstUseEpochDay()

        val visual = TreeVisualEngine.compute(prefs)
        val dayNumber = (visual.daySinceInstall + 1).coerceAtLeast(1)

        findViewById<TextView>(R.id.tvDayCounter)?.text = "Day $dayNumber"
    }

    private fun maybePlayThreeDayStreakLeaf() {
        // Feature temporarily disabled / not wired yet.
    }

    private fun showTreeRingMemory(mem: DailyMemory) {
        TreeRingMemoryBottomSheet.newInstance(mem)
            .show(supportFragmentManager, "TreeRingMemory")
    }

    private fun buildPlaceholderAffirmations(days: Int): List<String> {
        val pool = listOf(
            "You showed up.",
            "You kept going.",
            "Small steps count.",
            "Breathe, then continue.",
            "Gentle is still progress.",
            "You are learning.",
            "You can rest and return.",
            "You are not behind.",
            "This is enough for today.",
            "Keep it simple."
        )
        val n = (days + 1).coerceIn(1, 30)
        return List(n) { i -> pool[i % pool.size] }
    }

    private fun ensureFirstUseEpochDay() {
        val existing = prefs.getLong("first_use_epoch_day", -1L)
        if (existing != -1L) return

        val todayEpochDay: Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LocalDate.now().toEpochDay()
        } else {
            System.currentTimeMillis() / 86_400_000L
        }
        prefs.edit().putLong("first_use_epoch_day", todayEpochDay).apply()
    }

    // --------------------------------------------
    // VIEWS
    // --------------------------------------------
    private lateinit var progressTreeIntroHost: View 
    private lateinit var progressTreeSpotlight: SpotlightHoleView 
    private lateinit var btnProgressTreeIntroBegin: View 

    private lateinit var progressTreeIntroCard: View 

    private lateinit var sceneCanvas: View 
    private lateinit var liwanagPoseContainer: FrameLayout 

    private var bubbleGroup: View? = null 
    private var bubbleCard: MaterialCardView? = null 
    private var bubbleTail: ImageView? = null 
    private var bubbleText: TextView? = null 

    private var fireflyLayer: FrameLayout? = null 

    private lateinit var treeHolder: View
    private lateinit var hybridTreeView: HybridTreeView

    private lateinit var bottomNavPanel: View
    private lateinit var navRhythm: View
    private lateinit var navTreeRing: View

    private lateinit var bottomWarmGradient: View

    // --------------------------------------------
    // TREE RING OVERLAY
    // --------------------------------------------
    private lateinit var treeRingOverlayHost: View
    private var treeRingOverlayScrim: View? = null
    private lateinit var treeRingOverlayView: TreeRingView

    private lateinit var treeRingFocusLayer: View
    private lateinit var treeRingHintOverlay: View
    private lateinit var treeRingHintText: TextView

    private lateinit var gesturePulse: View
    private lateinit var fingerLeft: View
    private lateinit var fingerRight: View

    private val treeRingIdleDelayMs = 2000L
    private val treeRingIdleHandler = Handler(Looper.getMainLooper())
    private var treeRingHintRunnable: Runnable? = null

    private var gestureAnimRunning = false

    // --------------------------------------------
    // DRAW-IN (FAKE BLUR VIBE) STATE
    // --------------------------------------------
    private var homeFocusActive = false

    private fun animateHomeDrawIn(enter: Boolean) {
        if (enter == homeFocusActive) return
        homeFocusActive = enter

        val dur = if (enter) 260L else 220L
        val interp = SoftMotion.INTERP

        val targetScaleScene = if (enter) 1.045f else 1f
        val targetAlphaScene = if (enter) 0.86f else 1f

        val targetScaleUi = if (enter) 1.01f else 1f
        val targetAlphaUi = if (enter) 0.92f else 1f

        sceneCanvas.animate().cancel()
        sceneCanvas.animate()
            .scaleX(targetScaleScene)
            .scaleY(targetScaleScene)
            .alpha(targetAlphaScene)
            .setDuration(dur)
            .setInterpolator(interp)
            .start()

        listOf(bottomNavPanel, greetingGroup).forEach { v ->
            v.animate().cancel()
            v.animate()
                .scaleX(targetScaleUi)
                .scaleY(targetScaleUi)
                .alpha(targetAlphaUi)
                .setDuration(dur)
                .setInterpolator(interp)
                .start()
        }
    }

    private fun dayToDev(day: Int): Float {
        return when {
            day <= 1 -> 0.12f
            day == 2 -> 0.30f
            day == 3 -> 0.45f
            day == 4 -> 0.58f
            day in 5..10 -> 0.58f + (day - 4) * (0.22f / 6f)
            day in 11..20 -> 0.80f + (day - 10) * (0.18f / 10f)
            else -> 1.00f
        }.coerceIn(0f, 1f)
    }

    private fun dayToDevFloat(day: Float): Float {
        return when {
            day <= 1f -> 0.12f
            day <= 2f -> lerp(0.12f, 0.30f, day - 1f)
            day <= 3f -> lerp(0.30f, 0.45f, day - 2f)
            day <= 4f -> lerp(0.45f, 0.58f, day - 3f)

            day <= 10f -> 0.58f + (day - 4f) * (0.22f / 6f)
            day <= 20f -> 0.80f + (day - 10f) * (0.18f / 10f)

            else -> 1.00f
        }.coerceIn(0f, 1f)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float {
        return a + (b - a) * t.coerceIn(0f, 1f)
    }

    private fun growthToStage(growth01: Float): TreeStage {
        val g = growth01.coerceIn(0f, 1f)
        return when {
            g < 0.16f -> TreeStage.SEED
            g < 0.33f -> TreeStage.SPROUT
            g < 0.50f -> TreeStage.YOUNG_TREE
            g < 0.70f -> TreeStage.GROWING_TREE
            g < 0.88f -> TreeStage.MATURE_TREE
            else -> TreeStage.FLOURISHING_TREE
        }
    }

    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) return

        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 909)
    }

    // --------------------------------------------
    // ANDROID LIFE
    // --------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.homescreen2)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        requestPostNotificationsIfNeeded()

        bindViews()

        progressTreeIntroHost = req(R.id.progressTreeIntroHost)
        progressTreeSpotlight = req(R.id.progressTreeSpotlight)
        btnProgressTreeIntroBegin = req(R.id.btnProgressTreeIntroBegin)
        progressTreeIntroCard = req(R.id.progressTreeIntroCard)

        hybridTreeView.post {
            refreshTreeFromDb(animate = false)
        }

        maybeShowProgressTreeIntro()

        onBackPressedDispatcher.addCallback(this) {
            when {
                drawerLayout.isDrawerOpen(GravityCompat.START) -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
                treeRingOverlayHost.visibility == View.VISIBLE -> {
                    hideTreeRingOverlay()
                }
                else -> {
                    finish()
                }
            }
        }

        // If you have a back button in activity_main.xml
        findViewById<View?>(R.id.btnBack)?.apply {
            isClickable = true
            isFocusable = true
            setOnClickListener {
                when {
                    ::drawerLayout.isInitialized && drawerLayout.isDrawerOpen(GravityCompat.START) -> {
                        drawerLayout.closeDrawer(GravityCompat.START)
                    }
                    ::treeRingOverlayHost.isInitialized && treeRingOverlayHost.visibility == View.VISIBLE -> {
                        hideTreeRingOverlay()
                    }
                    else -> {
                        finish()
                    }
                }
            }
        }

        refreshNicknameUi()
        updateGreetingTexts()

        updateDayCounterText()

        setupDrawer()
        setupDrawerControls()


        drawerLayout.setScrimColor(0x66000000) // soft dim


        // Growth manager (slow baseline + faster via habit/check-in
        ensureFirstUseEpochDay()
        initSound()
        initPromptSystem()
        setupLiwanag()
        setupNavigation()
        setupTreeRingOverlay()
        setupTreeTapToOpenTreeRing()
        applyLiwanagSaturation()
        syncDayNightOverlayCrossfade()
        setupDebugTreeGrowthCyclerIfDebug()
        checkAndShowIntro()

        // (Redundant but harmless if you keep it)
        cardCrisisResources = req(R.id.cardCrisisResources)
        btnPlantTree = req(R.id.btnPlantTree)
        switchReminders = req(R.id.switchCheckInReminders)
        textReminderStatus = req(R.id.textReminderStatus)
        btnPickReminderTime = req(R.id.btnPickReminderTime)
        chipDays = req(R.id.chipDays)
        chipMon = req(R.id.chipMon)
        chipTue = req(R.id.chipTue)
        chipWed = req(R.id.chipWed)
        chipThu = req(R.id.chipThu)
        chipFri = req(R.id.chipFri)
        chipSat = req(R.id.chipSat)
        chipSun = req(R.id.chipSun)

        setupReminderControls()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        updateGreetingTexts()    // ✅ handles time changes + coming back via intent
        refreshNicknameUi()

        val didComplete = intent.getBooleanExtra(EXTRA_CHECKIN_COMPLETED, false)
        if (didComplete) refreshTreeFromDb(animate = true)
    }

    override fun onResume() {
        super.onResume()

        updateGreetingTexts()    // ✅ sync time greeting every time you come back
        refreshNicknameUi()      // ✅ sync drawer subtitle every time you come back

        updateDayCounterText()

        refreshTreeFromDb(animate = false)

        ensureGreetingAnimationRunning()

        // Keep drawer nickname subtitle accurate
        refreshNicknameUi()
        consumeGrowthSignalsFromIntent()

        if (DEBUG_TREE_TIMELAPSE) {
            runTreeTimelapsePreview()
            revealHomeUI()
            showGreetingIfReady()
            return
        }

      //  refreshTreeFromGrowth()

        val prefs = getSharedPreferences("AninawPrefs", Context.MODE_PRIVATE)
        val visual = TreeVisualEngine.compute(prefs)
        android.util.Log.d(
            "TreeVisual",
            "Home onResume timelineDayFloat=${visual.timelineDayFloat} daysSinceInstall=${visual.daySinceInstall}"
        )

        startOrRespawnFireflies()
        syncDayNightOverlayCrossfade()
        applyLiwanagSaturation()
        ensureDailyPoseOrder()

        if (DEBUG_FORCE_PROCEDURAL_GROWTH) {
            revealHomeUI()
            showGreetingIfReady()
            return
        }

        if (DEBUG_TREE_TIMELAPSE) {
            runTreeTimelapsePreview()
            revealHomeUI()
            showGreetingIfReady()
            return
        }

        val hasSeenIntro = prefs.getBoolean("has_seen_homescreen_intro", false)
        if (hasSeenIntro && !introRunning) {
            applySilentPoseRefreshesIfNeeded()
            setPose(getCurrentDailyPose(), restartLeafLoop = false)

            refreshTreeFromGrowth()
            startAmbient()

            bubbleArmed = true
            resetInactivityTimer()

            maybePlayThreeDayStreakLeaf()

            revealHomeUI()
            showGreetingIfReady()
            enterMinimalHomeModeSoon()
        }

        consumePauseReinforcementIfAny()
    }

    override fun onPause() {
        super.onPause()

        stopGreetingAnimations()

        cancelTreeRingIdleHint()
        hideTreeRingIdleHint()

        // if (treeRingOverlayHost.visibility == View.VISIBLE) {
        //    treeRingOverlayHost.visibility = View.GONE
        //    treeRingOverlayScrim?.alpha = 0f
        //    treeRingFocusLayer.alpha = 0f
        //    treeRingFocusLayer.visibility = View.GONE
        //    animateHomeDrawIn(false)
        // }

        stopAmbient()
        cancelIntroSequence()

        showBubbleRunnable?.let { handler.removeCallbacks(it) }
        hideBubbleRunnable?.let { handler.removeCallbacks(it) }

        stopAndClearFireflies()

        controlsAutohideRunnable?.let { handler.removeCallbacks(it) }
        controlsAutohideRunnable = null

        handler.removeCallbacksAndMessages(null)

        releaseSound()
    }


    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {

            // Hide pinch hint immediately on any tap
            // if (::treeRingOverlayHost.isInitialized &&
            //    treeRingOverlayHost.visibility == View.VISIBLE) {

            //    cancelTreeRingIdleHint()
            //    hideTreeRingIdleHintNow()
            // }

            nudgeControlsVisible()
        }

        return super.dispatchTouchEvent(ev)
    }

    // --------------------------------------------
    // Growth wiring
    // --------------------------------------------
    private fun consumeGrowthSignalsFromIntent() {
        // If a Habit screen or Check-in screen sends these extras back to Home,
        // we count that as a "bonus" for today (one bonus is enough to advance a step).
        val didHabit = intent?.getBooleanExtra(EXTRA_HABIT_COMPLETED, false) == true
        val didCheckin = intent?.getBooleanExtra(EXTRA_CHECKIN_COMPLETED, false) == true
        val checkinMood = intent?.getStringExtra(EXTRA_CHECKIN_MOOD)

        if (didHabit) {
            intent.putExtra(EXTRA_HABIT_COMPLETED, false)
        }
        if (didCheckin) {
            intent.putExtra(EXTRA_CHECKIN_COMPLETED, false)
            if (checkinMood != null) {
                applyAdaptivePrompt(checkinMood)
            } else {
                // Fallback if mood is missing but checkin done (rare)
                applyAdaptivePrompt("Okay")
            }
        }

        // ---- Visual timeline bonus: completion today grows the tree NEXT day ----
        if (didHabit || didCheckin) {

            val todayEpochDay: Long =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) LocalDate.now().toEpochDay()
                else System.currentTimeMillis() / 86_400_000L

            val nextEpochDay = todayEpochDay + 1L

            // Must match TreeVisualEngine.BONUS_PREFIX = "tree_bonus_day_"
            prefs.edit()
                .putBoolean("tree_bonus_day_$nextEpochDay", true)
                .apply()

            android.util.Log.d(
                "TreeVisual",
                "Bonus marked for NEXT epochDay=$nextEpochDay (earned today=$todayEpochDay)"
            )
        }
    }

    // --------------------------------------------
    // ADAPTIVE PROMPTS
    // --------------------------------------------
    private fun getAdaptivePromptForMood(mood: String): String {
        return when(mood) {
            "Happy", "Loved" -> "Feel your feet on the ground. Let this feeling settle." 
            "Sad" -> "It’s okay to not be okay. Be kind to yourself."
            "Shy" -> "Take it one breath at a time. No rush."
            "Okay" -> "Notice what is here, right now, without judgment."
            else -> "You are here, and that is enough."
        }
    }

    private fun applyAdaptivePrompt(mood: String) {
        val prompt = getAdaptivePromptForMood(mood)
        
        // Show in bubble
        bubbleText?.text = prompt
        bubbleGroup?.alpha = 0f
        bubbleGroup?.visibility = View.VISIBLE
        bubbleGroup?.animate()
            ?.alpha(1f)
            ?.setDuration(800)
            ?.setStartDelay(500)
            ?.start()

        // Hide bubble after longer delay
        handler.postDelayed({
             bubbleGroup?.animate()?.alpha(0f)?.setDuration(1000)?.start()
        }, 8000)
    }

    // private enum class EmoCategory { STRESS, SAD, ANGER, CALM, OTHER }
    //
    // private fun categorizeEmotionLabel(raw: String?): EmoCategory {
    //    // ...
    // }
    //
    // private fun intensityToLevel01to5(v: Float?): Int {
    //    // ...
    // }
    //
    // private fun likertMessageForIntensity(intensity: Float?): String {
    //    // ...
    // }

    private fun pickIconForLabel(label: String?): Int {
        return getMoodDrawable(label)
    }

    private fun refreshTreeFromGrowth() {
        val visual = TreeVisualEngine.compute(prefs)
        android.util.Log.d(
            "TreeGrowthTest",
            "timelineDayFloat=${visual.timelineDayFloat} daySinceInstall=${visual.daySinceInstall}"
        )

        val g = if (DEBUG_FORCE_PROCEDURAL_GROWTH) {
            1f
        } else {
            dayToDevFloat(visual.timelineDayFloat)
        }

        android.util.Log.d(
            "TreeGrowthTest",
            "growth(g)=$g"
        )

        hybridTreeView.growth = g
        hybridTreeView.alpha = 1f
    }

    // --------------------------------------------
    // BIND VIEWS (crash-proof-ish)
    // --------------------------------------------
    private fun <T : View> req(id: Int): T {
        return findViewById<T?>(id)
            ?: throw IllegalStateException(
                "Missing view id: ${resources.getResourceEntryName(id)} in homescreen2.xml"
            )
    }

    private fun <T : View> opt(id: Int): T? = findViewById(id)

    private fun bindViews() {
        sceneCanvas = req(R.id.sceneCanvas)
        liwanagPoseContainer = req(R.id.liwanagPoseContainer)

        greetingGroup = req(R.id.greetingGroup)
        textTimeGreeting = req(R.id.tvGreeting)
        textGreetingSub = req(R.id.tvGreetingSub)

        // MOOD CARD
        cardMood = req(R.id.cardMood)
        imgMoodIcon = req(R.id.imgMoodIcon)
        tvMoodTitle = req(R.id.tvMoodTitle)
        tvMoodBody = req(R.id.tvMoodBody)

        // cardMood.setOnClickListener {
        //    // Open daily check-in (or anchor if you prefer)
        //    startActivity(Intent(this, CheckInAnchorActivity::class.java))
        //    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        // }
        cardMood.isClickable = false
        cardMood.isFocusable = false

        bubbleGroup = opt(R.id.bubbleGroup)
        bubbleCard = opt(R.id.cardLiwanagBubble)
        bubbleTail = opt(R.id.bubbleTail)
        bubbleText = opt(R.id.textLiwanagPrompt)

        treeHolder = req(R.id.layoutTreeHero)
        hybridTreeView = req(R.id.hybridTreeView)

        bottomNavPanel = req(R.id.cardBottomNav)

        treeRingOverlayHost = req(R.id.treeRingOverlayHost)
        treeRingOverlayScrim = opt(R.id.treeRingOverlayScrim)
        treeRingOverlayView = req(R.id.treeRingOverlayView)

        treeRingFocusLayer = req(R.id.treeRingFocusLayer)
        treeRingHintOverlay = req(R.id.treeRingHintOverlay)
        treeRingHintText = req(R.id.treeRingHintText)

        gesturePulse = req(R.id.gesturePulse)
        fingerLeft = req(R.id.fingerLeft)
        fingerRight = req(R.id.fingerRight)

        drawerLayout = req(R.id.drawerLayout)
        btnMenu = req(R.id.btnMenu)

        findViewById<TextView?>(R.id.tvDebugDay)?.visibility = View.GONE

        findViewById<View?>(R.id.cardReflection)?.setOnClickListener {
            val intent = Intent(this, JournalEditorActivity::class.java).apply {
                putExtra(JournalEditorActivity.EXTRA_PROMPT, getString(R.string.tool_journal_sub))
                putExtra(JournalEditorActivity.EXTRA_TYPE, "QUICK")
            }
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        cardCrisisResources = req(R.id.cardCrisisResources)
        btnPlantTree = req(R.id.btnPlantTree)

        cardNickname = req(R.id.cardNickname)
        textNicknameStatus = req(R.id.textNicknameStatus)

        drawerBackButton = req(R.id.incBackRhythm)
    }

    private fun updateDebugDayLabel(label: TextView, day: Int) {
        label.text = "Day $day of 21 (tap to change)"
    }

    private lateinit var drawerBackButton: View

    private fun setupDrawerControls() {
        drawerBackButton.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        cardNickname.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            showNicknameDialog()
        }

        cardCrisisResources.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, CrisisResourcesActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }


        btnPlantTree.setOnClickListener {
            showPlantTreeDialog()
        }
    }

    private fun showPlantTreeDialog() {
        // Optional: close drawer first so the dialog feels “centered”
        if (::drawerLayout.isInitialized && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Plant a Real Tree")
            .setMessage(
                "This feature will support verified tree-planting partners.\n\n" +
                        "Transparent, optional, and never required.\n\n" +
                        "Coming soon."
            )
            .setPositiveButton("Okay", null)
            // Optional action button for later
            // .setNeutralButton("Learn more") { _, _ -> /* open link or activity */ }
            .show()
    }

    private fun loadDaysMask(): Int =
        prefs.getInt(PREF_REMIND_DAYS_MASK, DEFAULT_DAYS_MASK)

    private fun saveDaysMask(mask: Int) {
        prefs.edit().putInt(PREF_REMIND_DAYS_MASK, mask).apply()
    }

    private fun setChipStatesFromMask(mask: Int) {
        chipMon.isChecked = (mask and (1 shl 0)) != 0
        chipTue.isChecked = (mask and (1 shl 1)) != 0
        chipWed.isChecked = (mask and (1 shl 2)) != 0
        chipThu.isChecked = (mask and (1 shl 3)) != 0
        chipFri.isChecked = (mask and (1 shl 4)) != 0
        chipSat.isChecked = (mask and (1 shl 5)) != 0
        chipSun.isChecked = (mask and (1 shl 6)) != 0
    }

    private fun computeMaskFromChips(): Int {
        var mask = 0
        if (chipMon.isChecked) mask = mask or (1 shl 0)
        if (chipTue.isChecked) mask = mask or (1 shl 1)
        if (chipWed.isChecked) mask = mask or (1 shl 2)
        if (chipThu.isChecked) mask = mask or (1 shl 3)
        if (chipFri.isChecked) mask = mask or (1 shl 4)
        if (chipSat.isChecked) mask = mask or (1 shl 5)
        if (chipSun.isChecked) mask = mask or (1 shl 6)
        return mask
    }

    private fun formatSelectedDays(mask: Int): String {
        if (mask == 0) return "No days selected"
        if (mask == 0b1111111) return "Every day"
        val names = listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")
        val picked = names.filterIndexed { idx, _ -> (mask and (1 shl idx)) != 0 }
        return picked.joinToString(", ")
    }

    private fun setupReminderControls() {
        // Load saved
        val enabled = prefs.getBoolean("checkin_reminders_enabled", false)
        val hour = prefs.getInt("checkin_reminders_hour", 20)
        val minute = prefs.getInt("checkin_reminders_minute", 0)
        val daysMask = loadDaysMask()
        settingProgrammaticChange = true
        setChipStatesFromMask(daysMask)
        settingProgrammaticChange = false

        // Apply UI
        settingProgrammaticChange = true
        switchReminders.isChecked = enabled
        settingProgrammaticChange = false

        btnPickReminderTime.visibility = if (enabled) View.VISIBLE else View.GONE
        chipDays.visibility = if (enabled) View.VISIBLE else View.GONE

        val maskNow = loadDaysMask()
        textReminderStatus.text = if (enabled) {
            "${formatSelectedDays(maskNow)} • ${format12Hour(hour, minute)}"
        } else {
            "Off"
        }

        btnPickReminderTime.text = "Select time (${format12Hour(hour, minute)})"

        // Toggle
        switchReminders.setOnCheckedChangeListener { _, isChecked ->
            if (settingProgrammaticChange) return@setOnCheckedChangeListener

            prefs.edit().putBoolean("checkin_reminders_enabled", isChecked).apply()

            if (isChecked) {
                btnPickReminderTime.visibility = View.VISIBLE
                chipDays.visibility = View.VISIBLE
                textReminderStatus.text = "Daily at ${format12Hour(hour, minute)}"

                ReminderScheduler.scheduleDaily(this)

                val maskNow = loadDaysMask()
                textReminderStatus.text = "${formatSelectedDays(maskNow)} • ${format12Hour(hour, minute)}"

            } else {
                btnPickReminderTime.visibility = View.GONE
                chipDays.visibility = View.GONE
                textReminderStatus.text = "Off"
                ReminderScheduler.cancel(this)
            }
        }


        // Time picker
        btnPickReminderTime.setOnClickListener {
            val curHour = prefs.getInt("checkin_reminders_hour", 20)
            val curMinute = prefs.getInt("checkin_reminders_minute", 0)

            TimePickerDialog(
                this,
                { _, h, m ->
                    prefs.edit()
                        .putInt("checkin_reminders_hour", h)
                        .putInt("checkin_reminders_minute", m)
                        .apply()

                    btnPickReminderTime.text = "Select time (${format12Hour(h, m)})"
                    textReminderStatus.text = "Daily at ${format12Hour(h, m)}"

                    // If reminders are enabled, reschedule with new time
                    if (prefs.getBoolean("checkin_reminders_enabled", false)) {
                        ReminderScheduler.scheduleDaily(this)
                    }

                    Toast.makeText(this, "Reminder set for ${format12Hour(h, m)}", Toast.LENGTH_SHORT).show()
                },
                curHour,
                curMinute,
                false
            ).show()
        }
        val chipListener = View.OnClickListener {
            if (settingProgrammaticChange) return@OnClickListener

            val newMask = computeMaskFromChips()
            saveDaysMask(newMask)

            val curHour = prefs.getInt("checkin_reminders_hour", 20)
            val curMinute = prefs.getInt("checkin_reminders_minute", 0)

            textReminderStatus.text =
                "${formatSelectedDays(newMask)} • ${format12Hour(curHour, curMinute)}"

            if (prefs.getBoolean("checkin_reminders_enabled", false)) {
                ReminderScheduler.scheduleDaily(this)
            }
        }

        listOf(chipMon, chipTue, chipWed, chipThu, chipFri, chipSat, chipSun).forEach {
            it.setOnClickListener(chipListener)
        }
    }

    private fun format12Hour(hour24: Int, minute: Int): String {
        val ampm = if (hour24 >= 12) "PM" else "AM"
        val hour12 = when (val h = hour24 % 12) { 0 -> 12; else -> h }
        return String.format("%d:%02d %s", hour12, minute, ampm)
    }

    // --------------------------------------------
    // TREE RING IDLE HINT
    // --------------------------------------------
    private fun scheduleTreeRingIdleHint() {
        cancelTreeRingIdleHint()
        treeRingHintRunnable = Runnable {
            if (treeRingOverlayHost.visibility == View.VISIBLE) {
                showTreeRingIdleHint()
            }
        }
        treeRingIdleHandler.postDelayed(treeRingHintRunnable!!, treeRingIdleDelayMs)
    }

    private fun cancelTreeRingIdleHint() {
        treeRingHintRunnable?.let { treeRingIdleHandler.removeCallbacks(it) }
        treeRingHintRunnable = null
    }

    private fun showTreeRingIdleHint() {
        treeRingFocusLayer.animate().cancel()
        treeRingFocusLayer.alpha = 0f
        treeRingFocusLayer.visibility = View.VISIBLE

        treeRingHintOverlay.animate().cancel()
        treeRingHintOverlay.visibility = View.VISIBLE
        treeRingHintOverlay.alpha = 0f
        treeRingHintOverlay.scaleX = 0.98f
        treeRingHintOverlay.scaleY = 0.98f

        treeRingFocusLayer.animate()
            .alpha(0.26f)
            .setDuration(220L)
            .setInterpolator(SoftMotion.INTERP)
            .start()

        treeRingHintOverlay.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(260L)
            .setInterpolator(SoftMotion.INTERP)
            .withEndAction { startPinchHintAnimation() }
            .start()
    }

    private fun hideTreeRingIdleHintNow() {
        stopPinchHintAnimation()

        treeRingHintOverlay.animate().cancel()
        treeRingHintOverlay.animate()
            .alpha(0f)
            .scaleX(0.98f)
            .scaleY(0.98f)
            .setDuration(180L)
            .setInterpolator(SoftMotion.INTERP)
            .withEndAction { treeRingHintOverlay.visibility = View.GONE }
            .start()

        treeRingFocusLayer.animate().cancel()
        treeRingFocusLayer.animate()
            .alpha(0f)
            .setDuration(180L)
            .setInterpolator(SoftMotion.INTERP)
            .withEndAction { treeRingFocusLayer.visibility = View.GONE }
            .start()
    }

    private fun showTreeRingOverlay() {
        val visual = TreeVisualEngine.compute(prefs)
        val daysElapsed = visual.daySinceInstall.coerceAtLeast(0)

        val maxVisible = 180
        val totalRings = (daysElapsed + 1).coerceAtLeast(1)
        val visibleRingCount = kotlin.math.min(totalRings, maxVisible)

        val visibleDaysElapsed = visibleRingCount - 1

        // Bind rings + placeholder text (you can swap affirmations later)
        treeRingOverlayView.bind(
            stageName = "SAPLING",
            daysElapsed = visibleDaysElapsed,
            affirmations = buildPlaceholderAffirmations(visibleDaysElapsed)
        )

        // ✅ THIS is what you were missing: ring tap handler
        treeRingOverlayView.onRingTapped = { mem ->
            android.util.Log.d("TreeRingTap", "Overlay tap day=${mem.dateIso}")
            showTreeRingMemory(mem)
        }

        // Background tap closes overlay
        treeRingOverlayView.onBackgroundTapped = { hideTreeRingOverlay() }

        // ✅ Load memory data for the visible ring range
        val end = java.time.LocalDate.now()
        val start = end.minusDays((visibleRingCount - 1).toLong())

        val db = AninawDb.getDatabase(this)
        val repo = com.aninaw.data.treering.TreeRingMemoryRepository(db)

        lifecycleScope.launch {
            val logs = withContext(Dispatchers.IO) {
                repo.getRange(start, end)
            }

            val bestPerDay: Map<String, com.aninaw.data.treering.TreeRingMemoryEntity> =
                logs.groupBy { it.date }.mapValues { (_, list) ->
                    // Prefer FULL over QUICK; if tie, prefer latest timestamp
                    list.sortedWith(
                        compareBy<com.aninaw.data.treering.TreeRingMemoryEntity> { it.type != "FULL" }
                            .thenByDescending { it.timestamp }
                    ).first()
                }

            val memoryList = (0 until visibleRingCount).map { i ->
                val date = start.plusDays(i.toLong())
                val iso = date.toString()
                val e = bestPerDay[iso]

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

            treeRingOverlayView.setRings(visibleDaysElapsed, memoryList)
        }

        // --- show overlay (your existing animation code) ---
        animateHomeDrawIn(true)

        treeRingOverlayHost.alpha = 0f
        treeRingOverlayHost.visibility = View.VISIBLE

        treeRingOverlayView.scaleX = 0.985f
        treeRingOverlayView.scaleY = 0.985f
        treeRingOverlayView.alpha = 0f

        treeRingOverlayHost.hwLayerOn()
        treeRingOverlayView.hwLayerOn()

        treeRingOverlayHost.animate()
            .alpha(1f)
            .setDuration(260L)
            .setInterpolator(SoftMotion.INTERP)
            .withEndAction { treeRingOverlayHost.hwLayerOff() }
            .start()

        treeRingOverlayView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(260L)
            .setInterpolator(SoftMotion.INTERP)
            .withEndAction { treeRingOverlayView.hwLayerOff() }
            .start()

        scheduleTreeRingIdleHint()
    }

    private fun hideTreeRingIdleHint() {
        stopPinchHintAnimation()

        treeRingHintOverlay.animate().cancel()
        treeRingHintOverlay.animate()
            .alpha(0f)
            .scaleX(0.98f)
            .scaleY(0.98f)
            .setDuration(180L)
            .setInterpolator(SoftMotion.INTERP)
            .withEndAction { treeRingHintOverlay.visibility = View.GONE }
            .start()

        treeRingFocusLayer.animate().cancel()
        treeRingFocusLayer.animate()
            .alpha(0f)
            .setDuration(180L)
            .setInterpolator(SoftMotion.INTERP)
            .withEndAction { treeRingFocusLayer.visibility = View.GONE }
            .start()
    }

    private fun startPinchHintAnimation() {
        if (gestureAnimRunning) return
        gestureAnimRunning = true

        val d = resources.displayMetrics.density
        val interp = AccelerateDecelerateInterpolator()

        fingerLeft.animate().cancel()
        fingerRight.animate().cancel()
        gesturePulse.animate().cancel()

        fingerLeft.rotation = -18f
        fingerRight.rotation = 18f

        fingerLeft.translationX = -18f * d
        fingerLeft.translationY = 10f * d
        fingerRight.translationX = 18f * d
        fingerRight.translationY = -10f * d

        fingerLeft.alpha = 0.78f
        fingerRight.alpha = 0.78f

        gesturePulse.scaleX = 1f
        gesturePulse.scaleY = 1f
        gesturePulse.alpha = 0.10f

        val outDx = 34f * d
        val outDy = 18f * d

        fingerLeft.animate()
            .translationX(-outDx)
            .translationY(outDy)
            .setDuration(850L)
            .setInterpolator(interp)
            .withEndAction { if (gestureAnimRunning) loopPinchHintAnimation() }
            .start()

        fingerRight.animate()
            .translationX(outDx)
            .translationY(-outDy)
            .setDuration(850L)
            .setInterpolator(interp)
            .start()

        gesturePulse.animate()
            .alpha(0.0f)
            .scaleX(2.0f)
            .scaleY(2.0f)
            .setDuration(850L)
            .setInterpolator(interp)
            .start()
    }

    private fun loopPinchHintAnimation() {
        if (!gestureAnimRunning) return

        val d = resources.displayMetrics.density
        val interp = AccelerateDecelerateInterpolator()

        fingerLeft.animate().cancel()
        fingerRight.animate().cancel()
        gesturePulse.animate().cancel()

        fingerLeft.translationX = -18f * d
        fingerLeft.translationY = 10f * d
        fingerRight.translationX = 18f * d
        fingerRight.translationY = -10f * d

        gesturePulse.scaleX = 1f
        gesturePulse.scaleY = 1f
        gesturePulse.alpha = 0.10f

        val outDx = 34f * d
        val outDy = 18f * d

        fingerLeft.animate()
            .translationX(-outDx)
            .translationY(outDy)
            .setStartDelay(260L)
            .setDuration(850L)
            .setInterpolator(interp)
            .start()

        fingerRight.animate()
            .translationX(outDx)
            .translationY(-outDy)
            .setStartDelay(260L)
            .setDuration(850L)
            .setInterpolator(interp)
            .start()

        gesturePulse.animate()
            .alpha(0.0f)
            .scaleX(2.0f)
            .scaleY(2.0f)
            .setStartDelay(260L)
            .setDuration(850L)
            .setInterpolator(interp)
            .start()
    }

    private fun stopPinchHintAnimation() {
        gestureAnimRunning = false
        fingerLeft.animate().cancel()
        fingerRight.animate().cancel()
        gesturePulse.animate().cancel()
    }

    // --------------------------------------------
    // TREE RING OVERLAY
    // --------------------------------------------
    private fun setupTreeRingOverlay() {
        treeRingOverlayHost.visibility = View.GONE

        treeRingOverlayScrim?.alpha = 0f

        treeRingFocusLayer.alpha = 0f
        treeRingFocusLayer.visibility = View.GONE

        treeRingHintOverlay.visibility = View.GONE
        treeRingHintOverlay.alpha = 0f

        treeRingOverlayScrim?.setOnTouchListener { _, _ ->
            false // do NOT consume; let TreeRingView handle taps
        }

        treeRingOverlayHost.setOnClickListener(null)
    }

    private fun hideTreeRingOverlay() {
        if (treeRingOverlayHost.visibility != View.VISIBLE) return

        treeRingOverlayHost.hwLayerOn()
        treeRingOverlayView.hwLayerOn()


        cancelTreeRingIdleHint()
        hideTreeRingIdleHint()

        animateHomeDrawIn(false)

        treeRingOverlayView.animate().cancel()
        treeRingOverlayHost.animate().cancel()

        treeRingOverlayView.animate()
            .alpha(0f)
            .scaleX(0.985f)
            .scaleY(0.985f)
            .setDuration(200L)
            .setInterpolator(SoftMotion.INTERP)
            .withEndAction { treeRingOverlayView.hwLayerOff() }
            .start()

        treeRingOverlayHost.animate()
            .alpha(0f)
            .setDuration(200L)
            .setInterpolator(SoftMotion.INTERP)
            .withEndAction {
                treeRingOverlayHost.hwLayerOff()
                treeRingOverlayHost.visibility = View.GONE
                treeRingOverlayView.softResetView()
            }
            .start()
    }

    private fun openTreeRingFromTree() {
        val i = Intent(this, TreeRingAnimationActivity::class.java)
        startActivity(i)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun setupTreeTapToOpenTreeRing() {
        val open = View.OnClickListener { openTreeRingFromTree() }

        treeHolder.isClickable = true
        treeHolder.isFocusable = true
        treeHolder.setOnClickListener(open)

        hybridTreeView.isClickable = true
        hybridTreeView.isFocusable = true
        hybridTreeView.setOnClickListener(open)
    }

    // --------------------------------------------
    // GREETING + NICKNAME
    // --------------------------------------------

    private fun ensureGreetingAnimationRunning() {
        if (!::greetingGroup.isInitialized) return

        // Always keep the greeting text current.
        updateGreetingTexts()

        if (introRunning) {
            // Intro owns visibility; don't animate underneath it.
            stopGreetingAnimations()
            greetingGroup.visibility = View.INVISIBLE
            greetingGroup.alpha = 0f
            return
        }

        greetingGroup.visibility = View.VISIBLE

        // If it's already bobbing, don't restart it (prevents "jump" on resume).
        val running = greetingBobbingAnimator?.isRunning == true
        if (!running) {
            animateGreetingInThenBob()
        }
    }

    private fun showGreetingIfReady() {
        if (introRunning) {
            greetingGroup.alpha = 0f
            greetingGroup.visibility = View.INVISIBLE
            stopGreetingAnimations()
            return
        }

        greetingGroup.visibility = View.VISIBLE
        updateGreetingTexts()
        animateGreetingInThenBob()
    }

    private fun getNickname(): String? {
        val raw = prefs.getString(KEY_NICKNAME, null)?.trim()
        if (raw.isNullOrBlank()) return null
        return raw.take(NICKNAME_MAX_LEN)
    }

    private fun setNickname(name: String?) {
        val cleaned = name?.trim().orEmpty().take(NICKNAME_MAX_LEN)
        if (cleaned.isBlank()) {
            prefs.edit().remove(KEY_NICKNAME).apply()
        } else {
            prefs.edit().putString(KEY_NICKNAME, cleaned).apply()
        }
    }

    private fun refreshNicknameUi() {
        val nick = getNickname()
        textNicknameStatus.text = nick ?: "Not set"
    }

    private fun updateGreetingTexts() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val base = when (hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }

        val nickname = getNickname()
        textTimeGreeting.text = if (nickname != null) {
            "$base, $nickname."
        } else {
            "$base."
        }
        
        // Reset subtitle to default prompt
        textGreetingSub.text = "Pause for one slow breath."
        textGreetingSub.visibility = View.VISIBLE

        // Update the Mood Card separately
        updateMoodCard()
    }

    private fun getMoodDrawable(label: String?): Int {
        val t = (label ?: "").trim().lowercase()
        return when {
            t.contains("happy") || t.contains("calm") || t.contains("grateful") -> R.drawable.happy_mood
            t.contains("love") || t.contains("loved") -> R.drawable.happy_mood
            t.contains("okay") || t.contains("steady") -> R.drawable.okay_mood
            t.contains("shy") || t.contains("neutral") || t.contains("numb") || t.contains("tired") -> R.drawable.neutral_mood
            t.contains("difficult") || t.contains("tense") || t.contains("anx") || t.contains("angry") || t.contains("stress") -> R.drawable.tense_mood1
            t.contains("sad") || t.contains("heavy") -> R.drawable.sad_mood
            else -> R.drawable.ic_sprout
        }
    }

    private fun updateMoodCard() {
        lifecycleScope.launch {
            val db = AninawDb.getDatabase(this@MainActivity)
            val ringRepo = com.aninaw.data.treering.TreeRingMemoryRepository(db)
            val today = LocalDate.now()

            val todayIso = today.toString()

            // Get latest quick/full check-in for today
            val latestCheckIn = withContext(Dispatchers.IO) {
                val list = ringRepo.getRange(today, today)
                list.maxByOrNull { it.timestamp ?: 0L }
            }

            // Get latest journal for today (one-shot)
            val latestJournal = withContext(Dispatchers.IO) {
                runCatching { db.journalDao().getLatestForDate(todayIso) }.getOrNull()
            }

            // Decide latest mood source (journal vs check-in) by timestamp
            val latestMoodLabel: String? = when {
                latestJournal != null && (latestCheckIn?.timestamp ?: 0L) < (latestJournal.timestamp) -> {
                    latestJournal.mood
                }
                else -> latestCheckIn?.emotion
            }

            // Logic Tree for Mood Card
            if (latestMoodLabel.isNullOrBlank()) {
                // Not checked in yet
                tvMoodTitle.text = "How are you feeling today?"
                tvMoodBody.text = "You can take a quiet moment to check in."
                tvMoodBody.visibility = View.VISIBLE
                imgMoodIcon.setImageResource(R.drawable.ic_sprout)
                imgMoodIcon.alpha = 0.5f
            } else {
                // Checked in
                val mood = latestMoodLabel
                tvMoodTitle.text = "Mood: $mood"

                // Update icon
                val iconRes = getMoodDrawable(mood)
                imgMoodIcon.setImageResource(iconRes)
                imgMoodIcon.alpha = 1.0f

                // Show Likert-based line ONLY
                // tvMoodBody.text = likertMessageForIntensity(latestCheckIn?.intensity)
                val start = today.minusDays(6)
                val recentLogs = withContext(Dispatchers.IO) { ringRepo.getRange(start, today) }
                val cats = recentLogs.map { com.aninaw.prompts.AdaptivePromptEngine.categorizeEmotionLabel(it.emotion) }
                
                tvMoodBody.text = com.aninaw.prompts.AdaptivePromptEngine.computeReflectionLine(mood, latestCheckIn?.intensity, cats)
                tvMoodBody.visibility = View.VISIBLE
            }
        }
    }

    private fun showNicknameDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "Nickname (optional)"
            setSingleLine(true)
            setText(getNickname().orEmpty())
            setSelection(text?.length ?: 0)
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("What should we call you?")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                setNickname(input.text?.toString())
                refreshNicknameUi()
                updateGreetingTexts()
            }
            .setNeutralButton("Clear") { _, _ ->
                setNickname(null)
                refreshNicknameUi()
                updateGreetingTexts()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun animateGreetingInThenBob() {
        stopGreetingAnimations()

        val risePx = dp(6f)
        greetingGroup.animate().cancel()
        greetingGroup.alpha = 0f
        greetingGroup.translationY = risePx

        greetingGroup.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300L)
            .setInterpolator(SoftMotion.INTERP)
            .withEndAction {
                val bobPx = dp(1.6f)
                greetingBobbingAnimator = ObjectAnimator.ofFloat(
                    greetingGroup,
                    "translationY",
                    0f,
                    -bobPx,
                    0f
                ).apply {
                    duration = 7000L
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.RESTART
                    interpolator = SoftMotion.INTERP
                    start()
                }
            }
            .start()
    }

    private fun stopGreetingAnimations() {
        greetingBobbingAnimator?.cancel()
        greetingBobbingAnimator = null
        greetingGroup.animate().cancel()
    }

    private fun openGrowthImpact() {
        startActivity(Intent(this, GrowthStoryActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    // --------------------------------------------
    // NAVIGATION
    // --------------------------------------------
    private fun setupNavigation() {
        opt<MaterialCardView>(R.id.tilePause)?.setOnClickListener {
            startActivity(Intent(this, CheckInAnchorActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        opt<MaterialCardView>(R.id.tileRhythm)?.setOnClickListener {
            startActivity(Intent(this, RhythmActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        opt<View>(R.id.navDays)?.setOnClickListener {
            startActivity(Intent(this, RhythmActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        opt<View>(R.id.navWrite)?.setOnClickListener {
            startActivity(Intent(this, JournalActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        opt<MaterialCardView>(R.id.tileJournal)?.setOnClickListener {
            startActivity(Intent(this, JournalActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        opt<MaterialCardView>(R.id.tileSystems)?.setOnClickListener {
            startActivity(Intent(this, SystemsActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        opt<MaterialCardView>(R.id.tileBreathe)?.setOnClickListener {
            startActivity(Intent(this, MomentActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        opt<MaterialCardView>(R.id.tileTreeRing)?.setOnClickListener {
            openGrowthImpact()
            playSound(sfxTap)
        }

        opt<MaterialCardView>(R.id.tileRoots)?.setOnClickListener {
            startActivity(Intent(this, UntangleActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        opt<View>(R.id.navResources)?.setOnClickListener {
            startActivity(Intent(this, CalmToolsActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun setupDrawer() {
        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    // --------------------------------------------
    // HOME UI VISIBILITY (safe no-op stubs)
    // --------------------------------------------
    private fun hideHomeUI() {}
    private fun revealHomeUI() {}
    private fun enterMinimalHomeModeSoon() {}
    private fun nudgeControlsVisible() {}

    // --------------------------------------------
    // LIWANAG SATURATION
    // --------------------------------------------
    private fun applyLiwanagSaturation() {
        val saturation = 0.82f
        val cm = ColorMatrix().apply { setSaturation(saturation) }
        liwanagPoseContainer.post {
            applyColorMatrixToImageViews(liwanagPoseContainer, ColorMatrixColorFilter(cm))
        }
    }

    private fun applyColorMatrixToImageViews(root: View, filter: ColorMatrixColorFilter) {
        if (root is ImageView) {
            root.colorFilter = filter
            return
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                applyColorMatrixToImageViews(root.getChildAt(i), filter)
            }
        }
    }

    // --------------------------------------------
    // SOUND (safe stubs)
    // --------------------------------------------
    private val KEY_SOUNDS_ENABLED = "sounds_enabled"
    private fun soundsEnabled(): Boolean = prefs.getBoolean(KEY_SOUNDS_ENABLED, false)

    private fun initSound() {}
    private fun playSound(id: Int) {}
    private fun releaseSound() {}

    // --------------------------------------------
    // PROMPTS / BUBBLES (safe stubs)
    // --------------------------------------------
    private fun initPromptSystem() {}
    private fun resetInactivityTimer() {}
    private fun consumePauseReinforcementIfAny() {}

    // --------------------------------------------
    // FIREFLIES (safe stubs)
    // --------------------------------------------
    private fun startOrRespawnFireflies() {}
    private fun stopAndClearFireflies() {}

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    // --------------------------------------------
    // DAY/NIGHT (safe stub)
    // --------------------------------------------
    private fun syncDayNightOverlayCrossfade() {}

    // --------------------------------------------
    // INTRO FLOW
    // --------------------------------------------
    private fun checkAndShowIntro() {
        if (DEBUG_FORCE_PROCEDURAL_GROWTH) {
            revealHomeUI()
            showGreetingIfReady()
            return
        }

        if (!prefs.getBoolean("has_seen_homescreen_intro", false)) {
            startFirstTimeFlow()
        } else {
            revealHomeUI()
            // DO NOT override DB growth here
            showGreetingIfReady()
        }
    }

    private fun startFirstTimeFlow() {
        // If your intro sequence isn't implemented yet, don't trap the UI in introRunning=true.
        introRunning = false
        prefs.edit().putBoolean("has_seen_homescreen_intro", true).apply()

        revealHomeUI()
        showGreetingIfReady()
    }

    private fun cancelIntroSequence() {}

    // --------------------------------------------
    // LIWANAG (safe stubs)
    // --------------------------------------------
    private fun setupLiwanag() {}
    private fun ensureDailyPoseOrder() {}
    private fun applySilentPoseRefreshesIfNeeded() {}
    private fun getCurrentDailyPose(): LiwanagPose = LiwanagPose.LEANING
    private fun setPose(pose: LiwanagPose, restartLeafLoop: Boolean = true) {}
    private fun startAmbient() {}
    private fun stopAmbient() {}

    // --------------------------------------------
    // DEBUG
    // --------------------------------------------
    private fun isDebuggable(): Boolean {
        return (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private fun runTreeTimelapsePreview() {
        // Quick preview mode: force a fully-grown tree.
        hybridTreeView.growth = 1.0f
        hybridTreeView.alpha = 1f
    }

    private fun setupDebugTreeGrowthCyclerIfDebug() {}

    private object TreeVisualEngine {
        private const val KEY_FIRST_USE_EPOCH_DAY = "first_use_epoch_day"
        private const val KEY_LAST_VISUAL_EPOCH_DAY = "tree_last_visual_epoch_day"
        private const val KEY_VISUAL_PROGRESS = "tree_visual_progress" // int steps
        private const val KEY_IDLE_ACCUM = "tree_visual_idle_accum"     // 0..4
        private const val KEY_DEBUG_TREE_DAY = "debug_tree_day"

        // Must match bonus writer ("tree_bonus_day_$epochDay")
        private const val BONUS_PREFIX = "tree_bonus_day_"

        // 5-day delay when no bonus
        private const val IDLE_DAYS_PER_STEP = 5

        data class VisualState(
            val timelineDayFloat: Float, // 1f..364f (moves daily)
            val daySinceInstall: Int
        )

        fun compute(prefs: android.content.SharedPreferences): VisualState {
            val today = epochDayNow()
            var firstUse = prefs.getLong(KEY_FIRST_USE_EPOCH_DAY, -1L)
            if (firstUse == -1L) {
                firstUse = today
                prefs.edit()
                    .putLong(KEY_FIRST_USE_EPOCH_DAY, firstUse)
                    .putLong(KEY_LAST_VISUAL_EPOCH_DAY, today)
                    .putInt(KEY_VISUAL_PROGRESS, 0)
                    .putInt(KEY_IDLE_ACCUM, 0)
                    .apply()
            }

            val daySinceInstall = (today - firstUse).toInt().coerceAtLeast(0)

            val lastDay = prefs.getLong(KEY_LAST_VISUAL_EPOCH_DAY, firstUse)
            var progress = prefs.getInt(KEY_VISUAL_PROGRESS, 0).coerceAtLeast(0)
            var idleAccum = prefs.getInt(KEY_IDLE_ACCUM, 0).coerceIn(0, IDLE_DAYS_PER_STEP - 1)

            if (today > lastDay) {
                for (d in (lastDay + 1)..today) {
                    val didBonus = prefs.getBoolean("$BONUS_PREFIX$d", false)

                    if (didBonus) {
                        progress += 1
                        idleAccum = 0
                    } else {
                        // STRICT MODE: Only grow if the user actively uses the app (bonus day).
                        // No passive idle growth.
                        idleAccum = 0
                    }

                    progress = progress.coerceAtMost(364)
                }

                prefs.edit()
                    .putLong(KEY_LAST_VISUAL_EPOCH_DAY, today)
                    .putInt(KEY_VISUAL_PROGRESS, progress)
                    .putInt(KEY_IDLE_ACCUM, idleAccum)
                    .apply()
            }

            val baseDay = (progress + 1).coerceIn(1, 364)

// Daily micro-growth even without bonus
            val idleFrac = idleAccum.toFloat() / IDLE_DAYS_PER_STEP.toFloat()

            val dayFloat = (baseDay + idleFrac).coerceAtMost(364f)

            val debugDay = prefs.getInt(KEY_DEBUG_TREE_DAY, 0)
            if (debugDay in 1..21) {
                val mapped = 1f + ((debugDay - 1).toFloat() / 20f) * (364f - 1f)
                return VisualState(
                    timelineDayFloat = mapped,
                    daySinceInstall = debugDay - 1
                )
            }

            return VisualState(
                timelineDayFloat = dayFloat,
                daySinceInstall = daySinceInstall
            )
        }

        private fun epochDayNow(): Long {
            val real = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                java.time.LocalDate.now().toEpochDay()
            } else {
                System.currentTimeMillis() / 86_400_000L
            }

            // DEBUG: shift "today" forward by N days
            val shiftDays = 0L  // change this: 0, 1, 2, 3...
            return real + shiftDays
        }
    }

    private fun refreshTreeFromDb(animate: Boolean) {
        val tree = findViewById<HybridTreeView>(R.id.hybridTreeView)
        val tv = findViewById<TextView>(R.id.tvTreeNarrative)
        val db = AninawDb.getDatabase(this)

        lifecycleScope.launch {
            val ui = withContext(Dispatchers.IO) { GrowthManager.load(db) }

            val debugDay = prefs.getInt("debug_tree_day", 0)
            val safeGrowth = if (debugDay in 1..21) {
                val base = 0.12f
                val step = 0.04f  // bigger jump per day so leaves change visibly
                (base + step * (debugDay - 1)).coerceIn(0.12f, 1.0f)
            } else {
                ui.growth01.coerceAtLeast(0.12f)
            }
            val stage = growthToStage(safeGrowth)

            val adaptive = withContext(Dispatchers.IO) {
                val repo = com.aninaw.data.treering.TreeRingMemoryRepository(db)
                val profile = TreePromptEngine.buildProfile(repo)
                val daySeed = TreeVisualEngine.compute(prefs).daySinceInstall
                TreePromptEngine.pickPrompt(profile, daySeed, stage)
            }

            val line = ui.milestone ?: adaptive

            if (!animate) {
                // Don't let a fresh/empty DB force the tree to 0 and hide the sprout.
                tree.growth = safeGrowth
                tv?.text = line // Safe call
                return@launch
            }

            val start = tree.growth
            val end = safeGrowth

            ValueAnimator.ofFloat(start, end).apply {
                duration = 650L
                addUpdateListener { a ->
                    tree.growth = a.animatedValue as Float
                }
                start()
            }

            // 2) animate narrative (fade in, no cheesy pop)
            tv?.animate()?.cancel()
            tv?.alpha = 0f
            tv?.text = line
            tv?.animate()?.alpha(0.86f)?.setDuration(380L)?.start()
        }
    }
    private fun maybeShowProgressTreeIntro() {
        // Feature disabled for homescreen2 migration
        /*
        // Only once
        if (prefs.getBoolean(KEY_PROGRESS_TREE_INTRO_SHOWN, false)) return

        // If something else is overlaying (like TreeRing), don't stack chaos
        if (::treeRingOverlayHost.isInitialized && treeRingOverlayHost.visibility == View.VISIBLE) return

        // Wait until treeHolderCard is laid out so we can compute its screen rect
        treeHolder.post {
            // Still only once (double-check after post)
            if (prefs.getBoolean(KEY_PROGRESS_TREE_INTRO_SHOWN, false)) return@post

            val r = Rect()
            val ok = treeHolder.getGlobalVisibleRect(r)
            if (!ok) return@post

            // Convert global rect to overlay-local coordinates
            val overlayLoc = IntArray(2)
            progressTreeIntroHost.getLocationOnScreen(overlayLoc)

            val hole = RectF(
                (r.left - overlayLoc[0]).toFloat(),
                (r.top - overlayLoc[1]).toFloat(),
                (r.right - overlayLoc[0]).toFloat(),
                (r.bottom - overlayLoc[1]).toFloat()
            )

            // Set spotlight hole around the tree
            val padding = dp(16f)        // soft breathing room
            val radius = dp(26f)         // matches your rounded aesthetic
            progressTreeSpotlight.setHole(hole, paddingPx = padding, radiusPx = radius)

            progressTreeIntroCard.post {
                val margin = dp(12f)

                // Center horizontally relative to hole
                val desiredX = hole.centerX() - progressTreeIntroCard.width / 2f

                progressTreeIntroCard.x = desiredX.coerceIn(
                    margin,
                    progressTreeIntroHost.width - progressTreeIntroCard.width - margin
                )

                // Prefer positioning ABOVE the tree spotlight
                val aboveY = hole.top - progressTreeIntroCard.height - margin

                progressTreeIntroCard.y =
                    if (aboveY > margin) {
                        aboveY
                    } else {
                        hole.bottom + margin
                    }
            }

            findViewById<View?>(R.id.tvTreeNarrative)
                ?.animate()
                ?.alpha(0f)
                ?.setDuration(180L)
                ?.start()

            // Show overlay
            progressTreeIntroHost.visibility = View.VISIBLE
            progressTreeIntroHost.alpha = 0f
            progressTreeIntroHost.animate()
                .alpha(1f)
                .setDuration(260L)
                .setInterpolator(SoftMotion.INTERP)
                .start()

            // Subtle emphasis on the tree (optional but nice)
            treeHolder.animate().cancel()
            treeHolder.animate()
                .scaleX(1.03f)
                .scaleY(1.03f)
                .setDuration(260L)
                .setInterpolator(SoftMotion.INTERP)
                .start()

            findViewById<View?>(R.id.tvTreeNarrative)
                ?.animate()
                ?.alpha(0.86f)
                ?.setDuration(220L)
                ?.start()

            btnProgressTreeIntroBegin.setOnClickListener {
                prefs.edit().putBoolean(KEY_PROGRESS_TREE_INTRO_SHOWN, true).apply()

                // Reset tree emphasis
                treeHolder.animate().cancel()
                treeHolder.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220L)
                    .setInterpolator(SoftMotion.INTERP)
                    .start()

                // Fade overlay out
                progressTreeIntroHost.animate()
                    .alpha(0f)
                    .setDuration(220L)
                    .setInterpolator(SoftMotion.INTERP)
                    .withEndAction { progressTreeIntroHost.visibility = View.GONE }
                    .start()
            }
        }
        */
    }
}
