package com.papertrader.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.papertrader.app.ui.components.bounceClick
import com.papertrader.app.ui.theme.AccentWhite
import com.papertrader.app.ui.theme.BackgroundBlack
import com.papertrader.app.ui.theme.SurfaceCard
import com.papertrader.app.ui.theme.SurfaceCardElevated
import com.papertrader.app.ui.theme.TextSecondary

/**
 * Phantom-style bottom area: a real search field (crypto + stocks) with a white "+"
 * button, and the tab bar underneath. Typing or focusing the field opens the search
 * screen; the tab bar tucks away while the keyboard is open.
 */
@Composable
fun PaperTraderBottomBar(
    navController: NavHostController,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val route = currentDestination?.route
    val onSearchScreen = route == Screen.Search.route
    val hideSearchRow = route == Screen.Funds.route || route?.startsWith("order/") == true
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val focusManager = LocalFocusManager.current

    fun openSearch() {
        if (navController.currentDestination?.route != Screen.Search.route) {
            navController.navigate(Screen.Search.route) { launchSingleTop = true }
        }
    }

    Column(modifier = Modifier.background(BackgroundBlack).imePadding()) {
        if (!hideSearchRow) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(SurfaceCard)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (searchQuery.isEmpty()) {
                            Text("Search crypto & stocks", color = TextSecondary)
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = {
                                onSearchQueryChange(it)
                                if (it.isNotBlank()) openSearch()
                            },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                            cursorBrush = SolidColor(Color.White),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { if (it.isFocused) openSearch() }
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Clear search",
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable {
                                    onSearchQueryChange("")
                                    focusManager.clearFocus()
                                    if (onSearchScreen) navController.popBackStack()
                                }
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .bounceClick {
                            focusManager.clearFocus()
                            navController.navigate(Screen.Funds.route)
                        }
                        .clip(CircleShape)
                        .background(AccentWhite),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add funds", tint = BackgroundBlack)
                }
            }
        }

        AnimatedVisibility(visible = !imeVisible) {
            NavigationBar(containerColor = BackgroundBlack) {
                bottomNavItems.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            onSearchQueryChange("")
                            focusManager.clearFocus()
                            navController.navigate(item.screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentWhite,
                            selectedTextColor = AccentWhite,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = SurfaceCardElevated
                        )
                    )
                }
            }
        }
    }
}
