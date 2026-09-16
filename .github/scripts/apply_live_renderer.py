from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"missing patch anchor: {label}")
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# IslandEvent: stable visual identity shared by Compose and LiveActivity.
# ---------------------------------------------------------------------------
path = Path("app/src/main/java/com/ekoehler/expressivecutout/overlay/IslandEvent.kt")
text = path.read_text()
text = replace_once(
    text,
    "    /** Optional URI data to attach to [actionIntentAction]. */\n"
    "    val actionIntentUri: String? = null,\n"
    ")\n",
    "    /** Optional URI data to attach to [actionIntentAction]. */\n"
    "    val actionIntentUri: String? = null,\n"
    "    /** Stable LiveActivity identity; null for ordinary transient legacy events. */\n"
    "    val stableId: String? = null,\n"
    ") {\n"
    "    /** Identity Compose uses for arrival/gesture state without keying on mutable content. */\n"
    "    val visualIdentity: String\n"
    "        get() = stableId ?: notificationKey?.let { \"notification:$it\" } ?: \"event:$id\"\n"
    "}\n",
    "IslandEvent stable identity",
)
path.write_text(text)


# ---------------------------------------------------------------------------
# Motion policy: expose physically coherent stiffness scaling for every spring.
# ---------------------------------------------------------------------------
path = Path("app/src/main/java/com/ekoehler/expressivecutout/overlay/IslandMotionPolicy.kt")
text = path.read_text()
text = replace_once(
    text,
    "        val scaleSquared = durationScale * durationScale\n"
    "        val spatialStiffness = (baseSpatial / scaleSquared).coerceIn(MIN_STIFFNESS, MAX_STIFFNESS)\n"
    "        val effectStiffness = (baseEffect / scaleSquared).coerceIn(MIN_STIFFNESS, MAX_STIFFNESS)\n",
    "        val spatialStiffness = scaleStiffness(baseSpatial, animationDurationMs)\n"
    "        val effectStiffness = scaleStiffness(baseEffect, animationDurationMs)\n",
    "motion policy stiffness",
)
text = replace_once(
    text,
    "    private const val MIN_STIFFNESS = 25f\n"
    "    private const val MAX_STIFFNESS = 5_000f\n",
    "    /** Scales a spring so its characteristic duration follows the user's duration slider. */\n"
    "    fun scaleStiffness(baseStiffness: Float, animationDurationMs: Int): Float {\n"
    "        if (animationDurationMs <= 0) return MAX_STIFFNESS\n"
    "        val scale = animationDurationMs / BASE_TRANSITION_MS.toFloat()\n"
    "        return (baseStiffness / (scale * scale)).coerceIn(MIN_STIFFNESS, MAX_STIFFNESS)\n"
    "    }\n\n"
    "    private const val MIN_STIFFNESS = 25f\n"
    "    private const val MAX_STIFFNESS = 5_000f\n",
    "motion policy scale helper",
)
path.write_text(text)


