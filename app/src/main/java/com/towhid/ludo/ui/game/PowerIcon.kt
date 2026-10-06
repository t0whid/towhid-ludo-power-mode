package com.towhid.ludo.ui.game

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.towhid.ludo.game.model.PowerType

@Composable
fun PowerIcon(
    type: PowerType,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = Color(0xFF202020)
) {
    Canvas(modifier = modifier.size(size)) {
        drawPowerIcon(
            type = type,
            center = Offset(this.size.width / 2f, this.size.height / 2f),
            iconSize = this.size.minDimension * 0.88f,
            tint = tint
        )
    }
}

fun DrawScope.drawPowerIcon(
    type: PowerType,
    center: Offset,
    iconSize: Float,
    tint: Color
) {
    when (type) {
        PowerType.DOUBLE -> drawDoubleIcon(center, iconSize, tint)
        PowerType.CHOOSE_ROLL -> drawChooseRollIcon(center, iconSize, tint)
        PowerType.PROTECT -> drawProtectIcon(center, iconSize, tint)
        PowerType.EXTRA_ROLL -> drawExtraRollIcon(center, iconSize, tint)
    }
}

private fun DrawScope.drawDoubleIcon(center: Offset, iconSize: Float, tint: Color) {
    val paint = Paint().apply {
        color = tint.toArgbCompat()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = iconSize * 0.62f
    }
    drawContext.canvas.nativeCanvas.drawText(
        "×2",
        center.x,
        center.y - (paint.ascent() + paint.descent()) / 2f,
        paint
    )
}

private fun DrawScope.drawChooseRollIcon(center: Offset, iconSize: Float, tint: Color) {
    val side = iconSize * 0.76f
    val left = center.x - side / 2f
    val top = center.y - side / 2f
    val stroke = (iconSize * 0.075f).coerceAtLeast(1f)

    drawRoundRect(
        color = tint,
        topLeft = Offset(left, top),
        size = Size(side, side),
        cornerRadius = CornerRadius(side * 0.18f),
        style = Stroke(width = stroke)
    )

    val pipRadius = iconSize * 0.06f
    val delta = side * 0.23f
    listOf(
        Offset(center.x - delta, center.y - delta),
        center,
        Offset(center.x + delta, center.y + delta)
    ).forEach { drawCircle(tint, pipRadius, it) }
}

private fun DrawScope.drawProtectIcon(center: Offset, iconSize: Float, tint: Color) {
    val half = iconSize / 2f
    val path = Path().apply {
        moveTo(center.x, center.y - half * 0.82f)
        lineTo(center.x + half * 0.68f, center.y - half * 0.50f)
        lineTo(center.x + half * 0.56f, center.y + half * 0.24f)
        quadraticBezierTo(center.x + half * 0.40f, center.y + half * 0.72f, center.x, center.y + half * 0.92f)
        quadraticBezierTo(center.x - half * 0.40f, center.y + half * 0.72f, center.x - half * 0.56f, center.y + half * 0.24f)
        lineTo(center.x - half * 0.68f, center.y - half * 0.50f)
        close()
    }
    drawPath(path, tint, style = Stroke(width = (iconSize * 0.075f).coerceAtLeast(1f)))

    val checkStroke = (iconSize * 0.08f).coerceAtLeast(1f)
    drawLine(
        color = tint,
        start = Offset(center.x - half * 0.28f, center.y + half * 0.02f),
        end = Offset(center.x - half * 0.06f, center.y + half * 0.25f),
        strokeWidth = checkStroke,
        cap = StrokeCap.Round
    )
    drawLine(
        color = tint,
        start = Offset(center.x - half * 0.06f, center.y + half * 0.25f),
        end = Offset(center.x + half * 0.34f, center.y - half * 0.22f),
        strokeWidth = checkStroke,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawExtraRollIcon(center: Offset, iconSize: Float, tint: Color) {
    val radius = iconSize * 0.33f
    val stroke = (iconSize * 0.075f).coerceAtLeast(1f)
    val rect = Rect(
        center = center,
        radius = radius
    )
    drawArc(
        color = tint,
        startAngle = -55f,
        sweepAngle = 285f,
        useCenter = false,
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(width = stroke, cap = StrokeCap.Round)
    )

    val tip = Offset(center.x + radius * 0.82f, center.y - radius * 0.42f)
    drawLine(tint, tip, Offset(tip.x - radius * 0.48f, tip.y - radius * 0.05f), stroke, StrokeCap.Round)
    drawLine(tint, tip, Offset(tip.x - radius * 0.12f, tip.y + radius * 0.43f), stroke, StrokeCap.Round)

    val plus = iconSize * 0.18f
    drawLine(
        tint,
        Offset(center.x - plus, center.y),
        Offset(center.x + plus, center.y),
        stroke,
        StrokeCap.Round
    )
    drawLine(
        tint,
        Offset(center.x, center.y - plus),
        Offset(center.x, center.y + plus),
        stroke,
        StrokeCap.Round
    )
}

private fun Color.toArgbCompat(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt()
)
