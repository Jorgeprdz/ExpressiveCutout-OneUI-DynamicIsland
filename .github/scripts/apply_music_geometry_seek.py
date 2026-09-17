#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def read(path: str) -> str:
    return (ROOT / path).read_text()

def write(path: str, text: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text)

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)

def replace_section(text: str, start: str, end: str, replacement: str, label: str) -> str:
    start_index = text.find(start)
    if start_index < 0:
        raise SystemExit(f"{label}: start marker not found")
    end_index = text.find(end, start_index)
    if end_index < 0:
        raise SystemExit(f"{label}: end marker not found")
    if text.find(start, start_index + 1) >= 0:
        raise SystemExit(f"{label}: start marker is not unique")
    return text[:start_index] + replacement + text[end_index:]

# 1) Assistant default OFF for absent preference, preserving explicit stored/imported values.
path = "app/src/main/java/com/ekoehler/expressivecutout/data/DynamicTilePreferences.kt"
text = read(path)
text = replace_once(
    text,
    '''/** Backing store for which dynamic tiles the user has enabled. */
private val Context.dynamicTileDataStore: DataStore<Preferences> by preferencesDataStore(name = "dynamic_tile_prefs")

/**
 * Persists whether each dynamic tile is allowed to appear on the cutout. Absent means enabled,
 * so tiles show by default and only explicit opt-outs are stored — mirroring [EventPreferences]
 * but kept separate because tiles are a distinct concept from system events.
 */
class DynamicTilePreferences(private val context: Context) : JsonSerializable {
''',
    '''/** Backing store for which dynamic tiles the user has enabled. */
private val Context.dynamicTileDataStore: DataStore<Preferences> by preferencesDataStore(name = "dynamic_tile_prefs")

/**
 * Resolves a persisted dynamic-tile switch. Assistant is intentionally opt-in on a fresh install;
 * every other tile keeps the historical enabled-by-default behaviour. An explicit stored/imported
 * value always wins so existing users are never silently changed.
 */
internal fun resolveDynamicTileEnabled(tile: DynamicTile, persisted: Boolean?): Boolean =
    persisted ?: (tile != DynamicTile.ASSISTANT)

/**
 * Persists whether each dynamic tile is allowed to appear on the cutout. Assistant is the sole
 * opt-in tile; every other absent preference remains enabled, matching the historical defaults.
 */
class DynamicTilePreferences(private val context: Context) : JsonSerializable {
''',
    "assistant default helper",
)
text = replace_once(
    text,
    '''    val enabled: Flow<Map<DynamicTile, Boolean>> = context.dynamicTileDataStore.data.map { prefs ->
        DynamicTile.entries.associateWith { tile -> prefs[tile.key] ?: true }
    }
''',
    '''    val enabled: Flow<Map<DynamicTile, Boolean>> = context.dynamicTileDataStore.data.map { prefs ->
        DynamicTile.entries.associateWith { tile ->
            resolveDynamicTileEnabled(tile, prefs[tile.key])
        }
    }
''',
    "assistant flow default",
)
text = replace_once(
    text,
    '''    /**
     * Applies { enabled: { TILE_NAME: bool, ... } } exported by [toJson]. Every known tile is set
     * from the document, defaulting an absent entry to enabled (the store's own default), in one edit.
     */
    override suspend fun fromJson(json: String) {
        val enabledObj = JSONObject(json).optJSONObject("enabled") ?: return
        context.dynamicTileDataStore.edit { prefs ->
            DynamicTile.entries.forEach { tile ->
                prefs[tile.key] = enabledObj.optBoolean(tile.name, true)
            }
        }
    }
''',
    '''    /**
     * Applies { enabled: { TILE_NAME: bool, ... } } exported by [toJson]. Explicit imported values
     * win; a missing entry falls back to that tile's real default (Assistant off, the others on).
     */
    override suspend fun fromJson(json: String) {
        val enabledObj = JSONObject(json).optJSONObject("enabled") ?: return
        context.dynamicTileDataStore.edit { prefs ->
            DynamicTile.entries.forEach { tile ->
                val imported = if (enabledObj.has(tile.name)) enabledObj.getBoolean(tile.name) else null
                prefs[tile.key] = resolveDynamicTileEnabled(tile, imported)
            }
        }
    }
''',
    "assistant json default",
)
write(path, text)

