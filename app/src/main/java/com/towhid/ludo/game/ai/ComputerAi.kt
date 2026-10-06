package com.towhid.ludo.game.ai

import com.towhid.ludo.game.engine.GameEngine
import com.towhid.ludo.game.model.GameState
import com.towhid.ludo.game.model.Move
import com.towhid.ludo.game.model.PlayerColor
import com.towhid.ludo.game.model.PowerInventory
import com.towhid.ludo.game.model.PowerType
import com.towhid.ludo.game.model.Side
import com.towhid.ludo.game.model.Token

/**
 * Deterministic offline best-move search. No API/network is used.
 *
 * Dice rolls are chance nodes. Both sides are assumed to use every available power
 * optimally in future turns, so the computer searches against the strongest legal
 * human response rather than against a simplified/random opponent.
 */
object ComputerAi {
    /** Current action + one fully power-aware future roll/action decision. */
    private const val SEARCH_DEPTH = 2
    private const val WIN_SCORE = 1_000_000.0
    private const val SCORE_EPSILON = 0.000001

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

        val cache = HashMap<SearchKey, Double>()
        var bestDecision = PreRollDecision()
        var bestScore = Double.NEGATIVE_INFINITY

        for (useExtra in extraRollOptions(state)) {
            val extraState = if (useExtra) GameEngine.useExtraRoll(state) else state

            // Normal roll is a chance node: all faces are equally likely.
            val normalScore = (1..6).sumOf { roll ->
                val rolled = GameEngine.beginRoll(extraState, roll)
                findBestAfterRoll(rolled, SEARCH_DEPTH - 1, cache).second / 6.0
            }
            if (strictlyBetter(normalScore, bestScore, maximizing = true)) {
                bestScore = normalScore
                bestDecision = PreRollDecision(useExtraRoll = useExtra)
            }

            if (GameEngine.canUsePower(extraState, PowerType.CHOOSE_ROLL)) {
                for (chosen in 1..6) {
                    val chosenState = GameEngine.useChooseRoll(extraState, chosen)
                    val score = findBestAfterRoll(chosenState, SEARCH_DEPTH - 1, cache).second
                    if (strictlyBetter(score, bestScore, maximizing = true)) {
                        bestScore = score
                        bestDecision = PreRollDecision(
                            useExtraRoll = useExtra,
                            chosenRoll = chosen
                        )
                    }
                }
            }
        }

