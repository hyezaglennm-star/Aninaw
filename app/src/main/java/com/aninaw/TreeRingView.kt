// TreeRingView.kt
package com.aninaw

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.animation.doOnEnd
import kotlinx.parcelize.Parcelize
import kotlin.math.*
import android.graphics.DashPathEffect
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode


@Parcelize
data class DailyMemory(
    val dateIso: String,
    val hasCheckIn: Boolean,
    val emotion: String?,
    val intensity: Float?,
    val note: String?,
    val isQuick: Boolean,

    // ✅ add these so the bottom sheet can display full inputs
    val payloadJson: String? = null,
    val capacity: String? = null,
    val type: String? = null,
    val timestamp: Long? = null
) : Parcelable

class TreeRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // -----------------------------
    // Public API
    // -----------------------------
    var onRingTapped: ((memory: DailyMemory) -> Unit)? = null
    var onBackgroundTapped: (() -> Unit)? = null

    fun bind(stageName: String, daysElapsed: Int, affirmations: List<String>) {
        stage = runCatching { Stage.valueOf(stageName) }.getOrDefault(Stage.SAPLING)
        dayCount = daysElapsed.coerceAtLeast(0)
        ringTexts = affirmations
        seed = stableSeed(stageName, dayCount)
        buildRingsAndAngles()
        markerIndexF = (rings.size - 1).toFloat().coerceAtLeast(0f)
        ensureNoise(width, height)
        rebuildCachesIfPossible()
        postInvalidateOnAnimation()
    }

    fun bind(stageName: String, daysElapsed: Int) = bind(stageName, daysElapsed, emptyList())

    fun setDailyMemory(memory: List<DailyMemory>) {
        dailyMemory = memory
        buildRingsAndAngles()
        rebuildCachesIfPossible()
        postInvalidateOnAnimation()
    }

    fun showDailyAffirmationPulse(text: String, ringIndex: Int) {
        pulseText = text
        pulseRingIndex = ringIndex.coerceAtLeast(0)
        startPulseAnimator()
    }

    fun softResetView() {
        cancelZoomAnim()
        scale = 1f
        panX = 0f
        panY = 0f
        rotationDeg = 0f
        hoveredRingIndex = -1
        selectedRingIndex = -1
        invalidate()
    }

    // Small helpers for “Jump to Day 1 / Today”
    fun focusDay1() = focusRing(0, targetScale = 3.6f)
    fun focusToday() {
        if (rings.isEmpty()) return
        focusRing(rings.lastIndex, targetScale = 2.8f)
    }
    fun focusDay(index: Int) = focusRing(index, targetScale = 3.2f)

    // -----------------------------
