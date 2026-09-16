package com.ekoehler.expressivecutout.core.live

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the in-memory live-activity registry and deterministically selects the two activities eligible
 * for the primary and satellite island slots. Updating an existing stable ID never removes and
 * reinserts unrelated activities, so their identity and insertion order remain intact.
 */
class LiveActivityCoordinator {
    private val activities = LinkedHashMap<String, LiveActivity>()
    private val _state = MutableStateFlow<List<LiveActivity>>(emptyList())
    private val _slots = MutableStateFlow(Slots())

    /** Registered activities in stable insertion order. */
    val state: StateFlow<List<LiveActivity>> = _state.asStateFlow()

    /** The currently selected visible candidates, before device-width/layout suppression. */
    val slots: StateFlow<Slots> = _slots.asStateFlow()

    /** Inserts [activity] or updates the registered object with the same stable identity. */
    fun upsert(activity: LiveActivity) {
        activities[activity.stableId] = activity
        publish()
    }

    /** Removes [stableId] if it is still registered. */
    fun remove(stableId: String) {
        if (activities.remove(stableId) != null) publish()
    }

    /** Applies one source mutation to the registry. */
    fun apply(update: LiveActivityUpdate) {
        when (update) {
            is LiveActivityUpdate.Upsert -> upsert(update.activity)
            is LiveActivityUpdate.Remove -> remove(update.stableId)
        }
    }

    /** Publishes a consistent registry snapshot and recomputes its visible candidates. */
    private fun publish() {
        val snapshot = activities.values.toList()
        _state.value = snapshot
        _slots.value = selectSlots(snapshot)
    }

    /** Chooses primary and satellite by policy without placing a call in the satellite slot. */
    private fun selectSlots(snapshot: List<LiveActivity>): Slots {
        val ranked = snapshot.sortedWith(
            compareByDescending<LiveActivity> { effectivePriority(it) }
                .thenByDescending { it.updatedElapsedRealtime },
        )
        val primary = ranked.firstOrNull()
        val satellite = ranked.asSequence()
            .drop(1)
            .firstOrNull { candidate ->
                candidate.kind != LiveActivity.Kind.CALL && candidate.stableId != primary?.stableId
            }
        return Slots(primary = primary, satellite = satellite)
    }

    /** Makes calls non-demotable even if a future source supplies a custom priority override. */
    private fun effectivePriority(activity: LiveActivity): Int =
        if (activity.kind == LiveActivity.Kind.CALL) Int.MAX_VALUE else activity.priority

    /** The two candidates that the overlay may render at once. */
    data class Slots(
        val primary: LiveActivity? = null,
        val satellite: LiveActivity? = null,
    )
}
