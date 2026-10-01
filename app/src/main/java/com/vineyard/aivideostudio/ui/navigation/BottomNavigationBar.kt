package com.vineyard.aivideostudio.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.vineyard.aivideostudio.ui.theme.AmberAccent
import com.vineyard.aivideostudio.ui.theme.StudioSurface
import com.vineyard.aivideostudio.ui.theme.StudioSurfaceElevated
import com.vineyard.aivideostudio.ui.theme.TextPrimary
import com.vineyard.aivideostudio.ui.theme.TextSecondary
import com.vineyard.aivideostudio.ui.theme.VioletPrimary

@Composable
fun StudioBottomNavigationBar(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Hide bottom bar on full screen sub-screens if desired, or keep available
    val showBottomBar = Screen.bottomNavItems.any { it.route == currentRoute }

    if (showBottomBar) {
        NavigationBar(
            modifier = modifier.testTag("studio_bottom_nav"),
            containerColor = StudioSurface,
            tonalElevation = 8.dp,
            windowInsets = WindowInsets.navigationBars
        ) {
            Screen.bottomNavItems.forEach { screen ->
                val selected = currentRoute == screen.route
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        if (currentRoute != screen.route) {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                            contentDescription = screen.title
                        )
                    },
                    label = {
                        Text(
                            text = screen.title,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TextPrimary,
                        selectedTextColor = VioletPrimary,
                        indicatorColor = VioletPrimary.copy(alpha = 0.35f),
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
            }
        }
    }
}
