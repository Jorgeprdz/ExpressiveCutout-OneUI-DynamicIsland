package com.ekoehler.expressivecutout.overlay

import com.ekoehler.expressivecutout.core.live.LiveActivity

/** Immutable render projection that keeps the original LiveActivity payload and stable identity. */
internal data class LiveActivityRenderModel(
    val activity: LiveActivity,
    val renderKind: LiveActivityRenderKind,
) {
    val stableId: String get() = activity.stableId
    val title: String? get() = activity.title
    val subtitle: String? get() = activity.subtitle
    val phase: String? get() = activity.phase
    val progress: LiveActivity.Progress? get() = activity.progress
    val timing: LiveActivity.Timing? get() = activity.timing
}
