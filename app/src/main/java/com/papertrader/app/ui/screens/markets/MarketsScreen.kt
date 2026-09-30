package com.papertrader.app.ui.screens.markets

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.papertrader.app.domain.model.DexPair
import com.papertrader.app.ui.components.CoinCard
import com.papertrader.app.ui.components.SectionHeader
import com.papertrader.app.ui.components.TextTabs
import com.papertrader.app.ui.components.bounceClick
import com.papertrader.app.ui.components.formatPercent
import com.papertrader.app.ui.components.formatPriceSmart
import com.papertrader.app.ui.components.formatUsd
import com.papertrader.app.ui.components.formatUsdCompact
import com.papertrader.app.ui.theme.LossRed
import com.papertrader.app.ui.theme.ProfitGreen
import com.papertrader.app.ui.theme.TextSecondary
import com.papertrader.app.ui.viewmodel.ViewModelFactory

@Composable
fun MarketsScreen(factory: ViewModelFactory, onAssetClick: (String) -> Unit, onSearchClick: () -> Unit) {
    val viewModel: MarketsViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Markets", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Filled.Search, contentDescription = "Search", tint = Color.White)
            }
        }
        TextTabs(
            labels = listOf("Crypto", "Stocks", "DEX"),
            selected = state.selectedTab.ordinal,
            onSelect = { viewModel.selectTab(MarketsTab.values()[it]) },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        Crossfade(
            targetState = state.selectedTab,
            animationSpec = tween(200),
            label = "marketsTab"
        ) { tab ->
            when (tab) {
                MarketsTab.COINS -> CoinsTabContent(state, onAssetClick)
                MarketsTab.STOCKS -> StocksTabContent(state, onAssetClick)
                MarketsTab.DEX_PAIRS -> DexPairsTabContent(state.dexPairs)
            }
        }
    }
}

@Composable
private fun CoinsTabContent(state: MarketsUiState, onAssetClick: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (state.isLoading && state.allCoins.isEmpty()) {
            item { LoadingBox() }
        }
        state.errorMessage?.let { message ->
            if (state.allCoins.isEmpty()) item { Text(message, color = LossRed) }
        }
        item {
            SectionHeader("Trending")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                items(state.trending, key = { it.id }) { coin ->
                    MiniCoinChip(
                        name = coin.symbol,
                        price = formatPriceSmart(coin.currentPrice),
                        change = formatPercent(coin.priceChangePercent24h),
                        positive = coin.priceChangePercent24h >= 0
                    ) { onAssetClick(coin.id) }
                }
            }
        }
        item { SectionHeader("Top Gainers") }
        items(state.topGainers, key = { "g_${it.id}" }) { coin -> CoinCard(coin = coin, onClick = { onAssetClick(coin.id) }) }
        item { SectionHeader("Top Losers") }
        items(state.topLosers, key = { "l_${it.id}" }) { coin -> CoinCard(coin = coin, onClick = { onAssetClick(coin.id) }) }
        item { SectionHeader("Popular Coins") }
        items(state.allCoins, key = { "a_${it.id}" }) { coin -> CoinCard(coin = coin, onClick = { onAssetClick(coin.id) }) }
    }
}

@Composable
private fun StocksTabContent(state: MarketsUiState, onAssetClick: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (state.isStocksLoading && state.stocks.isEmpty()) {
            item { LoadingBox() }
        }
        state.stocksError?.let { message ->
            if (state.stocks.isEmpty()) {
                item { Text(message, color = LossRed, modifier = Modifier.padding(top = 12.dp)) }
            }
        }
        if (state.stocks.isNotEmpty()) {
            item { SectionHeader("Popular stocks") }
            items(state.stocks, key = { it.id }) { coin ->
                CoinCard(coin = coin, onClick = { onAssetClick(coin.id) })
            }
        }
    }
}

@Composable
private fun DexPairsTabContent(pairs: List<DexPair>) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(pairs, key = { it.pairAddress }) { pair ->
            Column(modifier = Modifier.fillMaxWidth().padding(2.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("${pair.baseTokenSymbol}/${pair.quoteTokenSymbol}", style = MaterialTheme.typography.titleMedium)
                        Text("${pair.chain} · ${pair.dexName}", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatUsd(pair.priceUsd), style = MaterialTheme.typography.titleMedium)
                        Text(
                            formatPercent(pair.priceChange24h),
                            color = if (pair.priceChange24h >= 0) ProfitGreen else LossRed
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
private fun LoadingBox() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) { CircularProgressIndicator() }
}

@Composable
private fun MiniCoinChip(name: String, price: String, change: String, positive: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.bounceClick(onClick).padding(vertical = 4.dp)) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        Text(price, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(change, color = if (positive) ProfitGreen else LossRed, style = MaterialTheme.typography.bodyMedium)
    }
}
