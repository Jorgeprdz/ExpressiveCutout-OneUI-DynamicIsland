package com.ekoehler.expressivecutout.notifications.live

import com.ekoehler.expressivecutout.core.live.LiveActivity
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies specialized producers keep source identity independent of changing presentation metadata. */
class SpecializedLiveActivityFactoryTest {

    @Test
    fun `call identity ignores caller label changes`() {
        val first = SpecializedLiveActivityFactory.call(
            packageName = "com.phone",
            notificationKey = "call-key",
            callerLabel = "Unknown",
            ongoing = false,
            nowElapsedRealtime = 100L,
        )
        val updated = SpecializedLiveActivityFactory.call(
            packageName = "com.phone",
            notificationKey = "call-key",
            callerLabel = "Alice",
            ongoing = true,
            nowElapsedRealtime = 200L,
        )

        assertEquals(first.stableId, updated.stableId)
        assertEquals(LiveActivity.Kind.CALL, updated.kind)
        assertEquals("connected", updated.phase)
    }

    @Test
    fun `timer identity keeps timing updates on one activity`() {
        val first = SpecializedLiveActivityFactory.timer(
            packageName = "com.clock",
            notificationKey = "timer-key",
            label = "Tea",
            endElapsedRealtimeMs = 10_000L,
            pausedRemainingMs = null,
            nowElapsedRealtime = 100L,
        )
        val updated = SpecializedLiveActivityFactory.timer(
            packageName = "com.clock",
            notificationKey = "timer-key",
            label = "Tea",
            endElapsedRealtimeMs = 20_000L,
            pausedRemainingMs = null,
            nowElapsedRealtime = 200L,
        )

        assertEquals(first.stableId, updated.stableId)
        assertEquals(20_000L, updated.timing?.endElapsedRealtimeMs)
    }

    @Test
    fun `music session identity survives track changes`() {
        val first = SpecializedLiveActivityFactory.music(
            packageName = "com.player",
            sessionId = "session-7",
            title = "Track A",
            artist = "Artist",
            isPlaying = true,
            nowElapsedRealtime = 100L,
        )
        val nextTrack = SpecializedLiveActivityFactory.music(
            packageName = "com.player",
            sessionId = "session-7",
            title = "Track B",
            artist = "Artist",
            isPlaying = true,
            nowElapsedRealtime = 200L,
        )

        assertEquals(first.stableId, nextTrack.stableId)
        assertEquals("Track B", nextTrack.title)
        assertEquals("playing", nextTrack.phase)
    }
}
