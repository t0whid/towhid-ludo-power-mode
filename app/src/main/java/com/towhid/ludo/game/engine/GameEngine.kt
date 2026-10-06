package com.towhid.ludo.game.engine

import com.towhid.ludo.game.model.GameState
import com.towhid.ludo.game.model.Move
import com.towhid.ludo.game.model.PlayerColor
import com.towhid.ludo.game.model.Side
import com.towhid.ludo.game.model.Token

object GameEngine {
    const val RING_SIZE = 52
    val safeRingIndexes: Set<Int> = setOf(0, 8, 13, 21, 26, 34, 39, 47)

    fun legalMoves(state: GameState, dice: Int): List<Move> {
        if (state.winner != null || dice !in 1..6) return emptyList()
        val player = state.activePlayer
        return player.tokens.mapNotNull { token ->
            when {
                token.isFinished -> null
                token.isHome && dice == 6 -> Move(player.color, token.id, dice)
                token.isHome -> null
                token.progress + dice <= Token.FINISH -> Move(player.color, token.id, dice)
                else -> null
            }
        }
    }

    fun beginRoll(state: GameState, dice: Int): GameState {
        require(dice in 1..6)
        if (state.winner != null) return state
        return state.copy(
            dice = dice,
            lastRoll = dice,
            message = "${displayName(state.activeColor)} rolled $dice"
        )
    }

    fun passIfNoMove(state: GameState): GameState {
        val dice = state.dice ?: return state
        if (legalMoves(state, dice).isNotEmpty()) return state
        return finishTurn(
            state = state,
            rolledSix = dice == 6,
            message = "No legal move"
        )
    }

    fun playMove(state: GameState, move: Move): GameState {
        if (state.winner != null) return state
        val dice = state.dice ?: move.dice
        require(move.color == state.activeColor) { "Move must belong to active player" }
        require(move.dice == dice) { "Move dice does not match current roll" }
        require(legalMoves(state, dice).any { it.tokenId == move.tokenId }) { "Illegal move" }

        val movingPlayer = state.activePlayer
        val movingToken = movingPlayer.tokens.first { it.id == move.tokenId }
        val newProgress = if (movingToken.isHome) 0 else movingToken.progress + dice

        var updatedPlayers = state.players.map { player ->
            if (player.color != movingPlayer.color) player
            else player.copy(tokens = player.tokens.map { token ->
                if (token.id == movingToken.id) token.copy(progress = newProgress) else token
            })
        }

        var captured = 0
        if (newProgress in 0..50) {
            val destination = ringIndex(movingPlayer.color, newProgress)
            if (destination !in safeRingIndexes) {
                updatedPlayers = updatedPlayers.map { player ->
                    if (player.side == movingPlayer.side) player
                    else player.copy(tokens = player.tokens.map { token ->
                        if (token.isOnTrack && ringIndex(player.color, token.progress) == destination) {
                            captured += 1
                            token.copy(progress = Token.HOME)
                        } else token
                    })
                }
            }
        }

        val winner = winnerFor(updatedPlayers)
        if (winner != null) {
            return state.copy(
                players = updatedPlayers,
                dice = null,
                winner = winner,
                turnSerial = state.turnSerial + 1,
                message = if (winner == Side.HUMAN) "You win!" else "Computer wins"
            )
        }

        val actionText = when {
            newProgress == Token.FINISH -> "${displayName(move.color)} brought a token home"
            captured > 0 -> "${displayName(move.color)} captured $captured token${if (captured > 1) "s" else ""}"
            else -> "${displayName(move.color)} moved ${move.dice}"
        }

        return finishTurn(
            state = state.copy(players = updatedPlayers),
            rolledSix = dice == 6,
            message = actionText
        )
    }

    fun ringIndex(color: PlayerColor, progress: Int): Int {
        require(progress in 0..50)
        return (color.startIndex + progress) % RING_SIZE
    }

    private fun winnerFor(players: List<com.towhid.ludo.game.model.PlayerState>): Side? {
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
        val nextIndex = if (rolledSix && state.activePlayer.tokens.any { !it.isFinished }) {
            state.activeTurnIndex
        } else {
            nextPlayableTurnIndex(state)
        }
        val nextPlayer = state.players.first { it.color == state.turnOrder[nextIndex] }
        val suffix = if (rolledSix && nextIndex == state.activeTurnIndex) {
            " — roll again"
        } else if (nextPlayer.side == Side.HUMAN) {
            " — your turn"
        } else {
            " — computer turn"
        }
        return state.copy(
            activeTurnIndex = nextIndex,
            dice = null,
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
