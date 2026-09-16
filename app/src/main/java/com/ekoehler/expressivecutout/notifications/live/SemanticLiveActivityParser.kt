package com.ekoehler.expressivecutout.notifications.live

import com.ekoehler.expressivecutout.core.live.LiveActivity
import com.ekoehler.expressivecutout.core.live.LiveActivityUpdate

/** Converts classified notification signals into stable coordinator-ready fallback updates. */
class SemanticLiveActivityParser(
    private val classifier: SemanticLiveActivityClassifier = SemanticLiveActivityClassifier(),
) {

    /**
     * Returns one stable upsert when semantic evidence is strong enough, otherwise null. The stable
     * identifier deliberately excludes changing title, phase, progress, ETA, and sensitive content.
     */
    fun parse(
        signals: NotificationLiveSignals,
        nowElapsedRealtime: Long,
    ): LiveActivityUpdate? {
        val candidate = classifier.classify(signals) ?: return null
        val expiresAt = candidate.ttlMs?.let { ttl -> nowElapsedRealtime + ttl }
        return LiveActivityUpdate.Upsert(
            LiveActivity(
                stableId = stableId(signals),
                kind = candidate.kind,
                phase = candidate.phase,
                title = signals.title,
                subtitle = signals.text ?: signals.subText ?: signals.infoText,
                appName = signals.appName,
                packageName = signals.packageName,
                updatedElapsedRealtime = nowElapsedRealtime,
                lifecycle = candidate.lifecycle,
                progress = candidate.progress,
                timing = candidate.timing,
                contentIntent = signals.contentIntent,
                actions = signals.actions,
                notificationKey = signals.notificationKey,
                sourceKind = LiveActivity.SourceKind.FALLBACK,
                isSensitive = candidate.isSensitive,
                expiresAtElapsedRealtime = expiresAt,
            ),
        )
    }

    /** Builds notification identity from framework-stable ownership and key only. */
    private fun stableId(signals: NotificationLiveSignals): String =
        "notification:${signals.packageName}:${signals.notificationKey}"
}
