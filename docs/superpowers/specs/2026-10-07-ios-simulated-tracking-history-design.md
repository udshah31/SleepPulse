# iOS Real-Time Simulated Tracking, Persistence, and History

## Status and approved decisions

The user selected this milestone following the native SwiftUI scaffold and approved
real-time simulation and foreground-only tracking on 2026-10-07.
The written specification was approved for implementation on 2026-10-07.

- Emit approximately one reading per real second using actual epoch timestamps.
- Persist readings during the session.
- Stop and save when the user stops tracking or the app enters the background.
- Recover interrupted saves from persisted readings on the next launch.
- Keep tracking running when switching between Home and History.

## Goal

Make the iOS app's simulated tracking functional end to end: Start, live readings and
score, Stop, durable nightly summary, and a native History screen that survives relaunch.
Re-use the shared Room KMP schema, `SleepRepository` contract, scoring, and summary builder.

## Scope and constraints

- Native SwiftUI, iOS 17+, iPhone and iPad, existing Calm Night theme.
- Existing static `SleepPulseShared` framework and opt-in iOS targets.
- Room database version remains 5; entity fields, migrations and exported schemas remain unchanged.
- Preserve existing scoring and duration formulas, including nullable HRV.
- Use a dedicated simulated-data database so the saved demo nights are distinguishable from
  future actual sensor records. Both Home and History identify their data as simulated.
- Keep the Android repository, Health Connect integration, services, and UI behavior intact.
- This milestone adds Home and History tabs. Tag editing, analytics, recovery screens, BLE,
  HealthKit, alarms, continuous background tracking, and iOS CI are subsequent milestones.
- Implementation uses an isolated worktree. No commit, push, or merge without a user request.

## Approach comparison

1. **Recommended: Kotlin repository with a small iOS bridge.** Keep Room access, session
   finalization and recovery in Kotlin. Swift receives immutable snapshots and issues commands.
   Reuses shared types and keeps the coroutine boundary contained.
2. **Swift-owned repository.** Swift would orchestrate suspend DAO calls and map shared entities,
   duplicating persistence and recovery rules in another language.
3. **Extract the existing Android repository into common code first.** This broadens the change
   to Android service, Health Connect and error-reporting seams before delivering the iOS flow.

Choose the first approach. Add a platform-neutral persisted repository in `commonMain` and
use it from an iOS-owned controller; Android continues using its existing implementation.
Keep iOS Room construction, framework callbacks, and lifecycle handling at the iOS boundary.

## Architecture and file responsibilities

```text
SwiftUI Home / History / scene lifecycle
                   |
Main-actor Swift application store + screen ViewModels
                   |
iOS Kotlin controller: commands + cancellable snapshot observation
                   |
PersistedSleepRepository : SleepRepository
        |                         |
Room KMP database        Real-time simulated SensorDataSource
        |
NightSummaryBuilder / SleepScoreCalculator
```

### Kotlin common code

- `shared/src/commonMain/kotlin/com/sleeppulse/shared/repository/PersistedSleepRepository.kt`: lifecycle serialization, creation
  and persistence of sessions, live readings after successful writes, summary finalization,
  recovery, recent-night flow, and tag preservation through the existing contract.
- `shared/src/commonMain/kotlin/com/sleeppulse/shared/repository/NightSummaryMappings.kt`: entity/domain conversions and same-date
  retention rule for this repository. Test against the current Android semantics.
- `shared/src/commonMain/kotlin/com/sleeppulse/shared/sensor/RealtimeSimulatedSensorDataSource.kt`: one deterministic simulation
  producer per session, actual timestamps, one-second cadence, controllable clock and scheduler.

### Kotlin iOS code

- `shared/src/iosMain/kotlin/com/sleeppulse/shared/db/IosDatabaseFactory.kt`: construct the existing Room database at
  an absolute path supplied by Swift; use `SleepPulseDatabaseConstructor`, bundled SQLite,
  migrations, and a background query coroutine context.
- `shared/src/iosMain/kotlin/com/sleeppulse/shared/tracking/IosTrackingController.kt`: own repository/sensor scope,
  initialize and recover, publish snapshots, and expose Start/Stop/Retry commands to Swift.
- `shared/src/iosMain/kotlin/com/sleeppulse/shared/tracking/IosTrackingSnapshot.kt`: Swift-friendly immutable values
  with primitive fields, nullable HRV, stable identifiers and status/error fields.

### Swift app (`iosApp/SleepPulse/`)

- `Shared/TrackingStore.swift`: main-actor owner of one Kotlin controller and its cancellable
  observation. Set up the database path and convert snapshots into native value types.
