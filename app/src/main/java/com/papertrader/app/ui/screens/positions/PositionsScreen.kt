package com.papertrader.app.ui.screens.positions

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.papertrader.app.ui.components.PnlText
import com.papertrader.app.ui.components.formatPercent
import com.papertrader.app.ui.components.formatUsd
import com.papertrader.app.ui.theme.LossRed
import com.papertrader.app.ui.theme.SurfaceCard
import com.papertrader.app.ui.theme.TextSecondary
import com.papertrader.app.ui.viewmodel.ViewModelFactory

@Composable
fun PositionsScreen(factory: ViewModelFactory) {
    val viewModel: PositionsViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
        Text(
            "Positions",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        if (state.positions.isEmpty() && !state.isLoading) {
            Text(
                "You don't have any open positions yet.",
                color = TextSecondary,
                modifier = Modifier.padding(20.dp)
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.positions) { model ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceCard, RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(model.position.symbol, style = MaterialTheme.typography.titleMedium)
                        Text("${model.position.quantity} ${model.position.symbol}")
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        LabeledValue("Entry", formatUsd(model.position.avgEntryPrice))
                        LabeledValue("Current", formatUsd(model.currentPrice))
                        LabeledValue("Value", formatUsd(model.positionValue))
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        PnlText(model.unrealizedPnl)
                        Text(
                            formatPercent(model.unrealizedPnlPercent),
                            color = if (model.unrealizedPnl >= 0) com.papertrader.app.ui.theme.ProfitGreen else LossRed
                        )
                    }
                    Button(
                        onClick = { viewModel.closePosition(model.position.coinId) },
                        colors = ButtonDefaults.buttonColors(containerColor = LossRed),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) { Text("Close Position") }
                }
            }
        }

        state.errorMessage?.let { Text(it, color = LossRed, modifier = Modifier.padding(20.dp)) }
        state.closeSuccessMessage?.let {
            Text(it, color = com.papertrader.app.ui.theme.ProfitGreen, modifier = Modifier.padding(20.dp))
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
