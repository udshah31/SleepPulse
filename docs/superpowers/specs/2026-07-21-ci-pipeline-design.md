# CI Pipeline — Design Spec

**Date:** 2026-07-21
**Status:** Approved

## Goal

Add GitHub Actions CI so lint failures, unit test regressions, and compile breaks on
`:app:assembleDebug` are caught automatically on push/PR, closing the last "deferred to
hardening pass" item from the README aside from BLE and the multi-module split.

## Scope

In scope:
- One workflow file: `.github/workflows/ci.yml`
- Checks: `./gradlew lint`, `./gradlew :app:test`, `./gradlew :app:assembleDebug`
- Gradle dependency caching via `actions/setup-java`'s built-in cache
- Upload lint/test HTML reports as build artifacts on failure, for debugging from the
  Actions tab without re-running locally
- A CI status badge in `README.md`

Out of scope (explicitly deferred, per earlier discussion):
- Instrumented/emulator tests (`connectedAndroidTest`) — slower, flakier, not needed yet
- Release/signing pipeline
- Any deploy step

## Trigger

```yaml
on:
  push:
    branches: [master]
  pull_request:
    branches: [master]
```

## Job

- Runner: `ubuntu-latest` (GitHub-hosted runners ship with the Android SDK preinstalled
  and `ANDROID_HOME` already set — no separate SDK-install step, and no `local.properties`
  needed in CI since Gradle picks up `ANDROID_HOME`)
- JDK: Temurin 17 via `actions/setup-java@v4`, with `cache: gradle` enabled
- Steps, in order:
  1. `actions/checkout@v4`
  2. `actions/setup-java@v4` (Temurin 17, `cache: gradle`)
  3. `./gradlew lint`
  4. `./gradlew :app:test`
  5. `./gradlew :app:assembleDebug`
  6. On failure only: `actions/upload-artifact@v4` uploading
     `app/build/reports/lint-results-debug.html` and
     `app/build/reports/tests/testDebugUnitTest/` (`if: failure()`)

Each Gradle step runs independently (not chained with `&&`) so a lint failure doesn't
mask a later test failure in the same run — GitHub Actions runs workflow steps
sequentially and stops on the first failing step by default, which is what we want here:
fail fast, surface the first broken check.

## README change

Add a badge near the top, under the title:

```markdown
![CI](https://github.com/udshah31/SleepPulse/actions/workflows/ci.yml/badge.svg)
```

## Testing

There's no meaningful "unit test" for a CI workflow file. Validation is: push the branch,
confirm the workflow runs and goes green on the current `master` (which is already
lint/test/build-clean), then confirm a deliberately broken commit (e.g. a failing
assertion) turns it red — done as a throwaway verification, not left in history.
