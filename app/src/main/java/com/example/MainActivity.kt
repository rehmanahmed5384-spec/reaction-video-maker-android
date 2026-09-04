package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.data.ProjectRepository
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.ReactionStudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = ProjectRepository(applicationContext)

        setContent {
            ReactionStudioTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                repository = repository,
                                onOpenProject = { projectId ->
                                    navController.navigate("editor/$projectId")
                                },
                                onNavigateDiagnostics = {
                                    navController.navigate("diagnostics")
                                }
                            )
                        }

                        composable(
                            route = "editor/{projectId}",
                            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                            EditorScreen(
                                projectId = projectId,
                                repository = repository,
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onNavigateDiagnostics = {
                                    navController.navigate("diagnostics")
                                }
                            )
                        }

                        composable("diagnostics") {
                            DiagnosticsScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
