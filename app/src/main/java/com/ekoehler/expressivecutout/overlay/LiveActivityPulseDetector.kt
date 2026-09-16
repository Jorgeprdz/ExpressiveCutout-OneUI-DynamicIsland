package com.ekoehler.expressivecutout.overlay

/**
 * Detects newly arriving stable LiveActivity identities without confusing content updates,
 * promotion, demotion, or temporary visibility changes with a fresh arrival.
 */
internal object LiveActivityPulseDetector {

    /** Identities already observed during this overlay-controller lifetime. */
    data class State(
        val initialized: Boolean = false,
        val seenStableIds: Set<String> = emptySet(),
    )

    /** Result of observing one canonical slot snapshot and its actually visible projection. */
    data class Result(
        val state: State,
        val newlyVisibleStableIds: Set<String>,
    ) {
        /** Whether at least one genuinely new stable identity became visible. */
        val shouldPulse: Boolean
            get() = newlyVisibleStableIds.isNotEmpty()
    }

    /**
     * Records every canonical identity as seen while only reporting identities that are both new
     * and visible. The first snapshot establishes a baseline and never triggers a pulse.
     */
    fun observe(
        previous: State,
        presentStableIds: Set<String>,
        visibleStableIds: Set<String>,
    ): Result {
        if (!previous.initialized) {
            return Result(
                state = State(
                    initialized = true,
                    seenStableIds = presentStableIds + visibleStableIds,
                ),
                newlyVisibleStableIds = emptySet(),
            )
        }

        val newlyVisible = visibleStableIds - previous.seenStableIds
        return Result(
            state = previous.copy(
                seenStableIds = previous.seenStableIds + presentStableIds + visibleStableIds,
            ),
            newlyVisibleStableIds = newlyVisible,
        )
    }
}
