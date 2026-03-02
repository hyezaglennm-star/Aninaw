package com.aninaw.views

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.use
import com.aninaw.R

class ScreenHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

    private val tvTitle: TextView
    private val tvSubtitle: TextView

    init {
        inflate(context, R.layout.view_screen_header, this)

        tvTitle = findViewById(R.id.tvScreenTitle)
        tvSubtitle = findViewById(R.id.tvScreenSubtitle)

        context.obtainStyledAttributes(attrs, R.styleable.ScreenHeaderView).use { a ->

            a.getString(R.styleable.ScreenHeaderView_headerTitle)
                ?.let { tvTitle.text = it }

            a.getColorStateList(R.styleable.ScreenHeaderView_headerTitleColor)
                ?.let { tvTitle.setTextColor(it) }

            a.getString(R.styleable.ScreenHeaderView_headerSubtitle)
                ?.let {
                    tvSubtitle.text = it
                    tvSubtitle.visibility = VISIBLE
                }

            a.getColorStateList(R.styleable.ScreenHeaderView_headerSubtitleColor)
                ?.let { tvSubtitle.setTextColor(it) }
        }
    }
}