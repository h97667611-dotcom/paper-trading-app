package com.papertrader.app.ui.screens.coindetail

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.papertrader.app.ui.components.CoinLogo
import com.papertrader.app.ui.components.PriceLineChart
import com.papertrader.app.ui.components.TradingViewChart
import com.papertrader.app.ui.components.formatPercent
import com.papertrader.app.ui.components.formatUsd
import com.papertrader.app.ui.components.formatUsdCompact
import com.papertrader.app.ui.theme.LossRed
import com.papertrader.app.ui.theme.ProfitGreen
import com.papertrader.app.ui.theme.SurfaceCard
import com.papertrader.app.ui.theme.TextSecondary
import com.papertrader.app.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.delay

@Composable
fun CoinDetailScreen(
    factory: ViewModelFactory,
    coinId: String,
    onBuyClick: (String) -> Unit,
    onSellClick: (String) -> Unit
) {
    val viewModel: CoinDetailViewModel = viewModel(factory = factory)
    LaunchedEffect(coinId) { viewModel.load(coinId) }
    val state by viewModel.uiState.collectAsState()
    var useTradingView by remember { mutableStateOf(true) }
    var chartReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300) // let the screen transition finish before creating the WebView
        chartReady = true
    }

    if (state.isLoading || state.coin == null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator(modifier = Modifier.padding(32.dp))
        }
        return
    }

    val coin = state.coin!!
    val positive = coin.priceChangePercent24h >= 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            CoinLogo(imageUrl = coin.imageUrl, symbol = coin.symbol, sizeDp = 44)
            Spacer(Modifier.padding(start = 8.dp))
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(coin.name, style = MaterialTheme.typography.titleLarge)
                Text(coin.symbol, color = TextSecondary)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(formatUsd(coin.currentPrice), style = MaterialTheme.typography.displaySmall)
        Text(
            formatPercent(coin.priceChangePercent24h),
            color = if (positive) ProfitGreen else LossRed,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChartModeChip("TradingView Live", useTradingView) { useTradingView = true }
            ChartModeChip("Simple", !useTradingView) { useTradingView = false }
        }
        Spacer(Modifier.height(12.dp))

        if (useTradingView) {
            if (chartReady) {
                TradingViewChart(
                    coinSymbol = coin.symbol,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(460.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(460.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) { CircularProgressIndicator() }
            }
        } else {
            PriceLineChart(values = state.chartValues, isPositive = positive)

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CoinChartPeriod.values().forEach { period ->
                    val selected = period == state.selectedPeriod
                    Text(
                        text = period.label,
                        color = if (selected) MaterialTheme.colorScheme.primary else TextSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.clickable { viewModel.selectPeriod(period) }
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCard, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            StatRow("Market Cap", formatUsdCompact(coin.marketCap))
            StatRow("24h Volume", formatUsdCompact(coin.volume24h))
            StatRow("24h High / Low", "${formatUsd(coin.high24h)} / ${formatUsd(coin.low24h)}")
            StatRow("All-Time High", formatUsd(coin.ath))
            StatRow("All-Time Low", formatUsd(coin.atl))
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { onBuyClick(coin.id) },
                colors = ButtonDefaults.buttonColors(containerColor = ProfitGreen),
                modifier = Modifier.weight(1f)
            ) { Text("BUY") }
            OutlinedButton(
                onClick = { onSellClick(coin.id) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LossRed),
                modifier = Modifier.weight(1f)
            ) { Text("SELL") }
        }

        state.errorMessage?.let {
            Text(it, color = LossRed, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary)
        Text(value)
    }
}

@Composable
private fun ChartModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(
                if (selected) MaterialTheme.colorScheme.primary else SurfaceCard,
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
