package com.sleeppulse.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleeppulse.app.data.export.DataExporter
import com.sleeppulse.shared.repository.SleepRepository
import com.sleeppulse.shared.analytics.SleepDebtCalculator
import com.sleeppulse.shared.analytics.SleepConsistencyCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: SleepRepository,
    private val dataExporter: DataExporter,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    private var nightsJob: Job? = null

    fun onIntent(intent: HistoryIntent) {
        when (intent) {
            HistoryIntent.Load -> load()
            is HistoryIntent.SelectTab -> {
                _state.update { it.copy(selectedTab = intent.tab) }
            }
            HistoryIntent.ExportData -> exportData()
        }
    }

    private fun load() {
        if (nightsJob?.isActive == true) return

        nightsJob = viewModelScope.launch {
            repository.recentNights().collect { nights ->
                val nightsWithTrend = nights.mapIndexed { index, summary ->
                    val previous = nights.getOrNull(index + 1)
                    NightWithTrend(summary, summary.trendAgainst(previous))
                }
                
                val debt = SleepDebtCalculator.calculate(nights)
                
                val consistencyScore = SleepConsistencyCalculator.calculateScore(nights)

                _state.update {
                    it.copy(
                        isLoading = false,
                        nights = nightsWithTrend,
                        sleepDebt = debt,
                        consistencyScore = consistencyScore
                    )
                }
            }
        }
    }

    private fun exportData() {
        viewModelScope.launch {
            val file = dataExporter.exportToCsv(_state.value.nights.map { it.summary })
            if (file != null) {
                // In a real app we might post an effect to show a Toast or trigger ACTION_SEND
                // For now, we'll just log it.
                android.util.Log.i("SleepPulse", "Exported CSV to: ${file.absolutePath}")
            } else {
                android.util.Log.e("SleepPulse", "Failed to export CSV")
            }
        }
    }
}
