package com.ekoehler.expressivecutout.notifications.live

import com.ekoehler.expressivecutout.core.live.LiveActivity
import com.ekoehler.expressivecutout.core.live.LiveActivityUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifies API-36 native metadata maps into coordinator-ready neutral activities. */
class NativeNotificationLiveActivityMapperTest {
    private val mapper = NativeNotificationLiveActivityMapper()

    @Test
    fun `progress style maps structured progress as native activity`() {
        val snapshot = NativeLiveSnapshot(
            evidence = NativeLiveEvidence.classify(
                platformAvailable = true,
                promotedFlag = false,
                promotable = false,
                requested = false,
            ),
            styleKind = NativeLiveSnapshot.StyleKind.PROGRESS,
            progress = NativeLiveSnapshot.Progress(
                current = 42,
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

        val update = mapper.map(
            signals = signals(title = "Download", text = "42%"),
            snapshot = snapshot,
            stableId = "notification:com.example:key-1:1000",
            nowElapsedRealtime = 5_000L,
        ) as LiveActivityUpdate.Upsert

        assertEquals(LiveActivity.Kind.GENERIC_PROGRESS, update.activity.kind)
        assertEquals(LiveActivity.SourceKind.NATIVE_API, update.activity.sourceKind)
        assertEquals(42, update.activity.progress?.current)
        assertEquals(100, update.activity.progress?.max)
        assertEquals("notification:com.example:key-1:1000", update.activity.stableId)
    }

    @Test
    fun `promoted non progress notification maps as native notification`() {
        val snapshot = NativeLiveSnapshot(
            evidence = NativeLiveEvidence.classify(
                platformAvailable = true,
                promotedFlag = true,
                promotable = true,
                requested = true,
            ),
            styleKind = NativeLiveSnapshot.StyleKind.OTHER,
        )

        val update = mapper.map(
            signals = signals(title = "Trip", text = "Arriving"),
            snapshot = snapshot,
            stableId = "notification:com.example:key-1:1000",
            nowElapsedRealtime = 5_000L,
        ) as LiveActivityUpdate.Upsert

        assertEquals(LiveActivity.Kind.NOTIFICATION, update.activity.kind)
        assertEquals("Trip", update.activity.title)
        assertEquals("Arriving", update.activity.subtitle)
        assertNull(update.activity.progress)
    }

    private fun signals(title: String?, text: String?): NotificationLiveSignals = NotificationLiveSignals(
        packageName = "com.example",
        notificationKey = "key-1",
        title = title,
        text = text,
        appName = "Example",
        ongoing = true,
        clearable = false,
        postTime = 1_000L,
    )
}
