package com.towhid.ludo.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.towhid.ludo.game.engine.GameEngine
import com.towhid.ludo.game.model.GameState
import com.towhid.ludo.game.model.PlayerColor
import com.towhid.ludo.game.model.Token
import kotlin.math.hypot

private data class Cell(val row: Int, val col: Int)
private data class Point(val x: Float, val y: Float)
private data class DrawToken(val color: PlayerColor, val tokenId: Int, val point: Point)

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
    PlayerColor.RED to listOf(Cell(7, 1), Cell(7, 2), Cell(7, 3), Cell(7, 4), Cell(7, 5), Cell(7, 6)),
    PlayerColor.GREEN to listOf(Cell(1, 7), Cell(2, 7), Cell(3, 7), Cell(4, 7), Cell(5, 7), Cell(6, 7)),
    PlayerColor.YELLOW to listOf(Cell(7, 13), Cell(7, 12), Cell(7, 11), Cell(7, 10), Cell(7, 9), Cell(7, 8)),
    PlayerColor.BLUE to listOf(Cell(13, 7), Cell(12, 7), Cell(11, 7), Cell(10, 7), Cell(9, 7), Cell(8, 7))
)

private val baseSpots = mapOf(
    PlayerColor.RED to listOf(Point(2.0f, 2.0f), Point(4.0f, 2.0f), Point(2.0f, 4.0f), Point(4.0f, 4.0f)),
    PlayerColor.GREEN to listOf(Point(10.0f, 2.0f), Point(12.0f, 2.0f), Point(10.0f, 4.0f), Point(12.0f, 4.0f)),
    PlayerColor.YELLOW to listOf(Point(10.0f, 10.0f), Point(12.0f, 10.0f), Point(10.0f, 12.0f), Point(12.0f, 12.0f)),
    PlayerColor.BLUE to listOf(Point(2.0f, 10.0f), Point(4.0f, 10.0f), Point(2.0f, 12.0f), Point(4.0f, 12.0f))
)

private fun colorOf(color: PlayerColor): Color = when (color) {
    PlayerColor.RED -> Color(0xFFE53935)
    PlayerColor.GREEN -> Color(0xFF2E7D32)
    PlayerColor.YELLOW -> Color(0xFFF9A825)
    PlayerColor.BLUE -> Color(0xFF1565C0)
}

@Composable
fun LudoBoard(
    state: GameState,
    selectableTokenIds: Set<Int>,
    onTokenTap: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = renderedTokens(state)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(tokens, selectableTokenIds, state.activeColor) {
                detectTapGestures { offset ->
                    if (selectableTokenIds.isEmpty()) return@detectTapGestures
                    val unit = size.width / 15f
                    val tapX = offset.x / unit
                    val tapY = offset.y / unit
                    tokens
                        .filter { it.color == state.activeColor && it.tokenId in selectableTokenIds }
                        .minByOrNull { hypot((it.point.x - tapX).toDouble(), (it.point.y - tapY).toDouble()) }
                        ?.takeIf { hypot((it.point.x - tapX).toDouble(), (it.point.y - tapY).toDouble()) < 0.62 }
                        ?.let { onTokenTap(it.tokenId) }
                }
            }
    ) {
        val unit = size.width / 15f
        drawRect(Color(0xFFF8F8F8))

        drawBase(PlayerColor.RED, 0, 0, unit)
        drawBase(PlayerColor.GREEN, 9, 0, unit)
        drawBase(PlayerColor.YELLOW, 9, 9, unit)
        drawBase(PlayerColor.BLUE, 0, 9, unit)

        ringCells.forEachIndexed { index, cell ->
            val startColor = PlayerColor.entries.firstOrNull { it.startIndex == index }
            drawCell(cell, unit, startColor?.let { colorOf(it).copy(alpha = 0.42f) } ?: Color.White)
            if (index in GameEngine.safeRingIndexes) {
                drawCircle(
                    color = Color(0xFF5D5D5D),
                    radius = unit * 0.09f,
                    center = Offset((cell.col + 0.5f) * unit, (cell.row + 0.5f) * unit)
                )
            }
        }

        homeLanes.forEach { (color, cells) ->
            cells.forEach { drawCell(it, unit, colorOf(color).copy(alpha = 0.38f)) }
        }

        drawRect(
            color = Color(0xFFECECEC),
            topLeft = Offset(6f * unit, 6f * unit),
            size = Size(3f * unit, 3f * unit)
        )
        drawRect(
            color = Color(0xFF444444),
            topLeft = Offset(6f * unit, 6f * unit),
            size = Size(3f * unit, 3f * unit),
            style = Stroke(width = unit * 0.045f)
        )

        tokens.forEach { token ->
            val color = colorOf(token.color)
            val center = Offset(token.point.x * unit, token.point.y * unit)
            if (token.color == state.activeColor && token.tokenId in selectableTokenIds) {
                drawCircle(
                    color = Color(0xFF212121),
                    radius = unit * 0.38f,
                    center = center,
                    style = Stroke(width = unit * 0.09f)
                )
            }
            drawCircle(color = color, radius = unit * 0.30f, center = center)
            drawCircle(
                color = Color.White,
                radius = unit * 0.30f,
                center = center,
                style = Stroke(width = unit * 0.06f)
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBase(
    color: PlayerColor,
    col: Int,
    row: Int,
    unit: Float
) {
    drawRect(
        color = colorOf(color).copy(alpha = 0.18f),
        topLeft = Offset(col * unit, row * unit),
        size = Size(6f * unit, 6f * unit)
    )
    drawRect(
        color = colorOf(color).copy(alpha = 0.42f),
        topLeft = Offset((col + 1f) * unit, (row + 1f) * unit),
        size = Size(4f * unit, 4f * unit),
        style = Stroke(width = unit * 0.08f)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCell(
    cell: Cell,
    unit: Float,
    fill: Color
) {
    drawRect(
        color = fill,
        topLeft = Offset(cell.col * unit, cell.row * unit),
        size = Size(unit, unit)
    )
    drawRect(
        color = Color(0xFF555555),
        topLeft = Offset(cell.col * unit, cell.row * unit),
        size = Size(unit, unit),
        style = Stroke(width = unit * 0.035f)
    )
}

private fun renderedTokens(state: GameState): List<DrawToken> {
    val raw = buildList {
        state.players.forEach { player ->
            player.tokens.forEach { token ->
                val point = when {
                    token.isHome -> baseSpots.getValue(player.color)[token.id]
                    token.isFinished -> finishSpot(player.color, token.id)
                    token.isOnTrack -> {
                        val cell = ringCells[GameEngine.ringIndex(player.color, token.progress)]
                        Point(cell.col + 0.5f, cell.row + 0.5f)
                    }
                    else -> {
                        val cell = homeLanes.getValue(player.color)[token.progress - 51]
                        Point(cell.col + 0.5f, cell.row + 0.5f)
                    }
                }
                add(DrawToken(player.color, token.id, point))
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

private fun finishSpot(color: PlayerColor, tokenId: Int): Point {
    val offsets = listOf(Point(-0.35f, -0.35f), Point(0.35f, -0.35f), Point(-0.35f, 0.35f), Point(0.35f, 0.35f))
    val center = when (color) {
        PlayerColor.RED -> Point(6.85f, 7.5f)
        PlayerColor.GREEN -> Point(7.5f, 6.85f)
        PlayerColor.YELLOW -> Point(8.15f, 7.5f)
        PlayerColor.BLUE -> Point(7.5f, 8.15f)
    }
    val offset = offsets[tokenId]
    return Point(center.x + offset.x * 0.35f, center.y + offset.y * 0.35f)
}
