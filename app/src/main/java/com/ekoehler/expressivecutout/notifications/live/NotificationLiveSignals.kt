package com.ekoehler.expressivecutout.notifications.live

import android.app.PendingIntent
import com.ekoehler.expressivecutout.core.live.LiveActivity

/**
 * Content and structured metadata copied from one notification for in-memory semantic analysis.
 * This value is never persisted or logged; private text and OTPs die with the active notification.
 */
data class NotificationLiveSignals(
    val packageName: String,
    val notificationKey: String,
    val title: String? = null,
    val text: String? = null,
    val subText: String? = null,
    val infoText: String? = null,
    val appName: String? = null,
    val category: String? = null,
    val ongoing: Boolean = false,
    val clearable: Boolean = true,
    val postTime: Long = 0L,
    val progressCurrent: Int? = null,
    val progressMax: Int? = null,
    val progressIndeterminate: Boolean = false,
    val chronometer: Boolean = false,
    val countdown: Boolean = false,
    val actionLabels: List<String> = emptyList(),
    val contentIntent: PendingIntent? = null,
    val actions: List<LiveActivity.Action> = emptyList(),
    val foregroundService: Boolean = false,
    val nativeEvidence: NativeLiveEvidence? = null,
    val hasProgressStyle: Boolean = false,
    val additionalText: List<String> = emptyList(),
)
