package com.sleeppulse.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sleeppulse.app.ui.theme.CalmNightSurface
import com.sleeppulse.app.ui.theme.CalmNightTextSecondary

@Composable
fun SettingsScreen(
    onNavigateToScan: () -> Unit = {},
    selectedBleDeviceLabel: String? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(selectedBleDeviceLabel) {
        selectedBleDeviceLabel?.let {
            viewModel.onIntent(SettingsIntent.SetSelectedBleDevice(it))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)

        SettingsSection(title = "Data source") {
            DataSourceMode.entries.forEach { mode ->
                SettingsOptionRow(
                    label = mode.label(),
                    selected = state.dataSourceMode == mode,
                    onClick = { viewModel.onIntent(SettingsIntent.SetDataSource(mode)) },
                )
            }
            if (state.dataSourceMode == DataSourceMode.BLE) {
                Button(onClick = onNavigateToScan, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Scan for device")
                }
                state.selectedBleDeviceLabel?.let { label ->
                    Text(text = "Selected: $label", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        SettingsSection(title = "Temperature unit") {
            TemperatureUnit.entries.forEach { unit ->
                SettingsOptionRow(
                    label = unit.label(),
                    selected = state.temperatureUnit == unit,
                    onClick = { viewModel.onIntent(SettingsIntent.SetTemperatureUnit(unit)) },
                )
            }
        }

        SettingsSection(title = "Target bedtime") {
            val bedtimeOptions = listOf(
                Pair(21, 30) to "9:30 PM",
                Pair(22, 30) to "10:30 PM",
                Pair(23, 30) to "11:30 PM",
            )
            bedtimeOptions.forEach { (time, label) ->
                SettingsOptionRow(
                    label = label,
                    selected = state.targetBedtimeHour == time.first && state.targetBedtimeMinute == time.second,
                    onClick = { viewModel.onIntent(SettingsIntent.SetTargetBedtime(time.first, time.second)) },
                )
            }
        }

        SettingsSection(title = "Smart wake-up alarm") {
            val wakeupOptions = listOf(
                Triple(6, 30, 30) to "6:30 AM (30m window)",
                Triple(7, 0, 30) to "7:00 AM (30m window)",
                Triple(7, 30, 30) to "7:30 AM (30m window)",
            )
            wakeupOptions.forEach { (time, label) ->
                SettingsOptionRow(
                    label = label,
                    selected = state.targetWakeupHour == time.first && state.targetWakeupMinute == time.second,
                    onClick = {
                        viewModel.onIntent(SettingsIntent.SetTargetWakeup(time.first, time.second, time.third))
                    },
                )
            }
        }

        SettingsSection(title = "Display") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "AMOLED Dark Mode", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Pure black background to save battery",
                        style = MaterialTheme.typography.bodySmall,
                        color = CalmNightTextSecondary,
                    )
                }
                Switch(
                    checked = state.amoledBlack,
                    onCheckedChange = { viewModel.onIntent(SettingsIntent.SetAmoledBlack(it)) },
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = CalmNightTextSecondary,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CalmNightSurface),
            content = content,
        )
    }
}

@Composable
private fun SettingsOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    badge: String? = null,
) {
    val rowBackground = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .background(rowBackground)
            .padding(vertical = 12.dp, horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else CalmNightTextSecondary,
            )
            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (selected) {
            Text(
                text = "✓",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private fun DataSourceMode.label(): String = when (this) {
    DataSourceMode.SIMULATED -> "Simulated"
    DataSourceMode.BLE -> "BLE sensor"
}

private fun TemperatureUnit.label(): String = when (this) {
    TemperatureUnit.CELSIUS -> "Celsius"
    TemperatureUnit.FAHRENHEIT -> "Fahrenheit"
}
