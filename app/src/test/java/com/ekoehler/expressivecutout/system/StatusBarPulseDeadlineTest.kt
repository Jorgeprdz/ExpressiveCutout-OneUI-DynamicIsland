package com.ekoehler.expressivecutout.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the monotonic, extendable deadline used by the transient status-bar pulse. */
class StatusBarPulseDeadlineTest {

    @Test
    fun `overlapping arrivals extend one deadline without gaps`() {
        var deadline = StatusBarPulseDeadline.extend(
            currentDeadlineElapsedRealtimeMs = null,
            nowElapsedRealtimeMs = 0L,
            durationMs = 2_500L,
        )
        assertEquals(2_500L, deadline)

        deadline = StatusBarPulseDeadline.extend(deadline, 1_000L, 2_500L)
        assertEquals(3_500L, deadline)

        deadline = StatusBarPulseDeadline.extend(deadline, 2_000L, 2_500L)
        assertEquals(4_500L, deadline)

        assertTrue(StatusBarPulseDeadline.isActive(deadline, 2_500L))
        assertTrue(StatusBarPulseDeadline.isActive(deadline, 3_500L))
        assertTrue(StatusBarPulseDeadline.isActive(deadline, 4_499L))
        assertFalse(StatusBarPulseDeadline.isActive(deadline, 4_500L))
    }

    @Test
    fun `zero duration does not activate a pulse`() {
        val deadline = StatusBarPulseDeadline.extend(
            currentDeadlineElapsedRealtimeMs = null,
            nowElapsedRealtimeMs = 10L,
            durationMs = 0L,
        )

        assertNull(deadline)
        assertFalse(StatusBarPulseDeadline.isActive(deadline, 10L))
    }

    @Test
    fun `stale expiry observes the latest deadline before clearing`() {
        val latestDeadline = 4_500L

        assertEquals(2_000L, StatusBarPulseDeadline.remainingMs(latestDeadline, 2_500L))
        assertEquals(1_000L, StatusBarPulseDeadline.remainingMs(latestDeadline, 3_500L))
        assertEquals(0L, StatusBarPulseDeadline.remainingMs(latestDeadline, 4_500L))
    }
}
