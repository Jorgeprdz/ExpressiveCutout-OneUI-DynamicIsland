package com.ekoehler.expressivecutout.overlay

import com.ekoehler.expressivecutout.data.AnimationBounce
import com.ekoehler.expressivecutout.data.AnimationSpeed

/** Pure island-motion policy: duration scaling, directional damping, and snap semantics. */
internal object IslandMotionPolicy {
    const val BASE_TRANSITION_MS = 220

    /** Builds physically scaled spring parameters for [direction] without touching Compose state. */
    fun profile(
        direction: IslandTransitionDirection,
        speed: AnimationSpeed,
        bounce: AnimationBounce,
        animationDurationMs: Int,
    ): IslandMotionProfile {
        val baseSpatial = when (speed) {
            AnimationSpeed.SLOW -> 170f
            AnimationSpeed.DEFAULT -> 380f
            AnimationSpeed.FAST -> 800f
        }
        val baseEffect = when (speed) {
            AnimationSpeed.SLOW -> 300f
            AnimationSpeed.DEFAULT -> 700f
            AnimationSpeed.FAST -> 1600f
        }
        val bounceDamping = when (bounce) {
            AnimationBounce.BIG -> 0.45f
            AnimationBounce.NORMAL -> 0.6f
            AnimationBounce.SMALL -> 0.8f
        }
        val snap = animationDurationMs <= 0
        val durationScale = if (snap) 1f else animationDurationMs / BASE_TRANSITION_MS.toFloat()
        val scaleSquared = durationScale * durationScale
        val spatialStiffness = (baseSpatial / scaleSquared).coerceIn(MIN_STIFFNESS, MAX_STIFFNESS)
        val effectStiffness = (baseEffect / scaleSquared).coerceIn(MIN_STIFFNESS, MAX_STIFFNESS)
        val spatialDamping = when (direction) {
            IslandTransitionDirection.ENTER,
            IslandTransitionDirection.EXPAND -> bounceDamping
            IslandTransitionDirection.PROMOTE,
            IslandTransitionDirection.DEMOTE -> maxOf(bounceDamping, 0.7f)
            IslandTransitionDirection.SATELLITE_ENTER -> maxOf(bounceDamping, 0.65f)
            IslandTransitionDirection.COLLAPSE,
            IslandTransitionDirection.SATELLITE_EXIT -> maxOf(bounceDamping, 0.9f)
            IslandTransitionDirection.EXIT -> 1f
        }

        return IslandMotionProfile(
            snap = snap,
            spatialStiffness = spatialStiffness,
            spatialDampingRatio = spatialDamping,
            effectStiffness = effectStiffness,
            effectDampingRatio = 1f,
            preserveVelocity = direction == IslandTransitionDirection.PROMOTE ||
                direction == IslandTransitionDirection.DEMOTE,
        )
    }

    private const val MIN_STIFFNESS = 25f
    private const val MAX_STIFFNESS = 5_000f
}
