package com.ekoehler.expressivecutout.notifications.live

import com.ekoehler.expressivecutout.core.live.LiveActivityCoordinator
import com.ekoehler.expressivecutout.core.live.LiveActivityUpdate

/**
 * Listener-facing boundary that applies processor-owned live mutations to one coordinator while
 * returning the exclusive route so legacy presentation can remain outside the live state model.
 */
class NotificationLiveActivityBridge(
    private val coordinator: LiveActivityCoordinator,
    private val processor: NotificationLiveActivityProcessor = NotificationLiveActivityProcessor(),
) {

    /** Processes one post and applies its live mutation exactly once when the winning route owns one. */
    fun post(input: NotificationLiveActivityProcessor.Input): NotificationLiveActivityProcessor.Result {
        val result = processor.process(input)
        result.update?.let(coordinator::apply)
        return result
    }

    /** Removes the exact processor-tracked notification activity and returns the mutation when known. */
    fun remove(packageName: String, notificationKey: String): LiveActivityUpdate.Remove? {
        val update = processor.remove(packageName, notificationKey) ?: return null
        coordinator.apply(update)
        return update
    }
}
