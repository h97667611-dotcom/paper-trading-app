package com.papertrader.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.papertrader.app.ui.components.PnlText
import com.papertrader.app.ui.components.PriceLineChart
import com.papertrader.app.ui.components.formatUsd
import com.papertrader.app.ui.theme.SurfaceCard
import com.papertrader.app.ui.theme.TextSecondary
import com.papertrader.app.ui.viewmodel.ViewModelFactory

@Composable
fun DashboardScreen(factory: ViewModelFactory) {
    val viewModel: DashboardViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 24.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text("Total Portfolio Value", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Text(
                formatUsd(state.totalValue),
                style = MaterialTheme.typography.displaySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PnlText(state.totalPnl)
                Text(
                    "(${"%+.2f".format(state.totalPnlPercent)}%)",
                    color = if (state.totalPnl >= 0) com.papertrader.app.ui.theme.ProfitGreen else com.papertrader.app.ui.theme.LossRed
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceCard)
                    .padding(16.dp)
            ) {
                PriceLineChart(values = state.chartValues, isPositive = state.totalPnl >= 0)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ChartPeriod.values().forEach { period ->
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
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Available Cash", formatUsd(state.cashBalance), Modifier.weight(1f))
                StatCard("Invested", formatUsd(state.investedValue), Modifier.weight(1f))
            }
        }

        item {
            StatCard("Today's P&L", null, Modifier.fillMaxWidth(), pnlValue = state.todayPnl, pnlPercent = state.todayPnlPercent)
        }

        if (state.isLoading) {
            item { CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp)) }
        }

        state.errorMessage?.let { message ->
            item { Text(message, color = com.papertrader.app.ui.theme.LossRed) }
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
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .padding(14.dp)
    ) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        if (value != null) {
            Text(value, style = MaterialTheme.typography.titleLarge)
        } else if (pnlValue != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PnlText(pnlValue)
                pnlPercent?.let {
                    Text(
                        "(${"%+.2f".format(it)}%)",
                        color = if (pnlValue >= 0) com.papertrader.app.ui.theme.ProfitGreen else com.papertrader.app.ui.theme.LossRed
                    )
                }
            }
        }
    }
}
