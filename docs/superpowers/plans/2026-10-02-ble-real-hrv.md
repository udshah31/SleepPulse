# Real HRV from BLE Straps Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** BLE mode reports real HRV (RMSSD from the strap's RR-intervals) when available and "unknown" (`null`) otherwise, instead of a fake `50.0`.

**Architecture:** Two pure, unit-tested helpers (`HeartRateMeasurementParser`, `RmssdCalculator`) feed `BleSensorDataSource`. `hrvMillis` / `avgHrvMillis` become nullable through the domain model, Room (v4 → v5 migration) and every consumer, which each get an explicit unknown-HRV rule.

**Tech Stack:** Kotlin, Room 2.6.1, Jetpack Compose, JUnit4 + hand-written fakes (`app/src/test/.../testutil`), `room-testing` `MigrationTestHelper` for the instrumented test.

**Spec:** `docs/superpowers/specs/2026-10-02-ble-real-hrv-design.md`

## Global Constraints

- All paths are under `/Volumes/Secondary/new_project/SleepPulse`; package root `com.sleeppulse.app` (`app/src/main/java/com/sleeppulse/app`).
- Run unit tests with `./gradlew :app:test`; run a single class with `./gradlew :app:test --tests 'com.sleeppulse.app.<pkg>.<Class>'`.
- Never write a placeholder HRV to Health Connect; valid HRV is `1.0..200.0` ms only.
- RR beats outside `300.0..2000.0` ms are dropped; RMSSD needs at least `10` valid successive differences; window is `60_000` ms.
- Room: bump `SleepPulseDatabase.VERSION` to `5`, add `Migration(4, 5)` to `MIGRATIONS`, commit `app/schemas/com.sleeppulse.app.data.local.SleepPulseDatabase/5.json`. `SleepPulseDatabaseMigrationsTest` must stay green.
- Git: stage files by exact path only (never `git add .` / `-A`); never stage `.claude/settings.local.json`, `CLAUDE.local.md`, `.env*`, or `.superpowers/brainstorm/`. End commit messages with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Do not push.
- Tests prefer hand-written fakes over mocking, but `SleepRepositoryImplTest` already mocks `HealthConnectManager`; keep its style.
- Out of scope: BLE sleep stage / movement, vendor HRV characteristics, any network service.

## Review Focus

- A strap that never sends RR-intervals (flags bit 4 clear): HRV stays `null`, the score is HR-only, no HRV is written to Health Connect (Task 3 test, Task 2 tests).
- A packet with energy-expended present (flags bit 3) before the RR field: RR must still be read from the right offset (Task 1 test).
- A truncated / empty characteristic value must not crash the GATT callback (Task 1 test).
- A single ectopic beat (e.g. 250 ms) in the RR stream must not poison RMSSD (Task 1 test).
- Old v4 rows with the `50.0` placeholder become unknown after migration; real v4 values survive (Task 4 test).
- A night where only some readings have HRV: the average uses only those (Task 2 test).

---

### Task 1: RR parsing and RMSSD (pure)

