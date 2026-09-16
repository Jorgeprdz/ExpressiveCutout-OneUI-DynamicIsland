package com.ekoehler.expressivecutout.core.live

/** Process-wide owner of the single coordinator shared by live-activity producers and the overlay. */
object LiveActivityRegistry {
    /** The registry and slot scheduler for this app process. */
    val coordinator = LiveActivityCoordinator()
}
