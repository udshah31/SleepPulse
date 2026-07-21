package com.sleeppulse.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.collectAsState
import com.sleeppulse.app.data.source.ScannedDevice
import com.sleeppulse.app.ui.dashboard.DashboardScreen
import com.sleeppulse.app.ui.history.HistoryScreen
import com.sleeppulse.app.ui.scan.ScanScreen
import com.sleeppulse.app.ui.settings.SettingsScreen

private sealed class Destination(val route: String, val label: String) {
    data object Dashboard : Destination("dashboard", "Home")
    data object History : Destination("history", "History")
    data object Settings : Destination("settings", "Settings")
    data object Scan : Destination("scan", "Scan")
}

private val destinations = listOf(Destination.Dashboard, Destination.History, Destination.Settings)

@Composable
fun SleepPulseApp() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route

            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon(), contentDescription = destination.label) },
                        label = { androidx.compose.material3.Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Dashboard.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Dashboard.route) { DashboardScreen() }
            composable(Destination.History.route) { HistoryScreen() }
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
                    onDeviceSelected = { device: ScannedDevice ->
                        navController.previousBackStackEntry?.savedStateHandle
                            ?.set("selected_ble_device", "${device.name ?: "Unknown"} (${device.address})")
                        navController.popBackStack()
                    },
                )
            }
        }
    }
}

private fun Destination.icon() = when (this) {
    Destination.Dashboard -> Icons.Filled.Home
    Destination.History -> Icons.Filled.History
    Destination.Settings -> Icons.Filled.Settings
    else -> Icons.Filled.Settings // Scan is not a bottom-nav tab
}
