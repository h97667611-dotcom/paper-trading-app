package com.papertrader.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.papertrader.app.domain.model.BetMarket
import com.papertrader.app.ui.theme.LossRed
import com.papertrader.app.ui.theme.ProfitGreen
import com.papertrader.app.ui.theme.SurfaceCardElevated
import com.papertrader.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

fun formatCents(price: Double): String = when {
    price >= 0.995 -> "100\u00A2"
    price < 0.005 -> "<1\u00A2"
    else -> "${(price * 100).roundToInt()}\u00A2"
}

/** Decimal betting odds from a probability, e.g. 0.62 -> 1.61. */
fun formatDecimalOdds(price: Double): String =
    if (price > 0.001) String.format(Locale.US, "%.2f", 1.0 / price) else "-"

fun formatEndDate(millis: Long?): String =
    if (millis == null) "-" else SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))

fun outcomeColor(label: String): Color = when (label.trim().lowercase()) {
    "yes", "over" -> ProfitGreen
    "no", "under" -> LossRed
    else -> Color.White
}

/** A prediction market in a list: question, and each outcome with its price (or decimal odds). */
@Composable
fun BetRow(market: BetMarket, decimalOdds: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        CoinLogo(imageUrl = market.image, symbol = market.question)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                market.question,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                market.outcomes.take(3).forEachIndexed { i, label ->
                    val price = market.prices[i]
                    Row(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceCardElevated)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            label,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (decimalOdds) formatDecimalOdds(price) else formatCents(price),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = outcomeColor(label)
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Vol ${formatUsdCompact(market.volume)} \u00B7 ends ${formatEndDate(market.endDateMillis)}",
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
}
