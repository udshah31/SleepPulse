package com.sleeppulse.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.collectAsState
import com.sleeppulse.app.data.source.ScannedDevice
import com.sleeppulse.app.ui.components.CalmNightBackdrop
import com.sleeppulse.app.ui.theme.CalmNightSurface
import com.sleeppulse.app.ui.theme.CalmNightTextSecondary
import com.sleeppulse.app.ui.theme.SleepIndigo
import com.sleeppulse.app.ui.dashboard.DashboardScreen
import com.sleeppulse.app.ui.history.HistoryScreen
import com.sleeppulse.app.ui.scan.ScanScreen
import com.sleeppulse.app.ui.settings.SettingsScreen
import com.sleeppulse.app.ui.recovery.RecoveryScreen
import com.sleeppulse.app.ui.alarm.AlarmScreen
import com.sleeppulse.app.ui.breathe.BreatheScreen

private sealed class Destination(val route: String, val label: String) {
    data object Dashboard : Destination("dashboard", "Home")
    data object History : Destination("history", "History")
    data object Recovery : Destination("recovery", "Recovery")
    data object Alarm : Destination("alarm", "Alarm")
    data object Settings : Destination("settings", "Settings")
    data object Scan : Destination("scan", "Scan")
    data object Breathe : Destination("breathe", "Breathe")
}

private val destinations = listOf(Destination.Dashboard, Destination.History, Destination.Recovery, Destination.Alarm, Destination.Settings)

@Composable
fun SleepPulseApp() {
    val navController = rememberNavController()

    CalmNightBackdrop(modifier = Modifier.fillMaxSize()) {
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val onTab = destinations.any { it.route == currentRoute }
        val haptic = LocalHapticFeedback.current
        // Tablets, foldables and landscape phones get a side rail instead of a bottom bar.
        val wide = LocalConfiguration.current.screenWidthDp >= WIDE_LAYOUT_MIN_DP
        val navigateTo: (Destination) -> Unit = { destination ->
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            navController.navigate(destination.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (onTab && !wide) {
                    NavigationBar(
                        containerColor = CalmNightSurface.copy(alpha = 0.96f),
                        tonalElevation = 0.dp,
                    ) {
                        destinations.forEach { destination ->
                            NavigationBarItem(
                                selected = currentRoute == destination.route,
                                onClick = { navigateTo(destination) },
                                icon = { Icon(destination.icon(), contentDescription = destination.label) },
                                label = { androidx.compose.material3.Text(destination.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = SleepIndigo,
                                    selectedTextColor = SleepIndigo,
                                    indicatorColor = SleepIndigo.copy(alpha = 0.14f),
                                    unselectedIconColor = CalmNightTextSecondary,
                                    unselectedTextColor = CalmNightTextSecondary,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(modifier = Modifier.padding(padding)) {
                if (onTab && wide) {
                    NavigationRail(containerColor = CalmNightSurface.copy(alpha = 0.96f)) {
                        destinations.forEach { destination ->
                            NavigationRailItem(
                                selected = currentRoute == destination.route,
                                onClick = { navigateTo(destination) },
                                icon = { Icon(destination.icon(), contentDescription = destination.label) },
                                label = { androidx.compose.material3.Text(destination.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = SleepIndigo,
                                    selectedTextColor = SleepIndigo,
                                    indicatorColor = SleepIndigo.copy(alpha = 0.14f),
                                    unselectedIconColor = CalmNightTextSecondary,
                                    unselectedTextColor = CalmNightTextSecondary,
                                ),
                            )
                        }
                    }
                }
                // Screens are laid out for phone widths; on wide windows keep them readable and centred.
                Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    NavHost(
                        navController = navController,
                        startDestination = Destination.Dashboard.route,
                        modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH_DP.dp).fillMaxSize(),
                    ) {
                        composable(Destination.Dashboard.route) {
                            DashboardScreen(
                                onNavigateToBreathe = { navController.navigate(Destination.Breathe.route) }
                            )
                        }
                        composable(Destination.History.route) { HistoryScreen() }
                        composable(Destination.Recovery.route) { RecoveryScreen() }
                        composable(Destination.Alarm.route) { AlarmScreen() }
                        composable(Destination.Settings.route) { backStackEntry ->
                            val selectedDevice by backStackEntry.savedStateHandle
                                .getStateFlow<String?>("selected_ble_device", null)
                                .collectAsState()
                            SettingsScreen(
                                onNavigateToScan = { navController.navigate(Destination.Scan.route) },
                                selectedBleDeviceLabel = selectedDevice,
                            )
                        }
                        composable(Destination.Scan.route) {
                            ScanScreen(
                                onBack = { navController.popBackStack() },
                                onDeviceSelected = { device: ScannedDevice ->
                                    navController.previousBackStackEntry?.savedStateHandle
                                        ?.set("selected_ble_device", "${device.name ?: "Unknown"} (${device.address})")
                                    navController.popBackStack()
                                },
                            )
                        }
                        composable(Destination.Breathe.route) {
                            BreatheScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}

private const val WIDE_LAYOUT_MIN_DP = 600
private const val MAX_CONTENT_WIDTH_DP = 840

private fun Destination.icon() = when (this) {
    Destination.Dashboard -> Icons.Filled.Home
    Destination.History -> Icons.Filled.History
    Destination.Recovery -> Icons.Filled.Favorite
    Destination.Alarm -> Icons.Filled.Alarm
    Destination.Settings -> Icons.Filled.Settings
    else -> Icons.Filled.Settings // Scan is not a bottom-nav tab
}
