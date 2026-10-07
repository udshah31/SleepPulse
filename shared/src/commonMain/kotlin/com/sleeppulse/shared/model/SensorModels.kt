package com.sleeppulse.shared.model

/** Sleep stages reported by a sensor source, ordered roughly by depth. */
enum class SleepStage {
    AWAKE, LIGHT, DEEP, REM,
}

/** A single instantaneous reading from a sensor source. */
data class SensorReading(
    val timestampMillis: Long,
    val heartRateBpm: Int,
    val hrvMillis: Double?,
    val sleepStage: SleepStage,
)

/** A contiguous run of one [SleepStage], with an exclusive end timestamp. */
data class StageSegment(
    val startMillis: Long,
    val endMillis: Long,
    val stage: SleepStage,
)

/** Connection lifecycle for any sensor implementation. */
sealed class SensorConnectionState {
    data object Disconnected : SensorConnectionState()
    data object Connecting : SensorConnectionState()
    data class Connected(val deviceName: String) : SensorConnectionState()
    data class Error(val message: String) : SensorConnectionState()
}
