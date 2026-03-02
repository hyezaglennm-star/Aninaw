package com.aninaw

import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator

object SoftMotion {
    const val DUR_SHORT = 700L
    const val DUR_MED = 1200L
    const val DUR_LONG = 1800L

    val INTERP: Interpolator = AccelerateDecelerateInterpolator()
}
