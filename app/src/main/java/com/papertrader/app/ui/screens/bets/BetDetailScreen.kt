package com.papertrader.app.ui.screens.bets

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.papertrader.app.domain.model.AssetIds
import com.papertrader.app.domain.model.Candle
import com.papertrader.app.domain.model.ChartRange
import com.papertrader.app.ui.components.ChartStyle
import com.papertrader.app.ui.components.InteractiveChart
import com.papertrader.app.ui.components.bounceClick
import com.papertrader.app.ui.components.formatCents
import com.papertrader.app.ui.components.formatDateTime
import com.papertrader.app.ui.components.formatDecimalOdds
import com.papertrader.app.ui.components.formatEndDate
import com.papertrader.app.ui.components.formatQuantity
import com.papertrader.app.ui.components.formatUsd
import com.papertrader.app.ui.components.formatUsdCompact
import com.papertrader.app.ui.components.outcomeColor
import com.papertrader.app.ui.theme.LossRed
import com.papertrader.app.ui.theme.ProfitGreen
import com.papertrader.app.ui.theme.SurfaceCard
import com.papertrader.app.ui.theme.SurfaceCardElevated
import com.papertrader.app.ui.theme.TextSecondary
import com.papertrader.app.ui.viewmodel.ViewModelFactory

private val BET_RANGES = listOf(
    ChartRange.ONE_HOUR, ChartRange.ONE_DAY, ChartRange.ONE_WEEK, ChartRange.ONE_MONTH, ChartRange.ALL
)

/**
 * Prediction market / sports bet: every outcome with its price (a share pays 1 if it wins),
 * price history you can scrub, and your open positions. Paper money only.
 */
@Composable
fun BetDetailScreen(
    factory: ViewModelFactory,
    tokenId: String,
    onBack: () -> Unit,
    onBuy: (String) -> Unit
) {
    val viewModel: BetDetailViewModel = viewModel(factory = factory)
    LaunchedEffect(tokenId) { viewModel.load(tokenId) }
    val state by viewModel.uiState.collectAsState()
    var scrubbed by remember { mutableStateOf<Candle?>(null) }
    var showRules by rememberSaveable { mutableStateOf(false) }

    val market = state.market
    if (market == null) {
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

    val selected = state.selected.coerceIn(0, market.outcomes.lastIndex)
    val resolved = market.closed
    val shown = scrubbed

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(if (market.isSports) "Sports bet" else "Prediction market", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text(market.question, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Ends ${formatEndDate(market.endDateMillis)} \u00B7 Volume ${formatUsdCompact(market.volume)}",
                color = TextSecondary,
                fontSize = 13.sp
            )
            if (resolved) {
                Spacer(Modifier.height(6.dp))
                Text("This market has resolved.", color = ProfitGreen, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(16.dp))
            market.outcomes.forEachIndexed { i, label ->
                OutcomeRow(
                    label = label,
                    price = market.prices[i],
                    selected = i == selected,
                    enabled = !resolved && market.acceptingOrders,
                    showOdds = market.isSports,
                    onSelect = { viewModel.selectOutcome(i) },
                    onBuy = { onBuy(market.tokenIds[i]) }
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(8.dp))
            Text(
                if (shown != null) "${market.outcomes[selected]}: ${formatCents(shown.close)} \u00B7 ${formatDateTime(shown.timeMillis)}"
                else "${market.outcomes[selected]} price history",
                color = TextSecondary,
                fontSize = 13.sp
            )
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                InteractiveChart(
                    candles = state.candles,
                    style = ChartStyle.AREA,
                    height = 240.dp,
                    onScrub = { scrubbed = it }
                )
                if (state.candles.isEmpty()) {
                    if (state.isChartLoading) CircularProgressIndicator()
                    else Text(state.chartError ?: "No history yet.", color = TextSecondary, modifier = Modifier.padding(24.dp))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BET_RANGES.forEach { r ->
                    val isSel = r == state.range
                    Text(
                        r.label,
                        color = if (isSel) Color.White else TextSecondary,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .bounceClick { viewModel.selectRange(r) }
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) SurfaceCardElevated else Color.Transparent)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            state.positions.forEach { pos ->
                Spacer(Modifier.height(16.dp))
                val index = market.tokenIds.indexOfFirst { AssetIds.bet(it) == pos.coinId }
                val price = market.prices.getOrElse(index) { 0.0 }
                val value = pos.quantity * price
                val cost = pos.quantity * pos.avgEntryPrice
                val pnl = value - cost
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceCard)
                        .padding(18.dp)
                ) {
                    Text("Your position: ${pos.symbol}", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    InfoLine("Shares", formatQuantity(pos.quantity))
                    InfoLine("Avg. price", formatCents(pos.avgEntryPrice))
                    InfoLine("Value now", formatUsd(value))
                    InfoLine("P&L", (if (pnl >= 0) "+" else "") + formatUsd(pnl), if (pnl >= 0) ProfitGreen else LossRed)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.closePosition(pos.coinId) },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = LossRed),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Sell all shares") }
                }
            }
            state.message?.let { Text(it, color = ProfitGreen, modifier = Modifier.padding(top = 8.dp)) }
            state.errorMessage?.let { Text(it, color = LossRed, modifier = Modifier.padding(top = 8.dp)) }

            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceCard)
                    .padding(18.dp)
            ) {
                Text("Market info", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                InfoLine("24h volume", formatUsdCompact(market.volume24h))
                InfoLine("Total volume", formatUsdCompact(market.volume))
                InfoLine("Liquidity", formatUsdCompact(market.liquidity))
                InfoLine("Ends", formatEndDate(market.endDateMillis))
                if (market.description.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Rules", color = TextSecondary, fontSize = 13.sp)
                    Text(
                        market.description,
                        fontSize = 13.sp,
                        maxLines = if (showRules) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (showRules) "Show less" else "Show more",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.bounceClick { showRules = !showRules }.padding(top = 6.dp)
                    )
                }
            }
            Text(
                "Paper trading with simulated money. Prices are live Polymarket probabilities.",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun OutcomeRow(
    label: String,
    price: Double,
    selected: Boolean,
    enabled: Boolean,
    showOdds: Boolean,
    onSelect: () -> Unit,
    onBuy: () -> Unit
) {
    val accent = outcomeColor(label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onSelect)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) SurfaceCardElevated else SurfaceCard)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (showOdds) "Odds ${formatDecimalOdds(price)} \u00B7 ${(price * 100).toInt()}% chance"
                else "${(price * 100).toInt()}% chance",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
        Text(formatCents(price), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = accent)
        Spacer(Modifier.padding(start = 12.dp))
        Button(
            onClick = onBuy,
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (accent == Color.White) Color.White else accent,
                contentColor = if (accent == LossRed) Color.White else Color.Black
            )
        ) { Text("Buy", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun InfoLine(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary)
        Text(value, color = valueColor)
    }
}
