package com.papertrader.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import com.papertrader.app.ui.screens.positions.PositionsScreen
import com.papertrader.app.ui.screens.profile.ProfileScreen
import com.papertrader.app.ui.viewmodel.ViewModelFactory

/**
 * Root navigation host. "Orders" in the bottom bar shows trade history (a
 * feed of every simulated order/fill); the Positions screen (linked from
 * Portfolio) covers currently-open holdings, matching the product brief's
 * distinct Positions vs. Trade History screens.
 */
@Composable
fun PaperTraderNavHost(factory: ViewModelFactory) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { PaperTraderBottomBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(factory)
            }
            composable(Screen.Markets.route) {
                MarketsScreen(factory, onCoinClick = { coinId ->
                    navController.navigate(Screen.CoinDetail.createRoute(coinId))
                })
            }
            composable(Screen.Portfolio.route) {
                PositionsScreen(factory)
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
                    onOrderFilled = { navController.popBackStack(Screen.Portfolio.route, inclusive = false) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Funds.route) {
                FundsScreen(factory, onBack = { navController.popBackStack() })
            }
        }
    }
}
