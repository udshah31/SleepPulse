package com.sleeppulse.shared.analytics

import com.sleeppulse.shared.model.NightlySummary

enum class DebtLevel { CAUGHT_UP, MILD, MODERATE, SEVERE }

data class SleepDebt(
    val deficitMinutes: Int,
    val nightsInWindow: Int,
    val level: DebtLevel,
)

object SleepDebtCalculator {
    const val DEFAULT_TARGET_MINUTES = 480
    private const val WINDOW_NIGHTS = 7

    fun calculate(nights: List<NightlySummary>, targetMinutesPerNight: Int = DEFAULT_TARGET_MINUTES): SleepDebt? {
        if (nights.isEmpty()) return null
        val window = nights.take(WINDOW_NIGHTS)
        val deficit = window.sumOf { (targetMinutesPerNight - it.totalSleepMinutes).coerceAtLeast(0) }
        val level = when {
            deficit <= 30 -> DebtLevel.CAUGHT_UP
            deficit <= 90 -> DebtLevel.MILD
            deficit <= 180 -> DebtLevel.MODERATE
            else -> DebtLevel.SEVERE
        }
        return SleepDebt(deficit, window.size, level)
    }
}
