package com.aninaw

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.aninaw.data.systems.BlockEntity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import java.util.Calendar
import java.util.UUID

class AddBlockBottomSheet(
    private val systemId: String,
    private val nextSortIndex: Int,
    private val onAdd: (BlockEntity) -> Unit
) : BottomSheetDialogFragment() {

    private var selectedType: String = "ACTION"
    private var pickedMinutes: Int? = null
    private var pickedReminderAt: Long? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.bottomsheet_add_block, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val chipGroup = view.findViewById<ChipGroup>(R.id.chipTypeGroup)
        val chipAction = view.findViewById<Chip>(R.id.chipAction)
        val chipHabit = view.findViewById<Chip>(R.id.chipHabit)
        val chipReminder = view.findViewById<Chip>(R.id.chipReminder)
        val chipNote = view.findViewById<Chip>(R.id.chipNote)

        val etTitle = view.findViewById<TextInputEditText>(R.id.etTitle)
        val etNote = view.findViewById<TextInputEditText>(R.id.etNote)

        val btnPickTime = view.findViewById<MaterialButton>(R.id.btnPickTime)
        val btnPickReminder = view.findViewById<MaterialButton>(R.id.btnPickReminderDateTime)
        val btnAdd = view.findViewById<MaterialButton>(R.id.btnAdd)

        fun refreshVisibility() {
            btnPickTime.visibility = if (selectedType == "ACTION") View.VISIBLE else View.GONE
            btnPickReminder.visibility = if (selectedType == "REMINDER") View.VISIBLE else View.GONE
        }

        fun setType(t: String) {
            selectedType = t
            // reset type-specific fields when switching
            pickedMinutes = null
            pickedReminderAt = null
            btnPickTime.text = "Pick time (optional)"
            btnPickReminder.text = "Pick date & time"
            refreshVisibility()
        }

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            when (checkedId) {
                R.id.chipAction -> setType("ACTION")
                R.id.chipHabit -> setType("HABIT")
                R.id.chipReminder -> setType("REMINDER")
                R.id.chipNote -> setType("NOTE")
            }
        }

        // default selection (don’t rely on android:checked or chip.isChecked)
        chipGroup.check(R.id.chipAction)
        setType("ACTION")

        btnPickTime.setOnClickListener {
            // store minutes only, not actual time-of-day
            val options = arrayOf("2 min", "5 min", "10 min", "15 min", "30 min", "45 min", "60 min")
            val values = intArrayOf(2, 5, 10, 15, 30, 45, 60)

            android.app.AlertDialog.Builder(requireContext())
                .setTitle("How long?")
                .setItems(options) { _, which ->
                    pickedMinutes = values[which]
                    btnPickTime.text = "Time: ${options[which]}"
                }
                .show()
        }

        btnPickReminder.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, y, m, d ->
                    cal.set(Calendar.YEAR, y)
                    cal.set(Calendar.MONTH, m)
                    cal.set(Calendar.DAY_OF_MONTH, d)

                    TimePickerDialog(
                        requireContext(),
                        { _, hh, mm ->
                            cal.set(Calendar.HOUR_OF_DAY, hh)
                            cal.set(Calendar.MINUTE, mm)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)

                            pickedReminderAt = cal.timeInMillis
                            btnPickReminder.text = "Reminder set"
                        },
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                        false
                    ).show()
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        btnAdd.setOnClickListener {
            val title = etTitle.text?.toString()?.trim().orEmpty()
            val note = etNote.text?.toString()?.trim()?.ifBlank { null }

            if (title.isBlank()) {
                etTitle.error = "Required"
                return@setOnClickListener
            }

            if (selectedType == "REMINDER" && pickedReminderAt == null) {
                android.widget.Toast.makeText(requireContext(), "Pick date & time first", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val now = System.currentTimeMillis()
            val block = BlockEntity(
                id = UUID.randomUUID().toString(),
                systemId = systemId,
                type = selectedType,
                title = title,
                note = note,
                timeMinutes = if (selectedType == "ACTION") pickedMinutes else null,
                remindAt = if (selectedType == "REMINDER") pickedReminderAt else null,
                sortIndex = nextSortIndex,
                isChecked = false,
                createdAt = now,
                updatedAt = now
            )

            onAdd(block)
            dismiss()
        }
    }
}