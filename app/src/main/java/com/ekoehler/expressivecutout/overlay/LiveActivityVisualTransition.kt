package com.ekoehler.expressivecutout.overlay

/** Identity-level transition between two coordinator slot snapshots. */
internal enum class LiveActivityVisualTransition {
    NONE,
    REVEAL,
    HIDE,
    REPLACE_PRIMARY,
    ADD_SATELLITE,
    REMOVE_SATELLITE,
    PROMOTE_SATELLITE,
    DEMOTE_PRIMARY_TO_SATELLITE,
    EXPAND,
    COLLAPSE,
}
