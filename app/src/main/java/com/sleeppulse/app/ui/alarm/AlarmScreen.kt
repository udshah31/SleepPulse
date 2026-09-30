package com.sleeppulse.app.ui.alarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sleeppulse.app.ui.components.CalmNightCard
import com.sleeppulse.app.ui.components.CalmNightSectionLabel
import com.sleeppulse.app.ui.theme.CalmNightSurfaceDim
import com.sleeppulse.app.ui.theme.CalmNightTextSecondary
import com.sleeppulse.app.ui.theme.SleepIndigo
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmScreen(
    viewModel: AlarmViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    var showTimePicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(
        initialHour = state.wakeupHour,
        initialMinute = state.wakeupMinute,
        is24Hour = false,
    )

    val amPm = if (state.wakeupHour >= 12) "PM" else "AM"
    val displayHour = if (state.wakeupHour % 12 == 0) 12 else state.wakeupHour % 12
    val formattedTime = String.format(
        Locale.getDefault(),
        "%d:%02d %s",
        displayHour,
        state.wakeupMinute,
        amPm,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Smart Alarm", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Wake gently during your lightest sleep phase.",
            style = MaterialTheme.typography.bodyMedium,
            color = CalmNightTextSecondary,
        )

        CalmNightCard(modifier = Modifier.fillMaxWidth(), padding = 22.dp) {
            CalmNightSectionLabel(text = "Target wake-up time")
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                color = SleepIndigo,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            OutlinedButton(onClick = { showTimePicker = true }) {
                Text("Change time")
            }
        }

        CalmNightCard(modifier = Modifier.fillMaxWidth(), padding = 20.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    CalmNightSectionLabel(text = "Wake-up window")
                    Text(
                        text = "${state.wakeWindowMinutes} minutes before target",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            Text(
                text = "SleepPulse looks for a lighter phase inside this window.",
                style = MaterialTheme.typography.bodySmall,
                color = CalmNightTextSecondary,
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
            )
            FlowRow(
                maxItemsInEachRow = 3,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(10, 20, 30, 40, 50, 60).forEach { minutes ->
                    FilterChip(
                        selected = state.wakeWindowMinutes == minutes,
                        onClick = { viewModel.updateWakeWindow(minutes) },
                        label = { Text("${minutes}m") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SleepIndigo.copy(alpha = 0.18f),
                            selectedLabelColor = SleepIndigo,
                            containerColor = CalmNightSurfaceDim,
                        ),
                    )
                }
            }
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
                    Text("Confirm", color = SleepIndigo)
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
                        selectorColor = SleepIndigo,
                        containerColor = CalmNightSurfaceDim,
                        periodSelectorSelectedContainerColor = SleepIndigo.copy(alpha = 0.2f),
                        periodSelectorSelectedContentColor = SleepIndigo,
                        timeSelectorSelectedContainerColor = SleepIndigo.copy(alpha = 0.2f),
                        timeSelectorSelectedContentColor = SleepIndigo,
                    ),
                )
            },
        )
    }
}
