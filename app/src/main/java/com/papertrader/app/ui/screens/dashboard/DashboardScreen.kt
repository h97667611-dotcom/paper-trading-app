package com.papertrader.app.ui.screens.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.papertrader.app.R
import com.papertrader.app.domain.model.Candle
import com.papertrader.app.ui.components.ChartStyle
import com.papertrader.app.ui.components.ChipPill
import com.papertrader.app.ui.components.CircleAction
import com.papertrader.app.ui.components.CoinLogo
import com.papertrader.app.ui.components.InteractiveChart
import com.papertrader.app.ui.components.PnlText
import com.papertrader.app.ui.components.TextTabs
import com.papertrader.app.ui.components.bounceClick
import com.papertrader.app.ui.components.formatDateTime
import com.papertrader.app.ui.components.formatPercent
import com.papertrader.app.ui.components.formatPriceSmart
import com.papertrader.app.ui.components.formatQuantity
import com.papertrader.app.ui.components.formatUsd
import com.papertrader.app.ui.theme.LossRed
import com.papertrader.app.ui.theme.ProfitGreen
import com.papertrader.app.ui.theme.SurfaceCard
import com.papertrader.app.ui.theme.SurfaceCardElevated
import com.papertrader.app.ui.theme.TextSecondary
import com.papertrader.app.ui.viewmodel.ViewModelFactory

/**
 * Home as a wallet screen: account chip, big balance (scrub the chart to see any moment),
 * round quick actions, a "what's new" card and Crypto / Stocks tabs with your assets.
 */
@Composable
fun DashboardScreen(
    factory: ViewModelFactory,
    onHoldingClick: (String) -> Unit,
    onAddFunds: () -> Unit,
    onTrade: () -> Unit,
    onHistory: () -> Unit,
    onMore: () -> Unit,
    onSearch: () -> Unit,
    onBrowseMarkets: () -> Unit
) {
    val viewModel: DashboardViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsState()
    var scrubbed by remember { mutableStateOf<Candle?>(null) }
    var tab by rememberSaveable { mutableStateOf(0) }
    val chartCandles = remember(state.chartPoints) {
        state.chartPoints.map { Candle(it.timestampMillis, it.price, it.price, it.price, it.price) }
    }
    val holdings = if (tab == 0) state.cryptoHoldings else state.stockHoldings

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_ghost),
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .graphicsLayer {
                                scaleX = 1.6f
                                scaleY = 1.6f
                            }
                    )
                }
                Spacer(Modifier.width(12.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(SurfaceCardElevated)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(ProfitGreen)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Paper account", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = Color.White)
                }
            }
        }

        item {
            val shown = scrubbed
            Column(modifier = Modifier.padding(top = 22.dp)) {
                Text(
                    formatUsd(shown?.close ?: state.totalValue),
                    style = MaterialTheme.typography.displaySmall
                )
                Spacer(Modifier.height(4.dp))
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
        }

        item {
            InteractiveChart(
                candles = chartCandles,
                style = ChartStyle.AREA,
                height = 150.dp,
                showAxes = false,
                onScrub = { scrubbed = it },
                modifier = Modifier.padding(top = 12.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ChartPeriod.values().forEach { period ->
                    ChipPill(period.label, period == state.selectedPeriod, { viewModel.selectPeriod(period) })
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CircleAction(Icons.Filled.Add, "Deposit", onAddFunds)
                CircleAction(Icons.Filled.SwapHoriz, "Trade", onTrade)
                CircleAction(Icons.Filled.History, "History", onHistory)
                CircleAction(Icons.Filled.MoreHoriz, "More", onMore)
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceCard)
                    .padding(18.dp)
            ) {
                Text("What's new", color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Paper-trade live crypto and stocks with real prices and zero risk.",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Try now  \u2192",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.bounceClick(onBrowseMarkets)
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceCard)
                    .padding(vertical = 14.dp, horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MiniStat("Invested", formatUsd(state.investedValue), Color.White)
                MiniStat("Today", formatUsd(state.todayPnl), if (state.todayPnl >= 0) ProfitGreen else LossRed)
                MiniStat("Open P&L", formatUsd(state.unrealizedPnl), if (state.unrealizedPnl >= 0) ProfitGreen else LossRed)
            }
        }

        item {
            TextTabs(
                labels = listOf("Crypto", "Stocks"),
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(top = 30.dp, bottom = 6.dp)
            )
        }

        if (tab == 0) {
            item {
                AssetRow(
                    imageUrl = null,
                    symbol = "USDT",
                    price = 1.0,
                    changePct = 0.0,
                    quantity = formatQuantity(state.cashBalance),
                    value = formatUsd(state.cashBalance),
                    onClick = null
                )
            }
        }
        items(holdings, key = { "h_${it.coinId}" }) { h ->
            AssetRow(
                imageUrl = h.imageUrl,
                symbol = h.symbol,
                price = h.price,
                changePct = h.dayChangePercent,
                quantity = formatQuantity(h.quantity),
                value = formatUsd(h.value),
                onClick = { onHoldingClick(h.coinId) }
            )
        }
        if (holdings.isEmpty() && !state.isLoading) {
            item {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        if (tab == 0) "No crypto positions yet." else "No stock positions yet.",
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    ChipPill("Browse markets", false, onBrowseMarkets)
                }
            }
        }

        state.errorMessage?.let { message ->
            item { Text(message, color = LossRed, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, valueColor: Color) {
    Column {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = valueColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

/** One asset row: logo, symbol with price and 24h change on the left, amount and value on the right. */
@Composable
private fun AssetRow(
    imageUrl: String?,
    symbol: String,
    price: Double,
    changePct: Double?,
    quantity: String,
    value: String,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.bounceClick(onClick) else Modifier)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CoinLogo(imageUrl = imageUrl, symbol = symbol)
            Column {
                Text(symbol, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row {
                    Text(formatPriceSmart(price), color = TextSecondary, fontSize = 13.sp)
                    if (changePct != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            formatPercent(changePct),
                            color = if (changePct >= 0) ProfitGreen else LossRed,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(quantity, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(value, color = TextSecondary, fontSize = 13.sp)
        }
    }
}
