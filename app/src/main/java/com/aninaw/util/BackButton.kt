package com.aninaw.util

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.aninaw.R

object BackButton {

    /**
     * If the layout contains a view with id btnBack, bind it to behave like system back.
     * Safe no-op if the view doesn't exist.
     */
    fun bind(activity: AppCompatActivity) {
        val back = activity.findViewById<View?>(R.id.btnBack) ?: return
        back.setOnClickListener {
            activity.onBackPressedDispatcher.onBackPressed()
        }
    }
}