# ---------------------------------------------------------------------------
# IslandMotion: directional profiles, duration-scaled springs, zero-duration snap.
# ---------------------------------------------------------------------------
Path("app/src/main/java/com/ekoehler/expressivecutout/overlay/IslandMotion.kt").write_text(r'''package com.ekoehler.expressivecutout.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import com.ekoehler.expressivecutout.data.AnimationBounce
import com.ekoehler.expressivecutout.data.AnimationSpeed
import com.ekoehler.expressivecutout.data.AnimationStyle
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Builds directional animation specs for the island from one shared physical motion policy.
 * Spatial motion may overshoot on entry/expansion, exits are restrained, and effects remain
 * critically damped. A zero duration setting snaps every transition immediately.
 */
internal class IslandMotion(
    style: AnimationStyle,
    private val speed: AnimationSpeed,
    private val bounce: AnimationBounce,
    private val animationDurationMs: Int,
) {
    private val animScale = animationDurationMs / BASE_TRANSITION_MS.toFloat()
    private val expressive = style == AnimationStyle.EXPRESSIVE
    private val snapMotion = animationDurationMs <= 0

    private fun scaled(baseMs: Int) = (baseMs * animScale).roundToInt().coerceAtLeast(0)

    private fun profile(direction: IslandTransitionDirection): IslandMotionProfile =
        IslandMotionPolicy.profile(direction, speed, bounce, animationDurationMs)

    /** Spatial motion on a scalar fraction, tuned for [direction]. */
    fun float(
        baseMs: Int = BASE_TRANSITION_MS,
        direction: IslandTransitionDirection = IslandTransitionDirection.ENTER,
    ): AnimationSpec<Float> {
        if (snapMotion) return snap()
        if (!expressive) return tween(durationMillis = scaled(baseMs), easing = EaseInOutEasing)
        val p = profile(direction)
        val baseFactor = baseMs / BASE_TRANSITION_MS.toFloat()
        val stiffness = (p.spatialStiffness / (baseFactor * baseFactor)).coerceAtLeast(25f)
        return spring(
            dampingRatio = p.spatialDampingRatio,
            stiffness = stiffness,
            visibilityThreshold = 0.001f,
        )
    }

    /** Spatial motion on sizes, offsets and corner radii. */
    fun dp(direction: IslandTransitionDirection = IslandTransitionDirection.EXPAND): AnimationSpec<Dp> {
        if (snapMotion) return snap()
        if (!expressive) return tween(durationMillis = scaled(BASE_TRANSITION_MS), easing = EaseInOutEasing)
        val p = profile(direction)
        return spring(
            dampingRatio = p.spatialDampingRatio,
            stiffness = p.spatialStiffness,
            visibilityThreshold = Dp.VisibilityThreshold,
        )
    }

    /** Critically damped geometry for continuously changing heights. */
    fun dpSmooth(direction: IslandTransitionDirection = IslandTransitionDirection.EXPAND): AnimationSpec<Dp> {
        if (snapMotion) return snap()
        if (!expressive) return tween(durationMillis = scaled(BASE_TRANSITION_MS), easing = EaseInOutEasing)
        val p = profile(direction)
        return spring(
            dampingRatio = 1f,
            stiffness = p.spatialStiffness,
            visibilityThreshold = Dp.VisibilityThreshold,
        )
    }

    /** Immediate press/release feedback; still obeys duration=0 and spring duration scaling. */
    fun boop(): AnimationSpec<Float> {
        if (snapMotion) return snap()
        if (!expressive) return tween(durationMillis = scaled(140), easing = EaseInOutEasing)
        return spring(
            dampingRatio = 1f,
            stiffness = IslandMotionPolicy.scaleStiffness(boopBaseStiffness(speed), animationDurationMs),
            visibilityThreshold = 0.0005f,
        )
    }

    /** One continuous expanded-tap swell and settle. */
    suspend fun pop(scale: Animatable<Float, AnimationVector1D>, peak: Float) {
        if (snapMotion) {
            scale.snapTo(REST_SCALE)
            return
        }
        if (expressive) {
            val stiffness = IslandMotionPolicy.scaleStiffness(spatialBaseStiffness(speed), animationDurationMs)
            val velocity = (peak - REST_SCALE) * sqrt(stiffness) / POP_PEAK_RATIO
            scale.animateTo(
                targetValue = REST_SCALE,
                animationSpec = spring(
                    dampingRatio = POP_DAMPING,
                    stiffness = stiffness,
                    visibilityThreshold = 0.0005f,
                ),
                initialVelocity = velocity,
            )
        } else {
            scale.animateTo(peak, tween(durationMillis = scaled(80), easing = EaseInOutEasing))
            scale.animateTo(REST_SCALE, tween(durationMillis = scaled(160), easing = EaseInOutEasing))
        }
    }

    /** Arrival pop for a genuinely new visual identity. */
    suspend fun popIn(scale: Animatable<Float, AnimationVector1D>) {
        if (snapMotion) {
            scale.snapTo(REST_SCALE)
            return
        }
        scale.snapTo(POP_IN_START_SCALE)
        scale.animateTo(
            targetValue = REST_SCALE,
            animationSpec = float(
                baseMs = POP_IN_MS,
                direction = IslandTransitionDirection.ENTER,
            ),
        )
    }

    /** Alpha/colour motion is always critically damped. */
    fun fade(direction: IslandTransitionDirection = IslandTransitionDirection.ENTER): AnimationSpec<Float> {
        if (snapMotion) return snap()
        if (!expressive) return tween(durationMillis = scaled(BASE_TRANSITION_MS), easing = EaseInOutEasing)
        val p = profile(direction)
        return spring(
            dampingRatio = p.effectDampingRatio,
            stiffness = p.effectStiffness,
            visibilityThreshold = 0.001f,
        )
    }

    companion object {
        const val BASE_TRANSITION_MS = IslandMotionPolicy.BASE_TRANSITION_MS
        private val EaseInOutEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
        private const val REST_SCALE = 1f
        private const val POP_DAMPING = 0.55f
        private const val POP_PEAK_RATIO = 0.5216f
        private const val POP_IN_START_SCALE = 0.55f
        private const val POP_IN_MS = 200

        private fun spatialBaseStiffness(speed: AnimationSpeed): Float = when (speed) {
            AnimationSpeed.SLOW -> 170f
            AnimationSpeed.DEFAULT -> 380f
            AnimationSpeed.FAST -> 800f
        }

        private fun boopBaseStiffness(speed: AnimationSpeed): Float = when (speed) {
            AnimationSpeed.SLOW -> 400f
            AnimationSpeed.DEFAULT -> 900f
            AnimationSpeed.FAST -> 1800f
        }
    }
}
''')


