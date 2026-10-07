package com.sleeppulse.app.ui.dashboard

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import com.sleeppulse.app.ui.components.CalmNightCard
import com.sleeppulse.app.ui.components.CalmNightStatusPill
import com.sleeppulse.app.ui.components.LiveMetricChart
import com.sleeppulse.app.ui.components.SleepScoreGauge
import com.sleeppulse.app.ui.theme.CalmNightBackground
import com.sleeppulse.app.ui.theme.CalmNightSurfaceDim
import com.sleeppulse.app.ui.theme.CalmNightTextSecondary
import com.sleeppulse.app.ui.theme.RecoveryGreen
import com.sleeppulse.app.ui.theme.SleepIndigo

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateToBreathe: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val haptic = LocalHapticFeedback.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { results ->
            results.forEach { (permission, granted) ->
                if (!granted) {
                    android.util.Log.w("SleepPulse", "$permission denied")
                }
            }
        },
    )

    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
        onResult = { granted -> viewModel.onIntent(DashboardIntent.HealthConnectPermissionsResult(granted)) },
    )

    LaunchedEffect(Unit) {
        viewModel.healthConnectPermissionRequests.collect { healthConnectPermissionLauncher.launch(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.onIntent(DashboardIntent.Start)
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.ACTIVITY_RECOGNITION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(text = "Tonight", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "A calmer way to read your recovery",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CalmNightTextSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            CalmNightStatusPill(
                text = connectionLabel(state),
                active = state.isConnected,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            SleepIndigo.copy(alpha = 0.35f),
                            CalmNightSurfaceDim,
                        ),
                    ),
                )
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SleepScoreGauge(score = state.sleepScore, size = 176.dp)
                Column(
                    modifier = Modifier.padding(start = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "SLEEP SCORE",
                        style = MaterialTheme.typography.labelMedium,
                        color = CalmNightTextSecondary,
                    )
                    Text(
                        text = scoreHeadline(state),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = scoreDescription(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = CalmNightTextSecondary,
                    )
                }
            }
        }

        DashboardGuidanceBanner(
            recoveryResult = state.recoveryResult,
            recordedNightsCount = state.recordedNightsCount,
        )

        state.latestReading?.let { reading ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DashboardMetricCard(
                    label = "Resting HR",
                    value = "${reading.heartRateBpm}",
                    unit = "bpm",
                    values = smoothed(state.recentReadings.map { it.heartRateBpm.toFloat() }),
                    color = SleepIndigo,
                    modifier = Modifier.weight(1f),
                )
                DashboardMetricCard(
                    label = "HRV",
                    value = reading.hrvMillis?.let { "${it.toInt()}" } ?: "—",
                    unit = if (reading.hrvMillis != null) "ms" else "",
                    values = smoothed(state.recentReadings.mapNotNull { it.hrvMillis?.toFloat() }),
                    color = RecoveryGreen,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        CalmNightCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.onIntent(DashboardIntent.ToggleSensorConnection)
                    },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, SleepIndigo.copy(alpha = 0.55f)),
                ) {
                    Text(if (state.isTracking) "Disconnect" else "Connect")
                }
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToBreathe()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SleepIndigo,
                        contentColor = CalmNightBackground,
                    ),
                ) {
                    Text("Breathe")
                }
            }
        }

        AnimatedVisibility(
            visible = state.windDownStep == null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.onIntent(DashboardIntent.BeginWindDown)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SleepIndigo,
                    contentColor = CalmNightBackground,
                ),
            ) {
                Text("Start wind-down")
            }
        }

        AnimatedVisibility(
            visible = state.windDownStep != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            state.windDownStep?.let { step ->
                CalmNightCard(modifier = Modifier.fillMaxWidth(), padding = 4.dp) {
                    WindDownFlow(
                        step = step,
                        onAdvance = { viewModel.onIntent(DashboardIntent.AdvanceWindDownStep) },
                        onCancel = { viewModel.onIntent(DashboardIntent.CancelWindDown) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardMetricCard(
    label: String,
    value: String,
    unit: String,
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    CalmNightCard(modifier = modifier, padding = 14.dp, shape = RoundedCornerShape(20.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = CalmNightTextSecondary)
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            Text(text = value, style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"))
            Text(
                text = " $unit",
                style = MaterialTheme.typography.labelMedium,
                color = CalmNightTextSecondary,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        if (values.size >= 2) {
            LiveMetricChart(
                values = values,
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                height = 28.dp,
            )
        }
    }
}

private fun scoreHeadline(state: DashboardState): String = state.recoveryResult?.tier?.name
    ?.lowercase()
    ?.replaceFirstChar { it.uppercase() }
    ?: "Baseline building"

private fun scoreDescription(state: DashboardState): String = if (state.recoveryResult == null) {
    "Keep tracking to unlock your personal recovery signal."
} else {
    state.recoveryResult.guidance
}

private fun connectionLabel(state: DashboardState): String = when {
    state.isLoading -> "Starting"
    state.isConnected -> "Live"
    state.isTracking -> "Connecting"
    else -> "Offline"
}

private fun smoothed(values: List<Float>, window: Int = 5): List<Float> {
    if (values.size <= window) return values
    val half = window / 2
    return values.indices.map { i ->
        val from = (i - half).coerceAtLeast(0)
        val to = (i + half).coerceAtMost(values.lastIndex)
        values.subList(from, to + 1).average().toFloat()
    }
}
