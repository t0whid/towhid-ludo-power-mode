package com.towhid.ludo.game.model

/**
 * Saveable representation of a match. Keeping this codec Android-free makes the persistence
 * contract easy to regression-test alongside the game engine.
 */
data class GameStateSnapshot(
    val mode: String,
    val tokens: String,
    val activeTurnIndex: Int,
    val dice: Int,
    val lastRoll: Int,
    val doubleActive: Int,
    val bonusRollsPending: Int,
    val humanPowers: String,
    val computerPowers: String,
    val winner: String,
    val turnSerial: Long,
    val message: String
)

object GameStateSnapshotCodec {
    fun snapshot(state: GameState): GameStateSnapshot = GameStateSnapshot(
        mode = state.mode.name,
        tokens = encodeTokens(state),
        activeTurnIndex = state.activeTurnIndex,
        dice = state.dice ?: -1,
        lastRoll = state.lastRoll ?: -1,
        doubleActive = if (state.doubleActive) 1 else 0,
        bonusRollsPending = state.bonusRollsPending,
        humanPowers = encodePowers(state.humanPowers),
        computerPowers = encodePowers(state.computerPowers),
        winner = state.winner?.name.orEmpty(),
        turnSerial = state.turnSerial,
        message = state.message
    )

    fun restore(snapshot: GameStateSnapshot): GameState {
        val mode = GameMode.valueOf(snapshot.mode)
        val base = GameState.newGame(mode)
        val tokenRows = snapshot.tokens.split(';')
        val players = base.players.mapIndexed { playerIndex, player ->
            val encodedTokens = tokenRows.getOrNull(playerIndex).orEmpty().split(',')
            player.copy(
                tokens = player.tokens.mapIndexed { tokenIndex, token ->
                    val parts = encodedTokens.getOrNull(tokenIndex).orEmpty().split(':')
                    if (parts.size != 2) token
                    else token.copy(
                        progress = parts[0].toInt(),
                        protected = parts[1] == "1"
                    )
                }
            )
        }

        return base.copy(
            players = players,
            activeTurnIndex = snapshot.activeTurnIndex,
            dice = snapshot.dice.takeIf { it >= 0 },
            lastRoll = snapshot.lastRoll.takeIf { it >= 0 },
            doubleActive = snapshot.doubleActive == 1,
            bonusRollsPending = snapshot.bonusRollsPending,
            humanPowers = decodePowers(snapshot.humanPowers),
            computerPowers = decodePowers(snapshot.computerPowers),
            winner = snapshot.winner.takeIf { it.isNotEmpty() }?.let(Side::valueOf),
            turnSerial = snapshot.turnSerial,
            message = snapshot.message
        )
    }

    private fun encodeTokens(state: GameState): String = state.players.joinToString(";") { player ->
        player.tokens.joinToString(",") { token ->
            "${token.progress}:${if (token.protected) 1 else 0}"
        }
    }

    private fun encodePowers(powers: PowerInventory): String = listOf(
        powers.double,
        powers.chooseRoll,
        powers.protect,
        powers.extraRoll
    ).joinToString(",")

    private fun decodePowers(encoded: String): PowerInventory {
        val values = encoded.split(',').map { it.toInt() }
        return PowerInventory(
            double = values.getOrElse(0) { 0 },
            chooseRoll = values.getOrElse(1) { 0 },
            protect = values.getOrElse(2) { 0 },
            extraRoll = values.getOrElse(3) { 0 }
        )
    }
}
