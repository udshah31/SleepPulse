# Dashboard Premium Metrics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Dashboard's flat "Heart rate 64 bpm · HRV 52 ms" text and static per-tier Recovery guidance with per-metric trend rows (value + ▲/▼ vs. 7-day baseline) and a dominant-factor guidance sentence, in a borderless (no Material `Card`) layout.

**Architecture:** Pure, side-effect-free calculators (`MetricBaselineCalculator`, extended `RecoveryScoreCalculator`) feed a new `metricBaseline` field on `DashboardState`; a new `MetricRow` composable renders each metric; `RecoveryScoreCard` is restyled from `Card` to a divider-based `Column` and hosts the new rows.

**Tech Stack:** Kotlin, Jetpack Compose (Material3, BOM 2024.06.00), Hilt, JUnit4 (existing `app/src/test` unit-test setup).

## Global Constraints

- Design principle: inspiration, not replication — no competitor colors, icons, wording, logos, or exact layouts; reuse SleepPulse's own Calm Night palette/typography/copy voice throughout (spec: "Design principle: inspiration, not replication").
- Gauge color banding is already correct (3-band Red/Amber/Green in `SleepScoreGauge.kt`) — do not modify `scoreColor()`.
- Dashboard-only change; do not touch History, Alarm, or Settings screens.
- All new logic must be pure/offline/deterministic — no new dependencies.
- Follow existing test conventions in `app/src/test/java/com/sleeppulse/app/ui/dashboard/` (JUnit4, `org.junit.Assert.*`, one `NightlySummary` factory helper per test file).

---

## Existing code this plan builds on

- `app/src/main/java/com/sleeppulse/app/data/model/NightlySummary.kt` — domain model for one completed night (`avgHeartRateBpm: Int`, `avgHrvMillis: Double`).
- `app/src/main/java/com/sleeppulse/app/ui/dashboard/RecoveryScoreCalculator.kt` — `RecoveryScoreCalculator.score(lastNight: NightlySummary, baseline: List<NightlySummary>): RecoveryResult?`, `RecoveryResult(score: Int, tier: RecoveryTier, guidance: String)`, `RecoveryTier { OPTIMAL, ADEQUATE, LOW, POOR }`.
- `app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardContract.kt` — `DashboardState` data class.
- `app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardViewModel.kt:74-78` — `computeRecovery(nights: List<NightlySummary>)`, called from the `repository.recentNights().collect { ... }` block at line ~71.
- `app/src/main/java/com/sleeppulse/app/ui/components/RecoveryScoreCard.kt` — currently a Material `Card` wrapping score/tier/guidance text.
- `app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardScreen.kt:105-109` — the `state.latestReading?.let { reading -> Text(...) }` block being replaced.
- `app/src/main/java/com/sleeppulse/app/ui/theme/Theme.kt` — `RecoveryGreen` (`0xFF5FD98A`), `CautionAmber` (`0xFFF6C358`), `AlertCoral` (`0xFFFF7A7A`), `CalmNightTextSecondary` (`0xFF8B93C4`).
- `data class SensorReading` (in `app/src/main/java/com/sleeppulse/app/data/model/`) has `heartRateBpm: Int` and `hrvMillis: Double` — used by `DashboardState.latestReading`.

---

### Task 1: `MetricBaselineCalculator` — 7-day HR/HRV averages

**Files:**
- Create: `app/src/main/java/com/sleeppulse/app/ui/dashboard/MetricBaselineCalculator.kt`
- Test: `app/src/test/java/com/sleeppulse/app/ui/dashboard/MetricBaselineCalculatorTest.kt`

**Interfaces:**
- Consumes: `com.sleeppulse.app.data.model.NightlySummary` (existing).
- Produces: `data class MetricBaselineResult(val avgHeartRateBpm: Double, val avgHrvMillis: Double)` and `object MetricBaselineCalculator { fun compute(nights: List<NightlySummary>): MetricBaselineResult?; fun percentDelta(actual: Double, baseline: Double): Double }` — both consumed by Task 2 (`DashboardViewModel`) and Task 4 (`MetricRow` call sites).

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.sleeppulse.app.ui.dashboard

import com.sleeppulse.app.data.model.NightlySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

