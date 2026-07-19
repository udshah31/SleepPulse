# Nightly session recording resilience

## Context

The Recovery Score feature (shipped 2026-07-18) depends on
`SleepRepository.recordNightlySummary()` actually being called, which only
happens today via one code path: the user manually taps the connection
toggle off while connected
(`DashboardViewModel.toggleConnection()`). Session readings are buffered
purely in memory (`DashboardViewModel.sessionReadings`, a plain
`mutableListOf`), and nothing is persisted incrementally to Room.

This means any interruption other than a deliberate manual disconnect
silently loses the entire night: a BLE disconnect (already handled at the
`SensorDataSource` layer, but never wired to recording), the app being
backgrounded, or the process being killed — all plausible or likely outcomes
for an app meant to track a full night's sleep while the phone sits on a
nightstand. There is no foreground service or WorkManager use anywhere in
the app; everything runs in `viewModelScope`, which dies with the
Activity/ViewModel.

This spec adds an incremental-persistence safety net so an interrupted
session isn't lost, without introducing a foreground service — that's a
larger architectural change (persistent notification, moving connection
lifecycle out of the ViewModel) considered too heavy for the current
single-Activity, Phase 1 architecture. The safety net trades perfect
durability (a foreground service would survive OS process kills more
reliably) for a much smaller change that still closes the "lose the whole
night" failure mode down to "lose at most the last unflushed batch."

## Scope

In scope:
- Two new Room entities: `SleepSessionEntity` (tracks an in-progress
  session) and `SessionReadingEntity` (raw readings batched to disk during a
  live session)
- `SleepPulseDatabase` bumped to `version = 2` with
  `fallbackToDestructiveMigration()`
- `SleepRepositoryImpl` changes: create a session row on `connectSensor()`,
  batch-flush incoming readings to `SessionReadingEntity` during
  `liveReadings()` collection, finalize (and clean up raw readings) on
  successful `recordNightlySummary()`
- Startup recovery: on repository init, detect any unfinalized session left
  over from a prior process, rebuild its `NightlySummary` via the existing
  `NightSummaryBuilder`, record it, and finalize/clean up
- Unit tests for the batching flush cadence and the recovery-on-init logic

Out of scope (deferred):
- A foreground service / WorkManager-based collection that survives OS
  process kills more robustly — this spec is a safety net, not a full fix
- Visually distinguishing recovered/partial nights in History or Dashboard
  (per design decision below, a recovered night is treated identically to a
  normally-completed one)
- Multiple concurrent sessions, naps, or manual session editing
- A real Room `Migration` — `fallbackToDestructiveMigration()` is
  acceptable pre-release (no shipped installs to preserve data for)

## Design decisions

**Recovered sessions are auto-finalized as full nights, not flagged.** When
the app finds an unfinalized session on startup, it runs it through the
same `NightSummaryBuilder` → `recordNightlySummary` pipeline as a normal
disconnect. No "partial" flag or distinct visual treatment — it appears in
History/Recovery Score exactly like any other night. Rationale: keeps the
Recovery Score baseline calculation and History list simple (no new state
to reason about downstream), and a session recovered from batched
persistence is real HR/HRV data, not a guess — just from a session that
didn't get an explicit user-initiated disconnect.

**Batched flush, not per-reading writes.** Readings are buffered in the
repository and flushed to `SessionReadingEntity` every 30 seconds or every
20 readings (whichever comes first), rather than inserting on every
emission. Rationale: an 8-hour session at typical HR-monitor emission rates
would otherwise generate a very large number of individual Room writes;
batching bounds worst-case data loss to one flush interval while keeping
write volume reasonable.

**Empty/near-empty sessions are dropped, not finalized.** If a recovered
session has zero readings (e.g. connect happened and the process died
before the first flush), skip `NightSummaryBuilder` (which assumes
non-empty input) and just delete the empty session row.

## Data model

```kotlin
@Entity(tableName = "sleep_session")
data class SleepSessionEntity(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val startEpochMillis: Long,
    val finalized: Boolean,
)

@Entity(tableName = "session_reading")
data class SessionReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestampMillis: Long,
    val heartRateBpm: Int,
    val hrvMillis: Double,
    val sleepStage: SleepStage,
)
```