# 2) Existing media transport: expose seek capability without another backend.
path = "app/src/main/java/com/ekoehler/expressivecutout/core/NowPlaying.kt"
text = read(path)
text = replace_once(
    text,
    '''/** The transport actions the music tile exposes. Backed by the active media session's controls. */
@Stable
interface MediaTransport {
    fun previous()
    fun playPause()
    fun next()
}
''',
    '''/** The transport actions the music tile exposes. Backed by the active media session's controls. */
@Stable
interface MediaTransport {
    fun previous()
    fun playPause()
    fun next()

    /** Whether the active session explicitly advertises seek support. */
    val canSeek: Boolean
        get() = false

    /** Move playback to [positionMs]. Unsupported transports keep the default no-op. */
    fun seekTo(positionMs: Long) = Unit
}
''',
    "media transport seek surface",
)
write(path, text)

path = "app/src/main/java/com/ekoehler/expressivecutout/events/MediaPlaybackMonitor.kt"
text = read(path)
text = replace_once(
    text,
    '''        override fun next() {
            runCatching { controller.transportControls.skipToNext() }
        }
    }
''',
    '''        override fun next() {
            runCatching { controller.transportControls.skipToNext() }
        }

        override val canSeek: Boolean
            get() = ((controller.playbackState?.actions ?: 0L) and PlaybackState.ACTION_SEEK_TO) != 0L

        override fun seekTo(positionMs: Long) {
            if (!canSeek) return
            runCatching { controller.transportControls.seekTo(positionMs.coerceAtLeast(0L)) }
        }
    }
''',
    "controller seek bridge",
)
write(path, text)

# 3) Pure seek / progress helpers used by UI and unit tests.
write(
    "app/src/main/java/com/ekoehler/expressivecutout/overlay/MusicInteractionPolicy.kt",
    '''package com.ekoehler.expressivecutout.overlay

import androidx.compose.ui.graphics.Color

/** Maps a horizontal pointer coordinate to a stable, bounded 0..1 media fraction. */
internal fun mediaSeekFraction(xPx: Float, widthPx: Float): Float? {
    if (!xPx.isFinite() || !widthPx.isFinite() || widthPx <= 0f) return null
    return (xPx / widthPx).coerceIn(0f, 1f)
}

/** Converts a pointer coordinate into a real media-session seek target. */
internal fun mediaSeekTargetMs(xPx: Float, widthPx: Float, durationMs: Long?): Long? {
    val duration = durationMs?.takeIf { it > 0L } ?: return null
    val fraction = mediaSeekFraction(xPx, widthPx) ?: return null
    return (duration * fraction).toLong().coerceIn(0L, duration)
}

/** A seek gesture is meaningful only for a finite-duration session that advertises ACTION_SEEK_TO. */
internal fun mediaSeekAvailable(durationMs: Long?, transportCanSeek: Boolean): Boolean =
    transportCanSeek && durationMs != null && durationMs > 0L

/** Keeps legacy progress on Material primary while album-colour mode uses its resolved music accent. */
internal fun resolveMediaProgressAccent(
    useAlbumColours: Boolean,
    resolvedAccent: Color,
    themePrimary: Color,
): Color = if (useAlbumColours) resolvedAccent else themePrimary
''',
)

# 4) Album palette: retain source colour character, but guarantee readable UI roles.
path = "app/src/main/java/com/ekoehler/expressivecutout/overlay/MusicPalettePolicy.kt"
write(
    path,
    '''package com.ekoehler.expressivecutout.overlay

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

private fun musicContrastRatio(first: Color, second: Color): Float {
    val firstLuminance = first.luminance()
    val secondLuminance = second.luminance()
    val lighter = maxOf(firstLuminance, secondLuminance)
    val darker = minOf(firstLuminance, secondLuminance)
    return (lighter + 0.05f) / (darker + 0.05f)
}

/**
 * Moves a colour toward the already-safe [onSurface] only as far as necessary to reach a readable
 * 3:1 UI contrast. This preserves album character instead of replacing every dark cover with white.
 */
private fun ensureMusicUiContrast(
    candidate: Color,
    surface: Color,
    onSurface: Color,
    minimumRatio: Float = 3f,
): Color {
    if (musicContrastRatio(candidate, surface) >= minimumRatio) return candidate
    for (step in 1..10) {
        val adjusted = lerp(candidate, onSurface, step / 10f)
        if (musicContrastRatio(adjusted, surface) >= minimumRatio) return adjusted
    }
    return onSurface
}

/** Resolves album-derived colours, falling back deterministically to the tile's existing accent. */
internal fun resolveMusicPalette(
    useAlbumColours: Boolean,
    fallbackAccent: Color,
    artworkAccent: Color?,
): MusicPalette {
    if (!useAlbumColours) return MusicPalette(accent = fallbackAccent)

    val sourceAccent = artworkAccent ?: fallbackAccent
    val surface = lerp(sourceAccent, Color(0xFF121216), 0.78f)
    val onSurface = if (surface.luminance() > 0.5f) Color(0xFF0A0A0A) else Color(0xFFF5F5F5)

    // When artwork exists, make the colour used by progress/buttons readable on its derived surface.
    // Artwork-unavailable fallback remains the exact historical event accent.
    val accent = if (artworkAccent != null) {
        ensureMusicUiContrast(sourceAccent, surface, onSurface)
    } else {
        sourceAccent
    }
    val secondarySeed = lerp(sourceAccent, onSurface, 0.22f)
    val secondaryAccent = ensureMusicUiContrast(secondarySeed, surface, onSurface)

    return MusicPalette(
        accent = accent,
        surface = surface,
        onSurface = onSurface,
        secondaryAccent = secondaryAccent,
    )
}

/** Palette identity follows track identity, so progress and play-state updates cannot churn it. */
internal fun mediaPaletteIdentity(event: IslandEvent, nowPlaying: NowPlaying?): MediaTrackContentKey =
    mediaTrackContentKey(event, nowPlaying)
''',
)