# ---------------------------------------------------------------------------
# DynamicIsland: stable visual keys, directional motion, CALL+satellite support.
# ---------------------------------------------------------------------------
path = Path("app/src/main/java/com/ekoehler/expressivecutout/overlay/DynamicIsland.kt")
text = path.read_text()
text = replace_once(
    text,
    "    satellite: IslandEvent? = null,\n"
    "    satellitePosition: SatellitePosition = SatellitePosition.RIGHT,\n",
    "    satellite: IslandEvent? = null,\n"
    "    satellitePosition: SatellitePosition = SatellitePosition.RIGHT,\n"
    "    liveTransition: LiveActivityVisualTransition = LiveActivityVisualTransition.NONE,\n",
    "DynamicIsland liveTransition parameter",
)
text = replace_once(
    text,
    "    val shownEvent = lastEvent\n"
    "    val emptyPill = event == null && showsWhenEmpty\n\n"
    "    val initialExpandedState = if (forcedExpanded == false) false else (shownEvent?.initiallyExpanded ?: false)\n"
    "    var tapExpanded by remember(shownEvent?.id, forcedExpanded) { mutableStateOf(initialExpandedState) }\n"
    "    var centerInteraction by remember { mutableStateOf(0) }\n"
    "    var replyingTo by remember(shownEvent?.id) { mutableStateOf<IslandAction?>(null) }\n",
    "    val shownEvent = lastEvent\n"
    "    val shownIdentity = shownEvent?.visualIdentity\n"
    "    val emptyPill = event == null && showsWhenEmpty\n\n"
    "    val initialExpandedState = if (forcedExpanded == false) false else (shownEvent?.initiallyExpanded ?: false)\n"
    "    var tapExpanded by remember(shownIdentity, forcedExpanded) { mutableStateOf(initialExpandedState) }\n"
    "    var centerInteraction by remember { mutableStateOf(0) }\n"
    "    var replyingTo by remember(shownIdentity) { mutableStateOf<IslandAction?>(null) }\n",
    "DynamicIsland stable expansion identity",
)
text = text.replace("var sentReply by remember(shownEvent?.id)", "var sentReply by remember(shownIdentity)")
text = text.replace("val dismissOffsetX = remember(shownEvent?.id)", "val dismissOffsetX = remember(shownIdentity)")
text = text.replace("var expandedNotificationHeightDp by remember(shownEvent?.id)", "var expandedNotificationHeightDp by remember(shownIdentity)")
text = text.replace("        shownEvent?.id,\n", "        shownIdentity,\n", 1)
text = replace_once(
    text,
    "            animationSpec = motion.float(baseMs = if (present) 320 else 200),\n",
    "            animationSpec = motion.float(\n"
    "                baseMs = if (present) 320 else 200,\n"
    "                direction = if (present) IslandTransitionDirection.ENTER else IslandTransitionDirection.EXIT,\n"
    "            ),\n",
    "primary reveal direction",
)
text = replace_once(
    text,
    "    // The normal cutout's icon pops in whenever a new event takes the pill over. What counts as\n"
    "    // \"new\" is deliberately not the event id: a live tile re-resolves on every refresh (a media\n"
    "    // progress tick, a timer second) and gets a fresh id each time, which would re-pop constantly.\n"
    "    // A notification's key survives its own updates, and a tile's label survives its lifetime.\n"
    "    val iconPop = remember { Animatable(1f) }\n"
    "    val iconPopKey = event?.let { it.notificationKey ?: it.label }\n"
    "    LaunchedEffect(iconPopKey) {\n"
    "        if (iconPopKey != null) motion.popIn(iconPop)\n"
    "    }\n",
    "    // Arrival animation keys strictly on stable visual identity, never mutable labels/progress.\n"
    "    val iconPop = remember { Animatable(1f) }\n"
    "    val iconPopKey = event?.visualIdentity\n"
    "    LaunchedEffect(iconPopKey, liveTransition) {\n"
    "        val liveArrival = event?.stableId == null || liveTransition == LiveActivityVisualTransition.REVEAL ||\n"
    "            liveTransition == LiveActivityVisualTransition.REPLACE_PRIMARY ||\n"
    "            liveTransition == LiveActivityVisualTransition.DEMOTE_PRIMARY_TO_SATELLITE\n"
    "        if (iconPopKey != null && liveArrival) motion.popIn(iconPop)\n"
    "    }\n",
    "stable icon pop identity",
)
text = text.replace(
    "reveal.animateTo(1f, animationSpec = motion.float(baseMs = 320))",
    "reveal.animateTo(1f, animationSpec = motion.float(baseMs = 320, direction = IslandTransitionDirection.ENTER))",
)
text = replace_once(
    text,
    "    val spec: AnimationSpec<Dp> = if (reveal.value == 0f || snapGeometry) snap() else motion.dp()\n",
    "    val geometryDirection = when (liveTransition) {\n"
    "        LiveActivityVisualTransition.PROMOTE_SATELLITE -> IslandTransitionDirection.PROMOTE\n"
    "        LiveActivityVisualTransition.DEMOTE_PRIMARY_TO_SATELLITE -> IslandTransitionDirection.DEMOTE\n"
    "        LiveActivityVisualTransition.HIDE -> IslandTransitionDirection.EXIT\n"
    "        else -> if (isExpanded) IslandTransitionDirection.EXPAND else IslandTransitionDirection.COLLAPSE\n"
    "    }\n"
    "    val spec: AnimationSpec<Dp> = if (reveal.value == 0f || snapGeometry) snap() else motion.dp(geometryDirection)\n",
    "directional geometry",
)
text = text.replace("isDynamicHeight -> motion.dpSmooth()", "isDynamicHeight -> motion.dpSmooth(geometryDirection)", 1)
text = replace_once(
    text,
    "    val satelliteSharing = satellite != null && !isExpanded && !isCall && !isStickToCamera\n",
    "    val satelliteSharing = satellite != null && !isExpanded && !isStickToCamera\n",
    "allow satellite with call",
)
text = text.replace(
    "// call (the call cutout fills its own trailing edge) and when stuck to the camera. Kept in a",
    "// expanded card is up or when stuck to the camera. Calls may keep a compact music satellite. Kept in a",
)
text = replace_once(
    text,
    "            animationSpec = motion.float(baseMs = if (satelliteShown) 320 else 200),\n",
    "            animationSpec = motion.float(\n"
    "                baseMs = if (satelliteShown) 320 else 200,\n"
    "                direction = if (satelliteShown) IslandTransitionDirection.SATELLITE_ENTER\n"
    "                else IslandTransitionDirection.SATELLITE_EXIT,\n"
    "            ),\n",
    "satellite direction",
)
path.write_text(text)


