package com.sleeppulse.app.ui.dashboard

import com.sleeppulse.app.data.model.SensorReading

/**
 * Pure, side-effect-free scoring so it's trivially unit-testable independent of the
 * ViewModel/Flow plumbing around it. Higher HRV and lower resting heart rate raise the score.
 */
object SleepScoreCalculator {

    fun score(readings: List<SensorReading>): Int {
        if (readings.isEmpty()) return 0

        val avgHr = readings.map { it.heartRateBpm }.average()
        val hrComponent = ((90.0 - avgHr) / 40.0 * 40).coerceIn(0.0, 40.0)

        val hrv = readings.mapNotNull { it.hrvMillis }
        // No HRV at all (e.g. a strap that sends no RR-intervals): score on heart rate alone,
        // rescaled so such a night can still reach the full range.
        if (hrv.isEmpty()) return (hrComponent / 40.0 * 100).toInt().coerceIn(0, 100)

        val hrvComponent = (hrv.average() / 100.0 * 60).coerceIn(0.0, 60.0)
        return (hrvComponent + hrComponent).toInt().coerceIn(0, 100)
    }
}
