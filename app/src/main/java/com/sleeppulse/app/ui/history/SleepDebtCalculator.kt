package com.sleeppulse.app.ui.history

import com.sleeppulse.app.data.model.NightlySummary

enum class DebtLevel {
    /** Up to 30 min short — essentially on target. */
    CAUGHT_UP,
    /** 31–90 min short over the window. */
    MILD,
    /** 91–180 min short. */
    MODERATE,
    /** More than 180 min short. */
    SEVERE,
}

/**
 * Rolling sleep-debt snapshot over a window of recent nights.
 *
 * @param deficitMinutes Total minutes short vs. [targetMinutesPerNight] across [nightsInWindow].
 *   Always ≥ 0 — surplus sleep is treated as 0 deficit (debt can't go negative).
 * @param nightsInWindow How many nights contributed to this calculation (1–7).
 * @param level Tier label derived from [deficitMinutes].
 */
data class SleepDebt(
    val deficitMinutes: Int,
    val nightsInWindow: Int,
    val level: DebtLevel,
)

/**
 * Computes a rolling sleep-debt figure from the most recent [WINDOW_NIGHTS] nights.
 *
 * Formula: sum each night's shortfall vs. [DEFAULT_TARGET_MINUTES], clamp negatives to 0
 * (surplus doesn't cancel previous debt), then classify the total into a [DebtLevel].
 *
 * Returns null when [nights] is empty — there's no meaningful debt figure without history.
 */
object SleepDebtCalculator {

    const val DEFAULT_TARGET_MINUTES = 480 // 8 hours
    private const val WINDOW_NIGHTS = 7

    fun calculate(
        nights: List<NightlySummary>,
        targetMinutesPerNight: Int = DEFAULT_TARGET_MINUTES,
    ): SleepDebt? {
        if (nights.isEmpty()) return null

        val window = nights.take(WINDOW_NIGHTS)
        val deficitMinutes = window.sumOf { night ->
            (targetMinutesPerNight - night.totalSleepMinutes).coerceAtLeast(0)
        }
        return SleepDebt(
            deficitMinutes = deficitMinutes,
            nightsInWindow = window.size,
            level = levelFor(deficitMinutes),
        )
    }

    private fun levelFor(deficitMinutes: Int): DebtLevel = when {
        deficitMinutes <= 30 -> DebtLevel.CAUGHT_UP
        deficitMinutes <= 90 -> DebtLevel.MILD
        deficitMinutes <= 180 -> DebtLevel.MODERATE
        else -> DebtLevel.SEVERE
    }
}
