#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def read(path):
    return (ROOT / path).read_text()

def write(path, text):
    (ROOT / path).write_text(text)

def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)

# 1) Music settings model / persistence / JSON.
path = "app/src/main/java/com/ekoehler/expressivecutout/data/MusicTilePreferences.kt"
text = read(path)
text = replace_once(text,
'''    /** Show a playback progress bar under the transport controls. */
    val showProgress: Boolean = DEFAULT_SHOW_PROGRESS,
) {''',
'''    /** Show a playback progress bar under the transport controls. */
    val showProgress: Boolean = DEFAULT_SHOW_PROGRESS,
    /** Use the expanded Material Expressive music layout. */
    val materialExpressivePlayer: Boolean = DEFAULT_MATERIAL_EXPRESSIVE_PLAYER,
    /** Derive music-only player colours from the current album artwork. */
    val useAlbumColours: Boolean = DEFAULT_USE_ALBUM_COLOURS,
    /** Draw the official Material Expressive wavy playback progress indicator. */
    val wavyProgress: Boolean = DEFAULT_WAVY_PROGRESS,
) {''', "music settings fields")
text = replace_once(text,
'''        const val DEFAULT_SHOW_CONTROLS = true
        const val DEFAULT_SHOW_PROGRESS = false
    }
}

/** Persists the music tile's display options (album art, expanded controls) and button styling. */''',
'''        const val DEFAULT_SHOW_CONTROLS = true
        const val DEFAULT_SHOW_PROGRESS = false
        const val DEFAULT_MATERIAL_EXPRESSIVE_PLAYER = false
        const val DEFAULT_USE_ALBUM_COLOURS = false
        const val DEFAULT_WAVY_PROGRESS = false
    }
}

/** The expressive-only fields exported with music settings. */
internal fun MusicTileSettings.expressiveJsonValues(): Map<String, Boolean> = mapOf(
    "materialExpressivePlayer" to materialExpressivePlayer,
    "useAlbumColours" to useAlbumColours,
    "wavyProgress" to wavyProgress,
)

/** Applies expressive JSON values while preserving existing values for absent legacy keys. */
internal fun MusicTileSettings.withExpressiveJsonValues(values: Map<String, Boolean>): MusicTileSettings = copy(
    materialExpressivePlayer = values["materialExpressivePlayer"] ?: materialExpressivePlayer,
    useAlbumColours = values["useAlbumColours"] ?: useAlbumColours,
    wavyProgress = values["wavyProgress"] ?: wavyProgress,
)

/** Persists the music tile's display options (album art, expanded controls) and button styling. */''',
"music defaults/helpers")
text = replace_once(text,
'''            showProgress = prefs[SHOW_PROGRESS] ?: MusicTileSettings.DEFAULT_SHOW_PROGRESS,
        )''',
'''            showProgress = prefs[SHOW_PROGRESS] ?: MusicTileSettings.DEFAULT_SHOW_PROGRESS,
            materialExpressivePlayer = prefs[MATERIAL_EXPRESSIVE_PLAYER]
                ?: MusicTileSettings.DEFAULT_MATERIAL_EXPRESSIVE_PLAYER,
            useAlbumColours = prefs[USE_ALBUM_COLOURS] ?: MusicTileSettings.DEFAULT_USE_ALBUM_COLOURS,
            wavyProgress = prefs[WAVY_PROGRESS] ?: MusicTileSettings.DEFAULT_WAVY_PROGRESS,
        )''', "settings flow")
text = replace_once(text,
'''            put("showControls", s.showControls)
            put("showProgress", s.showProgress)
            put("skipButton", s.skipButton.toJsonObject())''',
'''            put("showControls", s.showControls)
            put("showProgress", s.showProgress)
            s.expressiveJsonValues().forEach { (key, value) -> put(key, value) }
            put("skipButton", s.skipButton.toJsonObject())''', "json export")
