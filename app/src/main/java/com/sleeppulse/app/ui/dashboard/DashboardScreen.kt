package com.sleeppulse.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import com.sleeppulse.app.tracking.HealthConnectManager
import com.sleeppulse.app.ui.components.SleepScoreGauge
import com.sleeppulse.app.ui.theme.CalmNightSurfaceDim
import com.sleeppulse.app.ui.theme.CalmNightTextSecondary
import com.sleeppulse.app.ui.theme.ClinicalTeal
import com.sleeppulse.app.ui.theme.RecoveryGreen
import com.sleeppulse.app.ui.theme.SleepIndigo

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateToBreathe: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { results ->
            results.forEach { (permission, granted) ->
                if (!granted) {
                    // We'll log or silently fail if they deny — noise monitoring/notifications
                    // just won't happen, no blocking UI for either.
                    android.util.Log.w("SleepPulse", "$permission denied")
                }
            }
        }
    )

    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
        onResult = { granted ->
            if (!granted.containsAll(HealthConnectManager.REQUIRED_PERMISSIONS)) {
                // Nightly summaries just won't sync to Health Connect — no blocking UI.
                android.util.Log.w("SleepPulse", "Health Connect permission not fully granted")
            }
        }
    )

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

        val healthConnectManager = HealthConnectManager(context)
        if (healthConnectManager.isAvailable() &&
            !(healthConnectManager.hasRequiredPermissions() && healthConnectManager.hasReadPermissions())
        ) {
            healthConnectPermissionLauncher.launch(HealthConnectManager.REQUESTED_PERMISSIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Tonight", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = connectionLabel(state),
                style = MaterialTheme.typography.labelMedium,
                color = CalmNightTextSecondary,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CalmNightSurfaceDim, RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SleepScoreGauge(score = state.sleepScore)
            Text(
                text = "RECOVERY",
                style = MaterialTheme.typography.labelSmall,
                color = CalmNightTextSecondary,
            )
            state.recoveryResult?.let { result ->
                Text(
                    text = result.tier.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    color = ClinicalTeal,
                )
            }
        }

        DashboardGuidanceBanner(
            recoveryResult = state.recoveryResult,
            recordedNightsCount = state.recordedNightsCount,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.latestReading?.let { reading ->
                DashboardMetricRow(
                    icon = "♥",
                    label = "Resting HR",
                    value = "${reading.heartRateBpm} bpm",
                    sparklineValues = smoothed(state.recentReadings.map { it.heartRateBpm.toFloat() }),
                    sparklineColor = SleepIndigo,
                )
                DashboardMetricRow(
                    icon = "≈",
                    label = "HRV",
                    value = "${reading.hrvMillis.toInt()} ms",
                    sparklineValues = smoothed(state.recentReadings.map { it.hrvMillis.toFloat() }),
                    sparklineColor = RecoveryGreen,
                )
            }
        }

        OutlinedButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.onIntent(DashboardIntent.ToggleSensorConnection)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isConnected) "Disconnect sensor" else "Connect sensor")
        }

        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onNavigateToBreathe()
            },
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            Text("Breathe to Sleep")
        }

        AnimatedVisibility(
            visible = state.windDownStep == null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.onIntent(DashboardIntent.BeginWindDown)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ClinicalTeal),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start wind-down")
            }
        }

        AnimatedVisibility(
            visible = state.windDownStep != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            state.windDownStep?.let { step ->
                WindDownFlow(
                    step = step,
                    onAdvance = { viewModel.onIntent(DashboardIntent.AdvanceWindDownStep) },
                    onCancel = { viewModel.onIntent(DashboardIntent.CancelWindDown) },
                )
            }
        }
    }
}

private fun connectionLabel(state: DashboardState): String = when {
    state.isLoading -> "Connecting…"
    state.isConnected -> "Sensor connected"
    else -> "Sensor disconnected"
}

/**
 * Centered moving average so per-second sensor jitter doesn't turn the metric-row
 * sparklines into a jagged EKG line — the mockup's sparklines read as a calm trend, not
 * raw noise. Window is small enough to still track a genuine trend shift.
 */
private fun smoothed(values: List<Float>, window: Int = 5): List<Float> {
    if (values.size <= window) return values
    val half = window / 2
    return values.indices.map { i ->
        val from = (i - half).coerceAtLeast(0)
        val to = (i + half).coerceAtMost(values.lastIndex)
        values.subList(from, to + 1).average().toFloat()
    }
}
