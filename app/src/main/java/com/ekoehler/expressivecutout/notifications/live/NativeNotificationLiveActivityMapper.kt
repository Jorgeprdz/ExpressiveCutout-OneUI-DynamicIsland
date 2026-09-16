package com.ekoehler.expressivecutout.notifications.live

import com.ekoehler.expressivecutout.core.live.LiveActivity
import com.ekoehler.expressivecutout.core.live.LiveActivityUpdate

/** Maps Android 16 live-update metadata into the framework-independent live-activity model. */
class NativeNotificationLiveActivityMapper {

    /** Builds one coordinator-ready upsert without inventing presentation fields the source omitted. */
    fun map(
        signals: NotificationLiveSignals,
        snapshot: NativeLiveSnapshot,
        stableId: String,
        nowElapsedRealtime: Long,
    ): LiveActivityUpdate.Upsert {
        val progress = snapshot.progress?.toProgress()
        val complete = progress?.let { value ->
            !value.isIndeterminate && value.max > 0 && value.current >= value.max
        } == true
        val kind = if (snapshot.styleKind == NativeLiveSnapshot.StyleKind.PROGRESS) {
            LiveActivity.Kind.GENERIC_PROGRESS
        } else {
            LiveActivity.Kind.NOTIFICATION
        }
        return LiveActivityUpdate.Upsert(
            LiveActivity(
                stableId = stableId,
                kind = kind,
                phase = when {
                    complete -> "complete"
                    progress != null -> "progress"
                    else -> "live"
                },
                title = signals.title,
                subtitle = signals.text ?: signals.subText ?: signals.infoText,
                appName = signals.appName,
                packageName = signals.packageName,
                updatedElapsedRealtime = nowElapsedRealtime,
                lifecycle = LiveActivity.Lifecycle.UNTIL_REMOVED,
                progress = progress,
                contentIntent = signals.contentIntent,
                actions = signals.actions,
                notificationKey = signals.notificationKey,
                sourceKind = LiveActivity.SourceKind.NATIVE_API,
            ),
        )
    }

    /** Converts public ProgressStyle values while clamping malformed OEM ranges defensively. */
    private fun NativeLiveSnapshot.Progress.toProgress(): LiveActivity.Progress? {
        if (indeterminate) {
            return LiveActivity.Progress(
                current = current.coerceAtLeast(0),
                max = max.coerceAtLeast(0),
                isIndeterminate = true,
            )
        }
        if (max <= 0) return null
        return LiveActivity.Progress(
            current = current.coerceIn(0, max),
            max = max,
        )
    }
}
