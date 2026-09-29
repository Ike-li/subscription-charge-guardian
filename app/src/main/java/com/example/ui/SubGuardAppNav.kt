package com.example.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.AddEditScreen
import com.example.ui.screens.DetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : Screen("home", "首页", Icons.Filled.Home, Icons.Outlined.Home)
    data object Add : Screen("add", "添加", Icons.Filled.AddCircle, Icons.Filled.AddCircleOutline)
    data object Settings : Screen("settings", "设置", Icons.Filled.Settings, Icons.Outlined.Settings)

    companion object {
        val bottomNavItems: List<Screen>
            get() = listOf(Home, Add, Settings)
    }
}

@Composable
fun SubGuardApp(
    viewModel: SubscriptionViewModel,
    initialSubscriptionId: Long = -1L,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(initialSubscriptionId) {
        if (initialSubscriptionId > 0) {
            navController.navigate("detail/$initialSubscriptionId")
        }
    }

    // 保存后会返回上一页，“已保存”提示要放在外层 Scaffold 才能在返回后看到
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val onSaved: () -> Unit = {
        navController.popBackStack()
        coroutineScope.launch { snackbarHostState.showSnackbar("已保存") }
    }

    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Add.route,
        Screen.Settings.route
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    Screen.bottomNavItems.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title,
                                    modifier = Modifier.size(26.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            selected = isSelected,
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
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("bottom_nav_item_${screen.route}")
                        )
                    }
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
                    viewModel = viewModel,
                    onNavigateToAdd = {
                        navController.navigate(Screen.Add.route)
                    },
                    onNavigateToDetail = { subId ->
                        navController.navigate("detail/$subId")
                    }
                )
            }

            composable(Screen.Add.route) {
                AddEditScreen(
                    subscriptionId = 0L,
                    viewModel = viewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onSaved = onSaved
                )
            }

            composable(Screen.Settings.route) {
                val calendarSyncEnabled by viewModel.calendarSyncEnabled.collectAsStateWithLifecycle()
                SettingsScreen(
                    calendarSyncEnabled = calendarSyncEnabled,
                    onCalendarSyncEnabledChange = viewModel::setCalendarSyncEnabled
                )
            }

            composable(
                route = "detail/{subscriptionId}",
                arguments = listOf(navArgument("subscriptionId") { type = NavType.LongType })
            ) { backStackEntry ->
                val subId = backStackEntry.arguments?.getLong("subscriptionId") ?: 0L
                DetailScreen(
                    subscriptionId = subId,
                    viewModel = viewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToEdit = { editId ->
                        navController.navigate("edit/$editId")
                    }
                )
            }

            composable(
                route = "edit/{subscriptionId}",
                arguments = listOf(navArgument("subscriptionId") { type = NavType.LongType })
            ) { backStackEntry ->
                val subId = backStackEntry.arguments?.getLong("subscriptionId") ?: 0L
                AddEditScreen(
                    subscriptionId = subId,
                    viewModel = viewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onSaved = onSaved
                )
            }
        }
    }
}
