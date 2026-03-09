package com.aninaw

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class QuickStretchActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_stretch)

        com.aninaw.util.BackButton.bind(this)
    }
}

