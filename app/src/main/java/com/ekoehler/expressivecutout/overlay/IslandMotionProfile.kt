package com.ekoehler.expressivecutout.overlay

/** Pure spring/effect parameters consumed by IslandMotion for one transition direction. */
internal data class IslandMotionProfile(
    val snap: Boolean,
    val spatialStiffness: Float,
    val spatialDampingRatio: Float,
    val effectStiffness: Float,
    val effectDampingRatio: Float,
    val preserveVelocity: Boolean,
)
