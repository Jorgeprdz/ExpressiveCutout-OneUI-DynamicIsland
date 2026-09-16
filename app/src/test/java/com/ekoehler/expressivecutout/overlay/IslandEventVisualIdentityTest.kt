package com.ekoehler.expressivecutout.overlay

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Guards Compose identity so live metadata changes cannot replay arrival animations. */
class IslandEventVisualIdentityTest {

    @Test
    fun `live stable id survives renderer id and label updates`() {
        val first = event(id = 1L, label = "Track A", stableId = "music:spotify:sessionA")
        val updated = first.copy(id = 2L, label = "Track B")

        assertEquals(first.visualIdentity, updated.visualIdentity)
        assertEquals(primaryContentKey(first), primaryContentKey(updated))
    }

    @Test
    fun `different live stable ids use different primary content keys`() {
        val music = event(id = 1L, label = "Track", stableId = "music:spotify:sessionA")
        val call = event(id = 2L, label = "Caller", stableId = "call:dialer:key")

        assertNotEquals(primaryContentKey(music), primaryContentKey(call))
    }

    @Test
    fun `same satellite stable id keeps content key across metadata updates`() {
        val first = event(id = 1L, label = "Track A", stableId = "music:spotify:sessionA")
        val updated = first.copy(id = 2L, label = "Track B")

        assertEquals(first.visualIdentity, updated.visualIdentity)
        assertEquals(satelliteContentKey(first), satelliteContentKey(updated))
    }

    @Test
    fun `different satellite stable ids use different content keys`() {
        val first = event(id = 1L, label = "Track", stableId = "music:spotify:sessionA")
        val second = event(id = 2L, label = "Timer", stableId = "timer:clock:key")

        assertNotEquals(satelliteContentKey(first), satelliteContentKey(second))
    }

    @Test
    fun `legacy notification key survives renderer id updates`() {
        val first = event(id = 1L, label = "Download 10%", notificationKey = "pkg|42")
        val updated = first.copy(id = 2L, label = "Download 20%")

        assertEquals(first.visualIdentity, updated.visualIdentity)
    }

    @Test
    fun `unkeyed transient events retain per event identity`() {
        val first = event(id = 1L, label = "Charging")
        val second = event(id = 2L, label = "Charging")

        assertNotEquals(first.visualIdentity, second.visualIdentity)
    }

    private fun event(
        id: Long,
        label: String,
        stableId: String? = null,
        notificationKey: String? = null,
    ): IslandEvent = IslandEvent(
        id = id,
        icon = IslandIcon.Vector(Icons.Rounded.Notifications),
        label = label,
        accent = Color.White,
        stableId = stableId,
        notificationKey = notificationKey,
    )
}
