package com.aninaw

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aninaw.data.AninawDb
import com.aninaw.data.treering.TreeRingMemoryEntity
import com.aninaw.data.treering.TreeRingMemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class TreeRingDetailFragment : DialogFragment() {

    private lateinit var rvLogs: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var tvEmptyState: TextView
    private lateinit var btnClose: ImageButton

    private var dateIso: String = ""
    private var allLogs: List<TreeRingMemoryEntity> = emptyList()
    private val adapter = LogAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make it full screen
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_tree_ring_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        dateIso = arguments?.getString(ARG_DATE) ?: LocalDate.now().toString()

        rvLogs = view.findViewById(R.id.rvLogs)
        etSearch = view.findViewById(R.id.etSearch)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)
        btnClose = view.findViewById(R.id.btnClose)

        rvLogs.layoutManager = LinearLayoutManager(context)
        rvLogs.adapter = adapter

        btnClose.setOnClickListener { dismiss() }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterLogs(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        loadLogs()
    }

    private fun loadLogs() {
        val context = context ?: return
        val db = AninawDb.getDatabase(context)
        // TreeRingMemoryRepository constructor requires AninawDb instance?
        // Based on previous read, it takes 'db'.
        // If it took a Dao, I would need to get it.
        // Assuming: class TreeRingMemoryRepository(private val db: AninawDb)
        val repo = TreeRingMemoryRepository(db)

        lifecycleScope.launch {
            val logs = withContext(Dispatchers.IO) {
                try {
                    // Load last 180 days (matching the tree ring visual range)
                    val end = LocalDate.now()
                    val start = end.minusDays(180)
                    repo.getRange(start, end)
                } catch (e: Exception) {
                    emptyList()
                }
            }
            // Sort by date descending (newest first)
            allLogs = logs.sortedByDescending { it.timestamp }
            updateList(allLogs)
            
            // Scroll to the requested date if it exists
            scrollToDate(dateIso)
        }
    }

    private fun scrollToDate(targetIso: String) {
        val index = allLogs.indexOfFirst { it.date == targetIso }
        if (index != -1) {
            // Found exact match
            (rvLogs.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(index, 0)
        } else {
            // Find closest date? Or just stay at top?
            // If the user clicked an empty day, maybe show "No logs for [Date]"?
            // But since we are showing a feed, maybe just scrolling to the nearest date is fine.
            // For now, let's just stay at the top (latest) if the specific date isn't found.
        }
    }

    private fun filterLogs(query: String) {
        if (query.isBlank()) {
            updateList(allLogs)
        } else {
            val filtered = allLogs.filter { log ->
                (log.note?.contains(query, ignoreCase = true) == true) ||
                (log.emotion?.contains(query, ignoreCase = true) == true) ||
                (log.type.contains(query, ignoreCase = true))
            }
            updateList(filtered)
        }
    }

    private fun updateList(logs: List<TreeRingMemoryEntity>) {
        adapter.submitList(logs)
        
        if (logs.isEmpty()) {
            rvLogs.visibility = View.GONE
            tvEmptyState.visibility = View.VISIBLE
            
            if (etSearch.text.isNullOrBlank()) {
                tvEmptyState.text = "No logs yet."
            } else {
                tvEmptyState.text = "No logs match your search."
            }
        } else {
            tvEmptyState.visibility = View.GONE
            rvLogs.visibility = View.VISIBLE
        }
    }

    companion object {
        private const val ARG_DATE = "arg_date"

        fun newInstance(dateIso: String): TreeRingDetailFragment {
            return TreeRingDetailFragment().apply {
                arguments = bundleOf(ARG_DATE to dateIso)
            }
        }
    }

    // Inner Adapter Class
    inner class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {
        private var items: List<TreeRingMemoryEntity> = emptyList()

        fun submitList(newItems: List<TreeRingMemoryEntity>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_tree_ring_log, parent, false)
            return LogViewHolder(view)
        }

        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        inner class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvDateHeader: TextView = itemView.findViewById(R.id.tvDateHeader)
            private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
            private val tvContent: TextView = itemView.findViewById(R.id.tvContent)
            private val tvMood: TextView = itemView.findViewById(R.id.tvMood)

            fun bind(item: TreeRingMemoryEntity) {
                // Determine if we need a date header
                val pos = adapterPosition
                val prevItem = if (pos > 0) items.getOrNull(pos - 1) else null
                
                // Show header if it's the first item OR date changed from previous
                if (prevItem == null || prevItem.date != item.date) {
                    tvDateHeader.visibility = View.VISIBLE
                    try {
                        val date = LocalDate.parse(item.date)
                        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
                        tvDateHeader.text = date.format(formatter).uppercase()
                    } catch (e: Exception) {
                        tvDateHeader.text = item.date
                    }
                } else {
                    tvDateHeader.visibility = View.GONE
                }

                // Time
                val time = Instant.ofEpochMilli(item.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()
                val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
                tvTime.text = time.format(timeFormatter)

                // Content
                if (!item.note.isNullOrBlank()) {
                    tvContent.text = item.note
                    tvContent.visibility = View.VISIBLE
                } else {
                    tvContent.visibility = View.GONE
                }

                // Mood
                val moodText = item.emotion ?: "No mood"
                val intensity = if (item.intensity != null) " • ${(item.intensity * 100).toInt()}%" else ""
                tvMood.text = "$moodText$intensity"

                // Apply Dashboard Palette
                val bgColor = when (item.type) {
                    "QUICK" -> Color.parseColor("#FFD54F") // Check-in Yellow
                    "FULL" -> Color.parseColor("#F8BBD0")  // Journal Pink
                    else -> Color.parseColor("#FDFCF8")    // Default White
                }

                val shape = GradientDrawable()
                shape.shape = GradientDrawable.RECTANGLE
                shape.cornerRadius = 24f * itemView.context.resources.displayMetrics.density
                shape.setColor(bgColor)
                itemView.background = shape
            }
        }
    }
}
