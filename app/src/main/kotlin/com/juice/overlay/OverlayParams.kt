package com.juice.overlay

import android.view.Gravity
import com.juice.settings.JuiceSettings
import com.juice.settings.StripPosition

/** How [JuiceSettings] shape the strip window. */
internal fun StripPosition.gravity(): Int =
    (if (this == StripPosition.Top) Gravity.TOP else Gravity.BOTTOM) or Gravity.CENTER_HORIZONTAL

internal fun JuiceSettings.stripWidthPx(screenWidthPx: Int): Int =
    screenWidthPx * stripWidthPercent.coerceIn(
        JuiceSettings.MIN_STRIP_WIDTH_PERCENT,
        JuiceSettings.MAX_STRIP_WIDTH_PERCENT,
    ) / 100
