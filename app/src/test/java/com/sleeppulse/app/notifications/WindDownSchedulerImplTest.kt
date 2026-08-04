package com.sleeppulse.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class WindDownSchedulerImplTest {

    private val context: Context = mock {
        on { packageName } doReturn "com.sleeppulse.app"
    }
    private val alarmManager: AlarmManager = mock()

    private fun schedulerAt(nowMillis: Long) = WindDownSchedulerImpl(
        context = context,
        alarmManager = alarmManager,
        nowMillis = { nowMillis },
    )

    private fun calendarFor(vararg fields: Pair<Int, Int>) = Calendar.getInstance().apply {
        fields.forEach { (field, value) -> set(field, value) }
    }

    @Test
    fun `nextWindDownTriggerMillis is 45 minutes before bedtime when that time has not passed`() {
        val now = calendarFor(
            Calendar.HOUR_OF_DAY to 6,
            Calendar.MINUTE to 0,
            Calendar.SECOND to 0,
            Calendar.MILLISECOND to 0,
        )

        val trigger = nextWindDownTriggerMillis(targetHour = 22, targetMinute = 30, nowMillis = now.timeInMillis)

        val expected = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, -45)
        }
        assertEquals(expected.timeInMillis, trigger)
    }

    @Test
    fun `nextWindDownTriggerMillis rolls over to tomorrow when wind-down time has already passed today`() {
        val now = calendarFor(
            Calendar.HOUR_OF_DAY to 22,
            Calendar.MINUTE to 0,
            Calendar.SECOND to 0,
            Calendar.MILLISECOND to 0,
        )

        val trigger = nextWindDownTriggerMillis(targetHour = 22, targetMinute = 30, nowMillis = now.timeInMillis)

        val expected = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, -45)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        assertEquals(expected.timeInMillis, trigger)
    }

    @Test
    fun `scheduleWindDown sets an inexact repeating alarm`() {
        val scheduler = schedulerAt(0L)

        scheduler.scheduleWindDown(targetHour = 22, targetMinute = 30)

        verify(alarmManager).setInexactRepeating(
            eq(AlarmManager.RTC_WAKEUP),
            anyOrNull(),
            eq(AlarmManager.INTERVAL_DAY),
            anyOrNull(),
        )
    }

    @Test
    fun `cancelWindDown cancels the pending alarm`() {
        val scheduler = schedulerAt(0L)

        scheduler.cancelWindDown()

        verify(alarmManager).cancel(anyOrNull<PendingIntent>())
    }
}