text = replace_once(text,
'''            if (obj.has("showControls")) prefs[SHOW_CONTROLS] = obj.getBoolean("showControls")
            if (obj.has("showProgress")) prefs[SHOW_PROGRESS] = obj.getBoolean("showProgress")

            obj.optJSONObject("skipButton")''',
'''            if (obj.has("showControls")) prefs[SHOW_CONTROLS] = obj.getBoolean("showControls")
            if (obj.has("showProgress")) prefs[SHOW_PROGRESS] = obj.getBoolean("showProgress")
            if (obj.has("materialExpressivePlayer")) {
                prefs[MATERIAL_EXPRESSIVE_PLAYER] = obj.getBoolean("materialExpressivePlayer")
            }
            if (obj.has("useAlbumColours")) {
                prefs[USE_ALBUM_COLOURS] = obj.getBoolean("useAlbumColours")
            }
            if (obj.has("wavyProgress")) prefs[WAVY_PROGRESS] = obj.getBoolean("wavyProgress")

            obj.optJSONObject("skipButton")''', "json import")
text = replace_once(text,
'''    suspend fun setShowProgress(enabled: Boolean) = context.musicTileDataStore.edit {
        it[SHOW_PROGRESS] = enabled
    }

    /**''',
'''    suspend fun setShowProgress(enabled: Boolean) = context.musicTileDataStore.edit {
        it[SHOW_PROGRESS] = enabled
    }

    suspend fun setMaterialExpressivePlayer(enabled: Boolean) = context.musicTileDataStore.edit {
        it[MATERIAL_EXPRESSIVE_PLAYER] = enabled
    }

    suspend fun setUseAlbumColours(enabled: Boolean) = context.musicTileDataStore.edit {
        it[USE_ALBUM_COLOURS] = enabled
    }

    suspend fun setWavyProgress(enabled: Boolean) = context.musicTileDataStore.edit {
        it[WAVY_PROGRESS] = enabled
    }

    /**''', "preference setters")
text = replace_once(text,
'''        val PLAY_PAUSE_FILLED = booleanPreferencesKey("play_pause_button_filled")
        val SHOW_PROGRESS = booleanPreferencesKey("show_current_progress")
    }''',
'''        val PLAY_PAUSE_FILLED = booleanPreferencesKey("play_pause_button_filled")
        val SHOW_PROGRESS = booleanPreferencesKey("show_current_progress")
        val MATERIAL_EXPRESSIVE_PLAYER = booleanPreferencesKey("material_expressive_player")
        val USE_ALBUM_COLOURS = booleanPreferencesKey("use_album_colours")
        val WAVY_PROGRESS = booleanPreferencesKey("wavy_progress")
    }''', "preference keys")
write(path, text)

# 2) Media-only options propagation.
path = "app/src/main/java/com/ekoehler/expressivecutout/overlay/IslandEvent.kt"
text = read(path)
text = replace_once(text,
'''    /** Show the playback progress bar under the controls. */
    val showProgress: Boolean = false,
    /** Look of the previous / next (skip) buttons. */''',
'''    /** Show the playback progress bar under the controls. */
    val showProgress: Boolean = false,
    /** Use the expanded Material Expressive music layout. */
    val materialExpressivePlayer: Boolean = false,
    /** Derive the music player's palette from its current artwork. */
    val useAlbumColours: Boolean = false,
    /** Use the official Material Expressive wavy progress indicator. */
    val wavyProgress: Boolean = false,
    /** Look of the previous / next (skip) buttons. */''', "media options")
write(path, text)

path = "app/src/main/java/com/ekoehler/expressivecutout/overlay/IconResolver.kt"
text = read(path)
text = replace_once(text,
'''                showControls = settings.showControls,
                showProgress = settings.showProgress,
                skipStyle = settings.skipButton,''',
'''                showControls = settings.showControls,
                showProgress = settings.showProgress,
                materialExpressivePlayer = settings.materialExpressivePlayer,
                useAlbumColours = settings.useAlbumColours,
                wavyProgress = settings.wavyProgress,
                skipStyle = settings.skipButton,''', "resolver media options")
write(path, text)

