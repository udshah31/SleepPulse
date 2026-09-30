# CLAUDE.md

CRITICAL GIT RULES:
1. Never stage, commit, or push any of the following local configuration or secret files:
    - .claude/settings.local.json
    - CLAUDE.local.md
    - .env, .env.local, or any file ending in .env
    - Any files containing API keys, private tokens, or credentials.
2. If you need to use Git, always stage files explicitly by their exact path (e.g., `git add src/index.js`). Do NOT use `git add .` or `git add -A`.
3. If you detect that any of these forbidden files are untracked or modified, explicitly ignore them and warn me. Do not include them in any git operations.

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

SleepPulse: a native Android sleep/recovery tracking app (Kotlin + Jetpack Compose). Modules: `:app` (the phone app) and `:wear` (a minimal Wear OS shell — `MainActivity` + `WearApp`). See `README.md` for architecture rationale.

## Commands

```
./gradlew :app:assembleDebug          # build debug APK
./gradlew :app:installDebug           # build + install on connected device/emulator
./gradlew :app:test                   # unit tests (app/src/test) — the bulk of the test suite
./gradlew :app:connectedAndroidTest   # instrumented tests (app/src/androidTest) — not in CI
./gradlew lint
```

CI (`.github/workflows/ci.yml`, master + PRs) runs `lint`, `:app:test`, `:app:assembleDebug`. Requires Android SDK path in `local.properties` (`sdk.dir`) and JDK 17+. `:app` is minSdk 26, compileSdk 36, targetSdk 36 (`:wear` stays on compileSdk/targetSdk 34). targetSdk 35+ forces edge-to-edge: screens stay clear of the system bars only because `SleepPulseApp`'s `Scaffold` padding is applied to the `NavHost` — keep new screens inside it. The XML theme (`res/values/themes.xml`) is dark with `windowBackground` = `@color/calm_night_background`, so the launch splash matches the Compose background — keep that colour in sync with `CalmNightBackground`. The launcher icon is still a placeholder system drawable (`@android:drawable/ic_menu_myplaces`), which the splash shows on a white disc. Health Connect is `connect-client:1.1.0` (stable), which requires compileSdk 36 and AGP ≥ 8.9.1 — hence AGP 8.9.3 / Gradle 8.11.1. In 1.1.0 records must use the `Metadata.autoRecorded(...)`-style factories (the constructor is internal, so tests can't set `dataOrigin`; `fromOtherApps` takes an `originOf` seam for that).

## Architecture

Package root: `com.sleeppulse.app`.

**MVI per screen** — each screen under `ui/<screen>/` has a `*Contract.kt` (sealed `Intent` + immutable `State`), a `*ViewModel.kt` (reduces `Intent` → `State` via `onIntent()`, publishes through `StateFlow`), and a Composable that only calls `collectAsState()` — no business logic in Composables. Breathe is the exception (Composable only, no ViewModel).

**Screens** (`ui/SleepPulseApp.kt`, Compose Navigation): bottom-nav Dashboard ("Home"), History, Recovery, Alarm, Settings; plus non-tab routes Scan (BLE device picker, opened from Settings, returns the chosen address) and Breathe (opened from Dashboard).
- Dashboard: sleep-score gauge (`ui/components/SleepScoreGauge.kt`), live HR/HRV charts, wind-down flow, metric row + guidance banner (`DashboardMetricRow`, `DashboardGuidanceBanner`).
- History: cached nights, trends, and analytics calculators (`SleepConsistency/SleepDebt/SleepVariability/TagCorrelationCalculator`); nights can be tagged (`SleepRepository.updateTags`) and exported to CSV (`data/export/DataExporter`).
- Recovery: `RecoveryReadinessCalculator`.
- Alarm: smart-alarm window / bedtime targets.
- Settings: data-source mode, bedtime/wake targets, AMOLED-black toggle, BLE scan entry.

**Scoring** is pure, unit-tested objects in `ui/dashboard/`: `SleepScoreCalculator` (HR/HRV → 0–100), `RecoveryScoreCalculator` (last night vs. rolling 7-night baseline; returns `null` under 3 baseline nights; `scoreLatest(nights)` is the shared entry point), plus `HrvTrendCalculator`, `RestingHeartRateTrendCalculator`, `MetricBaselineCalculator`. Keep new scoring logic in this style.

