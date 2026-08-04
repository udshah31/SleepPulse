package com.sleeppulse.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WindDownSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : WindDownScheduler {

    override fun scheduleWindDown(targetHour: Int, targetMinute: Int) {
        val intent = Intent(context, WindDownReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            WIND_DOWN_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use inexact repeating alarm so we don't need SCHEDULE_EXACT_ALARM permission
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            nextWindDownTriggerMillis(targetHour, targetMinute, nowMillis()),
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    override fun cancelWindDown() {
        val intent = Intent(context, WindDownReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            WIND_DOWN_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    companion object {
        private const val WIND_DOWN_REQUEST_CODE = 1001
    }
}

// 45 minutes before bedtime; rolls to tomorrow if that time has already passed today.
internal fun nextWindDownTriggerMillis(targetHour: Int, targetMinute: Int, nowMillis: Long): Long {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, targetHour)
        set(Calendar.MINUTE, targetMinute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.MINUTE, -45)

        if (timeInMillis <= nowMillis) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }
    return calendar.timeInMillis
}