New DAO (`SleepSessionDao`):
- `suspend fun createSession(session: SleepSessionEntity): Long`
- `suspend fun insertReadings(readings: List<SessionReadingEntity>)`
- `suspend fun unfinalizedSessions(): List<SleepSessionEntity>`
- `suspend fun readingsFor(sessionId: Long): List<SessionReadingEntity>`
- `suspend fun finalizeAndClear(sessionId: Long)` — marks finalized and
  deletes that session's `SessionReadingEntity` rows (single `@Transaction`)

`SleepPulseDatabase` adds both entities, bumps to `version = 2`, keeps
`exportSchema = false`, and the `Room.databaseBuilder` call in
`di/AppModule.kt` adds `.fallbackToDestructiveMigration()`.

## Session lifecycle

1. **`connectSensor()`** — repository creates a `SleepSessionEntity` row
   (`finalized = false`, `startEpochMillis = now`), keeps the returned
   `sessionId` as internal state for the duration of the connection.
2. **Live collection** — the repository's collection of
   `SensorDataSource.readings()` (already wrapping/exposing
   `liveReadings()`) buffers readings into an in-memory list and flushes to
   `insertReadings()` every 30s or every 20 readings. `DashboardViewModel`'s
   own in-memory `sessionReadings` accumulation (used for the live chart and
   end-of-session score) is unchanged.
3. **Normal disconnect** — `toggleConnection()`'s existing flow (build
   summary from `sessionReadings`, `recordNightlySummary`, clear
   `sessionReadings`, `disconnectSensor()`) is unchanged, except
   `disconnectSensor()` now also calls `finalizeAndClear(sessionId)` after
   the summary is recorded successfully.
4. **Interrupted session** — nothing extra happens at interruption time;
   whatever was already flushed to `SessionReadingEntity` simply remains
   with `finalized = false`.
5. **Recovery on next launch** — during `SleepRepositoryImpl` init (or first
   access), query `unfinalizedSessions()`. For each: load its readings via
   `readingsFor(sessionId)`; if non-empty, run
   `NightSummaryBuilder.build(readings, dateFromEpochMillis(startEpochMillis))`
   and `recordNightlySummary(...)`; either way, call
   `finalizeAndClear(sessionId)` to clean up.

`SleepRepository`'s public interface (`connectSensor()`,
`disconnectSensor()`, `recordNightlySummary()`, etc.) is unchanged — session
bookkeeping and recovery are internal to `SleepRepositoryImpl`, so
`DashboardViewModel` and other callers require no changes.

## Error handling

- If a batch flush fails (e.g. transient Room/IO error), the buffered
  readings stay in memory and are retried on the next flush tick rather
  than dropped, matching the "best effort, bound the loss window" goal.
- If recovery's `recordNightlySummary` call fails for a given session (e.g.
  it collides with a night already recorded for that date via the existing
  upsert/REPLACE), the session is still finalized/cleared rather than
  retried indefinitely on every subsequent launch — avoids an unrecoverable
  session permanently blocking startup.
- `NightSummaryBuilder` continues to assume non-empty readings; the
  recovery path is responsible for checking emptiness before calling it, as
  it does for the normal disconnect path today.

## Testing

- `SleepSessionDao`/recovery logic: given a fake DAO with an unfinalized
  session plus readings, repository init should call
  `recordNightlySummary` with the expected `NightlySummary` and then
  finalize the session (unit test against `SleepRepositoryImpl` with a
  fake/in-memory DAO, following the existing pure-function/reducer test
  style used for `SleepScoreCalculator`/`RecoveryScoreCalculator`).
- Empty-session recovery: unfinalized session with zero readings results in
  no `recordNightlySummary` call, session still finalized.
- Batch flush cadence: given a simulated stream of readings over time,
  verify flush happens at the 20-reading and 30-second boundaries and not
  before.
- Existing 34 unit tests must continue passing; `assembleDebug` must remain
  green.
