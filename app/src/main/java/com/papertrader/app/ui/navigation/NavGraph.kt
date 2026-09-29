package com.papertrader.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.papertrader.app.domain.model.OrderSide
import com.papertrader.app.ui.screens.coindetail.CoinDetailScreen
import com.papertrader.app.ui.screens.dashboard.DashboardScreen
import com.papertrader.app.ui.screens.funds.FundsScreen
import com.papertrader.app.ui.screens.history.HistoryScreen
import com.papertrader.app.ui.screens.markets.MarketsScreen
import com.papertrader.app.ui.screens.order.OrderScreen
import com.papertrader.app.ui.screens.profile.ProfileScreen
import com.papertrader.app.ui.screens.search.SearchScreen
import com.papertrader.app.ui.viewmodel.ViewModelFactory

/**
 * Root navigation host. Home now shows the balance and every open crypto/stock
 * position (the old Portfolio tab was merged into it). "Orders" is the trade history.
 */
@Composable
fun PaperTraderNavHost(factory: ViewModelFactory) {
    val navController = rememberNavController()
    var searchQuery by rememberSaveable { mutableStateOf("") }

    Scaffold(
        bottomBar = {
            PaperTraderBottomBar(
                navController = navController,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 24 } },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(120)) }
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    factory = factory,
                    onHoldingClick = { id -> navController.navigate(Screen.CoinDetail.createRoute(id)) }
                )
            }
            composable(Screen.Markets.route) {
                MarketsScreen(factory, onAssetClick = { id ->
                    navController.navigate(Screen.CoinDetail.createRoute(id))
                })
            }
            composable(Screen.Search.route) {
                SearchScreen(
                    factory = factory,
                    query = searchQuery,
                    onSuggestion = { searchQuery = it },
                    onAssetClick = { id ->
                        searchQuery = ""
                        navController.navigate(Screen.CoinDetail.createRoute(id))
                    }
                )
            }
            composable(Screen.Orders.route) {
                HistoryScreen(factory)
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    factory = factory,
                    onOpenFunds = { navController.navigate(Screen.Funds.route) },
                    onResetComplete = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(navController.graph.id) { inclusive = false }
                        }
                    }
                )
            }
            composable(
                route = Screen.CoinDetail.route,
                arguments = listOf(navArgument("coinId") { type = NavType.StringType })
            ) { backStackEntry ->
                val coinId = backStackEntry.arguments?.getString("coinId").orEmpty()
                CoinDetailScreen(
                    factory = factory,
                    coinId = coinId,
                    onBack = { navController.popBackStack() },
                    onBuyClick = { navController.navigate(Screen.Order.createRoute(coinId, OrderSide.BUY.name)) },
                    onSellClick = { navController.navigate(Screen.Order.createRoute(coinId, OrderSide.SELL.name)) }
                )
            }
            composable(
                route = Screen.Order.route,
                arguments = listOf(
                    navArgument("coinId") { type = NavType.StringType },
                    navArgument("side") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val coinId = backStackEntry.arguments?.getString("coinId").orEmpty()
                val side = OrderSide.valueOf(backStackEntry.arguments?.getString("side") ?: OrderSide.BUY.name)
                OrderScreen(
                    factory = factory,
                    coinId = coinId,
                    side = side,
                    onOrderFilled = { goHome(navController) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Funds.route) {
                FundsScreen(factory, onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun goHome(navController: NavHostController) {
    navController.navigate(Screen.Dashboard.route) {
        popUpTo(Screen.Dashboard.route) { inclusive = false }
        launchSingleTop = true
    }
}
