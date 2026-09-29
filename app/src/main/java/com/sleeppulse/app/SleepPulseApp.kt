package com.sleeppulse.app

import android.app.Application
import com.sleeppulse.app.tracking.HealthConnectSleepSync
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class SleepPulseApp : Application() {

    @Inject lateinit var sleepSync: HealthConnectSleepSync
    @Inject lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Incremental after the first launch; a no-op-and-clear if read access isn't granted.
        appScope.launch { sleepSync.sync() }
    }
}
