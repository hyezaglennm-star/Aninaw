package com.aninaw

import android.app.Activity
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

object CreateSystemDialog {

    fun show(
        activity: Activity,
        onCreate: (name: String, icon: String, scheduleType: String, daysMask: Int) -> Unit
    ) {
        val v = LayoutInflater.from(activity).inflate(R.layout.dialog_create_system, null)

        val etName = v.findViewById<EditText>(R.id.etSystemName)
        val etIcon = v.findViewById<EditText>(R.id.etSystemIcon)
        val chipGroup = v.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipScheduleGroup)

        AlertDialog.Builder(activity)
            .setTitle("Create System")
            .setView(v)
            .setPositiveButton("Create") { _, _ ->
                val name = etName.text?.toString().orEmpty()
                val icon = etIcon.text?.toString().orEmpty().ifBlank { "🌿" }

                val scheduleType = when (chipGroup.checkedChipId) {
                    R.id.chipDaily -> "DAILY"
                    R.id.chipSpecific -> "SPECIFIC_DAYS"
                    else -> "null"
                }

                val daysMask = 0
                if (name.isNotBlank()) onCreate(name, icon, scheduleType, daysMask)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}