//JournalHistoryActivity.kt
package com.aninaw

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aninaw.data.AninawDb
import com.aninaw.data.journal.JournalEntity
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context

class JournalHistoryActivity : AppCompatActivity() {

    private lateinit var rvJournalHistory: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var etSearch: TextInputEditText
    private lateinit var adapter: JournalHistoryAdapter

    private var allEntries: List<JournalEntity> = emptyList()
    private var currentFilter: String = "ALL"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_journal_history)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        rvJournalHistory = findViewById(R.id.rvJournalHistory)
        tvEmpty = findViewById(R.id.tvEmpty)
        etSearch = findViewById(R.id.etSearch)

        adapter = JournalHistoryAdapter(emptyList()) { item ->
            val intent = Intent(this, JournalEditorActivity::class.java).apply {
                putExtra(JournalEditorActivity.EXTRA_ENTRY_ID, item.id)
            }
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        rvJournalHistory.layoutManager = LinearLayoutManager(this)
        rvJournalHistory.adapter = adapter

        setupFilterClicks()
        setupSearch()
        loadEntries()
    }

    override fun onResume() {
        super.onResume()
        loadEntries()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
    }

    private fun formatSearchableDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun setupFilterClicks() {
        findViewById<Chip>(R.id.chipAll).setOnClickListener {
            currentFilter = "ALL"
            applyFilters()
        }

        findViewById<Chip>(R.id.chipMorning).setOnClickListener {
            currentFilter = "MORNING"
            applyFilters()
        }

        findViewById<Chip>(R.id.chipPause).setOnClickListener {
            currentFilter = "QUICK"
            applyFilters()
        }

        findViewById<Chip>(R.id.chipIntention).setOnClickListener {
            currentFilter = "INTENTION"
            applyFilters()
        }
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                applyFilters()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })

        etSearch.setOnEditorActionListener { v, actionId, event ->
            val isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH
            val isEnterKey = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN

            if (isSearchAction || isEnterKey) {
                applyFilters()
                hideKeyboard()
                etSearch.clearFocus()
                true
            } else {
                false
            }
        }
    }

    private fun loadEntries() {
        lifecycleScope.launch {
            val db = AninawDb.getDatabase(this@JournalHistoryActivity)

            val entries = withContext(Dispatchers.IO) {
                db.journalDao().getAllEntriesList()
            }

            allEntries = entries.sortedByDescending { it.timestamp }
            applyFilters()
        }
    }

    private fun applyFilters() {
        val query = etSearch.text?.toString()
            ?.trim()
            ?.lowercase(Locale.getDefault())
            .orEmpty()

        val filteredEntries = allEntries.filter { entry ->
            val matchesFilter = when (currentFilter) {
                "ALL" -> true
                else -> entry.type.equals(currentFilter, ignoreCase = true)
            }

            val title = entry.prompt.ifBlank { mapTypeToFallbackTitle(entry.type) }
            val typeLabel = mapTypeToFallbackTitle(entry.type).lowercase(Locale.getDefault())
            val fullDate = formatSearchableDate(entry.timestamp).lowercase(Locale.getDefault())
            val monthHeader = formatMonthHeader(entry.timestamp).lowercase(Locale.getDefault())

            val searchableText = listOfNotNull(
                title,
                entry.content,
                entry.mood,
                entry.type,
                typeLabel,
                fullDate,
                monthHeader
            ).joinToString(" ").lowercase(Locale.getDefault())

            val matchesSearch = query.isBlank() || searchableText.contains(query)

            matchesFilter && matchesSearch
        }.sortedByDescending { it.timestamp }

        val groupedItems = buildGroupedList(filteredEntries)

        adapter.submitList(groupedItems)
        tvEmpty.visibility = if (filteredEntries.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun buildGroupedList(entries: List<JournalEntity>): List<JournalHistoryItem> {
        val result = mutableListOf<JournalHistoryItem>()
        var lastMonthHeader: String? = null

        entries.forEach { entry ->
            val monthHeader = formatMonthHeader(entry.timestamp)

            if (monthHeader != lastMonthHeader) {
                result.add(JournalHistoryItem.Header(monthHeader))
                lastMonthHeader = monthHeader
            }

            result.add(
                JournalHistoryItem.Entry(
                    id = entry.id,
                    title = entry.prompt.ifBlank { mapTypeToFallbackTitle(entry.type) },
                    content = entry.content,
                    type = entry.type,
                    timestamp = entry.timestamp,
                    mood = entry.mood
                )
            )
        }

        return result
    }

    private fun formatMonthHeader(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun mapTypeToFallbackTitle(type: String): String {
        return when (type.uppercase(Locale.getDefault())) {
            "MORNING" -> "Morning Reflection"
            "QUICK" -> "Pause & Reflect"
            "INTENTION" -> "Set Intentions"
            "EVENING" -> "Evening Reflection"
            "FREESTYLE" -> "Journal Entry"
            else -> "Journal Entry"
        }
    }
}