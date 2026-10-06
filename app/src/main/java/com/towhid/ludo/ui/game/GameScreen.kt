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
import com.towhid.ludo.game.model.PowerInventory
import com.towhid.ludo.game.model.PowerType
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
    var protectMode by remember { mutableStateOf(false) }
    var showChooseRoll by remember { mutableStateOf(false) }

    LaunchedEffect(state.turnSerial, state.winner) {
        if (state.winner != null || state.activeSide != Side.COMPUTER || state.dice != null) return@LaunchedEffect

        delay(350)
        val preRoll = withContext(Dispatchers.Default) { ComputerAi.choosePreRollDecision(state) }
        var working = state
        if (preRoll.useExtraRoll) {
            working = GameEngine.useExtraRoll(working)
            state = working
            delay(220)
        }
        working = if (preRoll.chosenRoll != null) {
            GameEngine.useChooseRoll(working, preRoll.chosenRoll)
        } else {
            GameEngine.beginRoll(working, Random.nextInt(1, 7))
        }
        state = working
        delay(420)

        val decision = withContext(Dispatchers.Default) { ComputerAi.chooseAfterRollDecision(working) }
        decision.protectTokenId?.let { tokenId ->
            working = GameEngine.protectToken(working, tokenId)
            state = working
            delay(220)
        }
        if (decision.useDouble) {
            working = GameEngine.activateDouble(working)
            state = working
            delay(220)
        }

        val move = decision.moveTokenId?.let { tokenId ->
            GameEngine.legalMoves(working).firstOrNull { it.tokenId == tokenId }
        }
        state = if (move != null) {
            GameEngine.playMove(working, move)
        } else {
            GameEngine.passIfNoMove(working)
        }
    }

    val legalMoves = if (state.dice != null) GameEngine.legalMoves(state) else emptyList()
    val protectableIds = if (state.activeSide == Side.HUMAN) GameEngine.protectableTokenIds(state) else emptySet()
    val selectableIds = when {
        state.activeSide != Side.HUMAN -> emptySet()
        protectMode -> protectableIds
        else -> legalMoves.map { it.tokenId }.toSet()
    }
    val humanPowers = state.powers(Side.HUMAN)

    fun resetGame() {
        state = GameState.newGame(mode)
        protectMode = false
        showChooseRoll = false
    }

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
            OutlinedButton(onClick = { resetGame() }) { Text("Reset") }
        }

        Spacer(Modifier.height(12.dp))
        StatusCard(state)
        Spacer(Modifier.height(8.dp))
        PowerInventoryCard(state)
        Spacer(Modifier.height(12.dp))

        LudoBoard(
            state = state,
            selectableTokenIds = selectableIds,
            onTokenTap = { tokenId ->
                if (state.activeSide != Side.HUMAN) return@LudoBoard
                if (protectMode) {
                    if (tokenId in protectableIds && humanPowers.protect > 0) {
                        state = GameEngine.protectToken(state, tokenId)
                    }
                    protectMode = false
                } else {
                    val move = legalMoves.firstOrNull { it.tokenId == tokenId }
                    if (move != null) state = GameEngine.playMove(state, move)
                }
            }
        )

        Spacer(Modifier.height(14.dp))

        if (state.winner == null) {
            if (state.activeSide == Side.HUMAN) {
                HumanPowerControls(
                    state = state,
                    protectMode = protectMode,
                    onProtectModeChange = {
                        protectMode = it
                        if (it) showChooseRoll = false
                    },
                    showChooseRoll = showChooseRoll,
                    onChooseRollToggle = {
                        showChooseRoll = !showChooseRoll
                        if (showChooseRoll) protectMode = false
                    },
                    onChooseRoll = { chosen ->
                        state = GameEngine.useChooseRoll(state, chosen)
                        showChooseRoll = false
                    },
                    onUseDouble = { state = GameEngine.activateDouble(state) },
                    onUseExtraRoll = { state = GameEngine.useExtraRoll(state) }
                )
                Spacer(Modifier.height(10.dp))

                if (state.dice == null) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showChooseRoll = false
                            protectMode = false
                            state = GameEngine.beginRoll(state, Random.nextInt(1, 7))
                        }
                    ) {
                        Text("ROLL DICE")
                    }
                } else if (legalMoves.isEmpty()) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            protectMode = false
                            state = GameEngine.passIfNoMove(state)
                        }
                    ) {
                        Text("NO MOVE • PASS")
                    }
                } else {
                    Text(
                        text = if (protectMode) "Tap a guti to protect it" else "Tap a highlighted guti",
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
                onClick = { resetGame() }
            ) {
                Text("PLAY AGAIN")
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Power icons: ×2 = Double, dice = Choose Roll, shield = Protect, circular + = Extra Roll. Land on a marked cell to collect one charge.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HumanPowerControls(
    state: GameState,
    protectMode: Boolean,
    onProtectModeChange: (Boolean) -> Unit,
    showChooseRoll: Boolean,
    onChooseRollToggle: () -> Unit,
    onChooseRoll: (Int) -> Unit,
    onUseDouble: () -> Unit,
    onUseExtraRoll: () -> Unit
) {
    val powers = state.powers(Side.HUMAN)
    val canProtect = powers.protect > 0 && GameEngine.protectableTokenIds(state).isNotEmpty()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state.dice == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = powers.chooseRoll > 0,
                    onClick = onChooseRollToggle
                ) { Text("Choose ×${powers.chooseRoll}") }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = powers.extraRoll > 0 && state.bonusRollsPending == 0,
                    onClick = onUseExtraRoll
                ) { Text("+1 Roll ×${powers.extraRoll}") }
            }
        } else {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = powers.double > 0 && !state.doubleActive,
                onClick = onUseDouble
            ) {
                Text(if (state.doubleActive) "Double active" else "Double ×${powers.double}")
            }
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = canProtect,
            onClick = { onProtectModeChange(!protectMode) }
        ) {
            Text(if (protectMode) "Cancel Protect" else "Protect Guti ×${powers.protect}")
        }

        if (state.bonusRollsPending > 0) {
            Text(
                text = "+1 extra roll armed",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (showChooseRoll && state.dice == null && powers.chooseRoll > 0) {
            Text(
                text = "Choose your roll",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            for (range in listOf(1..3, 4..6)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    range.forEach { value ->
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { onChooseRoll(value) }
                        ) { Text(value.toString()) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PowerInventoryCard(state: GameState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            PowerInventoryRow("You", state.powers(Side.HUMAN))
            PowerInventoryRow("Computer", state.powers(Side.COMPUTER))
        }
    }
}

@Composable
private fun PowerInventoryRow(label: String, powers: PowerInventory) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        PowerCount(PowerType.DOUBLE, powers.double)
        PowerCount(PowerType.CHOOSE_ROLL, powers.chooseRoll)
        PowerCount(PowerType.PROTECT, powers.protect)
        PowerCount(PowerType.EXTRA_ROLL, powers.extraRoll)
    }
}

@Composable
private fun PowerCount(type: PowerType, count: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PowerIcon(type = type, size = 18.dp)
        Text(count.toString())
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
                    .size(62.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        state.lastRoll == null -> "–"
                        state.dice != null && state.doubleActive -> "${state.dice}→${state.movementSteps}"
                        else -> state.lastRoll.toString()
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.activeSide == Side.HUMAN) {
                        "HUMAN • ${GameEngine.displayName(state.activeColor)}"
                    } else {
                        "COMPUTER • ${GameEngine.displayName(state.activeColor)}"
                    },
                    fontWeight = FontWeight.Bold
                )
                Text(text = state.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
