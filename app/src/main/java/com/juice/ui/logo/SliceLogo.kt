package com.juice.ui.logo

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.juice.core.progression.Flavour
import kotlin.math.cos
import kotlin.math.sin

/** The rim around a flavour's slice: a round peel, dragonfruit scales or a honeycomb. */
enum class RimShape { Round, Scales, Honeycomb }

/** Colours (ARGB) of a flavour's slice; [seed] is null for a seedless fruit. */
data class SliceStyle(
    val flesh: Int,
    val rim: Int,
    val segment: Int,
    val highlight: Int,
    val seed: Int?,
    val rimShape: RimShape,
) {
    companion object {
        fun of(flavour: Flavour): SliceStyle = when (flavour) {
            Flavour.ELECTRIC_CITRUS -> SliceStyle(0xFFFFF1CF.toInt(), 0xFFEA580C.toInt(), 0xFFF97316.toInt(), 0xFFFFB347.toInt(), null, RimShape.Round)
            Flavour.BERRY_SURGE -> SliceStyle(0xFFFDE7F3.toInt(), 0xFF9D174D.toInt(), 0xFFDB2777.toInt(), 0xFFF472B6.toInt(), 0xFFFDE7F3.toInt(), RimShape.Round)
            Flavour.DRAGONFRUIT_OBSIDIAN -> SliceStyle(0xFF150A30.toInt(), 0xFFA78BFA.toInt(), 0xFFE3DAFF.toInt(), 0xFFFFFFFF.toInt(), 0xFF150A30.toInt(), RimShape.Scales)
            Flavour.GOLDEN_HONEY -> SliceStyle(0xFF2B1D05.toInt(), 0xFFF5C542.toInt(), 0xFFD69E2E.toInt(), 0xFFFFE08A.toInt(), null, RimShape.Honeycomb)
        }
    }
}

/**
 * Juice's logo and handle: a fruit slice whose segments are the open items. Eight or more fill
 * the fruit; fewer leave that many segments. The [active] one (the focused card) pops out.
 */
class SliceLogoPainter {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val oval = RectF()
    private val scaleDash = DashPathEffect(floatArrayOf(13f, 4.28f), 0f)

    /** Draws the logo [size] px across, centred on ([cx], [cy]), with a soft drop shadow unless [shadow] is off. */
    fun draw(canvas: Canvas, cx: Float, cy: Float, size: Float, style: SliceStyle, slices: Int, active: Int, shadow: Boolean = true) {
        canvas.save()
        canvas.translate(cx - size / 2, cy - size / 2)
        canvas.scale(size / VIEW, size / VIEW)

        fill.color = style.flesh
        if (shadow) fill.setShadowLayer(14f, 0f, 10f, SHADOW)
        canvas.drawCircle(C, C, 58f, fill)
        fill.clearShadowLayer()

        stroke.color = style.rim
        if (style.rimShape == RimShape.Honeycomb) {
            stroke.strokeWidth = 6f
            stroke.pathEffect = null
            path.rewind()
            path.moveTo(60f, 12f)
            path.lineTo(101.6f, 36f)
            path.lineTo(101.6f, 84f)
            path.lineTo(60f, 108f)
            path.lineTo(18.4f, 84f)
            path.lineTo(18.4f, 36f)
            path.close()
            canvas.drawPath(path, stroke)
        } else {
            val scales = style.rimShape == RimShape.Scales
            stroke.strokeWidth = if (scales) 8f else 6f
            stroke.pathEffect = if (scales) scaleDash else null
            canvas.drawCircle(C, C, 44f, stroke)
        }

        for (i in 0 until sliceCount(slices)) {
            val a0 = -90f + i * 45f + GAP
            val a1 = -90f + (i + 1) * 45f - GAP
            val mid = Math.toRadians(((a0 + a1) / 2).toDouble())
            val pop = i == active
            canvas.save()
            if (pop) canvas.translate(3f * cos(mid).toFloat(), 3f * sin(mid).toFloat())
            path.rewind()
            oval.set(C - OUTER, C - OUTER, C + OUTER, C + OUTER)
            path.arcTo(oval, a0, a1 - a0, true)
            oval.set(C - INNER, C - INNER, C + INNER, C + INNER)
            path.arcTo(oval, a1, a0 - a1)
            path.close()
            fill.color = if (pop) style.highlight else style.segment
            canvas.drawPath(path, fill)
            style.seed?.let {
                fill.color = it
                canvas.drawCircle(C + 24f * cos(mid).toFloat(), C + 24f * sin(mid).toFloat(), 2.4f, fill)
            }
            canvas.restore()
        }

        fill.color = style.flesh
        canvas.drawCircle(C, C, 5f, fill)
        canvas.restore()
    }

    /**
     * The swipe ring shown while the handle is pressed: a dashed circle of [radius] px around
     * ([cx], [cy]) that fills as the press travels ([pull], 0..1) and glows [accent] once past it.
     */
    fun drawRing(canvas: Canvas, cx: Float, cy: Float, radius: Float, pull: Float, ready: Boolean, accent: Int, density: Float) {
        fill.color = RING_WASH
        canvas.drawCircle(cx, cy, radius, fill)
        fill.color = RING_FILL
        canvas.drawCircle(cx, cy, radius * pull.coerceIn(0f, 1f), fill)
        stroke.strokeWidth = 2 * density
        stroke.pathEffect = DashPathEffect(floatArrayOf(6 * density, 5 * density), 0f)
        stroke.color = if (ready) accent else RING_EDGE
        if (ready) stroke.setShadowLayer(12 * density, 0f, 0f, accent)
        canvas.drawCircle(cx, cy, radius - density, stroke)
        stroke.clearShadowLayer()
        stroke.pathEffect = null
    }

    companion object {
        /** Segments in a whole fruit. */
        const val SLICES = 8

        /** Segments shown for [openItems] open items. */
        fun sliceCount(openItems: Int): Int = openItems.coerceIn(0, SLICES)

        private const val VIEW = 120f
        private const val C = 60f
        private const val INNER = 7f
        private const val OUTER = 37f
        private const val GAP = 2.5f
        private const val SHADOW = 0x66000000
        private const val RING_WASH = 0x14FFFFFF
        private const val RING_FILL = 0x33FFFFFF
        private const val RING_EDGE = 0x99FFFFFF.toInt()
    }
}
