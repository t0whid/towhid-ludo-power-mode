package com.towhid.ludo.ui.game

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.towhid.ludo.game.model.GameState
import com.towhid.ludo.game.model.GameStateSnapshot
import com.towhid.ludo.game.model.GameStateSnapshotCodec

/** Saves the complete rules state using only primitive values supported by rememberSaveable. */
internal val GameStateSaver: Saver<GameState, Any> = listSaver(
    save = { state ->
        val snapshot = GameStateSnapshotCodec.snapshot(state)
        listOf(
            snapshot.mode,
            snapshot.tokens,
            snapshot.activeTurnIndex,
            snapshot.dice,
            snapshot.lastRoll,
            snapshot.doubleActive,
            snapshot.bonusRollsPending,
            snapshot.humanPowers,
            snapshot.computerPowers,
            snapshot.winner,
            snapshot.turnSerial,
            snapshot.message
        )
    },
    restore = { saved ->
        GameStateSnapshotCodec.restore(
            GameStateSnapshot(
                mode = saved[0] as String,
                tokens = saved[1] as String,
                activeTurnIndex = (saved[2] as Number).toInt(),
                dice = (saved[3] as Number).toInt(),
                lastRoll = (saved[4] as Number).toInt(),
                doubleActive = (saved[5] as Number).toInt(),
                bonusRollsPending = (saved[6] as Number).toInt(),
                humanPowers = saved[7] as String,
                computerPowers = saved[8] as String,
                winner = saved[9] as String,
                turnSerial = (saved[10] as Number).toLong(),
                message = saved[11] as String
            )
        )
    }
)
