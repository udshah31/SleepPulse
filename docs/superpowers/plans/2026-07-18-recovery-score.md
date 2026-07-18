# Recovery Score Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Recovery Score to the Dashboard that tells users whether they're recovered based on last night's HR/HRV vs. a 7-night rolling baseline, and add the minimal recording hook needed to make that baseline data exist.

**Architecture:** Two new pure, unit-tested functions (`RecoveryScoreCalculator`, `NightSummaryBuilder`) plus targeted changes to the existing `DashboardViewModel`/`DashboardContract` (accumulate a session's readings, record a summary on sensor disconnect, collect `recentNights()` to compute today's recovery result) and a new `RecoveryScoreCard` composable wired into `DashboardScreen`.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Hilt, kotlinx-coroutines, JUnit4/Turbine for tests (all already in the project — no new dependencies).

## Global Constraints

- No new Gradle dependencies.
- `RecoveryScoreCalculator` returns `null` when fewer than 3 baseline nights exist — never show a misleading score off insufficient data.
- Baseline is always `recentNights().drop(1).take(7)` (up to 7 nights preceding last night, excluding last night itself) — not user-configurable in this pass.
- Score formula: `score = (hrvComponent * 0.6 + rhrComponent * 0.4).toInt().coerceIn(0, 100)` where each component is `(50 + deviation * 200).coerceIn(0.0, 100.0)` — HRV-led weighting, exact values from the spec.
- Tiers: 80-100 OPTIMAL, 60-79 ADEQUATE, 40-59 LOW, 0-39 POOR, with the exact guidance strings specified per tier.
- The nightly-summary recording hook is minimal: triggered only on sensor disconnect, skipped entirely if no readings were accumulated during the session. This plan does NOT build a full "end of night" UI flow, editing, multi-session nights, or naps — those are explicitly out of scope.
- Recovery Score is Dashboard-only in this pass — not shown in History.
- `RecoveryResult` is never persisted — always recomputed from `recentNights()` on each emission.

---

## File Structure

Create:
- `app/src/main/java/com/sleeppulse/app/ui/dashboard/RecoveryScoreCalculator.kt` — pure scoring function, `RecoveryTier` enum, `RecoveryResult` data class
- `app/src/test/java/com/sleeppulse/app/ui/dashboard/RecoveryScoreCalculatorTest.kt`
- `app/src/main/java/com/sleeppulse/app/data/NightSummaryBuilder.kt` — pure function converting readings into a `NightlySummary`
- `app/src/test/java/com/sleeppulse/app/data/NightSummaryBuilderTest.kt`
- `app/src/main/java/com/sleeppulse/app/ui/components/RecoveryScoreCard.kt` — new composable

Modify:
- `app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardContract.kt` — add `recoveryResult` and `recordedNightsCount` to `DashboardState`
- `app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardViewModel.kt` — accumulate session readings, record on disconnect, collect `recentNights()`
- `app/src/test/java/com/sleeppulse/app/ui/dashboard/DashboardViewModelTest.kt` — append 4 new test cases
- `app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardScreen.kt` — render `RecoveryScoreCard`

---

### Task 1: RecoveryScoreCalculator

**Files:**
- Create: `app/src/main/java/com/sleeppulse/app/ui/dashboard/RecoveryScoreCalculator.kt`
- Test: `app/src/test/java/com/sleeppulse/app/ui/dashboard/RecoveryScoreCalculatorTest.kt`

**Interfaces:**
- Consumes: `NightlySummary(date: LocalDate, sleepScore: Int, avgHeartRateBpm: Int, avgHrvMillis: Double, totalSleepMinutes: Int, deepSleepMinutes: Int, remSleepMinutes: Int)` (existing, `app/src/main/java/com/sleeppulse/app/data/model/NightlySummary.kt`).
- Produces: `enum class RecoveryTier { OPTIMAL, ADEQUATE, LOW, POOR }`; `data class RecoveryResult(val score: Int, val tier: RecoveryTier, val guidance: String)`; `object RecoveryScoreCalculator { fun score(lastNight: NightlySummary, baseline: List<NightlySummary>): RecoveryResult? }` — consumed by Task 3 (`DashboardViewModel`) and Task 4 (`RecoveryScoreCard`).

- [ ] **Step 1: Write the implementation**

