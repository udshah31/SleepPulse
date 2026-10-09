# SleepPulse iOS App

This directory contains the native SwiftUI iOS app for SleepPulse. It provides Home, History
and Recovery tabs for clearly labelled simulated tracking, plus a separate native Apple Health
sleep import in History. The deterministic
simulator emits approximately one real-time reading per second, and the Kotlin
Multiplatform core through `SleepPulseShared.framework` owns scoring, Room persistence,
summary finalization and interrupted-session recovery.

Tracking is intentionally foreground-only: keep the app open while recording. Entering the
background stops the simulation and saves the durable session; returning to the foreground
does not auto-resume it. The saved database lives in Application Support as
`SleepPulse/sleeppulse-simulated.db`, and History keeps the latest 30 local start dates.
Recovery compares the latest recorded date with up to seven preceding recorded dates after
four dates exist. HRV and heart-rate trends compare two seven-recorded-night windows after
14 dates exist; HRV coverage is shown and missing HRV remains unavailable. History debt uses
the latest seven recorded nights against the shared 480-minute (8-hour) target, while bedtime
consistency uses all retained dates. Its score and duration charts show up to 14 recorded
dates, preserve calendar gaps, and keep actual short-session durations.
BLE, alarms and continuous background tracking are not part of this milestone.

## From Apple Health

History's **From Apple Health** section reads only HealthKit **sleep analysis**. It never writes
to Apple Health and requests no heart-rate or HRV access. The native `HealthKitSleepStore` is
app-scoped alongside the simulated `TrackingStore`; switching tabs preserves both stores.
Imported episodes stay out of the shared Room database and never affect simulated tracking,
live scores, Recovery/baselines, History insights, or charts.

- **Connect Apple Health** is the explicit authorization entry point, followed by an import.
  Launch, opening History, simulated Start, and foreground transitions do not prompt or query.
  **Refresh Apple Health** manually re-reads without requesting authorization again. There is
  no observer/background sync or automatic refresh on relaunch.
- Each import reads the preceding **30 calendar days** using the current calendar, ending at
  query time (not a fixed 720-hour interval across daylight-saving changes). The query includes
  samples overlapping the boundaries and preserves their original intervals.
- Episodes group touching/overlapping samples **within each source**, preserving source
  name/identifier and separate overlapping sources. They are not a single canonical night.
  Rows show a UTC-based start date, asleep/in-bed durations, and available awake/core/deep/REM
  stages. Missing metrics say **Unavailable**; known zero/subminute values say **<1 min**
  (VoiceOver: “less than 1 minute”).
- The versioned native JSON cache lives at Application Support
  `SleepPulse/healthkit-sleep-cache.json`, separate from `sleeppulse-simulated.db`. A successful
  import atomically replaces the entire snapshot (episodes, fetched time, window) only after
  query, normalization, encoding, and disk write all succeed. This removes old/deleted episodes
  on refresh. Concurrent actions share one in-flight operation.
- A query/normalization/write failure retains the previous snapshot and its metadata, labelled
  **Last loaded Apple Health sleep**, with error and retry guidance. This also applies to a
  previously successful empty cache. Cached results remain visible during refresh and load on
  relaunch without a query; the transient failure status itself is not persisted. A corrupt or
  unsupported cache is ignored with an error and Connect retry, leaving simulated data intact.
- A successful empty result replaces old episodes and says **No Apple Health sleep records
  found**. HealthKit intentionally hides read denial: no data may mean no records or no readable
  records. Neither empty data, a completed authorization request, nor an “unnecessary” request
  status establishes whether read access was granted. The UI offers Settings only for explicit
  supporting error evidence, not inferred denial. Unsupported devices show an unavailable state.

### Authorization capability and physical-device validation

The app target includes `SleepPulse/SleepPulse.entitlements` (`com.apple.developer.healthkit`)
and the HealthKit capability in both build configurations. Its generated Info.plist contains
`NSHealthShareUsageDescription`: “SleepPulse reads sleep dates, durations, and stages from
Apple Health to show them in History.” The requested read set is only `.sleepAnalysis`, and
the write set is empty; no HealthKit background-delivery entitlement is requested.

For a **user-authorized** physical-device check, use a compatible unlocked iPhone/iPad with
HealthKit available and sleep records in Apple Health, enable Developer Mode/trust as required,
and select a development team and provisioning profile supporting the app's bundle identifier
and HealthKit capability. The project defaults to an empty team; simulator ad-hoc signing is
not physical-device provisioning. Obtain authorization before installing onto a personal device.

1. Launch and start simulated tracking: confirm no HealthKit sheet; switch Home/History/Recovery
   and confirm tracking and simulated Recovery remain independent.
2. Explicitly tap **Connect Apple Health** and inspect the system sleep-only read request.
   Previously decided permissions may prevent a new sheet; record the prior state. Completion
   alone is not evidence of a grant—verify returned records with the user's chosen permissions.
3. Compare dates, durations, and source labels with known Health data; where two sources overlap,
   verify separate episodes. Missing stage values must remain unavailable.
4. Check manual refresh/full replacement, successful empty behavior without a denial claim,
   and a reproducible read failure retaining the last loaded snapshot and metadata.
5. Relaunch and verify cache persistence without a prompt/query; confirm imported dates never
   advance simulated Recovery or populate simulated charts.

These device checks remain manual and separate from simulator automation. Device discovery is
read-only; a connected device by itself does not mean HealthKit validation has been performed.

## Prerequisites

