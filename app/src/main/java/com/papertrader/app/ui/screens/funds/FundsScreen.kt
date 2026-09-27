package com.papertrader.app.ui.screens.funds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.papertrader.app.domain.model.FundsTransaction
import com.papertrader.app.domain.model.FundsTransactionType
import com.papertrader.app.ui.components.formatUsd
import com.papertrader.app.ui.theme.LossRed
import com.papertrader.app.ui.theme.ProfitGreen
import com.papertrader.app.ui.theme.SurfaceCard
import com.papertrader.app.ui.theme.TextSecondary
import com.papertrader.app.ui.viewmodel.ViewModelFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FundsScreen(factory: ViewModelFactory, onBack: () -> Unit) {
    val viewModel: FundsViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Funds", style = MaterialTheme.typography.headlineMedium)
            Text("Available Cash", color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
            Text(formatUsd(state.cashBalance), style = MaterialTheme.typography.displaySmall)
        }

        item {
            OutlinedTextField(
                value = state.amountInput,
                onValueChange = viewModel::onAmountChanged,
                label = { Text("Amount (USDT)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = viewModel::addFunds,
                    colors = ButtonDefaults.buttonColors(containerColor = ProfitGreen),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) { Text("+ Add Funds") }
                OutlinedButton(
                    onClick = viewModel::withdrawFunds,
                    modifier = Modifier.weight(1f).height(50.dp)
                ) { Text("Withdraw Funds") }
            }
            state.errorMessage?.let { Text(it, color = LossRed, modifier = Modifier.padding(top = 8.dp)) }
            state.successMessage?.let { Text(it, color = ProfitGreen, modifier = Modifier.padding(top = 8.dp)) }
        }

        item {
            Text(
                "This adds or removes virtual paper-trading balance only. No real money is deposited or withdrawn.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        item {
            Text("History", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        }

        items(state.history) { entry -> FundsHistoryRow(entry, dateFormat) }
    }
}

@Composable
private fun FundsHistoryRow(entry: FundsTransaction, dateFormat: SimpleDateFormat) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (entry.type == FundsTransactionType.DEPOSIT) "Deposit" else "Withdrawal",
                color = if (entry.type == FundsTransactionType.DEPOSIT) ProfitGreen else LossRed
            )
            Text(dateFormat.format(Date(entry.timestampMillis)), color = TextSecondary)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${if (entry.amount >= 0) "+" else ""}${formatUsd(entry.amount)}",
                color = if (entry.amount >= 0) ProfitGreen else LossRed
            )
            Text("New balance: ${formatUsd(entry.balanceAfter)}", color = TextSecondary)
        }
    }
}
