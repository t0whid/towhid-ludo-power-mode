package com.towhid.ludo

import com.towhid.ludo.game.ai.ComputerAi
import com.towhid.ludo.game.engine.GameEngine
import com.towhid.ludo.game.model.GameMode
import com.towhid.ludo.game.model.GameState
import com.towhid.ludo.game.model.PlayerColor
import com.towhid.ludo.game.model.PowerInventory
import com.towhid.ludo.game.model.PowerType
import com.towhid.ludo.game.model.Side
import com.towhid.ludo.game.model.Token
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {
    @Test
    fun sixBringsTokenOutAndKeepsTurn() {
        var state = GameState.newGame(GameMode.ONE_V_ONE)
        state = GameEngine.beginRoll(state, 6)
        val move = GameEngine.legalMoves(state).first { it.tokenId == 0 }
        state = GameEngine.playMove(state, move)

        assertEquals(0, state.player(PlayerColor.RED).tokens[0].progress)
        assertEquals(PlayerColor.RED, state.activeColor)
        assertEquals(null, state.dice)
    }

    @Test
    fun landingOnUnsafeOpponentCellCaptures() {
        var state = GameState.newGame(GameMode.ONE_V_ONE)
        state = state.copy(players = state.players.map { player ->
            when (player.color) {
                PlayerColor.RED -> player.copy(tokens = player.tokens.map { if (it.id == 0) it.copy(progress = 8) else it })
                PlayerColor.YELLOW -> player.copy(tokens = player.tokens.map { if (it.id == 0) it.copy(progress = 36) else it })
                else -> player
            }
        })
        state = GameEngine.beginRoll(state, 2)
        val move = GameEngine.legalMoves(state).first { it.tokenId == 0 }
        state = GameEngine.playMove(state, move)

        assertEquals(Token.HOME, state.player(PlayerColor.YELLOW).tokens[0].progress)
    }

    @Test
    fun finishRequiresExactRoll() {
        var state = GameState.newGame(GameMode.ONE_V_ONE)
        state = state.copy(players = state.players.map { player ->
            if (player.color == PlayerColor.RED) {
                player.copy(tokens = player.tokens.map { if (it.id == 0) it.copy(progress = 55) else it })
            } else player
        })

        val tooHigh = GameEngine.beginRoll(state, 2)
        assertFalse(GameEngine.legalMoves(tooHigh).any { it.tokenId == 0 })

        val exact = GameEngine.beginRoll(state, 1)
        val move = GameEngine.legalMoves(exact).first { it.tokenId == 0 }
        val finished = GameEngine.playMove(exact, move)
        assertTrue(finished.player(PlayerColor.RED).tokens[0].isFinished)
    }

    @Test
    fun landingOnPowerCellAddsChargeToTeamInventory() {
        var state = GameState.newGame(GameMode.ONE_V_ONE)
        state = state.copy(players = state.players.map { player ->
            if (player.color == PlayerColor.RED) {
                player.copy(tokens = player.tokens.map { if (it.id == 0) it.copy(progress = 3) else it })
            } else player
        })
        state = GameEngine.beginRoll(state, 2)
        val move = GameEngine.legalMoves(state).first { it.tokenId == 0 }
        state = GameEngine.playMove(state, move)

        assertEquals(1, state.humanPowers.double)
    }

    @Test
    fun doublePowerCanTurnThreeIntoSixAndReleaseToken() {
        var state = GameState.newGame(GameMode.ONE_V_ONE).copy(
            humanPowers = PowerInventory(double = 1)
        )
        state = GameEngine.beginRoll(state, 3)
        assertTrue(GameEngine.legalMoves(state).isEmpty())

        state = GameEngine.activateDouble(state)
        assertEquals(6, state.movementSteps)
        assertEquals(0, state.humanPowers.double)
        assertEquals(4, GameEngine.legalMoves(state).size)
    }

    @Test
    fun chooseRollConsumesPowerAndSetsChosenFace() {
        val state = GameState.newGame(GameMode.ONE_V_ONE).copy(
            humanPowers = PowerInventory(chooseRoll = 1)
        )
        val chosen = GameEngine.useChooseRoll(state, 5)

        assertEquals(5, chosen.dice)
        assertEquals(5, chosen.lastRoll)
        assertEquals(0, chosen.humanPowers.chooseRoll)
    }

    @Test
    fun protectedTokenCannotBeCapturedUntilItMoves() {
        var state = GameState.newGame(GameMode.ONE_V_ONE).copy(
            humanPowers = PowerInventory(protect = 1)
        )
        state = state.copy(players = state.players.map { player ->
            when (player.color) {
                PlayerColor.RED -> player.copy(tokens = player.tokens.map { if (it.id == 0) it.copy(progress = 10) else it })
                PlayerColor.YELLOW -> player.copy(tokens = player.tokens.map { if (it.id == 0) it.copy(progress = 34) else it })
                else -> player
            }
        })
        state = GameEngine.protectToken(state, 0)
        assertTrue(state.player(PlayerColor.RED).tokens[0].protected)

        state = state.copy(activeTurnIndex = 1, dice = null)
        state = GameEngine.beginRoll(state, 2)
        assertFalse(GameEngine.legalMoves(state).any { it.tokenId == 0 })
    }

    @Test
    fun extraRollPowerKeepsTurnAfterNonSix() {
        var state = GameState.newGame(GameMode.ONE_V_ONE).copy(
            humanPowers = PowerInventory(extraRoll = 1)
        )
        state = state.copy(players = state.players.map { player ->
            if (player.color == PlayerColor.RED) {
                player.copy(tokens = player.tokens.map { if (it.id == 0) it.copy(progress = 1) else it })
            } else player
        })
        state = GameEngine.useExtraRoll(state)
        state = GameEngine.beginRoll(state, 2)
        val move = GameEngine.legalMoves(state).first { it.tokenId == 0 }
        state = GameEngine.playMove(state, move)

        assertEquals(PlayerColor.RED, state.activeColor)
        assertEquals(0, state.bonusRollsPending)
        assertEquals(0, state.humanPowers.extraRoll)
    }

    @Test
    fun powerCellsAreBalancedAndNeverOverlapSafeCells() {
        assertEquals(8, GameEngine.powerCells.size)
        assertTrue(GameEngine.powerCells.keys.none { it in GameEngine.safeRingIndexes })

        PowerType.entries.forEach { type ->
            val indexes = GameEngine.powerCells.filterValues { it == type }.keys.sorted()
            assertEquals(2, indexes.size)
            assertEquals(26, indexes[1] - indexes[0])
        }
    }

    @Test
    fun computerAiReturnsLegalMove() {
        var state = GameState.newGame(GameMode.ONE_V_ONE).copy(activeTurnIndex = 1)
        state = state.copy(players = state.players.map { player ->
            if (player.color == PlayerColor.YELLOW) {
                player.copy(tokens = player.tokens.map { if (it.id == 0) it.copy(progress = 5) else it })
            } else player
        })
        state = GameEngine.beginRoll(state, 3)

        val move = ComputerAi.chooseBestMove(state)
        assertNotNull(move)
        assertTrue(GameEngine.legalMoves(state).contains(move))
    }

    @Test
    fun computerUsesChooseRollWhenItGuaranteesTheWin() {
        var state = GameState.newGame(GameMode.ONE_V_ONE).copy(
            activeTurnIndex = 1,
            computerPowers = PowerInventory(chooseRoll = 1)
        )
        state = state.copy(players = state.players.map { player ->
            if (player.color == PlayerColor.YELLOW) {
                player.copy(tokens = player.tokens.map { token ->
                    if (token.id == 0) token.copy(progress = 55)
                    else token.copy(progress = Token.FINISH)
                })
            } else player
        })

        val decision = ComputerAi.choosePreRollDecision(state)
        assertEquals(1, decision.chosenRoll)
    }
}
