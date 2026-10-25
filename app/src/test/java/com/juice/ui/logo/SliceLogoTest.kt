package com.juice.ui.logo

import com.juice.core.progression.Flavour
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SliceLogoTest {
    /** Shadow radius of the paint behind every circle drawn. */
    private fun circleShadowRadii(shadow: Boolean): List<Float> {
        val radii = mutableListOf<Float>()
        val canvas = object : Canvas() {
            override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) { radii += org.robolectric.Shadows.shadowOf(paint).shadowRadius }
        }
        SliceLogoPainter().draw(canvas, 0f, 0f, 100f, SliceStyle.of(Flavour.ELECTRIC_CITRUS), 8, 0, shadow)
        return radii
    }

    @Test fun theBackdropSliceCanBeDrawnWithoutAShadow() {
        assertTrue(circleShadowRadii(shadow = false).all { it == 0f })
    }

    @Test fun theSliceHasASoftShadowByDefault() {
        assertTrue(circleShadowRadii(shadow = true).first() > 0f)
    }

    @Test fun eachOpenItemIsASliceUpToAWholeFruit() {
        assertEquals(0, SliceLogoPainter.sliceCount(0))
        assertEquals(3, SliceLogoPainter.sliceCount(3))
        assertEquals(8, SliceLogoPainter.sliceCount(8))
        assertEquals(8, SliceLogoPainter.sliceCount(25))
    }

    @Test fun eachFlavourHasItsOwnFruit() {
        assertEquals(RimShape.Round, SliceStyle.of(Flavour.ELECTRIC_CITRUS).rimShape)
        assertNull(SliceStyle.of(Flavour.ELECTRIC_CITRUS).seed)
        assertEquals(0xFFFDE7F3.toInt(), SliceStyle.of(Flavour.BERRY_SURGE).seed)
        assertEquals(RimShape.Scales, SliceStyle.of(Flavour.DRAGONFRUIT_OBSIDIAN).rimShape)
        assertEquals(RimShape.Honeycomb, SliceStyle.of(Flavour.GOLDEN_HONEY).rimShape)
    }
}
