package com.papertrader.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.papertrader.app.BuildConfig
import com.papertrader.app.ui.theme.LossRed
import com.papertrader.app.ui.theme.SurfaceCard
import com.papertrader.app.ui.theme.TextSecondary
import com.papertrader.app.ui.viewmodel.ViewModelFactory

private data class SettingsRow(val title: String, val subtitle: String? = null, val onClick: () -> Unit = {})

@Composable
fun ProfileScreen(
    factory: ViewModelFactory,
    onOpenFunds: () -> Unit,
    onResetComplete: () -> Unit
) {
    val viewModel: ProfileViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.resetComplete) {
        if (state.resetComplete) {
            onResetComplete()
            viewModel.consumeResetComplete()
        }
    }

    val rows = listOf(
        SettingsRow("Account", "Demo paper-trading account"),
        SettingsRow("Funds", "Add or withdraw virtual balance", onOpenFunds),
        SettingsRow("Trading Settings", "Order defaults, confirmations"),
        SettingsRow("Currency", "USD"),
        SettingsRow("Notifications", "Price alerts, order fills"),
        SettingsRow("Dark Mode", "Always on for this app"),
        SettingsRow("API Settings", "CoinGecko & DexScreener endpoints"),
        SettingsRow("Data Sources", "CoinGecko (market data) · DexScreener (DEX pairs)"),
        SettingsRow("Reset Paper Account", "Erase portfolio & trade history", viewModel::requestReset),
        SettingsRow("About", "Paper Trader v${BuildConfig.VERSION_NAME}")
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Profile",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(rows) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceCard, RoundedCornerShape(16.dp))
                        .clickable(onClick = row.onClick)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            row.title,
                            color = if (row.title == "Reset Paper Account") LossRed else MaterialTheme.colorScheme.onSurface
                        )
                        row.subtitle?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.bodyMedium) }
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
                }
            }
            item {
                Text(
                    "Simulation only. No real funds, exchanges, or wallets are involved.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }
    }

    if (state.showResetConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissResetConfirmation,
            title = { Text("Reset Paper Account?") },
            text = { Text("Are you sure? This will permanently delete your paper portfolio and trade history.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmReset) { Text("Reset", color = LossRed) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissResetConfirmation) { Text("Cancel") }
            }
        )
    }
}
