package com.vineyard.aivideostudio.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    object Projects : Screen("projects", "Projects", Icons.Filled.Folder, Icons.Outlined.Folder)
    object Create : Screen("create", "Create", Icons.Filled.AddCircle, Icons.Outlined.AddCircleOutline)
    object Activity : Screen("activity", "Activity", Icons.AutoMirrored.Filled.ListAlt, Icons.AutoMirrored.Outlined.ListAlt)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)

    // Sub-screens
    object Processing : Screen("processing/{projectId}", "Processing", Icons.Filled.Home, Icons.Outlined.Home) {
        fun createRoute(projectId: String) = "processing/$projectId"
    }

    object Editor : Screen("editor/{projectId}", "Editor", Icons.Filled.Home, Icons.Outlined.Home) {
        fun createRoute(projectId: String) = "editor/$projectId"
    }

    companion object {
        val bottomNavItems = listOf(Home, Projects, Create, Activity, Settings)
    }
}
