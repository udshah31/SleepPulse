package com.sleeppulse.app.tracking

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The latest phone-motion score, written by [com.sleeppulse.app.services.SleepTrackingService] (through
 * [PhoneMotionMonitor]) and read by the BLE data source. A value older than [STALE_AFTER_MILLIS] reads as
 * unknown, so a monitor that died can never leave an old score behind.
 */
@Singleton
class PhoneMovement @Inject constructor() {

    private class Published(val score: Float?, val atMillis: Long)

    @Volatile private var last: Published? = null

    fun publish(score: Float?, nowMillis: Long) {
        last = Published(score, nowMillis)
    }

    fun current(nowMillis: Long): Float? {
        val p = last ?: return null
        return if (nowMillis - p.atMillis > STALE_AFTER_MILLIS) null else p.score
    }

    companion object {
        const val STALE_AFTER_MILLIS = 60_000L
    }
}
