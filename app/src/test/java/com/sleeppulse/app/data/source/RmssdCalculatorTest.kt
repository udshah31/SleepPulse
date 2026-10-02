package com.sleeppulse.app.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        // Complete eviction: everything older than 20_000
        val calc1 = RmssdCalculator().apply { feedAlternating() }
        assertNull(calc1.rmssd(nowMillis = 80_000L))

        // Partial eviction with 11 beats at t=0..10_000, window=60_000, now=65_000
        // Cutoff: 65_000 - 60_000 = 5_000
        // Evicted: beats at t < 5_000 (beats 0..4)
        // Remaining: beats 5..10 (6 beats, 5 differences < minDiffs=10)
        val calc2 = RmssdCalculator().apply { feedAlternating() }
        assertNull(calc2.rmssd(nowMillis = 65_000L))

        // Positive case 1: 25 beats at t=0..24_000, query at now=24_000 (all inside)
        // 24 differences of 10 ms -> 10.0
        val calc3 = RmssdCalculator().apply { feedAlternating(beats = 25) }
        assertEquals(10.0, calc3.rmssd(nowMillis = 24_000L)!!, 0.0001)

        // Positive case 2: 25 beats at t=0..24_000, query at now=69_000
        // Window=60_000, cutoff=9_000
        // Beats at t < 9_000 are evicted (0..8)
        // Remaining: beats at t=9_000..24_000 (16 beats, 15 differences)
        val calc4 = RmssdCalculator().apply { feedAlternating(beats = 25) }
        assertEquals(10.0, calc4.rmssd(nowMillis = 69_000L)!!, 0.0001)

        // Prove eviction: out-of-pattern beat at t=0 should be evicted at now=69_000
        val calc5a = RmssdCalculator()
        calc5a.add(0L, 1200.0)  // Out-of-pattern beat (within 20% of the next, so the relative filter keeps it)
        for (n in 1..24) {
            val rr = if (n % 2 == 0) 1000.0 else 1010.0
            calc5a.add((n * 1_000L), rr)
        }
        // At now=69_000, beats at t < 9_000 (including t=0) are evicted
        // Remaining: beats at t=9_000..24_000, all 10 ms differences -> 10.0
        assertEquals(10.0, calc5a.rmssd(nowMillis = 69_000L)!!, 0.0001)

        // Prove no eviction at now=24_000: out-of-pattern beat influences result
        val calc5b = RmssdCalculator()
        calc5b.add(0L, 1200.0)  // Out-of-pattern beat (within 20% of the next, so the relative filter keeps it)
        for (n in 1..24) {
            val rr = if (n % 2 == 0) 1000.0 else 1010.0
            calc5b.add((n * 1_000L), rr)
        }
        // At now=24_000, all 25 beats remain, including the 1200 beat
        // First difference is 190 ms, rest are 10 ms, so RMSSD will be much > 10.0
        val resultAt24K = calc5b.rmssd(nowMillis = 24_000L)!!
        assertTrue(Math.abs(resultAt24K - 10.0) > 1.0)
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
    fun `an in-range ectopic beat and its follower are dropped by the relative filter`() {
        val calc = RmssdCalculator()
        for (n in 0 until 20) {
            val rr = if (n == 8) 1800.0 else if (n % 2 == 0) 1000.0 else 1010.0
            calc.add(n * 1_000L, rr)
        }
        // 19 pairs minus (7,8),(8,9),(9,10) = 16 differences, all 10 ms
        assertEquals(10.0, calc.rmssd(nowMillis = 19_000L)!!, 0.0001)
    }

    @Test
    fun `a genuine step change rejects one beat and does not lock the filter out`() {
        val calc = RmssdCalculator()
        for (n in 0 until 24) calc.add(n * 1_000L, if (n < 12) 1000.0 else 1300.0)
        assertEquals(0.0, calc.rmssd(nowMillis = 23_000L)!!, 0.0001)
    }

    @Test
    fun `reset forgets everything`() {
        val calc = RmssdCalculator().apply { feedAlternating() }
        calc.reset()
        assertNull(calc.rmssd(nowMillis = 10_000L))
    }
}
