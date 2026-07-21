# BLE Scan Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user pick a real BLE device address from a scan screen and hand it to `BleSensorDataSource.setTargetDevice()`, closing the gap where that call could previously only be made by editing code.

**Architecture:** A new `BleDeviceScanner` wraps `BluetoothLeScanner` behind a `BleScanSource` interface (fakeable for tests). A new `ui/scan/` MVI screen (`ScanContract`/`ScanViewModel`/`ScanScreen`) requests Bluetooth runtime permissions, drives the scan, and lets the user pick a device. Selection is wired to `BleSensorDataSource` through a new `BleTargetDeviceSink` interface (also fakeable) so `ScanViewModel` never depends on the concrete Android BLE class. The picked device's label is passed back to `SettingsScreen` via Compose Navigation's `savedStateHandle` result pattern — no shared ViewModel introduced.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, `androidx.navigation.compose`, `androidx.activity.compose` (already a dependency, used for `rememberLauncherForActivityResult`), JUnit4 + Turbine + kotlinx-coroutines-test for ViewModel tests (all already declared, no new dependencies).

## Global Constraints

- No new Gradle dependencies.
- `BleDeviceScanner`'s scan results are **unfiltered** — no `ScanFilter` by service UUID. This is deliberate: there is no real HR peripheral to test against, so an unfiltered "any nearby BLE device" list is what's actually verifiable.
- The `@Binds` target for `SensorDataSource` in `AppModule.kt` (`SimulatedSensorDataSource`) is **not changed** by this plan. Selecting a device only calls `setTargetDevice()` — it does not make the app start using BLE data at runtime. This matches the README's existing documented manual swap step.
- Legacy pre-Android-12 BLE scanning (`ACCESS_FINE_LOCATION` + old `BLUETOOTH`/`BLUETOOTH_ADMIN` permissions) is out of scope. `AndroidManifest.xml` already declares `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` and needs no changes.
- Test doubles are hand-written fakes under `app/src/test/java/com/sleeppulse/app/testutil/`, not Mockito — matching the existing convention (`FakeSensorDataSource.kt`, `FakeSleepRepository.kt`, etc).
- `BleDeviceScanner` (the concrete Android-framework-touching implementation) has no unit test — it isn't testable in the JVM test tier, matching the existing precedent that `BleSensorDataSource`'s GATT plumbing also has no unit tests. Verification for that one class is `./gradlew :app:assembleDebug` (compiles) plus manual on-device testing.
- New Compose screens (`ScanScreen`) follow the existing pattern of having no automated UI test — `DashboardScreen`/`HistoryScreen`/`SettingsScreen` don't have one either; this plan doesn't introduce Compose UI testing infrastructure.

---

### Task 1: BLE scan data source + target-device sink interfaces

**Files:**
- Create: `app/src/main/java/com/sleeppulse/app/data/source/BleDeviceScanner.kt`
- Modify: `app/src/main/java/com/sleeppulse/app/data/source/BleSensorDataSource.kt`
- Modify: `app/src/main/java/com/sleeppulse/app/di/AppModule.kt`

**Interfaces:**
- Consumes: nothing new (first task).
- Produces:
  - `data class ScannedDevice(val address: String, val name: String?)`
  - `interface BleScanSource { fun scan(): Flow<List<ScannedDevice>> }`
  - `class BleDeviceScanner : BleScanSource` (concrete, `@Inject constructor(@ApplicationContext context: Context)`)
  - `interface BleTargetDeviceSink { fun setTargetDevice(address: String) }`, implemented by `BleSensorDataSource`
  - Hilt bindings: `BleScanSource` → `BleDeviceScanner`, `BleTargetDeviceSink` → `BleSensorDataSource`

These are what Task 2's `ScanViewModel` depends on.

- [ ] **Step 1: Create `BleDeviceScanner.kt` with the scan source interface and implementation**

