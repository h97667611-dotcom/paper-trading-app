package com.papertrader.app.ui.screens.casino

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.papertrader.app.data.casino.CasinoGame
import com.papertrader.app.data.casino.CasinoStore
import com.papertrader.app.ui.components.ChipPill
import com.papertrader.app.ui.components.SectionHeader
import com.papertrader.app.ui.components.bounceClick
import com.papertrader.app.ui.theme.LossRed
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

/** The "Casino" tab: built-in play-chip games plus the Script.Casino demo game catalog. */
@Composable
fun CasinoTabContent(factory: ViewModelFactory, onGame: (String) -> Unit, onPlaySlot: () -> Unit) {
    val vm: CasinoViewModel = viewModel(factory = factory)
    LaunchedEffect(Unit) { vm.refreshChips() }
    val state by vm.uiState.collectAsState()
    var urlInput by rememberSaveable { mutableStateOf(CasinoStore.DEFAULT_BASE_URL) }
    var idInput by rememberSaveable { mutableStateOf("") }
    var secretInput by rememberSaveable { mutableStateOf("") }

    val filtered = remember(state.games, state.category, state.search) {
        state.games.filter { game ->
            (state.category == null || game.category == state.category) &&
                (state.search.isBlank() ||
                    game.name.contains(state.search, ignoreCase = true) ||
                    game.provider.contains(state.search, ignoreCase = true))
        }
    }
    val categories = remember(state.games) {
        state.games.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    }

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

        item { SectionHeader("Demo slots and games (Script.Casino)") }

        if (!state.connected) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceCard)
                        .padding(18.dp)
                ) {
                    Text("Connect your merchant account", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "This is a B2B games API: you need a merchant ID and secret key from the provider. " +
                            "Only the catalog and demo mode are used, no real money. " +
                            "Both are stored only on this phone: never share your secret.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("API base URL") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = idInput,
                        onValueChange = { idInput = it.trim() },
                        label = { Text("Merchant ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = secretInput,
                        onValueChange = { secretInput = it.trim() },
                        label = { Text("Secret key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            vm.saveCredentials(urlInput, idInput, secretInput)
                            secretInput = ""
                        },
                        enabled = urlInput.isNotBlank() && idInput.isNotBlank() && secretInput.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("Connect", fontWeight = FontWeight.Bold) }
                }
            }
        } else {
            item {
                OutlinedTextField(
                    value = state.search,
                    onValueChange = { vm.setSearch(it) },
                    label = { Text("Search games or providers") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (categories.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { ChipPill("All", state.category == null, { vm.selectCategory(null) }) }
                        items(categories, key = { "c_$it" }) { category ->
                            ChipPill(category, state.category == category, { vm.selectCategory(category) })
                        }
                    }
                }
            }
            if (state.games.isNotEmpty()) {
                item { Text("${filtered.size} games loaded", color = TextSecondary, fontSize = 12.sp) }
            }
            items(filtered.take(state.visibleCount).chunked(2), key = { "g_${it.first().uuid}" }) { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    pair.forEach { game ->
                        GameTile(game, Modifier.weight(1f)) {
                            vm.openGame(game)
                            onPlaySlot()
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (state.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            state.error?.let { message ->
                item { Text(message, color = LossRed, fontSize = 13.sp) }
            }
            if (!state.isLoading) {
                if (filtered.size > state.visibleCount) {
                    item { ChipPill("Show more", false, { vm.showMore() }) }
                } else if (state.hasMore) {
                    item { ChipPill("Load more games", false, { vm.loadMore() }) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipPill("Reload", false, { vm.reload() })
                    ChipPill("Disconnect", false, { vm.clearCredentials() })
                }
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
private fun GameTile(game: CasinoGame, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .bounceClick(onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceCard)
    ) {
        AsyncImage(
            model = game.thumb,
            contentDescription = game.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(SurfaceCardElevated)
        )
        Column(modifier = Modifier.padding(10.dp)) {
            Text(game.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(game.provider, game.category).filter { it.isNotBlank() }.joinToString(" \u00B7 "),
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
