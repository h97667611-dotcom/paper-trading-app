package com.papertrader.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.papertrader.app.ui.navigation.PaperTraderNavHost
import com.papertrader.app.ui.theme.PaperTraderTheme
import com.papertrader.app.ui.viewmodel.ViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as PaperTraderApplication).container
        val viewModelFactory = ViewModelFactory(container)

        setContent {
            PaperTraderTheme {
                PaperTraderNavHost(factory = viewModelFactory)
            }
        }
    }
}
