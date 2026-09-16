# Expressive Cutout Live Activities Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a compilable debug APK with Android 16 Live Updates, local semantic fallback, stable simultaneous-activity scheduling, polished motion, Shizuku status-icon pulse, and resilient opt-in keep-alive.

**Architecture:** Introduce a small `core/live` domain model and coordinator, adapt existing producers incrementally, and keep ordinary system events on the legacy bus until parity is verified. Android 16 framework access stays isolated in `notifications/live`, while rendering receives neutral models with stable identity. Recovery and Shizuku features remain owned by their existing process/service owners instead of UI state.

**Tech Stack:** Kotlin/JVM 17, Android SDK 29-36, Jetpack Compose, coroutines/Flow, DataStore, Shizuku, Android notification APIs, optional AndroidX WorkManager, JUnit 4, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-16-expressive-cutout-live-activities-design.md`

## Global Constraints

- `minSdk=29`, `targetSdk=35`, `compileSdk=36`.
- No `SYSTEM_ALERT_WINDOW`, immersive/status-bar hiding APIs, navigation-bar changes, exact alarms, runtime networking, copied LiveBridge logic, or private OEM settings intents.
- Notification private content and OTP never persist or enter logs.
- API 36 types are isolated behind runtime guards and `@RequiresApi(36)`.
- Preserve current fork lineage from `33ec91e21beca8099b277350fbbc5336493a70d9`; do not merge upstream PR #42 automatically.
- KDoc/visibility/coroutine lifecycle/package layout follow repository style.
- Every behavior change follows red-green-refactor; CI runs unit tests before assemble.

---

### Task 1: CI baseline and pure live domain

**Files:**
- Modify: `.github/workflows/build.yml`
- Create: `app/src/main/java/com/ekoehler/expressivecutout/core/live/LiveActivity.kt`
- Create: `app/src/main/java/com/ekoehler/expressivecutout/core/live/LiveActivityUpdate.kt`
- Create: `app/src/main/java/com/ekoehler/expressivecutout/core/live/LiveActivitySource.kt`
- Create: `app/src/main/java/com/ekoehler/expressivecutout/core/live/LiveActivityCoordinator.kt`
- Create: `app/src/test/java/com/ekoehler/expressivecutout/core/live/LiveActivityCoordinatorTest.kt`
- Modify: `docs/CODING_STYLE.md` project tree

**Interfaces:**
- Produces `LiveActivity`, `LiveActivityKind`, `LiveActivityLifecycle`, `LiveActivityPriority`, `LiveActivityUpdate`, `LiveActivitySource`, `LiveActivityCoordinator.state`, `LiveActivityCoordinator.slots`, `upsert`, and `remove`.

- [ ] Add CI trigger for this feature branch and run `testDebugUnitTest` before `assembleDebug`.
- [ ] Write failing coordinator tests for stable in-place update, call priority, primary/satellite uniqueness, music retention under call, and remove.
- [ ] Trigger CI and verify RED because domain types do not exist.
- [ ] Implement the minimal immutable model/coordinator to satisfy tests.
- [ ] Trigger CI and verify unit tests + assemble GREEN.
- [ ] Commit only the domain/CI/style-tree changes.

### Task 2: Android 16 native notification source

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/ekoehler/expressivecutout/notifications/live/Android36LiveActivitySource.kt`
- Create: `app/src/main/java/com/ekoehler/expressivecutout/notifications/live/NativeLiveSnapshot.kt`
- Create: `app/src/test/java/com/ekoehler/expressivecutout/notifications/live/NativeLivePolicyTest.kt`
- Modify: `docs/CODING_STYLE.md`

**Interfaces:**
- Produces `Android36LiveActivitySource.inspect(context, sbn): NativeLiveSnapshot?` and a pure `NativeLivePolicy` used by tests.
- `NativeLiveSnapshot` contains promoted/requested/promotable evidence plus neutral progress/segment/point/icon metadata.

- [ ] Write pure failing tests for actual-promoted vs merely-promotable/requested classification and pre-36 guard policy.
- [ ] Trigger CI RED.
- [ ] Move `compileSdk` to 36 and remove duplicate hard-coded JUnit declaration.
- [ ] Implement isolated API-36 builder/style recovery with guarded fallbacks.
- [ ] Trigger CI GREEN and inspect warnings/errors.

### Task 3: Offline semantic parser and notification pipeline

**Files:**
- Create: `app/src/main/java/com/ekoehler/expressivecutout/notifications/live/NotificationSemanticParser.kt`
- Create: `app/src/main/java/com/ekoehler/expressivecutout/notifications/live/ParsedLiveCandidate.kt`
- Create: `app/src/test/java/com/ekoehler/expressivecutout/notifications/live/NotificationSemanticParserTest.kt`
- Modify: `app/src/main/java/com/ekoehler/expressivecutout/service/CutoutNotificationListenerService.kt`

**Interfaces:**
- Parser consumes normalized strings/actions/progress and returns a candidate with kind, phase, confidence, privacy-safe evidence codes, TTL/lifecycle, and optional progress/timing.
- Listener chooses one branch: call -> timer -> native API 36 -> semantic -> generic.

- [ ] Write failing EN/ES parser tests for rideshare, food, parcel, weather, OTP, generic progress and explicit false positives.
- [ ] Trigger CI RED.
- [ ] Implement normalization/scoring/exclusions without logging input.
- [ ] Add listener adapter that preserves legacy outputs while upserting coordinator activities and allows native/promotable ongoing notifications through `shouldSurface`.
- [ ] Add removal and stable post-time identity handling.
- [ ] Trigger CI GREEN.

