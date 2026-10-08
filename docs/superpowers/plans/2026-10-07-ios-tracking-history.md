# iOS Simulated Tracking and History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Start one real-time simulated iOS session, durably save or recover it, and display saved summaries in native History.

**Architecture:** A common persisted repository owns serialized lifecycle commands and recording. A Room storage adapter commits finalization atomically. An iOS controller delivers cancellable main-dispatcher snapshots to one Swift application store shared by Home and History.

**Tech Stack:** Kotlin 2.3.21, Room 2.8.4 with bundled SQLite, kotlinx-coroutines 1.8.1, SwiftUI/iOS 17+, static SleepPulseShared framework.

**Spec:** `docs/superpowers/specs/2026-10-07-ios-simulated-tracking-history-design.md`.

## Global Constraints

- Native SwiftUI, iOS 17+, iPhone and iPad, existing Calm Night theme.
- Existing static `SleepPulseShared` framework and opt-in iOS targets.
- Room database version remains 5; entity fields, migrations and exported schemas remain unchanged.
- Preserve existing scoring and duration formulas, including nullable HRV.
- Dedicated Application Support `sleeppulse-simulated.db`; identify Home and History data as simulated.
- Keep the Android repository, Health Connect integration, services, and UI behavior intact.
- Foreground-only simulation, one real second between readings, actual epoch timestamps.
- No commit, push, or merge without a user request; stage explicit paths only if subsequently requested.
- No generated frameworks, reports, local configuration, or databases in Git.
- Native-safe common-test names must omit parentheses and commas.

## File map and interfaces

All Kotlin paths below are relative to `shared/src/<sourceSet>/kotlin/com/sleeppulse/shared/`.

- `commonMain/sensor/RealtimeSimulatedSensorDataSource.kt`: one hot sensor producer with injected scope and clock.
- `commonMain/repository/NightSummaryMappings.kt`: entity conversions and larger-minute/incoming-tie retention with tag union.
- `commonMain/repository/SessionStorage.kt`: persistence seam and Room implementation; no schema changes.
- `commonMain/repository/PersistedSleepRepository.kt`: recovery, serialized lifecycle, recorded-only rolling buffer, errors.
- `iosMain/db/IosDatabaseFactory.kt`: Room constructor, migrations, bundled SQLite and background query dispatcher.
- `iosMain/tracking/IosTrackingSnapshot.kt`: primitive snapshots and cancellable observation token.
- `iosMain/tracking/IosTrackingController.kt`: database lifetime, initialization/retry, command dispatch and main-thread callbacks.
- `commonTest/sensor/RealtimeSimulatedSensorDataSourceTest.kt`: cadence, producer ownership and reset.
- `commonTest/repository/PersistedSleepRepositoryTest.kt`: handwritten storage/sensor fakes and lifecycle/failure cases.
- `iosTest/db/IosPersistenceTest.kt`: real close/reopen/recovery and transactional rollback.
- `iosApp/SleepPulse/Shared/TrackingModels.swift`: native status, metric and night values.
- `iosApp/SleepPulse/Shared/TrackingStore.swift`: application-scoped controller/observer and finite background-save task.
- `iosApp/SleepPulse/Navigation/SleepPulseRootView.swift`: Home/History tabs and scene phase.
- `iosApp/SleepPulse/Dashboard/DashboardViewModel.swift`, `DashboardView.swift`: observed state and Start/Stop UI.
- `iosApp/SleepPulse/History/HistoryViewModel.swift`, `HistoryView.swift`: observed history and date-only formatting.
- `iosApp/SleepPulse/SleepPulseApp.swift`: single store injection.
- `iosApp/SleepPulseTests/TrackingStoreTests.swift`, `DashboardViewModelTests.swift`: real framework persistence and lifecycle.
- `iosApp/SleepPulse.xcodeproj/project.pbxproj`: register new source and test files.
- `iosApp/README.md`, `README.md`: current capabilities and verification instructions.

### Task 1: Real-time sensor

**Consumes:** existing `SensorDataSource`, `SensorReading`, `SleepStage`.
**Produces:** `RealtimeSimulatedSensorDataSource(scope: CoroutineScope, nowMillis: () -> Long)` implementing `SensorDataSource`.

- [x] Add a coroutine test demonstrating actual clock use and shared production:

```kotlin
val sensor = RealtimeSimulatedSensorDataSource(backgroundScope) { 123_000L + testScheduler.currentTime }
val first = mutableListOf<SensorReading>()
val second = mutableListOf<SensorReading>()
backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sensor.readings().toList(first) }
backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sensor.readings().toList(second) }
sensor.connect()
runCurrent()
advanceTimeBy(2_001)
runCurrent()
assertEquals(listOf(123_000L, 124_000L, 125_000L), first.map { it.timestampMillis })
assertEquals(first, second)
sensor.disconnect()
advanceTimeBy(3_000)
assertEquals(3, first.size)
```