// Default zoom = Today (session baseline) + scrub zoom rides smoothly
// -----------------------------
    private val defaultTodayScale = 2.8f
    private val scrubMaxScale = 3.6f

    /** Call once after layout. Makes the initial view match "Today". */
    fun setDefaultZoomToToday() {
        if (rings.isEmpty()) return
        cancelZoomAnim()

        selectedRingIndex = rings.lastIndex
        hoveredRingIndex = rings.lastIndex
        markerIndexF = rings.lastIndex.toFloat()

        scale = defaultTodayScale
        panX = 0f
        panY = 0f
        rotationDeg = 0f

        constrainPan()
        postInvalidateOnAnimation()
    }

    /**
     * While dragging: zoom increases as you scrub away from Today.
     * - progress01: 0..1 (0 = oldest/inner, 1 = today/outer)
     * - does nothing when not dragging so it NEVER snaps back
     */
    fun applyScrubZoom(progress01: Float, isDragging: Boolean) {
        if (!isDragging || rings.isEmpty()) return

        val p = progress01.coerceIn(0f, 1f)
        val away = (1f - p).coerceIn(0f, 1f)          // farther from Today = stronger zoom
        val eased = away.pow(0.75f)                   // gentle curve

        val target = defaultTodayScale + (scrubMaxScale - defaultTodayScale) * eased

        cancelZoomAnim()
        scale = target.coerceIn(minScale, maxScale)
        panX = 0f
        panY = 0f

        constrainPan()
        postInvalidateOnAnimation()
    }

    // Called by Activity while scrubber is being dragged/released
    fun setScrubbingZoom(enabled: Boolean) {
        if (scrubZoomEnabled == enabled) return
        scrubZoomEnabled = enabled

        // Stop any ongoing zoom animation (pinch/double tap/long press etc.)
        cancelZoomAnim()

        val target = if (enabled) scrubZoomScale else scrubNormalScale

        // Zoom toward center (rings are concentric so center focus is correct)
        animateZoomTowardPoint(
            targetScale = target,
            focusX = width / 2f,
            focusY = height / 2f,
            durationMs = if (enabled) 160L else 220L
        )
    }

    // Zoom level follows scrub progress smoothly
    fun setZoomFromScrubProgress(progress01: Float) {
        if (rings.isEmpty()) return

        val p = progress01.coerceIn(0f, 1f)

        // Define how dramatic the zoom range should be
        val minZoom = 1.0f
        val maxZoom = 3.4f

        // Interpolate scale
        val targetScale = minZoom + (maxZoom - minZoom) * p

        cancelZoomAnim()

        scale = targetScale.coerceIn(minScale, maxScale)

        // Keep centered
        panX = 0f
        panY = 0f

        constrainPan()
        postInvalidateOnAnimation()
    }

    // Scrubber integration (preview + commit)
    fun previewRingFromProgress(progress01: Float, fromScrubberDragging: Boolean) {

        if (rings.isEmpty()) return

       

        val idx = (progress01.coerceIn(0f, 1f) * (rings.size - 1))
            .roundToInt()
            .coerceIn(0, rings.size - 1)

        if (fromScrubberDragging) setScrubbingZoom(true)

        // Highlight only (no zoom) while dragging scrubber
        setHoveredIndex(idx, haptic = false)

        // Smoothly move marker with scrub
        animateMarkerTo(idx, durationMs = 90L)

        // Tiny tick every N rings
        if (fromScrubberDragging) {
            val tickIdx = idx / scrubTickEvery
            if (tickIdx != lastScrubTickIdx) {
                lastScrubTickIdx = tickIdx
                performHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
    }

    fun commitRingFromProgress(progress01: Float) {
        if (rings.isEmpty()) return
        lastScrubTickIdx = -1
        val idx = (progress01.coerceIn(0f, 1f) * (rings.size - 1)).roundToInt().coerceIn(0, rings.size - 1)
        focusRing(idx, targetScale = 3.2f)
        hoveredRingIndex = idx
        selectedRingIndex = idx
        postInvalidateOnAnimation()
    }

    // Commit selection WITHOUT changing zoom/pan.
// Use this when releasing the scrubber so it doesn't "snap".
    fun commitRingFromProgressNoSnap(progress01: Float) {
        if (rings.isEmpty()) return
        lastScrubTickIdx = -1

        val idx = (progress01.coerceIn(0f, 1f) * (rings.size - 1))
            .roundToInt()
            .coerceIn(0, rings.size - 1)

        hoveredRingIndex = idx
        selectedRingIndex = idx
        animateMarkerTo(idx, durationMs = 140L)
        performHaptic(HapticFeedbackConstants.VIRTUAL_KEY)
        postInvalidateOnAnimation()
    }

    // -----------------------------
    // Core
    // -----------------------------
    private enum class Stage { SEED, SPROUT, SAPLING, SMALL, MATURE, OLD }
    private var stage: Stage = Stage.SAPLING
    private var dayCount: Int = 0
    private var seed: Long = 1337L

    private data class Ring(
        val t: Float,
        val thickness: Float,
        val wobble: Float,
        val grain: Float,
        val alpha: Int
    )

    private var dailyMemory: List<DailyMemory> = emptyList()
    private val rings = mutableListOf<Ring>()

    // -----------------------------
    // Cached geometry
    // -----------------------------
    private var cachedCx = 0f
    private var cachedCy = 0f
    private var cachedRadius = 0f
    private var cachedDiscR = 0f
    private var cachedInner = 0f
    private var cachedOuter = 0f
    private var cachedMaxOuter = 0f
    private var cachedPithR = 0f

    private val MAX_VISIBLE_RINGS = 180
    private val cachedRingPaths = ArrayList<Path>(200)
    private val cachedRingR = ArrayList<Float>(200)
    private var cachedClipBuilt = false
    private var cachedOuterBarkPath: Path? = null

    private var woodShader: Shader? = null
    private var washShader: Shader? = null
    private var vignetteShader: Shader? = null

    // Background
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E6F2E6") }

    // -----------------------------
    // Zoom + pan + rotation
    // -----------------------------
    private var scale = 1.0f
    private var panX = 0f
    private var panY = 0f
    private var rotationDeg = 0f

    private val minScale = 0.85f
    private val maxScale = 4.25f

    // Label behavior
    private val labelLowZoomMax = 1.60f
    private val labelMidZoomMax = 2.50f

    private val textFadeStart = 1.15f
    private val textFadeEnd = 1.65f

    // -----------------------------
    // Affirmations
    // -----------------------------
    private var ringTexts: List<String> = emptyList()
    private val ringTextAnglesDeg = mutableListOf<Float>()

    // Pulse
    private var pulseText: String? = null
    private var pulseRingIndex: Int = 0
    private var pulseAlpha: Float = 0f
    private var pulseAnimator: ValueAnimator? = null

    // -----------------------------
    // Paints
    // -----------------------------

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val texturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val centerWash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val backingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(235, 248, 242, 234)
    }

    private val centerPithPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val woodBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E8D7C7")
    }

    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    }

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#3A312B") // same family as textInk
    }

    private val noisePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    private val outerBarkSoftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val outerBarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val clipPath = Path()
    private val pithPath = Path()

    private val textArcPath = Path()
    private val textArcRect = RectF()

    // -----------------------------
    // Palette
    // -----------------------------
    private val ringBaseWarm = Color.parseColor("#B79B86")
    private val ringBaseCool = Color.parseColor("#A8A29A")
    private val bark = Color.parseColor("#6A5648")
    private val washWarm = Color.parseColor("#EFE4D6")
    private val textInk = Color.parseColor("#3A312B")
    private val barkOuterSoft = Color.parseColor("#5A463C")
    private val barkOuterDark = Color.parseColor("#4A3A31")

    // -----------------------------
    // Noise cache
    // -----------------------------
    private var noiseBmp: Bitmap? = null
    private var noiseW = 0
    private var noiseH = 0

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ensureNoise(w, h)
        rebuildCachesIfPossible()
    }

    private fun currentInner(baseRadius: Float): Float = cachedInner.takeIf { it > 0f } ?: (baseRadius * 0.15f)
    private fun currentOuter(baseRadius: Float): Float = cachedOuter.takeIf { it > 0f } ?: (baseRadius * 0.92f)

    // -----------------------------
    // Gestures state
    // -----------------------------
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var movedTooMuch = false

    private val tapSlopPx = 10f * resources.displayMetrics.density
    private val tapMaxMs = 220L

    // Double-tap
    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private val doubleTapWindowMs = 260L
    private val doubleTapSlopPx = 18f * resources.displayMetrics.density

    // Snap highlight
    private var hoveredRingIndex: Int = -1
    private var selectedRingIndex: Int = -1
    private val snapZoomThreshold = 2.20f

    // Scrubber haptic tick
    private var lastScrubTickIdx: Int = -1
    private val scrubTickEvery = 4 // 3–5 feels good; 4 is a nice middle

    // Long-press magnify
    private val longPressMs = 340L
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressArmed = false
    private var longPressTriggered = false
    private var longPressX = 0f
    private var longPressY = 0f
    private var scaleBeforeLongPress = 1f
    private val longPressRunnable = Runnable {
        if (!longPressArmed) return@Runnable
        longPressTriggered = true
        scaleBeforeLongPress = scale
        // Smooth magnify (hold)
        animateZoomTowardPoint(targetScale = 3.2f, focusX = longPressX, focusY = longPressY, durationMs = 220L)
        performHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    // Zoom animation
    private var zoomAnimator: ValueAnimator? = null
    private fun cancelZoomAnim() { zoomAnimator?.cancel(); zoomAnimator = null }

    // -----------------------------
// Marker dot smooth animation (scrub-follow)
// -----------------------------
    private var markerIndexF: Float = -1f
    private var markerAnim: ValueAnimator? = null

    private fun cancelMarkerAnim() {
        markerAnim?.cancel()
        markerAnim = null
    }

    private fun animateMarkerTo(targetIdx: Int, durationMs: Long = 120L) {
        if (rings.isEmpty()) return
        val t = targetIdx.coerceIn(0, rings.lastIndex).toFloat()

        if (markerIndexF < 0f) {
            markerIndexF = t
            postInvalidateOnAnimation()
            return
        }

        val start = markerIndexF
        if (abs(start - t) < 0.001f) return

        cancelMarkerAnim()
        markerAnim = ValueAnimator.ofFloat(start, t).apply {
            duration = durationMs
            addUpdateListener { anim ->
                markerIndexF = anim.animatedValue as Float
                postInvalidateOnAnimation()
            }
            doOnEnd { markerAnim = null }
            start()
        }
    }

    // -----------------------------
// Scrubber zoom mode
// -----------------------------
    private var scrubZoomEnabled = false
    private val scrubZoomScale = 3.2f     // zoom level while scrubbing (adjust if needed)
    private val scrubNormalScale = 1.0f   // zoom level when not scrubbing

    // Scale detector
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                cancelZoomAnim()

                val prev = scale
                scale = (scale * detector.scaleFactor).coerceIn(minScale, maxScale)

                val focusX = detector.focusX
                val focusY = detector.focusY
                val dx = focusX - width / 2f
                val dy = focusY - height / 2f
                val scaleChange = scale / prev

                panX = (panX - dx) * scaleChange + dx
                panY = (panY - dy) * scaleChange + dy

                constrainPan()
                postInvalidateOnAnimation()
                return true
            }
        }
    )

    // Rotation detector (kept, but never required)
    private val rotationDetector = RotationGestureDetector { deltaDeg ->
        cancelZoomAnim()
        rotationDeg = (rotationDeg + deltaDeg) % 360f
        constrainPan()
        postInvalidateOnAnimation()
    }

    private var lastPanX = 0f
    private var lastPanY = 0f
    private var isPanning = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }

        rotationDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)
        val isScaling = scaleDetector.isInProgress

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = System.currentTimeMillis()
                movedTooMuch = false

                lastPanX = event.x
                lastPanY = event.y
                isPanning = false

                // arm long-press
                longPressArmed = true
                longPressTriggered = false
                longPressX = event.x
                longPressY = event.y
                longPressHandler.removeCallbacks(longPressRunnable)
                longPressHandler.postDelayed(longPressRunnable, longPressMs)

                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx0 = event.x - downX
                val dy0 = event.y - downY
                if (!movedTooMuch && (abs(dx0) + abs(dy0)) > tapSlopPx) movedTooMuch = true

                if (movedTooMuch && longPressArmed) {
                    longPressArmed = false
                    longPressHandler.removeCallbacks(longPressRunnable)
                }

                // magnetic hover
                if (scale >= snapZoomThreshold && !isScaling && !rotationDetector.isRotating && event.pointerCount == 1) {
                    val (lx, ly) = screenToLocal(event.x, event.y)
                    val idx = ringIndexFromLocalPoint(lx, ly)
                    setHoveredIndex(idx, haptic = true)
                } else if (!isScaling && !rotationDetector.isRotating && scale < snapZoomThreshold) {
                    if (hoveredRingIndex != -1) {
                        hoveredRingIndex = -1
                        postInvalidateOnAnimation()
                    }
                }

                // pan (only when zoomed)
                if (event.pointerCount == 1 && scale > 1.02f && !isScaling && !rotationDetector.isRotating) {
                    val dx = event.x - lastPanX
                    val dy = event.y - lastPanY
                    if (!isPanning && (abs(dx) + abs(dy)) > 4f) isPanning = true

                    if (isPanning) {
                        cancelZoomAnim()
                        panX += dx
                        panY += dy
                        constrainPan()
                        postInvalidateOnAnimation()
                    }
                    lastPanX = event.x
                    lastPanY = event.y
                }
            }

            MotionEvent.ACTION_UP -> {
                val elapsed = System.currentTimeMillis() - downTime

                longPressArmed = false
                longPressHandler.removeCallbacks(longPressRunnable)

                val isTap =
                    !movedTooMuch &&
                            elapsed <= tapMaxMs &&
                            !rotationDetector.isRotating &&
                            !isScaling &&
                            !isPanning

                // long-press restore
                if (longPressTriggered) {
                    longPressTriggered = false
                    animateZoomTowardPoint(targetScale = scaleBeforeLongPress, focusX = event.x, focusY = event.y, durationMs = 220L)
                    isPanning = false
                    return true
                }

                if (isTap) {
                    val now = System.currentTimeMillis()
                    val dt = now - lastTapTime
                    val dx = event.x - lastTapX
                    val dy = event.y - lastTapY
                    val isDoubleTap = dt in 1..doubleTapWindowMs && (abs(dx) + abs(dy)) < doubleTapSlopPx

                    if (isDoubleTap) {
                        // 3-step zoom cycle
                        val target = when {
                            scale < 1.6f -> 2.6f
                            scale < 3.0f -> 4.1f
                            else -> 1.0f
                        }
                        animateZoomTowardPoint(targetScale = target, focusX = event.x, focusY = event.y, durationMs = 240L)
                        lastTapTime = 0L
                        isPanning = false
                        return true
                    } else {
                        lastTapTime = now
                        lastTapX = event.x
                        lastTapY = event.y

                        val hitRing = handleTapPreferHovered(event.x, event.y)
                        if (hitRing) performClick() else onBackgroundTapped?.invoke()
                    }
                }

                isPanning = false
            }

            MotionEvent.ACTION_CANCEL -> {
                isPanning = false
                longPressArmed = false
                longPressHandler.removeCallbacks(longPressRunnable)
            }
        }

        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun performHaptic(type: Int) {
        try { performHapticFeedback(type) } catch (_: Throwable) {}
    }

    // -----------------------------
    // Tap logic (prefer hovered when zoomed)
    // -----------------------------
    private fun handleTapPreferHovered(screenX: Float, screenY: Float): Boolean {
        if (rings.isEmpty()) return false

        // Commit rule:
        // When zoomed in, we ONLY open if we have a hovered ring.
        // If not hovered, treat as background tap (return false).
        if (scale >= snapZoomThreshold) {
            return if (hoveredRingIndex in rings.indices) {
                selectRingIndex(hoveredRingIndex)
            } else {
                false
            }
        }

        // At low zoom, use geometric tap (normal behavior).
        return handleRingTap(screenX, screenY)
    }

    private fun selectRingIndex(idx: Int): Boolean {
        val i = idx.coerceIn(0, rings.size - 1)
        selectedRingIndex = i
        hoveredRingIndex = i
        animateMarkerTo(i, durationMs = 160L)

        val mem = dailyMemory.getOrNull(i) ?: DailyMemory(
            dateIso = "Unknown",
            hasCheckIn = false,
            emotion = null,
            intensity = null,
            note = null,
            isQuick = false
        )

        // stronger selection haptic
        performHaptic(HapticFeedbackConstants.VIRTUAL_KEY)

        onRingTapped?.invoke(mem)
        postInvalidateOnAnimation()
        return true
    }

    private fun handleRingTap(x: Float, y: Float): Boolean {
        if (rings.isEmpty()) return false

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 1f || h <= 1f) return false

        val cx = w / 2f
        val cy = h / 2f

        // Undo transforms
        var px = x - panX
        var py = y - panY

        val rad = (-rotationDeg) * (Math.PI.toFloat() / 180f)
        val cosA = cos(rad)
        val sinA = sin(rad)

        val dx = px - cx
        val dy = py - cy
        val rx = dx * cosA - dy * sinA
        val ry = dx * sinA + dy * cosA
        px = cx + rx
        py = cy + ry

        val s = scale.coerceAtLeast(0.0001f)
        px = cx + (px - cx) / s
        py = cy + (py - cy) / s

        val dist = sqrt((px - cx).pow(2) + (py - cy).pow(2))

        if (!cachedClipBuilt || cachedOuter <= 0f || cachedInner <= 0f) {
            rebuildCachesIfPossible()
        }

        val inner = cachedInner.takeIf { it > 0f } ?: return false
        val outer = cachedOuter.takeIf { it > 0f } ?: return false
        val woodR = cachedDiscR.takeIf { it > 0f } ?: (outer * 1.1f)

        // If tap is outside the visual wood disc, ignore it (background)
        if (dist > woodR) return false

        // If tap is inside pith (center), treat as first ring
        if (dist < inner) {
            return selectRingIndex(0)
        }

        // If tap is inside wood but outside rings (bark), treat as last ring
        if (dist > outer) {
            return selectRingIndex(rings.size - 1)
        }

        val t = ((dist - inner) / (outer - inner)).coerceIn(0f, 1f)
        val idx = (t * (rings.size - 1)).roundToInt().coerceIn(0, rings.size - 1)

        return selectRingIndex(idx)
    }

    // -----------------------------
    // Smooth zoom animation (instead of instant jumps)
    // -----------------------------
    private fun animateZoomTowardPoint(targetScale: Float, focusX: Float, focusY: Float, durationMs: Long) {
        val startScale = scale
        val endScale = targetScale.coerceIn(minScale, maxScale)
        if (abs(endScale - startScale) < 0.001f) return

        cancelZoomAnim()

        val startPanX = panX
        val startPanY = panY

        val cx = width / 2f
        val cy = height / 2f
        val dx = focusX - cx
        val dy = focusY - cy

        zoomAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            addUpdateListener { anim ->
                // gentle ease-in-out
                val t = anim.animatedFraction.coerceIn(0f, 1f)
                val eased = (0.5f - 0.5f * cos(Math.PI.toFloat() * t))

                val sNow = startScale + (endScale - startScale) * eased
                val scaleChange = sNow / startScale

                scale = sNow
                panX = (startPanX - dx) * scaleChange + dx
                panY = (startPanY - dy) * scaleChange + dy

                constrainPan()
                postInvalidateOnAnimation()
            }
            doOnEnd { zoomAnimator = null }
            start()
        }
    }

    // -----------------------------
    // Coordinate helpers
    // -----------------------------
    private fun screenToLocal(x: Float, y: Float): Pair<Float, Float> {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f

        var px = x - panX
        var py = y - panY

        val rad = (-rotationDeg) * (Math.PI.toFloat() / 180f)
        val cosA = cos(rad)
        val sinA = sin(rad)

        val dx = px - cx
        val dy = py - cy
        val rx = dx * cosA - dy * sinA
        val ry = dx * sinA + dy * cosA
        px = cx + rx
        py = cy + ry

        val s = scale.coerceAtLeast(0.0001f)
        px = cx + (px - cx) / s
        py = cy + (py - cy) / s

        return px to py
    }

    private fun ringIndexFromLocalPoint(localX: Float, localY: Float): Int {
        if (rings.isEmpty()) return -1
        val cx = width / 2f
        val cy = height / 2f

        if (!cachedClipBuilt || cachedOuter <= 0f || cachedInner <= 0f) {
            rebuildCachesIfPossible()
        }

        val inner = cachedInner.takeIf { it > 0f } ?: return -1
        val outer = cachedOuter.takeIf { it > 0f } ?: return -1

        val dist = sqrt((localX - cx).pow(2) + (localY - cy).pow(2))

        val tapPad = (7f * resources.displayMetrics.density) / scale
        if (dist < inner - tapPad || dist > outer + tapPad) return -1

        val t = ((dist - inner) / (outer - inner)).coerceIn(0f, 1f)
        return (t * (rings.size - 1)).roundToInt().coerceIn(0, rings.size - 1)
    }

    private fun setHoveredIndex(idx: Int, haptic: Boolean) {
        if (idx == hoveredRingIndex) return
        hoveredRingIndex = idx
        if (haptic && idx != -1) performHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
        postInvalidateOnAnimation()
    }

    private fun focusRing(index: Int, targetScale: Float) {
        if (rings.isEmpty()) return
        val i = index.coerceIn(0, rings.lastIndex)
        selectedRingIndex = i
        hoveredRingIndex = i
        // Center is fine for concentric rings; just zoom smoothly.
        animateZoomTowardPoint(targetScale = targetScale, focusX = width / 2f, focusY = height / 2f, durationMs = 260L)
        performHaptic(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    // -----------------------------
    // Pan clamp
    // -----------------------------
    private fun constrainPan() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 1f || h <= 1f) return

        if (!cachedClipBuilt || cachedOuter <= 0f) {
            rebuildCachesIfPossible()
        }

        val baseRadius = min(w, h) * 0.49f
        val outerRingR = cachedOuter.takeIf { it > 0f } ?: (baseRadius * 0.92f)

        val approxTextSize = baseRadius * 0.050f
        val approxMaxStroke = baseRadius * 0.022f
        val textAboveMargin =
            (approxMaxStroke * 0.60f) + (approxTextSize * 0.45f) + (approxTextSize * 0.20f)

        val slack = baseRadius * 0.06f
        val maxExtent = (outerRingR + textAboveMargin + slack) * scale

        val halfW = w / 2f
        val halfH = h / 2f

        val maxPanX = max(0f, maxExtent - halfW)
        val maxPanY = max(0f, maxExtent - halfH)

        panX = panX.coerceIn(-maxPanX, maxPanX)
        panY = panY.coerceIn(-maxPanY, maxPanY)
    }

    // -----------------------------
    // Rings + angles
    // -----------------------------
    private fun buildRingsAndAngles() {
        rings.clear()
        ringTextAnglesDeg.clear()

        val ringCount = (dayCount + 1).coerceAtLeast(1)
        val total = ringCount.coerceAtMost(MAX_VISIBLE_RINGS)

        val rng = java.util.Random(seed)
        val golden = 137.507764f
        val baseAngle = rng.nextFloat() * 360f

        for (i in 0 until total) {
            val t = if (total == 1) 1f else i / (total - 1f)

            val mem = dailyMemory.getOrNull(i)
            val has = mem?.hasCheckIn == true
            val intensity = (mem?.intensity ?: if (has) 0.55f else 0.0f).coerceIn(0f, 1f)

            val thickness = if (has) {
                (0.010f + 0.010f * intensity) * (if (i % 4 == 0) 1.10f else 1f)
            } else {
                0.0038f
            }

            val wobble = (0.010f + rng.nextFloat() * 0.020f) * (0.85f + 0.30f * t)
            val grain = if (has) (0.28f + 0.50f * intensity) else 0.18f
            val alpha = if (has) (185 + (55 * intensity)).roundToInt().coerceIn(160, 235) else 75

            rings += Ring(t = t, thickness = thickness, wobble = wobble, grain = grain, alpha = alpha)

            val jitter = (rng.nextFloat() - 0.5f) * 18f
            val angle = (baseAngle + i * golden + jitter) % 360f
            ringTextAnglesDeg += angle
        }
    }

    private fun rebuildCachesIfPossible() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 1f || h <= 1f) return
        if (rings.isEmpty()) return

        cachedCx = w / 2f
        cachedCy = h / 2f

        val fullRadius = min(w, h) * 0.49f
        cachedRadius = fullRadius
        if (fullRadius <= 2f) return

        cachedMaxOuter = fullRadius * 0.92f
        cachedPithR = fullRadius * 0.11f

        val innerPad = fullRadius * 0.030f
        cachedInner = cachedPithR + innerPad

        val available = (cachedMaxOuter - cachedInner).coerceAtLeast(1f)

        val ringCount = rings.size.coerceAtLeast(1)