# 5) DynamicIsland: geometry, compact alignment, wavy height, interactive seek.
path = "app/src/main/java/com/ekoehler/expressivecutout/overlay/DynamicIsland.kt"
text = read(path)

old_metrics = '''/**
 * Height of the music progress bar, matching Material 3's LinearProgressIndicator default track.
 */
private const val MEDIA_PROGRESS_HEIGHT_DP = 4

/**
 * Extra height added to the expanded music tile when it shows the progress bar: the bar itself plus
 * the spacing above it. Reserved separately from [expandedActionsExtraDp] because the bar is a third
 * row in the same column — without its own allowance the track text is squeezed out of its slot and
 * the bar, drawn after it, paints over the artist line.
 */
internal fun expandedMediaProgressExtraDp(): Int = MEDIA_PROGRESS_HEIGHT_DP + ACTIONS_ROW_SPACING_DP

/** The music tile's artwork/track row: the album badge's own size, which the text column matches. */
private const val MEDIA_CONTENT_ROW_HEIGHT_DP = 44

/** Bottom inset under the music tile's column, matching its layout's own bottom padding. */
private const val MEDIA_EXPANDED_BOTTOM_PADDING_DP = 16

/**
 * The expanded music tile's base height, taken from its own content rather than the user's expanded
 * height: the camera band, the artwork row, and the bottom inset. Its layout is a top-anchored column
 * with no filler, so a taller card would strand the controls above dead space and a shorter one would
 * squeeze them out of it entirely — hence the height knob is deliberately not applied here. The
 * progress bar and transport controls are added on top of this by the usual height bonuses.
 */
internal fun mediaExpandedBaseHeightDp(topMarginDp: Int = IslandDimensions.DEFAULT_TOP_MARGIN_DP): Int =
    topMarginDp + MEDIA_CONTENT_ROW_HEIGHT_DP + MEDIA_EXPANDED_BOTTOM_PADDING_DP
'''
new_metrics = '''/** Legacy linear progress stays the exact 4dp visual track it had before seek support. */
private const val MEDIA_PROGRESS_HEIGHT_DP = 4

/** Wavy needs real vertical amplitude instead of being coerced into the legacy 4dp hairline. */
private const val MEDIA_WAVY_PROGRESS_HEIGHT_DP = 16

/** Invisible seek target, overlaid on the bar so it does not alter either layout's measured height. */
private const val MEDIA_PROGRESS_TOUCH_TARGET_DP = 32

/** Legacy metadata/art row remains exactly 44dp. */
private const val MEDIA_CONTENT_ROW_HEIGHT_DP = 44

/** Expressive uses a 52dp cover plus enough room for header/title/artist without clipping. */
private const val MEDIA_EXPRESSIVE_CONTENT_ROW_HEIGHT_DP = 60

internal fun mediaContentRowHeightDp(expressive: Boolean): Int =
    if (expressive) MEDIA_EXPRESSIVE_CONTENT_ROW_HEIGHT_DP else MEDIA_CONTENT_ROW_HEIGHT_DP

internal fun mediaProgressVisualHeightDp(wavy: Boolean): Int =
    if (wavy) MEDIA_WAVY_PROGRESS_HEIGHT_DP else MEDIA_PROGRESS_HEIGHT_DP

internal fun mediaProgressTouchTargetDp(): Int = MEDIA_PROGRESS_TOUCH_TARGET_DP

/**
 * Extra height is visual-only: the 32dp touch target floats over this area and therefore does not
 * make the card taller. Legacy remains 4 + 12 = 16dp exactly.
 */
internal fun expandedMediaProgressExtraDp(wavy: Boolean = false): Int =
    mediaProgressVisualHeightDp(wavy) + ACTIONS_ROW_SPACING_DP

/** Bottom inset under the music tile's column, matching its layout's own bottom padding. */
private const val MEDIA_EXPANDED_BOTTOM_PADDING_DP = 16

/** Music base height follows its selected metadata row while preserving the legacy 108dp default. */
internal fun mediaExpandedBaseHeightDp(
    topMarginDp: Int = IslandDimensions.DEFAULT_TOP_MARGIN_DP,
    expressive: Boolean = false,
): Int = topMarginDp + mediaContentRowHeightDp(expressive) + MEDIA_EXPANDED_BOTTOM_PADDING_DP
'''
text = replace_once(text, old_metrics, new_metrics, "music geometry metrics")

