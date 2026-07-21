# BLE Scan Flow — Design Spec

**Date:** 2026-07-21
**Status:** Approved

## Goal

Close the last real gap in `BleSensorDataSource`: its GATT client is fully wired but
`connect()` requires a device address that today can only be supplied by hand-editing
code. Add a scan screen so a real address can be picked from the UI and handed to
`BleSensorDataSource.setTargetDevice()`, per the swap steps already documented in the
README.

## Scope

**In scope:**
- A new BLE scan wrapper (`BleDeviceScanner`) around `BluetoothLeScanner`
- A new MVI screen (`ui/scan/`) listing nearby BLE devices, reachable only from Settings
- Runtime permission request for `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` on screen open
- Wiring "select a device" through to `BleSensorDataSource.setTargetDevice(address)`
- Settings shows the selected device's name/address once one is picked (in-memory only,
  consistent with Settings' existing preference handling)

**Explicitly out of scope:**
- Dynamically switching the live `SensorDataSource` binding at runtime. The `@Binds`
  target in `AppModule.kt` stays a compile-time choice, exactly as the README already
  documents ("change the `@Binds` target... nothing else needs to change"). This spec
  makes the scan → address → `setTargetDevice()` path real; it does not make the
  Settings BLE radio button actually switch the running data source.
- Legacy pre-Android-12 scanning (`ACCESS_FINE_LOCATION` + old `BLUETOOTH`/
  `BLUETOOTH_ADMIN` permissions, needed for API 26–30). The manifest only declares the
  modern `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` permissions today, and there's no legacy
  device available to test against — flagged as a known gap, not addressed here.
- Filtering scan results to the Heart Rate Service UUID. Without a real HR peripheral to
  test against, an unfiltered list (any nearby BLE device, by name/address) is what's
  actually verifiable right now. A service-UUID `ScanFilter` can be added later once
  real hardware is available to confirm it doesn't hide the target device.

## Testing constraint

