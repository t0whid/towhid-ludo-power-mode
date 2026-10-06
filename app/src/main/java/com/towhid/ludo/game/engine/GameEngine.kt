package com.towhid.ludo.game.engine

import com.towhid.ludo.game.model.GameState
import com.towhid.ludo.game.model.Move
import com.towhid.ludo.game.model.PlayerColor
import com.towhid.ludo.game.model.PlayerState
import com.towhid.ludo.game.model.PowerType
import com.towhid.ludo.game.model.Side
import com.towhid.ludo.game.model.Token

object GameEngine {
    const val RING_SIZE = 52
    val safeRingIndexes: Set<Int> = setOf(0, 8, 13, 21, 26, 34, 39, 47)

    /** Two symmetric cells for every power. */
    val powerCells: Map<Int, PowerType> = mapOf(
        5 to PowerType.DOUBLE,
        31 to PowerType.DOUBLE,
        11 to PowerType.CHOOSE_ROLL,
        37 to PowerType.CHOOSE_ROLL,
        18 to PowerType.PROTECT,
        44 to PowerType.PROTECT,
        24 to PowerType.EXTRA_ROLL,
        50 to PowerType.EXTRA_ROLL
    )

    fun legalMoves(state: GameState): List<Move> {
        val steps = state.movementSteps ?: return emptyList()
        return legalMoves(state, steps)
    }

    fun legalMoves(state: GameState, steps: Int): List<Move> {
        if (state.winner != null || steps !in 1..12) return emptyList()
        val player = state.activePlayer
        return player.tokens.mapNotNull { token ->
            val destinationProgress = when {
                token.isFinished -> return@mapNotNull null
                token.isHome && steps == 6 -> 0
                token.isHome -> return@mapNotNull null
                token.progress + steps <= Token.FINISH -> token.progress + steps
                else -> return@mapNotNull null
            }
            if (isBlockedByProtectedOpponent(state, player, destinationProgress)) null
            else Move(player.color, token.id, steps)
        }
    }

    fun beginRoll(state: GameState, dice: Int): GameState {
        require(dice in 1..6)
        if (state.winner != null || state.dice != null) return state
        return state.copy(
            dice = dice,
            lastRoll = dice,
            doubleActive = false,
            message = "${displayName(state.activeColor)} rolled $dice"
        )
    }

    fun useChooseRoll(state: GameState, chosen: Int): GameState {
        require(chosen in 1..6)
        require(state.dice == null) { "Choose Roll must be used before rolling" }
        val updated = consumePower(state, PowerType.CHOOSE_ROLL)
        return beginRoll(updated, chosen).copy(
            message = "${displayName(state.activeColor)} chose $chosen"
        )
    }

    fun activateDouble(state: GameState): GameState {
        require(state.dice != null) { "Double can only be used after a roll" }
        require(!state.doubleActive) { "Double is already active" }
        val updated = consumePower(state, PowerType.DOUBLE)
        val steps = state.dice * 2
        return updated.copy(
            doubleActive = true,
            message = "${displayName(state.activeColor)} doubled ${state.dice} to $steps"
        )
    }

    fun useExtraRoll(state: GameState): GameState {
        require(state.dice == null) { "+1 Roll must be armed before rolling" }
        require(state.bonusRollsPending == 0) { "An extra roll is already armed" }
        val updated = consumePower(state, PowerType.EXTRA_ROLL)
        return updated.copy(
            bonusRollsPending = 1,
            message = "${displayName(state.activeColor)} armed +1 extra roll"
        )
    }

    fun protectToken(state: GameState, tokenId: Int): GameState {
        val player = state.activePlayer
        val token = player.tokens.firstOrNull { it.id == tokenId }
            ?: error("Unknown token")
        require(!token.isHome && !token.isFinished) { "Only an active token can be protected" }
        require(!token.protected) { "Token is already protected" }

        val updated = consumePower(state, PowerType.PROTECT)
        return updated.copy(
            players = updated.players.map { current ->
                if (current.color != player.color) current
                else current.copy(tokens = current.tokens.map {
                    if (it.id == tokenId) it.copy(protected = true) else it
                })
            },
            message = "${displayName(player.color)} protected token ${tokenId + 1}"
        )
    }

    fun protectableTokenIds(state: GameState): Set<Int> = state.activePlayer.tokens
        .filter { !it.isHome && !it.isFinished && !it.protected }
        .map { it.id }
        .toSet()

    fun passIfNoMove(state: GameState): GameState {
        val naturalRoll = state.dice ?: return state
        if (legalMoves(state).isNotEmpty()) return state
        return finishTurn(
            state = state,
            rolledSix = naturalRoll == 6,
            message = "No legal move"
        )
    }

