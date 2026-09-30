package com.sleeppulse.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sleeppulse.app.MainActivity
import com.sleeppulse.app.R
import com.sleeppulse.app.data.repository.SleepRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import com.sleeppulse.app.data.repository.SettingsRepository
import com.sleeppulse.app.notifications.SmartAlarmScheduler
import com.sleeppulse.app.notifications.SleepSessionFinalizer
import com.sleeppulse.app.data.model.SleepStage
import com.sleeppulse.app.tracking.NoiseMonitor
import com.sleeppulse.app.wear.WearDataClient

@AndroidEntryPoint
class SleepTrackingService : Service() {

    @Inject
    lateinit var repository: SleepRepository
    
    @Inject
    lateinit var settingsRepository: SettingsRepository
    
    @Inject
    lateinit var smartAlarmScheduler: SmartAlarmScheduler
    
    @Inject
    lateinit var wearDataClient: WearDataClient

    @Inject
    lateinit var sleepSessionFinalizer: SleepSessionFinalizer

    private var hasFiredSmartAlarm = false
    private var noiseMonitor: NoiseMonitor? = null
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        noiseMonitor = NoiseMonitor(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_TRACKING) {
            sleepSessionFinalizer.finalizeAsync()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        scope.launch {
            repository.connectSensor()
        }
        
        scope.launch {
            repository.liveReadings().collect { reading ->
                if (!hasFiredSmartAlarm) {
                    val now = Calendar.getInstance()
                    val currentHour = now.get(Calendar.HOUR_OF_DAY)
                    val currentMinute = now.get(Calendar.MINUTE)
                    val currentTimeInMinutes = currentHour * 60 + currentMinute

                    val targetHour = settingsRepository.targetWakeupHour.value
                    val targetMinute = settingsRepository.targetWakeupMinute.value
                    val window = settingsRepository.wakeWindowMinutes.value

                    val targetTimeInMinutes = targetHour * 60 + targetMinute
                    var startTimeInMinutes = targetTimeInMinutes - window
                    
                    // Handle midnight wrapping for startTime
                    if (startTimeInMinutes < 0) {
                        startTimeInMinutes += 24 * 60
                    }

                    val isInWindow = if (startTimeInMinutes <= targetTimeInMinutes) {
                        currentTimeInMinutes in startTimeInMinutes..targetTimeInMinutes
                    } else {
                        // Window spans midnight (e.g. 23:45 to 00:15)
                        currentTimeInMinutes >= startTimeInMinutes || currentTimeInMinutes <= targetTimeInMinutes
                    }

                    if (isInWindow && (reading.sleepStage == SleepStage.LIGHT || reading.sleepStage == SleepStage.AWAKE)) {
                        smartAlarmScheduler.fireAlarmNow()
                        smartAlarmScheduler.cancelHardAlarm()
                        hasFiredSmartAlarm = true
                    }
                }
                
                wearDataClient.sendReadingToWearable(reading)
            }
        }

        scope.launch {
            noiseMonitor?.startMonitoring()?.collect { db ->
                if (db > 70.0) {
                    android.util.Log.i("SleepPulse", "Loud ambient noise detected: ${"%.1f".format(db)} dB")
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Use finalizeAsync() (app-scoped), not scope.launch{}: this Service's own `scope` is
        // cancelled a few lines below, and onDestroy() typically runs within milliseconds of
        // stopSelf(), so a finalize() launched on `scope` would be killed mid-flight before its
        // Room read + upsert + Health Connect write + widget refresh complete.
        sleepSessionFinalizer.finalizeAsync()
        noiseMonitor?.stopMonitoring()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SleepPulse is tracking")
            .setContentText("Monitoring your sleep data...")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sleep Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows that sleep tracking is active"
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "sleep_tracking_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP_TRACKING = "com.sleeppulse.app.ACTION_STOP_TRACKING"
    }
}
