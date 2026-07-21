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
        settingsRepository.setTargetWakeup(hour, minute, _state.value.wakeWindowMinutes)
    }

    fun updateWakeWindow(minutes: Int) {
        settingsRepository.setTargetWakeup(_state.value.wakeupHour, _state.value.wakeupMinute, minutes)
    }
}
