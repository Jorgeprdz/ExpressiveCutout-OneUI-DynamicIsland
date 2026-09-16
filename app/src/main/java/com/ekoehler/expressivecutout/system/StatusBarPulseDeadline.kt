package com.ekoehler.expressivecutout.system

/**
 * Pure monotonic-time policy for one extendable transient status-bar pulse deadline.
 */
internal object StatusBarPulseDeadline {

    /** Default time notification icons stay suppressed after a new stable LiveActivity appears. */
    const val DEFAULT_LIVE_ACTIVITY_PULSE_MS = 2_500L

    /**
     * Extends the current deadline from [nowElapsedRealtimeMs]. A non-positive duration disables
     * the pulse instead of creating a near-zero timer.
     */
    fun extend(
        currentDeadlineElapsedRealtimeMs: Long?,
        nowElapsedRealtimeMs: Long,
        durationMs: Long = DEFAULT_LIVE_ACTIVITY_PULSE_MS,
    ): Long? {
        if (durationMs <= 0L) return null
        val candidate = nowElapsedRealtimeMs + durationMs
        return maxOf(currentDeadlineElapsedRealtimeMs ?: candidate, candidate)
    }

    /** Whether [deadlineElapsedRealtimeMs] has not yet expired at [nowElapsedRealtimeMs]. */
    fun isActive(deadlineElapsedRealtimeMs: Long?, nowElapsedRealtimeMs: Long): Boolean =
        deadlineElapsedRealtimeMs != null && nowElapsedRealtimeMs < deadlineElapsedRealtimeMs

    /**
     * Returns true only when the wake-up still owns the current lease and that lease is expired.
     * A newer deadline therefore makes an older wake-up harmless.
     */
    fun shouldExpire(
        observedDeadlineElapsedRealtimeMs: Long,
        currentDeadlineElapsedRealtimeMs: Long?,
        nowElapsedRealtimeMs: Long,
    ): Boolean =
        currentDeadlineElapsedRealtimeMs == observedDeadlineElapsedRealtimeMs &&
            !isActive(currentDeadlineElapsedRealtimeMs, nowElapsedRealtimeMs)

    /** Remaining monotonic milliseconds, clamped to zero once the deadline has expired. */
    fun remainingMs(deadlineElapsedRealtimeMs: Long?, nowElapsedRealtimeMs: Long): Long =
        deadlineElapsedRealtimeMs?.minus(nowElapsedRealtimeMs)?.coerceAtLeast(0L) ?: 0L
}
