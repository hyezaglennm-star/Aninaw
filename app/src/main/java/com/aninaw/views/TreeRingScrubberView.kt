package com.aninaw.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class TreeRingScrubberView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /**
     * progress: 0f..1f
     * 0f = TOP (earliest)
     * 1f = BOTTOM (latest / today)
     */
    var progress: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var onScrub: ((Float, Boolean) -> Unit)? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(10f)
        color = 0xFFBFDAD0.toInt()
        alpha = 90
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(10f)
        color = 0xFF8EC8B6.toInt()
        alpha = 160
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF6FBCA8.toInt()
        alpha = 255
    }

    private val handleRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = 0xFFFFFFFF.toInt()
        alpha = 160
    }

    private var isScrubbing = false
    private var lastTick = -1
    private val tickEvery = 0.05f // haptic every 5%

    // Optional: make it feel like a thin "timeline"
    private val paddingTop = dp(14f)
    private val paddingBottom = dp(14f)
    private val paddingSide = dp(18f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        val cx = w / 2f
        val top = paddingTop
        val bottom = h - paddingBottom

        val trackX = cx

        // Track
        canvas.drawLine(trackX, top, trackX, bottom, trackPaint)

        // Fill (from bottom up to knob, so it reads as "progress toward today")
        val knobY = lerp(top, bottom, progress)
        canvas.drawLine(trackX, knobY, trackX, bottom, fillPaint)

        // Handle
        val r = dp(10f)
        canvas.drawCircle(trackX, knobY, r, handlePaint)
        canvas.drawCircle(trackX, knobY, r + dp(2.5f), handleRingPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                isScrubbing = true
                updateFromTouch(event.y)
                onScrub?.invoke(progress, true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isScrubbing) return false
                updateFromTouch(event.y)
                onScrub?.invoke(progress, true)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isScrubbing) return false
                isScrubbing = false
                onScrub?.invoke(progress, false)
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateFromTouch(y: Float) {
        val top = paddingTop
        val bottom = height.toFloat() - paddingBottom

        // Clamp to track bounds (NO infinity, NO looping)
        val clampedY = y.coerceIn(top, bottom)

        // Map y to progress (top=0, bottom=1)
        val p = ((clampedY - top) / (bottom - top)).coerceIn(0f, 1f)
        progress = p

        // Soft haptic ticks while dragging (optional)
        val tick = floor(progress / tickEvery).toInt()
        if (isScrubbing && tick != lastTick) {
            lastTick = tick
            try { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) } catch (_: Throwable) {}
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}