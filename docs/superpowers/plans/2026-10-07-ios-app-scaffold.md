# Native iOS App Scaffold Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and launch a native SwiftUI Home dashboard whose demo sleep score comes from the real KMP core.

**Architecture:** A static `SleepPulseShared` framework is built by an Xcode shell phase that invokes the KMP target link tasks and creates a universal simulator framework. Swift readings pass through a scoring adapter into Kotlin; a main-actor ViewModel publishes immutable state to a SwiftUI dashboard. A normal Xcode project and shared scheme build the app and real-boundary XCTest cases.

**Tech Stack:** SwiftUI, XCTest, iOS 17+, Xcode, Kotlin 2.3.21, AGP 9.0.1, Gradle 9.1.0, existing KMP/Room core.

**Spec:** `docs/superpowers/specs/2026-10-07-ios-app-scaffold-design.md`

## Global Constraints

- Work in `feature/ios-app-scaffold` in an isolated worktree; do not commit without a user request.
- Keep iOS targets opt-in with `-PenableIosTargets=true` and preserve existing shared formulas.
- Framework is static and named `SleepPulseShared`; Xcode invokes `iosApp/scripts/build_shared_framework.sh`, which links `:shared:linkDebugFrameworkIosSimulatorArm64` plus `:shared:linkDebugFrameworkIosX64` for simulator builds and creates a universal framework.
- App deployment target is iOS 17.0, iPhone and iPad; simulator builds require no team.
- Keep build paths relative; use JDK 17+, the existing Android SDK and full Xcode.
- Swift views contain no Kotlin construction or scoring logic.
- Demo data is labelled; readings are 58/62/LIGHT, 60/65/DEEP, 56/68/REM one minute apart.
- Expected score is 71, empty is 0, same heart rates with null HRV is 80.
- Ignore generated frameworks, DerivedData and user-specific Xcode state.

---

## Task 1: Swift-importable shared framework

**Files:** Modify `shared/build.gradle.kts`; framework headers are generated under `shared/build/bin/`.
**Interfaces:** Produces `SleepPulseShared.framework`, exported `SensorReading`, `SleepStage`, `KotlinDouble`, and `SleepScoreCalculator`.

- [x] Add framework declarations to each existing iOS target:

```kotlin
listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
    target.binaries.framework {
        baseName = "SleepPulseShared"
        isStatic = true
    }
}
```

- [x] Run `./gradlew -PenableIosTargets=true :shared:linkDebugFrameworkIosSimulatorArm64 --console=plain`.
- [x] Read the generated header to confirm exact Swift initializer and enum spellings before writing the adapter.
- [x] Verify the direct embed task behavior; with Xcode environment variables it is not stable under the current toolchain, so the app uses the checked-in link/lipo script described above.

## Task 2: Buildable app and real Swift/Kotlin scoring boundary

**Files:** Create `iosApp/SleepPulse.xcodeproj/project.pbxproj`, `iosApp/SleepPulse.xcodeproj/xcshareddata/xcschemes/SleepPulse.xcscheme`, `iosApp/SleepPulse/SleepPulseApp.swift`, `iosApp/SleepPulse/Shared/SharedSleepScoring.swift`, `iosApp/SleepPulse/Demo/DemoReadings.swift`, `iosApp/SleepPulseTests/SharedSleepScoringTests.swift`; modify `.gitignore`.
**Interfaces:** `SleepReading(timestampMillis: Int64, heartRateBpm: Int32, hrvMillis: Double?, stage: ReadingStage)`, `SleepScoring.score(readings: [SleepReading]) -> Int`, `SharedSleepScoring`, `DemoReadings.night`.

- [x] Write the three meaningful XCTest boundary cases before the adapter:

```swift
func testDemoScoreUsesSharedCalculator() {
    XCTAssertEqual(SharedSleepScoring().score(readings: DemoReadings.night), 71)
}
func testEmptyReadingsScoreZero() {
    XCTAssertEqual(SharedSleepScoring().score(readings: []), 0)
}
func testMissingHrvRemainsUnknown() {
    let readings = DemoReadings.night.map {
        SleepReading(timestampMillis: $0.timestampMillis, heartRateBpm: $0.heartRateBpm,
                     hrvMillis: nil, stage: $0.stage)
    }
    XCTAssertEqual(SharedSleepScoring().score(readings: readings), 80)
}
```

- [x] Configure app and hosted XCTest targets, source/resource phases, app dependency, and shared scheme.
- [x] Configure the framework build phase first, with relative `SRCROOT/..` paths and JDK detection:

```sh
set -eu
if [ -z "${JAVA_HOME:-}" ]; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
fi
"$SRCROOT/scripts/build_shared_framework.sh"
```

- [x] Set `FRAMEWORK_SEARCH_PATHS` to `$(SRCROOT)/build/KotlinFrameworks/$(CONFIGURATION)/$(SDK_NAME)`, link `-framework SleepPulseShared`, and use generated Info.plists. The static framework is linked into the app and is not embedded as a runtime framework.
- [x] Build test sources, observe unresolved adapter names, and implement the Swift adapter using the generated header's exact names.

```swift
protocol SleepScoring { func score(readings: [SleepReading]) -> Int }
// Map each reading to SensorReading, preserving nullable KotlinDouble, then:
// Int(SleepScoreCalculator.shared.score(readings: kotlinReadings))
```

- [x] Implement fixtures with fixed timestamps 1_780_000_000_000, +60_000 and +120_000.
- [x] Run `xcodebuild -project iosApp/SleepPulse.xcodeproj -scheme SleepPulse -destination 'platform=iOS Simulator,id=<available UUID>' -derivedDataPath iosApp/build/DerivedData test CODE_SIGNING_ALLOWED=NO` with an actual simulator UUID substituted.

## Task 3: Calm Night dashboard

**Files:** Create `iosApp/SleepPulse/Dashboard/DashboardViewModel.swift`, `DashboardView.swift`, `iosApp/SleepPulse/Theme/CalmNightTheme.swift`; update `SleepPulseApp.swift` and project source entries.
**Interfaces:** Immutable `DashboardState` with score and latest-reading metrics; `@MainActor DashboardViewModel` with published state, injected `SleepScoring` and readings.

- [x] Implement the ViewModel snapshot; calculate the score through `SleepScoring`, select the last reading and format unavailable values as an em dash.
- [x] Define SwiftUI colors matching `#0B0F22`, `#111633`, `#151B3D`, `#7C8BFF`, `#E4E6F5`, `#8B93C4`.
- [x] Render a scrollable safe-area-aware dashboard with heading, demo badge, circular score, latest-reading metric cards and shared-core note. Use relative/scalable type and an adaptive metric grid.
- [x] Add combined accessible labels with units and hide decorative shapes.
- [x] Build, install and launch the app with `xcrun simctl`; capture and inspect a simulator screenshot. The screenshot was saved to the approved temporary path because the simulator denied writing into the repository build directory.

## Task 4: Documentation and regression verification

**Files:** Create `iosApp/README.md`; update root `README.md` and current-state documentation to reflect the app.

- [x] Document Xcode, JDK, Android SDK, shared framework build phase, simulator build/test/launch commands, and labelled demo scope.
- [x] Run `./gradlew :shared:testAndroidHostTest :app:test lint :app:assembleDebug :wear:assembleDebug --console=plain`.
- [x] Run `./gradlew -PenableIosTargets=true :shared:iosSimulatorArm64Test --console=plain`.
- [x] Inspect the full change and run `git diff --check`; review ignored local files and keep only intended source/docs in the worktree.
- [x] Record the implementation worktree and simulator verification in the final handoff.

## Plan self-review

- Spec coverage: framework/project integration is Tasks 1–2; ViewModel, theme, accessibility and screenshot are Task 3; docs/regression checks are Task 4.
- Language-boundary testing covers fixed sample, nullable HRV and empty input without copying the Kotlin formula.
- No database schema changes, platform integrations, or unrelated UI migration are included.
- Generated interop names are confirmed from the real framework header before use.

## Verification record (2026-10-07)

- `xcodebuild ... test CODE_SIGNING_ALLOWED=NO`: 5 XCTest cases passed.
- `xcodebuild ... build CODE_SIGNING_ALLOWED=NO`: app build passed.
- iPhone 17 Pro simulator `09B95FEC-815B-4E54-81B1-D0A3FF8F0AE1`: app installed, launched,
  and screenshot inspected; Home showed score 71, 56 bpm, 68 ms and REM.
- `./gradlew :shared:testAndroidHostTest :app:test lint :app:assembleDebug :wear:assembleDebug` passed.
- `./gradlew -PenableIosTargets=true :shared:iosSimulatorArm64Test` passed.
- Added explicit KSP-to-lint task dependencies for AGP 9 host-test lint validation:
  `generateAndroidHostTestLintModel` and `lintAnalyzeAndroidHostTest` depend on `kspAndroidHostTest`.
- `git diff --check` passed. Generated `iosApp/build/`, Gradle build output and local properties
  remain ignored.
