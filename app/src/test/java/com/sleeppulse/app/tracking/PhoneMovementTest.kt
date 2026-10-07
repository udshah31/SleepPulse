package com.sleeppulse.app.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneMovementTest {

    private val movement = PhoneMovement()

    @Test
    fun `nothing published means unknown`() {
        assertNull(movement.current(nowMillis = 1_000L))
    }

    @Test
    fun `a fresh score is returned`() {
        movement.publish(0.4f, nowMillis = 10_000L)
        assertEquals(0.4f, movement.current(nowMillis = 12_000L)!!, 0f)
    }

    @Test
    fun `a score exactly at the limit is still fresh, one millisecond later it is stale`() {
        movement.publish(0.4f, nowMillis = 0L)
        assertEquals(0.4f, movement.current(nowMillis = PhoneMovement.STALE_AFTER_MILLIS)!!, 0f)
        assertNull(movement.current(nowMillis = PhoneMovement.STALE_AFTER_MILLIS + 1))
    }

    @Test
    fun `publishing null makes it unknown immediately`() {
        movement.publish(0.9f, nowMillis = 0L)
        movement.publish(null, nowMillis = 1_000L)
        assertNull(movement.current(nowMillis = 1_500L))
    }

    @Test
    fun `a newer score replaces the older one`() {
        movement.publish(0.9f, nowMillis = 0L)
        movement.publish(0.1f, nowMillis = 5_000L)
        assertEquals(0.1f, movement.current(nowMillis = 6_000L)!!, 0f)
    }

    @Test
    fun `a clock that moved backwards is not treated as stale`() {
        movement.publish(0.5f, nowMillis = 100_000L)
        assertEquals(0.5f, movement.current(nowMillis = 50_000L)!!, 0f)
    }
}
