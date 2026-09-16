package com.ekoehler.expressivecutout.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies that transient notification suppression composes with, rather than replaces, user wishes. */
class StatusBarEffectiveFlagsTest {

    @Test
    fun `pulse hides notification icons without changing unrelated persistent flags`() {
        val persistent = StatusBarFlagState(
            hideNotificationIcons = false,
            hideSystemInfo = true,
            hideClock = true,
            silenceAlerts = false,
        )

        val effective = StatusBarEffectiveFlags.compose(
            persistent = persistent,
            transientHideNotificationIcons = true,
        )

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

        val effective = StatusBarEffectiveFlags.compose(
            persistent = persistent,
            transientHideNotificationIcons = false,
        )

        assertEquals(persistent, effective)
    }

    @Test
    fun `persistent notification hiding remains after pulse ends`() {
        val persistent = StatusBarFlagState(hideNotificationIcons = true)

        val during = StatusBarEffectiveFlags.compose(persistent, transientHideNotificationIcons = true)
        val after = StatusBarEffectiveFlags.compose(persistent, transientHideNotificationIcons = false)

        assertTrue(during.hideNotificationIcons)
        assertTrue(after.hideNotificationIcons)
    }

    @Test
    fun `pulse changes only notification icon wish`() {
        val persistent = StatusBarFlagState(
            hideNotificationIcons = false,
            hideSystemInfo = true,
            hideClock = false,
            silenceAlerts = true,
        )

        val effective = StatusBarEffectiveFlags.compose(persistent, transientHideNotificationIcons = true)

        assertEquals(persistent.copy(hideNotificationIcons = true), effective)
    }
}