# ---------------------------------------------------------------------------
# IslandOverlayController: coordinator is canonical persistent scheduler.
# ---------------------------------------------------------------------------
path = Path("app/src/main/java/com/ekoehler/expressivecutout/overlay/IslandOverlayController.kt")
text = path.read_text()
text = replace_once(
    text,
    "import com.ekoehler.expressivecutout.core.SystemEventType\n",
    "import com.ekoehler.expressivecutout.core.SystemEventType\n"
    "import com.ekoehler.expressivecutout.core.live.LiveActivity\n"
    "import com.ekoehler.expressivecutout.core.live.LiveActivityCoordinator\n"
    "import com.ekoehler.expressivecutout.core.live.LiveActivityRegistry\n",
    "controller live imports",
)
text = replace_once(
    text,
    "import com.ekoehler.expressivecutout.service.CutoutNotificationListenerService\n",
    "import com.ekoehler.expressivecutout.service.CutoutNotificationListenerService\n"
    "import com.ekoehler.expressivecutout.service.ProgressData\n",
    "controller ProgressData import",
)
text = replace_once(
    text,
    "    private val currentEvent = MutableStateFlow<IslandEvent?>(null)\n",
    "    private val currentEvent = MutableStateFlow<IslandEvent?>(null)\n"
    "    /** Canonical persistent visual state projected from LiveActivityCoordinator slots. */\n"
    "    private val liveVisualState = MutableStateFlow(LiveActivityVisualState())\n"
    "    private val liveTransitionState = MutableStateFlow(LiveActivityVisualTransition.NONE)\n"
    "    private val liveEventCache = LinkedHashMap<String, IslandEvent>()\n"
    "    private var projectedLivePrimary: IslandEvent? = null\n"
    "    private var projectedLiveSatellite: IslandEvent? = null\n",
    "controller live state",
)
text = replace_once(
    text,
    "        observePreviewPin()\n"
    "        observeSignals()\n",
    "        observePreviewPin()\n"
    "        observeLiveActivitySlots()\n"
    "        observeSignals()\n",
    "start live slot observer",
)
text = replace_once(
    text,
    "                val satellite by satelliteEvent.collectAsStateWithLifecycle()\n"
    "                val layout by layoutState.collectAsStateWithLifecycle()\n",
    "                val satellite by satelliteEvent.collectAsStateWithLifecycle()\n"
    "                val liveTransition by liveTransitionState.collectAsStateWithLifecycle()\n"
    "                val layout by layoutState.collectAsStateWithLifecycle()\n",
    "collect live transition",
)
text = replace_once(
    text,
    "                        satellite = satellite,\n"
    "                        satellitePosition = behaviour.satellitePosition,\n",
    "                        satellite = satellite,\n"
    "                        satellitePosition = behaviour.satellitePosition,\n"
    "                        liveTransition = if (event?.stableId != null) liveTransition else LiveActivityVisualTransition.NONE,\n",
    "pass live transition",
)
text = replace_once(
    text,
    "            callActive && lastCallEvent != null -> {\n",
    "            projectedLivePrimary != null -> {\n"
    "                dismissJob?.cancel()\n"
    "                applyLiveProjection()\n"
    "            }\n"
    "            callActive && lastCallEvent != null -> {\n",
    "restore canonical live state",
)
text = replace_once(
    text,
    "    private fun isPinnedLiveTile(): Boolean = isPinnedMusic() || isPinnedCall() || isPinnedTimer() || isPinnedAssistant() || isPinnedLock()\n",
    "    private fun isPinnedLiveTile(): Boolean = currentEvent.value?.stableId != null ||\n"
    "        isPinnedMusic() || isPinnedCall() || isPinnedTimer() || isPinnedAssistant() || isPinnedLock()\n",
    "persistent live pinned",
)
text = replace_once(
    text,
    "    private fun satelliteSplitDp(): Int {\n"
    "        if (satelliteEvent.value == null) return 0\n"
    "        if (expanded) return 0\n"
    "        if (currentEvent.value?.call != null) return 0\n"
    "        if (isLandscapeSplitSuppressed()) return 0\n",
    "    private fun satelliteSplitDp(): Int {\n"
    "        if (satelliteEvent.value == null) return 0\n"
    "        if (expanded) return 0\n"
    "        if (isLandscapeSplitSuppressed()) return 0\n",
    "controller call satellite geometry",
)
text = replace_once(
    text,
    "    private fun onSatellitePromote() {\n"
    "        val bubble = satelliteEvent.value ?: return\n",
    "    private fun onSatellitePromote() {\n"
    "        val bubble = satelliteEvent.value ?: return\n"
    "        // Coordinator-owned slots cannot be reordered by the renderer; activate the satellite.\n"
    "        if (bubble.stableId != null && liveVisualState.value.satellite?.stableId == bubble.stableId) {\n"
    "            bubble.contentIntent?.let(::sendPendingIntent)\n"
    "            return\n"
    "        }\n",
    "canonical satellite click",
)
text = replace_once(
    text,
    "    private fun onExpandedChanged(isExpanded: Boolean) {\n",
    "    private fun onExpandedChanged(isExpanded: Boolean) {\n",
    "expanded anchor",
)
# Insert stable expansion tracking after targetExpanded is computed.
text = replace_once(
    text,
    "        val targetExpanded = if (isNoExpandLandscape) false else isExpanded\n",
    "        val targetExpanded = if (isNoExpandLandscape) false else isExpanded\n"
    "        if (currentEvent.value?.stableId != null) {\n"
    "            val next = LiveActivityVisualReducer.setExpanded(liveVisualState.value, targetExpanded)\n"
    "            liveVisualState.value = next\n"
    "            liveTransitionState.value = next.transition\n"
    "        }\n",
    "track live expanded state",
)
text = replace_once(
    text,
    "    private fun isLiveTileEvent(event: IslandEvent?): Boolean = event?.let {\n"
    "        it.media != null || it.call != null || it.timer != null || (isDeviceLocked && it.id == lastLockEvent?.id)\n"
    "    } == true\n",
    "    private fun isLiveTileEvent(event: IslandEvent?): Boolean = event?.let {\n"
    "        it.stableId != null || it.media != null || it.call != null || it.timer != null ||\n"
    "            (isDeviceLocked && it.id == lastLockEvent?.id)\n"
    "    } == true\n",
    "stable live tile recognition",
)

