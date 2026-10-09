# iOS HealthKit Sleep Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a read-only, manually refreshed, source-labelled 30-day HealthKit sleep section to iOS History without changing simulated Recovery or shared persistence.

**Architecture:** A native `HealthKitSleepStore` owns authorization, refresh state, and an atomic Codable cache. A protocol-backed `HealthKitClient` isolates `HKHealthStore`; pure native models and a deterministic normalizer convert HealthKit samples into source-preserving display episodes. History receives the app-scoped HealthKit store alongside the existing app-scoped `TrackingStore`; imported episodes remain outside `NightlySummary` and all shared calculators.

**Tech Stack:** SwiftUI, HealthKit, Swift concurrency, Codable, XCTest, XCUITest, iOS 17+, existing static `SleepPulseShared` framework.

**Spec:** `docs/superpowers/specs/2026-10-08-ios-healthkit-sleep-import-design.md`

## Global Constraints

- Execution preflight rulings in this plan's SDD ledger resolve API and normalization ambiguities; retain uncommitted work until Git integration is explicitly requested.

- Read only `HKCategoryTypeIdentifierSleepAnalysis`; request no heart rate, HRV, workouts, or write permissions.
- Refresh manually over the latest 30 calendar days; do not add background delivery, anchored queries, or automatic sync.
- Keep imported HealthKit episodes separate from simulated nights, Recovery, debt, trends, and simulated charts.
- Do not add HealthKit imports or iOS-only APIs to `shared/commonMain` or change Room schema version 5.
- Preserve source provenance and never cross-merge records from different HealthKit sources.
- Missing stages remain nullable and display as `Unavailable`; never fabricate zeroes or inferred stages.
- Permission is requested only after `Connect Apple Health`; app launch and simulated tracking never trigger a HealthKit prompt.
- Use an app-scoped native store and atomic Codable cache under the existing Application Support `SleepPulse` directory.
- Do not add TypeSafe AI; HealthKit parsing, grouping, scoring, and display decisions are deterministic.
- Use the existing Calm Night theme, centered max-width layout, UTC-safe date formatting, Dynamic Type, and accessibility patterns.
- Do not stage `.superpowers/brainstorm/`, local settings, environment files, credentials, generated frameworks, databases, or build outputs.

---

## File Map

- Create `iosApp/SleepPulse/HealthKit/HealthKitSleepModels.swift`: Codable, Equatable sample/episode/cache/state values with no HealthKit framework types.
- Create `iosApp/SleepPulse/HealthKit/HealthKitSleepNormalizer.swift`: pure source-preserving sample-to-episode normalization.
- Create `iosApp/SleepPulse/HealthKit/HealthKitClient.swift`: protocol, authorization enum, and typed client errors.
- Create `iosApp/SleepPulse/HealthKit/LiveHealthKitClient.swift`: the only production file that imports and calls HealthKit.
- Create `iosApp/SleepPulse/HealthKit/HealthKitCacheStore.swift`: atomic Codable file persistence.
- Create `iosApp/SleepPulse/HealthKit/HealthKitSleepStore.swift`: app-scoped authorization, refresh serialization, state, and cache orchestration.
- Create `iosApp/SleepPulse/History/AppleHealthSleepSection.swift`: store wrapper plus state-only SwiftUI presentation for all Apple Health states.
- Modify `iosApp/SleepPulse/SleepPulseApp.swift`: instantiate one `HealthKitSleepStore`.
- Modify `iosApp/SleepPulse/Navigation/SleepPulseRootView.swift`: pass the app-scoped HealthKit store to History.
- Modify `iosApp/SleepPulse/History/HistoryView.swift`: render Apple Health below simulated content and preserve existing fixture-only `HistoryContent` previews.
- Modify `iosApp/SleepPulse/History/HistoryViewModel.swift` only if a combined observable state is needed; keep it free of HealthKit API calls.
- Modify `iosApp/SleepPulse.xcodeproj/project.pbxproj`: register sources/tests, add HealthKit framework linkage if required, HealthKit entitlement, and usage description build setting.
- Create `iosApp/SleepPulse/SleepPulse.entitlements`: `com.apple.developer.healthkit` capability.
- Create `iosApp/SleepPulseTests/HealthKitSleepNormalizerTests.swift`: pure mapping/grouping/date/stage tests.
- Create `iosApp/SleepPulseTests/HealthKitSleepStoreTests.swift`: fake-client, cache, authorization, refresh, stale/error tests.
- Modify `iosApp/SleepPulseTests/NightInsightsTests.swift` or add `AppleHealthSleepSectionTests.swift`: state-only presentation screenshots and accessibility-facing summaries.
- Modify `iosApp/SleepPulseUITests/TrackingFlowTests.swift`: verify Apple Health card appears without a launch permission prompt while simulated tracking remains usable.
- Modify `iosApp/README.md` and `README.md`: document read-only manual 30-day HealthKit behavior and separation from simulated insights.

