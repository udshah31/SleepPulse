package com.sleeppulse.app.ui.dashboard

import com.sleeppulse.app.data.model.SensorReading

/**
 * Pure, side-effect-free scoring so it's trivially unit-testable independent of the
 * ViewModel/Flow plumbing around it. Higher HRV and lower resting heart rate raise the score.
 */
object SleepScoreCalculator {

    fun score(readings: List<SensorReading>): Int {
        if (readings.isEmpty()) return 0

        val avgHrv = readings.map { it.hrvMillis }.average()
        val avgHr = readings.map { it.heartRateBpm }.average()

        val hrvComponent = (avgHrv / 100.0 * 60).coerceIn(0.0, 60.0)
        val hrComponent = ((90.0 - avgHr) / 40.0 * 40).coerceIn(0.0, 40.0)

        return (hrvComponent + hrComponent).toInt().coerceIn(0, 100)
    }
}
