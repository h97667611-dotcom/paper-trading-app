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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.papertrader.app.domain.model.BetCategory
import com.papertrader.app.domain.model.DexPair
import com.papertrader.app.ui.components.BetRow
import com.papertrader.app.ui.screens.casino.CasinoTabContent
import com.papertrader.app.ui.components.ChipPill
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

private val DEX_CHAINS = listOf("Solana" to "solana", "Ethereum" to "ethereum", "Base" to "base", "BNB" to "bnb")

@Composable
fun MarketsScreen(
    factory: ViewModelFactory,
    onAssetClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onBetClick: (String) -> Unit,
    onCasinoGame: (String) -> Unit,
    onCasinoSlot: (String) -> Unit
) {
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
            labels = listOf("Crypto", "Stocks", "Bets", "Sports", "Casino"),
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
                MarketsTab.COINS -> CoinsTabContent(state, onAssetClick, viewModel::selectDexQuery)
                MarketsTab.STOCKS -> StocksTabContent(state, onAssetClick)
                MarketsTab.BETS -> BetsTabContent(state, false, viewModel::selectBetCategory, onBetClick)
                MarketsTab.SPORTS -> BetsTabContent(state, true, viewModel::selectBetCategory, onBetClick)
                MarketsTab.CASINO -> CasinoTabContent(factory, onCasinoGame, onCasinoSlot)
            }
        }
    }
}

@Composable
private fun CoinsTabContent(state: MarketsUiState, onAssetClick: (String) -> Unit, onDexQuery: (String) -> Unit) {
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
        item { SectionHeader("On-chain (DexScreener)") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DEX_CHAINS, key = { it.second }) { (label, query) ->
                    ChipPill(label, state.dexQuery == query, { onDexQuery(query) })
                }
            }
        }
        itemsIndexed(state.dexPairs.take(30), key = { index, pair -> "d_${index}_${pair.pairAddress}" }) { _, pair ->
            DexPairRow(pair)
        }
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

/** Polymarket markets. On the Sports tab odds are shown as decimal betting odds. */
@Composable
private fun BetsTabContent(
    state: MarketsUiState,
    sports: Boolean,
    onCategory: (BetCategory) -> Unit,
    onBetClick: (String) -> Unit
) {
    val expected = if (sports) BetCategory.SPORTS else state.betCategory
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (!sports) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(BetCategory.values().filter { it != BetCategory.SPORTS }, key = { it.name }) { category ->
                        ChipPill(category.label, category == state.betCategory, { onCategory(category) })
                    }
                }
            }
        }
        if (state.isBetsLoading && state.betsFor != expected) {
            item { LoadingBox() }
        }
        if (state.betsFor == expected) {
            items(state.bets, key = { "b_${it.id}" }) { market ->
                BetRow(market, decimalOdds = sports) { onBetClick(market.tokenIds.first()) }
            }
            if (state.bets.isEmpty() && !state.isBetsLoading) {
                item { Text(state.betsError ?: "Nothing here right now.", color = TextSecondary, modifier = Modifier.padding(top = 16.dp)) }
            }
        }
        item {
            Text(
                "Powered by Polymarket. Paper trading with simulated money.",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

/** A DexScreener pair inside the crypto tab. Tap to open it on DexScreener. */
@Composable
private fun DexPairRow(pair: DexPair) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { pair.url?.let { runCatching { uriHandler.openUri(it) } } }
            .padding(vertical = 10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${pair.baseTokenSymbol}/${pair.quoteTokenSymbol}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Text("${pair.chain} \u00B7 ${pair.dexName}", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatPriceSmart(pair.priceUsd), style = MaterialTheme.typography.titleMedium)
                Text(
                    formatPercent(pair.priceChange24h),
                    color = if (pair.priceChange24h >= 0) ProfitGreen else LossRed
                )
            }
        }
        Text(
            "Liquidity ${formatUsdCompact(pair.liquidityUsd)} \u00B7 Vol 24h ${formatUsdCompact(pair.volume24h)}",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
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
