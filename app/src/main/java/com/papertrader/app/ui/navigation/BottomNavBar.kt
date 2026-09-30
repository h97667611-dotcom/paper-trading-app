package com.papertrader.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.papertrader.app.ui.components.bounceClick
import com.papertrader.app.ui.theme.BackgroundBlack
import com.papertrader.app.ui.theme.TextSecondary

/** Switches to a top-level tab and keeps each tab's state. */
fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Bottom navigation: Home, Markets, a white round "Trade" button in the middle (opens search),
 * Orders and Profile. It tucks away while the keyboard is open.
 */
@Composable
fun PaperTraderBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    fun selected(route: String) = destination?.hierarchy?.any { it.route == route } == true

    AnimatedVisibility(visible = !imeVisible) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BackgroundBlack)
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(Icons.Filled.Home, "Home", selected(Screen.Dashboard.route)) {
                navController.switchTab(Screen.Dashboard.route)
            }
            NavItem(Icons.Filled.TrendingUp, "Markets", selected(Screen.Markets.route)) {
                navController.switchTab(Screen.Markets.route)
            }
            TradeButton(selected(Screen.Search.route)) {
                navController.navigate(Screen.Search.route) { launchSingleTop = true }
            }
            NavItem(Icons.Filled.List, "Orders", selected(Screen.Orders.route)) {
                navController.switchTab(Screen.Orders.route)
            }
            NavItem(Icons.Filled.AccountCircle, "Profile", selected(Screen.Profile.route)) {
                navController.switchTab(Screen.Profile.route)
            }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) Color.White else TextSecondary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .bounceClick(onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = tint, fontSize = 11.sp)
    }
}

@Composable
private fun TradeButton(selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .bounceClick(onClick)
            .padding(horizontal = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.SwapHoriz, contentDescription = "Trade", tint = Color.Black, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text("Trade", color = if (selected) Color.White else TextSecondary, fontSize = 11.sp)
    }
}
