# SleepPulse

![CI](https://github.com/udshah31/SleepPulse/actions/workflows/ci.yml/badge.svg)

A native Android sleep/recovery tracking companion app (Kotlin, Jetpack Compose), in the
spirit of Eight Sleep or Whoop. It tracks a night from a heart-rate sensor (simulated, or a
real BLE heart-rate strap), scores sleep and recovery, and keeps a 30-night history. A
minimal Wear OS module (`:wear`) sits alongside the phone app (`:app`). The platform-independent
core — models, scoring and analytics, night-summary construction, the repository/sensor
interfaces, and the Room database — lives in a Kotlin Multiplatform module (`:shared`). The
shared module builds and its tests run on iOS, and the native SwiftUI iOS app in `iosApp/`
now consumes the shared scoring core and provides foreground-only simulated tracking backed
by the shared Room database.

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

`SensorDataSource` (in `:shared`) is the interface the rest of the app depends on:

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
  Rate Service (`0x180D`)/Measurement Characteristic (`0x2A37`). HRV is a rolling RMSSD
  computed from the RR-intervals the strap sends (`null` until it has sent enough, or if it
  sends none); the service carries no sleep stage or movement, so the stage comes from the
  `SleepStagePredictor` heuristic.

`SensorSourceManager` is what Hilt binds to `SensorDataSource`. It delegates to the simulated
or BLE source depending on the data-source mode in Settings. Picking a device goes through
the Scan screen (`ui/scan/`, `BleDeviceScanner` behind `BleScanSource`), which hands the
chosen address to `BleSensorDataSource` via `BleTargetDeviceSink`. Nothing downstream of
`SensorDataSource` knows which implementation is live.

### Persistence and session lifecycle

`SleepRepository` sits between the data source/Room and the ViewModels; ViewModels never
touch Room or `SensorDataSource` directly.

- Room database in `:shared` (`db/`, Room KMP with the bundled SQLite driver; DB version 5, schemas exported to `shared/schemas/`, explicit migrations from 4 on): `NightlySummaryEntity` (last
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
- **Integrations** — Health Connect: sleep sessions with stages, heart rate and HRV (HRV only from sources that measure it) written; other apps' sleep (with that app's average heart rate) read and synced, Glance home-screen widget, Wear OS
  data sync, sleep-stage heuristic (`SleepStagePredictor`).

## Running the app

```
./gradlew :app:assembleDebug
# or, with a device/emulator connected:
./gradlew :app:installDebug
```

Requires the Android SDK at the path in `local.properties` (`sdk.dir`); JDK 17+; minSdk 26.

The current toolchain is Kotlin 2.3.21, AGP 9.0.1, Gradle 9.1.0, KSP 2.3.11,
Hilt 2.60.1, and Room 2.8.4. The shared module uses AGP's
`com.android.kotlin.multiplatform.library` plugin with a single Android variant;
its domain tests run as Android host tests.

## Testing and CI

```
./gradlew :shared:testAndroidHostTest # domain-core tests (shared/src/commonTest)
./gradlew :app:test                   # Android unit tests, hand-written fakes in app/src/test/.../testutil
./gradlew lint
./gradlew :app:assembleDebug :wear:assembleDebug
```

GitHub Actions (`.github/workflows/ci.yml`) runs lint, unit tests, and `assembleDebug` on
pushes and PRs to `master`. Both `:shared:testAndroidHostTest` and `:app:test` are included.
Instrumented tests live in `app/src/androidTest` and are not run in CI.

### Shared iOS validation

iOS targets are opt-in so Android development does not require Xcode. On a Mac with
full Xcode selected and an iOS simulator runtime installed, run:

```
./gradlew -PenableIosTargets=true :shared:compileKotlinIosSimulatorArm64
./gradlew -PenableIosTargets=true :shared:iosSimulatorArm64Test
```

The shared iOS simulator suite passed with 74 tests on 2026-10-07. These tests exercise
the shared library; they do not launch an iOS app. iOS tests are not currently in CI.
This machine's Xcode installation lives on the Secondary volume, which must be mounted.
Use `xcode-select -p` and `xcodebuild -version` to check the selected installation.

Use Kotlin/Native-safe common-test names: avoid `(`, `)`, and `,` in backtick function names.

### Native iOS app

The native SwiftUI app is in `iosApp/`. It targets iOS 17+, uses a static
`SleepPulseShared` framework, and provides clearly labelled simulated tracking. Home and
History share one app-scoped store. Start emits approximately one real-time reading per
second, persists each reading in the existing version-5 Room schema, and calculates the
live score from the last 40 recorded readings. Stop or backgrounding saves the full session
into the latest 30 simulated local start dates; an interrupted session is recovered on the
next launch. The app does not claim continuous background tracking, and HealthKit, BLE,
alarms and networking remain future integrations.

With full Xcode selected and a simulator UUID available, run:

```
xcodebuild -project iosApp/SleepPulse.xcodeproj -scheme SleepPulse \
  -destination 'platform=iOS Simulator,id=<simulator-uuid>' \
  -derivedDataPath iosApp/build/DerivedData test CODE_SIGNING_ALLOWED=YES
```

See [`iosApp/README.md`](iosApp/README.md) for framework wiring and launch instructions.

## Known limitations

- With a BLE strap, sleep stage is a simple HR/HRV threshold heuristic (the standard
  Heart Rate Service has no stage or movement data).
- Domain logic, the Room database and the iOS simulated repository/controller are shared
  (`:shared`); Android UI, Android database construction, BLE, Health Connect, services and
  widgets stay in `:app`. The iOS app intentionally stops simulated tracking when it enters
  the background and only uses a finite background task to finish saving.
- The `:wear` module is a minimal shell.

## Project history

Features were built spec → plan → implementation; see `docs/superpowers/specs/` and
`docs/superpowers/plans/`.
