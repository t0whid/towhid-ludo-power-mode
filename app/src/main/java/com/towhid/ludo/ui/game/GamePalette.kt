package com.towhid.ludo.ui.game

import androidx.compose.ui.graphics.Color
import com.towhid.ludo.game.model.PlayerColor

// Arcade game-room palette inspired by classic premium mobile Ludo rooms.
val GameNightTop = Color(0xFF45488F)
val GameNightMid = Color(0xFF393D82)
val GameNightBottom = Color(0xFF2D316E)
val GamePanel = Color(0xFF30356F)
val GamePanelDeep = Color(0xFF181D4B)
val GamePanelStroke = Color(0xFF767CC2)
val GameGold = Color(0xFFFFCF3F)
val GameGoldDeep = Color(0xFFD88C0A)
val GameCream = Color(0xFFF8FAFF)
val GameMuted = Color(0xFFBBC2E5)
val GameDanger = Color(0xFFFF5965)
val GameSuccess = Color(0xFF55DF92)

fun gameColor(color: PlayerColor): Color = when (color) {
    PlayerColor.RED -> Color(0xFFF04A39)
    PlayerColor.GREEN -> Color(0xFF12C967)
    PlayerColor.YELLOW -> Color(0xFFFFCC20)
    PlayerColor.BLUE -> Color(0xFF22A9F3)
}
