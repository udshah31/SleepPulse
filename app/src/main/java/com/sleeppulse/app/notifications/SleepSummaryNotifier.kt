package com.sleeppulse.app.notifications

import com.sleeppulse.shared.model.NightlySummary
import com.sleeppulse.shared.scoring.RecoveryResult

/**
 * Posts a local notification summarising a completed night's sleep.
 * Abstracted as an interface so tests can use a fake without touching NotificationManager.
 */
interface SleepSummaryNotifier {
    fun notify(summary: NightlySummary, recoveryResult: RecoveryResult?)
}
