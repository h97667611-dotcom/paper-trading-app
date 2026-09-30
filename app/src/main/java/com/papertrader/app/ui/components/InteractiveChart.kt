package com.papertrader.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.papertrader.app.domain.model.Candle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val UpColor = Color(0xFF2ECC71)
private val DownColor = Color(0xFFFF5C5C)
private val MutedText = Color(0xFF8E8E93)

/** Every chart type the user can switch between (a TradingView-like set). */
enum class ChartStyle(val label: String, val lineBased: Boolean) {
    LINE("Line", true),
    AREA("Area", true),
    STEP("Step line", true),
    MARKERS("Line + dots", true),
    BASELINE("Baseline", true),
    COLUMNS("Columns", true),
    CANDLES("Candles", false),
    HOLLOW("Hollow candles", false),
    HEIKIN("Heikin Ashi", false),
    BARS("Bars", false),
    HIGH_LOW("High-low", false)
}

fun formatChartPrice(v: Double): String = when {
    abs(v) >= 1000 -> String.format(Locale.US, "%,.0f", v)
    abs(v) >= 1 -> String.format(Locale.US, "%.2f", v)
    abs(v) >= 0.01 -> String.format(Locale.US, "%.4f", v)
    else -> String.format(Locale.US, "%.6f", v)
}

/** Full-precision dollar amount that also works for tiny-priced coins. */
fun formatPriceSmart(v: Double): String = when {
    abs(v) >= 1.0 -> formatUsd(v)
    abs(v) >= 0.01 -> "\$" + String.format(Locale.US, "%.4f", v)
    else -> "\$" + String.format(Locale.US, "%.8f", v)
}

fun formatDateTime(millis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))

private fun axisTimeFormat(spanMillis: Long): SimpleDateFormat = when {
    spanMillis <= 2L * 86_400_000L -> SimpleDateFormat("HH:mm", Locale.getDefault())
    spanMillis <= 120L * 86_400_000L -> SimpleDateFormat("d MMM", Locale.getDefault())
    else -> SimpleDateFormat("MMM yy", Locale.getDefault())
}

private fun pillTimeFormat(spanMillis: Long): SimpleDateFormat = when {
    spanMillis <= 2L * 86_400_000L -> SimpleDateFormat("HH:mm", Locale.getDefault())
    spanMillis <= 120L * 86_400_000L -> SimpleDateFormat("d MMM HH:mm", Locale.getDefault())
    else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault())
}

private fun toHeikinAshi(src: List<Candle>): List<Candle> {
    val out = ArrayList<Candle>(src.size)
    var prevOpen = 0.0
    var prevClose = 0.0
    src.forEachIndexed { i, c ->
        val close = (c.open + c.high + c.low + c.close) / 4.0
        val open = if (i == 0) (c.open + c.close) / 2.0 else (prevOpen + prevClose) / 2.0
        out.add(
            Candle(
                c.timeMillis,
                open,
                maxOf(c.high, open, close),
                minOf(c.low, open, close),
                close,
                c.volume
            )
        )
        prevOpen = open
        prevClose = close
    }
    return out
}

/**
 * Chart drawn on a Compose Canvas. Press and drag a finger over it to scrub:
 * a crosshair follows the finger and [onScrub] reports the candle under it
 * (so the screen can show the exact price and time), null when released.
 */
