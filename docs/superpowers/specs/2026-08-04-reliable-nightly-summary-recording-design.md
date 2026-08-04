# Reliable Nightly Summary Recording

## Problem

`recordNightlySummary` is already called, but only from `DashboardViewModel.toggleConnection()`,
using an in-memory `sessionReadings` list the ViewModel itself collects from
`repository.liveReadings()`. Meanwhile `SleepTrackingService` — the foreground service that keeps
tracking alive overnight — independently drives `SleepRepository.connectSensor()` /
`disconnectSensor()`, which durably flushes readings to Room (`SessionReadingEntity`) every 20
readings or 30 seconds via `SleepRepositoryImpl`.

`SleepRepositoryImpl.disconnectSensor()` currently just calls `sessionDao.finalizeAndClear(id)`,
which deletes the session's persisted readings without ever turning them into a `NightlySummary`.
The only code path that builds a summary from persisted readings is
`recoverUnfinalizedSessions()`, called once at app startup — and only for sessions that were never
finalized (i.e. the app crashed mid-session).

Net effect: if the Activity/ViewModel process dies overnight (very likely — screen off, memory
pressure) while the foreground service keeps tracking, the ViewModel's in-memory list is gone by
morning. The service's normal stop path then finalizes and **deletes** the durably-persisted
readings without ever recording a night. The user loses that night's history despite the data
having been safely written to disk the whole time.

## Goal

Make a completed sleep session reliably become a `NightlySummary` regardless of whether the
Activity/ViewModel is alive when the session ends — using the data that's already being durably
persisted to Room.

## Design

### 1. `SleepRepositoryImpl` owns finalization

- Track the active session's `SleepSessionEntity` (not just its id) so the start time is available
  at disconnect time.
- `disconnectSensor()` changes signature to `suspend fun disconnectSensor(): NightlySummary?`.
  After the final `flush()`:
  - Read back the session's persisted readings via `sessionDao.readingsFor(sessionId)`.
  - If empty: `finalizeAndClear(sessionId)`, return `null`.
  - If non-empty: build a `NightlySummary` via `NightSummaryBuilder.build(readings, date)`, where
    `date` is derived from the session's **start** `epochMillis` (matching what
    `recoverUnfinalizedSessions()` already does) — not "now" — so a session that starts at 11pm and
    ends at 7am is attributed to the same calendar day consistently regardless of which path
    recorded it.
  - Call the existing `recordNightlySummary(summary)`. **Only if that succeeds**, call
    `finalizeAndClear(sessionId)` and return the summary.
- Extract the shared "read readings → build summary → record" step into one private helper used by
  both `disconnectSensor()` and `recoverUnfinalizedSessions()`, eliminating the current
  duplication between the two paths.
- `SleepRepository` interface: `disconnectSensor()` return type changes from `Unit` to
  `NightlySummary?`.

### 2. `SleepSessionFinalizer` — new plain-Kotlin orchestrator

A small class with no Android `Service`/`Context` dependency, so it stays fully unit-testable:

```kotlin
class SleepSessionFinalizer @Inject constructor(
    private val repository: SleepRepository,
    private val notifier: SleepSummaryNotifier,
    private val widgetRefresher: WidgetRefresher,
) {
    suspend fun finalize() {
        val summary = try {
            repository.disconnectSensor()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Leave the session unfinalized; recoverUnfinalizedSessions() retries it on next launch.
            return
        } ?: return

        val recovery = RecoveryScoreCalculator.scoreLatest(repository.recentNights().first())
        notifier.notify(summary, recovery)
        widgetRefresher.refresh()
    }
}

interface WidgetRefresher {
    suspend fun refresh()
}
```

- `WidgetRefresher` is implemented by a thin Android wrapper (`SleepPulseWidgetRefresher` or
  similar, in `di`/`widget` package) that calls `SleepPulseWidget.refresh(context)`. This keeps the
  `Context` dependency behind a seam so `SleepSessionFinalizer` itself needs no Android framework
  classes and can be constructed directly in tests.
