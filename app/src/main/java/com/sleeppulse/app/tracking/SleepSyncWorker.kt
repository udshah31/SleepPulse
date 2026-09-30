package com.sleeppulse.app.tracking

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Keeps other apps' sleep in step while SleepPulse isn't open, so History is current the
 * moment it's opened. Skips (successfully) when the user hasn't allowed background reads:
 * the foreground syncs at app start and on opening History still cover them.
 */
@HiltWorker
class SleepSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: HealthConnectSleepSync,
    private val manager: HealthConnectManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!manager.canReadInBackground()) {
            android.util.Log.i("SleepPulse", "Background sleep sync skipped: no background read access")
            return Result.success()
        }
        val ok = sync.sync()
        android.util.Log.i("SleepPulse", "Background sleep sync ran (ok=$ok)")
        return if (ok) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_NAME = "health-connect-sleep-sync"

        /** Idempotent: KEEP leaves an already-scheduled job (and its timing) alone. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SleepSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
