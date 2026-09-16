package com.ekoehler.expressivecutout.overlay

import com.ekoehler.expressivecutout.core.live.LiveActivity
import com.ekoehler.expressivecutout.core.live.LiveActivityCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifies that coordinator slot changes become stable visual transitions keyed by stable ID. */
class LiveActivityVisualReducerTest {

    @Test
    fun `first primary reveals the island`() {
        val music = activity("music:spotify:sessionA", LiveActivity.Kind.MUSIC, "Track A", 100L)

        val result = LiveActivityVisualReducer.reduce(
            previous = LiveActivityVisualState(),
            slots = LiveActivityCoordinator.Slots(primary = music),
        )

        assertEquals(music.stableId, result.primary?.stableId)
        assertEquals(LiveActivityContainerState.COLLAPSED, result.containerState)
        assertEquals(LiveActivityVisualTransition.REVEAL, result.transition)
    }

    @Test
    fun `same primary stable id updates in place without reveal`() {
        val music = activity("music:spotify:sessionA", LiveActivity.Kind.MUSIC, "Track A", 100L)
        val updated = music.copy(title = "Track B", updatedElapsedRealtime = 200L)

        val result = LiveActivityVisualReducer.reduce(
            previous = LiveActivityVisualState(primary = music),
            slots = LiveActivityCoordinator.Slots(primary = updated),
        )

        assertEquals(updated, result.primary)
        assertEquals(LiveActivityVisualTransition.NONE, result.transition)
    }

    @Test
    fun `same stable id preserves expanded state across metadata updates`() {
        val music = activity("music:spotify:sessionA", LiveActivity.Kind.MUSIC, "Track A", 100L)
        val updated = music.copy(title = "Track B", updatedElapsedRealtime = 200L)

        val result = LiveActivityVisualReducer.reduce(
            previous = LiveActivityVisualState(
                primary = music,
                containerState = LiveActivityContainerState.EXPANDED,
            ),
            slots = LiveActivityCoordinator.Slots(primary = updated),
        )

        assertEquals(LiveActivityContainerState.EXPANDED, result.containerState)
        assertEquals(LiveActivityVisualTransition.NONE, result.transition)
    }

    @Test
    fun `call demotes existing music into satellite without removing music`() {
        val music = activity("music:spotify:sessionA", LiveActivity.Kind.MUSIC, "Track", 100L)
        val call = activity("call:dialer:key", LiveActivity.Kind.CALL, "Caller", 200L)

        val result = LiveActivityVisualReducer.reduce(
            previous = LiveActivityVisualState(primary = music),
            slots = LiveActivityCoordinator.Slots(primary = call, satellite = music),
        )

        assertEquals(call.stableId, result.primary?.stableId)
        assertEquals(music.stableId, result.satellite?.stableId)
        assertEquals(LiveActivityVisualTransition.DEMOTE_PRIMARY_TO_SATELLITE, result.transition)
    }

    @Test
    fun `removing call promotes already visible music without reveal`() {
        val music = activity("music:spotify:sessionA", LiveActivity.Kind.MUSIC, "Track", 100L)
        val call = activity("call:dialer:key", LiveActivity.Kind.CALL, "Caller", 200L)

        val result = LiveActivityVisualReducer.reduce(
            previous = LiveActivityVisualState(primary = call, satellite = music),
            slots = LiveActivityCoordinator.Slots(primary = music),
        )

        assertEquals(music.stableId, result.primary?.stableId)
        assertNull(result.satellite)
        assertEquals(LiveActivityVisualTransition.PROMOTE_SATELLITE, result.transition)
    }

    @Test
    fun `adding satellite does not restart primary`() {
        val call = activity("call:dialer:key", LiveActivity.Kind.CALL, "Caller", 200L)
        val music = activity("music:spotify:sessionA", LiveActivity.Kind.MUSIC, "Track", 100L)

        val result = LiveActivityVisualReducer.reduce(
            previous = LiveActivityVisualState(primary = call),
            slots = LiveActivityCoordinator.Slots(primary = call, satellite = music),
        )

        assertEquals(call.stableId, result.primary?.stableId)
        assertEquals(music.stableId, result.satellite?.stableId)
        assertEquals(LiveActivityVisualTransition.ADD_SATELLITE, result.transition)
    }

    @Test
    fun `removing satellite preserves primary`() {
        val call = activity("call:dialer:key", LiveActivity.Kind.CALL, "Caller", 200L)
        val music = activity("music:spotify:sessionA", LiveActivity.Kind.MUSIC, "Track", 100L)

        val result = LiveActivityVisualReducer.reduce(
            previous = LiveActivityVisualState(primary = call, satellite = music),
            slots = LiveActivityCoordinator.Slots(primary = call),
        )

        assertEquals(call.stableId, result.primary?.stableId)
        assertNull(result.satellite)
        assertEquals(LiveActivityVisualTransition.REMOVE_SATELLITE, result.transition)
    }

    @Test
    fun `removing the final primary hides the island`() {
        val timer = activity("timer:clock:key", LiveActivity.Kind.TIMER, "Timer", 100L)

        val result = LiveActivityVisualReducer.reduce(
            previous = LiveActivityVisualState(primary = timer),
            slots = LiveActivityCoordinator.Slots(),
        )

        assertNull(result.primary)
        assertEquals(LiveActivityContainerState.HIDDEN, result.containerState)
        assertEquals(LiveActivityVisualTransition.HIDE, result.transition)
    }

    private fun activity(
        stableId: String,
        kind: LiveActivity.Kind,
        title: String,
        updatedAt: Long,
    ): LiveActivity = LiveActivity(
        stableId = stableId,
        kind = kind,
        title = title,
        updatedElapsedRealtime = updatedAt,
        lifecycle = LiveActivity.Lifecycle.UNTIL_REMOVED,
        sourceKind = when (kind) {
            LiveActivity.Kind.CALL -> LiveActivity.SourceKind.CALL
            LiveActivity.Kind.TIMER -> LiveActivity.SourceKind.TIMER
            LiveActivity.Kind.MUSIC -> LiveActivity.SourceKind.MEDIA_SESSION
            else -> LiveActivity.SourceKind.FALLBACK
        },
    )
}