```kotlin
package com.sleeppulse.app.ui.dashboard

import com.sleeppulse.app.data.model.NightlySummary

enum class RecoveryTier { OPTIMAL, ADEQUATE, LOW, POOR }

data class RecoveryResult(
    val score: Int,
    val tier: RecoveryTier,
    val guidance: String,
)

/**
 * Compares last night's HR/HRV against a rolling baseline to estimate how recovered the
 * user is. Returns null when fewer than [MIN_BASELINE_NIGHTS] baseline nights are available —
 * showing a score off too little history would be misleading.
 */
object RecoveryScoreCalculator {

    private const val MIN_BASELINE_NIGHTS = 3

    fun score(lastNight: NightlySummary, baseline: List<NightlySummary>): RecoveryResult? {
        if (baseline.size < MIN_BASELINE_NIGHTS) return null

        val baselineAvgHrv = baseline.map { it.avgHrvMillis }.average()
        val baselineAvgHr = baseline.map { it.avgHeartRateBpm }.average()

        val hrvDeviation = (lastNight.avgHrvMillis - baselineAvgHrv) / baselineAvgHrv
        val rhrDeviation = (baselineAvgHr - lastNight.avgHeartRateBpm) / baselineAvgHr

        val hrvComponent = (50 + hrvDeviation * 200).coerceIn(0.0, 100.0)
        val rhrComponent = (50 + rhrDeviation * 200).coerceIn(0.0, 100.0)

        val score = (hrvComponent * 0.6 + rhrComponent * 0.4).toInt().coerceIn(0, 100)
        val tier = tierFor(score)
        return RecoveryResult(score = score, tier = tier, guidance = guidanceFor(tier))
    }

    private fun tierFor(score: Int): RecoveryTier = when {
        score >= 80 -> RecoveryTier.OPTIMAL
        score >= 60 -> RecoveryTier.ADEQUATE
        score >= 40 -> RecoveryTier.LOW
        else -> RecoveryTier.POOR
    }

    private fun guidanceFor(tier: RecoveryTier): String = when (tier) {
        RecoveryTier.OPTIMAL -> "Fully recovered — good day to push yourself."
        RecoveryTier.ADEQUATE -> "Recovered — normal training/activity load is fine."
        RecoveryTier.LOW -> "Under-recovered — consider an easier day."
        RecoveryTier.POOR -> "Poorly recovered — prioritize rest today."
    }
}
```

- [ ] **Step 2: Write the tests**

Note: assertions below use ranges rather than exact score equality in most cases, deliberately — the formula involves floating-point division/multiplication (e.g. `0.1` is not exactly representable in binary), and `.toInt()` truncates, so an exact-equality assertion on a computed score risks failing on a harmless off-by-one from floating-point rounding. Tier and range checks verify the same behavior without that brittleness.

```kotlin
package com.sleeppulse.app.ui.dashboard

import com.sleeppulse.app.data.model.NightlySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecoveryScoreCalculatorTest {

    private fun night(hrv: Double, hr: Int) = NightlySummary(
        date = LocalDate.of(2026, 7, 18),
        sleepScore = 70,
        avgHeartRateBpm = hr,
        avgHrvMillis = hrv,
        totalSleepMinutes = 420,
        deepSleepMinutes = 90,
        remSleepMinutes = 100,
    )

    @Test
    fun `fewer than 3 baseline nights returns null`() {
        val lastNight = night(hrv = 50.0, hr = 60)
        assertNull(RecoveryScoreCalculator.score(lastNight, emptyList()))
        assertNull(RecoveryScoreCalculator.score(lastNight, listOf(night(50.0, 60))))
        assertNull(RecoveryScoreCalculator.score(lastNight, listOf(night(50.0, 60), night(50.0, 60))))
    }

    @Test
    fun `exactly 3 baseline nights with no deviation scores around the midpoint`() {
        val baseline = listOf(night(50.0, 60), night(50.0, 60), night(50.0, 60))
        val lastNight = night(hrv = 50.0, hr = 60)

        val result = RecoveryScoreCalculator.score(lastNight, baseline)

        assertTrue(result != null)
        assertEquals(RecoveryTier.LOW, result!!.tier)
        assertTrue("expected score in 45..55, was ${result.score}", result.score in 45..55)
    }

    @Test
    fun `well-recovered inputs (higher HRV, lower RHR than baseline) score OPTIMAL`() {
        val baseline = listOf(night(50.0, 60), night(50.0, 60), night(50.0, 60))
        val lastNight = night(hrv = 62.5, hr = 54) // +25% HRV, -10% RHR vs baseline

        val result = RecoveryScoreCalculator.score(lastNight, baseline)!!

        assertEquals(RecoveryTier.OPTIMAL, result.tier)
        assertTrue("expected score >= 80, was ${result.score}", result.score >= 80)
    }

    @Test
    fun `poorly-recovered inputs (lower HRV, higher RHR than baseline) score POOR`() {
        val baseline = listOf(night(50.0, 60), night(50.0, 60), night(50.0, 60))
        val lastNight = night(hrv = 37.5, hr = 66) // -25% HRV, +10% RHR vs baseline

        val result = RecoveryScoreCalculator.score(lastNight, baseline)!!

        assertEquals(RecoveryTier.POOR, result.tier)
        assertTrue("expected score <= 39, was ${result.score}", result.score <= 39)
    }

    @Test
    fun `moderately-recovered inputs score ADEQUATE`() {
        val baseline = listOf(night(50.0, 60), night(50.0, 60), night(50.0, 60))
        val lastNight = night(hrv = 55.0, hr = 54) // +10% HRV, -10% RHR vs baseline

        val result = RecoveryScoreCalculator.score(lastNight, baseline)!!

        assertEquals(RecoveryTier.ADEQUATE, result.tier)
        assertTrue("expected score in 60..79, was ${result.score}", result.score in 60..79)
    }

    @Test
    fun `HRV is weighted more heavily than RHR (60-40 split)`() {
        val baseline = listOf(night(50.0, 60), night(50.0, 60), night(50.0, 60))
        // HRV strongly better (+25%, pushes hrvComponent to 100), RHR strongly worse
        // (+16.67%, pushes rhrComponent to 0). A 50/50 average of 100 and 0 would be
        // exactly 50; the 60/40 weighting toward HRV should pull the result above 50.
        val lastNight = night(hrv = 62.5, hr = 70)

        val result = RecoveryScoreCalculator.score(lastNight, baseline)!!

        assertTrue("expected score > 50 (HRV-led weighting), was ${result.score}", result.score > 50)
    }
}
```

