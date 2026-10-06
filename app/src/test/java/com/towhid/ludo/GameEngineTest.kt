package com.towhid.ludo

import com.towhid.ludo.game.ai.ComputerAi
import com.towhid.ludo.game.engine.GameEngine
import com.towhid.ludo.game.model.GameMode
import com.towhid.ludo.game.model.GameState
import com.towhid.ludo.game.model.PlayerColor
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
        val move = GameEngine.legalMoves(state, 6).first { it.tokenId == 0 }
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
        val move = GameEngine.legalMoves(state, 2).first { it.tokenId == 0 }
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
        assertFalse(GameEngine.legalMoves(tooHigh, 2).any { it.tokenId == 0 })

        val exact = GameEngine.beginRoll(state, 1)
        val move = GameEngine.legalMoves(exact, 1).first { it.tokenId == 0 }
        val finished = GameEngine.playMove(exact, move)
        assertTrue(finished.player(PlayerColor.RED).tokens[0].isFinished)
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
        assertTrue(GameEngine.legalMoves(state, 3).contains(move))
    }
}
