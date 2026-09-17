package com.ekoehler.expressivecutout.service

import android.app.KeyguardManager
import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSession
import android.os.PowerManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.os.BundleCompat
import com.ekoehler.expressivecutout.core.CutoutSignal
import com.ekoehler.expressivecutout.core.IslandEventBus
import com.ekoehler.expressivecutout.core.MediaArt
import com.ekoehler.expressivecutout.core.MediaArtBus
import com.ekoehler.expressivecutout.core.OnCall
import com.ekoehler.expressivecutout.core.OnCallBus
import com.ekoehler.expressivecutout.core.RunningTimer
import com.ekoehler.expressivecutout.core.RunningTimerBus
import com.ekoehler.expressivecutout.core.live.LiveActivity
import com.ekoehler.expressivecutout.core.live.LiveActivityRegistry
import com.ekoehler.expressivecutout.data.BehaviourPreferences
import com.ekoehler.expressivecutout.data.BehaviourSettings
import com.ekoehler.expressivecutout.events.CallNotificationParser
import com.ekoehler.expressivecutout.events.NotificationMediaSessionRegistry
import com.ekoehler.expressivecutout.events.TimerNotificationParser
import com.ekoehler.expressivecutout.notifications.live.NotificationLiveActivityBridge
import com.ekoehler.expressivecutout.notifications.live.NotificationLiveActivityProcessor
import com.ekoehler.expressivecutout.notifications.live.NotificationLiveActivityRouter
import com.ekoehler.expressivecutout.notifications.live.NotificationLiveSignalsExtractor
import com.ekoehler.expressivecutout.notifications.live.SpecializedLiveActivityFactory
import com.ekoehler.expressivecutout.overlay.NotificationHeaderResolver
import com.ekoehler.expressivecutout.overlay.loadImageBitmapOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Holds progress metadata extracted from a notification's extras. */
data class ProgressData(
    val max: Int = 0,
    val current: Int = 0,
    val isIndeterminate: Boolean = false,
    val title: String? = null,
) {
    /** True when this progress has reached its maximum and is not indeterminate. */
    val isComplete: Boolean
        get() = !isIndeterminate && max > 0 && current >= max
}

/**
 * Mirrors freshly posted notifications onto the island while also normalizing live work into the
 * process-wide [LiveActivityRegistry]. Specialized call/timer/media producers retain precedence;
 * native Android 16 and semantic live routes are decided before the ordinary notification filter.
 */
class CutoutNotificationListenerService : NotificationListenerService() {

    /** Key of the call notification currently driving the phone tile. Main-thread only. */
    private var currentCallKey: String? = null

    /** Key of the count-down notification currently driving the timer tile. */
    private var currentTimerKey: String? = null

    /** Key of the media notification the current album cover was lifted from. */
    private var currentMediaArtKey: String? = null

    /** Key of the assistant notification currently driving the assistant tile. */
    private var currentAssistantKey: String? = null

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val behaviourPreferences by lazy { BehaviourPreferences(this) }

    private val alerter by lazy { NotificationAlerter(this) }

    /** Applies notification-owned live mutations to the process-wide coordinator. */
    private val liveActivityBridge by lazy {
        NotificationLiveActivityBridge(LiveActivityRegistry.coordinator)
    }

    private var behaviourJob: Job? = null

    /** Cached mirror of BehaviourSettings.dismissNotifications. */
    private var dismissNotifications = BehaviourSettings.DEFAULT_DISMISS_NOTIFICATIONS

    /** Cached mirror of BehaviourSettings.displayWhileDnd. */
    private var displayWhileDnd = BehaviourSettings.DEFAULT_DISPLAY_WHILE_DND

    /** Cached mirror of BehaviourSettings.alertOnNotification. */
    private var alertOnNotification = BehaviourSettings.DEFAULT_ALERT_ON_NOTIFICATION

    /** Keys currently held out of the shade, mapped to the hold ceiling. */
    private val held = LinkedHashMap<String, Long>()

    /** Keys handed back to the shade and awaiting their re-post. */
    private val returning = LinkedHashMap<String, Long>()

    /** Keys the user killed on the pill and that should be cancelled on re-post. */
    private val pendingCancel = LinkedHashMap<String, Long>()

    /** Content fingerprints temporarily suppressed after their island presentation ends. */
    private val suppressed = LinkedHashMap<String, Long>()

