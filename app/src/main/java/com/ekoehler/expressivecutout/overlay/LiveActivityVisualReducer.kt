package com.ekoehler.expressivecutout.overlay

import com.ekoehler.expressivecutout.core.live.LiveActivityCoordinator

/** Reduces coordinator slots using stable IDs so metadata updates never masquerade as arrivals. */
internal object LiveActivityVisualReducer {

    /** Projects [slots] onto the previous visual state without re-ranking either slot. */
    fun reduce(
        previous: LiveActivityVisualState,
        slots: LiveActivityCoordinator.Slots,
    ): LiveActivityVisualState {
        val oldPrimaryId = previous.primary?.stableId
        val oldSatelliteId = previous.satellite?.stableId
        val newPrimaryId = slots.primary?.stableId
        val newSatelliteId = slots.satellite?.stableId

        val transition = when {
            newPrimaryId == null && newSatelliteId == null ->
                if (oldPrimaryId != null || oldSatelliteId != null) {
                    LiveActivityVisualTransition.HIDE
                } else {
                    LiveActivityVisualTransition.NONE
                }

            oldSatelliteId != null && newPrimaryId == oldSatelliteId ->
                LiveActivityVisualTransition.PROMOTE_SATELLITE

            oldPrimaryId != null && newSatelliteId == oldPrimaryId && newPrimaryId != oldPrimaryId ->
                LiveActivityVisualTransition.DEMOTE_PRIMARY_TO_SATELLITE

            oldPrimaryId == null && newPrimaryId != null ->
                LiveActivityVisualTransition.REVEAL

            oldPrimaryId == newPrimaryId -> when {
                oldSatelliteId == newSatelliteId -> LiveActivityVisualTransition.NONE
                oldSatelliteId == null && newSatelliteId != null -> LiveActivityVisualTransition.ADD_SATELLITE
                oldSatelliteId != null && newSatelliteId == null -> LiveActivityVisualTransition.REMOVE_SATELLITE
                else -> LiveActivityVisualTransition.ADD_SATELLITE
            }

            newPrimaryId != null -> LiveActivityVisualTransition.REPLACE_PRIMARY
            else -> LiveActivityVisualTransition.HIDE
        }

        val containerState = when {
            newPrimaryId == null -> LiveActivityContainerState.HIDDEN
            oldPrimaryId == newPrimaryId && previous.containerState == LiveActivityContainerState.EXPANDED ->
                LiveActivityContainerState.EXPANDED
            else -> LiveActivityContainerState.COLLAPSED
        }

        return LiveActivityVisualState(
            primary = slots.primary,
            satellite = slots.satellite,
            containerState = containerState,
            transition = transition,
        )
    }

    /** Changes only expansion state while retaining slot identity and content. */
    fun setExpanded(previous: LiveActivityVisualState, expanded: Boolean): LiveActivityVisualState {
        if (previous.primary == null) return previous.copy(containerState = LiveActivityContainerState.HIDDEN)
        val target = if (expanded) LiveActivityContainerState.EXPANDED else LiveActivityContainerState.COLLAPSED
        if (previous.containerState == target) return previous.copy(transition = LiveActivityVisualTransition.NONE)
        return previous.copy(
            containerState = target,
            transition = if (expanded) LiveActivityVisualTransition.EXPAND else LiveActivityVisualTransition.COLLAPSE,
        )
    }
}
