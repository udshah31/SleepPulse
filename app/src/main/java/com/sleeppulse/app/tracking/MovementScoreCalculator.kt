package com.sleeppulse.app.tracking

import kotlin.math.abs
import kotlin.math.sqrt

/** One accelerometer reading in m/s², stamped with when it happened (epoch millis). */
data class AccelSample(val timestampMillis: Long, val x: Float, val y: Float, val z: Float)

/**
 * Turns a window of phone accelerometer samples into a 0..1 movement score for
 * [SleepStagePredictor]. Only the change in acceleration magnitude from gravity counts, so the phone's
 * orientation doesn't matter. The caller (see [PhoneMotionMonitor]) keeps the window; this only scores it.
 *
 * ponytail: FULL_SCALE_DEVIATION and the window are first guesses made without real sleep data — a peak above
 * 1.6 m/s² (full scale 2.0 × the predictor's 0.8 AWAKE threshold) reads as awake. Tune against real nights.
 */
object MovementScoreCalculator {
    const val WINDOW_MILLIS = 30_000L
    const val FULL_SCALE_DEVIATION = 2.0f
    private const val GRAVITY = 9.81f

    /** Null when there is nothing finite to score. */
    fun score(samples: List<AccelSample>): Float? {
        var maxDeviation: Float? = null
        for (s in samples) {
            val magnitude = sqrt(s.x * s.x + s.y * s.y + s.z * s.z)
            if (!magnitude.isFinite()) continue
            val deviation = abs(magnitude - GRAVITY)
            if (maxDeviation == null || deviation > maxDeviation) maxDeviation = deviation
        }
        return maxDeviation?.let { (it / FULL_SCALE_DEVIATION).coerceIn(0f, 1f) }
    }
}
