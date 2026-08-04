package com.sleeppulse.app.ui.alarm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sleeppulse.app.ui.theme.CalmNightSurfaceDim
import com.sleeppulse.app.ui.theme.CalmNightTextSecondary
import com.sleeppulse.app.ui.theme.ClinicalTeal
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
        Text(
            "Smart Alarm",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.fillMaxWidth(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CalmNightSurfaceDim, RoundedCornerShape(20.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "TARGET WAKE-UP TIME",
                style = MaterialTheme.typography.labelSmall,
                color = CalmNightTextSecondary
            )

            val amPm = if (state.wakeupHour >= 12) "PM" else "AM"
            val displayHour = if (state.wakeupHour % 12 == 0) 12 else state.wakeupHour % 12
            val formattedTime = String.format(Locale.getDefault(), "%d:%02d %s", displayHour, state.wakeupMinute, amPm)

            Text(
                text = formattedTime,
                style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                color = ClinicalTeal,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            OutlinedButton(onClick = { showTimePicker = true }) {
                Text("Change time")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CalmNightSurfaceDim, RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Wake-up window",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${state.wakeWindowMinutes} min",
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    color = ClinicalTeal,
                )
            }

            Slider(
                value = state.wakeWindowMinutes.toFloat(),
                onValueChange = { viewModel.updateWakeWindow(it.toInt()) },
                valueRange = 10f..60f,
                steps = 4,
                colors = SliderDefaults.colors(
                    thumbColor = ClinicalTeal,
                    activeTrackColor = ClinicalTeal,
                ),
            )

            Text(
                text = "Your alarm will wake you during your lightest sleep phase within ${state.wakeWindowMinutes} minutes before your target wake-up time.",
                style = MaterialTheme.typography.bodySmall,
                color = CalmNightTextSecondary,
            )
        }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateWakeupTime(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) {
                    Text("Confirm", color = ClinicalTeal)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel", color = CalmNightTextSecondary)
                }
            },
            text = {
                TimePicker(
                    state = timePickerState,
                    colors = TimePickerDefaults.colors(
                        selectorColor = ClinicalTeal,
                        containerColor = CalmNightSurfaceDim,
                        periodSelectorSelectedContainerColor = ClinicalTeal.copy(alpha = 0.2f),
                        periodSelectorSelectedContentColor = ClinicalTeal,
                        timeSelectorSelectedContainerColor = ClinicalTeal.copy(alpha = 0.2f),
                        timeSelectorSelectedContentColor = ClinicalTeal,
                    ),
                )
            }
        )
    }
}
