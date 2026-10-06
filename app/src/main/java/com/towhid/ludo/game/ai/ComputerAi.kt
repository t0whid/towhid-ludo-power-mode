package com.towhid.ludo.game.ai

import com.towhid.ludo.game.engine.GameEngine
import com.towhid.ludo.game.model.GameState
import com.towhid.ludo.game.model.Move
import com.towhid.ludo.game.model.PowerInventory
import com.towhid.ludo.game.model.PowerType
import com.towhid.ludo.game.model.Side
import com.towhid.ludo.game.model.Token
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic offline best-move search. No API/network is used.
 *
 * Current-turn power combinations are explicitly compared. Future dice are treated as
 * six equally likely outcomes, with computer turns maximizing and human turns minimizing
 * the board evaluation.
 */
object ComputerAi {
    private const val SEARCH_DEPTH = 3
    private const val WIN_SCORE = 1_000_000.0

    data class PreRollDecision(
        val useExtraRoll: Boolean = false,
        /** null means use a normal random roll; 1..6 means spend Choose Roll. */
        val chosenRoll: Int? = null
    )

    data class AfterRollDecision(
        val protectTokenId: Int? = null,
        val useDouble: Boolean = false,
        val moveTokenId: Int? = null
    )

    fun choosePreRollDecision(state: GameState): PreRollDecision {
        require(state.activeSide == Side.COMPUTER)
        require(state.dice == null)

        val inventory = state.powers(Side.COMPUTER)
        val extraOptions = if (
            inventory.count(PowerType.EXTRA_ROLL) > 0 && state.bonusRollsPending == 0
        ) listOf(false, true) else listOf(false)
        val chooseOptions: List<Int?> = if (inventory.count(PowerType.CHOOSE_ROLL) > 0) {
            listOf<Int?>(null) + (1..6).map { it }
        } else listOf(null)

        var best = PreRollDecision()
        var bestScore = Double.NEGATIVE_INFINITY
        val cache = HashMap<SearchKey, Double>()

        for (useExtra in extraOptions) {
            val extraState = if (useExtra) GameEngine.useExtraRoll(state) else state
            for (chosen in chooseOptions) {
                val score = if (chosen == null) {
                    (1..6).sumOf { roll ->
                        bestAfterRollValue(GameEngine.beginRoll(extraState, roll), cache) / 6.0
                    }
                } else {
                    bestAfterRollValue(GameEngine.useChooseRoll(extraState, chosen), cache)
                }

                if (score > bestScore) {
                    bestScore = score
                    best = PreRollDecision(useExtraRoll = useExtra, chosenRoll = chosen)
                }
            }
        }
        return best
    }

    fun chooseAfterRollDecision(state: GameState): AfterRollDecision {
        require(state.activeSide == Side.COMPUTER)
        require(state.dice != null)
        return findBestAfterRoll(state, HashMap()).first
    }

    /** Compatibility helper for tests/callers that only need the selected move. */
    fun chooseBestMove(state: GameState): Move? {
        val decision = chooseAfterRollDecision(state)
        var working = state
        decision.protectTokenId?.let { working = GameEngine.protectToken(working, it) }
        if (decision.useDouble) working = GameEngine.activateDouble(working)
        val tokenId = decision.moveTokenId ?: return null
        return GameEngine.legalMoves(working).firstOrNull { it.tokenId == tokenId }
    }

    private fun bestAfterRollValue(
        state: GameState,
        cache: MutableMap<SearchKey, Double>
    ): Double = findBestAfterRoll(state, cache).second

