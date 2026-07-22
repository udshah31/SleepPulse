package com.sleeppulse.app.ui.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleeppulse.app.data.repository.SleepRepository
import com.sleeppulse.app.ui.dashboard.HrvTrendCalculator
import com.sleeppulse.app.ui.dashboard.HrvTrendResult
import com.sleeppulse.app.ui.dashboard.RecoveryScoreCalculator
import com.sleeppulse.app.ui.dashboard.RestingHeartRateTrendCalculator
import com.sleeppulse.app.ui.dashboard.RestingHeartRateTrendResult
import com.sleeppulse.app.ui.dashboard.TrendDirection
import com.sleeppulse.app.ui.history.SleepConsistencyCalculator
import com.sleeppulse.app.ui.history.SleepDebtCalculator
import com.sleeppulse.app.ui.history.SleepVariabilityCalculator
import com.sleeppulse.app.ui.history.SleepVariabilityResult
import com.sleeppulse.app.ui.history.TagCorrelation
import com.sleeppulse.app.ui.history.TagCorrelationCalculator
import com.sleeppulse.app.ui.history.VariabilityLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MAX_RECORDED_NIGHTS_DISPLAY = 4

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
                val hrvTrend = HrvTrendCalculator.analyze(nights)
                val rhrTrend = RestingHeartRateTrendCalculator.analyze(nights)
                val variability = SleepVariabilityCalculator.analyze(nights)
                val tagCorrelations = TagCorrelationCalculator.analyze(nights)
                val readiness = RecoveryReadinessCalculator.compute(
                    recoveryScore = recoveryResult?.score,
                    hrvTrend = hrvTrend,
                    rhrTrend = rhrTrend,
                    sleepDebt = debt,
                )

                val advice = generateAdvice(
                    debtMinutes = debt?.deficitMinutes ?: 0,
                    consistency = consistencyScore,
                    recoveryScore = recoveryResult?.score ?: 0,
                    hrvTrend = hrvTrend,
                    rhrTrend = rhrTrend,
                    variability = variability,
                    tagCorrelations = tagCorrelations,
                )

                _state.update {
                    it.copy(
                        isLoading = false,
                        recoveryResult = recoveryResult,
                        sleepDebt = debt,
                        consistencyScore = consistencyScore,
                        latestNight = latest,
                        personalizedAdvice = advice,
                        recordedNightsCount = nights.size.coerceAtMost(MAX_RECORDED_NIGHTS_DISPLAY),
                        hrvTrend = hrvTrend,
                        restingHeartRateTrend = rhrTrend,
                        variability = variability,
                        tagCorrelations = tagCorrelations,
                        readiness = readiness,
                    )
                }
            }
        }
    }

    private fun generateAdvice(
        debtMinutes: Int,
        consistency: Int,
        recoveryScore: Int,
        hrvTrend: HrvTrendResult?,
        rhrTrend: RestingHeartRateTrendResult?,
        variability: SleepVariabilityResult?,
        tagCorrelations: List<TagCorrelation>,
    ): String {
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

        if (hrvTrend?.direction == TrendDirection.FALLING) {
            parts.add("Your HRV has been trending down over the past two weeks — worth watching for early signs of overtraining or illness.")
        }

        if (rhrTrend?.direction == TrendDirection.RISING) {
            parts.add("Your resting heart rate has crept up over the past two weeks, even on nights that looked fine individually.")
        }

        if (variability?.level == VariabilityLevel.HIGH) {
            parts.add("Your recovery signals have been erratic night to night this week, not just low on average.")
        }

        tagCorrelations.firstOrNull { it.scoreDelta < -5 }?.let { worst ->
            parts.add("Nights tagged \"${worst.tag}\" average ${worst.avgScoreWithTag.toInt()} vs ${worst.avgScoreWithoutTag.toInt()} otherwise — worth cutting back.")
        }

        return parts.joinToString(" ")
    }
}