        return bestDecision
    }

    fun chooseAfterRollDecision(state: GameState): AfterRollDecision {
        require(state.activeSide == Side.COMPUTER)
        require(state.dice != null)
        return findBestAfterRoll(state, SEARCH_DEPTH - 1, HashMap()).first
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

    private fun findBestAfterRoll(
        state: GameState,
        futureDepth: Int,
        cache: MutableMap<SearchKey, Double>
    ): Pair<AfterRollDecision, Double> {
        val maximizing = state.activeSide == Side.COMPUTER
        var bestDecision = AfterRollDecision()
        var bestScore = if (maximizing) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
        var bestTieBreak = Int.MIN_VALUE

        for (protectId in protectOptions(state)) {
            val protectedState = if (protectId != null) {
                GameEngine.protectToken(state, protectId)
            } else {
                state
            }

            for (useDouble in doubleOptions(protectedState)) {
                val working = if (useDouble) GameEngine.activateDouble(protectedState) else protectedState
                val moves = GameEngine.legalMoves(working)

                if (moves.isEmpty()) {
                    val after = GameEngine.passIfNoMove(working)
                    val value = futureValue(after, futureDepth, cache)
                    if (strictlyBetter(value, bestScore, maximizing)) {
                        bestScore = value
                        bestDecision = AfterRollDecision(
                            protectTokenId = protectId,
                            useDouble = useDouble
                        )
                        bestTieBreak = Int.MIN_VALUE
                    }
                    continue
                }

                for (move in moves) {
                    val after = GameEngine.playMove(working, move)
                    val value = futureValue(after, futureDepth, cache)
                    val tieBreak = tieBreakScore(working, move)

                    val better = strictlyBetter(value, bestScore, maximizing)
                    val tied = scoresEqual(value, bestScore)
                    val betterTie = tied && tieBreak > bestTieBreak
                    if (better || betterTie) {
                        bestScore = value
                        bestTieBreak = tieBreak
                        bestDecision = AfterRollDecision(
                            protectTokenId = protectId,
                            useDouble = useDouble,
                            moveTokenId = move.tokenId
                        )
                    }
                }
            }
        }

        return bestDecision to bestScore
    }

    private fun futureValue(
        state: GameState,
        depth: Int,
        cache: MutableMap<SearchKey, Double>
    ): Double {
        state.winner?.let { return if (it == Side.COMPUTER) WIN_SCORE else -WIN_SCORE }
        if (depth <= 0) return evaluate(state)
        return bestPreRollValue(state, depth, cache)
    }

    /**
     * Full power-aware expectiminimax node for a fresh turn.
     *
     * The active side may arm +1, may spend Choose Roll, or may accept a random roll.
     * After the face is known it may protect, double, and choose its best legal token.
     */
    private fun bestPreRollValue(
        state: GameState,
        depth: Int,
        cache: MutableMap<SearchKey, Double>
    ): Double {
        state.winner?.let { return if (it == Side.COMPUTER) WIN_SCORE else -WIN_SCORE }
        if (depth <= 0) return evaluate(state)
        require(state.dice == null)

        val key = SearchKey(stateSignature(state), depth)
        cache[key]?.let { return it }

        val maximizing = state.activeSide == Side.COMPUTER
        var best = if (maximizing) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY

        for (useExtra in extraRollOptions(state)) {
            val extraState = if (useExtra) GameEngine.useExtraRoll(state) else state

            val randomRollValue = (1..6).sumOf { roll ->
                val rolled = GameEngine.beginRoll(extraState, roll)
                findBestAfterRoll(rolled, depth - 1, cache).second / 6.0
            }
            best = chooseBetter(best, randomRollValue, maximizing)

            if (GameEngine.canUsePower(extraState, PowerType.CHOOSE_ROLL)) {
                for (chosen in 1..6) {
                    val chosenState = GameEngine.useChooseRoll(extraState, chosen)
                    val chosenValue = findBestAfterRoll(chosenState, depth - 1, cache).second
                    best = chooseBetter(best, chosenValue, maximizing)
                }
            }
        }

        cache[key] = best
        return best
    }

    private fun protectOptions(state: GameState): List<Int?> {
        if (!GameEngine.canUsePower(state, PowerType.PROTECT)) return listOf(null)
        return listOf<Int?>(null) + GameEngine.protectableTokenIds(state).sorted()
    }

    private fun doubleOptions(state: GameState): List<Boolean> {
        return if (GameEngine.canUsePower(state, PowerType.DOUBLE)) {
            listOf(false, true)
        } else {
            listOf(false)
        }
    }

    private fun extraRollOptions(state: GameState): List<Boolean> {
        return if (GameEngine.canUsePower(state, PowerType.EXTRA_ROLL)) {
            listOf(false, true)
        } else {
            listOf(false)
        }
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
                    else -> 120.0 + token.progress * 8.0 + safetyValue(
                        state = state,
                        side = player.side,
                        color = player.color,
                        token = token
                    )
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
        color: PlayerColor,
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
                val canReachBeforeHomeLane = other.progress + distance <= 50
                if (!canReachBeforeHomeLane) continue

                if (distance in 1..6) {
                    danger += 130.0
                    if (state.powers(opponent.side).chooseRoll > 0) danger += 170.0
                }

                // Double can only produce 2,4,6,8,10,12 — never an odd distance.
                if (
                    distance in 2..12 &&
                    distance % 2 == 0 &&
                    state.powers(opponent.side).double > 0
                ) {
                    danger += 55.0
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
                .flatMap { player -> player.tokens.map { other -> player.color to other } }
                .any { (color, other) ->
                    other.isOnTrack &&
                        !other.protected &&
                        GameEngine.ringIndex(color, other.progress) == ring
                }
            if (canCapture) score += 5_000
        }
        return score
    }

    private fun chooseBetter(current: Double, candidate: Double, maximizing: Boolean): Double =
        if (strictlyBetter(candidate, current, maximizing)) candidate else current

    private fun strictlyBetter(value: Double, best: Double, maximizing: Boolean): Boolean =
        if (maximizing) value > best + SCORE_EPSILON else value < best - SCORE_EPSILON

    private fun scoresEqual(first: Double, second: Double): Boolean =
        kotlin.math.abs(first - second) <= SCORE_EPSILON

    /** Cache only fresh-turn states; dice/double are intentionally absent. */
    private fun stateSignature(state: GameState): String = buildString {
        append(state.activeTurnIndex).append('|')
        append(state.bonusRollsPending).append('|')
        append(inventorySignature(state.humanPowers)).append('|')
        append(inventorySignature(state.computerPowers)).append('|')
        for (player in state.players) {
            append(player.color.ordinal).append(':')
            player.tokens.forEach { token ->
                append(token.progress)
                    .append(if (token.protected) 's' else 'n')
                    .append(',')
            }
            append('|')
        }
    }

    private fun inventorySignature(inventory: PowerInventory): String =
        "${inventory.double},${inventory.chooseRoll},${inventory.protect},${inventory.extraRoll}"

    private data class SearchKey(val signature: String, val depth: Int)
}
