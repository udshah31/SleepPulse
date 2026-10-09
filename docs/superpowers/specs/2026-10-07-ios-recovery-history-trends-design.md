# iOS Recovery and History Trends

## Status

The user approved the Recovery + History-trends scope and Kotlin-snapshot approach on
2026-10-07. The written specification was subsequently approved for implementation.

## Goal

Add a native Recovery tab and useful History visualizations to the existing iOS app using
the shared Kotlin scoring and analytics calculators. All three tabs observe the same
persisted simulated-night data and application-scoped store.

## Scope and constraints

- Native SwiftUI, iOS 17+, iPhone and iPad, existing Calm Night theme.
- Add Home / History / Recovery tabs; tab changes preserve any running session.
- Compute analytics in Kotlin and deliver primitive immutable iOS snapshots. Swift formats
  and renders the results rather than implementing scoring formulas.
- Preserve existing calculator formulas, nullable HRV, Android behavior, tracking lifecycle,
  Room schema version 5, migrations and exported schemas.
- Use only the existing simulated-data database at runtime. Clearly label Recovery and
  History insights as simulated.
- Populate previews and tests using deterministic multi-night fixtures. Do not seed or
  overwrite a user's database, add a runtime demo mode, or accelerate tracking timestamps.
- Sleep-debt target remains the shared default, 480 minutes per night, displayed as an
  8-hour target. Settings and target editing are a separate milestone.
- Tag editing/correlations, CSV export, BLE, HealthKit, alarms, widgets and iOS CI are outside
  this milestone.
- Implement in an isolated worktree. Commit, push and merge require a user request.

## Approach and alternatives

### Selected: Kotlin-computed insights in the existing snapshots

A small pure common coordinator calls the existing calculators. The iOS controller maps
those results into an insights snapshot alongside the saved nights. The existing Swift
store owns the one observer, and all screens receive native immutable state.

This centralizes the calculation windows and null semantics, makes them testable in
commonTest, and avoids additional database collectors or sensor producers.

### Alternatives considered

1. Call each Kotlin calculator separately from Swift. This adds repeated domain-model
   conversions and multiple Swift/Kotlin calls to assemble a coherent result.
2. Create a separate analytics controller/observer. This adds unnecessary flow ownership,
   synchronization and teardown while using the same saved nights.

## Architecture and responsibilities

```text
Room recent nights -- map once per history emission --> Saved nights + NightInsights
                                                        |
TrackingSessionState -------------------------------- combine
                                                        |
                                        IosTrackingSnapshot
                                                        |
                                 Main-actor TrackingStore
                                     /         |        \
                                  Home      History   Recovery
```

### Common Kotlin

- `shared/src/commonMain/kotlin/com/sleeppulse/shared/analytics/NightInsightsCalculator.kt`:
  pure coordination over newest-first `NightlySummary` values; compose existing recovery,
  readiness, sleep debt, consistency and trend calculators. Expose immutable domain results
  and counts needed to describe insufficient data.
- `shared/src/commonTest/kotlin/com/sleeppulse/shared/analytics/NightInsightsCalculatorTest.kt`:
  test window boundaries, missing data, heart-rate-only recovery, readiness inputs and
  per-night score comparisons against known fixtures.

### iOS Kotlin

- `shared/src/iosMain/kotlin/com/sleeppulse/shared/tracking/IosNightInsightsSnapshot.kt`:
  primitive iOS values for recovery/readiness, guidance, comparison windows, debt,
  consistency, HRV/heart-rate trends and per-night score changes.
- Update `IosTrackingSnapshot.kt` and `IosTrackingController.kt`: include nights and their
  insights from the same Room emission. Compute saved-night analytics before combining
  with live tracking so a new live reading does not recalculate unchanged night analytics.
- Retain the controller's existing lifetime, main-dispatcher callbacks, cancellation handle,
  retry and shutdown behavior.

### Swift

- `Shared/NightInsightsModels.swift`: immutable native insight values and formatting for
  tiers, unavailable metrics, progress, comparison text and chart summaries.
