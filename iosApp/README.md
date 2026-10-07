# SleepPulse iOS App

This directory contains the first native SwiftUI iOS app for SleepPulse. It is a
Home dashboard scaffold only: the demo readings are deterministic, and the score is
calculated by the shared Kotlin Multiplatform core through `SleepPulseShared.framework`.
There is no iOS sensor, persistence, HealthKit, background tracking, or network flow yet.

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
  test CODE_SIGNING_ALLOWED=NO
```

The Xcode build phase runs `scripts/build_shared_framework.sh`. It links the Kotlin
framework for `iosSimulatorArm64` and `iosX64`, combines the simulator slices into a
universal static framework, and places it under the ignored `iosApp/build/KotlinFrameworks`
directory. The app links that static framework; it is not copied into the app bundle.

The XCTest target verifies the actual Swift/Kotlin boundary:

- The fixed demo readings score 71.
- Empty readings score 0.
- Null HRV remains absent and the heart-rate-only score is 80.
- The ViewModel exposes the latest demo reading: 56 bpm, 68 ms, REM.

## Launch on a simulator

```bash
xcrun simctl boot <simulator-uuid>
xcrun simctl bootstatus <simulator-uuid> -b
xcrun simctl install <simulator-uuid> /path/to/SleepPulse.app
xcrun simctl launch <simulator-uuid> com.sleeppulse.ios
```

The dashboard shows the Calm Night palette, a circular 0–100 score gauge, a Demo data
label, latest-reading cards and a note identifying the shared scoring core.
