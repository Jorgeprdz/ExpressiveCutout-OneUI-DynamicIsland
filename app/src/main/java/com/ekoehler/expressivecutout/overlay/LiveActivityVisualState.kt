package com.ekoehler.expressivecutout.overlay

import com.ekoehler.expressivecutout.core.live.LiveActivity

/** Renderer-facing snapshot derived directly from the coordinator's primary and satellite slots. */
internal data class LiveActivityVisualState(
    val primary: LiveActivity? = null,
    val satellite: LiveActivity? = null,
    val containerState: LiveActivityContainerState = LiveActivityContainerState.HIDDEN,
    val transition: LiveActivityVisualTransition = LiveActivityVisualTransition.NONE,
)
