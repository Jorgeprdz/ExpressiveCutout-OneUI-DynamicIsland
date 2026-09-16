package com.ekoehler.expressivecutout.notifications.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies that authoritative promotion is never confused with secondary eligibility evidence. */
class NativeLiveEvidenceTest {

    @Test
    fun `pre android 16 never reports live evidence`() {
        val evidence = NativeLiveEvidence.classify(
            platformAvailable = false,
            promotedFlag = true,
            promotable = true,
            requested = true,
        )

        assertEquals(NativeLiveEvidence.Status.UNAVAILABLE, evidence.status)
        assertFalse(evidence.shouldBypassOngoingFilter)
    }

    @Test
    fun `promoted flag is authoritative`() {
        val evidence = NativeLiveEvidence.classify(
            platformAvailable = true,
            promotedFlag = true,
            promotable = false,
            requested = false,
        )

        assertEquals(NativeLiveEvidence.Status.PROMOTED, evidence.status)
        assertTrue(evidence.shouldBypassOngoingFilter)
    }

    @Test
    fun `promotable notification is eligible but not promoted`() {
        val evidence = NativeLiveEvidence.classify(
            platformAvailable = true,
            promotedFlag = false,
            promotable = true,
            requested = false,
        )

        assertEquals(NativeLiveEvidence.Status.PROMOTABLE, evidence.status)
        assertTrue(evidence.shouldBypassOngoingFilter)
    }

    @Test
    fun `requested notification remains secondary evidence`() {
        val evidence = NativeLiveEvidence.classify(
            platformAvailable = true,
            promotedFlag = false,
            promotable = false,
            requested = true,
        )

        assertEquals(NativeLiveEvidence.Status.REQUESTED, evidence.status)
        assertTrue(evidence.shouldBypassOngoingFilter)
    }

    @Test
    fun `ordinary notification has no live evidence`() {
        val evidence = NativeLiveEvidence.classify(
            platformAvailable = true,
            promotedFlag = false,
            promotable = false,
            requested = false,
        )

        assertEquals(NativeLiveEvidence.Status.NONE, evidence.status)
        assertFalse(evidence.shouldBypassOngoingFilter)
    }
}
