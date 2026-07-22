package com.sleeppulse.app.data.repository

import com.sleeppulse.app.ui.settings.DataSourceMode
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
}
