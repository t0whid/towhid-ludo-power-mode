package com.towhid.ludo.game.model

import kotlin.random.Random

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

    operator fun plus(other: PowerInventory): PowerInventory = PowerInventory(
        double = double + other.double,
        chooseRoll = chooseRoll + other.chooseRoll,
        protect = protect + other.protect,
        extraRoll = extraRoll + other.extraRoll
    )
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
    val tokens: List<Token> = List(4) { Token(it) },
    val powers: PowerInventory = PowerInventory()
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
    /** Current moving power pickups on the 52-cell runway. */
    val powerCells: Map<Int, PowerType>,
    /** Deterministic pseudo-random seed so AI simulations see the same relocation result. */
    val powerSeed: Int,
    val winner: Side? = null,
    val turnSerial: Long = 0,
    val message: String = "Roll the dice"
) {
    val activeColor: PlayerColor get() = turnOrder[activeTurnIndex]
    val activePlayer: PlayerState get() = players.first { it.color == activeColor }
    val activeSide: Side get() = activePlayer.side
    val activePowers: PowerInventory get() = activePlayer.powers
    val movementSteps: Int? get() = dice?.let { if (doubleActive) it * 2 else it }

    fun player(color: PlayerColor): PlayerState = players.first { it.color == color }
    fun powers(color: PlayerColor): PowerInventory = player(color).powers

    fun teamPowers(side: Side): PowerInventory = players
        .filter { it.side == side }
        .fold(PowerInventory()) { total, player -> total + player.powers }

    fun withPowers(color: PlayerColor, inventory: PowerInventory): GameState = copy(
        players = players.map { player ->
            if (player.color == color) player.copy(powers = inventory) else player
        }
    )

    companion object {
        private const val RING_SIZE = 52
        private val SAFE_RING_INDEXES = setOf(0, 8, 13, 21, 26, 34, 39, 47)

        fun newGame(mode: GameMode, seed: Int = Random.nextInt()): GameState {
            // Human always starts. In 2v2 humans own Blue + Green, CPU owns Red + Yellow.
            val players = when (mode) {
                GameMode.ONE_V_ONE -> listOf(
                    PlayerState(PlayerColor.BLUE, Side.HUMAN),
                    PlayerState(PlayerColor.RED, Side.COMPUTER)
                )
                GameMode.TWO_V_TWO -> listOf(
                    PlayerState(PlayerColor.BLUE, Side.HUMAN),
                    PlayerState(PlayerColor.RED, Side.COMPUTER),
                    PlayerState(PlayerColor.GREEN, Side.HUMAN),
                    PlayerState(PlayerColor.YELLOW, Side.COMPUTER)
                )
            }
            return GameState(
                mode = mode,
                players = players,
                turnOrder = players.map { it.color },
                powerCells = generateInitialPowerCells(seed),
                powerSeed = seed,
                message = "Your turn — roll the dice"
            )
        }

        private fun generateInitialPowerCells(seed: Int): Map<Int, PowerType> {
            val random = Random(seed)
            val eligible = (0 until RING_SIZE)
                .filterNot { it in SAFE_RING_INDEXES }
                .shuffled(random)
            val types = listOf(
                PowerType.DOUBLE,
                PowerType.CHOOSE_ROLL,
                PowerType.PROTECT,
                PowerType.EXTRA_ROLL,
                PowerType.DOUBLE,
                PowerType.CHOOSE_ROLL,
                PowerType.PROTECT,
                PowerType.EXTRA_ROLL
            )
            return eligible.take(types.size).zip(types).toMap()
        }
    }
}
