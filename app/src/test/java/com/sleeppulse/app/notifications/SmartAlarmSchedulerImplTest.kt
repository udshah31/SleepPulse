package com.sleeppulse.app.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class SmartAlarmSchedulerImplTest {

    private val context: Context = mock {
        on { packageName } doReturn "com.sleeppulse.app"
    }
    private val alarmManager: AlarmManager = mock()
    private val notificationManager: NotificationManager = mock()

    private fun schedulerAt(nowMillis: Long) = SmartAlarmSchedulerImpl(
        context = context,
        alarmManager = alarmManager,
        notificationManager = notificationManager,
        nowMillis = { nowMillis },
    )

    private fun calendarFor(vararg fields: Pair<Int, Int>) = Calendar.getInstance().apply {
        fields.forEach { (field, value) -> set(field, value) }
    }

    @Test
    fun `nextTriggerMillis stays on the same day when target time has not passed`() {
        val now = calendarFor(
            Calendar.HOUR_OF_DAY to 6,
            Calendar.MINUTE to 0,
            Calendar.SECOND to 0,
            Calendar.MILLISECOND to 0,
        )

        val trigger = nextTriggerMillis(targetHour = 7, targetMinute = 30, nowMillis = now.timeInMillis)

        val expected = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expected.timeInMillis, trigger)
    }

    @Test
    fun `nextTriggerMillis rolls over to tomorrow when target time has already passed today`() {
        val now = calendarFor(
            Calendar.HOUR_OF_DAY to 8,
            Calendar.MINUTE to 0,
            Calendar.SECOND to 0,
            Calendar.MILLISECOND to 0,
        )

        val trigger = nextTriggerMillis(targetHour = 7, targetMinute = 30, nowMillis = now.timeInMillis)

        val expected = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        assertEquals(expected.timeInMillis, trigger)
    }

    @Test
    fun `scheduleHardAlarm sets an alarm clock`() {
        val scheduler = schedulerAt(0L)

        scheduler.scheduleHardAlarm(targetHour = 7, targetMinute = 30)

        verify(alarmManager).setAlarmClock(anyOrNull(), anyOrNull())
    }

    @Test
    fun `cancelHardAlarm cancels the pending alarm`() {
        val scheduler = schedulerAt(0L)

        scheduler.cancelHardAlarm()

        verify(alarmManager).cancel(anyOrNull<PendingIntent>())
    }
}
