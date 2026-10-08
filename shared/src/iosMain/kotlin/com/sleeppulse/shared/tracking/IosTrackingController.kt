package com.sleeppulse.shared.tracking

import com.sleeppulse.shared.db.IosDatabaseFactory
import com.sleeppulse.shared.db.SleepPulseDatabase
import com.sleeppulse.shared.repository.*
import com.sleeppulse.shared.scoring.SleepScoreCalculator
import com.sleeppulse.shared.sensor.RealtimeSimulatedSensorDataSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/** All public operations are called from Swift's main actor; snapshots are delivered on Main. */
class IosTrackingController(private val databasePath: String) {
    private val rootJob = SupervisorJob()
    private val scope = CoroutineScope(rootJob + Dispatchers.Main)
    private val commands = Mutex()
    private val snapshots = MutableStateFlow(IosTrackingSnapshot())
    private var database: SleepPulseDatabase? = null
    private var repository: PersistedSleepRepository? = null
    private var observing: Job? = null
    private var foreground = true
    private var foregroundGeneration = 0L
    private var closed = false

    init { command { initialize() } }

    fun observe(callback: (IosTrackingSnapshot) -> Unit): TrackingObservation =
        TrackingObservation(scope.launch { snapshots.collect { callback(it) } })

    fun start() {
        // Eligibility at request time matters: a queued Start during recovery must not auto-run.
        if (!foreground || snapshots.value.phase != "IDLE") return
        val requestedGeneration = foregroundGeneration
        command {
            if (!foreground || closed || requestedGeneration != foregroundGeneration) return@command
            repository?.connectSensorIf { foreground && requestedGeneration == foregroundGeneration }
            // Background may have arrived while the session row was being created.
            if (!foreground || requestedGeneration != foregroundGeneration) repository?.disconnectSensor()
        }
    }

    fun stop() = command { repository?.disconnectSensor() }

    fun retry() {
        if (snapshots.value.phase != "FAILED") return
        command {
            val repo = repository
            if (repo == null) initialize()
            else {
                repo.recover()
                observeRepository(repo)
            }
        }
    }

    fun setForeground(foreground: Boolean) {
        if (!foreground) foregroundGeneration++
        this.foreground = foreground
        if (!foreground) stop()
    }

    private suspend fun initialize() {
        snapshots.value = IosTrackingSnapshot()
        val db = database ?: IosDatabaseFactory.open(databasePath).also { database = it }
        val now = { Clock.System.now().toEpochMilliseconds() }
        val repo = PersistedSleepRepository(RealtimeSimulatedSensorDataSource(scope, now), RoomSessionStorage(db), scope, now)
        repository = repo
        observeRepository(repo)
        repo.recover()
    }

    private suspend fun observeRepository(repo: PersistedSleepRepository) {
        observing?.cancelAndJoin()
        observing = scope.launch {
            // Room-derived analytics are computed on history changes, not every live reading.
            val history = repo.recentNights().distinctUntilChanged().map(IosHistorySnapshotBuilder::build)
            combine(repo.sessionState, history) { state, saved ->
                val latest = state.readings.lastOrNull()
                IosTrackingSnapshot(
                    phase = state.phase.name,
                    score = state.readings.takeIf { it.isNotEmpty() }?.let { SleepScoreCalculator.score(it) },
                    elapsedSeconds = state.elapsedSeconds,
                    latest = latest?.let { IosReadingSnapshot(it.timestampMillis, it.heartRateBpm, it.hrvMillis, it.sleepStage.name) },
                    nights = saved.nights,
                    insights = saved.insights,
                    error = state.error,
                    notice = state.notice,
                )
            }.catch { e ->
                if (e is CancellationException) throw e
                snapshots.value = snapshots.value.copy(phase = "FAILED", error = "Could not read saved history. ${e.message}")
            }.collect { snapshots.value = it }
        }
    }

    private fun command(block: suspend () -> Unit) {
        if (closed) return
        scope.launch {
            commands.withLock {
                try { block() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    snapshots.value = snapshots.value.copy(phase = "FAILED",
                        error = "Could not open, save or recover tracking data. ${e.message ?: "Please retry."}")
                }
            }
        }
    }

    fun close() = closeAsync {}

    fun closeAsync(onComplete: () -> Unit) {
        if (closed) return
        closed = true
        rootJob.cancel()
        // Wait for non-cancellable inserts before closing their database; leave raw data for recovery.
        CoroutineScope(Dispatchers.Main).launch {
            rootJob.join()
            database?.close()
            database = null
            onComplete()
        }
    }
}
