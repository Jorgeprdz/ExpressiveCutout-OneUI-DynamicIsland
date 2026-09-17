package com.ekoehler.expressivecutout.overlay

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.ekoehler.expressivecutout.data.IslandDimensions
import com.ekoehler.expressivecutout.data.IslandLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicGeometrySeekPolicyTest {

    @Test
    fun `legacy music geometry remains unchanged`() {
        assertEquals(44, mediaContentRowHeightDp(expressive = false))
        assertEquals(4, mediaProgressVisualHeightDp(wavy = false))
        assertEquals(16, expandedMediaProgressExtraDp(wavy = false))
        assertEquals(
            IslandLayout.DEFAULT_EXPANDED.heightDp,
            mediaExpandedBaseHeightDp(
                topMarginDp = IslandDimensions.DEFAULT_TOP_MARGIN_DP,
                expressive = false,
            ),
        )
    }

    @Test
    fun `expressive geometry reserves enough room for artwork and metadata`() {
        val legacy = mediaExpandedBaseHeightDp(
            topMarginDp = IslandDimensions.DEFAULT_TOP_MARGIN_DP,
            expressive = false,
        )
        val expressive = mediaExpandedBaseHeightDp(
            topMarginDp = IslandDimensions.DEFAULT_TOP_MARGIN_DP,
            expressive = true,
        )

        assertTrue(mediaContentRowHeightDp(expressive = true) >= 52)
        assertTrue(expressive > legacy)
    }

    @Test
    fun `wavy progress keeps expressive amplitude while legacy track remains four dp`() {
        assertEquals(4, mediaProgressVisualHeightDp(wavy = false))
        assertTrue(mediaProgressVisualHeightDp(wavy = true) >= 12)
        assertTrue(expandedMediaProgressExtraDp(wavy = true) > expandedMediaProgressExtraDp(wavy = false))
        assertEquals(32, mediaProgressTouchTargetDp())
    }

    @Test
    fun `seek maps pointer position to bounded playback time`() {
        val duration = 200_000L

        assertEquals(0L, mediaSeekTargetMs(xPx = 0f, widthPx = 100f, durationMs = duration))
        assertEquals(100_000L, mediaSeekTargetMs(xPx = 50f, widthPx = 100f, durationMs = duration))
        assertEquals(duration, mediaSeekTargetMs(xPx = 100f, widthPx = 100f, durationMs = duration))
        assertEquals(0L, mediaSeekTargetMs(xPx = -20f, widthPx = 100f, durationMs = duration))
        assertEquals(duration, mediaSeekTargetMs(xPx = 140f, widthPx = 100f, durationMs = duration))
    }

    @Test
    fun `seek is unavailable without a finite positive width and duration`() {
        assertNull(mediaSeekTargetMs(xPx = 50f, widthPx = 100f, durationMs = null))
        assertNull(mediaSeekTargetMs(xPx = 50f, widthPx = 100f, durationMs = 0L))
        assertNull(mediaSeekTargetMs(xPx = 50f, widthPx = 0f, durationMs = 200_000L))
        assertNull(mediaSeekTargetMs(xPx = Float.NaN, widthPx = 100f, durationMs = 200_000L))
    }

    @Test
    fun `album palette provides readable accent roles on a dark surface`() {
        val palette = resolveMusicPalette(
            useAlbumColours = true,
            fallbackAccent = Color(0xFFF472B6),
            artworkAccent = Color(0xFF101018),
        )
        val surface = requireNotNull(palette.surface)
        val secondary = requireNotNull(palette.secondaryAccent)

        assertTrue(contrastRatio(palette.accent, surface) >= 3f)
        assertTrue(contrastRatio(secondary, surface) >= 3f)
    }

    @Test
    fun `progress uses resolved album accent only when album colours are enabled`() {
        val resolvedAlbumAccent = Color(0xFF90CAF9)
        val themePrimary = Color(0xFF6750A4)

        assertEquals(
            resolvedAlbumAccent,
            resolveMediaProgressAccent(
                useAlbumColours = true,
                resolvedAccent = resolvedAlbumAccent,
                themePrimary = themePrimary,
            ),
        )
        assertEquals(
            themePrimary,
            resolveMediaProgressAccent(
                useAlbumColours = false,
                resolvedAccent = resolvedAlbumAccent,
                themePrimary = themePrimary,
            ),
        )
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        val firstLuminance = first.luminance()
        val secondLuminance = second.luminance()
        val lighter = maxOf(firstLuminance, secondLuminance)
        val darker = minOf(firstLuminance, secondLuminance)
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
