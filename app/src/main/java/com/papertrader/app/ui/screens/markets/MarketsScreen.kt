package com.papertrader.app.ui.screens.markets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.papertrader.app.domain.model.DexPair
import com.papertrader.app.ui.components.CoinCard
import com.papertrader.app.ui.components.SectionHeader
import com.papertrader.app.ui.components.formatPercent
import com.papertrader.app.ui.components.formatUsd
import com.papertrader.app.ui.components.formatUsdCompact
import com.papertrader.app.ui.theme.SurfaceCard
import com.papertrader.app.ui.theme.TextSecondary
import com.papertrader.app.ui.viewmodel.ViewModelFactory

@Composable
fun MarketsScreen(factory: ViewModelFactory, onCoinClick: (String) -> Unit) {
    val viewModel: MarketsViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("Markets", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = { Text("Search coins") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = SurfaceCard,
                    focusedContainerColor = SurfaceCard
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )
            Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                MarketsTab.values().forEach { tab ->
                    val selected = tab == state.selectedTab
                    Text(
                        text = if (tab == MarketsTab.COINS) "Crypto" else "DEX Pairs",
                        color = if (selected) MaterialTheme.colorScheme.primary else TextSecondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                            .then(Modifier)
                            .clickableTab { viewModel.selectTab(tab) }
                    )
                }
            }
        }

        if (state.searchQuery.isNotBlank()) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.searchResults) { coin ->
                    CoinCard(coin = coin, onClick = { onCoinClick(coin.id) })
                }
            }
        } else {
            when (state.selectedTab) {
                MarketsTab.COINS -> CoinsTabContent(state, onCoinClick)
                MarketsTab.DEX_PAIRS -> DexPairsTabContent(state.dexPairs)
            }
        }
    }
}

@Composable
private fun CoinsTabContent(state: MarketsUiState, onCoinClick: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeader("Trending")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.trending) { coin ->
                    MiniCoinChip(name = coin.symbol, price = formatUsd(coin.currentPrice), change = formatPercent(coin.priceChangePercent24h), positive = coin.priceChangePercent24h >= 0) {
                        onCoinClick(coin.id)
                    }
                }
            }
        }
        item { SectionHeader("Top Gainers") }
        items(state.topGainers) { coin -> CoinCard(coin = coin, onClick = { onCoinClick(coin.id) }) }
        item { SectionHeader("Top Losers") }
        items(state.topLosers) { coin -> CoinCard(coin = coin, onClick = { onCoinClick(coin.id) }) }
        item { SectionHeader("Popular Coins") }
        items(state.allCoins) { coin -> CoinCard(coin = coin, onClick = { onCoinClick(coin.id) }) }
    }
}

@Composable
private fun DexPairsTabContent(pairs: List<DexPair>) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(pairs) { pair ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(2.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("${pair.baseTokenSymbol}/${pair.quoteTokenSymbol}", style = MaterialTheme.typography.titleMedium)
                        Text("${pair.chain} · ${pair.dexName}", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                        Text(formatUsd(pair.priceUsd), style = MaterialTheme.typography.titleMedium)
                        Text(
                            formatPercent(pair.priceChange24h),
                            color = if (pair.priceChange24h >= 0) com.papertrader.app.ui.theme.ProfitGreen else com.papertrader.app.ui.theme.LossRed
                        )
                    }
                }
                Text(
                    "Liquidity ${formatUsdCompact(pair.liquidityUsd)} · Vol 24h ${formatUsdCompact(pair.volume24h)}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun MiniCoinChip(name: String, price: String, change: String, positive: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(2.dp)
            .clickableTab(onClick)
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        Text(price, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(change, color = if (positive) com.papertrader.app.ui.theme.ProfitGreen else com.papertrader.app.ui.theme.LossRed)
    }
}

private fun Modifier.clickableTab(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(onClick = onClick))
