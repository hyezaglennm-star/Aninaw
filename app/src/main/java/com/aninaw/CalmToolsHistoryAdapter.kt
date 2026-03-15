package com.aninaw

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CalmToolsHistoryAdapter(
    private var items: List<CalmToolHistoryItem>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ENTRY = 1
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvHeader: TextView = view.findViewById(R.id.tvHistoryHeader)
    }

    class EntryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivToolIcon)
        val tvTitle: TextView = view.findViewById(R.id.tvToolTitle)
        val tvTime: TextView = view.findViewById(R.id.tvToolTime)
        val tvMeta: TextView = view.findViewById(R.id.tvToolMeta)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is CalmToolHistoryItem.Header -> TYPE_HEADER
            is CalmToolHistoryItem.Entry -> TYPE_ENTRY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_calm_tools_history_header, parent, false)
                HeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_calm_tool_history, parent, false)
                EntryViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is CalmToolHistoryItem.Header -> {
                (holder as HeaderViewHolder).tvHeader.text = item.title
            }

            is CalmToolHistoryItem.Entry -> {
                holder as EntryViewHolder

                holder.tvTitle.text = item.toolTitle
                holder.tvTime.text = formatTimestamp(item.completedAt)
                holder.tvMeta.text = buildMetaText(item.durationSeconds, item.completionState)
                holder.ivIcon.setImageResource(getToolIcon(item.toolType))
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<CalmToolHistoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMMM d, yyyy • h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun buildMetaText(durationSeconds: Int?, completionState: String): String {
        val durationText = when {
            durationSeconds == null -> ""
            durationSeconds < 60 -> "$durationSeconds sec"
            else -> "${durationSeconds / 60} min"
        }

        return when {
            durationText.isNotBlank() && completionState.isNotBlank() -> "• $durationText • ${completionState.replaceFirstChar { it.uppercase() }}"
            durationText.isNotBlank() -> "• $durationText"
            completionState.isNotBlank() -> "• ${completionState.replaceFirstChar { it.uppercase() }}"
            else -> ""
        }
    }

    private fun getToolIcon(toolType: String): Int {
        return when (toolType.lowercase(Locale.getDefault())) {
            "stretch" -> R.drawable.ic_stretch
            "grounding" -> R.drawable.ic_ground
            "breathing" -> R.drawable.ic_breath
            "journal" -> R.drawable.ic_journal
            "cbt" -> R.drawable.ic_reflect
            else -> R.drawable.ic_reflect
        }
    }
}