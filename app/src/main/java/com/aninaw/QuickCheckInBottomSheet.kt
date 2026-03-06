package com.aninaw

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import java.time.Instant

class QuickCheckInBottomSheet(
    private val onSaved: (QuickCheckInEntry) -> Unit,
    private val onSkip: (() -> Unit)? = null
) : BottomSheetDialogFragment() {

    data class QuickCheckInEntry(
        val timestampMs: Long,
        val emotion: String,
        val intensity: Int,   // 1=Light, 2=Medium, 3=Strong
        val capacity: String, // "LOW", "STEADY", "STRONG"
        val note: String?
    )

    private var selectedEmotion: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottomsheet_quick_checkin, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val btnConfirm = view.findViewById<MaterialButton>(R.id.btnConfirm)
        val tvSelectedEmotion = view.findViewById<android.widget.TextView>(R.id.tvSelectedEmotion)
        val tvSkip = view.findViewById<android.widget.TextView>(R.id.tvSkip)

        val faces = mapOf(
            view.findViewById<ImageView>(R.id.faceHappy) to "Happy",
            view.findViewById<ImageView>(R.id.faceShy) to "Shy",
            view.findViewById<ImageView>(R.id.faceNeutral) to "Neutral",
            view.findViewById<ImageView>(R.id.faceAnxious) to "Anxious",
            view.findViewById<ImageView>(R.id.faceSad) to "Sad"
        )

        fun updateSelection(selectedView: ImageView) {
            faces.keys.forEach { face ->
                if (face == selectedView) {
                    face.alpha = 1.0f
                    face.scaleX = 1.2f
                    face.scaleY = 1.2f
                    selectedEmotion = faces[face]
                    tvSelectedEmotion.text = selectedEmotion
                    tvSelectedEmotion.visibility = View.VISIBLE
                } else {
                    face.alpha = 0.5f
                    face.scaleX = 0.9f
                    face.scaleY = 0.9f
                }
            }
            btnConfirm.isEnabled = true
        }

        faces.keys.forEach { face ->
            face.setOnClickListener { updateSelection(face) }
        }

        tvSkip.setOnClickListener {
            onSkip?.invoke()
            dismissAllowingStateLoss()
        }

        btnConfirm.setOnClickListener {
            val emotion = selectedEmotion ?: return@setOnClickListener

            // Default mappings based on emotion
            val (intensity, capacity) = when (emotion) {
                "Happy" -> 2 to "STEADY"
                "Shy" -> 2 to "STEADY"
                "Neutral" -> 1 to "STEADY"
                "Anxious" -> 3 to "LOW"
                "Sad" -> 2 to "LOW"
                else -> 2 to "STEADY"
            }

            val entry = QuickCheckInEntry(
                timestampMs = Instant.now().toEpochMilli(),
                emotion = emotion,
                intensity = intensity,
                capacity = capacity,
                note = null
            )

            // ✅ TREE GROWTH: count daily quick check-in once
            TreeGrowthManager(requireContext()).onQuickCheckInCompleted()

            onSaved(entry)
            dismissAllowingStateLoss()
        }
    }
}
