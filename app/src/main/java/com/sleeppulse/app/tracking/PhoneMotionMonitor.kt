package com.sleeppulse.app.tracking

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log

/**
 * Listens to the phone's accelerometer while a session runs and reports a [MovementScoreCalculator] score for
 * the last [MovementScoreCalculator.WINDOW_MILLIS] every [PUBLISH_EVERY_MILLIS]. Samples are batched
 * ([REPORT_LATENCY_US]) to save battery. Does nothing when [sensorManager] is null or there is no accelerometer.
 * Not thread-safe: register/unregister and callbacks all arrive on the thread that called [start] (main).
 */
class PhoneMotionMonitor(
    private val sensorManager: SensorManager?,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : SensorEventListener {

    private val buffer = ArrayDeque<AccelSample>()
    private var onScore: ((Float?) -> Unit)? = null
    private var lastPublishMillis = 0L

    /** Returns false when there is no accelerometer to listen to (movement then simply stays unknown). */
    fun start(onScore: (Float?) -> Unit): Boolean {
        val manager = sensorManager ?: return false
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false
        this.onScore = onScore
        buffer.clear()
        lastPublishMillis = 0L
        return manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL, REPORT_LATENCY_US)
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        buffer.clear()
        onScore = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        val now = nowMillis()
        // Batched events arrive late; place each at the time it actually happened.
        val happenedAt = now - (SystemClock.elapsedRealtimeNanos() - event.timestamp) / 1_000_000L
        buffer.addLast(AccelSample(happenedAt, event.values[0], event.values[1], event.values[2]))
        while (buffer.isNotEmpty() && buffer.first().timestampMillis < now - MovementScoreCalculator.WINDOW_MILLIS) {
            buffer.removeFirst()
        }
        if (now - lastPublishMillis >= PUBLISH_EVERY_MILLIS) {
            lastPublishMillis = now
            val score = MovementScoreCalculator.score(buffer.toList())
            Log.d("SleepPulse", "phone movement score=$score")
            onScore?.invoke(score)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val PUBLISH_EVERY_MILLIS = 5_000L
        const val REPORT_LATENCY_US = 5_000_000
    }
}
