//GentleTreeView.kt
package com.aninaw

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*

class GentleTreeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // --------------------------------------------
    // PUBLIC API
    // --------------------------------------------
    enum class Stage { SAPLING, YOUNG, MATURE, OLD }

    /**
     * Optional manual override for previews/debug.
     * If null, stage is derived from growth (but does NOT rebuild geometry).
     */
    private var stageOverride: Stage? = null
    var stage: Stage
        get() = stageOverride ?: stageFromGrowth(growth)
        set(value) {
            stageOverride = value
            postInvalidateOnAnimation()
        }

    var growth: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            // CRITICAL FIX: do NOT rebuild() when growth crosses stage thresholds.
            postInvalidateOnAnimation()
        }

    var seed: Long = 1337L
        set(value) {
            field = value
            rebuild() // seed changes actual structure
        }

    var swayIntensity: Float = 0.12f
        set(value) {
            field = value.coerceIn(0f, 1f)
            postInvalidateOnAnimation()
        }

    var swayDurationMs: Long = 7200L
        set(value) {
            field = value.coerceIn(2000L, 20000L)
            animator?.cancel()
            animator = null
            startAnimator()
        }

    var stillMode: Boolean = false
        set(value) {
            field = value
            postInvalidateOnAnimation()
        }

    fun startDevGrowthTest(durationMs: Long = 45000L, slowTail: Float = 3.2f, loop: Boolean = true) {
        devAnimator?.cancel()
        val d = durationMs.coerceAtLeast(200L)
        devAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = d
            repeatCount = if (loop) ValueAnimator.INFINITE else 0
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener {
                val raw = it.animatedValue as Float
                growth = slowEnd(raw, slowTail)
            }
            start()
        }
    }

    // --------------------------------------------
    // INTERNAL MODEL
    // --------------------------------------------
    private enum class BranchKind { TRUNK, PRIMARY, SECONDARY, TERTIARY }

    private fun easeOutCubic(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        val u = 1f - x
        return 1f - u * u * u
    }

    private fun smoothstep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun stageFromGrowth(g: Float): Stage {
        val x = g.coerceIn(0f, 1f)
        return when {
            x < 0.35f -> Stage.SAPLING
            x < 0.70f -> Stage.YOUNG
            else -> Stage.MATURE
        }
    }

    private data class BranchSpec(
        val kind: BranchKind,
        val parentIndex: Int,
        val parentU: Float,
        val spawnG: Float,
        val growSpan: Float,
        val side: Float,
        val angleRad: Float,
        val lengthN: Float,
        val curve: Float,
        val taper: Float,
        val nodeCountPairs: Int,
        val nodeStartU: Float = 0.25f,
        val nodeEndU: Float = 0.98f,
        val phase: Float
    ) {
        fun grownP(growth: Float): Float {
            val t = ((growth - spawnG) / growSpan).coerceIn(0f, 1f)
            val u = 1f - t
            return 1f - u * u * u
        }
    }

    private data class BranchGeom(
        val x0: Float, val y0: Float,
        val cx: Float, val cy: Float,
        val x1: Float, val y1: Float,
        val grownP: Float
    )

    private data class LeafNode(
        val branchIndex: Int,
        val u: Float,
        val sideSign: Float,
        val normalAbs: Float,
        val sizeMul: Float,
        val appearG: Float,
        val edgeW: Float,
        val phase: Float,
        val color: Int
    )

    // --------------------------------------------
    // RENDER STATE
    // --------------------------------------------
    private val trunkPath = Path()
    private val leafPath = Path()
    private val tmpRect = RectF()

    private val trunkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isDither = true
    }

    private val branchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isDither = true
    }

    private val barkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isDither = true
    }

    private val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isDither = true
    }

    private val veinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isDither = true
    }

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isDither = true
    }

    private val leafDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var branches: List<BranchSpec> = emptyList()
    private var leaves: List<LeafNode> = emptyList()

    private var animator: ValueAnimator? = null
    private var devAnimator: ValueAnimator? = null
    private var tWind = 0f

    private companion object {
        const val BARK_MIX: Long = 0x3ADC0FFEE0DDF00DL
        const val BARK_TOP = "#7A6F34"
        const val BARK_BOT = "#5E5528"
    }

    init {
        setWillNotDraw(false)
        buildLeafShape()
        rebuild()

        if (isInEditMode) {
            stageOverride = Stage.MATURE
            growth = 1f
            stillMode = true
            swayIntensity = 0f
            tWind = 0f
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimator()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        devAnimator?.cancel()
        devAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // size changes don’t need spec rebuild, but harmless if you want stable layout math.
        invalidate()
    }

    private fun startAnimator() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = swayDurationMs
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener {
                tWind = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun rebuild() {
        // Build ONE stable structure, independent of stage.
        branches = buildBranchSpecs(seed)
        leaves = buildLeafNodesPaired(branches, seed)
        invalidate()
    }

    // --------------------------------------------
    // DRAW
    // --------------------------------------------
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)

        val cx = w * 0.5f
        val baseY = h * 0.80f
        val scale = min(w, h) * 0.74f

        // Smooth sizing by growth (not stage switching)
        val g = growth.coerceIn(0f, 1f)

        val canopyR = scale * (0.22f + 0.14f * smoothstep((g - 0.35f) / 0.65f))
        val trunkHMax = scale * (0.30f + 0.14f * smoothstep((g - 0.15f) / 0.85f))
        val trunkWMax = scale * (0.060f + 0.045f * smoothstep((g - 0.45f) / 0.55f))

        val trunkSway = if (stillMode) 0f else {
            val wave = sin((tWind * 2f * PI).toFloat())
            wave * 0.0065f * swayIntensity
        }

        branches.firstOrNull() ?: return

        // Trunk grows early, then thickens later
        val trunkGrow = easeOutCubic((g / 0.40f).coerceIn(0f, 1f))
        val thicken = smoothstep(((g - 0.45f) / 0.55f).coerceIn(0f, 1f))
        val trunkWNow = trunkWMax * (0.12f + 0.88f * thicken)

        val trunkTopY = baseY - trunkHMax * trunkGrow

        val trunkGeom = computeTrunkGeom(
            cx = cx,
            baseY = baseY,
            topY = trunkTopY,
            trunkGrow = trunkGrow,
            trunkSway = trunkSway,
            canopyR = canopyR
        )

        val geoms = ArrayList<BranchGeom?>(branches.size)
        geoms.add(trunkGeom)

        for (i in 1 until branches.size) {
            val br = branches[i]
            val parent = geoms.getOrNull(br.parentIndex)
            geoms.add(computeBranchGeom(br, parent, canopyR))
        }

        drawTrunk(canvas, trunkGeom, trunkWNow, trunkGrow, canopyR)
        drawBranchesAsTubes(canvas, branches, geoms, canopyR)

        // Sapling leaf-blades (the only thing you see early besides stem)
        drawSaplingLeafBlades(canvas, trunkGeom, canopyR)

        // Canopy leaves start AFTER sapling phase, smoothly
        drawLeavesPaired(canvas, geoms, canopyR)

        drawSoftLeafHighlight(canvas, canopyR)
    }

    // --------------------------------------------
    // SAPLING LEAF-BLADES (keeps your “only these” early look)
    // --------------------------------------------
    private fun drawSaplingLeafBlades(canvas: Canvas, trunkGeom: BranchGeom, canopyR: Float) {
        val g = growth.coerceIn(0f, 1f)

        // timeline (global growth):
        // 0.00..0.12 : all 4 blades fade in (already visible in first phase)
        // 0.42..0.55 : blades fade out as wood/leaves take over
        val tipInStart = 0.00f
        val tipInEnd = 0.12f

        val lowInStart = 0.00f
        val lowInEnd = 0.12f

        val fadeOutStart = 0.42f
        val fadeOutEnd = 0.55f

        val tipGate = ((g - tipInStart) / (tipInEnd - tipInStart)).coerceIn(0f, 1f)
        val lowGate = ((g - lowInStart) / (lowInEnd - lowInStart)).coerceIn(0f, 1f)

        val fadeOut = (1f - ((g - fadeOutStart) / (fadeOutEnd - fadeOutStart)).coerceIn(0f, 1f))
            .coerceIn(0f, 1f)

        val leafVis = fadeOut.coerceIn(0f, 1f)
        if (leafVis <= 0.001f) return

        val leafSize = canopyR * (0.085f + 0.065f * smoothstep((g - 0.14f) / 0.35f))

        val c1 = Color.parseColor("#93C173")
        val c2 = Color.parseColor("#B7D58A")

        val wind = if (stillMode) 0f else
            sin((tWind * 2f * PI * 0.12f).toFloat()) * 0.18f * swayIntensity

        // EXACTLY 4 blades (what you want to keep)
        val blades = listOf(
            // tip pair
            Blade(parentU = 0.985f, color = c1, gate = tipGate, side = -1f),
            Blade(parentU = 0.985f, color = c2, gate = tipGate, side = +1f),

            // lower pair
            Blade(parentU = 0.80f, color = c2, gate = lowGate, side = -1f),
            Blade(parentU = 0.80f, color = c1, gate = lowGate, side = +1f),
        )

        for ((i, b) in blades.withIndex()) {
            val (x, y) = pointOnQuad(trunkGeom, b.parentU)
            val (tx, ty) = tangentOnQuad(trunkGeom, b.parentU)
            val branchAngleDeg = atan2(ty, tx) * 180f / Math.PI.toFloat()
            val a = (180f * b.gate * leafVis).toInt().coerceIn(0, 180)
            if (a <= 0) continue

            drawLeafAtTextured(
                canvas = canvas,
                x = x,
                y = y,
                sizePx = leafSize,
                branchAngleDeg = branchAngleDeg,
                sideSign = b.side,
                color = b.color,
                alpha = a,
                seedLocal = seed xor (0x9E37L + i.toLong() * 131L),
                wind = wind
            )
        }
    }

    private data class Blade(
        val parentU: Float,
        val color: Int,
        val gate: Float,
        val side: Float
    )

    // --------------------------------------------
    // GEOMETRY
    // --------------------------------------------
    private fun computeTrunkGeom(
        cx: Float,
        baseY: Float,
        topY: Float,
        trunkGrow: Float,
        trunkSway: Float,
        canopyR: Float
    ): BranchGeom {
        val curveDir = ((hash01(seed xor 0xA11CE) - 0.5f) * 2f).coerceIn(-1f, 1f)
        val curveAmt = canopyR * 0.10f * curveDir * (0.35f + 0.65f * trunkGrow)

        val x0 = cx
        val y0 = baseY
        val x1 = cx + curveAmt + trunkSway * canopyR * 0.6f
        val y1 = topY

        val cx1 = cx + curveAmt * 0.55f
        val cy1 = baseY - (baseY - topY) * 0.55f

        return BranchGeom(x0, y0, cx1, cy1, x1, y1, trunkGrow)
    }

    private fun computeBranchGeom(spec: BranchSpec, parent: BranchGeom?, canopyR: Float): BranchGeom? {
        if (parent == null) return null

        val grown = spec.grownP(growth)
        if (grown <= 0.001f) return null

        val (sx, sy) = pointOnQuad(parent, spec.parentU)

        val wind = if (stillMode) 0f else {
            sin((tWind * 2f * PI * 0.14f).toFloat() + spec.phase) * swayIntensity
        }

        val ang = spec.angleRad + wind * 0.020f * spec.side
        val len = canopyR * spec.lengthN * grown

        val dx = cos(ang) * len
        val dy = sin(ang) * len

        val x1 = sx + dx
        val y1 = sy + dy

        val cx1 = sx + dx * 0.52f + (-dy) * spec.curve * 0.18f
        val cy1 = sy + dy * 0.52f + (dx) * spec.curve * 0.18f

        return BranchGeom(sx, sy, cx1, cy1, x1, y1, grown)
    }

    private fun pointOnQuad(g: BranchGeom, u: Float): Pair<Float, Float> {
        val t = u.coerceIn(0f, 1f)
        val omt = 1f - t
        val x = omt * omt * g.x0 + 2f * omt * t * g.cx + t * t * g.x1
        val y = omt * omt * g.y0 + 2f * omt * t * g.cy + t * t * g.y1
        return x to y
    }

    private fun tangentOnQuad(g: BranchGeom, u: Float): Pair<Float, Float> {
        val t = u.coerceIn(0f, 1f)
        val dx = 2f * (1f - t) * (g.cx - g.x0) + 2f * t * (g.x1 - g.cx)
        val dy = 2f * (1f - t) * (g.cy - g.y0) + 2f * t * (g.y1 - g.cy)
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.0001f)
        return (dx / len) to (dy / len)
    }

    // --------------------------------------------
    // TRUNK DRAW + TEXTURE
    // --------------------------------------------
    private fun drawTrunk(canvas: Canvas, g: BranchGeom, trunkWNow: Float, trunkGrow: Float, canopyR: Float) {
        trunkPath.reset()

        val baseY = g.y0
        val topY = g.y1

        val halfBase = trunkWNow * (0.90f + 0.30f * trunkGrow)
        val halfTop = trunkWNow * 0.44f

        val cxBase = g.x0
        val cxTop = g.x1

        trunkPath.moveTo(cxBase - halfBase, baseY)

        trunkPath.cubicTo(
            cxBase - halfBase * 1.12f, baseY - trunkWNow,
            cxTop - halfTop, topY + trunkWNow,
            cxTop - halfTop, topY
        )

        tmpRect.set(
            cxTop - halfTop,
            topY - trunkWNow * 0.10f,
            cxTop + halfTop,
            topY + trunkWNow * 0.78f
        )
        trunkPath.arcTo(tmpRect, 180f, 180f, false)

        trunkPath.cubicTo(
            cxTop + halfTop, topY + trunkWNow,
            cxBase + halfBase * 1.12f, baseY - trunkWNow,
            cxBase + halfBase, baseY
        )
        trunkPath.close()

        val topC = Color.parseColor(BARK_TOP)
        val botC = Color.parseColor(BARK_BOT)

        trunkPaint.shader = LinearGradient(
            g.x1, topY,
            g.x0, baseY,
            topC, botC,
            Shader.TileMode.CLAMP
        )
        trunkPaint.alpha = 215
        canvas.drawPath(trunkPath, trunkPaint)
        trunkPaint.shader = null

        val barkAlpha = (10 + 18 * trunkGrow).toInt().coerceIn(10, 30)
        drawBarkTexture(canvas, trunkPath, seed xor BARK_MIX, barkAlpha, strokePx = trunkWNow * 0.05f)

        val haloA = (8 + 10 * trunkGrow).toInt().coerceIn(0, 22)
        if (haloA > 0) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = RadialGradient(
                    g.x1, g.y1,
                    canopyR * 0.25f,
                    Color.argb(haloA, 255, 255, 255),
                    Color.argb(0, 255, 255, 255),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(g.x1, g.y1, canopyR * 0.28f, p)
        }
    }

    private fun drawBarkTexture(canvas: Canvas, clipShape: Path, localSeed: Long, alpha: Int, strokePx: Float) {
        canvas.save()
        canvas.clipPath(clipShape)

        barkPaint.strokeWidth = strokePx.coerceAtLeast(0.8f)
        barkPaint.color = Color.argb(alpha.coerceIn(0, 40), 30, 22, 10)

        val lines = 16
        for (i in 0 until lines) {
            val x = (i - lines / 2f) * 18f + noise2(localSeed, i, 91) * 10f
            val tilt = noise2(localSeed, i, 92) * 14f
            canvas.drawLine(x, -4000f, x + tilt, 4000f, barkPaint)
        }

        leafDotPaint.color = Color.argb((alpha * 0.55f).toInt().coerceIn(0, 22), 25, 18, 10)
        val dots = 70
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in 0 until dots) {
            val px = (noise2(localSeed, i, 101) * w * 0.6f) + w * 0.5f
            val py = (noise2(localSeed, i, 102) * h * 0.6f) + h * 0.5f
            val rr = 0.6f + 1.4f * hash01(localSeed xor (i.toLong() * 31L))
            canvas.drawCircle(px, py, rr, leafDotPaint)
        }

        canvas.restore()
    }

    // --------------------------------------------
    // BRANCHES AS TUBES + BLEND COLLARS
    // --------------------------------------------
    private fun drawBranchesAsTubes(
        canvas: Canvas,
        specs: List<BranchSpec>,
        geoms: List<BranchGeom?>,
        canopyR: Float
    ) {
        val top = Color.parseColor(BARK_TOP)
        val bot = Color.parseColor(BARK_BOT)

        for (i in 1 until specs.size) {
            val br = specs[i]
            val g = geoms.getOrNull(i) ?: continue
            if (g.grownP <= 0.001f) continue

            val kindMul = when (br.kind) {
                BranchKind.PRIMARY -> 1.00f
                BranchKind.SECONDARY -> 0.78f
                BranchKind.TERTIARY -> 0.62f
                else -> 1f
            }

            val baseRadius = (canopyR * 0.030f) * kindMul * (0.78f + 0.22f * g.grownP)
            val wobblePx = (canopyR * 0.010f) * (0.35f + 0.65f * g.grownP)

            val tube = buildBranchTubePath(
                g = g,
                baseRadius = baseRadius,
                seedLocal = seed xor (i.toLong() * 1315423911L),
                wobblePx = wobblePx
            )

            branchPaint.shader = LinearGradient(g.x1, g.y1, g.x0, g.y0, top, bot, Shader.TileMode.CLAMP)
            branchPaint.alpha = (150 + 75 * g.grownP).toInt().coerceIn(0, 215)
            canvas.drawPath(tube, branchPaint)

            drawBranchCollar(canvas, g, baseRadius, top, bot)

            branchPaint.shader = null

            val barkA = (10 + 12 * g.grownP).toInt().coerceIn(8, 24)
            drawBarkTexture(canvas, tube, seed xor (i.toLong() * 0x9E37L), barkA, strokePx = baseRadius * 0.18f)
        }

        branchPaint.alpha = 255
    }

    private fun drawBranchCollar(canvas: Canvas, g: BranchGeom, baseRadius: Float, top: Int, bot: Int) {
        val r = baseRadius * 1.18f
        if (r <= 0.5f) return

        val collarPaint = branchPaint
        collarPaint.shader = RadialGradient(
            g.x0, g.y0,
            r * 1.25f,
            intArrayOf(
                ColorUtils.setAlphaComponent(top, 160),
                ColorUtils.setAlphaComponent(bot, 0)
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        collarPaint.alpha = 200
        canvas.drawCircle(g.x0, g.y0, r, collarPaint)
        collarPaint.shader = null
    }

    private fun sampleQuad(g: BranchGeom, steps: Int): FloatArray {
        val out = FloatArray((steps + 1) * 2)
        for (i in 0..steps) {
            val u = i / steps.toFloat()
            val omt = 1f - u
            val x = omt * omt * g.x0 + 2f * omt * u * g.cx + u * u * g.x1
            val y = omt * omt * g.y0 + 2f * omt * u * g.cy + u * u * g.y1
            out[i * 2] = x
            out[i * 2 + 1] = y
        }
        return out
    }

    private fun buildBranchTubePath(
        g: BranchGeom,
        baseRadius: Float,
        seedLocal: Long,
        wobblePx: Float
    ): Path {
        val steps = 16
        val pts = sampleQuad(g, steps)

        val left = ArrayList<PointF>(steps + 1)
        val right = ArrayList<PointF>(steps + 1)

        for (i in 0..steps) {
            val x = pts[i * 2]
            val y = pts[i * 2 + 1]

            val i0 = max(0, i - 1)
            val i1 = min(steps, i + 1)
            val x0 = pts[i0 * 2]
            val y0 = pts[i0 * 2 + 1]
            val x1 = pts[i1 * 2]
            val y1 = pts[i1 * 2 + 1]

            var tx = x1 - x0
            var ty = y1 - y0
            val tLen = sqrt(tx * tx + ty * ty).coerceAtLeast(0.0001f)
            tx /= tLen
            ty /= tLen

            val nx = -ty
            val ny = tx

            val midW = sin((i / steps.toFloat()) * Math.PI).toFloat()
            val wob = wobblePx * midW * noise2(seedLocal, i, 7)

            val wx = nx * wob
            val wy = ny * wob

            val tt = i / steps.toFloat()
            val radius = baseRadius * (1f - 0.70f * tt) * (0.75f + 0.25f * g.grownP)

            val asym = 1f + 0.12f * noise2(seedLocal, i, 11)
            val rL = radius * asym
            val rR = radius * (2f - asym)

            left += PointF(x + wx + nx * rL, y + wy + ny * rL)
            right += PointF(x + wx - nx * rR, y + wy - ny * rR)
        }

        return Path().apply {
            moveTo(left[0].x, left[0].y)
            for (i in 1 until left.size) lineTo(left[i].x, left[i].y)
            for (i in right.size - 1 downTo 0) lineTo(right[i].x, right[i].y)
            close()
        }
    }

    // --------------------------------------------
    // LEAVES
    // --------------------------------------------
    private fun drawLeavesPaired(canvas: Canvas, geoms: List<BranchGeom?>, canopyR: Float) {
        val g = growth.coerceIn(0f, 1f)

        // Start canopy leaves after sapling blades have done their job
        val globalLeafGate = smoothstep(((g - 0.40f) / 0.60f).coerceIn(0f, 1f))
        if (globalLeafGate <= 0.001f) return

        val baseLeaf = canopyR * (0.105f + 0.020f * globalLeafGate)

        for ((idx, leaf) in leaves.withIndex()) {
            if (g < leaf.appearG) continue

            val geom = geoms.getOrNull(leaf.branchIndex) ?: continue
            if (geom.grownP <= 0.001f) continue

            val u = (leaf.u * geom.grownP).coerceIn(0f, 1f)
            val (x0, y0) = pointOnQuad(geom, u)

            val (tx, ty) = tangentOnQuad(geom, u)
            val nx = -ty
            val ny = tx

            val normal = leaf.normalAbs * leaf.sideSign
            val ox = nx * canopyR * normal
            val oy = ny * canopyR * normal

            val micro = if (stillMode) 0f else sin((tWind * 2f * PI * 0.18f).toFloat() + leaf.phase)
            val microX = canopyR * 0.010f * micro * leaf.edgeW
            val microY = canopyR * 0.006f * micro * leaf.edgeW

            val branchAngleDeg = atan2(ty, tx) * 180f / Math.PI.toFloat()

            // ---- UPDATED ALPHA (less fog, more readable) ----
            val a = ((g - leaf.appearG) / 0.12f).coerceIn(0f, 1f)

            // inner leaves more opaque, outer edge slightly lighter
            val innerBoost = (1.10f - 0.18f * leaf.edgeW).coerceIn(0.90f, 1.15f)

            val alphaF = (145f + 95f * (a * globalLeafGate)) * innerBoost
            val alpha = alphaF.toInt().coerceIn(0, 245)
            // -----------------------------------------------

            val edgeBoost = 0.92f + 0.35f * leaf.edgeW
            val size = baseLeaf * leaf.sizeMul * edgeBoost * (0.65f + 0.35f * globalLeafGate)

            val wind = if (stillMode) 0f else micro * 0.20f * swayIntensity

            drawLeafAtTextured(
                canvas,
                x = x0 + ox + microX,
                y = y0 + oy + microY,
                sizePx = size,
                branchAngleDeg = branchAngleDeg,
                sideSign = leaf.sideSign,
                color = leaf.color,
                alpha = alpha,
                seedLocal = seed xor (leaf.branchIndex.toLong() * 1315423911L) xor (idx.toLong() * 31L),
                wind = wind
            )
        }
    }

    private fun drawLeafAtTextured(
        canvas: Canvas,
        x: Float,
        y: Float,
        sizePx: Float,
        branchAngleDeg: Float,
        sideSign: Float,
        color: Int,
        alpha: Int,
        seedLocal: Long,
        wind: Float
    ) {
        val tilt = noise2(seedLocal, 2, 8) * 18f
        val rot = branchAngleDeg + (if (sideSign > 0f) -90f else 90f) + tilt + wind * 10f

        leafPaint.color = color
        leafPaint.alpha = alpha

        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rot)
        canvas.scale(sizePx, sizePx)
        canvas.drawPath(leafPath, leafPaint)

        val veinA = (alpha * 0.22f).toInt().coerceIn(0, 30)
        veinPaint.color = Color.argb(veinA, 255, 255, 255)
        veinPaint.strokeWidth = 0.06f
        val wob = noise2(seedLocal, 3, 9) * 0.12f
        canvas.drawLine(0f, -0.40f, wob, 0.35f, veinPaint)

        val dotA = (alpha * 0.10f).toInt().coerceIn(0, 18)
        if (dotA > 0) {
            leafDotPaint.color = Color.argb(dotA, 255, 255, 255)
            val dx = noise2(seedLocal, 5, 12) * 0.10f
            val dy = noise2(seedLocal, 6, 13) * 0.10f
            canvas.drawCircle(dx, dy, 0.10f, leafDotPaint)
        }

        canvas.restore()
    }

    private fun drawSoftLeafHighlight(canvas: Canvas, canopyR: Float) {
        val g = growth.coerceIn(0f, 1f)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w * 0.5f
        val cy = h * 0.43f

        val alpha = (6f + 10f * g).toInt().coerceIn(0, 18)

        highlightPaint.shader = RadialGradient(
            cx, cy,
            canopyR * 0.95f,
            intArrayOf(Color.argb(alpha, 255, 255, 255), Color.argb(0, 255, 255, 255)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, canopyR * 1.02f, highlightPaint)
        highlightPaint.shader = null
    }

    // --------------------------------------------
    // LEAF SHAPE
    // --------------------------------------------
    private fun buildLeafShape() {
        leafPath.reset()
        leafPath.moveTo(0f, -0.62f)
        leafPath.cubicTo(0.42f, -0.50f, 0.56f, -0.10f, 0.30f, 0.22f)
        leafPath.cubicTo(0.18f, 0.48f, 0.06f, 0.62f, 0f, 0.66f)
        leafPath.cubicTo(-0.12f, 0.62f, -0.30f, 0.46f, -0.40f, 0.20f)
        leafPath.cubicTo(-0.56f, -0.14f, -0.34f, -0.52f, 0f, -0.62f)
        leafPath.close()
    }

    // --------------------------------------------
    // BUILD SPECS (STABLE, NOT STAGE-DEPENDENT)
    // --------------------------------------------
    private fun buildBranchSpecs(seed: Long): List<BranchSpec> {
        val r = java.util.Random(seed)
        fun rf(a: Float, b: Float) = a + r.nextFloat() * (b - a)
        fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

        val minAng = (-Math.PI.toFloat() + Math.toRadians(4.0).toFloat())
        val maxAng = (0f - Math.toRadians(4.0).toFloat())
        val upAng = (-Math.PI.toFloat() / 2f)

        val specs = ArrayList<BranchSpec>()

        // TRUNK
        specs += BranchSpec(
            kind = BranchKind.TRUNK,
            parentIndex = -1,
            parentU = 0f,
            spawnG = 0f,
            growSpan = 0.45f,
            side = 0f,
            angleRad = upAng,
            lengthN = 1.00f,
            curve = rf(-0.06f, 0.06f),
            taper = 0.55f,
            nodeCountPairs = 0,
            phase = rf(0f, (2f * Math.PI).toFloat())
        )

        // Growth bands (global):
        // primary: 0.18..0.48
        // secondary: 0.45..0.72
        // tertiary: 0.72..0.95
        val primarySpawn = 0.18f
        val primarySpan = 0.30f

        val secondarySpawn = 0.45f
        val secondarySpan = 0.27f

        val tertiarySpawn = 0.72f
        val tertiarySpan = 0.23f

        val heights = floatArrayOf(
            0.56f + rf(-0.01f, 0.01f),
            0.62f + rf(-0.01f, 0.01f),
            0.68f + rf(-0.01f, 0.01f),
            0.74f + rf(-0.01f, 0.01f)
        )

        val angles = floatArrayOf(
            (-Math.toRadians(140.0)).toFloat(),
            (-Math.toRadians(110.0)).toFloat(),
            (-Math.toRadians(70.0)).toFloat(),
            (-Math.toRadians(40.0)).toFloat()
        )

        // PRIMARY (4)
        for (i in 0 until 4) {
            val attachU = heights[i].coerceIn(0.48f, 0.82f)

            var ang = angles[i] + rf(-0.05f, 0.05f)
            ang = ang.coerceIn(minAng, maxAng)

            val edgeBoost = 1f + 0.14f * abs(i / 3f - 0.5f) * 2f
            val length = (0.95f - attachU * 0.30f) * rf(1.00f, 1.12f) * edgeBoost
            val side = if (cos(ang) < 0f) -1f else +1f

            specs += BranchSpec(
                kind = BranchKind.PRIMARY,
                parentIndex = 0,
                parentU = attachU,
                spawnG = primarySpawn,
                growSpan = primarySpan,
                side = side,
                angleRad = ang,
                lengthN = length.coerceIn(0.42f, 1.05f),
                curve = rf(0.12f, 0.26f) * side,
                taper = 0.62f,

                // UPDATED: fewer leaf pairs (was 7)
                nodeCountPairs = 4,

                nodeStartU = 0.22f,
                nodeEndU = 0.98f,
                phase = rf(0f, (2f * Math.PI).toFloat())
            )
        }

        // SECONDARY
        run {
            val baseSec = 7
            val lastIndex = specs.lastIndex
            for (pi in 1..lastIndex) {
                val parent = specs[pi]
                if (parent.kind != BranchKind.PRIMARY) continue

                val isRight = cos(parent.angleRad) > 0f
                val secPerPrimary = baseSec + if (isRight) 2 else 1

                for (k in 0 until secPerPrimary) {
                    val t = if (secPerPrimary == 1) 0f else k / (secPerPrimary - 1f).toFloat()
                    val lowBias = t.pow(1.75f)
                    val attachU = (lerp(0.12f, 0.72f, lowBias) + rf(-0.02f, 0.02f))
                        .coerceIn(0.10f, 0.80f)

                    val lowerFactor = (1f - attachU).coerceIn(0f, 1f)
                    val len = (parent.lengthN * (0.70f + 0.65f * lowerFactor) * rf(0.78f, 1.08f))
                        .coerceIn(0.32f, 0.98f)

                    val flare = 0.55f + 0.40f * lowerFactor
                    var ang = parent.angleRad + rf(-1.00f, 1.00f) * flare
                    ang = lerp(ang, upAng, 0.10f + 0.08f * (1f - lowerFactor))
                    ang = ang.coerceIn(minAng, maxAng)

                    val side = if (cos(ang) < 0f) -1f else +1f

                    specs += BranchSpec(
                        kind = BranchKind.SECONDARY,
                        parentIndex = pi,
                        parentU = attachU,
                        spawnG = secondarySpawn,
                        growSpan = secondarySpan,
                        side = side,
                        angleRad = ang,
                        lengthN = len,
                        curve = rf(0.10f, 0.22f) * side,
                        taper = 0.74f,

                        // UPDATED: fewer leaf pairs (was 5)
                        nodeCountPairs = 3,

                        nodeStartU = 0.24f,
                        nodeEndU = 0.98f,
                        phase = rf(0f, (2f * Math.PI).toFloat())
                    )
                }
            }
        }

        // TERTIARY
        run {
            val baseTert = 7
            val lastIndex = specs.lastIndex
            for (si in 1..lastIndex) {
                val parent = specs[si]
                if (parent.kind != BranchKind.SECONDARY) continue

                val isRight = cos(parent.angleRad) > 0f
                val tertPerSecondary = baseTert + if (isRight) 2 else 1

                for (k in 0 until tertPerSecondary) {
                    val t = if (tertPerSecondary == 1) 0f else k / (tertPerSecondary - 1f).toFloat()
                    val lowBias = t.pow(1.55f)
                    val attachU = (lerp(0.16f, 0.92f, lowBias) + rf(-0.02f, 0.02f))
                        .coerceIn(0.14f, 0.96f)

                    val lowerFactor = (1f - attachU).coerceIn(0f, 1f)
                    val len = (parent.lengthN * (0.28f + 0.22f * lowerFactor) * rf(0.70f, 1.10f))
                        .coerceIn(0.09f, 0.28f)

                    val twigSpread = 0.45f + 0.30f * lowerFactor
                    var ang = parent.angleRad + rf(-1.15f, 1.15f) * twigSpread
                    ang = lerp(ang, upAng, 0.20f)
                    ang = ang.coerceIn(minAng, maxAng)

                    val side = if (cos(ang) < 0f) -1f else +1f

                    specs += BranchSpec(
                        kind = BranchKind.TERTIARY,
                        parentIndex = si,
                        parentU = attachU,
                        spawnG = tertiarySpawn,
                        growSpan = tertiarySpan,
                        side = side,
                        angleRad = ang,
                        lengthN = len,
                        curve = rf(0.05f, 0.13f) * side,
                        taper = 0.84f,

                        // UPDATED: fewer leaf pairs (was 3)
                        nodeCountPairs = 2,

                        nodeStartU = 0.30f,
                        nodeEndU = 0.98f,
                        phase = rf(0f, (2f * Math.PI).toFloat())
                    )
                }
            }
        }

        return specs
    }

    // --------------------------------------------
    // BUILD LEAVES (NO SAPLING CANOPY; starts later)
    // --------------------------------------------
    private fun buildLeafNodesPaired(branches: List<BranchSpec>, seed: Long): List<LeafNode> {
        val mix = 0x3E3779B97F4A7C15L
        val r = java.util.Random(seed xor mix)
        fun rf(a: Float, b: Float) = a + r.nextFloat() * (b - a)

        // palette
        val cLight = Color.parseColor("#B7D58A")
        val cMid = Color.parseColor("#93C173")
        val cDeep = Color.parseColor("#6F9B72") // deeper for inner mass

        // edgeW: 0.55(inner-ish) -> 1.0(edge)
        fun pickColor(edgeW: Float): Int {
            val p = r.nextFloat()
            return if (edgeW < 0.72f) {
                // inner canopy: darker + more stable
                when {
                    p < 0.55f -> cDeep
                    p < 0.90f -> cMid
                    else -> cLight
                }
            } else {
                // outer edge: lighter and fresher
                when {
                    p < 0.55f -> cLight
                    p < 0.92f -> cMid
                    else -> cDeep
                }
            }
        }

        val out = ArrayList<LeafNode>()

        for ((bi, br) in branches.withIndex()) {
            if (br.nodeCountPairs <= 0) continue

            val pairCount = br.nodeCountPairs.coerceAtLeast(2)
            val startU = br.nodeStartU.coerceIn(0f, 1f)
            val endU = br.nodeEndU.coerceIn(0f, 1f)

            // tip leaf (kept)
            run {
                val tipU = endU.coerceAtMost(0.99f)
                val tipAppear = (br.spawnG + br.growSpan * 0.24f + rf(0.00f, 0.03f)).coerceIn(0f, 1f)

                out += LeafNode(
                    branchIndex = bi,
                    u = tipU,
                    sideSign = +1f,
                    normalAbs = 0f,
                    sizeMul = rf(1.06f, 1.18f),
                    appearG = tipAppear,
                    edgeW = 1f,
                    phase = rf(0f, (2f * Math.PI).toFloat()),
                    color = pickColor(1f)
                )
            }

            for (k in 0 until pairCount) {
                val t = if (pairCount == 1) 0f else k / (pairCount - 1f).toFloat()
                val u = (endU - 0.06f - (endU - startU) * t).coerceIn(0f, 0.99f)

                // Smaller + calmer offsets = less “scatter”
                val sizeMul = (1.02f - 0.18f * t) * rf(0.96f, 1.06f)

                // Reduce normal offset so leaves follow branch direction better
                val normalAbs = rf(0.022f, 0.040f) * (0.92f + 0.18f * (1f - t))

                // edgeW acts like a canopy edge measure
                val edgeW = (0.55f + 0.45f * t)

                // Slightly slower appearance to avoid “instant bush”
                val early = 0.22f + 0.24f * t
                val appear = (br.spawnG + br.growSpan * early + rf(0.00f, 0.04f)).coerceIn(0f, 1f)

                // + side
                out += LeafNode(
                    branchIndex = bi,
                    u = u,
                    sideSign = +1f,
                    normalAbs = normalAbs,
                    sizeMul = sizeMul,
                    appearG = appear,
                    edgeW = edgeW,
                    phase = rf(0f, (2f * Math.PI).toFloat()),
                    color = pickColor(edgeW)
                )

                // - side (tiny asymmetry)
                out += LeafNode(
                    branchIndex = bi,
                    u = u,
                    sideSign = -1f,
                    normalAbs = normalAbs,
                    sizeMul = sizeMul * rf(0.98f, 1.04f),
                    appearG = (appear + rf(0.00f, 0.02f)).coerceIn(0f, 1f),
                    edgeW = edgeW,
                    phase = rf(0f, (2f * Math.PI).toFloat()),
                    color = pickColor(edgeW)
                )
            }
        }

        return out
    }

    // --------------------------------------------
    // EASING + NOISE
    // --------------------------------------------
    private fun slowEnd(x: Float, tail: Float): Float {
        val t = x.coerceIn(0f, 1f)
        if (tail <= 1f) return t
        val k = (tail - 1f).coerceIn(0f, 6f)
        val slow = t.pow(1f + k * 0.35f)
        return (0.45f * t + 0.55f * slow).coerceIn(0f, 1f)
    }

    private fun hash01(v: Long): Float {
        var x = v
        x = x xor (x ushr 33)
        x *= -0xae502812aa7333L
        x = x xor (x ushr 33)
        x *= -0x3b314601e57a13adL
        x = x xor (x ushr 33)
        val n = (x ushr 40).toInt() and 0xFFFF
        return n / 65535f
    }

    private fun hashSigned(v: Long): Float = hash01(v) * 2f - 1f

    private fun noise2(seed: Long, i: Int, j: Int): Float {
        val a = 0x3E3779B97F4A7C15L
        val b = 0x32B2AE3D27D4EB4FL
        val h = seed xor (i.toLong() * a) xor (j.toLong() * b)
        return hashSigned(h)
    }
}

// tiny helper without importing androidx.core.graphics.ColorUtils
private object ColorUtils {
    fun setAlphaComponent(color: Int, alpha: Int): Int {
        val a = alpha.coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }
}