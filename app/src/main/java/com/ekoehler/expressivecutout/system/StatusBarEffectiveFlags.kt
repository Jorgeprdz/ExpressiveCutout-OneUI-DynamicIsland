package com.ekoehler.expressivecutout.system

/** Composes transient status-bar effects over the user's persistent status-bar wishes. */
internal object StatusBarEffectiveFlags {

    /** Adds transient notification-icon suppression without altering any persistent wish. */
    fun compose(
        persistent: StatusBarFlagState,
        transientHideNotificationIcons: Boolean,
    ): StatusBarFlagState = persistent.copy(
        hideNotificationIcons = persistent.hideNotificationIcons || transientHideNotificationIcons,
    )
}
