# SleepPulse Native iOS App Scaffold

## Status

Approved on 2026-10-07. The full design was reviewed in the main checkout before
execution in the `feature/ios-app-scaffold` worktree.

## Goal and architecture

Build a native SwiftUI iOS 17+ app under `iosApp/` that displays a Calm Night Home
dashboard with clearly labelled deterministic demo readings. A Swift adapter calls
the existing Kotlin `SleepScoreCalculator` through a static `SleepPulseShared` framework.
The ViewModel owns immutable display state and runs on the main actor; views render
state without constructing Kotlin objects or calling calculators.

## Framework and project

- Keep all iOS targets opt-in via `-PenableIosTargets=true`.
- Each existing iOS target produces the static `SleepPulseShared` framework.
- An Xcode build phase invokes `iosApp/scripts/build_shared_framework.sh` through the
  repository Gradle wrapper before Swift compilation. The script links the simulator's
  ARM64 and x86_64 static frameworks and creates a universal simulator framework with `lipo`;
  it uses the direct embed task's lower-level link tasks because the current AGP 9/Kotlin
  plugin combination does not keep `embedAndSignAppleFrameworkForXcode` registered when
  Xcode's full environment is present.
- Use relative project/framework paths, a normal Xcode project and a shared scheme;
  no project generator is required. Document JDK 17+, Android SDK, Xcode and simulator prerequisites.
- Simulator builds need no development team. Support iPhone and iPad on iOS 17+.
- Ignore frameworks, DerivedData, build outputs, and user-specific Xcode state.

## App responsibilities

- `SleepPulse.xcodeproj`: app and XCTest targets, shared scheme, Kotlin build phase.
- `SleepPulseApp.swift`: app entry and dashboard composition.
- `DashboardView.swift`: heading, demo label, circular sleep score, latest demo metrics.
- `DashboardViewModel.swift`: immutable snapshot and injected scoring adapter.
- `SharedSleepScoring.swift`: Swift reading/stage conversion to Kotlin and score delegation.
- `DemoReadings.swift`: fixed sample readings separate from presentation.
- `CalmNightTheme.swift`: Android Calm Night palette, native scalable typography.
- `SharedSleepScoringTests.swift` and `DashboardViewModelTests.swift`: real Swift/Kotlin
  language-boundary and state-snapshot tests.
- `iosApp/README.md`: build, test, launch instructions and demo scope.

## Data and behavior

Use three readings one minute apart: `(58 bpm, 62 ms, LIGHT)`,
`(60 bpm, 65 ms, DEEP)`, `(56 bpm, 68 ms, REM)`, with fixed epoch timestamps.
The shared score is 71; the latest demo metrics are 56 bpm, 68 ms and REM.
The same readings with null HRV score 80; empty readings score zero and display
unavailable metrics. HRV nullability must survive the Swift/Kotlin conversion.
The initial app is a synchronous demo with no network, tracking, permissions or persistence.

## Visual design

Match Android colors: background `#0B0F22`, gradient end `#111633`, surface `#151B3D`,
accent `#7C8BFF`, primary text `#E4E6F5`, secondary text `#8B93C4`.
Use a safe-area-aware scrollable dashboard, readable width on iPad, adaptive metric
layout at large Dynamic Type sizes, and meaningful VoiceOver labels and units.
Hide decorative gauge artwork from duplicate announcements.

## Acceptance and verification

1. Build the real simulator framework and the app from the committed shared scheme.
2. Run XCTest cases for score 71, empty-input score 0, and null-HRV score 80.
3. Install and launch on an iPhone simulator, capture and inspect a screenshot.
4. Run shared Android host tests and iOS simulator tests.
5. Run Android unit tests, lint, and phone/Wear debug builds.
6. Review source/docs and `git diff --check`; exclude local/generated artifacts.

## Follow-up milestones

Persisted sessions, History, iOS sensor/repository implementations, recovery UI,
CoreBluetooth, HealthKit, alarms, background tracking and iOS CI follow separately.
