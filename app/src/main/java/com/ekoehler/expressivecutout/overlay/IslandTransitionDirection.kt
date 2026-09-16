package com.ekoehler.expressivecutout.overlay

/** Direction of one island motion so spatial springs can be tuned asymmetrically. */
internal enum class IslandTransitionDirection {
    ENTER,
    EXPAND,
    COLLAPSE,
    EXIT,
    PROMOTE,
    DEMOTE,
    SATELLITE_ENTER,
    SATELLITE_EXIT,
}