## Interfaces

The following signatures are the contracts between tasks:

```swift
enum HealthKitSleepStage: String, Codable, Equatable {
    case inBed, asleepUnspecified, awake, core, deep, rem, unknown
}

struct HealthKitSleepSample: Codable, Equatable, Identifiable {
    let id: String
    let sourceIdentifier: String
    let sourceName: String
    let stage: HealthKitSleepStage
    let start: Date
    let end: Date
}

struct HealthKitSleepEpisode: Codable, Equatable, Identifiable {
    let id: String
    let sourceIdentifier: String
    let sourceName: String
    let start: Date
    let end: Date
    let inBedMinutes: Int?
    let asleepMinutes: Int?
    let awakeMinutes: Int?
    let coreMinutes: Int?
    let deepMinutes: Int?
    let remMinutes: Int?
}

enum HealthKitSleepNormalizer {
    static func episodes(from samples: [HealthKitSleepSample]) -> [HealthKitSleepEpisode]
}

protocol HealthKitClient {
    func isHealthDataAvailable() -> Bool
    func authorizationState() async -> HealthKitAuthorizationState
    func requestReadAccess() async throws
    func readSleepSamples(from: Date, to: Date) async throws -> [HealthKitSleepSample]
}

struct HealthKitSleepCache: Codable, Equatable {
    let formatVersion: Int
    let episodes: [HealthKitSleepEpisode]
    let fetchedAt: Date
    let windowStart: Date
    let windowEnd: Date
}

protocol HealthKitCacheStorage {
    func load() throws -> HealthKitSleepCache?
    func replace(with cache: HealthKitSleepCache) throws
}

@MainActor
final class HealthKitSleepStore: ObservableObject {
    @Published private(set) var state: HealthKitSleepState
    init(client: HealthKitClient, cache: HealthKitCacheStorage, now: @escaping () -> Date = Date.init)
    func connect()
    func refresh()
}
```

The implementation may add small supporting values, but later tasks must use these names and
keep the HealthKit framework boundary inside `LiveHealthKitClient`.

---

## Task 1: Pure HealthKit models and episode normalization

**Files:**
- Create: `iosApp/SleepPulse/HealthKit/HealthKitSleepModels.swift`
- Create: `iosApp/SleepPulse/HealthKit/HealthKitSleepNormalizer.swift`
- Test: `iosApp/SleepPulseTests/HealthKitSleepNormalizerTests.swift`

**Produces:** Codable native samples, episodes, cache/state values, and deterministic
`HealthKitSleepNormalizer.episodes(from:)`.

- [ ] **Step 1: Write failing normalization tests.** Add tests with fixed UTC dates and literal expectations for:
  - an `inBed` sample plus overlapping core/deep/REM/awake samples using the in-bed outer boundaries;
  - stage-only boundaries when no `inBed` sample exists;
  - overlapping same-source samples combining into one episode;
  - a positive gap splitting same-source episodes;
  - two sources with overlapping times remaining two episodes;
  - unknown stages remaining absent from known duration totals;
  - zero/sub-minute durations clamped to zero minutes and retained as real values;
  - stable IDs, newest-first ordering, and UTC-independent display date inputs.

