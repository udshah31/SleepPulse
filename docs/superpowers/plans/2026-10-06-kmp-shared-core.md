# SleepPulse Kotlin Multiplatform Shared Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract SleepPulse's platform-independent models, sleep calculations, analytics, and repository/sensor contracts into a validated Kotlin Multiplatform `:shared` module while preserving the Android app and Wear module.

**Architecture:** Add a KMP library module with `commonMain`, Android, and iOS targets. The Android app continues to own Compose UI, ViewModels, Room, BLE, Health Connect, services, scheduling, notifications, widgets, and Wear integration; it consumes shared models and logic through normal Gradle dependency injection. Shared code uses `kotlinx.datetime` and `kotlinx.coroutines`, with no Android, Compose, Room, Hilt, or Java-time dependencies.

**Tech Stack:** Kotlin Multiplatform, Kotlin 1.9.24, Android Gradle Plugin already used by the repository, `kotlinx-coroutines-core`, `kotlinx-coroutines-test`, `kotlinx-datetime`, Kotlin Test, existing Android Compose/Hilt/Room stack.

**Spec:** `docs/superpowers/specs/2026-10-06-kmp-shared-core-design.md`

## Global Constraints

- The first milestone extracts the shared KMP core only; it does not create an iOS app or rewrite Compose UI.
- `:app` keeps Android-only integrations: Compose, Hilt, Room, BLE GATT, Health Connect, foreground service, WorkManager, AlarmManager, notifications, Glance, and Wear Data Layer.
- `:wear` remains unchanged except for optional future shared model adoption; do not change it in this migration.
- Shared code must not import Android, Compose, Room, Hilt, Health Connect, Bluetooth, WorkManager, or Java-time APIs.
- Shared code uses `kotlinx.datetime` for date/time values and `kotlinx.coroutines` for flows.
- Do not change scoring formulas, persistence schemas, database versions, or user-visible Android behavior.
- Do not stage or commit `.env`, credential files, `.claude/settings.local.json`, `CLAUDE.local.md`, `.superpowers/brainstorm/`, or `__agent__/`.
- Do not commit during execution unless the user explicitly requests commits.

---

## File map

### New shared files

- `shared/build.gradle.kts` — KMP library targets, Android library settings, and common dependencies.
- `shared/src/commonMain/kotlin/com/sleeppulse/shared/model/SensorModels.kt` — shared sensor, stage, and connection models.
- `shared/src/commonMain/kotlin/com/sleeppulse/shared/model/NightlySummary.kt` — shared nightly-summary domain model.
- `shared/src/commonMain/kotlin/com/sleeppulse/shared/sleep/NightSummaryBuilder.kt` — duration, stage totals, and stage-segment construction.
- `shared/src/commonMain/kotlin/com/sleeppulse/shared/scoring/SleepScoreCalculator.kt` — sleep score.
- `shared/src/commonMain/kotlin/com/sleeppulse/shared/scoring/RecoveryScoreCalculator.kt` — recovery score and result types.
- `shared/src/commonMain/kotlin/com/sleeppulse/shared/scoring/HrvTrendCalculator.kt` — HRV trend logic.
- `shared/src/commonMain/kotlin/com/sleeppulse/shared/scoring/RestingHeartRateTrendCalculator.kt` — resting-HR trend logic.
- `shared/src/commonMain/kotlin/com/sleeppulse/shared/scoring/MetricBaselineCalculator.kt` — metric baseline logic.
- `shared/src/commonMain/kotlin/com/sleeppulse/shared/analytics/SleepDebtCalculator.kt` — sleep-debt logic.
- `shared/src/commonMain/kotlin/com/sleeppulse/shared/analytics/SleepConsistencyCalculator.kt` — bedtime consistency logic.
- `shared/src/commonMain/kotlin/com/sleeppulse/shared/analytics/SleepVariabilityCalculator.kt` — variability logic.
- `shared/src/commonMain/kotlin/com/sleeppulse/shared/analytics/TagCorrelationCalculator.kt` — tag correlation logic.
- `shared/src/commonMain/kotlin/com/sleeppulse/shared/analytics/RecoveryReadinessCalculator.kt` — readiness logic.
- `shared/src/commonMain/kotlin/com/sleeppulse/shared/sensor/SensorDataSource.kt` — shared sensor-source interface.
- `shared/src/commonMain/kotlin/com/sleeppulse/shared/repository/SleepRepository.kt` — shared repository interface.
- `shared/src/commonTest/kotlin/com/sleeppulse/shared/...` — migrated pure calculator and summary-builder tests.