- `Shared/TrackingModels.swift`: immutable native tracking/history models and status enums.
- `Navigation/SleepPulseRootView.swift`: Home/History tabs and scene-phase handling.
- `Dashboard/DashboardViewModel.swift` and `DashboardView.swift`: render repository-backed
  state, Start/Stop, elapsed time, live score and latest metrics, loading/saving/error states.
- `History/HistoryViewModel.swift` and `HistoryView.swift`: render observed saved nights,
  empty state, date, score, recorded duration, stage totals and average metrics.
- `SleepPulseApp.swift`: create the shared store once for the app and inject it into the root.

Existing `SharedSleepScoring` and fixed fixtures may remain for calculator boundary tests
and SwiftUI previews; the running application no longer starts with the fixed score of 71.

## Simulation and live readings

- Start the simulator only after successful recovery and session-row creation.
- Emit the first reading immediately, then wait approximately one second between samples.
- Use the current wall-clock epoch timestamp for each sample; do not replace it with
  `start + tick * 1000`, accelerate timestamps, or fill gaps with fabricated readings.
- Heart rate and HRV follow a repeatable stage-dependent sequence through
  AWAKE, LIGHT, DEEP, LIGHT, REM, LIGHT; use deterministic tick-based values rather than
  nondeterministic random input. Reset the sequence for each new session.
- A single simulation job produces readings. Dashboard, persistence and History observers
  must not create additional sensor producers.
- Write every reading before making it visible as a successfully recorded live sample.
  Database writes run outside Swift's main actor.
- Display recorded elapsed seconds from the first through latest persisted timestamps;
  display live sleep score using `SleepScoreCalculator` over the last 40 persisted readings.
  Final saved scores and metrics use the complete persisted session through `NightSummaryBuilder`.
- Wall-clock regressions are skipped until a timestamp is later than the previous persisted
  timestamp, preventing negative summary durations. Skipped timestamps do not advance the
  visible recorded time or count as saved samples.

## Session lifecycle and concurrency

### Initialization

Create an Application Support directory owned by SleepPulse and open
`sleeppulse-simulated.db` there. Do not store the database in a disposable cache directory.
Initialize the controller and recover unfinished sessions before enabling Start.
History observes the Room recent-night flow once and publishes newest-first snapshots.

### Start

- Serialize commands; repeated Start does not create multiple rows, jobs, or collectors.
- Clear the previous live score/readings only after the new session is successfully created.
- Record session start epoch milliseconds. At finalization, the summary date uses
  `localDateAt(startEpochMillis)` in the device's current zone, matching Android. No new
  stored timezone field is introduced.
- Mark `isTracking` true for the actual active session and start the producer/collector.
- Disable duplicate control actions while starting, stopping or recovering.

### Stop

- Stop production and join the recording job before finalization so no sample can arrive
  after the final summary is built. Finish any in-flight database insert first.
- Treat duplicate Stop and concurrent background Stop as a single lifecycle operation.
- Build the summary from persisted readings, not the UI's last-40 buffer.
- Inside a Room write transaction, merge/upsert the nightly summary, trim to the last 30
  dates, then finalize and clear that session's readings. Roll back all these changes on failure.
- An empty session is finalized without creating a night. One reading is a valid summary
  with zero recorded minutes; preserve the existing builder behavior.
- On success, Home returns to idle and retains that session's last metrics until a new Start.
  History refreshes from Room. Relaunch starts Home idle with unavailable live metrics;
  saved summaries are available through History.
- If saving fails, stop simulation, retain persisted session data, surface an error and
  offer Retry save. Block new sessions until recovery succeeds.

### Background and interruptions

- Stop when SwiftUI `scenePhase` becomes `.background`, including screen locking.
  `.inactive` alone does not stop tracking; it covers temporary foreground interruptions.
- Switching between app tabs does not affect the app-scoped controller or session.
- Swift may request a finite UIKit background task only to finish saving. It does not
  continue simulation or make continuous background tracking claims.
- Finalization belongs to the controller's scope, not a screen task that is cancelled on
  navigation or backgrounding. Release any UIKit background task when saving finishes or expires.
- If suspension or termination interrupts finalization, durable readings remain available
  for launch-time recovery. No readings are generated to cover time spent in the background.
- Foreground re-entry never restarts simulation automatically.
- Backgrounding during Start or recovery prevents a pending Start from activating the
  simulator afterward; an explicit foreground Start is needed for the next session.

### Recovery

On launch or Retry save, finalize unfinished sessions ordered by start timestamp and then
session ID before Start is available. Empty sessions create no nightly row. A failure leaves that session and readings
intact; attempt the other unfinished sessions and report that recovery needs retry.
Never resume the simulator for an interrupted session or count the interruption as sleep.

## Night retention and History

- Keep the existing one-row-per-local-start-date key and newest-first, last-30-date policy.
- On a second session with the same date, compare the existing `totalSleepMinutes` field
  and keep the larger value; the incoming summary wins equal-duration ties, matching
  Android's `nightToKeep` rule. Merge both nights' tags without duplicates.
