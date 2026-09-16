package com.ekoehler.expressivecutout.system

import android.content.Context
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.ekoehler.expressivecutout.data.StatusBarPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/** Log tag for the reflection below, which reports failures rather than throwing. */
private const val TAG = "StatusBarIcons"

/** Hidden `StatusBarManager` disable flags. Only the ones we use are named here. */
private const val DISABLE_NONE = 0x00000000
private const val DISABLE_NOTIFICATION_ICONS = 0x00020000
private const val DISABLE_NOTIFICATION_ALERTS = 0x00040000
private const val DISABLE_SYSTEM_INFO = 0x00100000
private const val DISABLE_CLOCK = 0x00800000

/**
 * Hides selected system status-bar elements through Shizuku while composing transient effects over
 * the user's persistent wishes.
 *
 * The call itself is `IStatusBarService.disable`, which needs `android.permission.STATUS_BAR` — a
 * privileged permission we can never hold, but shell does, which is what Shizuku lends us.
 *
 * Two things drive the design:
 *
 * - **Reflection, not a stub AIDL.** AIDL transaction IDs are positional, so a hand-written
 *   `IStatusBarService.aidl` would have to match the running OS's method order exactly and would
 *   break on the next Android release. Reflecting on the framework's own class always matches.
 * - **[token] must outlive the call.** `StatusBarManagerService` keeps the disable flags in a record
 *   keyed by this binder and drops them when it dies, so the token is held for the process lifetime.
 *   The upshot is a free safety net: if our process is killed the icons come back on their own, and
 *   [start] re-applies them next launch.
 */
object StatusBarIconController {

    /** The one process-lifetime status-bar client identity, reused for persistent and transient flags. */
    private val token = Binder()

    /** Cached Shizuku-backed framework proxy, invalidated whenever the bridge becomes unavailable. */
    @Volatile
    private var service: Any? = null

    /** Last user wish observed from DataStore; transient pulse state is never persisted into it. */
    @Volatile
    private var persistentFlags = StatusBarFlagState()

    /** In-memory notification-icon suppression layered over [persistentFlags]. */
    @Volatile
    private var transientHideNotificationIcons = false

    /** Package identity supplied to `IStatusBarService.disable`, seeded once from [start]. */
    @Volatile
    private var applicationPackageName: String? = null

    /**
     * Keeps the system status bar in sync with the saved wish, re-applying whenever Shizuku becomes
     * reachable again — after a reboot, or after the user starts Shizuku for the first time.
     *
     * There is deliberately no `stop()`, and adding one would be a mistake: releasing [token] is
     * what restores the system icons, so a public stop would be a way to silently undo the user's
     * setting. The process dying is the only thing that should clear these flags, which is the
     * safety net described above.
     */
    fun start(context: Context, scope: CoroutineScope) {
        val preferences = StatusBarPreferences(context)
        applicationPackageName = context.packageName
        scope.launch {
            combine(
                preferences.hideNotificationIcons,
                preferences.hideSystemInfo,
                preferences.hideClock,
                preferences.silenceAlerts,
                ShizukuState.status,
            ) { hideIcons, hideSystemInfo, hideClock, silenceAlerts, status ->
                Wish(hideIcons, hideSystemInfo, hideClock, silenceAlerts, status)
            }
                .collect { wish ->
                    updatePersistentFlags(
                        StatusBarFlagState(
                            hideNotificationIcons = wish.hideIcons,
                            hideSystemInfo = wish.hideSystemInfo,
                            hideClock = wish.hideClock,
                            silenceAlerts = wish.silenceAlerts,
                        ),
                    )
                    if (wish.status != ShizukuStatus.READY) {
                        // A dead Shizuku also invalidates the cached proxy; drop it so the next
                        // successful call rebuilds one over the fresh binder.
                        service = null
                        return@collect
                    }
                    applyEffectiveFlags(context.packageName)
                }
        }
    }

    /**
     * Applies or clears the persistent status-bar wishes while preserving any active transient
     * notification-icon pulse. Kept as the existing public API for callers outside [start].
     */
    @Synchronized
    fun apply(
        hideIcons: Boolean,
        hideSystemInfo: Boolean,
        hideClock: Boolean,
        silenceAlerts: Boolean,
        packageName: String,
    ): Boolean {
        applicationPackageName = packageName
        persistentFlags = StatusBarFlagState(
            hideNotificationIcons = hideIcons,
            hideSystemInfo = hideSystemInfo,
            hideClock = hideClock,
            silenceAlerts = silenceAlerts,
        )
        return applyEffectiveFlagsLocked(packageName)
    }

