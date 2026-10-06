package com.towhid.ludo.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.towhid.ludo.game.ai.ComputerAi
import com.towhid.ludo.game.engine.GameEngine
import com.towhid.ludo.game.model.GameMode
import com.towhid.ludo.game.model.GameState
import com.towhid.ludo.game.model.Move
import com.towhid.ludo.game.model.PlayerColor
import com.towhid.ludo.game.model.PowerInventory
import com.towhid.ludo.game.model.PowerType
import com.towhid.ludo.game.model.Side
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

@Composable
fun GameScreen(
    mode: GameMode,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var state by rememberSaveable(mode, stateSaver = GameStateSaver) {
        mutableStateOf(GameState.newGame(mode))
    }
    var protectMode by remember { mutableStateOf(false) }
    var showChooseRoll by remember { mutableStateOf(false) }
    var isRolling by remember { mutableStateOf(false) }
    var animationLock by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var sessionId by remember { mutableIntStateOf(0) }
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }
    var showResetConfirmation by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val interactionLocked = isRolling || animationLock

    fun unlockAfter(move: Move) {
        val currentSession = sessionId
        animationLock = true
        scope.launch {
            delay(moveAnimationMillis(move))
            if (sessionId == currentSession) animationLock = false
        }
    }

    fun resetGame() {
        sessionId += 1
        state = GameState.newGame(mode)
        protectMode = false
        showChooseRoll = false
        isRolling = false
        animationLock = false
        feedback = null
    }

    LaunchedEffect(state.message) {
        val event = feedbackFor(state.message)
        if (event != null) {
            feedback = event
            delay(1450)
            if (feedback == event) feedback = null
        }
    }

    LaunchedEffect(state.turnSerial, state.winner, animationLock) {
        if (
            state.winner != null ||
            state.activeSide != Side.COMPUTER ||
            animationLock ||
            isRolling
        ) return@LaunchedEffect

        delay(350)
        var working = state

        // A saved/recreated game can resume after the computer already rolled. In that case
        // skip the pre-roll phase and continue directly with the best after-roll decision.
        if (working.dice == null) {
            val preRoll = withContext(Dispatchers.Default) { ComputerAi.choosePreRollDecision(working) }
            if (preRoll.useExtraRoll) {
                working = GameEngine.useExtraRoll(working)
                state = working
                delay(260)
            }

            working = if (preRoll.chosenRoll != null) {
                GameEngine.useChooseRoll(working, preRoll.chosenRoll)
            } else {
                isRolling = true
                delay(320)
                val rolled = GameEngine.beginRoll(working, Random.nextInt(1, 7))
                isRolling = false
                rolled
            }
            state = working
            delay(430)
        }

        val decision = withContext(Dispatchers.Default) { ComputerAi.chooseAfterRollDecision(working) }
        decision.protectTokenId?.let { tokenId ->
            working = GameEngine.protectToken(working, tokenId)
            state = working
            delay(260)
        }
        if (decision.useDouble) {
            working = GameEngine.activateDouble(working)
            state = working
            delay(260)
        }

        val move = decision.moveTokenId?.let { tokenId ->
            GameEngine.legalMoves(working).firstOrNull { it.tokenId == tokenId }
        }
        if (move != null) {
            unlockAfter(move)
            state = GameEngine.playMove(working, move)
        } else {
            state = GameEngine.passIfNoMove(working)
        }
    }

    val legalMoves = if (state.dice != null) GameEngine.legalMoves(state) else emptyList()
    val protectableIds = if (state.activeSide == Side.HUMAN) GameEngine.protectableTokenIds(state) else emptySet()
    val selectableIds = when {
        interactionLocked || state.activeSide != Side.HUMAN -> emptySet()
        protectMode -> protectableIds
        else -> legalMoves.map { it.tokenId }.toSet()
    }
    val humanPowers = state.powers(Side.HUMAN)

    BackHandler(enabled = state.winner == null) {
        showExitConfirmation = true
    }

    if (showExitConfirmation) {
        ConfirmActionDialog(
            title = "Leave this match?",
            message = "Your current match will be closed.",
            confirmLabel = "LEAVE",
            onConfirm = {
                showExitConfirmation = false
                onBack()
            },
            onDismiss = { showExitConfirmation = false }
        )
    }

    if (showResetConfirmation) {
        ConfirmActionDialog(
            title = "Restart match?",
            message = "All guti positions and collected powers will be reset.",
            confirmLabel = "RESTART",
            onConfirm = {
                showResetConfirmation = false
                resetGame()
            },
            onDismiss = { showResetConfirmation = false }
        )
    }

    if (state.winner != null) {
        ResultDialog(
            winner = state.winner!!,
            mode = mode,
            onPlayAgain = { resetGame() },
            onExit = onBack
        )
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
            OutlinedButton(onClick = { showExitConfirmation = true }, enabled = !interactionLocked) { Text("Back") }
            Text(
                text = if (mode == GameMode.ONE_V_ONE) "1 vs 1" else "2 vs 2",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(onClick = { showResetConfirmation = true }, enabled = !interactionLocked) { Text("Reset") }
        }

        Spacer(Modifier.height(12.dp))
        StatusCard(state = state, rolling = isRolling)
        Spacer(Modifier.height(8.dp))
        MatchProgressCard(state = state)

        AnimatedVisibility(
            visible = feedback != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 })
        ) {
            feedback?.let { EventBanner(it) }
        }

        Spacer(Modifier.height(8.dp))
        PowerInventoryCard(state)
        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            LudoBoard(
                state = state,
                selectableTokenIds = selectableIds,
                onTokenTap = { tokenId ->
                    if (interactionLocked || state.activeSide != Side.HUMAN) return@LudoBoard
                    if (protectMode) {
                        if (tokenId in protectableIds && humanPowers.protect > 0) {
                            state = GameEngine.protectToken(state, tokenId)
                        }
                        protectMode = false
                    } else {
                        val move = legalMoves.firstOrNull { it.tokenId == tokenId }
                        if (move != null) {
                            unlockAfter(move)
                            state = GameEngine.playMove(state, move)
                        }
                    }
                },
                modifier = Modifier.padding(4.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        if (state.winner == null) {
            if (state.activeSide == Side.HUMAN) {
                HumanPowerControls(
                    state = state,
                    enabled = !interactionLocked,
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
                        if (!interactionLocked) {
                            state = GameEngine.useChooseRoll(state, chosen)
                            showChooseRoll = false
                        }
                    },
                    onUseDouble = {
                        if (!interactionLocked) state = GameEngine.activateDouble(state)
                    },
                    onUseExtraRoll = {
                        if (!interactionLocked) state = GameEngine.useExtraRoll(state)
                    }
                )
                Spacer(Modifier.height(10.dp))

                if (state.dice == null) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !interactionLocked,
                        onClick = {
                            showChooseRoll = false
                            protectMode = false
                            val currentSession = sessionId
                            isRolling = true
                            scope.launch {
                                delay(320)
                                if (sessionId == currentSession) {
                                    state = GameEngine.beginRoll(state, Random.nextInt(1, 7))
                                    isRolling = false
                                }
                            }
                        }
                    ) {
                        Text(if (isRolling) "ROLLING…" else "ROLL DICE")
                    }
                } else if (legalMoves.isEmpty()) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !interactionLocked,
                        onClick = {
                            protectMode = false
                            state = GameEngine.passIfNoMove(state)
                        }
                    ) {
                        Text("NO MOVE • PASS")
                    }
                } else {
                    Text(
                        text = when {
                            animationLock -> "Moving guti…"
                            protectMode -> "Tap a guti to protect it"
                            else -> "Tap a highlighted guti"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text(
                    text = if (animationLock) "Computer is moving…" else "Computer is calculating the best move…",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
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
    enabled: Boolean,
    protectMode: Boolean,
    onProtectModeChange: (Boolean) -> Unit,
    showChooseRoll: Boolean,
    onChooseRollToggle: () -> Unit,
    onChooseRoll: (Int) -> Unit,
    onUseDouble: () -> Unit,
    onUseExtraRoll: () -> Unit
) {
    val powers = state.powers(Side.HUMAN)
    val canProtect = enabled && GameEngine.canUsePower(state, PowerType.PROTECT)
    val canChooseRoll = enabled && GameEngine.canUsePower(state, PowerType.CHOOSE_ROLL)
    val canExtraRoll = enabled && GameEngine.canUsePower(state, PowerType.EXTRA_ROLL)
    val canDouble = enabled && GameEngine.canUsePower(state, PowerType.DOUBLE)

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
                    enabled = canChooseRoll,
                    onClick = onChooseRollToggle
                ) {
                    PowerIcon(PowerType.CHOOSE_ROLL, size = 18.dp)
                    Spacer(Modifier.size(6.dp))
                    Text("Choose ×${powers.chooseRoll}")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = canExtraRoll,
                    onClick = onUseExtraRoll
                ) {
                    PowerIcon(PowerType.EXTRA_ROLL, size = 18.dp)
                    Spacer(Modifier.size(6.dp))
                    Text("+1 Roll ×${powers.extraRoll}")
                }
            }
        } else {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = canDouble,
                onClick = onUseDouble
            ) {
                PowerIcon(PowerType.DOUBLE, size = 18.dp)
                Spacer(Modifier.size(6.dp))
                Text(if (state.doubleActive) "Double active" else "Double ×${powers.double}")
            }
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = canProtect,
            onClick = { onProtectModeChange(!protectMode) }
        ) {
            PowerIcon(PowerType.PROTECT, size = 18.dp)
            Spacer(Modifier.size(6.dp))
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

        if (showChooseRoll && canChooseRoll) {
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
                            enabled = enabled,
                            onClick = { onChooseRoll(value) }
                        ) { Text(value.toString()) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventBanner(text: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}

@Composable
private fun MatchProgressCard(state: GameState) {
    fun finished(side: Side): Int = state.players
        .filter { it.side == side }
        .sumOf { player -> player.tokens.count { it.isFinished } }

    fun total(side: Side): Int = state.players
        .filter { it.side == side }
        .sumOf { it.tokens.size }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "YOU  ${finished(Side.HUMAN)}/${total(Side.HUMAN)} HOME",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "COMPUTER  ${finished(Side.COMPUTER)}/${total(Side.COMPUTER)} HOME",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResultDialog(
    winner: Side,
    mode: GameMode,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit
) {
    val humanWon = winner == Side.HUMAN
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = if (humanWon) "You Win!" else "Computer Wins",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                if (humanWon) {
                    if (mode == GameMode.ONE_V_ONE) "All 4 of your guti reached home." else "Your team brought all 8 guti home."
                } else {
                    if (mode == GameMode.ONE_V_ONE) "Computer brought all 4 guti home first." else "Computer team brought all 8 guti home first."
                }
            )
        },
        confirmButton = {
            Button(onClick = onPlayAgain) { Text("PLAY AGAIN") }
        },
        dismissButton = {
            OutlinedButton(onClick = onExit) { Text("EXIT") }
        }
    )
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
private fun StatusCard(state: GameState, rolling: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "dice")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 560, easing = LinearEasing)
        ),
        label = "diceRotation"
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DiceFace(
                value = if (rolling) 6 else state.lastRoll,
                modifier = Modifier.graphicsLayer { rotationZ = if (rolling) rotation else 0f },
                size = 58.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    PlayerColorDot(state.activeColor)
                    Text(
                        text = if (state.activeSide == Side.HUMAN) {
                            "YOU • ${GameEngine.displayName(state.activeColor)}"
                        } else {
                            "COMPUTER • ${GameEngine.displayName(state.activeColor)}"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = if (rolling) "Rolling dice…" else state.message,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (state.dice != null && state.doubleActive) {
                    Text(
                        text = "Double active: ${state.dice} → ${state.movementSteps}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerColorDot(color: PlayerColor) {
    val fill = when (color) {
        PlayerColor.RED -> Color(0xFFE53935)
        PlayerColor.GREEN -> Color(0xFF2E9D57)
        PlayerColor.YELLOW -> Color(0xFFF4B522)
        PlayerColor.BLUE -> Color(0xFF2474D8)
    }
    Canvas(modifier = Modifier.size(12.dp)) {
        drawCircle(fill)
    }
}

private fun moveAnimationMillis(move: Move): Long =
    (move.dice * 70L + 190L).coerceIn(330L, 1_080L)

private fun feedbackFor(message: String): String? {
    val text = message.substringBefore(" — ")
    return when {
        "captured" in text -> text
        "brought a token home" in text -> text
        " • +" in text -> text
        "protected token" in text -> text
        "doubled" in text -> text
        "chose" in text -> text
        "armed +1" in text -> text
        else -> null
    }
}