**Data source abstraction** — `data/source/SensorDataSource` is the only sensor interface the rest of the app sees. `SensorSourceManager` (the Hilt binding) delegates via `flatMapLatest` on `SettingsRepository.dataSourceMode` to:
- `SimulatedSensorDataSource` — plausible night (`AWAKE → LIGHT → DEEP → LIGHT → REM → LIGHT`).
- `BleSensorDataSource` — `BluetoothGatt` client on the standard Heart Rate Service (`0x180D`/`0x2A37`); `hrvMillis`/`sleepStage` are placeholders (not in the HR service). Target address is set through `BleTargetDeviceSink`; scanning is `BleDeviceScanner` behind `BleScanSource`.

**Repository & persistence** — `SleepRepository` (`SleepRepositoryImpl`) is the only thing ViewModels touch: live readings, `recentNights()`, `connectSensor()`, `disconnectSensor(): NightlySummary?`, `recordNightlySummary`, `updateTags`. Room (`data/local/`, DB version 4) stores `NightlySummaryEntity` (last 30 nights), plus `SleepSessionEntity`/`SessionReadingEntity` which persist in-progress sessions so a night survives process death. `disconnectSensor` builds the summary from persisted readings (`data/NightSummaryBuilder`); nights are keyed by date; when a second session lands on the same date, `nightToKeep` keeps the longer one's stats (the newer on a tie) and merges both nights' tags (built summaries never have any). Health Connect still gets every session — it stores sessions, not nights; `recoverUnfinalizedSessions()` retries sessions left unfinalized on next launch. Schemas export to `app/schemas/` (commit each new `N.json`). To change the schema: bump `SleepPulseDatabase.VERSION`, add `Migration(VERSION - 1, VERSION)` to `SleepPulseDatabase.MIGRATIONS`, commit the new JSON; `SleepPulseDatabaseMigrationsTest` fails CI on a missing migration (it can't detect an uncommitted JSON — Room regenerates it every build). Only the never-shipped pre-export versions 1–3 are reset (`fallbackToDestructiveMigrationFrom`); from 4 on, a missing migration crashes instead of silently wiping history. No instrumented `MigrationTestHelper` test yet — add one (with `room-testing`) alongside the first real migration.

**Session lifecycle** — `services/SleepTrackingService` (foreground service; mic noise via `tracking/NoiseMonitor`, smart alarm firing, readings mirrored to the watch via `wear/WearDataClient`). Its stop path calls `notifications/SleepSessionFinalizer.finalizeAsync()`, which runs on the **application** `CoroutineScope` (not the service's, which is cancelled in `onDestroy`) and does: disconnect + record summary → recovery score → summary notification → widget refresh. Don't launch finalize work on the service scope. `DashboardViewModel` does not build/record summaries itself.

**Integrations** — Health Connect write of sleep sessions (`tracking/HealthConnectManager.buildSleepRecord`: real start/end, merged stage segments from `NightSummaryBuilder.segments`, zone offsets, and `clientRecordId = sleeppulse-<bedtimeMillis>` so retries upsert; skips if not granted or duration is 0) a heart-rate write (`buildHeartRateRecord`: one sample per minute = that minute's mean bpm, readings outside 1..300 dropped, `clientRecordId = sleeppulse-hr-<firstReadingMillis>`, needs `WRITE_HEART_RATE`; each write checks only its own permission) and a read path (`readSleepSessions` → `ExternalSleepSession`, other apps' records only, needs `READ_SLEEP`; not yet shown in any UI). `tracking/HealthConnectSleepSync` keeps a local copy in sync via a changes token (stored with the cached sessions in SharedPreferences by `PrefsSleepSyncStore`): first run or expired token → take token *then* full 30-day read; otherwise incremental `getChanges`; read access missing/revoked → drop token and cache. Runs on app start (`SleepPulseApp.onCreate`), on opening History, and every 6h in the background via `tracking/SleepSyncWorker` (WorkManager + `@HiltWorker`; `SleepPulseApp` is the `Configuration.Provider` and the manifest removes WorkManager's default initializer). The worker needs `READ_HEALTH_DATA_IN_BACKGROUND` (Android 14+ devices that support it) and skips without it. `sync()` never throws and returns false on failure, keeping cache and token; a `SecurityException` counts as revocation (wipe) only if read access is really gone (`HealthConnectManager.revokedOrRethrow`) — a background-read refusal must not wipe the cache. A forced `cmd jobscheduler run` won't re-run the periodic work before its period is up (WorkManager treats it as not due); test the worker with a one-off `OneTimeWorkRequest` instead. The manifest's `VIEW_PERMISSION_USAGE` activity-alias is required on Android 14+ or every Health Connect call fails. `app/src/androidTest/.../HealthConnectReadTest` exercises the real read/changes API on a device (grant `READ_SLEEP`/`WRITE_SLEEP` via `adb shell pm grant` first; not in CI). Read and write permissions are checked separately so a denied read never blocks writes. `DashboardViewModel` decides whether to ask (once per ViewModel, on `Start`) and emits `healthConnectPermissionRequests`; `DashboardScreen` only launches the ActivityResult contract and sends the result back as `DashboardIntent.HealthConnectPermissionsResult`. Testing the real prompt: `adb pm revoke` marks permissions user-denied and Health Connect then stops showing the prompt — clear with `pm clear-permission-flags <pkg> <perm> user-set user-fixed`. `NightSummaryBuilder` never sets tags — tags are user-entered only, via `ui/history/NightTags` (preset factors + free text, saved through `SleepRepository.updateTags`; typed tags are normalised and a typed preset name maps to the preset's spelling so one factor's correlation isn't split); Glance home-screen widget (`widget/`, refreshed through the `WidgetRefresher` seam); wind-down reminder and smart/hard alarm via AlarmManager (`WindDownScheduler`, `SmartAlarmScheduler` + receivers). `tracking/SleepStagePredictor` is a simple threshold heuristic (HR/HRV/movement).

**DI** — Hilt; all bindings/provides live in `di/AppModule.kt` (`BindingsModule` for `@Binds`, `DatabaseModule` for DB, DAOs, application scope, `nowMillis`, system services).

**Settings persist** — `SettingsRepository` exposes one `StateFlow` per setting, initialised from a `SettingsStore` and saving the whole `Settings` snapshot on every setter (`PrefsSettingsStore`, SharedPreferences file `settings`; unknown enum names fall back to defaults). It also owns the temperature unit and the chosen BLE device: `ScanViewModel` saves the device, and `SensorSourceManager.connect()` hands the saved address back to `BleSensorDataSource` in BLE mode (which only remembers its target in memory). Tests use the no-arg constructor (in-memory store); it's a secondary constructor, not a default argument, because Kotlin would copy `@Inject` onto the generated no-arg constructor and Hilt rejects two.

There are two `SleepPulseApp.kt` files — `com.sleeppulse.app.SleepPulseApp` (the `@HiltAndroidApp` Application class) and `com.sleeppulse.app.ui.SleepPulseApp` (the root Composable/nav setup). Don't conflate them when searching.

## Testing patterns

- Unit tests in `app/src/test` mirror the main packages; hand-written fakes live in `testutil/` (`FakeSleepRepository`, `FakeSensorDataSource`, `FakeSleepSessionDao`, `FakeNightlySummaryDao`, `FakeBleScanSource`, `FakeSleepSummaryNotifier`, `FakeWidgetRefresher`, …) and `MainDispatcherRule`. Prefer these over a mocking library.
- Orchestration classes are kept plain Kotlin behind interfaces (`SleepSummaryNotifier`, `WidgetRefresher`, `*Scheduler`) so they test without Robolectric — the Service/Receiver stays a thin shell.

## Docs / workflow

Features are developed spec → plan → implementation: `docs/superpowers/specs/` and `docs/superpowers/plans/`, with per-plan progress in `.superpowers/sdd/` (subagent-driven development). Use isolated worktrees for plan tasks; earlier runs landed commits on `master` by mistake without them. `.superpowers/brainstorm/` is untracked scratch.
