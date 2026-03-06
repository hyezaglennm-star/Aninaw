package com.aninaw

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class TreeRingAdapter(
    private var memories: List<DailyMemory> = emptyList(),
    private val onItemClick: (DailyMemory) -> Unit
) : RecyclerView.Adapter<TreeRingAdapter.ViewHolder>() {

    private val dayFormatter = DateTimeFormatter.ofPattern("d", Locale.getDefault())

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDayNumber: TextView = itemView.findViewById(R.id.tvDayNumber)
        val dateContainer: View = itemView.findViewById(R.id.dateContainer)

        fun bind(memory: DailyMemory) {
            val date = LocalDate.parse(memory.dateIso)
            tvDayNumber.text = date.format(dayFormatter)

            val colorRes = if (memory.hasCheckIn) {
                getColorResForEmotion(memory.emotion)
            } else {
                R.color.aninaw_bg_bottom // Neutral
            }
            
            val color = ContextCompat.getColor(itemView.context, colorRes)
            dateContainer.backgroundTintList = ColorStateList.valueOf(color)

            // White text for filled days, gray for empty
            if (memory.hasCheckIn) {
                tvDayNumber.alpha = 1.0f
                tvDayNumber.setTextColor(Color.WHITE)
            } else {
                tvDayNumber.alpha = 0.4f
                tvDayNumber.setTextColor(Color.parseColor("#3A312B"))
            }

            itemView.setOnClickListener { onItemClick(memory) }
        }
    }

    private fun getColorResForEmotion(label: String?): Int {
        val e = (label ?: "").trim().lowercase()
        return when {
            // Positive / High Energy
            e.contains("joy") || e.contains("happy") -> R.color.emotion_joyful
            e.contains("cite") -> R.color.emotion_excited
            e.contains("grate") -> R.color.emotion_grateful
            e.contains("love") -> R.color.emotion_grateful
            e.contains("energy") || e.contains("gized") -> R.color.emotion_energized
            
            // Calm / Grounded
            e.contains("calm") -> R.color.emotion_bored // Green
            e.contains("steady") -> R.color.emotion_bored
            e.contains("okay") -> R.color.emotion_bored
            
            // Low Energy / Heavy
            e.contains("sad") -> R.color.emotion_confused // Blue
            e.contains("heavy") -> R.color.emotion_confused
            e.contains("tired") -> R.color.emotion_sensitive // Light Blue
            e.contains("fog") -> R.color.emotion_sensitive
            e.contains("numb") -> R.color.emotion_sensitive
            
            // High Energy / Negative
            e.contains("anx") -> R.color.emotion_insecure // Orange
            e.contains("tense") -> R.color.emotion_stressed // Teal
            e.contains("stress") -> R.color.emotion_stressed
            e.contains("angr") -> R.color.emotion_angry // Red
            e.contains("hurt") -> R.color.emotion_hurt // Amber
            e.contains("guilt") -> R.color.emotion_guilty // Yellow
            
            // Default
            else -> R.color.hs_orange_light
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tree_ring_memory, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(memories[position])
    }

    override fun getItemCount(): Int = memories.size

    fun submitList(newMemories: List<DailyMemory>) {
        memories = newMemories
        notifyDataSetChanged()
    }
}
