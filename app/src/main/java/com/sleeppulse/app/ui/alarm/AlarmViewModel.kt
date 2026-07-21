package com.sleeppulse.app.ui.alarm

import androidx.lifecycle.ViewModel
import com.sleeppulse.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import androidx.lifecycle.viewModelScope

data class AlarmState(
    val wakeupHour: Int = 7,
    val wakeupMinute: Int = 0,
    val wakeWindowMinutes: Int = 30
)

@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AlarmState())
    val state: StateFlow<AlarmState> = _state.asStateFlow()

    init {
        combine(
            settingsRepository.targetWakeupHour,
            settingsRepository.targetWakeupMinute,
            settingsRepository.wakeWindowMinutes
        ) { h, m, w ->
            _state.value = AlarmState(h, m, w)
        }.launchIn(viewModelScope)
    }

    fun updateWakeupTime(hour: Int, minute: Int) {
        // Read the unchanged field straight from the repository's own StateFlow, not
        // _state.value — the latter is only updated asynchronously by the combine
        // collector above, so it can be stale for a beat after a rapid prior update
        // (e.g. quick slider drags), silently reverting the field being read here.
        settingsRepository.setTargetWakeup(hour, minute, settingsRepository.wakeWindowMinutes.value)
    }

    fun updateWakeWindow(minutes: Int) {
        settingsRepository.setTargetWakeup(
            settingsRepository.targetWakeupHour.value,
            settingsRepository.targetWakeupMinute.value,
            minutes,
        )
    }
}
