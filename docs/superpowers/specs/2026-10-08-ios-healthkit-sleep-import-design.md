# iOS HealthKit Sleep Import

## Status

The proposed read-only HealthKit import design was approved for specification work on
2026-10-08. The user subsequently approved this specification and subagent-driven execution.

## Goal

Add a native iOS HealthKit sleep-import experience to History. Users can explicitly connect
Apple Health, manually refresh the latest 30 days of sleep-analysis data, and view imported
sleep episodes with source labels and honest unavailable states.

HealthKit data remains separate from SleepPulse's simulated nights. Imported records do not
change Recovery, sleep debt, HRV/heart-rate trends, simulated charts, or the simulated nightly
baseline in this milestone.

## Scope and constraints

- Native SwiftUI and HealthKit on iOS 17+.
- Read-only access to `HKCategoryTypeIdentifierSleepAnalysis`.
- Manual refresh only, limited to the latest 30 days.
- No HealthKit writes.
- No background delivery, observer query, or automatic sync yet.
- No HealthKit APIs in `shared/commonMain`.
- No Room schema change for imported records.
- Keep imported data in an app-scoped native store with a Codable cache.
- Preserve the existing app-scoped `TrackingStore` and simulated tracking lifecycle.
- Keep HealthKit records in a separate History section below simulated insights and nights.
- Preserve source provenance; do not automatically merge records from different sources.
- Do not infer missing sleep stages or convert unavailable values to zero.
- No TypeSafe AI dependency. HealthKit normalization and display decisions are deterministic.
- Do not block simulated tracking or Recovery when HealthKit is unavailable or denied.
- HealthKit imported records are display-only in this milestone.

## Product behavior

History remains organized as two explicit datasets:

```text
History
├── SleepPulse simulated nights
│   ├── Recovery insights
│   ├── sleep debt and bedtime consistency
│   ├── simulated score/duration charts
│   └── saved simulated-night list
└── From Apple Health
    ├── connection and authorization state
    ├── last refresh and refresh action
    └── source-labelled imported sleep episodes
```

The Apple Health section is visible independently of simulated history. Opening History does
not automatically present a permission prompt. The prompt begins only after the user selects
`Connect Apple Health`.

### Apple Health states

The presentation state distinguishes:

- HealthKit unavailable on the device.
- Not connected / read access not yet requested.
- Loading the latest 30-day window.
- Loaded records with a refresh timestamp.
- Successful query with no records.
- Refresh failure with cached records (`last loaded` state).
- Refresh failure without cached records.

HealthKit cannot reliably expose whether an empty read means denied read access or simply no
records. A successful empty query is therefore shown as `No Apple Health sleep records found`,
not as a definitive permission diagnosis. Query and authorization errors retain their own
failure state and retry action.

## Architecture

```text
SleepPulseApp
├── TrackingStore
│   └── simulated sessions, summaries and Recovery
└── HealthKitSleepStore
    ├── authorization state
    ├── refresh orchestration
    ├── atomic cache ownership
    └── HealthKitClient
        ├── LiveHealthKitClient
        └── FakeHealthKitClient

HistoryView
├── simulated History content ← TrackingStore
└── Apple Health content       ← HealthKitSleepStore
```

`HealthKitSleepStore` is created once by the SwiftUI application root and supplied to History.
It owns authorization, refresh serialization, cache reads/writes, and observable presentation
state. `TrackingStore` remains responsible only for SleepPulse's simulated session/database
lifecycle.

The system boundary is protocol-backed so unit tests do not require a real HealthKit store:

```swift
protocol HealthKitClient {
    func isHealthDataAvailable() -> Bool
    func authorizationState() async -> HealthKitAuthorizationState
    func requestReadAccess() async throws
    func readSleepEpisodes(from: Date, to: Date) async throws -> [HealthKitSleepEpisode]
}
```

The live client is the only type that imports and calls HealthKit. The store and presentation
models use native value types and do not expose `HKObject` instances to SwiftUI.

## Data model and normalization

### HealthKitSleepEpisode

```text
HealthKitSleepEpisode
- stable ID
- source display name
- source bundle identifier, when available
- start date/time
- end date/time
- in-bed minutes, nullable
- asleep minutes, nullable
- awake minutes, nullable
- core/light minutes, nullable
- deep minutes, nullable
- REM minutes, nullable
- stage availability
```

All durations are derived from HealthKit sample intervals and are clamped to nonnegative
values. A display episode is source-specific.

### Query window

The store requests a fixed rolling window:

```text
end = current date/time
start = end minus 30 calendar days
```

The requested window and successful fetch time are stored with the cache. Refresh replaces the
complete cached window rather than applying partial mutations.

### Source preservation

HealthKit samples are grouped only when they share a source identity. Source identity uses the
HealthKit metadata source bundle identifier when available and a stable fallback display value
otherwise. Samples from Apple Watch, SleepPulse-adjacent apps, AutoSleep, Pillow, or another
source are not cross-merged automatically.

### Episode boundaries

