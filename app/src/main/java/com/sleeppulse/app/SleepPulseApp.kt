package com.sleeppulse.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.sleeppulse.app.tracking.HealthConnectSleepSync
import com.sleeppulse.app.tracking.SleepSyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class SleepPulseApp : Application(), Configuration.Provider {

    @Inject lateinit var sleepSync: HealthConnectSleepSync
    @Inject lateinit var appScope: CoroutineScope
    @Inject lateinit var workerFactory: HiltWorkerFactory

    // WorkManager's default initializer is removed in the manifest so workers get Hilt injection.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Incremental after the first launch; a no-op-and-clear if read access isn't granted.
        appScope.launch { sleepSync.sync() }
        SleepSyncWorker.schedule(this)
    }
}
