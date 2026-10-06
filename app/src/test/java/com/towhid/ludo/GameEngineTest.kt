package com.towhid.ludo

import com.towhid.ludo.game.ai.ComputerAi
import com.towhid.ludo.game.engine.GameEngine
import com.towhid.ludo.game.model.GameMode
import com.towhid.ludo.game.model.GameState
import com.towhid.ludo.game.model.GameStateSnapshotCodec
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
    private fun GameState.give(color: PlayerColor, powers: PowerInventory): GameState =
        withPowers(color, powers)

    @Test
    fun humanStartsAndOwnsRequestedColors() {
        val one = GameState.newGame(GameMode.ONE_V_ONE, seed = 1)
        assertEquals(PlayerColor.BLUE, one.activeColor)
        assertEquals(Side.HUMAN, one.player(PlayerColor.BLUE).side)
        assertEquals(Side.COMPUTER, one.player(PlayerColor.RED).side)

        val two = GameState.newGame(GameMode.TWO_V_TWO, seed = 1)
        assertEquals(PlayerColor.BLUE, two.activeColor)
        assertEquals(Side.HUMAN, two.player(PlayerColor.BLUE).side)
        assertEquals(Side.HUMAN, two.player(PlayerColor.GREEN).side)
        assertEquals(Side.COMPUTER, two.player(PlayerColor.RED).side)
        assertEquals(Side.COMPUTER, two.player(PlayerColor.YELLOW).side)
    }

    @Test
    fun sixBringsTokenOutAndKeepsTurn() {
        var state = GameState.newGame(GameMode.ONE_V_ONE, seed = 1)
        state = GameEngine.beginRoll(state, 6)
        state = GameEngine.playMove(state, GameEngine.legalMoves(state).first { it.tokenId == 0 })

        assertEquals(0, state.player(PlayerColor.BLUE).tokens[0].progress)
        assertEquals(PlayerColor.BLUE, state.activeColor)
        assertEquals(null, state.dice)
    }

    @Test
    fun landingOnUnsafeOpponentCellCaptures() {
        var state = GameState.newGame(GameMode.ONE_V_ONE, seed = 1)
        state = state.copy(players = state.players.map { player ->
            when (player.color) {
                PlayerColor.BLUE -> player.copy(tokens = player.tokens.map { if (it.id == 0) it.copy(progress = 8) else it })
                PlayerColor.RED -> player.copy(tokens = player.tokens.map { if (it.id == 0) it.copy(progress = 49) else it })
                else -> player
            }
        })
        state = GameEngine.beginRoll(state, 2)
        state = GameEngine.playMove(state, GameEngine.legalMoves(state).first { it.tokenId == 0 })

        assertEquals(Token.HOME, state.player(PlayerColor.RED).tokens[0].progress)
    }

    @Test
    fun safeCellPreventsCapture() {
        var state = GameState.newGame(GameMode.ONE_V_ONE, seed = 1)
        state = state.copy(players = state.players.map { player ->
            when (player.color) {
                PlayerColor.BLUE -> player.copy(tokens = player.tokens.map { if (it.id == 0) it.copy(progress = 6) else it })
                PlayerColor.RED -> player.copy(tokens = player.tokens.map { if (it.id == 0) it.copy(progress = 47) else it })
                else -> player
            }
        })
        state = GameEngine.beginRoll(state, 2)
        state = GameEngine.playMove(state, GameEngine.legalMoves(state).first { it.tokenId == 0 })

        assertEquals(47, state.player(PlayerColor.RED).tokens[0].progress)
    }

    @Test
    fun finishRequiresExactRoll() {
        var state = GameState.newGame(GameMode.ONE_V_ONE, seed = 1)
        state = state.copy(players = state.players.map { player ->
            if (player.color == PlayerColor.BLUE) {
                player.copy(tokens = player.tokens.map { if (it.id == 0) it.copy(progress = 55) else it })
            } else player
        })

        assertFalse(GameEngine.legalMoves(GameEngine.beginRoll(state, 2)).any { it.tokenId == 0 })
        val exact = GameEngine.beginRoll(state, 1)
        val finished = GameEngine.playMove(exact, GameEngine.legalMoves(exact).first { it.tokenId == 0 })
        assertTrue(finished.player(PlayerColor.BLUE).tokens[0].isFinished)
    }

    @Test
    fun powerCellsAreRandomizedBalancedAndAvoidSafeCells() {
        val first = GameState.newGame(GameMode.ONE_V_ONE, seed = 123)
        val second = GameState.newGame(GameMode.ONE_V_ONE, seed = 456)

        assertEquals(8, first.powerCells.size)
        assertTrue(first.powerCells.keys.none { it in GameEngine.safeRingIndexes })
        PowerType.entries.forEach { type ->
            assertEquals(2, first.powerCells.count { it.value == type })
        }
        assertFalse(first.powerCells == second.powerCells)
    }

    @Test
    fun collectedPowerBelongsToThatPlayerAndRelocates() {
        var state = GameState.newGame(GameMode.ONE_V_ONE, seed = 7).copy(
            powerCells = mapOf(49 to PowerType.DOUBLE)
        )
        state = state.copy(players = state.players.map { player ->
            if (player.color == PlayerColor.BLUE) {
                player.copy(tokens = player.tokens.map { if (it.id == 0) it.copy(progress = 8) else it })
            } else player
        })
        state = GameEngine.beginRoll(state, 2)
        state = GameEngine.playMove(state, GameEngine.legalMoves(state).first { it.tokenId == 0 })

        assertEquals(1, state.powers(PlayerColor.BLUE).double)
        assertEquals(0, state.powers(PlayerColor.RED).double)
        assertFalse(49 in state.powerCells)
        assertEquals(1, state.powerCells.count { it.value == PowerType.DOUBLE })
        assertTrue(state.powerCells.keys.none { it in GameEngine.safeRingIndexes })
    }

    @Test
    fun teammatePowerInventoriesAreIndependent() {
        var state = GameState.newGame(GameMode.TWO_V_TWO, seed = 1)
        state = state.give(PlayerColor.BLUE, PowerInventory(double = 2))
        assertEquals(2, state.powers(PlayerColor.BLUE).double)
        assertEquals(0, state.powers(PlayerColor.GREEN).double)
    }

    @Test
    fun doublePowerCanReleaseToken() {
        var state = GameState.newGame(GameMode.ONE_V_ONE, seed = 1)
            .give(PlayerColor.BLUE, PowerInventory(double = 1))
        state = GameEngine.beginRoll(state, 3)
        assertTrue(GameEngine.legalMoves(state).isEmpty())

        state = GameEngine.activateDouble(state)
        assertEquals(6, state.movementSteps)
        assertEquals(0, state.powers(PlayerColor.BLUE).double)
        assertEquals(4, GameEngine.legalMoves(state).size)
    }

    @Test
    fun chooseRollConsumesOnlyActivePlayersPower() {
        val state = GameState.newGame(GameMode.ONE_V_ONE, seed = 1)
            .give(PlayerColor.BLUE, PowerInventory(chooseRoll = 1))
        val chosen = GameEngine.useChooseRoll(state, 5)

        assertEquals(5, chosen.dice)
        assertEquals(0, chosen.powers(PlayerColor.BLUE).chooseRoll)
        assertEquals(0, chosen.powers(PlayerColor.RED).chooseRoll)
    }

    @Test
    fun protectedTokenCannotBeCapturedUntilItMoves() {
        var state = GameState.newGame(GameMode.ONE_V_ONE, seed = 1)
            .give(PlayerColor.BLUE, PowerInventory(protect = 1))
        state = state.copy(players = state.players.map { player ->
            when (player.color) {
                PlayerColor.BLUE -> player.copy(tokens = player.tokens.map { if (it.id == 0) it.copy(progress = 10) else it })
                PlayerColor.RED -> player.copy(tokens = player.tokens.map { if (it.id == 0) it.copy(progress = 47) else it })
                else -> player
            }
        })
        state = GameEngine.protectToken(state, 0)
        assertTrue(state.player(PlayerColor.BLUE).tokens[0].protected)

        state = state.copy(activeTurnIndex = 1, dice = null)
        state = GameEngine.beginRoll(state, 2)
        assertFalse(GameEngine.legalMoves(state).any { it.tokenId == 0 })
    }

    @Test
    fun protectedTokenLosesShieldWhenItMoves() {
        var state = GameState.newGame(GameMode.ONE_V_ONE, seed = 1)
        state = state.copy(players = state.players.map { player ->
            if (player.color == PlayerColor.BLUE) {
                player.copy(tokens = player.tokens.map { if (it.id == 0) it.copy(progress = 10, protected = true) else it })
            } else player
        })
        state = GameEngine.beginRoll(state, 2)
        state = GameEngine.playMove(state, GameEngine.legalMoves(state).first { it.tokenId == 0 })
        assertFalse(state.player(PlayerColor.BLUE).tokens[0].protected)
    }

    @Test
    fun extraRollPowerKeepsTurnAfterNonSix() {
        var state = GameState.newGame(GameMode.ONE_V_ONE, seed = 1)
            .give(PlayerColor.BLUE, PowerInventory(extraRoll = 1))
        state = state.copy(players = state.players.map { player ->
            if (player.color == PlayerColor.BLUE) {
                player.copy(tokens = player.tokens.map { if (it.id == 0) it.copy(progress = 1) else it })
            } else player
        })
        state = GameEngine.useExtraRoll(state)
        state = GameEngine.beginRoll(state, 2)
        state = GameEngine.playMove(state, GameEngine.legalMoves(state).first { it.tokenId == 0 })

        assertEquals(PlayerColor.BLUE, state.activeColor)
        assertEquals(0, state.bonusRollsPending)
        assertEquals(0, state.powers(PlayerColor.BLUE).extraRoll)
    }

    @Test
    fun computerAiReturnsLegalMove() {
        var state = GameState.newGame(GameMode.ONE_V_ONE, seed = 1).copy(activeTurnIndex = 1)
        state = state.copy(players = state.players.map { player ->
            if (player.color == PlayerColor.RED) {
                player.copy(tokens = player.tokens.map { if (it.id == 0) it.copy(progress = 5) else it })
            } else player
        })
        state = GameEngine.beginRoll(state, 3)

        val move = ComputerAi.chooseBestMove(state)
        assertNotNull(move)
        assertTrue(GameEngine.legalMoves(state).contains(move))
    }

    @Test
    fun computerUsesChooseRollWhenItGuaranteesWin() {
        var state = GameState.newGame(GameMode.ONE_V_ONE, seed = 1).copy(activeTurnIndex = 1)
            .give(PlayerColor.RED, PowerInventory(chooseRoll = 1))
        state = state.copy(players = state.players.map { player ->
            if (player.color == PlayerColor.RED) {
                player.copy(tokens = player.tokens.map { token ->
                    if (token.id == 0) token.copy(progress = 55) else token.copy(progress = Token.FINISH)
                })
            } else player
        })

        assertEquals(1, ComputerAi.choosePreRollDecision(state).chosenRoll)
    }

    @Test
    fun computerUsesDoubleWhenItImmediatelyFinishesGame() {
        var state = GameState.newGame(GameMode.ONE_V_ONE, seed = 1).copy(activeTurnIndex = 1)
            .give(PlayerColor.RED, PowerInventory(double = 1))
        state = state.copy(players = state.players.map { player ->
            if (player.color == PlayerColor.RED) {
                player.copy(tokens = player.tokens.map { token ->
                    if (token.id == 0) token.copy(progress = 50) else token.copy(progress = Token.FINISH)
                })
            } else player
        })
        state = GameEngine.beginRoll(state, 3)

        val decision = ComputerAi.chooseAfterRollDecision(state)
        assertTrue(decision.useDouble)
        assertEquals(0, decision.moveTokenId)
    }

    @Test
    fun twoVsTwoWinnerRequiresBothHumanColors() {
        var state = GameState.newGame(GameMode.TWO_V_TWO, seed = 1)
        state = state.copy(players = state.players.map { player ->
            when (player.color) {
                PlayerColor.BLUE -> player.copy(tokens = player.tokens.map { token ->
                    if (token.id == 0) token.copy(progress = 55) else token.copy(progress = Token.FINISH)
                })
                PlayerColor.GREEN -> player.copy(tokens = player.tokens.map { token ->
                    if (token.id == 0) token.copy(progress = 20) else token.copy(progress = Token.FINISH)
                })
                else -> player
            }
        })
        state = GameEngine.beginRoll(state, 1)
        state = GameEngine.playMove(state, GameEngine.legalMoves(state).first { it.tokenId == 0 })
        assertEquals(null, state.winner)
    }

    @Test
    fun twoVsTwoDeclaresWinnerWhenBlueAndGreenFinish() {
        var state = GameState.newGame(GameMode.TWO_V_TWO, seed = 1)
        state = state.copy(players = state.players.map { player ->
            when (player.color) {
                PlayerColor.BLUE -> player.copy(tokens = player.tokens.map { token ->
                    if (token.id == 0) token.copy(progress = 55) else token.copy(progress = Token.FINISH)
                })
                PlayerColor.GREEN -> player.copy(tokens = player.tokens.map { it.copy(progress = Token.FINISH) })
                else -> player
            }
        })
        state = GameEngine.beginRoll(state, 1)
        state = GameEngine.playMove(state, GameEngine.legalMoves(state).first { it.tokenId == 0 })
        assertEquals(Side.HUMAN, state.winner)
    }

    @Test
    fun gameStateSnapshotRoundTripPreservesPerPlayerPowersAndMovingPowerCells() {
        var state = GameState.newGame(GameMode.TWO_V_TWO, seed = 99)
            .give(PlayerColor.BLUE, PowerInventory(double = 2, chooseRoll = 1))
            .give(PlayerColor.GREEN, PowerInventory(protect = 3, extraRoll = 1))
            .give(PlayerColor.RED, PowerInventory(double = 1, chooseRoll = 2))
            .give(PlayerColor.YELLOW, PowerInventory(protect = 1, extraRoll = 2))
        state = state.copy(
            activeTurnIndex = 2,
            dice = 4,
            lastRoll = 4,
            doubleActive = true,
            bonusRollsPending = 1,
            turnSerial = 17,
            message = "Saved mid-turn"
        )
        state = state.copy(players = state.players.map { player ->
            if (player.color == PlayerColor.GREEN) {
                player.copy(tokens = player.tokens.map { token ->
                    when (token.id) {
                        0 -> token.copy(progress = 42, protected = true)
                        1 -> token.copy(progress = Token.FINISH)
                        else -> token
                    }
                })
            } else player
        })

        val restored = GameStateSnapshotCodec.restore(GameStateSnapshotCodec.snapshot(state))
        assertEquals(state, restored)
    }

    @Test
    fun doubleWouldEnableMoveDetectsAutoPassException() {
        var state = GameState.newGame(GameMode.ONE_V_ONE, seed = 1)
            .give(PlayerColor.BLUE, PowerInventory(double = 1))
        state = GameEngine.beginRoll(state, 3)
        assertTrue(GameEngine.legalMoves(state).isEmpty())
        assertTrue(GameEngine.doubleWouldEnableMove(state))

        val withoutPower = state.withPowers(PlayerColor.BLUE, PowerInventory())
        assertFalse(GameEngine.doubleWouldEnableMove(withoutPower))
    }
}