    /**
     * Temporarily adds notification-icon suppression to the saved status-bar wish. Activation is
     * skipped when Shizuku is unavailable and never requests permission; deactivation always clears
     * the in-memory transient bit so a later reconnect restores only the user's persistent wishes.
     */
    @Synchronized
    fun setTransientNotificationIconSuppression(active: Boolean): Boolean {
        val packageName = applicationPackageName ?: return false
        if (active && ShizukuState.status.value != ShizukuStatus.READY) return false

        transientHideNotificationIcons = active
        if (ShizukuState.status.value != ShizukuStatus.READY) {
            service = null
            return false
        }

        val applied = applyEffectiveFlagsLocked(packageName)
        if (!applied && active) transientHideNotificationIcons = false
        return applied
    }

    /** Replaces the remembered persistent wish without touching transient pulse state. */
    @Synchronized
    private fun updatePersistentFlags(flags: StatusBarFlagState) {
        persistentFlags = flags
    }

    /** Applies the composed persistent and transient wish using the process-lifetime [token]. */
    @Synchronized
    private fun applyEffectiveFlags(packageName: String): Boolean = applyEffectiveFlagsLocked(packageName)

    /** Performs the composed apply while the controller monitor is already held. */
    private fun applyEffectiveFlagsLocked(packageName: String): Boolean {
        val effective = StatusBarEffectiveFlags.compose(
            persistent = persistentFlags,
            transientHideNotificationIcons = transientHideNotificationIcons,
        )
        return applyFlagsLocked(effective, packageName)
    }

    /** Converts one complete effective wish into the platform disable mask and applies it atomically. */
    private fun applyFlagsLocked(flags: StatusBarFlagState, packageName: String): Boolean = runCatching {
        var disableFlags = DISABLE_NONE
        if (flags.hideNotificationIcons) disableFlags = disableFlags or DISABLE_NOTIFICATION_ICONS
        if (flags.hideSystemInfo) disableFlags = disableFlags or DISABLE_SYSTEM_INFO
        if (flags.hideClock) disableFlags = disableFlags or DISABLE_CLOCK
        if (flags.silenceAlerts) disableFlags = disableFlags or DISABLE_NOTIFICATION_ALERTS
        val statusBar = service ?: buildService().also { service = it }
        statusBar.disable(disableFlags, packageName)
        true
    }.getOrElse { error ->
        Log.w(
            TAG,
            "Could not apply status-bar flags " +
                "(icons=${flags.hideNotificationIcons}, systemInfo=${flags.hideSystemInfo}, " +
                "clock=${flags.hideClock}, alerts=${flags.silenceAlerts})",
            error,
        )
        service = null
        false
    }

    /** The full set of status-bar wishes plus the bridge state, combined for [start]. */
    private data class Wish(
        val hideIcons: Boolean,
        val hideSystemInfo: Boolean,
        val hideClock: Boolean,
        val silenceAlerts: Boolean,
        val status: ShizukuStatus,
    )

    /**
     * Reflects `IStatusBarService` out from behind a Shizuku binder wrapper. Hidden and non-SDK,
     * which is why the app lifts the hidden-API restriction at startup.
     */
    private fun buildService(): Any {
        val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("statusbar"))
        return Class.forName("com.android.internal.statusbar.IStatusBarService\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)
            ?: error("IStatusBarService.asInterface returned null")
    }

    /**
     * Invokes `disable(int, IBinder, String)`, falling back to the per-user overload on builds that
     * only expose that one.
     */
    private fun Any.disable(flags: Int, packageName: String) {
        val disable = runCatching {
            javaClass.getMethod(
                "disable",
                Int::class.javaPrimitiveType,
                IBinder::class.java,
                String::class.java,
            )
        }.getOrNull()
        if (disable != null) {
            disable.invoke(this, flags, token, packageName)
            return
        }
        javaClass.getMethod(
            "disableForUser",
            Int::class.javaPrimitiveType,
            IBinder::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
        ).invoke(this, flags, token, packageName, android.os.Process.myUserHandle().hashCode())
    }
}
