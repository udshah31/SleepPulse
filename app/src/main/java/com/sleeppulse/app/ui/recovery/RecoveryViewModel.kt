package com.sleeppulse.app.ui.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleeppulse.app.data.repository.SleepRepository
import com.sleeppulse.app.ui.dashboard.RecoveryScoreCalculator
import com.sleeppulse.app.ui.history.SleepConsistencyCalculator
import com.sleeppulse.app.ui.history.SleepDebtCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecoveryViewModel @Inject constructor(
    private val repository: SleepRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RecoveryState())
    val state: StateFlow<RecoveryState> = _state.asStateFlow()

    fun onIntent(intent: RecoveryIntent) {
        when (intent) {
            RecoveryIntent.Load -> load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            repository.recentNights().collect { nights ->
                val latest = nights.firstOrNull()
                val recoveryResult = if (latest != null) {
                    RecoveryScoreCalculator.score(latest, nights.drop(1))
                } else null
                
                val debt = SleepDebtCalculator.calculate(nights)
                val consistencyScore = SleepConsistencyCalculator.calculateScore(nights) ?: 0

                val advice = generateAdvice(debt?.deficitMinutes ?: 0, consistencyScore, recoveryResult?.score ?: 0)

                _state.update {
                    it.copy(
                        isLoading = false,
                        recoveryResult = recoveryResult,
                        sleepDebt = debt,
                        consistencyScore = consistencyScore,
                        latestNight = latest,
                        personalizedAdvice = advice
                    )
                }
            }
        }
    }

    private fun generateAdvice(debtMinutes: Int, consistency: Int, recoveryScore: Int): String {
        val parts = mutableListOf<String>()
        
        if (debtMinutes > 60) {
            val hours = debtMinutes / 60
            parts.add("You have $hours hour(s) of sleep debt. Target an extra 30m of sleep tonight.")
        } else {
            parts.add("Your sleep debt is low. Keep up the good work!")
        }

        if (consistency < 70) {
            parts.add("Your sleep schedule is irregular. Try going to bed at the same time tonight.")
        }

        if (recoveryScore > 80) {
            parts.add("You are well recovered, a great day for a workout.")
        } else if (recoveryScore in 1..50) {
            parts.add("Your body is stressed. Prioritize rest today.")
        }

        return parts.joinToString(" ")
    }
}
