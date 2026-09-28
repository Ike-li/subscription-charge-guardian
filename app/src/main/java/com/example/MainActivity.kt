package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.AppDatabase
import com.example.data.SubscriptionRepository
import com.example.notification.NotificationHelper
import com.example.ui.SubscriptionViewModel
import com.example.ui.screens.AddEditScreen
import com.example.ui.screens.DetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

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

class MainActivity : ComponentActivity() {

    private val subscriptionViewModel: SubscriptionViewModel by viewModels {
        val repo = (application as? SubGuardApplication)?.repository
            ?: SubscriptionRepository(AppDatabase.getDatabase(applicationContext).subscriptionDao())
        SubscriptionViewModel.provideFactory(repo, applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val targetSubscriptionId = intent?.getLongExtra(NotificationHelper.EXTRA_SUBSCRIPTION_ID, -1L) ?: -1L

        setContent {
            MyApplicationTheme {
                SubGuardApp(
                    viewModel = subscriptionViewModel,
                    initialSubscriptionId = targetSubscriptionId
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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

    // 如果从通知栏打开指定的订阅，自动导航至详情页
    LaunchedEffect(initialSubscriptionId) {
        if (initialSubscriptionId > 0) {
            navController.navigate("detail/$initialSubscriptionId")
        }
    }

    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Add.route,
        Screen.Settings.route
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
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
            // 首页
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

            // 添加订阅
            composable(Screen.Add.route) {
                AddEditScreen(
                    subscriptionId = 0L,
                    viewModel = viewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            // 系统设置
            composable(Screen.Settings.route) {
                SettingsScreen()
            }

            // 订阅详情
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

            // 编辑订阅
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
                    }
                )
            }
        }
    }
}
