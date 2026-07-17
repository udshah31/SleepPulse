package com.sleeppulse.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleeppulse.app.data.repository.SleepRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: SleepRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    fun onIntent(intent: HistoryIntent) {
        when (intent) {
            HistoryIntent.Load -> load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            repository.recentNights().collect { nights ->
                // recentNights() is already newest-first; pair each night with the one
                // that follows it chronologically (i.e. the previous element) for trend.
                val withTrend = nights.mapIndexed { index, night ->
                    val previous = nights.getOrNull(index + 1)
                    NightWithTrend(night, night.trendAgainst(previous))
                }
                _state.update { it.copy(nights = withTrend, isLoading = false) }
            }
        }
    }
}
