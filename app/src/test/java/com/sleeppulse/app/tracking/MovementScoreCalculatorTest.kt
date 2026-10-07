package com.sleeppulse.app.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementScoreCalculatorTest {

    private fun s(x: Float, y: Float, z: Float, t: Long = 0L) = AccelSample(t, x, y, z)
    private fun flat(magnitude: Float) = s(0f, 0f, magnitude)

    @Test
    fun `a still phone scores about zero`() {
        assertEquals(0f, MovementScoreCalculator.score(listOf(flat(9.81f)))!!, 0.001f)
    }

    @Test
    fun `breathing-sized noise stays below 0_1`() {
        val noise = listOf(flat(9.83f), flat(9.79f), flat(9.84f))
        assertTrue(MovementScoreCalculator.score(noise)!! < 0.1f)
    }

    @Test
    fun `a 1 m per s2 bump scores 0_5`() {
        assertEquals(0.5f, MovementScoreCalculator.score(listOf(flat(10.81f)))!!, 0.001f)
    }

    @Test
    fun `a 3 m per s2 spike scores 1 and is above the AWAKE threshold`() {
        val score = MovementScoreCalculator.score(listOf(flat(12.81f)))!!
        assertEquals(1f, score, 0.001f)
        assertTrue(score > 0.8f)
    }

    @Test
    fun `the peak in the window counts, not the latest sample`() {
        val score = MovementScoreCalculator.score(listOf(flat(12.81f), flat(9.81f)))!!
        assertEquals(1f, score, 0.001f)
    }

    @Test
    fun `only the change from gravity counts, not the phone's orientation`() {
        // Same 10.81 magnitude along three different orientations.
        val along = listOf(s(10.81f, 0f, 0f), s(0f, 10.81f, 0f), s(6f, 6f, 6.6974f))
        MovementScoreCalculator.score(along)!!.let { assertEquals(0.5f, it, 0.01f) }
        // A tilted but still phone: magnitude is gravity.
        // (6, 6, 4.9231) has magnitude 9.81: 6² + 6² + 4.9231² = 96.236 = 9.81².
        assertEquals(0f, MovementScoreCalculator.score(listOf(s(6f, 6f, 4.9231f)))!!, 0.01f)
    }

    @Test
    fun `no samples means unknown`() {
        assertNull(MovementScoreCalculator.score(emptyList()))
    }

    @Test
    fun `non-finite samples are ignored and cannot poison the score`() {
        val withNan = listOf(s(Float.NaN, 0f, 0f), s(0f, Float.POSITIVE_INFINITY, 0f), flat(9.81f))
        assertEquals(0f, MovementScoreCalculator.score(withNan)!!, 0.001f)
        assertNull(MovementScoreCalculator.score(listOf(s(Float.NaN, 0f, 0f))))
    }
}