- [ ] **Step 3: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.ui.dashboard.RecoveryScoreCalculatorTest"`
Expected: BUILD SUCCESSFUL, 6 tests passed

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/ui/dashboard/RecoveryScoreCalculator.kt app/src/test/java/com/sleeppulse/app/ui/dashboard/RecoveryScoreCalculatorTest.kt
git commit -m "feat: add RecoveryScoreCalculator"
```

---

### Task 2: NightSummaryBuilder

**Files:**
- Create: `app/src/main/java/com/sleeppulse/app/data/NightSummaryBuilder.kt`
- Test: `app/src/test/java/com/sleeppulse/app/data/NightSummaryBuilderTest.kt`

**Interfaces:**
- Consumes: `SensorReading(timestampMillis: Long, heartRateBpm: Int, hrvMillis: Double, sleepStage: SleepStage)`, `SleepStage.AWAKE/.LIGHT/.DEEP/.REM` (existing, `app/src/main/java/com/sleeppulse/app/data/model/SensorModels.kt`); `SleepScoreCalculator.score(readings: List<SensorReading>): Int` (existing, `app/src/main/java/com/sleeppulse/app/ui/dashboard/SleepScoreCalculator.kt`); `NightlySummary` (as in Task 1).
- Produces: `object NightSummaryBuilder { fun build(readings: List<SensorReading>, date: LocalDate): NightlySummary }` — consumed by Task 3 (`DashboardViewModel`). Caller must ensure `readings` is non-empty.

- [ ] **Step 1: Write the implementation**

```kotlin
package com.sleeppulse.app.data

import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.data.model.SleepStage
import com.sleeppulse.app.ui.dashboard.SleepScoreCalculator
import java.time.LocalDate

/**
 * Converts a full session's [SensorReading]s into a storable [NightlySummary]. Caller must
 * ensure [readings] is non-empty — behavior is undefined otherwise.
 */
object NightSummaryBuilder {

    fun build(readings: List<SensorReading>, date: LocalDate): NightlySummary {
        val avgHeartRateBpm = readings.map { it.heartRateBpm }.average().toInt()
        val avgHrvMillis = readings.map { it.hrvMillis }.average()

        var totalMinutes = 0L
        var deepMinutes = 0L
        var remMinutes = 0L
        for (i in 0 until readings.size - 1) {
            val deltaMinutes = (readings[i + 1].timestampMillis - readings[i].timestampMillis) / 60_000L
            totalMinutes += deltaMinutes
            when (readings[i].sleepStage) {
                SleepStage.DEEP -> deepMinutes += deltaMinutes
                SleepStage.REM -> remMinutes += deltaMinutes
                SleepStage.AWAKE, SleepStage.LIGHT -> Unit
            }
        }

        return NightlySummary(
            date = date,
            sleepScore = SleepScoreCalculator.score(readings),
            avgHeartRateBpm = avgHeartRateBpm,
            avgHrvMillis = avgHrvMillis,
            totalSleepMinutes = totalMinutes.toInt(),
            deepSleepMinutes = deepMinutes.toInt(),
            remSleepMinutes = remMinutes.toInt(),
        )
    }
}
```

- [ ] **Step 2: Write the tests**

```kotlin
package com.sleeppulse.app.data