```kotlin
package com.sleeppulse.app.data.source

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class ScannedDevice(val address: String, val name: String?)

/** Testable seam over [BleDeviceScanner] so [com.sleeppulse.app.ui.scan.ScanViewModel] can be unit-tested with a fake. */
interface BleScanSource {
    fun scan(): Flow<List<ScannedDevice>>
}

/**
 * Wraps [android.bluetooth.le.BluetoothLeScanner]. Results are unfiltered (no service-UUID
 * [android.bluetooth.le.ScanFilter]) so nearby devices show up during manual testing without
 * a real Heart Rate Service peripheral available.
 *
 * Callers must hold BLUETOOTH_SCAN/BLUETOOTH_CONNECT before calling [scan] — this class does
 * not check permissions itself and lets [android.bluetooth.le.BluetoothLeScanner.startScan]'s
 * [SecurityException] propagate as a flow failure if they're missing.
 */
class BleDeviceScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) : BleScanSource {

    @SuppressLint("MissingPermission")
    override fun scan(): Flow<List<ScannedDevice>> = callbackFlow {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            close(IllegalStateException("Bluetooth is unavailable or turned off"))
            return@callbackFlow
        }

        val devices = linkedMapOf<String, ScannedDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                devices[device.address] = ScannedDevice(address = device.address, name = device.name)
                trySend(devices.values.toList())
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("BLE scan failed with error code $errorCode"))
            }
        }

        try {
            scanner.startScan(callback)
        } catch (e: SecurityException) {
            close(e)
            return@callbackFlow
        }

        awaitClose { scanner.stopScan(callback) }
    }
}
```

- [ ] **Step 2: Add `BleTargetDeviceSink` and implement it on `BleSensorDataSource`**

In `app/src/main/java/com/sleeppulse/app/data/source/BleSensorDataSource.kt`, add this interface above the `BleSensorDataSource` class:

```kotlin
/** Testable seam over [BleSensorDataSource.setTargetDevice] so [com.sleeppulse.app.ui.scan.ScanViewModel] can be unit-tested with a fake. */
interface BleTargetDeviceSink {
    fun setTargetDevice(address: String)
}
```

Change the class declaration from:

```kotlin
class BleSensorDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : SensorDataSource {
```

to:

```kotlin
class BleSensorDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : SensorDataSource, BleTargetDeviceSink {
```

The existing `fun setTargetDevice(address: String) { targetDeviceAddress = address }` method already matches the interface signature — add `override` to it:

```kotlin
    override fun setTargetDevice(address: String) {
        targetDeviceAddress = address
    }
```

- [ ] **Step 3: Add Hilt bindings in `AppModule.kt`**

In `app/src/main/java/com/sleeppulse/app/di/AppModule.kt`, add these imports:

```kotlin
import com.sleeppulse.app.data.source.BleDeviceScanner
import com.sleeppulse.app.data.source.BleScanSource
import com.sleeppulse.app.data.source.BleSensorDataSource
import com.sleeppulse.app.data.source.BleTargetDeviceSink
```

Add these two `@Binds` methods inside `BindingsModule` (after the existing `bindSensorDataSource`):

```kotlin
    @Binds
    abstract fun bindBleScanSource(impl: BleDeviceScanner): BleScanSource

    @Binds
    abstract fun bindBleTargetDeviceSink(impl: BleSensorDataSource): BleTargetDeviceSink
```

