package com.towhid.ludo.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.towhid.ludo.game.engine.GameEngine
import com.towhid.ludo.game.model.GameState
import com.towhid.ludo.game.model.PlayerColor
import com.towhid.ludo.game.model.PowerType
import com.towhid.ludo.game.model.Token
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class Cell(val row: Int, val col: Int)
private data class Point(val x: Float, val y: Float)
private data class DrawToken(
    val color: PlayerColor,
    val tokenId: Int,
    val progress: Int,
    val point: Point,
    val protected: Boolean
)

/** Clockwise 52-cell Ludo ring on a standard 15x15 board. */
private val ringCells = listOf(
    Cell(6, 1), Cell(6, 2), Cell(6, 3), Cell(6, 4), Cell(6, 5),
    Cell(5, 6), Cell(4, 6), Cell(3, 6), Cell(2, 6), Cell(1, 6), Cell(0, 6),
    Cell(0, 7), Cell(0, 8), Cell(1, 8), Cell(2, 8), Cell(3, 8), Cell(4, 8), Cell(5, 8),
    Cell(6, 9), Cell(6, 10), Cell(6, 11), Cell(6, 12), Cell(6, 13), Cell(6, 14),
    Cell(7, 14), Cell(8, 14), Cell(8, 13), Cell(8, 12), Cell(8, 11), Cell(8, 10), Cell(8, 9),
    Cell(9, 8), Cell(10, 8), Cell(11, 8), Cell(12, 8), Cell(13, 8), Cell(14, 8),
    Cell(14, 7), Cell(14, 6), Cell(13, 6), Cell(12, 6), Cell(11, 6), Cell(10, 6), Cell(9, 6),
    Cell(8, 5), Cell(8, 4), Cell(8, 3), Cell(8, 2), Cell(8, 1), Cell(8, 0), Cell(7, 0), Cell(6, 0)
)

private val homeLanes = mapOf(
    PlayerColor.RED to listOf(Cell(7, 1), Cell(7, 2), Cell(7, 3), Cell(7, 4), Cell(7, 5)),
    PlayerColor.GREEN to listOf(Cell(1, 7), Cell(2, 7), Cell(3, 7), Cell(4, 7), Cell(5, 7)),
    PlayerColor.YELLOW to listOf(Cell(7, 13), Cell(7, 12), Cell(7, 11), Cell(7, 10), Cell(7, 9)),
    PlayerColor.BLUE to listOf(Cell(13, 7), Cell(12, 7), Cell(11, 7), Cell(10, 7), Cell(9, 7))
)

private val baseSpots = mapOf(
    PlayerColor.RED to listOf(Point(2f, 2f), Point(4f, 2f), Point(2f, 4f), Point(4f, 4f)),
    PlayerColor.GREEN to listOf(Point(11f, 2f), Point(13f, 2f), Point(11f, 4f), Point(13f, 4f)),
    PlayerColor.YELLOW to listOf(Point(11f, 11f), Point(13f, 11f), Point(11f, 13f), Point(13f, 13f)),
    PlayerColor.BLUE to listOf(Point(2f, 11f), Point(4f, 11f), Point(2f, 13f), Point(4f, 13f))
)

private val boardInk = Color(0xFF2E3135)
private val boardPaper = Color(0xFFFFFEFA)
private val powerGold = Color(0xFFFFC933)
private val powerGoldSoft = Color(0xFFFFF3C7)
private val shieldGold = Color(0xFFFFB300)

private fun colorOf(color: PlayerColor): Color = when (color) {
    PlayerColor.RED -> Color(0xFFE53935)
    PlayerColor.GREEN -> Color(0xFF2E9D57)
    PlayerColor.YELLOW -> Color(0xFFF4B522)
    PlayerColor.BLUE -> Color(0xFF2474D8)
}

