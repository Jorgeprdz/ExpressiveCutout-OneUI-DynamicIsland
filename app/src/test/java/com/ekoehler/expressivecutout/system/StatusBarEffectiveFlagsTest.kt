package com.ekoehler.expressivecutout.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies that transient arrival suppression composes with, rather than replaces, user wishes. */
class StatusBarEffectiveFlagsTest {

    @Test
    fun `arrival pulse hides status icons without silencing alerts`() {
        val persistent = StatusBarFlagState(
            hideNotificationIcons = false,
            hideSystemInfo = false,
            hideClock = false,
            silenceAlerts = false,
        )

        val effective = StatusBarEffectiveFlags.compose(persistent, true)

        assertTrue(effective.hideNotificationIcons)
        assertTrue(effective.hideSystemInfo)
        assertTrue(effective.hideClock)
        assertFalse(effective.silenceAlerts)
    }

    @Test
    fun `ending pulse restores exact persistent wishes`() {
        val persistent = StatusBarFlagState(
            hideNotificationIcons = false,
            hideSystemInfo = false,
            hideClock = true,
            silenceAlerts = true,
        )

        val effective = StatusBarEffectiveFlags.compose(persistent, false)

        assertEquals(persistent, effective)
    }

    @Test
    fun `persistent icon hiding remains after pulse ends`() {
        val persistent = StatusBarFlagState(
            hideNotificationIcons = true,
            hideSystemInfo = true,
            hideClock = false,
        )

        val during = StatusBarEffectiveFlags.compose(persistent, true)
        val after = StatusBarEffectiveFlags.compose(persistent, false)

        assertTrue(during.hideNotificationIcons)
        assertTrue(during.hideSystemInfo)
        assertTrue(during.hideClock)
        assertTrue(after.hideNotificationIcons)
        assertTrue(after.hideSystemInfo)
        assertFalse(after.hideClock)
    }

    @Test
    fun `arrival pulse preserves persistent alert silence wish`() {
        val persistent = StatusBarFlagState(
            hideNotificationIcons = false,
            hideSystemInfo = false,
            hideClock = false,
            silenceAlerts = true,
        )

        val effective = StatusBarEffectiveFlags.compose(persistent, true)

        assertTrue(effective.hideNotificationIcons)
        assertTrue(effective.hideSystemInfo)
        assertTrue(effective.hideClock)
        assertTrue(effective.silenceAlerts)
    }
}
