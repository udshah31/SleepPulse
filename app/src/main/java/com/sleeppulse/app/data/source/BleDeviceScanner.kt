package com.sleeppulse.app.data.source

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.ParcelUuid
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
 * Wraps [android.bluetooth.le.BluetoothLeScanner], listing only devices whose advertisement
 * includes the standard Heart Rate Service (0x180D), so the list isn't flooded with unrelated,
 * mostly unnamed nearby devices. The filtering is done here, not with a
 * [android.bluetooth.le.ScanFilter]: on a Redmi M2006C3LG (Android 13) a hardware-style service-UUID
 * filter returned nothing even though the advertisement carried the UUID. A device that doesn't
 * put 0x180D in its advertisement won't be listed.
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
                if (result.scanRecord?.serviceUuids?.contains(HEART_RATE_SERVICE) != true) return
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

        awaitClose {
            try {
                scanner.stopScan(callback)
            } catch (e: SecurityException) {
                // Permission was revoked mid-scan — already tearing down, nothing to do.
            }
        }
    }

    private companion object {
        val HEART_RATE_SERVICE: ParcelUuid = ParcelUuid.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    }
}