@Composable
fun LudoBoard(
    state: GameState,
    selectableTokenIds: Set<Int>,
    onTokenTap: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = renderedTokens(state)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        val unit = maxWidth / 15f

        Canvas(modifier = Modifier.fillMaxSize()) {
            val pxUnit = size.width / 15f
            drawRect(boardPaper)

            drawBase(PlayerColor.RED, col = 0, row = 0, unit = pxUnit)
            drawBase(PlayerColor.GREEN, col = 9, row = 0, unit = pxUnit)
            drawBase(PlayerColor.YELLOW, col = 9, row = 9, unit = pxUnit)
            drawBase(PlayerColor.BLUE, col = 0, row = 9, unit = pxUnit)

            ringCells.forEachIndexed { index, cell ->
                val startColor = PlayerColor.entries.firstOrNull { it.startIndex == index }
                val power = GameEngine.powerCells[index]
                val fill = when {
                    startColor != null -> colorOf(startColor).copy(alpha = 0.72f)
                    power != null -> powerGoldSoft
                    else -> Color.White
                }
                drawCell(cell, pxUnit, fill)

                if (index in GameEngine.safeRingIndexes) {
                    drawSafeStar(
                        center = Offset((cell.col + 0.5f) * pxUnit, (cell.row + 0.5f) * pxUnit),
                        radius = pxUnit * 0.20f,
                        color = if (startColor != null) Color.White else Color(0xFF7A7D81)
                    )
                }

                if (power != null) {
                    drawPowerMedallion(power, cell, pxUnit)
                }
            }

            homeLanes.forEach { (color, cells) ->
                cells.forEachIndexed { index, cell ->
                    drawCell(cell, pxUnit, colorOf(color).copy(alpha = if (index == cells.lastIndex) 0.74f else 0.52f))
                }
            }

            drawHomeCenter(pxUnit)
            drawBoardBorder(pxUnit)
        }

        tokens.forEach { token ->
            AnimatedToken(
                token = token,
                unit = unit,
                selectable = token.color == state.activeColor && token.tokenId in selectableTokenIds,
                onTap = { onTokenTap(token.tokenId) }
            )
        }
    }
}

@Composable
private fun AnimatedToken(
    token: DrawToken,
    unit: Dp,
    selectable: Boolean,
    onTap: () -> Unit
) {
    val animatedX = remember(token.color, token.tokenId) { Animatable(token.point.x) }
    val animatedY = remember(token.color, token.tokenId) { Animatable(token.point.y) }
    val scale = remember(token.color, token.tokenId) { Animatable(1f) }
    var previousProgress by remember(token.color, token.tokenId) { mutableIntStateOf(token.progress) }

    LaunchedEffect(token.progress, token.point.x, token.point.y) {
        val fromProgress = previousProgress
        previousProgress = token.progress

        suspend fun animateToPoint(point: Point, durationMillis: Int) = coroutineScope {
            launch { animatedX.animateTo(point.x, tween(durationMillis)) }
            launch { animatedY.animateTo(point.y, tween(durationMillis)) }
        }

        when {
            token.progress == fromProgress -> {
                animateToPoint(token.point, 130)
            }

            token.progress == Token.HOME && fromProgress != Token.HOME -> {
                scale.animateTo(0.18f, tween(100))
                animatedX.snapTo(token.point.x)
                animatedY.snapTo(token.point.y)
                scale.animateTo(1f, tween(170))
            }

            token.progress > fromProgress && fromProgress >= Token.HOME -> {
                val firstStep = if (fromProgress == Token.HOME) 0 else fromProgress + 1
                if (firstStep <= token.progress) {
                    for (progress in firstStep..token.progress) {
                        animateToPoint(
                            point = pointForProgress(token.color, token.tokenId, progress),
                            durationMillis = 70
                        )
                    }
                }
                animateToPoint(token.point, 90)
            }

            else -> animateToPoint(token.point, 190)
        }
    }

    val tokenSize = unit * 0.88f
    Canvas(
        modifier = Modifier
            .offset(
                x = unit * animatedX.value - tokenSize / 2f,
                y = unit * animatedY.value - tokenSize / 2f
            )
            .size(tokenSize)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clickable(enabled = selectable, onClick = onTap)
    ) {
        drawTokenPiece(
            color = token.color,
            protected = token.protected,
            selectable = selectable
        )
    }
}