import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.data.model.SleepStage
import com.sleeppulse.app.ui.dashboard.SleepScoreCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class NightSummaryBuilderTest {

    private fun reading(timestampMillis: Long, stage: SleepStage) = SensorReading(
        timestampMillis = timestampMillis,
        heartRateBpm = 60,
        hrvMillis = 50.0,
        sleepStage = stage,
    )

    @Test
    fun `averages heart rate and hrv across all readings`() {
        val readings = listOf(
            reading(0L, SleepStage.AWAKE).copy(heartRateBpm = 58, hrvMillis = 60.0),
            reading(300_000L, SleepStage.LIGHT).copy(heartRateBpm = 62, hrvMillis = 55.0),
        )

        val date = LocalDate.of(2026, 7, 18)
        val summary = NightSummaryBuilder.build(readings, date)

        assertEquals(date, summary.date)
        assertEquals(60, summary.avgHeartRateBpm)
        assertEquals(57.5, summary.avgHrvMillis, 0.0001)
    }

    @Test
    fun `sums timestamp deltas per stage into total, deep, and rem minutes`() {
        // AWAKE 0->5min (5min AWAKE), LIGHT 5->15min (10min LIGHT), DEEP 15->45min (30min DEEP),
        // LIGHT 45->60min (15min LIGHT), REM 60->90min (30min REM). Total = 5+10+30+15+30 = 90.
        val readings = listOf(
            reading(0L, SleepStage.AWAKE),
            reading(300_000L, SleepStage.LIGHT),
            reading(900_000L, SleepStage.DEEP),
            reading(2_700_000L, SleepStage.LIGHT),
            reading(3_600_000L, SleepStage.REM),
            reading(5_400_000L, SleepStage.LIGHT),
        )

        val summary = NightSummaryBuilder.build(readings, LocalDate.of(2026, 7, 18))

        assertEquals(90, summary.totalSleepMinutes)
        assertEquals(30, summary.deepSleepMinutes)
        assertEquals(30, summary.remSleepMinutes)
    }

    @Test
    fun `sleepScore matches SleepScoreCalculator over the same readings`() {
        val readings = listOf(
            reading(0L, SleepStage.LIGHT),
            reading(300_000L, SleepStage.LIGHT),
        )

        val summary = NightSummaryBuilder.build(readings, LocalDate.of(2026, 7, 18))

        assertEquals(SleepScoreCalculator.score(readings), summary.sleepScore)
    }

    @Test
    fun `single reading produces zero total, deep, and rem minutes`() {
        val readings = listOf(reading(0L, SleepStage.DEEP))

        val summary = NightSummaryBuilder.build(readings, LocalDate.of(2026, 7, 18))

        assertEquals(0, summary.totalSleepMinutes)
        assertEquals(0, summary.deepSleepMinutes)
        assertEquals(0, summary.remSleepMinutes)
    }
}
```

- [ ] **Step 3: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.data.NightSummaryBuilderTest"`
Expected: BUILD SUCCESSFUL, 4 tests passed

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/data/NightSummaryBuilder.kt app/src/test/java/com/sleeppulse/app/data/NightSummaryBuilderTest.kt
git commit -m "feat: add NightSummaryBuilder"
```

---

### Task 3: DashboardViewModel wiring (record on disconnect, compute recovery)

**Files:**
- Modify: `app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardContract.kt`
- Modify: `app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardViewModel.kt`
- Modify: `app/src/test/java/com/sleeppulse/app/ui/dashboard/DashboardViewModelTest.kt` (append tests)

**Interfaces:**
- Consumes: `RecoveryScoreCalculator.score(...)`, `RecoveryResult`, `RecoveryTier` (Task 1); `NightSummaryBuilder.build(...)` (Task 2); `SleepRepository.recordNightlySummary(summary: NightlySummary)` / `.recentNights(): Flow<List<NightlySummary>>` (existing).
- Produces: `DashboardState.recoveryResult: RecoveryResult?` and `DashboardState.recordedNightsCount: Int` — consumed by Task 4 (`RecoveryScoreCard`/`DashboardScreen`).

- [ ] **Step 1: Modify `DashboardContract.kt`**

The current file (`app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardContract.kt`) is:

```kotlin
package com.sleeppulse.app.ui.dashboard

import com.sleeppulse.app.data.model.SensorConnectionState
import com.sleeppulse.app.data.model.SensorReading

