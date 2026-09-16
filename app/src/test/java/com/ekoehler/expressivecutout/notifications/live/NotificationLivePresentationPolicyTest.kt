package com.ekoehler.expressivecutout.notifications.live

import org.junit.Assert.assertFalse
import org.junit.Test

/** Verifies native framework-owned live routes never duplicate themselves through legacy presentation. */
class NotificationLivePresentationPolicyTest {
    private val processor = NotificationLiveActivityProcessor()

    /** A promoted native Live Update owns presentation even when the legacy filter would also accept it. */
    @Test
    fun `native live never preserves legacy presentation`() {
        val evidence = NativeLiveEvidence.classify(
            platformAvailable = true,
            promotedFlag = true,
            promotable = true,
            requested = true,
        )
        val result = processor.process(
            NotificationLiveActivityProcessor.Input(
                signals = signals(nativeEvidence = evidence),
                nativeSnapshot = NativeLiveSnapshot(evidence, NativeLiveSnapshot.StyleKind.OTHER),
                legacySurfaceable = true,
                nowElapsedRealtime = 1_000L,
            ),
        )

        assertFalse(result.preserveLegacyPresentation)
    }

    /** Public ProgressStyle owns presentation even when an ordinary notification would be surfaceable. */
    @Test
    fun `progress style never preserves legacy presentation`() {
        val evidence = NativeLiveEvidence.classify(
            platformAvailable = true,
            promotedFlag = false,
            promotable = false,
            requested = false,
        )
        val snapshot = NativeLiveSnapshot(
            evidence = evidence,
            styleKind = NativeLiveSnapshot.StyleKind.PROGRESS,
            progress = NativeLiveSnapshot.Progress(
                current = 25,
                max = 100,
                indeterminate = false,
                styledByProgress = true,
                segments = emptyList(),
                points = emptyList(),
                startIcon = null,
                trackerIcon = null,
                endIcon = null,
            ),
        )
        val result = processor.process(
            NotificationLiveActivityProcessor.Input(
                signals = signals(hasProgressStyle = true),
                nativeSnapshot = snapshot,
                legacySurfaceable = true,
                nowElapsedRealtime = 1_000L,
            ),
        )

        assertFalse(result.preserveLegacyPresentation)
    }

    /** Builds a surfaceable notification signal without storing presentation text. */
    private fun signals(
        nativeEvidence: NativeLiveEvidence? = null,
        hasProgressStyle: Boolean = false,
    ): NotificationLiveSignals = NotificationLiveSignals(
        packageName = "com.example",
        notificationKey = "key-1",
        ongoing = false,
        clearable = true,
        postTime = 1_000L,
        nativeEvidence = nativeEvidence,
        hasProgressStyle = hasProgressStyle,
    )
}
