package com.ekoehler.expressivecutout.system

import com.ekoehler.expressivecutout.overlay.LiveActivityPulseDetector
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
        )
        assertEquals(2_500L, deadline)

        deadline = StatusBarPulseDeadline.extend(deadline, 1_000L)
        assertEquals(3_500L, deadline)

        deadline = StatusBarPulseDeadline.extend(deadline, 2_000L)
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
    fun `stale observed deadline cannot expire a newer lease`() {
        val observedDeadline = 2_500L
        val extendedDeadline = 5_000L

        assertFalse(
            StatusBarPulseDeadline.shouldExpire(
                observedDeadlineElapsedRealtimeMs = observedDeadline,
                currentDeadlineElapsedRealtimeMs = extendedDeadline,
                nowElapsedRealtimeMs = 2_500L,
            ),
        )
        assertFalse(
            StatusBarPulseDeadline.shouldExpire(
                observedDeadlineElapsedRealtimeMs = extendedDeadline,
                currentDeadlineElapsedRealtimeMs = extendedDeadline,
                nowElapsedRealtimeMs = 4_999L,
            ),
        )
        assertTrue(
            StatusBarPulseDeadline.shouldExpire(
                observedDeadlineElapsedRealtimeMs = extendedDeadline,
                currentDeadlineElapsedRealtimeMs = extendedDeadline,
                nowElapsedRealtimeMs = 5_000L,
            ),
        )
    }

    @Test
    fun `removal does not extend deadline`() {
        var detectorState = LiveActivityPulseDetector.State()
        detectorState = LiveActivityPulseDetector.observe(
            previous = detectorState,
            presentStableIds = emptySet(),
            visibleStableIds = emptySet(),
        ).state

        val arrival = LiveActivityPulseDetector.observe(
            previous = detectorState,
            presentStableIds = setOf("activity:A"),
            visibleStableIds = setOf("activity:A"),
        )
        detectorState = arrival.state
        var deadline = if (arrival.shouldPulse) {
            StatusBarPulseDeadline.extend(null, 0L)
        } else {
            null
        }
        assertEquals(2_500L, deadline)

        val removal = LiveActivityPulseDetector.observe(
            previous = detectorState,
            presentStableIds = emptySet(),
            visibleStableIds = emptySet(),
        )
        if (removal.shouldPulse) {
            deadline = StatusBarPulseDeadline.extend(deadline, 1_000L)
        }

        assertFalse(removal.shouldPulse)
        assertEquals(2_500L, deadline)
    }
}