```swift
func testOverlappingSameSourceSamplesUseInBedBoundsAndKnownStages() {
    let samples = [
        sample("bed", source: "watch", stage: .inBed, start: "2026-10-07T22:00:00Z", end: "2026-10-08T06:00:00Z"),
        sample("core", source: "watch", stage: .core, start: "2026-10-07T22:30:00Z", end: "2026-10-08T01:00:00Z"),
        sample("deep", source: "watch", stage: .deep, start: "2026-10-08T01:00:00Z", end: "2026-10-08T02:00:00Z"),
        sample("rem", source: "watch", stage: .rem, start: "2026-10-08T02:00:00Z", end: "2026-10-08T03:00:00Z"),
        sample("awake", source: "watch", stage: .awake, start: "2026-10-08T03:00:00Z", end: "2026-10-08T03:15:00Z")
    ]

    let episodes = HealthKitSleepNormalizer.episodes(from: samples)

    XCTAssertEqual(episodes.count, 1)
    XCTAssertEqual(episodes[0].start, date("2026-10-07T22:00:00Z"))
    XCTAssertEqual(episodes[0].end, date("2026-10-08T06:00:00Z"))
    XCTAssertEqual(episodes[0].coreMinutes, 150)
    XCTAssertEqual(episodes[0].deepMinutes, 60)
    XCTAssertEqual(episodes[0].remMinutes, 60)
    XCTAssertEqual(episodes[0].awakeMinutes, 15)
    XCTAssertEqual(episodes[0].asleepMinutes, 270)
}
```

- [ ] **Step 2: Run the focused test to verify the expected failure.**

Run:

```bash
xcodebuild -quiet -project iosApp/SleepPulse.xcodeproj -scheme SleepPulse \
  -destination 'platform=iOS Simulator,id=09B95FEC-815B-4E54-81B1-D0A3FF8F0AE1' \
  -derivedDataPath iosApp/build/DerivedData \
  -only-testing:SleepPulseTests/HealthKitSleepNormalizerTests test \
  CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=-
```

Expected: compile/test failure because the model and normalizer types do not exist.

- [ ] **Step 3: Implement the value types.** Make samples, episodes, cache, and state values
  `Codable` and `Equatable`. Keep durations nullable except for a genuine known zero. Use
  `HealthKitSleepStage` for every supported and unknown category.

- [ ] **Step 4: Implement normalization.** Group by `sourceIdentifier`, sort by start time,
  form one episode while the next interval overlaps or exactly touches the current interval,
  start a new episode for any positive gap, prefer `inBed` boundaries, and compute asleep duration
  from the interval union of all explicit asleep categories before flooring to minutes. Keep
  per-stage totals independent; exclude `inBed`, `awake`, and unknown intervals from asleep, retain
  missing asleep as `nil` and explicit zero as `0`, and never infer missing stages. Derive a
  deterministic ID from source identifier plus episode start/end.

- [ ] **Step 5: Run the focused tests to verify they pass.**

Expected: all normalization tests pass with no HealthKit framework dependency.

- [ ] **Step 6: Commit the focused unit.**

```bash
git add iosApp/SleepPulse/HealthKit/HealthKitSleepModels.swift \
  iosApp/SleepPulse/HealthKit/HealthKitSleepNormalizer.swift \
  iosApp/SleepPulseTests/HealthKitSleepNormalizerTests.swift
git commit -m "feat: normalize native sleep episodes"
```

---

## Task 2: HealthKit client and target capability

**Files:**
- Create: `iosApp/SleepPulse/HealthKit/HealthKitClient.swift`
- Create: `iosApp/SleepPulse/HealthKit/LiveHealthKitClient.swift`
- Create: `iosApp/SleepPulse/SleepPulse.entitlements`
- Modify: `iosApp/SleepPulse.xcodeproj/project.pbxproj`
- Test: `iosApp/SleepPulseTests/HealthKitSleepStoreTests.swift` fake-client scaffolding

**Consumes:** `HealthKitSleepSample`, `HealthKitSleepStage`, and
`HealthKitSleepNormalizer.episodes(from:)` from Task 1.

**Produces:** A protocol-backed client that maps HealthKit category samples to native samples,
requests read-only sleep permission, and queries the rolling 30-day window.

- [ ] **Step 1: Add client contract and fake behavior tests.** Define
  `HealthKitAuthorizationState` and typed client errors. Add a fake client in the test file
  with configurable availability, authorization result, requested-access error, and sample/query
  result. Write tests proving the store/client boundary can represent unavailable, denied,
  empty, and query-failure outcomes without importing HealthKit in tests.

- [ ] **Step 2: Run the focused test and verify the missing-contract failure.**

Expected: compile failure until the client protocol and test fake contract exist.

- [ ] **Step 3: Implement `LiveHealthKitClient`.** Import HealthKit only in this file. Use
  `HKHealthStore.isHealthDataAvailable()`, request read authorization for
  `HKObjectType.categoryType(forIdentifier: .sleepAnalysis)`, and execute an `HKSampleQuery`
  over `start = now - 30 calendar days` through `end = now`. Use a non-strict date predicate so
  samples crossing the window boundary are included. Sort by start date ascending. Map:
  `inBed`, `asleepUnspecified`, `awake`, `asleepCore`, `asleepDeep`, and `asleepREM`; map any
  future/unknown raw value to `.unknown`. Use source revision bundle identifier and source name,
  and preserve each HealthKit sample UUID as the native sample ID.

