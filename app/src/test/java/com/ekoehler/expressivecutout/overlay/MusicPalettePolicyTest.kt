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

class MusicPalettePolicyTest {

    @Test
    fun `album colour disabled keeps current accent`() {
        val fallback = Color(0xFFF472B6)
        val artwork = Color(0xFFFF503F)

        assertEquals(
            fallback,
            resolveMusicPalette(
                useAlbumColours = false,
                fallbackAccent = fallback,
                artworkAccent = artwork,
            ).accent,
        )
    }

    @Test
    fun `album colour enabled without artwork falls back safely`() {
        val fallback = Color(0xFFF472B6)

        assertEquals(
            fallback,
            resolveMusicPalette(
                useAlbumColours = true,
                fallbackAccent = fallback,
                artworkAccent = null,
            ).accent,
        )
    }

    @Test
    fun `track change changes palette identity`() {
        val event = mediaEvent(stableId = "music:spotify:session")
        val first = nowPlaying(title = "Track A", artist = "Artist")
        val second = nowPlaying(title = "Track B", artist = "Artist")

        assertNotEquals(mediaPaletteIdentity(event, first), mediaPaletteIdentity(event, second))
    }

    @Test
    fun `progress update keeps palette identity`() {
        val event = mediaEvent(stableId = "music:spotify:session")
        val first = nowPlaying(
            title = "Track A",
            artist = "Artist",
            progress = MediaProgress(10_000L, 180_000L, 1f, 1_000L),
        )
        val later = nowPlaying(
            title = "Track A",
            artist = "Artist",
            progress = MediaProgress(20_000L, 180_000L, 1f, 11_000L),
        )

        assertEquals(mediaPaletteIdentity(event, first), mediaPaletteIdentity(event, later))
    }

    private fun mediaEvent(stableId: String): IslandEvent = IslandEvent(
        id = 1L,
        icon = IslandIcon.Vector(Icons.Rounded.Notifications),
        label = "Track A",
        detail = "Artist",
        packageName = "com.spotify.music",
        accent = Color(0xFFF472B6),
        stableId = stableId,
        media = MediaTileOptions(showAlbumArt = true, rotateAlbumArt = false, showControls = true),
    )

    private fun nowPlaying(
        title: String?,
        artist: String?,
        progress: MediaProgress? = null,
    ): NowPlaying = NowPlaying(
        packageName = "com.spotify.music",
        title = title,
        artist = artist,
        albumArt = null,
        isPlaying = true,
        transport = object : MediaTransport {
            override fun previous() = Unit
            override fun playPause() = Unit
            override fun next() = Unit
        },
        progress = progress,
    )
}
