package com.towhid.ludo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.towhid.ludo.game.model.GameMode
import com.towhid.ludo.ui.game.GameGold
import com.towhid.ludo.ui.game.GameNightBottom
import com.towhid.ludo.ui.game.GameNightMid
import com.towhid.ludo.ui.game.GameNightTop
import com.towhid.ludo.ui.game.GamePanel
import com.towhid.ludo.ui.game.GamePanelStroke
import com.towhid.ludo.ui.game.GameScreen
import com.towhid.ludo.ui.theme.TowhidLudoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TowhidLudoTheme(darkTheme = true) {
                TowhidLudoApp()
            }
        }
    }
}

@Composable
private fun TowhidLudoApp() {
    var modeName by rememberSaveable { mutableStateOf<String?>(null) }
    val mode = modeName?.let(GameMode::valueOf)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = GameNightBottom
    ) { innerPadding ->
        if (mode == null) {
            HomeScreen(
                modifier = Modifier.padding(innerPadding),
                onModeSelected = { modeName = it.name }
            )
        } else {
            GameScreen(
                mode = mode,
                onBack = { modeName = null },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun HomeScreen(
    onModeSelected: (GameMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(GameNightTop, GameNightMid, GameNightBottom)
                )
            )
    ) {
        HomeBackdrop()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LudoMark()
            Spacer(Modifier.height(14.dp))
            Text(
                text = "TOWHID",
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 5.sp
            )
            Text(
                text = "LUDO",
                color = GameGold,
                fontSize = 46.sp,
                lineHeight = 48.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Text(
                text = "POWER MODE",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )

            Spacer(Modifier.height(34.dp))
            ModeCard(
                title = "1 VS 1",
                subtitle = "You vs Computer",
                badge = "SOLO",
                onClick = { onModeSelected(GameMode.ONE_V_ONE) }
            )
            Spacer(Modifier.height(14.dp))
            ModeCard(
                title = "2 VS 2",
                subtitle = "Your team vs Computer team",
                badge = "TEAM",
                onClick = { onModeSelected(GameMode.TWO_V_TWO) }
            )

            Spacer(Modifier.height(28.dp))
            Text(
                text = "OFFLINE  •  BEST-MOVE AI  •  POWER LUDO",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f),
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.38f)
            )
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    badge: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(GamePanel, Color(0xFF1C4968))
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(Color(0xFF0B2438)),
            contentAlignment = Alignment.Center
        ) {
            MiniVersusArt(team = badge == "TEAM")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.68f),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = badge,
                color = GameGold,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Text(
                text = "PLAY  ›",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MiniVersusArt(team: Boolean) {
    Canvas(modifier = Modifier.size(48.dp)) {
        val r = size.minDimension * 0.16f
        val left = size.width * 0.27f
        val right = size.width * 0.73f
        val y = size.height * 0.50f
        drawCircle(Color(0xFF2D7FF9), r, Offset(left, y))
        drawCircle(Color.White, r, Offset(left, y), style = androidx.compose.ui.graphics.drawscope.Stroke(size.width * 0.035f))
        drawCircle(Color(0xFFF04452), r, Offset(right, y))
        drawCircle(Color.White, r, Offset(right, y), style = androidx.compose.ui.graphics.drawscope.Stroke(size.width * 0.035f))
        if (team) {
            drawCircle(Color(0xFFFFC72C), r * 0.72f, Offset(left, y + r * 1.55f))
            drawCircle(Color(0xFF18B96C), r * 0.72f, Offset(right, y + r * 1.55f))
        }
    }
}

@Composable
private fun LudoMark() {
    Box(
        modifier = Modifier
            .size(116.dp)
            .shadow(18.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(GamePanel),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(92.dp)) {
            val half = size.width / 2f
            val inset = size.width * 0.04f
            val radius = size.width * 0.08f
            drawRoundRect(Color(0xFFF04452), Offset(inset, inset), Size(half - inset * 1.5f, half - inset * 1.5f), androidx.compose.ui.geometry.CornerRadius(radius))
            drawRoundRect(Color(0xFF18B96C), Offset(half + inset * 0.5f, inset), Size(half - inset * 1.5f, half - inset * 1.5f), androidx.compose.ui.geometry.CornerRadius(radius))
            drawRoundRect(Color(0xFF2D7FF9), Offset(inset, half + inset * 0.5f), Size(half - inset * 1.5f, half - inset * 1.5f), androidx.compose.ui.geometry.CornerRadius(radius))
            drawRoundRect(Color(0xFFFFC72C), Offset(half + inset * 0.5f, half + inset * 0.5f), Size(half - inset * 1.5f, half - inset * 1.5f), androidx.compose.ui.geometry.CornerRadius(radius))
            drawCircle(Color.White, size.width * 0.22f, center)
            drawCircle(Color(0xFF102A43), size.width * 0.055f, center)
        }
    }
}

@Composable
private fun HomeBackdrop() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val glow = Color.White.copy(alpha = 0.035f)
        drawCircle(glow, size.minDimension * 0.42f, Offset(size.width * 0.08f, size.height * 0.18f))
        drawCircle(glow, size.minDimension * 0.48f, Offset(size.width * 0.95f, size.height * 0.82f))
        repeat(9) { index ->
            val x = size.width * ((index % 3) * 0.38f + 0.12f)
            val y = size.height * ((index / 3) * 0.23f + 0.12f)
            drawCircle(GamePanelStroke.copy(alpha = 0.10f), size.minDimension * 0.012f, Offset(x, y))
        }
    }
}
