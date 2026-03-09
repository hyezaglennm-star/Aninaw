package com.aninaw

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class Grounding54321Activity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grounding_54321)

        com.aninaw.util.BackButton.bind(this)
    }
}

