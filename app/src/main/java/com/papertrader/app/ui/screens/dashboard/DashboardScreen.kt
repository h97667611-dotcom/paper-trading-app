package com.papertrader.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.papertrader.app.domain.model.Candle
import com.papertrader.app.ui.components.ChartStyle
import com.papertrader.app.ui.components.ChipPill
import com.papertrader.app.ui.components.CoinLogo
import com.papertrader.app.ui.components.InteractiveChart
import com.papertrader.app.ui.components.PnlText
import com.papertrader.app.ui.components.SectionHeader
import com.papertrader.app.ui.components.bounceClick
import com.papertrader.app.ui.components.formatDateTime
import com.papertrader.app.ui.components.formatPercent
import com.papertrader.app.ui.components.formatPriceSmart
import com.papertrader.app.ui.components.formatQuantity
import com.papertrader.app.ui.components.formatUsd
import com.papertrader.app.ui.theme.LossRed
import com.papertrader.app.ui.theme.ProfitGreen
import com.papertrader.app.ui.theme.SurfaceCard
import com.papertrader.app.ui.theme.TextSecondary
import com.papertrader.app.ui.viewmodel.ViewModelFactory

/**
 * Home = old dashboard + old portfolio: total balance, chart you can scrub with a
 * finger, cash/invested cards, and all open crypto and stock positions live.
 */
@Composable
fun DashboardScreen(factory: ViewModelFactory, onHoldingClick: (String) -> Unit) {
    val viewModel: DashboardViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsState()
    var scrubbed by remember { mutableStateOf<Candle?>(null) }
    val chartCandles = remember(state.chartPoints) {
        state.chartPoints.map { Candle(it.timestampMillis, it.price, it.price, it.price, it.price) }
    }
    val hasHoldings = state.cryptoHoldings.isNotEmpty() || state.stockHoldings.isNotEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            val shown = scrubbed
            Text("Total balance", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Text(formatUsd(shown?.close ?: state.totalValue), style = MaterialTheme.typography.displaySmall)
            if (shown != null) {
                Text(formatDateTime(shown.timeMillis), color = TextSecondary)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PnlText(state.totalPnl)
                    Text(
                        "(${formatPercent(state.totalPnlPercent)})",
                        color = if (state.totalPnl >= 0) ProfitGreen else LossRed
                    )
                }
            }
        }

        item {
            InteractiveChart(
                candles = chartCandles,
                style = ChartStyle.AREA,
                height = 200.dp,
                showAxes = false,
                onScrub = { scrubbed = it },
                modifier = Modifier.padding(top = 12.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ChartPeriod.values().forEach { period ->
                    ChipPill(period.label, period == state.selectedPeriod, { viewModel.selectPeriod(period) })
                }
            }
        }

        item {
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard("Cash", formatUsd(state.cashBalance), Modifier.weight(1f))
                StatCard("Invested", formatUsd(state.investedValue), Modifier.weight(1f))
            }
        }

        item {
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard("Today", null, Modifier.weight(1f), state.todayPnl, state.todayPnlPercent)
                StatCard("Open P&L", null, Modifier.weight(1f), state.unrealizedPnl, null)
            }
        }

        if (state.cryptoHoldings.isNotEmpty()) {
            item { SectionHeader("Crypto") }
            items(state.cryptoHoldings, key = { "c_${it.coinId}" }) { holding ->
                HoldingRow(holding) { onHoldingClick(holding.coinId) }
            }
        }
        if (state.stockHoldings.isNotEmpty()) {
            item { SectionHeader("Stocks") }
            items(state.stockHoldings, key = { "s_${it.coinId}" }) { holding ->
                HoldingRow(holding) { onHoldingClick(holding.coinId) }
            }
        }
        if (!hasHoldings && !state.isLoading) {
            item {
                Text(
                    "No positions yet. Use the search bar below to find a coin or stock and place your first paper trade.",
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
        }

        state.errorMessage?.let { message ->
            item { Text(message, color = LossRed, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun HoldingRow(holding: HoldingUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick)
            .clip(RoundedCornerShape(20.dp))
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CoinLogo(imageUrl = holding.imageUrl, symbol = holding.symbol)
            Column {
                Text(holding.symbol, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${formatQuantity(holding.quantity)} · ${formatPriceSmart(holding.price)}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatUsd(holding.value), style = MaterialTheme.typography.titleMedium)
            Text(
                "${if (holding.pnl >= 0) "+" else ""}${formatUsd(holding.pnl)} (${formatPercent(holding.pnlPercent)})",
                color = if (holding.pnl >= 0) ProfitGreen else LossRed,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    pnlValue: Double? = null,
    pnlPercent: Double? = null
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceCard)
            .padding(16.dp)
    ) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        if (value != null) {
            Text(value, style = MaterialTheme.typography.titleLarge)
        } else if (pnlValue != null) {
            PnlText(pnlValue)
            if (pnlPercent != null) {
                Text(
                    formatPercent(pnlPercent),
                    color = if (pnlValue >= 0) ProfitGreen else LossRed,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