There is no real HR peripheral available. Verification is: scan starts, the permission
prompt appears and is handled correctly (granted and denied paths), and the results list
populates with whatever BLE devices are actually nearby (phones, headphones, etc — real
hardware, just not the target device type). Parsing of actual heart-rate data over GATT
is unverified end-to-end until a real peripheral exists — this was already true before
this spec (`BleSensorDataSource`'s GATT plumbing is unit-untested for the same reason)
and remains a known limitation.

## Architecture

### `BleDeviceScanner`

New file: `app/src/main/java/com/sleeppulse/app/data/source/BleDeviceScanner.kt`

An interface, so `ScanViewModelTest` can inject a hand-written fake (matching the
project's established "hand-written fakes, not Mockito" convention — see
`app/src/test/java/com/sleeppulse/app/testutil/`), plus one concrete implementation
wrapping the real `BluetoothLeScanner`:

```kotlin
data class ScannedDevice(val address: String, val name: String?)

interface BleScanSource {
    fun scan(): Flow<List<ScannedDevice>>
}

class BleDeviceScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) : BleScanSource {
    override fun scan(): Flow<List<ScannedDevice>> = ...
}
```

`ScanViewModel` depends on `BleScanSource`, not `BleDeviceScanner` directly.
`di/AppModule.kt` gets one addition: `@Binds abstract fun bindBleScanSource(impl:
BleDeviceScanner): BleScanSource`.

`scan()` starts a `BluetoothLeScanner.startScan(callback)` with no `ScanFilter` (see
Scope) when collected, accumulates devices into a deduplicated-by-address list (`Map`
keyed by address, updated per `ScanResult`, emitted as `.values.toList()`), and stops the
scan in `awaitClose`. No manual permission checks inside this class — the caller
(ViewModel) is responsible for only invoking `scan()` once permissions are granted, since
`startScan` throws `SecurityException` without them and the ViewModel is where the
permission-result callback lives anyway.

### `ui/scan/` — new MVI screen

Three new files, following the existing per-screen MVI pattern (see `ui/settings/` for
the shape to match):

- `ScanContract.kt` — `ScanIntent` (`StartScan`, `SelectDevice(address: String)`),
  `ScanState` (`devices: List<ScannedDevice>`, `isScanning: Boolean`,
  `permissionDenied: Boolean`)
- `ScanViewModel.kt` — `@HiltViewModel`, injects `BleDeviceScanner` and
  `BleSensorDataSource` directly (both have `@Inject` constructors already — no change
  to `AppModule.kt`'s `@Binds` needed for either). `StartScan` launches collection of
  `scanner.scan()` into state. `SelectDevice` calls
  `bleSensorDataSource.setTargetDevice(address)`, then publishes a one-shot "selection
  complete" signal (a `Channel<Unit>`/`SharedFlow`, the app's existing pattern for
  navigation side effects — check `WindDownFlow.kt`/`DashboardViewModel.kt` for the
  established idiom before introducing a new one) that `ScanScreen` observes to pop back.
- `ScanScreen.kt` — on first composition, requests `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT`
  via `rememberLauncherForActivityResult(RequestMultiplePermissions())`. On grant, sends
  `ScanIntent.StartScan`. On denial, renders a message ("Bluetooth permissions are
  required to scan") with a retry button that re-launches the permission request. On
  grant, renders a `LazyColumn` of `state.devices`, each row clickable to send
  `SelectDevice`.

### Navigation

`ui/SleepPulseApp.kt`: add `Destination.Scan` to the sealed class (route `"scan"`), add
`composable(Destination.Scan.route) { ScanScreen(onDeviceSelected = { navController.popBackStack() }) }`
to the `NavHost`. **Not** added to the `destinations` list used for the bottom
`NavigationBar` — Scan is reachable only via a button from Settings, not a tab.

### Settings changes

`ui/settings/SettingsScreen.kt`: when `state.dataSourceMode == DataSourceMode.BLE`, show
a "Scan for device" button below the data-source radio group that calls
`onNavigateToScan()` (new parameter, wired from `SleepPulseApp.kt`'s `NavHost` the same
way other screens receive callbacks — check existing screens for the callback-param
convention already in use, e.g. how `Destination` composables receive lambdas today, and
follow it).

`ui/settings/SettingsContract.kt`: add `selectedBleDeviceLabel: String? = null` to
`SettingsState`.

Result passing uses `navController`'s back-stack saved state handle (the standard Compose
Navigation pattern for returning a result to the previous screen), not a shared
ViewModel — the codebase has no shared/activity-scoped ViewModel pattern today and this
doesn't need to introduce one:

1. `ScanScreen`'s `SelectDevice` handler, after `ScanViewModel` calls
   `setTargetDevice()`, calls
   `navController.previousBackStackEntry?.savedStateHandle?.set("selected_ble_device", "$name ($address)")`
   then `navController.popBackStack()`.
2. `SettingsScreen` reads it back via
   `navController.currentBackStackEntry?.savedStateHandle?.getLiveData<String>("selected_ble_device")`
   (observed as state, following the same `collectAsState()`-style pattern already used
   elsewhere) and forwards it into `SettingsViewModel` as
   `SettingsIntent.SetSelectedBleDevice(label: String)`, which sets
   `state.selectedBleDeviceLabel`.

This keeps the result-passing mechanism entirely inside `SleepPulseApp.kt`'s nav wiring —
`ScanViewModel` and `SettingsViewModel` never reference each other.

### Manifest

No changes — `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` are already declared in
`AndroidManifest.xml`.

## Error handling

- `BluetoothAdapter.getDefaultAdapter()` returns null (no Bluetooth hardware) or
  `bluetoothLeScanner` is null (adapter disabled): `ScanViewModel` surfaces this as
  `ScanState.permissionDenied`-style error text ("Bluetooth is turned off") rather than
  silently showing an empty list forever.
- `SecurityException` from `startScan` (permission revoked between grant and scan start,
  a real race on some OEM skins): caught in `BleDeviceScanner.scan()`, closes the flow
  with the exception, `ScanViewModel` maps it to the same denied-state UI as an explicit
  permission denial.

## Testing

- `BleDeviceScanner` (the concrete `BluetoothLeScanner`-wrapping implementation): not
  unit-testable in the JVM test tree — it touches final Android framework classes with no
  instrumentation environment in this test tier. No unit test for this class; manual
  verification only, per the Testing Constraint above.
- `ScanViewModel`: unit-testable following the existing ViewModel test pattern (see
  `DashboardViewModelTest.kt`), injecting a hand-written `FakeBleScanSource : BleScanSource`
  test double under `app/src/test/java/com/sleeppulse/app/testutil/`. Covers: scan start
  populates `state.devices`, `SelectDevice` calls `setTargetDevice()` on a fake
  `SleepRepository`/`BleSensorDataSource` double and emits the selection signal.
- `SettingsViewModel`: add a test case for `SetSelectedBleDevice` setting
  `state.selectedBleDeviceLabel`, following the existing test file's pattern (create
  `SettingsViewModelTest.kt` if one doesn't already exist).
- Manual verification (per Testing Constraint): scan starts on screen open, permission
  grant/deny both produce correct UI, device list populates with real nearby devices,
  selecting a device navigates back to Settings with the label shown.
