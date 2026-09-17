package com.ekoehler.expressivecutout.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicTileSettingsTest {

    @Test
    fun `expressive music options default off`() {
        val settings = MusicTileSettings()

        assertFalse(settings.materialExpressivePlayer)
        assertFalse(settings.useAlbumColours)
        assertFalse(settings.wavyProgress)
    }

    @Test
    fun `old backup without expressive keys preserves existing values`() {
        val current = MusicTileSettings(
            materialExpressivePlayer = true,
            useAlbumColours = false,
            wavyProgress = true,
        )

        val restored = current.withExpressiveJsonValues(emptyMap())

        assertEquals(current.materialExpressivePlayer, restored.materialExpressivePlayer)
        assertEquals(current.useAlbumColours, restored.useAlbumColours)
        assertEquals(current.wavyProgress, restored.wavyProgress)
    }

    @Test
    fun `new backup round trip preserves expressive options`() {
        val source = MusicTileSettings(
            materialExpressivePlayer = true,
            useAlbumColours = true,
            wavyProgress = true,
        )

        val exported = source.expressiveJsonValues()
        val restored = MusicTileSettings().withExpressiveJsonValues(exported)

        assertEquals(
            setOf("materialExpressivePlayer", "useAlbumColours", "wavyProgress"),
            exported.keys,
        )
        assertTrue(restored.materialExpressivePlayer)
        assertTrue(restored.useAlbumColours)
        assertTrue(restored.wavyProgress)
    }
}
