package com.sleeppulse.app.ui.history

import com.sleeppulse.shared.repository.SleepRepository
import com.sleeppulse.app.testutil.MainDispatcherRule
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking

class NightTagsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `typed tags are trimmed, collapsed and capped`() {
        assertEquals(listOf("late workout"), addTypedTag(emptyList(), "  late   workout "))
        assertEquals(24, addTypedTag(emptyList(), "x".repeat(40)).single().length)
    }

    @Test
    fun `blank input and case-insensitive duplicates change nothing`() {
        val selected = listOf("Alcohol", "late workout")
        assertEquals(selected, addTypedTag(selected, "   "))
        assertEquals(selected, addTypedTag(selected, "ALCOHOL"))
        assertEquals(selected, addTypedTag(selected, "Late Workout"))
    }

    @Test
    fun `typing a preset's name selects the preset in its own spelling`() {
        assertEquals(listOf("Caffeine"), addTypedTag(emptyList(), "caffeine"))
    }

    @Test
    fun `options are the presets then this night's custom tags, without duplicating a preset`() {
        val options = tagOptions(listOf("Caffeine", "late workout"))
        assertEquals(PRESET_TAGS + "late workout", options)
    }

    @Test
    fun `toggling adds then removes`() {
        val on = toggleTag(emptyList(), "Stress")
        assertEquals(listOf("Stress"), on)
        assertEquals(emptyList<String>(), toggleTag(on, "Stress"))
    }

    @Test
    fun `saving writes the tags through the repository`() = runTest {
        val repository = mock<SleepRepository>()
        val date = LocalDate(2026, 9, 29)

        NightTagsViewModel(repository).setTags(date, listOf("Caffeine", "Stress"))
        advanceUntilIdle()

        verifyBlocking(repository) { updateTags(date, listOf("Caffeine", "Stress")) }
    }
}
