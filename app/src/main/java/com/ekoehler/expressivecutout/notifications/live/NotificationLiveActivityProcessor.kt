package com.ekoehler.expressivecutout.notifications.live

import com.ekoehler.expressivecutout.core.live.LiveActivityUpdate

/**
 * Converts one posted notification into exactly one winning route and, when applicable, one
 * coordinator-ready live update. Android listener side effects remain outside this pure decision
 * boundary.
 */
class NotificationLiveActivityProcessor(
    private val router: NotificationLiveActivityRouter = NotificationLiveActivityRouter(),
    private val semanticParser: SemanticLiveActivityParser = SemanticLiveActivityParser(),
    private val nativeMapper: NativeNotificationLiveActivityMapper = NativeNotificationLiveActivityMapper(),
    private val identityTracker: NotificationStableIdentityTracker = NotificationStableIdentityTracker(),
) {

    /** Routes [input] and materializes a live upsert only for native or semantic live work. */
    fun process(input: Input): Result {
        if (input.blocked) {
            return Result(NotificationLiveActivityRouter.Route.IGNORE)
        }

        val hasProgressStyle = input.nativeSnapshot?.styleKind == NativeLiveSnapshot.StyleKind.PROGRESS ||
            input.signals.hasProgressStyle
        val nativeEvidence = input.nativeSnapshot?.evidence ?: input.signals.nativeEvidence
        val canTrySemantic = !input.isCall &&
            !input.isTimer &&
            !input.isMedia &&
            nativeEvidence?.shouldBypassOngoingFilter != true &&
            !hasProgressStyle
        val semanticUpdate = if (canTrySemantic) {
            semanticParser.parse(input.signals, input.nowElapsedRealtime) as? LiveActivityUpdate.Upsert
        } else {
            null
        }
        val route = router.route(
            NotificationLiveActivityRouter.Input(
                isCall = input.isCall,
                isTimer = input.isTimer,
                isMedia = input.isMedia,
                nativeEvidence = nativeEvidence,
                hasProgressStyle = hasProgressStyle,
                hasSemanticLiveActivity = semanticUpdate != null,
                legacySurfaceable = input.legacySurfaceable,
            ),
        )
        val update = when (route) {
            NotificationLiveActivityRouter.Route.NATIVE_LIVE,
            NotificationLiveActivityRouter.Route.PROGRESS_STYLE -> {
                val snapshot = input.nativeSnapshot ?: return Result(route)
                nativeMapper.map(
                    signals = input.signals,
                    snapshot = snapshot,
                    stableId = identityTracker.resolve(
                        input.signals.packageName,
                        input.signals.notificationKey,
                        input.signals.postTime,
                    ),
                    nowElapsedRealtime = input.nowElapsedRealtime,
                )
            }

            NotificationLiveActivityRouter.Route.SEMANTIC_LIVE -> {
                val parsed = semanticUpdate ?: return Result(route)
                val stableId = identityTracker.resolve(
                    input.signals.packageName,
                    input.signals.notificationKey,
                    input.signals.postTime,
                )
                parsed.copy(activity = parsed.activity.copy(stableId = stableId))
            }

            else -> null
        }
        return Result(
            route = route,
            update = update,
            preserveLegacyPresentation = input.legacySurfaceable && route in LEGACY_COMPATIBLE_ROUTES,
        )
    }

    /** Returns a removal only when this processor previously registered the notification instance. */
    fun remove(packageName: String, notificationKey: String): LiveActivityUpdate.Remove? =
        identityTracker.remove(packageName, notificationKey)?.let { stableId ->
            LiveActivityUpdate.Remove(stableId)
        }

    /** All evidence required to choose a route without coupling the router to Android callbacks. */
    data class Input(
        val signals: NotificationLiveSignals,
        val nativeSnapshot: NativeLiveSnapshot? = null,
        val isCall: Boolean = false,
        val isTimer: Boolean = false,
        val isMedia: Boolean = false,
        val blocked: Boolean = false,
        val legacySurfaceable: Boolean,
        val nowElapsedRealtime: Long,
    )

    /** The exclusive route, optional live update, and temporary legacy-renderer compatibility flag. */
    data class Result(
        val route: NotificationLiveActivityRouter.Route,
        val update: LiveActivityUpdate? = null,
        val preserveLegacyPresentation: Boolean = false,
    )

    private companion object {
        /** Routes that may still use the legacy renderer until the LiveActivity renderer lands. */
        val LEGACY_COMPATIBLE_ROUTES = setOf(
            NotificationLiveActivityRouter.Route.NATIVE_LIVE,
            NotificationLiveActivityRouter.Route.PROGRESS_STYLE,
            NotificationLiveActivityRouter.Route.SEMANTIC_LIVE,
            NotificationLiveActivityRouter.Route.LEGACY_NOTIFICATION,
        )
    }
}
