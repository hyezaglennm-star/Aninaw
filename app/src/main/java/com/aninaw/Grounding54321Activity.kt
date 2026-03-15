//GroundingActivity.kt
package com.aninaw

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import androidx.lifecycle.lifecycleScope
import com.aninaw.data.AninawDb
import com.aninaw.data.calmhistory.CalmToolHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Grounding54321Activity : AppCompatActivity() {

    private var startedAt: Long = 0L
    private var hasSavedHistory = false
    private lateinit var etItem: EditText
    private lateinit var listItems: LinearLayout
    private lateinit var scrollContainer: androidx.core.widget.NestedScrollView
    private lateinit var tvStepCounter: TextView
    private lateinit var tvPrompt: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var layoutProgress: LinearLayout
    private lateinit var btnNext: MaterialButton
    private lateinit var btnSkip: MaterialButton

    private var currentStep = 1 // 1..5
    private val stepItems = mutableListOf<String>()
    
    // Config per step: (count needed, title, subtitle)
    private val steps = listOf(
        Triple(5, "Look around you.\n\nWhat are 5 things you can see?", "5 things you can see"),
        Triple(4, "Focus on your body.\n\nWhat are 4 things you can feel?", "4 things you can feel"),
        Triple(3, "Listen closely.\n\nWhat are 3 things you can hear?", "3 things you can hear"),
        Triple(2, "Take a breath.\n\nWhat are 2 things you can smell?", "2 things you can smell"),
        Triple(1, "Finally.\n\nWhat is 1 thing you can taste?", "1 thing you can taste")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grounding_54321)

        startedAt = System.currentTimeMillis()

        com.aninaw.util.BackButton.bind(this)

        etItem = findViewById(R.id.etItem)
        listItems = findViewById(R.id.listItems)
        scrollContainer = findViewById(R.id.scrollContainer)
        tvStepCounter = findViewById(R.id.tvStepCounter)
        tvPrompt = findViewById(R.id.tvPrompt)
        tvSubtitle = findViewById(R.id.tvSubtitle)
        layoutProgress = findViewById(R.id.layoutProgress)
        btnNext = findViewById(R.id.btnNext)
        btnSkip = findViewById(R.id.btnSkip)

        setupInput()
        updateUiForStep()

        btnNext.setOnClickListener {
            advanceStep()
        }

        btnSkip.setOnClickListener {
            advanceStep()
        }
    }

    private fun advanceStep() {
        if (currentStep < 5) {
            currentStep++
            updateUiForStep()
        } else {
            saveHistoryIfNeeded("completed")
            finish()
        }
    }

    private fun setupInput() {
        etItem.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                addItem()
                return@setOnEditorActionListener true
            }
            false
        }
    }

    private fun addItem() {
        val text = etItem.text.toString().trim()
        if (text.isBlank()) return

        // 1. Add item visually
        val item = TextView(this).apply {
            this.text = "• $text"
            textSize = 15f
            setTextColor(0xFF4A4A44.toInt())
            setPadding(0, 8, 0, 8)
        }
        listItems.addView(item, 0) // Add to top

        // 2. Clear field
        etItem.setText("")

        // 3. Track progress
        stepItems.add(text)
        checkStepCompletion()
        
        // 4. Scroll to keep the input field and new item visible (prevent keyboard obscuring)
        listItems.post {
            scrollContainer.smoothScrollTo(0, etItem.top)
        }
    }

    private fun checkStepCompletion() {
        val needed = steps[currentStep - 1].first
        // Enable next button when items collected match or exceed required count
        if (stepItems.size >= needed) {
            btnNext.isEnabled = true
            btnNext.alpha = 1.0f

            // Auto-advance
            etItem.isEnabled = false
            
            val stepWhenTriggered = currentStep
            listItems.postDelayed({
                if (currentStep == stepWhenTriggered) {
                    advanceStep()
                }
            }, 300)
        } else {
            // Keep disabled if not enough items
            btnNext.isEnabled = false
            btnNext.alpha = 0.55f
        }
    }
    private fun saveHistoryIfNeeded(completionState: String) {
        if (hasSavedHistory) return
        hasSavedHistory = true

        val durationSeconds = ((System.currentTimeMillis() - startedAt) / 1000L).toInt()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AninawDb.getDatabase(this@Grounding54321Activity)
                    .calmToolHistoryDao()
                    .insert(
                        CalmToolHistoryEntity(
                            toolType = "grounding",
                            toolTitle = "Ground",
                            completedAt = System.currentTimeMillis(),
                            durationSeconds = durationSeconds,
                            completionState = completionState
                        )
                    )
            }
        }
    }

    private fun updateUiForStep() {
        val (count, prompt, sub) = steps[currentStep - 1]
        
        tvStepCounter.text = "Step $currentStep of 5"
        tvPrompt.text = prompt
        tvSubtitle.text = sub
        
        // Reset state for new step
        stepItems.clear()
        listItems.removeAllViews()
        btnNext.isEnabled = false
        btnNext.alpha = 0.55f
        
        etItem.isEnabled = true
        etItem.text.clear()
        etItem.requestFocus()
        
        // Update dots
        for (i in 0 until layoutProgress.childCount) {
            val dot = layoutProgress.getChildAt(i)
            val bg = if (i == currentStep - 1) R.drawable.bg_progress_dot_active else R.drawable.bg_progress_dot_inactive
            dot.setBackgroundResource(bg)
        }
    }
}

