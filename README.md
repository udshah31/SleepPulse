# SleepPulse

![CI](https://github.com/udshah31/SleepPulse/actions/workflows/ci.yml/badge.svg)

A native Android sleep/recovery tracking companion app (Kotlin, Jetpack Compose), in the
spirit of Eight Sleep or Whoop. This is **Phase 1** of the build: a working single-module
app with the core architecture, screens, and animations in place. BLE hardware
integration, the full multi-module split, automated tests, and CI are scoped for a
follow-up hardening pass (see "What's deferred" below).

## Architecture

### MVI (Model-View-Intent)

Each screen has three pieces:

- **Intent** (`*Contract.kt`) — a sealed class of every user action the screen supports
  (e.g. `DashboardIntent.BeginWindDown`).
- **State** (`*Contract.kt`) — an immutable data class holding everything the Composable
  needs to render. Composables never hold their own business state.
- **ViewModel** (`*ViewModel.kt`) — takes an `Intent` via `onIntent()`, reduces it against
  the current state, and publishes the new state through a `StateFlow`. Composables
  `collectAsState()` that flow and are otherwise dumb — no business logic lives in a
  Composable body.

This keeps state changes unidirectional and testable: given a starting `State` and an
`Intent`, the resulting `State` is deterministic and doesn't require a UI to verify.

Why MVI here specifically: the Dashboard mixes three concurrent, long-lived streams (live
sensor readings, connection state, and a multi-step wind-down flow) that all need to
converge into one coherent screen state. A single `StateFlow<DashboardState>` as a
join point avoids the alternative of scattering multiple independent `mutableStateOf`
flags across the Composable and reasoning about their combinations by hand.

### Data source abstraction

`data/source/SensorDataSource` is the interface the rest of the app depends on:

```kotlin
interface SensorDataSource {
    val connectionState: Flow<SensorConnectionState>
    fun readings(): Flow<SensorReading>
    suspend fun connect()
    suspend fun disconnect()
}
```

Two implementations:

- **`SimulatedSensorDataSource`** — a coroutine ticker that walks heart rate and HRV
  toward stage-dependent targets (rather than pure random jitter) and cycles through
  `AWAKE → LIGHT → DEEP → LIGHT → REM → LIGHT`, so a demo session looks like a
  plausible night rather than white noise.
- **`BleSensorDataSource`** — a real `BluetoothGatt` client stub. It targets the
  standard Bluetooth SIG Heart Rate Service (`0x180D`)/Measurement Characteristic
  (`0x2A37`), handles the connect → discover services → enable notifications →
  parse-characteristic lifecycle correctly, but has no scan flow wired up yet (see
  below) and is **not bound** by Hilt today.

`di/AppModule.kt` binds `SensorDataSource` to `SimulatedSensorDataSource`:

```kotlin
@Binds
abstract fun bindSensorDataSource(impl: SimulatedSensorDataSource): SensorDataSource
```

**To swap in the real BLE source** once you have a peripheral to test against:

1. Change the `@Binds` target in `AppModule.kt` from `SimulatedSensorDataSource` to
   `BleSensorDataSource`.
2. Add a BLE scan (`BluetoothLeScanner`) somewhere in the connect flow — likely
   triggered from Settings — that calls `BleSensorDataSource.setTargetDevice(address)`
   before `connect()` is invoked. Nothing else in the app needs to change: the
   `SleepRepository` and every ViewModel above it only see the `SensorDataSource`
   interface.
3. `hrvMillis` and `sleepStage` are not part of the standard Heart Rate Service — a real
   product would need a vendor-specific characteristic (or a second sensor) for those;
   `BleSensorDataSource` currently emits placeholder values for them.

Nothing downstream — repository, ViewModels, UI — imports either implementation
directly, only the `SensorDataSource` interface, so this swap never touches business logic.

### Local caching

`SleepRepository` sits between the data source and the ViewModel layer, and between
Room and the ViewModel layer, so either side is independently swappable:

- `data/local/` — Room entity/DAO/database for the last 30 nights (`observeRecent()`
  is capped to 30 rows; `trimToLast30Days()` prunes older rows on write).
- `data/repository/SleepRepositoryImpl` — converts between the Room entity shape and
  the domain-level `NightlySummary` model, and re-exposes the live sensor `Flow`s.

ViewModels depend on `SleepRepository` only — never on Room or `SensorDataSource`
directly.

### Dependency injection

Hilt wires the graph: `SleepPulseApp` (`@HiltAndroidApp`), `MainActivity`
(`@AndroidEntryPoint`), each `@HiltViewModel`, and `di/AppModule.kt` (`@Binds`/`@Provides`
for the repository, data source, and Room database).

## Screens

- **Dashboard** (`ui/dashboard/`) — animated circular sleep-score gauge
  (`ui/components/SleepScoreGauge.kt`, arc sweep + color driven by an `Animatable`), live
  heart-rate/HRV line charts that animate their vertical scale as new points arrive
  (`ui/components/LiveMetricChart.kt`), and a wind-down flow with `AnimatedContent`
  transitions between steps (`ui/dashboard/WindDownFlow.kt`).
- **History** (`ui/history/`) — a scrollable list of cached nights with a trend arrow
  (▲/▼/―) computed against the previous night's score.
- **Settings** (`ui/settings/`) — simulated-vs-BLE data source preference and a
  temperature unit toggle. (The data-source radio button currently only records the
  user's preference; see "What's deferred.")

## Running the app

```
./gradlew :app:assembleDebug
# or, with a device/emulator connected:
./gradlew :app:installDebug
```

Requires the Android SDK at the path in `local.properties` (`sdk.dir`); JDK 17+.

## What's deferred to the hardening pass

This phase intentionally stops short of the full original spec so the core could be
verified end-to-end first:

- **BLE scan flow** — `BleSensorDataSource` has correct GATT plumbing but no scan UI to
  pick a real peripheral's address.
- **Multi-module Gradle split** (`:app`, `:data`, `:domain`, `:ui-components`) — currently
  one `:app` module. The package structure (`data/`, `ui/`, `di/`) already mirrors where
  the module boundaries would land.
- **Automated tests** — unit tests for `SleepScoreCalculator`/ViewModels/repository, and
  an Espresso/Compose UI test for the Dashboard.
- **GitHub Actions CI** — lint + unit tests + `assembleDebug` on push.

Verified manually on an emulator for this phase: Dashboard renders and animates with live
simulated data, sensor connect/disconnect toggles correctly, the wind-down flow steps
through with its transition animation, History and Settings render (History is
legitimately empty — nothing calls `recordNightlySummary` yet, since there's no
full-night-completion flow in this phase).
