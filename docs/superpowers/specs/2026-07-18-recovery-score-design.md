# Recovery Score feature

## Context

SleepPulse currently shows a per-night Sleep Score but nothing that tells users
whether they're actually *recovered* — i.e. how last night compares to their
recent baseline, the way Whoop/Oura's recovery metrics work. This is the
highest-value "helps people" feature identified when brainstorming Phase 2
additions: it turns raw HR/HRV data the app already collects into an
actionable "should I push myself today or take it easy" signal.

This feature has a hard dependency: nothing currently calls
`SleepRepository.recordNightlySummary()`, so `NightlySummaryDao` never
accumulates real history and History is permanently empty. This spec bundles
the smallest possible fix for that (a recording hook on sensor disconnect) —
it does not attempt the full "nightly summary flow" feature also listed in
Phase 2 (e.g. an explicit "end my night" UI flow, editing/correcting a
recorded night, etc.).

## Scope

In scope:
- `RecoveryScoreCalculator` — pure scoring function
- `NightSummaryBuilder` — pure function converting a reading session into a
  `NightlySummary`
- `DashboardViewModel` changes: accumulate a full-session reading list,
  record a nightly summary on sensor disconnect, collect `recentNights()` to
  compute today's recovery result
- `DashboardState`/`DashboardContract` additions for the recovery result
- A new `RecoveryScoreCard` composable on `DashboardScreen`
- Unit tests for both new calculators and the `DashboardViewModel` changes

Out of scope (deferred):
- A dedicated "end of night" UI flow (user explicitly marking a night as
  over, editing a recorded summary, multiple sessions per night, naps)
- Historical recovery score shown in `HistoryScreen` (this spec is
  Dashboard-only; a follow-up could add a recovery column to History)
- User-configurable baseline window (fixed at 7 nights per the design
  decision below)
- Persisting/caching the computed `RecoveryResult` — it's recomputed from
  `recentNights()` on every emission, which is cheap (≤8 rows) and avoids a
  second source of truth

## RecoveryScoreCalculator

Location: `app/src/main/java/com/sleeppulse/app/ui/dashboard/RecoveryScoreCalculator.kt`

```kotlin
enum class RecoveryTier { OPTIMAL, ADEQUATE, LOW, POOR }

data class RecoveryResult(
    val score: Int,
    val tier: RecoveryTier,
    val guidance: String,
)

object RecoveryScoreCalculator {
    // Returns null if fewer than 3 baseline nights are available (insufficient
    // data to compute a meaningful baseline).
    fun score(lastNight: NightlySummary, baseline: List<NightlySummary>): RecoveryResult?
}
```

Formula:

```
baselineAvgHrv = baseline.map { it.avgHrvMillis }.average()
baselineAvgHr  = baseline.map { it.avgHeartRateBpm }.average()

hrvDeviation = (lastNight.avgHrvMillis - baselineAvgHrv) / baselineAvgHrv
rhrDeviation = (baselineAvgHr - lastNight.avgHeartRateBpm) / baselineAvgHr

hrvComponent = (50 + hrvDeviation * 200).coerceIn(0.0, 100.0)
rhrComponent = (50 + rhrDeviation * 200).coerceIn(0.0, 100.0)

score = (hrvComponent * 0.6 + rhrComponent * 0.4).toInt().coerceIn(0, 100)
```

Tiers and guidance text:

| Score | Tier | Guidance |
|---|---|---|
| 80-100 | OPTIMAL | "Fully recovered — good day to push yourself." |
| 60-79 | ADEQUATE | "Recovered — normal training/activity load is fine." |
| 40-59 | LOW | "Under-recovered — consider an easier day." |
| 0-39 | POOR | "Poorly recovered — prioritize rest today." |

Baseline is always `recentNights().drop(1).take(7)` (up to 7 nights
immediately preceding last night, excluding last night itself). If fewer than
3 nights are available in that slice, `score()` returns `null`.

## NightSummaryBuilder

Location: `app/src/main/java/com/sleeppulse/app/data/NightSummaryBuilder.kt`

```kotlin
object NightSummaryBuilder {
    // Caller must ensure readings is non-empty; behavior is undefined otherwise
    // (the ViewModel is responsible for skipping the call when readings is empty).
    fun build(readings: List<SensorReading>, date: LocalDate): NightlySummary
}
```

- `avgHeartRateBpm` / `avgHrvMillis`: arithmetic mean over `readings`.
- `totalSleepMinutes`: sum of timestamp deltas between consecutive readings
  (in minutes), across the whole list.
