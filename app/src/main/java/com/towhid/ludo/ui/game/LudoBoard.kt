package com.towhid.ludo.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.Brush
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

private val boardInk = Color(0xFF748293)
private val boardPaper = Color(0xFFF7FAFD)
private val powerGold = Color(0xFF3B78C7)
private val powerGoldSoft = Color(0xFFF7FAFD)
private val shieldGold = Color(0xFFFFC83D)

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
                val power = state.powerCells[index]
                val fill = when {
                    startColor != null -> gameColor(startColor).copy(alpha = 0.88f)
                    power != null -> powerGoldSoft
                    else -> Color.White
                }
                drawCell(cell, pxUnit, fill)

                if (index in GameEngine.safeRingIndexes) {
                    val center = Offset((cell.col + 0.5f) * pxUnit, (cell.row + 0.5f) * pxUnit)
                    if (startColor != null) {
                        drawStartArrow(startColor, center, pxUnit * 0.30f, Color.White.copy(alpha = 0.92f))
                    } else {
                        drawSafeStar(
                            center = center,
                            radius = pxUnit * 0.20f,
                            color = Color(0xFF5C6878)
                        )
                    }
                }

            }

            homeLanes.forEach { (color, cells) ->
                cells.forEachIndexed { index, cell ->
                    drawCell(cell, pxUnit, gameColor(color).copy(alpha = if (index == cells.lastIndex) 0.92f else 0.68f))
                }
            }

            drawHomeCenter(pxUnit)
            drawBoardBorder(pxUnit)
        }

        state.powerCells.toSortedMap().forEach { (index, type) ->
            val cell = ringCells.getOrNull(index) ?: return@forEach
            AnimatedPowerPickup(
                type = type,
                unit = unit,
                cell = cell
            )
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
private fun AnimatedPowerPickup(
    type: PowerType,
    unit: Dp,
    cell: Cell
) {
    val appear = remember(cell.row, cell.col, type) { Animatable(0.25f) }
    val pulseTransition = rememberInfiniteTransition(label = "powerPulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(760),
            repeatMode = RepeatMode.Reverse
        ),
        label = "powerScale"
    )
    val spin by pulseTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(980),
            repeatMode = RepeatMode.Reverse
        ),
        label = "powerTilt"
    )

    LaunchedEffect(cell.row, cell.col, type) {
        appear.snapTo(0.25f)
        appear.animateTo(1f, spring(dampingRatio = 0.58f, stiffness = 430f))
    }

    val pickupSize = unit * 0.84f
    Canvas(
        modifier = Modifier
            .offset(
                x = unit * (cell.col + 0.5f) - pickupSize / 2f,
                y = unit * (cell.row + 0.5f) - pickupSize / 2f
            )
            .size(pickupSize)
            .graphicsLayer {
                scaleX = appear.value * pulse
                scaleY = appear.value * pulse
                rotationZ = spin
            }
    ) {
        val c = center
        val r = size.minDimension * 0.46f
        drawCircle(Color.Black.copy(alpha = 0.22f), r, c + Offset(size.width * 0.035f, size.height * 0.055f))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color(0xFF9ED8FF), Color(0xFF4B83D0), Color(0xFF213C82)),
                center = Offset(c.x * 0.78f, c.y * 0.72f),
                radius = r * 1.35f
            ),
            radius = r,
            center = c
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.93f),
            radius = r,
            center = c,
            style = Stroke(width = size.minDimension * 0.055f)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.38f),
            radius = r * 0.66f,
            center = Offset(c.x - r * 0.16f, c.y - r * 0.18f),
            style = Stroke(width = size.minDimension * 0.045f)
        )
        drawPowerIcon(
            type = type,
            center = c,
            iconSize = size.minDimension * 0.56f,
            tint = Color(0xFF17376F)
        )
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

        suspend fun animateToPoint(point: Point, durationMillis: Int, hop: Boolean = false) = coroutineScope {
            launch { animatedX.animateTo(point.x, tween(durationMillis, easing = FastOutSlowInEasing)) }
            launch { animatedY.animateTo(point.y, tween(durationMillis, easing = FastOutSlowInEasing)) }
            if (hop) {
                launch {
                    scale.animateTo(1.13f, tween((durationMillis * 0.42f).toInt().coerceAtLeast(35)))
                    scale.animateTo(1f, tween((durationMillis * 0.58f).toInt().coerceAtLeast(45), easing = FastOutSlowInEasing))
                }
            }
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
                            durationMillis = 82,
                            hop = true
                        )
                    }
                }
                animateToPoint(token.point, 105)
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
    val baseColor = gameColor(color)
    val topLeft = Offset(col * unit, row * unit)
    val baseSize = Size(6f * unit, 6f * unit)

    drawRect(
        color = baseColor.copy(alpha = 0.45f),
        topLeft = topLeft + Offset(unit * 0.06f, unit * 0.08f),
        size = baseSize
    )
    drawRect(color = baseColor, topLeft = topLeft, size = baseSize)

    drawRoundRect(
        color = Color.Black.copy(alpha = 0.12f),
        topLeft = Offset((col + 0.82f) * unit, (row + 0.86f) * unit),
        size = Size(4.42f * unit, 4.42f * unit),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(unit * 0.42f)
    )
    drawRoundRect(
        color = boardPaper,
        topLeft = Offset((col + 0.78f) * unit, (row + 0.78f) * unit),
        size = Size(4.44f * unit, 4.44f * unit),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(unit * 0.42f)
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.82f),
        topLeft = Offset((col + 0.90f) * unit, (row + 0.90f) * unit),
        size = Size(4.20f * unit, 4.20f * unit),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(unit * 0.34f),
        style = Stroke(width = unit * 0.06f)
    )

    baseSpots.getValue(color).forEach { spot ->
        val center = Offset(spot.x * unit, spot.y * unit)
        drawCircle(Color.Black.copy(alpha = 0.12f), unit * 0.76f, center + Offset(unit * 0.04f, unit * 0.06f))
        drawCircle(baseColor.copy(alpha = 0.16f), unit * 0.72f, center)
        drawCircle(baseColor.copy(alpha = 0.86f), unit * 0.72f, center, style = Stroke(width = unit * 0.09f))
        drawCircle(Color.White.copy(alpha = 0.68f), unit * 0.57f, center, style = Stroke(width = unit * 0.035f))
    }
}

