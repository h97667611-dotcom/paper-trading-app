package com.papertrader.app.ui.screens.coindetail

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.papertrader.app.domain.model.AssetIds
import com.papertrader.app.domain.model.Candle
import com.papertrader.app.domain.model.ChartRange
import com.papertrader.app.ui.components.ChartStyle
import com.papertrader.app.ui.components.ChipPill
import com.papertrader.app.ui.components.CoinLogo
import com.papertrader.app.ui.components.InteractiveChart
import com.papertrader.app.ui.components.PnlText
import com.papertrader.app.ui.components.TradingViewChart
import com.papertrader.app.ui.components.bounceClick
import com.papertrader.app.ui.components.formatChartPrice
import com.papertrader.app.ui.components.formatDateTime
import com.papertrader.app.ui.components.formatPercent
import com.papertrader.app.ui.components.formatPriceSmart
import com.papertrader.app.ui.components.formatQuantity
import com.papertrader.app.ui.components.formatUsd
import com.papertrader.app.ui.components.formatUsdCompact
import com.papertrader.app.ui.components.tradingViewSymbol
import com.papertrader.app.ui.theme.LossRed
import com.papertrader.app.ui.theme.ProfitGreen
import com.papertrader.app.ui.theme.SurfaceCard
import com.papertrader.app.ui.theme.TextSecondary
import com.papertrader.app.ui.viewmodel.ViewModelFactory

