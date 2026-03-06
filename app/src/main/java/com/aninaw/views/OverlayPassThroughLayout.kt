package com.aninaw.views

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView

/**
 * A Layout that only dispatches touch events to its children if the touch
 * explicitly hits a clickable view item (e.g. a ViewHolder in a RecyclerView).
 * Otherwise, it returns false, allowing the event to pass through to views behind it.
 */
class OverlayPassThroughLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val viewLocation = IntArray(2)

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // If the action is DOWN, we decide whether to consume the stream or pass it through.
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            if (!hitTest(this, ev.rawX, ev.rawY)) {
                return false
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun hitTest(view: View, rawX: Float, rawY: Float): Boolean {
        if (view.visibility != View.VISIBLE) return false

        // Get view bounds
        view.getLocationOnScreen(viewLocation)
        val left = viewLocation[0]
        val top = viewLocation[1]
        val right = left + view.width
        val bottom = top + view.height

        // Check if point is inside view
        if (rawX < left || rawX > right || rawY < top || rawY > bottom) {
            return false
        }

        // For RecyclerView, check if we hit any child (item)
        if (view is RecyclerView) {
            for (i in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(i)
                if (hitTest(child, rawX, rawY)) {
                    return true
                }
            }
            // If we hit the RecyclerView but not a child, pass through
            return false
        }
        
        // For other ViewGroups, recurse
        if (view is ViewGroup) {
             for (i in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(i)
                if (hitTest(child, rawX, rawY)) {
                    return true
                }
            }
            // If it's this layout itself, we don't count it as a "hit" unless a child is hit
            if (view == this) return false
            
            // For other containers, we assume they are transparent unless they have a background or listener?
            // Simplified: if it's not a RecyclerView and not this layout, we treat it as solid for now.
            // But wait, the FrameLayout inside might be blocking too.
            // Let's be aggressive: only leaf views or specific interactive views count.
            return false
        }

        // Leaf view (e.g. TextView inside item)
        return true
    }
}
