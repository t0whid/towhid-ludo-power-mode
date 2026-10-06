package com.towhid.ludo.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun DiceFace(
    value: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 58.dp
) {
    val surface = MaterialTheme.colorScheme.surface
    val ink = MaterialTheme.colorScheme.onSurface
    val border = MaterialTheme.colorScheme.outline

    Canvas(modifier = modifier.size(size)) {
        val side = this.size.minDimension
        val stroke = (side * 0.045f).coerceAtLeast(1f)
        drawRoundRect(
            color = surface,
            size = Size(side, side),
            cornerRadius = CornerRadius(side * 0.20f)
        )
        drawRoundRect(
            color = border,
            size = Size(side, side),
            cornerRadius = CornerRadius(side * 0.20f),
            style = Stroke(width = stroke)
        )

        if (value == null || value !in 1..6) {
            drawLine(
                color = ink.copy(alpha = 0.55f),
                start = Offset(side * 0.36f, side * 0.50f),
                end = Offset(side * 0.64f, side * 0.50f),
                strokeWidth = stroke * 1.3f
            )
            return@Canvas
        }

        val left = side * 0.29f
        val middle = side * 0.50f
        val right = side * 0.71f
        val top = side * 0.29f
        val center = side * 0.50f
        val bottom = side * 0.71f
        val radius = side * 0.065f

        fun pip(x: Float, y: Float) = drawCircle(ink, radius, Offset(x, y))

        when (value) {
            1 -> pip(middle, center)
            2 -> {
                pip(left, top)
                pip(right, bottom)
            }
            3 -> {
                pip(left, top)
                pip(middle, center)
                pip(right, bottom)
            }
            4 -> {
                pip(left, top)
                pip(right, top)
                pip(left, bottom)
                pip(right, bottom)
            }
            5 -> {
                pip(left, top)
                pip(right, top)
                pip(middle, center)
                pip(left, bottom)
                pip(right, bottom)
            }
            6 -> {
                pip(left, top)
                pip(right, top)
                pip(left, center)
                pip(right, center)
                pip(left, bottom)
                pip(right, bottom)
            }
        }
    }
}
