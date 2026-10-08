package com.sleeppulse.shared.repository

import com.sleeppulse.shared.db.SleepSessionEntity
import com.sleeppulse.shared.model.*
import com.sleeppulse.shared.sensor.SensorDataSource
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate

enum class TrackingPhase { RECOVERING, IDLE, STARTING, TRACKING, SAVING, FAILED }

data class TrackingSessionState(
    val phase: TrackingPhase = TrackingPhase.RECOVERING,
    val readings: List<SensorReading> = emptyList(),
    val elapsedSeconds: Long = 0,
    val error: String? = null,
    val notice: String? = null,
)

/** Durable foreground sessions. UI observations cannot start sensor or recording jobs. */
class PersistedSleepRepository(
    private val sensor: SensorDataSource,
    private val storage: SessionStorage,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long,
) : SleepRepository {
    private val commands = Mutex()
    private val state = MutableStateFlow(TrackingSessionState())
    val sessionState = state.asStateFlow()
    private val tracking = MutableStateFlow(false)
    override val isTracking = tracking.asStateFlow()
    override val connectionState = sensor.connectionState
    private val recorded = MutableSharedFlow<SensorReading>(extraBufferCapacity = 40, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override fun liveReadings(): Flow<SensorReading> = recorded
    override fun recentNights() = storage.recentNights()
    private var session: SleepSessionEntity? = null
    private var recorder: Job? = null
    private var firstTimestamp: Long? = null
    private var lastTimestamp: Long? = null

    suspend fun recover() = commands.withLock {
        check(!tracking.value) { "Stop tracking before recovery" }
        try {
            haltRecording()
            state.value = state.value.copy(phase = TrackingPhase.RECOVERING, error = null)
            var failure: Exception? = null
            storage.unfinished().sortedWith(compareBy({ it.startEpochMillis }, { it.sessionId })).forEach {
                try { storage.finish(it) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { failure = e }
            }
            failure?.let { throw it }
            session = null
            state.value = state.value.copy(phase = TrackingPhase.IDLE, error = null)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { failed(e); throw e }
    }

    override suspend fun connectSensor() = connectSensorIf { true }

    suspend fun connectSensorIf(shouldContinue: () -> Boolean) = commands.withLock {
        if (tracking.value) return@withLock
        check(state.value.phase == TrackingPhase.IDLE) { "Recovery must complete before Start" }
        state.value = state.value.copy(phase = TrackingPhase.STARTING, error = null, notice = null)
        try {
            session = storage.create(nowMillis())
            firstTimestamp = null
            lastTimestamp = null
            state.value = TrackingSessionState(phase = TrackingPhase.TRACKING)
            tracking.value = true
            val id = checkNotNull(session).sessionId
            if (!shouldContinue()) {
                tracking.value = false
                storage.finish(checkNotNull(session))
                session = null
                state.value = TrackingSessionState(phase = TrackingPhase.IDLE)
                return@withLock
            }
            recorder = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    sensor.readings().collect { reading ->
                        if (lastTimestamp?.let { reading.timestampMillis <= it } == true) return@collect
                        // A Stop/cancellation must finish the one insert already in flight.
                        withContext(NonCancellable) {
                            storage.append(id, reading)
                            if (firstTimestamp == null) firstTimestamp = reading.timestampMillis
                            lastTimestamp = reading.timestampMillis
                            state.value = state.value.copy(
                                readings = (state.value.readings + reading).takeLast(40),
                                elapsedSeconds = (reading.timestampMillis - checkNotNull(firstTimestamp)) / 1_000,
                            )
                            recorded.tryEmit(reading)
                        }
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    sensor.disconnect()
                    failed(e)
                }
            }
            sensor.connect()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { haltRecording(); failed(e); throw e }
    }

    override suspend fun disconnectSensor(): NightlySummary? = commands.withLock {
        val active = session ?: return@withLock null
        state.value = state.value.copy(phase = TrackingPhase.SAVING)
        try {
            haltRecording()
            val summary = storage.finish(active)
            session = null
            state.value = state.value.copy(phase = TrackingPhase.IDLE, error = null,
                notice = if (summary == null) "No readings to save." else
                    "Session saved. History keeps the longest session for each date.")
            summary
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { failed(e); throw e }
    }

    private suspend fun haltRecording() {
        sensor.disconnect()
        recorder?.cancelAndJoin()
        recorder = null
        tracking.value = false
    }

    private fun failed(e: Exception) {
        tracking.value = false
        state.value = state.value.copy(phase = TrackingPhase.FAILED,
            error = "Could not save or recover the session. ${e.message ?: "Please retry."}")
    }

    override suspend fun recordNightlySummary(summary: NightlySummary) = storage.record(summary)
    override suspend fun updateTags(date: LocalDate, tags: List<String>) = storage.updateTags(date, tags)
}
