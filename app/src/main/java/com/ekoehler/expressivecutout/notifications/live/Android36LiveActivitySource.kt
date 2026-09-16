package com.ekoehler.expressivecutout.notifications.live

import android.app.Notification
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi

/**
 * Reads Android 16 promoted-ongoing evidence and public [Notification.ProgressStyle] data while
 * keeping every API-36 symbol behind one guarded entry point. Failures in OEM builder recovery are
 * treated as missing style metadata, not as a reason to drop the notification.
 */
object Android36LiveActivitySource {
    private const val REQUEST_PROMOTED_ONGOING_EXTRA = "android.requestPromotedOngoing"

    /** Returns native live metadata when supported and evidenced, otherwise null. */
    fun inspect(context: Context, sbn: StatusBarNotification): NativeLiveSnapshot? {
        if (Build.VERSION.SDK_INT < 36) return null
        return inspectApi36(context, sbn.notification)
    }

    /** API-36 implementation separated so older runtimes never execute references to new symbols. */
    @RequiresApi(36)
    private fun inspectApi36(context: Context, notification: Notification): NativeLiveSnapshot? {
        val evidence = NativeLiveEvidence.classify(
            platformAvailable = true,
            promotedFlag = notification.flags and Notification.FLAG_PROMOTED_ONGOING != 0,
            promotable = runCatching { notification.hasPromotableCharacteristics() }.getOrDefault(false),
            requested = notification.extras?.getBoolean(REQUEST_PROMOTED_ONGOING_EXTRA, false) == true,
        )
        val style = runCatching {
            Notification.Builder.recoverBuilder(context, notification).style
        }.getOrNull()
        val progress = (style as? Notification.ProgressStyle)?.toSnapshot()
        if (!evidence.shouldBypassOngoingFilter && progress == null) return null

        return NativeLiveSnapshot(
            evidence = evidence,
            styleKind = if (progress != null) {
                NativeLiveSnapshot.StyleKind.PROGRESS
            } else {
                NativeLiveSnapshot.StyleKind.OTHER
            },
            progress = progress,
        )
    }

    /** Copies mutable framework ProgressStyle collections into immutable app-owned values. */
    @RequiresApi(36)
    private fun Notification.ProgressStyle.toSnapshot(): NativeLiveSnapshot.Progress =
        NativeLiveSnapshot.Progress(
            current = progress,
            max = progressMax,
            indeterminate = isProgressIndeterminate,
            styledByProgress = isStyledByProgress,
            segments = progressSegments.map { segment ->
                NativeLiveSnapshot.Segment(
                    id = segment.id,
                    length = segment.length,
                    color = segment.color,
                )
            },
            points = progressPoints.map { point ->
                NativeLiveSnapshot.Point(
                    id = point.id,
                    position = point.position,
                    color = point.color,
                )
            },
            startIcon = progressStartIcon,
            trackerIcon = progressTrackerIcon,
            endIcon = progressEndIcon,
        )
}
