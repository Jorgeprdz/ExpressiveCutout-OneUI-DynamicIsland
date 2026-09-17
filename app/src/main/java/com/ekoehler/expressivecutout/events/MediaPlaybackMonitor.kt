package com.ekoehler.expressivecutout.events

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.ekoehler.expressivecutout.core.CutoutSignal
import com.ekoehler.expressivecutout.core.DynamicTile
import com.ekoehler.expressivecutout.core.IslandEventBus
import com.ekoehler.expressivecutout.core.MediaProgress
import com.ekoehler.expressivecutout.core.MediaTransport
import com.ekoehler.expressivecutout.core.NowPlaying
import com.ekoehler.expressivecutout.core.NowPlayingBus
import com.ekoehler.expressivecutout.core.live.LiveActivityRegistry
import com.ekoehler.expressivecutout.data.AppPreferences
import com.ekoehler.expressivecutout.data.DynamicTilePreferences
import com.ekoehler.expressivecutout.notifications.live.SpecializedLiveActivityFactory
import com.ekoehler.expressivecutout.overlay.loadImageBitmapOrNull
import com.ekoehler.expressivecutout.overlay.toArtImageBitmap
import com.ekoehler.expressivecutout.service.CutoutNotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Watches the device's active media sessions and drives the music tile. It keeps [NowPlayingBus]
 * in sync with the current session (title, artist, album art, play/pause state and a transport
 * handle) and republishes a [CutoutSignal.Music] whenever playback starts or the track changes, so
 * the island pops up. MediaSessionManager remains the preferred source; notification-carried
 * MediaSession tokens are watched as a fallback for OEMs that omit a player from getActiveSessions.
 */
class MediaPlaybackMonitor(private val context: Context) {

    private val sessionManager = context.getSystemService<MediaSessionManager>()
    private val listenerComponent = ComponentName(context, CutoutNotificationListenerService::class.java)
    private val dynamicTilePreferences = DynamicTilePreferences(context)
    private val appPreferences = AppPreferences(context)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Framework-discovered controllers we're currently watching, paired with their callback. */
    private val watched = mutableMapOf<MediaController, MediaController.Callback>()

    /** Notification-backed candidates last published by the notification listener. */
    private var fallbackSessions: Map<String, NotificationMediaSessionCandidate> = emptyMap()

    /** Notification-backed controllers currently watched because no active equivalent exists. */
    private val fallbackWatched = mutableMapOf<String, FallbackWatcher>()

    /** Enabled state of dynamic tiles. */
    private var tileEnabled: Map<DynamicTile, Boolean> = emptyMap()

    /** Packages the user muted on the Apps screen; their sessions are ignored outright. */
    private var disabledApps: Set<String> = emptySet()

    /** The track last surfaced as a "show" signal, so we don't re-pop on every state tick. */
    private var lastShownKey: String? = null

    /** The pending "show" emission, held for [SHOW_DEBOUNCE_MS] so a start settles into one pop. */
    private var showJob: Job? = null

