# SleepPulse Kotlin Multiplatform Shared Core

## Status

Proposed design for the first KMP migration milestone. This milestone extracts
the platform-independent domain/core layer only; it does not add an iOS app or
rewrite the existing Android UI.

## Goal

Create a `:shared` Kotlin Multiplatform module that can be consumed by the
existing Android app and later by an iOS app, while preserving the current
Android runtime behavior and keeping Android-only integrations in `:app`.

The first milestone is successful when the Android app builds and its existing
unit tests pass while the shared module owns the core models, calculations,
summary construction, and platform-facing interfaces.

## Chosen approach

Use a shared-core migration rather than a full Compose Multiplatform rewrite.

```text
                 +-------------------+
                 |      :shared      |
                 | KMP domain + core |
                 +---------+---------+
                           |
             +-------------+-------------+
             |                           |
      +------v------+              +-----v------+
      |     :app     |              |  future iOS |
      | Android UI   |              |    app      |
      | integrations |              | adapters/UI |
      +-------------+              +------------+

      :wear remains a separate Android/Wear application.
```

This keeps the first change narrow and lets platform adapters evolve without
forcing an immediate rewrite of Compose navigation, permissions, services, or
Health Connect flows.

## Module layout

Add:

```text
:shared
  src/commonMain/kotlin/com/sleeppulse/shared/
    model/
    sleep/
    scoring/
    analytics/
    repository/
    sensor/
  src/commonTest/kotlin/com/sleeppulse/shared/
```

Configure the module with Kotlin Multiplatform targets for:

- Android JVM, consumed by `:app`
- iOS arm64
- iOS simulator arm64
- iOS x64 where supported by the installed Kotlin toolchain

The first milestone does not create an Xcode application. iOS targets are
enabled with `-PenableIosTargets=true` because Kotlin/Native requires Xcode and
its command-line tools even when the current developer is only validating the
Android app. Without that property, common and Android shared code remains
buildable on machines without Xcode.

## Shared code boundaries

### Move to `:shared/commonMain`

The following concepts are platform-independent and should have shared
packages:

- Sensor and sleep-stage models
- Nightly summary and stage-segment models
- `NightSummaryBuilder`
- Sleep-score calculation
- Recovery-score calculation
- HRV and resting-heart-rate trend calculations
- Metric baseline calculation
- Sleep debt, consistency, variability, and tag-correlation calculations
- Recovery readiness calculation
- Sensor data-source interface
- Sleep repository interface
- Clock/time-provider interface where domain code needs current time

Current Android UI package names must not remain the ownership boundary for
shared calculations. Shared code should use neutral packages such as
`com.sleeppulse.shared.scoring` and `com.sleeppulse.shared.analytics`; Android
UI classes can import those shared types.

### Keep in `:app`

The following remain Android-specific in this milestone:

- Jetpack Compose screens and navigation
- Hilt modules and Android ViewModels
- Room entities, DAOs, database, and migrations
- Android BLE GATT implementation
- Health Connect manager and permissions
- Foreground tracking service
- WorkManager worker
- AlarmManager schedulers and receivers
- Android notifications and Glance widget
- Wearable Data Layer client
- Android manifest and Activity Result APIs

`:wear` remains unchanged except for optional future adoption of shared model
types. It is not part of the first migration.

## Time and data types

Shared code must not depend on `java.time` or Android classes. Replace domain
uses of `LocalDate`, `Instant`, and `ZoneId` with `kotlinx.datetime` types where
they cross the shared boundary.

Epoch-millisecond fields may remain `Long` at persistence and sensor boundaries
because the Android database and BLE code already use them. Conversion helpers
should be placed in shared time utilities rather than duplicated in UI code.

The first milestone does not introduce serialization or network transport. No
new persistence format is required.

## Interfaces and dependency direction

Shared code may depend on:

- Kotlin standard library
- `kotlinx-coroutines-core`
- `kotlinx-datetime`

Shared code must not depend on Compose, AndroidX UI, Hilt, Room, Health Connect,
Bluetooth, WorkManager, or Android framework types.

Android implementations depend on shared interfaces:

