package com.sleeppulse.app.ui.recovery

import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.ui.dashboard.RecoveryResult
import com.sleeppulse.app.ui.history.SleepDebt

data class RecoveryState(
    val isLoading: Boolean = true,
    val recoveryResult: RecoveryResult? = null,
    val sleepDebt: SleepDebt? = null,
    val consistencyScore: Int = 0,
    val latestNight: NightlySummary? = null,
    val personalizedAdvice: String? = null
)

sealed interface RecoveryIntent {
    data object Load : RecoveryIntent
}