// Give rings more breathing space
        val spacingFactor = 1.35f   // increase this for larger gaps (1.2–1.6 is safe)

        val step = (available / (ringCount - 1f).coerceAtLeast(1f)) * spacingFactor

        cachedOuter = (cachedInner + step * (ringCount - 1))
            .coerceAtMost(cachedMaxOuter)

        val discMargin = fullRadius * 0.08f
        cachedDiscR = (cachedOuter + discMargin).coerceAtMost(fullRadius * 0.98f)

        cachedRingPaths.clear()
        cachedRingR.clear()
        cachedRingPaths.ensureCapacity(rings.size)
        cachedRingR.ensureCapacity(rings.size)

        for ((idx, ring) in rings.withIndex()) {
            val rr = lerp(cachedInner, cachedOuter, ring.t)

            val p = Path()
            buildOrganicCirclePath(
                path = p,
                cx = cachedCx,
                cy = cachedCy,
                r = rr,
                wobble = ring.wobble,
                seed = seed + idx * 9999L
            )
            cachedRingPaths.add(p)
            cachedRingR.add(rr)
        }

        clipPath.reset()
        buildOrganicCirclePath(
            path = clipPath,
            cx = cachedCx,
            cy = cachedCy,
            r = cachedDiscR,
            wobble = 0.014f,
            seed = seed xor 0xA11CEL
        )

        pithPath.reset()
        buildOrganicCirclePath(
            path = pithPath,
            cx = cachedCx,
            cy = cachedCy,
            r = cachedPithR,
            wobble = 0.20f,
            seed = seed xor 0xC0FFEEL
        )

        cachedOuterBarkPath = Path().apply {
            val barkR = cachedOuter + fullRadius * 0.018f
            buildOrganicCirclePath(
                path = this,
                cx = cachedCx,
                cy = cachedCy,
                r = barkR,
                wobble = 0.030f,
                seed = seed xor 0xBAADF00DL
            )
        }

        woodShader = RadialGradient(
            cachedCx, cachedCy,
            cachedDiscR,
            intArrayOf(
                adjustAlpha(Color.parseColor("#F3E9DC"), 0.90f),
                adjustAlpha(Color.parseColor("#E8D7C7"), 0.60f),
                adjustAlpha(Color.parseColor("#DCC6B3"), 0.28f)
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )

        washShader = RadialGradient(
            cachedCx, cachedCy,
            cachedDiscR * 0.78f,
            intArrayOf(adjustAlpha(washWarm, 0.42f), adjustAlpha(washWarm, 0.0f)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )

        vignetteShader = RadialGradient(
            cachedCx, cachedCy,
            cachedDiscR * 1.08f,
            intArrayOf(adjustAlpha(Color.BLACK, 0.00f), adjustAlpha(Color.BLACK, 0.06f)),
            floatArrayOf(0.72f, 1f),
            Shader.TileMode.CLAMP
        )

        cachedClipBuilt = true
    }

    private fun emotionBaseColor(emotion: String?, idx: Int): Int {
        val e = (emotion ?: "").trim().lowercase()
        val warm = ringBaseWarm
        val cool = ringBaseCool

        return when {
            e.contains("grateful") || e.contains("joy") || e.contains("happy") || e.contains("shy") -> warm
            e.contains("calm") || e.contains("okay") || e.contains("steady") || e.contains("neutral") -> if (idx % 2 == 0) warm else cool
            e.contains("tired") || e.contains("numb") || e.contains("foggy") -> cool
            e.contains("heavy") || e.contains("sad") || e.contains("anxious") -> cool
            e.contains("angry") || e.contains("tense") -> warm
            else -> if (idx % 2 == 0) warm else cool
        }
    }

    // -----------------------------
    // Drawing
    // -----------------------------
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 1f || h <= 1f) return

        val corner = min(w, h) * 0.08f
        canvas.drawRoundRect(0f, 0f, w, h, corner, corner, backingPaint)

        val cx = w / 2f
        val cy = h / 2f

        val baseRadius = min(w, h) * 0.49f
        if (baseRadius <= 2f) return

        if (!cachedClipBuilt || woodShader == null || washShader == null || vignetteShader == null || cachedRingPaths.size != rings.size) {
            rebuildCachesIfPossible()
        }

        canvas.save()
        canvas.translate(panX, panY)
        canvas.rotate(rotationDeg, cx, cy)
        canvas.scale(scale, scale, cx, cy)

        val radius = cachedDiscR.takeIf { it > 2f } ?: run {
            canvas.restore()
            return
        }

        // Wood body
        woodBodyPaint.shader = woodShader
        canvas.drawCircle(cx, cy, radius * 0.98f, woodBodyPaint)
        woodBodyPaint.shader = null

        // Wash
        centerWash.shader = washShader
        canvas.drawCircle(cx, cy, radius * 0.74f, centerWash)
        centerWash.shader = null

        // Vignette
        vignettePaint.shader = vignetteShader
        canvas.drawCircle(cx, cy, radius * 0.99f, vignettePaint)
        vignettePaint.shader = null

        // Clip
        canvas.save()
        canvas.clipPath(clipPath)

        // Pith
        centerPithPaint.color = adjustAlpha(Color.parseColor("#BCA897"), 0.62f)
        canvas.drawPath(pithPath, centerPithPaint)

        val inner = currentInner(radius)
        val outer = currentOuter(radius)

        val zoomLabelFade = ((scale - textFadeStart) / (textFadeEnd - textFadeStart)).coerceIn(0f, 1f)

        textPaint.textSize = radius * 0.050f
        textPaint.color = adjustAlpha(textInk, 0.88f)

        val hasSelection = selectedRingIndex in rings.indices
        val dimOthersFactor = if (hasSelection) 0.92f else 1.0f

        for (idx in rings.indices) {
            val ring = rings[idx]
            val path = cachedRingPaths[idx]

            val mem = dailyMemory.getOrNull(idx)
            val base = emotionBaseColor(mem?.emotion, idx)
            val denom = (rings.size - 1f).coerceAtLeast(1f)
            val darken = 1f - (0.10f * (idx / denom))
            var ringColor = darkenColor(base, darken)

            val isFocus = (idx == hoveredRingIndex) || (idx == selectedRingIndex)
            if (hasSelection && !isFocus) {
                ringColor = darkenColor(ringColor, dimOthersFactor)
            }

            if (idx == selectedRingIndex) {
                val rr = cachedRingR.getOrNull(idx) ?: lerp(inner, outer, ring.t)
                val angleDeg = ringTextAnglesDeg.getOrNull(idx) ?: 270f
                val theta = Math.toRadians(angleDeg.toDouble())
                val rDot = rr + ringPaint.strokeWidth * 0.65f

                val dx = (cos(theta) * rDot).toFloat()
                val dy = (sin(theta) * rDot).toFloat()

                dotPaint.color = adjustAlpha(ringColor, 0.55f)
                canvas.drawCircle(cx + dx, cy + dy, max(1.5f * resources.displayMetrics.density, ringPaint.strokeWidth * 0.35f), dotPaint)
            }

            ringPaint.color = adjustAlpha(ringColor, ring.alpha / 255f)
            ringPaint.strokeWidth = radius * ring.thickness

            // Minimum stroke at deep zoom so inner rings stay readable
            if (scale > 2.0f) {
                ringPaint.strokeWidth = max(ringPaint.strokeWidth, 1.2f * resources.displayMetrics.density)
            }

            canvas.drawPath(path, ringPaint)

            // Selected/hovered highlight (subtle)
            if (isFocus) {
                highlightPaint.color = adjustAlpha(ringColor, 0.40f)
                highlightPaint.strokeWidth = ringPaint.strokeWidth * 1.25f
                canvas.drawPath(path, highlightPaint)
            }

            // Grain texture
            val grainAlpha = (0.10f + 0.08f * ring.grain).coerceIn(0.08f, 0.18f)
            texturePaint.color = adjustAlpha(bark, grainAlpha)
            texturePaint.strokeWidth = max(1.0f, ringPaint.strokeWidth * 0.55f)

            val dashOn = max(6f, radius * 0.045f)
            val dashOff = max(6f, radius * 0.030f)
            texturePaint.pathEffect = DashPathEffect(floatArrayOf(dashOn, dashOff), (idx * 9f) % 40f)

            canvas.drawPath(path, texturePaint)
            texturePaint.pathEffect = null
        }

        // Tiny marker dot for the selected ring (confirmation cue)
        // Tiny marker dot follows scrub smoothly (uses animated markerIndexF)
        if (rings.isNotEmpty() && markerIndexF >= 0f) {
            val f = markerIndexF.coerceIn(0f, (rings.size - 1).toFloat())
            val i0 = floor(f).toInt().coerceIn(0, rings.lastIndex)
            val i1 = ceil(f).toInt().coerceIn(0, rings.lastIndex)
            val t = (f - i0.toFloat()).coerceIn(0f, 1f)

            val rr0 = cachedRingR.getOrNull(i0) ?: lerp(inner, outer, rings[i0].t)
            val rr1 = cachedRingR.getOrNull(i1) ?: lerp(inner, outer, rings[i1].t)
            val rr = lerp(rr0, rr1, t)

            val a0 = ringTextAnglesDeg.getOrNull(i0) ?: 270f
            val a1 = ringTextAnglesDeg.getOrNull(i1) ?: 270f

            // shortest-angle interpolation so it doesn't spin the long way
            var da = (a1 - a0)
            while (da > 180f) da -= 360f
            while (da < -180f) da += 360f
            val angleDeg = a0 + da * t

            val theta = Math.toRadians(angleDeg.toDouble())

            val dotR = max(2.2f * resources.displayMetrics.density, ringPaint.strokeWidth * 0.35f)
            val dotRadiusFromCenter =
                rr + (ringPaint.strokeWidth * 0.55f) + (textPaint.textSize * 0.20f)

            val dx = (cos(theta) * dotRadiusFromCenter).toFloat()
            val dy = (sin(theta) * dotRadiusFromCenter).toFloat()

            markerPaint.color = adjustAlpha(textInk, 0.55f)
            canvas.drawCircle(cx + dx, cy + dy, dotR, markerPaint)
        }

        // Outer bark rim
        cachedOuterBarkPath?.let { barkPath ->
            outerBarkSoftPaint.strokeWidth = radius * 0.050f
            outerBarkSoftPaint.color = adjustAlpha(barkOuterSoft, 0.14f)

            val on = max(8f, radius * 0.060f)
            val off = max(8f, radius * 0.040f)
            outerBarkSoftPaint.pathEffect = DashPathEffect(floatArrayOf(on, off), (seed % 60).toFloat())

            canvas.drawPath(barkPath, outerBarkSoftPaint)
            outerBarkSoftPaint.pathEffect = null

            outerBarkPaint.strokeWidth = radius * 0.026f
            outerBarkPaint.color = adjustAlpha(barkOuterDark, 0.22f)
            canvas.drawPath(barkPath, outerBarkPaint)
        }

        // Noise fades as you zoom in (clarity > texture)
        noiseBmp?.let { bmp ->
            val baseAlpha = 16
            val fade = (1f - ((scale - 1.6f) / 2.4f)).coerceIn(0.35f, 1f)
            noisePaint.alpha = (baseAlpha * fade).roundToInt()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                noisePaint.blendMode = BlendMode.SOFT_LIGHT
                canvas.drawBitmap(bmp, 0f, 0f, noisePaint)
                noisePaint.blendMode = null
            } else {
                noisePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
                canvas.drawBitmap(bmp, 0f, 0f, noisePaint)
                noisePaint.xfermode = null
            }
        }

        canvas.restore() // stop clipping BEFORE text

        // -----------------------------
        // Adaptive labels
        // -----------------------------
        val labelIndices: IntRange? = when {
            scale < labelLowZoomMax -> {
                if (selectedRingIndex in rings.indices) selectedRingIndex..selectedRingIndex else null
            }
            scale < labelMidZoomMax -> {
                if (selectedRingIndex in rings.indices) {
                    val a = (selectedRingIndex - 2).coerceAtLeast(0)
                    val b = (selectedRingIndex + 2).coerceAtMost(rings.lastIndex)
                    a..b
                } else null
            }
            else -> 0..rings.lastIndex
        }

        // Low zoom labels should still be visible for selected ring
        val labelAlpha =
            when {
                scale < labelLowZoomMax -> 0.85f
                scale < labelMidZoomMax -> 0.85f
                else -> 0.85f * zoomLabelFade
            }

        if (labelIndices != null && labelAlpha > 0.01f) {
            for (idx in labelIndices) {
                val ring = rings[idx]
                val text = ringTexts.getOrNull(idx)?.takeIf { it.isNotBlank() } ?: continue
                val rr = cachedRingR.getOrNull(idx) ?: lerp(inner, outer, ring.t)
                val angle = ringTextAnglesDeg.getOrNull(idx) ?: 270f

                drawCurvedTextAboveRingArc(
                    canvas = canvas,
                    cx = cx,
                    cy = cy,
                    ringR = rr,
                    ringStrokeWidth = ringPaint.strokeWidth,
                    centerAngleDeg = angle,
                    sweepDeg = 140f,
                    text = text,
                    alpha = labelAlpha
                )
            }
        }

        // Pulse overlay (kept)
        val pText = pulseText
        if (!pText.isNullOrBlank() && pulseAlpha > 0.01f) {
            val i = pulseRingIndex.coerceIn(0, rings.lastIndex.coerceAtLeast(0))
            val rr = cachedRingR.getOrNull(i) ?: lerp(inner, outer, rings.getOrNull(i)?.t ?: 1f)
            val angle = ringTextAnglesDeg.getOrNull(i) ?: 270f

            drawCurvedTextAboveRingArc(
                canvas = canvas,
                cx = cx,
                cy = cy,
                ringR = rr,
                ringStrokeWidth = ringPaint.strokeWidth,
                centerAngleDeg = angle,
                sweepDeg = 150f,
                text = pText,
                alpha = 0.95f * pulseAlpha
            )
        }

        canvas.restore() // pan/rotate/zoom
    }

    private fun drawCurvedTextAboveRingArc(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        ringR: Float,
        ringStrokeWidth: Float,
        centerAngleDeg: Float,
        sweepDeg: Float,
        text: String,
        alpha: Float
    ) {
        val a = alpha.coerceIn(0f, 1f)
        textPaint.color = adjustAlpha(textInk, a)

        val gap = textPaint.textSize * 0.35f
        val textRadius = ringR + (ringStrokeWidth * 0.55f) + gap + (textPaint.textSize * 0.15f)

        textArcRect.set(cx - textRadius, cy - textRadius, cx + textRadius, cy + textRadius)
        textArcPath.reset()

        val start = (centerAngleDeg - sweepDeg / 2f)
        textArcPath.addArc(textArcRect, start, sweepDeg)

        val pm = PathMeasure(textArcPath, false)
        val arcLen = pm.length
        val textWidth = textPaint.measureText(text)

        val hOffset = ((arcLen - textWidth) / 2f).coerceAtLeast(0f)
        val vOffset = -textPaint.textSize * 0.18f

        canvas.drawTextOnPath(text, textArcPath, hOffset, vOffset, textPaint)
    }

    // -----------------------------
    // Pulse animation
    // -----------------------------
    private fun startPulseAnimator() {
        pulseAnimator?.cancel()

        val total = 6000L
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = total
            addUpdateListener { anim ->
                val p = anim.animatedFraction.coerceIn(0f, 1f)
                pulseAlpha = when {
                    p < 0.15f -> (p / 0.15f)
                    p < 0.85f -> 1f
                    else -> (1f - ((p - 0.85f) / 0.15f)).coerceIn(0f, 1f)
                }
                postInvalidateOnAnimation()
            }
            doOnEnd {
                pulseAlpha = 0f
                pulseText = null
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    // -----------------------------
    // Organic circle
    // -----------------------------
    private fun buildOrganicCirclePath(
        path: Path,
        cx: Float,
        cy: Float,
        r: Float,
        wobble: Float,
        seed: Long
    ) {
        val rng = java.util.Random(seed)
        val points = 110
        val wobblePx = r * wobble

        fun noise(t: Float): Float {
            val a = sin(t * 2f) * 0.55f
            val b = sin(t * 5f + 1.3f) * 0.30f
            val c = sin(t * 9f + 2.1f) * 0.15f
            val rand = (rng.nextFloat() - 0.5f) * 0.12f
            return (a + b + c + rand).coerceIn(-1f, 1f)
        }

        for (i in 0..points) {
            val t = (i / points.toFloat()) * (2f * Math.PI.toFloat())
            val n = noise(t)
            val rr = r + n * wobblePx

            val x = cx + cos(t) * rr
            val y = cy + sin(t) * rr

            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    // -----------------------------
    // Helpers
    // -----------------------------
    private fun stableSeed(stage: String, day: Int): Long {
        var s = 1125899906842597L
        val text = "$stage-$day"
        for (c in text) s = 31L * s + c.code
        return s
    }

    private fun adjustAlpha(color: Int, a: Float): Int {
        val alpha = (Color.alpha(color) * a).roundToInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun darkenColor(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f)
        return Color.HSVToColor(Color.alpha(color), hsv)
    }

    private fun ensureNoise(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        if (noiseBmp != null && noiseW == w && noiseH == h) return

        noiseW = w
        noiseH = h
        noiseBmp?.recycle()

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val rng = java.util.Random(seed xor 0xBEEF1234L)

        val pixels = IntArray(w * h)
        for (i in pixels.indices) {
            val v = (120 + rng.nextInt(16) - 8).coerceIn(0, 255)
            pixels[i] = Color.argb(255, v, v, v)
        }
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        noiseBmp = bmp
    }

    // -----------------------------
    // Rotation detector (2-finger)
    // -----------------------------
    private class RotationGestureDetector(
        private val onRotate: (deltaDegrees: Float) -> Unit
    ) {
        var isRotating: Boolean = false
            private set

        private var prevAngle = 0f
        private var active = false

        private var armed = false
        private var accum = 0f

        fun onTouchEvent(ev: MotionEvent) {
            when (ev.actionMasked) {


                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (ev.pointerCount >= 2) {
                        prevAngle = angle(ev)
                        active = true
                        armed = true
                        accum = 0f
                        isRotating = false // don't rotate immediately
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (active && ev.pointerCount >= 2) {
                        val a = angle(ev)
                        val delta = normalizeDelta(a - prevAngle)
                        prevAngle = a

                        // require a small intentional rotation before "locking in"
                        if (armed) {
                            accum += abs(delta)
                            if (accum >= 2.5f) { // ~2.5 degrees threshold
                                armed = false
                                isRotating = true
                            } else {
                                return
                            }
                        }

                        if (isRotating && abs(delta) > 0.1f) onRotate(delta)
                    }
                }
            }
        }

        private fun angle(ev: MotionEvent): Float {
            val dx = ev.getX(1) - ev.getX(0)
            val dy = ev.getY(1) - ev.getY(0)
            return (atan2(dy, dx) * (180f / Math.PI.toFloat()))
        }

        private fun normalizeDelta(d: Float): Float {
            var x = d
            while (x > 180f) x -= 360f
            while (x < -180f) x += 360f
            return x
        }
    }

}