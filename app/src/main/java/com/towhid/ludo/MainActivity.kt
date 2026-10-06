package com.towhid.ludo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.towhid.ludo.game.model.GameMode
import com.towhid.ludo.ui.game.GameScreen
import com.towhid.ludo.ui.theme.TowhidLudoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TowhidLudoTheme {
                TowhidLudoApp()
            }
        }
    }
}

@Composable
private fun TowhidLudoApp() {
    var mode by remember { mutableStateOf<GameMode?>(null) }
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        if (mode == null) {
            HomeScreen(
                modifier = Modifier.padding(innerPadding),
                onModeSelected = { mode = it }
            )
        } else {
            GameScreen(
                mode = mode!!,
                onBack = { mode = null },
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
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "TowhidLudo",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "Human vs Computer • Power Ludo",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )
        Button(
            onClick = { onModeSelected(GameMode.ONE_V_ONE) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("1 vs 1")
        }
        Button(
            onClick = { onModeSelected(GameMode.TWO_V_TWO) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            Text("2 vs 2")
        }
    }
}