class MetricBaselineCalculatorTest {

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
    fun `fewer than 3 nights returns null`() {
        assertNull(MetricBaselineCalculator.compute(emptyList()))
        assertNull(MetricBaselineCalculator.compute(listOf(night(50.0, 60))))
        assertNull(MetricBaselineCalculator.compute(listOf(night(50.0, 60), night(50.0, 60))))
    }

    @Test
    fun `averages HR and HRV across up to 7 most recent nights`() {
        val nights = listOf(
            night(60.0, 50), night(50.0, 60), night(40.0, 70),
        )

        val result = MetricBaselineCalculator.compute(nights)

        assertNotNull(result)
        assertEquals(50.0, result!!.avgHrvMillis, 0.001)
        assertEquals(60.0, result.avgHeartRateBpm, 0.001)
    }

    @Test
    fun `only the first 7 nights (most recent) count toward the average`() {
        val recentSeven = List(7) { night(hrv = 60.0, hr = 50) }
        val olderNights = List(5) { night(hrv = 20.0, hr = 90) }

        val result = MetricBaselineCalculator.compute(recentSeven + olderNights)

        assertNotNull(result)
        assertEquals(60.0, result!!.avgHrvMillis, 0.001)
        assertEquals(50.0, result.avgHeartRateBpm, 0.001)
    }