- Bind `WidgetRefresher` in `AppModule.kt`.

### 3. `RecoveryScoreCalculator.scoreLatest(nights)`

Extract the existing private `computeRecovery` logic out of `DashboardViewModel` into a new
function on `RecoveryScoreCalculator` (same file as the existing `score` function):

```kotlin
fun scoreLatest(nights: List<NightlySummary>): RecoveryResult? {
    val lastNight = nights.firstOrNull() ?: return null
    val baseline = nights.drop(1).take(7)
    return score(lastNight, baseline)
}
```

Both `DashboardViewModel` (for live dashboard display) and `SleepSessionFinalizer` (for the
notification) call this shared function instead of duplicating the logic.

### 4. `SleepTrackingService` calls the finalizer

Replace the direct `repository.disconnectSensor()` calls in `onStartCommand` (stop action) and
`onDestroy()` with `sleepSessionFinalizer.finalize()`. The service no longer needs to know about
notifications or widget refresh directly — `SleepSessionFinalizer` owns that orchestration, and the
service stays a thin Android shell.

### 5. `DashboardViewModel` simplifies

`toggleConnection()`'s disconnect branch drops its manual `NightSummaryBuilder.build(...)`,
`repository.recordNightlySummary(...)`, `notifier.notify(...)`, and
`SleepPulseWidget.refresh(...)` calls entirely — it just starts/stops
`SleepTrackingService` as it does today for connect. The ViewModel's separate `sessionReadings`
list and live `sleepScore`/chart updates are untouched; those are for the live gauge only and
unrelated to persistence.

## Error handling

- `recordNightlySummary()` failing (Room or Health Connect write error) inside
  `disconnectSensor()` must **not** finalize/clear the session — leaving it unfinalized so
  `recoverUnfinalizedSessions()` retries it on next app launch, the same recovery path that already
  exists for crashes.
- `SleepSessionFinalizer.finalize()` catches non-`CancellationException` exceptions from
  `repository.disconnectSensor()`, logs, and returns without crashing the service — mirroring the
  existing catch block in `recoverUnfinalizedSessions()`.
- Zero readings at disconnect: `disconnectSensor()` still finalizes/clears the (empty) session row
  so it doesn't linger forever, but returns `null` — `finalize()` no-ops (no notification, no
  widget refresh), matching current behavior.

## Testing

- `SleepRepositoryImplTest` (extend existing):
  - Disconnect with accumulated readings records a summary dated from the session's **start**
    time and finalizes the session.
  - Disconnect with no readings records nothing but still finalizes the session.
  - A `recordNightlySummary` failure (simulate via a fake DAO throwing, following the existing
    `readingsForFailures` pattern in `FakeSleepSessionDao`) leaves the session unfinalized.
- New `SleepSessionFinalizerTest`:
  - Notifies and refreshes the widget only when `disconnectSensor()` returns a non-null summary.
  - Recovery passed to the notifier is computed against the just-recorded night (last night +
    baseline), not stale state.
  - A repository failure during finalize doesn't throw out of `finalize()`.
- `DashboardViewModelTest`: remove the now-obsolete recording/notify assertions on
  `ToggleSensorConnection` (`disconnecting after readings accumulated records a nightly summary`,
  `disconnecting after readings fires notifier...`, `disconnecting with no readings does not
  record/fire...`, `disconnecting notifies with recovery computed against the just-recorded
  summary...`); keep the two tests asserting the service start/stop calls.
- `RecoveryScoreCalculatorTest`: add coverage for the new `scoreLatest(nights)` helper.

## Out of scope

- BLE scan flow wiring (separate, already-identified follow-up item).
- Any change to `SleepScoreCalculator` or the live dashboard chart/gauge behavior.
- Adding Robolectric or instrumented tests for `SleepTrackingService` itself — deliberately avoided
  by keeping the service a thin shell with all logic in the plain-Kotlin `SleepSessionFinalizer`.