- [ ] **Step 4: Add the HealthKit capability and usage description.** Register an entitlements
  file containing `com.apple.developer.healthkit = true`, add `CODE_SIGN_ENTITLEMENTS` to the
  app Debug/Release configurations, and add the generated Info.plist setting:

```text
NSHealthShareUsageDescription = "SleepPulse reads sleep dates, durations, and stages from Apple Health to show them in History."
```

Do not add write permissions or unrelated HealthKit usage descriptions. Register the new source
files and entitlements in the Xcode project using the project’s existing manual-ID style.

- [ ] **Step 5: Build the app and focused tests.**

```bash
xcodebuild -quiet -project iosApp/SleepPulse.xcodeproj -scheme SleepPulse \
  -destination 'platform=iOS Simulator,id=09B95FEC-815B-4E54-81B1-D0A3FF8F0AE1' \
  -derivedDataPath iosApp/build/DerivedData \
  -only-testing:SleepPulseTests/HealthKitSleepNormalizerTests test \
  CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=-
```

Expected: compile succeeds; tests still use the fake client and do not show a system prompt.

- [ ] **Step 6: Commit the client/config unit.**

```bash
git add iosApp/SleepPulse/HealthKit/HealthKitClient.swift \
  iosApp/SleepPulse/HealthKit/LiveHealthKitClient.swift \
  iosApp/SleepPulse/SleepPulse.entitlements \
  iosApp/SleepPulse.xcodeproj/project.pbxproj \
  iosApp/SleepPulseTests/HealthKitSleepStoreTests.swift
git commit -m "feat: add read-only HealthKit sleep client"
```

---

## Task 3: Atomic cache and app-scoped HealthKit store

**Files:**
- Create: `iosApp/SleepPulse/HealthKit/HealthKitCacheStore.swift`
- Create: `iosApp/SleepPulse/HealthKit/HealthKitSleepStore.swift`
- Modify: `iosApp/SleepPulse/SleepPulseApp.swift`
- Test: `iosApp/SleepPulseTests/HealthKitSleepStoreTests.swift`

**Consumes:** `HealthKitClient`, `HealthKitSleepCache`, and normalized episodes from Tasks 1–2.

**Produces:** `HealthKitSleepStore(client:cache:now:)`, `connect()`, `refresh()`, and observable
states that retain prior cache on refresh failure.

- [ ] **Step 1: Write failing store/cache tests.** Cover:
  - cache load on initialization;
  - successful connect/request/refresh;
  - a successful empty query producing an empty-success state;
  - unavailable HealthKit without a prompt;
  - authorization failure with no cache;
  - query failure retaining cached episodes and marking stale data;
  - query failure without cache exposing retryable failure;
  - replacement of the full cache window rather than append behavior;
  - concurrent refresh calls not interleaving client reads or cache writes;
  - corrupt cache being ignored without affecting simulated data.

```swift
func testRefreshFailureRetainsCachedEpisodesAndMarksLastLoadedData() async {
    let old = cache(episodes: [episode(source: "watch", date: "2026-10-07")])
    let client = FakeHealthKitClient(samples: [], readError: .queryFailed)
    let store = await HealthKitSleepStore(client: client, cache: FakeCacheStorage(value: old), now: fixedNow)

    await store.refresh()

    let state = await store.state
    XCTAssertEqual(state.phase, .stale)
    XCTAssertEqual(state.episodes, old.episodes)
    XCTAssertNotNil(state.error)
    XCTAssertEqual(state.fetchedAt, old.fetchedAt)
}
```

- [ ] **Step 2: Run the focused store tests and verify the expected failure.**

Expected: compile failure because cache/store types and transitions do not exist.

- [ ] **Step 3: Implement `HealthKitCacheStore`.** Resolve the default URL under
  `Application Support/SleepPulse/healthkit-sleep-cache.json`, create the directory if needed,
  decode one versioned `HealthKitSleepCache`, and use `Data.write(to:options:.atomic)` for full
  replacement. Inject a fake storage in tests.

