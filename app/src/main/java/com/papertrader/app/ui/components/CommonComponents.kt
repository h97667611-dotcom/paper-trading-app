package com.papertrader.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.papertrader.app.domain.model.Coin
import com.papertrader.app.ui.theme.LossRed
import com.papertrader.app.ui.theme.ProfitGreen
import com.papertrader.app.ui.theme.TextSecondary
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

private val usdFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US)

fun formatUsd(value: Double): String = usdFormat.format(value)

fun formatUsdCompact(value: Double): String {
    val abs = abs(value)
    return when {
        abs >= 1_000_000_000 -> "$${"%.2f".format(value / 1_000_000_000)}B"
        abs >= 1_000_000 -> "$${"%.2f".format(value / 1_000_000)}M"
        abs >= 1_000 -> "$${"%.2f".format(value / 1_000)}K"
        else -> formatUsd(value)
    }
}

fun formatPercent(value: Double): String {
    val sign = if (value >= 0) "+" else ""
    return "$sign${"%.2f".format(value)}%"
}

@Composable
fun PnlText(value: Double, showSign: Boolean = true, modifier: Modifier = Modifier) {
    val color = if (value >= 0) ProfitGreen else LossRed
    val sign = if (showSign && value >= 0) "+" else ""
    Text(
        text = "$sign${formatUsd(value)}",
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
    )
}

@Composable
fun CoinCard(
    coin: Coin,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CoinLogo(imageUrl = coin.imageUrl, symbol = coin.symbol)
            Column {
                Text(coin.symbol, style = MaterialTheme.typography.titleMedium)
                Text(coin.name, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
            Text(formatUsd(coin.currentPrice), style = MaterialTheme.typography.titleMedium)
            Text(
                formatPercent(coin.priceChangePercent24h),
                color = if (coin.priceChangePercent24h >= 0) ProfitGreen else LossRed,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun CoinLogo(imageUrl: String?, symbol: String, sizeDp: Int = 40) {
    if (imageUrl != null) {
        AsyncImage(
            model = imageUrl,
            contentDescription = symbol,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
        )
    } else {
        Column(
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(Color(0xFF33343A)),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(symbol.take(1), color = TextSecondary)
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(vertical = 8.dp)
    )
}
