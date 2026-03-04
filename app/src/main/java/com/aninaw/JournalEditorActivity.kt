package com.aninaw

import android.os.Bundle
import android.view.View
import android.widget.EditText
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
    private lateinit var chipGroupMood: ChipGroup
    private lateinit var btnSave: MaterialButton
    private lateinit var btnDelete: View

    private var entryId: Long = -1L
    private var entryType: String = "QUICK"
    private var entryPrompt: String = ""
    private var existingEntry: JournalEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_journal_editor)

        etContent = findViewById(R.id.etContent)
        tvPrompt = findViewById(R.id.tvPrompt)
        tvDate = findViewById(R.id.tvDate)
        chipGroupMood = findViewById(R.id.chipGroupMood)
        btnSave = findViewById(R.id.btnSave)
        btnDelete = findViewById(R.id.btnDelete)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

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
                
                // Set mood chip if matches
                entry.mood?.let { mood ->
                    // Iterate children to find match (simple approach)
                    for (i in 0 until chipGroupMood.childCount) {
                        val chip = chipGroupMood.getChildAt(i) as? Chip
                        if (chip?.text.toString() == mood) {
                            chip?.isChecked = true
                            break
                        }
                    }
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

        val moodId = chipGroupMood.checkedChipId
        val mood = if (moodId != View.NO_ID) {
            findViewById<Chip>(moodId).text.toString()
        } else null

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
                mood = mood,
                tags = existingEntry?.tags
            )
            
            db.journalDao().insert(entry)
            
            withContext(Dispatchers.Main) {
                Toast.makeText(this@JournalEditorActivity, "Saved!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
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