private fun DrawScope.drawBase(
    color: PlayerColor,
    col: Int,
    row: Int,
    unit: Float
) {
    val baseColor = colorOf(color)
    drawRect(
        color = baseColor.copy(alpha = 0.92f),
        topLeft = Offset(col * unit, row * unit),
        size = Size(6f * unit, 6f * unit)
    )

    drawRoundRect(
        color = Color.White,
        topLeft = Offset((col + 0.75f) * unit, (row + 0.75f) * unit),
        size = Size(4.5f * unit, 4.5f * unit),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(unit * 0.34f)
    )

    val spots = baseSpots.getValue(color)
    spots.forEach { spot ->
        drawCircle(
            color = baseColor.copy(alpha = 0.16f),
            radius = unit * 0.72f,
            center = Offset(spot.x * unit, spot.y * unit)
        )
        drawCircle(
            color = baseColor,
            radius = unit * 0.72f,
            center = Offset(spot.x * unit, spot.y * unit),
            style = Stroke(width = unit * 0.09f)
        )
    }
}

private fun DrawScope.drawCell(cell: Cell, unit: Float, fill: Color) {
    val topLeft = Offset(cell.col * unit, cell.row * unit)
    drawRect(color = fill, topLeft = topLeft, size = Size(unit, unit))
    drawRect(
        color = boardInk.copy(alpha = 0.72f),
        topLeft = topLeft,
        size = Size(unit, unit),
        style = Stroke(width = (unit * 0.035f).coerceAtLeast(1f))
    )
}

private fun DrawScope.drawPowerMedallion(type: PowerType, cell: Cell, unit: Float) {
    val center = Offset((cell.col + 0.5f) * unit, (cell.row + 0.5f) * unit)
    drawCircle(
        color = powerGold,
        radius = unit * 0.34f,
        center = center
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.78f),
        radius = unit * 0.34f,
        center = center,
        style = Stroke(width = unit * 0.055f)
    )
    drawPowerIcon(
        type = type,
        center = center,
        iconSize = unit * 0.54f,
        tint = boardInk
    )
}

private fun DrawScope.drawHomeCenter(unit: Float) {
    val left = 6f * unit
    val top = 6f * unit
    val right = 9f * unit
    val bottom = 9f * unit
    val center = Offset(7.5f * unit, 7.5f * unit)

    drawTriangle(
        color = colorOf(PlayerColor.RED),
        first = Offset(left, top),
        second = Offset(left, bottom),
        third = center
    )
    drawTriangle(
        color = colorOf(PlayerColor.GREEN),
        first = Offset(left, top),
        second = Offset(right, top),
        third = center
    )
    drawTriangle(
        color = colorOf(PlayerColor.YELLOW),
        first = Offset(right, top),
        second = Offset(right, bottom),
        third = center
    )
    drawTriangle(
        color = colorOf(PlayerColor.BLUE),
        first = Offset(left, bottom),
        second = Offset(right, bottom),
        third = center
    )

    drawRect(
        color = boardInk.copy(alpha = 0.82f),
        topLeft = Offset(left, top),
        size = Size(3f * unit, 3f * unit),
        style = Stroke(width = unit * 0.055f)
    )
}

private fun DrawScope.drawTriangle(color: Color, first: Offset, second: Offset, third: Offset) {
    val path = Path().apply {
        moveTo(first.x, first.y)
        lineTo(second.x, second.y)
        lineTo(third.x, third.y)
        close()
    }
    drawPath(path = path, color = color.copy(alpha = 0.88f))
    drawPath(
        path = path,
        color = boardInk.copy(alpha = 0.58f),
        style = Stroke(width = 1.5f)
    )
}

