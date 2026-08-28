package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.OnboardingDialog
import com.example.ui.navigation.Screen
import com.example.ui.navigation.bottomNavItems
import com.example.ui.screens.editor.EditorViewModel
import com.example.ui.screens.editor.PhotoEditorScreen
import com.example.ui.screens.history.HistoryScreen
import com.example.ui.screens.history.HistoryViewModel
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.scanner.ScannerScreen
import com.example.ui.screens.scanner.ScannerViewModel
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.settings.ThemeMode
import com.example.ui.theme.SnapScanTheme

class MainActivity : ComponentActivity() {

    private val scannerViewModel: ScannerViewModel by viewModels()
    private val editorViewModel: EditorViewModel by viewModels()
    private val historyViewModel: HistoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("snapscan_prefs", Context.MODE_PRIVATE) }

            var themeMode by remember {
                val savedTheme = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
                mutableStateOf(ThemeMode.valueOf(savedTheme))
            }

            var showOnboarding by remember {
                val isFirstLaunch = prefs.getBoolean("is_first_launch", true)
                mutableStateOf(isFirstLaunch)
            }

            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            SnapScanTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier.testTag("bottom_nav_bar"),
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp
                        ) {
                            bottomNavItems.forEach { screen ->
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
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                        indicatorColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag("nav_item_${screen.route}")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen(
                                onNavigateToScanner = {
                                    navController.navigate(Screen.Scanner.route) {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToEditor = {
                                    navController.navigate(Screen.Editor.route) {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToHistory = {
                                    navController.navigate(Screen.History.route) {
                                        launchSingleTop = true
                                    }
                                },
                                historyViewModel = historyViewModel
                            )
                        }

                        composable(Screen.Scanner.route) {
                            ScannerScreen(viewModel = scannerViewModel)
                        }

                        composable(Screen.Editor.route) {
                            PhotoEditorScreen(viewModel = editorViewModel)
                        }

                        composable(Screen.History.route) {
                            HistoryScreen(viewModel = historyViewModel)
                        }

                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                currentThemeMode = themeMode,
                                onThemeModeChanged = { newMode ->
                                    themeMode = newMode
                                    prefs.edit().putString("theme_mode", newMode.name).apply()
                                },
                                onShowOnboarding = { showOnboarding = true },
                                onClearAllHistory = { historyViewModel.clearAllHistory() }
                            )
                        }
                    }
                }

                // Welcome Onboarding Dialog on first launch or when triggered
                if (showOnboarding) {
                    OnboardingDialog(
                        onDismiss = {
                            showOnboarding = false
                            prefs.edit().putBoolean("is_first_launch", false).apply()
                        }
                    )
                }
            }
        }
    }
}
