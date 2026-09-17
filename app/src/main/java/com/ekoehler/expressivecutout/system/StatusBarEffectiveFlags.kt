package com.ekoehler.expressivecutout.system

/** Composes transient status-bar effects over the user's persistent status-bar wishes. */
internal object StatusBarEffectiveFlags {

    /** Adds transient arrival icon suppression without altering any persistent wish or alert state. */
    fun compose(
        persistent: StatusBarFlagState,
        transientHideStatusIcons: Boolean,
    ): StatusBarFlagState = persistent.copy(
        hideNotificationIcons = persistent.hideNotificationIcons || transientHideStatusIcons,
        hideSystemInfo = persistent.hideSystemInfo || transientHideStatusIcons,
        hideClock = persistent.hideClock || transientHideStatusIcons,
    )
}
