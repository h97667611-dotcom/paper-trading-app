package com.papertrader.app.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Live TradingView Advanced Chart embedded in a WebView.
 *
 * The TradingView toolbar gives access to every chart type (candles, bars,
 * line, area, step line, Heikin Ashi, baseline, ...), all intervals, the
 * date-range buttons (1D, 5D, 1M, 6M, YTD, 1Y, All), "go to date", drawing
 * tools and indicators. The crosshair shows the exact price at any time.
 *
 * @param coinSymbol ticker of the coin, e.g. "BTC". Mapped to BINANCE:BTCUSDT.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TradingViewChart(
    coinSymbol: String,
    modifier: Modifier = Modifier
) {
    val tvSymbol = remember(coinSymbol) { toTradingViewSymbol(coinSymbol) }
    val html = remember(tvSymbol) { buildTradingViewHtml(tvSymbol) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                setBackgroundColor(android.graphics.Color.BLACK)
                webViewClient = WebViewClient()
                // Let the chart handle pan/zoom gestures instead of the parent scroll view.
                setOnTouchListener { v, _ ->
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    false
                }
                tag = tvSymbol
                loadDataWithBaseURL("https://www.tradingview.com", html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            if (webView.tag != tvSymbol) {
                webView.tag = tvSymbol
                webView.loadDataWithBaseURL("https://www.tradingview.com", html, "text/html", "UTF-8", null)
            }
        }
    )
}

private fun toTradingViewSymbol(coinSymbol: String): String {
    val clean = coinSymbol.uppercase().filter { it.isLetterOrDigit() }.ifEmpty { "BTC" }
    return when (clean) {
        "USDT" -> "KRAKEN:USDTUSD"
        "USDC" -> "COINBASE:USDCUSD"
        else -> "BINANCE:${clean}USDT"
    }
}

private fun buildTradingViewHtml(symbol: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<style>
  html, body { margin: 0; padding: 0; height: 100%; background: #000000; overflow: hidden; }
  #tv_chart { height: 100%; width: 100%; }
</style>
</head>
<body>
<div id="tv_chart"></div>
<script src="https://s3.tradingview.com/tv.js"></script>
<script>
  new TradingView.widget({
    container_id: "tv_chart",
    autosize: true,
    symbol: "$symbol",
    interval: "60",
    timezone: "Europe/Berlin",
    theme: "dark",
    style: "1",
    locale: "en",
    backgroundColor: "#000000",
    gridColor: "rgba(255, 255, 255, 0.06)",
    toolbar_bg: "#000000",
    enable_publishing: false,
    allow_symbol_change: true,
    hide_top_toolbar: false,
    hide_side_toolbar: false,
    withdateranges: true,
    save_image: true,
    details: false,
    hotlist: false,
    calendar: false
  });
</script>
</body>
</html>
""".trimIndent()
