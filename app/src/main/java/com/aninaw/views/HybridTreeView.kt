package com.aninaw.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import kotlin.math.*
import kotlin.random.Random

/**
 * HybridTreeView
 *
 * Hybrid reveal tree (mango-inspired):
 * - Full geometry is built ONCE in onSizeChanged()
 * - Segments/leaf clusters are revealed by growth thresholds (no "drawing" animation)
 * - Leaves gently sway over time
 *
 * Public API:
 *   var growth: Float  // 0f..1f
 */
class HybridTreeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // Fixed seed = stable tree shape across rebuilds (no flickering geometry)
    private val rng = Random(42)

    // ---------------- Public API ----------------
    var growth: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var treeScale: Float = 0.50f   // 1.0 = full size, smaller = smaller tree
        set(value) {
            field = value.coerceIn(0.35f, 1.0f)
            invalidate()
        }

    var leafSizeFactor: Float = 1.12f   // 1.0 = original, >1 = bigger leaves
        set(value) {
            field = value.coerceIn(0.8f, 1.6f)
            rebuild()
        }

    // ---------------- Internal model ----------------
    private data class BranchNode(
        val path: Path,
        val width: Float,
        val threshold: Float
    )

    private data class Leaf(
        val basePoint: PointF,
        val dir: PointF,
        val size: Float,
        val thresholdStart: Float,
        val thresholdEnd: Float? = null,
        val phase: Float
    )

    private data class Firefly(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var r: Float,
        var phase: Float
    )

    // --------- ROOTS (soft, subtle) ----------
    private data class Root(
        val path: Path,
        val width: Float,
        val alpha: Int,
        val threshold: Float
    )

    private val roots = mutableListOf<Root>()

    private val branches = mutableListOf<BranchNode>()
    private val leaves = mutableListOf<Leaf>()
    private val tempPath = Path()

    private val groundPath = Path()

    private val branchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#8D6E63")
    }

    // Root paint (soft under-trunk)
    private val rootPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#7A5B52")
        alpha = 80
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }

    private val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#6e8b3d") // softer, warmer green
        alpha = 200
    }

    private val fireflies = mutableListOf<Firefly>()

    // Center of activity for fireflies (near canopy)
    private val canopyCenter = PointF(0f, 0f)

    // Points around the tree where fireflies prefer to hang around
    private val fireflyAttractors = mutableListOf<PointF>()

    // Soft warm glow paint
    private val fireflyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFDCA8") // warm amber
        alpha = 0
        maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
    }

    // Area radius around canopy where fireflies wander (computed on size)
    private var fireflyFieldRadius = 0f

    // -------- Gentle Ground --------
    private val groundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#D8C7B6") // soft warm brown
    }

    private val groundHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#10FFFFFF") // subtle soft light
    }

    // ---------------- Sway clock ----------------
    private var timeSec = 0f
    private var lastFrameNanos = 0L
    private var isRunning = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning) return

            if (lastFrameNanos == 0L) lastFrameNanos = frameTimeNanos
            val dt = (frameTimeNanos - lastFrameNanos) / 1_000_000_000f
            lastFrameNanos = frameTimeNanos

            timeSec += dt.coerceIn(0f, 0.05f)
            updateFireflies(dt.coerceIn(0f, 0.05f))
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stop() {
        isRunning = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    fun rebuild() {
        if (width > 0 && height > 0) {
            // Update blur whenever leaf size factor changes
            leafPaint.maskFilter = BlurMaskFilter(
                leafSizeFactor * 1.2f,
                BlurMaskFilter.Blur.NORMAL
            )
            buildMangoLikeGeometry(width, height)
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Needed for BlurMaskFilter to render reliably
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        buildMangoLikeGeometry(w, h)

        // --- Bigger Arc Hill Ground ---
        groundPath.reset()

        val arcTopY = h * 0.90f          // baseline of arc
        val arcLift = h * 0.20f          // how high the arc rises (increase this for more curve)
        val arcWidthExtra = w * 0.05f    // wider so curve feels natural

        groundPaint.shader = LinearGradient(
            0f,
            arcTopY - arcLift,
            0f,
            h.toFloat(),
            Color.parseColor("#E7DACE"),
            Color.parseColor("#CDB8A3"),
            Shader.TileMode.CLAMP
        )

        // Start bottom left (slightly outside view)
        groundPath.moveTo(-arcWidthExtra, h.toFloat())

        // Stronger upward arc
        groundPath.quadTo(
            w * 0.5f,
            arcTopY - arcLift,
            w + arcWidthExtra,
            h.toFloat()
        )

        // Close bottom
        groundPath.lineTo(w + arcWidthExtra, h.toFloat())
        groundPath.lineTo(-arcWidthExtra, h.toFloat())
        groundPath.close()
    }

    private fun buildMangoLikeGeometry(w: Int, h: Int) {
        branches.clear()
        leaves.clear()
        roots.clear()

        val cx = w * 0.5f
        val groundY = h * 0.90f

        // already in pixels, do NOT multiply by w again
        val globalLeafSize = w * 0.045f

        // ---- TRUNK (thick + tapered, with subtle root flare) ----
        val trunkBase = PointF(cx, groundY)

        // ---- SOFT ROOTS (subtle, under trunk) ----
        run {
            val base = PointF(trunkBase.x, trunkBase.y - h * 0.010f) // slightly above ground line
            val spread = w * 0.18f
            val dip = h * 0.030f

            fun rootCurve(endX: Float, endY: Float, c1x: Float, c1y: Float, c2x: Float, c2y: Float): Path {
                return Path().apply {
                    moveTo(base.x, base.y)
                    cubicTo(c1x, c1y, c2x, c2y, endX, endY)
                }
            }

            // Left main
            roots += Root(
                path = rootCurve(
                    endX = base.x - spread,
                    endY = base.y + dip,
                    c1x = base.x - spread * 0.25f,
                    c1y = base.y + dip * 0.15f,
                    c2x = base.x - spread * 0.70f,
                    c2y = base.y + dip * 0.90f
                ),
                width = w * 0.030f,
                alpha = 70,
                threshold = 0.00f
            )

            // Right main
            roots += Root(
                path = rootCurve(
                    endX = base.x + spread,
                    endY = base.y + dip,
                    c1x = base.x + spread * 0.25f,
                    c1y = base.y + dip * 0.15f,
                    c2x = base.x + spread * 0.70f,
                    c2y = base.y + dip * 0.90f
                ),
                width = w * 0.030f,
                alpha = 70,
                threshold = 0.00f
            )

            // Two small side roots (fainter)
            roots += Root(
                path = rootCurve(
                    endX = base.x - spread * 0.55f,
                    endY = base.y + dip * 0.55f,
                    c1x = base.x - spread * 0.20f,
                    c1y = base.y + dip * 0.10f,
                    c2x = base.x - spread * 0.45f,
                    c2y = base.y + dip * 0.60f
                ),
                width = w * 0.018f,
                alpha = 45,
                threshold = 0.00f
            )

            roots += Root(
                path = rootCurve(
                    endX = base.x + spread * 0.55f,
                    endY = base.y + dip * 0.55f,
                    c1x = base.x + spread * 0.20f,
                    c1y = base.y + dip * 0.10f,
                    c2x = base.x + spread * 0.45f,
                    c2y = base.y + dip * 0.60f
                ),
                width = w * 0.018f,
                alpha = 45,
                threshold = 0.00f
            )

            // A tiny center “toe” root (barely there)
            roots += Root(
                path = rootCurve(
                    endX = base.x,
                    endY = base.y + dip * 0.75f,
                    c1x = base.x - spread * 0.08f,
                    c1y = base.y + dip * 0.10f,
                    c2x = base.x + spread * 0.08f,
                    c2y = base.y + dip * 0.70f
                ),
                width = w * 0.014f,
                alpha = 35,
                threshold = 0.00f
            )
        }

        // bring trunkTop slightly lower so it blends into canopy better
        val trunkTop = PointF(cx, h * 0.34f)

        canopyCenter.set(trunkTop.x, trunkTop.y + h * 0.02f)

        // Approx canopy ellipse size (used for ring targets)
        canopyRx = w * 0.26f
        canopyRy = h * 0.18f

        // --- Firefly attractors (tree-centric, not screen-centric) ---
        fireflyAttractors.clear()

        // canopy cluster
        fireflyAttractors += PointF(cx, trunkTop.y + h * 0.04f)             // canopy center
        fireflyAttractors += PointF(cx - w * 0.18f, trunkTop.y + h * 0.08f) // left canopy
        fireflyAttractors += PointF(cx + w * 0.18f, trunkTop.y + h * 0.08f) // right canopy
        fireflyAttractors += PointF(cx, h * 0.52f)                          // mid-trunk zone

        // a couple near the sides of the tree silhouette
        fireflyAttractors += PointF(cx - w * 0.10f, h * 0.60f)
        fireflyAttractors += PointF(cx + w * 0.10f, h * 0.60f)

        // near ground but not on the very bottom edge
        fireflyAttractors += PointF(cx, groundY - h * 0.10f)

        // bigger “comfort radius” (used only for spawning distribution)
        fireflyFieldRadius = min(w, h) * 0.55f

        ensureFireflies(count = 14)

        // trunk control points (4 segments = smoother taper)
        val trunkMid1 = PointF(cx, h * 0.78f) // near base
        val trunkMid2 = PointF(cx, h * 0.60f)
        val trunkMid3 = PointF(cx, h * 0.46f)

        // Root flare: short, extra-thick base segment
        branches += BranchNode(
            path = bezierStem(trunkBase, trunkMid1, bendX = w * 0.018f),
            width = w * 0.090f,
            threshold = 0.00f
        )

        // Lower trunk
        branches += BranchNode(
            path = bezierStem(trunkMid1, trunkMid2, bendX = w * 0.022f),
            width = w * 0.075f,
            threshold = 0.00f
        )

        // Mid trunk
        branches += BranchNode(
            path = bezierStem(trunkMid2, trunkMid3, bendX = w * 0.026f),
            width = w * 0.058f,
            threshold = 0.00f
        )

        // Upper trunk into canopy
        branches += BranchNode(
            path = bezierStem(trunkMid3, trunkTop, bendX = w * 0.030f),
            width = w * 0.044f,
            threshold = 0.00f
        )

        // ---- SPROUT ----
        val sproutThresholdStart = 0.00f
        val sproutThresholdEnd = 0.19f
        val sproutBaseSize = w * 0.075f
        val sproutGap = sproutBaseSize * 0.55f

        leaves += Leaf(
            basePoint = PointF(trunkTop.x - sproutGap, trunkTop.y + sproutBaseSize * 0.15f),
            dir = PointF(-0.35f, -1.0f),
            size = sproutBaseSize,
            thresholdStart = sproutThresholdStart,
            thresholdEnd = sproutThresholdEnd,
            phase = 10.1f
        )
        leaves += Leaf(
            basePoint = PointF(trunkTop.x + sproutGap, trunkTop.y + sproutBaseSize * 0.15f),
            dir = PointF(0.35f, -1.0f),
            size = sproutBaseSize,
            thresholdStart = sproutThresholdStart,
            thresholdEnd = sproutThresholdEnd,
            phase = 11.3f
        )

        // ---- TIER 1 (SCAFFOLDS) ----
        val tier1Threshold = 0.20f
        val startY = h * 0.44f

        val scaffoldAngles = listOf(
            -70f, -40f, -8f, 8f, 40f, 70f
        )

        val scaffoldStarts = listOf(
            PointF(cx, startY + h * 0.018f),
            PointF(cx, startY - h * 0.006f),
            PointF(cx, startY - h * 0.032f),
            PointF(cx, startY - h * 0.028f),
            PointF(cx, startY - h * 0.006f),
            PointF(cx, startY + h * 0.018f)
        )

        val scaffoldTips = mutableListOf<PointF>()

        for (i in scaffoldAngles.indices) {
            val s = scaffoldStarts[i]
            val angle = scaffoldAngles[i]

            // Dome shaping: center arms longer, extremes shorter + tiny length noise
            val centerBias = 1f - (abs(angle) / 80f).coerceIn(0f, 1f)
            val lengthNoise = 1f + (rng.nextFloat() - 0.5f) * 0.08f          // ±4%
            val lens = (w * 0.22f) * (0.75f + 0.35f * centerBias) * lengthNoise

            // Break shelf look: tiny lift staggering
            val liftNoise = (rng.nextFloat() - 0.5f) * (h * 0.02f)
            val tip = polarTip(s, lens, angle, lift = h * 0.06f + liftNoise)

            scaffoldTips += tip

            branches += BranchNode(
                path = branchPath(s, tip, upwardBias = h * 0.10f),
                width = w * 0.038f,
                threshold = tier1Threshold
            )

            addLeafCluster(
                base = tip,
                dirAngleDeg = angle,
                baseSize = globalLeafSize,
                threshold = tier1Threshold,
                phaseSeed = i * 1.7f,
                count = 4 // fuller = less banding
            )
        }

        // ---- CENTER FILLER (removes hollow middle) ----
        run {
            val centerBase = PointF(cx, startY - h * 0.05f)
            val centerTip = polarTip(
                start = centerBase,
                len = w * 0.18f,
                angleDeg = 0f,
                lift = h * 0.03f
            )

            branches += BranchNode(
                path = branchPath(centerBase, centerTip, upwardBias = h * 0.06f),
                width = w * 0.030f,
                threshold = tier1Threshold
            )

            addLeafCluster(
                base = centerTip,
                dirAngleDeg = 0f,
                baseSize = globalLeafSize,
                threshold = tier1Threshold,
                phaseSeed = 999f,
                count = 4
            )
        }

        // ---- TIER 2 ----
        val tier2Threshold = 0.40f
        val secondaryTips = mutableListOf<PointF>()

        scaffoldTips.forEachIndexed { idx, p ->
            val baseAngle = scaffoldAngles[idx]
            val angles = listOf(baseAngle - 18f, baseAngle + 18f)
            val len = w * 0.12f

            angles.forEachIndexed { j, a ->
                // small lift jitter so tier lines don't look like shelves
                val liftNoise = (rng.nextFloat() - 0.5f) * (h * 0.015f)
                val tip = polarTip(p, len, a, lift = h * 0.03f + liftNoise)
                secondaryTips += tip

                addLeafCluster(
                    base = tip,
                    dirAngleDeg = a,
                    baseSize = globalLeafSize,
                    threshold = tier2Threshold,
                    phaseSeed = (idx * 10 + j) * 1.3f,
                    count = 4 // was 3
                )
            }
        }

        // ---- TIER 3 ----
        val tier3Threshold = 0.60f
        secondaryTips.forEachIndexed { idx, p ->
            val baseDir = if (p.x < cx) -20f else 20f
            val offsets = listOf(-15f, 0f, 15f)

            offsets.forEachIndexed { j, off ->
                val angleNoise = rng.nextFloat() * 6f - 3f // ±3 degrees
                val a = baseDir + off + angleNoise

                val randomLenFactor = 1f + rng.nextFloat() * 0.12f - 0.06f // ±6%
                val tip = polarTip(p, w * 0.14f * randomLenFactor, a, lift = h * 0.02f)

                branches += BranchNode(
                    path = branchPath(p, tip, upwardBias = h * 0.04f),
                    width = w * 0.018f,
                    threshold = tier3Threshold
                )

                addLeafCluster(
                    base = tip,
                    dirAngleDeg = a,
                    baseSize = globalLeafSize,
                    threshold = tier3Threshold,
                    phaseSeed = (idx * 10 + j) * 0.9f,
                    count = 3
                )
            }
        }

        // ---- TIER 4 ----
        val tier4Threshold = 0.72f

        // Rebuild tier3 tips (must match tier3 length scale, not the old 0.20f)
        val tier3Tips = mutableListOf<PointF>()
        secondaryTips.forEach { p ->
            val baseDir = if (p.x < cx) -20f else 20f
            val offsets = listOf(-15f, 0f, 15f)

            offsets.forEach { off ->
                val angleNoise = rng.nextFloat() * 6f - 3f
                val a = baseDir + off + angleNoise
                val tip = polarTip(p, w * 0.14f, a, lift = h * 0.02f)
                tier3Tips += tip
            }
        }

        val tier4Tips = buildNextTier(
            bases = tier3Tips,
            cx = cx,
            w = w,
            h = h,
            threshold = tier4Threshold,
            branchWidth = 0.014f,
            lenFactor = 0.14f,
            angleSpread = 18f,
            lift = 0.015f,
            upwardBias = 0.030f,
            leafBaseSize = 0.030f,
            phaseSeedBase = 200f
        )

        // ---- TIER 5 ----
        val tier5Threshold = 0.84f
        buildNextTier(
            bases = tier4Tips,
            cx = cx,
            w = w,
            h = h,
            threshold = tier5Threshold,
            branchWidth = 0.010f,
            lenFactor = 0.10f,
            angleSpread = 14f,
            lift = 0.012f,
            upwardBias = 0.022f,
            leafBaseSize = 0.026f,
            phaseSeedBase = 400f
        )

        // ---- LATE CANOPY DENSITY (leaves only) ----
        val denseLeafThreshold = 0.80f
        scaffoldTips.forEachIndexed { idx, tip ->
            addLeafCluster(
                base = PointF(tip.x, tip.y - h * 0.04f),
                dirAngleDeg = scaffoldAngles[idx],
                baseSize = globalLeafSize, // was w*0.034f; keep consistent
                threshold = denseLeafThreshold,
                phaseSeed = 50f + idx * 2.2f,
                count = 5
            )
        }
    }

    private var debugLockGrowth: Float? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawPath(groundPath, groundPaint)

        // Save once for the whole draw so temporary transforms don't leak
        canvas.save()

        // Subtle floating (visual only)
        val floatY = sin(timeSec * 0.4f) * 3f
        canvas.translate(0f, floatY)

        val g = (debugLockGrowth ?: growth).coerceIn(0f, 1f)

        val cx = width * 0.5f
        val groundY = height * 0.90f

        // Save again for scale-only block
        canvas.save()

        // Scale around trunk base so it shrinks inward naturally (and stays grounded)
        canvas.translate(cx, groundY)
        canvas.scale(treeScale, treeScale)
        canvas.translate(-cx, -groundY)

        // ---- Roots (draw first so trunk sits on top) ----
        for (r in roots) {
            if (g >= r.threshold) {
                rootPaint.strokeWidth = r.width
                val t = (g / 0.35f).coerceIn(0f, 1f)
                rootPaint.alpha = (r.alpha * (0.55f + 0.45f * t)).toInt().coerceIn(0, 110)
                canvas.drawPath(r.path, rootPaint)
            }
        }

        // ---- Branches ----
        for (b in branches) {
            if (g >= b.threshold) {
                branchPaint.strokeWidth = b.width
                canvas.drawPath(b.path, branchPaint)
            }
        }

        // ---- Leaves ----
        for (leaf in leaves) {
            if (g < leaf.thresholdStart) continue
            if (leaf.thresholdEnd != null && g > leaf.thresholdEnd) continue

            val sway = sin(timeSec * 0.85f + leaf.phase) * 0.55f
            val twist = sin(timeSec * 0.55f + leaf.phase * 0.7f) * 0.35f

            val dx = leaf.dir.x
            val dy = leaf.dir.y
            val px = -dy
            val py = dx

            val wiggle = leaf.size * 0.35f
            val ox = px * wiggle * sway
            val oy = py * wiggle * sway

            val center = PointF(leaf.basePoint.x + ox, leaf.basePoint.y + oy)

            val t = ((g - leaf.thresholdStart) / (1f - leaf.thresholdStart)).coerceIn(0f, 1f)
            leafPaint.alpha = (210 + 45 * t).toInt()

            drawMangoLeaf(canvas, center, leaf.dir, leaf.size, twist)
        }

        // Restore scale transform
        canvas.restore()

        drawFireflies(canvas, g)

        // Restore float transform (and anything else)
        canvas.restore()
    }

    // ---------------- Helpers ----------------

    private var canopyRx = 0f
    private var canopyRy = 0f

    private fun ensureFireflies(count: Int) {
        if (fireflies.size == count) return
        fireflies.clear()

        val pad = 18f

        repeat(count) { i ->
            val x = pad + rng.nextFloat() * (width - 2f * pad)
            val y = pad + rng.nextFloat() * (height - 2f * pad)

            val vx = (rng.nextFloat() - 0.5f) * 10f
            val vy = (rng.nextFloat() - 0.5f) * 10f

            val r = 4.5f + rng.nextFloat() * 5.5f
            val phase = rng.nextFloat() * 100f + i * 3.3f

            fireflies += Firefly(x, y, vx, vy, r, phase)
        }
    }

    private fun updateFireflies(dt: Float) {
        if (fireflies.isEmpty()) return

        val pad = 18f
        val maxV = 16f
        val damping = 0.988f

        // 1) random-walk motion (gentle wandering)
        for (f in fireflies) {
            val ax = (rng.nextFloat() - 0.5f) * 10f   // wander strength
            val ay = (rng.nextFloat() - 0.5f) * 10f

            f.vx = (f.vx * damping + ax * dt).coerceIn(-maxV, maxV)
            f.vy = (f.vy * damping + ay * dt).coerceIn(-maxV, maxV)

            f.x += f.vx * dt
            f.y += f.vy * dt

            // soft bounds bounce so they stay inside the view
            if (f.x < pad) { f.x = pad; f.vx = abs(f.vx) * 0.6f }
            if (f.x > width - pad) { f.x = width - pad; f.vx = -abs(f.vx) * 0.6f }
            if (f.y < pad) { f.y = pad; f.vy = abs(f.vy) * 0.6f }
            if (f.y > height - pad) { f.y = height - pad; f.vy = -abs(f.vy) * 0.6f }
        }

        // 2) anti-overlap: separation pass (keeps them from stacking)
        for (i in 0 until fireflies.size) {
            for (j in i + 1 until fireflies.size) {
                val a = fireflies[i]
                val b = fireflies[j]

                val dx = a.x - b.x
                val dy = a.y - b.y
                val d = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)

                val minD = (a.r + b.r) * 1.15f
                if (d < minD) {
                    val overlap = (minD - d)
                    val ux = dx / d
                    val uy = dy / d

                    // push both apart
                    a.x += ux * (overlap * 0.5f)
                    a.y += uy * (overlap * 0.5f)
                    b.x -= ux * (overlap * 0.5f)
                    b.y -= uy * (overlap * 0.5f)

                    // damp their velocities a bit so they don't re-collide immediately
                    a.vx *= 0.92f; a.vy *= 0.92f
                    b.vx *= 0.92f; b.vy *= 0.92f
                }
            }
        }
    }

    private fun drawFireflies(canvas: Canvas, g: Float) {
        if (fireflies.isEmpty()) return

        // Tie visibility to growth gently (still visible even early)
        val visibility = (0.35f + 0.65f * g).coerceIn(0f, 1f)

        for (f in fireflies) {
            // Soft pulse
            val pulse = (0.35f + 0.65f * (sin(timeSec * 1.2f + f.phase) * 0.5f + 0.5f))
            val a = (45f + 155f * visibility * pulse).toInt().coerceIn(0, 210)
            fireflyPaint.alpha = a

            canvas.drawCircle(f.x, f.y, f.r, fireflyPaint)
        }
    }

    private fun bezierStem(base: PointF, top: PointF, bendX: Float): Path {
        val midY = (base.y + top.y) * 0.5f
        val c1 = PointF(base.x + bendX, midY + (base.y - top.y) * 0.15f)
        val c2 = PointF(top.x - bendX, midY - (base.y - top.y) * 0.15f)

        return Path().apply {
            moveTo(base.x, base.y)
            cubicTo(c1.x, c1.y, c2.x, c2.y, top.x, top.y)
        }
    }

    private fun branchPath(start: PointF, end: PointF, upwardBias: Float): Path {
        val midNoiseX = (rng.nextFloat() - 0.5f) * 20f
        val midNoiseY = (rng.nextFloat() - 0.5f) * 20f

        val mx = (start.x + end.x) * 0.5f + midNoiseX
        val my = (start.y + end.y) * 0.5f - upwardBias + midNoiseY

        return Path().apply {
            moveTo(start.x, start.y)
            cubicTo(mx, my, mx, my, end.x, end.y)
        }
    }

    private fun polarTip(start: PointF, len: Float, angleDeg: Float, lift: Float): PointF {
        val a = Math.toRadians(angleDeg.toDouble())
        val x = start.x + (sin(a) * len).toFloat()
        val y = start.y - (cos(a) * len).toFloat() - lift
        return PointF(x, y)
    }

    private fun buildNextTier(
        bases: List<PointF>,
        cx: Float,
        w: Int,
        h: Int,
        threshold: Float,
        branchWidth: Float,
        lenFactor: Float,
        angleSpread: Float,
        lift: Float,
        upwardBias: Float,
        leafBaseSize: Float,
        phaseSeedBase: Float
    ): MutableList<PointF> {

        val newTips = mutableListOf<PointF>()

        bases.forEachIndexed { idx, p ->
            val outwardDir = if (p.x < cx) -1f else 1f
            val offsets = listOf(-1f, 0f, 1f)

            offsets.forEachIndexed { j, s ->
                val angle = outwardDir * (angleSpread * s)
                val tip = polarTip(
                    start = p,
                    len = w * lenFactor,
                    angleDeg = angle,
                    lift = h * lift
                )

                newTips += tip

                branches += BranchNode(
                    path = branchPath(p, tip, upwardBias = h * upwardBias),
                    width = w * branchWidth,
                    threshold = threshold
                )

                addLeafCluster(
                    base = tip,
                    dirAngleDeg = angle,
                    baseSize = w * leafBaseSize,
                    threshold = threshold,
                    phaseSeed = phaseSeedBase + (idx * 10 + j) * 1.1f,
                    count = 3
                )
            }
        }

        return newTips
    }

    private fun addLeafCluster(
        base: PointF,
        dirAngleDeg: Float,
        baseSize: Float,
        threshold: Float,
        phaseSeed: Float,
        count: Int = 3
    ) {
        val a = Math.toRadians(dirAngleDeg.toDouble())
        val dir = PointF(sin(a).toFloat(), -cos(a).toFloat())

        for (i in 0 until count) {
            val t = i / max(1f, (count - 1).toFloat())
            val spread = (t - 0.5f) * baseSize * 2.2f
            val perp = PointF(-dir.y, dir.x)

            val p = PointF(
                base.x + perp.x * spread,
                base.y + perp.y * spread
            )

            leaves += Leaf(
                basePoint = p,
                dir = dir,
                size = baseSize * leafSizeFactor,
                thresholdStart = threshold,
                thresholdEnd = null,
                phase = phaseSeed + i * 0.8f
            )
        }
    }

    private fun drawMangoLeaf(canvas: Canvas, center: PointF, dir: PointF, size: Float, twist: Float) {
        val len = sqrt(dir.x * dir.x + dir.y * dir.y).coerceAtLeast(0.001f)
        val ux = dir.x / len
        val uy = dir.y / len
        val px = -uy
        val py = ux

        val length = size * 2.15f
        val width = size * 0.95f

        // FIX: don't chop the leaf in half
        val tip = PointF(center.x + ux * (length * 0.94f), center.y + uy * (length * 0.94f))
        val base = PointF(center.x - ux * length * 0.35f, center.y - uy * length * 0.35f)

        val twistAmt = width * 0.35f * twist
        val c1 = PointF(center.x + px * (width + twistAmt), center.y + py * (width + twistAmt))
        val c2 = PointF(center.x - px * (width - twistAmt), center.y - py * (width - twistAmt))

        tempPath.reset()
        tempPath.moveTo(base.x, base.y)
        tempPath.quadTo(c1.x, c1.y, tip.x, tip.y)
        tempPath.quadTo(c2.x, c2.y, base.x, base.y)
        tempPath.close()

        canvas.drawPath(tempPath, leafPaint)
    }
}