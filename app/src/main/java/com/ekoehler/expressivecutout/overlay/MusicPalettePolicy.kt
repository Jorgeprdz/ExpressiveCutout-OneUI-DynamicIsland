package com.ekoehler.expressivecutout.overlay

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.ekoehler.expressivecutout.core.NowPlaying

/** Music-only colours; null surface roles mean the existing island appearance remains untouched. */
internal data class MusicPalette(
    val accent: Color,
    val surface: Color? = null,
    val onSurface: Color? = null,
    val secondaryAccent: Color? = null,
)

/** Resolves album-derived colours, falling back deterministically to the tile's existing accent. */
internal fun resolveMusicPalette(
    useAlbumColours: Boolean,
    fallbackAccent: Color,
    artworkAccent: Color?,
): MusicPalette {
    if (!useAlbumColours) return MusicPalette(accent = fallbackAccent)
    val accent = artworkAccent ?: fallbackAccent
    val surface = lerp(accent, Color(0xFF121216), 0.78f)
    val onSurface = if (surface.luminance() > 0.5f) Color(0xFF0A0A0A) else Color(0xFFF5F5F5)
    return MusicPalette(
        accent = accent,
        surface = surface,
        onSurface = onSurface,
        secondaryAccent = lerp(accent, onSurface, 0.22f),
    )
}

/** Palette identity follows track identity, so progress and play-state updates cannot churn it. */
internal fun mediaPaletteIdentity(event: IslandEvent, nowPlaying: NowPlaying?): MediaTrackContentKey =
    mediaTrackContentKey(event, nowPlaying)
