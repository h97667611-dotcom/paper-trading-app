package com.papertrader.app.ui.screens.coindetail

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.papertrader.app.domain.model.AssetIds
import com.papertrader.app.domain.model.Candle
import com.papertrader.app.domain.model.ChartRange
import com.papertrader.app.ui.components.ChartStyle
import com.papertrader.app.ui.components.ChipPill
import com.papertrader.app.ui.components.InteractiveChart
import com.papertrader.app.ui.components.PnlText
import com.papertrader.app.ui.components.bounceClick
import com.papertrader.app.ui.components.formatChartPrice
import com.papertrader.app.ui.components.formatDateTime
import com.papertrader.app.ui.components.formatPercent
import com.papertrader.app.ui.components.formatPriceSmart
import com.papertrader.app.ui.components.formatQuantity
import com.papertrader.app.ui.components.formatUsd
import com.papertrader.app.ui.components.formatUsdCompact
import com.papertrader.app.ui.theme.LossRed
import com.papertrader.app.ui.theme.ProfitGreen
import com.papertrader.app.ui.theme.SurfaceCard
import com.papertrader.app.ui.theme.SurfaceCardElevated
import com.papertrader.app.ui.theme.TextSecondary
import com.papertrader.app.ui.viewmodel.ViewModelFactory

/**
 * Trading screen: pair header, big last price with 24h stats, timeframes, candles with volume
 * bars (press and drag to read any moment), your position and stats, and Buy / Sell pinned at the bottom.
 */
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

    var styleName by rememberSaveable { mutableStateOf(ChartStyle.CANDLES.name) }
    var showStyles by rememberSaveable { mutableStateOf(false) }
    var scrubbed by remember { mutableStateOf<Candle?>(null) }
    val style = ChartStyle.valueOf(styleName)

    val coin = state.coin
    if (coin == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.errorMessage != null) {
                    Text(state.errorMessage.orEmpty(), color = LossRed, modifier = Modifier.padding(32.dp))
                } else {
                    CircularProgressIndicator()
                }
            }
        }
        return
    }

    val isStock = AssetIds.isStock(coin.id)
    val positive = coin.priceChangePercent24h >= 0
    val dirColor = if (positive) ProfitGreen else LossRed
    val candles = state.candles
    val shown = scrubbed
    val rangeStart = candles.firstOrNull()
    val pairLabel = if (isStock) coin.symbol else "${coin.symbol}/USDT"

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(pairLabel, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(coin.name, color = TextSecondary, fontSize = 13.sp, maxLines = 1)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        formatPriceSmart(shown?.close ?: coin.currentPrice),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = dirColor
                    )
                    if (shown != null) {
                        Text(formatDateTime(shown.timeMillis), color = TextSecondary, fontSize = 13.sp)
                        if (rangeStart != null && rangeStart.open > 0.0) {
                            val pct = (shown.close - rangeStart.open) / rangeStart.open * 100.0
                            Text(
                                "${formatPercent(pct)} since start of range",
                                color = if (pct >= 0) ProfitGreen else LossRed,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        Text(
                            "${formatPercent(coin.priceChangePercent24h)} today",
                            color = dirColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    SideStat(if (isStock) "Day high" else "24h high", formatChartPrice(coin.high24h))
                    SideStat(if (isStock) "Day low" else "24h low", formatChartPrice(coin.low24h))
                    SideStat(if (isStock) "Volume" else "24h vol", if (isStock) "%,.0f".format(coin.volume24h) else formatUsdCompact(coin.volume24h))
                }
            }

            if (shown != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "O ${formatChartPrice(shown.open)}  H ${formatChartPrice(shown.high)}  " +
                        "L ${formatChartPrice(shown.low)}  C ${formatChartPrice(shown.close)}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ChartRange.values().forEach { r ->
                    val selected = r == state.range
                    Text(
                        text = r.label,
                        color = if (selected) Color.White else TextSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .bounceClick { viewModel.selectRange(r) }
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) SurfaceCardElevated else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .bounceClick { showStyles = !showStyles }
                    .clip(RoundedCornerShape(50))
                    .background(SurfaceCardElevated)
                    .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(style.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Chart type", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            AnimatedVisibility(visible = showStyles) {
                LazyRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ChartStyle.values().toList(), key = { it.name }) { s ->
                        ChipPill(s.label, s == style, { styleName = s.name })
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                InteractiveChart(
                    candles = candles,
                    style = style,
                    height = 340.dp,
                    showVolume = true,
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
            Text(
                "Press and drag on the chart to see the price at any moment",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 6.dp)
            )

            if (candles.size >= 2) {
                Spacer(Modifier.height(20.dp))
                val first = candles.first()
                val last = candles.last()
                val change = if (first.open > 0.0) (last.close - first.open) / first.open * 100.0 else 0.0
                val high = candles.maxByOrNull { it.high }
                val low = candles.minByOrNull { it.low }
                InfoCard {
                    Text("Performance (${state.range.label})", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    StatRow("Change", formatPercent(change), if (change >= 0) ProfitGreen else LossRed)
                    if (high != null) StatRow("Highest", "${formatPriceSmart(high.high)}  ·  ${formatDateTime(high.timeMillis)}")
                    if (low != null) StatRow("Lowest", "${formatPriceSmart(low.low)}  ·  ${formatDateTime(low.timeMillis)}")
                }
            }

            state.position?.let { pos ->
                Spacer(Modifier.height(16.dp))
                val value = pos.quantity * coin.currentPrice
                val cost = pos.quantity * pos.avgEntryPrice
                val pnl = value - cost
                val pnlPct = if (cost > 0.0) pnl / cost * 100.0 else 0.0
                InfoCard {
                    Text("Your position", fontWeight = FontWeight.SemiBold)
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

            Spacer(Modifier.height(16.dp))
            InfoCard {
                Text("Stats", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                if (!isStock) StatRow("Market cap", formatUsdCompact(coin.marketCap))
                StatRow(if (isStock) "52-week high" else "All-time high", formatPriceSmart(coin.ath))
                StatRow(if (isStock) "52-week low" else "All-time low", formatPriceSmart(coin.atl))
            }

            state.errorMessage?.let {
                Text(it, color = LossRed, modifier = Modifier.padding(top = 12.dp))
            }
            Spacer(Modifier.height(24.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onBuyClick(coin.id) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ProfitGreen, contentColor = Color.Black),
                modifier = Modifier.weight(1f).height(50.dp)
            ) { Text("Buy", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            Button(
                onClick = { onSellClick(coin.id) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LossRed, contentColor = Color.White),
                modifier = Modifier.weight(1f).height(50.dp)
            ) { Text("Sell", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

@Composable
private fun SideStat(label: String, value: String) {
    Row(modifier = Modifier.padding(bottom = 3.dp)) {
        Text(label, color = TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.size(8.dp))
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard)
            .padding(18.dp)
    ) { content() }
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
