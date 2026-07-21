package com.sleeppulse.app.notifications

/**
 * Schedules a daily reminder to wind down before bed.
 */
interface WindDownScheduler {
    
    /**
     * Schedules the wind down reminder.
     * The reminder is typically triggered 45 minutes prior to [targetHour]:[targetMinute].
     */
    fun scheduleWindDown(targetHour: Int, targetMinute: Int)
    
    /**
     * Cancels any existing wind down reminder.
     */
    fun cancelWindDown()
}
