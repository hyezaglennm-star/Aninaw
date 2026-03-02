package com.aninaw.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class SpotlightHoleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B3000000") // ~70% black
        style = Paint.Style.FILL
    }

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val holeRect = RectF()
    private var cornerRadiusPx = 40f
    private var holePaddingPx = 18f

    fun setHole(target: RectF, paddingPx: Float = holePaddingPx, radiusPx: Float = cornerRadiusPx) {
        holePaddingPx = paddingPx
        cornerRadiusPx = radiusPx
        holeRect.set(
            target.left - holePaddingPx,
            target.top - holePaddingPx,
            target.right + holePaddingPx,
            target.bottom + holePaddingPx
        )
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Use a saved layer so CLEAR works reliably
        val checkpoint = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        // Draw dim scrim
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        // Punch a rounded hole
        val r = max(0f, cornerRadiusPx)
        canvas.drawRoundRect(holeRect, r, r, clearPaint)

        canvas.restoreToCount(checkpoint)
    }
}