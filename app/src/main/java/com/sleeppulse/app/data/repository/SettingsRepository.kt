package com.sleeppulse.app.data.repository

import android.content.Context
import com.sleeppulse.app.ui.settings.DataSourceMode
import com.sleeppulse.app.ui.settings.TemperatureUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the user can set, as one snapshot so it can be saved and restored together. */
data class Settings(
    val dataSourceMode: DataSourceMode = DataSourceMode.SIMULATED,
    val targetBedtimeHour: Int = 22, // 10:30 PM
    val targetBedtimeMinute: Int = 30,
    val targetWakeupHour: Int = 7, // 7:00 AM
    val targetWakeupMinute: Int = 0,
    val wakeWindowMinutes: Int = 30,
    val amoledBlack: Boolean = false,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    /** The BLE sensor picked on the Scan screen; BLE mode can't connect without it. */
    val bleDeviceAddress: String? = null,
    val bleDeviceLabel: String? = null,
)

/**
 * Global holder for app settings. Each setter updates its flow and saves the whole snapshot to
 * [store], so settings survive a restart.
 */
@Singleton
class SettingsRepository @Inject constructor(private val store: SettingsStore) {

    /** Unpersisted, for tests. Not a default argument: Kotlin would copy @Inject onto both constructors. */
    constructor() : this(InMemorySettingsStore())

    private val initial = store.load()

    private val _dataSourceMode = MutableStateFlow(initial.dataSourceMode)
    val dataSourceMode: StateFlow<DataSourceMode> = _dataSourceMode.asStateFlow()

    private val _targetBedtimeHour = MutableStateFlow(initial.targetBedtimeHour)
    val targetBedtimeHour: StateFlow<Int> = _targetBedtimeHour.asStateFlow()

    private val _targetBedtimeMinute = MutableStateFlow(initial.targetBedtimeMinute)
    val targetBedtimeMinute: StateFlow<Int> = _targetBedtimeMinute.asStateFlow()

    private val _targetWakeupHour = MutableStateFlow(initial.targetWakeupHour)
    val targetWakeupHour: StateFlow<Int> = _targetWakeupHour.asStateFlow()

    private val _targetWakeupMinute = MutableStateFlow(initial.targetWakeupMinute)
    val targetWakeupMinute: StateFlow<Int> = _targetWakeupMinute.asStateFlow()

    private val _wakeWindowMinutes = MutableStateFlow(initial.wakeWindowMinutes)
    val wakeWindowMinutes: StateFlow<Int> = _wakeWindowMinutes.asStateFlow()

    private val _amoledBlack = MutableStateFlow(initial.amoledBlack)
    val amoledBlack: StateFlow<Boolean> = _amoledBlack.asStateFlow()

    private val _temperatureUnit = MutableStateFlow(initial.temperatureUnit)
    val temperatureUnit: StateFlow<TemperatureUnit> = _temperatureUnit.asStateFlow()

    private val _bleDeviceAddress = MutableStateFlow(initial.bleDeviceAddress)
    val bleDeviceAddress: StateFlow<String?> = _bleDeviceAddress.asStateFlow()

    private val _bleDeviceLabel = MutableStateFlow(initial.bleDeviceLabel)
    val bleDeviceLabel: StateFlow<String?> = _bleDeviceLabel.asStateFlow()

    fun setDataSourceMode(mode: DataSourceMode) {
        _dataSourceMode.value = mode
        save()
    }

    fun setTargetBedtime(hour: Int, minute: Int) {
        _targetBedtimeHour.value = hour
        _targetBedtimeMinute.value = minute
        save()
    }

    fun setTargetWakeup(hour: Int, minute: Int, windowMinutes: Int) {
        _targetWakeupHour.value = hour
        _targetWakeupMinute.value = minute
        _wakeWindowMinutes.value = windowMinutes
        save()
    }

    fun setAmoledBlack(enabled: Boolean) {
        _amoledBlack.value = enabled
        save()
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        _temperatureUnit.value = unit
        save()
    }

    fun setBleDevice(address: String, label: String) {
        _bleDeviceAddress.value = address
        _bleDeviceLabel.value = label
        save()
    }

    private fun save() = store.save(
        Settings(
            dataSourceMode = _dataSourceMode.value,
            targetBedtimeHour = _targetBedtimeHour.value,
            targetBedtimeMinute = _targetBedtimeMinute.value,
            targetWakeupHour = _targetWakeupHour.value,
            targetWakeupMinute = _targetWakeupMinute.value,
            wakeWindowMinutes = _wakeWindowMinutes.value,
            amoledBlack = _amoledBlack.value,
            temperatureUnit = _temperatureUnit.value,
            bleDeviceAddress = _bleDeviceAddress.value,
            bleDeviceLabel = _bleDeviceLabel.value,
        )
    )
}

interface SettingsStore {
    fun load(): Settings
    fun save(settings: Settings)
}

class InMemorySettingsStore(private var settings: Settings = Settings()) : SettingsStore {
    override fun load() = settings
    override fun save(settings: Settings) {
        this.settings = settings
    }
}

class PrefsSettingsStore @Inject constructor(@ApplicationContext context: Context) : SettingsStore {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    override fun load(): Settings {
        val d = Settings()
        return Settings(
            // An unknown name (e.g. an enum value removed in a later version) falls back to the default.
            dataSourceMode = enumOrDefault(prefs.getString("data_source_mode", null), d.dataSourceMode),
            targetBedtimeHour = prefs.getInt("bedtime_hour", d.targetBedtimeHour),
            targetBedtimeMinute = prefs.getInt("bedtime_minute", d.targetBedtimeMinute),
            targetWakeupHour = prefs.getInt("wakeup_hour", d.targetWakeupHour),
            targetWakeupMinute = prefs.getInt("wakeup_minute", d.targetWakeupMinute),
            wakeWindowMinutes = prefs.getInt("wake_window_minutes", d.wakeWindowMinutes),
            amoledBlack = prefs.getBoolean("amoled_black", d.amoledBlack),
            temperatureUnit = enumOrDefault(prefs.getString("temperature_unit", null), d.temperatureUnit),
            bleDeviceAddress = prefs.getString("ble_device_address", null),
            bleDeviceLabel = prefs.getString("ble_device_label", null),
        )
    }

    override fun save(settings: Settings) {
        prefs.edit()
            .putString("data_source_mode", settings.dataSourceMode.name)
            .putInt("bedtime_hour", settings.targetBedtimeHour)
            .putInt("bedtime_minute", settings.targetBedtimeMinute)
            .putInt("wakeup_hour", settings.targetWakeupHour)
            .putInt("wakeup_minute", settings.targetWakeupMinute)
            .putInt("wake_window_minutes", settings.wakeWindowMinutes)
            .putBoolean("amoled_black", settings.amoledBlack)
            .putString("temperature_unit", settings.temperatureUnit.name)
            .putString("ble_device_address", settings.bleDeviceAddress)
            .putString("ble_device_label", settings.bleDeviceLabel)
            .apply()
    }
}

internal inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default
