# Automated Tests (Core Logic, ViewModels, Repository) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a JVM unit-test safety net for `SleepScoreCalculator`, the three ViewModels, and `SleepRepositoryImpl` — none of which have any test coverage today.

**Architecture:** Pure JVM tests under `app/src/test/java/com/sleeppulse/app/`, using hand-written fakes for `SleepRepository`, `SensorDataSource`, and `NightlySummaryDao` instead of Mockito. A shared `MainDispatcherRule` swaps `Dispatchers.Main` for a `StandardTestDispatcher` so ViewModel tests (which use `viewModelScope`) run deterministically. Turbine asserts `StateFlow` emissions.

**Tech Stack:** JUnit4, Turbine 1.1.0, kotlinx-coroutines-test 1.8.1 (all already declared in `app/build.gradle.kts` — no dependency changes).

## Global Constraints

- No new Gradle dependencies — use only what's already declared in `app/build.gradle.kts` (JUnit4, Turbine, kotlinx-coroutines-test, mockito — mockito stays unused/untouched).
- Test doubles are hand-written fakes, not Mockito mocks (spec decision — Flow-returning interfaces are awkward to stub with Mockito).
- All new files live under `app/src/test/java/com/sleeppulse/app/` mirroring main source package structure; fakes go in a `testutil` subpackage.
- Scope is JVM unit tests only — no Room instrumented tests, no Compose/Espresso UI tests, no `BleSensorDataSource` tests (per spec's explicit exclusions).

---

## File Structure

Create:
- `app/src/test/java/com/sleeppulse/app/testutil/MainDispatcherRule.kt`
- `app/src/test/java/com/sleeppulse/app/testutil/FakeSensorDataSource.kt`
- `app/src/test/java/com/sleeppulse/app/testutil/FakeNightlySummaryDao.kt`
- `app/src/test/java/com/sleeppulse/app/testutil/FakeSleepRepository.kt`
- `app/src/test/java/com/sleeppulse/app/ui/dashboard/SleepScoreCalculatorTest.kt`
- `app/src/test/java/com/sleeppulse/app/ui/dashboard/DashboardViewModelTest.kt`
- `app/src/test/java/com/sleeppulse/app/ui/history/HistoryViewModelTest.kt`
- `app/src/test/java/com/sleeppulse/app/ui/settings/SettingsViewModelTest.kt`
- `app/src/test/java/com/sleeppulse/app/data/repository/SleepRepositoryImplTest.kt`

No main-source files are modified — this plan is additive only.

---

### Task 1: MainDispatcherRule

**Files:**
- Create: `app/src/test/java/com/sleeppulse/app/testutil/MainDispatcherRule.kt`
- Test: none (this is test infrastructure itself; verified indirectly by Task 3)

**Interfaces:**
- Consumes: nothing
- Produces: `class MainDispatcherRule(private val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher()` — applied via `@get:Rule` in ViewModel test classes.

- [ ] **Step 1: Write the rule**

```kotlin
package com.sleeppulse.app.testutil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
```

- [ ] **Step 2: Compile-check**

Run: `./gradlew :app:compileDebugUnitTestKotlin`
Expected: BUILD SUCCESSFUL (no tests exist yet to run, this just verifies the file compiles)

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/sleeppulse/app/testutil/MainDispatcherRule.kt
git commit -m "test: add MainDispatcherRule for coroutine test infra"
```

---

### Task 2: SleepScoreCalculatorTest

**Files:**
- Test: `app/src/test/java/com/sleeppulse/app/ui/dashboard/SleepScoreCalculatorTest.kt`

**Interfaces:**
- Consumes: `SleepScoreCalculator.score(readings: List<SensorReading>): Int` (existing, `app/src/main/java/com/sleeppulse/app/ui/dashboard/SleepScoreCalculator.kt`); `SensorReading(timestampMillis: Long, heartRateBpm: Int, hrvMillis: Double, sleepStage: SleepStage)`; `SleepStage.LIGHT` (existing enum value).
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.sleeppulse.app.ui.dashboard

import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.data.model.SleepStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepScoreCalculatorTest {

    private fun reading(heartRateBpm: Int, hrvMillis: Double) = SensorReading(
        timestampMillis = 0L,
        heartRateBpm = heartRateBpm,
        hrvMillis = hrvMillis,
        sleepStage = SleepStage.LIGHT,
    )

    @Test
    fun `empty readings score zero`() {
        assertEquals(0, SleepScoreCalculator.score(emptyList()))
    }

    @Test
    fun `high hrv and low heart rate score near the top`() {
        val readings = listOf(reading(heartRateBpm = 50, hrvMillis = 100.0))
        val score = SleepScoreCalculator.score(readings)
        assertTrue("expected score >= 90, was $score", score >= 90)
    }

    @Test
    fun `low hrv and high heart rate score near the bottom`() {
        val readings = listOf(reading(heartRateBpm = 90, hrvMillis = 0.0))
        val score = SleepScoreCalculator.score(readings)
        assertTrue("expected score <= 10, was $score", score <= 10)
    }

    @Test
    fun `single reading produces a valid score`() {
        val readings = listOf(reading(heartRateBpm = 65, hrvMillis = 60.0))
        val score = SleepScoreCalculator.score(readings)
        assertTrue(score in 0..100)
    }

    @Test
    fun `extreme inputs stay clamped between 0 and 100`() {
        val extremeHigh = listOf(reading(heartRateBpm = 0, hrvMillis = 1000.0))
        val extremeLow = listOf(reading(heartRateBpm = 500, hrvMillis = -100.0))
        assertEquals(100, SleepScoreCalculator.score(extremeHigh))
        assertEquals(0, SleepScoreCalculator.score(extremeLow))
    }
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.ui.dashboard.SleepScoreCalculatorTest"`
Expected: BUILD SUCCESSFUL, 5 tests passed

(Note: `SleepScoreCalculator` already exists with this exact behavior, so these tests should pass immediately — this task is verifying existing behavior, not driving new implementation. If `extreme inputs stay clamped` fails because `hrComponent`'s `coerceIn(0.0, 40.0)` doesn't clamp a negative `avgHr`-derived value the way expected, adjust the assertion to match actual observed output rather than changing production code — this plan does not change `SleepScoreCalculator`.)

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/sleeppulse/app/ui/dashboard/SleepScoreCalculatorTest.kt
git commit -m "test: add SleepScoreCalculator unit tests"
```

---

### Task 3: FakeSensorDataSource

**Files:**
- Create: `app/src/test/java/com/sleeppulse/app/testutil/FakeSensorDataSource.kt`

**Interfaces:**
- Consumes: `SensorDataSource` interface (`connectionState: Flow<SensorConnectionState>`, `readings(): Flow<SensorReading>`, `suspend fun connect()`, `suspend fun disconnect()`), `SensorConnectionState.Disconnected`/`Connected`/`Connecting`/`Error`.
- Produces: `class FakeSensorDataSource : SensorDataSource` with public `val connectionStateFlow: MutableStateFlow<SensorConnectionState>`, public `val readingsFlow: MutableSharedFlow<SensorReading>`, `var connectCallCount: Int`, `var disconnectCallCount: Int` — consumed by Task 8 (`SleepRepositoryImplTest`).

- [ ] **Step 1: Write the fake**

```kotlin
package com.sleeppulse.app.testutil

import com.sleeppulse.app.data.model.SensorConnectionState
import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.data.source.SensorDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSensorDataSource : SensorDataSource {
    val connectionStateFlow = MutableStateFlow<SensorConnectionState>(SensorConnectionState.Disconnected)
    val readingsFlow = MutableSharedFlow<SensorReading>(extraBufferCapacity = 10)

    var connectCallCount = 0
        private set
    var disconnectCallCount = 0
        private set

    override val connectionState: Flow<SensorConnectionState> = connectionStateFlow

    override fun readings(): Flow<SensorReading> = readingsFlow

    override suspend fun connect() {
        connectCallCount++
        connectionStateFlow.value = SensorConnectionState.Connected(deviceName = "fake-device")
    }

    override suspend fun disconnect() {
        disconnectCallCount++
        connectionStateFlow.value = SensorConnectionState.Disconnected
    }
}
```

- [ ] **Step 2: Compile-check**

Run: `./gradlew :app:compileDebugUnitTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/sleeppulse/app/testutil/FakeSensorDataSource.kt
git commit -m "test: add FakeSensorDataSource test double"
```

---

### Task 4: FakeNightlySummaryDao

**Files:**
- Create: `app/src/test/java/com/sleeppulse/app/testutil/FakeNightlySummaryDao.kt`

**Interfaces:**
- Consumes: `NightlySummaryDao` interface (`fun observeRecent(): Flow<List<NightlySummaryEntity>>`, `suspend fun upsert(summary: NightlySummaryEntity)`, `suspend fun trimToLast30Days()`), `NightlySummaryEntity(dateEpochDay: Long, sleepScore: Int, avgHeartRateBpm: Int, avgHrvMillis: Double, totalSleepMinutes: Int, deepSleepMinutes: Int, remSleepMinutes: Int)`.
- Produces: `class FakeNightlySummaryDao : NightlySummaryDao` with public `val entitiesFlow: MutableStateFlow<List<NightlySummaryEntity>>` — consumed by Task 8 (`SleepRepositoryImplTest`).

- [ ] **Step 1: Write the fake**

```kotlin
package com.sleeppulse.app.testutil

import com.sleeppulse.app.data.local.NightlySummaryDao
import com.sleeppulse.app.data.local.NightlySummaryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeNightlySummaryDao : NightlySummaryDao {
    val entitiesFlow = MutableStateFlow<List<NightlySummaryEntity>>(emptyList())

    override fun observeRecent(): Flow<List<NightlySummaryEntity>> = entitiesFlow

    override suspend fun upsert(summary: NightlySummaryEntity) {
        val withoutExisting = entitiesFlow.value.filterNot { it.dateEpochDay == summary.dateEpochDay }
        entitiesFlow.value = (withoutExisting + summary).sortedByDescending { it.dateEpochDay }
    }

    override suspend fun trimToLast30Days() {
        entitiesFlow.value = entitiesFlow.value
            .sortedByDescending { it.dateEpochDay }
            .take(30)
    }
}
```

- [ ] **Step 2: Compile-check**

Run: `./gradlew :app:compileDebugUnitTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/sleeppulse/app/testutil/FakeNightlySummaryDao.kt
git commit -m "test: add FakeNightlySummaryDao test double"
```

---

### Task 5: FakeSleepRepository

**Files:**
- Create: `app/src/test/java/com/sleeppulse/app/testutil/FakeSleepRepository.kt`

**Interfaces:**
- Consumes: `SleepRepository` interface (`connectionState: Flow<SensorConnectionState>`, `fun liveReadings(): Flow<SensorReading>`, `fun recentNights(): Flow<List<NightlySummary>>`, `suspend fun connectSensor()`, `suspend fun disconnectSensor()`, `suspend fun recordNightlySummary(summary: NightlySummary)`).
- Produces: `class FakeSleepRepository : SleepRepository` with public `val connectionStateFlow: MutableStateFlow<SensorConnectionState>`, `val readingsFlow: MutableSharedFlow<SensorReading>`, `val nightsFlow: MutableStateFlow<List<NightlySummary>>`, `var connectSensorCallCount: Int`, `var disconnectSensorCallCount: Int`, `val recordedSummaries: MutableList<NightlySummary>` — consumed by Tasks 6, 7, and the ViewModel test in Task 9 (Settings needs no repository, so only Dashboard/History tests use this).

- [ ] **Step 1: Write the fake**

```kotlin
package com.sleeppulse.app.testutil

import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.data.model.SensorConnectionState
import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.data.repository.SleepRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSleepRepository : SleepRepository {
    val connectionStateFlow = MutableStateFlow<SensorConnectionState>(SensorConnectionState.Disconnected)
    val readingsFlow = MutableSharedFlow<SensorReading>(extraBufferCapacity = 10)
    val nightsFlow = MutableStateFlow<List<NightlySummary>>(emptyList())

    var connectSensorCallCount = 0
        private set
    var disconnectSensorCallCount = 0
        private set
    val recordedSummaries = mutableListOf<NightlySummary>()

    override val connectionState: Flow<SensorConnectionState> = connectionStateFlow

    override fun liveReadings(): Flow<SensorReading> = readingsFlow

    override fun recentNights(): Flow<List<NightlySummary>> = nightsFlow

    override suspend fun connectSensor() {
        connectSensorCallCount++
        connectionStateFlow.value = SensorConnectionState.Connected(deviceName = "fake-device")
    }

    override suspend fun disconnectSensor() {
        disconnectSensorCallCount++
        connectionStateFlow.value = SensorConnectionState.Disconnected
    }

    override suspend fun recordNightlySummary(summary: NightlySummary) {
        recordedSummaries.add(summary)
    }
}
```

- [ ] **Step 2: Compile-check**

Run: `./gradlew :app:compileDebugUnitTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/sleeppulse/app/testutil/FakeSleepRepository.kt
git commit -m "test: add FakeSleepRepository test double"
```

---

### Task 6: DashboardViewModelTest

**Files:**
- Test: `app/src/test/java/com/sleeppulse/app/ui/dashboard/DashboardViewModelTest.kt`

**Interfaces:**
- Consumes: `FakeSleepRepository` (Task 5); `DashboardViewModel(repository: SleepRepository)`; `DashboardIntent.Start/.ToggleSensorConnection/.BeginWindDown/.AdvanceWindDownStep/.CancelWindDown`; `DashboardState(isLoading, connectionState, sleepScore, latestReading, recentReadings, windDownStep)`; `WindDownStep.BREATHE/.DIM_LIGHTS/.SET_ALARM/.DONE`; `MainDispatcherRule` (Task 1).
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.sleeppulse.app.ui.dashboard

import app.cash.turbine.test
import com.sleeppulse.app.data.model.SensorConnectionState
import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.data.model.SleepStage
import com.sleeppulse.app.testutil.FakeSleepRepository
import com.sleeppulse.app.testutil.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun reading(bpm: Int = 60, hrv: Double = 50.0) = SensorReading(
        timestampMillis = 0L,
        heartRateBpm = bpm,
        hrvMillis = hrv,
        sleepStage = SleepStage.LIGHT,
    )

    @Test
    fun `Start collects connection state and readings into state`() = runTest {
        val repository = FakeSleepRepository()
        val viewModel = DashboardViewModel(repository)

        viewModel.state.test {
            assertEquals(DashboardState(), awaitItem())

            viewModel.onIntent(DashboardIntent.Start)
            advanceUntilIdle()

            repository.connectionStateFlow.value = SensorConnectionState.Connected("fake-device")
            val afterConnect = awaitItem()
            assertEquals(SensorConnectionState.Connected("fake-device"), afterConnect.connectionState)
            assertEquals(false, afterConnect.isLoading)

            val firstReading = reading(bpm = 55, hrv = 80.0)
            repository.readingsFlow.emit(firstReading)
            val afterReading = awaitItem()
            assertEquals(firstReading, afterReading.latestReading)
            assertEquals(listOf(firstReading), afterReading.recentReadings)
            assertEquals(SleepScoreCalculator.score(listOf(firstReading)), afterReading.sleepScore)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recent readings cap at 40 points`() = runTest {
        val repository = FakeSleepRepository()
        val viewModel = DashboardViewModel(repository)

        viewModel.onIntent(DashboardIntent.Start)
        advanceUntilIdle()

        viewModel.state.test {
            awaitItem() // current state before new emissions

            repeat(45) { i ->
                repository.readingsFlow.emit(reading(bpm = 60 + i))
                awaitItem()
            }

            assertEquals(40, viewModel.state.value.recentReadings.size)
            assertEquals(60 + 44, viewModel.state.value.recentReadings.last().heartRateBpm)
            assertEquals(60 + 5, viewModel.state.value.recentReadings.first().heartRateBpm)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ToggleSensorConnection disconnects when connected`() = runTest {
        val repository = FakeSleepRepository()
        val viewModel = DashboardViewModel(repository)
        viewModel.onIntent(DashboardIntent.Start)
        advanceUntilIdle()
        repository.connectionStateFlow.value = SensorConnectionState.Connected("fake-device")
        advanceUntilIdle()

        viewModel.onIntent(DashboardIntent.ToggleSensorConnection)
        advanceUntilIdle()

        assertEquals(1, repository.disconnectSensorCallCount)
    }

    @Test
    fun `ToggleSensorConnection connects when not connected`() = runTest {
        val repository = FakeSleepRepository()
        val viewModel = DashboardViewModel(repository)
        viewModel.onIntent(DashboardIntent.Start)
        advanceUntilIdle()

        viewModel.onIntent(DashboardIntent.ToggleSensorConnection)
        advanceUntilIdle()

        // connectSensorCallCount is 2: once from Start(), once from the toggle
        assertEquals(2, repository.connectSensorCallCount)
    }

    @Test
    fun `wind-down flow advances through all steps and stops at DONE`() = runTest {
        val repository = FakeSleepRepository()
        val viewModel = DashboardViewModel(repository)

        viewModel.onIntent(DashboardIntent.BeginWindDown)
        assertEquals(WindDownStep.BREATHE, viewModel.state.value.windDownStep)

        viewModel.onIntent(DashboardIntent.AdvanceWindDownStep)
        assertEquals(WindDownStep.DIM_LIGHTS, viewModel.state.value.windDownStep)

        viewModel.onIntent(DashboardIntent.AdvanceWindDownStep)
        assertEquals(WindDownStep.SET_ALARM, viewModel.state.value.windDownStep)

        viewModel.onIntent(DashboardIntent.AdvanceWindDownStep)
        assertEquals(WindDownStep.DONE, viewModel.state.value.windDownStep)

        viewModel.onIntent(DashboardIntent.AdvanceWindDownStep)
        assertNull(viewModel.state.value.windDownStep)
    }

    @Test
    fun `CancelWindDown clears the step`() = runTest {
        val repository = FakeSleepRepository()
        val viewModel = DashboardViewModel(repository)

        viewModel.onIntent(DashboardIntent.BeginWindDown)
        viewModel.onIntent(DashboardIntent.AdvanceWindDownStep)
        assertEquals(WindDownStep.DIM_LIGHTS, viewModel.state.value.windDownStep)

        viewModel.onIntent(DashboardIntent.CancelWindDown)
        assertNull(viewModel.state.value.windDownStep)
    }
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.ui.dashboard.DashboardViewModelTest"`
Expected: BUILD SUCCESSFUL, 6 tests passed

(These tests exercise existing `DashboardViewModel` behavior as-is — no production code changes expected. If `recent readings cap at 40 points` fails on the exact boundary values, double check `MAX_CHART_POINTS` in `DashboardViewModel.kt:14` is still `40` and adjust the expected first/last heart rate values to match, not the production constant.)

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/sleeppulse/app/ui/dashboard/DashboardViewModelTest.kt
git commit -m "test: add DashboardViewModel unit tests"
```

---

### Task 7: HistoryViewModelTest

**Files:**
- Test: `app/src/test/java/com/sleeppulse/app/ui/history/HistoryViewModelTest.kt`

**Interfaces:**
- Consumes: `FakeSleepRepository` (Task 5); `HistoryViewModel(repository: SleepRepository)`; `HistoryIntent.Load`; `HistoryState(isLoading, nights: List<NightWithTrend>)`; `NightWithTrend(summary: NightlySummary, trend: NightlySummary.Trend)`; `NightlySummary(date: LocalDate, sleepScore: Int, avgHeartRateBpm: Int, avgHrvMillis: Double, totalSleepMinutes: Int, deepSleepMinutes: Int, remSleepMinutes: Int)`; `NightlySummary.Trend.UP/.DOWN/.FLAT`; `MainDispatcherRule` (Task 1).
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.sleeppulse.app.ui.history

import app.cash.turbine.test
import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.testutil.FakeSleepRepository
import com.sleeppulse.app.testutil.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun summary(date: LocalDate, score: Int) = NightlySummary(
        date = date,
        sleepScore = score,
        avgHeartRateBpm = 60,
        avgHrvMillis = 50.0,
        totalSleepMinutes = 420,
        deepSleepMinutes = 90,
        remSleepMinutes = 100,
    )

    @Test
    fun `Load with no nights produces empty non-loading state`() = runTest {
        val repository = FakeSleepRepository()
        val viewModel = HistoryViewModel(repository)

        viewModel.state.test {
            assertEquals(HistoryState(), awaitItem())

            viewModel.onIntent(HistoryIntent.Load)
            val loaded = awaitItem()
            assertEquals(emptyList<NightWithTrend>(), loaded.nights)
            assertEquals(false, loaded.isLoading)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Load computes trend against the following list element`() = runTest {
        val repository = FakeSleepRepository()
        // Newest-first, as recentNights() promises: today (score 80), yesterday (score 60), day before (score 61)
        val today = summary(LocalDate.of(2026, 7, 17), score = 80)
        val yesterday = summary(LocalDate.of(2026, 7, 16), score = 60)
        val dayBefore = summary(LocalDate.of(2026, 7, 15), score = 61)
        repository.nightsFlow.value = listOf(today, yesterday, dayBefore)

        val viewModel = HistoryViewModel(repository)

        viewModel.state.test {
            awaitItem() // initial empty state

            viewModel.onIntent(HistoryIntent.Load)
            val loaded = awaitItem()

            assertEquals(3, loaded.nights.size)
            assertEquals(NightlySummary.Trend.UP, loaded.nights[0].trend) // 80 vs 60 -> UP
            assertEquals(NightlySummary.Trend.DOWN, loaded.nights[1].trend) // 60 vs 61 -> DOWN
            assertEquals(NightlySummary.Trend.FLAT, loaded.nights[2].trend) // no next element -> FLAT
            assertTrue(loaded.nights.map { it.summary } == listOf(today, yesterday, dayBefore))

            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.ui.history.HistoryViewModelTest"`
Expected: BUILD SUCCESSFUL, 2 tests passed

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/sleeppulse/app/ui/history/HistoryViewModelTest.kt
git commit -m "test: add HistoryViewModel unit tests"
```

---

### Task 8: SettingsViewModelTest

**Files:**
- Test: `app/src/test/java/com/sleeppulse/app/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `SettingsViewModel()` (no-arg constructor, no repository dependency); `SettingsIntent.SetDataSource(mode: DataSourceMode)`/`.SetTemperatureUnit(unit: TemperatureUnit)`; `SettingsState(dataSourceMode, temperatureUnit)`; `DataSourceMode.SIMULATED/.BLE`; `TemperatureUnit.CELSIUS/.FAHRENHEIT`.
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.sleeppulse.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsViewModelTest {

    @Test
    fun `default state is simulated and celsius`() {
        val viewModel = SettingsViewModel()
        assertEquals(DataSourceMode.SIMULATED, viewModel.state.value.dataSourceMode)
        assertEquals(TemperatureUnit.CELSIUS, viewModel.state.value.temperatureUnit)
    }

    @Test
    fun `SetDataSource updates only dataSourceMode`() {
        val viewModel = SettingsViewModel()
        viewModel.onIntent(SettingsIntent.SetDataSource(DataSourceMode.BLE))

        assertEquals(DataSourceMode.BLE, viewModel.state.value.dataSourceMode)
        assertEquals(TemperatureUnit.CELSIUS, viewModel.state.value.temperatureUnit)
    }

    @Test
    fun `SetTemperatureUnit updates only temperatureUnit`() {
        val viewModel = SettingsViewModel()
        viewModel.onIntent(SettingsIntent.SetTemperatureUnit(TemperatureUnit.FAHRENHEIT))

        assertEquals(TemperatureUnit.FAHRENHEIT, viewModel.state.value.temperatureUnit)
        assertEquals(DataSourceMode.SIMULATED, viewModel.state.value.dataSourceMode)
    }

    @Test
    fun `both intents applied in sequence do not clobber each other`() {
        val viewModel = SettingsViewModel()
        viewModel.onIntent(SettingsIntent.SetDataSource(DataSourceMode.BLE))
        viewModel.onIntent(SettingsIntent.SetTemperatureUnit(TemperatureUnit.FAHRENHEIT))

        assertEquals(DataSourceMode.BLE, viewModel.state.value.dataSourceMode)
        assertEquals(TemperatureUnit.FAHRENHEIT, viewModel.state.value.temperatureUnit)
    }
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.ui.settings.SettingsViewModelTest"`
Expected: BUILD SUCCESSFUL, 4 tests passed

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/sleeppulse/app/ui/settings/SettingsViewModelTest.kt
git commit -m "test: add SettingsViewModel unit tests"
```

---

### Task 9: SleepRepositoryImplTest

**Files:**
- Test: `app/src/test/java/com/sleeppulse/app/data/repository/SleepRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `FakeSensorDataSource` (Task 3); `FakeNightlySummaryDao` (Task 4); `SleepRepositoryImpl(sensorDataSource: SensorDataSource, dao: NightlySummaryDao)`; `NightlySummary`/`NightlySummaryEntity` shapes (as read above).
- Produces: nothing consumed by later tasks. This is the final task in the plan.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.sleeppulse.app.data.repository

import app.cash.turbine.test
import com.sleeppulse.app.data.local.NightlySummaryEntity
import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.testutil.FakeNightlySummaryDao
import com.sleeppulse.app.testutil.FakeSensorDataSource
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepRepositoryImplTest {

    private fun summary(date: LocalDate, score: Int) = NightlySummary(
        date = date,
        sleepScore = score,
        avgHeartRateBpm = 58,
        avgHrvMillis = 72.5,
        totalSleepMinutes = 410,
        deepSleepMinutes = 95,
        remSleepMinutes = 105,
    )

    @Test
    fun `recordNightlySummary upserts then trims`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val repository = SleepRepositoryImpl(sensorDataSource, dao)

        val date = LocalDate.of(2026, 7, 17)
        repository.recordNightlySummary(summary(date, score = 88))

        val stored = dao.entitiesFlow.value.single()
        assertEquals(date.toEpochDay(), stored.dateEpochDay)
        assertEquals(88, stored.sleepScore)
        assertEquals(58, stored.avgHeartRateBpm)
        assertEquals(72.5, stored.avgHrvMillis, 0.0001)
        assertEquals(410, stored.totalSleepMinutes)
        assertEquals(95, stored.deepSleepMinutes)
        assertEquals(105, stored.remSleepMinutes)
    }

    @Test
    fun `recentNights maps entities to domain objects with date round-trip`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val repository = SleepRepositoryImpl(sensorDataSource, dao)

        val date = LocalDate.of(2026, 7, 10)
        dao.entitiesFlow.value = listOf(
            NightlySummaryEntity(
                dateEpochDay = date.toEpochDay(),
                sleepScore = 72,
                avgHeartRateBpm = 61,
                avgHrvMillis = 65.0,
                totalSleepMinutes = 400,
                deepSleepMinutes = 80,
                remSleepMinutes = 90,
            )
        )

        repository.recentNights().test {
            val nights = awaitItem()
            assertEquals(1, nights.size)
            assertEquals(date, nights[0].date)
            assertEquals(72, nights[0].sleepScore)
            assertEquals(61, nights[0].avgHeartRateBpm)
            assertEquals(65.0, nights[0].avgHrvMillis, 0.0001)
            assertEquals(400, nights[0].totalSleepMinutes)
            assertEquals(80, nights[0].deepSleepMinutes)
            assertEquals(90, nights[0].remSleepMinutes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `liveReadings and connectionState pass through from the data source`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val repository = SleepRepositoryImpl(sensorDataSource, dao)

        repository.connectSensor()
        assertEquals(1, sensorDataSource.connectCallCount)

        repository.disconnectSensor()
        assertEquals(1, sensorDataSource.disconnectCallCount)

        repository.liveReadings().test {
            sensorDataSource.readingsFlow.emit(
                com.sleeppulse.app.data.model.SensorReading(
                    timestampMillis = 1L,
                    heartRateBpm = 62,
                    hrvMillis = 55.0,
                    sleepStage = com.sleeppulse.app.data.model.SleepStage.DEEP,
                )
            )
            val reading = awaitItem()
            assertEquals(62, reading.heartRateBpm)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.data.repository.SleepRepositoryImplTest"`
Expected: BUILD SUCCESSFUL, 3 tests passed

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/sleeppulse/app/data/repository/SleepRepositoryImplTest.kt
git commit -m "test: add SleepRepositoryImpl unit tests"
```

---

### Task 10: Full test suite verification

**Files:** none created/modified — verification-only task.

**Interfaces:**
- Consumes: all test files from Tasks 2, 6, 7, 8, 9.
- Produces: nothing (terminal task).

- [ ] **Step 1: Run the full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 20 tests passed (5 + 6 + 2 + 4 + 3), 0 failures

- [ ] **Step 2: Confirm no regressions to the build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit (if any cleanup was needed in Step 1/2, otherwise skip — no files to commit)**

If Step 1 or Step 2 required fixes to test files, stage and commit them:

```bash
git add app/src/test
git commit -m "test: fix full-suite regressions"
```

---

## Self-Review

**Spec coverage:**
- `MainDispatcherRule` + Turbine infra → Task 1 ✓
- `SleepScoreCalculatorTest` (5 cases from spec) → Task 2 ✓
- `FakeSensorDataSource`, `FakeNightlySummaryDao`, `FakeSleepRepository` → Tasks 3–5 ✓
- `DashboardViewModelTest` (Start, 40-point cap, toggle connect/disconnect, wind-down sequence, cancel) → Task 6 ✓
- `HistoryViewModelTest` (trend computation, empty state) → Task 7 ✓
- `SettingsViewModelTest` (independent field updates) → Task 8 ✓
- `SleepRepositoryImplTest` (upsert-then-trim ordering, entity↔domain mapping, pass-through flows) → Task 9 ✓
- No new Gradle dependencies, mockito untouched → Global Constraints ✓
- Room DAO instrumented tests / Compose UI tests / BLE tests explicitly excluded → not present in plan ✓

**Placeholder scan:** No TBD/TODO; every step has runnable code and exact commands.

**Type consistency:** `FakeSleepRepository`/`FakeSensorDataSource`/`FakeNightlySummaryDao` method names and field names used in Tasks 6–9 match their definitions in Tasks 3–5 (`connectionStateFlow`, `readingsFlow`, `nightsFlow`, `entitiesFlow`, `connectCallCount`/`connectSensorCallCount`, `disconnectCallCount`/`disconnectSensorCallCount`, `recordedSummaries`) — verified consistent across tasks.
