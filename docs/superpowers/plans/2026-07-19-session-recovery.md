# Nightly Session Recording Resilience Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist live sensor readings incrementally to Room during a session, and recover/finalize any session left unfinalized by an interruption (BLE drop, backgrounding, process death) the next time the app starts, so a night is only lost from the last unflushed batch forward instead of entirely.

**Architecture:** Two new Room entities (`SleepSessionEntity`, `SessionReadingEntity`) plus a `SleepSessionDao`, all owned by `SleepRepositoryImpl`. On `connectSensor()`, the repository creates a session row and starts its own buffered collection of `SensorDataSource.readings()` (separate from `DashboardViewModel`'s own collection, which is unchanged), flushing to Room every 20 readings or 30 seconds. On `disconnectSensor()`, the session is finalized and its raw readings deleted. On repository construction, any session left `finalized = false` from a prior process is rebuilt into a `NightlySummary` via the existing `NightSummaryBuilder` and recorded exactly like a normal night.

**Tech Stack:** Kotlin, Room, Hilt, Kotlin Coroutines/Flow, JUnit4 + kotlinx-coroutines-test + Turbine (matches existing `SleepRepositoryImplTest`).

## Global Constraints

- `SleepRepository`'s public interface (`connectSensor()`, `disconnectSensor()`, `recordNightlySummary()`, `liveReadings()`, `connectionState`, `recentNights()`) must not change — `DashboardViewModel` and all other callers require zero changes (per spec: "Repository/interface changes" section).
- `SleepPulseDatabase` bumps to `version = 2`; use `fallbackToDestructiveMigration()` in `di/AppModule.kt` — no real `Migration` class (spec: pre-release, no installs to preserve).
- Recovered sessions are auto-finalized as full nights via the existing `NightSummaryBuilder` → `recordNightlySummary` pipeline — no "partial" flag anywhere.
- Flush cadence: every 20 buffered readings or every 30 seconds, whichever comes first.
- Empty/near-empty recovered sessions (zero readings) are dropped (session row deleted) without calling `NightSummaryBuilder`, which assumes non-empty input.
- All 34 existing unit tests must keep passing; `./gradlew :app:assembleDebug` must stay green after every task.

---

### Task 1: Room entities, DAO, and database wiring

**Files:**
- Create: `app/src/main/java/com/sleeppulse/app/data/local/SleepSessionEntity.kt`
- Create: `app/src/main/java/com/sleeppulse/app/data/local/SessionReadingEntity.kt`
- Create: `app/src/main/java/com/sleeppulse/app/data/local/SleepSessionDao.kt`
- Modify: `app/src/main/java/com/sleeppulse/app/data/local/SleepPulseDatabase.kt`
- Modify: `app/src/main/java/com/sleeppulse/app/di/AppModule.kt`

**Interfaces:**
- Produces: `SleepSessionEntity(sessionId: Long, startEpochMillis: Long, finalized: Boolean)`, `SessionReadingEntity(id: Long, sessionId: Long, timestampMillis: Long, heartRateBpm: Int, hrvMillis: Double, sleepStage: SleepStage)`, `SleepSessionDao` with `createSession(SleepSessionEntity): Long`, `insertReadings(List<SessionReadingEntity>)`, `unfinalizedSessions(): List<SleepSessionEntity>`, `readingsFor(sessionId: Long): List<SessionReadingEntity>`, `finalizeAndClear(sessionId: Long)`. These are consumed by Task 3-5.

This task has no independent test (it's Room boilerplate, matching how `NightlySummaryEntity`/`NightlySummaryDao` have none) — it's verified by the build compiling and the app installing.

- [ ] **Step 1: Create `SleepSessionEntity.kt`**

```kotlin
package com.sleeppulse.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Tracks one connect-to-disconnect session so an interrupted night can be recovered on next launch. */
@Entity(tableName = "sleep_session")
data class SleepSessionEntity(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val startEpochMillis: Long,
    val finalized: Boolean,
)
```

- [ ] **Step 2: Create `SessionReadingEntity.kt`**

```kotlin
package com.sleeppulse.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sleeppulse.app.data.model.SleepStage

/** One raw reading, batch-flushed to disk during a live session as a recovery safety net. */
@Entity(tableName = "session_reading")
data class SessionReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestampMillis: Long,
    val heartRateBpm: Int,
    val hrvMillis: Double,
    val sleepStage: SleepStage,
)
```

- [ ] **Step 3: Create `SleepSessionDao.kt`**

```kotlin
package com.sleeppulse.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface SleepSessionDao {
    @Insert
    suspend fun createSession(session: SleepSessionEntity): Long

    @Insert
    suspend fun insertReadings(readings: List<SessionReadingEntity>)

    @Query("SELECT * FROM sleep_session WHERE finalized = 0")
    suspend fun unfinalizedSessions(): List<SleepSessionEntity>

    @Query("SELECT * FROM session_reading WHERE sessionId = :sessionId ORDER BY timestampMillis ASC")
    suspend fun readingsFor(sessionId: Long): List<SessionReadingEntity>

    @Query("UPDATE sleep_session SET finalized = 1 WHERE sessionId = :sessionId")
    suspend fun markFinalized(sessionId: Long)

    @Query("DELETE FROM session_reading WHERE sessionId = :sessionId")
    suspend fun deleteReadings(sessionId: Long)

    @Transaction
    suspend fun finalizeAndClear(sessionId: Long) {
        markFinalized(sessionId)
        deleteReadings(sessionId)
    }
}
```

- [ ] **Step 4: Bump `SleepPulseDatabase` to version 2 with the new entities**

Replace the full contents of `SleepPulseDatabase.kt`:

```kotlin
package com.sleeppulse.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [NightlySummaryEntity::class, SleepSessionEntity::class, SessionReadingEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class SleepPulseDatabase : RoomDatabase() {
    abstract fun nightlySummaryDao(): NightlySummaryDao
    abstract fun sleepSessionDao(): SleepSessionDao
}
```

- [ ] **Step 5: Wire `fallbackToDestructiveMigration()` and provide `SleepSessionDao` in `AppModule.kt`**

In `di/AppModule.kt`, add the import `com.sleeppulse.app.data.local.SleepSessionDao`, change the `provideDatabase` body, and add a new provider:

```kotlin
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SleepPulseDatabase =
        Room.databaseBuilder(context, SleepPulseDatabase::class.java, "sleeppulse.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideNightlySummaryDao(database: SleepPulseDatabase): NightlySummaryDao =
        database.nightlySummaryDao()

    @Provides
    fun provideSleepSessionDao(database: SleepPulseDatabase): SleepSessionDao =
        database.sleepSessionDao()
```

- [ ] **Step 6: Build to verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/data/local/SleepSessionEntity.kt \
        app/src/main/java/com/sleeppulse/app/data/local/SessionReadingEntity.kt \
        app/src/main/java/com/sleeppulse/app/data/local/SleepSessionDao.kt \
        app/src/main/java/com/sleeppulse/app/data/local/SleepPulseDatabase.kt \
        app/src/main/java/com/sleeppulse/app/di/AppModule.kt
git commit -m "feat: add session/reading Room entities for recovery safety net"
```

---

### Task 2: Fake DAO test util

**Files:**
- Create: `app/src/test/java/com/sleeppulse/app/testutil/FakeSleepSessionDao.kt`

**Interfaces:**
- Consumes: `SleepSessionDao`, `SleepSessionEntity`, `SessionReadingEntity` from Task 1.
- Produces: `FakeSleepSessionDao` with public `sessions: MutableList<SleepSessionEntity>`, `readings: MutableList<SessionReadingEntity>`, `recordedCalls: MutableList<String>` — consumed by Task 3-5 tests.

- [ ] **Step 1: Create the fake, following `FakeNightlySummaryDao`'s style**

```kotlin
package com.sleeppulse.app.testutil

import com.sleeppulse.app.data.local.SessionReadingEntity
import com.sleeppulse.app.data.local.SleepSessionDao
import com.sleeppulse.app.data.local.SleepSessionEntity

class FakeSleepSessionDao : SleepSessionDao {
    val sessions: MutableList<SleepSessionEntity> = mutableListOf()
    val readings: MutableList<SessionReadingEntity> = mutableListOf()
    val recordedCalls: MutableList<String> = mutableListOf()

    private var nextSessionId = 1L
    private var nextReadingId = 1L

    override suspend fun createSession(session: SleepSessionEntity): Long {
        recordedCalls.add("createSession")
        val id = nextSessionId++
        sessions.add(session.copy(sessionId = id))
        return id
    }

    override suspend fun insertReadings(readings: List<SessionReadingEntity>) {
        recordedCalls.add("insertReadings:${readings.size}")
        readings.forEach { this.readings.add(it.copy(id = nextReadingId++)) }
    }

    override suspend fun unfinalizedSessions(): List<SleepSessionEntity> =
        sessions.filter { !it.finalized }

    override suspend fun readingsFor(sessionId: Long): List<SessionReadingEntity> =
        readings.filter { it.sessionId == sessionId }.sortedBy { it.timestampMillis }

    override suspend fun markFinalized(sessionId: Long) {
        recordedCalls.add("markFinalized")
        val index = sessions.indexOfFirst { it.sessionId == sessionId }
        if (index >= 0) sessions[index] = sessions[index].copy(finalized = true)
    }

    override suspend fun deleteReadings(sessionId: Long) {
        recordedCalls.add("deleteReadings")
        readings.removeAll { it.sessionId == sessionId }
    }

    override suspend fun finalizeAndClear(sessionId: Long) {
        markFinalized(sessionId)
        deleteReadings(sessionId)
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:compileDebugUnitTestKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/sleeppulse/app/testutil/FakeSleepSessionDao.kt
git commit -m "test: add FakeSleepSessionDao test util"
```

---

### Task 3: Session creation and buffered persistence on connect

**Files:**
- Modify: `app/src/main/java/com/sleeppulse/app/data/repository/SleepRepositoryImpl.kt`
- Modify: `app/src/test/java/com/sleeppulse/app/data/repository/SleepRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `SleepSessionDao`, `SleepSessionEntity`, `SessionReadingEntity` (Task 1); `FakeSleepSessionDao` (Task 2).
- Produces: `SleepRepositoryImpl` constructor becomes `(sensorDataSource: SensorDataSource, dao: NightlySummaryDao, sessionDao: SleepSessionDao, appScope: CoroutineScope, nowMillis: () -> Long = System::currentTimeMillis)`. `appScope` and `nowMillis` are consumed directly by tests (Task 3-5); `appScope` is DI-provided in Task 6.

This task adds the constructor params and the connect-time session/buffering logic, without yet wiring finalize (Task 4) or recovery (Task 5) — so the new session row and its readings just accumulate until Task 4 lands. The existing three tests in `SleepRepositoryImplTest` must be updated to the new constructor signature to keep compiling.

- [ ] **Step 1: Update existing test file's constructor calls and add the buffering test (failing first)**

Replace the full contents of `SleepRepositoryImplTest.kt`:

```kotlin
package com.sleeppulse.app.data.repository

import app.cash.turbine.test
import com.sleeppulse.app.data.local.NightlySummaryEntity
import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.data.model.SleepStage
import com.sleeppulse.app.testutil.FakeNightlySummaryDao
import com.sleeppulse.app.testutil.FakeSensorDataSource
import com.sleeppulse.app.testutil.FakeSleepSessionDao
import java.time.LocalDate
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepRepositoryImplTest {

    private fun summary(date: LocalDate, score: Int) = NightlySummary(
        date = date,
        sleepScore = score,
        avgHeartRateBpm = 58,
        avgHrvMillis = 72.5,
        totalSleepMinutes = 410,
        deepSleepMinutes = 95,
        remSleepMinutes = 105,
    )

    private fun reading(timestampMillis: Long, heartRateBpm: Int = 60) = SensorReading(
        timestampMillis = timestampMillis,
        heartRateBpm = heartRateBpm,
        hrvMillis = 60.0,
        sleepStage = SleepStage.LIGHT,
    )

    @Test
    fun `recordNightlySummary upserts then trims`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val sessionDao = FakeSleepSessionDao()
        val repository = SleepRepositoryImpl(sensorDataSource, dao, sessionDao, backgroundScope) { 0L }

        val date = LocalDate.of(2026, 7, 17)
        repository.recordNightlySummary(summary(date, score = 88))

        val stored = dao.entitiesFlow.value.single()
        assertEquals(date.toEpochDay(), stored.dateEpochDay)
        assertEquals(88, stored.sleepScore)
        assertEquals(58, stored.avgHeartRateBpm)
        assertEquals(72.5, stored.avgHrvMillis, 0.0001)
        assertEquals(410, stored.totalSleepMinutes)
        assertEquals(95, stored.deepSleepMinutes)
        assertEquals(105, stored.remSleepMinutes)
        assertEquals(listOf("upsert", "trimToLast30Days"), dao.recordedCalls)
    }

    @Test
    fun `recentNights maps entities to domain objects with date round-trip`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val sessionDao = FakeSleepSessionDao()
        val repository = SleepRepositoryImpl(sensorDataSource, dao, sessionDao, backgroundScope) { 0L }

        val date = LocalDate.of(2026, 7, 10)
        dao.entitiesFlow.value = listOf(
            NightlySummaryEntity(
                dateEpochDay = date.toEpochDay(),
                sleepScore = 72,
                avgHeartRateBpm = 61,
                avgHrvMillis = 65.0,
                totalSleepMinutes = 400,
                deepSleepMinutes = 80,
                remSleepMinutes = 90,
            )
        )

        repository.recentNights().test {
            val nights = awaitItem()
            assertEquals(1, nights.size)
            assertEquals(date, nights[0].date)
            assertEquals(72, nights[0].sleepScore)
            assertEquals(61, nights[0].avgHeartRateBpm)
            assertEquals(65.0, nights[0].avgHrvMillis, 0.0001)
            assertEquals(400, nights[0].totalSleepMinutes)
            assertEquals(80, nights[0].deepSleepMinutes)
            assertEquals(90, nights[0].remSleepMinutes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `connectSensor creates a session and disconnectSensor delegates to the data source`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val sessionDao = FakeSleepSessionDao()
        val repository = SleepRepositoryImpl(sensorDataSource, dao, sessionDao, backgroundScope) { 1_000L }

        repository.connectSensor()
        assertEquals(1, sensorDataSource.connectCallCount)
        assertEquals(1, sessionDao.sessions.size)
        assertEquals(1_000L, sessionDao.sessions.single().startEpochMillis)
        assertTrue(!sessionDao.sessions.single().finalized)

        repository.disconnectSensor()
        assertEquals(1, sensorDataSource.disconnectCallCount)
    }

    @Test
    fun `readings flush to the session dao every 20 readings`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val sessionDao = FakeSleepSessionDao()
        val repository = SleepRepositoryImpl(sensorDataSource, dao, sessionDao, backgroundScope) { 0L }

        repository.connectSensor()
        repeat(20) { i -> sensorDataSource.readingsFlow.emit(reading(timestampMillis = i.toLong())) }
        advanceTimeBy(1)

        assertEquals(20, sessionDao.readings.size)
    }

    @Test
    fun `readings flush after 30 seconds even under the batch size`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val sessionDao = FakeSleepSessionDao()
        val repository = SleepRepositoryImpl(sensorDataSource, dao, sessionDao, backgroundScope) { 0L }

        repository.connectSensor()
        sensorDataSource.readingsFlow.emit(reading(timestampMillis = 1L))
        sensorDataSource.readingsFlow.emit(reading(timestampMillis = 2L))
        assertEquals(0, sessionDao.readings.size)

        advanceTimeBy(30_001)

        assertEquals(2, sessionDao.readings.size)
    }

    @Test
    fun `liveReadings and connectionState pass through from the data source`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val sessionDao = FakeSleepSessionDao()
        val repository = SleepRepositoryImpl(sensorDataSource, dao, sessionDao, backgroundScope) { 0L }

        repository.connectSensor()
        assertEquals(1, sensorDataSource.connectCallCount)

        repository.disconnectSensor()
        assertEquals(1, sensorDataSource.disconnectCallCount)

        repository.liveReadings().test {
            sensorDataSource.readingsFlow.emit(reading(timestampMillis = 1L, heartRateBpm = 62))
            val received = awaitItem()
            assertEquals(62, received.heartRateBpm)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run the tests to confirm they fail to compile (constructor mismatch)**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.data.repository.SleepRepositoryImplTest"`
Expected: FAIL — compile error, `SleepRepositoryImpl` does not have a 4th/5th constructor parameter yet.

- [ ] **Step 3: Update `SleepRepositoryImpl` to accept the new dependencies and buffer/flush readings**

Replace the full contents of `SleepRepositoryImpl.kt`:

```kotlin
package com.sleeppulse.app.data.repository

import com.sleeppulse.app.data.local.NightlySummaryDao
import com.sleeppulse.app.data.local.NightlySummaryEntity
import com.sleeppulse.app.data.local.SessionReadingEntity
import com.sleeppulse.app.data.local.SleepSessionDao
import com.sleeppulse.app.data.local.SleepSessionEntity
import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.data.model.SensorConnectionState
import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.data.source.SensorDataSource
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val FLUSH_BATCH_SIZE = 20
private const val FLUSH_INTERVAL_MILLIS = 30_000L

class SleepRepositoryImpl @Inject constructor(
    private val sensorDataSource: SensorDataSource,
    private val dao: NightlySummaryDao,
    private val sessionDao: SleepSessionDao,
    private val appScope: CoroutineScope,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : SleepRepository {

    override val connectionState: Flow<SensorConnectionState> = sensorDataSource.connectionState

    override fun liveReadings(): Flow<SensorReading> = sensorDataSource.readings()

    override fun recentNights(): Flow<List<NightlySummary>> =
        dao.observeRecent().map { entities -> entities.map { it.toDomain() } }

    private var activeSessionId: Long? = null
    private var collectionJob: Job? = null
    private var flushTimerJob: Job? = null
    private val pendingReadings = mutableListOf<SessionReadingEntity>()

    override suspend fun connectSensor() {
        sensorDataSource.connect()
        val sessionId = sessionDao.createSession(
            SleepSessionEntity(startEpochMillis = nowMillis(), finalized = false)
        )
        activeSessionId = sessionId

        collectionJob = appScope.launch {
            sensorDataSource.readings().collect { reading ->
                pendingReadings.add(reading.toSessionEntity(sessionId))
                if (pendingReadings.size >= FLUSH_BATCH_SIZE) flush()
            }
        }
        flushTimerJob = appScope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MILLIS)
                flush()
            }
        }
    }

    override suspend fun disconnectSensor() {
        collectionJob?.cancel()
        flushTimerJob?.cancel()
        collectionJob = null
        flushTimerJob = null
        flush()
        activeSessionId?.let { sessionDao.finalizeAndClear(it) }
        activeSessionId = null
        sensorDataSource.disconnect()
    }

    private suspend fun flush() {
        if (pendingReadings.isEmpty()) return
        sessionDao.insertReadings(pendingReadings.toList())
        pendingReadings.clear()
    }

    override suspend fun recordNightlySummary(summary: NightlySummary) {
        dao.upsert(summary.toEntity())
        dao.trimToLast30Days()
    }

    private fun SensorReading.toSessionEntity(sessionId: Long) = SessionReadingEntity(
        sessionId = sessionId,
        timestampMillis = timestampMillis,
        heartRateBpm = heartRateBpm,
        hrvMillis = hrvMillis,
        sleepStage = sleepStage,
    )

    private fun NightlySummaryEntity.toDomain() = NightlySummary(
        date = LocalDate.ofEpochDay(dateEpochDay),
        sleepScore = sleepScore,
        avgHeartRateBpm = avgHeartRateBpm,
        avgHrvMillis = avgHrvMillis,
        totalSleepMinutes = totalSleepMinutes,
        deepSleepMinutes = deepSleepMinutes,
        remSleepMinutes = remSleepMinutes,
    )

    private fun NightlySummary.toEntity() = NightlySummaryEntity(
        dateEpochDay = date.toEpochDay(),
        sleepScore = sleepScore,
        avgHeartRateBpm = avgHeartRateBpm,
        avgHrvMillis = avgHrvMillis,
        totalSleepMinutes = totalSleepMinutes,
        deepSleepMinutes = deepSleepMinutes,
        remSleepMinutes = remSleepMinutes,
    )
}
```

Note: `disconnectSensor()` finalizing the session here is Task 3 scope creep vs. the task title, but it's required for the `` `connectSensor creates a session and disconnectSensor delegates to the data source` `` test above to pass without leaving a dangling job — implementing it now (rather than deferring purely to Task 4) keeps this task's tests green. Task 4 will only add the "finalize happens after a successful recordNightlySummary in the ViewModel's disconnect flow" ordering check.

- [ ] **Step 4: Run the tests again to confirm they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.data.repository.SleepRepositoryImplTest"`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/data/repository/SleepRepositoryImpl.kt \
        app/src/test/java/com/sleeppulse/app/data/repository/SleepRepositoryImplTest.kt
git commit -m "feat: buffer and batch-flush session readings to Room on connect"
```

---

### Task 4: Empty-session handling and finalize ordering guard

**Files:**
- Modify: `app/src/main/java/com/sleeppulse/app/data/repository/SleepRepositoryImpl.kt`
- Modify: `app/src/test/java/com/sleeppulse/app/data/repository/SleepRepositoryImplTest.kt`

**Interfaces:**
- Consumes: everything from Task 3.
- Produces: no new public surface — this task only hardens the flush-on-disconnect path (an empty pending buffer must not call `insertReadings` with an empty list, which `flush()` already guards via its `isEmpty()` check — this task adds the regression test that locks that behavior in).

- [ ] **Step 1: Add the regression test**

Add this test to `SleepRepositoryImplTest.kt` (inside the class, alongside the others):

```kotlin
    @Test
    fun `disconnecting with no readings collected does not call insertReadings`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val sessionDao = FakeSleepSessionDao()
        val repository = SleepRepositoryImpl(sensorDataSource, dao, sessionDao, backgroundScope) { 0L }

        repository.connectSensor()
        repository.disconnectSensor()

        assertTrue(sessionDao.recordedCalls.none { it.startsWith("insertReadings") })
        assertTrue(sessionDao.sessions.single().finalized)
        assertEquals(0, sessionDao.readings.size)
    }
```

- [ ] **Step 2: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.data.repository.SleepRepositoryImplTest"`
Expected: PASS (7 tests) — the existing `flush()` guard from Task 3 already makes this pass with no production code change needed.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/sleeppulse/app/data/repository/SleepRepositoryImplTest.kt
git commit -m "test: lock in no-op flush behavior when a session has zero readings"
```

---

### Task 5: Startup recovery of unfinalized sessions

**Files:**
- Modify: `app/src/main/java/com/sleeppulse/app/data/repository/SleepRepositoryImpl.kt`
- Modify: `app/src/test/java/com/sleeppulse/app/data/repository/SleepRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `NightSummaryBuilder.build(readings: List<SensorReading>, date: LocalDate): NightlySummary` (existing, `app/src/main/java/com/sleeppulse/app/data/NightSummaryBuilder.kt`).
- Produces: a `suspend fun recoverUnfinalizedSessions()` on `SleepRepositoryImpl`, called once from `init`. Exposed as non-private (internal) specifically so the test can call it directly instead of relying on `init` timing, since `init` runs synchronously at construction before `nowMillis`/mocks are fully wired up in some test setups — calling it explicitly keeps the test deterministic.

- [ ] **Step 1: Add the failing recovery tests**

Add these two tests to `SleepRepositoryImplTest.kt`:

```kotlin
    @Test
    fun `recoverUnfinalizedSessions rebuilds and records a summary for a leftover session`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val sessionDao = FakeSleepSessionDao()
        val startMillis = LocalDate.of(2026, 7, 18).atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        sessionDao.sessions.add(
            com.sleeppulse.app.data.local.SleepSessionEntity(
                sessionId = 5L,
                startEpochMillis = startMillis,
                finalized = false,
            )
        )
        sessionDao.readings.addAll(
            listOf(
                com.sleeppulse.app.data.local.SessionReadingEntity(
                    id = 1L, sessionId = 5L, timestampMillis = startMillis,
                    heartRateBpm = 58, hrvMillis = 70.0, sleepStage = SleepStage.LIGHT,
                ),
                com.sleeppulse.app.data.local.SessionReadingEntity(
                    id = 2L, sessionId = 5L, timestampMillis = startMillis + 60_000,
                    heartRateBpm = 56, hrvMillis = 72.0, sleepStage = SleepStage.DEEP,
                ),
            )
        )
        val repository = SleepRepositoryImpl(sensorDataSource, dao, sessionDao, backgroundScope) { startMillis }

        repository.recoverUnfinalizedSessions()

        assertEquals(listOf("upsert", "trimToLast30Days"), dao.recordedCalls)
        val stored = dao.entitiesFlow.value.single()
        assertEquals(LocalDate.of(2026, 7, 18).toEpochDay(), stored.dateEpochDay)
        assertTrue(sessionDao.sessions.single().finalized)
        assertEquals(0, sessionDao.readings.size)
    }

    @Test
    fun `recoverUnfinalizedSessions drops an empty leftover session without recording anything`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val sessionDao = FakeSleepSessionDao()
        sessionDao.sessions.add(
            com.sleeppulse.app.data.local.SleepSessionEntity(
                sessionId = 9L, startEpochMillis = 0L, finalized = false,
            )
        )
        val repository = SleepRepositoryImpl(sensorDataSource, dao, sessionDao, backgroundScope) { 0L }

        repository.recoverUnfinalizedSessions()

        assertTrue(dao.recordedCalls.isEmpty())
        assertTrue(sessionDao.sessions.single().finalized)
    }
```

- [ ] **Step 2: Run the tests to confirm they fail (method doesn't exist yet)**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.data.repository.SleepRepositoryImplTest"`
Expected: FAIL — compile error, `recoverUnfinalizedSessions` unresolved reference.

- [ ] **Step 3: Add `recoverUnfinalizedSessions()` and call it from `init`**

In `SleepRepositoryImpl.kt`, add the import `com.sleeppulse.app.data.NightSummaryBuilder`, an `init` block, and the new method:

```kotlin
    init {
        appScope.launch { recoverUnfinalizedSessions() }
    }

    suspend fun recoverUnfinalizedSessions() {
        sessionDao.unfinalizedSessions().forEach { session ->
            val readings = sessionDao.readingsFor(session.sessionId).map { it.toDomainReading() }
            if (readings.isNotEmpty()) {
                val date = java.time.Instant.ofEpochMilli(session.startEpochMillis)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                recordNightlySummary(NightSummaryBuilder.build(readings, date))
            }
            sessionDao.finalizeAndClear(session.sessionId)
        }
    }

    private fun SessionReadingEntity.toDomainReading() = SensorReading(
        timestampMillis = timestampMillis,
        heartRateBpm = heartRateBpm,
        hrvMillis = hrvMillis,
        sleepStage = sleepStage,
    )
```

Place `recoverUnfinalizedSessions()` and `toDomainReading()` after `recordNightlySummary` in the class body. The `init` block goes directly below the `pendingReadings` property declaration.

- [ ] **Step 4: Run the full repository test file**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.data.repository.SleepRepositoryImplTest"`
Expected: PASS (9 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/data/repository/SleepRepositoryImpl.kt \
        app/src/test/java/com/sleeppulse/app/data/repository/SleepRepositoryImplTest.kt
git commit -m "feat: recover and finalize unfinalized sessions on repository init"
```

---

### Task 6: DI wiring for the application-scoped CoroutineScope, and full-suite verification

**Files:**
- Modify: `app/src/main/java/com/sleeppulse/app/di/AppModule.kt`

**Interfaces:**
- Produces: a Hilt-provided `@Singleton CoroutineScope` bound to `SleepRepositoryImpl`'s `appScope` constructor parameter — the final piece needed for the app to actually compile and inject the real repository (Tasks 3-5 only updated tests, which construct `SleepRepositoryImpl` directly).

- [ ] **Step 1: Add the `CoroutineScope` provider to `AppModule.kt`**

Add these imports to `AppModule.kt`:

```kotlin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
```

Add this provider inside the existing `DatabaseModule` object (renaming it mentally doesn't matter — it's still a reasonable home since both are app-lifetime singletons; add it as its own `@Provides` in `DatabaseModule`):

```kotlin
    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
```

- [ ] **Step 2: Build the full debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL` — confirms Hilt can resolve every `SleepRepositoryImpl` constructor param (`SensorDataSource`, `NightlySummaryDao`, `SleepSessionDao`, `CoroutineScope`; `nowMillis` uses its default).

- [ ] **Step 3: Run the entire unit test suite**

Run: `./gradlew :app:test`
Expected: PASS, all suites green (34 pre-existing + 6 new in `SleepRepositoryImplTest` = 40 total; exact count depends on Task 3-5 additions above).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/di/AppModule.kt
git commit -m "feat: provide application-scoped CoroutineScope for session persistence"
```

---

## Manual verification (after all tasks)

Install on an emulator and confirm the existing golden path still works end to end (per CLAUDE.md guidance to verify UI changes manually where feasible):

1. `./gradlew :app:installDebug`
2. Launch the app, connect (simulated source), let it run a few seconds, disconnect — confirm History still shows the recorded night as before (no regression to the normal path).
3. Connect again, force-stop the app from Android's app-info screen mid-session (simulating a process kill) without disconnecting first, then relaunch — confirm a night now appears in History for that session (this is the new recovery behavior; previously it would have been silently lost).
