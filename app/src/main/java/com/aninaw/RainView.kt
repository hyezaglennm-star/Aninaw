package com.aninaw

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.roundToInt
import kotlin.random.Random

class RainView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class Drop(
        var x: Float,
        var y: Float,
        var len: Float,
        var speed: Float,
        var alpha: Int
    )

    private val rng = Random.Default
    private val drops = ArrayList<Drop>(120)

    // Gentle rain: sparse + slow
    private var dropCount = 70
    private var lastFrameMs = 0L

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.1f)
        strokeCap = Paint.Cap.ROUND
    }

    var isRunning: Boolean = false
        private set

    fun start() {
        if (isRunning) return
        isRunning = true
        lastFrameMs = 0L
        postInvalidateOnAnimation()
    }

    fun stop() {
        isRunning = false
        lastFrameMs = 0L
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildDrops(w, h)
    }

    private fun buildDrops(w: Int, h: Int) {
        drops.clear()
        if (w <= 0 || h <= 0) return

        // Density proportional to area, capped
        val areaFactor = (w * h) / 1_000_000f
        dropCount = (55 + (areaFactor * 25f)).roundToInt().coerceIn(55, 90)

        repeat(dropCount) {
            drops.add(
                Drop(
                    x = rng.nextFloat() * w,
                    y = rng.nextFloat() * h,
                    len = dp(rng.nextInt(10, 22).toFloat()),
                    speed = dp(rng.nextInt(24, 52).toFloat()), // px/sec-ish
                    alpha = rng.nextInt(30, 70) // very subtle
                )
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        // If not running, draw nothing (keeps Steady mode clean)
        if (!isRunning) return

        val now = System.currentTimeMillis()
        val dt = if (lastFrameMs == 0L) 16L else (now - lastFrameMs).coerceIn(8L, 40L)
        lastFrameMs = now
        val dtSec = dt / 1000f

        // Slight diagonal, like quiet rain, not a screensaver
        val dx = dp(10f) * dtSec

        for (d in drops) {
            d.y += d.speed * dtSec
            d.x += dx

            if (d.y - d.len > h) {
                d.y = -dp(rng.nextInt(10, 60).toFloat())
                d.x = rng.nextFloat() * w
            }
            if (d.x > w + dp(20f)) d.x = -dp(20f)

            paint.alpha = d.alpha
            // warm-ish, desaturated rain (looks softer than pure gray)
            paint.color = 0xFF6E7B6E.toInt()

            canvas.drawLine(d.x, d.y, d.x - dp(6f), d.y + d.len, paint)
        }

        postInvalidateOnAnimation()
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
