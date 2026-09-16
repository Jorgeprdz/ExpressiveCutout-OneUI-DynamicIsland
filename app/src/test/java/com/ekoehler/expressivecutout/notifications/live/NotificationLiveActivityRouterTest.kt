package com.ekoehler.expressivecutout.notifications.live

import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies notification routing is exclusive and preserves specialized/native precedence. */
class NotificationLiveActivityRouterTest {
    private val router = NotificationLiveActivityRouter()

    @Test
    fun `call wins over every other route`() {
        assertEquals(
            NotificationLiveActivityRouter.Route.CALL,
            router.route(
                NotificationLiveActivityRouter.Input(
                    isCall = true,
                    isTimer = true,
                    isMedia = true,
                    nativeEvidence = promotedEvidence(),
                    hasProgressStyle = true,
                    hasSemanticLiveActivity = true,
                    legacySurfaceable = true,
                ),
            ),
        )
    }

    @Test
    fun `timer wins after call`() {
        assertEquals(
            NotificationLiveActivityRouter.Route.TIMER,
            router.route(
                NotificationLiveActivityRouter.Input(
                    isTimer = true,
                    isMedia = true,
                    nativeEvidence = promotedEvidence(),
                    hasProgressStyle = true,
                    hasSemanticLiveActivity = true,
                    legacySurfaceable = true,
                ),
            ),
        )
    }

    @Test
    fun `media wins over native and semantic notification routes`() {
        assertEquals(
            NotificationLiveActivityRouter.Route.MEDIA,
            router.route(
                NotificationLiveActivityRouter.Input(
                    isMedia = true,
                    nativeEvidence = promotedEvidence(),
                    hasProgressStyle = true,
                    hasSemanticLiveActivity = true,
                    legacySurfaceable = true,
                ),
            ),
        )
    }

    @Test
    fun `promoted native evidence wins over progress semantic and legacy`() {
        assertEquals(
            NotificationLiveActivityRouter.Route.NATIVE_LIVE,
            router.route(
                NotificationLiveActivityRouter.Input(
                    nativeEvidence = promotedEvidence(),
                    hasProgressStyle = true,
                    hasSemanticLiveActivity = true,
                    legacySurfaceable = true,
                ),
            ),
        )
    }

    @Test
    fun `promotable ongoing bypasses legacy surface filter`() {
        val promotable = NativeLiveEvidence.classify(
            platformAvailable = true,
            promotedFlag = false,
            promotable = true,
            requested = false,
        )
        assertEquals(
            NotificationLiveActivityRouter.Route.NATIVE_LIVE,
            router.route(NotificationLiveActivityRouter.Input(nativeEvidence = promotable)),
        )
    }

    @Test
    fun `progress style wins over semantic text fallback`() {
        assertEquals(
            NotificationLiveActivityRouter.Route.PROGRESS_STYLE,
            router.route(
                NotificationLiveActivityRouter.Input(
                    hasProgressStyle = true,
                    hasSemanticLiveActivity = true,
                    legacySurfaceable = true,
                ),
            ),
        )
    }

    @Test
    fun `semantic live wins over ordinary legacy notification`() {
        assertEquals(
            NotificationLiveActivityRouter.Route.SEMANTIC_LIVE,
            router.route(
                NotificationLiveActivityRouter.Input(
                    hasSemanticLiveActivity = true,
                    legacySurfaceable = true,
                ),
            ),
        )
    }

    @Test
    fun `ordinary surfaceable notification remains legacy`() {
        assertEquals(
            NotificationLiveActivityRouter.Route.LEGACY_NOTIFICATION,
            router.route(NotificationLiveActivityRouter.Input(legacySurfaceable = true)),
        )
    }

    @Test
    fun `ordinary ongoing or non-clearable notification remains ignored`() {
        assertEquals(
            NotificationLiveActivityRouter.Route.IGNORE,
            router.route(NotificationLiveActivityRouter.Input(legacySurfaceable = false)),
        )
    }

    private fun promotedEvidence(): NativeLiveEvidence = NativeLiveEvidence.classify(
        platformAvailable = true,
        promotedFlag = true,
        promotable = true,
        requested = true,
    )
}
