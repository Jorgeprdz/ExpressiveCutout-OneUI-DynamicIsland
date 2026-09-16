package com.ekoehler.expressivecutout.notifications.live

import com.ekoehler.expressivecutout.core.live.LiveActivity
import com.ekoehler.expressivecutout.core.live.LiveActivityUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies semantic candidates become stable privacy-aware live-activity updates. */
class SemanticLiveActivityParserTest {

    private val parser = SemanticLiveActivityParser()

    @Test
    fun `same notification keeps stable identity across progress updates`() {
        val first = parser.parse(
            signals(text = "Downloading update - 10% complete", progressCurrent = 10, progressMax = 100),
            nowElapsedRealtime = 1_000L,
        ) as LiveActivityUpdate.Upsert
        val second = parser.parse(
            signals(text = "Downloading update - 90% complete", progressCurrent = 90, progressMax = 100),
            nowElapsedRealtime = 2_000L,
        ) as LiveActivityUpdate.Upsert

        assertEquals(first.activity.stableId, second.activity.stableId)
        assertEquals(10, first.activity.progress?.current)
        assertEquals(90, second.activity.progress?.current)
        assertNotEquals(first.activity.updatedElapsedRealtime, second.activity.updatedElapsedRealtime)
    }

    @Test
    fun `otp is transient sensitive and never uses otp text as identity`() {
        val update = parser.parse(
            signals(text = "Your verification code is 482193"),
            nowElapsedRealtime = 5_000L,
        ) as LiveActivityUpdate.Upsert

        assertEquals(LiveActivity.Kind.OTP, update.activity.kind)
        assertEquals(LiveActivity.Lifecycle.TRANSIENT, update.activity.lifecycle)
        assertTrue(update.activity.isSensitive)
        assertTrue(update.activity.expiresAtElapsedRealtime != null)
        assertTrue("482193" !in update.activity.stableId)
    }

    @Test
    fun `different notification keys do not collide`() {
        val first = parser.parse(
            signals(notificationKey = "key-A", text = "Rain starts in 15 min"),
            nowElapsedRealtime = 10L,
        ) as LiveActivityUpdate.Upsert
        val second = parser.parse(
            signals(notificationKey = "key-B", text = "Rain starts in 15 min"),
            nowElapsedRealtime = 20L,
        ) as LiveActivityUpdate.Upsert

        assertNotEquals(first.activity.stableId, second.activity.stableId)
    }

    @Test
    fun `ordinary notification returns null`() {
        assertNull(
            parser.parse(
                signals(title = "Hello", text = "Welcome back"),
                nowElapsedRealtime = 100L,
            ),
        )
    }

    @Test
    fun `parser preserves available presentation fields without inventing missing values`() {
        val update = parser.parse(
            signals(
                title = "Delivery",
                text = "Package out for delivery - 2 stops away",
            ),
            nowElapsedRealtime = 1_000L,
        ) as LiveActivityUpdate.Upsert

        assertEquals("Delivery", update.activity.title)
        assertEquals("Package out for delivery - 2 stops away", update.activity.subtitle)
        assertNull(update.activity.appName)
        assertEquals("com.example.app", update.activity.packageName)
        assertEquals("key-1", update.activity.notificationKey)
        assertEquals(LiveActivity.SourceKind.FALLBACK, update.activity.sourceKind)
    }

    private fun signals(
        notificationKey: String = "key-1",
        title: String? = null,
        text: String? = null,
        progressCurrent: Int? = null,
        progressMax: Int? = null,
    ): NotificationLiveSignals = NotificationLiveSignals(
        packageName = "com.example.app",
        notificationKey = notificationKey,
        title = title,
        text = text,
        ongoing = true,
        clearable = false,
        postTime = 1_700_000_000_000L,
        progressCurrent = progressCurrent,
        progressMax = progressMax,
        hasProgressStyle = progressCurrent != null || progressMax != null,
    )
}
