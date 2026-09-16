package com.ekoehler.expressivecutout.notifications.live

import com.ekoehler.expressivecutout.core.live.LiveActivityCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies listener-facing live routing applies coordinator mutations without duplicating legacy work. */
class NotificationLiveActivityBridgeTest {
    private val coordinator = LiveActivityCoordinator()
    private val bridge = NotificationLiveActivityBridge(coordinator)

    /** A semantic live notification is applied once and returned with its exclusive winning route. */
    @Test
    fun `semantic live post enters coordinator once`() {
        val result = bridge.post(
            NotificationLiveActivityProcessor.Input(
                signals = signals(text = "Your driver is arriving in 3 min"),
                legacySurfaceable = true,
                nowElapsedRealtime = 1_000L,
            ),
        )

        assertEquals(NotificationLiveActivityRouter.Route.SEMANTIC_LIVE, result.route)
        assertEquals(1, coordinator.state.value.size)
        assertEquals(result.update?.let { update ->
            (update as com.ekoehler.expressivecutout.core.live.LiveActivityUpdate.Upsert).activity.stableId
        }, coordinator.state.value.single().stableId)
    }

    /** Ordinary notifications remain legacy-only and leave the live coordinator untouched. */
    @Test
    fun `legacy notification does not mutate coordinator`() {
        val result = bridge.post(
            NotificationLiveActivityProcessor.Input(
                signals = signals(title = "Hello", text = "World"),
                legacySurfaceable = true,
                nowElapsedRealtime = 1_000L,
            ),
        )

        assertEquals(NotificationLiveActivityRouter.Route.LEGACY_NOTIFICATION, result.route)
        assertTrue(coordinator.state.value.isEmpty())
    }

    /** Removal resolves the processor-owned stable identity and removes exactly that activity. */
    @Test
    fun `tracked removal clears the matching live activity`() {
        bridge.post(
            NotificationLiveActivityProcessor.Input(
                signals = signals(text = "Rain starts in 15 min"),
                legacySurfaceable = false,
                nowElapsedRealtime = 1_000L,
            ),
        )

        bridge.remove("com.example", "key-1")

        assertTrue(coordinator.state.value.isEmpty())
    }

    /** Builds one in-memory notification signal for listener-boundary tests. */
    private fun signals(
        title: String? = null,
        text: String? = null,
    ): NotificationLiveSignals = NotificationLiveSignals(
        packageName = "com.example",
        notificationKey = "key-1",
        title = title,
        text = text,
        ongoing = true,
        clearable = false,
        postTime = 1_000L,
    )
}