- [ ] **Step 4: Implement `HealthKitSleepStore`.** Make it `@MainActor ObservableObject`; load
  cache before the first refresh, inject `now`, guard against unavailable HealthKit, serialize
  refresh with one `Task`/`isRefreshing` gate, request access only from `connect()`, query the
  30-day window, normalize samples, and atomically replace cache only after the entire query and
  encode succeeds. On failure, keep cached episodes and use `.stale`; on no cache use `.failed`.
  Expose `openSettingsURL` only for a denied/needs-settings state. Do not call the client from
  `TrackingStore` or any simulated-session lifecycle method.

- [ ] **Step 5: Wire one store at the app root.** Add:

```swift
@StateObject private var healthKitStore = HealthKitSleepStore.live()
```

and pass it through `SleepPulseRootView` to History. Keep `TrackingStore` as the only owner of
the simulated database/controller.

- [ ] **Step 6: Run all HealthKit store tests.** Expected: all fake-client, cache, stale-state,
  and serialization tests pass.

- [ ] **Step 7: Commit the store unit.**

```bash
git add iosApp/SleepPulse/HealthKit/HealthKitCacheStore.swift \
  iosApp/SleepPulse/HealthKit/HealthKitSleepStore.swift \
  iosApp/SleepPulse/SleepPulseApp.swift \
  iosApp/SleepPulseTests/HealthKitSleepStoreTests.swift
git commit -m "feat: cache HealthKit sleep imports"
```

---

## Task 4: Apple Health History presentation

**Files:**
- Create: `iosApp/SleepPulse/History/AppleHealthSleepSection.swift`
- Modify: `iosApp/SleepPulse/Navigation/SleepPulseRootView.swift`
- Modify: `iosApp/SleepPulse/History/HistoryView.swift`
- Modify: `iosApp/SleepPulse/History/HistoryViewModel.swift` only if needed for store observation
- Modify: `iosApp/SleepPulse.xcodeproj/project.pbxproj`
- Test: `iosApp/SleepPulseTests/AppleHealthSleepSectionTests.swift`

**Consumes:** `HealthKitSleepStore.state`, `HealthKitSleepEpisode`, `TrackingState`, and the
existing Calm Night components/date formatter.

**Produces:** A separate Apple Health section with state-only rendering and a store-backed wrapper.

- [ ] **Step 1: Write state/presentation tests before the view.** Assert that state-only content
  exposes the expected user-facing text for:
  - unavailable HealthKit;
  - connect action;
  - loading;
  - loaded source-labelled episode with known stage values;
  - empty successful query;
  - stale cache with retry text;
  - failure without cache;
  - missing stage values displayed as `Unavailable`;
  - episode accessibility summary contains source, date, asleep duration, and unavailable stages.

- [ ] **Step 2: Run the focused presentation tests and verify the expected failure.**

Expected: compile failure because `AppleHealthSleepSection` and state rendering do not exist.

- [ ] **Step 3: Implement state-only Apple Health content.** Create a view that takes
  `HealthKitSleepState` and closures for connect, refresh, and settings. Use existing Calm Night
  cards, sentence-case copy, explicit source labels, newest-first episode rows, UTC-safe date
  text, `<1 min` for sub-minute durations, and `Unavailable` for nil stage values. Keep each
  row as one accessibility element with a complete summary.

- [ ] **Step 4: Implement the store-backed wrapper.** Observe the app-scoped
  `HealthKitSleepStore`, call `connect()`/`refresh()`, and open `UIApplication.openSettingsURLString`
  only when the state exposes a settings action. The wrapper must not create another store or
  query HealthKit directly.

- [ ] **Step 5: Add the section below simulated content.** Change `HistoryView` to accept both
  `TrackingStore` and `HealthKitSleepStore`, render existing `HistoryContent` unchanged for
  simulated data, then render the Apple Health section. Keep fixture previews state-only and
  add Apple Health previews for unavailable, empty, loaded, and stale states without opening a
  real HealthKit store.

- [ ] **Step 6: Add project registration and run focused XCTest tests.** Register the new source
  and test files in the manually maintained project file. Expected: all state/presentation tests
  pass and no HealthKit prompt appears during unit tests.

- [ ] **Step 7: Commit the presentation unit.**

