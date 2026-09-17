package com.ekoehler.expressivecutout.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies notification-backed fallback state cannot outlive or duplicate its notification key. */
class NotificationMediaSessionStateTest {

    @Test
    fun `upsert replaces the candidate owned by the same notification key`() {
        val initial = updateNotificationMediaSessionState(
            current = emptyMap(),
            notificationKey = "media-key",
            candidate = "token-a",
        )
        val updated = updateNotificationMediaSessionState(
            current = initial,
            notificationKey = "media-key",
            candidate = "token-b",
        )

        assertEquals(mapOf("media-key" to "token-b"), updated)
    }

    @Test
    fun `notification removal drops its fallback candidate`() {
        val current = mapOf("media-key" to "token-a", "other-key" to "token-b")

        val updated = updateNotificationMediaSessionState(
            current = current,
            notificationKey = "media-key",
            candidate = null,
        )

        assertFalse(updated.containsKey("media-key"))
        assertEquals("token-b", updated["other-key"])
    }

    @Test
    fun `missing token does not create a fallback ghost`() {
        val updated = updateNotificationMediaSessionState<String>(
            current = emptyMap(),
            notificationKey = "media-key",
            candidate = null,
        )

        assertTrue(updated.isEmpty())
    }
}
