package com.ekoehler.expressivecutout.notifications.live

/**
 * Keeps the first observed post time for each active framework notification so mutable content,
 * progress, and ETA updates never change its live-activity identity.
 */
class NotificationStableIdentityTracker {
    private val firstObservedByKey = LinkedHashMap<NotificationKey, Long>()

    /** Resolves the stable live-activity ID for the currently active notification instance. */
    fun resolve(packageName: String, notificationKey: String, postTime: Long): String {
        val key = NotificationKey(packageName, notificationKey)
        val firstObserved = firstObservedByKey.getOrPut(key) { postTime }
        return stableId(key, firstObserved)
    }

    /** Removes an active notification identity and returns the exact live-activity ID it owned. */
    fun remove(packageName: String, notificationKey: String): String? {
        val key = NotificationKey(packageName, notificationKey)
        val firstObserved = firstObservedByKey.remove(key) ?: return null
        return stableId(key, firstObserved)
    }

    /** Formats identity from framework ownership plus the immutable first-observed timestamp. */
    private fun stableId(key: NotificationKey, firstObserved: Long): String =
        "notification:${key.packageName}:${key.notificationKey}:$firstObserved"

    /** The framework ownership pair used as the in-memory tracking key. */
    private data class NotificationKey(
        val packageName: String,
        val notificationKey: String,
    )
}
