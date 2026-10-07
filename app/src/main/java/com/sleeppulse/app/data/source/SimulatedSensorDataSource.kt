package com.sleeppulse.app.data.source

import com.sleeppulse.shared.model.SensorConnectionState
import com.sleeppulse.shared.model.SensorReading
import com.sleeppulse.shared.model.SleepStage
import com.sleeppulse.shared.sensor.SensorDataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlin.math.sin
import kotlin.random.Random

import com.sleeppulse.app.tracking.SleepStagePredictor

/**
 * Emits plausible heart-rate/HRV/sleep-stage data on a coroutine ticker, walking each
 * value smoothly (rather than pure random jitter) and cycling through sleep stages the
 * way a real night roughly does. Stands in for [BleSensorDataSource] until real hardware
 * is wired up.
 */
class SimulatedSensorDataSource @Inject constructor(
    private val predictor: SleepStagePredictor
) : SensorDataSource {

    private val _connectionState =
        MutableStateFlow<SensorConnectionState>(SensorConnectionState.Disconnected)
    override val connectionState: Flow<SensorConnectionState> = _connectionState.asStateFlow()

    private val stageCycle = listOf(
        SleepStage.AWAKE, SleepStage.LIGHT, SleepStage.DEEP,
        SleepStage.LIGHT, SleepStage.REM, SleepStage.LIGHT,
    )

    override fun readings(): Flow<SensorReading> = flow {
        var tick = 0
        var heartRate = 62.0
        var hrv = 55.0
        while (true) {
            // Keep the dashboard's long-lived collector alive, but don't generate fake sensor
            // data until a tracking session has actually connected the simulated device.
            connectionState.filter { it is SensorConnectionState.Connected }.first()

            while (_connectionState.value is SensorConnectionState.Connected) {
                val stage = stageCycle[(tick / 20) % stageCycle.size]

                val stageHrBias = when (stage) {
                    SleepStage.AWAKE -> 8.0
                    SleepStage.LIGHT -> 0.0
                    SleepStage.DEEP -> -10.0
                    SleepStage.REM -> 4.0
                }
                val stageHrvBias = when (stage) {
                    SleepStage.AWAKE -> -8.0
                    SleepStage.LIGHT -> 0.0
                    SleepStage.DEEP -> 12.0
                    SleepStage.REM -> -4.0
                }

                val target = 60.0 + stageHrBias + sin(tick / 15.0) * 3.0
                heartRate += (target - heartRate) * 0.06 + Random.nextDouble(-0.15, 0.15)

                val hrvTarget = 55.0 + stageHrvBias + sin(tick / 22.0) * 4.0
                hrv += (hrvTarget - hrv) * 0.05 + Random.nextDouble(-0.2, 0.2)

                val predictedStage = predictor.predict(
                    heartRateBpm = heartRate.toInt(),
                    hrvMillis = hrv.toLong(),
                    movement = if (stage == SleepStage.AWAKE) 1.0f else 0.1f
                )

                // A disconnect can happen while the reading is being calculated. Check again
                // immediately before emitting so a stopped session does not receive a trailing
                // simulated sample.
                if (_connectionState.value is SensorConnectionState.Connected) {
                    emit(
                        SensorReading(
                            timestampMillis = System.currentTimeMillis(),
                            heartRateBpm = heartRate.toInt().coerceIn(38, 140),
                            hrvMillis = hrv.coerceIn(15.0, 120.0),
                            sleepStage = predictedStage,
                        )
                    )
                    tick++
                }
                delay(1_000)
            }
        }
    }

    override suspend fun connect() {
        _connectionState.value = SensorConnectionState.Connecting
        delay(400)
        _connectionState.value = SensorConnectionState.Connected(deviceName = "Simulated Sensor")
    }

    override suspend fun disconnect() {
        _connectionState.value = SensorConnectionState.Disconnected
    }
}
