package com.juice.overlay

/** What sits behind the overlay content: the blurred screen, or a dark semi-opaque scrim. */
internal enum class Backdrop {
    BLUR,
    SCRIM,
    ;

    companion object {
        /** Blur only when the user enabled it, the device supports it and battery saver is off. */
        fun choose(blurEnabled: Boolean, blurSupported: Boolean, batterySaver: Boolean): Backdrop =
            if (blurEnabled && blurSupported && !batterySaver) BLUR else SCRIM
    }
}