### Modified Android files

- `settings.gradle.kts` — include `:shared`.
- `build.gradle.kts` — register the Kotlin Multiplatform and Android library plugins without changing existing app plugin versions.
- `app/build.gradle.kts` — depend on `project(":shared")`.
- `app/src/main/java/com/sleeppulse/app/data/repository/SleepRepositoryImpl.kt` — use shared repository/model/builder types while retaining Room and Health Connect orchestration.
- `app/src/main/java/com/sleeppulse/app/data/local/*.kt` — map shared models to Room entities without changing schema or migrations.
- `app/src/main/java/com/sleeppulse/app/data/source/*.kt` — implement the shared sensor interface with Android simulated/BLE behavior.
- `app/src/main/java/com/sleeppulse/app/tracking/HealthConnectManager.kt` — import shared summary and stage types.
- `app/src/main/java/com/sleeppulse/app/ui/**/*.kt` — update imports for shared models/calculators; preserve state and intent contracts.
- Existing Android unit tests — update imports and leave Android-specific tests in `app/src/test`.

---

## Task 1: Scaffold the KMP shared module

**Files:**
- Create: `shared/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Create: `shared/src/commonMain/kotlin/com/sleeppulse/shared/ModuleMarker.kt`
- Create: `shared/src/commonTest/kotlin/com/sleeppulse/shared/SharedModuleTest.kt`

**Interfaces:**
- Produces Gradle project `:shared` and Android dependency `implementation(project(":shared"))`.
- Produces an empty compilable common source set and test source set before domain migration begins.

- [x] **Step 1: Add a failing shared-module smoke test**

Create `shared/src/commonTest/kotlin/com/sleeppulse/shared/SharedModuleTest.kt`:

```kotlin
package com.sleeppulse.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedModuleTest {
    @Test
    fun sharedTestRuntimeIsConfigured() {
        assertEquals(2, 1 + 1)
    }
}
```

- [x] **Step 2: Register the module and plugins**

Add the Kotlin Multiplatform plugin to the root plugin block using the repository's existing Kotlin version, add the Android library plugin if it is not already registered, and add `include(":shared")` to `settings.gradle.kts`.

Create `shared/build.gradle.kts` with an Android target and opt-in iOS targets:

```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    androidTarget()
    val enableIosTargets = providers.gradleProperty("enableIosTargets")
        .map(String::toBoolean)
        .orElse(false)
        .get()
    if (enableIosTargets) {
        iosX64()
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
    }
}

android {
    namespace = "com.sleeppulse.shared"
    compileSdk = 36
}
```

Use the repository's exact existing AGP/Kotlin versions; do not upgrade unrelated tooling during this task.

- [x] **Step 3: Add minimal common source**

Create `ModuleMarker.kt`:

```kotlin
package com.sleeppulse.shared

