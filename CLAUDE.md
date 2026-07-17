# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

SleepPulse: a native Android sleep/recovery tracking app (Kotlin + Jetpack Compose), single-module (`:app`), Phase 1 of the build. See `README.md` for full architecture rationale and what's deferred.

## Commands

```
./gradlew :app:assembleDebug          # build debug APK
./gradlew :app:installDebug           # build + install on connected device/emulator
./gradlew :app:test                   # unit tests (app/src/test)
./gradlew :app:connectedAndroidTest   # instrumented tests (app/src/androidTest)
./gradlew :app:lint
```

Note: no unit or instrumented tests currently exist in `app/src/test` / `app/src/androidTest` — this is called out in the README as deferred to a later hardening pass. Requires Android SDK path in `local.properties` (`sdk.dir`) and JDK 17+.

## Architecture

Package root: `com.sleeppulse.app`.

**MVI per screen** — each screen under `ui/<screen>/` has a `*Contract.kt` (sealed `Intent` + immutable `State`), a `*ViewModel.kt` (reduces `Intent` → `State` via `onIntent()`, publishes through `StateFlow`), and a Composable that only calls `collectAsState()` — no business logic in Composables.

**Data source abstraction** — `data/source/SensorDataSource` is the only interface the rest of the app depends on (`connectionState: Flow`, `readings(): Flow`, `connect()`/`disconnect()`). Two implementations:
- `SimulatedSensorDataSource` — coroutine ticker producing a plausible night (cycles `AWAKE → LIGHT → DEEP → LIGHT → REM → LIGHT`), currently bound by Hilt.
- `BleSensorDataSource` — real `BluetoothGatt` client targeting the standard Heart Rate Service (`0x180D`)/Measurement Characteristic (`0x2A37`); GATT lifecycle is correct but there's no scan flow wired up yet, and it's not bound by Hilt. `hrvMillis`/`sleepStage` are placeholder values since they aren't in the standard HR service.

To swap to BLE: change the `@Binds` target in `di/AppModule.kt`, then add a `BluetoothLeScanner` flow (e.g. from Settings) calling `BleSensorDataSource.setTargetDevice(address)` before `connect()`. Nothing else needs to change — `SleepRepository` and ViewModels only see the `SensorDataSource` interface.

**Local caching** — `SleepRepository` (impl in `data/repository/SleepRepositoryImpl`) sits between `SensorDataSource`/Room and the ViewModel layer, converting Room entities to the domain `NightlySummary` model and re-exposing live sensor flows. `data/local/` holds the Room entity/DAO/database, capped to the last 30 nights (`observeRecent()`, `trimToLast30Days()`). ViewModels depend only on `SleepRepository`, never on Room or `SensorDataSource` directly.

**DI** — Hilt (`SleepPulseApp` is `@HiltAndroidApp`, `MainActivity` is `@AndroidEntryPoint`, ViewModels are `@HiltViewModel`). All bindings/provides live in `di/AppModule.kt`.

**Screens** (`ui/dashboard`, `ui/history`, `ui/settings`):
- Dashboard: animated circular sleep-score gauge (`ui/components/SleepScoreGauge.kt`), live HR/HRV charts (`ui/components/LiveMetricChart.kt`), wind-down flow (`ui/dashboard/WindDownFlow.kt`, `AnimatedContent` transitions). Score logic in `ui/dashboard/SleepScoreCalculator.kt`.
- History: cached nights list with trend arrows vs. previous night. Legitimately empty in this phase — nothing calls `recordNightlySummary` yet.
- Settings: simulated-vs-BLE source preference (currently only records the preference, doesn't switch the actual binding) and temperature unit toggle.

There are two `SleepPulseApp.kt` files — `com.sleeppulse.app.SleepPulseApp` (the `@HiltAndroidApp` Application class) and `com.sleeppulse.app.ui.SleepPulseApp` (the root Composable/nav setup). Don't conflate them when searching.
