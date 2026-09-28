package com.papertrader.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * A dependency-free line chart drawn on a Compose [Canvas]. Renders a
 * gradient-filled price/value line, matching the subtle glowing-line look of
 * the reference designs, without pulling in a third-party charting library.
 */
@Composable
fun PriceLineChart(
    values: List<Float>,
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    val lineColor = if (isPositive) Color(0xFF2ECC71) else Color(0xFFFF5C5C)
    var progress by remember(values) { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 400),
        label = "chartProgress"
    )
    LaunchedEffect(values) { progress = 1f }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        if (values.size < 2) return@Canvas
        val minValue = values.min()
        val maxValue = values.max()
        val range = (maxValue - minValue).takeIf { it > 0f } ?: 1f

        val stepX = size.width / (values.size - 1)
        val points = values.mapIndexed { index, value ->
            val x = index * stepX
            val y = size.height - ((value - minValue) / range) * size.height
            x to y
        }

        val visibleCount = (points.size * animatedProgress).toInt().coerceAtLeast(2)
        val visiblePoints = points.take(visibleCount)

        val linePath = Path().apply {
            visiblePoints.forEachIndexed { index, (x, y) ->
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        val fillPath = Path().apply {
            addPath(linePath)
            val last = visiblePoints.last()
            lineTo(last.first, size.height)
            lineTo(visiblePoints.first().first, size.height)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent)
            )
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 4f)
        )
    }
}
