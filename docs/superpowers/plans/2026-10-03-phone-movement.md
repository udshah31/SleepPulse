# Phone-on-the-bed Movement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In BLE mode, optionally feed a real movement score from the phone's accelerometer into sleep-stage prediction, behind a default-off Settings toggle.

**Architecture:** A pure `MovementScoreCalculator` scores a window of accelerometer samples; an Android-bound `PhoneMotionMonitor`, run by `SleepTrackingService`, publishes the score every 5 s to a singleton `PhoneMovement` holder; `BleSensorDataSource` reads the holder when it predicts each stage. One new boolean setting gates it. `SensorReading` and Room do not change.

**Tech Stack:** Kotlin, Android `SensorManager`, Hilt, Jetpack Compose, JUnit4 with mockito-kotlin (existing test style).

**Spec:** `docs/superpowers/specs/2026-10-03-phone-movement-design.md`

## Global Constraints

- All paths are relative to the repository worktree root; package root `com.sleeppulse.app` is `app/src/main/java/com/sleeppulse/app` (tests: `app/src/test/java/com/sleeppulse/app`).
- Movement score: deviation = `|‖(x,y,z)‖ − 9.81|` m/s²; score = `clamp(max deviation / 2.0, 0, 1)`; window `30_000` ms; the predictor's existing AWAKE rule (`movement > 0.8`) is NOT changed.
- Publish every `5_000` ms; accelerometer at `SENSOR_DELAY_NORMAL` with a `5_000_000` µs max report latency; `PhoneMovement` treats a value older than `60_000` ms as unknown (null).
- The monitor runs only when `phoneMovementEnabled` is true AND `dataSourceMode == BLE`; the setting defaults to `false`; no new manifest permission.
- Unit tests: `./gradlew :app:testDebugUnitTest --tests '<fqcn>'` for one class; full check `./gradlew :app:test lint :app:assembleDebug`.
- Settings pattern: follow `amoledBlack` exactly (Settings field → repository `StateFlow` + setter + `save()` → `PrefsSettingsStore` key → ViewModel collector + intent → Screen row).
- No logic in Composables (MVI). Hand-written fakes/simple objects over mocks where possible.
- Git: stage files by exact path only (never `git add .`/`-A`); never stage `.claude/settings.local.json`, `CLAUDE.local.md`, `.env*`, `local.properties`, `.superpowers/`. Commit messages end with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Do not push.
- Out of scope: a strap's own accelerometer, persisting movement per reading, restlessness metrics, Simulated-mode movement, threshold calibration UI.

## Review Focus

- A phone with no accelerometer: `PhoneMotionMonitor.start` returns false, the service does not crash, movement stays null (Task 4).
- Toggle on but Simulated mode: no monitor is registered (Task 4).
- A monitor that stops or dies must not leave an old score behind: `PhoneMovement` returns null after 60 s (Task 2 test).
- Non-finite accelerometer values (NaN/Inf) must not poison the score (Task 1 test).
- `onStartCommand` runs more than once (repeated Connect / sticky restart): the accelerometer must not be registered twice (Task 4 guard).
- Flipping the toggle mid-session only takes effect at the next session start; this must be stated in the Settings subtitle or docs rather than silently ignored (Task 3/4).

---

### Task 1: Movement score (pure)

