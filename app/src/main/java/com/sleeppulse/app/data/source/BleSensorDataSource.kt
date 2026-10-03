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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import com.sleeppulse.app.tracking.PhoneMovement
import com.sleeppulse.app.tracking.SleepStagePredictor

/** Testable seam over [BleSensorDataSource.setTargetDevice] so [com.sleeppulse.app.ui.scan.ScanViewModel] can be unit-tested with a fake. */
interface BleTargetDeviceSink {
    fun setTargetDevice(address: String)
}

/**
 * Real BLE implementation, structured around Android's GATT client APIs so a physical
 * heart-rate peripheral (e.g. a chest strap or ring exposing the standard Heart Rate
 * Service, 0x180D) can be dropped in.
 *
 * Service/characteristic discovery and parsing are wired correctly; [connect] targets
 * a device address that must be supplied by a real scan result.
 * BLE mode is selectable in Settings; HRV is real only when the strap sends RR-intervals.
 * Movement comes from the phone's accelerometer ([PhoneMovement]) when the "Phone on the bed" option is on,
 * otherwise it is unknown (null).
 */
class BleSensorDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val predictor: SleepStagePredictor,
    private val phoneMovement: PhoneMovement,
) : SensorDataSource, BleTargetDeviceSink {

    private val _connectionState =
        MutableStateFlow<SensorConnectionState>(SensorConnectionState.Disconnected)
    override val connectionState: Flow<SensorConnectionState> = _connectionState.asStateFlow()

    // GATT callbacks are registered when connectGatt() is called, so this must be a stable
    // callback rather than one created later by a readings() collector. A shared flow also lets
    // the repository and dashboard observe the same peripheral without replacing the callback.
    private val _readings = MutableSharedFlow<SensorReading>(extraBufferCapacity = 16)

    private var gatt: BluetoothGatt? = null
    private var targetDeviceAddress: String? = null
    private val rmssd = RmssdCalculator()

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

        // LE explicitly: with AUTO, Android may pick classic BR/EDR for a dual-mode peripheral and fail with 133.
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    override suspend fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _connectionState.value = SensorConnectionState.Disconnected
    }

    /**
     * Live readings from the connected peripheral. Heart rate and RR-intervals come from the
     * standard Heart Rate Service; HRV is the rolling RMSSD of those RR-intervals (null when
     * the device sends none); sleep stage is predicted from heart rate and HRV.
     *
     * This is shared because the GATT callback belongs to the connection, not to an individual
     * collector. Creating a callback inside this method would allow a later collector to replace
     * the callback that was registered with connectGatt().
     */
    override fun readings(): Flow<SensorReading> = _readings.asSharedFlow()

    private val gattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value =
                        SensorConnectionState.Connected(deviceName = g.device.name ?: g.device.address)
                    rmssd.reset()
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
            val packet = HeartRateMeasurementParser.parse(characteristic.value) ?: return
            val now = System.currentTimeMillis()
            packet.rrIntervalsMillis.forEach { rmssd.add(now, it) }
            val hrvMillis = rmssd.rmssd(now) // null until the strap has sent enough RR-intervals

            val predictedStage = predictor.predict(
                heartRateBpm = packet.bpm,
                hrvMillis = hrvMillis?.toLong(),
                movement = phoneMovement.current(now), // null unless the phone-on-the-bed option is running
            )

            _readings.tryEmit(
                SensorReading(
                    timestampMillis = now,
                    heartRateBpm = packet.bpm,
                    hrvMillis = hrvMillis,
                    sleepStage = predictedStage,
                )
            )
        }
    }

    companion object {
        private val BluetoothGattDescriptorEnableNotification = byteArrayOf(0x01, 0x00)
    }
}
