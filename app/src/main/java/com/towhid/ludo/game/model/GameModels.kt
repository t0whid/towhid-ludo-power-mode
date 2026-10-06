package com.towhid.ludo.game.model

enum class GameMode { ONE_V_ONE, TWO_V_TWO }

enum class Side { HUMAN, COMPUTER }

enum class PlayerColor(val startIndex: Int) {
    RED(0), GREEN(13), YELLOW(26), BLUE(39)
}

enum class PowerType(val displayName: String) {
    DOUBLE("Double"),
    CHOOSE_ROLL("Choose Roll"),
    PROTECT("Protect"),
    EXTRA_ROLL("+1 Roll")
}

data class PowerInventory(
    val double: Int = 0,
    val chooseRoll: Int = 0,
    val protect: Int = 0,
    val extraRoll: Int = 0
) {
    fun count(type: PowerType): Int = when (type) {
        PowerType.DOUBLE -> double
        PowerType.CHOOSE_ROLL -> chooseRoll
        PowerType.PROTECT -> protect
        PowerType.EXTRA_ROLL -> extraRoll
    }

    fun add(type: PowerType, amount: Int = 1): PowerInventory = when (type) {
        PowerType.DOUBLE -> copy(double = double + amount)
        PowerType.CHOOSE_ROLL -> copy(chooseRoll = chooseRoll + amount)
        PowerType.PROTECT -> copy(protect = protect + amount)
        PowerType.EXTRA_ROLL -> copy(extraRoll = extraRoll + amount)
    }

    fun consume(type: PowerType): PowerInventory {
        require(count(type) > 0) { "No ${type.displayName} power available" }
        return add(type, -1)
    }
}

data class Token(
    val id: Int,
    val progress: Int = HOME,
    val protected: Boolean = false
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
    /** Number of board steps. Can be 2..12 when Double is active. */
    val dice: Int
)

data class GameState(
    val mode: GameMode,
    val players: List<PlayerState>,
    val turnOrder: List<PlayerColor>,
    val activeTurnIndex: Int = 0,
    /** Natural/selected die face. Always 1..6 while a roll is active. */
    val dice: Int? = null,
    val lastRoll: Int? = null,
    val doubleActive: Boolean = false,
    /** +1 powers waiting to be consumed by a non-six completed roll. */
    val bonusRollsPending: Int = 0,
    val humanPowers: PowerInventory = PowerInventory(),
    val computerPowers: PowerInventory = PowerInventory(),
    val winner: Side? = null,
    val turnSerial: Long = 0,
    val message: String = "Roll the dice"
) {
    val activeColor: PlayerColor get() = turnOrder[activeTurnIndex]
    val activePlayer: PlayerState get() = players.first { it.color == activeColor }
    val activeSide: Side get() = activePlayer.side
    val movementSteps: Int? get() = dice?.let { if (doubleActive) it * 2 else it }

    fun player(color: PlayerColor): PlayerState = players.first { it.color == color }

    fun powers(side: Side): PowerInventory = when (side) {
        Side.HUMAN -> humanPowers
        Side.COMPUTER -> computerPowers
    }

    fun withPowers(side: Side, inventory: PowerInventory): GameState = when (side) {
        Side.HUMAN -> copy(humanPowers = inventory)
        Side.COMPUTER -> copy(computerPowers = inventory)
    }

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