text = replace_once(
    text,
    '''        isExpanded && shownEvent?.media != null ->
            expanded.copy(heightDp = mediaExpandedBaseHeightDp(expanded.topMarginDp))
''',
    '''        isExpanded && shownEvent?.media != null ->
            expanded.copy(
                heightDp = mediaExpandedBaseHeightDp(
                    topMarginDp = expanded.topMarginDp,
                    expressive = shownEvent.media.materialExpressivePlayer,
                ),
            )
''',
    "expressive base height",
)

text = replace_once(
    text,
    '''            controlsExtra + if (hasMediaProgress) expandedMediaProgressExtraDp() else 0
''',
    '''            controlsExtra + if (hasMediaProgress) {
                expandedMediaProgressExtraDp(wavy = shownEvent.media.wavyProgress)
            } else {
                0
            }
''',
    "wavy height bonus",
)

event_badge_start = '''@Composable
internal fun EventBadge(
'''
event_badge_end = '''/**
 * The collapsed pill's contents:'''
event_badge_replacement = '''@Composable
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
        // AnimatedContent is now the direct Box child, so it owns the caller's placement modifier.
        AnimatedContent(
            targetState = MusicArtworkFrame(trackKey, albumArt),
            modifier = modifier,
            contentAlignment = Alignment.Center,
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
                    rotate = event.media.rotateAlbumArt,
                    playing = nowPlaying?.isPlaying == true,
                    strokeColor = albumArtStrokeFor(event),
                )
            } else {
                IconBadge(event = event, badgeSize = badgeSize, iconSize = iconSize)
            }
        }
        return
    }
    val callPhoto = if (showCallPhoto) {
        val onCall = OnCallBus.state.collectAsStateWithLifecycle().value
        event.call?.takeIf { it.showPhoto }?.let { onCall?.photo }
    } else {
        null
    }

    when {
        callPhoto != null -> ContactPhoto(bitmap = callPhoto, size = badgeSize, modifier = modifier)
        else -> IconBadge(event = event, badgeSize = badgeSize, iconSize = iconSize, modifier = modifier)
    }
}

'''
text = replace_section(text, event_badge_start, event_badge_end, event_badge_replacement, "EventBadge")

media_section_start = '''/**
 * The music tile's expanded layout: album art + track/artist, and (when enabled) a row of
'''
media_section_end = '''/**
 * Previous / play‑pause / next.'''