private fun DrawScope.drawCell(cell: Cell, unit: Float, fill: Color) {
    val topLeft = Offset(cell.col * unit, cell.row * unit)
    drawRect(color = Color.Black.copy(alpha = 0.055f), topLeft = topLeft + Offset(unit * 0.025f, unit * 0.025f), size = Size(unit, unit))
    drawRect(color = fill, topLeft = topLeft, size = Size(unit, unit))
    drawRect(
        color = boardInk.copy(alpha = 0.48f),
        topLeft = topLeft,
        size = Size(unit, unit),
        style = Stroke(width = (unit * 0.035f).coerceAtLeast(1f))
    )
}

private fun DrawScope.drawHomeCenter(unit: Float) {
    val left = 6f * unit
    val top = 6f * unit
    val right = 9f * unit
    val bottom = 9f * unit
    val center = Offset(7.5f * unit, 7.5f * unit)

    drawTriangle(
        color = gameColor(PlayerColor.RED),
        first = Offset(left, top),
        second = Offset(left, bottom),
        third = center
    )
    drawTriangle(
        color = gameColor(PlayerColor.GREEN),
        first = Offset(left, top),
        second = Offset(right, top),
        third = center
    )
    drawTriangle(
        color = gameColor(PlayerColor.YELLOW),
        first = Offset(right, top),
        second = Offset(right, bottom),
        third = center
    )
    drawTriangle(
        color = gameColor(PlayerColor.BLUE),
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
    drawPath(path = path, color = color)
    drawPath(
        path = path,
        color = boardInk.copy(alpha = 0.58f),
        style = Stroke(width = 1.5f)
    )
}

private fun DrawScope.drawStartArrow(
    color: PlayerColor,
    center: Offset,
    radius: Float,
    tint: Color
) {
    val direction = when (color) {
        PlayerColor.RED -> Offset(1f, 0f)
        PlayerColor.GREEN -> Offset(0f, 1f)
        PlayerColor.YELLOW -> Offset(-1f, 0f)
        PlayerColor.BLUE -> Offset(0f, -1f)
    }
    val normal = Offset(-direction.y, direction.x)
    val tail = Offset(
        center.x - direction.x * radius * 0.72f,
        center.y - direction.y * radius * 0.72f
    )
    val neck = Offset(
        center.x + direction.x * radius * 0.03f,
        center.y + direction.y * radius * 0.03f
    )
    val tip = Offset(
        center.x + direction.x * radius,
        center.y + direction.y * radius
    )
    drawLine(
        color = tint,
        start = tail,
        end = neck,
        strokeWidth = radius * 0.44f
    )
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(neck.x + normal.x * radius * 0.66f, neck.y + normal.y * radius * 0.66f)
        lineTo(neck.x - normal.x * radius * 0.66f, neck.y - normal.y * radius * 0.66f)
        close()
    }
    drawPath(path, tint)
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
        style = Stroke(width = (unit * 0.12f).coerceAtLeast(2f))
    )
    drawRect(
        color = powerGold.copy(alpha = 0.88f),
        topLeft = Offset(unit * 0.08f, unit * 0.08f),
        size = Size(14.84f * unit, 14.84f * unit),
        style = Stroke(width = (unit * 0.035f).coerceAtLeast(1f))
    )
}

