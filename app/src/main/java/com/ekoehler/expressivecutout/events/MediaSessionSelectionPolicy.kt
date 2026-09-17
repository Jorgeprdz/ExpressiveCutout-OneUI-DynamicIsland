package com.ekoehler.expressivecutout.events

/** Source of a candidate media session surfaced to [MediaPlaybackMonitor]. */
internal enum class MediaSessionCandidateSource {
    ACTIVE,
    NOTIFICATION_FALLBACK,
}

/** Minimal, Android-free description used to choose one logical media session. */
internal data class MediaSessionSelectionCandidate(
    val identity: String,
    val source: MediaSessionCandidateSource,
    val isPlaying: Boolean,
)

/**
 * Chooses one logical media session while preferring framework-discovered sessions over their
 * notification-backed equivalent. Duplicate identities collapse to the ACTIVE source so a session
 * moving from fallback to MediaSessionManager never becomes two music activities.
 */
internal fun selectMediaSessionCandidate(
    candidates: List<MediaSessionSelectionCandidate>,
): MediaSessionSelectionCandidate? {
    val uniqueByIdentity = linkedMapOf<String, MediaSessionSelectionCandidate>()
    candidates.forEach { candidate ->
        val existing = uniqueByIdentity[candidate.identity]
        if (
            existing == null ||
            candidate.source == MediaSessionCandidateSource.ACTIVE &&
            existing.source != MediaSessionCandidateSource.ACTIVE
        ) {
            uniqueByIdentity[candidate.identity] = candidate
        }
    }

    val unique = uniqueByIdentity.values.toList()
    val active = unique.filter { it.source == MediaSessionCandidateSource.ACTIVE }
    val fallback = unique.filter { it.source == MediaSessionCandidateSource.NOTIFICATION_FALLBACK }

    return active.firstOrNull { it.isPlaying }
        ?: active.firstOrNull()
        ?: fallback.firstOrNull { it.isPlaying }
        ?: fallback.firstOrNull()
}

/** Stable identity shared by active and notification-created controllers for the same token. */
internal fun mediaSessionIdentity(packageName: String, tokenHash: Int): String =
    "$packageName:$tokenHash"