media_section_replacement = '''/**
 * The music tile's expanded layout: album art + track/artist, optional seekable progress, and
 * previous / play‑pause / next controls. Geometry is stable per mode and never keys on progress.
 */
@Composable
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
    val progress = nowPlaying?.progress
    val transport = nowPlaying?.transport
    val durationMs = progress?.durationMs
    val seekEnabled = mediaSeekAvailable(durationMs, transport?.canSeek == true)
    var scrubFraction by remember(trackKey) { mutableStateOf<Float?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 18.dp, end = 18.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = topMarginDp.dp, bottom = MEDIA_EXPANDED_BOTTOM_PADDING_DP.dp),
            verticalArrangement = Arrangement.spacedBy(ACTIONS_ROW_SPACING_DP.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(mediaContentRowHeightDp(expressive).dp),
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
                                color = LocalContentColor.current.copy(alpha = 0.78f),
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
                    progress = progress,
                    wavy = media.wavyProgress,
                    resolvedAccent = event.accent,
                    useAlbumColours = media.useAlbumColours,
                    scrubFraction = scrubFraction,
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
                    onPrevious = { transport?.previous() },
                    onPlayPause = { transport?.playPause() },
                    onNext = { transport?.next() },
                )
            }
        }

        // Float a larger hit target over the visual bar; measured geometry remains unchanged.
        if (media.showProgress && seekEnabled && durationMs != null && transport != null) {
            val visualHeightDp = mediaProgressVisualHeightDp(media.wavyProgress)
            val touchHeightDp = mediaProgressTouchTargetDp()
            val touchTopDp = topMarginDp +
                mediaContentRowHeightDp(expressive) +
                ACTIONS_ROW_SPACING_DP +
                (visualHeightDp - touchHeightDp) / 2f

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .offset(y = touchTopDp.dp)
                    .height(touchHeightDp.dp)
                    .pointerInput(trackKey, durationMs, transport) {
                        detectTapGestures { offset ->
                            val fraction = mediaSeekFraction(offset.x, size.width.toFloat())
                            val target = mediaSeekTargetMs(offset.x, size.width.toFloat(), durationMs)
                            if (fraction != null && target != null) {
                                scrubFraction = fraction
                                transport.seekTo(target)
                                scrubFraction = null
                            }
                        }
                    }
                    .pointerInput(trackKey, durationMs, transport) {
                        var dragFraction: Float? = null
                        detectHorizontalDragGestures(
                            onDragStart = { start ->
                                dragFraction = mediaSeekFraction(start.x, size.width.toFloat())
                                scrubFraction = dragFraction
                            },
                            onDragEnd = {
                                dragFraction?.let { fraction ->
                                    mediaSeekTargetMs(
                                        xPx = fraction,
                                        widthPx = 1f,
                                        durationMs = durationMs,
                                    )?.let(transport::seekTo)
                                }
                                scrubFraction = null
                                dragFraction = null
                            },
                            onDragCancel = {
                                scrubFraction = null
                                dragFraction = null
                            },
                        ) { change, _ ->
                            val next = mediaSeekFraction(change.position.x, size.width.toFloat())
                            if (next != null) {
                                dragFraction = next
                                scrubFraction = next
                                change.consume()
                            }
                        }
                    },
            )
        }
    }
}

/**
 * The music tile's playback bar. [MediaProgress] remains the only playback clock. During a drag,
 * [scrubFraction] temporarily controls only the visual fraction and never track/palette identity.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaProgressBar(
    progress: MediaProgress?,
    wavy: Boolean,
    resolvedAccent: Color,
    useAlbumColours: Boolean,
    scrubFraction: Float?,
) {
    val color = resolveMediaProgressAccent(
        useAlbumColours = useAlbumColours,
        resolvedAccent = resolvedAccent,
        themePrimary = MaterialTheme.colorScheme.primary,
    )
    val trackColor = if (useAlbumColours) {
        color.copy(alpha = 0.24f)
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val barModifier = Modifier
        .fillMaxWidth()
        .requiredHeight(mediaProgressVisualHeightDp(wavy).dp)

    if (progress?.durationMs == null) {
        if (wavy) {
            LinearWavyProgressIndicator(
                modifier = barModifier,
                color = color,
                trackColor = trackColor,
            )
        } else {
            LinearProgressIndicator(
                modifier = barModifier,
                color = color,
                trackColor = trackColor,
                strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
            )
        }
        return
    }

    var fraction by remember(progress) {
        mutableFloatStateOf(progress.fractionAt(SystemClock.elapsedRealtime()) ?: 0f)
    }
    LaunchedEffect(progress, scrubFraction) {
        while (progress.speed > 0f) {
            delay(PROGRESS_TICK_MS)
            if (scrubFraction == null) {
                fraction = progress.fractionAt(SystemClock.elapsedRealtime()) ?: 0f
            }
        }
    }
    val displayedFraction = scrubFraction ?: fraction

    if (wavy) {
        LinearWavyProgressIndicator(
            progress = { displayedFraction },
            modifier = barModifier,
            color = color,
            trackColor = trackColor,
        )
    } else {
        LinearProgressIndicator(
            progress = { displayedFraction },
            modifier = barModifier,
            color = color,
            trackColor = trackColor,
            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
        )
    }
}

'''
text = replace_section(text, media_section_start, media_section_end, media_section_replacement, "media section")
write(path, text)

print("MUSIC_GEOMETRY_SEEK_PATCH=APPLIED")
