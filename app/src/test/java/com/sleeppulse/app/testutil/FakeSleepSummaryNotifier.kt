package com.sleeppulse.app.testutil

import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.notifications.SleepSummaryNotifier
import com.sleeppulse.app.ui.dashboard.RecoveryResult

class FakeSleepSummaryNotifier : SleepSummaryNotifier {
    val notifiedSummaries = mutableListOf<NightlySummary>()
    val notifiedRecoveryResults = mutableListOf<RecoveryResult?>()

    override fun notify(summary: NightlySummary, recoveryResult: RecoveryResult?) {
        notifiedSummaries.add(summary)
        notifiedRecoveryResults.add(recoveryResult)
    }
}