live_methods = r'''
    /** Collects the coordinator once; it alone decides persistent primary/satellite placement. */
    private fun observeLiveActivitySlots() = scope.launch {
        LiveActivityRegistry.coordinator.slots.collect(::updateLiveSlots)
    }

    /** Re-projects current coordinator slots, used when richer legacy payload arrives for the same ID. */
    private fun refreshLiveProjection() = updateLiveSlots(LiveActivityRegistry.coordinator.slots.value)

    /** Applies coordinator slots without re-ranking them in the renderer. */
    private fun updateLiveSlots(slots: LiveActivityCoordinator.Slots) {
        val visiblePrimary = slots.primary?.takeIf(::isLiveActivityVisible)
        val visibleSatellite = if (visiblePrimary != null) slots.satellite?.takeIf(::isLiveActivityVisible) else null
        val visibleSlots = LiveActivityCoordinator.Slots(visiblePrimary, visibleSatellite)
        val next = LiveActivityVisualReducer.reduce(liveVisualState.value, visibleSlots)
        liveVisualState.value = next
        liveTransitionState.value = next.transition
        projectedLivePrimary = next.primary?.let(::resolveLiveActivityEvent)
        projectedLiveSatellite = next.satellite?.let(::resolveLiveActivityEvent)
        val activeIds = setOfNotNull(next.primary?.stableId, next.satellite?.stableId)
        liveEventCache.keys.retainAll(activeIds)
        if (!overlayHidden && !previewPinned) applyLiveProjection()
    }

    /** User/app filters may hide a slot, but never promote/re-rank another activity here. */
    private fun isLiveActivityVisible(activity: LiveActivity): Boolean {
        if (activity.packageName in disabledApps) return false
        return when (activity.kind) {
            LiveActivity.Kind.CALL -> tileEnabled[DynamicTile.PHONE] != false && !shouldHideForPhoneApp()
            LiveActivity.Kind.TIMER -> tileEnabled[DynamicTile.TIMER] != false
            LiveActivity.Kind.MUSIC -> tileEnabled[DynamicTile.MUSIC] != false && !shouldHideForPlayerApp()
            LiveActivity.Kind.ASSISTANT -> tileEnabled[DynamicTile.ASSISTANT] != false
            else -> true
        }
    }

    /** Converts one neutral LiveActivity into the existing event renderer family, retaining its ID. */
    private fun resolveLiveActivityEvent(activity: LiveActivity): IslandEvent {
        val cached = liveEventCache[activity.stableId]
        val render = LiveActivityRenderAdapter.adapt(activity)
        val packageName = activity.packageName.orEmpty()
        val cutoutActions = activity.actions.mapNotNull { action ->
            val intent = action.intent ?: return@mapNotNull null
            CutoutSignal.Notification.Action(action.label, intent)
        }
        val progress = activity.progress?.let {
            ProgressData(
                max = it.max.coerceAtLeast(0),
                current = if (it.max > 0) it.current.coerceIn(0, it.max) else it.current.coerceAtLeast(0),
                isIndeterminate = it.isIndeterminate,
                title = activity.title,
            )
        }
        val signal: CutoutSignal = when (render.renderKind) {
            LiveActivityRenderKind.CALL -> CutoutSignal.Call(
                packageName = packageName,
                callerLabel = activity.title ?: activity.appName.orEmpty(),
                key = activity.notificationKey,
                contentIntent = activity.contentIntent,
                actions = cutoutActions,
                ongoing = activity.phase != "incoming",
            )
            LiveActivityRenderKind.TIMER -> CutoutSignal.Timer(
                packageName = packageName,
                label = activity.title,
                key = activity.notificationKey,
                contentIntent = activity.contentIntent,
                actions = cutoutActions,
            )
            LiveActivityRenderKind.MUSIC -> CutoutSignal.Music(
                packageName = packageName,
                title = activity.title,
                artist = activity.subtitle,
                contentIntent = activity.contentIntent,
            )
            LiveActivityRenderKind.GENERIC_PROGRESS,
            LiveActivityRenderKind.GENERIC_LIVE -> CutoutSignal.Notification(
                packageName = packageName,
                title = activity.title,
                text = activity.subtitle ?: activity.phase,
                appName = activity.appName,
                key = activity.notificationKey,
                contentIntent = activity.contentIntent,
                actions = cutoutActions,
                progressData = progress,
            )
        }
        val autoExpand = when (render.renderKind) {
            LiveActivityRenderKind.MUSIC -> musicSettings.expandOnPlay
            LiveActivityRenderKind.CALL,
            LiveActivityRenderKind.TIMER -> false
            else -> behaviourState.value.notificationsAutoExpand
        }
        val normalOnly = activity.packageName in normalOnlyApps
        val resolved = resolver.resolve(
            signal = signal,
            customIcons = customIcons,
            musicSettings = musicSettings,
            phoneSettings = phoneSettings,
            timerSettings = timerSettings,
            assistantSettings = assistantSettings,
            dynamicEventColor = eventDynamicColor,
            dynamicEventColorRole = eventDynamicColorRole,
            dynamicEventColorOpacity = eventDynamicColorOpacity,
            animatedIconEnabled = eventAnimatedIcons,
            animatedIconLoop = eventAnimatedIconLoops,
            eventColorOverrides = eventColors,
            preferDynamicIconColor = appearanceState.value.preferDynamicIconColor,
        ).copy(
            id = cached?.id ?: resolverIdFallback(cached),
            initiallyExpanded = autoExpand,
            normalOnly = normalOnly,
            stableId = activity.stableId,
            label = activity.title?.takeIf { it.isNotBlank() }
                ?: activity.appName?.takeIf { it.isNotBlank() }
                ?: signalLabel(signal),
            detail = activity.subtitle ?: activity.phase,
            appName = activity.appName,
            contentIntent = activity.contentIntent,
            notificationKey = activity.notificationKey,
            progressData = progress,
            secondaryLines = activity.phase?.takeIf { it != activity.subtitle && it != activity.title }?.let(::listOf)
                ?: emptyList(),
        )
        val finalEvent = if (cached != null) resolved.copy(icon = cached.icon, appColor = cached.appColor) else resolved
        liveEventCache[activity.stableId] = finalEvent
        return finalEvent
    }

    /** Keeps a freshly resolved id when there is no cached stable event. */
    private fun resolverIdFallback(cached: IslandEvent?): Long = cached?.id ?: Long.MIN_VALUE

    /** Fallback label used only when the neutral source supplied no title/app name. */
    private fun signalLabel(signal: CutoutSignal): String = when (signal) {
        is CutoutSignal.Music -> signal.title ?: context.getString(DynamicTile.MUSIC.labelRes)
        is CutoutSignal.Call -> signal.callerLabel
        is CutoutSignal.Timer -> signal.label ?: context.getString(DynamicTile.TIMER.labelRes)
        is CutoutSignal.Notification -> signal.title ?: signal.appName ?: "Live activity"
        is CutoutSignal.Assistant -> signal.title ?: "Assistant"
        is CutoutSignal.System -> context.getString(signal.type.labelRes)
    }

    /** Shows the canonical live slots, temporarily leaving a transient legacy event on top when safe. */
    private fun applyLiveProjection() {
        val primary = projectedLivePrimary
        val secondary = projectedLiveSatellite
        val current = currentEvent.value
        val transientOnTop = current != null && current.stableId == null
        val callMustLead = liveVisualState.value.primary?.kind == LiveActivity.Kind.CALL
        val splitAllowed = behaviourState.value.splitIslandEnabled &&
            !isLandscapeSplitSuppressed() && satelliteFitsWidth()

        satelliteDismissJob?.cancel()
        if (transientOnTop && !callMustLead) {
            if (satelliteEvent.value?.stableId != null || primary != null) {
                satelliteEvent.value = if (splitAllowed) primary else null
                satelliteDeadlineMs = null
                satelliteSystemEventType = null
            }
            syncWindowSize()
            return
        }

        dismissJob?.cancel()
        currentDeadlineMs = null
        satelliteDeadlineMs = null
        currentSystemEventType = null
        satelliteSystemEventType = null
        currentEvent.value = primary
        satelliteEvent.value = if (splitAllowed) secondary else null
        if (primary == null) {
            forcedExpanded.value = null
            expanded = false
        }
        syncWindowSize()
    }

    /** Finds whether a legacy signal is merely richer payload for an already canonical activity. */
    private fun matchingLiveActivity(signal: CutoutSignal): LiveActivity? {
        val activities = LiveActivityRegistry.coordinator.state.value
        return when (signal) {
            is CutoutSignal.Call -> activities.firstOrNull {
                it.kind == LiveActivity.Kind.CALL && it.packageName == signal.packageName &&
                    (signal.key == null || it.notificationKey == signal.key)
            }
            is CutoutSignal.Timer -> activities.firstOrNull {
                it.kind == LiveActivity.Kind.TIMER && it.packageName == signal.packageName &&
                    (signal.key == null || it.notificationKey == signal.key)
            }
            is CutoutSignal.Music -> activities.firstOrNull {
                it.kind == LiveActivity.Kind.MUSIC && it.packageName == signal.packageName
            }
            is CutoutSignal.Notification -> activities.firstOrNull {
                it.notificationKey != null && it.notificationKey == signal.key && it.packageName == signal.packageName
            }
            is CutoutSignal.Assistant -> activities.firstOrNull {
                it.kind == LiveActivity.Kind.ASSISTANT && it.packageName == signal.packageName
            }
            is CutoutSignal.System -> null
        }
    }

    /** Enriches a canonical activity from the legacy producer without letting that producer schedule it. */
    private fun absorbCanonicalSignal(signal: CutoutSignal, resolved: IslandEvent, activity: LiveActivity) {
        val cached = liveEventCache[activity.stableId]
        val enriched = resolved.copy(
            id = cached?.id ?: resolved.id,
            stableId = activity.stableId,
            initiallyExpanded = cached?.initiallyExpanded ?: resolved.initiallyExpanded,
        )
        liveEventCache[activity.stableId] = enriched
        when (signal) {
            is CutoutSignal.Music -> {
                musicPlaying = true
                lastMusicEvent = enriched
            }
            is CutoutSignal.Call -> {
                callActive = true
                lastCallEvent = enriched
            }
            is CutoutSignal.Timer -> {
                timerActive = true
                lastTimerEvent = enriched
            }
            is CutoutSignal.Assistant -> {
                assistantActive = signal.active
                lastAssistantEvent = enriched
            }
            else -> Unit
        }
        refreshLiveProjection()
    }

'''
text = replace_once(
    text,
    "    /**\n"
    "     * The single consumer of [IslandEventBus]: turns each signal into a pill or a live tile,\n",
    live_methods +
    "    /**\n"
    "     * The single consumer of [IslandEventBus]: turns each signal into a pill or a live tile,\n",
    "insert coordinator renderer methods",
)

