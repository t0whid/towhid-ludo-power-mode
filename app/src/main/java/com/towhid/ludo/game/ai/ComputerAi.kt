package com.towhid.ludo.game.ai

import com.towhid.ludo.game.engine.GameEngine
import com.towhid.ludo.game.model.GameState
import com.towhid.ludo.game.model.Move
import com.towhid.ludo.game.model.Side
import com.towhid.ludo.game.model.Token
import kotlin.math.max
import kotlin.math.min

/**
 * Offline best-move search. No network/API is used.
 *
 * The AI knows only the current board and the current dice. Future rolls are treated
 * as six equally likely outcomes. Computer turns maximize the evaluation while human
 * turns minimize it. This makes the search deterministic and prevents intentional
 * weak/random moves.
 */
object ComputerAi {
    private const val SEARCH_DEPTH = 3
    private const val WIN_SCORE = 1_000_000.0

    fun chooseBestMove(state: GameState): Move? {
        val dice = state.dice ?: return null
        val moves = GameEngine.legalMoves(state, dice)
        if (moves.isEmpty()) return null

        val cache = HashMap<SearchKey, Double>()
        return moves.maxWithOrNull(
            compareBy<Move> { move ->
                val after = GameEngine.playMove(state, move)
                expectFuture(after, SEARCH_DEPTH, cache)
            }.thenBy { tieBreakScore(state, it) }
        )
    }

    private fun expectFuture(
        state: GameState,
        depth: Int,
        cache: MutableMap<SearchKey, Double>
    ): Double {
        state.winner?.let { return if (it == Side.COMPUTER) WIN_SCORE else -WIN_SCORE }
        if (depth <= 0) return evaluate(state)

        val key = SearchKey(stateSignature(state), depth)
        cache[key]?.let { return it }

        var total = 0.0
        for (dice in 1..6) {
            val rolled = GameEngine.beginRoll(state, dice)
            val moves = GameEngine.legalMoves(rolled, dice)
            val value = if (moves.isEmpty()) {
                expectFuture(GameEngine.passIfNoMove(rolled), depth - 1, cache)
            } else if (rolled.activeSide == Side.COMPUTER) {
                var best = Double.NEGATIVE_INFINITY
                for (move in moves) {
                    best = max(best, expectFuture(GameEngine.playMove(rolled, move), depth - 1, cache))
                }
                best
            } else {
                var best = Double.POSITIVE_INFINITY
                for (move in moves) {
                    best = min(best, expectFuture(GameEngine.playMove(rolled, move), depth - 1, cache))
                }
                best
            }
            total += value / 6.0
        }

        cache[key] = total
        return total
    }

    private fun evaluate(state: GameState): Double {
        var score = 0.0
        for (player in state.players) {
            val sign = if (player.side == Side.COMPUTER) 1.0 else -1.0
            for (token in player.tokens) {
                val tokenScore = when {
                    token.isFinished -> 2_500.0
                    token.isHome -> 0.0
                    token.isInHomeLane -> 900.0 + token.progress * 8.0
                    else -> 120.0 + token.progress * 8.0 + safetyValue(state, player.side, player.color, token)
                }
                score += sign * tokenScore
            }
        }
        return score
    }

    private fun safetyValue(
        state: GameState,
        side: Side,
        color: com.towhid.ludo.game.model.PlayerColor,
        token: Token
    ): Double {
        val index = GameEngine.ringIndex(color, token.progress)
        if (index in GameEngine.safeRingIndexes) return 90.0

        var danger = 0.0
        for (opponent in state.players.filter { it.side != side }) {
            for (other in opponent.tokens.filter { it.isOnTrack }) {
                val opponentIndex = GameEngine.ringIndex(opponent.color, other.progress)
                val distance = (index - opponentIndex + GameEngine.RING_SIZE) % GameEngine.RING_SIZE
                if (distance in 1..6 && other.progress + distance <= 50) {
                    danger += 130.0
                }
            }
        }
        return -danger
    }

    private fun tieBreakScore(state: GameState, move: Move): Int {
        val token = state.activePlayer.tokens.first { it.id == move.tokenId }
        val destination = if (token.isHome) 0 else token.progress + move.dice
        var score = destination
        if (destination == Token.FINISH) score += 10_000
        if (destination in 0..50) {
            val ring = GameEngine.ringIndex(move.color, destination)
            if (ring in GameEngine.safeRingIndexes) score += 500
            val canCapture = state.players
                .filter { it.side != state.activeSide }
                .flatMap { it.tokens.map { token -> it.color to token } }
                .any { (color, other) -> other.isOnTrack && GameEngine.ringIndex(color, other.progress) == ring }
            if (canCapture) score += 5_000
        }
        return score
    }

    private fun stateSignature(state: GameState): String = buildString {
        append(state.activeTurnIndex).append('|')
        for (player in state.players) {
            append(player.color.ordinal).append(':')
            player.tokens.forEach { append(it.progress).append(',') }
            append('|')
        }
    }

    private data class SearchKey(val signature: String, val depth: Int)
}
