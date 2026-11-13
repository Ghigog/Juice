package com.juice.overlay

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.juice.settings.JuiceSettings
import com.juice.ui.liquid.JuiceLiquidView
import com.juice.ui.overlay.LiquidTouch
import com.juice.ui.overlay.LiquidTouchPhase
import com.juice.ui.theme.FlavourTheme

/** Hosts the Thirst Reservoir's [view] for one overlay: its touch phases, flavour and settings. */
class LiquidHost(context: Context, val view: JuiceLiquidView = JuiceLiquidView(context)) {
    /** A finger on the centre card, as fractions of the card, goes to the liquid's touch phases. */
    fun touch(touch: LiquidTouch) {
        val x = touch.position.x / touch.size.width
        val y = touch.position.y / touch.size.height
        when (touch.phase) {
            LiquidTouchPhase.DOWN -> view.touchDown(x, y)
            LiquidTouchPhase.MOVE -> view.touchMove(x, y)
            LiquidTouchPhase.UP -> view.touchUp()
        }
    }

    fun setFlavour(theme: FlavourTheme) {
        view.setFlavour(theme.accent, theme.deep)
        view.viscous = theme.viscous
        view.fizz = theme.fizz
    }

    fun sync(settings: JuiceSettings) {
        view.sloshEnabled = settings.sloshEnabled
        view.particlesEnabled = settings.particlesEnabled
        view.quality = settings.liquidQuality
    }
}

/** Embeds the [reservoir] view and pushes the meter's [fill] into it on every recomposition. */
@Composable
internal fun ReservoirHost(reservoir: JuiceLiquidView, fill: Float, modifier: Modifier) {
    AndroidView(factory = { reservoir.also { (it.parent as? android.view.ViewGroup)?.removeView(it) } }, modifier = modifier, update = { it.fill = fill })
}
