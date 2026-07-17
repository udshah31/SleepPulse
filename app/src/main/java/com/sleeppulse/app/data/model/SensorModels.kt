package com.sleeppulse.app.data.model

/** Sleep stages reported by a sensor source, ordered roughly by depth. */
enum class SleepStage {
    AWAKE, LIGHT, DEEP, REM
}

/** A single instantaneous reading from a sensor source (real or simulated). */
data class SensorReading(
    val timestampMillis: Long,
    val heartRateBpm: Int,
    val hrvMillis: Double,
    val sleepStage: SleepStage,
)

/** Connection lifecycle for any [com.sleeppulse.app.data.source.SensorDataSource]. */
sealed class SensorConnectionState {
    data object Disconnected : SensorConnectionState()
    data object Connecting : SensorConnectionState()
    data class Connected(val deviceName: String) : SensorConnectionState()
    data class Error(val message: String) : SensorConnectionState()
}