- If a shorter test session finishes, History continues showing the longer stored summary;
  the Home confirmation explains that History keeps the longest session for that date.
- Pass both epoch-day identity and ISO date to Swift. Display dates without converting
  date-only values through a timezone-shifted Foundation midnight instant.
- Show score, recorded duration, deep/REM totals, average heart rate and nullable average HRV.
  Use "<1 min" when recorded duration is zero; do not invent a minimum duration.
- HRV is an em dash when unknown. Screen-reader output says "unavailable", not "zero".
- Show a labelled simulated-history empty state until a real simulated session is saved;
  do not seed the database with prefilled sample nights.

## Swift/Kotlin observation and error contract

- Expose snapshot observation through a Kotlin callback API that returns an explicit
  cancellation handle. Hide Room, Flow, and coroutine ownership from Swift screens.
- Deliver snapshot callbacks on the main dispatcher. Swift's adapter updates its
  main-actor store and distributes state to Home and History without creating per-view jobs.
- Commands launch in the Kotlin controller's lifetime scope. Provide an explicit close
  operation to cancel observation, stop jobs and release the database when ownership ends.
- Before immediately reopening the same database path, await `TrackingStore.closeAndWait()`;
  its controller completion waits for in-flight inserts, scope cancellation and database close.
- Snapshots distinguish loading/recovering, idle, starting, tracking, saving and failed.
  Command failures become structured error state rather than uncaught Kotlin exceptions
  crossing into Swift. Preserve coroutine cancellation as cancellation.
- A reading-write failure stops production and moves to failed/recovery-required state;
  successfully persisted readings remain recoverable. Never publish the failed sample as saved.
- A database-open failure displays a Retry action. Do not silently replace a failed database
  with a new empty file or clear history.
- Keep simulated-data labels and foreground-only instructions visible:
  "Simulated tracking — keep the app open. Sessions save when the app goes into the background."

## Testing and acceptance

### Common tests

Use injected clocks, coroutine test scheduling and handwritten persistence fakes:

- No readings before Start or after Stop; one producer with multiple observers.
- Real-time timestamps, deterministic values and reset on new session.
- Idempotent Start/Stop and a reading arriving during Stop.
- Per-reading persistence before live publication; persistence failure leaves prior data intact.
- Final summary from the full persisted session, including empty/single-reading/nullable-HRV cases.
- Summary date from session start, including crossing midnight.
- Same-date larger-minute/incoming-tie selection, tag merge and last-30-date retention.
- Initialization waits for recovery; retries do not duplicate nights or discard failed sessions.
- A skipped backward timestamp never produces a negative interval.

### iOS Kotlin persistence integration

Run on the iOS simulator with a real temporary Room database:

- Record readings, finalize, close and reopen; saved night and nullable HRV survive.
- Recover an unfinished persisted session after reopen; recovery is idempotent.
- A failed finalization transaction leaves the raw session available and does not partially
  update history or clear readings.
- Database remains schema version 5; no new exported-schema contents.

### Swift tests and simulator checks

- Test real framework observation and Start/Stop; Home and History share one session.
- Loading/error state disables inappropriate actions and Retry re-enables Start after recovery.
- Observe History update after save and reload saved data after store recreation.
- Background saves once, including a Start/background race; `.inactive` does not stop;
  foreground re-entry does not resume.
- Run the app on a simulator, track for at least 65 seconds, stop and verify nonzero minutes
  in History. Verify live recorded time does not advance faster than wall time.
- Repeat with background save and interrupted-session relaunch recovery.
- Inspect Home/History screenshots, safe areas, Dynamic Type layout, and accessible labels.

### Regression verification

```bash
./gradlew :shared:testAndroidHostTest :app:test lint :app:assembleDebug :wear:assembleDebug
./gradlew -PenableIosTargets=true :shared:iosSimulatorArm64Test
xcodebuild -project iosApp/SleepPulse.xcodeproj -scheme SleepPulse \
  -destination 'platform=iOS Simulator,id=<available-simulator-uuid>' \
  -derivedDataPath iosApp/build/DerivedData test CODE_SIGNING_ALLOWED=YES
git diff --check
```

Use an actual available simulator UUID. Preserve Native-safe common-test names and keep
generated frameworks, database files, test reports and local configuration out of Git.

## Completion criteria

The running iOS app can start a single real-time simulated session, stop it reliably,
save or recover its persisted readings into a nightly summary, and show saved nights in
History after relaunch. Screen navigation preserves the session; backgrounding ends it.
Shared calculations, database schema and existing Android behavior remain compatible,
with the required Kotlin, Swift, simulator and Android checks passing.
