package com.juice.overlay

import com.juice.settings.JuiceSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class OverlayParamsTest {
    private val margin = HandleEdges(90f, 90f, 90f, 90f)

    @Test
    fun `handle centre is its fractions of the screen`() {
        assertEquals(540f to 240f, JuiceSettings(handleX = 0.5f, handleY = 0.1f).handleCentre(1080, 2400))
    }

    @Test
    fun `handle centre stays on screen`() {
        assertEquals(1080f to 0f, JuiceSettings(handleX = 3f, handleY = -1f).handleCentre(1080, 2400))
    }

    @Test
    fun `a handle low on a portrait screen is still on screen in landscape`() {
        // Dropped near the bottom right in portrait; the same fractions on the landscape screen.
        val settings = JuiceSettings(handleX = 0.9f, handleY = 0.95f)
        val (x, y) = settings.handleCentre(2400, 1080, margin)
        assertEquals(2160f, x)
        assertEquals(1080f - 90f, y)
    }

    @Test
    fun `handle centre keeps clear of the edges it is given`() {
        // A navigation bar down the right side in landscape.
        val edges = HandleEdges(90f, 90f, 200f, 90f)
        assertEquals(2400f - 200f to 540f, JuiceSettings(handleX = 1f, handleY = 0.5f).handleCentre(2400, 1080, edges))
    }

    @Test
    fun `edges take the bars and cutout into account as well as the window margin`() {
        assertEquals(HandleEdges(90f, 136f, 236f, 90f), HandleEdges.of(90f, 56f, insetLeft = 0, insetTop = 80, insetRight = 180, insetBottom = 20))
    }

    @Test
    fun `a dropped handle is saved as fractions, kept inside the edge margin`() {
        assertEquals(0.5f to 0.25f, handleFractions(540f, 600f, 1080, 2400, margin))
        assertEquals(90f / 1080 to (2400f - 90f) / 2400, handleFractions(-50f, 5000f, 1080, 2400, margin))
    }

    @Test
    fun `a handle dropped under a bar lands beside it`() {
        val edges = HandleEdges(90f, 90f, 200f, 90f)
        assertEquals(2200f to 500f, handleLanding(2380f, 500f, 2400, 1080, edges))
    }
}