# 3) ViewModel setters.
path = "app/src/main/java/com/ekoehler/expressivecutout/ui/AppViewModel.kt"
text = read(path)
text = replace_once(text,
'''    fun setMusicShowProgress(enabled: Boolean) = viewModelScope.launch {
        musicTilePreferences.setShowProgress(enabled)
    }
''',
'''    fun setMusicShowProgress(enabled: Boolean) = viewModelScope.launch {
        musicTilePreferences.setShowProgress(enabled)
    }

    fun setMusicMaterialExpressivePlayer(enabled: Boolean) = viewModelScope.launch {
        musicTilePreferences.setMaterialExpressivePlayer(enabled)
    }

    fun setMusicUseAlbumColours(enabled: Boolean) = viewModelScope.launch {
        musicTilePreferences.setUseAlbumColours(enabled)
    }

    fun setMusicWavyProgress(enabled: Boolean) = viewModelScope.launch {
        musicTilePreferences.setWavyProgress(enabled)
    }
''', "viewmodel setters")
write(path, text)

# 4) Existing Music Settings screen.
path = "app/src/main/java/com/ekoehler/expressivecutout/ui/screen/tiles/MusicTileScreen.kt"
text = read(path)
text = replace_once(text,
'''        SettingsToggleCard(
            shape = RoundedCornerShape(size = 24.dp),
            title = stringResource(R.string.music_progress_title),
            description = stringResource(R.string.music_progress_description),
            checked = settings.showProgress,
            onCheckedChange = viewModel::setMusicShowProgress,
        )
    }
}''',
'''        SettingsToggleCard(
            shape = RoundedCornerShape(size = 24.dp),
            title = stringResource(R.string.music_progress_title),
            description = stringResource(R.string.music_progress_description),
            checked = settings.showProgress,
            onCheckedChange = viewModel::setMusicShowProgress,
        )
        SettingsToggleCard(
            shape = RoundedCornerShape(size = 24.dp),
            title = stringResource(R.string.music_material_expressive_title),
            description = stringResource(R.string.music_material_expressive_desc),
            checked = settings.materialExpressivePlayer,
            onCheckedChange = viewModel::setMusicMaterialExpressivePlayer,
        )
        SettingsToggleCard(
            shape = RoundedCornerShape(size = 24.dp),
            title = stringResource(R.string.music_album_colours_title),
            description = stringResource(R.string.music_album_colours_desc),
            checked = settings.useAlbumColours,
            onCheckedChange = viewModel::setMusicUseAlbumColours,
        )
        SettingsToggleCard(
            shape = RoundedCornerShape(size = 24.dp),
            title = stringResource(R.string.music_wavy_progress_title),
            description = stringResource(R.string.music_wavy_progress_desc),
            checked = settings.wavyProgress,
            onCheckedChange = viewModel::setMusicWavyProgress,
        )
    }
}''', "music settings ui")
write(path, text)

path = "app/src/main/res/values/strings.xml"
text = read(path)
text = replace_once(text,
'''    <string name="music_progress_title">Show progress</string>
    <string name="music_progress_description">Display a playback progress bar under the controls</string>''',
'''    <string name="music_progress_title">Show progress</string>
    <string name="music_progress_description">Display a playback progress bar under the controls</string>
    <string name="music_material_expressive_title">Material Expressive player</string>
    <string name="music_material_expressive_desc">Use a more expressive layout for expanded music playback.</string>
    <string name="music_album_colours_title">Use album colours</string>
    <string name="music_album_colours_desc">Derive the music player accent and surface colours from the current album artwork.</string>
    <string name="music_wavy_progress_title">Wavy progress</string>
    <string name="music_wavy_progress_desc">Use the Material Expressive wavy playback progress indicator.</string>''',
"strings")
write(path, text)

