package com.ekoehler.expressivecutout.core.live

import android.app.PendingIntent
import androidx.compose.runtime.Immutable

/**
 * One start-to-finish activity that can occupy the island without changing identity as its
 * presentation evolves. Framework-specific notification styles are normalized into this model
 * before the overlay sees them.
 */
@Immutable
data class LiveActivity(
    val stableId: String,
    val kind: Kind,
    val phase: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val appName: String? = null,
    val packageName: String? = null,
    val updatedElapsedRealtime: Long,
    val lifecycle: Lifecycle,
    val progress: Progress? = null,
    val timing: Timing? = null,
    val contentIntent: PendingIntent? = null,
    val actions: List<Action> = emptyList(),
    val notificationKey: String? = null,
    val sourceKind: SourceKind,
    val priority: Int = kind.priorityBand,
    val isSensitive: Boolean = false,
    val expiresAtElapsedRealtime: Long? = null,
) {
    /** The semantic renderer and scheduling class of an activity. */
    enum class Kind(internal val priorityBand: Int) {
        CALL(1_000),
        OTP(900),
        NAVIGATION(700),
        RIDESHARE(700),
        FOOD_ORDER(700),
        PARCEL_DELIVERY(700),
        TIMER(700),
        ASSISTANT(600),
        MUSIC(500),
        WEATHER(300),
        GENERIC_PROGRESS(300),
        NOTIFICATION(200),
    }

    /** How long an activity is eligible to remain registered without a framework removal. */
    enum class Lifecycle {
        TRANSIENT,
        PINNED,
        UNTIL_REMOVED,
    }

    /** Which producer supplied the normalized activity. */
    enum class SourceKind {
        NATIVE_API,
        FALLBACK,
        MEDIA_SESSION,
        CALL,
        TIMER,
        ASSISTANT,
        SYSTEM,
    }

    /** Neutral determinate or indeterminate progress independent of an Android notification style. */
    @Immutable
    data class Progress(
        val current: Int,
        val max: Int,
        val isIndeterminate: Boolean = false,
    )

    /** Timing values expressed on the elapsed-realtime clock so wall-clock changes cannot skew them. */
    @Immutable
    data class Timing(
        val endElapsedRealtimeMs: Long? = null,
        val pausedRemainingMs: Long? = null,
    )

    /** A user-visible action that belongs to this activity. */
    @Immutable
    data class Action(
        val label: String,
        val intent: PendingIntent? = null,
    )
}