    private fun findBestAfterRoll(
        state: GameState,
        cache: MutableMap<SearchKey, Double>
    ): Pair<AfterRollDecision, Double> {
        val inventory = state.powers(state.activeSide)
        val protectOptions: List<Int?> = if (inventory.count(PowerType.PROTECT) > 0) {
            listOf<Int?>(null) + GameEngine.protectableTokenIds(state).sorted().map { it }
        } else listOf(null)
        val doubleOptions = if (inventory.count(PowerType.DOUBLE) > 0 && !state.doubleActive) {
            listOf(false, true)
        } else listOf(false)

        val maximizing = state.activeSide == Side.COMPUTER
        var bestDecision = AfterRollDecision()
        var bestScore = if (maximizing) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY

        for (protectId in protectOptions) {
            val protectedState = if (protectId != null) GameEngine.protectToken(state, protectId) else state
            for (useDouble in doubleOptions) {
                val working = if (useDouble) GameEngine.activateDouble(protectedState) else protectedState
                val moves = GameEngine.legalMoves(working)
                if (moves.isEmpty()) {
                    val value = expectFuture(GameEngine.passIfNoMove(working), SEARCH_DEPTH - 1, cache)
                    if (isBetter(value, bestScore, maximizing)) {
                        bestScore = value
                        bestDecision = AfterRollDecision(protectTokenId = protectId, useDouble = useDouble)
                    }
                } else {
                    for (move in moves) {
                        val after = GameEngine.playMove(working, move)
                        val value = expectFuture(after, SEARCH_DEPTH - 1, cache)
                        if (isBetter(value, bestScore, maximizing) ||
                            (value == bestScore && tieBreakScore(working, move) > decisionTieBreak(working, bestDecision))
                        ) {
                            bestScore = value
                            bestDecision = AfterRollDecision(
                                protectTokenId = protectId,
                                useDouble = useDouble,
                                moveTokenId = move.tokenId
                            )
                        }
                    }
                }
            }
        }
        return bestDecision to bestScore
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
            total += bestFutureRollValue(rolled, depth, cache) / 6.0
        }
        cache[key] = total
        return total
    }

    /** Future search includes smart Double use while keeping branching mobile-friendly. */
    private fun bestFutureRollValue(
        rolled: GameState,
        depth: Int,
        cache: MutableMap<SearchKey, Double>
    ): Double {
        val maximizing = rolled.activeSide == Side.COMPUTER
        val canDouble = rolled.powers(rolled.activeSide).count(PowerType.DOUBLE) > 0
        val variants = if (canDouble) listOf(rolled, GameEngine.activateDouble(rolled)) else listOf(rolled)
        var best = if (maximizing) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY

        for (variant in variants) {
            val moves = GameEngine.legalMoves(variant)
            val value = if (moves.isEmpty()) {
                expectFuture(GameEngine.passIfNoMove(variant), depth - 1, cache)
            } else {
                var branchBest = if (maximizing) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
                for (move in moves) {
                    val child = expectFuture(GameEngine.playMove(variant, move), depth - 1, cache)
                    branchBest = if (maximizing) max(branchBest, child) else min(branchBest, child)
                }
                branchBest
            }
            best = if (maximizing) max(best, value) else min(best, value)
        }
        return best
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
        score += inventoryValue(state.computerPowers)
        score -= inventoryValue(state.humanPowers)
        if (state.bonusRollsPending > 0) {
            score += if (state.activeSide == Side.COMPUTER) 180.0 else -180.0
        }
        return score
    }

    private fun inventoryValue(inventory: PowerInventory): Double =
        inventory.double * 150.0 +
            inventory.chooseRoll * 260.0 +
            inventory.protect * 160.0 +
            inventory.extraRoll * 210.0

    private fun safetyValue(
        state: GameState,
        side: Side,
        color: com.towhid.ludo.game.model.PlayerColor,
        token: Token
    ): Double {
        val index = GameEngine.ringIndex(color, token.progress)
        if (index in GameEngine.safeRingIndexes) return 90.0
        if (token.protected) return 240.0

        var danger = 0.0
        for (opponent in state.players.filter { it.side != side }) {
            for (other in opponent.tokens.filter { it.isOnTrack }) {
                val opponentIndex = GameEngine.ringIndex(opponent.color, other.progress)
                val distance = (index - opponentIndex + GameEngine.RING_SIZE) % GameEngine.RING_SIZE
                if (distance in 1..6 && other.progress + distance <= 50) {
                    danger += 130.0
                    if (state.powers(opponent.side).chooseRoll > 0) danger += 170.0
                    if (distance in 2..12 && state.powers(opponent.side).double > 0) danger += 55.0
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
            if (ring in GameEngine.powerCells) score += 700
            val canCapture = state.players
                .filter { it.side != state.activeSide }
                .flatMap { player -> player.tokens.map { token -> player.color to token } }
                .any { (color, other) ->
                    other.isOnTrack && !other.protected && GameEngine.ringIndex(color, other.progress) == ring
                }
            if (canCapture) score += 5_000
        }
        return score
    }

    private fun decisionTieBreak(state: GameState, decision: AfterRollDecision): Int {
        val tokenId = decision.moveTokenId ?: return Int.MIN_VALUE
        var working = state
        decision.protectTokenId?.let { working = GameEngine.protectToken(working, it) }
        if (decision.useDouble) working = GameEngine.activateDouble(working)
        val move = GameEngine.legalMoves(working).firstOrNull { it.tokenId == tokenId } ?: return Int.MIN_VALUE
        return tieBreakScore(working, move)
    }

    private fun isBetter(value: Double, best: Double, maximizing: Boolean): Boolean =
        if (maximizing) value > best else value < best

    private fun stateSignature(state: GameState): String = buildString {
        append(state.activeTurnIndex).append('|')
        append(state.bonusRollsPending).append('|')
        append(inventorySignature(state.humanPowers)).append('|')
        append(inventorySignature(state.computerPowers)).append('|')
        for (player in state.players) {
            append(player.color.ordinal).append(':')
            player.tokens.forEach {
                append(it.progress).append(if (it.protected) 's' else 'n').append(',')
            }
            append('|')
        }
    }

    private fun inventorySignature(inventory: PowerInventory): String =
        "${inventory.double},${inventory.chooseRoll},${inventory.protect},${inventory.extraRoll}"

    private data class SearchKey(val signature: String, val depth: Int)
}
