package com.sleeppulse.app.ui.dashboard

import com.sleeppulse.app.data.model.SensorConnectionState
import com.sleeppulse.app.data.model.SensorReading

/** User-triggered actions on the Dashboard screen. */
sealed class DashboardIntent {
    data object Start : DashboardIntent()
    data object ToggleSensorConnection : DashboardIntent()
    data object BeginWindDown : DashboardIntent()
    data object AdvanceWindDownStep : DashboardIntent()
    data object CancelWindDown : DashboardIntent()
}

enum class WindDownStep { BREATHE, DIM_LIGHTS, SET_ALARM, DONE }

/** Everything the Dashboard Composable needs to render; produced only by [DashboardViewModel]. */
data class DashboardState(
    val isLoading: Boolean = true,
    val connectionState: SensorConnectionState = SensorConnectionState.Disconnected,
    val sleepScore: Int = 0,
    val latestReading: SensorReading? = null,
    val recentReadings: List<SensorReading> = emptyList(),
    val windDownStep: WindDownStep? = null,
    val recoveryResult: RecoveryResult? = null,
    val recordedNightsCount: Int = 0,
) {
    val isConnected: Boolean
        get() = connectionState is SensorConnectionState.Connected
}
