# SleepPulse iOS App

This directory contains the native SwiftUI iOS app for SleepPulse. It provides a Home
dashboard and History tab for clearly labelled simulated tracking. The deterministic
simulator emits approximately one real-time reading per second, and the Kotlin
Multiplatform core through `SleepPulseShared.framework` owns scoring, Room persistence,
summary finalization and interrupted-session recovery.

Tracking is intentionally foreground-only: keep the app open while recording. Entering the
background stops the simulation and saves the durable session; returning to the foreground
does not auto-resume it. The saved database lives in Application Support as
`SleepPulse/sleeppulse-simulated.db`, and History keeps the latest 30 local start dates.
BLE, HealthKit, alarms and continuous background tracking are not part of this milestone.

## Prerequisites

- Full Xcode with an iOS simulator runtime selected by `xcode-select`.
- JDK 17 and the Android SDK, because the Gradle wrapper builds the KMP framework.
- The repository's `local.properties` with `sdk.dir` for Gradle tasks.
- An iPhone or iPad simulator running iOS 17 or newer.

## Build and test

From the repository root:

```bash
xcodebuild \
  -project iosApp/SleepPulse.xcodeproj \
  -scheme SleepPulse \
  -destination 'platform=iOS Simulator,id=<simulator-uuid>' \
  -derivedDataPath iosApp/build/DerivedData \
   test CODE_SIGNING_ALLOWED=YES
```

Simulator UI tests require local ad-hoc signing of the test runner; this does not require
an Apple development team or a provisioning profile.

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

The UI test target runs a real 65-second session across Home and History, checks wall-clock
elapsed time, background saving, relaunch persistence and large-text layout screenshots.

## Launch on a simulator

```bash
xcrun simctl boot <simulator-uuid>
xcrun simctl bootstatus <simulator-uuid> -b
xcrun simctl install <simulator-uuid> /path/to/SleepPulse.app
xcrun simctl launch <simulator-uuid> com.sleeppulse.ios
```

The app shows the Calm Night palette, a circular 0–100 score gauge, simulated-data labels,
recorded elapsed time, latest-reading cards and a native History list of saved summaries.