@Composable
fun InteractiveChart(
    candles: List<Candle>,
    style: ChartStyle,
    modifier: Modifier = Modifier,
    height: Dp = 300.dp,
    showAxes: Boolean = true,
    showVolume: Boolean = false,
    onScrub: (Candle?) -> Unit = {}
) {
    val measurer = rememberTextMeasurer()
    val series = remember(candles, style) {
        if (style == ChartStyle.HEIKIN) toHeikinAshi(candles) else candles
    }
    val spanMillis = if (candles.size >= 2) candles.last().timeMillis - candles.first().timeMillis else 0L
    val axisFormat = remember(spanMillis) { axisTimeFormat(spanMillis) }
    val pillFormat = remember(spanMillis) { pillTimeFormat(spanMillis) }
    var scrubIndex by remember(candles) { mutableStateOf<Int?>(null) }
    val currentOnScrub by rememberUpdatedState(onScrub)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(candles, showAxes) {
                val gutter = if (showAxes) 56.dp.toPx() else 0f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (candles.size < 2) return@awaitEachGesture
                    var last = -1
                    fun update(x: Float) {
                        val plotW = size.width - gutter
                        if (plotW <= 0f) return
                        val idx = ((x / plotW) * candles.size).toInt().coerceIn(0, candles.size - 1)
                        if (idx != last) {
                            last = idx
                            scrubIndex = idx
                            currentOnScrub(candles[idx])
                        }
                    }
                    update(down.position.x)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        update(change.position.x)
                        change.consume()
                    }
                    scrubIndex = null
                    currentOnScrub(null)
                }
            }
    ) {
        if (series.size < 2) return@Canvas
        drawChart(series, style, measurer, showAxes, showVolume, axisFormat, pillFormat, scrubIndex)
    }
}

private fun DrawScope.drawPill(
    measurer: TextMeasurer,
    text: String,
    centerX: Float,
    centerY: Float,
    bg: Color,
    minX: Float,
    maxX: Float,
    style: TextStyle
) {
    val layout = measurer.measure(text, style)
    val padH = 5.dp.toPx()
    val padV = 2.dp.toPx()
    val w = layout.size.width + padH * 2
    val h = layout.size.height + padV * 2
    val left = (centerX - w / 2f).coerceIn(minX, max(minX, maxX - w))
    val top = centerY - h / 2f
    drawRoundRect(bg, topLeft = Offset(left, top), size = Size(w, h), cornerRadius = CornerRadius(6.dp.toPx()))
    drawText(layout, topLeft = Offset(left + padH, top + padV))
}

