package com.ekoehler.expressivecutout.system

/** Complete user-facing status-bar disable wish before it is converted to platform bit flags. */
internal data class StatusBarFlagState(
    val hideNotificationIcons: Boolean = false,
    val hideSystemInfo: Boolean = false,
    val hideClock: Boolean = false,
    val silenceAlerts: Boolean = false,
)
