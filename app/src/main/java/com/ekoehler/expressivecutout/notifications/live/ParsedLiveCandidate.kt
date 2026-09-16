package com.ekoehler.expressivecutout.notifications.live

import com.ekoehler.expressivecutout.core.live.LiveActivity

/**
 * Privacy-safe semantic result produced from [NotificationLiveSignals]. Evidence contains only
 * classifier codes, never notification text or extracted OTP values.
 */
data class ParsedLiveCandidate(
    val kind: LiveActivity.Kind,
    val phase: String? = null,
    val confidence: Int,
    val evidenceCodes: Set<String>,
    val lifecycle: LiveActivity.Lifecycle,
    val ttlMs: Long? = null,
    val isSensitive: Boolean = false,
    val progress: LiveActivity.Progress? = null,
    val timing: LiveActivity.Timing? = null,
)