- [x] Run `./gradlew :shared:testAndroidHostTest --console=plain`; confirm the missing feature fails.
- [x] Implement a hot shared flow, one idempotent producer, immediate initial sample and `delay(1_000)` afterward. Deterministic stages cycle AWAKE/LIGHT/DEEP/LIGHT/REM/LIGHT every 20 ticks; deterministic small HR/HRV offsets reset on every connection.
- [x] Add disconnect/reconnect reset and no-observer/no-start coverage; run the shared suite.

### Task 2: Persisted repository and transaction adapter

**Consumes:** Task 1 sensor, existing DAOs, NightSummaryBuilder, Room writer transactions.
**Produces:**

```kotlin
interface SessionStorage {
    fun recentNights(): Flow<List<NightlySummary>>
    suspend fun create(startMillis: Long): SleepSessionEntity
    suspend fun append(sessionId: Long, reading: SensorReading)
    suspend fun unfinished(): List<SleepSessionEntity>
    suspend fun finish(session: SleepSessionEntity): NightlySummary?
    suspend fun record(summary: NightlySummary)
    suspend fun updateTags(date: LocalDate, tags: List<String>)
}
// RoomSessionStorage(database: SleepPulseDatabase) : SessionStorage
// PersistedSleepRepository(sensor, storage, scope, nowMillis) : SleepRepository
// Additional repository API: recover(), sessionState: StateFlow<TrackingSessionState>
// State fields: phase, readings, elapsedSeconds, error, notice.
// TrackingPhase: RECOVERING, IDLE, STARTING, TRACKING, SAVING, FAILED.
```

- [x] Write failing tests with handwritten storage/sensor fakes for initialization gating, duplicate Start/Stop, persisted-only publication, full-session summary and write failure.

```kotlin
repository.recover()
repository.connectSensor()
repository.connectSensor()
sensor.send(reading(0))
repeat(65) { sensor.send(reading((it + 1) * 1_000L)) }
assertEquals(40, repository.sessionState.value.readings.size)
val summary = repository.disconnectSensor()
assertEquals(1, summary?.totalSleepMinutes)
assertFalse(repository.isTracking.value)
assertEquals(null, repository.disconnectSensor())
```

- [x] Run shared tests and confirm the missing repository/storage API fails.
- [x] Implement mappings and retention: choose existing only when its `totalSleepMinutes` is strictly larger, then merge distinct tags.
- [x] Implement Room storage using `database.useWriterConnection { it.withTransaction(IMMEDIATE) { ... } }`: read all persisted readings, build using session start date, upsert merged history, trim dates, finalize/clear. Empty sessions finalize without a night. Keep all operations in one transaction.
- [x] Implement command mutex, explicit recovery-before-start, collector subscription before sensor connection, monotonic reading timestamps, non-cancellable in-flight insert completion, rolling 40-sample buffer, full stored-session finalization and retryable structured error state. Recovery processes start/id order, attempts other sessions after a failure, then reports failure.
- [x] Test empty/single/null-HRV cases, backwards clock, cross-midnight start date, in-flight insert on Stop, failed recovery retaining data, same-date selection/tags and rolling score independence. Run shared tests.

### Task 3: iOS Room/controller bridge

**Consumes:** Tasks 1–2.
**Produces:**

```kotlin
object IosDatabaseFactory { fun open(path: String): SleepPulseDatabase }
class IosTrackingController(databasePath: String) {
    fun observe(callback: (IosTrackingSnapshot) -> Unit): TrackingObservation
    fun start()
    fun stop()
    fun retry()
    fun setForeground(foreground: Boolean)
    fun close()
}
// Snapshot: phase:String, score:Int?, elapsedSeconds:Long,
// latest:IosReadingSnapshot?, nights:List<IosNightSnapshot>, error:String?, notice:String?
// Reading: timestampMillis, heartRateBpm, hrvMillis, stage:String.
// Night: epochDay, isoDate, score, totalMinutes, deepMinutes, remMinutes, averageHeartRate, averageHrv.
// TrackingObservation.cancel() explicitly cancels its one callback collection.
```

- [x] Add failing `iosTest` real database close/reopen/recovery tests. Use temporary absolute paths, close all databases and delete SQLite/WAL/SHM files during cleanup.

```kotlin
val db = IosDatabaseFactory.open(path)
val store = RoomSessionStorage(db)
val session = store.create(1_700_000_000_000)
store.append(session.sessionId, SensorReading(session.startEpochMillis, 60, null, SleepStage.LIGHT))
db.close()
val reopened = IosDatabaseFactory.open(path)
val persisted = RoomSessionStorage(reopened)
val unfinished = persisted.unfinished().single()
persisted.finish(unfinished)
assertEquals(null, persisted.recentNights().first().single().avgHrvMillis)
assertTrue(persisted.unfinished().isEmpty())
reopened.close()
```

- [x] Run `./gradlew -PenableIosTargets=true :shared:iosSimulatorArm64Test --console=plain`; confirm missing iOS factory failure.
- [x] Implement Room factory with bundled driver, existing migrations and Default query context. No destructive fallback or swallowed open error.
- [x] Implement controller on Main with SupervisorJob and repository jobs; catch ordinary command/open/history errors, preserve cancellation, retry opening/recovery. Gate queued Start with foreground flag. Snapshot score is calculated by existing calculator using recorded last-40 buffer; callback handles cancel explicitly.
- [x] Add SQL trigger abort integration test proving finalization rolls back summary/session/readings together; add history newest-30 retention and schema version assertion. Run simulator Kotlin tests and link the Debug ARM64 framework.

