package com.papertrader.app.ui.screens.casino

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.papertrader.app.data.casino.DemoSlot
import com.papertrader.app.data.casino.DemoSlots
import com.papertrader.app.ui.components.ChipPill
import com.papertrader.app.ui.components.SectionHeader
import com.papertrader.app.ui.components.bounceClick
import com.papertrader.app.ui.theme.SurfaceCard
import com.papertrader.app.ui.theme.SurfaceCardElevated
import com.papertrader.app.ui.theme.TextSecondary
import com.papertrader.app.ui.viewmodel.ViewModelFactory

private val CLASSIC_GAMES = listOf(
    Triple("slots", "Slot machine", "\uD83C\uDFB0"),
    Triple("dice", "Dice", "\uD83C\uDFB2"),
    Triple("limbo", "Limbo", "\uD83D\uDE80"),
    Triple("coinflip", "Coin flip", "\uD83E\uDE99")
)

/** The "Casino" tab: classic chip games plus 20 fictional demo slots. Play chips only. */
@Composable
fun CasinoTabContent(factory: ViewModelFactory, onGame: (String) -> Unit, onSlot: (String) -> Unit) {
    val vm: CasinoViewModel = viewModel(factory = factory)
    LaunchedEffect(Unit) { vm.refreshChips() }
    val state by vm.uiState.collectAsState()
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val categories = remember { DemoSlots.all.map { it.category }.distinct().sorted() }
    val slots = remember(category) { DemoSlots.all.filter { category == null || it.category == category } }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceCard)
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Play chips", color = TextSecondary, fontSize = 13.sp)
                    Text("%,d".format(state.chips), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Not real money. Separate from your trading account.", color = TextSecondary, fontSize = 11.sp)
                }
                ChipPill("Reset", false, { vm.resetChips() })
            }
        }

        item { SectionHeader("Classic games") }
        items(CLASSIC_GAMES.chunked(2), key = { it.first().first }) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEach { (key, title, emoji) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .bounceClick { onGame(key) }
                            .clip(RoundedCornerShape(20.dp))
                            .background(SurfaceCardElevated)
                            .padding(vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(emoji, fontSize = 36.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(title, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        item { SectionHeader("Demo slots") }
        item {
            Text(
                "${DemoSlots.all.size} fictional slots with different themes and mechanics. Demo only, no real money.",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { ChipPill("All", category == null, { category = null }) }
                items(categories, key = { "c_$it" }) { name ->
                    ChipPill(name, category == name, { category = name })
                }
            }
        }
        items(slots.chunked(2), key = { "s_${it.first().id}" }) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEach { slot ->
                    SlotTile(slot, Modifier.weight(1f)) { onSlot(slot.id) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        item {
            Text(
                "Demo games only. 18+. Never gamble with money you cannot afford to lose.",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun SlotTile(slot: DemoSlot, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .bounceClick(onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceCard)
            .padding(14.dp)
    ) {
        Text(slot.icon, fontSize = 34.sp)
        Spacer(Modifier.height(6.dp))
        Text(slot.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${slot.category} \u00B7 ${slot.reels}x${slot.rows} ${slot.mechanic}",
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
