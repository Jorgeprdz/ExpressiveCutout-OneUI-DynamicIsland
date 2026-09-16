package com.ekoehler.expressivecutout.notifications.live

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import com.ekoehler.expressivecutout.core.live.LiveActivity

/** Copies framework notification state into short-lived neutral values used by live routing. */
object NotificationLiveSignalsExtractor {

    /** Extracts semantic signals together with any Android 16 native snapshot available for routing. */
    fun extract(context: Context, sbn: StatusBarNotification): Result {
        val notification = sbn.notification
        val extras = notification.extras
        val nativeSnapshot = Android36LiveActivitySource.inspect(context, sbn)
        val nativeProgress = nativeSnapshot?.progress
        val actionPairs = notification.actions.orEmpty().mapNotNull { action ->
            val label = action.title?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val intent = action.actionIntent ?: return@mapNotNull null
            label to LiveActivity.Action(label = label, intent = intent)
        }
        val progressMax = nativeProgress?.max
            ?: extras?.getInt(Notification.EXTRA_PROGRESS_MAX, 0)?.takeIf { it > 0 }
        val progressCurrent = nativeProgress?.current
            ?: extras?.getInt(Notification.EXTRA_PROGRESS, 0)?.takeIf { progressMax != null }
        val progressIndeterminate = nativeProgress?.indeterminate
            ?: (extras?.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false) == true)

        return Result(
            signals = NotificationLiveSignals(
                packageName = sbn.packageName,
                notificationKey = sbn.key,
                title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
                infoText = extras?.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString(),
                appName = context.applicationLabel(sbn.packageName),
                category = notification.category,
                ongoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
                clearable = sbn.isClearable,
                postTime = sbn.postTime,
                progressCurrent = progressCurrent,
                progressMax = progressMax,
                progressIndeterminate = progressIndeterminate,
                chronometer = extras?.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) == true,
                countdown = extras?.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false) == true,
                actionLabels = actionPairs.map { it.first },
                contentIntent = notification.contentIntent,
                actions = actionPairs.map { it.second },
                foregroundService = notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
                nativeEvidence = nativeSnapshot?.evidence,
                hasProgressStyle = nativeSnapshot?.styleKind == NativeLiveSnapshot.StyleKind.PROGRESS,
                additionalText = extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                    ?.map { it.toString() }
                    .orEmpty(),
            ),
            nativeSnapshot = nativeSnapshot,
        )
    }

    /** Framework extraction result kept in memory only for the current notification callback. */
    data class Result(
        val signals: NotificationLiveSignals,
        val nativeSnapshot: NativeLiveSnapshot?,
    )

    /** Resolves a package label without making notification extraction fail when package state races. */
    private fun Context.applicationLabel(packageName: String): String? = runCatching {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrNull()
}
