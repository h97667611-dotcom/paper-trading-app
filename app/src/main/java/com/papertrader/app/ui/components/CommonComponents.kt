package com.papertrader.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.papertrader.app.domain.model.Coin
import com.papertrader.app.ui.theme.LossRed
import com.papertrader.app.ui.theme.ProfitGreen
import com.papertrader.app.ui.theme.SurfaceCardElevated
import com.papertrader.app.ui.theme.TextSecondary
import java.math.BigDecimal
import java.math.RoundingMode
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

/** Quantity without trailing zeros, e.g. 0.5 or 12.3456. */
fun formatQuantity(quantity: Double): String {
    val scale = if (quantity >= 1.0) 4 else 8
    return BigDecimal(quantity).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}

/** Clickable with a soft spring "press" scale for a smoother, more tactile feel. */
fun Modifier.bounceClick(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bounce"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

@Composable
fun ChipPill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = label,
        color = if (selected) Color.Black else Color.White,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .bounceClick(onClick)
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White else SurfaceCardElevated)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
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
            .bounceClick(onClick)
            .clip(RoundedCornerShape(20.dp))
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CoinLogo(imageUrl = coin.imageUrl, symbol = coin.symbol)
            Column {
                Text(coin.symbol, style = MaterialTheme.typography.titleMedium)
                Text(
                    coin.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatPriceSmart(coin.currentPrice), style = MaterialTheme.typography.titleMedium)
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
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(SurfaceCardElevated),
            contentAlignment = Alignment.Center
        ) {
            Text(symbol.take(1), color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}
