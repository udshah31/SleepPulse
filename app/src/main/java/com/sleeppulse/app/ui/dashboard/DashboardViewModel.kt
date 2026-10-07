package com.sleeppulse.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleeppulse.shared.model.NightlySummary
import com.sleeppulse.shared.model.SensorConnectionState
import com.sleeppulse.shared.model.SensorReading
import com.sleeppulse.shared.repository.SleepRepository
import com.sleeppulse.shared.scoring.RecoveryResult
import com.sleeppulse.shared.scoring.RecoveryScoreCalculator
import com.sleeppulse.shared.scoring.SleepScoreCalculator
import com.sleeppulse.shared.scoring.MetricBaselineCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.content.Context
import android.content.Intent
import com.sleeppulse.app.services.SleepTrackingService
import com.sleeppulse.app.tracking.HealthConnectManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private const val MAX_CHART_POINTS = 40
private const val MAX_RECORDED_NIGHTS_DISPLAY = 4

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SleepRepository,
    private val healthConnect: HealthConnectManager,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    // One-shot: the permission prompt needs an ActivityResult launcher, which only the screen
    // has, so the ViewModel decides *whether* to ask and the screen just launches it.
    private val _healthConnectPermissionRequests = Channel<Set<String>>(Channel.BUFFERED)
    val healthConnectPermissionRequests: Flow<Set<String>> = _healthConnectPermissionRequests.receiveAsFlow()
    private var askedHealthConnect = false
    private var connectionJob: Job? = null
    private var trackingJob: Job? = null
    private var readingsJob: Job? = null
    private var nightsJob: Job? = null

    fun onIntent(intent: DashboardIntent) {
        when (intent) {
            DashboardIntent.Start -> start()
            DashboardIntent.ToggleSensorConnection -> toggleConnection()
            DashboardIntent.BeginWindDown -> _state.update { it.copy(windDownStep = WindDownStep.BREATHE) }
            DashboardIntent.AdvanceWindDownStep -> advanceWindDown()
            DashboardIntent.CancelWindDown -> _state.update { it.copy(windDownStep = null) }
            is DashboardIntent.HealthConnectPermissionsResult -> onHealthConnectResult(intent.granted)
        }
    }

    /** Asks at most once per ViewModel, not every time Home re-enters composition. */
    private fun requestHealthConnectPermissionsIfNeeded() {
        if (askedHealthConnect) return
        askedHealthConnect = true
        viewModelScope.launch {
            if (healthConnect.isAvailable() &&
                !(healthConnect.hasRequiredPermissions() && healthConnect.hasReadPermissions())
            ) {
                _healthConnectPermissionRequests.send(HealthConnectManager.REQUESTED_PERMISSIONS)
            }
        }
    }

    private fun onHealthConnectResult(granted: Set<String>) {
        if (!granted.containsAll(HealthConnectManager.REQUIRED_PERMISSIONS)) {
            // Nightly summaries just won't sync to Health Connect — no blocking UI.
            android.util.Log.w("SleepPulse", "Health Connect permission not fully granted")
        }
    }

    private fun start() {
        requestHealthConnectPermissionsIfNeeded()
        // Navigation can recreate the Composable while retaining this ViewModel. Keep each
        // long-lived collector idempotent so returning to the Dashboard does not multiply work.
        if (connectionJob?.isActive != true) {
            connectionJob = viewModelScope.launch {
                repository.connectionState.collect { connection ->
                    _state.update { current ->
                        val updated = current.copy(connectionState = connection, isLoading = false)
                        // A new session must not inherit the last one's chart points or live score
                        // (both are built from recentReadings); after a disconnect they stay visible.
                        val newSession = connection is SensorConnectionState.Connected &&
                            current.connectionState !is SensorConnectionState.Connected
                        if (newSession) updated.copy(recentReadings = emptyList(), latestReading = null, sleepScore = 0) else updated
                    }
                }
            }
        }
        if (trackingJob?.isActive != true) {
            trackingJob = viewModelScope.launch {
                repository.isTracking.collect { tracking -> _state.update { it.copy(isTracking = tracking) } }
            }
        }
        if (readingsJob?.isActive != true) {
            readingsJob = viewModelScope.launch {
                repository.liveReadings().collect { reading ->
                    _state.update { current ->
                        val updatedHistory = (current.recentReadings + reading).takeLast(MAX_CHART_POINTS)
                        current.copy(
                            latestReading = reading,
                            recentReadings = updatedHistory,
                            sleepScore = SleepScoreCalculator.score(updatedHistory),
                            isLoading = false,
                        )
                    }
                }
            }
        }
        if (nightsJob?.isActive != true) {
            nightsJob = viewModelScope.launch {
                repository.recentNights().collect { nights ->
                    _state.update {
                        it.copy(
                            recoveryResult = computeRecovery(nights),
                            recordedNightsCount = nights.size.coerceAtMost(MAX_RECORDED_NIGHTS_DISPLAY),
                            metricBaseline = MetricBaselineCalculator.compute(nights),
                        )
                    }
                }
            }
        }
    }

    private fun computeRecovery(nights: List<NightlySummary>): RecoveryResult? =
        RecoveryScoreCalculator.scoreLatest(nights)

    private fun toggleConnection() {
        viewModelScope.launch {
            // Keyed on the session, not the sensor: a BLE session whose strap never connects
            // must still be stoppable.
            if (_state.value.isTracking) {
                val stopIntent = Intent(context, SleepTrackingService::class.java).apply {
                    action = SleepTrackingService.ACTION_STOP_TRACKING
                }
                context.startService(stopIntent)
            } else {
                val startIntent = Intent(context, SleepTrackingService::class.java)
                context.startForegroundService(startIntent)
            }
        }
    }

    private fun advanceWindDown() {
        val next = when (_state.value.windDownStep) {
            WindDownStep.BREATHE -> WindDownStep.DIM_LIGHTS
            WindDownStep.DIM_LIGHTS -> WindDownStep.SET_ALARM
            WindDownStep.SET_ALARM -> WindDownStep.DONE
            WindDownStep.DONE, null -> null
        }
        _state.update { it.copy(windDownStep = next) }
    }
}
