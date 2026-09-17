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

private fun musicContrastRatio(first: Color, second: Color): Float {
    val firstLuminance = first.luminance()
    val secondLuminance = second.luminance()
    val lighter = maxOf(firstLuminance, secondLuminance)
    val darker = minOf(firstLuminance, secondLuminance)
    return (lighter + 0.05f) / (darker + 0.05f)
}

/**
 * Moves a colour toward the already-safe [onSurface] only as far as necessary to reach a readable
 * 3:1 UI contrast. This preserves album character instead of replacing every dark cover with white.
 */
private fun ensureMusicUiContrast(
    candidate: Color,
    surface: Color,
    onSurface: Color,
    minimumRatio: Float = 3f,
): Color {
    if (musicContrastRatio(candidate, surface) >= minimumRatio) return candidate
    for (step in 1..10) {
        val adjusted = lerp(candidate, onSurface, step / 10f)
        if (musicContrastRatio(adjusted, surface) >= minimumRatio) return adjusted
    }
    return onSurface
}

/** Resolves album-derived colours, falling back deterministically to the tile's existing accent. */
internal fun resolveMusicPalette(
    useAlbumColours: Boolean,
    fallbackAccent: Color,
    artworkAccent: Color?,
): MusicPalette {
    if (!useAlbumColours) return MusicPalette(accent = fallbackAccent)

    val sourceAccent = artworkAccent ?: fallbackAccent
    val surface = lerp(sourceAccent, Color(0xFF121216), 0.78f)
    val onSurface = if (surface.luminance() > 0.5f) Color(0xFF0A0A0A) else Color(0xFFF5F5F5)

    // When artwork exists, make the colour used by progress/buttons readable on its derived surface.
    // Artwork-unavailable fallback remains the exact historical event accent.
    val accent = if (artworkAccent != null) {
        ensureMusicUiContrast(sourceAccent, surface, onSurface)
    } else {
        sourceAccent
    }
    val secondarySeed = lerp(sourceAccent, onSurface, 0.22f)
    val secondaryAccent = ensureMusicUiContrast(secondarySeed, surface, onSurface)

    return MusicPalette(
        accent = accent,
        surface = surface,
        onSurface = onSurface,
        secondaryAccent = secondaryAccent,
    )
}

/** Palette identity follows track identity, so progress and play-state updates cannot churn it. */
internal fun mediaPaletteIdentity(event: IslandEvent, nowPlaying: NowPlaying?): MediaTrackContentKey =
    mediaTrackContentKey(event, nowPlaying)