@Composable
fun CoinDetailScreen(
    factory: ViewModelFactory,
    coinId: String,
    onBack: () -> Unit,
    onBuyClick: (String) -> Unit,
    onSellClick: (String) -> Unit
) {
    val viewModel: CoinDetailViewModel = viewModel(factory = factory)
    LaunchedEffect(coinId) { viewModel.load(coinId) }
    val state by viewModel.uiState.collectAsState()

    var useTradingView by rememberSaveable { mutableStateOf(false) }
    var styleName by rememberSaveable { mutableStateOf(ChartStyle.LINE.name) }
    val style = ChartStyle.valueOf(styleName)
    var scrubbed by remember { mutableStateOf<Candle?>(null) }

    val coin = state.coin
    if (coin == null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.errorMessage != null) {
                Text(state.errorMessage.orEmpty(), color = LossRed, modifier = Modifier.padding(32.dp))
            } else {
                CircularProgressIndicator()
            }
        }
        return
    }

    val isStock = AssetIds.isStock(coin.id)
    val positive = coin.priceChangePercent24h >= 0
    val candles = state.candles
    val shown = scrubbed
    val rangeStart = candles.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CoinLogo(imageUrl = coin.imageUrl, symbol = coin.symbol, sizeDp = 44)
            Column {
                Text(coin.name, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Text(coin.symbol, color = TextSecondary)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(formatPriceSmart(shown?.close ?: coin.currentPrice), style = MaterialTheme.typography.displaySmall)
        if (shown != null) {
            Text(formatDateTime(shown.timeMillis), color = TextSecondary)
            if (rangeStart != null && rangeStart.open > 0.0) {
                val pct = (shown.close - rangeStart.open) / rangeStart.open * 100.0
                Text(
                    "${formatPercent(pct)} since start of range",
                    color = if (pct >= 0) ProfitGreen else LossRed,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                "O ${formatChartPrice(shown.open)}  H ${formatChartPrice(shown.high)}  " +
                    "L ${formatChartPrice(shown.low)}  C ${formatChartPrice(shown.close)}",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text(
                "${formatPercent(coin.priceChangePercent24h)} today",
                color = if (positive) ProfitGreen else LossRed,
                fontWeight = FontWeight.SemiBold
            )
            Text("Press and drag on the chart to see the price at any moment", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipPill("Ghost chart", !useTradingView, { useTradingView = false })
            ChipPill("TradingView", useTradingView, { useTradingView = true })
        }
        Spacer(Modifier.height(12.dp))

        if (!useTradingView) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ChartStyle.values().toList(), key = { it.name }) { s ->
                    ChipPill(s.label, s == style, { styleName = s.name })
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                InteractiveChart(
                    candles = candles,
                    style = style,
                    height = 300.dp,
                    onScrub = { scrubbed = it }
                )
                if (candles.isEmpty()) {
                    if (state.isChartLoading) {
                        CircularProgressIndicator()
                    } else {
                        Text(
                            state.chartError ?: "No chart data available.",
                            color = TextSecondary,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ChartRange.values().forEach { r ->
                    val selected = r == state.range
                    Text(
                        text = r.label,
                        color = if (selected) Color.Black else TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .bounceClick { viewModel.selectRange(r) }
                            .background(if (selected) Color.White else Color.Transparent, RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            if (candles.size >= 2) {
                Spacer(Modifier.height(20.dp))
                val first = candles.first()
                val last = candles.last()
                val change = if (first.open > 0.0) (last.close - first.open) / first.open * 100.0 else 0.0
                val high = candles.maxByOrNull { it.high }
                val low = candles.minByOrNull { it.low }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceCard, RoundedCornerShape(24.dp))
                        .padding(18.dp)
                ) {
                    Text("Performance (${state.range.label})", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    StatRow("Change", formatPercent(change), if (change >= 0) ProfitGreen else LossRed)
                    if (high != null) StatRow("Highest", "${formatPriceSmart(high.high)}  ·  ${formatDateTime(high.timeMillis)}")
                    if (low != null) StatRow("Lowest", "${formatPriceSmart(low.low)}  ·  ${formatDateTime(low.timeMillis)}")
                }
            }
        } else {
            TradingViewChart(
                tvSymbol = tradingViewSymbol(coin),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
            )
        }

        state.position?.let { pos ->
            Spacer(Modifier.height(20.dp))
            val value = pos.quantity * coin.currentPrice
            val cost = pos.quantity * pos.avgEntryPrice
            val pnl = value - cost
            val pnlPct = if (cost > 0.0) pnl / cost * 100.0 else 0.0
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceCard, RoundedCornerShape(24.dp))
                    .padding(18.dp)
            ) {
                Text("Your position", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                StatRow("Quantity", "${formatQuantity(pos.quantity)} ${coin.symbol}")
                StatRow("Value", formatUsd(value))
                StatRow("Avg. entry", formatPriceSmart(pos.avgEntryPrice))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("P&L", color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PnlText(pnl)
                        Text(formatPercent(pnlPct), color = if (pnl >= 0) ProfitGreen else LossRed)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.closePosition() },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LossRed),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Close position") }
            }
        }
        state.closeMessage?.let {
            Text(it, color = ProfitGreen, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCard, RoundedCornerShape(24.dp))
                .padding(18.dp)
        ) {
            Text("Stats", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            if (!isStock) StatRow("Market cap", formatUsdCompact(coin.marketCap))
            StatRow(if (isStock) "Volume" else "24h volume", if (isStock) "%,.0f".format(coin.volume24h) else formatUsdCompact(coin.volume24h))
            StatRow(if (isStock) "Day high / low" else "24h high / low", "${formatPriceSmart(coin.high24h)} / ${formatPriceSmart(coin.low24h)}")
            StatRow(if (isStock) "52-week high" else "All-time high", formatPriceSmart(coin.ath))
            StatRow(if (isStock) "52-week low" else "All-time low", formatPriceSmart(coin.atl))
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { onBuyClick(coin.id) },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ProfitGreen, contentColor = Color.Black),
                modifier = Modifier.weight(1f).height(52.dp)
            ) { Text("BUY", fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = { onSellClick(coin.id) },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LossRed),
                modifier = Modifier.weight(1f).height(52.dp)
            ) { Text("SELL", fontWeight = FontWeight.Bold) }
        }

        state.errorMessage?.let {
            Text(it, color = LossRed, modifier = Modifier.padding(top = 12.dp))
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary)
        Text(value, color = valueColor, modifier = Modifier.padding(start = 16.dp))
    }
}
