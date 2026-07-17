package com.sleeppulse.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sleeppulse.app.ui.components.LiveMetricChart
import com.sleeppulse.app.ui.components.SleepScoreGauge
import com.sleeppulse.app.ui.theme.RecoveryGreen
import com.sleeppulse.app.ui.theme.SleepIndigo

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(DashboardIntent.Start)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(text = "Tonight", style = MaterialTheme.typography.headlineMedium)

        SleepScoreGauge(score = state.sleepScore)

        Text(
            text = connectionLabel(state),
            style = MaterialTheme.typography.bodyMedium,
        )

        state.latestReading?.let { reading ->
            Text(text = "Heart rate ${reading.heartRateBpm} bpm  ·  HRV ${reading.hrvMillis.toInt()} ms")
        }

        Text(text = "Heart rate", style = MaterialTheme.typography.titleSmall)
        LiveMetricChart(
            values = state.recentReadings.map { it.heartRateBpm.toFloat() },
            color = SleepIndigo,
        )

        Text(text = "HRV", style = MaterialTheme.typography.titleSmall)
        LiveMetricChart(
            values = state.recentReadings.map { it.hrvMillis.toFloat() },
            color = RecoveryGreen,
        )

        OutlinedButton(onClick = { viewModel.onIntent(DashboardIntent.ToggleSensorConnection) }) {
            Text(if (state.isConnected) "Disconnect sensor" else "Connect sensor")
        }

        if (state.windDownStep == null) {
            Button(onClick = { viewModel.onIntent(DashboardIntent.BeginWindDown) }) {
                Text("Start wind-down")
            }
        } else {
            WindDownFlow(
                step = state.windDownStep!!,
                onAdvance = { viewModel.onIntent(DashboardIntent.AdvanceWindDownStep) },
                onCancel = { viewModel.onIntent(DashboardIntent.CancelWindDown) },
            )
        }
    }
}

private fun connectionLabel(state: DashboardState): String = when {
    state.isLoading -> "Connecting…"
    state.isConnected -> "Sensor connected"
    else -> "Sensor disconnected"
}
