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

CI (`.github/workflows/ci.yml`, master + PRs) runs `lint`, `:app:test`, `:app:assembleDebug`. Requires Android SDK path in `local.properties` (`sdk.dir`) and JDK 17+. minSdk 26, targetSdk 34.

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

**Repository & persistence** — `SleepRepository` (`SleepRepositoryImpl`) is the only thing ViewModels touch: live readings, `recentNights()`, `connectSensor()`, `disconnectSensor(): NightlySummary?`, `recordNightlySummary`, `updateTags`. Room (`data/local/`, DB version 4) stores `NightlySummaryEntity` (last 30 nights), plus `SleepSessionEntity`/`SessionReadingEntity` which persist in-progress sessions so a night survives process death. `disconnectSensor` builds the summary from persisted readings (`data/NightSummaryBuilder`); `recoverUnfinalizedSessions()` retries sessions left unfinalized on next launch. The DB uses `fallbackToDestructiveMigration()` and `exportSchema = false` — bumping the version wipes data.

**Session lifecycle** — `services/SleepTrackingService` (foreground service; mic noise via `tracking/NoiseMonitor`, smart alarm firing, readings mirrored to the watch via `wear/WearDataClient`). Its stop path calls `notifications/SleepSessionFinalizer.finalizeAsync()`, which runs on the **application** `CoroutineScope` (not the service's, which is cancelled in `onDestroy`) and does: disconnect + record summary → recovery score → summary notification → widget refresh. Don't launch finalize work on the service scope. `DashboardViewModel` does not build/record summaries itself.

**Integrations** — Health Connect write of sleep sessions (`tracking/HealthConnectManager.buildSleepRecord`: real start/end, merged stage segments from `NightSummaryBuilder.segments`, zone offsets, and `clientRecordId = sleeppulse-<bedtimeMillis>` so retries upsert; write-sleep permission only, no reads; skips if not granted or duration is 0); Glance home-screen widget (`widget/`, refreshed through the `WidgetRefresher` seam); wind-down reminder and smart/hard alarm via AlarmManager (`WindDownScheduler`, `SmartAlarmScheduler` + receivers). `tracking/SleepStagePredictor` is a simple threshold heuristic (HR/HRV/movement).

**DI** — Hilt; all bindings/provides live in `di/AppModule.kt` (`BindingsModule` for `@Binds`, `DatabaseModule` for DB, DAOs, application scope, `nowMillis`, system services).

**Settings are in-memory only** — `SettingsRepository` is `MutableStateFlow`s with defaults (simulated source, bed 22:30, wake 07:00, 30-min window); nothing is persisted across process restarts.

There are two `SleepPulseApp.kt` files — `com.sleeppulse.app.SleepPulseApp` (the `@HiltAndroidApp` Application class) and `com.sleeppulse.app.ui.SleepPulseApp` (the root Composable/nav setup). Don't conflate them when searching.

## Testing patterns

- Unit tests in `app/src/test` mirror the main packages; hand-written fakes live in `testutil/` (`FakeSleepRepository`, `FakeSensorDataSource`, `FakeSleepSessionDao`, `FakeNightlySummaryDao`, `FakeBleScanSource`, `FakeSleepSummaryNotifier`, `FakeWidgetRefresher`, …) and `MainDispatcherRule`. Prefer these over a mocking library.
- Orchestration classes are kept plain Kotlin behind interfaces (`SleepSummaryNotifier`, `WidgetRefresher`, `*Scheduler`) so they test without Robolectric — the Service/Receiver stays a thin shell.

## Docs / workflow

Features are developed spec → plan → implementation: `docs/superpowers/specs/` and `docs/superpowers/plans/`, with per-plan progress in `.superpowers/sdd/` (subagent-driven development). Use isolated worktrees for plan tasks; earlier runs landed commits on `master` by mistake without them. `.superpowers/brainstorm/` is untracked scratch.
