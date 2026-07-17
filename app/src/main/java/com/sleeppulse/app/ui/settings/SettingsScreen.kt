package com.sleeppulse.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

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
