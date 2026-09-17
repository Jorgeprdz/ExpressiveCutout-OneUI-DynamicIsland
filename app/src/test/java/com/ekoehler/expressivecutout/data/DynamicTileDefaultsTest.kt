package com.ekoehler.expressivecutout.data

import com.ekoehler.expressivecutout.core.DynamicTile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicTileDefaultsTest {

    @Test
    fun `assistant defaults off when no preference exists`() {
        assertFalse(resolveDynamicTileEnabled(DynamicTile.ASSISTANT, persisted = null))
    }

    @Test
    fun `assistant preserves an explicit enabled preference`() {
        assertTrue(resolveDynamicTileEnabled(DynamicTile.ASSISTANT, persisted = true))
    }

    @Test
    fun `assistant preserves an explicit disabled preference`() {
        assertFalse(resolveDynamicTileEnabled(DynamicTile.ASSISTANT, persisted = false))
    }

    @Test
    fun `existing dynamic tiles keep their enabled default`() {
        assertTrue(resolveDynamicTileEnabled(DynamicTile.MUSIC, persisted = null))
        assertTrue(resolveDynamicTileEnabled(DynamicTile.PHONE, persisted = null))
        assertTrue(resolveDynamicTileEnabled(DynamicTile.TIMER, persisted = null))
    }
}
