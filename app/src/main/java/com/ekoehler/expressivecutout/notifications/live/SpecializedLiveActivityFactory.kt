package com.ekoehler.expressivecutout.notifications.live

import android.app.PendingIntent
import com.ekoehler.expressivecutout.core.live.LiveActivity

/** Builds stable live-activity values for specialized call, timer, and media producers. */
object SpecializedLiveActivityFactory {

    /** Builds a call whose identity follows the framework notification rather than mutable caller data. */
    fun call(
        packageName: String,
        notificationKey: String,
        callerLabel: String?,
        ongoing: Boolean,
        nowElapsedRealtime: Long,
        contentIntent: PendingIntent? = null,
        actions: List<LiveActivity.Action> = emptyList(),
    ): LiveActivity = LiveActivity(
        stableId = callId(packageName, notificationKey),
        kind = LiveActivity.Kind.CALL,
        phase = if (ongoing) "connected" else "incoming",
        title = callerLabel,
        packageName = packageName,
        updatedElapsedRealtime = nowElapsedRealtime,
        lifecycle = LiveActivity.Lifecycle.UNTIL_REMOVED,
        contentIntent = contentIntent,
        actions = actions,
        notificationKey = notificationKey,
        sourceKind = LiveActivity.SourceKind.CALL,
    )

    /** Builds a timer whose identity survives remaining-time and pause-state updates. */
    fun timer(
        packageName: String,
        notificationKey: String,
        label: String?,
        endElapsedRealtimeMs: Long?,
        pausedRemainingMs: Long?,
        nowElapsedRealtime: Long,
        contentIntent: PendingIntent? = null,
        actions: List<LiveActivity.Action> = emptyList(),
    ): LiveActivity = LiveActivity(
        stableId = timerId(packageName, notificationKey),
        kind = LiveActivity.Kind.TIMER,
        phase = if (pausedRemainingMs != null) "paused" else "running",
        title = label,
        packageName = packageName,
        updatedElapsedRealtime = nowElapsedRealtime,
        lifecycle = LiveActivity.Lifecycle.UNTIL_REMOVED,
        timing = LiveActivity.Timing(
            endElapsedRealtimeMs = endElapsedRealtimeMs,
            pausedRemainingMs = pausedRemainingMs,
        ),
        contentIntent = contentIntent,
        actions = actions,
        notificationKey = notificationKey,
        sourceKind = LiveActivity.SourceKind.TIMER,
    )

    /** Builds media state keyed to one session so track and playback metadata remain in-place updates. */
    fun music(
        packageName: String,
        sessionId: String,
        title: String?,
        artist: String?,
        isPlaying: Boolean,
        nowElapsedRealtime: Long,
        contentIntent: PendingIntent? = null,
    ): LiveActivity = LiveActivity(
        stableId = musicId(packageName, sessionId),
        kind = LiveActivity.Kind.MUSIC,
        phase = if (isPlaying) "playing" else "paused",
        title = title,
        subtitle = artist,
        packageName = packageName,
        updatedElapsedRealtime = nowElapsedRealtime,
        lifecycle = LiveActivity.Lifecycle.UNTIL_REMOVED,
        contentIntent = contentIntent,
        sourceKind = LiveActivity.SourceKind.MEDIA_SESSION,
    )

    /** Returns the stable coordinator identity for one call notification. */
    fun callId(packageName: String, notificationKey: String): String =
        "call:$packageName:$notificationKey"

    /** Returns the stable coordinator identity for one timer notification. */
    fun timerId(packageName: String, notificationKey: String): String =
        "timer:$packageName:$notificationKey"

    /** Returns the stable coordinator identity for one media session. */
    fun musicId(packageName: String, sessionId: String): String =
        "music:$packageName:$sessionId"
}
