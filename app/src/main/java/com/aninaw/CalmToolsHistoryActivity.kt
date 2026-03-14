package com.aninaw

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aninaw.data.AninawDb
import com.aninaw.data.calmhistory.CalmToolHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CalmToolsHistoryActivity : AppCompatActivity() {

    private var allHistory: List<CalmToolHistoryEntity> = emptyList()
    private var currentFilter: String = "ALL"
    private lateinit var rvHistory: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvSummary: TextView
    private lateinit var adapter: CalmToolsHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calm_tools_history)

        com.aninaw.util.BackButton.bind(this)

        rvHistory = findViewById(R.id.rvCalmHistory)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvSummary = findViewById(R.id.tvSummary)

        adapter = CalmToolsHistoryAdapter(emptyList())
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = adapter

        setupFilterClicks()
        loadHistory()
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val history = withContext(Dispatchers.IO) {
                AninawDb.getDatabase(this@CalmToolsHistoryActivity)
                    .calmToolHistoryDao()
                    .getAllHistory()
            }

            allHistory = history
            applyFilter()
        }
    }

    private fun buildGroupedList(entries: List<CalmToolHistoryEntity>): List<CalmToolHistoryItem> {
        val result = mutableListOf<CalmToolHistoryItem>()
        var lastHeader: String? = null

        entries.forEach { entry ->
            val header = formatSectionHeader(entry.completedAt)

            if (header != lastHeader) {
                result.add(CalmToolHistoryItem.Header(header))
                lastHeader = header
            }

            result.add(
                CalmToolHistoryItem.Entry(
                    id = entry.id,
                    toolType = entry.toolType,
                    toolTitle = entry.toolTitle,
                    completedAt = entry.completedAt,
                    durationSeconds = entry.durationSeconds,
                    completionState = entry.completionState
                )
            )
        }

        return result
    }

    private fun formatSectionHeader(timestamp: Long): String {
        val entryCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val nowCal = Calendar.getInstance()

        val isToday =
            entryCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                    entryCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)

        if (isToday) return "Today"

        nowCal.add(Calendar.DAY_OF_YEAR, -1)
        val isYesterday =
            entryCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                    entryCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)

        if (isYesterday) return "Yesterday"

        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun buildSummary(entries: List<CalmToolHistoryEntity>): String {
        if (entries.isEmpty()) {
            return "Your moments of support will appear here."
        }

        val lastUsed = formatLastUsed(entries.first().completedAt)
        val total = entries.size
        return "$total moments recorded • Last used $lastUsed"
    }
    private fun setupFilterClicks() {
        findViewById<com.google.android.material.chip.Chip>(R.id.chipAll).setOnClickListener {
            currentFilter = "ALL"
            applyFilter()
        }

        findViewById<com.google.android.material.chip.Chip>(R.id.chipStretch).setOnClickListener {
            currentFilter = "stretch"
            applyFilter()
        }

        findViewById<com.google.android.material.chip.Chip>(R.id.chipGrounding).setOnClickListener {
            currentFilter = "grounding"
            applyFilter()
        }

        findViewById<com.google.android.material.chip.Chip>(R.id.chipBreathing).setOnClickListener {
            currentFilter = "breathing"
            applyFilter()
        }

        findViewById<com.google.android.material.chip.Chip>(R.id.chipJournal).setOnClickListener {
            currentFilter = "journal"
            applyFilter()
        }

        findViewById<com.google.android.material.chip.Chip>(R.id.chipCbt).setOnClickListener {
            currentFilter = "cbt"
            applyFilter()
        }
    }

    private fun applyFilter() {
        val filtered = allHistory.filter { entry ->
            currentFilter == "ALL" || entry.toolType.equals(currentFilter, ignoreCase = true)
        }

        adapter.submitList(buildGroupedList(filtered))

        tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        rvHistory.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        tvSummary.text = buildSummary(filtered)
    }

    private fun formatLastUsed(timestamp: Long): String {
        val entryCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val nowCal = Calendar.getInstance()

        val isToday =
            entryCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                    entryCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)

        if (isToday) return "today"

        nowCal.add(Calendar.DAY_OF_YEAR, -1)
        val isYesterday =
            entryCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                    entryCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)

        if (isYesterday) return "yesterday"

        val sdf = SimpleDateFormat("MMMM d", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}