# 5) Reuse dominant-colour extraction.
path = "app/src/main/java/com/ekoehler/expressivecutout/overlay/AppIconColorExtractor.kt"
text = read(path)
text = replace_once(text,
'''import androidx.compose.ui.graphics.Color
import androidx.core.graphics.createBitmap''',
'''import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.graphics.createBitmap''', "color extractor imports")
text = replace_once(text,
'''    /**
     * Extracts the primary vibrant/dominant branding color from a [Bitmap].
     * Saturated, vivid colors are scored higher than neutral/dark/white background pixels.
     */
    private fun extractDominantColor(bitmap: Bitmap): Int? {''',
'''    /** Extracts the same vibrant dominant colour from an already-decoded artwork bitmap. */
    internal fun extractDominantColor(bitmap: ImageBitmap): Color? =
        extractDominantColor(bitmap.asAndroidBitmap())?.let { Color(it) }

    /**
     * Extracts the primary vibrant/dominant branding color from a [Bitmap].
     * Saturated, vivid colors are scored higher than neutral/dark/white background pixels.
     */
    private fun extractDominantColor(bitmap: Bitmap): Int? {''', "image bitmap extractor")
write(path, text)

# 6) Pure palette policy.
palette_path = ROOT / "app/src/main/java/com/ekoehler/expressivecutout/overlay/MusicPalettePolicy.kt"
if palette_path.exists():
    raise SystemExit("MusicPalettePolicy.kt already exists")
palette_path.write_text('''package com.ekoehler.expressivecutout.overlay

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.ekoehler.expressivecutout.core.NowPlaying

/** Music-only colours; null surface roles mean the existing island appearance remains untouched. */
internal data class MusicPalette(
    val accent: Color,
    val surface: Color? = null,
    val onSurface: Color? = null,
    val secondaryAccent: Color? = null,
)

/** Resolves album-derived colours, falling back deterministically to the tile's existing accent. */
internal fun resolveMusicPalette(
    useAlbumColours: Boolean,
    fallbackAccent: Color,
    artworkAccent: Color?,
): MusicPalette {
    if (!useAlbumColours) return MusicPalette(accent = fallbackAccent)
    val accent = artworkAccent ?: fallbackAccent
    val surface = lerp(accent, Color(0xFF121216), 0.78f)
    val onSurface = if (surface.luminance() > 0.5f) Color(0xFF0A0A0A) else Color(0xFFF5F5F5)
    return MusicPalette(
        accent = accent,
        surface = surface,
        onSurface = onSurface,
        secondaryAccent = lerp(accent, onSurface, 0.22f),
    )
}

/** Palette identity follows track identity, so progress and play-state updates cannot churn it. */
internal fun mediaPaletteIdentity(event: IslandEvent, nowPlaying: NowPlaying?): MediaTrackContentKey =
    mediaTrackContentKey(event, nowPlaying)
''')

# 7) Renderer.
path = "app/src/main/java/com/ekoehler/expressivecutout/overlay/DynamicIsland.kt"
text = read(path)
text = replace_once(text,
'''import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat''',
'''import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat''', "animate color import")
text = replace_once(text,
'''import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults''',
'''import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults''', "wavy imports")
text = replace_once(text,
'''            if (present || reveal.value > 0f) {
                IslandSurface(
                    modifier = Modifier''',
'''            if (present || reveal.value > 0f) {
                val musicPalette = rememberMusicPalette(shownEvent)
                IslandSurface(
                    modifier = Modifier''', "surface palette local")
text = replace_once(text,
'''                    appColor = shownEvent?.primaryColor(),
                    adaptiveColor = shownEvent?.primaryColor(),
                ) {''',
'''                    appColor = musicPalette?.accent ?: shownEvent?.primaryColor(),
                    adaptiveColor = musicPalette?.accent ?: shownEvent?.primaryColor(),
                    surfaceColorOverride = musicPalette?.surface,
                    contentColorOverride = musicPalette?.onSurface,
                ) {''', "surface palette args")
text = replace_once(text,
'''                                contentEvent?.let { e ->
                                    if (e.call != null) {''',
'''                                contentEvent?.let { content ->
                                    val e = if (content.media != null && musicPalette != null) {
                                        content.copy(accent = musicPalette.accent)
                                    } else {
                                        content
                                    }
                                    if (e.call != null) {''', "content music accent")
