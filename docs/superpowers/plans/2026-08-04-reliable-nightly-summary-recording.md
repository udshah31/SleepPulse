# Reliable Nightly Summary Recording Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a completed sleep session reliably become a `NightlySummary` regardless of whether the Activity/ViewModel is alive when the session ends, using data already durably persisted to Room.

**Architecture:** Move summary-building out of `DashboardViewModel` (dies with the process) into `SleepRepositoryImpl.disconnectSensor()` (reads back persisted `SessionReadingEntity` rows before they're deleted) and a new plain-Kotlin `SleepSessionFinalizer` that `SleepTrackingService` calls to notify + refresh the widget after a summary is actually recorded.

**Tech Stack:** Kotlin, Hilt DI, Room, JUnit4 + Mockito-Kotlin + Turbine for unit tests (`app/src/test`, no Robolectric/instrumented tests in this codebase).

## Global Constraints

- Never use `git add -A` or `git add .` — stage files explicitly by path (per `CLAUDE.md`).
- No unit or instrumented Android Service tests are added — keep `SleepTrackingService` a thin shell so all logic stays testable as plain Kotlin.
- Match existing test-double conventions: hand-written `Fake*` classes in `app/src/test/java/com/sleeppulse/app/testutil/`, not Mockito mocks, for repository/notifier-style seams.

---

### Task 1: Extract `RecoveryScoreCalculator.scoreLatest`

**Files:**
- Modify: `app/src/main/java/com/sleeppulse/app/ui/dashboard/RecoveryScoreCalculator.kt`
- Modify: `app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardViewModel.kt:78-82`
- Test: `app/src/test/java/com/sleeppulse/app/ui/dashboard/RecoveryScoreCalculatorTest.kt`

**Interfaces:**
- Produces: `RecoveryScoreCalculator.scoreLatest(nights: List<NightlySummary>): RecoveryResult?` — takes nights ordered most-recent-first (same ordering `SleepRepository.recentNights()` already returns), treats `nights.first()` as "last night" and the next 7 as baseline. Returns `null` if `nights` is empty (delegates to `score`, which returns null itself when baseline < 3).

This is a pure refactor extracting logic already proven correct by the existing `DashboardViewModelTest` recovery tests — no behavior change, so no new DashboardViewModel test is needed here; Task 6 will remove the now-redundant private helper reference once `SleepSessionFinalizer` also depends on `scoreLatest`.

- [ ] **Step 1: Write the failing test**

Add to the bottom of `RecoveryScoreCalculatorTest.kt`, inside the `RecoveryScoreCalculatorTest` class, right after the last existing test (`RecoveryResult exposes the raw hrvDeviation...`):

```kotlin
    @Test
    fun `scoreLatest treats the first night as last night and the next 7 as baseline`() {
        val baseline = listOf(night(50.0, 60), night(50.0, 60), night(50.0, 60))
        val lastNight = night(hrv = 62.5, hr = 54) // +25% HRV, -10% RHR vs baseline

        val result = RecoveryScoreCalculator.scoreLatest(listOf(lastNight) + baseline)

        assertEquals(RecoveryTier.OPTIMAL, result?.tier)
    }

    @Test
    fun `scoreLatest returns null for an empty night list`() {
        assertNull(RecoveryScoreCalculator.scoreLatest(emptyList()))
    }

    @Test
    fun `scoreLatest ignores nights beyond the first 7 baseline nights`() {
        val lastNight = night(hrv = 62.5, hr = 54) // +25% HRV, -10% RHR vs. the neutral baseline below
        val neutralBaseline = List(7) { night(50.0, 60) }
        // Wildly different "poison" nights beyond the 7-night baseline window: if scoreLatest
        // incorrectly included them in the average, the deviations above would be swamped and
        // the tier would drop well below OPTIMAL.
        val poisonNights = List(5) { night(hrv = 5.0, hr = 150) }

        val result = RecoveryScoreCalculator.scoreLatest(listOf(lastNight) + neutralBaseline + poisonNights)

        assertEquals(RecoveryTier.OPTIMAL, result?.tier)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.ui.dashboard.RecoveryScoreCalculatorTest" -q`
Expected: FAIL with "unresolved reference: scoreLatest"

- [ ] **Step 3: Implement `scoreLatest`**

In `RecoveryScoreCalculator.kt`, add this function to the `RecoveryScoreCalculator` object, right after `score(...)` and before `private fun tierFor(...)`:

```kotlin
    fun scoreLatest(nights: List<NightlySummary>): RecoveryResult? {
        val lastNight = nights.firstOrNull() ?: return null
        val baseline = nights.drop(1).take(7)
        return score(lastNight, baseline)
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.ui.dashboard.RecoveryScoreCalculatorTest" -q`
Expected: PASS

- [ ] **Step 5: Wire `DashboardViewModel` to use it**

In `DashboardViewModel.kt`, replace the existing `computeRecovery` function body (currently lines 78-82):

```kotlin
    private fun computeRecovery(nights: List<NightlySummary>): RecoveryResult? {
        val lastNight = nights.firstOrNull() ?: return null
        val baseline = nights.drop(1).take(7)
        return RecoveryScoreCalculator.score(lastNight, baseline)
    }
```

with:

```kotlin
    private fun computeRecovery(nights: List<NightlySummary>): RecoveryResult? =
        RecoveryScoreCalculator.scoreLatest(nights)
```

- [ ] **Step 6: Run the full test suite to confirm no regressions**

Run: `./gradlew :app:test -q`
Expected: PASS (all existing `DashboardViewModelTest` recovery tests still pass unchanged, since behavior is identical)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/ui/dashboard/RecoveryScoreCalculator.kt app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardViewModel.kt app/src/test/java/com/sleeppulse/app/ui/dashboard/RecoveryScoreCalculatorTest.kt
git commit -m "refactor: extract RecoveryScoreCalculator.scoreLatest from DashboardViewModel"
```

---

### Task 2: `SleepRepositoryImpl.disconnectSensor()` records the completed session

**Files:**
- Modify: `app/src/main/java/com/sleeppulse/app/data/repository/SleepRepository.kt`
- Modify: `app/src/main/java/com/sleeppulse/app/data/repository/SleepRepositoryImpl.kt`
- Modify: `app/src/test/java/com/sleeppulse/app/testutil/FakeSleepRepository.kt`
- Test: `app/src/test/java/com/sleeppulse/app/data/repository/SleepRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `NightSummaryBuilder.build(readings: List<SensorReading>, date: LocalDate): NightlySummary` (existing, unchanged), `SleepSessionDao.readingsFor/finalizeAndClear` (existing, unchanged).
- Produces: `SleepRepository.disconnectSensor(): NightlySummary?` (changed from `Unit`) — the recorded summary, or `null` if the session had no readings. Later tasks (`SleepSessionFinalizer`) consume this return value.

- [ ] **Step 1: Write the failing tests**

Add these tests to `SleepRepositoryImplTest.kt`, right after the existing `disconnecting with no readings collected does not call insertReadings` test (after line 180):

```kotlin
    @Test
    fun `disconnecting after readings were flushed records a nightly summary dated from session start`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val sessionDao = FakeSleepSessionDao()
        val startMillis = LocalDate.of(2026, 7, 20).atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val repository = SleepRepositoryImpl(sensorDataSource, dao, sessionDao, backgroundScope, mock()) { startMillis }

        repository.connectSensor()
        runCurrent()
        repeat(20) { i -> sensorDataSource.readingsFlow.emit(reading(timestampMillis = startMillis + i * 60_000L)) }
        advanceTimeBy(1)

        val summary = repository.disconnectSensor()

        assertEquals(LocalDate.of(2026, 7, 20), summary?.date)
        assertEquals(listOf("upsert", "trimToLast30Days"), dao.recordedCalls)
        assertTrue(sessionDao.sessions.single().finalized)
        assertEquals(0, sessionDao.readings.size)
    }

    @Test
    fun `disconnecting with no readings returns null and still finalizes the empty session`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val sessionDao = FakeSleepSessionDao()
        val repository = SleepRepositoryImpl(sensorDataSource, dao, sessionDao, backgroundScope, mock()) { 0L }

        repository.connectSensor()
        val summary = repository.disconnectSensor()

        assertEquals(null, summary)
        assertTrue(dao.recordedCalls.isEmpty())
        assertTrue(sessionDao.sessions.single().finalized)
    }

    @Test
    fun `a recordNightlySummary failure during disconnect leaves the session unfinalized`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val sessionDao = FakeSleepSessionDao()
        val startMillis = 1_000L
        val repository = SleepRepositoryImpl(sensorDataSource, dao, sessionDao, backgroundScope, mock()) { startMillis }

        repository.connectSensor()
        runCurrent()
        sensorDataSource.readingsFlow.emit(reading(timestampMillis = startMillis))
        advanceTimeBy(30_001) // force a flush to the session dao

        val sessionId = sessionDao.sessions.single().sessionId
        sessionDao.readingsForFailures.add(sessionId)

        try {
            repository.disconnectSensor()
        } catch (e: IllegalStateException) {
            // expected: readingsFor throws for this session id
        }

        assertTrue(!sessionDao.sessions.single().finalized)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.data.repository.SleepRepositoryImplTest" -q`
Expected: FAIL — `disconnectSensor()` currently returns `Unit`, so `summary?.date` and `assertEquals(null, summary)` won't compile / will fail type-checking. (If Kotlin compilation fails outright, that also counts as the expected "fail" for this step — proceed to Step 3 regardless.)

- [ ] **Step 3: Update the `SleepRepository` interface**

In `SleepRepository.kt`, change:

```kotlin
    suspend fun disconnectSensor()
```

to:

```kotlin
    suspend fun disconnectSensor(): NightlySummary?
```

- [ ] **Step 4: Rewrite `SleepRepositoryImpl` to track session start and share finalize logic**

In `SleepRepositoryImpl.kt`, replace the `private var activeSessionId: Long? = null` line with:

```kotlin
    private var activeSessionId: Long? = null
    private var activeSessionStartMillis: Long? = null
```

Replace the body of `connectSensor()` (the session-creation portion) — change:

```kotlin
    override suspend fun connectSensor() {
        sensorDataSource.connect()
        val sessionId = sessionDao.createSession(
            SleepSessionEntity(startEpochMillis = nowMillis(), finalized = false)
        )
        activeSessionId = sessionId
```

to:

```kotlin
    override suspend fun connectSensor() {
        sensorDataSource.connect()
        val startMillis = nowMillis()
        val sessionId = sessionDao.createSession(
            SleepSessionEntity(startEpochMillis = startMillis, finalized = false)
        )
        activeSessionId = sessionId
        activeSessionStartMillis = startMillis
```

Replace the entire `disconnectSensor()` function:

```kotlin
    override suspend fun disconnectSensor() {
        collectionJob?.cancelAndJoin()
        flushTimerJob?.cancelAndJoin()
        collectionJob = null
        flushTimerJob = null
        flush()
        activeSessionId?.let { sessionDao.finalizeAndClear(it) }
        activeSessionId = null
        sensorDataSource.disconnect()
    }
```

with:

```kotlin
    override suspend fun disconnectSensor(): NightlySummary? {
        collectionJob?.cancelAndJoin()
        flushTimerJob?.cancelAndJoin()
        collectionJob = null
        flushTimerJob = null
        flush()
        val sessionId = activeSessionId
        val startMillis = activeSessionStartMillis
        activeSessionId = null
        activeSessionStartMillis = null
        sensorDataSource.disconnect()
        return if (sessionId != null && startMillis != null) {
            finalizeSession(sessionId, startMillis)
        } else {
            null
        }
    }
```

Replace the body of `recoverUnfinalizedSessions()` — change:

```kotlin
    suspend fun recoverUnfinalizedSessions() {
        sessionDao.unfinalizedSessions().forEach { session ->
            try {
                val readings = sessionDao.readingsFor(session.sessionId).map { it.toDomainReading() }
                if (readings.isNotEmpty()) {
                    val date = java.time.Instant.ofEpochMilli(session.startEpochMillis)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                    recordNightlySummary(NightSummaryBuilder.build(readings, date))
                }
                sessionDao.finalizeAndClear(session.sessionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Leave this session unfinalized so it's retried on the next launch rather than
                // silently discarding real reading data from a single bad session.
            }
        }
    }
```

to:

```kotlin
    suspend fun recoverUnfinalizedSessions() {
        sessionDao.unfinalizedSessions().forEach { session ->
            try {
                finalizeSession(session.sessionId, session.startEpochMillis)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Leave this session unfinalized so it's retried on the next launch rather than
                // silently discarding real reading data from a single bad session.
            }
        }
    }

    /**
     * Reads back a session's persisted readings, records a [NightlySummary] if there are any,
     * then finalizes/clears the session row. Only finalizes on success — if [recordNightlySummary]
     * throws (e.g. a Room or Health Connect write failure), the session is left unfinalized so
     * it's retried by [recoverUnfinalizedSessions] on next app launch rather than losing data.
     */
    private suspend fun finalizeSession(sessionId: Long, startEpochMillis: Long): NightlySummary? {
        val readings = sessionDao.readingsFor(sessionId).map { it.toDomainReading() }
        if (readings.isEmpty()) {
            sessionDao.finalizeAndClear(sessionId)
            return null
        }
        val date = java.time.Instant.ofEpochMilli(startEpochMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        val summary = NightSummaryBuilder.build(readings, date)
        recordNightlySummary(summary)
        sessionDao.finalizeAndClear(sessionId)
        return summary
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.data.repository.SleepRepositoryImplTest" -q`
Expected: PASS — including the pre-existing `recoverUnfinalizedSessions...` tests (unchanged behavior, now sharing `finalizeSession`).

- [ ] **Step 6: Update `FakeSleepRepository` to match the new signature**

In `FakeSleepRepository.kt`, add a settable field near the other `val`/`var` declarations (after `val recordedSummaries = mutableListOf<NightlySummary>()`):

```kotlin
    var nextDisconnectSummary: NightlySummary? = null
```

Replace:

```kotlin
    override suspend fun disconnectSensor() {
        disconnectSensorCallCount++
        connectionStateFlow.value = SensorConnectionState.Disconnected
    }
```

with:

```kotlin
    override suspend fun disconnectSensor(): NightlySummary? {
        disconnectSensorCallCount++
        connectionStateFlow.value = SensorConnectionState.Disconnected
        val summary = nextDisconnectSummary
        if (summary != null) {
            // Mirrors recordNightlySummary()'s effect on nightsFlow below, so callers that
            // read recentNights() right after disconnecting (e.g. to compute recovery) see
            // tonight's summary already reflected, matching the real repository.
            nightsFlow.value = listOf(summary) + nightsFlow.value
        }
        return summary
    }
```

- [ ] **Step 7: Build the whole module to confirm nothing else breaks from the interface change**

Run: `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin -q`
Expected: BUILD SUCCESSFUL (this surfaces any other `disconnectSensor()` caller/override that needs attention before continuing — there should be none besides `SleepTrackingService`, which Task 5 updates)

- [ ] **Step 8: Run the full test suite**

Run: `./gradlew :app:test -q`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/data/repository/SleepRepository.kt app/src/main/java/com/sleeppulse/app/data/repository/SleepRepositoryImpl.kt app/src/test/java/com/sleeppulse/app/testutil/FakeSleepRepository.kt app/src/test/java/com/sleeppulse/app/data/repository/SleepRepositoryImplTest.kt
git commit -m "fix: record a nightly summary from persisted readings on disconnectSensor

Previously disconnectSensor() deleted the session's durably-flushed
readings without ever building a NightlySummary from them, so a
session lost its history if the ViewModel process died before the
foreground service stopped tracking. Now it shares the same
read-build-record logic recoverUnfinalizedSessions() already used for
crash recovery, and only finalizes the session row on success."
```

---

### Task 3: `WidgetRefresher` seam

**Files:**
- Create: `app/src/main/java/com/sleeppulse/app/widget/WidgetRefresher.kt`
- Create: `app/src/main/java/com/sleeppulse/app/widget/WidgetRefresherImpl.kt`
- Create: `app/src/test/java/com/sleeppulse/app/testutil/FakeWidgetRefresher.kt`
- Modify: `app/src/main/java/com/sleeppulse/app/di/AppModule.kt`

**Interfaces:**
- Produces: `interface WidgetRefresher { suspend fun refresh() }`, implemented by `WidgetRefresherImpl` (wraps `SleepPulseWidget.refresh(context)`), bound in Hilt as `@Singleton`. `FakeWidgetRefresher` (test double) exposes `refreshCallCount: Int`.

- [ ] **Step 1: Create the interface**

Create `app/src/main/java/com/sleeppulse/app/widget/WidgetRefresher.kt`:

```kotlin
package com.sleeppulse.app.widget

/**
 * Seam around [SleepPulseWidget.refresh] so callers that need to trigger a widget refresh
 * don't need an Android [android.content.Context] dependency directly — keeps them plain
 * Kotlin and unit-testable.
 */
interface WidgetRefresher {
    suspend fun refresh()
}
```

- [ ] **Step 2: Create the implementation**

Create `app/src/main/java/com/sleeppulse/app/widget/WidgetRefresherImpl.kt`:

```kotlin
package com.sleeppulse.app.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetRefresherImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetRefresher {
    override suspend fun refresh() = SleepPulseWidget.refresh(context)
}
```

- [ ] **Step 3: Create the fake test double**

Create `app/src/test/java/com/sleeppulse/app/testutil/FakeWidgetRefresher.kt`:

```kotlin
package com.sleeppulse.app.testutil

import com.sleeppulse.app.widget.WidgetRefresher

class FakeWidgetRefresher : WidgetRefresher {
    var refreshCallCount = 0
        private set

    override suspend fun refresh() {
        refreshCallCount++
    }
}
```

- [ ] **Step 4: Bind it in Hilt**

In `AppModule.kt`, add the import:

```kotlin
import com.sleeppulse.app.widget.WidgetRefresher
import com.sleeppulse.app.widget.WidgetRefresherImpl
```

(insert alphabetically among the existing imports, after the `com.sleeppulse.app.tracking.*` imports)

Add this binding to `BindingsModule`, after `bindSmartAlarmScheduler`:

```kotlin
    @Binds
    @Singleton
    abstract fun bindWidgetRefresher(impl: WidgetRefresherImpl): WidgetRefresher
```

- [ ] **Step 5: Build to confirm it compiles and Hilt wires it correctly**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/widget/WidgetRefresher.kt app/src/main/java/com/sleeppulse/app/widget/WidgetRefresherImpl.kt app/src/test/java/com/sleeppulse/app/testutil/FakeWidgetRefresher.kt app/src/main/java/com/sleeppulse/app/di/AppModule.kt
git commit -m "feat: add WidgetRefresher seam for widget updates outside Composable/Service code"
```

---

### Task 4: `SleepSessionFinalizer`

**Files:**
- Create: `app/src/main/java/com/sleeppulse/app/notifications/SleepSessionFinalizer.kt`
- Test: `app/src/test/java/com/sleeppulse/app/notifications/SleepSessionFinalizerTest.kt`

**Interfaces:**
- Consumes: `SleepRepository.disconnectSensor(): NightlySummary?` (Task 2), `SleepRepository.recentNights(): Flow<List<NightlySummary>>` (existing), `RecoveryScoreCalculator.scoreLatest(nights): RecoveryResult?` (Task 1), `SleepSummaryNotifier.notify(summary, recoveryResult)` (existing), `WidgetRefresher.refresh()` (Task 3).
- Produces: `class SleepSessionFinalizer(repository, notifier, widgetRefresher) { suspend fun finalize() }`. Consumed by `SleepTrackingService` in Task 5.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/sleeppulse/app/notifications/SleepSessionFinalizerTest.kt`:

```kotlin
package com.sleeppulse.app.notifications

import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.testutil.FakeSleepRepository
import com.sleeppulse.app.testutil.FakeSleepSummaryNotifier
import com.sleeppulse.app.testutil.FakeWidgetRefresher
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepSessionFinalizerTest {

    private fun night(date: LocalDate, hrv: Double = 50.0, hr: Int = 60) = NightlySummary(
        date = date,
        sleepScore = 70,
        avgHeartRateBpm = hr,
        avgHrvMillis = hrv,
        totalSleepMinutes = 420,
        deepSleepMinutes = 90,
        remSleepMinutes = 100,
    )

    @Test
    fun `notifies and refreshes the widget when a summary was recorded`() = runTest {
        val repository = FakeSleepRepository()
        val notifier = FakeSleepSummaryNotifier()
        val widgetRefresher = FakeWidgetRefresher()
        val summary = night(LocalDate.of(2026, 8, 4))
        repository.nextDisconnectSummary = summary
        val finalizer = SleepSessionFinalizer(repository, notifier, widgetRefresher)

        finalizer.finalize()

        assertEquals(listOf(summary), notifier.notifiedSummaries)
        assertEquals(1, widgetRefresher.refreshCallCount)
    }

    @Test
    fun `does nothing when disconnectSensor returns null`() = runTest {
        val repository = FakeSleepRepository()
        val notifier = FakeSleepSummaryNotifier()
        val widgetRefresher = FakeWidgetRefresher()
        repository.nextDisconnectSummary = null
        val finalizer = SleepSessionFinalizer(repository, notifier, widgetRefresher)

        finalizer.finalize()

        assertEquals(0, notifier.notifiedSummaries.size)
        assertEquals(0, widgetRefresher.refreshCallCount)
    }

    @Test
    fun `computes recovery against the just-recorded summary as last night`() = runTest {
        val repository = FakeSleepRepository()
        val notifier = FakeSleepSummaryNotifier()
        val widgetRefresher = FakeWidgetRefresher()

        // 3 baseline nights already on record before tonight's summary is recorded.
        repository.nightsFlow.value = listOf(
            night(LocalDate.of(2026, 8, 1)),
            night(LocalDate.of(2026, 8, 2)),
            night(LocalDate.of(2026, 8, 3)),
        )
        val tonight = night(LocalDate.of(2026, 8, 4), hrv = 62.5, hr = 54) // well-recovered vs. baseline
        repository.nextDisconnectSummary = tonight
        val finalizer = SleepSessionFinalizer(repository, notifier, widgetRefresher)

        finalizer.finalize()

        // FakeSleepRepository.disconnectSensor() prepends nextDisconnectSummary to nightsFlow
        // itself (mirroring the real repository's recordNightlySummary), so recentNights()
        // already includes tonight as "last night" by the time recovery is computed.
        assertEquals(com.sleeppulse.app.ui.dashboard.RecoveryTier.OPTIMAL, notifier.notifiedRecoveryResults.single()?.tier)
    }

    @Test
    fun `a repository failure during finalize does not throw`() = runTest {
        val repository = object : com.sleeppulse.app.data.repository.SleepRepository by FakeSleepRepository() {
            override suspend fun disconnectSensor(): NightlySummary? = error("simulated persistence failure")
        }
        val notifier = FakeSleepSummaryNotifier()
        val widgetRefresher = FakeWidgetRefresher()
        val finalizer = SleepSessionFinalizer(repository, notifier, widgetRefresher)

        finalizer.finalize() // must not throw

        assertEquals(0, notifier.notifiedSummaries.size)
        assertEquals(0, widgetRefresher.refreshCallCount)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.notifications.SleepSessionFinalizerTest" -q`
Expected: FAIL — `SleepSessionFinalizer` doesn't exist yet.

- [ ] **Step 3: Implement `SleepSessionFinalizer`**

Create `app/src/main/java/com/sleeppulse/app/notifications/SleepSessionFinalizer.kt`:

```kotlin
package com.sleeppulse.app.notifications

import com.sleeppulse.app.data.repository.SleepRepository
import com.sleeppulse.app.ui.dashboard.RecoveryScoreCalculator
import com.sleeppulse.app.widget.WidgetRefresher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Turns a just-ended sleep session into user-visible feedback: fires the summary
 * notification and refreshes the home-screen widget. Deliberately plain Kotlin (no Android
 * Service dependency) so this orchestration is unit-testable without Robolectric — the
 * actual [android.app.Service] that calls this stays a thin shell.
 */
@Singleton
class SleepSessionFinalizer @Inject constructor(
    private val repository: SleepRepository,
    private val notifier: SleepSummaryNotifier,
    private val widgetRefresher: WidgetRefresher,
) {
    suspend fun finalize() {
        val summary = try {
            repository.disconnectSensor()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Leave the session unfinalized in Room; SleepRepositoryImpl.recoverUnfinalizedSessions()
            // retries it on next app launch rather than losing the night's data.
            return
        } ?: return

        val recovery = RecoveryScoreCalculator.scoreLatest(repository.recentNights().first())
        notifier.notify(summary, recovery)
        widgetRefresher.refresh()
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.notifications.SleepSessionFinalizerTest" -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/notifications/SleepSessionFinalizer.kt app/src/test/java/com/sleeppulse/app/notifications/SleepSessionFinalizerTest.kt
git commit -m "feat: add SleepSessionFinalizer to orchestrate post-session notify + widget refresh"
```

---

### Task 5: Wire `SleepTrackingService` to `SleepSessionFinalizer`

**Files:**
- Modify: `app/src/main/java/com/sleeppulse/app/services/SleepTrackingService.kt`

**Interfaces:**
- Consumes: `SleepSessionFinalizer.finalize()` (Task 4).

No new automated test — this file is an Android `Service` with no test harness in this codebase (see Global Constraints). Verified manually per Step 4 below.

- [ ] **Step 1: Add the injected dependency**

In `SleepTrackingService.kt`, add the import:

```kotlin
import com.sleeppulse.app.notifications.SleepSessionFinalizer
```

Add the field, after the existing `wearDataClient` injection:

```kotlin
    @Inject
    lateinit var sleepSessionFinalizer: SleepSessionFinalizer
```

- [ ] **Step 2: Replace both `disconnectSensor()` call sites**

In `onStartCommand`, replace:

```kotlin
        if (intent?.action == ACTION_STOP_TRACKING) {
            scope.launch {
                repository.disconnectSensor()
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
```

with:

```kotlin
        if (intent?.action == ACTION_STOP_TRACKING) {
            scope.launch {
                sleepSessionFinalizer.finalize()
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
```

In `onDestroy()`, replace:

```kotlin
    override fun onDestroy() {
        scope.launch {
            repository.disconnectSensor()
        }
        noiseMonitor?.stopMonitoring()
        scope.cancel()
        super.onDestroy()
    }
```

with:

```kotlin
    override fun onDestroy() {
        scope.launch {
            sleepSessionFinalizer.finalize()
        }
        noiseMonitor?.stopMonitoring()
        scope.cancel()
        super.onDestroy()
    }
```

- [ ] **Step 3: Build to confirm it compiles**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Manual verification on a device/emulator**

Use the `run` skill (or `./gradlew :app:installDebug`) to install the debug build, start tracking from the Dashboard, let a few readings accumulate (the simulated source produces them continuously), then tap disconnect. Confirm:
- A "Last night: Sleep score ..." notification appears.
- The History screen (or widget, if placed on the home screen) reflects the new night.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/services/SleepTrackingService.kt
git commit -m "refactor: route SleepTrackingService's stop path through SleepSessionFinalizer"
```

---

### Task 6: Simplify `DashboardViewModel` and remove obsolete tests

**Files:**
- Modify: `app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardViewModel.kt`
- Modify: `app/src/test/java/com/sleeppulse/app/ui/dashboard/DashboardViewModelTest.kt`

**Interfaces:**
- No new interfaces — this task removes now-dead code and the tests that only existed to cover it (that coverage now lives in `SleepRepositoryImplTest` and `SleepSessionFinalizerTest` from Tasks 2 and 4).

- [ ] **Step 1: Remove the now-redundant recording/notify tests**

In `DashboardViewModelTest.kt`, delete these five `@Test` functions entirely (including their `@Test` annotation and body):
- `disconnecting after readings accumulated records a nightly summary`
- `disconnecting with no readings does not record a summary`
- `disconnecting after readings fires notifier with the recorded summary`
- `disconnecting with no readings does not fire notifier`
- `disconnecting notifies with recovery computed against the just-recorded summary, not stale state`

Keep every other test in the file (`Start collects connection state...`, `recent readings cap at 40 points`, `ToggleSensorConnection disconnects when connected`, `ToggleSensorConnection connects when not connected`, the wind-down tests, and both `recoveryResult...` tests).

- [ ] **Step 2: Run the (now-failing-to-compile-against-old-code) tests to confirm removal**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.ui.dashboard.DashboardViewModelTest" -q`
Expected: still PASSES for now (the ViewModel hasn't changed yet, only the tests were removed) — this step just confirms the file still compiles after deletion.

- [ ] **Step 3: Simplify `toggleConnection()`**

In `DashboardViewModel.kt`, replace the entire `toggleConnection()` function:

```kotlin
    private fun toggleConnection() {
        viewModelScope.launch {
            if (_state.value.isConnected) {
                if (sessionReadings.isNotEmpty()) {
                    val summary = NightSummaryBuilder.build(sessionReadings.toList(), LocalDate.now())
                    repository.recordNightlySummary(summary)
                    val recovery = computeRecovery(repository.recentNights().first())
                    notifier.notify(summary, recovery)
                    com.sleeppulse.app.widget.SleepPulseWidget.refresh(context)
                    sessionReadings.clear()
                }
                val stopIntent = Intent(context, SleepTrackingService::class.java).apply {
                    action = SleepTrackingService.ACTION_STOP_TRACKING
                }
                context.startService(stopIntent)
            } else {
                val startIntent = Intent(context, SleepTrackingService::class.java)
                context.startForegroundService(startIntent)
            }
        }
    }
```

with:

```kotlin
    private fun toggleConnection() {
        viewModelScope.launch {
            if (_state.value.isConnected) {
                val stopIntent = Intent(context, SleepTrackingService::class.java).apply {
                    action = SleepTrackingService.ACTION_STOP_TRACKING
                }
                context.startService(stopIntent)
            } else {
                val startIntent = Intent(context, SleepTrackingService::class.java)
                context.startForegroundService(startIntent)
            }
        }
    }
```

- [ ] **Step 4: Remove the now-dead `sessionReadings` field and its accumulation**

Replace:

```kotlin
    private val sessionReadings = mutableListOf<SensorReading>()
```

with nothing (delete the line entirely).

In the `start()` function, inside the `repository.liveReadings().collect { reading -> ... }` block, replace:

```kotlin
            repository.liveReadings().collect { reading ->
                sessionReadings.add(reading)
                _state.update { current ->
```

with:

```kotlin
            repository.liveReadings().collect { reading ->
                _state.update { current ->
```

- [ ] **Step 5: Remove now-unused imports**

Remove these two import lines from the top of `DashboardViewModel.kt` (both are only referenced by the code just deleted):

```kotlin
import com.sleeppulse.app.data.NightSummaryBuilder
```

```kotlin
import java.time.LocalDate
```

Leave `import com.sleeppulse.app.data.model.SensorReading` in place — it's still used by `DashboardState`'s `latestReading`/`recentReadings` fields and the `reading` lambda parameter type.

- [ ] **Step 6: Build and run the full test suite**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL (confirms no leftover reference to removed imports/fields)

Run: `./gradlew :app:test -q`
Expected: PASS

- [ ] **Step 7: Run lint**

Run: `./gradlew :app:lintDebug -q`
Expected: PASS (no new warnings/errors)

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/ui/dashboard/DashboardViewModel.kt app/src/test/java/com/sleeppulse/app/ui/dashboard/DashboardViewModelTest.kt
git commit -m "refactor: DashboardViewModel no longer builds/records nightly summaries itself

Recording now happens reliably in SleepRepositoryImpl.disconnectSensor()
regardless of whether the ViewModel is still alive; notify + widget
refresh moved to SleepSessionFinalizer, called from SleepTrackingService."
```

---

### Task 7: Full verification pass

**Files:** none (verification only)

- [ ] **Step 1: Run the full unit test suite**

Run: `./gradlew :app:test -q`
Expected: PASS, all tests including the new ones from Tasks 1, 2, and 4.

- [ ] **Step 2: Run lint**

Run: `./gradlew :app:lintDebug -q`
Expected: PASS

- [ ] **Step 3: Assemble the debug APK**

Run: `./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Manual smoke test**

Use the `run` skill to install and launch the app on a device/emulator. Start tracking, wait for a few simulated readings, disconnect, and confirm the night appears in the History tab (this is the actual bug this plan fixes — previously it would not appear if the process had been killed, and even in the happy path this is the first time `disconnectSensor()` itself is responsible for recording it rather than the ViewModel).

- [ ] **Step 5: Push**

```bash
git push origin master
```

Then check CI: `gh run list --branch master --limit 1` and `gh run watch <run-id> --exit-status`.
