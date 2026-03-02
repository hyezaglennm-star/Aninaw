package com.aninaw.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class RadarRhythmView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val labels = listOf("Body", "Mind", "Soul", "Environment")

    // 0f..1f
    private var values = floatArrayOf(0f, 0f, 0f, 0f)

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A6B5A52") // subtle brown tint
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A6B5A52")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6B8E6B") // calm green
        style = Paint.Style.FILL
        alpha = 90
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6B8E6B")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        alpha = 160
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6B8E6B")
        style = Paint.Style.FILL
        alpha = 200
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7A645B")
        textSize = 34f
        textAlign = Paint.Align.CENTER
    }

    fun setValues(body: Float, mind: Float, soul: Float, env: Float) {
        values[0] = body.coerceIn(0f, 1f)
        values[1] = mind.coerceIn(0f, 1f)
        values[2] = soul.coerceIn(0f, 1f)
        values[3] = env.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) * 0.62f

        // 4 axes, starting at top
        val angles = floatArrayOf(
            (-90f).toRadians(),
            (0f).toRadians(),
            (90f).toRadians(),
            (180f).toRadians()
        )

        drawGrid(canvas, cx, cy, radius, angles)
        drawData(canvas, cx, cy, radius, angles)
        drawLabels(canvas, cx, cy, radius, angles)
    }

    private fun drawGrid(canvas: Canvas, cx: Float, cy: Float, radius: Float, angles: FloatArray) {
        val levels = 4
        for (lvl in 1..levels) {
            val r = radius * (lvl / levels.toFloat())
            val path = Path()
            for (i in 0..3) {
                val x = cx + r * cos(angles[i])
                val y = cy + r * sin(angles[i])
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            canvas.drawPath(path, gridPaint)
        }

        // axis lines
        for (i in 0..3) {
            val x = cx + radius * cos(angles[i])
            val y = cy + radius * sin(angles[i])
            canvas.drawLine(cx, cy, x, y, axisPaint)
        }
    }

    private fun drawData(canvas: Canvas, cx: Float, cy: Float, radius: Float, angles: FloatArray) {
        val path = Path()

        val pts = ArrayList<PointF>(4)
        for (i in 0..3) {
            val r = radius * values[i]
            val x = cx + r * cos(angles[i])
            val y = cy + r * sin(angles[i])
            pts.add(PointF(x, y))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, outlinePaint)

        // points
        val pr = radius * 0.02f
        pts.forEach { p -> canvas.drawCircle(p.x, p.y, pr, pointPaint) }
    }

    private fun drawLabels(canvas: Canvas, cx: Float, cy: Float, radius: Float, angles: FloatArray) {
        val labelRadius = radius * 1.22f
        for (i in 0..3) {
            val x = cx + labelRadius * cos(angles[i])
            val y = cy + labelRadius * sin(angles[i]) + 12f
            canvas.drawText(labels[i], x, y, labelPaint)
        }
    }

    private fun Float.toRadians(): Float = (this * Math.PI / 180.0).toFloat()
}