private fun DrawScope.drawChart(
    series: List<Candle>,
    style: ChartStyle,
    measurer: TextMeasurer,
    showAxes: Boolean,
    showVolume: Boolean,
    axisFormat: SimpleDateFormat,
    pillFormat: SimpleDateFormat,
    scrubIndex: Int?
) {
    val n = series.size
    val gutterR = if (showAxes) 56.dp.toPx() else 0f
    val gutterB = if (showAxes) 24.dp.toPx() else 0f
    val plotT = 8.dp.toPx()
    val plotW = size.width - gutterR
    val plotH = size.height - plotT - gutterB
    val plotB = plotT + plotH
    if (plotW <= 0f || plotH <= 0f) return
    // With volume bars the price area gets the upper part and the bars the bottom ~20%.
    val volGap = if (showVolume) 8.dp.toPx() else 0f
    val volH = if (showVolume) plotH * 0.2f else 0f
    val priceH = plotH - volH - volGap
    val priceB = plotT + priceH

    var lo = Double.MAX_VALUE
    var hi = -Double.MAX_VALUE
    for (c in series) {
        val l = if (style.lineBased) c.close else c.low
        val h = if (style.lineBased) c.close else c.high
        if (l < lo) lo = l
        if (h > hi) hi = h
    }
    if (hi - lo < 1e-12) {
        hi += 1.0
        lo -= 1.0
    }
    val pad = (hi - lo) * 0.08
    val top = hi + pad
    val bottom = lo - pad
    val span = top - bottom

    fun yOf(p: Double): Float = plotT + (((top - p) / span) * priceH).toFloat()
    val slot = plotW / n
    fun xOf(i: Int): Float = (i + 0.5f) * slot

    val firstClose = series.first().close
    val lastClose = series.last().close
    val trend = if (lastClose >= firstClose) UpColor else DownColor
    val lineW = 2.dp.toPx()
    val lineStroke = Stroke(width = lineW, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))

    val axisStyle = TextStyle(color = MutedText, fontSize = 10.sp)
    val pillDark = TextStyle(color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)

    // Grid + axis labels
    if (showAxes) {
        val lines = 4
        for (k in 0..lines) {
            val price = top - span * k / lines
            val y = plotT + priceH * k / lines
            drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, y), Offset(plotW, y), strokeWidth = 1f)
            val layout = measurer.measure(formatChartPrice(price), axisStyle)
            drawText(
                layout,
                topLeft = Offset(
                    plotW + 6.dp.toPx(),
                    (y - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height)
                )
            )
        }
        val timeCount = 4
        for (k in 0 until timeCount) {
            val idx = ((n - 1) * k / (timeCount - 1)).coerceIn(0, n - 1)
            val layout = measurer.measure(axisFormat.format(Date(series[idx].timeMillis)), axisStyle)
            val x = (xOf(idx) - layout.size.width / 2f).coerceIn(0f, max(0f, plotW - layout.size.width))
            drawText(layout, topLeft = Offset(x, plotB + 6.dp.toPx()))
        }
    }

    // Shared line path for line-like styles
    val linePath = Path()
    if (style.lineBased) {
        series.forEachIndexed { i, c ->
            val x = xOf(i)
            val y = yOf(c.close)
            if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
        }
    }

    val bodyW = max(1f, min(slot * 0.68f, 14.dp.toPx()))
    val wickW = max(1f, min(slot * 0.12f, 2.dp.toPx()))

    when (style) {
        ChartStyle.LINE -> drawPath(linePath, trend, style = lineStroke)

        ChartStyle.MARKERS -> {
            drawPath(linePath, trend, style = lineStroke)
            val stepDot = max(1, n / 120)
            var i = 0
            while (i < n) {
                drawCircle(trend, radius = 2.5.dp.toPx(), center = Offset(xOf(i), yOf(series[i].close)))
                i += stepDot
            }
        }

        ChartStyle.AREA -> {
            val fill = Path().apply {
                addPath(linePath)
                lineTo(xOf(n - 1), priceB)
                lineTo(xOf(0), priceB)
                close()
            }
            drawPath(
                fill,
                brush = Brush.verticalGradient(
                    colors = listOf(trend.copy(alpha = 0.32f), Color.Transparent),
                    startY = plotT,
                    endY = priceB
                )
            )
            drawPath(linePath, trend, style = lineStroke)
        }

        ChartStyle.STEP -> {
            val stepPath = Path()
            var prevY = 0f
            series.forEachIndexed { i, c ->
                val x = xOf(i)
                val y = yOf(c.close)
                if (i == 0) {
                    stepPath.moveTo(x, y)
                } else {
                    stepPath.lineTo(x, prevY)
                    stepPath.lineTo(x, y)
                }
                prevY = y
            }
            drawPath(stepPath, trend, style = lineStroke)
        }

        ChartStyle.BASELINE -> {
            val by = yOf(firstClose).coerceIn(plotT, priceB)
            val fill = Path().apply {
                addPath(linePath)
                lineTo(xOf(n - 1), by)
                lineTo(xOf(0), by)
                close()
            }
            clipRect(0f, plotT, plotW, by) {
                drawPath(fill, UpColor.copy(alpha = 0.22f))
                drawPath(linePath, UpColor, style = lineStroke)
            }
            clipRect(0f, by, plotW, priceB) {
                drawPath(fill, DownColor.copy(alpha = 0.22f))
                drawPath(linePath, DownColor, style = lineStroke)
            }
            drawLine(
                Color.White.copy(alpha = 0.35f),
                Offset(0f, by),
                Offset(plotW, by),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dash
            )
        }

        ChartStyle.COLUMNS -> {
            series.forEachIndexed { i, c ->
                val color = if (c.close >= c.open) UpColor else DownColor
                val x = xOf(i)
                val y = yOf(c.close)
                drawRect(color.copy(alpha = 0.9f), Offset(x - bodyW / 2f, y), Size(bodyW, max(1f, priceB - y)))
            }
        }

        ChartStyle.CANDLES, ChartStyle.HOLLOW, ChartStyle.HEIKIN -> {
            series.forEachIndexed { i, c ->
                val x = xOf(i)
                val color = if (c.close >= c.open) UpColor else DownColor
                drawLine(color, Offset(x, yOf(c.high)), Offset(x, yOf(c.low)), strokeWidth = wickW)
                val yO = yOf(c.open)
                val yC = yOf(c.close)
                val bodyTop = min(yO, yC)
                val bodyH = max(abs(yO - yC), 1f)
                if (style == ChartStyle.HOLLOW && c.close >= c.open) {
                    drawRect(color, Offset(x - bodyW / 2f, bodyTop), Size(bodyW, bodyH), style = Stroke(width = wickW))
                } else {
                    drawRect(color, Offset(x - bodyW / 2f, bodyTop), Size(bodyW, bodyH))
                }
            }
        }

        ChartStyle.BARS -> {
            val tick = max(1f, min(slot * 0.4f, 6.dp.toPx()))
            series.forEachIndexed { i, c ->
                val x = xOf(i)
                val color = if (c.close >= c.open) UpColor else DownColor
                drawLine(color, Offset(x, yOf(c.high)), Offset(x, yOf(c.low)), strokeWidth = wickW)
                drawLine(color, Offset(x - tick, yOf(c.open)), Offset(x, yOf(c.open)), strokeWidth = wickW)
                drawLine(color, Offset(x, yOf(c.close)), Offset(x + tick, yOf(c.close)), strokeWidth = wickW)
            }
        }

        ChartStyle.HIGH_LOW -> {
            series.forEachIndexed { i, c ->
                val x = xOf(i)
                val color = if (c.close >= c.open) UpColor else DownColor
                val yH = yOf(c.high)
                val yL = yOf(c.low)
                drawRect(color, Offset(x - bodyW * 0.45f, yH), Size(bodyW * 0.9f, max(2f, yL - yH)))
            }
        }
    }

    // Volume bars
    if (showVolume) {
        var maxVol = 0.0
        for (c in series) if (c.volume > maxVol) maxVol = c.volume
        if (maxVol > 0.0) {
            series.forEachIndexed { i, c ->
                val barH = max(1f, (c.volume / maxVol * volH).toFloat())
                val color = if (c.close >= c.open) UpColor else DownColor
                drawRect(color.copy(alpha = 0.55f), Offset(xOf(i) - bodyW / 2f, plotB - barH), Size(bodyW, barH))
            }
        }
    }

    // Live price line + label
    val lastY = yOf(lastClose)
    drawLine(
        trend.copy(alpha = 0.6f),
        Offset(0f, lastY),
        Offset(plotW, lastY),
        strokeWidth = 1.dp.toPx(),
        pathEffect = dash
    )
    if (showAxes) {
        drawPill(measurer, formatChartPrice(lastClose), plotW + gutterR / 2f, lastY, trend, plotW, size.width, pillDark)
    }

    // Finger crosshair
    if (scrubIndex != null && scrubIndex in 0 until n) {
        val c = series[scrubIndex]
        val x = xOf(scrubIndex)
        val y = yOf(c.close)
        drawLine(
            Color.White.copy(alpha = 0.55f),
            Offset(x, plotT),
            Offset(x, plotB),
            strokeWidth = 1.dp.toPx(),
            pathEffect = dash
        )
        drawLine(
            Color.White.copy(alpha = 0.25f),
            Offset(0f, y),
            Offset(plotW, y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = dash
        )
        drawCircle(Color.Black, radius = 6.dp.toPx(), center = Offset(x, y))
        drawCircle(Color.White, radius = 4.5.dp.toPx(), center = Offset(x, y))
        if (showAxes) {
            drawPill(measurer, formatChartPrice(c.close), plotW + gutterR / 2f, y, Color.White, plotW, size.width, pillDark)
            drawPill(
                measurer,
                pillFormat.format(Date(c.timeMillis)),
                x,
                plotB + gutterB / 2f,
                Color.White,
                0f,
                plotW,
                pillDark
            )
        }
    }
}