    /** Fingerprint each live key was last emitted under. */
    private val shownFingerprint = LinkedHashMap<String, String>()

    /** How many fetch-backs are in flight with notification effects muted. */
    private var mutedReturns = 0

    /** Closes the mute window even if a fetch-back never lands. */
    private var unmuteJob: Job? = null

    /** Publishes the listener, starts settings observation, and restores current media state. */
    override fun onListenerConnected() {
        instance = this
        _bound.value = true
        observeBehaviour()
        seedMediaState()
    }

    /** Republishes media-session fallbacks and cover art from notifications that predate this bind. */
    private fun seedMediaState() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        active.sortedBy { it.postTime }.forEach { notification ->
            notification.publishMediaSessionFallback()
            notification.publishMediaArt()
        }
    }

    /** Clears the published binding state when Android disconnects this listener. */
    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        _bound.value = false
        NotificationMediaSessionRegistry.clear()
    }

    /** Releases process-local listener state that must not survive service destruction. */
    override fun onDestroy() {
        if (instance === this) instance = null
        _bound.value = false
        NotificationMediaSessionRegistry.clear()
        if (mutedReturns > 0) setEffectsMuted(false)
        alerter.stop()
        scope.cancel()
        super.onDestroy()
    }

    /** Keeps cached behavior settings in sync without blocking notification callbacks on DataStore. */
    private fun observeBehaviour() {
        if (behaviourJob?.isActive == true) return
        behaviourJob = scope.launch {
            behaviourPreferences.settings
                .distinctUntilChanged()
                .collect { settings ->
                    dismissNotifications = settings.dismissNotifications
                    displayWhileDnd = settings.displayWhileDnd
                    alertOnNotification = settings.alertOnNotification
                }
        }
    }

    /** Whether Do Not Disturb should keep this non-call/non-timer notification off the island. */
    private fun suppressedByDnd(): Boolean {
        if (displayWhileDnd) return false
        return when (currentInterruptionFilter) {
            INTERRUPTION_FILTER_PRIORITY,
            INTERRUPTION_FILTER_ALARMS,
            INTERRUPTION_FILTER_NONE -> true
            else -> false
        }
    }

    /** Whether the user could plausibly have been looking at the island when a notification landed. */
    private fun isUserPresent(): Boolean {
        val power = getSystemService(PowerManager::class.java) ?: return false
        val keyguard = getSystemService(KeyguardManager::class.java) ?: return false
        return power.isInteractive && !keyguard.isKeyguardLocked
    }

    /** Rings/buzzes for a notification when the island is configured as the user's alert surface. */
    private fun alertFor(sbn: StatusBarNotification) {
        val ranking = Ranking()
        if (currentRanking?.getRanking(sbn.key, ranking) != true) return
        if (!ranking.matchesInterruptionFilter()) return
        val channel = ranking.channel
        val importance = ranking.importance
        scope.launch(Dispatchers.IO) { alerter.alert(channel, importance) }
    }

    /** Pulls [key] out of the shade while its pill is active. */
    private fun hold(key: String) {
        if (!snooze(key, MAX_HOLD_MS)) return
        returning.remove(key)
        pendingCancel.remove(key)
        held.track(key, SystemClock.elapsedRealtime() + MAX_HOLD_MS)
        verifyHold(key)
    }

    /** Optionally verifies that Android actually accepted a snooze request. */
    private fun verifyHold(key: String) {
        if (!TRACE_HOLDS) return
        scope.launch {
            delay(HOLD_CHECK_DELAY_MS)
            val active = runCatching { getActiveNotifications(arrayOf(key)) }
                .onFailure { Log.w(TAG, "getActiveNotifications failed for $key", it) }
                .getOrNull() ?: return@launch
            if (active.isNotEmpty()) {
                Log.w(TAG, "Snooze refused: $key still active after ${HOLD_CHECK_DELAY_MS}ms")
            } else {
                Log.i(TAG, "Snooze held: $key")
            }
        }
    }

    /** Ends a hold so an unacted-on notification can return to the panel. */
    private fun releaseHeld(key: String) {
        val ceiling = held.remove(key) ?: return
        muteReturn()
        snooze(key, RETURN_DELAY_MS)
        returning.track(key, ceiling)
    }

    /** Discards a held notification after the user acted on its pill. */
    private fun discard(key: String, onlyIfHeld: Boolean) {
        val ceiling = held.remove(key)
        if (ceiling == null) {
            if (onlyIfHeld || !dismissNotifications) return
            cancel(key)
            return
        }
        returning.remove(key)
        muteReturn()
        snooze(key, RETURN_DELAY_MS)
        pendingCancel.track(key, ceiling)
    }

    /** Temporarily mutes framework notification effects while held notifications are fetched back. */
    private fun muteReturn() {
        mutedReturns++
        if (mutedReturns == 1) setEffectsMuted(true)
        unmuteJob?.cancel()
        unmuteJob = scope.launch {
            delay(RETURN_DELAY_MS + SNOOZE_GRACE_MS)
            mutedReturns = 0
            setEffectsMuted(false)
        }
    }

    /** Drops one reference from the global effects-mute window after a fetch-back lands. */
    private fun endMutedReturn() {
        if (mutedReturns == 0) return
        mutedReturns--
        if (mutedReturns > 0) return
        unmuteJob?.cancel()
        unmuteJob = null
        setEffectsMuted(false)
    }

    /** Requests framework notification-effect suppression while [muted] is true. */
    private fun setEffectsMuted(muted: Boolean) {
        val hints = if (muted) HINT_HOST_DISABLE_NOTIFICATION_EFFECTS else 0
        runCatching { requestListenerHints(hints) }
            .onFailure { Log.w(TAG, "Failed to request listener hints", it) }
    }

    /** Snoozes [key] for [durationMs], reporting whether the call reached the framework. */
    private fun snooze(key: String, durationMs: Long): Boolean =
        runCatching { snoozeNotification(key, durationMs) }
            .onFailure { Log.w(TAG, "Failed to snooze notification $key", it) }
            .isSuccess

    /** Cancels [key] from the system notification panel. */
    private fun cancel(key: String) {
        runCatching { cancelNotification(key) }
            .onFailure { Log.w(TAG, "Failed to cancel notification $key", it) }
    }

    /** Tracks [key] until [dueAt], pruning expired and excessive entries. */
    private fun LinkedHashMap<String, Long>.track(key: String, dueAt: Long) {
        val now = SystemClock.elapsedRealtime()
        entries.removeAll { it.value + SNOOZE_GRACE_MS < now }
        put(key, dueAt)
        while (size > MAX_TRACKED_KEYS) remove(keys.first())
    }

    /** Consumes [key], returning true only when it still belongs to the tracked window. */
    private fun LinkedHashMap<String, Long>.consume(key: String): Boolean {
        val dueAt = remove(key) ?: return false
        return SystemClock.elapsedRealtime() <= dueAt + SNOOZE_GRACE_MS
    }

    /** Builds content identity stable across framework re-posts that reuse the same key. */
    private fun fingerprint(packageName: String, title: String?, text: String?): String =
        "$packageName\u0000${title.orEmpty()}\u0000${text.orEmpty()}"

    /** Returns whether [fingerprint] remains suppressed and prunes it once expired. */
    private fun LinkedHashMap<String, Long>.isSuppressed(fingerprint: String): Boolean {
        val until = this[fingerprint] ?: return false
        if (SystemClock.elapsedRealtime() > until) {
            remove(fingerprint)
            return false
        }
        return true
    }

    /** Remembers the fingerprint [key] was emitted under. */
    private fun rememberShown(key: String, fingerprint: String) {
        shownFingerprint[key] = fingerprint
        while (shownFingerprint.size > MAX_TRACKED_KEYS) shownFingerprint.remove(shownFingerprint.keys.first())
    }

    /** Suppresses the content previously shown for [key] for the short re-post grace window. */
    private fun markSuppressed(key: String) {
        val fingerprint = shownFingerprint.remove(key) ?: return
        val now = SystemClock.elapsedRealtime()
        suppressed.entries.removeAll { it.value < now }
        suppressed[fingerprint] = now + SUPPRESS_MS
        while (suppressed.size > MAX_TRACKED_KEYS) suppressed.remove(suppressed.keys.first())
    }

    /** Returns structured legacy progress data from [sbn], or null when no real progress exists. */
    fun getProgressDataOrNull(sbn: StatusBarNotification): ProgressData? {
        val extras = sbn.notification?.extras ?: return null
        val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val current = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        val isIndeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

        if (!isIndeterminate && max <= 0) return null

        return ProgressData(
            max = max,
            current = current.coerceIn(0, max.coerceAtLeast(0)),
            isIndeterminate = isIndeterminate,
            title = title,
        )
    }

    /** The single entry point for freshly posted notifications. */
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        onNotificationPosted(sbn, null)
    }

    /** Routes a posted notification through specialized, native, semantic, then legacy paths. */
    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        val notification = sbn ?: return

        if (CallNotificationParser.isCall(notification)) {
            handleCall(notification)
            return
        }

        if (TimerNotificationParser.isTimer(notification)) {
            handleTimer(notification)
            return
        }

        // Media side effects must publish before MEDIA routing returns.
        notification.publishMediaSessionFallback()
        notification.publishMediaArt()

        if (pendingCancel.consume(notification.key)) {
            cancel(notification.key)
            endMutedReturn()
            return
        }

        if (returning.consume(notification.key)) {
            endMutedReturn()
            return
        }

        val extras = notification.notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val progress = getProgressDataOrNull(notification)
        val fingerprint = fingerprint(notification.packageName, title, text)
        val blocked = notification.isExplicitlyBlockedFromLiveRouting() ||
            suppressedByDnd() ||
            (progress == null && suppressed.isSuppressed(fingerprint))
        val extracted = NotificationLiveSignalsExtractor.extract(this, notification)
        val result = liveActivityBridge.post(
            NotificationLiveActivityProcessor.Input(
                signals = extracted.signals,
                nativeSnapshot = extracted.nativeSnapshot,
                isMedia = notification.notification.extras
                    ?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true,
                blocked = blocked,
                legacySurfaceable = notification.shouldSurface(),
                nowElapsedRealtime = SystemClock.elapsedRealtime(),
            ),
        )

        when (result.route) {
            NotificationLiveActivityRouter.Route.NATIVE_LIVE,
            NotificationLiveActivityRouter.Route.PROGRESS_STYLE,
            NotificationLiveActivityRouter.Route.MEDIA,
            NotificationLiveActivityRouter.Route.IGNORE,
            NotificationLiveActivityRouter.Route.CALL,
            NotificationLiveActivityRouter.Route.TIMER -> return

            NotificationLiveActivityRouter.Route.SEMANTIC_LIVE -> {
                if (!result.preserveLegacyPresentation) return
            }

            NotificationLiveActivityRouter.Route.LEGACY_NOTIFICATION -> Unit
        }

        val appName = NotificationHeaderResolver.resolveAppName(this, notification.packageName)
        val postTimeMs = NotificationHeaderResolver.resolvePostTimeMs(notification.postTime)
        val isSilent = isSilentNotification(notification, rankingMap)
        val islandEvent = CutoutSignal.Notification(
            packageName = notification.packageName,
            title = title,
            text = text,
            appName = appName,
            postTimeMs = postTimeMs,
            key = notification.key,
            contentIntent = notification.notification.contentIntent,
            actions = notification.notification.surfaceableActions(),
            largeIcon = notification.notification.getLargeIcon(),
            smallIcon = notification.notification.smallIcon,
            progressData = progress,
            isSilent = isSilent,
        )

        IslandEventBus.emit(islandEvent)
        rememberShown(notification.key, fingerprint)

        if (alertOnNotification && progress == null) {
            alertFor(notification)
        }

        if (dismissNotifications && progress == null && isUserPresent()) {
            hold(notification.key)
        }
    }

    /** Determines whether [sbn] represents a silent notification. */
    fun isSilentNotification(sbn: StatusBarNotification, rankingMap: RankingMap? = null): Boolean {
        val ranking = Ranking()
        val found = (rankingMap != null && rankingMap.getRanking(sbn.key, ranking)) ||
            (currentRanking != null && currentRanking.getRanking(sbn.key, ranking))
        if (found) {
            val importance = ranking.importance
            val isAmbient = ranking.isAmbient
            val priority = sbn.notification?.priority ?: Notification.PRIORITY_DEFAULT
            return NotificationClassifier.isSilent(
                importance = importance,
                isAmbient = isAmbient,
                priority = priority,
            )
        }
        val priority = sbn.notification?.priority ?: Notification.PRIORITY_DEFAULT
        return NotificationClassifier.isSilent(
            importance = null,
            isAmbient = false,
            priority = priority,
        )
    }

    /** Removes live and legacy state owned by a notification that left the framework. */
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val removed = sbn
        if (removed != null) {
            NotificationMediaSessionRegistry.remove(removed.key)
            liveActivityBridge.remove(removed.packageName, removed.key)
            if (CallNotificationParser.isCall(removed)) {
                LiveActivityRegistry.coordinator.remove(
                    SpecializedLiveActivityFactory.callId(removed.packageName, removed.key),
                )
            }
            if (TimerNotificationParser.isTimer(removed)) {
                LiveActivityRegistry.coordinator.remove(
                    SpecializedLiveActivityFactory.timerId(removed.packageName, removed.key),
                )
            }
        }

        if (removed?.key != null && removed.key == currentCallKey) {
            currentCallKey = null
            OnCallBus.update(null)
        }
        if (removed?.key != null && removed.key == currentTimerKey) {
            currentTimerKey = null
            RunningTimerBus.update(null)
        }
        if (removed?.key != null && removed.key == currentMediaArtKey) {
            currentMediaArtKey = null
            MediaArtBus.update(null)
        }
        super.onNotificationRemoved(sbn)
    }

    /** Keeps the specialized call bus and live coordinator representation synchronized. */
    private fun handleCall(sbn: StatusBarNotification) {
        val call = CallNotificationParser.parse(sbn, this)
        val prevOngoing = OnCallBus.state.value?.ongoing

        LiveActivityRegistry.coordinator.upsert(
            SpecializedLiveActivityFactory.call(
                packageName = sbn.packageName,
                notificationKey = sbn.key,
                callerLabel = call.callerLabel,
                ongoing = call.ongoing,
                nowElapsedRealtime = SystemClock.elapsedRealtime(),
                contentIntent = sbn.notification.contentIntent,
                actions = call.actions.map { action ->
                    LiveActivity.Action(label = action.title, intent = action.intent)
                },
            ),
        )

        OnCallBus.update(
            OnCall(
                callerLabel = call.callerLabel,
                callerNumber = call.callerNumber,
                photo = call.photo,
                startTimeMs = call.startTimeMs,
                ongoing = call.ongoing,
                packageName = sbn.packageName,
            ),
        )

        if (sbn.key != currentCallKey || prevOngoing != call.ongoing) {
            currentCallKey = sbn.key
            IslandEventBus.emit(
                CutoutSignal.Call(
                    packageName = sbn.packageName,
                    callerLabel = call.callerLabel,
                    key = sbn.key,
                    contentIntent = sbn.notification.contentIntent,
                    actions = call.actions,
                    ongoing = call.ongoing,
                ),
            )
        }
    }

    /** Keeps the specialized timer bus and live coordinator representation synchronized. */
    private fun handleTimer(sbn: StatusBarNotification) {
        val timer = TimerNotificationParser.parse(sbn)

        LiveActivityRegistry.coordinator.upsert(
            SpecializedLiveActivityFactory.timer(
                packageName = sbn.packageName,
                notificationKey = sbn.key,
                label = timer.label,
                endElapsedRealtimeMs = timer.endElapsedRealtimeMs,
                pausedRemainingMs = timer.pausedRemainingMs,
                nowElapsedRealtime = SystemClock.elapsedRealtime(),
                contentIntent = sbn.notification.contentIntent,
                actions = timer.actions.map { action ->
                    LiveActivity.Action(label = action.title, intent = action.intent)
                },
            ),
        )

        RunningTimerBus.update(
            RunningTimer(
                endElapsedRealtimeMs = timer.endElapsedRealtimeMs,
                pausedRemainingMs = timer.pausedRemainingMs,
                label = timer.label,
                actions = timer.actions,
            ),
        )
        if (sbn.key != currentTimerKey) {
            currentTimerKey = sbn.key
            IslandEventBus.emit(
                CutoutSignal.Timer(
                    packageName = sbn.packageName,
                    label = timer.label,
                    key = sbn.key,
                    contentIntent = sbn.notification.contentIntent,
                    actions = timer.actions,
                ),
            )
        }
    }

    /** Returns action buttons worth mirroring into the current legacy notification renderer. */
    private fun Notification.surfaceableActions(): List<CutoutSignal.Notification.Action> =
        actions.orEmpty().mapNotNull { action ->
            val title = action.title?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val intent = action.actionIntent ?: return@mapNotNull null
            CutoutSignal.Notification.Action(title, intent, action.toReplyInput())
        }

    /** Builds a reply descriptor if this action accepts free-form typed text. */
    private fun Notification.Action.toReplyInput(): CutoutSignal.Notification.ReplyInput? {
        val inputs = remoteInputs?.toList().orEmpty()
        val freeForm = inputs.firstOrNull { it.allowFreeFormInput } ?: return null
        return CutoutSignal.Notification.ReplyInput(
            resultKey = freeForm.resultKey,
            remoteInputs = inputs,
            hint = freeForm.label?.toString(),
        )
    }

    /** Publishes a media notification's MediaSession token as a playback-monitor fallback. */
    private fun StatusBarNotification.publishMediaSessionFallback() {
        val token = runCatching {
            notification.extras?.let { extras ->
                BundleCompat.getParcelable(
                    extras,
                    Notification.EXTRA_MEDIA_SESSION,
                    MediaSession.Token::class.java,
                )
            }
        }.onFailure {
            Log.w(TAG, "Failed to read media-session token from notification $key", it)
        }.getOrNull()
        NotificationMediaSessionRegistry.update(
            notificationKey = key,
            packageName = packageName,
            token = token,
        )
    }

    /** Publishes a media notification's local large icon as fallback album art. */
    private fun StatusBarNotification.publishMediaArt() {
        if (notification.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) != true) return
        val art = notification.getLargeIcon()
            ?.loadImageBitmapOrNull(this@CutoutNotificationListenerService)
            ?: return
        currentMediaArtKey = key
        MediaArtBus.update(MediaArt(packageName = packageName, art = art))
    }

    /** Explicit exclusions that must win even over promoted/native live evidence. */
    private fun StatusBarNotification.isExplicitlyBlockedFromLiveRouting(): Boolean {
        if (packageName == this@CutoutNotificationListenerService.packageName) return true
        return notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
    }

    /** Applies the legacy ordinary-notification surfaceability filter. */
    private fun StatusBarNotification.shouldSurface(): Boolean {
        if (packageName == this@CutoutNotificationListenerService.packageName) return false
        val flags = notification.flags
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        if (getProgressDataOrNull(this) != null) return true
        val isOngoing = flags and Notification.FLAG_ONGOING_EVENT != 0
        return isClearable && !isOngoing
    }

    companion object {
        private const val TAG = "CutoutNotifListener"

        /** Enables verbose hold diagnostics only while investigating snooze behavior. */
        private const val TRACE_HOLDS = false

        /** Delay before optionally verifying that a framework snooze actually took effect. */
        private const val HOLD_CHECK_DELAY_MS = 400L

        /** Maximum duration for a notification hold if the overlay never reports completion. */
        private const val MAX_HOLD_MS = 60_000L

        /** Delay used to fetch a held notification back from the framework. */
        private const val RETURN_DELAY_MS = 500L

        /** Grace allowed for framework re-posts that arrive later than their nominal window. */
        private const val SNOOZE_GRACE_MS = 3_000L

        /** Upper bound for in-flight key tracking maps. */
        private const val MAX_TRACKED_KEYS = 64

        /** Short suppression window preventing a completed pill from immediately re-popping. */
        private const val SUPPRESS_MS = 8_000L

        /** The currently connected listener, or null while unbound. */
        @Volatile
        private var instance: CutoutNotificationListenerService? = null

        private val _bound = MutableStateFlow(false)

        /** True only while Android actually has this notification listener bound. */
        val bound: StateFlow<Boolean> = _bound.asStateFlow()

        /** Asks Android to rebind this notification listener after a stale grant/binding state. */
        fun requestRebind(context: Context) {
            val component = ComponentName(context, CutoutNotificationListenerService::class.java)
            runCatching { requestRebind(component) }
                .onFailure { Log.w(TAG, "Failed to request listener rebind", it) }
        }

        /** Handles a user swipe of the island pill associated with [key]. */
        fun dismiss(key: String) {
            instance?.run {
                markSuppressed(key)
                discard(key, onlyIfHeld = false)
            }
        }

        /** Handles a user action on the island pill associated with [key]. */
        fun settle(key: String) {
            instance?.run {
                markSuppressed(key)
                discard(key, onlyIfHeld = true)
            }
        }

        /** Releases an unacted-on pill's held system notification back to the panel. */
        fun release(key: String) {
            instance?.run {
                markSuppressed(key)
                releaseHeld(key)
            }
        }
    }
}
