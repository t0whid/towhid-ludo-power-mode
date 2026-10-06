package com.towhid.ludo.ui.game

import androidx.compose.ui.graphics.Color
import com.towhid.ludo.game.model.PlayerColor

// Premium game-room palette: purple/indigo shell with bright arcade pieces.
val GameNightTop = Color(0xFF45488D)
val GameNightMid = Color(0xFF393C7D)
val GameNightBottom = Color(0xFF292C64)
val GamePanel = Color(0xFF2D326F)
val GamePanelDeep = Color(0xFF1B2052)
val GamePanelStroke = Color(0xFF6970B8)
val GameGold = Color(0xFFFFCB3D)
val GameGoldDeep = Color(0xFFD9920C)
val GameCream = Color(0xFFF7FAFD)
val GameMuted = Color(0xFFB6BCE0)
val GameDanger = Color(0xFFFF5664)
val GameSuccess = Color(0xFF54DE93)

fun gameColor(color: PlayerColor): Color = when (color) {
    PlayerColor.RED -> Color(0xFFF14A3F)
    PlayerColor.GREEN -> Color(0xFF0ACB62)
    PlayerColor.YELLOW -> Color(0xFFFFC928)
    PlayerColor.BLUE -> Color(0xFF24A8F2)
}
