package com.sleeppulse.app.ui.history

import com.sleeppulse.app.tracking.ExternalSleepSession
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class OtherAppsSleepRowsTest {

    private val zone = ZoneId.of("America/Chicago")

    @Before
    fun englishDates() = Locale.setDefault(Locale.US)

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 9, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun session(id: String, start: Long, end: Long, deep: Int = 0, rem: Int = 0, pkg: String = "com.samsung.android.app.health") =
        ExternalSleepSession(id, start, end, deep, rem, pkg)

    @Test
    fun `rows are newest first with date, duration, stages and a readable source`() {
        val rows = toOtherAppsRows(
            listOf(
                session("old", at(26, 23), at(27, 6, 30), deep = 90, rem = 105),
                session("new", at(28, 22, 45), at(29, 6, 50), deep = 75, rem = 5),
            ),
            zone,
            label = { if (it == "com.samsung.android.app.health") "Samsung Health" else it },
        )

        assertEquals(listOf("new", "old"), rows.map { it.id })
        assertEquals("Mon, Sep 28", rows[0].date)
        assertEquals("8h 05m", rows[0].duration)
        assertEquals("Deep 1h 15m · REM 0h 05m", rows[0].stages)
        assertEquals("Samsung Health", rows[0].source)
        assertEquals("7h 30m", rows[1].duration)
    }

    @Test
    fun `a source without stage data shows no stage line instead of zeros`() {
        val row = toOtherAppsRows(listOf(session("a", at(28, 23), at(29, 7))), zone).single()

        assertNull(row.stages)
        assertEquals("com.samsung.android.app.health", row.source)
    }
}
