package com.papertrader.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Markets : Screen("markets")
    data object Orders : Screen("orders")
    data object Profile : Screen("profile")
    data object Search : Screen("search")

    data object CoinDetail : Screen("coin/{coinId}") {
        fun createRoute(coinId: String) = "coin/$coinId"
    }
    data object Order : Screen("order/{coinId}/{side}") {
        fun createRoute(coinId: String, side: String) = "order/$coinId/$side"
    }
    data object Funds : Screen("funds")
    data object FundsHistory : Screen("funds_history")
    data object BetDetail : Screen("bet/{tokenId}") {
        fun createRoute(tokenId: String) = "bet/$tokenId"
    }
    data object CasinoGame : Screen("casino_game/{game}") {
        fun createRoute(game: String) = "casino_game/$game"
    }
    data object CasinoPlay : Screen("casino_play")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

// Portfolio is merged into Home, so there is no separate Portfolio tab any more.
val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard, "Home", Icons.Filled.Home),
    BottomNavItem(Screen.Markets, "Markets", Icons.Filled.TrendingUp),
    BottomNavItem(Screen.Orders, "Orders", Icons.Filled.List),
    BottomNavItem(Screen.Profile, "Profile", Icons.Filled.AccountCircle)
)