    /** Stable ID of the media session currently registered with the live coordinator. */
    private var currentLiveMusicId: String? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            rebind(controllers.orEmpty())
        }

    /**
     * Begins watching active sessions, notification-backed fallback sessions, and tile/app settings.
     * Failure to query MediaSessionManager no longer disables the notification fallback path.
     */
    fun start() {
        scope.launch {
            dynamicTilePreferences.enabled.collect { enabled ->
                tileEnabled = enabled
                sync()
            }
        }
        // Muting an app mid-playback should drop its tile straight away, so re-sync on every change.
        scope.launch {
            appPreferences.disabledPackages.collect { disabled ->
                disabledApps = disabled
                sync()
            }
        }
        scope.launch {
            NotificationMediaSessionRegistry.sessions.collect { sessions ->
                fallbackSessions = sessions
                rebindFallback()
            }
        }

        val manager = sessionManager ?: run {
            Log.w(TAG, "MediaSessionManager unavailable; using notification media-session fallback")
            return
        }
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
            rebind(manager.getActiveSessions(listenerComponent))
        }.onFailure {
            Log.w(TAG, "Media session access unavailable; using notification media-session fallback", it)
        }
    }

    /**
     * Unregisters every session callback and clears the published state, so a disabled tile leaves
     * nothing behind on the island.
     */
    fun stop() {
        sessionManager?.let { runCatching { it.removeOnActiveSessionsChangedListener(sessionsListener) } }
        watched.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
        watched.clear()
        fallbackWatched.values.forEach { watcher ->
            watcher.controller.unregisterCallback(watcher.callback)
        }
        fallbackWatched.clear()
        scope.coroutineContext.cancelChildren()
        clearPendingShow()
        clearLiveMusic()
        NowPlayingBus.update(null)
    }

    /** Removes the media session currently registered in the process-wide live coordinator. */
    private fun clearLiveMusic() {
        currentLiveMusicId?.let(LiveActivityRegistry.coordinator::remove)
        currentLiveMusicId = null
    }

    /** Forgets the surfaced track and drops any pop still waiting to fire. */
    private fun clearPendingShow() {
        showJob?.cancel()
        showJob = null
        lastShownKey = null
    }

    /** Attach callbacks to newly active sessions and detach ones that have gone away. */
    private fun rebind(controllers: List<MediaController>) {
        val current = controllers.toSet()
        watched.keys.filter { it !in current }.toList().forEach(::detach)

        controllers.filter { it !in watched }.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = sync()
                override fun onMetadataChanged(metadata: MediaMetadata?) = sync()
                override fun onSessionDestroyed() = detach(controller)
            }
            watched[controller] = callback
            controller.registerCallback(callback)
        }
        rebindFallback()
        sync()
    }

    /** Stops watching one framework controller and allows a stored fallback to take over if needed. */
    private fun detach(controller: MediaController) {
        watched.remove(controller)?.let { controller.unregisterCallback(it) }
        rebindFallback()
        sync()
    }

    /**
     * Reconciles notification-backed tokens with the framework sessions. Tokens already represented
     * by MediaSessionManager are deliberately not watched twice.
     */
    private fun rebindFallback() {
        val activeIdentities = watched.keys.mapTo(mutableSetOf()) { it.sessionIdentity }
        val desired = fallbackSessions.filterValues { it.identity !in activeIdentities }

        fallbackWatched.keys.toList().forEach { notificationKey ->
            val existing = fallbackWatched[notificationKey] ?: return@forEach
            val replacement = desired[notificationKey]
            if (replacement == null || replacement.identity != existing.candidate.identity) {
                detachFallback(notificationKey, resync = false)
            }
        }

        desired.forEach { (notificationKey, candidate) ->
            if (fallbackWatched.containsKey(notificationKey)) return@forEach
            val controller = runCatching { MediaController(context, candidate.token) }
                .onFailure { Log.w(TAG, "Failed to create fallback media controller", it) }
                .getOrNull() ?: return@forEach
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = sync()
                override fun onMetadataChanged(metadata: MediaMetadata?) = sync()
                override fun onSessionDestroyed() {
                    NotificationMediaSessionRegistry.remove(notificationKey)
                    detachFallback(notificationKey)
                }
            }
            val registered = runCatching { controller.registerCallback(callback) }
                .onFailure { Log.w(TAG, "Failed to watch fallback media controller", it) }
                .isSuccess
            if (registered) {
                fallbackWatched[notificationKey] = FallbackWatcher(candidate, controller, callback)
            }
        }
        sync()
    }

    /** Stops watching one notification-backed controller. */
    private fun detachFallback(notificationKey: String, resync: Boolean = true) {
        fallbackWatched.remove(notificationKey)?.let { watcher ->
            watcher.controller.unregisterCallback(watcher.callback)
        }
        if (resync) sync()
    }

    /**
     * Whether the package is a voice assistant. Assistants publish a media session for their own
     * chime, which would otherwise pop a music tile for a sound the user never started.
     */
    private fun isAssistantPackage(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        return pkg == "com.google.android.googlequicksearchbox" ||
            pkg == "com.google.android.apps.googleassistant" ||
            pkg == "com.google.android.apps.bard" ||
            pkg == "com.samsung.android.bixby.agent" ||
            pkg == "com.samsung.android.bixby.service" ||
            pkg == "com.amazon.dee.app" ||
            pkg == "com.openai.chatgpt" ||
            pkg == "com.microsoft.copilot" ||
            pkg.contains("assistant") ||
            pkg.contains("bixby") ||
            pkg.contains("gemini")
    }

    /** Whether a controller remains eligible after app and assistant filtering. */
    private fun isEligible(controller: MediaController): Boolean {
        if (controller.packageName in disabledApps) return false
        if (isAssistantPackage(controller.packageName)) {
            return tileEnabled[DynamicTile.ASSISTANT] != false
        }
        return true
    }

    /**
     * Recompute the surfaced session. Framework sessions are primary; notification-backed sessions
     * are considered only when no valid framework candidate exists.
     */
    private fun sync() {
        val controllerCandidates = buildList {
            watched.keys.filter(::isEligible).forEach { controller ->
                add(
                    ControllerCandidate(
                        selection = MediaSessionSelectionCandidate(
                            identity = controller.sessionIdentity,
                            source = MediaSessionCandidateSource.ACTIVE,
                            isPlaying = controller.isPlaying,
                        ),
                        controller = controller,
                    ),
                )
            }
            fallbackWatched.values
                .map { it.controller }
                .filter(::isEligible)
                .forEach { controller ->
                    add(
                        ControllerCandidate(
                            selection = MediaSessionSelectionCandidate(
                                identity = controller.sessionIdentity,
                                source = MediaSessionCandidateSource.NOTIFICATION_FALLBACK,
                                isPlaying = controller.isPlaying,
                            ),
                            controller = controller,
                        ),
                    )
                }
        }

        val selected = selectMediaSessionCandidate(controllerCandidates.map { it.selection })
        val primary = selected?.let { target ->
            controllerCandidates.firstOrNull { it.selection == target }?.controller
        }
        if (primary == null) {
            NowPlayingBus.update(null)
            clearPendingShow()
            clearLiveMusic()
            return
        }

        val playing = primary.isPlaying
        val metadata = primary.metadata

        val rawTitle = metadata?.getText(MediaMetadata.METADATA_KEY_TITLE)?.toString()
            ?: metadata?.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.toString()
        val rawArtist = metadata?.getText(MediaMetadata.METADATA_KEY_ARTIST)?.toString()
            ?: metadata?.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)?.toString()
            ?: metadata?.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)?.toString()
            ?: metadata?.getText(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION)?.toString()
            ?: metadata?.getText(MediaMetadata.METADATA_KEY_ALBUM)?.toString()
            ?: metadata?.getText(MediaMetadata.METADATA_KEY_AUTHOR)?.toString()

        val title = rawTitle
        val artist = rawArtist

        if (isAssistantPackage(primary.packageName)) {
            // Assistant sessions are handled exclusively via NotificationListenerService.
            NowPlayingBus.update(null)
            clearPendingShow()
            clearLiveMusic()
            return
        }

        val albumArt = metadata?.albumArt()

        NowPlayingBus.update(
            NowPlaying(
                packageName = primary.packageName,
                title = title,
                artist = artist,
                albumArt = albumArt,
                isPlaying = playing,
                transport = ControllerTransport(primary),
                progress = primary.progress(metadata, playing),
            ),
        )

        val liveMusic = SpecializedLiveActivityFactory.music(
            packageName = primary.packageName,
            sessionId = primary.sessionToken.hashCode().toString(),
            title = title,
            artist = artist,
            isPlaying = playing,
            nowElapsedRealtime = SystemClock.elapsedRealtime(),
            contentIntent = primary.sessionActivity,
        )
        if (currentLiveMusicId != liveMusic.stableId) {
            clearLiveMusic()
            currentLiveMusicId = liveMusic.stableId
        }
        LiveActivityRegistry.coordinator.upsert(liveMusic)

        // Pop the island when a fresh track begins playing; reset when paused so a resume re-pops.
        if (!playing) {
            clearPendingShow()
            return
        }
        val key = "${primary.packageName}|$title|$artist"
        if (key == lastShownKey) return
        lastShownKey = key
        // Held briefly rather than emitted here: players routinely report STATE_PLAYING a tick or two
        // before publishing the track, so the same start arrives first as "no metadata" and then as
        // the real title — two different keys, which read as two tracks starting and would leave the
        // island showing the same tile twice. Waiting for the metadata to settle collapses that into
        // one pop carrying the final track, while a genuine track change is still its own pop.
        val signal = CutoutSignal.Music(
            packageName = primary.packageName,
            title = title,
            artist = artist,
            contentIntent = primary.sessionActivity,
        )
        showJob?.cancel()
        showJob = scope.launch {
            delay(SHOW_DEBOUNCE_MS)
            IslandEventBus.emit(signal)
        }
    }

    private val MediaController.isPlaying: Boolean
        get() = playbackState?.state == PlaybackState.STATE_PLAYING

    private val MediaController.sessionIdentity: String
        get() = mediaSessionIdentity(packageName, sessionToken.hashCode())

    /**
     * The session's position anchor. [PlaybackState.getPosition] is a sample taken at
     * [PlaybackState.getLastPositionUpdateTime], not a live figure — it is passed through as-is and
     * the tile extrapolates. Players that publish no duration, or a negative one for a live stream,
     * yield a null length and an indeterminate bar. A state carrying [PlaybackState.PLAYBACK_POSITION_UNKNOWN]
     * gives no anchor at all.
     */
    private fun MediaController.progress(metadata: MediaMetadata?, playing: Boolean): MediaProgress? {
        val state = playbackState ?: return null
        if (state.position == PlaybackState.PLAYBACK_POSITION_UNKNOWN) return null

        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0L }
        // A paused session keeps its position but must not creep; a player reporting a nonsense
        // speed while playing is treated as ordinary 1x rather than freezing the bar.
        val speed = if (playing) state.playbackSpeed.takeIf { it > 0f } ?: 1f else 0f
        return MediaProgress(
            positionMs = state.position.coerceAtLeast(0L),
            durationMs = duration,
            speed = speed,
            anchorUptimeMs = state.lastPositionUpdateTime.takeIf { it > 0L } ?: SystemClock.elapsedRealtime(),
        )
    }

    /**
     * The cover the session itself carries: a bitmap if the player published one, else a URI we can
     * read locally. A player pointing at a remote CDN (Spotify) yields null here and the tile falls
     * back to the cover lifted off its media notification — see
     * [com.ekoehler.expressivecutout.core.MediaArtBus].
     */
    private fun MediaMetadata.albumArt(): ImageBitmap? = (
        getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        )?.toArtImageBitmap()
        ?: artUri()?.loadImageBitmapOrNull(context)

    /** The art URI a player publishes in place of a bitmap, if it gave one at all. */
    private fun MediaMetadata.artUri(): Uri? = listOf(
        MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
        MediaMetadata.METADATA_KEY_ART_URI,
        MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
    ).firstNotNullOfOrNull { key -> getString(key)?.takeIf { it.isNotBlank() } }
        ?.let { runCatching { it.toUri() }.getOrNull() }

    /** Bridges the tile's transport buttons to the active session's controls. */
    private class ControllerTransport(private val controller: MediaController) : MediaTransport {
        override fun previous() {
            runCatching { controller.transportControls.skipToPrevious() }
        }

        override fun playPause() {
            runCatching {
                if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    controller.transportControls.pause()
                } else {
                    controller.transportControls.play()
                }
            }
        }

        override fun next() {
            runCatching { controller.transportControls.skipToNext() }
        }

        override val canSeek: Boolean
            get() = ((controller.playbackState?.actions ?: 0L) and PlaybackState.ACTION_SEEK_TO) != 0L

        override fun seekTo(positionMs: Long) {
            if (!canSeek) return
            runCatching { controller.transportControls.seekTo(positionMs.coerceAtLeast(0L)) }
        }
    }

    private data class FallbackWatcher(
        val candidate: NotificationMediaSessionCandidate,
        val controller: MediaController,
        val callback: MediaController.Callback,
    )

    private data class ControllerCandidate(
        val selection: MediaSessionSelectionCandidate,
        val controller: MediaController,
    )

    private companion object {
        const val TAG = "MediaPlaybackMonitor"

        /**
         * How long a new track is held before it pops the island, letting a session that reports its
         * playback state and its metadata in separate ticks settle into a single signal. Short enough
         * that a real track change still feels immediate.
         */
        const val SHOW_DEBOUNCE_MS = 250L
    }
}
