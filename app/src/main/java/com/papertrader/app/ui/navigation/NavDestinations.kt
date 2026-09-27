package com.papertrader.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Markets : Screen("markets")
    data object Portfolio : Screen("portfolio")
    data object Orders : Screen("orders")
    data object Profile : Screen("profile")

    data object CoinDetail : Screen("coin/{coinId}") {
        fun createRoute(coinId: String) = "coin/$coinId"
    }
    data object Order : Screen("order/{coinId}/{side}") {
        fun createRoute(coinId: String, side: String) = "order/$coinId/$side"
    }
    data object Funds : Screen("funds")
    data object FundsHistory : Screen("funds_history")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard, "Home", Icons.Filled.Home),
    BottomNavItem(Screen.Markets, "Markets", Icons.Filled.TrendingUp),
    BottomNavItem(Screen.Portfolio, "Portfolio", Icons.Filled.PieChart),
    BottomNavItem(Screen.Orders, "Orders", Icons.Filled.List),
    BottomNavItem(Screen.Profile, "Profile", Icons.Filled.AccountCircle)
)
