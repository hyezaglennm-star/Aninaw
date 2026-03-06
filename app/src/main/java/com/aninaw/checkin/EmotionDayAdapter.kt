//EmotionDayAdapter.kt
package com.aninaw.checkin


import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aninaw.R
import kotlin.math.roundToInt

class EmotionDayAdapter(
    private val onDayClick: (index: Int, record: DailyEmotionRecord) -> Unit
) : ListAdapter<DailyEmotionRecord, EmotionDayAdapter.VH>(DIFF) {

    private var selectedIndex: Int = 0

    fun setSelected(index: Int) {
        val old = selectedIndex
        selectedIndex = index.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
        if (old != selectedIndex) {
            notifyItemChanged(old)
            notifyItemChanged(selectedIndex)
        }
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val pill: View = itemView.findViewById(R.id.vPill)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_emotion_day, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val rec = getItem(position)

        val prev = getItemOrNull(position - 1)
        val next = getItemOrNull(position + 1)

        holder.pill.background = makeDayDrawable(
            record = rec,
            prev = prev,
            next = next,
            density = holder.pill.resources.displayMetrics.density,
            isSelected = (position == selectedIndex)
        )

        holder.itemView.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) {
                setSelected(p)
                onDayClick(p, getItem(p))
            }
        }
    }

    private fun getItemOrNull(pos: Int): DailyEmotionRecord? =
        if (pos in 0 until itemCount) getItem(pos) else null

    private fun makeDayDrawable(
        record: DailyEmotionRecord,
        prev: DailyEmotionRecord?,
        next: DailyEmotionRecord?,
        density: Float,
        isSelected: Boolean
    ): GradientDrawable {
        val r = 999f * density

        val primary = record.primary
        val secondary = record.secondary

        // No entry = soft neutral fog
        if (primary == null) {
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = r
                setColor(Color.argb(35, 255, 255, 255))
                if (isSelected) setStroke((density * 2f).roundToInt(), Color.argb(90, 120, 90, 60))
            }
        }

        val center = colorFor(primary, 190)

        // give each day a "blend" hint (secondary influences center a bit)
        val centerTweaked =
            if (secondary != null && secondary != primary) blend(colorFor(secondary, 150), center, 0.45f)
            else center

        // Smooth neighbor transition: left edge leans to prev primary, right edge leans to next primary
        val left = prev?.primary?.let { colorFor(it, 150) } ?: centerTweaked
        val right = next?.primary?.let { colorFor(it, 150) } ?: centerTweaked

        return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(left, centerTweaked, right)).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = r
            if (isSelected) setStroke((density * 2f).roundToInt(), Color.argb(110, 120, 90, 60))
        }
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        val ar = Color.red(a); val ag = Color.green(a); val ab = Color.blue(a); val aa = Color.alpha(a)
        val br = Color.red(b); val bg = Color.green(b); val bb = Color.blue(b); val ba = Color.alpha(b)
        return Color.argb(
            (aa + (ba - aa) * tt).roundToInt(),
            (ar + (br - ar) * tt).roundToInt(),
            (ag + (bg - ag) * tt).roundToInt(),
            (ab + (bb - ab) * tt).roundToInt()
        )
    }

    private fun colorFor(e: Emotion, alpha: Int): Int {
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
            Emotion.HAPPY -> Triple(255, 213, 79)
            Emotion.SHY -> Triple(244, 143, 177)
            Emotion.NEUTRAL -> Triple(165, 214, 167)
            Emotion.ANXIOUS -> Triple(255, 204, 128)
            Emotion.SAD -> Triple(144, 202, 249)
        }
        return Color.argb(alpha.coerceIn(0, 255), r, g, b)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DailyEmotionRecord>() {
            override fun areItemsTheSame(oldItem: DailyEmotionRecord, newItem: DailyEmotionRecord): Boolean =
                oldItem.ymd == newItem.ymd

            override fun areContentsTheSame(oldItem: DailyEmotionRecord, newItem: DailyEmotionRecord): Boolean =
                oldItem == newItem
        }
    }
}
