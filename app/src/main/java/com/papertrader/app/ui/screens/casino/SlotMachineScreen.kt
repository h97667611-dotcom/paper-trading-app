package com.papertrader.app.ui.screens.casino

import androidx.compose.foundation.background
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
import com.papertrader.app.data.casino.DemoSlots
import com.papertrader.app.data.casino.SlotEngine
import com.papertrader.app.data.casino.SlotWin
import com.papertrader.app.ui.components.ChipPill
import com.papertrader.app.ui.theme.LossRed
import com.papertrader.app.ui.theme.ProfitGreen
import com.papertrader.app.ui.theme.SurfaceCard
import com.papertrader.app.ui.theme.TextSecondary
import com.papertrader.app.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.floor

/** One demo slot: spin with play chips, reels animate, wins are multipliers of the bet. */
@Composable
fun SlotMachineScreen(factory: ViewModelFactory, slotId: String, onBack: () -> Unit) {
    val slot = DemoSlots.byId(slotId)
    val vm: CasinoViewModel = viewModel(factory = factory)
    val state by vm.uiState.collectAsState()
    var bet by remember { mutableStateOf(10L) }
    var grid by remember(slotId) { mutableStateOf(slot?.let { SlotEngine.randomGrid(it) } ?: emptyList()) }
    var spinning by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var won by remember { mutableStateOf<Boolean?>(null) }
    var wins by remember { mutableStateOf<List<SlotWin>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.refreshChips() }
    LaunchedEffect(state.chips) { if (bet > state.chips && state.chips > 0L) bet = state.chips }

    if (slot == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Slot not found.", color = TextSecondary, modifier = Modifier.padding(20.dp))
        }
        return
    }

    val canPlay = !spinning && state.chips >= bet && bet > 0L

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(slot.name, fontWeight = FontWeight.Bold, fontSize = 20.sp)
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
            Text(
                "DEMO \u00B7 ${slot.provider} \u00B7 ${slot.category}",
                color = TextSecondary,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(slot.description, color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val gap = 6.dp
                val cell = ((maxWidth - gap * (slot.reels - 1)) / slot.reels).coerceAtMost(64.dp)
                Row(modifier = Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    grid.forEach { reel ->
                        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                            reel.forEach { symbol ->
                                Box(
                                    modifier = Modifier
                                        .size(cell)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(SurfaceCard),
                                    contentAlignment = Alignment.Center
                                ) { Text(symbol, fontSize = (cell.value * 0.55f).sp) }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    scope.launch {
                        spinning = true
                        message = ""
                        won = null
                        wins = emptyList()
                        repeat(12) {
                            grid = SlotEngine.randomGrid(slot)
                            delay(70)
                        }
                        val result = SlotEngine.spin(slot)
                        grid = result.reels
                        val payout = floor(bet * result.totalMultiplier).toLong()
                        vm.settle(bet, payout)
                        wins = result.wins
                        won = payout > bet
                        message = when {
                            payout > bet -> "Win! +${payout - bet} chips (x${result.totalMultiplier})"
                            payout > 0L -> "Small win: $payout chips back"
                            else -> "No win. -$bet chips"
                        }
                        result.bonus?.let { message += "\nBonus flag: ${it.spins} free spins (demo only, not played)" }
                        spinning = false
                    }
                },
                enabled = canPlay,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text(if (spinning) "Spinning..." else "Spin", fontWeight = FontWeight.Bold, fontSize = 17.sp) }

            Spacer(Modifier.height(16.dp))
            Text(
                message,
                color = when (won) {
                    true -> ProfitGreen
                    false -> LossRed
                    null -> TextSecondary
                },
                fontWeight = FontWeight.SemiBold
            )
            wins.take(4).forEach { w ->
                Text("${w.symbol} x${w.count} (${w.type}): x${w.multiplier}", color = TextSecondary, fontSize = 12.sp)
            }

            Spacer(Modifier.height(20.dp))
            Text("Bet", color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10L, 50L, 100L, 500L).forEach { amount ->
                    ChipPill(amount.toString(), bet == amount, { if (!spinning) bet = amount })
                }
                ChipPill("Max", false, { if (!spinning && state.chips > 0L) bet = state.chips })
            }
            if (state.chips <= 0L) {
                Spacer(Modifier.height(12.dp))
                Text("You are out of chips.", color = LossRed)
                Spacer(Modifier.height(8.dp))
                ChipPill("Get 1,000 new chips", false, { vm.resetChips() })
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Play chips only, no real money. Wild: ${slot.wild ?: "-"}  Scatter: ${slot.scatter ?: "-"}. " +
                    "Symbols from low to high: ${slot.symbols.joinToString(" ")}",
                color = TextSecondary,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
