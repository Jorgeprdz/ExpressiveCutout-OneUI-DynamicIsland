# Expressive Cutout Live Activities / Dynamic Island Design

## Status

Approved by the user on 2026-09-16. This document records the implementation contract supplied in the ChatGPT handoff and reconciled against the current fork branch `fix/background-listener-recovery` at `33ec91e21beca8099b277350fbbc5336493a70d9`.

Upstream `EvanKoe/expressive-cutout` merged PR #42 (`Feature/rework music`) as merge commit `80353d2a90d09ddf36775e843d86ad007f49ebb0` after the audited base. This work MUST NOT merge that PR automatically. It preserves the current fork lineage and may only integrate upstream music changes deliberately in a later review.

## Goal

Implement four coordinated areas without regressing ordinary notifications, media, calls, timer, assistant, system events, split/satellite island, or Shizuku:

1. Android 16 native Live Updates plus a local offline semantic fallback.
2. Stable activity identity, deterministic primary/satellite scheduling, and smoother island motion.
3. Momentary notification-icon hiding through the existing Shizuku `IStatusBarService.disable` token.
4. Modern listener recovery plus an opt-in `specialUse` foreground service and optional WorkManager health check.

## Non-negotiable constraints

- `minSdk` remains 29; `targetSdk` remains 35 during this scope; `compileSdk` moves to 36.
- No `SYSTEM_ALERT_WINDOW`, immersive mode, `WindowInsetsController.hide(statusBars())`, navigation-bar changes, exact alarms, private OEM settings intents, or runtime network dependency.
- `INTERNET` stays absent. Semantic rules and fixtures are bundled in the APK.
- Notification bodies, OTPs, names, addresses, order identifiers, and private text are processed in memory only and never logged, persisted, or sent to analytics.
- No LiveBridge code, regex, or dictionaries are copied. Its GPL repository may inform concepts only.
- Android 16 framework references are isolated behind `SDK_INT >= 36` guards and `@RequiresApi(36)`.
- Existing call/timer parsers and media-session behavior are adapted, not needlessly replaced.
- KDoc, visibility, coroutine lifecycle, Compose, and package-layout rules in `AGENTS.md` and `docs/CODING_STYLE.md` apply unless contradicted by the observed tree or by this approved scope.

## Architecture

### Unified activity model

Add `core/live` with immutable `LiveActivity`, `LiveActivityUpdate`, `LiveActivitySource`, and `LiveActivityCoordinator` types. Notification-backed stable IDs use notification key plus the first observed post time. Media/call/timer/assistant use stable source-owned identifiers. `LiveActivityCoordinator` owns the registry, priority ordering, and two visible slots; the same stable activity is never present in both slots.

The migration is incremental: sources can continue feeding legacy buses while the overlay is moved to the coordinator. Legacy boolean/last-event bookkeeping is removed only after parity tests pass.

### Notification pipeline

`CutoutNotificationListenerService` uses exactly one winning branch per notification:

1. call parser,
2. timer parser,
3. Android 16 native live detector,
4. semantic fallback,
5. generic/progress notification.

Removal always propagates by stable identity. Native Android 16 handling recognizes actual promoted ongoing flags authoritatively and promotable/requested state only as secondary evidence. `Notification.Builder.recoverBuilder()` is guarded with `runCatching`; an OEM/framework failure falls through to the semantic parser.

### Semantic fallback

`notifications/live/NotificationSemanticParser.kt` normalizes notification extras and action labels in memory. It uses score-based evidence and explicit exclusions for promotions, commercial percentages, currency, hidden content, years, phone numbers, and ambiguous text. Categories are rideshare, food order, parcel delivery, weather, OTP, and generic progress. OTP is sensitive, ephemeral, hidden where configured, and never persisted/logged.

### Visual and scheduling policy

`LiveActivityCoordinator` provides deterministic ordering: call first; fresh OTP or direct user interaction next; ride/delivery/timer next; assistant; music; transient weather/system/generic events last. Two slots are visible at most. During an incoming/active call, music remains registered and may occupy satellite only when width/layout rules permit. Ending the call restores music without a new reveal/icon pop.

A stable ID remains the Compose identity across phase/progress/title changes. Internal labels may crossfade, but whole-card reveal/dismiss/expanded state is not restarted.

### Motion

The island exposes visual states `Hidden`, `Collapsed`, and `Expanded` with one coherent transition target. Geometry uses direction-specific springs; alpha/color remain critically damped. The existing duration slider must influence EXPRESSIVE motion by converting the selected duration to spring stiffness with safe clamping, with `0` mapping to snap.

### Status-bar icon pulse

`StatusBarIconController` gains a process-lifetime transient deadline. `pulseNotificationIcons(durationMs)` extends a single elapsed-realtime deadline; it composes with persistent icon/system-info/clock/alert settings on the existing Binder token. Expiry restores only the transient bit and leaves all persistent flags untouched.

The overlay invokes the pulse only when a new stable activity actually becomes visible, never for progress ticks, repeated media metadata, or phase updates of the same activity.

### Recovery

Listener rebind uses bounded exponential backoff: 1, 2, 4, 8, 16, then 30 seconds, maximum six attempts, reset on connect. The accessibility service and `MainActivity` keep defensive permission-aware rebinds. Media sessions resync after listener recovery.

An opt-in `IslandKeepAliveService` is a separate `specialUse` FGS with low-importance ongoing notification and `START_STICKY`. It is user-started from visible UI only and makes no promise against force-stop or OEM killing. WorkManager may be added for a >=15 minute local health check; AlarmManager is not used.

## Verification

Required automated tests cover semantic positives/false positives in English and Spanish, identity preservation, priority/deduplication, TTL/removal, OTP redaction behavior, status-bar deadline/flag composition, listener backoff, and motion-duration mapping. CI must run at least `./gradlew testDebugUnitTest` and `./gradlew assembleDebug`.

Manual validation must cover Android 10-16 behavior, Shizuku ready/dead/restarted, permanent icon toggles, burst pulses, call+music, timer+music, notification+music, lock screen/OTP, listener reconnect, FGS toggle, and OEM background guidance. No test claim may be made without real evidence.
