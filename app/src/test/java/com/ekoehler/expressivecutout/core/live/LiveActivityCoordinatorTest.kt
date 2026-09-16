package com.ekoehler.expressivecutout.core.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Verifies stable activity identity and deterministic two-slot scheduling. */
class LiveActivityCoordinatorTest {

    @Test
    fun `updating the same stable id replaces fields without duplicating registry entry`() {
        val coordinator = LiveActivityCoordinator()
        val first = activity(
            stableId = "notif:key:100",
            kind = LiveActivity.Kind.RIDESHARE,
            title = "Searching",
            updatedAt = 100L,
        )
        val second = first.copy(title = "Driver assigned", updatedElapsedRealtime = 200L)

        coordinator.upsert(first)
        coordinator.upsert(second)

        assertEquals(1, coordinator.state.value.size)
        assertEquals("notif:key:100", coordinator.state.value.single().stableId)
        assertEquals("Driver assigned", coordinator.state.value.single().title)
    }

    @Test
    fun `call always owns primary while music is retained as satellite`() {
        val coordinator = LiveActivityCoordinator()
        val music = activity(
            stableId = "music:session",
            kind = LiveActivity.Kind.MUSIC,
            title = "Track",
            updatedAt = 200L,
        )
        val call = activity(
            stableId = "call:key",
            kind = LiveActivity.Kind.CALL,
            title = "Incoming call",
            updatedAt = 100L,
        )

        coordinator.upsert(music)
        coordinator.upsert(call)

        assertEquals(call.stableId, coordinator.slots.value.primary?.stableId)
        assertEquals(music.stableId, coordinator.slots.value.satellite?.stableId)
        assertEquals(2, coordinator.state.value.size)
    }

    @Test
    fun `call is never selected as satellite`() {
        val coordinator = LiveActivityCoordinator()
        val firstCall = activity("call:one", LiveActivity.Kind.CALL, "One", 200L)
        val secondCall = activity("call:two", LiveActivity.Kind.CALL, "Two", 100L)

        coordinator.upsert(firstCall)
        coordinator.upsert(secondCall)

        assertEquals(firstCall.stableId, coordinator.slots.value.primary?.stableId)
        assertNull(coordinator.slots.value.satellite)
    }

    @Test
    fun `removing primary promotes retained music without creating a new activity`() {
        val coordinator = LiveActivityCoordinator()
        val music = activity("music:session", LiveActivity.Kind.MUSIC, "Track", 100L)
        val call = activity("call:key", LiveActivity.Kind.CALL, "Call", 200L)

        coordinator.upsert(music)
        coordinator.upsert(call)
        val registeredMusic = coordinator.state.value.first { it.stableId == music.stableId }

        coordinator.remove(call.stableId)

        assertEquals(music.stableId, coordinator.slots.value.primary?.stableId)
        assertNull(coordinator.slots.value.satellite)
        assertSame(registeredMusic, coordinator.state.value.single())
    }

    @Test
    fun `more recent activity wins inside the same priority band`() {
        val coordinator = LiveActivityCoordinator()
        val older = activity("timer:old", LiveActivity.Kind.TIMER, "Old", 100L)
        val newer = activity("ride:new", LiveActivity.Kind.RIDESHARE, "New", 200L)

        coordinator.upsert(older)
        coordinator.upsert(newer)

        assertEquals(newer.stableId, coordinator.slots.value.primary?.stableId)
        assertEquals(older.stableId, coordinator.slots.value.satellite?.stableId)
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
        sourceKind = LiveActivity.SourceKind.FALLBACK,
    )
}
