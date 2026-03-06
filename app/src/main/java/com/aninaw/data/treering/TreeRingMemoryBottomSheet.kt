//TreeRingMemoryBottomSheet.kt
package com.aninaw

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class TreeRingMemoryBottomSheet : BottomSheetDialogFragment() {

    private fun prettyFromPayload(payload: String?): String {
        if (payload.isNullOrBlank()) return ""

        return try {
            val obj = org.json.JSONObject(payload)

            fun s(key: String): String = obj.optString(key, "").trim().takeIf { it.isNotBlank() && it != "null" } ?: ""

            fun i(key: String): Int? {
                val v = obj.optInt(key, -1)
                return if (v >= 0) v else null
            }

            val lines = mutableListOf<String>()

            s("category").takeIf { it.isNotBlank() }?.let { lines += "Category: $it" }
            s("situation").takeIf { it.isNotBlank() }?.let { lines += "Moment: $it" }
            s("automaticThought").takeIf { it.isNotBlank() }?.let { lines += "Thought: $it" }
            s("alternativeThought").takeIf { it.isNotBlank() }?.let { lines += "Alternative: $it" }
            s("balancedThought").takeIf { it.isNotBlank() }?.let { lines += "Balanced: $it" }

            s("feelingsBefore").takeIf { it.isNotBlank() }?.let { lines += "Feelings: $it" }
            s("bodyBefore").takeIf { it.isNotBlank() }?.let { lines += "Body: $it" }

            i("intensityBefore")?.let { lines += "Intensity (before): $it/100" }
            i("intensityAfter")?.let { lines += "Intensity (after): $it/100" }

            s("nextStep").takeIf { it.isNotBlank() }?.let { lines += "Next step: $it" }

            lines.joinToString("\n\n")
        } catch (_: Throwable) {
            ""
        }
    }

    companion object {
        private const val ARG_MEMORY = "arg_memory"

        fun newInstance(memory: DailyMemory): TreeRingMemoryBottomSheet {
            return TreeRingMemoryBottomSheet().apply {
                arguments = bundleOf(ARG_MEMORY to memory)
            }
        }
    }

    private val memory: DailyMemory by lazy(LazyThreadSafetyMode.NONE) {
        val args = requireArguments()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            args.getParcelable(ARG_MEMORY, DailyMemory::class.java)
        } else {
            @Suppress("DEPRECATION")
            args.getParcelable(ARG_MEMORY)
        } ?: throw IllegalStateException("Missing DailyMemory argument")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottomsheet_tree_ring_memory, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tvDate = view.findViewById<TextView>(R.id.tvDate)
        val tvMood = view.findViewById<TextView>(R.id.tvMood)
        val tvType = view.findViewById<TextView>(R.id.tvType)
        val tvNote = view.findViewById<TextView>(R.id.tvNote)

        // Modern Date Format
        try {
            val date = java.time.LocalDate.parse(memory.dateIso)
            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.getDefault())
            tvDate.text = date.format(formatter).uppercase()
        } catch (e: Exception) {
            tvDate.text = memory.dateIso
        }

        val mood = memory.emotion?.takeIf { it.isNotBlank() } ?: "No mood saved"
        val intensity = memory.intensity?.let { " • ${(it * 100).toInt()}%" } ?: ""
        tvMood.text = mood + intensity

        tvType.text = if (memory.isQuick) "Quick check-in" else "Check-in"

        val pretty = prettyFromPayload(memory.payloadJson)

        tvNote.text = when {
            pretty.isNotBlank() -> pretty
            !memory.note.isNullOrBlank() -> memory.note
            else -> "No details saved for this day."
        }

        tvNote.alpha = if (pretty.isBlank() && memory.note.isNullOrBlank()) 0.7f else 1f
    }
}