### Task 4: Adapt call/timer/media/assistant and deterministic slots

**Files:**
- Modify: `events/MediaPlaybackMonitor.kt`
- Modify: `events/CallNotificationParser.kt` only where mapping requires it
- Modify: `events/TimerNotificationParser.kt` only where mapping requires it
- Modify: `service/CutoutAccessibilityService.kt`
- Modify: `overlay/IslandEvent.kt`
- Modify: `overlay/IconResolver.kt`
- Modify: `overlay/IslandOverlayController.kt`
- Modify: `overlay/IslandSatellite.kt`
- Create/modify tests under `core/live` and `overlay`

**Interfaces:**
- Producers publish stable activities into the coordinator while legacy buses remain temporary compatibility adapters.
- Overlay maps coordinator `primary`/`satellite` to existing specific renderers.

- [ ] Write failing tests for call+music, timer+music, same-stable-ID dedupe, assistant return, and call never demoted.
- [ ] Trigger CI RED.
- [ ] Implement producer adapters and controller slot observation.
- [ ] Preserve music state/art updates while call is primary and restore without new identity.
- [ ] Trigger CI GREEN.

### Task 5: Stable visual identity and motion state machine

**Files:**
- Modify: `overlay/DynamicIsland.kt`
- Modify: `overlay/IslandMotion.kt`
- Modify: `overlay/IslandEvent.kt`
- Create/modify motion tests under `app/src/test/.../overlay`

**Interfaces:**
- Adds visual state `Hidden/Collapsed/Expanded` and pure duration-to-stiffness helpers.
- Compose identity keys use stable activity ID + presentation kind, never phase/progress/title.

- [ ] Write failing tests for duration scaling, snap at zero, bounds, and unchanged stable visual ID over phase updates.
- [ ] Trigger CI RED.
- [ ] Implement one transition target, direction-specific springs, internal text crossfade, and duration-aware EXPRESSIVE stiffness.
- [ ] Replace binary content Crossfade with Empty/Collapsed/Expanded + presentation kind key.
- [ ] Trigger CI GREEN.

### Task 6: Shizuku momentary notification-icon pulse

**Files:**
- Modify: `data/StatusBarPreferences.kt`
- Modify: `system/StatusBarIconController.kt`
- Modify: `ui/AppViewModel.kt`
- Modify: `ui/screen/SettingScreens/ShizukuScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `overlay/IslandOverlayController.kt`
- Create: `app/src/test/java/com/ekoehler/expressivecutout/system/StatusBarPulseStateTest.kt`

**Interfaces:**
- `pulseNotificationIcons(durationMs: Long)` extends one elapsed-realtime deadline and composes transient notification-icon hiding with persistent disable flags.

- [ ] Write failing reducer tests for t=0/1s/2s pulses restoring at 4.5s for 2.5s duration and persistent flags surviving restore.
- [ ] Trigger CI RED.
- [ ] Implement preferences, reducer/deadline/job, UI toggle/slider, and new-visible-activity hook.
- [ ] Trigger CI GREEN.

### Task 7: Listener backoff and opt-in keep-alive

**Files:**
- Create: `service/NotificationListenerRebinder.kt`
- Modify: `service/CutoutNotificationListenerService.kt`
- Modify: `service/CutoutAccessibilityService.kt`
- Modify: `MainActivity.kt`
- Create: `service/IslandKeepAliveService.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Add preference/UI wiring in existing behavior/background settings surface
- Create tests for backoff sequence and keep-alive pure policy

**Interfaces:**
- Rebinder exposes bounded sequence `[1000,2000,4000,8000,16000,30000]` and reset-on-connect behavior.
- Keep-alive is a separate user-started `specialUse` FGS returning `START_STICKY`.

- [ ] Write failing backoff and start-policy tests.
- [ ] Trigger CI RED.
- [ ] Replace current accessibility watchdog with single bounded owner and resync media on listener connect.
- [ ] Add specialUse permissions/service/property, low-importance notification, open-app/stop actions, and visible-user start/stop path.
- [ ] Trigger CI GREEN.

### Task 8: Optional WorkManager health check, privacy audit, final artifact

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `work/ResilienceWorker.kt`
- Modify: `ExpressiveCutoutApp.kt`
- Update permission/help UI and strings for OEM guidance
- Update README/docs where privacy/accessibility wording changes
- Final workflow/artifact metadata collection

**Interfaces:**
- Unique periodic health work performs only permission check + `requestRebind`; interval >=15 minutes; never starts Accessibility or FGS.

- [ ] Add WorkManager version-catalog alias and worker/scheduling only if CI resolves the dependency cleanly.
- [ ] Add OEM help text using only generic app-details settings.
- [ ] Search source/manifest for `INTERNET`, `SYSTEM_ALERT_WINDOW`, exact-alarm permissions, immersive status-bar APIs, private OEM components, and private-content logging.
- [ ] Run final `testDebugUnitTest`, `assembleDebug`, and lint.
- [ ] Download the Actions APK artifact, compute size/SHA-256, and inspect manifest/package/version metadata.
- [ ] If no emulator/device is available, explicitly record that installation smoke test was not run and provide `adb install -r`.
