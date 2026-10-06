package com.towhid.ludo.game.model

enum class GameMode { ONE_V_ONE, TWO_V_TWO }

enum class Side { HUMAN, COMPUTER }

enum class PlayerColor(val startIndex: Int) {
    RED(0), GREEN(13), YELLOW(26), BLUE(39)
}

data class Token(
    val id: Int,
    val progress: Int = HOME
) {
    companion object {
        const val HOME = -1
        const val FINISH = 56
    }

    val isHome: Boolean get() = progress == HOME
    val isFinished: Boolean get() = progress == FINISH
    val isOnTrack: Boolean get() = progress in 0..50
    val isInHomeLane: Boolean get() = progress in 51 until FINISH
}

data class PlayerState(
    val color: PlayerColor,
    val side: Side,
    val tokens: List<Token> = List(4) { Token(it) }
)

data class Move(
    val color: PlayerColor,
    val tokenId: Int,
    val dice: Int
)

data class GameState(
    val mode: GameMode,
    val players: List<PlayerState>,
    val turnOrder: List<PlayerColor>,
    val activeTurnIndex: Int = 0,
    val dice: Int? = null,
    val lastRoll: Int? = null,
    val winner: Side? = null,
    val turnSerial: Long = 0,
    val message: String = "Roll the dice"
) {
    val activeColor: PlayerColor get() = turnOrder[activeTurnIndex]
    val activePlayer: PlayerState get() = players.first { it.color == activeColor }
    val activeSide: Side get() = activePlayer.side

    fun player(color: PlayerColor): PlayerState = players.first { it.color == color }

    companion object {
        fun newGame(mode: GameMode): GameState {
            val players = when (mode) {
                GameMode.ONE_V_ONE -> listOf(
                    PlayerState(PlayerColor.RED, Side.HUMAN),
                    PlayerState(PlayerColor.YELLOW, Side.COMPUTER)
                )
                GameMode.TWO_V_TWO -> listOf(
                    PlayerState(PlayerColor.RED, Side.HUMAN),
                    PlayerState(PlayerColor.GREEN, Side.COMPUTER),
                    PlayerState(PlayerColor.YELLOW, Side.HUMAN),
                    PlayerState(PlayerColor.BLUE, Side.COMPUTER)
                )
            }
            return GameState(
                mode = mode,
                players = players,
                turnOrder = players.map { it.color },
                message = "Your turn — roll the dice"
            )
        }
    }
}
