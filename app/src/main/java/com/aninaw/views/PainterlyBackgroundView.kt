package com.aninaw.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*
import kotlin.random.Random

/**
 * PainterlyBackgroundView
 * Soft watercolor-ish landscape:
 * - Sky gradient + faint cloud smears
 * - Mist layers
 * - Distant hills (blurred)
 * - Tree masses (blurred blobs)
 * - Foreground grass texture (soft strokes)
 * - Subtle noise overlay for "paper"
 *
 * Stable across frames (seeded). No animation by default.
 */
class PainterlyBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val rng = Random(42) // stable look

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
        isFilterBitmap = true
    }

    private val tmpPath = Path()

    private var w = 0f
    private var h = 0f

    // Reused bitmap noise (generated once per size)
    private var noiseBmp: Bitmap? = null
    private var noiseShader: Shader? = null

    override fun onSizeChanged(width: Int, height: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(width, height, oldw, oldh)
        w = width.toFloat()
        h = height.toFloat()

        // Build subtle noise texture
        noiseBmp = makeNoiseBitmap(width, height, strength = 18) // 0..255
        noiseShader = noiseBmp?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (w <= 0f || h <= 0f) return

        // 1) Sky wash
        drawSky(canvas)

        // 2) Soft cloud smears
        drawCloudSmears(canvas)

        // 3) Distant mist / horizon glow
        drawMist(canvas)

        // 4) Distant hills
        drawHills(canvas)

        // 5) Midground tree masses (left/right)
        drawTreeMasses(canvas)

        // 6) Foreground field + grass texture
        drawField(canvas)

        // 7) Paper/noise overlay (very subtle)
        drawNoiseOverlay(canvas)
    }

    private fun drawSky(canvas: Canvas) {
        val skyTop = Color.parseColor("#DCE9F7")
        val skyMid = Color.parseColor("#EEF4FA")
        val skyLow = Color.parseColor("#F6F2F2")

        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(skyTop, skyMid, skyLow),
            floatArrayOf(0f, 0.35f, 0.75f),
            Shader.TileMode.CLAMP
        )
        paint.alpha = 255
        paint.maskFilter = null
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }

    private fun drawCloudSmears(canvas: Canvas) {
        // Soft horizontal smears with low alpha + blur
        paint.color = Color.WHITE
        paint.alpha = 26
        paint.style = Paint.Style.FILL
        paint.maskFilter = BlurMaskFilter(min(w, h) * 0.045f, BlurMaskFilter.Blur.NORMAL)

        val bandY = h * 0.18f
        repeat(10) { i ->
            val y = bandY + (i * h * 0.02f) + rng.nextFloat() * h * 0.01f
            val left = -w * 0.1f
            val right = w * 1.1f
            val height = h * (0.03f + rng.nextFloat() * 0.02f)

            tmpPath.reset()
            tmpPath.moveTo(left, y)
            val mid1x = w * (0.25f + rng.nextFloat() * 0.15f)
            val mid2x = w * (0.65f + rng.nextFloat() * 0.15f)
            tmpPath.cubicTo(
                mid1x, y - height * 0.35f,
                mid2x, y + height * 0.35f,
                right, y
            )
            tmpPath.lineTo(right, y + height)
            tmpPath.cubicTo(
                mid2x, y + height * 1.2f,
                mid1x, y + height * 0.8f,
                left, y + height
            )
            tmpPath.close()

            canvas.drawPath(tmpPath, paint)
        }

        paint.maskFilter = null
        paint.alpha = 255
    }

    private fun drawMist(canvas: Canvas) {
        // Big soft glow at horizon
        val glow = RadialGradient(
            w * 0.5f, h * 0.42f,
            max(w, h) * 0.55f,
            intArrayOf(Color.parseColor("#80FFFFFF"), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = glow
        paint.alpha = 255
        paint.maskFilter = null
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // Horizontal mist bands (like soft fog layers)
        paint.color = Color.WHITE
        paint.alpha = 22
        paint.maskFilter = BlurMaskFilter(min(w, h) * 0.05f, BlurMaskFilter.Blur.NORMAL)

        val startY = h * 0.48f
        repeat(9) { i ->
            val y = startY + i * h * 0.045f + rng.nextFloat() * h * 0.01f
            val bandH = h * (0.035f + rng.nextFloat() * 0.02f)
            canvas.drawRoundRect(
                -w * 0.1f, y,
                w * 1.1f, y + bandH,
                bandH, bandH,
                paint
            )
        }

        paint.maskFilter = null
        paint.alpha = 255
    }

    private fun drawHills(canvas: Canvas) {
        // Two layers of distant hills, bluish and very soft
        fun hillLayer(yBase: Float, amp: Float, color: Int, alpha: Int, blur: Float) {
            paint.color = color
            paint.alpha = alpha
            paint.style = Paint.Style.FILL
            paint.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)

            tmpPath.reset()
            tmpPath.moveTo(0f, yBase)
            val steps = 8
            for (i in 1..steps) {
                val x = w * (i / steps.toFloat())
                val wave = sin(i * 0.9f + 0.6f) * amp
                val noise = (rng.nextFloat() - 0.5f) * amp * 0.35f
                tmpPath.lineTo(x, yBase + wave + noise)
            }
            tmpPath.lineTo(w, h)
            tmpPath.lineTo(0f, h)
            tmpPath.close()

            canvas.drawPath(tmpPath, paint)
            paint.maskFilter = null
        }

        hillLayer(
            yBase = h * 0.52f,
            amp = h * 0.03f,
            color = Color.parseColor("#C9D3E3"),
            alpha = 80,
            blur = min(w, h) * 0.02f
        )
        hillLayer(
            yBase = h * 0.56f,
            amp = h * 0.04f,
            color = Color.parseColor("#BFCBE0"),
            alpha = 65,
            blur = min(w, h) * 0.025f
        )
    }

    private fun drawTreeMasses(canvas: Canvas) {
        // Left and right clusters, fuzzy and muted green
        fun treeBlob(cx: Float, cy: Float, rx: Float, ry: Float, baseColor: Int, alpha: Int) {
            paint.color = baseColor
            paint.alpha = alpha
            paint.style = Paint.Style.FILL
            paint.maskFilter = BlurMaskFilter(min(rx, ry) * 0.18f, BlurMaskFilter.Blur.NORMAL)

            tmpPath.reset()
            val lobes = 7
            for (i in 0..lobes) {
                val t = (i / lobes.toFloat()) * (2f * Math.PI).toFloat()
                val nx = cos(t) * rx * (0.85f + rng.nextFloat() * 0.35f)
                val ny = sin(t) * ry * (0.85f + rng.nextFloat() * 0.35f)
                val x = cx + nx
                val y = cy + ny
                if (i == 0) tmpPath.moveTo(x, y) else tmpPath.lineTo(x, y)
            }
            tmpPath.close()

            canvas.drawPath(tmpPath, paint)
            paint.maskFilter = null
        }

        val y = h * 0.63f

        // Left grove
        repeat(10) {
            treeBlob(
                cx = w * (0.10f + rng.nextFloat() * 0.22f),
                cy = y - rng.nextFloat() * h * 0.08f,
                rx = w * (0.09f + rng.nextFloat() * 0.07f),
                ry = h * (0.07f + rng.nextFloat() * 0.05f),
                baseColor = Color.parseColor("#AEB89B"),
                alpha = 70
            )
        }

        // Right grove
        repeat(12) {
            treeBlob(
                cx = w * (0.74f + rng.nextFloat() * 0.22f),
                cy = y - rng.nextFloat() * h * 0.08f,
                rx = w * (0.09f + rng.nextFloat() * 0.08f),
                ry = h * (0.07f + rng.nextFloat() * 0.06f),
                baseColor = Color.parseColor("#A7B294"),
                alpha = 72
            )
        }

        // Mid hazy bushes
        repeat(8) {
            treeBlob(
                cx = w * (0.35f + rng.nextFloat() * 0.30f),
                cy = h * 0.66f + rng.nextFloat() * h * 0.02f,
                rx = w * (0.06f + rng.nextFloat() * 0.05f),
                ry = h * (0.05f + rng.nextFloat() * 0.04f),
                baseColor = Color.parseColor("#B9C2A6"),
                alpha = 45
            )
        }
    }

    private fun drawField(canvas: Canvas) {
        // Base field gradient
        paint.shader = LinearGradient(
            0f, h * 0.60f, 0f, h,
            intArrayOf(
                Color.parseColor("#EDE6C5"),
                Color.parseColor("#D9D8B6"),
                Color.parseColor("#C9D1B2")
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.alpha = 110
        paint.maskFilter = BlurMaskFilter(min(w, h) * 0.015f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawRect(0f, h * 0.58f, w, h, paint)
        paint.shader = null
        paint.maskFilter = null
        paint.alpha = 255

        // Grass strokes: tiny soft lines, mostly near bottom
        paint.color = Color.parseColor("#9DA98B")
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.maskFilter = BlurMaskFilter(min(w, h) * 0.0045f, BlurMaskFilter.Blur.NORMAL)

        val strokes = (w * h / 9000f).toInt().coerceIn(700, 2600)
        repeat(strokes) {
            val x = rng.nextFloat() * w
            val y = h * (0.68f + rng.nextFloat() * 0.32f)
            val len = h * (0.006f + rng.nextFloat() * 0.02f)
            val ang = (-0.7f + rng.nextFloat() * 1.4f) // slight lean
            paint.alpha = (10 + rng.nextInt(22))
            paint.strokeWidth = (1.0f + rng.nextFloat() * 2.3f)

            val x2 = x + cos(ang) * len
            val y2 = y - sin(ang) * len
            canvas.drawLine(x, y, x2, y2, paint)
        }

        // Near-ground soft fog to blend everything (your image has that creamy haze)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.alpha = 35
        paint.maskFilter = BlurMaskFilter(min(w, h) * 0.06f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawRoundRect(
            -w * 0.1f, h * 0.70f,
            w * 1.1f, h * 1.02f,
            h * 0.12f, h * 0.12f,
            paint
        )

        paint.maskFilter = null
        paint.alpha = 255
    }

    private fun drawNoiseOverlay(canvas: Canvas) {
        val shader = noiseShader ?: return

        paint.shader = shader
        paint.alpha = 18 // subtle paper grain
        paint.style = Paint.Style.FILL
        paint.maskFilter = null

        // Use SRC_OVER by default; this works like a light texture.
        canvas.drawRect(0f, 0f, w, h, paint)

        paint.shader = null
        paint.alpha = 255
    }

    private fun makeNoiseBitmap(width: Int, height: Int, strength: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        // light gray noise (not black specks)
        for (i in pixels.indices) {
            val n = rng.nextInt(strength) // 0..strength
            val v = 255 - n
            pixels[i] = Color.argb(255, v, v, v)
        }
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmp
    }
}