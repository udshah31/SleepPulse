package com.sleeppulse.shared.analytics

import com.sleeppulse.shared.model.NightlySummary
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

class TagCorrelationCalculatorTest {

    private fun night(daysAgo: Int, score: Int, tags: List<String> = emptyList()) = NightlySummary(
        date = LocalDate(2026, 7, 18).minus(daysAgo, DateTimeUnit.DAY),
        sleepScore = score,
        avgHeartRateBpm = 55,
        avgHrvMillis = 50.0,
        totalSleepMinutes = 420,
        deepSleepMinutes = 90,
        remSleepMinutes = 60,
        tags = tags,
    )

    @Test
    fun `returns empty list when no tags recorded`() {
        val nights = (0 until 5).map { night(it, 80) }
        assertEquals(emptyList<TagCorrelation>(), TagCorrelationCalculator.analyze(nights))
    }

    @Test
    fun `tag with fewer than 3 occurrences is excluded`() {
        val nights = listOf(
            night(0, 60, listOf("caffeine")),
            night(1, 65, listOf("caffeine")),
            night(2, 90),
            night(3, 92),
        )
        assertEquals(emptyList<TagCorrelation>(), TagCorrelationCalculator.analyze(nights))
    }

    @Test
    fun `caffeine tag correlates with lower scores`() {
        val nights = listOf(
            night(0, 60, listOf("caffeine")),
            night(1, 62, listOf("caffeine")),
            night(2, 58, listOf("caffeine")),
            night(3, 90),
            night(4, 92),
            night(5, 88),
        )
        val result = TagCorrelationCalculator.analyze(nights)

        assertEquals(1, result.size)
        val caffeine = result.first()
        assertEquals("caffeine", caffeine.tag)
        assertEquals(60.0, caffeine.avgScoreWithTag, 0.01)
        assertEquals(90.0, caffeine.avgScoreWithoutTag, 0.01)
        assertTrue(caffeine.scoreDelta < 0)
        assertEquals(3, caffeine.taggedNightCount)
    }

    @Test
    fun `results are sorted worst-impact first`() {
        val nights = listOf(
            night(0, 95, listOf("good-tag")),
            night(1, 96, listOf("good-tag")),
            night(2, 97, listOf("good-tag")),
            night(3, 50, listOf("bad-tag")),
            night(4, 52, listOf("bad-tag")),
            night(5, 51, listOf("bad-tag")),
            night(6, 80),
            night(7, 82),
        )
        val result = TagCorrelationCalculator.analyze(nights)

        assertEquals(listOf("bad-tag", "good-tag"), result.map { it.tag })
    }
}
