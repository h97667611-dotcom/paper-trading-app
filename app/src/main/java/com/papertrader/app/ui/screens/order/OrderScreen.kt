package com.papertrader.app.ui.screens.order

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.papertrader.app.domain.model.OrderSide
import com.papertrader.app.domain.model.OrderType
import com.papertrader.app.ui.components.formatUsd
import com.papertrader.app.ui.theme.LossRed
import com.papertrader.app.ui.theme.ProfitGreen
import com.papertrader.app.ui.theme.SurfaceCard
import com.papertrader.app.ui.theme.TextSecondary
import com.papertrader.app.ui.viewmodel.ViewModelFactory

@Composable
fun OrderScreen(
    factory: ViewModelFactory,
    coinId: String,
    side: OrderSide,
    onOrderFilled: () -> Unit,
    onBack: () -> Unit
) {
    val viewModel: OrderViewModel = viewModel(factory = factory)
    LaunchedEffect(coinId, side) { viewModel.load(coinId, side) }
    val state by viewModel.uiState.collectAsState()
    val coin = state.coin

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            text = if (side == OrderSide.BUY) "Buy ${coin?.symbol.orEmpty()}" else "Sell ${coin?.symbol.orEmpty()}",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Current price", color = TextSecondary)
            Text(formatUsd(coin?.currentPrice ?: 0.0))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (side == OrderSide.BUY) "Available cash" else "Available balance", color = TextSecondary)
            Text(
                if (side == OrderSide.BUY) formatUsd(state.availableCash) else "${state.heldQuantity} ${coin?.symbol.orEmpty()}"
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OrderType.values().forEach { type ->
                FilterChip(
                    selected = state.orderType == type,
                    onClick = { viewModel.onOrderTypeChanged(type) },
                    label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.quantityInput,
            onValueChange = viewModel::onQuantityChanged,
            label = { Text("Quantity (${coin?.symbol.orEmpty()})") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (state.orderType == OrderType.LIMIT) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.limitPriceInput,
                onValueChange = viewModel::onLimitPriceChanged,
                label = { Text("Limit price (USDT)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCard, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            SummaryRow("Order Value", formatUsd(state.orderValue))
            SummaryRow("Estimated Fee (simulated)", formatUsd(state.estimatedFee))
        }

        state.errorMessage?.let {
            Text(it, color = LossRed, modifier = Modifier.padding(top = 12.dp))
        }
        state.successMessage?.let {
            Text(it, color = ProfitGreen, modifier = Modifier.padding(top = 12.dp))
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = viewModel::requestConfirmation,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (side == OrderSide.BUY) ProfitGreen else LossRed
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Place Paper Trade")
        }
    }

    if (state.showConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirmation,
            title = { Text("Confirm Paper Trade") },
            text = {
                Column {
                    Text("${side.name} ${state.quantityInput} ${coin?.symbol.orEmpty()}")
                    Text("Order type: ${state.orderType.name}")
                    Text("Estimated value: ${formatUsd(state.orderValue)}")
                    Text(
                        "This simulates a trade using virtual funds only. No real order is sent to any exchange.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmPlaceOrder(onOrderFilled) }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConfirmation) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary)
        Text(value)
    }
}
