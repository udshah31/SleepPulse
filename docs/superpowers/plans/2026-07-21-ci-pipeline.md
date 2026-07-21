# CI Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a GitHub Actions workflow that runs lint, unit tests, and `assembleDebug` on every push to `master` and every PR targeting `master`, with a README status badge.

**Architecture:** A single workflow file (`.github/workflows/ci.yml`) running on `ubuntu-latest` with Temurin JDK 17 and Gradle dependency caching via `actions/setup-java`. Three sequential Gradle steps (lint → test → assembleDebug), each independent so the first failure stops the run and is clearly attributable. On any step failure, lint/test HTML reports are uploaded as build artifacts. A badge is added to `README.md` pointing at the workflow's status.

**Tech Stack:** GitHub Actions (`actions/checkout@v4`, `actions/setup-java@v4`, `actions/upload-artifact@v4`), Gradle 8.9 (already wrapped in the repo), JDK 17.

## Global Constraints

- Runner: `ubuntu-latest` — GitHub-hosted runners have the Android SDK preinstalled with `ANDROID_HOME` set; no SDK-install step, no `local.properties` needed in CI.
- JDK: Temurin (Eclipse Temurin) distribution, version 17, matching the project's stated JDK 17+ requirement.
- Trigger exactly: `push` to `master`, `pull_request` targeting `master` — no other branches.
- Steps must run as separate `run:` entries (not chained with `&&`), so the first failing check is unambiguous in the Actions UI.
- Scope is CI only — no emulator/instrumented tests, no release/signing, no deploy step (per spec's explicit exclusions).

---

### Task 1: CI workflow file

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: nothing (first task)
- Produces: a GitHub Actions workflow named `CI` with job id `build`, triggered on `push`/`pull_request` to `master`. Task 2's badge URL depends on the workflow file being named `ci.yml`.

- [ ] **Step 1: Create the workflow directory and file**

Create `.github/workflows/ci.yml` with this exact content:

```yaml
name: CI

on:
  push:
    branches: [master]
  pull_request:
    branches: [master]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: gradle

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Lint
        run: ./gradlew lint

      - name: Unit tests
        run: ./gradlew :app:test

      - name: Assemble debug APK
        run: ./gradlew :app:assembleDebug

      - name: Upload lint report
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: lint-report
          path: app/build/reports/lint-results-debug.html
          if-no-files-found: ignore

      - name: Upload test report
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: test-report
          path: app/build/reports/tests/testDebugUnitTest/
          if-no-files-found: ignore
```

- [ ] **Step 2: Validate YAML syntax locally**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))" && echo VALID`
Expected: `VALID` printed, no exception.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add GitHub Actions workflow for lint, test, and assembleDebug"
```

---

### Task 2: README status badge

**Files:**
- Modify: `README.md:1-3`

**Interfaces:**
- Consumes: the workflow file name `ci.yml` from Task 1 (badge URL embeds this path).
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Add the badge under the title**

In `README.md`, the file currently starts with:

```markdown
# SleepPulse

A native Android sleep/recovery tracking companion app (Kotlin, Jetpack Compose), in the
```

Change it to:

```markdown
# SleepPulse

![CI](https://github.com/udshah31/SleepPulse/actions/workflows/ci.yml/badge.svg)

A native Android sleep/recovery tracking companion app (Kotlin, Jetpack Compose), in the
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: add CI status badge to README"
```

---

### Task 3: Verify the workflow runs and is red/green correctly

**Files:**
- None created/modified permanently by this task — verification only, plus a throwaway commit that gets reverted.

**Interfaces:**
- Consumes: the workflow from Task 1, already committed on `master`.
- Produces: confirmation the pipeline works, recorded in the task's own commit message / PR description. No later task depends on this one.

- [ ] **Step 1: Push master and confirm the workflow runs green**

```bash
git push origin master
```

Then check the Actions tab (or `gh run list --branch master --limit 1`) and confirm the most recent run for commit from Task 2 succeeds (lint, test, assembleDebug all pass), since `master` is already known lint/test/build-clean going into this task.

Run: `gh run list --branch master --limit 1`
Expected: latest run shows `completed` / `success` for workflow `CI`.

- [ ] **Step 2: Prove the pipeline catches a real failure**

Create a throwaway broken commit on a scratch branch:

```bash
git checkout -b ci-verify-throwaway
```

Edit `app/src/test/java/com/sleeppulse/app/data/NightSummaryBuilderTest.kt` and add a deliberately failing assertion inside any existing `@Test` function, e.g. add the line `assertEquals(1, 2)` as the first line of the test body (using whatever assertion import that file already has).

```bash
git add app/src/test/java/com/sleeppulse/app/data/NightSummaryBuilderTest.kt
git commit -m "test: deliberately broken assertion to verify CI fails (throwaway)"
git push origin ci-verify-throwaway
```

Open a PR from `ci-verify-throwaway` into `master` (or just push and check the workflow run triggered by the push, since `pull_request` targeting `master` also triggers on the PR):

Run: `gh pr create --title "CI verify (throwaway, do not merge)" --body "Verifying CI catches failures. Will be closed without merging." --base master --head ci-verify-throwaway`

Then: `gh run list --branch ci-verify-throwaway --limit 1`
Expected: latest run shows `completed` / `failure`, with the `Unit tests` step as the failing step (not lint or assemble).

- [ ] **Step 3: Clean up the throwaway verification branch**

```bash
gh pr close <pr-number> --delete-branch
git checkout master
git branch -D ci-verify-throwaway 2>/dev/null || true
```

Confirm `master` is unaffected: `git log --oneline -3` should show Task 2's badge commit at the top, with no trace of the broken-assertion commit.
