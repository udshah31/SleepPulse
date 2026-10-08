package com.sleeppulse.shared.sensor

import com.sleeppulse.shared.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** One hot producer, independent of the number of consumers. Timestamps are never accelerated. */
class RealtimeSimulatedSensorDataSource(
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long,
) : SensorDataSource {
    private val state = MutableStateFlow<SensorConnectionState>(SensorConnectionState.Disconnected)
    override val connectionState = state.asStateFlow()
    private val samples = MutableSharedFlow<SensorReading>(extraBufferCapacity = 64)
    override fun readings(): Flow<SensorReading> = samples
    private var producer: Job? = null

    override suspend fun connect() {
        if (producer?.isActive == true) return
        state.value = SensorConnectionState.Connected("Simulated sensor")
        producer = scope.launch {
            var tick = 0
            val stages = listOf(SleepStage.AWAKE, SleepStage.LIGHT, SleepStage.DEEP,
                SleepStage.LIGHT, SleepStage.REM, SleepStage.LIGHT)
            while (isActive) {
                val stage = stages[(tick / 20) % stages.size]
                val (hr, hrv) = when (stage) {
                    SleepStage.AWAKE -> 72 to 38.0
                    SleepStage.LIGHT -> 60 to 58.0
                    SleepStage.DEEP -> 54 to 72.0
                    SleepStage.REM -> 64 to 52.0
                }
                val offset = tick % 5 - 2
                samples.emit(SensorReading(nowMillis(), hr + offset, hrv + offset, stage))
                tick++
                delay(1_000)
            }
        }
    }

    override suspend fun disconnect() {
        producer?.cancelAndJoin()
        producer = null
        state.value = SensorConnectionState.Disconnected
    }
}
