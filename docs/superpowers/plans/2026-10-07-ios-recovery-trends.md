# iOS Recovery and History Trends Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Kotlin-derived recovery/readiness and saved-night insights to a native Recovery tab and History charts.

**Architecture:** A pure common coordinator composes existing calculators over newest-first nights. The controller precomputes a history snapshot on Room emissions, then combines it with live tracking. One Swift store supplies all three screens; previews use explicit Kotlin-computed fixtures without opening a database.

**Tech Stack:** Kotlin 2.3.21, kotlinx-datetime 0.6.0, Room 2.8.4, SwiftUI/Swift Charts, iOS 17+.

**Spec:** `docs/superpowers/specs/2026-10-07-ios-recovery-history-trends-design.md`.

## Global Constraints

- Native SwiftUI, iOS 17+, iPhone and iPad, existing Calm Night theme.
- Add Home / History / Recovery tabs; tab changes preserve any running session.
- Compute analytics in Kotlin and deliver primitive immutable iOS snapshots.
- Preserve existing formulas, nullable HRV, Android behavior, tracking lifecycle and Room schema version 5.
- Runtime uses only persisted simulated nights. Fixtures are explicit previews/tests, with no database seeding or runtime demo switch.
- Debt target is 480 minutes per night; label the 8-hour target.
- Recovery uses latest + at least 3 preceding dates, at most 7 baseline nights. Trends use two 7-recorded-night windows. Consistency uses all retained nights.
- Charts show latest 14 recorded dates chronologically, segment at date gaps and preserve zero durations.
- No commit, merge or push without a user request. Keep generated artifacts/local settings out of Git.

## File map

- Create `shared/src/commonMain/kotlin/com/sleeppulse/shared/analytics/NightInsightsCalculator.kt`: coordinator and immutable `NightInsights`/`NightScoreChange` values.
- Create `shared/src/commonTest/kotlin/com/sleeppulse/shared/analytics/NightInsightsCalculatorTest.kt`: meaningful composition/windows/null tests.
- Create `shared/src/iosMain/kotlin/com/sleeppulse/shared/tracking/IosNightInsightsSnapshot.kt`: primitive recovery/readiness/debt/trend/change values.
- Create `shared/src/iosMain/kotlin/com/sleeppulse/shared/tracking/IosHistorySnapshotBuilder.kt`: one nights+insights conversion used by controller, previews and tests.
- Modify `shared/src/iosMain/kotlin/com/sleeppulse/shared/tracking/IosTrackingSnapshot.kt`, `IosTrackingController.kt`: new `insights` field and precomputed history flow.
- Create `shared/src/iosTest/kotlin/com/sleeppulse/shared/tracking/IosHistorySnapshotTest.kt`: snapshot mapping and real Room reopen/updates.
- Create `iosApp/SleepPulse/Shared/NightInsightsModels.swift`: native immutable models, display copy and snapshot mapping.
- Modify `iosApp/SleepPulse/Shared/TrackingModels.swift`, `TrackingStore.swift`: insights and one state mapper.
- Create `iosApp/SleepPulse/Recovery/RecoveryViewModel.swift`, `RecoveryView.swift`: observed store and reusable state-rendering content.
- Create `iosApp/SleepPulse/History/HistoryTrendsView.swift`: chart points with segments and accessible native charts.
- Modify `iosApp/SleepPulse/History/HistoryViewModel.swift`, `HistoryView.swift`: native insights cards, score comparisons and charts.
- Create `iosApp/SleepPulse/Demo/DemoNights.swift`: 14-night/partial/missing-HRV/empty fixtures built through actual Kotlin coordinator.
- Modify `iosApp/SleepPulse/Navigation/SleepPulseRootView.swift`, Xcode project: third tab/new files.
- Create `iosApp/SleepPulseTests/NightInsightsTests.swift`: native mapping, chart gaps, nulls, progress and actual Kotlin fixtures.
- Modify `iosApp/SleepPulseTests/TrackingStoreTests.swift`, `SleepPulseUITests/TrackingFlowTests.swift`: three-tab integration/save/reopen.
- Update README files and completion record.