```bash
git add iosApp/SleepPulse/History/AppleHealthSleepSection.swift \
  iosApp/SleepPulse/Navigation/SleepPulseRootView.swift \
  iosApp/SleepPulse/History/HistoryView.swift \
  iosApp/SleepPulse/History/HistoryViewModel.swift \
  iosApp/SleepPulse.xcodeproj/project.pbxproj \
  iosApp/SleepPulseTests/AppleHealthSleepSectionTests.swift
git commit -m "feat: show Apple Health sleep in History"
```

---

## Task 5: Integration, permissions, accessibility, and documentation

**Files:**
- Modify: `iosApp/SleepPulseUITests/TrackingFlowTests.swift`
- Modify: `iosApp/SleepPulseTests/TrackingStoreTests.swift` if app-scoped wiring needs coverage
- Modify: `iosApp/README.md`
- Modify: `README.md`

- [ ] **Step 1: Add UI coverage for the runtime boundary.** Launch the real app and verify the
  Apple Health section and `Connect Apple Health` action appear without a launch-time permission
  prompt. Start simulated tracking, move between Home/History/Recovery, and confirm the HealthKit
  card does not alter the tracking control or simulated Recovery state.

- [ ] **Step 2: Add state-only screenshot/accessibility coverage.** Render loaded, empty, stale,
  unavailable, large-text, and iPad Apple Health states in the XCTest host. Verify source/date/
  duration/unavailable-stage labels and inspect screenshots for clipping.

- [ ] **Step 3: Run the focused Swift unit/UI suite.**

```bash
xcodebuild -quiet -project iosApp/SleepPulse.xcodeproj -scheme SleepPulse \
  -destination 'platform=iOS Simulator,id=09B95FEC-815B-4E54-81B1-D0A3FF8F0AE1' \
  -derivedDataPath iosApp/build/DerivedData \
  -parallel-testing-enabled NO test CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=-
```

Expected: all Swift and UI tests pass; no system permission prompt is triggered by launch.

- [ ] **Step 4: Document the feature.** Update both READMEs with read-only sleep-analysis
  permission, manual 30-day refresh, source-preserving episodes, stale-cache behavior, and the
  fact that Apple Health records do not affect simulated Recovery or charts.

- [ ] **Step 5: Run the full regression suite.**

```bash
ANDROID_HOME=/Users/udaysah/Library/Android/sdk \
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
./gradlew -PenableIosTargets=true \
  :shared:testAndroidHostTest :shared:iosSimulatorArm64Test :app:test lint \
  :app:assembleDebug :wear:assembleDebug --console=plain

ANDROID_HOME=/Users/udaysah/Library/Android/sdk \
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
xcodebuild -quiet -project iosApp/SleepPulse.xcodeproj -scheme SleepPulse \
  -destination 'platform=iOS Simulator,id=09B95FEC-815B-4E54-81B1-D0A3FF8F0AE1' \
  -derivedDataPath iosApp/build/DerivedData \
  -parallel-testing-enabled NO test CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=-

git diff --check
git diff -- shared/schemas app wear
```

Expected: all tests/builds pass, no schema or Android/Wear changes appear, and the diff is
whitespace-clean.

- [ ] **Step 6: Perform a real-device HealthKit check when available.** On an iOS device with
  Apple Health sleep data, verify permission prompt timing, source labels, overlapping-source
  separation, empty/failure behavior, and cache persistence across relaunch. Do not make this a
  prerequisite for simulator-only CI because the simulator may not contain real HealthKit data.

- [ ] **Step 7: Commit documentation and integration tests.**

```bash
git add iosApp/SleepPulseUITests/TrackingFlowTests.swift \
  iosApp/SleepPulseTests/TrackingStoreTests.swift \
  iosApp/README.md README.md
git commit -m "test: verify Apple Health History integration"
```

## Self-review checklist

- Spec coverage: Tasks 1–2 cover typed models, source-preserving normalization, HealthKit
  authorization, capability, usage text, and the 30-day query; Task 3 covers app-scoped state,
  atomic cache, stale failures, and refresh serialization; Task 4 covers all History states,
  accessibility, previews, and separation from simulated insights; Task 5 covers UI boundary,
  docs, real-device validation, and full regressions.
- No schema path is modified; the plan explicitly verifies `git diff -- shared/schemas app wear`.
- No shared common code imports HealthKit.
- Type consistency: `HealthKitClient` returns samples, the store normalizes them, and the UI
  consumes `HealthKitSleepState.episodes`; no later task expects raw HealthKit objects.
- No TypeSafe AI is introduced because the feature is deterministic structured-data handling.
