package com.juice.ui.liquid

import android.graphics.Canvas
import android.graphics.Paint

/**
 * Up to [capacity] square sprites drawn with one [Canvas.drawVertices] call instead of one draw each. Each quad
 * maps the whole of a [texSize]-pixel texture (the paint's BitmapShader), and its vertex colour modulates it.
 */
internal class QuadBatch(private val capacity: Int, private val texSize: Float) {
    private val verts = FloatArray(capacity * 8)
    private val texs = FloatArray(capacity * 8)
    private val colors = IntArray(capacity * 4)
    private val indices = ShortArray(capacity * 6)
    var size = 0; private set

    init {
        for (q in 0 until capacity) {
            val t = q * 8
            texs[t] = 0f; texs[t + 1] = 0f; texs[t + 2] = texSize; texs[t + 3] = 0f
            texs[t + 4] = texSize; texs[t + 5] = texSize; texs[t + 6] = 0f; texs[t + 7] = texSize
            val v = (q * 4).toShort(); val i = q * 6
            indices[i] = v; indices[i + 1] = (v + 1).toShort(); indices[i + 2] = (v + 2).toShort()
            indices[i + 3] = v; indices[i + 4] = (v + 2).toShort(); indices[i + 5] = (v + 3).toShort()
        }
    }

    fun clear() { size = 0 }

    /** Adds a square of half-width [r] centred on ([x], [y]), tinted [color]. Ignored once full. */
    fun add(x: Float, y: Float, r: Float, color: Int) {
        if (size >= capacity) return
        val v = size * 8
        verts[v] = x - r; verts[v + 1] = y - r; verts[v + 2] = x + r; verts[v + 3] = y - r
        verts[v + 4] = x + r; verts[v + 5] = y + r; verts[v + 6] = x - r; verts[v + 7] = y + r
        val c = size * 4
        colors[c] = color; colors[c + 1] = color; colors[c + 2] = color; colors[c + 3] = color
        size++
    }

    fun draw(canvas: Canvas, paint: Paint) {
        if (size == 0) return
        canvas.drawVertices(Canvas.VertexMode.TRIANGLES, size * 8, verts, 0, texs, 0, colors, 0, indices, 0, size * 6, paint)
    }

    /** The corners of quad [q] as x0, y0 .. x3, y3, for tests. */
    internal fun quad(q: Int) = verts.copyOfRange(q * 8, q * 8 + 8)
    internal fun color(q: Int) = colors[q * 4]
}
