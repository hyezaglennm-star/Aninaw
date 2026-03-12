package com.aninaw

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aninaw.data.AninawDb
import com.aninaw.data.journal.JournalEntity
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class JournalEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ENTRY_ID = "entry_id"
        const val EXTRA_TYPE = "entry_type"     // MORNING, EVENING, QUICK
        const val EXTRA_PROMPT = "entry_prompt" // e.g. "What are you grateful for?"
    }

    private lateinit var etContent: EditText
    private lateinit var tvPrompt: TextView
    private lateinit var tvDate: TextView
    private lateinit var recyclerMood: androidx.recyclerview.widget.RecyclerView
    private lateinit var btnSave: MaterialButton
    private lateinit var btnDelete: View

    private var entryId: Long = -1L
    private var entryType: String = "QUICK"
    private var entryPrompt: String = ""
    private var existingEntry: JournalEntity? = null

    private var selectedMood: String? = null
    private lateinit var moodAdapter: MoodAdapter
    private val moodOptions = listOf(
        Pair("Difficult", R.drawable.tense_mood1),
        Pair("Heavy", R.drawable.sad_mood),
        Pair("Neutral", R.drawable.neutral_mood),
        Pair("Okay", R.drawable.okay_mood),
        Pair("Calm", R.drawable.happy_mood)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_journal_editor)

        etContent = findViewById(R.id.etContent)
        tvPrompt = findViewById(R.id.tvPrompt)
        tvDate = findViewById(R.id.tvDate)
        recyclerMood = findViewById(R.id.recyclerMood)
        btnSave = findViewById(R.id.btnSave)
        btnDelete = findViewById(R.id.btnDelete)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        // Setup Mood Grid
        recyclerMood.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 5)
        moodAdapter = MoodAdapter(moodOptions) { label ->
            selectedMood = label
        }
        recyclerMood.adapter = moodAdapter

        // Load extras
        entryId = intent.getLongExtra(EXTRA_ENTRY_ID, -1L)
        entryType = intent.getStringExtra(EXTRA_TYPE) ?: "QUICK"
        entryPrompt = intent.getStringExtra(EXTRA_PROMPT) ?: "Journal Entry"

        tvPrompt.text = entryPrompt
        tvDate.text = LocalDate.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))

        if (entryId != -1L) {
            loadEntry(entryId)
        }

        btnSave.setOnClickListener { saveEntry() }
        
        btnDelete.setOnClickListener {
            if (entryId != -1L) deleteEntry()
        }
    }

    private fun loadEntry(id: Long) {
        lifecycleScope.launch {
            val db = AninawDb.getDatabase(this@JournalEditorActivity)
            
            existingEntry = withContext(Dispatchers.IO) {
                db.journalDao().getById(id)
            }
            
            existingEntry?.let { entry ->
                etContent.setText(entry.content)
                tvPrompt.text = entry.prompt
                tvDate.text = LocalDate.parse(entry.date).format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                
                // Set mood selection
                entry.mood?.let { mood ->
                    selectedMood = mood
                    moodAdapter.setSelected(mood)
                }
                
                btnDelete.visibility = View.VISIBLE
            }
        }
    }

    private fun saveEntry() {
        val content = etContent.text.toString().trim()
        if (content.isBlank()) {
            Toast.makeText(this, "Please write something...", Toast.LENGTH_SHORT).show()
            return
        }

        val db = AninawDb.getDatabase(this)
        val today = LocalDate.now().toString()

        lifecycleScope.launch(Dispatchers.IO) {
            val entry = JournalEntity(
                id = if (entryId != -1L) entryId else 0,
                date = existingEntry?.date ?: today,
                timestamp = System.currentTimeMillis(),
                type = existingEntry?.type ?: entryType,
                prompt = existingEntry?.prompt ?: entryPrompt,
                content = content,
                mood = selectedMood,
                tags = existingEntry?.tags
            )
            
            db.journalDao().insert(entry)
            
            withContext(Dispatchers.Main) {
                Toast.makeText(this@JournalEditorActivity, "Saved!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    // Adapter
    inner class MoodAdapter(
        private val items: List<Pair<String, Int>>,
        private val onClick: (String) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<MoodAdapter.MoodViewHolder>() {

        private var selectedPos = -1

        fun setSelected(label: String?) {
            if (label == null) return
            val idx = items.indexOfFirst { it.first == label }
            if (idx != -1) {
                val prev = selectedPos
                selectedPos = idx
                notifyItemChanged(prev)
                notifyItemChanged(selectedPos)
                onClick(label)
            }
        }

        inner class MoodViewHolder(v: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.imgMood)
        val txt: TextView = v.findViewById(R.id.tvMoodLabel)
        val container: View = v
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MoodViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_mood_grid, parent, false)
        return MoodViewHolder(v)
    }

        override fun onBindViewHolder(holder: MoodViewHolder, position: Int) {
            val (label, resId) = items[position]
            holder.txt.text = label
            holder.img.setImageResource(resId)
            
            val isSelected = (position == selectedPos)
            holder.container.alpha = if (isSelected || selectedPos == -1) 1.0f else 0.4f
            holder.container.scaleX = if (isSelected) 1.1f else 1.0f
            holder.container.scaleY = if (isSelected) 1.1f else 1.0f

            holder.container.setOnClickListener {
                val prev = selectedPos
                selectedPos = holder.adapterPosition
                notifyItemChanged(prev)
                notifyItemChanged(selectedPos)
                onClick(label)
            }
        }

        override fun getItemCount() = items.size
    }

    private fun deleteEntry() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AninawDb.getDatabase(this@JournalEditorActivity)
            existingEntry?.let { db.journalDao().delete(it) }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@JournalEditorActivity, "Deleted", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