/** User-triggered actions on the Dashboard screen. */
sealed class DashboardIntent {
    data object Start : DashboardIntent()
    data object ToggleSensorConnection : DashboardIntent()
    data object BeginWindDown : DashboardIntent()
    data object AdvanceWindDownStep : DashboardIntent()
    data object CancelWindDown : DashboardIntent()
}

enum class WindDownStep { BREATHE, DIM_LIGHTS, SET_ALARM, DONE }

/** Everything the Dashboard Composable needs to render; produced only by [DashboardViewModel]. */
data class DashboardState(
    val isLoading: Boolean = true,
    val connectionState: SensorConnectionState = SensorConnectionState.Disconnected,
    val sleepScore: Int = 0,
    val latestReading: SensorReading? = null,
    val recentReadings: List<SensorReading> = emptyList(),
    val windDownStep: WindDownStep? = null,
) {
    val isConnected: Boolean
        get() = connectionState is SensorConnectionState.Connected
}
```

Replace the `DashboardState` data class with:

```kotlin
/** Everything the Dashboard Composable needs to render; produced only by [DashboardViewModel]. */
data class DashboardState(
    val isLoading: Boolean = true,
    val connectionState: SensorConnectionState = SensorConnectionState.Disconnected,
    val sleepScore: Int = 0,
    val latestReading: SensorReading? = null,
    val recentReadings: List<SensorReading> = emptyList(),
    val windDownStep: WindDownStep? = null,
    val recoveryResult: RecoveryResult? = null,
    val recordedNightsCount: Int = 0,
) {
    val isConnected: Boolean
        get() = connectionState is SensorConnectionState.Connected
}
```

(No new import needed — `RecoveryResult` is in the same `com.sleeppulse.app.ui.dashboard` package as this file.)

- [ ] **Step 2: Modify `DashboardViewModel.kt`**

The current file (`app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardViewModel.kt`) is:

```kotlin
package com.sleeppulse.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleeppulse.app.data.repository.SleepRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MAX_CHART_POINTS = 40

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: SleepRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    fun onIntent(intent: DashboardIntent) {
        when (intent) {
            DashboardIntent.Start -> start()
            DashboardIntent.ToggleSensorConnection -> toggleConnection()
            DashboardIntent.BeginWindDown -> _state.update { it.copy(windDownStep = WindDownStep.BREATHE) }
            DashboardIntent.AdvanceWindDownStep -> advanceWindDown()
            DashboardIntent.CancelWindDown -> _state.update { it.copy(windDownStep = null) }
        }
    }

    private fun start() {
        viewModelScope.launch {
            repository.connectionState.collect { connection ->
                _state.update { it.copy(connectionState = connection, isLoading = false) }
            }
        }
        viewModelScope.launch {
            repository.connectSensor()
        }
        viewModelScope.launch {
            repository.liveReadings().collect { reading ->
                _state.update { current ->
                    val updatedHistory = (current.recentReadings + reading).takeLast(MAX_CHART_POINTS)
                    current.copy(
                        latestReading = reading,
                        recentReadings = updatedHistory,
                        sleepScore = SleepScoreCalculator.score(updatedHistory),
                        isLoading = false,
                    )
                }
            }
        }
    }

    private fun toggleConnection() {
        viewModelScope.launch {
            if (_state.value.isConnected) {
                repository.disconnectSensor()
            } else {
                repository.connectSensor()
            }
        }
    }

    private fun advanceWindDown() {
        val next = when (_state.value.windDownStep) {
            WindDownStep.BREATHE -> WindDownStep.DIM_LIGHTS
            WindDownStep.DIM_LIGHTS -> WindDownStep.SET_ALARM
            WindDownStep.SET_ALARM -> WindDownStep.DONE
            WindDownStep.DONE, null -> null
        }
        _state.update { it.copy(windDownStep = next) }
    }
}
```

Replace it entirely with:

```kotlin
package com.sleeppulse.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleeppulse.app.data.NightSummaryBuilder
import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.data.repository.SleepRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private const val MAX_CHART_POINTS = 40
private const val MAX_RECORDED_NIGHTS_DISPLAY = 4

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: SleepRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val sessionReadings = mutableListOf<SensorReading>()

    fun onIntent(intent: DashboardIntent) {
        when (intent) {
            DashboardIntent.Start -> start()
            DashboardIntent.ToggleSensorConnection -> toggleConnection()
            DashboardIntent.BeginWindDown -> _state.update { it.copy(windDownStep = WindDownStep.BREATHE) }
            DashboardIntent.AdvanceWindDownStep -> advanceWindDown()
            DashboardIntent.CancelWindDown -> _state.update { it.copy(windDownStep = null) }
        }
    }

    private fun start() {
        viewModelScope.launch {
            repository.connectionState.collect { connection ->
                _state.update { it.copy(connectionState = connection, isLoading = false) }
            }
        }
        viewModelScope.launch {
            repository.connectSensor()
        }
        viewModelScope.launch {
            repository.liveReadings().collect { reading ->
                sessionReadings.add(reading)
                _state.update { current ->
                    val updatedHistory = (current.recentReadings + reading).takeLast(MAX_CHART_POINTS)
                    current.copy(
                        latestReading = reading,
                        recentReadings = updatedHistory,
                        sleepScore = SleepScoreCalculator.score(updatedHistory),
                        isLoading = false,
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.recentNights().collect { nights ->
                _state.update {
                    it.copy(
                        recoveryResult = computeRecovery(nights),
                        recordedNightsCount = nights.size.coerceAtMost(MAX_RECORDED_NIGHTS_DISPLAY),
                    )
                }
            }
        }
    }

    private fun computeRecovery(nights: List<NightlySummary>): RecoveryResult? {
        val lastNight = nights.firstOrNull() ?: return null
        val baseline = nights.drop(1).take(7)
        return RecoveryScoreCalculator.score(lastNight, baseline)
    }

    private fun toggleConnection() {
        viewModelScope.launch {
            if (_state.value.isConnected) {
                if (sessionReadings.isNotEmpty()) {
                    repository.recordNightlySummary(
                        NightSummaryBuilder.build(sessionReadings.toList(), LocalDate.now())
                    )
                    sessionReadings.clear()
                }
                repository.disconnectSensor()
            } else {
                repository.connectSensor()
            }
        }
    }

    private fun advanceWindDown() {
        val next = when (_state.value.windDownStep) {
            WindDownStep.BREATHE -> WindDownStep.DIM_LIGHTS
            WindDownStep.DIM_LIGHTS -> WindDownStep.SET_ALARM
            WindDownStep.SET_ALARM -> WindDownStep.DONE
            WindDownStep.DONE, null -> null
        }
        _state.update { it.copy(windDownStep = next) }
    }
}
```

- [ ] **Step 3: Append new tests to `DashboardViewModelTest.kt`**

The current file's imports are:

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
```

Replace that import block with (adding `NightlySummary` and `LocalDate`):

```kotlin
package com.sleeppulse.app.ui.dashboard

import app.cash.turbine.test
import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.data.model.SensorConnectionState
import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.data.model.SleepStage
import com.sleeppulse.app.testutil.FakeSleepRepository
import com.sleeppulse.app.testutil.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
```

Then, immediately before the final closing `}` of the `DashboardViewModelTest` class (i.e. after the existing `CancelWindDown clears the step` test), insert these 4 new test methods:

```kotlin

    @Test
    fun `disconnecting after readings accumulated records a nightly summary`() = runTest {
        val repository = FakeSleepRepository()
        val viewModel = DashboardViewModel(repository)

        viewModel.onIntent(DashboardIntent.Start)
        advanceUntilIdle()

        repository.readingsFlow.emit(reading(bpm = 58, hrv = 60.0))
        advanceUntilIdle()
        repository.readingsFlow.emit(reading(bpm = 62, hrv = 55.0))
        advanceUntilIdle()

        repository.connectionStateFlow.value = SensorConnectionState.Connected("fake-device")
        advanceUntilIdle()

        viewModel.onIntent(DashboardIntent.ToggleSensorConnection)
        advanceUntilIdle()

        assertEquals(1, repository.recordedSummaries.size)
        val summary = repository.recordedSummaries.single()
        assertEquals(60, summary.avgHeartRateBpm)
        assertEquals(57.5, summary.avgHrvMillis, 0.0001)
        assertEquals(LocalDate.now(), summary.date)
    }

    @Test
    fun `disconnecting with no readings does not record a summary`() = runTest {
        val repository = FakeSleepRepository()
        val viewModel = DashboardViewModel(repository)

        viewModel.onIntent(DashboardIntent.Start)
        advanceUntilIdle()
        repository.connectionStateFlow.value = SensorConnectionState.Connected("fake-device")
        advanceUntilIdle()

        viewModel.onIntent(DashboardIntent.ToggleSensorConnection)
        advanceUntilIdle()

        assertEquals(0, repository.recordedSummaries.size)
    }

    @Test
    fun `recoveryResult is null until enough baseline nights are recorded`() = runTest {
        val repository = FakeSleepRepository()
        val viewModel = DashboardViewModel(repository)

        viewModel.onIntent(DashboardIntent.Start)
        advanceUntilIdle()

        assertNull(viewModel.state.value.recoveryResult)
        assertEquals(0, viewModel.state.value.recordedNightsCount)

        val night = NightlySummary(
            date = LocalDate.now(),
            sleepScore = 70,
            avgHeartRateBpm = 60,
            avgHrvMillis = 50.0,
            totalSleepMinutes = 420,
            deepSleepMinutes = 90,
            remSleepMinutes = 100,
        )
        // Only 2 baseline nights after dropping the first (last night) entry — below the
        // RecoveryScoreCalculator minimum of 3.
        repository.nightsFlow.value = listOf(night, night, night)
        advanceUntilIdle()

        assertNull(viewModel.state.value.recoveryResult)
        assertEquals(3, viewModel.state.value.recordedNightsCount)
    }

    @Test
    fun `recoveryResult is computed once enough baseline nights exist`() = runTest {
        val repository = FakeSleepRepository()
        val viewModel = DashboardViewModel(repository)

        viewModel.onIntent(DashboardIntent.Start)
        advanceUntilIdle()

        fun night(hrv: Double, hr: Int) = NightlySummary(
            date = LocalDate.now(),
            sleepScore = 70,
            avgHeartRateBpm = hr,
            avgHrvMillis = hrv,
            totalSleepMinutes = 420,
            deepSleepMinutes = 90,
            remSleepMinutes = 100,
        )

        val lastNight = night(hrv = 62.5, hr = 54) // well-recovered vs. the baseline below
        val baseline = listOf(night(50.0, 60), night(50.0, 60), night(50.0, 60))
        repository.nightsFlow.value = listOf(lastNight) + baseline
        advanceUntilIdle()

        val result = viewModel.state.value.recoveryResult
        assertEquals(RecoveryTier.OPTIMAL, result?.tier)
        assertEquals(4, viewModel.state.value.recordedNightsCount)
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.ui.dashboard.DashboardViewModelTest"`
Expected: BUILD SUCCESSFUL, 10 tests passed (6 existing + 4 new)

(These tests exercise real coroutine/StateFlow timing against the actual `DashboardViewModel`. If a test fails on exact emission count or ordering — the same class of issue found in the earlier test-suite work on this file — adjust the test's mechanics (e.g. an extra `advanceUntilIdle()` or `awaitItem()`) to match actual observed behavior, but do not change `DashboardViewModel.kt`'s logic to make a test pass.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardContract.kt app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardViewModel.kt app/src/test/java/com/sleeppulse/app/ui/dashboard/DashboardViewModelTest.kt
git commit -m "feat: record nightly summaries on disconnect and compute recovery result"
```

---

### Task 4: RecoveryScoreCard UI

**Files:**
- Create: `app/src/main/java/com/sleeppulse/app/ui/components/RecoveryScoreCard.kt`
- Modify: `app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardScreen.kt`

**Interfaces:**
- Consumes: `RecoveryResult(score: Int, tier: RecoveryTier, guidance: String)`, `RecoveryTier` (Task 1); `DashboardState.recoveryResult`/`.recordedNightsCount` (Task 3).
- Produces: `@Composable fun RecoveryScoreCard(recoveryResult: RecoveryResult?, recordedNightsCount: Int, modifier: Modifier = Modifier)` — this is the final task in the plan, nothing downstream consumes it.

- [ ] **Step 1: Write `RecoveryScoreCard.kt`**

```kotlin
package com.sleeppulse.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sleeppulse.app.ui.dashboard.RecoveryResult

private const val MIN_NIGHTS_FOR_RECOVERY = 4

/**
 * Shows today's Recovery Score (last night vs. a 7-night rolling baseline), or a
 * "building your baseline" message when there isn't yet enough recorded history.
 */
@Composable
fun RecoveryScoreCard(
    recoveryResult: RecoveryResult?,
    recordedNightsCount: Int,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (recoveryResult != null) {
                Text(
                    text = "Recovery: ${recoveryResult.score}",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = recoveryResult.tier.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = recoveryResult.guidance,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = "Building your baseline ($recordedNightsCount/$MIN_NIGHTS_FOR_RECOVERY nights)",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Modify `DashboardScreen.kt` to render the card**

The current file (`app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardScreen.kt`) is:

```kotlin
package com.sleeppulse.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sleeppulse.app.ui.components.LiveMetricChart
import com.sleeppulse.app.ui.components.SleepScoreGauge
import com.sleeppulse.app.ui.theme.RecoveryGreen
import com.sleeppulse.app.ui.theme.SleepIndigo

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(DashboardIntent.Start)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(text = "Tonight", style = MaterialTheme.typography.headlineMedium)

        SleepScoreGauge(score = state.sleepScore)

        Text(
            text = connectionLabel(state),
            style = MaterialTheme.typography.bodyMedium,
        )

        state.latestReading?.let { reading ->
            Text(text = "Heart rate ${reading.heartRateBpm} bpm  ·  HRV ${reading.hrvMillis.toInt()} ms")
        }

        Text(text = "Heart rate", style = MaterialTheme.typography.titleSmall)
        LiveMetricChart(
            values = state.recentReadings.map { it.heartRateBpm.toFloat() },
            color = SleepIndigo,
        )

        Text(text = "HRV", style = MaterialTheme.typography.titleSmall)
        LiveMetricChart(
            values = state.recentReadings.map { it.hrvMillis.toFloat() },
            color = RecoveryGreen,
        )

        OutlinedButton(onClick = { viewModel.onIntent(DashboardIntent.ToggleSensorConnection) }) {
            Text(if (state.isConnected) "Disconnect sensor" else "Connect sensor")
        }

        if (state.windDownStep == null) {
            Button(onClick = { viewModel.onIntent(DashboardIntent.BeginWindDown) }) {
                Text("Start wind-down")
            }
        } else {
            WindDownFlow(
                step = state.windDownStep!!,
                onAdvance = { viewModel.onIntent(DashboardIntent.AdvanceWindDownStep) },
                onCancel = { viewModel.onIntent(DashboardIntent.CancelWindDown) },
            )
        }
    }
}

private fun connectionLabel(state: DashboardState): String = when {
    state.isLoading -> "Connecting…"
    state.isConnected -> "Sensor connected"
    else -> "Sensor disconnected"
}
```

Replace the import block with (adding `fillMaxWidth` and `RecoveryScoreCard`):

```kotlin
package com.sleeppulse.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sleeppulse.app.ui.components.LiveMetricChart
import com.sleeppulse.app.ui.components.RecoveryScoreCard
import com.sleeppulse.app.ui.components.SleepScoreGauge
import com.sleeppulse.app.ui.theme.RecoveryGreen
import com.sleeppulse.app.ui.theme.SleepIndigo
```

Then insert the `RecoveryScoreCard` call directly after the `SleepScoreGauge(score = state.sleepScore)` line and before the `Text(text = connectionLabel(state), ...)` line:

```kotlin
        SleepScoreGauge(score = state.sleepScore)

        RecoveryScoreCard(
            recoveryResult = state.recoveryResult,
            recordedNightsCount = state.recordedNightsCount,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = connectionLabel(state),
            style = MaterialTheme.typography.bodyMedium,
        )
```

- [ ] **Step 3: Verify the app builds**

This is a Compose UI change with no automated UI test in this codebase (Compose/Espresso tests are out of scope, per the project's README). Verify via a full build instead:

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

Also re-run the full unit test suite to confirm nothing broke:

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 34 tests passed (20 from the prior test suite + 6 `RecoveryScoreCalculatorTest` + 4 `NightSummaryBuilderTest` + 4 new `DashboardViewModelTest` cases from Task 3 = 34)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/ui/components/RecoveryScoreCard.kt app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardScreen.kt
git commit -m "feat: render RecoveryScoreCard on the Dashboard"
```

---

## Self-Review

**Spec coverage:**
- `RecoveryScoreCalculator` with exact formula, tiers, guidance strings, null-below-3-baseline-nights → Task 1 ✓
- `NightSummaryBuilder` with avg HR/HRV, total/deep/rem minutes from timestamp deltas, matching `sleepScore` → Task 2 ✓
- `DashboardViewModel` session-reading accumulation, disconnect-triggered recording (skipped when empty), `recentNights()` collection → Task 3 ✓
- `DashboardState.recoveryResult`/`.recordedNightsCount` → Task 3 ✓
- `RecoveryScoreCard` UI with computed/building-baseline states → Task 4 ✓
- Testing plan from the spec (calculator edge cases, builder timestamp math, ViewModel recording/recovery behavior) → Tasks 1, 2, 3 ✓
- Out-of-scope items (History display, configurable baseline window, persisting `RecoveryResult`, full end-of-night flow) are absent from every task — correctly excluded ✓

**Placeholder scan:** No TBD/TODO; every step has complete runnable code and exact commands.

**Type consistency:** `RecoveryResult`/`RecoveryTier` (Task 1) used identically in Task 3 (`computeRecovery`, test assertions) and Task 4 (`RecoveryScoreCard` parameter types). `NightSummaryBuilder.build(readings, date)` signature (Task 2) matches its call site in Task 3's `toggleConnection()`. `DashboardState.recoveryResult`/`.recordedNightsCount` (Task 3) match the fields `RecoveryScoreCard` (Task 4) and `DashboardScreen`'s call site expect.
