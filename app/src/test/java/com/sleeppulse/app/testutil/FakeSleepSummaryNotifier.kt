package com.sleeppulse.app.testutil

import com.sleeppulse.shared.model.NightlySummary
import com.sleeppulse.shared.scoring.RecoveryResult
import com.sleeppulse.app.notifications.SleepSummaryNotifier

class FakeSleepSummaryNotifier : SleepSummaryNotifier {
    val notifiedSummaries = mutableListOf<NightlySummary>()
    val notifiedRecoveryResults = mutableListOf<RecoveryResult?>()

    override fun notify(summary: NightlySummary, recoveryResult: RecoveryResult?) {
        notifiedSummaries.add(summary)
        notifiedRecoveryResults.add(recoveryResult)
    }
}
