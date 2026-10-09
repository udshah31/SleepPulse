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
by the shared Room database. Home, History and Recovery share one simulated-tracking store;
Recovery and History insights are computed by the shared Kotlin calculators. iOS History also
offers a separate, read-only Apple Health sleep import backed by a native app-scoped store.

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
- **History** — cached nights with bedtime consistency, seven-recorded-night sleep debt,
  score-change indicators, and accessible 14-date score/duration charts. Charts preserve
  calendar gaps and actual short-session durations.
- **Recovery** — latest recorded-date recovery/readiness insights from the shared calculators.
  Recovery needs 4 recorded dates (latest plus 3 preceding dates); trends compare the latest
  7 recorded dates with the preceding 7 and disclose known-HRV coverage. Missing HRV remains
  unavailable and recovery falls back to heart rate when the shared calculator permits it.
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

The shared iOS simulator suite passed with 97 tests on 2026-10-08. These tests exercise
the shared library; they do not launch an iOS app. iOS tests are not currently in CI.
This machine's Xcode installation lives on the Secondary volume, which must be mounted.
Use `xcode-select -p` and `xcodebuild -version` to check the selected installation.

Use Kotlin/Native-safe common-test names: avoid `(`, `)`, and `,` in backtick function names.

### Native iOS app

The native SwiftUI app is in `iosApp/`. It targets iOS 17+, uses a static
`SleepPulseShared` framework, and provides clearly labelled simulated tracking. Home, History
and Recovery share one app-scoped store. Start emits approximately one real-time reading per
second, persists each reading in the existing version-5 Room schema, and calculates the
live score from the last 40 recorded readings. Stop or backgrounding saves the full session
into the latest 30 simulated local start dates; an interrupted session is recovered on the
next launch. Tracking is foreground-only; BLE, alarms and networking remain future integrations.

History's **From Apple Health** section imports only HealthKit **sleep analysis**, read-only.
**Connect Apple Health** explicitly requests access and then imports the last **30 calendar
days**, ending at the request's query time. Launching the app, opening History, or starting
simulated tracking does not request permission or query HealthKit. **Refresh Apple Health**
manually replaces the whole window without another authorization request; there is no
background sync. Samples overlapping a window boundary retain their original intervals.

Imported episodes preserve each source's name/identifier. Overlapping sources stay separate;
they are not combined into a canonical night. Missing stages are **Unavailable**, while known
zero/subminute durations display **<1 min**. These native records do not enter shared Room,
the simulated tracking controls, scores, Recovery baseline, History insights, or charts.

The separate Application Support cache (`SleepPulse/healthkit-sleep-cache.json`) atomically
replaces episodes, fetched time, and import-window metadata only after a successful query,
normalization, encoding, and write. Failures retain the last successful snapshot with stale
wording and retry guidance, including a previously empty snapshot. A successful empty read
replaces old records and says **No Apple Health sleep records found**: HealthKit deliberately
does not reveal read denial, so empty data is not proof of denied access (nor is a completed
authorization request proof of a grant). Cached results load on relaunch without querying.

The Xcode app target declares the HealthKit capability/entitlement and
`NSHealthShareUsageDescription`; it requests no write, heart-rate, or HRV access. Physical-device
validation requires an authorized installation, a development team/provisioning profile with
HealthKit enabled, a compatible unlocked device, and Apple Health sleep data. Simulator/fake
tests do not establish that an actual permission grant or real-data import works on a device.

With full Xcode selected and a simulator UUID available, run:

```
ANDROID_HOME=/Users/udaysah/Library/Android/sdk \
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
xcodebuild -project iosApp/SleepPulse.xcodeproj -scheme SleepPulse \
  -destination 'platform=iOS Simulator,id=<simulator-uuid>' \
  -derivedDataPath iosApp/build/DerivedData \
  -parallel-testing-enabled NO -collect-test-diagnostics never \
  test CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- \
  SYMROOT="$PWD/iosApp/build/DerivedData/Build/Products" \
  OBJROOT="$PWD/iosApp/build/DerivedData/Build/Intermediates.noindex"
```

Set `ANDROID_HOME` to your SDK location (especially in worktrees without `local.properties`).
System UI tests use a unique `SLEEPPULSE_UI_TEST_STORAGE_ID` UUID in each app's launch environment.
The DEBUG-only app-root override isolates both the simulated DB and native Health cache under
`Application Support/SleepPulse/UITests/<UUID>/`, preserving that state for intentional relaunches.
It never resets or reuses normal user data; Release ignores the override. Each test creates its
own recording when saved/chart data is required. See the iOS README for validation/failure behavior.
See [`iosApp/README.md`](iosApp/README.md) for framework wiring, test constraints, launch
instructions, and the physical-device HealthKit checklist.

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
