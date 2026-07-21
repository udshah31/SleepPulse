package com.sleeppulse.app.notifications

interface SmartAlarmScheduler {
    fun scheduleHardAlarm(targetHour: Int, targetMinute: Int)
    fun cancelHardAlarm()
    fun fireAlarmNow()
}