# Fix the fresh-id placeholder generated above: resolve once, then retain cached ID if present.
text = text.replace(
    "        val resolved = resolver.resolve(\n",
    "        val freshlyResolved = resolver.resolve(\n",
    1,
)
text = text.replace(
    "        ).copy(\n            id = cached?.id ?: resolverIdFallback(cached),\n",
    "        )\n        val resolved = freshlyResolved.copy(\n            id = cached?.id ?: freshlyResolved.id,\n",
    1,
)
text = text.replace(
    "\n    /** Keeps a freshly resolved id when there is no cached stable event. */\n"
    "    private fun resolverIdFallback(cached: IslandEvent?): Long = cached?.id ?: Long.MIN_VALUE\n",
    "",
    1,
)

# In observeSignals, canonical signals enrich cache and return before legacy scheduling.
anchor = "            ).copy(initiallyExpanded = autoExpand, normalOnly = normalOnly)\n\n            if (overlayHidden) {\n"
replacement = (
    "            ).copy(initiallyExpanded = autoExpand, normalOnly = normalOnly)\n\n"
    "            val canonicalActivity = matchingLiveActivity(signal)\n"
    "            if (canonicalActivity != null) {\n"
    "                absorbCanonicalSignal(signal, resolvedEvent, canonicalActivity)\n"
    "                return@collect\n"
    "            }\n\n"
    "            // A live call, or an already full persistent pair, cannot be displaced by a transient.\n"
    "            val liveSlots = liveVisualState.value\n"
    "            if (liveSlots.primary?.kind == LiveActivity.Kind.CALL || liveSlots.satellite != null) {\n"
    "                if (signal !is CutoutSignal.Call && signal !is CutoutSignal.Music && signal !is CutoutSignal.Timer) {\n"
    "                    return@collect\n"
    "                }\n"
    "            }\n\n"
    "            if (overlayHidden) {\n"
)
text = replace_once(text, anchor, replacement, "canonical signal short-circuit")

# Generic persistent live events count as pinned/showing live.
text = replace_once(
    text,
    "        // Whatever is parked in the bubble is already visible, so slide it into the pill rather than\n"
    "        // clearing the island and waiting out the usual return delay, which would read as a stutter.\n"
    "        if (promoteSatelliteCollapsed()) return\n",
    "        // Whatever is parked in the bubble is already visible, so slide it into the pill rather than\n"
    "        // clearing the island and waiting out the usual return delay, which would read as a stutter.\n"
    "        if (promoteSatelliteCollapsed()) return\n"
    "        projectedLivePrimary?.let { live ->\n"
    "            currentEvent.value = live\n"
    "            satelliteEvent.value = projectedLiveSatellite\n"
    "            syncWindowSize()\n"
    "            return\n"
    "        }\n",
    "restore persistent live immediately",
)
path.write_text(text)

print("LIVE_RENDERER_PATCH=APPLIED")
