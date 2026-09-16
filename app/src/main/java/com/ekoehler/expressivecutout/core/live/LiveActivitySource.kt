package com.ekoehler.expressivecutout.core.live

import kotlinx.coroutines.flow.Flow

/**
 * Lifecycle-aware producer of normalized live-activity mutations. Implementations register framework
 * callbacks in [start] and undo those registrations in [stop].
 */
interface LiveActivitySource {
    /** Mutations emitted while the source is active. */
    val updates: Flow<LiveActivityUpdate>

    /** Starts observing the underlying framework source. */
    fun start()

    /** Stops observation and releases every registration created by [start]. */
    fun stop()
}
