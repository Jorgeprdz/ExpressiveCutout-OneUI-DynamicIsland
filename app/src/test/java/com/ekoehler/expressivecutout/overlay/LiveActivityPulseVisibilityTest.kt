package com.ekoehler.expressivecutout.overlay

import com.ekoehler.expressivecutout.core.live.LiveActivity
import com.ekoehler.expressivecutout.core.live.LiveActivityCoordinator
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies that pulse arrivals are based on what the overlay actually renders. */
class LiveActivityPulseVisibilityTest {

    @Test
    fun `hidden overlay reports no visible stable ids`() {
        val slots = slots(primary = activity("primary:A"))

        assertEquals(
            emptySet<String>(),
            LiveActivityPulseVisibility.visibleStableIds(
                slots = slots,
                overlayCanProject = false,
                splitAllowed = true,
                transientOnTop = false,
            ),
        )
    }

    @Test
    fun `ordinary projection exposes primary and split satellite`() {
        val slots = slots(
            primary = activity("primary:A"),
            satellite = activity("satellite:B", LiveActivity.Kind.MUSIC),
        )

        assertEquals(
            setOf("primary:A", "satellite:B"),
            LiveActivityPulseVisibility.visibleStableIds(
                slots = slots,
                overlayCanProject = true,
                splitAllowed = true,
                transientOnTop = false,
            ),
        )
    }

    @Test
    fun `split disabled exposes only primary`() {
        val slots = slots(
            primary = activity("primary:A"),
            satellite = activity("satellite:B", LiveActivity.Kind.MUSIC),
        )

        assertEquals(
            setOf("primary:A"),
            LiveActivityPulseVisibility.visibleStableIds(
                slots = slots,
                overlayCanProject = true,
                splitAllowed = false,
                transientOnTop = false,
            ),
        )
    }

    @Test
    fun `transient on top hides live primary when split is unavailable`() {
        val slots = slots(primary = activity("primary:A"))

        assertEquals(
            emptySet<String>(),
            LiveActivityPulseVisibility.visibleStableIds(
                slots = slots,
                overlayCanProject = true,
                splitAllowed = false,
                transientOnTop = true,
            ),
        )
    }

    @Test
    fun `transient on top exposes live primary through satellite when split is available`() {
        val slots = slots(primary = activity("primary:A"))

        assertEquals(
            setOf("primary:A"),
            LiveActivityPulseVisibility.visibleStableIds(
                slots = slots,
                overlayCanProject = true,
                splitAllowed = true,
                transientOnTop = true,
            ),
        )
    }

    @Test
    fun `call still leads while transient exists and keeps music satellite visible`() {
        val slots = slots(
            primary = activity("call:A", LiveActivity.Kind.CALL),
            satellite = activity("music:B", LiveActivity.Kind.MUSIC),
        )

        assertEquals(
            setOf("call:A", "music:B"),
            LiveActivityPulseVisibility.visibleStableIds(
                slots = slots,
                overlayCanProject = true,
                splitAllowed = true,
                transientOnTop = true,
            ),
        )
    }

    private fun slots(
        primary: LiveActivity? = null,
        satellite: LiveActivity? = null,
    ) = LiveActivityCoordinator.Slots(primary = primary, satellite = satellite)

    private fun activity(
        stableId: String,
        kind: LiveActivity.Kind = LiveActivity.Kind.NOTIFICATION,
    ) = LiveActivity(
        stableId = stableId,
        kind = kind,
        updatedElapsedRealtime = 1L,
        lifecycle = LiveActivity.Lifecycle.UNTIL_REMOVED,
        sourceKind = LiveActivity.SourceKind.FALLBACK,
    )
}