text = replace_once(text,
'''    appColor: Color? = null,
    adaptiveColor: Color? = null,
    content: @Composable () -> Unit,
) {''',
'''    appColor: Color? = null,
    adaptiveColor: Color? = null,
    surfaceColorOverride: Color? = null,
    contentColorOverride: Color? = null,
    content: @Composable () -> Unit,
) {''', "surface override params")
text = replace_once(text,
'''    val currentBaseColor = lerp(normalBaseColor, expandedBaseColor, progress)

    val repColor = lerp(''',
'''    val currentBaseColor = surfaceColorOverride ?: lerp(normalBaseColor, expandedBaseColor, progress)

    val repColor = surfaceColorOverride ?: lerp(''', "surface base override")
text = replace_once(text,
'''    val contentColor = appearance.textColor?.resolve(appColor, adaptiveColor) ?: autoContentColor''',
'''    val contentColor = appearance.textColor?.resolve(appColor, adaptiveColor)
        ?: contentColorOverride
        ?: autoContentColor''', "surface content override")
text = replace_once(text,
'''            Box(modifier = Modifier.fillMaxSize().background(currentBaseColor)) {
                Box(modifier = Modifier.fillMaxSize().background(normalBrush))
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = progress }
                            .background(expandedBrush),
                    )
                }
                content()
            }''',
'''            Box(modifier = Modifier.fillMaxSize().background(currentBaseColor)) {
                if (surfaceColorOverride == null) {
                    Box(modifier = Modifier.fillMaxSize().background(normalBrush))
                    if (progress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = progress }
                                .background(expandedBrush),
                        )
                    }
                }
                content()
            }''', "surface brushes override")
text = replace_once(text,
'''@Composable
internal fun EventBadge(
    event: IslandEvent,
    badgeSize: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    showCallPhoto: Boolean = true,
) {
    val nowPlaying by NowPlayingBus.state.collectAsStateWithLifecycle()
    val albumArt = albumArtFor(event, nowPlaying)
    val callPhoto = if (showCallPhoto) {''',
'''/** One artwork frame retained by AnimatedContent while a MUSIC track fades to the next one. */
private data class MusicArtworkFrame(
    val trackKey: MediaTrackContentKey,
    val artwork: ImageBitmap?,
)

@Composable
internal fun EventBadge(
    event: IslandEvent,
    badgeSize: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    showCallPhoto: Boolean = true,
) {
    val nowPlaying by NowPlayingBus.state.collectAsStateWithLifecycle()
    val albumArt = albumArtFor(event, nowPlaying)
    if (event.media != null) {
        val trackKey = mediaTrackContentKey(event, nowPlaying)
        AnimatedContent(
            targetState = MusicArtworkFrame(trackKey, albumArt),
            transitionSpec = {
                fadeIn(animationSpec = tween(160)) togetherWith fadeOut(animationSpec = tween(160))
            },
            contentKey = { it.trackKey },
            label = "musicArtworkTrackContent",
        ) { frame ->
            if (frame.artwork != null) {
                AlbumArt(
                    bitmap = frame.artwork,
                    size = badgeSize,
                    modifier = modifier,
                    rotate = event.media.rotateAlbumArt,
                    playing = nowPlaying?.isPlaying == true,
                    strokeColor = albumArtStrokeFor(event),
                )
            } else {
                IconBadge(event = event, badgeSize = badgeSize, iconSize = iconSize, modifier = modifier)
            }
        }
        return
    }
    val callPhoto = if (showCallPhoto) {''', "event badge artwork transition")
text = replace_once(text,
'''    when {
        albumArt != null -> AlbumArt(
            bitmap = albumArt,
            size = badgeSize,
            modifier = modifier,
            rotate = event.media?.rotateAlbumArt == true,
            playing = nowPlaying?.isPlaying == true,
            strokeColor = albumArtStrokeFor(event),
        )

        callPhoto != null -> ContactPhoto''',
'''    when {
        callPhoto != null -> ContactPhoto''', "remove old music badge branch")