- `deepSleepMinutes` / `remSleepMinutes`: sum of timestamp deltas between
  consecutive readings where the *earlier* reading's `sleepStage` is `DEEP`
  (respectively `REM`) — i.e. each delta is attributed to the stage the
  reading at its start represents.
- `sleepScore`: computed via the existing `SleepScoreCalculator.score(readings)`
  so a recorded night's stored score matches what the Dashboard displayed
  live during that session.

## DashboardViewModel changes

- Add a private (non-`State`) accumulator:
  `private val sessionReadings = mutableListOf<SensorReading>()`, appended to
  inside the existing `liveReadings().collect { }` block in `start()`,
  alongside the existing `recentReadings`/chart-cap logic (no change to that
  logic — this is a second, uncapped list).
- In `toggleConnection()`, when the branch taken is "currently connected →
  disconnect": before calling `repository.disconnectSensor()`, if
  `sessionReadings.isNotEmpty()`, call
  `repository.recordNightlySummary(NightSummaryBuilder.build(sessionReadings, LocalDate.now()))`,
  then clear `sessionReadings`. If `sessionReadings` is empty, skip recording
  entirely (no zero-reading row is ever stored).
- Add a new coroutine launch in `start()`:
  `repository.recentNights().collect { nights -> _state.update { it.copy(recoveryResult = computeRecovery(nights)) } }`,
  where `computeRecovery` is a small private helper:
  ```kotlin
  private fun computeRecovery(nights: List<NightlySummary>): RecoveryResult? {
      val lastNight = nights.firstOrNull() ?: return null
      val baseline = nights.drop(1).take(7)
      return RecoveryScoreCalculator.score(lastNight, baseline)
  }
  ```
- `DashboardContract.kt`: add `val recoveryResult: RecoveryResult? = null` to
  `DashboardState`. No new `Intent` needed — this is purely derived state.

## UI: RecoveryScoreCard

Location: `app/src/main/java/com/sleeppulse/app/ui/components/RecoveryScoreCard.kt`

A `Card` (Material3), placed on `DashboardScreen` directly below
`SleepScoreGauge` and above the "Heart rate"/"HRV" chart section. Three
states:

- `recoveryResult != null`: shows the score number, the tier label (e.g.
  "Optimal"), and the guidance line. Score color follows the same
  green/amber/coral mapping `SleepScoreGauge` already uses via `scoreColor()`
  (tier boundaries at 80/60/40 map onto that function's existing 0.75/0.4
  fractions closely enough to reuse it directly, passing `score / 100f`).
- `recoveryResult == null` and total recorded nights `< 4`: shows "Building
  your baseline (N/4 nights)" where N is the count of currently-recorded
  nights (0 to 3).
- No nights recorded at all (`recentNights()` empty): same "Building your
  baseline (0/4 nights)" message — no separate empty state needed, it's the
  same message with N=0.

No new navigation, no new screen — this is additive to the existing
Dashboard layout.

## Testing

- `RecoveryScoreCalculatorTest`: baseline sizes 0/1/2 return `null`; baseline
  size 3 (minimum) computes a score; a well-recovered case (HRV above
  baseline, RHR below baseline) lands in OPTIMAL; a poorly-recovered case
  lands in POOR; each tier boundary (79/80, 59/60, 39/40) produces the
  correct label; a case where HRV and RHR components disagree confirms the
  60/40 weighting (result skews toward whichever tier the HRV component
  alone would produce).
- `NightSummaryBuilderTest`: avg HR/HRV match a hand-computed average over a
  small fixed reading list; total/deep/rem minutes correctly sum timestamp
  deltas for a scripted sequence of stage transitions (e.g.
  AWAKE→LIGHT→DEEP→LIGHT→REM, with known per-segment durations); the
  `sleepScore` field matches `SleepScoreCalculator.score()` called on the
  same input.
- `DashboardViewModelTest` additions (extending the existing file and its
  `FakeSleepRepository`): disconnecting after readings accumulated calls
  `repository.recordedSummaries` with an entry built from those readings;
  disconnecting with zero readings does not add to `recordedSummaries`;
  `recentNights()` emissions with 0, 3, and 4+ nights produce
  `recoveryResult` of `null`, `null`, and a computed non-null result
  respectively.

## Out of scope / deferred

- Full "nightly summary flow" (explicit end-of-night UI, editing/correcting
  a recorded night, multi-session nights, naps) — this spec only adds the
  minimal disconnect-triggered recording needed to unblock Recovery Score.
- Recovery Score shown in History (Dashboard-only for this spec).
- User-configurable baseline window (fixed at 7 nights).
- Persisting the computed `RecoveryResult` (always recomputed from
  `recentNights()`).
