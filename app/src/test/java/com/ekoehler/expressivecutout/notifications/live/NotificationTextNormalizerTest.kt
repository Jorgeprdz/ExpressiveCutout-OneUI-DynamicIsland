package com.ekoehler.expressivecutout.notifications.live

import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies deterministic locale-independent normalization used by semantic matching. */
class NotificationTextNormalizerTest {
    @Test
    fun `normalization is case accent and punctuation insensitive`() {
        assertEquals("tu conductor llegara en 3 min", NotificationTextNormalizer.normalize("TU conductor llegara en 3 min"))
    }

    @Test
    fun `normalization handles null and empty input`() {
        assertEquals("", NotificationTextNormalizer.normalize(null))
        assertEquals("", NotificationTextNormalizer.normalize(""))
    }
}
