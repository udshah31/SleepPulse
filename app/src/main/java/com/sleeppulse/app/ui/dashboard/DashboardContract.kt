package com.sleeppulse.app.ui.dashboard

import com.sleeppulse.shared.model.SensorConnectionState
import com.sleeppulse.shared.model.SensorReading
import com.sleeppulse.shared.scoring.RecoveryResult
import com.sleeppulse.shared.scoring.MetricBaselineResult

/** User-triggered actions on the Dashboard screen. */
sealed class DashboardIntent {
    data object Start : DashboardIntent()
    data object ToggleSensorConnection : DashboardIntent()
    data object BeginWindDown : DashboardIntent()
    data object AdvanceWindDownStep : DashboardIntent()
    data object CancelWindDown : DashboardIntent()
    data class HealthConnectPermissionsResult(val granted: Set<String>) : DashboardIntent()
}

enum class WindDownStep { BREATHE, DIM_LIGHTS, SET_ALARM, DONE }

/** Everything the Dashboard Composable needs to render; produced only by [DashboardViewModel]. */
data class DashboardState(
    val isLoading: Boolean = true,
    val connectionState: SensorConnectionState = SensorConnectionState.Disconnected,
    /** A session is running (the tracking service is on), connected or not. */
    val isTracking: Boolean = false,
    val sleepScore: Int = 0,
    val latestReading: SensorReading? = null,
    val recentReadings: List<SensorReading> = emptyList(),
    val windDownStep: WindDownStep? = null,
    val recoveryResult: RecoveryResult? = null,
    val recordedNightsCount: Int = 0,
    val metricBaseline: MetricBaselineResult? = null,
) {
    val isConnected: Boolean
        get() = connectionState is SensorConnectionState.Connected
}
