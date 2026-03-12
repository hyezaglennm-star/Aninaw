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
        Pair("Calm", R.drawable.happy_mood),
        Pair("Okay", R.drawable.okay_mood),
        Pair("Tense", R.drawable.tense_mood1),
        Pair("Heavy", R.drawable.sad_mood),
        Pair("Tired", R.drawable.neutral_mood),
        // Additional mappings (fallback or new PNGs needed if specific ones requested)
        Pair("Anxious", R.drawable.tense_mood1),
        Pair("Sad", R.drawable.sad_mood),
        Pair("Angry", R.drawable.tense_mood1),
        Pair("Grateful", R.drawable.happy_mood),
        Pair("Numb", R.drawable.neutral_mood)
    )

    private var selectedEmotion: String? = null

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
        val recycler = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerEmotions)
        recycler.layoutManager = androidx.recyclerview.widget.GridLayoutManager(context, 5)

        val adapter = MoodAdapter(emotions) { label ->
            selectedEmotion = label
            updateSaveEnabled(view)
        }
        recycler.adapter = adapter

        val toggleIntensity = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleIntensity)
        val toggleCapacity = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleCapacity)

        val tvAddNote = view.findViewById<View>(R.id.tvAddNote)
        val noteContainer = view.findViewById<TextInputLayout>(R.id.noteContainer)
        val etNote = view.findViewById<TextInputEditText>(R.id.etNote)

        val btnSave = view.findViewById<MaterialButton>(R.id.btnSave)

        // Defaults
        toggleIntensity.check(R.id.btnIntensityMedium) // medium default
        toggleCapacity.check(R.id.btnCapSteady)        // steady required default

        // Optional note toggle
        tvAddNote.setOnClickListener {
            noteContainer.isVisible = !noteContainer.isVisible
            if (!noteContainer.isVisible) etNote.setText("")
        }

        updateSaveEnabled(view)

        // Calm checked-state tint for toggle buttons (compatible approach)
        val checkedColor = Color.parseColor("#CFE8D6")
        val uncheckedColor = Color.parseColor("#FFFFFF")
        setupToggleTint(toggleIntensity, checkedColor, uncheckedColor)
        setupToggleTint(toggleCapacity, checkedColor, uncheckedColor)

        // Save
        btnSave.setOnClickListener {
            val emotion = selectedEmotion ?: return@setOnClickListener

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

    private fun updateSaveEnabled(view: View) {
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSave)
        btnSave.isEnabled = selectedEmotion != null
    }

    // Adapter class
    inner class MoodAdapter(
        private val items: List<Pair<String, Int>>,
        private val onClick: (String) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<MoodAdapter.MoodViewHolder>() {

        private var selectedPos = -1

        inner class MoodViewHolder(v: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(v) {
            val img: android.widget.ImageView = v.findViewById(R.id.imgMood)
            val txt: android.widget.TextView = v.findViewById(R.id.tvMoodLabel)
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
