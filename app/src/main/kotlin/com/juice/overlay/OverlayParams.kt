package com.juice.overlay

import com.juice.settings.JuiceSettings

/** The slice handle's centre in px on a [width] x [height] px screen. */
internal fun JuiceSettings.handleCentre(width: Int, height: Int): Pair<Float, Float> =
    handleX.coerceIn(0f, 1f) * width to handleY.coerceIn(0f, 1f) * height

/** Where a handle dropped at ([x], [y]) px is saved: fractions of the screen, kept [margin] px inside it. */
internal fun handleFractions(x: Float, y: Float, width: Int, height: Int, margin: Float): Pair<Float, Float> =
    x.coerceIn(margin, width - margin) / width to y.coerceIn(margin, height - margin) / height
