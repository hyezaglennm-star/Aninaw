//EmotionalWeatherView.kt
package com.aninaw.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.roundToInt
import com.aninaw.checkin.DailyEmotionRecord
import com.aninaw.checkin.Emotion

class EmotionalWeatherView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onDayTapped: ((index: Int) -> Unit)? = null

    private val density = resources.displayMetrics.density

    private val ribbonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isDither = true
        isFilterBitmap = true
    }

    // Warm “paper” track
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(70, 255, 252, 247)
    }

    private val trackStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = Color.argb(45, 120, 95, 80)
    }

    private val handleGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(35, 120, 90, 70)
        maskFilter = BlurMaskFilter(10f * density, BlurMaskFilter.Blur.NORMAL)
    }

    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(200, 150, 130, 120)
    }

    private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
        color = Color.argb(85, 90, 70, 60)
    }

    private val handleInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(70, 255, 255, 255)
    }

    private val rect = RectF()
    private val handleRect = RectF()
    private val handleInnerRect = RectF()

    private var allData: List<DailyEmotionRecord> = emptyList()

    // Indices into allData that have real check-ins (entriesCount > 0)
    private var shownIdx: IntArray = intArrayOf()

    // Selection is now a position in shownIdx (0..shownIdx.size-1)
    private var selectedPos = 0

    // We track the last sent ORIGINAL index (index in allData)
    private var lastSentOriginalIndex = -1

    private var isDragging = false
    private var downX = 0f
    private var downTime = 0L

    fun setData(records: List<DailyEmotionRecord>) {
        allData = records

        shownIdx = records.indices
            .filter { i -> records[i].entriesCount > 0 } // only checked-in days
            .toIntArray()

        selectedPos = (shownIdx.size - 1).coerceAtLeast(0) // default: latest check-in
        lastSentOriginalIndex = -1

        invalidate()
        dispatchSelection(force = true)
    }

    fun setSelectedIndex(index: Int) {
        if (shownIdx.isEmpty()) return

        // index is the ORIGINAL index in allData
        val pos = shownIdx.indexOf(index).let { found ->
            if (found >= 0) found else (shownIdx.size - 1) // fallback to latest
        }

        selectedPos = pos.coerceIn(0, shownIdx.size - 1)
        invalidate()
        dispatchSelection(force = true)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val pad = 2f * density
        rect.set(pad, pad, w - pad, h - pad)
        val radius = rect.height() / 2f

        // Track
        canvas.drawRoundRect(rect, radius, radius, trackPaint)
        canvas.drawRoundRect(rect, radius, radius, trackStroke)

        if (shownIdx.isEmpty()) return

        val n = shownIdx.size
        val dayColors = IntArray(n) { p ->
            val r = allData[shownIdx[p]]
            blendedDayColor(r.primary, r.secondary, alpha = 200)
        }

        // Smooth gradient: add midpoint stops between each day
        val maxStepsPerGap = 3
        val totalStops = if (n <= 1) 2 else (n + (n - 1) * maxStepsPerGap)

        val colors = IntArray(totalStops)
        val pos = FloatArray(totalStops)

        if (n == 1) {
            val c = dayColors[0]
            colors[0] = c; pos[0] = 0f
            colors[1] = c; pos[1] = 1f
        } else {
            var k = 0
            for (i in 0 until n - 1) {
                val cA = dayColors[i]
                val cB = dayColors[i + 1]

                val tA = i.toFloat() / (n - 1).toFloat()
                val tB = (i + 1).toFloat() / (n - 1).toFloat()

                colors[k] = cA
                pos[k] = tA
                k++

                for (s in 1..maxStepsPerGap) {
                    val u = s.toFloat() / (maxStepsPerGap + 1).toFloat()
                    colors[k] = mix(cA, cB, u)
                    pos[k] = tA + (tB - tA) * u
                    k++
                }
            }
            colors[totalStops - 1] = dayColors[n - 1]
            pos[totalStops - 1] = 1f
        }

        ribbonPaint.shader = LinearGradient(
            rect.left, rect.centerY(),
            rect.right, rect.centerY(),
            colors,
            pos,
            Shader.TileMode.CLAMP
        )

        canvas.drawRoundRect(rect, radius, radius, ribbonPaint)

        // Handle
        val handleW = 10f * density
        attachHandle(canvas, handleW)
    }

    private fun attachHandle(canvas: Canvas, handleW: Float) {
        val n = shownIdx.size.coerceAtLeast(1)
        val p = if (n == 1) 0f else selectedPos.coerceIn(0, n - 1).toFloat() / (n - 1).toFloat()
        val rawX = rect.left + p * rect.width()

        // clamp so it can’t clip
        val minX = rect.left + handleW / 2f
        val maxX = rect.right - handleW / 2f
        val hx = rawX.coerceIn(minX, maxX)

        val handleH = rect.height() * 0.82f
        val top = rect.centerY() - handleH / 2f
        val bottom = rect.centerY() + handleH / 2f

        handleRect.set(hx - handleW / 2f, top, hx + handleW / 2f, bottom)
        val r = handleRect.width() / 2f

        val glowPad = 6f * density
        val glowRect = RectF(
            handleRect.left - glowPad,
            handleRect.top - glowPad,
            handleRect.right + glowPad,
            handleRect.bottom + glowPad
        )
        canvas.drawRoundRect(glowRect, r + glowPad, r + glowPad, handleGlow)

        canvas.drawRoundRect(handleRect, r, r, handleFill)
        canvas.drawRoundRect(handleRect, r, r, handleStroke)

        val innerPad = 2.3f * density
        handleInnerRect.set(
            handleRect.left + innerPad,
            handleRect.top + innerPad,
            handleRect.right - innerPad,
            handleRect.top + 10.5f * density
        )
        canvas.drawRoundRect(handleInnerRect, 6f * density, 6f * density, handleInner)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (shownIdx.isEmpty()) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                isDragging = true
                downX = event.x
                downTime = event.eventTime
                updateSelectionFromX(event.x, allowHaptic = false)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false
                updateSelectionFromX(event.x, allowHaptic = true)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isDragging) return false
                isDragging = false

                val dx = abs(event.x - downX)
                val dt = event.eventTime - downTime
                if (dx < 6f * density && dt < 250) {
                    updateSelectionFromX(event.x, allowHaptic = true)
                }

                dispatchSelection(force = true)
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateSelectionFromX(xRaw: Float, allowHaptic: Boolean) {
        val pad = 2f * density
        val left = pad
        val right = width.toFloat() - pad
        val x = xRaw.coerceIn(left, right)

        val t = if (right == left) 0f else (x - left) / (right - left)
        val n = shownIdx.size
        val pos = ((n - 1) * t).roundToInt().coerceIn(0, n - 1)

        if (pos != selectedPos) {
            selectedPos = pos
            invalidate()
            if (allowHaptic) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            dispatchSelection(force = false)
        }
    }

    private fun dispatchSelection(force: Boolean) {
        if (shownIdx.isEmpty()) return

        val originalIndex = shownIdx[selectedPos]

        if (!force && originalIndex == lastSentOriginalIndex) return
        lastSentOriginalIndex = originalIndex

        onDayTapped?.invoke(originalIndex)
    }

    private fun blendedDayColor(p: Emotion?, s: Emotion?, alpha: Int): Int {
        // Empty day: warm fog
        if (p == null && s == null) return Color.argb(alpha.coerceIn(0, 255), 242, 238, 232)
        if (s == null || s == p) return colorFor(p, alpha)

        val c1 = colorFor(p, alpha)
        val c2 = colorFor(s, alpha)
        return mix(c1, c2, 0.42f)
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        val r = (Color.red(a) * (1f - tt) + Color.red(b) * tt).toInt()
        val g = (Color.green(a) * (1f - tt) + Color.green(b) * tt).toInt()
        val bl = (Color.blue(a) * (1f - tt) + Color.blue(b) * tt).toInt()
        val al = (Color.alpha(a) * (1f - tt) + Color.alpha(b) * tt).toInt()
        return Color.argb(al, r, g, bl)
    }

    private fun colorFor(e: Emotion?, alpha: Int): Int {
        val a = alpha.coerceIn(0, 255)
        if (e == null) return Color.argb(a, 242, 238, 232)

        val (r, g, b) = when (e) {
            Emotion.CALM -> Triple(154, 176, 169)
            Emotion.STEADY -> Triple(186, 175, 150)
            Emotion.FOGGY -> Triple(169, 171, 178)
            Emotion.MIXED -> Triple(176, 165, 172)
            Emotion.HEAVY -> Triple(168, 147, 152)
            Emotion.TENSE -> Triple(162, 155, 173)
            Emotion.TIRED -> Triple(184, 173, 156)
            Emotion.UNSURE -> Triple(176, 170, 164)
            Emotion.UNDISCLOSED -> Triple(235, 232, 225)
        }
        return Color.argb(a, r, g, b)
    }
}
