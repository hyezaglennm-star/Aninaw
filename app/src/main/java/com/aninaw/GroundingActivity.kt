package com.aninaw

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aninaw.data.AninawDb
import com.aninaw.growth.GrowthManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class GroundingActivity : AppCompatActivity() {

    private lateinit var tvStepCounter: TextView
    private lateinit var tvStepHint: TextView
    private lateinit var etItem: EditText
    private lateinit var btnAdd: MaterialButton
    private lateinit var listItems: LinearLayout
    private lateinit var btnSkip: MaterialButton
    private lateinit var btnNext: MaterialButton

    private var currentStepIndex = 0
    private val steps = listOf(
        Step("5 things you can see", 5),
        Step("4 things you can feel", 4),
        Step("3 things you can hear", 3),
        Step("2 things you can smell", 2),
        Step("1 thing you can taste", 1)
    )
    
    // Store user inputs for current step
    private val currentInputs = mutableListOf<String>()

    data class Step(val hint: String, val requiredCount: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grounding_54321)

        tvStepCounter = findViewById(R.id.tvStepCounter)
        tvStepHint = findViewById(R.id.tvStepHint)
        etItem = findViewById(R.id.etItem)
        btnAdd = findViewById(R.id.btnAdd)
        listItems = findViewById(R.id.listItems)
        btnSkip = findViewById(R.id.btnSkip)
        btnNext = findViewById(R.id.btnNext)

        // Back button
        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }

        btnAdd.setOnClickListener {
            addItem()
        }

        btnSkip.setOnClickListener {
            nextStep()
        }

        btnNext.setOnClickListener {
            nextStep()
        }

        loadStep(0)
    }

    private fun loadStep(index: Int) {
        currentStepIndex = index
        val step = steps[index]
        
        currentInputs.clear()
        listItems.removeAllViews()
        etItem.text.clear()

        tvStepCounter.text = "Step ${index + 1} of 5"
        tvStepHint.text = step.hint
        
        updateButtons()
    }

    private fun addItem() {
        val text = etItem.text.toString().trim()
        if (text.isNotBlank()) {
            currentInputs.add(text)
            addReasonView(text)
            etItem.text.clear()
            updateButtons()
            
            // Auto advance if enough items added? No, let user decide or click next.
            // But maybe we can focus back to edit text
        }
    }

    private fun addReasonView(text: String) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_simple_text, listItems, false) as TextView
        view.text = text
        listItems.addView(view)
    }

    private fun updateButtons() {
        val step = steps[currentStepIndex]
        // logic: if they added enough items, maybe highlight Next?
        // For now, Next is always enabled to allow partial completion
        
        if (currentStepIndex == steps.size - 1) {
            btnNext.text = "Finish"
            btnSkip.visibility = View.GONE
        } else {
            btnNext.text = "Next"
            btnSkip.visibility = View.VISIBLE
        }
    }

    private fun nextStep() {
        if (currentStepIndex < steps.size - 1) {
            loadStep(currentStepIndex + 1)
        } else {
            finishActivityWithGrowth()
        }
    }

    private fun finishActivityWithGrowth() {
        lifecycleScope.launch {
            val db = AninawDb.getDatabase(this@GroundingActivity)
            GrowthManager.recordCheckIn(db)
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}
