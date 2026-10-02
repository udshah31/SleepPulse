package com.sleeppulse.app.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RmssdCalculatorTest {

    /** 11 beats alternating 1000/1010 ms, one per second: 10 successive differences of 10 ms. */
    private fun RmssdCalculator.feedAlternating(beats: Int = 11) {
        for (n in 0 until beats) add(timestampMillis = n * 1_000L, rrMillis = if (n % 2 == 0) 1000.0 else 1010.0)
    }

    @Test
    fun `known intervals give the known rmssd`() {
        val calc = RmssdCalculator().apply { feedAlternating() }
        assertEquals(10.0, calc.rmssd(nowMillis = 10_000L)!!, 0.0001)
    }

    @Test
    fun `fewer than ten successive differences is unknown`() {
        val calc = RmssdCalculator().apply { feedAlternating(beats = 10) } // 9 differences
        assertNull(calc.rmssd(nowMillis = 9_000L))
    }

    @Test
    fun `beats older than the window are evicted`() {
        val calc = RmssdCalculator().apply { feedAlternating() }
        assertNull(calc.rmssd(nowMillis = 80_000L))   // everything older than 20_000
        assertNull(calc.rmssd(nowMillis = 65_000L))   // only 6 beats (5 differences) left
    }

    @Test
    fun `an out-of-range beat is dropped and breaks the pair, not the whole window`() {
        val calc = RmssdCalculator()
        for (n in 0 until 12) {
            // beat 5 is a 250 ms ectopic beat: neither (4,5) nor (5,6) may count
            val rr = if (n == 5) 250.0 else if (n % 2 == 0) 1000.0 else 1010.0
            calc.add(n * 1_000L, rr)
        }
        // 11 pairs minus the 2 touching beat 5 = 9 differences -> still unknown
        assertNull(calc.rmssd(nowMillis = 11_000L))
        calc.add(12_000L, 1000.0)
        calc.add(13_000L, 1010.0)
        // 11 differences, all 10 ms
        assertEquals(10.0, calc.rmssd(nowMillis = 13_000L)!!, 0.0001)
    }

    @Test
    fun `reset forgets everything`() {
        val calc = RmssdCalculator().apply { feedAlternating() }
        calc.reset()
        assertNull(calc.rmssd(nowMillis = 10_000L))
    }
}
