package com.sleeppulse.app.ui.alarm

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen(
    viewModel: AlarmViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    var showTimePicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(
        initialHour = state.wakeupHour,
        initialMinute = state.wakeupMinute,
        is24Hour = false
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Smart Alarm", style = MaterialTheme.typography.headlineMedium)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Target Wake-Up Time",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                val amPm = if (state.wakeupHour >= 12) "PM" else "AM"
                val displayHour = if (state.wakeupHour % 12 == 0) 12 else state.wakeupHour % 12
                val formattedTime = String.format(Locale.getDefault(), "%d:%02d %s", displayHour, state.wakeupMinute, amPm)
                
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                OutlinedButton(onClick = { showTimePicker = true }) {
                    Text("Change Time")
                }
            }
        }

        Text(
            text = "Wake-Up Window",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Start)
        )

        Text(
            text = "${state.wakeWindowMinutes} minutes",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.align(Alignment.Start)
        )

        Slider(
            value = state.wakeWindowMinutes.toFloat(),
            onValueChange = { viewModel.updateWakeWindow(it.toInt()) },
            valueRange = 10f..60f,
            steps = 4
        )

        Text(
            text = "Your alarm will wake you during your lightest sleep phase within ${state.wakeWindowMinutes} minutes before your target wake-up time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateWakeupTime(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }
}
