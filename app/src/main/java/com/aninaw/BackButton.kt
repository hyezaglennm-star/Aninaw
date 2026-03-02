package com.aninaw

import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

fun AppCompatActivity.bindBack(button: ImageButton?) {
    button?.setOnClickListener {
        onBackPressedDispatcher.onBackPressed()
    }
}