- Prefer `inBed` samples for the outer episode start/end when available.
- Otherwise use the earliest and latest sleep-stage sample from that source group.
- Combine overlapping or directly adjacent samples from the same source into one display
  episode when they belong to the same continuous sleep period.
- Preserve separate same-source episodes when a meaningful gap exists.
- Keep unknown or unsupported stages out of known-stage totals rather than assigning a guessed
  stage.
- `asleep` duration is the interval union of all explicit asleep categories (`asleepUnspecified`,
  core/light, deep, and REM) within the same-source episode, floored to whole minutes only after
  unioning and totaling their durations. Overlapping categories count once; `inBed`, `awake`, and
  unknown intervals do not contribute. No explicit asleep samples means unavailable (`nil`);
  an explicit zero or sub-minute total remains a real `0`.
- Per-stage durations independently union that stage's explicit intervals before flooring to
  minutes. They need not sum to `asleep`; missing stages remain unavailable and are never inferred.
- `awake` is calculated only from explicit awake samples.
- Core/light, deep, and REM values are nullable independently.
- The UI displays unavailable stage values as `Unavailable`, not `0 min`.

These rules create display episodes only. They do not create `NightlySummary` values and do
not enter any shared scoring calculator.

## Persistence and cache

Use a dedicated Codable file in the existing Application Support SleepPulse directory:

```text
HealthKitSleepCache
- formatVersion: Int
- episodes: [HealthKitSleepEpisode]
- fetchedAt: Date?
- windowStart: Date?
- windowEnd: Date?
```

The cache is written atomically through a temporary file followed by replacement. A failed
write leaves the previous valid cache intact. Decoding failure discards only the invalid
HealthKit cache and leaves the simulated Room database untouched.

The store loads the cache before or alongside a refresh so a previous successful result can be
shown while a new query is running. A failed refresh with valid cached episodes reports stale
data explicitly and never presents the failed query as successful.

## Authorization and privacy

- Add the HealthKit capability/entitlement to the iOS target.
- Add a specific sleep-analysis read usage description.
- Request only sleep-analysis read access.
- Do not request heart rate, HRV, workouts, or write permissions.
- Request access only from the explicit History action.
- Never request HealthKit permission during app launch or while starting simulated tracking.
- Keep HealthKit unavailable/denied states local to the Apple Health section.
- Provide an `Open Settings` action when the app can no longer present a useful permission
  prompt and the user needs to restore access.
- Do not log raw HealthKit samples or personally identifying source metadata.

## SwiftUI presentation

The Apple Health section should use existing Calm Night cards and the centered max-width
layout. Each episode row is newest first and includes:

- source label;
- date and time range or date-only summary;
- asleep duration when available;
- in-bed duration when available;
- available stage durations;
- an explicit unavailable label for missing stages.

Each row exposes one VoiceOver summary containing source, date, duration, and available stage
values. Date-only text uses the existing timezone-safe `NightDateFormatting` helper.

Manual refresh is disabled while a refresh is active. Multiple refresh taps serialize through
the store and cannot interleave cache writes. The History screen remains usable while the
HealthKit section loads or fails.

## Testing strategy

### HealthKit client/store tests

- HealthKit unavailable.
- Authorization success and request failure.
- Empty successful query.
- Query failure with no cache.
- Query failure with cached data and stale-state labeling.
- Atomic cache write and reload.
- Corrupt cache recovery without touching simulated data.
- Refresh serialization and replacement of the complete 30-day window.

### Normalization tests

- `inBed` defines outer boundaries when present.
- Stage-only fallback boundaries.
- Same-source overlapping samples combine correctly.
- Same-source gaps create separate episodes.
- Different sources never merge.
- Unknown stages remain unavailable.
- Zero and sub-minute durations retain correct nullable/text values.
- Source labels and stable IDs remain deterministic.
- Date formatting does not change across Foundation time zones.

### SwiftUI/UI tests

- Connect action appears without a launch prompt.
- Authorization, loading, loaded, empty, unavailable, retry, and stale-cache states render.
- Imported episodes remain below simulated content and do not alter simulated Recovery state.
- Refresh updates the source-labelled list.
- Accessibility summaries include source, date, duration, and unavailable stage values.
- Large text and iPad layouts remain readable.

Run the existing shared Android/iOS tests, Android unit tests, lint, phone/Wear builds, full
Swift/XCTest tests, UI tests, and `git diff --check` after implementation.

## Out of scope

- HealthKit write-back.
- Background delivery and anchored queries.
- HealthKit heart-rate/HRV import.
- Merging imported nights into `NightlySummary`.
- Using Apple Health data in Recovery, sleep debt, trends, or simulated charts.
- Cross-source deduplication or source-priority settings.
- HealthKit-driven alarms, widgets, or notifications.
- TypeSafe AI or natural-language sleep summaries.

## Completion criteria

The iOS History screen can explicitly connect to Apple Health, manually import the latest
30-day sleep-analysis window, cache it atomically, display source-preserving episodes, and
recover honestly from unavailable, empty, and failed states. Existing simulated tracking,
Recovery calculations, Android behavior, Room schema version 5, and app lifecycle ownership
remain unchanged.
