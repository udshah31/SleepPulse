package com.sleeppulse.app.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateMeasurementParserTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `8-bit bpm without rr`() {
        val m = HeartRateMeasurementParser.parse(bytes(0x00, 60))!!
        assertEquals(60, m.bpm)
        assertEquals(emptyList<Double>(), m.rrIntervalsMillis)
    }

    @Test
    fun `16-bit bpm`() {
        // 0x012C = 300
        assertEquals(300, HeartRateMeasurementParser.parse(bytes(0x01, 0x2C, 0x01))!!.bpm)
    }

    @Test
    fun `rr intervals are converted from 1024ths of a second to milliseconds`() {
        // flags 0x10 = rr present; 1024 -> 1000 ms, 512 -> 500 ms
        val m = HeartRateMeasurementParser.parse(bytes(0x10, 60, 0x00, 0x04, 0x00, 0x02))!!
        assertEquals(listOf(1000.0, 500.0), m.rrIntervalsMillis)
    }

    @Test
    fun `energy expended field is skipped before the rr field`() {
        // flags 0x18 = energy (bit 3) + rr (bit 4): 2 energy bytes, then rr 1024 -> 1000 ms
        val m = HeartRateMeasurementParser.parse(bytes(0x18, 60, 0xFF, 0xFF, 0x00, 0x04))!!
        assertEquals(60, m.bpm)
        assertEquals(listOf(1000.0), m.rrIntervalsMillis)
    }

    @Test
    fun `a trailing odd byte is ignored`() {
        val m = HeartRateMeasurementParser.parse(bytes(0x10, 60, 0x00, 0x04, 0x07))!!
        assertEquals(listOf(1000.0), m.rrIntervalsMillis)
    }

    @Test
    fun `null, empty and truncated packets return null instead of crashing`() {
        assertNull(HeartRateMeasurementParser.parse(null))
        assertNull(HeartRateMeasurementParser.parse(bytes()))
        assertNull(HeartRateMeasurementParser.parse(bytes(0x00)))        // 8-bit bpm missing
        assertNull(HeartRateMeasurementParser.parse(bytes(0x01, 0x2C)))  // 16-bit bpm half missing
    }
}