```kotlin
interface SensorDataSource {
    val connectionState: Flow<SensorConnectionState>
    fun readings(): Flow<SensorReading>
    suspend fun connect()
    suspend fun disconnect()
}

interface SleepRepository {
    val connectionState: Flow<SensorConnectionState>
    fun liveReadings(): Flow<SensorReading>
    fun recentNights(): Flow<List<NightlySummary>>
    suspend fun connectSensor()
    suspend fun disconnectSensor(): NightlySummary?
    suspend fun recordNightlySummary(summary: NightlySummary)
    suspend fun updateTags(date: LocalDate, tags: List<String>)
}
```

The exact shared date type may be `kotlinx.datetime.LocalDate`; Android mapping
is handled at the repository/Room boundary.

## Android integration strategy

The Android repository remains responsible for mapping:

```text
shared domain model <-> Android Room entity
shared SensorDataSource <-> simulated/BLE implementations
shared repository contract <-> SleepRepositoryImpl
```

The Android ViewModels keep their existing public state and intent contracts in
the first milestone. This avoids changing Compose behavior while imports are
migrated from the old Android-owned domain classes to shared classes.

## Migration order

1. Add the `:shared` KMP module and Gradle targets.
2. Add common dependencies and shared source/test source sets.
3. Move or recreate the pure models in shared packages.
4. Port time handling from `java.time` to `kotlinx.datetime`.
5. Move summary construction and scoring/analytics calculators.
6. Move the sensor and repository interfaces.
7. Update Android implementations and ViewModels to consume shared types.
8. Move the corresponding pure tests to `commonTest`; retain Android-specific
   tests under `app/src/test` and `app/src/androidTest`.
9. Run shared compilation, Android unit tests, lint, and both debug builds.
10. Confirm no Android-only dependency leaked into `commonMain`.

Each step should compile before the next step changes another boundary.

## Testing strategy

Shared tests cover:

- Score boundaries and empty inputs
- Recovery baseline requirements
- Trend direction and thresholds
- Sleep debt and consistency calculations
- Summary duration and stage-segment construction
- Nullable/invalid sensor input behavior where applicable

Android tests continue to cover:

- Room mappings and migrations
- Android repository orchestration
- BLE and sensor-source routing seams
- Health Connect integrations
- ViewModel behavior
- Notifications, scheduling, and service orchestration

Required verification for the first milestone:

```text
./gradlew :shared:allTests
./gradlew :app:test
./gradlew lint
./gradlew :app:assembleDebug :wear:assembleDebug
```

If the installed Kotlin/Gradle toolchain does not expose `:shared:allTests`,
the equivalent generated common-test and platform test tasks must be run and
recorded.

## Non-goals

- No iOS application UI in this milestone
- No Compose Multiplatform UI migration
- No HealthKit implementation
- No CoreBluetooth implementation
- No Room-to-SQLDelight migration
- No settings persistence redesign
- No database schema changes
- No changes to scoring formulas or user-visible behavior
- No changes to the existing Android navigation structure

## Risks and mitigations

### Kotlin/Gradle compatibility

The project uses an existing Android/Kotlin toolchain. The KMP plugin must be
introduced using versions compatible with the current Kotlin and AGP versions.
If the current plugin cannot support all configured iOS targets, Android plus
common metadata compilation remains the fallback for the first incremental
step; the source boundary is still valid.

### Date-type migration breadth

`NightlySummary` and analytics currently use Java time types. Port this as one
small, independently compiling change and keep conversion helpers at the
Android boundary.

### Accidental behavior changes

Do not alter formulas while moving files. Shared tests should be moved before
or alongside each calculator migration, and the Android test count/results
must remain equivalent unless a test is intentionally relocated.

### Duplicate domain types

Avoid leaving two authoritative versions of the same model. Temporary type
aliases are acceptable during a narrow migration step, but the final milestone
must have one shared source of truth consumed by Android.

## Acceptance criteria

- `:shared` is a valid KMP module with common and Android source sets.
- Shared code contains no Android, Compose, Room, Hilt, or Java-time imports.
- Android app compiles against shared models and calculators.
- Existing Android UI and runtime integrations remain operational.
- Shared and Android tests pass with zero failures.
- Phone and Wear debug builds succeed.
- No database migration or user-data format changes are introduced.
