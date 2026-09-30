# SleepPulse

![CI](https://github.com/udshah31/SleepPulse/actions/workflows/ci.yml/badge.svg)

A native Android sleep/recovery tracking companion app (Kotlin, Jetpack Compose), in the
spirit of Eight Sleep or Whoop. It tracks a night from a heart-rate sensor (simulated, or a
real BLE heart-rate strap), scores sleep and recovery, and keeps a 30-night history. A
minimal Wear OS module (`:wear`) sits alongside the phone app (`:app`).

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
- **`BleSensorDataSource`** — a `BluetoothGatt` client for the standard Bluetooth SIG Heart
  Rate Service (`0x180D`)/Measurement Characteristic (`0x2A37`). `hrvMillis` and
  `sleepStage` are not part of that service, so it emits placeholder values for them (a
  real product needs a vendor characteristic or second sensor).

`SensorSourceManager` is what Hilt binds to `SensorDataSource`. It delegates to the simulated
or BLE source depending on the data-source mode in Settings. Picking a device goes through
the Scan screen (`ui/scan/`, `BleDeviceScanner` behind `BleScanSource`), which hands the
chosen address to `BleSensorDataSource` via `BleTargetDeviceSink`. Nothing downstream of
`SensorDataSource` knows which implementation is live.

### Persistence and session lifecycle

`SleepRepository` sits between the data source/Room and the ViewModels; ViewModels never
touch Room or `SensorDataSource` directly.

- `data/local/` — Room (DB version 4, destructive migration): `NightlySummaryEntity` (last
  30 nights, with user tags) plus `SleepSessionEntity`/`SessionReadingEntity`, which persist
  an in-progress session so a night survives process death.
- On disconnect the summary is built from the persisted readings (`NightSummaryBuilder`);
  sessions left unfinalized are retried on next launch (`recoverUnfinalizedSessions`).
- `SleepTrackingService` (foreground service) runs the night: mic noise level, smart-alarm
  firing, and mirroring readings to the watch. Stopping it calls `SleepSessionFinalizer`,
  which — on the application scope, so it outlives the service — records the summary,
  computes the recovery score, posts the summary notification, and refreshes the widget.

Settings (`SettingsRepository`) are saved to SharedPreferences, including the chosen BLE sensor, so BLE mode reconnects after a restart.

### Dependency injection

Hilt wires the graph: `SleepPulseApp` (`@HiltAndroidApp`), `MainActivity`
(`@AndroidEntryPoint`), each `@HiltViewModel`, and `di/AppModule.kt`.

## Features

- **Dashboard** — animated sleep-score gauge, live HR/HRV charts, metric row and guidance
  banner, and a wind-down flow. Links to a Breathe exercise.
- **History** — cached nights with trend arrows, sleep consistency/debt/variability
  analytics, night tags with tag correlations, and CSV export.
- **Recovery** — readiness/recovery score from last night vs. a rolling 7-night baseline
  (needs 3+ baseline nights).
- **Alarm** — bedtime/wake targets, smart-alarm window (with a hard-alarm fallback), and a
  wind-down reminder.
- **Settings** — simulated vs. BLE source, BLE device scan, targets, AMOLED-black theme.
- **Integrations** — Health Connect sleep-session write, Glance home-screen widget, Wear OS
  data sync, sleep-stage heuristic (`SleepStagePredictor`).

## Running the app

```
./gradlew :app:assembleDebug
# or, with a device/emulator connected:
./gradlew :app:installDebug
```

Requires the Android SDK at the path in `local.properties` (`sdk.dir`); JDK 17+; minSdk 26.

## Testing and CI

```
./gradlew :app:test    # unit tests, hand-written fakes in app/src/test/.../testutil
./gradlew lint
```

GitHub Actions (`.github/workflows/ci.yml`) runs lint, unit tests, and `assembleDebug` on
pushes and PRs to `master`. Instrumented tests live in `app/src/androidTest` and are not run
in CI.

## Known limitations

- HRV and sleep stage from a real BLE strap are placeholders (see above); stage prediction
  is a simple HR/HRV/movement threshold heuristic.
- Room uses destructive migration and doesn't export schemas — a version bump wipes data.
- Single `:app` module (no `:data`/`:domain` split); the `data/`, `ui/`, `di/` packages
  mirror where the boundaries would go.
- The `:wear` module is a minimal shell.

## Project history

Features were built spec → plan → implementation; see `docs/superpowers/specs/` and
`docs/superpowers/plans/`.