- [ ] **Step 4: Verify the project compiles**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. (No unit test for `BleDeviceScanner` itself — see Global Constraints.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/data/source/BleDeviceScanner.kt \
        app/src/main/java/com/sleeppulse/app/data/source/BleSensorDataSource.kt \
        app/src/main/java/com/sleeppulse/app/di/AppModule.kt
git commit -m "feat: add BLE scan source and target-device sink interfaces"
```

---

### Task 2: Scan screen MVI logic (Contract + ViewModel)

**Files:**
- Create: `app/src/main/java/com/sleeppulse/app/ui/scan/ScanContract.kt`
- Create: `app/src/main/java/com/sleeppulse/app/ui/scan/ScanViewModel.kt`
- Create: `app/src/test/java/com/sleeppulse/app/testutil/FakeBleScanSource.kt`
- Create: `app/src/test/java/com/sleeppulse/app/testutil/FakeBleTargetDeviceSink.kt`
- Test: `app/src/test/java/com/sleeppulse/app/ui/scan/ScanViewModelTest.kt`

**Interfaces:**
- Consumes: `BleScanSource`/`ScannedDevice` and `BleTargetDeviceSink` from Task 1 (`com.sleeppulse.app.data.source`).
- Produces:
  - `sealed class ScanIntent { StartScan, SelectDevice(device: ScannedDevice), PermissionDenied }`
  - `data class ScanState(devices: List<ScannedDevice> = emptyList(), isScanning: Boolean = false, error: String? = null)`
  - `class ScanViewModel(scanSource: BleScanSource, targetDeviceSink: BleTargetDeviceSink) : ViewModel()` with `val state: StateFlow<ScanState>`, `val deviceSelected: Flow<ScannedDevice>` (one-shot selection signal), and `fun onIntent(intent: ScanIntent)`.

Task 3's `ScanScreen` consumes `ScanState`, `ScanIntent`, and `ScanViewModel.deviceSelected` exactly as named above.

- [ ] **Step 1: Create `ScanContract.kt`**

```kotlin
package com.sleeppulse.app.ui.scan

import com.sleeppulse.app.data.source.ScannedDevice

sealed class ScanIntent {
    data object StartScan : ScanIntent()
    data class SelectDevice(val device: ScannedDevice) : ScanIntent()
    data object PermissionDenied : ScanIntent()
}

data class ScanState(
    val devices: List<ScannedDevice> = emptyList(),
    val isScanning: Boolean = false,
    val error: String? = null,
)
```

- [ ] **Step 2: Create the fakes for `ScanViewModelTest`**

`app/src/test/java/com/sleeppulse/app/testutil/FakeBleScanSource.kt`:

```kotlin
package com.sleeppulse.app.testutil

import com.sleeppulse.app.data.source.BleScanSource
import com.sleeppulse.app.data.source.ScannedDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class FakeBleScanSource : BleScanSource {
    val resultsFlow = MutableSharedFlow<List<ScannedDevice>>(extraBufferCapacity = 10)
    var errorToThrow: Throwable? = null

    var scanCallCount = 0
        private set

    override fun scan(): Flow<List<ScannedDevice>> {
        scanCallCount++
        return flow {
            errorToThrow?.let { throw it }
            emitAll(resultsFlow)
        }
    }
}
```

`app/src/test/java/com/sleeppulse/app/testutil/FakeBleTargetDeviceSink.kt`:

```kotlin
package com.sleeppulse.app.testutil

import com.sleeppulse.app.data.source.BleTargetDeviceSink

class FakeBleTargetDeviceSink : BleTargetDeviceSink {
    var lastTargetAddress: String? = null
        private set

    override fun setTargetDevice(address: String) {
        lastTargetAddress = address
    }
}
```

- [ ] **Step 3: Write the failing test**

Create `app/src/test/java/com/sleeppulse/app/ui/scan/ScanViewModelTest.kt`:

```kotlin
package com.sleeppulse.app.ui.scan

import app.cash.turbine.test
import com.sleeppulse.app.data.source.ScannedDevice
import com.sleeppulse.app.testutil.FakeBleScanSource
import com.sleeppulse.app.testutil.FakeBleTargetDeviceSink
import com.sleeppulse.app.testutil.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ScanViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `StartScan collects devices into state`() = runTest {
        val scanSource = FakeBleScanSource()
        val viewModel = ScanViewModel(scanSource, FakeBleTargetDeviceSink())

        viewModel.state.test {
            assertEquals(ScanState(), awaitItem())

            viewModel.onIntent(ScanIntent.StartScan)
            advanceUntilIdle()

            val scanning = awaitItem()
            assertEquals(true, scanning.isScanning)
            assertEquals(null, scanning.error)

            val device = ScannedDevice(address = "AA:BB", name = "Fake HR Strap")
            scanSource.resultsFlow.emit(listOf(device))
            advanceUntilIdle()

            val withDevice = awaitItem()
            assertEquals(listOf(device), withDevice.devices)
            assertEquals(true, withDevice.isScanning)
        }
    }

    @Test
    fun `SelectDevice sets target device and emits selection signal`() = runTest {
        val sink = FakeBleTargetDeviceSink()
        val viewModel = ScanViewModel(FakeBleScanSource(), sink)
        val device = ScannedDevice(address = "AA:BB", name = "Fake HR Strap")

        viewModel.deviceSelected.test {
            viewModel.onIntent(ScanIntent.SelectDevice(device))
            assertEquals(device, awaitItem())
        }
        assertEquals("AA:BB", sink.lastTargetAddress)
    }

    @Test
    fun `PermissionDenied sets error text`() {
        val viewModel = ScanViewModel(FakeBleScanSource(), FakeBleTargetDeviceSink())

        viewModel.onIntent(ScanIntent.PermissionDenied)

        assertEquals("Bluetooth permissions are required to scan", viewModel.state.value.error)
    }

    @Test
    fun `scan failure surfaces as error and stops scanning`() = runTest {
        val scanSource = FakeBleScanSource().apply {
            errorToThrow = IllegalStateException("Bluetooth is turned off")
        }
        val viewModel = ScanViewModel(scanSource, FakeBleTargetDeviceSink())

        viewModel.state.test {
            assertEquals(ScanState(), awaitItem())

            viewModel.onIntent(ScanIntent.StartScan)
            advanceUntilIdle()

            val scanning = awaitItem()
            assertEquals(true, scanning.isScanning)

            val errored = awaitItem()
            assertEquals(false, errored.isScanning)
            assertEquals("Bluetooth is turned off", errored.error)
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `./gradlew :app:test --tests "com.sleeppulse.app.ui.scan.ScanViewModelTest"`
Expected: FAIL — compile error, `ScanViewModel` does not exist yet.

- [ ] **Step 5: Implement `ScanViewModel.kt`**

```kotlin
package com.sleeppulse.app.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleeppulse.app.data.source.BleScanSource
import com.sleeppulse.app.data.source.BleTargetDeviceSink
import com.sleeppulse.app.data.source.ScannedDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanSource: BleScanSource,
    private val targetDeviceSink: BleTargetDeviceSink,
) : ViewModel() {

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private val _deviceSelected = Channel<ScannedDevice>(Channel.BUFFERED)
    val deviceSelected: Flow<ScannedDevice> = _deviceSelected.receiveAsFlow()

    fun onIntent(intent: ScanIntent) {
        when (intent) {
            ScanIntent.StartScan -> startScan()
            is ScanIntent.SelectDevice -> selectDevice(intent.device)
            ScanIntent.PermissionDenied ->
                _state.update { it.copy(error = "Bluetooth permissions are required to scan") }
        }
    }

    private fun startScan() {
        _state.update { it.copy(isScanning = true, error = null) }
        viewModelScope.launch {
            scanSource.scan()
                .catch { e ->
                    _state.update { it.copy(isScanning = false, error = e.message ?: "Scan failed") }
                }
                .collect { devices ->
                    _state.update { it.copy(devices = devices) }
                }
        }
    }

    private fun selectDevice(device: ScannedDevice) {
        targetDeviceSink.setTargetDevice(device.address)
        _deviceSelected.trySend(device)
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :app:test --tests "com.sleeppulse.app.ui.scan.ScanViewModelTest"`
Expected: PASS, 4/4 tests.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/ui/scan/ScanContract.kt \
        app/src/main/java/com/sleeppulse/app/ui/scan/ScanViewModel.kt \
        app/src/test/java/com/sleeppulse/app/testutil/FakeBleScanSource.kt \
        app/src/test/java/com/sleeppulse/app/testutil/FakeBleTargetDeviceSink.kt \
        app/src/test/java/com/sleeppulse/app/ui/scan/ScanViewModelTest.kt
git commit -m "feat: add ScanViewModel with permission-aware scan/select flow"
```

---

### Task 3: Scan screen UI + runtime permission handling

**Files:**
- Create: `app/src/main/java/com/sleeppulse/app/ui/scan/ScanScreen.kt`

**Interfaces:**
- Consumes: `ScanState`, `ScanIntent`, `ScanViewModel` (`state`, `deviceSelected`, `onIntent`) from Task 2; `ScannedDevice` from Task 1.
- Produces: `@Composable fun ScanScreen(onDeviceSelected: (ScannedDevice) -> Unit, viewModel: ScanViewModel = hiltViewModel())`. Task 4's `SleepPulseApp.kt` NavHost wires this exact signature.

- [ ] **Step 1: Create `ScanScreen.kt`**

```kotlin
package com.sleeppulse.app.ui.scan

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sleeppulse.app.data.source.ScannedDevice

@Composable
fun ScanScreen(
    onDeviceSelected: (ScannedDevice) -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) {
            viewModel.onIntent(ScanIntent.StartScan)
        } else {
            viewModel.onIntent(ScanIntent.PermissionDenied)
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
        )
    }

    LaunchedEffect(Unit) {
        viewModel.deviceSelected.collect { device -> onDeviceSelected(device) }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Nearby devices", style = MaterialTheme.typography.titleMedium)

        val error = state.error
        if (error != null) {
            Text(text = error)
            Button(
                onClick = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
                    )
                },
            ) {
                Text("Retry")
            }
        } else {
            LazyColumn {
                items(state.devices, key = { it.address }) { device ->
                    Text(
                        text = device.name ?: device.address,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { viewModel.onIntent(ScanIntent.SelectDevice(device)) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify the project compiles**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. (No automated UI test for this screen — see Global Constraints; manual verification happens after Task 4 wires navigation.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/ui/scan/ScanScreen.kt
git commit -m "feat: add ScanScreen with runtime Bluetooth permission handling"
```

---

### Task 4: Navigation wiring + Settings integration

**Files:**
- Modify: `app/src/main/java/com/sleeppulse/app/ui/SleepPulseApp.kt`
- Modify: `app/src/main/java/com/sleeppulse/app/ui/settings/SettingsContract.kt`
- Modify: `app/src/main/java/com/sleeppulse/app/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/sleeppulse/app/ui/settings/SettingsScreen.kt`
- Test: `app/src/test/java/com/sleeppulse/app/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `ScanScreen(onDeviceSelected, viewModel)` from Task 3; `ScannedDevice` from Task 1.
- Produces: nothing consumed by a later task (last task in this plan).

- [ ] **Step 1: Write the failing test for the new Settings intent**

Add this test to the end of the existing `SettingsViewModelTest.kt` (inside the class, after the last `@Test` function):

```kotlin
    @Test
    fun `SetSelectedBleDevice updates only selectedBleDeviceLabel`() {
        val viewModel = SettingsViewModel()
        viewModel.onIntent(SettingsIntent.SetSelectedBleDevice("Fake HR Strap (AA:BB)"))

        assertEquals("Fake HR Strap (AA:BB)", viewModel.state.value.selectedBleDeviceLabel)
        assertEquals(DataSourceMode.SIMULATED, viewModel.state.value.dataSourceMode)
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:test --tests "com.sleeppulse.app.ui.settings.SettingsViewModelTest"`
Expected: FAIL — compile error, `SettingsIntent.SetSelectedBleDevice` and `selectedBleDeviceLabel` don't exist yet.

- [ ] **Step 3: Update `SettingsContract.kt`**

Change:

```kotlin
sealed class SettingsIntent {
    data class SetDataSource(val mode: DataSourceMode) : SettingsIntent()
    data class SetTemperatureUnit(val unit: TemperatureUnit) : SettingsIntent()
}

data class SettingsState(
    val dataSourceMode: DataSourceMode = DataSourceMode.SIMULATED,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
)
```

to:

```kotlin
sealed class SettingsIntent {
    data class SetDataSource(val mode: DataSourceMode) : SettingsIntent()
    data class SetTemperatureUnit(val unit: TemperatureUnit) : SettingsIntent()
    data class SetSelectedBleDevice(val label: String) : SettingsIntent()
}

data class SettingsState(
    val dataSourceMode: DataSourceMode = DataSourceMode.SIMULATED,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val selectedBleDeviceLabel: String? = null,
)
```

- [ ] **Step 4: Update `SettingsViewModel.kt`**

Add a branch to the `when (intent)` block:

```kotlin
    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SetDataSource -> _state.update { it.copy(dataSourceMode = intent.mode) }
            is SettingsIntent.SetTemperatureUnit -> _state.update { it.copy(temperatureUnit = intent.unit) }
            is SettingsIntent.SetSelectedBleDevice -> _state.update { it.copy(selectedBleDeviceLabel = intent.label) }
        }
    }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:test --tests "com.sleeppulse.app.ui.settings.SettingsViewModelTest"`
Expected: PASS, 5/5 tests.

- [ ] **Step 6: Update `SettingsScreen.kt`**

Add two parameters to the composable and a "Scan for device" button plus the selected-device label, shown only in BLE mode. Change:

```kotlin
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(text = "Data source", style = MaterialTheme.typography.titleMedium)
        DataSourceMode.entries.forEach { mode ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.dataSourceMode == mode,
                    onClick = { viewModel.onIntent(SettingsIntent.SetDataSource(mode)) },
                )
                Text(text = mode.label())
            }
        }
```

to:

```kotlin
@Composable
fun SettingsScreen(
    onNavigateToScan: () -> Unit = {},
    selectedBleDeviceLabel: String? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(selectedBleDeviceLabel) {
        selectedBleDeviceLabel?.let {
            viewModel.onIntent(SettingsIntent.SetSelectedBleDevice(it))
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(text = "Data source", style = MaterialTheme.typography.titleMedium)
        DataSourceMode.entries.forEach { mode ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.dataSourceMode == mode,
                    onClick = { viewModel.onIntent(SettingsIntent.SetDataSource(mode)) },
                )
                Text(text = mode.label())
            }
        }
        if (state.dataSourceMode == DataSourceMode.BLE) {
            Button(onClick = onNavigateToScan) {
                Text("Scan for device")
            }
            state.selectedBleDeviceLabel?.let { label ->
                Text(text = "Selected: $label")
            }
        }
```

Add the required imports at the top of the file:

```kotlin
import androidx.compose.material3.Button
import androidx.compose.runtime.LaunchedEffect
```

(Leave the rest of the file — the closing braces and the `label()` extension functions below — unchanged.)

- [ ] **Step 7: Update `SleepPulseApp.kt` — add the Scan destination and result passback**

Add `ScanScreen` and `ScannedDevice` imports:

```kotlin
import com.sleeppulse.app.data.source.ScannedDevice
import com.sleeppulse.app.ui.scan.ScanScreen
```

Add `Destination.Scan` to the sealed class:

```kotlin
private sealed class Destination(val route: String, val label: String) {
    data object Dashboard : Destination("dashboard", "Home")
    data object History : Destination("history", "History")
    data object Settings : Destination("settings", "Settings")
    data object Scan : Destination("scan", "Scan")
}
```

Do **not** add `Destination.Scan` to the `destinations` list (it stays `listOf(Destination.Dashboard, Destination.History, Destination.Settings)`) — Scan is not a bottom-nav tab.

Replace the `NavHost` body:

```kotlin
        NavHost(
            navController = navController,
            startDestination = Destination.Dashboard.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Dashboard.route) { DashboardScreen() }
            composable(Destination.History.route) { HistoryScreen() }
            composable(Destination.Settings.route) { backStackEntry ->
                val selectedDevice by backStackEntry.savedStateHandle
                    .getStateFlow<String?>("selected_ble_device", null)
                    .collectAsState()
                SettingsScreen(
                    onNavigateToScan = { navController.navigate(Destination.Scan.route) },
                    selectedBleDeviceLabel = selectedDevice,
                )
            }
            composable(Destination.Scan.route) {
                ScanScreen(
                    onDeviceSelected = { device: ScannedDevice ->
                        navController.previousBackStackEntry?.savedStateHandle
                            ?.set("selected_ble_device", "${device.name ?: "Unknown"} (${device.address})")
                        navController.popBackStack()
                    },
                )
            }
        }
```

Add the `collectAsState` import if not already present (it already is, from the top-level file imports — verify `androidx.compose.runtime.collectAsState` and `androidx.compose.runtime.getValue` are both imported; they are, per the existing file).

- [ ] **Step 8: Run the full test suite**

Run: `./gradlew :app:test`
Expected: `BUILD SUCCESSFUL`, all tests passing (previous suite count + 5 new `ScanViewModelTest` cases + 1 new `SettingsViewModelTest` case).

- [ ] **Step 9: Verify the project builds**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Manual verification (per plan's Testing Constraint — no real HR peripheral)**

Install on a device/emulator: `./gradlew :app:installDebug`. Then manually verify:
1. Settings → select "BLE sensor (coming soon)" → "Scan for device" button appears.
2. Tap it → permission dialog appears (grant path) → scan screen shows a live-updating list of nearby BLE devices (or an empty list if none nearby — not an error).
3. Deny permissions instead → error text + Retry button appear; tapping Retry re-triggers the permission dialog.
4. Tap a device in the list → screen navigates back to Settings → "Selected: <name> (<address>)" is shown.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/ui/SleepPulseApp.kt \
        app/src/main/java/com/sleeppulse/app/ui/settings/SettingsContract.kt \
        app/src/main/java/com/sleeppulse/app/ui/settings/SettingsViewModel.kt \
        app/src/main/java/com/sleeppulse/app/ui/settings/SettingsScreen.kt \
        app/src/test/java/com/sleeppulse/app/ui/settings/SettingsViewModelTest.kt
git commit -m "feat: wire BLE scan screen into navigation and Settings"
```
