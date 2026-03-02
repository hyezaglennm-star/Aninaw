//QuickCheckInBottomSheet.kt
package com.aninaw

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.time.Instant

class QuickCheckInBottomSheet(
    private val onSaved: (QuickCheckInEntry) -> Unit
) : BottomSheetDialogFragment() {

    data class QuickCheckInEntry(
        val timestampMs: Long,
        val emotion: String,
        val intensity: Int,   // 1=Light, 2=Medium, 3=Strong
        val capacity: String, // "LOW", "STEADY", "STRONG"
        val note: String?
    )

    private val emotions = listOf(
        "Calm", "Okay", "Tense", "Heavy", "Tired",
        "Anxious", "Sad", "Angry", "Grateful", "Numb"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // IMPORTANT: must match your file name in res/layout
        return inflater.inflate(R.layout.bottomsheet_quick_checkin, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Views
        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupEmotion)

        val toggleIntensity = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleIntensity)
        val toggleCapacity = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleCapacity)

        val tvAddNote = view.findViewById<View>(R.id.tvAddNote)
        val noteContainer = view.findViewById<TextInputLayout>(R.id.noteContainer)
        val etNote = view.findViewById<TextInputEditText>(R.id.etNote)

        val btnSave = view.findViewById<MaterialButton>(R.id.btnSave)

        // Build emotion chips dynamically (preview will look empty, runtime will be fine)
        chipGroup.removeAllViews()
        emotions.forEach { label ->
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                isClickable = true
            }
            chipGroup.addView(chip)
        }

        // Defaults
        toggleIntensity.check(R.id.btnIntensityMedium) // medium default
        toggleCapacity.check(R.id.btnCapSteady)        // steady required default

        // Optional note toggle
        tvAddNote.setOnClickListener {
            noteContainer.isVisible = !noteContainer.isVisible
            if (!noteContainer.isVisible) etNote.setText("")
        }

        // Enable save only if an emotion is selected
        fun updateSaveEnabled() {
            btnSave.isEnabled = chipGroup.checkedChipId != View.NO_ID
        }
        chipGroup.setOnCheckedStateChangeListener { _, _ -> updateSaveEnabled() }
        updateSaveEnabled()

        // Calm checked-state tint for toggle buttons (compatible approach)
        val checkedColor = Color.parseColor("#CFE8D6")
        val uncheckedColor = Color.parseColor("#FFFFFF")
        setupToggleTint(toggleIntensity, checkedColor, uncheckedColor)
        setupToggleTint(toggleCapacity, checkedColor, uncheckedColor)

        // Save
        btnSave.setOnClickListener {
            val checkedChipId = chipGroup.checkedChipId
            if (checkedChipId == View.NO_ID) return@setOnClickListener

            val emotion = chipGroup.findViewById<Chip>(checkedChipId)
                ?.text?.toString()?.trim().orEmpty()
            if (emotion.isBlank()) return@setOnClickListener

            val intensity = when (toggleIntensity.checkedButtonId) {
                R.id.btnIntensityLight -> 1
                R.id.btnIntensityStrong -> 3
                else -> 2
            }

            val capacity = when (toggleCapacity.checkedButtonId) {
                R.id.btnCapLow -> "LOW"
                R.id.btnCapStrong -> "STRONG"
                else -> "STEADY"
            }

            val note = etNote.text?.toString()?.trim()?.takeIf { it.isNotBlank() }

            val entry = QuickCheckInEntry(
                timestampMs = Instant.now().toEpochMilli(),
                emotion = emotion,
                intensity = intensity,
                capacity = capacity,
                note = note
            )


// ✅ TREE GROWTH: count daily quick check-in once
            TreeGrowthManager(requireContext()).onQuickCheckInCompleted()

            onSaved(entry)
            dismissAllowingStateLoss()

        }
    }

    // Put this at CLASS LEVEL (not inside onViewCreated)
    private fun setupToggleTint(
        group: MaterialButtonToggleGroup,
        checkedColor: Int,
        uncheckedColor: Int
    ) {
        fun refresh() {
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                if (child is MaterialButton) {
                    val isChecked = child.id == group.checkedButtonId
                    child.setBackgroundColor(if (isChecked) checkedColor else uncheckedColor)
                }
            }
        }

        group.addOnButtonCheckedListener { _, _, _ -> refresh() }
        refresh()
    }
}
