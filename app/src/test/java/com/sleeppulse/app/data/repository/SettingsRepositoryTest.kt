package com.sleeppulse.app.data.repository

import com.sleeppulse.app.ui.settings.DataSourceMode
import com.sleeppulse.app.ui.settings.TemperatureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsRepositoryTest {

    private val repository = SettingsRepository()

    @Test
    fun `defaults match documented values`() {
        assertEquals(DataSourceMode.SIMULATED, repository.dataSourceMode.value)
        assertEquals(22, repository.targetBedtimeHour.value)
        assertEquals(30, repository.targetBedtimeMinute.value)
        assertEquals(7, repository.targetWakeupHour.value)
        assertEquals(0, repository.targetWakeupMinute.value)
        assertEquals(30, repository.wakeWindowMinutes.value)
        assertFalse(repository.amoledBlack.value)
        assertFalse(repository.phoneMovementEnabled.value)
    }

    @Test
    fun `setDataSourceMode updates only dataSourceMode`() {
        repository.setDataSourceMode(DataSourceMode.BLE)
        assertEquals(DataSourceMode.BLE, repository.dataSourceMode.value)
    }

    @Test
    fun `setTargetBedtime updates hour and minute together`() {
        repository.setTargetBedtime(23, 15)
        assertEquals(23, repository.targetBedtimeHour.value)
        assertEquals(15, repository.targetBedtimeMinute.value)
    }

    @Test
    fun `setTargetWakeup updates hour, minute, and window together`() {
        repository.setTargetWakeup(6, 45, 20)
        assertEquals(6, repository.targetWakeupHour.value)
        assertEquals(45, repository.targetWakeupMinute.value)
        assertEquals(20, repository.wakeWindowMinutes.value)
    }

    @Test
    fun `setAmoledBlack toggles independently of other settings`() {
        repository.setTargetBedtime(23, 15)
        repository.setAmoledBlack(true)
        assertEquals(true, repository.amoledBlack.value)
        assertEquals(23, repository.targetBedtimeHour.value)
    }

    @Test
    fun `setPhoneMovementEnabled toggles independently of other settings`() {
        repository.setTargetBedtime(23, 15)
        repository.setPhoneMovementEnabled(true)
        assertEquals(true, repository.phoneMovementEnabled.value)
        assertEquals(23, repository.targetBedtimeHour.value)
    }

    @Test
    fun `every setting survives a restart through the store`() {
        val store = InMemorySettingsStore()
        SettingsRepository(store).apply {
            setDataSourceMode(DataSourceMode.BLE)
            setTargetBedtime(23, 15)
            setTargetWakeup(6, 45, 20)
            setAmoledBlack(true)
            setTemperatureUnit(TemperatureUnit.FAHRENHEIT)
            setBleDevice("AA:BB", "Strap (AA:BB)")
            setPhoneMovementEnabled(true)
        }

        val restarted = SettingsRepository(store)

        assertEquals(DataSourceMode.BLE, restarted.dataSourceMode.value)
        assertEquals(23, restarted.targetBedtimeHour.value)
        assertEquals(15, restarted.targetBedtimeMinute.value)
        assertEquals(6, restarted.targetWakeupHour.value)
        assertEquals(45, restarted.targetWakeupMinute.value)
        assertEquals(20, restarted.wakeWindowMinutes.value)
        assertEquals(true, restarted.amoledBlack.value)
        assertEquals(TemperatureUnit.FAHRENHEIT, restarted.temperatureUnit.value)
        assertEquals("AA:BB", restarted.bleDeviceAddress.value)
        assertEquals("Strap (AA:BB)", restarted.bleDeviceLabel.value)
        assertEquals(true, restarted.phoneMovementEnabled.value)
    }

    @Test
    fun `an unknown stored enum name falls back to the default instead of crashing`() {
        assertEquals(DataSourceMode.SIMULATED, enumOrDefault("REMOVED_MODE", DataSourceMode.SIMULATED))
        assertEquals(TemperatureUnit.FAHRENHEIT, enumOrDefault("FAHRENHEIT", TemperatureUnit.CELSIUS))
        assertEquals(TemperatureUnit.CELSIUS, enumOrDefault(null, TemperatureUnit.CELSIUS))
    }
}
