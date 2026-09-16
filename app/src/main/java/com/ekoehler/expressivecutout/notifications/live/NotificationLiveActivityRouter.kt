package com.ekoehler.expressivecutout.notifications.live

/** Selects exactly one notification path while keeping specialized and framework evidence authoritative. */
class NotificationLiveActivityRouter {

    /** Returns the single winning route for [input]. */
    fun route(input: Input): Route = when {
        input.isCall -> Route.CALL
        input.isTimer -> Route.TIMER
        input.isMedia -> Route.MEDIA
        input.nativeEvidence?.shouldBypassOngoingFilter == true -> Route.NATIVE_LIVE
        input.hasProgressStyle -> Route.PROGRESS_STYLE
        input.hasSemanticLiveActivity -> Route.SEMANTIC_LIVE
        input.legacySurfaceable -> Route.LEGACY_NOTIFICATION
        else -> Route.IGNORE
    }

    /** Pure routing evidence gathered before any listener side effect is chosen. */
    data class Input(
        val isCall: Boolean = false,
        val isTimer: Boolean = false,
        val isMedia: Boolean = false,
        val nativeEvidence: NativeLiveEvidence? = null,
        val hasProgressStyle: Boolean = false,
        val hasSemanticLiveActivity: Boolean = false,
        val legacySurfaceable: Boolean = false,
    )

    /** The mutually exclusive notification paths understood by the listener. */
    enum class Route {
        CALL,
        TIMER,
        MEDIA,
        NATIVE_LIVE,
        PROGRESS_STYLE,
        SEMANTIC_LIVE,
        LEGACY_NOTIFICATION,
        IGNORE,
    }
}
