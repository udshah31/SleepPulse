package com.sleeppulse.app.ui.scan

import app.cash.turbine.test
import com.sleeppulse.app.data.repository.SettingsRepository
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
        val viewModel = ScanViewModel(scanSource, FakeBleTargetDeviceSink(), SettingsRepository())

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
        val viewModel = ScanViewModel(FakeBleScanSource(), sink, SettingsRepository())
        val device = ScannedDevice(address = "AA:BB", name = "Fake HR Strap")

        viewModel.deviceSelected.test {
            viewModel.onIntent(ScanIntent.SelectDevice(device))
            assertEquals(device, awaitItem())
        }
        assertEquals("AA:BB", sink.lastTargetAddress)
    }

    @Test
    fun `SelectDevice saves the device so BLE mode survives a restart`() {
        val settings = SettingsRepository()
        val viewModel = ScanViewModel(FakeBleScanSource(), FakeBleTargetDeviceSink(), settings)

        viewModel.onIntent(ScanIntent.SelectDevice(ScannedDevice(address = "AA:BB", name = null)))

        assertEquals("AA:BB", settings.bleDeviceAddress.value)
        assertEquals("Unknown (AA:BB)", settings.bleDeviceLabel.value)
    }

    @Test
    fun `PermissionDenied sets error text`() {
        val viewModel = ScanViewModel(FakeBleScanSource(), FakeBleTargetDeviceSink(), SettingsRepository())

        viewModel.onIntent(ScanIntent.PermissionDenied)

        assertEquals("Bluetooth permissions are required to scan", viewModel.state.value.error)
    }

    @Test
    fun `StartScan called twice cancels the prior scan instead of running concurrently`() = runTest {
        val scanSource = FakeBleScanSource()
        val viewModel = ScanViewModel(scanSource, FakeBleTargetDeviceSink(), SettingsRepository())

        viewModel.onIntent(ScanIntent.StartScan)
        advanceUntilIdle()
        assertEquals(1, scanSource.scanCallCount)
        assertEquals(1, scanSource.activeCollectorCount)

        viewModel.onIntent(ScanIntent.StartScan)
        advanceUntilIdle()

        assertEquals(2, scanSource.scanCallCount)
        // Only the second scan's collector should still be active — the first must have
        // been cancelled, not left running concurrently.
        assertEquals(1, scanSource.activeCollectorCount)
    }

    @Test
    fun `scan failure surfaces as error and stops scanning`() = runTest {
        val scanSource = FakeBleScanSource().apply {
            errorToThrow = IllegalStateException("Bluetooth is turned off")
        }
        val viewModel = ScanViewModel(scanSource, FakeBleTargetDeviceSink(), SettingsRepository())

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
