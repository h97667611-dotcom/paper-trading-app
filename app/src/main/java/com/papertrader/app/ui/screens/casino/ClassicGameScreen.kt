package com.papertrader.app.ui.screens.casino

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.papertrader.app.ui.components.ChipPill
import com.papertrader.app.ui.theme.LossRed
import com.papertrader.app.ui.theme.ProfitGreen
import com.papertrader.app.ui.theme.SurfaceCard
import com.papertrader.app.ui.theme.TextSecondary
import com.papertrader.app.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

private val SLOT_SYMBOLS = listOf("\uD83C\uDF52", "\uD83C\uDF4B", "\uD83C\uDF47", "\uD83D\uDD14", "\u2B50", "\uD83D\uDC8E")
private val SLOT_PAYOUT = mapOf(
    SLOT_SYMBOLS[0] to 5, SLOT_SYMBOLS[1] to 8, SLOT_SYMBOLS[2] to 12,
    SLOT_SYMBOLS[3] to 20, SLOT_SYMBOLS[4] to 40, SLOT_SYMBOLS[5] to 100
)

/** Built-in games that use play chips: slot machine, dice, limbo and coin flip. */
@Composable
fun ClassicGameScreen(factory: ViewModelFactory, game: String, onBack: () -> Unit) {
    val vm: CasinoViewModel = viewModel(factory = factory)
    val state by vm.uiState.collectAsState()
    var bet by remember { mutableStateOf(10L) }
    var message by remember { mutableStateOf("") }
    var won by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) { vm.refreshChips() }
    LaunchedEffect(state.chips) { if (bet > state.chips && state.chips > 0L) bet = state.chips }

    val canPlay = state.chips >= bet && bet > 0L
    val title = when (game) {
        "slots" -> "Slot machine"
        "dice" -> "Dice"
        "limbo" -> "Limbo"
        else -> "Coin flip"
    }
    val onSettle: (Long, String) -> Unit = { payout, text ->
        vm.settle(bet, payout)
        message = text
        won = payout > bet
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            Text("%,d chips".format(state.chips), fontWeight = FontWeight.SemiBold)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            when (game) {
                "slots" -> SlotsGame(canPlay, bet, onSettle)
                "dice" -> DiceGame(canPlay, bet, onSettle)
                "limbo" -> LimboGame(canPlay, bet, onSettle)
                else -> CoinFlipGame(canPlay, bet, onSettle)
            }

            Spacer(Modifier.height(20.dp))
            Text(
                message,
                color = when (won) {
                    true -> ProfitGreen
                    false -> LossRed
                    null -> TextSecondary
                },
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )

            Spacer(Modifier.height(24.dp))
            Text("Bet", color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10L, 50L, 100L, 500L).forEach { amount ->
                    ChipPill(amount.toString(), bet == amount, { bet = amount })
                }
                ChipPill("Max", false, { if (state.chips > 0L) bet = state.chips })
            }
            if (state.chips <= 0L) {
                Spacer(Modifier.height(16.dp))
                Text("You are out of chips.", color = LossRed)
                Spacer(Modifier.height(8.dp))
                ChipPill("Refill chips", false, { vm.resetChips() })
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Play chips only, no real money. Results are random and for fun.",
                color = TextSecondary,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PlayButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
        modifier = Modifier.fillMaxWidth().height(54.dp)
    ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
}

private fun slotPayout(reels: List<String>, bet: Long): Long {
    if (reels[0] == reels[1] && reels[1] == reels[2]) return bet * (SLOT_PAYOUT[reels[0]] ?: 0)
    return if (reels.count { it == SLOT_SYMBOLS[0] } >= 2) bet * 2 else 0L
}