**Files:**
- Create: `app/src/main/java/com/sleeppulse/app/tracking/MovementScoreCalculator.kt`
- Test: `app/src/test/java/com/sleeppulse/app/tracking/MovementScoreCalculatorTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `data class AccelSample(val timestampMillis: Long, val x: Float, val y: Float, val z: Float)`
  - `object MovementScoreCalculator { const val WINDOW_MILLIS = 30_000L; const val FULL_SCALE_DEVIATION = 2.0f; fun score(samples: List<AccelSample>): Float? }`

- [ ] **Step 1: Write the failing test**

`MovementScoreCalculatorTest.kt`:

```kotlin
package com.sleeppulse.app.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementScoreCalculatorTest {

    private fun s(x: Float, y: Float, z: Float, t: Long = 0L) = AccelSample(t, x, y, z)
    private fun flat(magnitude: Float) = s(0f, 0f, magnitude)

    @Test
    fun `a still phone scores about zero`() {
        assertEquals(0f, MovementScoreCalculator.score(listOf(flat(9.81f)))!!, 0.001f)
    }

    @Test
    fun `breathing-sized noise stays below 0_1`() {
        val noise = listOf(flat(9.83f), flat(9.79f), flat(9.84f))
        assertTrue(MovementScoreCalculator.score(noise)!! < 0.1f)
    }

    @Test
    fun `a 1 m per s2 bump scores 0_5`() {
        assertEquals(0.5f, MovementScoreCalculator.score(listOf(flat(10.81f)))!!, 0.001f)
    }

    @Test
    fun `a 3 m per s2 spike scores 1 and is above the AWAKE threshold`() {
        val score = MovementScoreCalculator.score(listOf(flat(12.81f)))!!
        assertEquals(1f, score, 0.001f)
        assertTrue(score > 0.8f)
    }

    @Test
    fun `the peak in the window counts, not the latest sample`() {
        val score = MovementScoreCalculator.score(listOf(flat(12.81f), flat(9.81f)))!!
        assertEquals(1f, score, 0.001f)
    }

    @Test
    fun `only the change from gravity counts, not the phone's orientation`() {
        // Same 10.81 magnitude along three different orientations.
        val along = listOf(s(10.81f, 0f, 0f), s(0f, 10.81f, 0f), s(6f, 6f, 6.6974f))
        MovementScoreCalculator.score(along)!!.let { assertEquals(0.5f, it, 0.01f) }
        // A tilted but still phone: magnitude is gravity.
        // (6, 6, 4.9231) has magnitude 9.81: 6² + 6² + 4.9231² = 96.236 = 9.81².
        assertEquals(0f, MovementScoreCalculator.score(listOf(s(6f, 6f, 4.9231f)))!!, 0.01f)
    }

    @Test
    fun `no samples means unknown`() {
        assertNull(MovementScoreCalculator.score(emptyList()))
    }

    @Test
    fun `non-finite samples are ignored and cannot poison the score`() {
        val withNan = listOf(s(Float.NaN, 0f, 0f), s(0f, Float.POSITIVE_INFINITY, 0f), flat(9.81f))
        assertEquals(0f, MovementScoreCalculator.score(withNan)!!, 0.001f)
        assertNull(MovementScoreCalculator.score(listOf(s(Float.NaN, 0f, 0f))))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.sleeppulse.app.tracking.MovementScoreCalculatorTest'`
Expected: FAIL (compilation error, `AccelSample` / `MovementScoreCalculator` unresolved).

- [ ] **Step 3: Implement**

`MovementScoreCalculator.kt`:

```kotlin
package com.sleeppulse.app.tracking

import kotlin.math.abs
import kotlin.math.sqrt

/** One accelerometer reading in m/s², stamped with when it happened (epoch millis). */
data class AccelSample(val timestampMillis: Long, val x: Float, val y: Float, val z: Float)

/**
 * Turns a window of phone accelerometer samples into a 0..1 movement score for
 * [SleepStagePredictor]. Only the change in acceleration magnitude from gravity counts, so the phone's
 * orientation doesn't matter. The caller (see [PhoneMotionMonitor]) keeps the window; this only scores it.
 *
 * ponytail: FULL_SCALE_DEVIATION and the window are first guesses made without real sleep data — a peak above
 * 1.6 m/s² (full scale 2.0 × the predictor's 0.8 AWAKE threshold) reads as awake. Tune against real nights.
 */
object MovementScoreCalculator {
    const val WINDOW_MILLIS = 30_000L
    const val FULL_SCALE_DEVIATION = 2.0f
    private const val GRAVITY = 9.81f

    /** Null when there is nothing finite to score. */
    fun score(samples: List<AccelSample>): Float? {
        var maxDeviation: Float? = null
        for (s in samples) {
            val magnitude = sqrt(s.x * s.x + s.y * s.y + s.z * s.z)
            if (!magnitude.isFinite()) continue
            val deviation = abs(magnitude - GRAVITY)
            if (maxDeviation == null || deviation > maxDeviation) maxDeviation = deviation
        }
        return maxDeviation?.let { (it / FULL_SCALE_DEVIATION).coerceIn(0f, 1f) }
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.sleeppulse.app.tracking.MovementScoreCalculatorTest'`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/tracking/MovementScoreCalculator.kt app/src/test/java/com/sleeppulse/app/tracking/MovementScoreCalculatorTest.kt
git commit -m "feat: add MovementScoreCalculator (accelerometer window -> 0..1 score)"
```

---

### Task 2: Shared movement holder

**Files:**
- Create: `app/src/main/java/com/sleeppulse/app/tracking/PhoneMovement.kt`
- Test: `app/src/test/java/com/sleeppulse/app/tracking/PhoneMovementTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `@Singleton class PhoneMovement @Inject constructor() { fun publish(score: Float?, nowMillis: Long); fun current(nowMillis: Long): Float?; companion object { const val STALE_AFTER_MILLIS = 60_000L } }`

- [ ] **Step 1: Write the failing test**

`PhoneMovementTest.kt`:

```kotlin
package com.sleeppulse.app.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneMovementTest {

    private val movement = PhoneMovement()

    @Test
    fun `nothing published means unknown`() {
        assertNull(movement.current(nowMillis = 1_000L))
    }

    @Test
    fun `a fresh score is returned`() {
        movement.publish(0.4f, nowMillis = 10_000L)
        assertEquals(0.4f, movement.current(nowMillis = 12_000L)!!, 0f)
    }

    @Test
    fun `a score exactly at the limit is still fresh, one millisecond later it is stale`() {
        movement.publish(0.4f, nowMillis = 0L)
        assertEquals(0.4f, movement.current(nowMillis = PhoneMovement.STALE_AFTER_MILLIS)!!, 0f)
        assertNull(movement.current(nowMillis = PhoneMovement.STALE_AFTER_MILLIS + 1))
    }

    @Test
    fun `publishing null makes it unknown immediately`() {
        movement.publish(0.9f, nowMillis = 0L)
        movement.publish(null, nowMillis = 1_000L)
        assertNull(movement.current(nowMillis = 1_500L))
    }

    @Test
    fun `a newer score replaces the older one`() {
        movement.publish(0.9f, nowMillis = 0L)
        movement.publish(0.1f, nowMillis = 5_000L)
        assertEquals(0.1f, movement.current(nowMillis = 6_000L)!!, 0f)
    }

    @Test
    fun `a clock that moved backwards is not treated as stale`() {
        movement.publish(0.5f, nowMillis = 100_000L)
        assertEquals(0.5f, movement.current(nowMillis = 50_000L)!!, 0f)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.sleeppulse.app.tracking.PhoneMovementTest'`
Expected: FAIL (compilation error, `PhoneMovement` unresolved).

- [ ] **Step 3: Implement**

`PhoneMovement.kt`:

```kotlin
package com.sleeppulse.app.tracking

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The latest phone-motion score, written by [com.sleeppulse.app.services.SleepTrackingService] (through
 * [PhoneMotionMonitor]) and read by the BLE data source. A value older than [STALE_AFTER_MILLIS] reads as
 * unknown, so a monitor that died can never leave an old score behind.
 */
@Singleton
class PhoneMovement @Inject constructor() {

    private class Published(val score: Float?, val atMillis: Long)

    @Volatile private var last: Published? = null

    fun publish(score: Float?, nowMillis: Long) {
        last = Published(score, nowMillis)
    }

    fun current(nowMillis: Long): Float? {
        val p = last ?: return null
        return if (nowMillis - p.atMillis > STALE_AFTER_MILLIS) null else p.score
    }

    companion object {
        const val STALE_AFTER_MILLIS = 60_000L
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.sleeppulse.app.tracking.PhoneMovementTest'`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/tracking/PhoneMovement.kt app/src/test/java/com/sleeppulse/app/tracking/PhoneMovementTest.kt
git commit -m "feat: add PhoneMovement holder that reads as unknown when stale"
```

---

### Task 3: The setting and its toggle

**Files:**
- Modify: `app/src/main/java/com/sleeppulse/app/data/repository/SettingsRepository.kt` (Settings, repository, store)
- Modify: `app/src/main/java/com/sleeppulse/app/ui/settings/SettingsContract.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`
- Test: `app/src/test/java/com/sleeppulse/app/data/repository/SettingsRepositoryTest.kt`, `app/src/test/java/com/sleeppulse/app/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `Settings.phoneMovementEnabled: Boolean = false`; `SettingsRepository.phoneMovementEnabled: StateFlow<Boolean>` and `setPhoneMovementEnabled(enabled: Boolean)`; `SettingsIntent.SetPhoneMovementEnabled(val enabled: Boolean)`; `SettingsState.phoneMovementEnabled: Boolean = false`.

- [ ] **Step 1: Write the failing tests**

In `SettingsRepositoryTest.kt`: in `defaults match documented values` add `assertFalse(repository.phoneMovementEnabled.value)`; add the test

```kotlin
    @Test
    fun `setPhoneMovementEnabled toggles independently of other settings`() {
        repository.setTargetBedtime(23, 15)
        repository.setPhoneMovementEnabled(true)
        assertEquals(true, repository.phoneMovementEnabled.value)
        assertEquals(23, repository.targetBedtimeHour.value)
    }
```

and in `every setting survives a restart through the store` add `setPhoneMovementEnabled(true)` inside the `apply { ... }` block and `assertEquals(true, restarted.phoneMovementEnabled.value)` after the other asserts.

In `SettingsViewModelTest.kt` add:

```kotlin
    @Test
    fun `SetPhoneMovementEnabled updates only phoneMovementEnabled and defaults to off`() = runTest {
        val viewModel = SettingsViewModel(mockContext, SettingsRepository(), mockWindDownScheduler, mockSmartAlarmScheduler)
        advanceUntilIdle()
        assertEquals(false, viewModel.state.value.phoneMovementEnabled)

        viewModel.onIntent(SettingsIntent.SetPhoneMovementEnabled(true))
        advanceUntilIdle()

        assertEquals(true, viewModel.state.value.phoneMovementEnabled)
        assertEquals(DataSourceMode.SIMULATED, viewModel.state.value.dataSourceMode)
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.sleeppulse.app.data.repository.SettingsRepositoryTest' --tests 'com.sleeppulse.app.ui.settings.SettingsViewModelTest'`
Expected: FAIL (compilation errors on the missing members).

- [ ] **Step 3: Implement the setting**

`SettingsRepository.kt`:
- in `data class Settings(...)`, after `bleDeviceLabel`: `/** Use the phone's accelerometer as the BLE movement source (phone on the bed). Applies from the next session. */ val phoneMovementEnabled: Boolean = false,`
- after `_bleDeviceLabel`/`bleDeviceLabel`:

```kotlin
    private val _phoneMovementEnabled = MutableStateFlow(initial.phoneMovementEnabled)
    val phoneMovementEnabled: StateFlow<Boolean> = _phoneMovementEnabled.asStateFlow()
```
- after `setAmoledBlack`:

```kotlin
    fun setPhoneMovementEnabled(enabled: Boolean) {
        _phoneMovementEnabled.value = enabled
        save()
    }
```
- in `save()`: `phoneMovementEnabled = _phoneMovementEnabled.value,` (after `bleDeviceLabel`)
- `PrefsSettingsStore.load()`: `phoneMovementEnabled = prefs.getBoolean("phone_movement_enabled", d.phoneMovementEnabled),`; `save()`: `.putBoolean("phone_movement_enabled", settings.phoneMovementEnabled)`.

`SettingsContract.kt`: add `data class SetPhoneMovementEnabled(val enabled: Boolean) : SettingsIntent()` and `val phoneMovementEnabled: Boolean = false,` to `SettingsState`.

`SettingsViewModel.kt`: in `init`, after the amoled collector:

```kotlin
        viewModelScope.launch {
            repository.phoneMovementEnabled.collect { enabled ->
                _state.update { it.copy(phoneMovementEnabled = enabled) }
            }
        }
```
and in `onIntent`: `is SettingsIntent.SetPhoneMovementEnabled -> repository.setPhoneMovementEnabled(intent.enabled)`.

`SettingsScreen.kt`:
- add a private composable that holds the switch colors currently inlined for AMOLED, and use it in both places:

```kotlin
@Composable
private fun settingsSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = CalmNightBackground,
    checkedTrackColor = SleepIndigo,
    checkedBorderColor = SleepIndigo,
    uncheckedThumbColor = CalmNightTextSecondary,
    uncheckedTrackColor = CalmNightSurfaceDim,
    uncheckedBorderColor = CalmNightTextSecondary.copy(alpha = 0.35f),
)
```
  and change the AMOLED `Switch(... colors = SwitchDefaults.colors(...))` to `colors = settingsSwitchColors()` (same values, no visual change).
- inside `if (state.dataSourceMode == DataSourceMode.BLE) { ... }` (after the "Selected: …" text) add:

```kotlin
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Phone on the bed", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "Uses the phone's motion sensor to spot waking; applies from the next session",
                                style = MaterialTheme.typography.bodySmall,
                                color = CalmNightTextSecondary,
                            )
                        }
                        Switch(
                            checked = state.phoneMovementEnabled,
                            onCheckedChange = { viewModel.onIntent(SettingsIntent.SetPhoneMovementEnabled(it)) },
                            colors = settingsSwitchColors(),
                        )
                    }
```

- [ ] **Step 4: Run them to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.sleeppulse.app.data.repository.SettingsRepositoryTest' --tests 'com.sleeppulse.app.ui.settings.SettingsViewModelTest'`
Expected: PASS. Then `./gradlew :app:compileDebugKotlin` must succeed (the Screen changes).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/data/repository/SettingsRepository.kt app/src/main/java/com/sleeppulse/app/ui/settings/SettingsContract.kt app/src/main/java/com/sleeppulse/app/ui/settings/SettingsViewModel.kt app/src/main/java/com/sleeppulse/app/ui/settings/SettingsScreen.kt app/src/test/java/com/sleeppulse/app/data/repository/SettingsRepositoryTest.kt app/src/test/java/com/sleeppulse/app/ui/settings/SettingsViewModelTest.kt
git commit -m "feat: add the phoneMovementEnabled setting and its Settings toggle (BLE mode only)"
```

---

### Task 4: Monitor, service and BLE wiring

**Files:**
- Create: `app/src/main/java/com/sleeppulse/app/tracking/PhoneMotionMonitor.kt`
- Modify: `app/src/main/java/com/sleeppulse/app/services/SleepTrackingService.kt`
- Modify: `app/src/main/java/com/sleeppulse/app/data/source/BleSensorDataSource.kt`
- Modify: `app/src/test/java/com/sleeppulse/app/data/source/SensorSourceManagerTest.kt` (3 constructor calls)
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes: `MovementScoreCalculator`, `AccelSample` (Task 1); `PhoneMovement` (Task 2); `SettingsRepository.phoneMovementEnabled` and `dataSourceMode` (Task 3).
- Produces: `class PhoneMotionMonitor(sensorManager: SensorManager?, nowMillis: () -> Long = System::currentTimeMillis) : SensorEventListener { fun start(onScore: (Float?) -> Unit): Boolean; fun stop() }`; `BleSensorDataSource(context, predictor, phoneMovement: PhoneMovement)`.

This is Android-bound glue that JVM unit tests cannot exercise (`SensorManager`/`SensorEvent`); the existing suite and a compile prove it still fits together, and Task 5 verifies it on the device.

- [ ] **Step 1: Update the BLE source constructor and its tests (failing build first)**

In `BleSensorDataSource.kt` change the constructor to

```kotlin
class BleSensorDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val predictor: SleepStagePredictor,
    private val phoneMovement: PhoneMovement,
) : SensorDataSource, BleTargetDeviceSink {
```
(add `import com.sleeppulse.app.tracking.PhoneMovement`) and replace the predictor call's movement argument:

```kotlin
                movement = phoneMovement.current(now), // null unless the phone-on-the-bed option is running
```
(delete the old comment about the Heart Rate Service carrying no movement; keep the class KDoc accurate: movement comes from the phone when the option is on, else unknown.)

Run `./gradlew :app:compileDebugUnitTestKotlin`; expected FAIL in `SensorSourceManagerTest.kt` (3 calls). Fix each call `BleSensorDataSource(mockContext, mock<SleepStagePredictor>())` → `BleSensorDataSource(mockContext, mock<SleepStagePredictor>(), PhoneMovement())` (add the import).

- [ ] **Step 2: Implement the monitor**

`PhoneMotionMonitor.kt`:

```kotlin
package com.sleeppulse.app.tracking

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log

/**
 * Listens to the phone's accelerometer while a session runs and reports a [MovementScoreCalculator] score for
 * the last [MovementScoreCalculator.WINDOW_MILLIS] every [PUBLISH_EVERY_MILLIS]. Samples are batched
 * ([REPORT_LATENCY_US]) to save battery. Does nothing when [sensorManager] is null or there is no accelerometer.
 * Not thread-safe: register/unregister and callbacks all arrive on the thread that called [start] (main).
 */
class PhoneMotionMonitor(
    private val sensorManager: SensorManager?,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : SensorEventListener {

    private val buffer = ArrayDeque<AccelSample>()
    private var onScore: ((Float?) -> Unit)? = null
    private var lastPublishMillis = 0L

    /** Returns false when there is no accelerometer to listen to (movement then simply stays unknown). */
    fun start(onScore: (Float?) -> Unit): Boolean {
        val manager = sensorManager ?: return false
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false
        this.onScore = onScore
        buffer.clear()
        lastPublishMillis = 0L
        return manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL, REPORT_LATENCY_US)
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        buffer.clear()
        onScore = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        val now = nowMillis()
        // Batched events arrive late; place each at the time it actually happened.
        val happenedAt = now - (SystemClock.elapsedRealtimeNanos() - event.timestamp) / 1_000_000L
        buffer.addLast(AccelSample(happenedAt, event.values[0], event.values[1], event.values[2]))
        while (buffer.isNotEmpty() && buffer.first().timestampMillis < now - MovementScoreCalculator.WINDOW_MILLIS) {
            buffer.removeFirst()
        }
        if (now - lastPublishMillis >= PUBLISH_EVERY_MILLIS) {
            lastPublishMillis = now
            val score = MovementScoreCalculator.score(buffer.toList())
            Log.d("SleepPulse", "phone movement score=$score")
            onScore?.invoke(score)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val PUBLISH_EVERY_MILLIS = 5_000L
        const val REPORT_LATENCY_US = 5_000_000
    }
}
```

- [ ] **Step 3: Wire it into the service**

`SleepTrackingService.kt`:
- imports: `android.hardware.SensorManager`, `com.sleeppulse.app.tracking.PhoneMotionMonitor`, `com.sleeppulse.app.tracking.PhoneMovement`, `com.sleeppulse.app.ui.settings.DataSourceMode`.
- field after `sleepSessionFinalizer`: `@Inject lateinit var phoneMovement: PhoneMovement` and next to `noiseMonitor`: `private var phoneMotionMonitor: PhoneMotionMonitor? = null`.
- in `onStartCommand`, after the `noiseMonitor` `scope.launch { ... }` block and before `return START_STICKY`:

```kotlin
        // Phone-on-the-bed movement: only for BLE mode with the option on, and never registered twice
        // (onStartCommand runs again on a repeated Connect or a sticky restart).
        if (phoneMotionMonitor == null &&
            settingsRepository.phoneMovementEnabled.value &&
            settingsRepository.dataSourceMode.value == DataSourceMode.BLE
        ) {
            val monitor = PhoneMotionMonitor(getSystemService(SensorManager::class.java))
            if (monitor.start { score -> phoneMovement.publish(score, System.currentTimeMillis()) }) {
                phoneMotionMonitor = monitor
            }
        }
```
- in `onDestroy`, after `noiseMonitor?.stopMonitoring()`:

```kotlin
        phoneMotionMonitor?.stop()
        phoneMotionMonitor = null
        phoneMovement.publish(null, System.currentTimeMillis())
```

- [ ] **Step 4: Update CLAUDE.md**

In the `BleSensorDataSource` bullet, replace the sentence about BLE movement being `null` / future work with: movement is `PhoneMovement.current(now)` — the phone's accelerometer score when the Settings toggle "Phone on the bed" is on and BLE mode is selected (`SleepTrackingService` runs `PhoneMotionMonitor`, published every 5 s, unknown after 60 s; `MovementScoreCalculator`, thresholds untuned, `Log.d` tag `SleepPulse` "phone movement score="), otherwise `null`; a strap's own accelerometer is future work. Keep the file's density.

- [ ] **Step 5: Full verification**

Run: `./gradlew :app:test lint :app:assembleDebug`
Expected: BUILD SUCCESSFUL, 0 test failures (new tests from Tasks 1–3 included; report the totals from `app/build/test-results/testDebugUnitTest/*.xml`).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/tracking/PhoneMotionMonitor.kt app/src/main/java/com/sleeppulse/app/services/SleepTrackingService.kt app/src/main/java/com/sleeppulse/app/data/source/BleSensorDataSource.kt app/src/test/java/com/sleeppulse/app/data/source/SensorSourceManagerTest.kt CLAUDE.md
git commit -m "feat: feed the phone accelerometer into BLE sleep-stage prediction (opt-in)"
```

---

### Task 5: Check it on the phone

**Files:** none (verification only; report, do not change code unless a bug is found — then stop and report).

- [ ] **Step 1: Install and enable**

`./gradlew :app:installDebug` with the Redmi connected (`adb devices`; pin one serial with `adb -s <serial>`). In the app: Settings → BLE source → turn "Phone on the bed" on. (BLE mode with a fake strap is needed; if no strap is available, say so — the log check in Step 2 needs a session running in BLE mode.)

- [ ] **Step 2: Read the score**

Start a session (Home → Connect) with the phone lying still, then run `adb -s <serial> logcat -d -s SleepPulse:D | grep "phone movement score"` after ~20 s. Expected: lines appear about every 5 s with scores near 0 (well under 0.1). Move or tap the phone and read again: scores rise (a firm shake should reach ≥ 0.8). Record the observed still and shaken values.

- [ ] **Step 3: Check the off paths**

Turn the toggle off, reconnect: no `phone movement score` lines appear. Switch to Simulated with the toggle on, reconnect: no lines appear. Disconnect: no crash in `adb logcat -b crash`.

- [ ] **Step 4: Report**

State what was observed (still / shaken scores, off paths) and anything that looked wrong. This task is evidence only; the unit suite does not cover the sensor, so say plainly if any part could not be verified.
