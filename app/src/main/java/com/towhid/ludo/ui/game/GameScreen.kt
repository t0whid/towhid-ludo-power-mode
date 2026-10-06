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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.towhid.ludo.game.ai.ComputerAi
import com.towhid.ludo.game.engine.GameEngine
import com.towhid.ludo.game.model.GameMode
import com.towhid.ludo.game.model.GameState
import com.towhid.ludo.game.model.Move
import com.towhid.ludo.game.model.PlayerColor
import com.towhid.ludo.game.model.PlayerState
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
    var rollingFace by remember { mutableIntStateOf(1) }
    var animationLock by remember { mutableStateOf(false) }
    var autoPassPending by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var sessionId by remember { mutableIntStateOf(0) }
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }
    var showResetConfirmation by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val interactionLocked = isRolling || animationLock || autoPassPending

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
        rollingFace = 1
        animationLock = false
        autoPassPending = false
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
                repeat(7) {
                    rollingFace = Random.nextInt(1, 7)
                    delay(52)
                }
                val finalRoll = Random.nextInt(1, 7)
                rollingFace = finalRoll
                val rolled = GameEngine.beginRoll(working, finalRoll)
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

    /* No manual PASS button: when a human roll cannot move anything, the turn
       advances automatically. We pause only when Double could turn that roll
       into a legal move, so the power never gets skipped accidentally. */
    LaunchedEffect(
        state.turnSerial,
        state.dice,
        state.doubleActive,
        state.activeSide,
        animationLock,
        isRolling
    ) {
        autoPassPending = false
        if (
            state.winner == null &&
            state.activeSide == Side.HUMAN &&
            state.dice != null &&
            !animationLock &&
            !isRolling &&
            GameEngine.legalMoves(state).isEmpty() &&
            !GameEngine.doubleWouldEnableMove(state)
        ) {
            val serial = state.turnSerial
            val roll = state.dice
            autoPassPending = true
            delay(720)
            if (
                state.turnSerial == serial &&
                state.activeSide == Side.HUMAN &&
                state.dice == roll &&
                GameEngine.legalMoves(state).isEmpty()
            ) {
                protectMode = false
                showChooseRoll = false
                state = GameEngine.passIfNoMove(state)
            }
            autoPassPending = false
        }
    }

    val legalMoves = if (state.dice != null) GameEngine.legalMoves(state) else emptyList()
    val protectableIds = if (state.activeSide == Side.HUMAN) GameEngine.protectableTokenIds(state) else emptySet()
    val selectableIds = when {
        interactionLocked || state.activeSide != Side.HUMAN -> emptySet()
        protectMode -> protectableIds
        else -> legalMoves.map { it.tokenId }.toSet()
    }
    val humanPowers = state.activePlayer.takeIf { it.side == Side.HUMAN }?.powers ?: PowerInventory()

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(GameNightTop, GameNightMid, GameNightBottom)
                )
            )
    ) {
        GameRoomBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 5.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GameTopBar(
                mode = mode,
                enabled = !interactionLocked,
                onBack = { showExitConfirmation = true },
                onReset = { showResetConfirmation = true }
            )

            Spacer(Modifier.height(3.dp))

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Keep the whole match on one screen. The 118dp reserve is shared
                // by the four corner seats around the square board.
                val verticalRoom = if (maxHeight > 360.dp) maxHeight - 118.dp else maxHeight * 0.72f
                val boardSize = if (maxWidth < verticalRoom) maxWidth else verticalRoom

                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(boardSize)
                        .shadow(22.dp, RoundedCornerShape(5.dp)),
                    shape = RoundedCornerShape(5.dp),
                    color = Color(0xFFF4F7FA),
                    border = BorderStroke(2.dp, Color(0xFF171A45).copy(alpha = 0.96f))
                ) {
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
                        modifier = Modifier.padding(0.5.dp)
                    )
                }

                CornerPlayerSeat(
                    state = state,
                    color = PlayerColor.RED,
                    alignRight = false,
                    modifier = Modifier.align(Alignment.TopStart)
                )
                CornerPlayerSeat(
                    state = state,
                    color = PlayerColor.GREEN,
                    alignRight = true,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
                CornerPlayerSeat(
                    state = state,
                    color = PlayerColor.BLUE,
                    alignRight = false,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
                CornerPlayerSeat(
                    state = state,
                    color = PlayerColor.YELLOW,
                    alignRight = true,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )

                StatusPill(
                    state = state,
                    rolling = isRolling,
                    animationLock = animationLock,
                    autoPassPending = autoPassPending,
                    protectMode = protectMode,
                    legalMoves = legalMoves,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = 17.dp - boardSize / 2f)
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = feedback != null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = 48.dp - boardSize / 2f),
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 })
                ) {
                    feedback?.let { EventBanner(it) }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
            ) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    HumanPowerOrbs(
                        state = state,
                        enabled = state.winner == null && !isRolling && !animationLock,
                        protectMode = protectMode,
                        showChooseRoll = showChooseRoll,
                        onProtectModeChange = {
                            protectMode = it
                            if (it) showChooseRoll = false
                        },
                        onChooseRollToggle = {
                            showChooseRoll = !showChooseRoll
                            if (showChooseRoll) protectMode = false
                        },
                        onUseDouble = {
                            if (!interactionLocked) state = GameEngine.activateDouble(state)
                        },
                        onUseExtraRoll = {
                            if (!interactionLocked) state = GameEngine.useExtraRoll(state)
                        }
                    )

                    DiceActionBubble(
                        state = state,
                        side = Side.HUMAN,
                        rolling = isRolling,
                        rollingFace = rollingFace,
                        enabled = state.winner == null &&
                            !interactionLocked &&
                            state.activeSide == Side.HUMAN &&
                            state.dice == null,
                        autoPassPending = false,
                        onRoll = {
                            if (state.winner == null && state.activeSide == Side.HUMAN && state.dice == null && !isRolling && !animationLock) {
                                showChooseRoll = false
                                protectMode = false
                                val currentSession = sessionId
                                val rollState = state
                                isRolling = true
                                scope.launch {
                                    repeat(7) {
                                        rollingFace = Random.nextInt(1, 7)
                                        delay(52)
                                    }
                                    if (sessionId == currentSession) {
                                        val finalRoll = Random.nextInt(1, 7)
                                        rollingFace = finalRoll
                                        state = GameEngine.beginRoll(rollState, finalRoll)
                                    }
                                    isRolling = false
                                }
                            }
                        }
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showChooseRoll && GameEngine.canUsePower(state, PowerType.CHOOSE_ROLL),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-50).dp)
                ) {
                    ChooseDiceTray(
                        enabled = state.winner == null && !interactionLocked,
                        onChooseRoll = { chosen ->
                            if (!interactionLocked) {
                                state = GameEngine.useChooseRoll(state, chosen)
                                showChooseRoll = false
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GameRoomBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFF44478F),
                    Color(0xFF393D82),
                    Color(0xFF2E326F)
                )
            )
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF8A8EE8).copy(alpha = 0.22f), Color.Transparent),
                center = Offset(size.width * 0.52f, size.height * 0.42f),
                radius = size.width * 0.78f
            ),
            radius = size.width * 0.78f,
            center = Offset(size.width * 0.52f, size.height * 0.42f)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.028f),
            radius = size.width * 0.34f,
            center = Offset(size.width * 0.06f, size.height * 0.20f)
        )
        drawCircle(
            color = Color(0xFF11163F).copy(alpha = 0.20f),
            radius = size.width * 0.62f,
            center = Offset(size.width * 0.94f, size.height * 0.88f)
        )
    }
}

