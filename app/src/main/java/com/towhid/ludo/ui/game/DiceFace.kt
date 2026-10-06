package com.towhid.ludo.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun DiceFace(
    value: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 58.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val side = this.size.minDimension
        val faceSide = side * 0.90f
        val corner = CornerRadius(side * 0.19f)
        val stroke = (side * 0.030f).coerceAtLeast(1f)
        val faceTopLeft = Offset(side * 0.04f, side * 0.03f)

        // Soft arcade-style blue shadow, then a glossy white die face.
        drawRoundRect(
            color = Color(0xFF10163C).copy(alpha = 0.42f),
            topLeft = Offset(side * 0.07f, side * 0.09f),
            size = Size(faceSide, faceSide),
            cornerRadius = corner
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.White, Color(0xFFF1F5FB), Color(0xFFDCE5F0)),
                start = faceTopLeft,
                end = Offset(side * 0.92f, side * 0.92f)
            ),
            topLeft = faceTopLeft,
            size = Size(faceSide, faceSide),
            cornerRadius = corner
        )
        drawRoundRect(
            color = Color(0xFFB9C7D8),
            topLeft = faceTopLeft,
            size = Size(faceSide, faceSide),
            cornerRadius = corner,
            style = Stroke(width = stroke)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.86f),
            topLeft = Offset(side * 0.11f, side * 0.08f),
            size = Size(side * 0.68f, side * 0.18f),
            cornerRadius = CornerRadius(side * 0.09f),
            style = Stroke(width = side * 0.018f)
        )

        val ink = Color(0xFF252A42)
        if (value == null || value !in 1..6) {
            drawCircle(ink.copy(alpha = 0.35f), side * 0.045f, Offset(side * 0.37f, side * 0.48f))
            drawCircle(ink.copy(alpha = 0.35f), side * 0.045f, Offset(side * 0.50f, side * 0.48f))
            drawCircle(ink.copy(alpha = 0.35f), side * 0.045f, Offset(side * 0.63f, side * 0.48f))
            return@Canvas
        }

        val left = side * 0.28f
        val middle = side * 0.49f
        val right = side * 0.70f
        val top = side * 0.27f
        val center = side * 0.49f
        val bottom = side * 0.70f
        val radius = side * 0.061f

        fun pip(x: Float, y: Float) {
            drawCircle(Color.Black.copy(alpha = 0.12f), radius * 1.08f, Offset(x + side * 0.012f, y + side * 0.016f))
            drawCircle(ink, radius, Offset(x, y))
        }

        when (value) {
            1 -> pip(middle, center)
            2 -> { pip(left, top); pip(right, bottom) }
            3 -> { pip(left, top); pip(middle, center); pip(right, bottom) }
            4 -> { pip(left, top); pip(right, top); pip(left, bottom); pip(right, bottom) }
            5 -> { pip(left, top); pip(right, top); pip(middle, center); pip(left, bottom); pip(right, bottom) }
            6 -> { pip(left, top); pip(right, top); pip(left, center); pip(right, center); pip(left, bottom); pip(right, bottom) }
        }
    }
}