- Update `Shared/TrackingModels.swift` and `TrackingStore.swift`: map the new snapshot once
  into published state; keep the existing single controller and observation.
- `Recovery/RecoveryViewModel.swift`: observe the application store and expose presentation
  state. No Room, Flow, scoring or sensor ownership in the screen.
- `Recovery/RecoveryView.swift`: Calm Night Recovery screen, latest recorded date, recovery
  and readiness cards, baseline progress, trend cards and insufficient-data states.
- Update `History/HistoryViewModel.swift` and `HistoryView.swift`: expose insights and show
  consistency/debt, per-night changes and charts above the existing saved-night list.
- `History/HistoryTrendsView.swift`: native Swift Charts score and duration visualization
  of the latest 14 saved dates, with accessible textual summaries.
- `Demo/DemoNights.swift`: deterministic 14-night, partial-data and empty preview fixtures,
  consumed only by explicit previews/tests. Fixture results must pass through the same
  Kotlin insights coordinator used by the running app.
- Update `Navigation/SleepPulseRootView.swift` and the Xcode project: register the third
  tab and new sources/tests without changing scene lifecycle ownership.

## Calculation contract

Inputs are the repository's newest-first list, one retained summary per local start date,
up to 30 dates. These are recorded nights, not necessarily consecutive calendar days.
Never pad gaps or treat multiple same-day sessions as additional nights.

### Recovery

- Use `RecoveryScoreCalculator.scoreLatest(nights)` as the canonical entry point.
- Compare the latest night with the next up to seven recorded nights, excluding the latest
  from its own baseline. At least three preceding nights are required: four dates in total.
- Preserve the existing heart-rate-only fallback when the latest HRV is absent or fewer than
  three baseline nights have HRV.
- Show the shared score, tier and guidance, plus the latest recorded date and actual
  baseline-night count. Do not label an old stored night as today's recovery.
- When HRV comparison is unavailable, show an unavailable value and explain that recovery
  uses heart rate only. Do not reinterpret missing HRV as zero or fabricate a deviation.
- The shared calculator's heart-rate deviation is positive when the latest heart rate is
  lower than baseline. Human-readable text must say below/above baseline correctly.
- With fewer than four dates, show “Building your baseline”, the recorded count and remaining
  dates needed. Explain that another session on the same date does not advance the baseline.

### Readiness

- Call `RecoveryReadinessCalculator.compute` with the recovery score, available HRV and
  heart-rate trend results, and the shared sleep-debt result.
- Preserve its existing trend adjustments, debt penalty, clamping and tier thresholds.
- No recovery score means no readiness score. Render this as unavailable, not zero.
- Label readiness for the latest recorded date and explain that it blends recovery,
  available trends and sleep debt. It is a simulated-data illustration.

### HRV and heart-rate trends

- Use `HrvTrendCalculator.analyze` and `RestingHeartRateTrendCalculator.analyze` unchanged.
- Require at least 14 recorded nights, comparing the latest seven with the preceding seven.
  Label these as recorded-night windows rather than claiming two consecutive calendar weeks.
- HRV trends require at least one known HRV in each seven-night window, following the existing
  shared formula. Display the known-HRV counts for both windows when results are shown.
- With fewer than 14 dates, show recorded-count progress. With 14 dates but an HRV window
  containing no known values, explain the missing-HRV requirement separately.
- Show rising/falling/stable using both text and icon, plus recent and prior averages.
  Rising heart rate is not given the same favorable treatment as rising HRV.

### History analytics

- Sleep debt uses `SleepDebtCalculator.calculate(nights)` over the latest up to seven
  recorded nights. Show deficit duration, actual nights in the window and “8-hour target”.
  Missing calendar days do not accrue invented debt.
- Bedtime consistency uses `SleepConsistencyCalculator.calculateScore(nights)` over all
  available retained dates, up to 30. It becomes available at two dates. Label it as bedtime
  consistency and show its recorded-night count.
