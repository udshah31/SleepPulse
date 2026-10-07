package com.sleeppulse.shared.scoring

import com.sleeppulse.shared.model.SensorReading

/** Higher HRV and lower heart rate raise the 0–100 sleep score. */
object SleepScoreCalculator {

    fun score(readings: List<SensorReading>): Int {
        if (readings.isEmpty()) return 0

        val avgHr = readings.map { it.heartRateBpm }.average()
        val hrComponent = ((90.0 - avgHr) / 40.0 * 40).coerceIn(0.0, 40.0)

        val hrv = readings.mapNotNull { it.hrvMillis }
        if (hrv.isEmpty()) return (hrComponent / 40.0 * 100).toInt().coerceIn(0, 100)

        val hrvComponent = (hrv.average() / 100.0 * 60).coerceIn(0.0, 60.0)
        return (hrvComponent + hrComponent).toInt().coerceIn(0, 100)
    }
}
