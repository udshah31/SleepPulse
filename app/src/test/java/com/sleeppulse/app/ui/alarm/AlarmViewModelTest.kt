package com.sleeppulse.app.ui.alarm

import com.sleeppulse.app.data.repository.SettingsRepository
import com.sleeppulse.app.testutil.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AlarmViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `updateWakeWindow does not clobber wakeup time even before the state collector has run`() = runTest {
        val repository = SettingsRepository()
        repository.setTargetWakeup(6, 15, 30)
        val viewModel = AlarmViewModel(repository)

        // Deliberately no advanceUntilIdle() here — the init{} combine collector hasn't
        // run yet, so _state.value is still its default (7, 0, 30), not (6, 15, 30).
        // Reading the "unchanged" hour/minute from _state.value instead of the
        // repository would silently revert them to the wrong defaults here.
        viewModel.updateWakeWindow(60)

        assertEquals(6, repository.targetWakeupHour.value)
        assertEquals(15, repository.targetWakeupMinute.value)
        assertEquals(60, repository.wakeWindowMinutes.value)
    }

    @Test
    fun `updateWakeupTime does not clobber wake window even before the state collector has run`() = runTest {
        val repository = SettingsRepository()
        repository.setTargetWakeup(6, 15, 45)
        val viewModel = AlarmViewModel(repository)

        viewModel.updateWakeupTime(hour = 8, minute = 30)

        assertEquals(8, repository.targetWakeupHour.value)
        assertEquals(30, repository.targetWakeupMinute.value)
        assertEquals(45, repository.wakeWindowMinutes.value)
    }
}
