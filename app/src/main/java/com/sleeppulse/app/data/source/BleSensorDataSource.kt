package com.sleeppulse.app.data.source

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.sleeppulse.app.data.model.SensorConnectionState
import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.data.model.SleepStage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID
import javax.inject.Inject

/** Testable seam over [BleSensorDataSource.setTargetDevice] so [com.sleeppulse.app.ui.scan.ScanViewModel] can be unit-tested with a fake. */
interface BleTargetDeviceSink {
    fun setTargetDevice(address: String)
}

/**
 * Real BLE implementation, structured around Android's GATT client APIs so a physical
 * heart-rate/HRV peripheral (e.g. a chest strap or ring exposing the standard Heart Rate
 * Service, 0x180D) can be dropped in later.
 *
 * Service/characteristic discovery and parsing are wired correctly, but [connect] targets
 * a device address that must be supplied by a real scan result — until that's plumbed in
 * (see README, "Swapping in a real BLE peripheral"), this source stays unused at runtime
 * and [SimulatedSensorDataSource] is bound instead.
 */
class BleSensorDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : SensorDataSource, BleTargetDeviceSink {

    private val _connectionState =
        MutableStateFlow<SensorConnectionState>(SensorConnectionState.Disconnected)
    override val connectionState: Flow<SensorConnectionState> = _connectionState.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var targetDeviceAddress: String? = null

    /** Standard Bluetooth SIG Heart Rate Service/Characteristic UUIDs. */
    private object Ble {
        val HEART_RATE_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CLIENT_CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    override fun setTargetDevice(address: String) {
        targetDeviceAddress = address
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect() {
        val address = targetDeviceAddress
        if (address == null) {
            _connectionState.value =
                SensorConnectionState.Error("No BLE device selected; run a scan first")
            return
        }

        _connectionState.value = SensorConnectionState.Connecting
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val device: BluetoothDevice? = adapter?.getRemoteDevice(address)
        if (device == null) {
            _connectionState.value = SensorConnectionState.Error("Bluetooth unavailable")
            return
        }

        gatt = device.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    override suspend fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _connectionState.value = SensorConnectionState.Disconnected
    }

    /**
     * Live readings from the connected peripheral. Only heart rate is available from the
     * standard Heart Rate Service; HRV and sleep stage would come from a vendor-specific
     * characteristic on real hardware and default to neutral placeholders here.
     */
    override fun readings(): Flow<SensorReading> = callbackFlow {
        val callback = object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        _connectionState.value =
                            SensorConnectionState.Connected(deviceName = g.device.name ?: g.device.address)
                        g.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        _connectionState.value = SensorConnectionState.Disconnected
                    }
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val hrCharacteristic = g.getService(Ble.HEART_RATE_SERVICE)
                    ?.getCharacteristic(Ble.HEART_RATE_MEASUREMENT)
                    ?: return
                g.setCharacteristicNotification(hrCharacteristic, true)
                hrCharacteristic.getDescriptor(Ble.CLIENT_CONFIG_DESCRIPTOR)?.let { descriptor ->
                    descriptor.value = BluetoothGattDescriptorEnableNotification
                    g.writeDescriptor(descriptor)
                }
            }

            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                val bpm = parseHeartRate(characteristic) ?: return
                trySend(
                    SensorReading(
                        timestampMillis = System.currentTimeMillis(),
                        heartRateBpm = bpm,
                        hrvMillis = 0.0, // not available from the standard HR characteristic
                        sleepStage = SleepStage.AWAKE, // vendor-specific data would supply this
                    )
                )
            }
        }

        gattCallbackRef = callback

        awaitClose {
            disconnectGattQuietly()
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectGattQuietly() {
        gatt?.disconnect()
    }

    private var gattCallbackRef: BluetoothGattCallback? = null
    private val gattCallback: BluetoothGattCallback
        get() = gattCallbackRef ?: object : BluetoothGattCallback() {}

    private fun parseHeartRate(characteristic: BluetoothGattCharacteristic): Int? {
        val flags = characteristic.value?.getOrNull(0)?.toInt() ?: return null
        val format = if (flags and 0x01 != 0) {
            BluetoothGattCharacteristic.FORMAT_UINT16
        } else {
            BluetoothGattCharacteristic.FORMAT_UINT8
        }
        return characteristic.getIntValue(format, 1)
    }

    companion object {
        private val BluetoothGattDescriptorEnableNotification = byteArrayOf(0x01, 0x00)
    }
}
