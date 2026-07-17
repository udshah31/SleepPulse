# Automated tests for core logic, ViewModels, and repository

## Context

SleepPulse Phase 1 shipped with no automated tests (`app/src/test` and
`app/src/androidTest` are empty), called out in the README as deferred. This is
the first hardening pass: add a JVM unit-test safety net for the pieces with
the most business logic, without requiring an emulator/device.

## Scope

In scope (pure JVM unit tests, `app/src/test`):
- `SleepScoreCalculator`
- `DashboardViewModel`
- `HistoryViewModel`
- `SettingsViewModel`
- `SleepRepositoryImpl`

Out of scope for this pass:
- `NightlySummaryDao` instrumented tests against real Room/SQLite
- Compose/Espresso UI tests for the Dashboard
- `BleSensorDataSource` (not bound by Hilt, no scan flow yet — nothing to test)

## Test doubles

Hand-written fakes, not Mockito, since `SleepRepository`, `SensorDataSource`,
and `NightlySummaryDao` are small interfaces and Flow-returning mocks are
awkward to drive with Mockito's `whenever`/stubbing model.

- **`FakeSensorDataSource`** (implements `SensorDataSource`) — a
  `MutableStateFlow<SensorConnectionState>` for `connectionState`, a
  `MutableSharedFlow<SensorReading>` for `readings()` that test code emits
  into directly, and `connect()`/`disconnect()` that flip connection state and
  increment call counters so tests can assert they were invoked.
- **`FakeNightlySummaryDao`** (implements `NightlySummaryDao`) — an in-memory
  `MutableList<NightlySummaryEntity>` backed by a `MutableStateFlow` for
  `observeRecent()` (already sorted/limited to mirror the real query);
  `trimToLast30Days()` reproduces the real SQL's "keep newest 30 by
  `dateEpochDay`" semantics in plain Kotlin.
- **`FakeSleepRepository`** (implements `SleepRepository`) — used directly by
  the three ViewModel tests, independent of the two fakes above, since
  ViewModels never see the DAO or data source directly.

All fakes live under `app/src/test/java/com/sleeppulse/app/testutil/`.

## Coroutine/Flow test infra

- **`MainDispatcherRule`** — a JUnit4 `TestWatcher` that swaps
  `Dispatchers.Main` for a `StandardTestDispatcher` in `starting()` and resets
  it in `finished()`. Applied via `@get:Rule` in every ViewModel test class
  (ViewModels use `viewModelScope`, which dispatches on `Dispatchers.Main`).
- **Turbine** (already a test dependency) for asserting `StateFlow`/`Flow`
  emissions instead of manual `launch { collect { } }` boilerplate.

## Test cases

**`SleepScoreCalculatorTest`**
- Empty reading list → score `0`.
- High HRV + low resting HR → score near the top of the range.
- Low HRV + high resting HR → score near the bottom of the range.
- Single reading still produces a valid score.
- Extreme inputs stay clamped to `[0, 100]`.

**`DashboardViewModelTest`**
- `Start`: connection-state and reading emissions from the fake repository
  update `state.connectionState`, `state.latestReading`, `state.sleepScore`,
  and flip `isLoading` to `false`.
- Reading history caps at `MAX_CHART_POINTS` (40) — pushing 45 readings leaves
  `state.recentReadings.size == 40`, oldest ones dropped.
- `ToggleSensorConnection` calls `disconnectSensor()` when currently connected
  and `connectSensor()` when not.
- `BeginWindDown` → `AdvanceWindDownStep` sequence walks
  `BREATHE → DIM_LIGHTS → SET_ALARM → DONE`, and advancing past `DONE` stays
  `null`/no-ops.
- `CancelWindDown` clears `windDownStep` back to `null` from any step.

**`HistoryViewModelTest`**
- `Load` with multiple nights (newest-first, as `recentNights()` promises)
  produces `NightWithTrend` entries whose trend matches
  `NightlySummary.trendAgainst` against the following (chronologically
  earlier) list element.
- Empty night list → empty `state.nights`, `isLoading == false`.

**`SettingsViewModelTest`**
- `SetDataSource` updates `state.dataSourceMode` only.
- `SetTemperatureUnit` updates `state.temperatureUnit` only.
- The two intents don't clobber each other's field when applied in sequence.

**`SleepRepositoryImplTest`**
- `recordNightlySummary` calls `dao.upsert(...)` with the correctly-mapped
  entity, then `dao.trimToLast30Days()`, in that order.
- `recentNights()` maps `NightlySummaryEntity` rows to `NightlySummary`
  correctly, including the `dateEpochDay` ↔ `LocalDate` round-trip.
- `liveReadings()` and `connectionState` pass through unmodified from the
  fake `SensorDataSource`.

## Dependencies

No new Gradle dependencies. `mockito-core`/`mockito-kotlin` remain declared
in `app/build.gradle.kts` but are unused by this pass — left in place in case
a future test genuinely needs a mock; not removed since that's out of scope
here.

## Out of scope / deferred

- Room DAO instrumented tests (`app/src/androidTest`) verifying
  `trimToLast30Days()` against real SQLite.
- Compose UI test for the Dashboard screen.
- Tests for `BleSensorDataSource` (blocked on the scan flow being wired up).