private fun DrawScope.drawSafeStar(center: Offset, radius: Float, color: Color) {
    val inner = radius * 0.44f
    val points = 10
    val path = Path()
    repeat(points) { index ->
        val angle = -PI / 2 + index * PI / 5
        val r = if (index % 2 == 0) radius else inner
        val point = Offset(
            x = center.x + (cos(angle) * r).toFloat(),
            y = center.y + (sin(angle) * r).toFloat()
        )
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawBoardBorder(unit: Float) {
    drawRect(
        color = boardInk,
        topLeft = Offset.Zero,
        size = Size(15f * unit, 15f * unit),
        style = Stroke(width = (unit * 0.085f).coerceAtLeast(2f))
    )
}

private fun DrawScope.drawTokenPiece(
    color: PlayerColor,
    protected: Boolean,
    selectable: Boolean
) {
    val side = size.minDimension
    val center = Offset(size.width / 2f, size.height / 2f)
    val tokenColor = colorOf(color)

    drawCircle(
        color = Color.Black.copy(alpha = 0.18f),
        radius = side * 0.38f,
        center = center + Offset(side * 0.04f, side * 0.06f)
    )

    if (selectable) {
        drawCircle(
            color = Color.White,
            radius = side * 0.49f,
            center = center,
            style = Stroke(width = side * 0.10f)
        )
        drawCircle(
            color = boardInk,
            radius = side * 0.45f,
            center = center,
            style = Stroke(width = side * 0.06f)
        )
    }

    if (protected) {
        drawCircle(
            color = shieldGold,
            radius = side * 0.44f,
            center = center,
            style = Stroke(width = side * 0.09f)
        )
    }

    drawCircle(color = tokenColor, radius = side * 0.35f, center = center)
    drawCircle(
        color = Color.White.copy(alpha = 0.92f),
        radius = side * 0.35f,
        center = center,
        style = Stroke(width = side * 0.06f)
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.34f),
        radius = side * 0.11f,
        center = center + Offset(-side * 0.10f, -side * 0.10f)
    )

    if (protected) {
        val badgeCenter = center + Offset(side * 0.30f, -side * 0.30f)
        drawCircle(Color.White, side * 0.16f, badgeCenter)
        drawPowerIcon(
            type = PowerType.PROTECT,
            center = badgeCenter,
            iconSize = side * 0.25f,
            tint = shieldGold
        )
    }
}

private fun renderedTokens(state: GameState): List<DrawToken> {
    val raw = buildList {
        state.players.forEach { player ->
            player.tokens.forEach { token ->
                val point = pointForProgress(player.color, token.id, token.progress)
                add(DrawToken(player.color, token.id, token.progress, point, token.protected))
            }
        }
    }

    val grouped = raw.groupBy { Pair(it.point.x, it.point.y) }
    return raw.map { token ->
        val group = grouped.getValue(Pair(token.point.x, token.point.y))
        if (group.size == 1) token
        else {
            val index = group.indexOf(token)
            val offsets = listOf(
                Point(-0.16f, -0.16f), Point(0.16f, -0.16f),
                Point(-0.16f, 0.16f), Point(0.16f, 0.16f),
                Point(-0.24f, 0f), Point(0.24f, 0f), Point(0f, -0.24f), Point(0f, 0.24f)
            )
            val offset = offsets[index % offsets.size]
            token.copy(point = Point(token.point.x + offset.x, token.point.y + offset.y))
        }
    }
}

private fun pointForProgress(color: PlayerColor, tokenId: Int, progress: Int): Point = when {
    progress == Token.HOME -> baseSpots.getValue(color)[tokenId]
    progress == Token.FINISH -> finishSpot(color, tokenId)
    progress in 0..50 -> {
        val cell = ringCells[GameEngine.ringIndex(color, progress)]
        Point(cell.col + 0.5f, cell.row + 0.5f)
    }
    progress in 51 until Token.FINISH -> {
        val cell = homeLanes.getValue(color)[progress - 51]
        Point(cell.col + 0.5f, cell.row + 0.5f)
    }
    else -> error("Unsupported token progress: $progress")
}

private fun finishSpot(color: PlayerColor, tokenId: Int): Point {
    val offsets = listOf(
        Point(-0.35f, -0.35f),
        Point(0.35f, -0.35f),
        Point(-0.35f, 0.35f),
        Point(0.35f, 0.35f)
    )
    val center = when (color) {
        PlayerColor.RED -> Point(6.86f, 7.5f)
        PlayerColor.GREEN -> Point(7.5f, 6.86f)
        PlayerColor.YELLOW -> Point(8.14f, 7.5f)
        PlayerColor.BLUE -> Point(7.5f, 8.14f)
    }
    val offset = offsets[tokenId]
    return Point(center.x + offset.x * 0.34f, center.y + offset.y * 0.34f)
}
