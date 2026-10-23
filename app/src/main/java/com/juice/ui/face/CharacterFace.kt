package com.juice.ui.face

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.juice.core.character.Expression
import com.juice.core.character.Face
import com.juice.core.character.Shape

/** A character's [face] drawn from vector shapes in an [expression]; it fills the smaller side of its bounds. */
@Composable
fun CharacterFace(face: Face, expression: Expression, modifier: Modifier = Modifier, color: Color = Color.White) {
    val geometry = FaceGeometry.of(face, expression)
    Canvas(modifier.size(96.dp)) {
        val side = minOf(size.width, size.height)
        listOf(geometry.leftEye, geometry.rightEye, geometry.mouth).forEach { drawPlacement(it, side, color) }
    }
}

private fun DrawScope.drawPlacement(p: ShapePlacement, side: Float, color: Color) {
    val center = Offset(p.centerX * side, p.centerY * side)
    val w = p.width * side
    val h = p.height * side
    val topLeft = Offset(center.x - w / 2, center.y - h / 2)
    rotate(p.rotation, pivot = center) {
        when (p.shape) {
            Shape.SQUARE -> drawRect(color, topLeft, Size(w, h))
            Shape.CIRCLE -> drawOval(color, topLeft, Size(w, h))
            Shape.TRIANGLE -> drawPath(
                Path().apply {
                    moveTo(center.x, topLeft.y)
                    lineTo(topLeft.x + w, topLeft.y + h)
                    lineTo(topLeft.x, topLeft.y + h)
                    close()
                },
                color,
            )
        }
    }
}
