package com.ekoehler.expressivecutout.notifications.live

import com.ekoehler.expressivecutout.core.live.LiveActivityCoordinator
import com.ekoehler.expressivecutout.core.live.LiveActivityUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Protects semantic update/removal behavior at the coordinator boundary. */
class NotificationCoordinatorIntegrationTest {
    private val parser = SemanticLiveActivityParser()

    @Test
    fun `semantic updates replace one activity and removal clears it`() {
        val coordinator = LiveActivityCoordinator()
        val first = parser.parse(
            signals(text = "Downloading update 10%", current = 10),
            nowElapsedRealtime = 100L,
        ) as LiveActivityUpdate.Upsert
        val second = parser.parse(
            signals(text = "Downloading update 90%", current = 90),
            nowElapsedRealtime = 200L,
        ) as LiveActivityUpdate.Upsert

        coordinator.apply(first)
        coordinator.apply(second)

        assertEquals(1, coordinator.state.value.size)
        assertEquals(90, coordinator.state.value.single().progress?.current)

        coordinator.apply(LiveActivityUpdate.Remove(second.activity.stableId))
        assertTrue(coordinator.state.value.isEmpty())
    }

    private fun signals(text: String, current: Int): NotificationLiveSignals = NotificationLiveSignals(
        packageName = "com.example",
        notificationKey = "key-1",
        text = text,
        ongoing = true,
        clearable = false,
        postTime = 1_000L,
        progressCurrent = current,
        progressMax = 100,
        hasProgressStyle = true,
    )
}