@Composable
private fun GameTopBar(
    mode: GameMode,
    enabled: Boolean,
    onBack: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundIconButton(symbol = "‹", enabled = enabled, onClick = onBack)
            RoundIconButton(symbol = "↻", enabled = enabled, onClick = onReset)
        }
        Surface(
            color = Color(0xFF25295F).copy(alpha = 0.72f),
            shape = RoundedCornerShape(17.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            shadowElevation = 4.dp
        ) {
            Text(
                text = if (mode == GameMode.ONE_V_ONE) "1 VS 1" else "2 VS 2",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                color = Color.White.copy(alpha = 0.92f),
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )
        }
        RoundIconButton(symbol = "☀", enabled = false, onClick = {})
    }
}

@Composable
private fun RoundIconButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .shadow(5.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF343A73), Color(0xFF1C214E))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            color = Color.White.copy(alpha = if (enabled) 0.92f else 0.52f),
            fontSize = if (symbol == "‹") 31.sp else 20.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun SideZone(
    state: GameState,
    side: Side,
    rolling: Boolean,
    rollingFace: Int,
    autoPassPending: Boolean
) {
    val players = state.players.filter { it.side == side }
    val activeColor = state.activeColor.takeIf { state.activeSide == side && state.winner == null }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (players.size > 1) 124.dp else 112.dp)
    ) {
        players.getOrNull(0)?.let { player ->
            PlayerSeat(
                player = player,
                side = side,
                powers = state.powers(player.color),
                active = activeColor == player.color,
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }
        players.getOrNull(1)?.let { player ->
            PlayerSeat(
                player = player,
                side = side,
                powers = state.powers(player.color),
                active = activeColor == player.color,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
        if (side == Side.COMPUTER) {
            DiceActionBubble(
                state = state,
                side = side,
                rolling = rolling,
                rollingFace = rollingFace,
                enabled = false,
                autoPassPending = autoPassPending,
                onRoll = {},
                modifier = Modifier
                    .align(if (players.size > 1 && activeColor == players.getOrNull(1)?.color) Alignment.BottomEnd else Alignment.BottomStart)
                    .offset(x = if (players.size > 1 && activeColor == players.getOrNull(1)?.color) (-86).dp else 86.dp, y = 4.dp)
            )
        }
    }
}

@Composable
private fun HumanZone(
    state: GameState,
    rolling: Boolean,
    rollingFace: Int,
    enabled: Boolean,
    protectMode: Boolean,
    showChooseRoll: Boolean,
    onRoll: () -> Unit,
    onProtectModeChange: (Boolean) -> Unit,
    onChooseRollToggle: () -> Unit,
    onChooseRoll: (Int) -> Unit,
    onUseDouble: () -> Unit,
    onUseExtraRoll: () -> Unit
) {
    val players = state.players.filter { it.side == Side.HUMAN }
    val activeColor = state.activeColor.takeIf { state.activeSide == Side.HUMAN && state.winner == null }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (players.size > 1) 96.dp else 88.dp)
        ) {
            players.getOrNull(0)?.let { player ->
                PlayerSeat(
                    player = player,
                    side = Side.HUMAN,
                    powers = state.powers(player.color),
                    active = activeColor == player.color,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
            players.getOrNull(1)?.let { player ->
                PlayerSeat(
                    player = player,
                    side = Side.HUMAN,
                    powers = state.powers(player.color),
                    active = activeColor == player.color,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            HumanPowerOrbs(
                state = state,
                enabled = enabled,
                protectMode = protectMode,
                showChooseRoll = showChooseRoll,
                onProtectModeChange = onProtectModeChange,
                onChooseRollToggle = onChooseRollToggle,
                onUseDouble = onUseDouble,
                onUseExtraRoll = onUseExtraRoll
            )
            DiceActionBubble(
                state = state,
                side = Side.HUMAN,
                rolling = rolling,
                rollingFace = rollingFace,
                enabled = enabled && state.activeSide == Side.HUMAN && state.dice == null,
                autoPassPending = false,
                onRoll = onRoll
            )
        }

        AnimatedVisibility(visible = showChooseRoll && GameEngine.canUsePower(state, PowerType.CHOOSE_ROLL)) {
            ChooseDiceTray(enabled = enabled, onChooseRoll = onChooseRoll)
        }
    }
}

@Composable
private fun CornerPlayerSeat(
    state: GameState,
    color: PlayerColor,
    alignRight: Boolean,
    modifier: Modifier = Modifier
) {
    val player = state.players.firstOrNull { it.color == color } ?: return
    val active = state.winner == null && state.activeColor == color
    val accent = gameColor(color)
    val label = when {
        player.side == Side.HUMAN && state.mode == GameMode.TWO_V_TWO && color == PlayerColor.GREEN -> "PARTNER"
        player.side == Side.HUMAN -> "YOU"
        color == PlayerColor.YELLOW -> "CPU 2"
        else -> "CPU 1"
    }

    @Composable
    fun Avatar() {
        Box(contentAlignment = Alignment.BottomEnd) {
            PlayerAvatar(
                side = player.side,
                accent = accent,
                active = active,
                modifier = Modifier.size(52.dp)
            )
            Surface(
                color = accent,
                shape = RoundedCornerShape(7.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.72f)),
                shadowElevation = 2.dp
            ) {
                Text(
                    text = color.name.take(1),
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }

    @Composable
    fun Info() {
        Column(
            horizontalAlignment = if (alignRight) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Surface(
                color = Color(0xFF20255B).copy(alpha = 0.84f),
                shape = RoundedCornerShape(9.dp),
                border = BorderStroke(1.dp, if (active) accent else Color.White.copy(alpha = 0.08f))
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    color = if (active) Color.White else Color.White.copy(alpha = 0.70f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black
                )
            }
            PlayerPowerCounts(powers = state.powers(color), active = active)
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (alignRight) {
            Info()
            Avatar()
        } else {
            Avatar()
            Info()
        }
    }
}

@Composable
private fun PlayerSeat(
    player: PlayerState,
    side: Side,
    powers: PowerInventory,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = gameColor(player.color)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            PlayerAvatar(
                side = side,
                accent = accent,
                active = active,
                modifier = Modifier.size(64.dp)
            )
            Surface(
                color = accent,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.75f)),
                shadowElevation = 3.dp
            ) {
                Text(
                    text = player.color.name.take(1),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                color = Color(0xFF20255B).copy(alpha = 0.80f),
                shape = RoundedCornerShape(11.dp),
                border = BorderStroke(1.dp, if (active) accent else Color.White.copy(alpha = 0.08f))
            ) {
                Text(
                    text = if (side == Side.HUMAN) "YOU" else "CPU",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    color = if (active) Color.White else Color.White.copy(alpha = 0.68f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
            }
            PlayerPowerCounts(powers = powers, active = active)
        }
    }
}

@Composable
private fun PlayerAvatar(
    side: Side,
    accent: Color,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "avatarGlow")
    val glow by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(820), repeatMode = androidx.compose.animation.core.RepeatMode.Reverse),
        label = "avatarGlowAlpha"
    )
    Canvas(modifier = modifier) {
        val radius = size.minDimension * 0.40f
        val c = center
        drawCircle(Color.Black.copy(alpha = 0.28f), radius * 1.18f, c + Offset(0f, radius * 0.09f))
        if (active) {
            drawCircle(accent.copy(alpha = 0.28f * glow), radius * 1.30f, c)
            drawCircle(Color.White.copy(alpha = 0.22f * glow), radius * 1.18f, c)
        }
        drawCircle(Color(0xFF171B49), radius * 1.11f, c)
        drawCircle(accent, radius * 1.02f, c)
        drawCircle(Color.White, radius * 0.82f, c)
        drawCircle(Color(0xFFF3F5FF), radius * 0.72f, c)

        if (side == Side.HUMAN) {
            drawCircle(accent.copy(alpha = 0.95f), radius * 0.24f, Offset(c.x, c.y - radius * 0.20f))
            drawRoundRect(
                color = accent.copy(alpha = 0.95f),
                topLeft = Offset(c.x - radius * 0.42f, c.y + radius * 0.10f),
                size = Size(radius * 0.84f, radius * 0.46f),
                cornerRadius = CornerRadius(radius * 0.23f)
            )
        } else {
            drawRoundRect(
                color = accent,
                topLeft = Offset(c.x - radius * 0.42f, c.y - radius * 0.30f),
                size = Size(radius * 0.84f, radius * 0.62f),
                cornerRadius = CornerRadius(radius * 0.17f)
            )
            drawCircle(Color.White, radius * 0.08f, Offset(c.x - radius * 0.17f, c.y - radius * 0.04f))
            drawCircle(Color.White, radius * 0.08f, Offset(c.x + radius * 0.17f, c.y - radius * 0.04f))
            drawLine(
                color = accent,
                start = Offset(c.x, c.y - radius * 0.50f),
                end = Offset(c.x, c.y - radius * 0.30f),
                strokeWidth = radius * 0.08f
            )
            drawCircle(accent, radius * 0.09f, Offset(c.x, c.y - radius * 0.54f))
        }
    }
}

@Composable
private fun PlayerPowerCounts(powers: PowerInventory, active: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerPowerCount(PowerType.PROTECT, powers.protect, active)
        PlayerPowerCount(PowerType.CHOOSE_ROLL, powers.chooseRoll, active)
        PlayerPowerCount(PowerType.DOUBLE, powers.double, active)
        PlayerPowerCount(PowerType.EXTRA_ROLL, powers.extraRoll, active)
    }
}

@Composable
private fun PlayerPowerCount(type: PowerType, count: Int, active: Boolean) {
    Box(
        modifier = Modifier
            .size(25.dp)
            .shadow(if (active && count > 0) 4.dp else 1.dp, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    if (count > 0) {
                        listOf(Color.White, Color(0xFF82C7FF), Color(0xFF315DA6), Color(0xFF17244F))
                    } else {
                        listOf(Color(0xFF69729A), Color(0xFF30385F), Color(0xFF1A2047))
                    }
                )
            )
            .border(1.dp, Color.White.copy(alpha = if (count > 0) 0.52f else 0.16f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        PowerIcon(
            type = type,
            size = 14.dp,
            tint = if (count > 0) Color.White else Color.White.copy(alpha = 0.28f)
        )
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).size(10.dp),
            shape = CircleShape,
            color = if (count > 0) Color(0xFFF04959) else Color(0xFF4B5272),
            border = BorderStroke(0.7.dp, Color.White.copy(alpha = 0.55f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = count.coerceAtMost(9).toString(),
                    color = Color.White,
                    fontSize = 6.sp,
                    lineHeight = 6.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun InventoryPreview(powers: PowerInventory, compact: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)) {
        MiniPowerOrb(PowerType.DOUBLE, powers.double)
        MiniPowerOrb(PowerType.CHOOSE_ROLL, powers.chooseRoll)
        MiniPowerOrb(PowerType.PROTECT, powers.protect)
        MiniPowerOrb(PowerType.EXTRA_ROLL, powers.extraRoll)
    }
}

@Composable
private fun MiniPowerOrb(type: PowerType, count: Int) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF86C5FF), Color(0xFF2F5FA8), Color(0xFF182653))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        PowerIcon(
            type = type,
            size = 21.dp,
            tint = if (count > 0) Color.White else Color.White.copy(alpha = 0.35f)
        )
        if (count > 0) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).size(15.dp),
                shape = CircleShape,
                color = Color(0xFFF04B5A)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(count.toString(), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun HumanPowerOrbs(
    state: GameState,
    enabled: Boolean,
    protectMode: Boolean,
    showChooseRoll: Boolean,
    onProtectModeChange: (Boolean) -> Unit,
    onChooseRollToggle: () -> Unit,
    onUseDouble: () -> Unit,
    onUseExtraRoll: () -> Unit
) {
    val powers = state.activePlayer.powers
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PowerOrb(
            type = PowerType.PROTECT,
            count = powers.protect,
            enabled = enabled && GameEngine.canUsePower(state, PowerType.PROTECT),
            active = protectMode,
            onClick = { onProtectModeChange(!protectMode) }
        )
        PowerOrb(
            type = PowerType.CHOOSE_ROLL,
            count = powers.chooseRoll,
            enabled = enabled && GameEngine.canUsePower(state, PowerType.CHOOSE_ROLL),
            active = showChooseRoll,
            onClick = onChooseRollToggle
        )
        PowerOrb(
            type = PowerType.DOUBLE,
            count = powers.double,
            enabled = enabled && GameEngine.canUsePower(state, PowerType.DOUBLE),
            active = state.doubleActive,
            onClick = onUseDouble
        )
        PowerOrb(
            type = PowerType.EXTRA_ROLL,
            count = powers.extraRoll,
            enabled = enabled && GameEngine.canUsePower(state, PowerType.EXTRA_ROLL),
            active = state.bonusRollsPending > 0,
            onClick = onUseExtraRoll
        )
    }
}