anchor = '''@Composable
private fun ExpandedContent(
'''
helper = '''/**
 * Resolves and animates the album palette only when MUSIC asks for it. Extraction is keyed by the
 * stable track identity plus artwork object identity, never by playback position or clock ticks.
 */
@Composable
private fun rememberMusicPalette(event: IslandEvent?): MusicPalette? {
    val musicEvent = event?.takeIf { it.media?.useAlbumColours == true } ?: return null
    val nowPlaying by NowPlayingBus.state.collectAsStateWithLifecycle()
    val notificationArt by MediaArtBus.state.collectAsStateWithLifecycle()
    val artwork = nowPlaying?.albumArt
        ?: notificationArt?.takeIf { it.packageName == nowPlaying?.packageName }?.art
    val paletteIdentity = mediaPaletteIdentity(musicEvent, nowPlaying)
    val artworkIdentity = remember(artwork) { artwork?.let(System::identityHashCode) ?: 0 }
    var artworkAccent by remember(paletteIdentity) { mutableStateOf<Color?>(null) }

    LaunchedEffect(paletteIdentity, artworkIdentity) {
        artworkAccent = artwork?.let { image ->
            withContext(Dispatchers.Default) { AppIconColorExtractor.extractDominantColor(image) }
        }
    }

    val target = resolveMusicPalette(
        useAlbumColours = true,
        fallbackAccent = musicEvent.accent,
        artworkAccent = artworkAccent,
    )
    val accent by animateColorAsState(target.accent, tween(320), label = "musicAlbumAccent")
    val surface by animateColorAsState(
        target.surface ?: musicEvent.accent,
        tween(320),
        label = "musicAlbumSurface",
    )
    val onSurface by animateColorAsState(
        target.onSurface ?: LocalContentColor.current,
        tween(320),
        label = "musicAlbumOnSurface",
    )
    val secondary by animateColorAsState(
        target.secondaryAccent ?: target.accent,
        tween(320),
        label = "musicAlbumSecondary",
    )
    return MusicPalette(accent, surface, onSurface, secondary)
}

'''
text = replace_once(text, anchor, helper + anchor, "palette composable")

start = text.index('''@Composable
private fun MediaExpandedContent(''')
end = text.index('''/**
 * The music tile's playback bar.''', start)
new_block = '''@Composable
private fun MediaExpandedContent(
    event: IslandEvent,
    appearance: AppearanceSettings,
    buttonHeightDp: Int,
    topMarginDp: Int = IslandDimensions.DEFAULT_TOP_MARGIN_DP,
) {
    val nowPlaying by NowPlayingBus.state.collectAsStateWithLifecycle()
    val media = event.media ?: return
    val expressive = media.materialExpressivePlayer
    val relativeTime = rememberRelativeTime(event.postTimeMs)
    val headerText = formatNotificationHeader(
        appName = event.appName,
        relativeTime = relativeTime,
        showAppName = appearance.showSourceAppName,
        showTimestamp = appearance.showTimestamp,
    )
    val trackKey = mediaTrackContentKey(event, nowPlaying)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 18.dp, end = 18.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = topMarginDp.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(ACTIONS_ROW_SPACING_DP.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (expressive) 16.dp else 14.dp),
            ) {
                EventBadge(
                    event = event,
                    badgeSize = if (expressive) 52.dp else 44.dp,
                    iconSize = if (expressive) 30.dp else 26.dp,
                    showCallPhoto = false,
                )
                AnimatedContent(
                    targetState = trackKey,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        fadeIn(animationSpec = tween(160)) togetherWith fadeOut(animationSpec = tween(160))
                    },
                    contentKey = { it },
                    label = "musicTrackMetadataContent",
                ) { track ->
                    Column {
                        if (headerText != null) {
                            Text(
                                text = headerText,
                                color = event.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = track.title,
                            color = LocalContentColor.current,
                            fontSize = if (expressive) 17.sp else 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        track.artist.takeIf { it.isNotBlank() }?.let { artist ->
                            Text(
                                text = artist,
                                color = LocalContentColor.current.copy(alpha = 0.70f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (media.showProgress) {
                MediaProgressBar(
                    progress = nowPlaying?.progress,
                    wavy = media.wavyProgress,
                    accent = event.accent,
                    useAccent = media.useAlbumColours,
                )
            }

            if (media.showControls) {
                MediaControls(
                    isPlaying = nowPlaying?.isPlaying == true,
                    accent = event.accent,
                    enabled = nowPlaying != null,
                    heightDp = buttonHeightDp,
                    skipStyle = media.skipStyle,
                    playPauseStyle = media.playPauseStyle,
                    expressive = expressive,
                    onPrevious = { nowPlaying?.transport?.previous() },
                    onPlayPause = { nowPlaying?.transport?.playPause() },
                    onNext = { nowPlaying?.transport?.next() },
                )
            }
        }
    }
}

'''
text = text[:start] + new_block + text[end:]

