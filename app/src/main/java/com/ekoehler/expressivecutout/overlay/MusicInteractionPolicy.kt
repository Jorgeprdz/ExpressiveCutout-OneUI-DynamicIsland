package com.ekoehler.expressivecutout.overlay

import androidx.compose.ui.graphics.Color

/** Maps a horizontal pointer coordinate to a stable, bounded 0..1 media fraction. */
internal fun mediaSeekFraction(xPx: Float, widthPx: Float): Float? {
    if (!xPx.isFinite() || !widthPx.isFinite() || widthPx <= 0f) return null
    return (xPx / widthPx).coerceIn(0f, 1f)
}

/** Converts a pointer coordinate into a real media-session seek target. */
internal fun mediaSeekTargetMs(xPx: Float, widthPx: Float, durationMs: Long?): Long? {
    val duration = durationMs?.takeIf { it > 0L } ?: return null
    val fraction = mediaSeekFraction(xPx, widthPx) ?: return null
    return (duration * fraction).toLong().coerceIn(0L, duration)
}

/** A seek gesture is meaningful only for a finite-duration session that advertises ACTION_SEEK_TO. */
internal fun mediaSeekAvailable(durationMs: Long?, transportCanSeek: Boolean): Boolean =
    transportCanSeek && durationMs != null && durationMs > 0L

/** Keeps legacy progress on Material primary while album-colour mode uses its resolved music accent. */
internal fun resolveMediaProgressAccent(
    useAlbumColours: Boolean,
    resolvedAccent: Color,
    themePrimary: Color,
): Color = if (useAlbumColours) resolvedAccent else themePrimary