- Full Xcode with an iOS simulator runtime selected by `xcode-select`.
- JDK 17 and the Android SDK, because the Gradle wrapper builds the KMP framework.
- `ANDROID_HOME` pointing at the Android SDK, or the checkout's `local.properties` with
  `sdk.dir`. A worktree does not automatically inherit another checkout's local SDK file.
- An iPhone or iPad simulator running iOS 17 or newer.

## Build and test

From the repository root:

```bash
ANDROID_HOME=/Users/udaysah/Library/Android/sdk \
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
xcodebuild \
  -project iosApp/SleepPulse.xcodeproj \
  -scheme SleepPulse \
  -destination 'platform=iOS Simulator,id=<simulator-uuid>' \
  -derivedDataPath iosApp/build/DerivedData \
  -parallel-testing-enabled NO -collect-test-diagnostics never \
  test CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- \
  SYMROOT="$PWD/iosApp/build/DerivedData/Build/Products" \
  OBJROOT="$PWD/iosApp/build/DerivedData/Build/Intermediates.noindex"
```

Simulator UI tests require local ad-hoc signing of the test runner; this does not require
an Apple development team or a provisioning profile.
Use your SDK path and an installed simulator UUID. For retained evidence, add an explicit
`-resultBundlePath iosApp/build/<unique-run-name>.xcresult` after verifying `iosApp/build`
exists; Xcode requires a result-bundle path that does not already exist.

The Xcode build phase runs `scripts/build_shared_framework.sh`. It links the Kotlin
framework for `iosSimulatorArm64` and `iosX64`, combines the simulator slices into a
universal static framework, and places it under the ignored `iosApp/build/KotlinFrameworks`
directory. The app links that static framework; it is not copied into the app bundle.

The XCTest target verifies the actual Swift/Kotlin boundary:

- The fixed demo readings score 71.
- Empty readings score 0.
- Null HRV remains absent and the heart-rate-only score is 80.
- The ViewModel exposes the latest demo reading: 56 bpm, 68 ms, REM.
- Real framework tests cover Start/Stop, save/reload, interrupted recovery, background saves,
  nullable HRV and lifecycle races.
- Native HealthKit tests cover source-preserving normalization, read-only client operations,
  authorization request semantics, 30-calendar-day/DST windows, atomic cache replacement,
  stale/empty results, and serialization. A real simulated store plus fake HealthKit I/O verifies
  that loaded, stale, and empty imports do not alter tracking, saved nights, or Recovery.
- `AppleHealthSleepSectionTests` renders every phase, exercises Connect/retry/Refresh through
  fake-backed buttons, and checks source/date/duration/stage VoiceOver labels. Retained screenshots
  cover loaded, empty, stale (including an empty cache), unavailable, zero/subminute metrics,
  large Dynamic Type, and an 834-point iPad-width host layout.

The UI test target runs a real 65-second session across Home, History and Recovery, checks
wall-clock elapsed time, background saving, relaunch persistence, chart accessibility labels,
and large-text layout screenshots. It verifies **From Apple Health** and **Connect Apple Health**
are reachable before/after simulated Start and relaunch, with no observed app/system sheet,
and unchanged simulated Recovery while tracking survives tab switching. It never taps the
actual Connect action or claims an actual permission grant; controlled permission outcomes
are covered by fake-backed integration tests.

Each UI test sets a fresh UUID in
`app.launchEnvironment["SLEEPPULSE_UI_TEST_STORAGE_ID"]`. In **DEBUG builds only**, the app-root
`AppStorageConfiguration` validates/canonicalizes that UUID and creates the namespace
`Application Support/SleepPulse/UITests/<UUID>/`. Both `sleeppulse-simulated.db` and
`healthkit-sleep-cache.json` use that directory through the existing store/cache constructors;
the HealthKit client remains live. Missing environment values retain normal user storage;
invalid IDs or directory-creation failures reject launch rather than falling back to user data.
Release builds ignore the override entirely. No default user data is reset, deleted, copied,
or used by an overridden launch, and no runtime fixtures are installed by production code.

The same app instance retains its UUID/environment through intentional relaunches, so its
saved data persists. Different tests get different directories and begin with empty simulated
History, a **0 of 4 dates** baseline, and a disconnected native cache regardless of existing
default/other-test data. The chart test explicitly starts and saves its own short real recording,
waiting for recorded time to advance before stopping. Unit tests additionally populate normal
and other-test locations under a temporary root, verify fresh-ID isolation and invalid-ID
rejection, and reconstruct the same ID with a saved simulated night and populated Health cache.
Test directories are retained for inspection; the test's UUID is attached to its XCTest result.

The hosted accessibility helper uses the undocumented `_AXSSetAutomationEnabled` /
`_AXSAutomationEnabled` symbols **only in the XCTest target**, restores their previous state,
and has been validated on the supplied iOS 26.2 simulator runtime. A different runtime may
require revisiting that helper; production has no such dependency. The iPad-width screenshot
is a hosted layout on that simulator, not a physical iPad run. The existing test-target
traditional-headermap warning and always-run shared-framework build-phase note are known.

## Launch on a simulator

```bash
xcrun simctl boot <simulator-uuid>
xcrun simctl bootstatus <simulator-uuid> -b
xcrun simctl install <simulator-uuid> /path/to/SleepPulse.app
xcrun simctl launch <simulator-uuid> com.sleeppulse.ios
```

The app shows the Calm Night palette, a circular 0–100 score gauge, simulated-data labels,
recorded elapsed time, latest-reading cards, Recovery baseline/readiness cards, and History
insight cards with accessible native Charts. Preview/test fixtures are explicit in-memory
values calculated through the same Kotlin coordinator; they never seed the runtime database.