start = text.index('''@Composable
private fun MediaProgressBar(''')
end = text.index('''/**
 * Previous / play‑pause / next.''', start)
new = '''@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaProgressBar(
    progress: MediaProgress?,
    wavy: Boolean,
    accent: Color,
    useAccent: Boolean,
) {
    val color = if (useAccent) accent else MaterialTheme.colorScheme.primary
    val trackColor = if (useAccent) accent.copy(alpha = 0.24f) else MaterialTheme.colorScheme.primaryContainer

    if (progress?.durationMs == null) {
        if (wavy) {
            LinearWavyProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = color,
                trackColor = trackColor,
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = color,
                trackColor = trackColor,
                strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
            )
        }
        return
    }

    // Re-anchored on every new snapshot, so a seek or a track change lands immediately rather than
    // being animated across from the stale position.
    var fraction by remember(progress) {
        mutableFloatStateOf(progress.fractionAt(SystemClock.elapsedRealtime()) ?: 0f)
    }
    LaunchedEffect(progress) {
        while (progress.speed > 0f) {
            delay(PROGRESS_TICK_MS)
            fraction = progress.fractionAt(SystemClock.elapsedRealtime()) ?: 0f
        }
    }

    if (wavy) {
        LinearWavyProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = trackColor,
        )
    } else {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = trackColor,
            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
        )
    }
}

'''
text = text[:start] + new + text[end:]
text = replace_once(text,
'''    playPauseStyle: MusicButtonStyle,
    onPrevious: () -> Unit,''',
'''    playPauseStyle: MusicButtonStyle,
    expressive: Boolean = false,
    onPrevious: () -> Unit,''', "media controls expressive arg")
text = replace_once(text,
'''    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaButton(
            icon = Icons.Rounded.SkipPrevious,
            contentDescription = "Previous track",
            enabled = enabled,
            heightDp = heightDp,''',
'''    val skipHeightDp = if (expressive) (heightDp * 0.78f).roundToInt().coerceAtLeast(1) else heightDp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            if (expressive) 16.dp else 12.dp,
            Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaButton(
            icon = Icons.Rounded.SkipPrevious,
            contentDescription = "Previous track",
            enabled = enabled,
            heightDp = skipHeightDp,''', "first skip sizing")
text = replace_once(text,
'''        MediaButton(
            icon = Icons.Rounded.SkipNext,
            contentDescription = "Next track",
            enabled = enabled,
            heightDp = heightDp,
            iconSize = 26.dp,''',
'''        MediaButton(
            icon = Icons.Rounded.SkipNext,
            contentDescription = "Next track",
            enabled = enabled,
            heightDp = skipHeightDp,
            iconSize = 26.dp,''', "next skip sizing")
write(path, text)

# 8) Pin only Material3 to the requested first compile candidate.
path = "gradle/libs.versions.toml"
text = read(path)
text = replace_once(text,
'''composeBom = "2024.12.01"
datastore = "1.1.1"''',
'''composeBom = "2024.12.01"
material3 = "1.4.0"
datastore = "1.1.1"''', "material3 version")
text = replace_once(text,
'''androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }''',
'''androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3", version.ref = "material3" }''',
"material3 alias")
write(path, text)

print("MUSIC_POLISH_PATCH=APPLIED")