## Task 1: Common insights composition

**Interface:** `NightInsightsCalculator.calculate(nights: List<NightlySummary>): NightInsights` takes unique newest-first retained dates. `NightInsights` carries `recordedNights`, `baselineNights`, `latestDate`, nullable `recovery`, `readiness`, `sleepDebt`, `consistencyScore`, `hrvTrend`, `heartRateTrend`, HRV counts in each trend window and `scoreChanges: List<NightScoreChange>`. Each change has epoch-day, optional prior score and optional `NightlySummary.Trend`.

- [x] Write failing tests before implementation:

```kotlin
val empty = NightInsightsCalculator.calculate(emptyList())
assertNull(empty.recovery)
assertNull(empty.sleepDebt)
val four = NightInsightsCalculator.calculate(listOf(latest) + List(3) { baseline })
assertEquals(3, four.baselineNights)
assertEquals(80, four.recovery?.score)
assertEquals(80, four.readiness?.score)
```

- [x] Run `./gradlew :shared:testAndroidHostTest --console=plain` and verify missing API failure.
- [x] Implement composition using the existing entry points:

```kotlin
val recovery = RecoveryScoreCalculator.scoreLatest(nights)
val debt = SleepDebtCalculator.calculate(nights)
val hrv = HrvTrendCalculator.analyze(nights)
val hr = RestingHeartRateTrendCalculator.analyze(nights)
val readiness = RecoveryReadinessCalculator.compute(recovery?.score, hrv, hr, debt)
```

- [x] Assert 0/1/2/3/4 boundaries, baseline poison nights beyond 7, two trend windows and partial/null HRV, debt latest 7 with zero preserved, consistency all nights, +/-2 score-change semantics and no oldest comparison. Run shared host tests.

## Task 2: Atomic iOS history snapshots

**Interfaces:** `IosHistorySnapshotBuilder.build(nights: List<NightlySummary>): IosHistorySnapshot`, whose fields are `nights: List<IosNightSnapshot>` and `insights: IosNightInsightsSnapshot`. Add optional `insights` to `IosTrackingSnapshot` with null default. Nested primitive values: recovery score/tier/guidance/hrvDeviation/heartRateDeviation, readiness score/tier, trend recentAverage/priorAverage/direction, debt deficitMinutes/nights/level/targetMinutes, score change epochDay/previousScore/direction. Include recorded/baseline counts and HRV-window counts.

- [x] Write failing iOS test for atomic Room emissions and reopen:

```kotlin
val first = IosHistorySnapshotBuilder.build(store.recentNights().first())
assertEquals(first.nights.size, first.insights.recordedNights)
assertEquals(first.nights.firstOrNull()?.isoDate, first.insights.latestIsoDate)
assertNull(first.insights.recovery)
```

- [x] Run `./gradlew -PenableIosTargets=true :shared:iosSimulatorArm64Test --console=plain`; verify missing API failure.
- [x] Implement builder, nullable primitive mapping and controller flow:

```kotlin
val history = repo.recentNights().distinctUntilChanged().map(IosHistorySnapshotBuilder::build)
combine(repo.sessionState, history) { state, saved ->
    IosTrackingSnapshot(/* existing live fields */, nights = saved.nights, insights = saved.insights)
}
```

- [x] Add real database save/reopen tests confirming same-date retention, actual latest date, counts and null HRV. Run simulator Kotlin tests and link Debug framework.

## Task 3: Swift mapping, fixtures and presentation state

**Interfaces:** `NightInsights`, `RecoveryInsight`, `ReadinessInsight`, `MetricTrend`, `SleepDebtInsight`, `NightScoreChange` are native immutable values. `TrackingState.init(snapshot: IosTrackingSnapshot, isForeground: Bool)` maps all fields once. `DemoNights.state(count: Int = 14, missingHrv: Bool = false) -> TrackingState` explicitly calls Kotlin builder on fixture summaries. `RecoveryViewModel(store:)` observes the one store; `RecoveryContent(state:)` and `HistoryContent(state:)` allow preview rendering without constructing a store. `HistoryChartPoint.make(nights:)` selects 14 latest, reverses to chronological and assigns an incrementing segment ID on epoch gaps.

