package com.sleeppulse.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.sleeppulse.app.tracking.SleepSyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SleepPulseApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    // WorkManager's default initializer is removed in the manifest so workers get Hilt injection.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // No Health Connect sync here: the process also starts in the background (widget,
        // alarms, WorkManager), where reads fail without background access and spend Health
        // Connect's read quota. Opening History and SleepSyncWorker do the syncing.
        SleepSyncWorker.schedule(this)
    }
}
