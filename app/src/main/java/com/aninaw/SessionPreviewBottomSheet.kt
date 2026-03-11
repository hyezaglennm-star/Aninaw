package com.aninaw

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

class SessionPreviewBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_LABEL = "arg_label"
        private const val ARG_TITLE = "arg_title"
        private const val ARG_DESCRIPTION = "arg_description"

        fun newInstance(label: String, title: String, description: String): SessionPreviewBottomSheet {
            return SessionPreviewBottomSheet().apply {
                arguments = bundleOf(
                    ARG_LABEL to label,
                    ARG_TITLE to title,
                    ARG_DESCRIPTION to description
                )
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.sheet_session_preview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val label = requireArguments().getString(ARG_LABEL).orEmpty()
        val title = requireArguments().getString(ARG_TITLE).orEmpty()
        val description = requireArguments().getString(ARG_DESCRIPTION).orEmpty()

        view.findViewById<TextView>(R.id.tvSheetLabel).text = label
        view.findViewById<TextView>(R.id.tvSheetTitle).text = title
        view.findViewById<TextView>(R.id.tvSheetDesc).text = description

        view.findViewById<MaterialButton>(R.id.btnSheetClose).setOnClickListener {
            dismiss()
        }

        view.findViewById<MaterialButton>(R.id.btnSheetDone).setOnClickListener {
            dismiss()
        }
    }
}