- [x] Register XCTest and write failing meaningful assertions:

```swift
let full = DemoNights.state()
XCTAssertEqual(full.insights?.recordedNights, 14)
XCTAssertNotNil(full.insights?.recovery)
XCTAssertNotNil(full.insights?.hrvTrend)
let partial = DemoNights.state(count: 3)
XCTAssertNil(partial.insights?.readiness)
XCTAssertEqual(partial.insights?.remainingBaselineDates, 1)
```

- [x] Run Xcode unit tests only; verify missing mapping/fixture API failure.
- [x] Implement native model copy, boxed-number conversion, correct positive-RHR-deviation wording, progress, HR-only fallback, date-only formatting, oldest-no-comparison and chart summary text.
- [x] Implement deterministic Kotlin-calculated fixtures (fixed epoch-day and actual bedtime millis, differing HR/HRV/durations); never call them from the runtime store or root.
- [x] Assert empty/progress/full/missing-HRV/loading/stale labels, zero values, calendar gaps, 14-point limit and timezone-independent date strings. Run XCTest unit target.

## Task 4: Recovery, History and charts

**Interfaces:** consume Task 3 native state through existing application store. Charts use numeric epoch days, `PointMark`/segmented `LineMark`, duration `BarMark`, and `AxisMarks` labels derived from date-only formatter. Score y range 0–100; duration y represents actual hours with no minimum sleep assumption.

- [x] Extend UI test to tap Recovery while tracking, verify baseline-progress count and return Home still Stop-and-save. Expected to fail until tab exists.
- [x] Add Recovery tab/content with latest recorded date, shared guidance, recovery gauge/readiness card, baseline progress, HR-only disclosure, trends and seven-night comparison labels/HRV coverage. Respect theme, safe areas and Dynamic Type.
- [x] Add History content insight cards and score-change indicators; chart content above full newest-first list; provide accessible date/value text and single-night explanation.
- [x] Add `#Preview` variants for full/partial/empty/missing-HRV and large text. Register sources; run UI/Swift tests on iPhone 17 Pro.
- [x] Inspect populated content through a test-host presentation in XCTest (fixtures are in-memory and never persisted), export screenshots, exercise accessibility labels and large-text layout.

## Task 5: Verification, docs and review

- [x] Verify Room retained-history update/reopen changes insights coherently, runtime empty state remains unseeded, three tabs preserve tracking and background-save behavior.
- [x] Run full checks:

```bash
ANDROID_HOME=/Users/udaysah/Library/Android/sdk JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew -PenableIosTargets=true :shared:testAndroidHostTest :shared:iosSimulatorArm64Test :app:test lint :app:assembleDebug :wear:assembleDebug --console=plain
ANDROID_HOME=/Users/udaysah/Library/Android/sdk JAVA_HOME=$(/usr/libexec/java_home -v 17) xcodebuild -project iosApp/SleepPulse.xcodeproj -scheme SleepPulse -destination 'platform=iOS Simulator,id=09B95FEC-815B-4E54-81B1-D0A3FF8F0AE1' -derivedDataPath iosApp/build/DerivedData test CODE_SIGNING_ALLOWED=YES
git diff --check
git diff -- shared/schemas app wear
```

- [x] Update READMEs with data thresholds/windows, chart gaps and simulated short-night behavior; record verified test results and review fixes. Use requesting-code-review for a read-only review after implementation; repair concrete Important findings with focused tests.
- [x] Report feature branch/worktree and verification results. Preserve worktree and uncommitted work until Git integration is requested.

## Self-review

The plan covers the spec's recovery/readiness/window semantics, immutable snapshots, one observer, honest unavailable states, timeline gaps and accessible native charts. Existing calculator formulas/schema and Android adapters are preserved. The explicit fixture-only views allow populated verification without a runtime switch or seeded simulated history.
