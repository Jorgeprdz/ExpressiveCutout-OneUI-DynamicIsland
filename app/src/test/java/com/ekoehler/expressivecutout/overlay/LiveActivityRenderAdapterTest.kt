package com.ekoehler.expressivecutout.overlay

import com.ekoehler.expressivecutout.core.live.LiveActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Verifies the single mapping from neutral live activities to renderer families. */
class LiveActivityRenderAdapterTest {

    @Test
    fun `specialized kinds keep their existing renderer families`() {
        assertEquals(LiveActivityRenderKind.CALL, LiveActivityRenderAdapter.adapt(activity(LiveActivity.Kind.CALL)).renderKind)
        assertEquals(LiveActivityRenderKind.TIMER, LiveActivityRenderAdapter.adapt(activity(LiveActivity.Kind.TIMER)).renderKind)
        assertEquals(LiveActivityRenderKind.MUSIC, LiveActivityRenderAdapter.adapt(activity(LiveActivity.Kind.MUSIC)).renderKind)
        assertEquals(
            LiveActivityRenderKind.GENERIC_PROGRESS,
            LiveActivityRenderAdapter.adapt(activity(LiveActivity.Kind.GENERIC_PROGRESS)).renderKind,
        )
    }

    @Test
    fun `semantic and native notification kinds use the generic live renderer`() {
        val genericKinds = listOf(
            LiveActivity.Kind.NOTIFICATION,
            LiveActivity.Kind.RIDESHARE,
            LiveActivity.Kind.FOOD_ORDER,
            LiveActivity.Kind.PARCEL_DELIVERY,
            LiveActivity.Kind.WEATHER,
            LiveActivity.Kind.OTP,
            LiveActivity.Kind.NAVIGATION,
        )

        genericKinds.forEach { kind ->
            assertEquals(
                "Expected generic renderer for $kind",
                LiveActivityRenderKind.GENERIC_LIVE,
                LiveActivityRenderAdapter.adapt(activity(kind)).renderKind,
            )
        }
    }

    @Test
    fun `adapter preserves stable identity and structured live payload`() {
        val progress = LiveActivity.Progress(current = 42, max = 100)
        val timing = LiveActivity.Timing(endElapsedRealtimeMs = 9_000L)
        val source = activity(LiveActivity.Kind.RIDESHARE).copy(
            stableId = "ride:stable",
            title = "Driver arriving",
            subtitle = "2 min",
            phase = "arriving",
            progress = progress,
            timing = timing,
        )

        val rendered = LiveActivityRenderAdapter.adapt(source)

        assertEquals(source.stableId, rendered.stableId)
        assertEquals(source.title, rendered.title)
        assertEquals(source.subtitle, rendered.subtitle)
        assertEquals(source.phase, rendered.phase)
        assertSame(progress, rendered.progress)
        assertSame(timing, rendered.timing)
    }

    private fun activity(kind: LiveActivity.Kind): LiveActivity = LiveActivity(
        stableId = "${kind.name.lowercase()}:stable",
        kind = kind,
        title = "Title",
        updatedElapsedRealtime = 100L,
        lifecycle = LiveActivity.Lifecycle.UNTIL_REMOVED,
        sourceKind = LiveActivity.SourceKind.FALLBACK,
    )
}
