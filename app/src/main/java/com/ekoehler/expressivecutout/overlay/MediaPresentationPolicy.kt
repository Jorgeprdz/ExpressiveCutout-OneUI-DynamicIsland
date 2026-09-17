package com.ekoehler.expressivecutout.overlay

import com.ekoehler.expressivecutout.core.NowPlaying

/**
 * Stable identity for the currently presented music track. Session identity keeps two players with
 * identical metadata apart, while title/artist changes are the only updates that replace track
 * content; play state and progress deliberately do not participate.
 */
internal data class MediaTrackContentKey(
    val sessionIdentity: String,
    val title: String,
    val artist: String,
)

/**
 * Builds the nested MUSIC-content key without changing the island's own primary identity. Live
 * [NowPlaying] metadata wins when it belongs to the same package; the event payload is the fallback
 * while the media session is still settling.
 */
internal fun mediaTrackContentKey(event: IslandEvent, nowPlaying: NowPlaying?): MediaTrackContentKey {
    val matchingPlayback = nowPlaying?.takeIf { playback ->
        event.packageName == null || event.packageName == playback.packageName
    }
    val title = matchingPlayback?.title?.takeIf { it.isNotBlank() }
        ?: event.label
    val artist = matchingPlayback?.artist?.takeIf { it.isNotBlank() }
        ?: event.detail.orEmpty()
    return MediaTrackContentKey(
        sessionIdentity = event.stableId ?: event.visualIdentity,
        title = title,
        artist = artist,
    )
}
