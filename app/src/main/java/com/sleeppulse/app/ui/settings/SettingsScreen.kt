package com.sleeppulse.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

import androidx.compose.material3.Button
import androidx.compose.runtime.LaunchedEffect

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
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(text = "Data source", style = MaterialTheme.typography.titleMedium)
        DataSourceMode.entries.forEach { mode ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.dataSourceMode == mode,
                    onClick = { viewModel.onIntent(SettingsIntent.SetDataSource(mode)) },
                )
                Text(text = mode.label())
            }
        }
        if (state.dataSourceMode == DataSourceMode.BLE) {
            Button(onClick = onNavigateToScan) {
                Text("Scan for device")
            }
            state.selectedBleDeviceLabel?.let { label ->
                Text(text = "Selected: $label")
            }
        }

        Text(text = "Temperature unit", style = MaterialTheme.typography.titleMedium)
        TemperatureUnit.entries.forEach { unit ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.temperatureUnit == unit,
                    onClick = { viewModel.onIntent(SettingsIntent.SetTemperatureUnit(unit)) },
                )
                Text(text = unit.label())
            }
        }

        Text(text = "Target bedtime", style = MaterialTheme.typography.titleMedium)
        val bedtimeOptions = listOf(
            Pair(21, 30) to "9:30 PM",
            Pair(22, 30) to "10:30 PM",
            Pair(23, 30) to "11:30 PM"
        )
        bedtimeOptions.forEach { (time, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.targetBedtimeHour == time.first && state.targetBedtimeMinute == time.second,
                    onClick = { viewModel.onIntent(SettingsIntent.SetTargetBedtime(time.first, time.second)) },
                )
                Text(text = label)
            }
        }

        Text(text = "Smart wake-up alarm", style = MaterialTheme.typography.titleMedium)
        val wakeupOptions = listOf(
            Triple(6, 30, 30) to "6:30 AM (30m window)",
            Triple(7, 0, 30) to "7:00 AM (30m window)",
            Triple(7, 30, 30) to "7:30 AM (30m window)"
        )
        wakeupOptions.forEach { (time, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.targetWakeupHour == time.first && state.targetWakeupMinute == time.second,
                    onClick = { viewModel.onIntent(SettingsIntent.SetTargetWakeup(time.first, time.second, time.third)) },
                )
                Text(text = label)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "AMOLED Dark Mode", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Pure black background to save battery",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.amoledBlack,
                onCheckedChange = { viewModel.onIntent(SettingsIntent.SetAmoledBlack(it)) }
            )
        }
    }
}

private fun DataSourceMode.label(): String = when (this) {
    DataSourceMode.SIMULATED -> "Simulated"
    DataSourceMode.BLE -> "BLE sensor (coming soon)"
}

private fun TemperatureUnit.label(): String = when (this) {
    TemperatureUnit.CELSIUS -> "Celsius"
    TemperatureUnit.FAHRENHEIT -> "Fahrenheit"
}
