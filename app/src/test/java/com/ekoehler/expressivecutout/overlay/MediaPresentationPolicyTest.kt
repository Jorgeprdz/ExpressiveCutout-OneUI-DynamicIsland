package com.ekoehler.expressivecutout.overlay

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.ui.graphics.Color
import com.ekoehler.expressivecutout.core.MediaProgress
import com.ekoehler.expressivecutout.core.MediaTransport
import com.ekoehler.expressivecutout.core.NowPlaying
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Regression coverage for nested MUSIC content transitions without replaying island arrival. */
class MediaPresentationPolicyTest {

    @Test
    fun `track change keeps island identity but changes media content key`() {
        val eventA = mediaEvent(label = "Track A", detail = "Artist", stableId = "music:spotify:session")
        val eventB = eventA.copy(id = 2L, label = "Track B")
        val playingA = nowPlaying(title = "Track A", artist = "Artist")
        val playingB = nowPlaying(title = "Track B", artist = "Artist")

        assertEquals(primaryContentKey(eventA), primaryContentKey(eventB))
        assertNotEquals(mediaTrackContentKey(eventA, playingA), mediaTrackContentKey(eventB, playingB))
    }

    @Test
    fun `pause resume keeps media content key for same track`() {
        val event = mediaEvent(label = "Track A", detail = "Artist", stableId = "music:spotify:session")
        val playing = nowPlaying(title = "Track A", artist = "Artist", isPlaying = true)
        val paused = nowPlaying(title = "Track A", artist = "Artist", isPlaying = false)

        assertEquals(mediaTrackContentKey(event, playing), mediaTrackContentKey(event, paused))
    }

    @Test
    fun `progress updates keep media content key for same track`() {
        val event = mediaEvent(label = "Track A", detail = "Artist", stableId = "music:spotify:session")
        val first = nowPlaying(
            title = "Track A",
            artist = "Artist",
            progress = MediaProgress(10_000L, 180_000L, 1f, 1_000L),
        )
        val later = nowPlaying(
            title = "Track A",
            artist = "Artist",
            progress = MediaProgress(45_000L, 180_000L, 1f, 36_000L),
        )

        assertEquals(mediaTrackContentKey(event, first), mediaTrackContentKey(event, later))
    }

    @Test
    fun `different media sessions keep distinct content keys even for same metadata`() {
        val first = mediaEvent(label = "Track A", detail = "Artist", stableId = "music:spotify:session-a")
        val second = mediaEvent(label = "Track A", detail = "Artist", stableId = "music:spotify:session-b")
        val nowPlaying = nowPlaying(title = "Track A", artist = "Artist")

        assertNotEquals(mediaTrackContentKey(first, nowPlaying), mediaTrackContentKey(second, nowPlaying))
    }

    private fun mediaEvent(label: String, detail: String?, stableId: String): IslandEvent = IslandEvent(
        id = 1L,
        icon = IslandIcon.Vector(Icons.Rounded.Notifications),
        label = label,
        detail = detail,
        packageName = "com.spotify.music",
        accent = Color.White,
        stableId = stableId,
        media = MediaTileOptions(showAlbumArt = true, rotateAlbumArt = false, showControls = true),
    )

    private fun nowPlaying(
        title: String?,
        artist: String?,
        isPlaying: Boolean = true,
        progress: MediaProgress? = null,
    ): NowPlaying = NowPlaying(
        packageName = "com.spotify.music",
        title = title,
        artist = artist,
        albumArt = null,
        isPlaying = isPlaying,
        transport = object : MediaTransport {
            override fun previous() = Unit
            override fun playPause() = Unit
            override fun next() = Unit
        },
        progress = progress,
    )
}