public object ModuleMarker
```

- [x] **Step 4: Run the smoke test and Android compile**

Run:

```bash
./gradlew :shared:testDebugUnitTest :shared:compileDebugKotlinAndroid :app:compileDebugKotlin
```

Expected: the shared test passes and the existing Android app compiles against the empty shared module.

---

## Task 2: Move shared domain models and time types

**Files:**
- Create: `shared/src/commonMain/kotlin/com/sleeppulse/shared/model/SensorModels.kt`
- Create: `shared/src/commonMain/kotlin/com/sleeppulse/shared/model/NightlySummary.kt`
- Create: `shared/src/commonMain/kotlin/com/sleeppulse/shared/model/TimeTypes.kt`
- Create: `shared/src/commonTest/kotlin/com/sleeppulse/shared/model/ModelTest.kt`
- Modify: Android imports for current model consumers under `app/src/main/java` and `app/src/test`
- Delete only after all consumers migrate: `app/src/main/java/com/sleeppulse/app/data/model/SensorModels.kt`, `NightlySummary.kt`

**Interfaces:**
- Produces shared `SleepStage`, `SensorReading`, `StageSegment`, `SensorConnectionState`, `NightlySummary`, and `LocalDate` usage based on `kotlinx.datetime.LocalDate`.
- Keeps field shapes and default values unchanged except for the package and date-type portability change.

- [x] **Step 1: Write model portability tests**

Test that `SensorReading`, `StageSegment`, and `NightlySummary` preserve values and that `NightlySummary.trendAgainst` retains its existing thresholds. Use `kotlinx.datetime.LocalDate(2026, 7, 18)`.

- [x] **Step 2: Implement shared model files**

Copy the existing model semantics into `com.sleeppulse.shared.model`, replacing `java.time.LocalDate` with `kotlinx.datetime.LocalDate`. Do not change HRV nullability or numeric defaults during this extraction.

- [x] **Step 3: Update Android imports and Room mappings**

Update Android domain consumers to import `com.sleeppulse.shared.model.*`. Convert Room's epoch-day mapping at the Room boundary with `LocalDate.toEpochDays()`/`LocalDate.fromEpochDays()` or an equivalent helper. Keep the entity schema unchanged.

- [x] **Step 4: Run focused model tests and compile**

Run:

```bash
./gradlew :shared:testDebugUnitTest :app:testDebugUnitTest --tests '*NightlySummary*' :app:compileDebugKotlin
```

Expected: shared model tests and Android summary-related tests pass.

---

## Task 3: Move night summary construction

**Files:**
- Create: `shared/src/commonMain/kotlin/com/sleeppulse/shared/sleep/NightSummaryBuilder.kt`
- Create: `shared/src/commonTest/kotlin/com/sleeppulse/shared/sleep/NightSummaryBuilderTest.kt`
- Modify: `app/src/main/java/com/sleeppulse/app/data/repository/SleepRepositoryImpl.kt`
- Modify: Health Connect and Android tests that import `NightSummaryBuilder`
- Delete after consumers migrate: `app/src/main/java/com/sleeppulse/app/data/NightSummaryBuilder.kt`

**Interfaces:**
- Produces `NightSummaryBuilder.build(readings: List<SensorReading>, date: LocalDate): NightlySummary`.
- Produces `NightSummaryBuilder.segments(readings: List<SensorReading>): List<StageSegment>`.

- [x] **Step 1: Port the existing tests to `commonTest`**

Move the existing summary-builder cases unchanged in meaning: averages, duration/stage totals, one-reading behavior, one-second cadence, merged segments, and tag preservation. Replace only package and portable date imports.

- [x] **Step 2: Port the implementation without formula changes**

Preserve the current duration accumulation, deep/REM totals, score delegation, and segment merging exactly. Do not fix or reinterpret `totalSleepMinutes` in this migration.

- [x] **Step 3: Switch Android repository and Health Connect imports**

The Android repository continues to call the shared builder, then maps the shared output into Room and Health Connect records. No database or Health Connect record behavior changes.

- [x] **Step 4: Run summary tests and Android tests**

Run:

```bash
./gradlew :shared:testDebugUnitTest :app:testDebugUnitTest --tests '*NightSummaryBuilder*' --tests '*SleepRepositoryImplTest*' --tests '*HealthConnectManagerTest*'
```

---

## Task 4: Move scoring and recovery calculations

**Files:**
- Create shared scoring files under `shared/src/commonMain/kotlin/com/sleeppulse/shared/scoring/`.
- Create shared scoring tests under `shared/src/commonTest/kotlin/com/sleeppulse/shared/scoring/`.
- Create `shared/src/commonMain/kotlin/com/sleeppulse/shared/analytics/RecoveryReadinessCalculator.kt` and its test.
- Modify Android ViewModels, components, and screens to import shared result types.
- Delete the old Android-owned calculator files after every consumer and test uses shared packages.

**Interfaces:**
- Preserve existing public calculator entry points and result semantics:
  - `SleepScoreCalculator.score(readings)`
  - `RecoveryScoreCalculator.score(lastNight, baseline)` and `scoreLatest(nights)`
  - `HrvTrendCalculator.analyze(nights)`
  - `RestingHeartRateTrendCalculator.analyze(nights)`
  - `MetricBaselineCalculator.compute(nights)` and `percentDelta(actual, baseline)`
  - `RecoveryReadinessCalculator.compute(...)`

- [x] **Step 1: Move or recreate calculator tests in `commonTest`**

Port the existing boundary tests without changing expected values. Keep formulas byte-for-byte equivalent where practical; if package changes force edits, change only imports and test fixtures.

- [x] **Step 2: Implement the shared calculator packages**

Move the pure implementations and all result enums/data classes needed by callers. Replace Java-time access with shared `kotlinx.datetime` helpers where trend calculations inspect dates.

- [x] **Step 3: Update Android ViewModels and components**

Update Dashboard, History, Recovery, and reusable score components to consume the shared result types. Preserve all existing `State`, `Intent`, and Composable signatures.

- [x] **Step 4: Run focused scoring and analytics tests**

Run:

```bash
./gradlew :shared:testDebugUnitTest :app:testDebugUnitTest --tests '*CalculatorTest' --tests '*RecoveryViewModelTest*' --tests '*HistoryViewModelTest*' --tests '*DashboardViewModelTest*'
```

---

## Task 5: Move sensor and repository contracts

**Files:**
- Create: `shared/src/commonMain/kotlin/com/sleeppulse/shared/sensor/SensorDataSource.kt`
- Create: `shared/src/commonMain/kotlin/com/sleeppulse/shared/repository/SleepRepository.kt`
- Create: shared contract tests using simple fake implementations where useful
- Modify: `app/src/main/java/com/sleeppulse/app/data/source/*.kt`
- Modify: `app/src/main/java/com/sleeppulse/app/data/repository/SleepRepositoryImpl.kt`
- Modify: fake repositories and fake sensor sources under `app/src/test`
- Delete old Android-owned interface files after consumers migrate

**Interfaces:**
- Shared `SensorDataSource` exposes `Flow<SensorConnectionState>`, `Flow<SensorReading>`, `connect()`, and `disconnect()`.
- Shared `SleepRepository` exposes the existing repository methods, using shared models and `kotlinx.datetime.LocalDate`.

- [x] **Step 1: Add shared interfaces and contract-level fakes**

Implement interfaces with no Android imports. Use `MutableStateFlow`/`MutableSharedFlow` only in tests or shared test utilities.

- [x] **Step 2: Update Android source implementations**

Make simulated and BLE sources implement the shared sensor interface. Preserve the stable BLE callback and simulated-connected gating already implemented.

- [x] **Step 3: Update repository implementation and fakes**

Make `SleepRepositoryImpl` implement the shared contract while retaining Room session recovery, finalization, Health Connect writes, and application-scope behavior.

- [x] **Step 4: Update ViewModels and DI imports**

Update Hilt bindings and ViewModel constructor imports to use the shared interface types. Do not introduce Hilt into `:shared`.

- [x] **Step 5: Run repository and sensor tests**

Run:

```bash
./gradlew :shared:testDebugUnitTest :app:testDebugUnitTest --tests '*SensorSourceManagerTest*' --tests '*SleepRepositoryImplTest*' --tests '*DashboardViewModelTest*'
```

---

## Task 6: Remove duplicate Android core ownership and audit boundaries

**Files:**
- Delete migrated duplicate model, builder, calculator, and interface files from `app/src/main/java`.
- Modify any remaining Android imports found by search.
- Add or update package documentation if needed.

**Interfaces:**
- `:shared` becomes the only source of truth for migrated domain types and calculations.
- `:app` remains the source of truth for Android implementations and UI orchestration.

- [x] **Step 1: Search for forbidden shared imports**

Run:

```bash
rg -n 'android\.|androidx\.|java\.time|com\.sleeppulse\.app\.ui|com\.sleeppulse\.app\.data' shared/src/commonMain
```

The result must contain no Android/Compose/Room/Hilt/Java-time imports and no stale imports of deleted Android-owned domain classes.

- [x] **Step 2: Search for duplicate domain definitions**

Run:

```bash
rg -n 'data class (SensorReading|NightlySummary|StageSegment)|object (NightSummaryBuilder|SleepScoreCalculator|RecoveryScoreCalculator)|interface (SensorDataSource|SleepRepository)' app/src/main shared/src
```

Each migrated authoritative type must occur only in `:shared`, apart from Android implementation classes that intentionally share their names with a different responsibility.

- [x] **Step 3: Compile all targets and Android code**

Run:

```bash
./gradlew :shared:compileKotlinMetadata :shared:compileDebugKotlinAndroid :app:compileDebugKotlin
```

On a machine with Xcode configured, additionally run:

```bash
./gradlew -PenableIosTargets=true :shared:compileKotlinIosSimulatorArm64
```

If Xcode is unavailable, record that iOS-target compilation is deferred; do not weaken the common-source boundary.

---

## Task 7: Full verification and migration handoff

**Files:**
- Modify only documentation or generated reports if a verification note is required.

- [x] **Step 1: Run shared tests**

```bash
./gradlew :shared:testDebugUnitTest
```

- [x] **Step 2: Run Android unit tests and lint**

```bash
./gradlew :app:test lint
```

- [x] **Step 3: Build phone and Wear debug artifacts**

```bash
./gradlew :app:assembleDebug :wear:assembleDebug
```

- [x] **Step 4: Inspect generated artifacts and working tree**

```bash
git diff --check
```

Do not include local configuration, credentials, `.superpowers/brainstorm/`, or `__agent__/` in any migration change.

- [ ] **Step 5: Runtime smoke test Android behavior**

On an available emulator or device, verify startup, Dashboard navigation, simulated sensor connect/disconnect, History, Recovery, Alarm, Settings, Breathe, and Scan entry. Confirm no database migration was generated and no Android-only feature was removed.

## Self-review checklist

- Spec coverage: module structure is Task 1; shared boundaries are Tasks 2–5; duplicate ownership is Task 6; testing and acceptance are Task 7.
- Placeholder scan: every task has concrete files, interfaces, commands, and expected outcomes; no implementation step is left unfinished.
- Type consistency: shared model types are introduced in Task 2 before builder/calculators in Tasks 3–4; shared interfaces are introduced in Task 5 after their model dependencies; Android consumers are updated before old files are deleted.

## Verification record (2026-10-06)

- Boundary audit (Task 6 Steps 1–2): `commonMain` has no Android/Java-time/app imports; every migrated type is defined only in `:shared`; no stale imports of the old locations.
- `:shared:compileKotlinMetadata`, `:shared:compileDebugKotlinAndroid`, `:app:compileDebugKotlin`: pass.
- iOS compilation deferred: this machine has only the Command Line Tools, and Kotlin/Native needs full Xcode (`xcodebuild -version` fails).
- `:shared:testDebugUnitTest` 74 pass; `:app:testDebugUnitTest` 153 pass; `lint`, `:app:assembleDebug`, `:wear:assembleDebug` pass; `git diff --check` clean.
- No Room schema or database-version change.
- CI now runs `:shared:testDebugUnitTest` alongside `:app:test`.
- Open: Task 7 Step 5 (runtime smoke test on a device).

