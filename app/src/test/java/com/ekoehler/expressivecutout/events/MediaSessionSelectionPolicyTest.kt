package com.ekoehler.expressivecutout.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Regression coverage for choosing one logical media session across framework and notification sources. */
class MediaSessionSelectionPolicyTest {

    @Test
    fun `active session wins over fallback`() {
        val fallback = candidate("spotify-session", MediaSessionCandidateSource.NOTIFICATION_FALLBACK)
        val active = candidate("spotify-session", MediaSessionCandidateSource.ACTIVE)

        assertEquals(active, selectMediaSessionCandidate(listOf(fallback, active)))
    }

    @Test
    fun `fallback is selected when no active session exists`() {
        val fallback = candidate("spotify-session", MediaSessionCandidateSource.NOTIFICATION_FALLBACK)

        assertEquals(fallback, selectMediaSessionCandidate(listOf(fallback)))
    }

    @Test
    fun `same identity from active and fallback is one logical session`() {
        val fallback = candidate("spotify-session", MediaSessionCandidateSource.NOTIFICATION_FALLBACK)
        val active = candidate("spotify-session", MediaSessionCandidateSource.ACTIVE)

        val selected = selectMediaSessionCandidate(listOf(fallback, active, fallback))

        assertEquals(MediaSessionCandidateSource.ACTIVE, selected?.source)
        assertEquals("spotify-session", selected?.identity)
    }

    @Test
    fun `active source naturally replaces fallback for the same session`() {
        val fallbackOnly = selectMediaSessionCandidate(
            listOf(candidate("spotify-session", MediaSessionCandidateSource.NOTIFICATION_FALLBACK)),
        )
        val withActive = selectMediaSessionCandidate(
            listOf(
                candidate("spotify-session", MediaSessionCandidateSource.NOTIFICATION_FALLBACK),
                candidate("spotify-session", MediaSessionCandidateSource.ACTIVE),
            ),
        )

        assertEquals(MediaSessionCandidateSource.NOTIFICATION_FALLBACK, fallbackOnly?.source)
        assertEquals(MediaSessionCandidateSource.ACTIVE, withActive?.source)
        assertEquals(fallbackOnly?.identity, withActive?.identity)
    }

    @Test
    fun `no candidate yields no selected session`() {
        assertNull(selectMediaSessionCandidate(emptyList()))
    }

    private fun candidate(
        identity: String,
        source: MediaSessionCandidateSource,
        playing: Boolean = true,
    ) = MediaSessionSelectionCandidate(
        identity = identity,
        source = source,
        isPlaying = playing,
    )
}
