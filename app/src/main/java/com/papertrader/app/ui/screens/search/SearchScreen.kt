package com.papertrader.app.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.papertrader.app.ui.components.ChipPill
import com.papertrader.app.ui.components.CoinCard
import com.papertrader.app.ui.components.SectionHeader
import com.papertrader.app.ui.theme.LossRed
import com.papertrader.app.ui.theme.TextSecondary
import com.papertrader.app.ui.viewmodel.ViewModelFactory

private val SUGGESTIONS = listOf("Bitcoin", "Ethereum", "Solana", "Apple", "Tesla", "Nvidia", "Microsoft")

/** Results for whatever is typed into the bottom search bar. */
@Composable
fun SearchScreen(
    factory: ViewModelFactory,
    query: String,
    onSuggestion: (String) -> Unit,
    onAssetClick: (String) -> Unit
) {
    val viewModel: SearchViewModel = viewModel(factory = factory)
    LaunchedEffect(query) { viewModel.onQueryChanged(query) }
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item { Text("Search", style = MaterialTheme.typography.headlineMedium) }

        if (query.isBlank()) {
            item {
                Text(
                    "Find any coin or stock. Type in the bar below.",
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SUGGESTIONS, key = { it }) { suggestion ->
                        ChipPill(suggestion, false, { onSuggestion(suggestion) })
                    }
                }
            }
        } else {
            if (state.isLoading) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        color = androidx.compose.ui.graphics.Color.White,
                        trackColor = androidx.compose.ui.graphics.Color(0xFF1A1A1A)
                    )
                }
            }
            if (state.crypto.isNotEmpty()) {
                item { SectionHeader("Crypto") }
                items(state.crypto, key = { "c_${it.id}" }) { coin ->
                    CoinCard(coin = coin, onClick = { onAssetClick(coin.id) })
                }
            }
            if (state.stocks.isNotEmpty()) {
                item { SectionHeader("Stocks") }
                items(state.stocks, key = { "s_${it.id}" }) { coin ->
                    CoinCard(coin = coin, onClick = { onAssetClick(coin.id) })
                }
            }
            if (!state.isLoading && state.crypto.isEmpty() && state.stocks.isEmpty() && state.query == query) {
                item {
                    Text(
                        state.errorMessage ?: "No results for \"$query\".",
                        color = if (state.errorMessage != null) LossRed else TextSecondary,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }
}