private fun DrawScope.drawTokenPiece(
    color: PlayerColor,
    protected: Boolean,
    selectable: Boolean
) {
    val side = size.minDimension
    val center = Offset(size.width / 2f, size.height / 2f)
    val tokenColor = gameColor(color)
    val head = Offset(center.x, center.y - side * 0.12f)

    if (selectable) {
        drawCircle(GameGold.copy(alpha = 0.30f), side * 0.50f, center)
        drawCircle(GameGold, side * 0.46f, center, style = Stroke(width = side * 0.055f))
    }
    if (protected) {
        drawCircle(shieldGold.copy(alpha = 0.22f), side * 0.49f, center)
        drawCircle(shieldGold, side * 0.44f, center, style = Stroke(width = side * 0.065f))
    }

    drawOval(
        color = Color.Black.copy(alpha = 0.22f),
        topLeft = Offset(side * 0.18f, side * 0.64f),
        size = Size(side * 0.70f, side * 0.24f)
    )
    drawOval(
        color = tokenColor.copy(alpha = 0.80f),
        topLeft = Offset(side * 0.20f, side * 0.56f),
        size = Size(side * 0.60f, side * 0.25f)
    )
    drawCircle(Color.Black.copy(alpha = 0.16f), side * 0.27f, head + Offset(side * 0.025f, side * 0.04f))
    drawCircle(tokenColor, side * 0.27f, head)
    drawCircle(Color.White.copy(alpha = 0.90f), side * 0.27f, head, style = Stroke(width = side * 0.045f))
    drawCircle(
        Color.White.copy(alpha = 0.36f),
        side * 0.075f,
        head + Offset(-side * 0.085f, -side * 0.085f)
    )

    if (protected) {
        val badgeCenter = Offset(side * 0.78f, side * 0.22f)
        drawCircle(Color.White, side * 0.16f, badgeCenter)
        drawCircle(shieldGold, side * 0.16f, badgeCenter, style = Stroke(width = side * 0.035f))
        drawPowerIcon(PowerType.PROTECT, badgeCenter, side * 0.22f, shieldGold)
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
