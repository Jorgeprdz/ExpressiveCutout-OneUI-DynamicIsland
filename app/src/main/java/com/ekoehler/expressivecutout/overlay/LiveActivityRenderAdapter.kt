package com.ekoehler.expressivecutout.overlay

import com.ekoehler.expressivecutout.core.live.LiveActivity

/** Selects one existing renderer family for a neutral LiveActivity without performing scheduling. */
internal object LiveActivityRenderAdapter {

    /** Preserves the full source payload while choosing the renderer family for its kind. */
    fun adapt(activity: LiveActivity): LiveActivityRenderModel = LiveActivityRenderModel(
        activity = activity,
        renderKind = when (activity.kind) {
            LiveActivity.Kind.CALL -> LiveActivityRenderKind.CALL
            LiveActivity.Kind.TIMER -> LiveActivityRenderKind.TIMER
            LiveActivity.Kind.MUSIC -> LiveActivityRenderKind.MUSIC
            LiveActivity.Kind.GENERIC_PROGRESS -> LiveActivityRenderKind.GENERIC_PROGRESS
            else -> LiveActivityRenderKind.GENERIC_LIVE
        },
    )
}
