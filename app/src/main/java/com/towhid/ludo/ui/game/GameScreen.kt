package com.towhid.ludo.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.towhid.ludo.game.ai.ComputerAi
import com.towhid.ludo.game.engine.GameEngine
import com.towhid.ludo.game.model.GameMode
import com.towhid.ludo.game.model.GameState
import com.towhid.ludo.game.model.Side
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.random.Random

@Composable
fun GameScreen(
    mode: GameMode,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var state by remember(mode) { mutableStateOf(GameState.newGame(mode)) }

    LaunchedEffect(state.turnSerial, state.winner) {
        if (state.winner != null || state.activeSide != Side.COMPUTER || state.dice != null) return@LaunchedEffect

        delay(450)
        val rolled = GameEngine.beginRoll(state, Random.nextInt(1, 7))
        state = rolled
        delay(500)

        val moves = GameEngine.legalMoves(rolled, rolled.dice!!)
        if (moves.isEmpty()) {
            state = GameEngine.passIfNoMove(rolled)
        } else {
            val bestMove = withContext(Dispatchers.Default) { ComputerAi.chooseBestMove(rolled) }
            if (bestMove != null) {
                state = GameEngine.playMove(rolled, bestMove)
            }
        }
    }

    val legalMoves = state.dice?.let { GameEngine.legalMoves(state, it) }.orEmpty()
    val selectableIds = if (state.activeSide == Side.HUMAN) legalMoves.map { it.tokenId }.toSet() else emptySet()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Text(
                text = if (mode == GameMode.ONE_V_ONE) "1 vs 1" else "2 vs 2",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(onClick = { state = GameState.newGame(mode) }) { Text("Reset") }
        }

        Spacer(Modifier.height(12.dp))
        StatusCard(state)
        Spacer(Modifier.height(12.dp))

        LudoBoard(
            state = state,
            selectableTokenIds = selectableIds,
            onTokenTap = { tokenId ->
                val move = legalMoves.firstOrNull { it.tokenId == tokenId }
                if (move != null) state = GameEngine.playMove(state, move)
            }
        )

        Spacer(Modifier.height(16.dp))

        if (state.winner == null) {
            if (state.activeSide == Side.HUMAN) {
                if (state.dice == null) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val rolled = GameEngine.beginRoll(state, Random.nextInt(1, 7))
                            state = if (GameEngine.legalMoves(rolled, rolled.dice!!).isEmpty()) {
                                GameEngine.passIfNoMove(rolled)
                            } else rolled
                        }
                    ) {
                        Text("ROLL DICE")
                    }
                } else {
                    Text(
                        text = "Tap a highlighted token",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text(
                    text = "Computer is calculating the best move…",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { state = GameState.newGame(mode) }
            ) {
                Text("PLAY AGAIN")
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Current build: core Ludo rules + offline best-move AI. Power cells are the next layer.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatusCard(state: GameState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.lastRoll?.toString() ?: "–",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.activeSide == Side.HUMAN) "HUMAN • ${GameEngine.displayName(state.activeColor)}" else "COMPUTER • ${GameEngine.displayName(state.activeColor)}",
                    fontWeight = FontWeight.Bold
                )
                Text(text = state.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
