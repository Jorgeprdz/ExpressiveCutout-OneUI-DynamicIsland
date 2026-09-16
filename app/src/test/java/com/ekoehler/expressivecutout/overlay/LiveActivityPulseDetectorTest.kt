package com.ekoehler.expressivecutout.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies that status-bar pulses follow newly observed stable live identities, not content updates. */
class LiveActivityPulseDetectorTest {

    @Test
    fun `initial snapshot establishes baseline without pulse`() {
        val result = LiveActivityPulseDetector.observe(
            previous = LiveActivityPulseDetector.State(),
            presentStableIds = setOf("music:spotify:sessionA"),
            visibleStableIds = setOf("music:spotify:sessionA"),
        )

        assertFalse(result.shouldPulse)
        assertEquals(setOf("music:spotify:sessionA"), result.state.seenStableIds)
    }

    @Test
    fun `new music after empty baseline pulses once while metadata updates do not`() {
        var state = LiveActivityPulseDetector.observe(
            previous = LiveActivityPulseDetector.State(),
            presentStableIds = emptySet(),
            visibleStableIds = emptySet(),
        ).state

        val arrival = LiveActivityPulseDetector.observe(
            previous = state,
            presentStableIds = setOf("music:spotify:sessionA"),
            visibleStableIds = setOf("music:spotify:sessionA"),
        )
        assertTrue(arrival.shouldPulse)
        assertEquals(setOf("music:spotify:sessionA"), arrival.newlyVisibleStableIds)

        state = arrival.state
        val update = LiveActivityPulseDetector.observe(
            previous = state,
            presentStableIds = setOf("music:spotify:sessionA"),
            visibleStableIds = setOf("music:spotify:sessionA"),
        )
        assertFalse(update.shouldPulse)
    }

    @Test
    fun `call arrival pulses but music demotion and later promotion do not`() {
        var state = LiveActivityPulseDetector.observe(
            previous = LiveActivityPulseDetector.State(),
            presentStableIds = setOf("music:B"),
            visibleStableIds = setOf("music:B"),
        ).state

        val callArrival = LiveActivityPulseDetector.observe(
            previous = state,
            presentStableIds = setOf("call:A", "music:B"),
            visibleStableIds = setOf("call:A", "music:B"),
        )
        assertTrue(callArrival.shouldPulse)
        assertEquals(setOf("call:A"), callArrival.newlyVisibleStableIds)

        state = callArrival.state
        val promotion = LiveActivityPulseDetector.observe(
            previous = state,
            presentStableIds = setOf("music:B"),
            visibleStableIds = setOf("music:B"),
        )
        assertFalse(promotion.shouldPulse)
    }

    @Test
    fun `new satellite pulses without treating existing primary as new`() {
        val baseline = LiveActivityPulseDetector.observe(
            previous = LiveActivityPulseDetector.State(),
            presentStableIds = setOf("primary:A"),
            visibleStableIds = setOf("primary:A"),
        )

        val result = LiveActivityPulseDetector.observe(
            previous = baseline.state,
            presentStableIds = setOf("primary:A", "satellite:B"),
            visibleStableIds = setOf("primary:A", "satellite:B"),
        )

        assertTrue(result.shouldPulse)
        assertEquals(setOf("satellite:B"), result.newlyVisibleStableIds)
    }

    @Test
    fun `removal does not pulse and a genuinely new replacement does`() {
        var state = LiveActivityPulseDetector.observe(
            previous = LiveActivityPulseDetector.State(),
            presentStableIds = setOf("primary:A", "satellite:B"),
            visibleStableIds = setOf("primary:A", "satellite:B"),
        ).state

        val removal = LiveActivityPulseDetector.observe(
            previous = state,
            presentStableIds = setOf("primary:A"),
            visibleStableIds = setOf("primary:A"),
        )
        assertFalse(removal.shouldPulse)

        state = removal.state
        val replacement = LiveActivityPulseDetector.observe(
            previous = state,
            presentStableIds = setOf("primary:C"),
            visibleStableIds = setOf("primary:C"),
        )
        assertTrue(replacement.shouldPulse)
        assertEquals(setOf("primary:C"), replacement.newlyVisibleStableIds)
    }

    @Test
    fun `activity first observed while hidden does not pulse when later shown`() {
        var state = LiveActivityPulseDetector.observe(
            previous = LiveActivityPulseDetector.State(),
            presentStableIds = emptySet(),
            visibleStableIds = emptySet(),
        ).state

        val hiddenArrival = LiveActivityPulseDetector.observe(
            previous = state,
            presentStableIds = setOf("music:hidden"),
            visibleStableIds = emptySet(),
        )
        assertFalse(hiddenArrival.shouldPulse)

        state = hiddenArrival.state
        val restored = LiveActivityPulseDetector.observe(
            previous = state,
            presentStableIds = setOf("music:hidden"),
            visibleStableIds = setOf("music:hidden"),
        )
        assertFalse(restored.shouldPulse)
    }
}
