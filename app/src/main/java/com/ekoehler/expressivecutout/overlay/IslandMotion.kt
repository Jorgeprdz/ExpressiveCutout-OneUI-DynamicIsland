package com.ekoehler.expressivecutout.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import com.ekoehler.expressivecutout.data.AnimationBounce
import com.ekoehler.expressivecutout.data.AnimationSpeed
import com.ekoehler.expressivecutout.data.AnimationStyle
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Builds directional animation specs for the island from one shared physical motion policy.
 * Spatial motion may overshoot on entry/expansion, exits are restrained, and effects remain
 * critically damped. A zero duration setting snaps every transition immediately.
 */
internal class IslandMotion(
    style: AnimationStyle,
    private val speed: AnimationSpeed,
    private val bounce: AnimationBounce,
    private val animationDurationMs: Int,
) {
    private val animScale = animationDurationMs / BASE_TRANSITION_MS.toFloat()
    private val expressive = style == AnimationStyle.EXPRESSIVE
    private val snapMotion = animationDurationMs <= 0

    private fun scaled(baseMs: Int) = (baseMs * animScale).roundToInt().coerceAtLeast(0)

    private fun profile(direction: IslandTransitionDirection): IslandMotionProfile =
        IslandMotionPolicy.profile(direction, speed, bounce, animationDurationMs)

    /** Spatial motion on a scalar fraction, tuned for [direction]. */
    fun float(
        baseMs: Int = BASE_TRANSITION_MS,
        direction: IslandTransitionDirection = IslandTransitionDirection.ENTER,
    ): AnimationSpec<Float> {
        if (snapMotion) return snap()
        if (!expressive) return tween(durationMillis = scaled(baseMs), easing = EaseInOutEasing)
        val p = profile(direction)
        val baseFactor = baseMs / BASE_TRANSITION_MS.toFloat()
        val stiffness = (p.spatialStiffness / (baseFactor * baseFactor)).coerceAtLeast(25f)
        return spring(
            dampingRatio = p.spatialDampingRatio,
            stiffness = stiffness,
            visibilityThreshold = 0.001f,
        )
    }

    /** Spatial motion on sizes, offsets and corner radii. */
    fun dp(direction: IslandTransitionDirection = IslandTransitionDirection.EXPAND): AnimationSpec<Dp> {
        if (snapMotion) return snap()
        if (!expressive) return tween(durationMillis = scaled(BASE_TRANSITION_MS), easing = EaseInOutEasing)
        val p = profile(direction)
        return spring(
            dampingRatio = p.spatialDampingRatio,
            stiffness = p.spatialStiffness,
            visibilityThreshold = Dp.VisibilityThreshold,
        )
    }

    /** Critically damped geometry for continuously changing heights. */
    fun dpSmooth(direction: IslandTransitionDirection = IslandTransitionDirection.EXPAND): AnimationSpec<Dp> {
        if (snapMotion) return snap()
        if (!expressive) return tween(durationMillis = scaled(BASE_TRANSITION_MS), easing = EaseInOutEasing)
        val p = profile(direction)
        return spring(
            dampingRatio = 1f,
            stiffness = p.spatialStiffness,
            visibilityThreshold = Dp.VisibilityThreshold,
        )
    }

    /** Immediate press/release feedback; still obeys duration=0 and spring duration scaling. */
    fun boop(): AnimationSpec<Float> {
        if (snapMotion) return snap()
        if (!expressive) return tween(durationMillis = scaled(140), easing = EaseInOutEasing)
        return spring(
            dampingRatio = 1f,
            stiffness = IslandMotionPolicy.scaleStiffness(boopBaseStiffness(speed), animationDurationMs),
            visibilityThreshold = 0.0005f,
        )
    }

    /** One continuous expanded-tap swell and settle. */
    suspend fun pop(scale: Animatable<Float, AnimationVector1D>, peak: Float) {
        if (snapMotion) {
            scale.snapTo(REST_SCALE)
            return
        }
        if (expressive) {
            val stiffness = IslandMotionPolicy.scaleStiffness(spatialBaseStiffness(speed), animationDurationMs)
            val velocity = (peak - REST_SCALE) * sqrt(stiffness) / POP_PEAK_RATIO
            scale.animateTo(
                targetValue = REST_SCALE,
                animationSpec = spring(
                    dampingRatio = POP_DAMPING,
                    stiffness = stiffness,
                    visibilityThreshold = 0.0005f,
                ),
                initialVelocity = velocity,
            )
        } else {
            scale.animateTo(peak, tween(durationMillis = scaled(80), easing = EaseInOutEasing))
            scale.animateTo(REST_SCALE, tween(durationMillis = scaled(160), easing = EaseInOutEasing))
        }
    }

    /** Arrival pop for a genuinely new visual identity. */
    suspend fun popIn(scale: Animatable<Float, AnimationVector1D>) {
        if (snapMotion) {
            scale.snapTo(REST_SCALE)
            return
        }
        scale.snapTo(POP_IN_START_SCALE)
        scale.animateTo(
            targetValue = REST_SCALE,
            animationSpec = float(
                baseMs = POP_IN_MS,
                direction = IslandTransitionDirection.ENTER,
            ),
        )
    }

    /** Alpha/colour motion is always critically damped. */
    fun fade(direction: IslandTransitionDirection = IslandTransitionDirection.ENTER): AnimationSpec<Float> {
        if (snapMotion) return snap()
        if (!expressive) return tween(durationMillis = scaled(BASE_TRANSITION_MS), easing = EaseInOutEasing)
        val p = profile(direction)
        return spring(
            dampingRatio = p.effectDampingRatio,
            stiffness = p.effectStiffness,
            visibilityThreshold = 0.001f,
        )
    }

    companion object {
        const val BASE_TRANSITION_MS = IslandMotionPolicy.BASE_TRANSITION_MS
        private val EaseInOutEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
        private const val REST_SCALE = 1f
        private const val POP_DAMPING = 0.55f
        private const val POP_PEAK_RATIO = 0.5216f
        private const val POP_IN_START_SCALE = 0.55f
        private const val POP_IN_MS = 200

        private fun spatialBaseStiffness(speed: AnimationSpeed): Float = when (speed) {
            AnimationSpeed.SLOW -> 170f
            AnimationSpeed.DEFAULT -> 380f
            AnimationSpeed.FAST -> 800f
        }

        private fun boopBaseStiffness(speed: AnimationSpeed): Float = when (speed) {
            AnimationSpeed.SLOW -> 400f
            AnimationSpeed.DEFAULT -> 900f
            AnimationSpeed.FAST -> 1800f
        }
    }
}