    fun playMove(state: GameState, move: Move): GameState {
        if (state.winner != null) return state
        val naturalRoll = state.dice ?: error("Roll before moving")
        val steps = state.movementSteps ?: error("No movement value")
        require(move.color == state.activeColor) { "Move must belong to active player" }
        require(move.dice == steps) { "Move value does not match current roll/power" }
        require(legalMoves(state).any { it.tokenId == move.tokenId }) { "Illegal move" }

        val movingPlayer = state.activePlayer
        val movingToken = movingPlayer.tokens.first { it.id == move.tokenId }
        val newProgress = if (movingToken.isHome) 0 else movingToken.progress + steps

        var updatedPlayers = state.players.map { player ->
            if (player.color != movingPlayer.color) player
            else player.copy(tokens = player.tokens.map { token ->
                if (token.id == movingToken.id) token.copy(progress = newProgress, protected = false) else token
            })
        }

        var captured = 0
        if (newProgress in 0..50) {
            val destination = ringIndex(movingPlayer.color, newProgress)
            if (destination !in safeRingIndexes) {
                updatedPlayers = updatedPlayers.map { player ->
                    if (player.side == movingPlayer.side) player
                    else player.copy(tokens = player.tokens.map { token ->
                        if (
                            token.isOnTrack &&
                            !token.protected &&
                            ringIndex(player.color, token.progress) == destination
                        ) {
                            captured += 1
                            token.copy(progress = Token.HOME, protected = false)
                        } else token
                    })
                }
            }
        }

        var updatedState = state.copy(players = updatedPlayers)
        val collectedPower = if (newProgress in 0..50) {
            powerCells[ringIndex(movingPlayer.color, newProgress)]
        } else null
        if (collectedPower != null) {
            updatedState = grantPower(updatedState, movingPlayer.side, collectedPower)
        }

        val winner = winnerFor(updatedState.players)
        if (winner != null) {
            return updatedState.copy(
                dice = null,
                doubleActive = false,
                winner = winner,
                turnSerial = state.turnSerial + 1,
                message = if (winner == Side.HUMAN) "You win!" else "Computer wins"
            )
        }

        val actionText = when {
            newProgress == Token.FINISH -> "${displayName(move.color)} brought a token home"
            captured > 0 -> "${displayName(move.color)} captured $captured token${if (captured > 1) "s" else ""}"
            else -> "${displayName(move.color)} moved $steps"
        }
        val powerText = collectedPower?.let { " • +${it.displayName}" }.orEmpty()

        return finishTurn(
            state = updatedState,
            rolledSix = naturalRoll == 6,
            message = actionText + powerText
        )
    }

    fun ringIndex(color: PlayerColor, progress: Int): Int {
        require(progress in 0..50)
        return (color.startIndex + progress) % RING_SIZE
    }

    private fun isBlockedByProtectedOpponent(
        state: GameState,
        movingPlayer: PlayerState,
        destinationProgress: Int
    ): Boolean {
        if (destinationProgress !in 0..50) return false
        val destination = ringIndex(movingPlayer.color, destinationProgress)
        if (destination in safeRingIndexes) return false
        return state.players
            .filter { it.side != movingPlayer.side }
            .any { opponent ->
                opponent.tokens.any { token ->
                    token.protected && token.isOnTrack &&
                        ringIndex(opponent.color, token.progress) == destination
                }
            }
    }

    private fun consumePower(state: GameState, type: PowerType): GameState {
        val side = state.activeSide
        val inventory = state.powers(side)
        require(inventory.count(type) > 0) { "No ${type.displayName} power available" }
        return state.withPowers(side, inventory.consume(type))
    }

    private fun grantPower(state: GameState, side: Side, type: PowerType): GameState {
        return state.withPowers(side, state.powers(side).add(type))
    }

    private fun winnerFor(players: List<PlayerState>): Side? {
        return Side.entries.firstOrNull { side ->
            val teamPlayers = players.filter { it.side == side }
            teamPlayers.isNotEmpty() && teamPlayers.all { player -> player.tokens.all { it.isFinished } }
        }
    }

    private fun finishTurn(
        state: GameState,
        rolledSix: Boolean,
        message: String
    ): GameState {
        val useQueuedBonus = !rolledSix && state.bonusRollsPending > 0
        val keepTurn = rolledSix || useQueuedBonus
        val nextBonusRolls = if (useQueuedBonus) state.bonusRollsPending - 1 else state.bonusRollsPending
        val nextIndex = if (keepTurn && state.activePlayer.tokens.any { !it.isFinished }) {
            state.activeTurnIndex
        } else {
            nextPlayableTurnIndex(state)
        }
        val nextPlayer = state.players.first { it.color == state.turnOrder[nextIndex] }
        val suffix = if (keepTurn && nextIndex == state.activeTurnIndex) {
            if (useQueuedBonus) " — +1 roll" else " — roll again"
        } else if (nextPlayer.side == Side.HUMAN) {
            " — your turn"
        } else {
            " — computer turn"
        }
        return state.copy(
            activeTurnIndex = nextIndex,
            dice = null,
            doubleActive = false,
            bonusRollsPending = nextBonusRolls,
            turnSerial = state.turnSerial + 1,
            message = message + suffix
        )
    }

    private fun nextPlayableTurnIndex(state: GameState): Int {
        var index = state.activeTurnIndex
        repeat(state.turnOrder.size) {
            index = (index + 1) % state.turnOrder.size
            val color = state.turnOrder[index]
            val player = state.players.first { it.color == color }
            if (player.tokens.any { !it.isFinished }) return index
        }
        return state.activeTurnIndex
    }

    fun displayName(color: PlayerColor): String = color.name.lowercase().replaceFirstChar { it.uppercase() }
}
