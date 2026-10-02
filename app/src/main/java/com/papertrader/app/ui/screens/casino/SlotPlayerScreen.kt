package com.papertrader.app.ui.screens.casino

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.papertrader.app.data.casino.Hub88Client
import com.papertrader.app.ui.theme.LossRed
import kotlinx.coroutines.CancellationException

private const val LOBBY_URL = "https://ghosttrade.invalid/lobby"

/**
 * Opens one Hub88 game in DEMO mode: asks Hub88 for the launch URL (signed request) and loads it.
 * The game's "home" button is sent to a placeholder lobby URL, which closes this screen.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SlotPlayerScreen(onBack: () -> Unit) {
    var url by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            url = Hub88Client.demoUrl(
                baseUrl = CasinoSession.baseUrl,
                operatorId = CasinoSession.operatorId,
                privateKey = CasinoSession.privateKey,
                gameCode = CasinoSession.gameCode,
                lobbyUrl = LOBBY_URL
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = Hub88Client.describeError(e)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(CasinoSession.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val launchUrl = url
        when {
            launchUrl != null -> AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.userAgentString = settings.userAgentString.replace("; wv", "")
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                if (request.url.toString().startsWith(LOBBY_URL)) {
                                    onBack()
                                    return true
                                }
                                return false
                            }
                        }
                        loadUrl(launchUrl)
                    }
                },
                onRelease = { it.destroy() }
            )
            error != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error.orEmpty(), color = LossRed, modifier = Modifier.padding(32.dp))
            }
            else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
