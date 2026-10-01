package com.vineyard.aivideostudio.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vineyard.aivideostudio.EditoraApplication
import com.vineyard.aivideostudio.ui.components.FloatingLogConsole
import com.vineyard.aivideostudio.ui.screens.activity.ActivityScreen
import com.vineyard.aivideostudio.ui.screens.activity.ActivityViewModel
import com.vineyard.aivideostudio.ui.screens.create.CreateScreen
import com.vineyard.aivideostudio.ui.screens.create.CreateViewModel
import com.vineyard.aivideostudio.ui.screens.editor.EditorScreen
import com.vineyard.aivideostudio.ui.screens.editor.EditorViewModel
import com.vineyard.aivideostudio.ui.screens.home.HomeScreen
import com.vineyard.aivideostudio.ui.screens.home.HomeViewModel
import com.vineyard.aivideostudio.ui.screens.processing.ProcessingScreen
import com.vineyard.aivideostudio.ui.screens.processing.ProcessingViewModel
import com.vineyard.aivideostudio.ui.screens.projects.ProjectsScreen
import com.vineyard.aivideostudio.ui.screens.projects.ProjectsViewModel
import com.vineyard.aivideostudio.ui.screens.settings.SettingsScreen
import com.vineyard.aivideostudio.ui.screens.settings.SettingsViewModel

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val app = context.applicationContext as EditoraApplication
    val container = app.container

    Scaffold(
        bottomBar = {
            StudioBottomNavigationBar(navController = navController)
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.fillMaxSize()
            ) {
            composable(Screen.Home.route) {
                val homeViewModel = remember {
                    HomeViewModel(
                        container.projectRepository,
                        container.modelRepository,
                        container.geminiPreferences
                    )
                }
                HomeScreen(
                    viewModel = homeViewModel,
                    onCreateProject = { navController.navigate(Screen.Create.route) },
                    onOpenProject = { projectId ->
                        navController.navigate(Screen.Processing.createRoute(projectId))
                    },
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    onOpenProjects = { navController.navigate(Screen.Projects.route) }
                )
            }

            composable(Screen.Projects.route) {
                val projectsViewModel = remember {
                    ProjectsViewModel(
                        container.projectRepository,
                        container.projectStorageManager
                    )
                }
                ProjectsScreen(
                    viewModel = projectsViewModel,
                    onOpenProject = { projectId ->
                        navController.navigate(Screen.Processing.createRoute(projectId))
                    }
                )
            }

            composable(Screen.Create.route) {
                val createViewModel = remember {
                    CreateViewModel(
                        context,
                        container.projectRepository,
                        container.projectStorageManager,
                        container.videoMetadataReader
                    )
                }
                CreateScreen(
                    viewModel = createViewModel,
                    onProjectCreated = { projectId ->
                        navController.navigate(Screen.Processing.createRoute(projectId)) {
                            popUpTo(Screen.Home.route)
                        }
                    }
                )
            }

            composable(Screen.Activity.route) {
                val activityViewModel = remember {
                    ActivityViewModel(container.logger)
                }
                ActivityScreen(viewModel = activityViewModel)
            }

            composable(Screen.Settings.route) {
                val settingsViewModel = remember {
                    SettingsViewModel(
                        container.geminiPreferences,
                        container.processingPreferences,
                        container.modelRepository,
                        container.voiceRepository
                    )
                }
                SettingsScreen(viewModel = settingsViewModel)
            }

            composable(
                route = Screen.Processing.route,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                val processingViewModel = remember(projectId) {
                    ProcessingViewModel(
                        projectId = projectId,
                        controller = container.processingController,
                        projectRepository = container.projectRepository
                    )
                }
                ProcessingScreen(
                    viewModel = processingViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenEditor = { id ->
                        navController.navigate(Screen.Editor.createRoute(id))
                    }
                )
            }

            composable(
                route = Screen.Editor.route,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                val editorViewModel = remember(projectId) {
                    EditorViewModel(
                        projectId = projectId,
                        projectRepository = container.projectRepository
                    )
                }
                EditorScreen(
                    viewModel = editorViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        // Floating Log Console overlay available globally across all screens
        FloatingLogConsole(logger = container.logger)
    }
}
}
