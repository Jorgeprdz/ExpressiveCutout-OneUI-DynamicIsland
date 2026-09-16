package com.ekoehler.expressivecutout.overlay

import com.ekoehler.expressivecutout.core.live.LiveActivity
import com.ekoehler.expressivecutout.core.live.LiveActivityCoordinator

/**
 * Projects coordinator slots into the stable LiveActivity identities actually visible on the
 * overlay, including the one-slot tradeoff when a transient legacy event is temporarily on top.
 */
internal object LiveActivityPulseVisibility {

    /**
     * Returns the stable IDs the user can currently see without treating a hidden or displaced
     * activity as visible merely because it remains present in the coordinator.
     */
    fun visibleStableIds(
        slots: LiveActivityCoordinator.Slots,
        overlayCanProject: Boolean,
        splitAllowed: Boolean,
        transientOnTop: Boolean,
    ): Set<String> {
        if (!overlayCanProject) return emptySet()
        val primary = slots.primary ?: return emptySet()
        val callMustLead = primary.kind == LiveActivity.Kind.CALL

        if (transientOnTop && !callMustLead) {
            return if (splitAllowed) setOf(primary.stableId) else emptySet()
        }

        return buildSet {
            add(primary.stableId)
            if (splitAllowed) slots.satellite?.stableId?.let(::add)
        }
    }
}
