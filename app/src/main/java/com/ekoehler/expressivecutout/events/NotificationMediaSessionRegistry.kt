package com.ekoehler.expressivecutout.events

import android.media.session.MediaSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** MediaSession token discovered from a player's notification rather than MediaSessionManager. */
internal data class NotificationMediaSessionCandidate(
    val notificationKey: String,
    val packageName: String,
    val token: MediaSession.Token,
) {
    val identity: String
        get() = mediaSessionIdentity(packageName, token.hashCode())
}

/**
 * Process-local bridge from NotificationListenerService to MediaPlaybackMonitor. It stores only
 * session identity/token ownership; metadata and rendering remain owned by MediaPlaybackMonitor.
 */
internal object NotificationMediaSessionRegistry {
    private val _sessions = MutableStateFlow<Map<String, NotificationMediaSessionCandidate>>(emptyMap())
    val sessions: StateFlow<Map<String, NotificationMediaSessionCandidate>> = _sessions.asStateFlow()

    fun update(notificationKey: String, packageName: String, token: MediaSession.Token?) {
        val candidate = token?.let {
            NotificationMediaSessionCandidate(
                notificationKey = notificationKey,
                packageName = packageName,
                token = it,
            )
        }
        _sessions.value = updateNotificationMediaSessionState(
            current = _sessions.value,
            notificationKey = notificationKey,
            candidate = candidate,
        )
    }

    fun remove(notificationKey: String) {
        _sessions.value = updateNotificationMediaSessionState(
            current = _sessions.value,
            notificationKey = notificationKey,
            candidate = null,
        )
    }

    fun clear() {
        _sessions.value = emptyMap()
    }
}

/** Pure ownership update used by the registry and unit tests. A null candidate removes the key. */
internal fun <T> updateNotificationMediaSessionState(
    current: Map<String, T>,
    notificationKey: String,
    candidate: T?,
): Map<String, T> {
    val updated = LinkedHashMap(current)
    if (candidate == null) {
        updated.remove(notificationKey)
    } else {
        updated[notificationKey] = candidate
    }
    return updated
}