**Files:**
- Create: `app/src/main/java/com/sleeppulse/app/data/source/HeartRateMeasurementParser.kt`
- Create: `app/src/main/java/com/sleeppulse/app/data/source/RmssdCalculator.kt`
- Test: `app/src/test/java/com/sleeppulse/app/data/source/HeartRateMeasurementParserTest.kt`
- Test: `app/src/test/java/com/sleeppulse/app/data/source/RmssdCalculatorTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `data class HeartRateMeasurement(val bpm: Int, val rrIntervalsMillis: List<Double>)`
  - `object HeartRateMeasurementParser { fun parse(bytes: ByteArray?): HeartRateMeasurement? }`
  - `class RmssdCalculator(windowMillis: Long = 60_000L, minDiffs: Int = 10) { fun add(timestampMillis: Long, rrMillis: Double); fun rmssd(nowMillis: Long): Double?; fun reset() }`

- [ ] **Step 1: Write the failing parser test**

`HeartRateMeasurementParserTest.kt`:

```kotlin
package com.sleeppulse.app.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateMeasurementParserTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `8-bit bpm without rr`() {
        val m = HeartRateMeasurementParser.parse(bytes(0x00, 60))!!
        assertEquals(60, m.bpm)
        assertEquals(emptyList<Double>(), m.rrIntervalsMillis)
    }

    @Test
    fun `16-bit bpm`() {
        // 0x012C = 300
        assertEquals(300, HeartRateMeasurementParser.parse(bytes(0x01, 0x2C, 0x01))!!.bpm)
    }

    @Test
    fun `rr intervals are converted from 1024ths of a second to milliseconds`() {
        // flags 0x10 = rr present; 1024 -> 1000 ms, 512 -> 500 ms
        val m = HeartRateMeasurementParser.parse(bytes(0x10, 60, 0x00, 0x04, 0x00, 0x02))!!
        assertEquals(listOf(1000.0, 500.0), m.rrIntervalsMillis)
    }

    @Test
    fun `energy expended field is skipped before the rr field`() {
        // flags 0x18 = energy (bit 3) + rr (bit 4): 2 energy bytes, then rr 1024 -> 1000 ms
        val m = HeartRateMeasurementParser.parse(bytes(0x18, 60, 0xFF, 0xFF, 0x00, 0x04))!!
        assertEquals(60, m.bpm)
        assertEquals(listOf(1000.0), m.rrIntervalsMillis)
    }

    @Test
    fun `a trailing odd byte is ignored`() {
        val m = HeartRateMeasurementParser.parse(bytes(0x10, 60, 0x00, 0x04, 0x07))!!
        assertEquals(listOf(1000.0), m.rrIntervalsMillis)
    }

    @Test
    fun `null, empty and truncated packets return null instead of crashing`() {
        assertNull(HeartRateMeasurementParser.parse(null))
        assertNull(HeartRateMeasurementParser.parse(bytes()))
        assertNull(HeartRateMeasurementParser.parse(bytes(0x00)))        // 8-bit bpm missing
        assertNull(HeartRateMeasurementParser.parse(bytes(0x01, 0x2C)))  // 16-bit bpm half missing
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:test --tests 'com.sleeppulse.app.data.source.HeartRateMeasurementParserTest'`
Expected: FAIL (compilation error, `HeartRateMeasurementParser` unresolved).

- [ ] **Step 3: Implement the parser**

`HeartRateMeasurementParser.kt`:

```kotlin
package com.sleeppulse.app.data.source

/** One decoded Heart Rate Measurement (GATT 0x2A37) notification. */
data class HeartRateMeasurement(val bpm: Int, val rrIntervalsMillis: List<Double>)

/**
 * Pure decoder for the Heart Rate Measurement characteristic. Flags bit 0: bpm is UINT16; bit 3:
 * a 2-byte energy-expended field follows the bpm; bit 4: RR-intervals (UINT16, 1/1024 s) follow.
 */
object HeartRateMeasurementParser {

    fun parse(bytes: ByteArray?): HeartRateMeasurement? {
        if (bytes == null || bytes.isEmpty()) return null
        val flags = bytes[0].toInt() and 0xFF
        var i = 1

        val bpm = if (flags and 0x01 != 0) {
            if (bytes.size < i + 2) return null
            u16(bytes, i).also { i += 2 }
        } else {
            if (bytes.size < i + 1) return null
            (bytes[i].toInt() and 0xFF).also { i += 1 }
        }

        if (flags and 0x08 != 0) i += 2

        val rr = mutableListOf<Double>()
        if (flags and 0x10 != 0) {
            while (i + 1 < bytes.size) {
                rr += u16(bytes, i) * 1000.0 / 1024.0
                i += 2
            }
        }
        return HeartRateMeasurement(bpm, rr)
    }

    private fun u16(b: ByteArray, i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :app:test --tests 'com.sleeppulse.app.data.source.HeartRateMeasurementParserTest'`
Expected: PASS (6 tests).

- [ ] **Step 5: Write the failing RMSSD test**

`RmssdCalculatorTest.kt`:

```kotlin
package com.sleeppulse.app.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RmssdCalculatorTest {

    /** 11 beats alternating 1000/1010 ms, one per second: 10 successive differences of 10 ms. */
    private fun RmssdCalculator.feedAlternating(beats: Int = 11) {
        for (n in 0 until beats) add(timestampMillis = n * 1_000L, rrMillis = if (n % 2 == 0) 1000.0 else 1010.0)
    }

    @Test
    fun `known intervals give the known rmssd`() {
        val calc = RmssdCalculator().apply { feedAlternating() }
        assertEquals(10.0, calc.rmssd(nowMillis = 10_000L)!!, 0.0001)
    }

    @Test
    fun `fewer than ten successive differences is unknown`() {
        val calc = RmssdCalculator().apply { feedAlternating(beats = 10) } // 9 differences
        assertNull(calc.rmssd(nowMillis = 9_000L))
    }

    @Test
    fun `beats older than the window are evicted`() {
        val calc = RmssdCalculator().apply { feedAlternating() }
        assertNull(calc.rmssd(nowMillis = 80_000L))   // everything older than 20_000
        assertNull(calc.rmssd(nowMillis = 65_000L))   // only 6 beats (5 differences) left
    }

    @Test
    fun `an out-of-range beat is dropped and breaks the pair, not the whole window`() {
        val calc = RmssdCalculator()
        for (n in 0 until 12) {
            // beat 5 is a 250 ms ectopic beat: neither (4,5) nor (5,6) may count
            val rr = if (n == 5) 250.0 else if (n % 2 == 0) 1000.0 else 1010.0
            calc.add(n * 1_000L, rr)
        }
        // 11 pairs minus the 2 touching beat 5 = 9 differences -> still unknown
        assertNull(calc.rmssd(nowMillis = 11_000L))
        calc.add(12_000L, 1000.0)
        calc.add(13_000L, 1010.0)
        // 11 differences, all 10 ms
        assertEquals(10.0, calc.rmssd(nowMillis = 13_000L)!!, 0.0001)
    }

    @Test
    fun `reset forgets everything`() {
        val calc = RmssdCalculator().apply { feedAlternating() }
        calc.reset()
        assertNull(calc.rmssd(nowMillis = 10_000L))
    }
}
```

- [ ] **Step 6: Run it to verify it fails**

Run: `./gradlew :app:test --tests 'com.sleeppulse.app.data.source.RmssdCalculatorTest'`
Expected: FAIL (compilation error, `RmssdCalculator` unresolved).

- [ ] **Step 7: Implement the calculator**

`RmssdCalculator.kt`:

```kotlin
package com.sleeppulse.app.data.source

import kotlin.math.sqrt

/**
 * Rolling RMSSD over the last [windowMillis] of RR-intervals. A beat outside 300..2000 ms is kept
 * as a gap marker so the pairs on either side of it are skipped (successive differences across a
 * dropped beat are meaningless). Returns null until [minDiffs] valid differences exist.
 * Not thread-safe; owned by a single GATT callback.
 */
class RmssdCalculator(
    private val windowMillis: Long = 60_000L,
    private val minDiffs: Int = 10,
) {
    private class Beat(val atMillis: Long, val rrMillis: Double) // NaN = dropped

    private val beats = ArrayDeque<Beat>()

    fun add(timestampMillis: Long, rrMillis: Double) {
        beats.addLast(Beat(timestampMillis, if (rrMillis in MIN_RR_MILLIS..MAX_RR_MILLIS) rrMillis else Double.NaN))
    }

    fun rmssd(nowMillis: Long): Double? {
        while (beats.isNotEmpty() && beats.first().atMillis < nowMillis - windowMillis) beats.removeFirst()
        var sumSquares = 0.0
        var diffs = 0
        for (i in 1 until beats.size) {
            val a = beats[i - 1].rrMillis
            val b = beats[i].rrMillis
            if (a.isNaN() || b.isNaN()) continue
            sumSquares += (b - a) * (b - a)
            diffs++
        }
        return if (diffs < minDiffs) null else sqrt(sumSquares / diffs)
    }

    fun reset() = beats.clear()

    private companion object {
        const val MIN_RR_MILLIS = 300.0
        const val MAX_RR_MILLIS = 2000.0
    }
}
```

- [ ] **Step 8: Run both test classes**

Run: `./gradlew :app:test --tests 'com.sleeppulse.app.data.source.*'`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/data/source/HeartRateMeasurementParser.kt app/src/main/java/com/sleeppulse/app/data/source/RmssdCalculator.kt app/src/test/java/com/sleeppulse/app/data/source/HeartRateMeasurementParserTest.kt app/src/test/java/com/sleeppulse/app/data/source/RmssdCalculatorTest.kt
git commit -m "feat: add Heart Rate Measurement parser and rolling RMSSD calculator"
```

---

### Task 2: Nullable HRV through model, storage and consumers

This is one atomic type change: the moment `hrvMillis` becomes `Double?`, every consumer stops compiling, so the build is red from Step 3 until the last code step. Do all steps, then run the full suite once, then commit once. Do not commit mid-task.

**Files:**
- Modify: `data/model/SensorModels.kt`, `data/model/NightlySummary.kt`, `data/local/SessionReadingEntity.kt`, `data/local/NightlySummaryEntity.kt`, `data/local/SleepPulseDatabase.kt`
- Modify: `data/NightSummaryBuilder.kt`, `tracking/SleepStagePredictor.kt`, `tracking/HealthConnectManager.kt`
- Modify: `ui/dashboard/SleepScoreCalculator.kt`, `ui/dashboard/RecoveryScoreCalculator.kt`, `ui/dashboard/HrvTrendCalculator.kt`, `ui/dashboard/MetricBaselineCalculator.kt`, `ui/history/SleepVariabilityCalculator.kt`
- Modify: `ui/dashboard/DashboardScreen.kt`, `ui/components/RecoveryScoreCard.kt`, `ui/history/HistoryScreen.kt`, `data/export/DataExporter.kt`, `wear/WearDataClient.kt`
- Create (generated): `app/schemas/com.sleeppulse.app.data.local.SleepPulseDatabase/5.json`
- Test: update/add in `app/src/test/java/com/sleeppulse/app/...` as listed per step

(All main paths are under `app/src/main/java/com/sleeppulse/app/`.)

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces (later tasks rely on these exact types):
  - `SensorReading.hrvMillis: Double?`
  - `NightlySummary.avgHrvMillis: Double?`
  - `SleepStagePredictor.predict(heartRateBpm: Int, hrvMillis: Long?, movement: Float): SleepStage`
  - `MetricBaselineResult.avgHrvMillis: Double?`
  - `RecoveryResult.hrvDeviation: Double?`

- [ ] **Step 1: Write the failing tests for the new null behaviour**

Add to `SleepScoreCalculatorTest.kt` (change the helper's parameter to `Double?` and add):

```kotlin
    private fun reading(heartRateBpm: Int, hrvMillis: Double?) = SensorReading(
        timestampMillis = 0L, heartRateBpm = heartRateBpm, hrvMillis = hrvMillis, sleepStage = SleepStage.LIGHT,
    )

    @Test
    fun `without any hrv the score is the heart-rate component rescaled to 0 to 100`() {
        assertEquals(100, SleepScoreCalculator.score(listOf(reading(heartRateBpm = 50, hrvMillis = null))))
        assertEquals(0, SleepScoreCalculator.score(listOf(reading(heartRateBpm = 90, hrvMillis = null))))
        assertEquals(50, SleepScoreCalculator.score(listOf(reading(heartRateBpm = 70, hrvMillis = null))))
    }

    @Test
    fun `readings without hrv are left out of the hrv average`() {
        val withNulls = SleepScoreCalculator.score(listOf(reading(50, 100.0), reading(50, null)))
        val without = SleepScoreCalculator.score(listOf(reading(50, 100.0)))
        assertEquals(without, withNulls)
    }
```

Add to `NightSummaryBuilderTest.kt` (reuse the file's existing reading helper; adapt names to it):

```kotlin
    @Test
    fun `average hrv ignores unknown readings and is null when none are known`() {
        val date = java.time.LocalDate.of(2026, 1, 1)
        fun r(t: Long, hrv: Double?) = SensorReading(t, 60, hrv, SleepStage.LIGHT)
        assertEquals(40.0, NightSummaryBuilder.build(listOf(r(0, 40.0), r(1_000, null), r(2_000, 40.0)), date).avgHrvMillis!!, 0.0001)
        assertNull(NightSummaryBuilder.build(listOf(r(0, null), r(1_000, null)), date).avgHrvMillis)
    }
```

Add to `SleepStagePredictorTest.kt`:

```kotlin
    @Test
    fun `unknown hrv never predicts DEEP or REM`() {
        assertEquals(SleepStage.LIGHT, predictor.predict(heartRateBpm = 45, hrvMillis = null, movement = 0.1f))
        assertEquals(SleepStage.LIGHT, predictor.predict(heartRateBpm = 58, hrvMillis = null, movement = 0.1f))
        assertEquals(SleepStage.AWAKE, predictor.predict(heartRateBpm = 58, hrvMillis = null, movement = 0.9f))
    }
```

Add to `RecoveryScoreCalculatorTest.kt` (use the file's existing night helper; here `night(hr, hrv)` stands for it — adapt to the real helper name/signature, with `hrv: Double?`):

```kotlin
    @Test
    fun `without a known hrv the score comes from resting heart rate alone`() {
        val baseline = List(3) { night(avgHr = 60, avgHrv = null) }
        val result = RecoveryScoreCalculator.score(night(avgHr = 54, avgHrv = null), baseline)!!
        assertNull(result.hrvDeviation)
        // rhrDeviation = (60-54)/60 = 0.10 -> rhrComponent = 70, score = 70 (no 60/40 mix)
        assertEquals(70, result.score)
    }

    @Test
    fun `hrv is ignored when fewer than three baseline nights have it`() {
        val baseline = listOf(night(60, 50.0), night(60, null), night(60, null))
        val result = RecoveryScoreCalculator.score(night(54, 80.0), baseline)!!
        assertNull(result.hrvDeviation)
        assertEquals(70, result.score)
    }
```

Add to `HrvTrendCalculatorTest.kt`:

```kotlin
    @Test
    fun `a window with no hrv at all gives no trend`() {
        val nights = List(7) { night(avgHrv = 50.0) } + List(7) { night(avgHrv = null) }
        assertNull(HrvTrendCalculator.analyze(nights))
    }
```

Add to `MetricBaselineCalculatorTest.kt`:

```kotlin
    @Test
    fun `baseline hrv is null when no night has one`() {
        val result = MetricBaselineCalculator.compute(List(3) { night(avgHrv = null) })!!
        assertNull(result.avgHrvMillis)
    }
```

Add to `SleepVariabilityCalculatorTest.kt`:

```kotlin
    @Test
    fun `fewer than seven nights with hrv gives no variability`() {
        val nights = List(6) { night(avgHrv = 50.0) } + night(avgHrv = null)
        assertNull(SleepVariabilityCalculator.analyze(nights))
    }
```

Add to `HealthConnectManagerTest.kt`:

```kotlin
    @Test
    fun `readings with unknown hrv produce no hrv records`() {
        val readings = listOf(
            SensorReading(0, 60, null, SleepStage.LIGHT),
            SensorReading(1_000, 60, 45.0, SleepStage.LIGHT),
        )
        assertEquals(listOf(45.0), HealthConnectManager.buildHrvRecords(readings, zone).map { it.heartRateVariabilityMillis })
        assertEquals(emptyList<Any>(), HealthConnectManager.buildHrvRecords(listOf(SensorReading(0, 60, null, SleepStage.LIGHT)), zone))
    }
```

Add to `DataExporterTest.kt` (adapt to its existing setup): a night with `avgHrvMillis = null` exports an empty cell, e.g. the row contains `,60,,` between avg HR and total sleep — assert the exact CSV line against the file's existing assertion style.

(Where a test helper takes `Double`, widen its parameter to `Double?`.)

- [ ] **Step 2: Change the types (the build goes red here)**

`data/model/SensorModels.kt`: `val hrvMillis: Double?,` — add to the class KDoc: `/** [hrvMillis] is null when the source can't measure it; never a placeholder. */`

`data/model/NightlySummary.kt`: `val avgHrvMillis: Double?,`

`data/local/SessionReadingEntity.kt`: `val hrvMillis: Double?,`

`data/local/NightlySummaryEntity.kt`: `val avgHrvMillis: Double?,`

- [ ] **Step 3: Room v5 migration**

`data/local/SleepPulseDatabase.kt` — add imports `androidx.sqlite.db.SupportSQLiteDatabase`, set `const val VERSION = 5`, and replace `val MIGRATIONS: Array<Migration> = arrayOf()` with:

```kotlin
        /**
         * HRV became nullable. SQLite can't drop NOT NULL in place, so both tables are rebuilt. A BLE
         * strap's HRV used to be a fixed placeholder, always exactly 50.0, so that exact value is
         * stored as unknown; real measurements are non-integer-valued averages and keep their value.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE `nightly_summary_new` (`dateEpochDay` INTEGER NOT NULL, `bedtimeEpochMillis` INTEGER NOT NULL, " +
                        "`sleepScore` INTEGER NOT NULL, `avgHeartRateBpm` INTEGER NOT NULL, `avgHrvMillis` REAL, " +
                        "`totalSleepMinutes` INTEGER NOT NULL, `deepSleepMinutes` INTEGER NOT NULL, " +
                        "`remSleepMinutes` INTEGER NOT NULL, `tags` TEXT NOT NULL, PRIMARY KEY(`dateEpochDay`))",
                )
                db.execSQL(
                    "INSERT INTO `nightly_summary_new` SELECT `dateEpochDay`, `bedtimeEpochMillis`, `sleepScore`, " +
                        "`avgHeartRateBpm`, CASE WHEN `avgHrvMillis` = 50.0 THEN NULL ELSE `avgHrvMillis` END, " +
                        "`totalSleepMinutes`, `deepSleepMinutes`, `remSleepMinutes`, `tags` FROM `nightly_summary`",
                )
                db.execSQL("DROP TABLE `nightly_summary`")
                db.execSQL("ALTER TABLE `nightly_summary_new` RENAME TO `nightly_summary`")

                db.execSQL(
                    "CREATE TABLE `session_reading_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` INTEGER NOT NULL, `timestampMillis` INTEGER NOT NULL, " +
                        "`heartRateBpm` INTEGER NOT NULL, `hrvMillis` REAL, `sleepStage` TEXT NOT NULL)",
                )
                db.execSQL(
                    "INSERT INTO `session_reading_new` SELECT `id`, `sessionId`, `timestampMillis`, `heartRateBpm`, " +
                        "CASE WHEN `hrvMillis` = 50.0 THEN NULL ELSE `hrvMillis` END, `sleepStage` FROM `session_reading`",
                )
                db.execSQL("DROP TABLE `session_reading`")
                db.execSQL("ALTER TABLE `session_reading_new` RENAME TO `session_reading`")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_4_5)
```

Keep the existing KDoc on the class; the `FIRST_EXPORTED_VERSION` stays `4`.

- [ ] **Step 4: Pure producers — builder, predictor, score**

`data/NightSummaryBuilder.kt`, replace the `avgHrvMillis` line:

```kotlin
        val avgHrvMillis = readings.mapNotNull { it.hrvMillis }.average().takeUnless { it.isNaN() }
```

`tracking/SleepStagePredictor.kt`:

```kotlin
    fun predict(heartRateBpm: Int, hrvMillis: Long?, movement: Float): SleepStage {
        return when {
            movement > 0.8f -> SleepStage.AWAKE
            hrvMillis != null && heartRateBpm < 50 && hrvMillis > 60 -> SleepStage.DEEP
            hrvMillis != null && heartRateBpm in 50..65 && hrvMillis < 40 -> SleepStage.REM
            else -> SleepStage.LIGHT
        }
    }
```

`ui/dashboard/SleepScoreCalculator.kt`, replace the body after `if (readings.isEmpty()) return 0`:

```kotlin
        val avgHr = readings.map { it.heartRateBpm }.average()
        val hrComponent = ((90.0 - avgHr) / 40.0 * 40).coerceIn(0.0, 40.0)

        val hrv = readings.mapNotNull { it.hrvMillis }
        // No HRV at all (e.g. a strap that sends no RR-intervals): score on heart rate alone,
        // rescaled so such a night can still reach the full range.
        if (hrv.isEmpty()) return (hrComponent / 40.0 * 100).toInt().coerceIn(0, 100)

        val hrvComponent = (hrv.average() / 100.0 * 60).coerceIn(0.0, 60.0)
        return (hrvComponent + hrComponent).toInt().coerceIn(0, 100)
```

- [ ] **Step 5: Recovery, trend, baseline, variability**

`ui/dashboard/RecoveryScoreCalculator.kt`:
- `RecoveryResult.hrvDeviation: Double?`
- in `score(...)`, replace the HRV lines and the score mix:

```kotlin
        val baselineHrv = baseline.mapNotNull { it.avgHrvMillis }
        val baselineAvgHr = baseline.map { it.avgHeartRateBpm }.average()

        // HRV needs both last night's value and enough baseline nights with one.
        val lastHrv = lastNight.avgHrvMillis
        val hrvDeviation = if (lastHrv != null && baselineHrv.size >= MIN_BASELINE_NIGHTS) {
            val baselineAvgHrv = baselineHrv.average()
            (lastHrv - baselineAvgHrv) / baselineAvgHrv
        } else {
            null
        }
        val rhrDeviation = (baselineAvgHr - lastNight.avgHeartRateBpm) / baselineAvgHr

        val rhrComponent = (50 + rhrDeviation * 200).coerceIn(0.0, 100.0)
        val score = if (hrvDeviation != null) {
            val hrvComponent = (50 + hrvDeviation * 200).coerceIn(0.0, 100.0)
            (hrvComponent * 0.6 + rhrComponent * 0.4).toInt()
        } else {
            rhrComponent.toInt()
        }.coerceIn(0, 100)
```
(delete the old `hrvComponent`/`rhrComponent`/`score` lines it replaces; `tier`, `RecoveryResult(...)` construction unchanged).
- `guidanceFor(tier, hrvDeviation: Double?, rhrDeviation)`: `val hrvMagnitude = hrvDeviation?.let { abs(it) } ?: 0.0`; the HRV branch condition becomes `if (hrvDeviation != null && hrvMagnitude >= rhrMagnitude)` and `percentText(hrvDeviation)` / `hrvDeviation >= 0` stay (smart-cast inside that branch).

`ui/dashboard/HrvTrendCalculator.kt` — replace the two `map { it.avgHrvMillis }.average()` lines:

```kotlin
        val recentHrv = recentWindow.mapNotNull { it.avgHrvMillis }
        val priorHrv = priorWindow.mapNotNull { it.avgHrvMillis }
        if (recentHrv.isEmpty() || priorHrv.isEmpty()) return null
        val recentAvg = recentHrv.average()
        val priorAvg = priorHrv.average()
```

`ui/dashboard/MetricBaselineCalculator.kt`: `val avgHrvMillis: Double?` in `MetricBaselineResult`; in `compute`: `avgHrvMillis = window.mapNotNull { it.avgHrvMillis }.average().takeUnless { it.isNaN() },`

`ui/history/SleepVariabilityCalculator.kt`, in `analyze`:

```kotlin
        val window = nights.take(WINDOW_NIGHTS)
        val hrv = window.mapNotNull { it.avgHrvMillis }
        if (hrv.size < WINDOW_NIGHTS) return null
        val hrvStdDev = stdDev(hrv)
```

- [ ] **Step 6: Health Connect HRV records**

`tracking/HealthConnectManager.kt`, replace the body of `buildHrvRecords` from `val valid = ...` through the `.map { ... }` with:

```kotlin
            val valid = readings.mapNotNull { r ->
                r.hrvMillis?.takeIf { it in 1.0..200.0 }?.let { r.timestampMillis to it }
            }
            val firstMillis = valid.firstOrNull()?.first ?: return emptyList()
            return valid
                .groupBy { (it.first - firstMillis) / HRV_WINDOW_MILLIS }
                .toSortedMap()
                .map { (_, window) ->
                    val time = Instant.ofEpochMilli(window.first().first)
                    HeartRateVariabilityRmssdRecord(
                        time = time,
                        zoneOffset = zone.rules.getOffset(time),
                        heartRateVariabilityMillis = window.map { it.second }.average(),
                        metadata = sensorMetadata("sleeppulse-hrv-${time.toEpochMilli()}"),
                    )
                }
```

- [ ] **Step 7: UI, export, watch**

`ui/dashboard/DashboardScreen.kt` (HRV card, ~line 187):

```kotlin
                DashboardMetricCard(
                    label = "HRV",
                    value = reading.hrvMillis?.let { "${it.toInt()}" } ?: "—",
                    unit = if (reading.hrvMillis != null) "ms" else "",
                    values = smoothed(state.recentReadings.mapNotNull { it.hrvMillis?.toFloat() }),
                    color = RecoveryGreen,
                    modifier = Modifier.weight(1f),
                )
```
(`DashboardMetricCard` already draws the chart only for `values.size >= 2`.)

`ui/components/RecoveryScoreCard.kt` — wrap the HRV `MetricRow`:

```kotlin
            val hrv = latestReading.hrvMillis
            val baselineHrv = metricBaseline.avgHrvMillis
            if (hrv != null && baselineHrv != null) {
                MetricRow(
                    label = "HRV",
                    value = "${hrv.toInt()} ms",
                    deltaPercent = MetricBaselineCalculator.percentDelta(actual = hrv, baseline = baselineHrv),
                    favorableWhenPositive = true,
                )
            }
```

`ui/history/HistoryScreen.kt` (~line 201): `text = "${formatDuration(night.summary.totalSleepMinutes)} · HRV ${night.summary.avgHrvMillis?.let { "${it.toInt()} ms" } ?: "—"}",`

`data/export/DataExporter.kt`: `${night.avgHrvMillis ?: ""}` in place of `${night.avgHrvMillis}`.

`wear/WearDataClient.kt`: `reading.hrvMillis?.let { dataMap.putDouble("hrv", it) }`

- [ ] **Step 8: Fix compile errors in existing tests**

Build the tests: `./gradlew :app:compileDebugUnitTestKotlin`. Fix each error by widening test helpers to `Double?`, and by adding `!!` where a test reads a now-nullable value into a numeric `assertEquals`, namely:
- `RecoveryScoreCalculatorTest.kt:140` → `result.hrvDeviation!!`
- `NightSummaryBuilderTest.kt:32` → `summary.avgHrvMillis!!`
- `SleepRepositoryImplTest.kt:59` and `:92` → `stored.avgHrvMillis!!`, `nights[0].avgHrvMillis!!`
- `MetricBaselineCalculatorTest.kt:38,50` → `result.avgHrvMillis!!`
- `DashboardViewModelTest.kt:270` already uses `?: 0.0`; leave it.

Do not weaken any assertion; only add `!!`/widen types.

- [ ] **Step 9: Run the whole unit suite and lint**

Run: `./gradlew :app:test lint`
Expected: PASS. The new tests from Step 1 must now pass; `SleepPulseDatabaseMigrationsTest` must pass (needs the generated `5.json`, which Room writes during this build).

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/data app/src/main/java/com/sleeppulse/app/tracking app/src/main/java/com/sleeppulse/app/ui app/src/main/java/com/sleeppulse/app/wear app/schemas/com.sleeppulse.app.data.local.SleepPulseDatabase/5.json app/src/test
git commit -m "feat: make HRV nullable end to end (Room v5, scoring, recovery, UI, export)"
```
(Staging directories here is safe: they contain only tracked source; confirm with `git status --short` first that nothing from the forbidden list is among them.)

---

### Task 3: BLE emits real HRV; Health Connect writes it

**Files:**
- Modify: `app/src/main/java/com/sleeppulse/app/data/source/BleSensorDataSource.kt`
- Modify: `app/src/main/java/com/sleeppulse/app/data/repository/SleepRepositoryImpl.kt:148-153`
- Modify: `app/src/test/java/com/sleeppulse/app/data/repository/SleepRepositoryImplTest.kt:415-441`

**Interfaces:**
- Consumes: `HeartRateMeasurementParser.parse`, `RmssdCalculator` (Task 1); `SensorReading.hrvMillis: Double?`, `SleepStagePredictor.predict(.., hrvMillis: Long?, ..)` (Task 2).
- Produces: nothing new.

- [ ] **Step 1: Rewrite the repository test (failing)**

Replace the test `hrv goes to Health Connect only when the source measures it, not from a BLE strap's placeholder` with:

```kotlin
    @Test
    fun `hrv readings go to Health Connect in every data-source mode`() = runTest {
        for (mode in com.sleeppulse.app.ui.settings.DataSourceMode.entries) {
            val sessionDao = FakeSleepSessionDao()
            val healthConnect: com.sleeppulse.app.tracking.HealthConnectManager = mock()
            val settings = SettingsRepository().apply { setDataSourceMode(mode) }
            val repository = SleepRepositoryImpl(FakeSensorDataSource(), FakeNightlySummaryDao(), sessionDao, backgroundScope, healthConnect, settings) { 1_000L }

            repository.connectSensor()
            sessionDao.insertReadings(
                listOf(0L, 1_000L).map {
                    com.sleeppulse.app.data.local.SessionReadingEntity(
                        sessionId = sessionDao.sessions.single().sessionId, timestampMillis = it,
                        heartRateBpm = 60, hrvMillis = 42.5, sleepStage = SleepStage.LIGHT,
                    )
                },
            )
            repository.disconnectSensor()

            // Filtering out unknown / out-of-range HRV is HealthConnectManager.buildHrvRecords' job.
            verify(healthConnect).writeHrv(argThat { size == 2 })
            verify(healthConnect).writeHeartRate(any())
        }
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:test --tests 'com.sleeppulse.app.data.repository.SleepRepositoryImplTest'`
Expected: FAIL for mode `BLE` (`writeHrv` never called).

- [ ] **Step 3: Remove the BLE gate**

`SleepRepositoryImpl.kt`, replace lines 148-153 with:

```kotlin
        // writeHrv drops unknown and out-of-range HRV, so a strap without RR-intervals writes nothing.
        if (readings.isNotEmpty()) healthConnectManager.writeHrv(readings)
```
Delete the now-unused `import com.sleeppulse.app.ui.settings.DataSourceMode` if nothing else uses it. Leave the `settings` constructor parameter in place (callers and tests pass it); note it in the commit body as a candidate for later removal.

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :app:test --tests 'com.sleeppulse.app.data.repository.SleepRepositoryImplTest'`
Expected: PASS.

- [ ] **Step 5: Wire the parser and RMSSD into the BLE source**

`BleSensorDataSource.kt`:
- add a field next to the other private state: `private val rmssd = RmssdCalculator()`
- in `onConnectionStateChange`, in the `STATE_CONNECTED` branch, add `rmssd.reset()` before `g.discoverServices()`
- replace the body of `onCharacteristicChanged` with:

```kotlin
            val packet = HeartRateMeasurementParser.parse(characteristic.value) ?: return
            val now = System.currentTimeMillis()
            packet.rrIntervalsMillis.forEach { rmssd.add(now, it) }
            val hrvMillis = rmssd.rmssd(now) // null until the strap has sent enough RR-intervals

            val predictedStage = predictor.predict(
                heartRateBpm = packet.bpm,
                hrvMillis = hrvMillis?.toLong(),
                movement = 0.5f, // ponytail: movement is still a placeholder; real BLE movement is a separate piece of work
            )

            _readings.tryEmit(
                SensorReading(
                    timestampMillis = now,
                    heartRateBpm = packet.bpm,
                    hrvMillis = hrvMillis,
                    sleepStage = predictedStage,
                )
            )
```
- delete the `parseHeartRate` function
- update the `readings()` KDoc: heart rate and RR-intervals come from the standard Heart Rate Service; HRV is the rolling RMSSD of those RR-intervals (null when the device sends none); sleep stage is predicted from heart rate and HRV.
- update the class KDoc / comment near line 34 so it no longer says HRV is a placeholder.

- [ ] **Step 6: Run the full suite and lint**

Run: `./gradlew :app:test lint :app:assembleDebug`
Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/data/source/BleSensorDataSource.kt app/src/main/java/com/sleeppulse/app/data/repository/SleepRepositoryImpl.kt app/src/test/java/com/sleeppulse/app/data/repository/SleepRepositoryImplTest.kt
git commit -m "feat: BLE source reports real RMSSD from RR-intervals; HRV write no longer gated on BLE mode"
```

---

### Task 4: Migration data-preservation test, docs, device check

**Files:**
- Modify: `app/src/androidTest/java/com/sleeppulse/app/data/local/SleepPulseDatabaseMigrationTest.kt`
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes: `SleepPulseDatabase.MIGRATION_4_5`, schemas `4.json` and `5.json` (Task 2).
- Produces: nothing.

- [ ] **Step 1: Add the data-preservation test**

In `SleepPulseDatabaseMigrationTest.kt` add (imports: `org.junit.Assert.assertEquals`, `org.junit.Assert.assertNull`, `org.junit.Assert.assertNotNull`):

```kotlin
    @Test
    fun migration4to5KeepsRealHrvAndNullsThePlaceholder() {
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL("INSERT INTO nightly_summary VALUES (1, 100, 80, 55, 62.25, 400, 80, 90, 'caffeine,late meal')")
            execSQL("INSERT INTO nightly_summary VALUES (2, 200, 70, 58, 50.0, 380, 70, 80, '[]')")
            execSQL("INSERT INTO sleep_session VALUES (1, 1000, 0)")
            execSQL("INSERT INTO session_reading VALUES (1, 1, 1000, 60, 41.5, 'LIGHT')")
            execSQL("INSERT INTO session_reading VALUES (2, 1, 2000, 60, 50.0, 'LIGHT')")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, SleepPulseDatabase.MIGRATION_4_5)

        db.query("SELECT dateEpochDay, sleepScore, tags, avgHrvMillis FROM nightly_summary ORDER BY dateEpochDay").use {
            assertEquals(2, it.count)
            it.moveToFirst()
            assertEquals(80, it.getInt(1))
            assertEquals("caffeine,late meal", it.getString(2))
            assertEquals(62.25, it.getDouble(3), 0.0001)
            it.moveToNext()
            assertEquals(70, it.getInt(1))
            assertEquals(true, it.isNull(3)) // placeholder 50.0 is now unknown
        }
        db.query("SELECT hrvMillis, sleepStage FROM session_reading ORDER BY id").use {
            assertEquals(2, it.count)
            it.moveToFirst()
            assertEquals(41.5, it.getDouble(0), 0.0001)
            assertEquals("LIGHT", it.getString(1))
            it.moveToNext()
            assertEquals(true, it.isNull(0))
        }
        db.close()
    }
```
 Tags are stored by `Converters` as comma-joined text (`''` for none), which is what the inserts use.

- [ ] **Step 2: Update CLAUDE.md**

Edit the Data-source line about BLE placeholders, the Repository/persistence line (DB version 4 → 5, the migration, 5.json), and the Health Connect HRV sentence so they describe: BLE HRV = rolling RMSSD from RR-intervals when the strap sends them, `null` otherwise; HRV nullable everywhere; HRV write is per-reading (null/out-of-range dropped), no BLE-mode gate; the instrumented migration test now also has a 4→5 data-preservation case.

- [ ] **Step 3: Run the instrumented test on the device**

With the phone connected (`adb devices` shows it):

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.sleeppulse.app.data.local.SleepPulseDatabaseMigrationTest`
Expected: PASS (3 tests). Note: this uninstalls the app from the device afterwards.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/com/sleeppulse/app/data/local/SleepPulseDatabaseMigrationTest.kt CLAUDE.md
git commit -m "test: 4->5 migration keeps real HRV and nulls the BLE placeholder; update CLAUDE.md"
```

- [ ] **Step 5: Manual on-device check (needs a strap; report, do not fake)**

`./gradlew :app:installDebug`, set the data source to BLE, scan and pick a strap. With a strap that sends RR-intervals: HRV card shows a number after about 10+ beats and Health Connect receives HRV records after the night. With a device that sends no RR (heart rate only): HRV card shows "—", the sleep score still appears, no HRV records are written. Record which device was used in the final report. If no strap is available, say so — the unit and migration tests are the evidence, and this step stays open.