### Task 4: Swift application store and native UI

**Consumes:** Task 3 exported controller/snapshots.
**Produces:** `TrackingStore` owning controller once; published `TrackingState`; `start()`, `stop()`, `retry()`, `sceneChanged(_:)`, `close()`; Home and History consume this shared store.

- [x] Add XCTest demonstrating initial unavailable metrics, real Start/Stop and disk reload:

```swift
let store = TrackingStore(databasePath: temporaryDatabasePath)
await waitUntil { store.state.phase == .idle }
XCTAssertNil(store.state.score)
store.start()
await waitUntil { store.state.latestReading != nil }
store.stop()
await waitUntil { store.state.phase == .idle && !store.state.nights.isEmpty }
store.close()
let reopened = TrackingStore(databasePath: temporaryDatabasePath)
await waitUntil { reopened.state.phase == .idle }
XCTAssertEqual(reopened.state.nights.count, 1)
XCTAssertNil(reopened.state.latestReading)
reopened.close()
```

- [x] Register the new test and run `xcodebuild test` on the available simulator; confirm missing store behavior fails.
- [x] Implement immutable native models, nullable Kotlin boxed-number conversion and ISO date-only formatting. Start enabled only idle/foreground; Stop only tracking; errors offer Retry.
- [x] Implement one main-actor store with cancellable callback, Application Support directory, finite UIKit background task released on idle/failed/expiration, and close cancelling observation/controller. `.inactive` leaves tracking alone; `.background` sets controller foreground false and requests Stop; `.active` only restores eligibility.
- [x] Adapt Dashboard ViewModel to observe the store. Replace fixed fixture runtime with recorded score/elapsed/latest metrics, loading/saving/error state and accessible controls. Use the existing card/gauge theme; unknown metrics render em dash and speak unavailable.
- [x] Add History ViewModel/View, stable epoch-day identity, simulated empty state, newest-first score/duration/deep/REM/averages. Zero minutes displays `<1 min`.
- [x] Add root tab view, app-scoped store injection and scene lifecycle, register Swift sources, retain calculator fixture tests.
- [x] Add real framework tests for inactive/background/re-entry, Start/background race, invalid database/open Retry and shared Home/History observation. Run all Swift tests.

### Task 5: End-to-end verification and documentation

**Consumes:** Complete native application.
**Produces:** Verified feature and updated docs, with no Git integration until requested.

- [x] Install and launch built app on iPhone 17 Pro simulator. Start and observe at least 65 seconds of actual time; verify History nonzero minutes and recorded elapsed not accelerated.
- [x] Test background save and interrupted-process relaunch recovery; ensure no auto-resume. Inspect Home/History screenshots, Dynamic Type and accessibility labels/safe areas.
- [x] Run full regressions:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :shared:testAndroidHostTest :app:test lint :app:assembleDebug :wear:assembleDebug --console=plain
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew -PenableIosTargets=true :shared:iosSimulatorArm64Test --console=plain
xcodebuild -project iosApp/SleepPulse.xcodeproj -scheme SleepPulse -destination 'platform=iOS Simulator,id=09B95FEC-815B-4E54-81B1-D0A3FF8F0AE1' -derivedDataPath iosApp/build/DerivedData test CODE_SIGNING_ALLOWED=YES
git diff --check
git status --short
```

- [x] Update README capabilities, foreground/persistence behavior and tests, update plan checkboxes and record verification evidence.
- [x] Review schema diff (must be empty), Android implementation diff (must preserve behavior), ownership/cancellation, command/error races and generated-file exclusions. Report branch/worktree and verification results.

## Self-review

Every specification section maps to Tasks 1–5. The storage seam is a testability boundary around the same existing DAOs and writer transaction, not a new persistence schema. All current integration task names and simulator UUID were confirmed against the repository. No automatic Git integration is authorized.

## Verification and integration record

- 2026-10-07: all 82 shared Android host tests, 85 shared iOS simulator tests and 172
  Android app unit tests passed. Lint and phone/Wear debug builds passed.
- The complete Swift/Xcode suite passed on iPhone 17 Pro, iOS 26.2: 13 tests, zero
  failures. The suite includes a real 65-second tracking flow, tab switching, background
  save, relaunch persistence and large-text screenshots.
- Review identified a pending-Start/background-task window and asynchronous
  shutdown/reopen ordering. The Swift store now tracks a pending Start before dispatch,
  and same-path reopen callers await `closeAndWait()`. The reviewer rechecked these
  changes and approved the feature for merging.
- The original approved specification accompanies this plan. Simulator UI tests use
  `CODE_SIGNING_ALLOWED=YES` for local ad-hoc runner signing.
- `git diff --check` passed; no database schema or Android implementation changes.
- The user delegated the integration choice; selected local commit and merge into
  `master`, followed by verification on the merged result and worktree cleanup.