@Composable
private fun PowerOrb(
    type: PowerType,
    count: Int,
    enabled: Boolean,
    active: Boolean,
    onClick: () -> Unit
) {
    val outer = when {
        active -> GameGold
        enabled -> Color(0xFF84C5FF)
        else -> Color(0xFF4F5A88)
    }
    Box(
        modifier = Modifier
            .size(45.dp)
            .shadow(if (active || enabled) 7.dp else 2.dp, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    if (active) {
                        listOf(Color(0xFFFFE787), Color(0xFFC58B16), Color(0xFF5A3A09))
                    } else {
                        listOf(Color.White, Color(0xFF9DD9FF), Color(0xFF4175BF), Color(0xFF18234D))
                    }
                )
            )
            .border(2.dp, outer.copy(alpha = if (enabled || active) 0.95f else 0.42f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        PowerIcon(
            type = type,
            size = 27.dp,
            tint = if (enabled || active) Color.White else Color.White.copy(alpha = 0.30f)
        )
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).size(17.dp),
            shape = CircleShape,
            color = if (count > 0) Color(0xFFF04B5A) else Color(0xFF535A78),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(count.toString(), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun DiceActionBubble(
    state: GameState,
    side: Side,
    rolling: Boolean,
    rollingFace: Int,
    enabled: Boolean,
    autoPassPending: Boolean,
    onRoll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = state.activeSide == side && state.winner == null
    val infiniteTransition = rememberInfiniteTransition(label = "dice")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(560, easing = LinearEasing)),
        label = "diceRotation"
    )

    Box(
        modifier = modifier
            .size(70.dp)
            .shadow(if (active) 10.dp else 4.dp, RoundedCornerShape(17.dp))
            .clip(RoundedCornerShape(17.dp))
            .background(
                Brush.verticalGradient(
                    if (active) listOf(Color(0xFF474D93), Color(0xFF20265F), Color(0xFF14183E))
                    else listOf(Color(0xFF34396F), Color(0xFF171A42))
                )
            )
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) Color.White.copy(alpha = 0.48f) else Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(17.dp)
            )
            .clickable(enabled = enabled, onClick = onRoll),
        contentAlignment = Alignment.Center
    ) {
        when {
            autoPassPending -> Text(
                text = "PASS",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
            rolling && active -> DiceFace(
                value = rollingFace.coerceIn(1, 6),
                modifier = Modifier.graphicsLayer { rotationZ = rotation },
                size = 51.dp
            )
            active && state.dice == null && side == Side.HUMAN -> Surface(
                color = Color.White,
                shape = RoundedCornerShape(11.dp),
                border = BorderStroke(2.dp, Color(0xFFD9DDEB))
            ) {
                Text(
                    text = "GO",
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
                    color = Color(0xFF191D4C),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
            }
            active && state.dice == null -> Text(
                text = "•••",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )
            else -> DiceFace(
                value = if (active) state.dice ?: state.lastRoll else state.lastRoll,
                size = 51.dp
            )
        }

        if (active && state.doubleActive) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd),
                color = GameGold,
                shape = CircleShape
            ) {
                Text(
                    text = "×2",
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    color = Color(0xFF3C2B00),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun ChooseDiceTray(enabled: Boolean, onChooseRoll: (Int) -> Unit) {
    Surface(
        color = Color(0xFF20255B).copy(alpha = 0.96f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            (1..6).forEach { value ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable(enabled = enabled) { onChooseRoll(value) },
                    contentAlignment = Alignment.Center
                ) {
                    DiceFace(value = value, size = 36.dp)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    state: GameState,
    rolling: Boolean,
    animationLock: Boolean,
    autoPassPending: Boolean,
    protectMode: Boolean,
    legalMoves: List<Move>,
    modifier: Modifier = Modifier
) {
    val text = when {
        autoPassPending -> "No move • auto passing…"
        rolling -> "Rolling dice…"
        animationLock && state.activeSide == Side.COMPUTER -> "Computer is moving…"
        animationLock -> "Moving guti…"
        protectMode -> "Tap a guti to protect it"
        state.activeSide == Side.COMPUTER -> "Computer thinking…"
        state.dice == null -> "Your turn"
        legalMoves.isNotEmpty() -> "Tap a glowing guti"
        GameEngine.doubleWouldEnableMove(state) -> "No normal move • ×2 can unlock a move"
        else -> state.message
    }

    Surface(
        modifier = modifier,
        color = Color(0xFF20255B).copy(alpha = 0.80f),
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EventBanner(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        color = Color(0xFF2F3470).copy(alpha = 0.96f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, GameGold.copy(alpha = 0.70f))
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Black,
            color = Color(0xFFFFE695),
            fontSize = 11.sp
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
private fun ResultDialog(
    winner: Side,
    mode: GameMode,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit
) {
    val humanWon = winner == Side.HUMAN
    AlertDialog(
        onDismissRequest = {},
        icon = {
            Text(if (humanWon) "★" else "◆", color = GameGold, fontSize = 34.sp, fontWeight = FontWeight.Black)
        },
        title = {
            Text(
                text = if (humanWon) "YOU WIN!" else "COMPUTER WINS",
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
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
        confirmButton = { Button(onClick = onPlayAgain) { Text("PLAY AGAIN") } },
        dismissButton = { OutlinedButton(onClick = onExit) { Text("EXIT") } }
    )
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