@Composable
private fun SlotsGame(canPlay: Boolean, bet: Long, onSettle: (Long, String) -> Unit) {
    var reels by remember { mutableStateOf(listOf(SLOT_SYMBOLS[0], SLOT_SYMBOLS[1], SLOT_SYMBOLS[2])) }
    var spinning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        reels.forEach { symbol ->
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(SurfaceCard),
                contentAlignment = Alignment.Center
            ) { Text(symbol, fontSize = 46.sp) }
        }
    }
    Spacer(Modifier.height(24.dp))
    PlayButton("Spin", canPlay && !spinning) {
        spinning = true
        scope.launch {
            repeat(14) {
                reels = List(3) { SLOT_SYMBOLS.random() }
                delay(70)
            }
            val result = List(3) { SLOT_SYMBOLS.random() }
            reels = result
            val payout = slotPayout(result, bet)
            spinning = false
            onSettle(
                payout,
                if (payout > 0L) "Win! +${payout - bet} chips" else "No win. -$bet chips"
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "3 of a kind pays: " + SLOT_SYMBOLS.joinToString("  ") { "$it x${SLOT_PAYOUT[it]}" } +
            "\nTwo cherries pay x2",
        color = TextSecondary,
        fontSize = 12.sp
    )
}

@Composable
private fun DiceGame(canPlay: Boolean, bet: Long, onSettle: (Long, String) -> Unit) {
    var target by remember { mutableStateOf(50f) }
    var roll by remember { mutableStateOf<Double?>(null) }
    val t = target.roundToInt()
    val multiplier = 98.0 / t
    Text(
        roll?.let { String.format(Locale.US, "%.2f", it) } ?: "--",
        fontSize = 60.sp,
        fontWeight = FontWeight.Bold
    )
    Text(
        "Roll under $t  \u00B7  win chance $t%  \u00B7  x${String.format(Locale.US, "%.2f", multiplier)}",
        color = TextSecondary
    )
    Slider(value = target, onValueChange = { target = it }, valueRange = 2f..95f)
    PlayButton("Roll", canPlay) {
        val r = Random.nextDouble() * 100.0
        roll = r
        val win = r < t
        val payout = if (win) floor(bet * multiplier).toLong() else 0L
        onSettle(payout, if (win) "Win! +${payout - bet} chips" else "Lost. -$bet chips")
    }
}

@Composable
private fun LimboGame(canPlay: Boolean, bet: Long, onSettle: (Long, String) -> Unit) {
    var target by remember { mutableStateOf(2f) }
    var crash by remember { mutableStateOf<Double?>(null) }
    val t = (target * 100).roundToInt() / 100.0
    Text(
        crash?.let { String.format(Locale.US, "%.2fx", it) } ?: "--",
        fontSize = 60.sp,
        fontWeight = FontWeight.Bold
    )
    Text(
        "Target ${String.format(Locale.US, "%.2f", t)}x  \u00B7  win chance ${String.format(Locale.US, "%.1f", 98.0 / t)}%",
        color = TextSecondary
    )
    Slider(value = target, onValueChange = { target = it }, valueRange = 1.1f..20f)
    PlayButton("Play", canPlay) {
        val u = 1.0 - Random.nextDouble()
        val result = min(1000.0, max(1.0, 0.98 / u))
        crash = result
        val win = result >= t
        val payout = if (win) floor(bet * t).toLong() else 0L
        onSettle(payout, if (win) "Win! +${payout - bet} chips" else "Crashed. -$bet chips")
    }
}

@Composable
private fun CoinFlipGame(canPlay: Boolean, bet: Long, onSettle: (Long, String) -> Unit) {
    var pick by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<String?>(null) }
    Text("\uD83E\uDE99", fontSize = 80.sp)
    Text(result ?: "Pick a side", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChipPill("Heads", pick == 0, { pick = 0 })
        ChipPill("Tails", pick == 1, { pick = 1 })
    }
    Spacer(Modifier.height(16.dp))
    Text("Pays x1.96", color = TextSecondary)
    Spacer(Modifier.height(12.dp))
    PlayButton("Flip", canPlay) {
        val heads = Random.nextBoolean()
        result = if (heads) "Heads" else "Tails"
        val win = (pick == 0) == heads
        val payout = if (win) floor(bet * 1.96).toLong() else 0L
        onSettle(payout, if (win) "Win! +${payout - bet} chips" else "Lost. -$bet chips")
    }
}
