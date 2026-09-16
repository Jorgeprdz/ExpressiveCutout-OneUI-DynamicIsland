package com.ekoehler.expressivecutout.notifications.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifies notification identity survives updates but not removal and replacement. */
class NotificationStableIdentityTrackerTest {

    @Test
    fun `same notification key keeps first observed identity across updates`() {
        val tracker = NotificationStableIdentityTracker()
        val first = tracker.resolve("com.example", "key-1", 1_000L)
        val etaUpdate = tracker.resolve("com.example", "key-1", 2_000L)
        val progressUpdate = tracker.resolve("com.example", "key-1", 3_000L)

        assertEquals(first, etaUpdate)
        assertEquals(first, progressUpdate)
        assertEquals("notification:com.example:key-1:1000", first)
    }

    @Test
    fun `different keys and packages never collide`() {
        val tracker = NotificationStableIdentityTracker()
        val first = tracker.resolve("com.example.a", "key", 100L)
        val second = tracker.resolve("com.example.a", "other", 100L)
        val third = tracker.resolve("com.example.b", "key", 100L)

        assertNotEquals(first, second)
        assertNotEquals(first, third)
    }

    @Test
    fun `remove returns tracked id and replacement gets a new identity`() {
        val tracker = NotificationStableIdentityTracker()
        val original = tracker.resolve("com.example", "key-1", 1_000L)

        assertEquals(original, tracker.remove("com.example", "key-1"))

        val replacement = tracker.resolve("com.example", "key-1", 9_000L)
        assertNotEquals(original, replacement)
        assertEquals("notification:com.example:key-1:9000", replacement)
    }

    @Test
    fun `removing unknown notification does not affect tracked activity`() {
        val tracker = NotificationStableIdentityTracker()
        val known = tracker.resolve("com.example", "known", 100L)

        assertNull(tracker.remove("com.example", "unknown"))
        assertEquals(known, tracker.resolve("com.example", "known", 500L))
    }
}
