//JournalHistoryAdapter.kt
package com.aninaw

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JournalHistoryAdapter(
    private var items: List<JournalHistoryItem>,
    private val onItemClick: (JournalHistoryItem.Entry) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ENTRY = 1
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMonthHeader: TextView = view.findViewById(R.id.tvMonthHeader)
    }

    class EntryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvEntryTitle: TextView = view.findViewById(R.id.tvEntryTitle)
        val tvEntryMeta: TextView = view.findViewById(R.id.tvEntryMeta)
        val tvEntryPreview: TextView = view.findViewById(R.id.tvEntryPreview)
        val tvEntryType: TextView = view.findViewById(R.id.tvEntryType)
        val tvMood: TextView = view.findViewById(R.id.tvMood)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is JournalHistoryItem.Header -> TYPE_HEADER
            is JournalHistoryItem.Entry -> TYPE_ENTRY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_journal_month_header, parent, false)
                HeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_journal_history, parent, false)
                EntryViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is JournalHistoryItem.Header -> {
                (holder as HeaderViewHolder).tvMonthHeader.text = item.title
            }

            is JournalHistoryItem.Entry -> {
                holder as EntryViewHolder

                holder.tvEntryTitle.text = item.title
                holder.tvEntryMeta.text = formatTimestamp(item.timestamp)
                holder.tvEntryPreview.text = item.content
                holder.tvEntryType.text = mapTypeLabel(item.type)

                if (item.mood.isNullOrBlank()) {
                    holder.tvMood.visibility = View.GONE
                } else {
                    holder.tvMood.visibility = View.VISIBLE
                    holder.tvMood.text = item.mood
                }

                holder.itemView.setOnClickListener {
                    onItemClick(item)
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<JournalHistoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMMM d, yyyy • h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun mapTypeLabel(type: String): String {
        return when (type.uppercase(Locale.getDefault())) {
            "MORNING" -> "Morning"
            "QUICK" -> "Pause"
            "INTENTION" -> "Intention"
            "EVENING" -> "Evening"
            "FREESTYLE" -> "Freestyle"
            else -> type.replaceFirstChar { it.uppercase() }
        }
    }
}