package com.sleeppulse.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleeppulse.app.data.NightSummaryBuilder
import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.data.repository.SleepRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private const val MAX_CHART_POINTS = 40
private const val MAX_RECORDED_NIGHTS_DISPLAY = 4

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: SleepRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val sessionReadings = mutableListOf<SensorReading>()

    fun onIntent(intent: DashboardIntent) {
        when (intent) {
            DashboardIntent.Start -> start()
            DashboardIntent.ToggleSensorConnection -> toggleConnection()
            DashboardIntent.BeginWindDown -> _state.update { it.copy(windDownStep = WindDownStep.BREATHE) }
            DashboardIntent.AdvanceWindDownStep -> advanceWindDown()
            DashboardIntent.CancelWindDown -> _state.update { it.copy(windDownStep = null) }
        }
    }

    private fun start() {
        viewModelScope.launch {
            repository.connectionState.collect { connection ->
                _state.update { it.copy(connectionState = connection, isLoading = false) }
            }
        }
        viewModelScope.launch {
            repository.connectSensor()
        }
        viewModelScope.launch {
            repository.liveReadings().collect { reading ->
                sessionReadings.add(reading)
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
        viewModelScope.launch {
            repository.recentNights().collect { nights ->
                _state.update {
                    it.copy(
                        recoveryResult = computeRecovery(nights),
                        recordedNightsCount = nights.size.coerceAtMost(MAX_RECORDED_NIGHTS_DISPLAY),
                    )
                }
            }
        }
    }

    private fun computeRecovery(nights: List<NightlySummary>): RecoveryResult? {
        val lastNight = nights.firstOrNull() ?: return null
        val baseline = nights.drop(1).take(7)
        return RecoveryScoreCalculator.score(lastNight, baseline)
    }

    private fun toggleConnection() {
        viewModelScope.launch {
            if (_state.value.isConnected) {
                if (sessionReadings.isNotEmpty()) {
                    repository.recordNightlySummary(
                        NightSummaryBuilder.build(sessionReadings.toList(), LocalDate.now())
                    )
                    sessionReadings.clear()
                }
                repository.disconnectSensor()
            } else {
                repository.connectSensor()
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
