package com.ekoehler.expressivecutout.notifications.live

import com.ekoehler.expressivecutout.core.live.LiveActivity
import com.ekoehler.expressivecutout.core.live.LiveActivityUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies posted/removal processing before Android listener side effects are applied. */
class NotificationLiveActivityProcessorTest {
    private val processor = NotificationLiveActivityProcessor()

    @Test
    fun `native live evidence produces one native upsert`() {
        val evidence = NativeLiveEvidence.classify(
            platformAvailable = true,
            promotedFlag = true,
            promotable = true,
            requested = true,
        )
        val snapshot = NativeLiveSnapshot(evidence, NativeLiveSnapshot.StyleKind.OTHER)

        val result = processor.process(
            NotificationLiveActivityProcessor.Input(
                signals = signals(text = "Your driver arrives in 3 min", nativeEvidence = evidence),
                nativeSnapshot = snapshot,
                legacySurfaceable = false,
                nowElapsedRealtime = 5_000L,
            ),
        )

        assertEquals(NotificationLiveActivityRouter.Route.NATIVE_LIVE, result.route)
        val upsert = result.update as LiveActivityUpdate.Upsert
        assertEquals(LiveActivity.SourceKind.NATIVE_API, upsert.activity.sourceKind)
        assertEquals(LiveActivity.Kind.NOTIFICATION, upsert.activity.kind)
        assertTrue(!result.preserveLegacyPresentation)
    }

    @Test
    fun `public progress style wins without promoted evidence`() {
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
                current = 20,
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
                signals = signals(text = "Downloading", hasProgressStyle = true),
                nativeSnapshot = snapshot,
                legacySurfaceable = false,
                nowElapsedRealtime = 10L,
            ),
        )

        assertEquals(NotificationLiveActivityRouter.Route.PROGRESS_STYLE, result.route)
        assertEquals(20, (result.update as LiveActivityUpdate.Upsert).activity.progress?.current)
    }

    @Test
    fun `semantic updates keep first observed identity while content changes`() {
        val first = processor.process(
            NotificationLiveActivityProcessor.Input(
                signals = signals(text = "Your driver is arriving in 3 min", postTime = 1_000L),
                legacySurfaceable = true,
                nowElapsedRealtime = 100L,
            ),
        )
        val update = processor.process(
            NotificationLiveActivityProcessor.Input(
                signals = signals(text = "Your driver is arriving in 1 min", postTime = 9_000L),
                legacySurfaceable = true,
                nowElapsedRealtime = 200L,
            ),
        )

        assertEquals(NotificationLiveActivityRouter.Route.SEMANTIC_LIVE, first.route)
        assertEquals(NotificationLiveActivityRouter.Route.SEMANTIC_LIVE, update.route)
        val firstId = (first.update as LiveActivityUpdate.Upsert).activity.stableId
        val updateId = (update.update as LiveActivityUpdate.Upsert).activity.stableId
        assertEquals(firstId, updateId)
        assertEquals("notification:com.example:key-1:1000", firstId)
        assertTrue(first.preserveLegacyPresentation)
    }

    @Test
    fun `ordinary surfaceable notification remains legacy with no live update`() {
        val result = processor.process(
            NotificationLiveActivityProcessor.Input(
                signals = signals(title = "Hello", text = "World"),
                legacySurfaceable = true,
                nowElapsedRealtime = 100L,
            ),
        )

        assertEquals(NotificationLiveActivityRouter.Route.LEGACY_NOTIFICATION, result.route)
        assertNull(result.update)
        assertTrue(result.preserveLegacyPresentation)
    }

    @Test
    fun `blocked notification cannot bypass through native evidence`() {
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
                blocked = true,
                legacySurfaceable = false,
                nowElapsedRealtime = 100L,
            ),
        )

        assertEquals(NotificationLiveActivityRouter.Route.IGNORE, result.route)
        assertNull(result.update)
    }

    @Test
    fun `removal targets tracked activity and replacement receives fresh identity`() {
        val first = processor.process(
            NotificationLiveActivityProcessor.Input(
                signals = signals(text = "Rain starts in 15 min", postTime = 100L),
                legacySurfaceable = false,
                nowElapsedRealtime = 100L,
            ),
        )
        val firstId = (first.update as LiveActivityUpdate.Upsert).activity.stableId
        val removal = processor.remove("com.example", "key-1")

        assertEquals(firstId, removal?.stableId)
        assertNull(processor.remove("com.example", "unknown"))

        val replacement = processor.process(
            NotificationLiveActivityProcessor.Input(
                signals = signals(text = "Rain starts in 10 min", postTime = 900L),
                legacySurfaceable = false,
                nowElapsedRealtime = 900L,
            ),
        )
        val replacementId = (replacement.update as LiveActivityUpdate.Upsert).activity.stableId
        assertNotEquals(firstId, replacementId)
    }

    private fun signals(
        title: String? = null,
        text: String? = null,
        postTime: Long = 1_000L,
        nativeEvidence: NativeLiveEvidence? = null,
        hasProgressStyle: Boolean = false,
    ): NotificationLiveSignals = NotificationLiveSignals(
        packageName = "com.example",
        notificationKey = "key-1",
        title = title,
        text = text,
        ongoing = true,
        clearable = false,
        postTime = postTime,
        nativeEvidence = nativeEvidence,
        hasProgressStyle = hasProgressStyle,
    )
}