    @Test
    fun `percentDelta computes signed percentage change from baseline`() {
        assertEquals(10.0, MetricBaselineCalculator.percentDelta(actual = 55.0, baseline = 50.0), 0.001)
        assertEquals(-10.0, MetricBaselineCalculator.percentDelta(actual = 45.0, baseline = 50.0), 0.001)
        assertEquals(0.0, MetricBaselineCalculator.percentDelta(actual = 50.0, baseline = 0.0), 0.001)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.ui.dashboard.MetricBaselineCalculatorTest"`
Expected: FAIL (compilation error — `MetricBaselineCalculator` / `MetricBaselineResult` unresolved)

- [ ] **Step 3: Write the implementation**

```kotlin
package com.sleeppulse.app.ui.dashboard

import com.sleeppulse.app.data.model.NightlySummary

private const val WINDOW_NIGHTS = 7
private const val MIN_NIGHTS_FOR_BASELINE = 3

/** 7-day rolling HR/HRV averages, used to show tonight's live reading vs. baseline. */
data class MetricBaselineResult(
    val avgHeartRateBpm: Double,
    val avgHrvMillis: Double,
)

/**
 * Computes rolling HR/HRV baselines from recorded nights, and a shared percent-delta
 * helper for comparing a live reading against those baselines.
 */
object MetricBaselineCalculator {

    fun compute(nights: List<NightlySummary>): MetricBaselineResult? {
        if (nights.size < MIN_NIGHTS_FOR_BASELINE) return null

        val window = nights.take(WINDOW_NIGHTS)
        return MetricBaselineResult(
            avgHeartRateBpm = window.map { it.avgHeartRateBpm }.average(),
            avgHrvMillis = window.map { it.avgHrvMillis }.average(),
        )
    }

    fun percentDelta(actual: Double, baseline: Double): Double {
        if (baseline == 0.0) return 0.0
        return (actual - baseline) / baseline * 100.0
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.ui.dashboard.MetricBaselineCalculatorTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/ui/dashboard/MetricBaselineCalculator.kt app/src/test/java/com/sleeppulse/app/ui/dashboard/MetricBaselineCalculatorTest.kt
git commit -m "feat: add MetricBaselineCalculator for 7-day HR/HRV averages"
```

---

### Task 2: Wire `metricBaseline` into `DashboardState`/`DashboardViewModel`

**Files:**
- Modify: `app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardContract.kt`
- Modify: `app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardViewModel.kt:71-79`
- Test: `app/src/test/java/com/sleeppulse/app/ui/dashboard/DashboardViewModelTest.kt` (create if it doesn't already exist; append if it does)

**Interfaces:**
- Consumes: `MetricBaselineCalculator.compute(nights: List<NightlySummary>): MetricBaselineResult?` (Task 1).
- Produces: `DashboardState.metricBaseline: MetricBaselineResult?` — consumed by Task 4 (`DashboardScreen`).

- [ ] **Step 1: Check for an existing DashboardViewModel test file**

Run: `ls app/src/test/java/com/sleeppulse/app/ui/dashboard/DashboardViewModelTest.kt 2>&1`

If it exists, read it fully before continuing — reuse its existing fake `SleepRepository`/`SleepSummaryNotifier` test doubles and constructor pattern instead of duplicating them. If it does not exist, this task only needs the state/viewmodel change below plus a minimal focused test — write:

```kotlin
package com.sleeppulse.app.ui.dashboard

import com.sleeppulse.app.data.model.NightlySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DashboardStateMetricBaselineTest {

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
    fun `DashboardState defaults metricBaseline to null`() {
        assertNull(DashboardState().metricBaseline)
    }

    @Test
    fun `DashboardState can hold a computed MetricBaselineResult`() {
        val baseline = MetricBaselineCalculator.compute(
            listOf(night(50.0, 60), night(50.0, 60), night(50.0, 60))
        )

        val state = DashboardState(metricBaseline = baseline)

        assertEquals(50.0, state.metricBaseline!!.avgHrvMillis, 0.001)
    }
}
```

(This file only exercises the new field's plumbing at the state level; the reactive wiring inside `DashboardViewModel.start()` is exercised indirectly by manual verification in Task 5, matching how `recoveryResult`/`recordedNightsCount` are handled today — there is no existing `DashboardViewModelTest` with a fake repository to extend, so we don't invent one here.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.ui.dashboard.DashboardStateMetricBaselineTest"`
Expected: FAIL (compilation error — `DashboardState` has no `metricBaseline` parameter)

- [ ] **Step 3: Add the field to `DashboardState`**

In `app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardContract.kt`, add the new field to the existing `DashboardState` data class (keep every existing field unchanged):

```kotlin
data class DashboardState(
    val isLoading: Boolean = true,
    val connectionState: SensorConnectionState = SensorConnectionState.Disconnected,
    val sleepScore: Int = 0,
    val latestReading: SensorReading? = null,
    val recentReadings: List<SensorReading> = emptyList(),
    val windDownStep: WindDownStep? = null,
    val recoveryResult: RecoveryResult? = null,
    val recordedNightsCount: Int = 0,
    val metricBaseline: MetricBaselineResult? = null,
) {
    val isConnected: Boolean
        get() = connectionState is SensorConnectionState.Connected
}
```

- [ ] **Step 4: Populate it in `DashboardViewModel`**

In `app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardViewModel.kt`, modify the `repository.recentNights().collect { ... }` block (currently at lines 71-78) to also compute and set `metricBaseline`:

```kotlin
        viewModelScope.launch {
            repository.recentNights().collect { nights ->
                _state.update {
                    it.copy(
                        recoveryResult = computeRecovery(nights),
                        recordedNightsCount = nights.size.coerceAtMost(MAX_RECORDED_NIGHTS_DISPLAY),
                        metricBaseline = MetricBaselineCalculator.compute(nights),
                    )
                }
            }
        }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.ui.dashboard.DashboardStateMetricBaselineTest"`
Expected: PASS (2 tests)

- [ ] **Step 6: Run the full unit test suite to confirm no regressions**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (including pre-existing `RecoveryScoreCalculatorTest`, `HrvTrendCalculatorTest`, etc.)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardContract.kt app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardViewModel.kt app/src/test/java/com/sleeppulse/app/ui/dashboard/DashboardStateMetricBaselineTest.kt
git commit -m "feat: populate DashboardState.metricBaseline from recorded nights"
```

---

### Task 3: Dominant-factor guidance sentence in `RecoveryScoreCalculator`

**Files:**
- Modify: `app/src/main/java/com/sleeppulse/app/ui/dashboard/RecoveryScoreCalculator.kt`
- Modify: `app/src/test/java/com/sleeppulse/app/ui/dashboard/RecoveryScoreCalculatorTest.kt`

**Interfaces:**
- Consumes: nothing new — reuses the `hrvDeviation`/`rhrDeviation` values already computed inside `RecoveryScoreCalculator.score()`.
- Produces: `RecoveryResult` gains two new fields (`hrvDeviation: Double`, `rhrDeviation: Double`); `guidance: String` is now generated by a dominant-factor sentence instead of a static per-tier lookup. Both are consumed by Task 4 (`RecoveryScoreCard`/`DashboardScreen` — `guidance` only; `hrvDeviation`/`rhrDeviation` are exposed for completeness/future use but no Task 4 step reads them directly, since Task 4's per-metric deltas come from `MetricBaselineResult`, not `RecoveryResult`).

- [ ] **Step 1: Write the failing tests**

Add these test cases to the existing `app/src/test/java/com/sleeppulse/app/ui/dashboard/RecoveryScoreCalculatorTest.kt` (append inside the existing `RecoveryScoreCalculatorTest` class, after the last `@Test` method):

```kotlin
    @Test
    fun `guidance names HRV as the dominant factor when its deviation is larger`() {
        val baseline = listOf(night(50.0, 60), night(50.0, 60), night(50.0, 60))
        // HRV +25% (dominant), RHR +5% (smaller magnitude)
        val lastNight = night(hrv = 62.5, hr = 63)

        val result = RecoveryScoreCalculator.score(lastNight, baseline)!!

        assertTrue(
            "expected guidance to mention HRV, was: ${result.guidance}",
            result.guidance.contains("HRV", ignoreCase = true),
        )
    }

    @Test
    fun `guidance names resting heart rate as the dominant factor when its deviation is larger`() {
        val baseline = listOf(night(50.0, 60), night(50.0, 60), night(50.0, 60))
        // RHR +20% (dominant), HRV +2% (smaller magnitude)
        val lastNight = night(hrv = 51.0, hr = 72)

        val result = RecoveryScoreCalculator.score(lastNight, baseline)!!

        assertTrue(
            "expected guidance to mention resting heart rate, was: ${result.guidance}",
            result.guidance.contains("resting heart rate", ignoreCase = true),
        )
    }

    @Test
    fun `guidance falls back to the static tier message when both deviations are within the neutral threshold`() {
        val baseline = listOf(night(50.0, 60), night(50.0, 60), night(50.0, 60))
        // HRV +1%, RHR +1% — both within the +/-3% neutral threshold
        val lastNight = night(hrv = 50.5, hr = 60.6.toInt())

        val result = RecoveryScoreCalculator.score(lastNight, baseline)!!

        assertEquals("Recovered — normal training/activity load is fine.", result.guidance)
    }

    @Test
    fun `RecoveryResult exposes the raw hrvDeviation and rhrDeviation used to compute the score`() {
        val baseline = listOf(night(50.0, 60), night(50.0, 60), night(50.0, 60))
        val lastNight = night(hrv = 62.5, hr = 54) // +25% HRV, -10% RHR vs baseline

        val result = RecoveryScoreCalculator.score(lastNight, baseline)!!

        assertEquals(0.25, result.hrvDeviation, 0.001)
        assertEquals(0.10, result.rhrDeviation, 0.001)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.ui.dashboard.RecoveryScoreCalculatorTest"`
Expected: FAIL (compilation error — `RecoveryResult` has no `hrvDeviation`/`rhrDeviation` properties)

- [ ] **Step 3: Write the implementation**

Replace the full contents of `app/src/main/java/com/sleeppulse/app/ui/dashboard/RecoveryScoreCalculator.kt` with:

```kotlin
package com.sleeppulse.app.ui.dashboard

import com.sleeppulse.app.data.model.NightlySummary
import kotlin.math.abs

enum class RecoveryTier { OPTIMAL, ADEQUATE, LOW, POOR }

data class RecoveryResult(
    val score: Int,
    val tier: RecoveryTier,
    val guidance: String,
    val hrvDeviation: Double,
    val rhrDeviation: Double,
)

/**
 * Compares last night's HR/HRV against a rolling baseline to estimate how recovered the
 * user is. Returns null when fewer than [MIN_BASELINE_NIGHTS] baseline nights are available —
 * showing a score off too little history would be misleading.
 */
object RecoveryScoreCalculator {

    private const val MIN_BASELINE_NIGHTS = 3
    private const val NEUTRAL_DEVIATION_THRESHOLD = 0.03 // +/-3% counts as "nothing notably moved"

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
        return RecoveryResult(
            score = score,
            tier = tier,
            guidance = guidanceFor(tier, hrvDeviation, rhrDeviation),
            hrvDeviation = hrvDeviation,
            rhrDeviation = rhrDeviation,
        )
    }

    private fun tierFor(score: Int): RecoveryTier = when {
        score >= 80 -> RecoveryTier.OPTIMAL
        score >= 60 -> RecoveryTier.ADEQUATE
        score >= 40 -> RecoveryTier.LOW
        else -> RecoveryTier.POOR
    }

    /**
     * Names whichever of HRV/resting-HR deviated furthest from baseline (by absolute
     * magnitude) in the guidance sentence, so the user learns *why* their score moved —
     * not just the tier. Falls back to the static per-tier message when neither deviation
     * clears [NEUTRAL_DEVIATION_THRESHOLD], so we don't manufacture a "why" when nothing
     * actually moved.
     *
     * Note: rhrDeviation is defined as (baselineAvgHr - lastNight) / baselineAvgHr, so a
     * *positive* rhrDeviation means resting HR is LOWER than baseline (favorable) — the
     * sign is flipped relative to hrvDeviation's "positive is favorable" convention above
     * it, matching how these two are already combined in [score].
     */
    private fun guidanceFor(tier: RecoveryTier, hrvDeviation: Double, rhrDeviation: Double): String {
        val hrvMagnitude = abs(hrvDeviation)
        val rhrMagnitude = abs(rhrDeviation)

        if (hrvMagnitude < NEUTRAL_DEVIATION_THRESHOLD && rhrMagnitude < NEUTRAL_DEVIATION_THRESHOLD) {
            return staticGuidanceFor(tier)
        }

        val percentText = { deviation: Double -> "${(abs(deviation) * 100).toInt()}%" }

        return if (hrvMagnitude >= rhrMagnitude) {
            if (hrvDeviation >= 0) {
                "Your HRV is ${percentText(hrvDeviation)} above your weekly average, suggesting strong recovery."
            } else {
                "Your HRV is ${percentText(hrvDeviation)} below your weekly average — consider an easier day."
            }
        } else {
            // rhrDeviation >= 0 means resting HR is LOWER than baseline (favorable).
            if (rhrDeviation >= 0) {
                "Your resting heart rate is ${percentText(rhrDeviation)} below your weekly average, suggesting strong recovery."
            } else {
                "Your resting heart rate is ${percentText(rhrDeviation)} above your weekly average — consider an easier day."
            }
        }
    }

    private fun staticGuidanceFor(tier: RecoveryTier): String = when (tier) {
        RecoveryTier.OPTIMAL -> "Fully recovered — good day to push yourself."
        RecoveryTier.ADEQUATE -> "Recovered — normal training/activity load is fine."
        RecoveryTier.LOW -> "Under-recovered — consider an easier day."
        RecoveryTier.POOR -> "Poorly recovered — prioritize rest today."
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.ui.dashboard.RecoveryScoreCalculatorTest"`
Expected: PASS (all original tests + 4 new ones)

- [ ] **Step 5: Run the full unit test suite to confirm no regressions**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — in particular, `SleepSummaryNotifierImpl`/`SleepSummaryNotifier` call sites that construct or read `RecoveryResult` (search with `grep -rn "RecoveryResult(" app/src/main/java/`) must still compile; if any call site constructs `RecoveryResult(...)` positionally elsewhere, update it to include `hrvDeviation`/`rhrDeviation` — but as of this plan, `RecoveryScoreCalculator.score()` is the only place `RecoveryResult` is constructed.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/ui/dashboard/RecoveryScoreCalculator.kt app/src/test/java/com/sleeppulse/app/ui/dashboard/RecoveryScoreCalculatorTest.kt
git commit -m "feat: name the dominant deviating factor in Recovery guidance text"
```

---

### Task 4: `MetricRow` composable

**Files:**
- Create: `app/src/main/java/com/sleeppulse/app/ui/components/MetricRow.kt`

**Interfaces:**
- Consumes: `MetricBaselineCalculator.percentDelta` (Task 1) is called by the composable's *caller* (Task 5), not by `MetricRow` itself — `MetricRow` takes an already-computed `deltaPercent: Double?`.
- Produces: `@Composable fun MetricRow(label: String, value: String, deltaPercent: Double?, favorableWhenPositive: Boolean, modifier: Modifier = Modifier)` — consumed by Task 5 (`RecoveryScoreCard`).

This is a pure UI component with no calculator logic to unit-test in JUnit (Compose UI isn't covered by the existing `app/src/test` setup — consistent with the rest of `ui/components/`, none of which have tests today). Verification happens visually in Task 6.

- [ ] **Step 1: Write the composable**

```kotlin
package com.sleeppulse.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sleeppulse.app.ui.theme.AlertCoral
import com.sleeppulse.app.ui.theme.CalmNightTextSecondary
import com.sleeppulse.app.ui.theme.RecoveryGreen
import kotlin.math.abs

/**
 * One metric line: label, current value, and — once a baseline exists — a small
 * colored trend arrow with the percent delta versus that baseline. Deliberately has no
 * card/background/border, sitting directly on the screen so several rows read as one
 * continuous list (divided by [CalmNightDivider], not boxed).
 */
@Composable
fun MetricRow(
    label: String,
    value: String,
    deltaPercent: Double?,
    favorableWhenPositive: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = CalmNightTextSecondary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (deltaPercent != null && abs(deltaPercent) >= 1.0) {
                val isFavorable = (deltaPercent >= 0) == favorableWhenPositive
                val arrow = if (deltaPercent >= 0) "▲" else "▼"
                Text(
                    text = " $arrow ${abs(deltaPercent).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFavorable) RecoveryGreen else AlertCoral,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Compile check**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL (no callers yet, but the file itself must compile standalone)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/ui/components/MetricRow.kt
git commit -m "feat: add MetricRow composable for per-metric trend display"
```

---

### Task 5: Restyle `RecoveryScoreCard` (borderless) and embed `MetricRow`s

**Files:**
- Modify: `app/src/main/java/com/sleeppulse/app/ui/components/RecoveryScoreCard.kt`
- Modify: `app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardScreen.kt:105-109` (the `latestReading` text block) and the `RecoveryScoreCard(...)` call site (currently lines 98-102)

**Interfaces:**
- Consumes: `MetricRow` (Task 4), `MetricBaselineCalculator.percentDelta` (Task 1), `DashboardState.metricBaseline`/`latestReading` (Task 2, pre-existing).
- Produces: nothing further downstream — this is the final integration task.

- [ ] **Step 1: Replace `RecoveryScoreCard.kt`**

```kotlin
package com.sleeppulse.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.ui.dashboard.MetricBaselineCalculator
import com.sleeppulse.app.ui.dashboard.MetricBaselineResult
import com.sleeppulse.app.ui.dashboard.RecoveryResult
import com.sleeppulse.app.ui.theme.CalmNightTextSecondary

private const val MIN_NIGHTS_FOR_RECOVERY = 4

/**
 * Shows today's Recovery Score (last night vs. a 7-night rolling baseline) plus live
 * HR/HRV trend rows vs. a 7-day baseline, or a "building your baseline" message when
 * there isn't yet enough recorded history. Borderless — a single top divider instead of
 * a Material Card — so it reads as part of the screen rather than a boxed widget.
 */
@Composable
fun RecoveryScoreCard(
    recoveryResult: RecoveryResult?,
    recordedNightsCount: Int,
    latestReading: SensorReading?,
    metricBaseline: MetricBaselineResult?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = CalmNightTextSecondary.copy(alpha = 0.2f))
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            if (recoveryResult != null) {
                Text(
                    text = "Recovery: ${recoveryResult.score}",
                    style = MaterialTheme.typography.titleLarge,
                    color = scoreColor(recoveryResult.score / 100f),
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

            if (latestReading != null && metricBaseline != null) {
                MetricRow(
                    label = "Heart rate",
                    value = "${latestReading.heartRateBpm} bpm",
                    deltaPercent = MetricBaselineCalculator.percentDelta(
                        actual = latestReading.heartRateBpm.toDouble(),
                        baseline = metricBaseline.avgHeartRateBpm,
                    ),
                    favorableWhenPositive = false,
                )
                MetricRow(
                    label = "HRV",
                    value = "${latestReading.hrvMillis.toInt()} ms",
                    deltaPercent = MetricBaselineCalculator.percentDelta(
                        actual = latestReading.hrvMillis,
                        baseline = metricBaseline.avgHrvMillis,
                    ),
                    favorableWhenPositive = true,
                )
            }
        }
    }
}
```

Note: `scoreColor` is declared `internal fun scoreColor(...)` in `app/src/main/java/com/sleeppulse/app/ui/components/SleepScoreGauge.kt`, which is the same `com.sleeppulse.app.ui.components` package `RecoveryScoreCard.kt` is already in — it's visible with no import needed, exactly as the original `RecoveryScoreCard.kt` already used it before this change.

- [ ] **Step 2: Update `DashboardScreen.kt`'s `RecoveryScoreCard` call site and remove the old reading text**

In `app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardScreen.kt`, find:

```kotlin
        RecoveryScoreCard(
            recoveryResult = state.recoveryResult,
            recordedNightsCount = state.recordedNightsCount,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = connectionLabel(state),
            style = MaterialTheme.typography.bodyMedium,
        )

        state.latestReading?.let { reading ->
            Text(text = "Heart rate ${reading.heartRateBpm} bpm  ·  HRV ${reading.hrvMillis.toInt()} ms")
        }
```

Replace with:

```kotlin
        RecoveryScoreCard(
            recoveryResult = state.recoveryResult,
            recordedNightsCount = state.recordedNightsCount,
            latestReading = state.latestReading,
            metricBaseline = state.metricBaseline,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = connectionLabel(state),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (state.metricBaseline == null) {
            state.latestReading?.let { reading ->
                Text(text = "Heart rate ${reading.heartRateBpm} bpm  ·  HRV ${reading.hrvMillis.toInt()} ms")
            }
        }
```

(The fallback plain-text line only shows while there's no baseline yet — e.g. brand-new install with fewer than 3 recorded nights — mirroring the existing "Building your baseline" graceful-degradation pattern; once a baseline exists, the same numbers are shown via the `MetricRow`s inside `RecoveryScoreCard` instead, so they aren't duplicated.)

- [ ] **Step 3: Compile check**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run the full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/ui/components/RecoveryScoreCard.kt app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardScreen.kt
git commit -m "feat: restyle RecoveryScoreCard borderless and embed per-metric trend rows"
```

---

### Task 6: On-device verification

**Files:** none (manual verification only)

- [ ] **Step 1: Build and install the debug APK**

Run: `./gradlew :app:installDebug -q`
Expected: `Installed on 1 device.`

- [ ] **Step 2: Launch the app and open Dashboard**

Run: `adb shell am start -n com.sleeppulse.app/.MainActivity`

- [ ] **Step 3: Capture and inspect a screenshot**

Run:
```bash
adb shell screencap -p /sdcard/dashboard.png && adb pull /sdcard/dashboard.png
```
Then view `dashboard.png`. Confirm:
- No Material `Card` box/elevation around the Recovery section — a thin top divider instead.
- If fewer than 3 nights are recorded (fresh install), the "Building your baseline (N/4 nights)" message still shows, and the plain-text HR/HRV fallback line still shows (since `metricBaseline` is null) — no `MetricRow`s yet.
- Gauge is still color-coded exactly as before (no visual regression) — this plan didn't touch `SleepScoreGauge.kt`.

- [ ] **Step 4: Report findings**

If the fresh-install (no-baseline) state looks correct but you want to see the `MetricRow`s with real data, note that reaching 3+ recorded nights requires completing 3+ sleep-tracking sessions (`ToggleSensorConnection` on then off) — this is expected app behavior, not a bug, and is out of scope to fake for this plan. Report what was visually confirmed; do not claim the `MetricRow` trend-arrow rendering was visually verified unless 3+ nights were actually recorded on the test device.

---

## Self-Review Notes

- **Spec coverage:** Goal 1 (per-metric rows) → Tasks 1, 2, 4, 5. Goal 2 (dynamic insight sentence) → Task 3. Goal 3 (borderless restyle) → Task 5. Gauge color banding explicitly called out as "no change" in Global Constraints. Inspiration-not-replication principle carried into Global Constraints so every task inherits it.
- **Type consistency:** `MetricBaselineResult` (Task 1) → `DashboardState.metricBaseline` (Task 2) → `RecoveryScoreCard(metricBaseline: MetricBaselineResult?)` (Task 5) — same type throughout. `MetricRow(deltaPercent: Double?, favorableWhenPositive: Boolean)` (Task 4) signature matches both call sites in Task 5.
- **No placeholders:** every step has complete, runnable code; the one caveat note in Task 5 Step 1 (about `scoreColor` visibility) tells the implementer exactly what to check and what to do in either case, rather than saying "handle appropriately."