- Per-night change uses `NightlySummary.trendAgainst(previous)` with the next older stored
  date, preserving the existing +/-2-point neutral range. The oldest retained row has no
  comparison and says so; it does not imply a zero-point previous night.
- Debt/consistency remain nullable on insufficient data. A computed score or deficit of
  zero is a real result and must be displayed, not replaced by unavailable.
- Short simulated test sessions retain their actual recorded minutes, even when they yield
  large illustrative sleep debt. Do not convert a short test into a complete night's sleep.

## Charts and date handling

- Show sleep-score and recorded-duration charts using at most the latest 14 saved dates,
  ordered oldest to newest for plotting. Keep the full saved-night list newest first.
- Use stable epoch-day identity and calendar spacing. Date-axis labels must be derived from
  date-only values without changing the displayed date in a different Foundation timezone.
- Preserve gaps between recorded dates; do not add points or interpolate across missing
  dates. Score lines are segmented at calendar gaps, with point marks for isolated dates.
- Score chart uses a 0–100 vertical range. Duration chart shows real recorded hours/minutes;
  a zero-minute summary remains zero and its textual value is `<1 min`.
- With one night, show its point/bar and explain that more dates reveal a pattern. With no
  history, use the existing simulated-history empty state instead of empty axes.
- Chart accessibility provides the chart name, date range, night count and values per date.
  Users must be able to understand the data without relying only on color or the graphic.
- Charts/cards fit the existing centered maximum-width layout, safe areas and Dynamic Type.

## State, errors and lifecycle

- Saved nights and insights arrive atomically in one snapshot so a save cannot briefly pair
  new rows with old analytics or vice versa.
- The same app-scoped controller/store powers Home, History and Recovery. Navigation must
  not create database collectors, sensor jobs or independent analytics sessions.
- Opening/recovering the database displays loading rather than insufficient-data scores.
- Existing read/save failures remain visible with the existing retry path on Home. If a
  failure retains previously observed history, label the displayed insights as last loaded
  data and do not imply that a failed save contributed to them.
- Stop, background save, foreground eligibility and awaitable controller shutdown retain
  their current semantics.

## Verification

### Kotlin common tests

- Empty, one, two and three-night inputs produce correct unavailable results and progress.
- Four nights enable recovery; nights beyond the next seven do not influence its baseline.
- Unknown HRV preserves heart-rate-only recovery and correct comparison text inputs.
- Fourteen-night trend windows use the intended samples and preserve nullable HRV.
- Readiness uses exactly the assembled calculator inputs; missing recovery yields null.
- Sleep debt uses the latest seven, the 480-minute target and observed dates only.
- Bedtime consistency uses all retained dates and is unavailable below two.
- Score changes honor the +/-2 neutral range and omit comparison for the oldest night.

### iOS Kotlin / Swift tests

- Snapshot mapping preserves values, counts, nullable metrics, date identities and tiers.
- Analytics update after Room history changes and after persisted database reopening.
- Home, History and Recovery observe the same store and remain coherent during tracking.
- Native view models render empty, building-baseline, complete and missing-HRV states.
- Fourteen-night fixture presentation uses the actual Kotlin calculations; tests/previews
  do not populate the runtime simulated database.
- Chart points are chronological, limited to 14, preserve missing dates and segment lines.
- Formatting is timezone-safe and VoiceOver describes unavailable values and trend direction.

### Simulator and regression checks

- Inspect Recovery and History with empty, insufficient and full-history preview/test
  fixtures, including large text and accessible chart descriptions.
- Verify all three tab changes preserve live tracking; stopping/backgrounding saves and
  updates history/insights coherently; relaunch restores persisted results.
- Run the existing Kotlin common/iOS simulator suites, Swift/UI suites, Android unit tests,
  lint and phone/Wear debug builds. Verify no Room schema diff and run `git diff --check`.

## Completion criteria

The running app presents Recovery and History insights derived solely from its saved
simulated nights. Empty and insufficient-data states are honest, populated results match
the shared calculators, and charts preserve real recorded dates and durations. All three
tabs share one lifecycle and update together after save/recovery, with platform regressions
and accessibility checks passing.
