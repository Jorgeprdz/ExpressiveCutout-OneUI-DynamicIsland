package com.ekoehler.expressivecutout.core.live

/** A mutation emitted by a [LiveActivitySource] and consumed by [LiveActivityCoordinator]. */
sealed interface LiveActivityUpdate {
    /** Inserts a new activity or replaces the fields of the registered activity with the same ID. */
    data class Upsert(val activity: LiveActivity) : LiveActivityUpdate

    /** Removes one activity without affecting other registered work. */
    data class Remove(
        val stableId: String,
        val reason: RemovalReason = RemovalReason.SOURCE_REMOVED,
    ) : LiveActivityUpdate

    /** Why an activity left the coordinator, kept content-free so private notification text is never logged. */
    enum class RemovalReason {
        SOURCE_REMOVED,
        EXPIRED,
        COMPLETED,
        CANCELLED,
        DISMISSED,
    }
}
