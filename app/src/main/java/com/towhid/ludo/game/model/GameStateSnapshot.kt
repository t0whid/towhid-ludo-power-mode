package com.towhid.ludo.game.model

/** Android-free saveable representation of a running match. */
data class GameStateSnapshot(
    val mode: String,
    val tokens: String,
    val playerPowers: String,
    val powerCells: String,
    val powerSeed: Int,
    val activeTurnIndex: Int,
    val dice: Int,
    val lastRoll: Int,
    val doubleActive: Int,
    val bonusRollsPending: Int,
    val winner: String,
    val turnSerial: Long,
    val message: String
)

object GameStateSnapshotCodec {
    fun snapshot(state: GameState): GameStateSnapshot = GameStateSnapshot(
        mode = state.mode.name,
        tokens = encodeTokens(state),
        playerPowers = state.players.joinToString(";") { encodePowers(it.powers) },
        powerCells = state.powerCells.toSortedMap().entries.joinToString(",") { (index, type) ->
            "$index:${type.name}"
        },
        powerSeed = state.powerSeed,
        activeTurnIndex = state.activeTurnIndex,
        dice = state.dice ?: -1,
        lastRoll = state.lastRoll ?: -1,
        doubleActive = if (state.doubleActive) 1 else 0,
        bonusRollsPending = state.bonusRollsPending,
        winner = state.winner?.name.orEmpty(),
        turnSerial = state.turnSerial,
        message = state.message
    )

    fun restore(snapshot: GameStateSnapshot): GameState {
        val mode = GameMode.valueOf(snapshot.mode)
        val base = GameState.newGame(mode, seed = snapshot.powerSeed)
        val tokenRows = snapshot.tokens.split(';')
        val powerRows = snapshot.playerPowers.split(';')
        val players = base.players.mapIndexed { playerIndex, player ->
            val encodedTokens = tokenRows.getOrNull(playerIndex).orEmpty().split(',')
            player.copy(
                powers = decodePowers(powerRows.getOrNull(playerIndex).orEmpty()),
                tokens = player.tokens.mapIndexed { tokenIndex, token ->
                    val parts = encodedTokens.getOrNull(tokenIndex).orEmpty().split(':')
                    if (parts.size != 2) token
                    else token.copy(
                        progress = parts[0].toIntOrNull() ?: token.progress,
                        protected = parts[1] == "1"
                    )
                }
            )
        }

        return base.copy(
            players = players,
            powerCells = decodePowerCells(snapshot.powerCells).ifEmpty { base.powerCells },
            activeTurnIndex = snapshot.activeTurnIndex.coerceIn(base.turnOrder.indices),
            dice = snapshot.dice.takeIf { it >= 0 },
            lastRoll = snapshot.lastRoll.takeIf { it >= 0 },
            doubleActive = snapshot.doubleActive == 1,
            bonusRollsPending = snapshot.bonusRollsPending,
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
        val values = encoded.split(',').mapNotNull { it.toIntOrNull() }
        return PowerInventory(
            double = values.getOrElse(0) { 0 },
            chooseRoll = values.getOrElse(1) { 0 },
            protect = values.getOrElse(2) { 0 },
            extraRoll = values.getOrElse(3) { 0 }
        )
    }

    private fun decodePowerCells(encoded: String): Map<Int, PowerType> = encoded
        .split(',')
        .mapNotNull { item ->
            val parts = item.split(':')
            if (parts.size != 2) null
            else {
                val index = parts[0].toIntOrNull() ?: return@mapNotNull null
                val type = runCatching { PowerType.valueOf(parts[1]) }.getOrNull() ?: return@mapNotNull null
                index to type
            }
        }
        .toMap()
}
