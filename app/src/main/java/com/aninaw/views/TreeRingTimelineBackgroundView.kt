//TreeRingTimelineBackgroundView.kt
package com.example.aninaw.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class TreeRingTimelineBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density

    private val fineRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#8A6247")
        alpha = 22
        strokeWidth = 1.2f * density
    }

    private val mainBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#8A6247")
        alpha = 52
        strokeWidth = 5f * density
    }

    private val strongBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#7B563E")
        alpha = 70
        strokeWidth = 7f * density
    }

    private val rootRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#7B563E")
        alpha = 48
        strokeWidth = 2.2f * density
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = (1800 * density).toInt()
        setMeasuredDimension(width, resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // Soft background tint, because flat white is a crime.
        canvas.drawColor(Color.parseColor("#F6F0E8"))

        drawFineWoodLines(canvas, w, h)
        drawTimelineBands(canvas, w, h)
        drawBottomRings(canvas, w, h)
    }

    private fun drawFineWoodLines(canvas: Canvas, w: Float, h: Float) {
        val centerX = w / 2f
        val centerY = h * 1.10f

        var radius = 120f * density
        while (radius < max(w, h) * 1.4f) {
            val oval = RectF(
                centerX - radius,
                centerY - radius * 0.52f,
                centerX + radius,
                centerY + radius * 0.52f
            )
            canvas.drawArc(oval, 180f, 180f, false, fineRingPaint)
            radius += 28f * density
        }
    }

    private fun drawTimelineBands(canvas: Canvas, w: Float, h: Float) {
        val bandYs = listOf(
            430f * density,
            720f * density,
            1010f * density,
            1300f * density
        )

        for ((index, y) in bandYs.withIndex()) {
            val radiusX = (w * 0.70f) + (index * 22f * density)
            val radiusY = 170f * density

            val oval = RectF(
                (w / 2f) - radiusX,
                y - radiusY,
                (w / 2f) + radiusX,
                y + radiusY
            )

            canvas.drawArc(oval, 180f, 180f, false, if (index % 2 == 0) strongBandPaint else mainBandPaint)

            var innerOffset = 22f * density
            repeat(4) {
                val inner = RectF(
                    oval.left + innerOffset,
                    oval.top + innerOffset * 0.42f,
                    oval.right - innerOffset,
                    oval.bottom - innerOffset * 0.42f
                )
                canvas.drawArc(inner, 180f, 180f, false, fineRingPaint)
                innerOffset += 18f * density
            }
        }
    }

    private fun drawBottomRings(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h - (120f * density)

        var radius = 36f * density
        repeat(10) {
            canvas.drawCircle(cx, cy, radius, rootRingPaint)
            radius += 16f * density
        }

        // Tiny sprout stem
        val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#6D7F3B")
            strokeWidth = 3f * density
        }
        canvas.drawLine(cx, cy - 42f * density, cx, cy - 8f * density, stemPaint)

        // Tiny leaves
        val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#879A4A")
            alpha = 220
        }

        canvas.drawOval(
            RectF(cx - 20f * density, cy - 52f * density, cx - 4f * density, cy - 36f * density),
            leafPaint
        )
        canvas.drawOval(
            RectF(cx + 4f * density, cy - 52f * density, cx + 20f * density, cy - 36f * density),
            leafPaint
        )
    }
}