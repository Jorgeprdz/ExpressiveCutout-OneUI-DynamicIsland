from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"missing patch anchor: {label}")
    return text.replace(old, new, 1)


path = Path("app/src/main/java/com/ekoehler/expressivecutout/overlay/IslandOverlayController.kt")
text = path.read_text()

text = replace_once(
    text,
    "import com.ekoehler.expressivecutout.system.PermissionUsageMonitor\n",
    "import com.ekoehler.expressivecutout.system.PermissionUsageMonitor\n"
    "import com.ekoehler.expressivecutout.system.StatusBarIconController\n",
    "status bar pulse import",
)

text = replace_once(
    text,
    "    private var projectedLivePrimary: IslandEvent? = null\n"
    "    private var projectedLiveSatellite: IslandEvent? = null\n",
    "    private var projectedLivePrimary: IslandEvent? = null\n"
    "    private var projectedLiveSatellite: IslandEvent? = null\n"
    "    /** Stable IDs already observed by this controller, used only to detect true live arrivals. */\n"
    "    private var liveActivityPulseState = LiveActivityPulseDetector.State()\n",
    "live pulse detector state",
)

text = replace_once(
    text,
    "        val visibleSatellite = if (visiblePrimary != null) slots.satellite?.takeIf(::isLiveActivityVisible) else null\n"
    "        val visibleSlots = LiveActivityCoordinator.Slots(visiblePrimary, visibleSatellite)\n"
    "        val next = LiveActivityVisualReducer.reduce(liveVisualState.value, visibleSlots)\n",
    "        val visibleSatellite = if (visiblePrimary != null) slots.satellite?.takeIf(::isLiveActivityVisible) else null\n"
    "        val visibleSlots = LiveActivityCoordinator.Slots(visiblePrimary, visibleSatellite)\n"
    "        observeLiveActivityPulse(slots, visibleSlots)\n"
    "        val next = LiveActivityVisualReducer.reduce(liveVisualState.value, visibleSlots)\n",
    "observe pulse before visual reduction",
)

pulse_methods = r'''
    /**
     * Pulses only for stable identities that become genuinely visible. Canonical-but-hidden IDs are
     * still recorded as seen so restoring the overlay, enabling split mode, or changing app filters
     * cannot masquerade as a fresh arrival.
     */
    private fun observeLiveActivityPulse(
        canonicalSlots: LiveActivityCoordinator.Slots,
        filteredSlots: LiveActivityCoordinator.Slots,
    ) {
        val presentStableIds = setOfNotNull(
            canonicalSlots.primary?.stableId,
            canonicalSlots.satellite?.stableId,
        )
        val overlayCanProject = behaviourState.value.cutoutEnabled && !overlayHidden && !previewPinned
        val splitAllowed = overlayCanProject && behaviourState.value.splitIslandEnabled &&
            !isLandscapeSplitSuppressed() && satelliteFitsWidth()
        val transientOnTop = currentEvent.value?.let { it.stableId == null } == true
        val visibleStableIds = LiveActivityPulseVisibility.visibleStableIds(
            slots = filteredSlots,
            overlayCanProject = overlayCanProject,
            splitAllowed = splitAllowed,
            transientOnTop = transientOnTop,
        )
        val result = LiveActivityPulseDetector.observe(
            previous = liveActivityPulseState,
            presentStableIds = presentStableIds,
            visibleStableIds = visibleStableIds,
        )
        liveActivityPulseState = result.state
        if (result.shouldPulse) {
            StatusBarIconController.pulseNotificationIcons()
        }
    }

'''

text = replace_once(
    text,
    "    /** User/app filters may hide a slot, but never promote/re-rank another activity here. */\n",
    pulse_methods + "    /** User/app filters may hide a slot, but never promote/re-rank another activity here. */\n",
    "insert live pulse observer",
)

path.write_text(text)
print("LIVE_PULSE_WIRING=APPLIED")
