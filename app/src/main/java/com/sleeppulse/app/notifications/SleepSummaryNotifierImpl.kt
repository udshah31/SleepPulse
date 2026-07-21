package com.sleeppulse.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.ui.dashboard.RecoveryResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val CHANNEL_ID = "sleep_summary"
private const val CHANNEL_NAME = "Sleep Summary"
private const val CHANNEL_DESC = "Nightly sleep and recovery summary after sensor disconnect"
private const val NOTIFICATION_ID = 1001

/**
 * Fires a local notification with last night's sleep score, recovery score, and sleep
 * duration as soon as the sensor is disconnected and a nightly summary has been recorded.
 *
 * The notification channel is created lazily on first use — Android no-ops duplicate
 * channel registrations so this is safe to call multiple times.
 */
@Singleton
class SleepSummaryNotifierImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SleepSummaryNotifier {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun notify(summary: NightlySummary, recoveryResult: RecoveryResult?) {
        ensureChannel()

        val hours = summary.totalSleepMinutes / 60
        val minutes = summary.totalSleepMinutes % 60
        val durationText = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

        val title = "Last night: Sleep score ${summary.sleepScore}"
        val body = buildString {
            append("Sleep $durationText")
            if (recoveryResult != null) {
                append("  ·  Recovery ${recoveryResult.score} (${recoveryResult.tier.name.lowercase()})")
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = CHANNEL_DESC }
        notificationManager.createNotificationChannel(channel)
    }
}
