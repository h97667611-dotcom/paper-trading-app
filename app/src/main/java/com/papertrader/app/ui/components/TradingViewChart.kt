package com.papertrader.app.ui.components

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.papertrader.app.domain.model.AssetIds
import com.papertrader.app.domain.model.Coin

/** Maps one of our assets to a TradingView symbol. */
fun tradingViewSymbol(coin: Coin): String {
    if (AssetIds.isStock(coin.id)) return AssetIds.stockSymbol(coin.id).replace('-', '.')
    val clean = coin.symbol.uppercase().filter { it.isLetterOrDigit() }.ifEmpty { "BTC" }
    return when (clean) {
        "USDT" -> "KRAKEN:USDTUSD"
        "USDC" -> "COINBASE:USDCUSD"
        else -> "BINANCE:${clean}USDT"
    }
}

/**
 * Official TradingView "advanced chart" embed in a WebView. It has every chart type,
 * indicators, drawing tools, all timeframes and "go to date". Third-party cookies
 * and a Chrome-like user agent are enabled because the widget's inner frame needs them.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TradingViewChart(tvSymbol: String, modifier: Modifier = Modifier) {
    val html = remember(tvSymbol) { buildTradingViewHtml(tvSymbol) }
    var loaded by remember(tvSymbol) { mutableStateOf(false) }
    var failed by remember(tvSymbol) { mutableStateOf(false) }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.userAgentString = settings.userAgentString.replace("; wv", "")
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    // Let the chart handle pan/zoom instead of the parent scroll view.
                    setOnTouchListener { v, _ ->
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        false
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean = request.isForMainFrame

                        override fun onPageFinished(view: WebView?, url: String?) {
                            loaded = true
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError
                        ) {
                            if (request.isForMainFrame) failed = true
                        }
                    }
                    tag = tvSymbol
                    loadDataWithBaseURL("https://www.tradingview.com/", html, "text/html", "UTF-8", null)
                }
            },
            update = { webView ->
                if (webView.tag != tvSymbol) {
                    webView.tag = tvSymbol
                    loaded = false
                    failed = false
                    webView.loadDataWithBaseURL("https://www.tradingview.com/", html, "text/html", "UTF-8", null)
                }
            }
        )
        if (!loaded && !failed) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        if (failed) {
            Text(
                "TradingView could not load. Check your connection or use the Ghost chart.",
                color = Color.Gray,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

private fun buildTradingViewHtml(symbol: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<style>
  html, body { margin: 0; padding: 0; height: 100%; background: #000000; overflow: hidden; }
  .tradingview-widget-container { height: 100%; width: 100%; }
</style>
</head>
<body>
<div class="tradingview-widget-container">
  <div class="tradingview-widget-container__widget" style="height:100%;width:100%"></div>
  <script type="text/javascript" src="https://s3.tradingview.com/external-embedding/embed-widget-advanced-chart.js" async>
  {
    "autosize": true,
    "symbol": "$symbol",
    "interval": "60",
    "timezone": "Europe/Berlin",
    "theme": "dark",
    "style": "1",
    "locale": "en",
    "backgroundColor": "#000000",
    "gridColor": "rgba(255, 255, 255, 0.06)",
    "allow_symbol_change": true,
    "hide_side_toolbar": false,
    "withdateranges": true,
    "save_image": false,
    "calendar": false,
    "support_host": "https://www.tradingview.com"
  }
  </script>
</div>
</body>
</html>
""".trimIndent()
