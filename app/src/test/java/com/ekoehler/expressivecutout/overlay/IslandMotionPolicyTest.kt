package com.ekoehler.expressivecutout.overlay

import com.ekoehler.expressivecutout.data.AnimationBounce
import com.ekoehler.expressivecutout.data.AnimationSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies directional motion profiles and duration scaling before Compose consumes them. */
class IslandMotionPolicyTest {

    @Test
    fun `default duration preserves default spatial stiffness`() {
        val profile = IslandMotionPolicy.profile(
            direction = IslandTransitionDirection.ENTER,
            speed = AnimationSpeed.DEFAULT,
            bounce = AnimationBounce.NORMAL,
            animationDurationMs = 220,
        )

        assertFalse(profile.snap)
        assertEquals(380f, profile.spatialStiffness, 0.01f)
    }

    @Test
    fun `doubling duration quarters spring stiffness`() {
        val profile = IslandMotionPolicy.profile(
            direction = IslandTransitionDirection.ENTER,
            speed = AnimationSpeed.DEFAULT,
            bounce = AnimationBounce.NORMAL,
            animationDurationMs = 440,
        )

        assertEquals(95f, profile.spatialStiffness, 0.01f)
    }

    @Test
    fun `zero duration snaps every transition`() {
        IslandTransitionDirection.entries.forEach { direction ->
            val profile = IslandMotionPolicy.profile(
                direction = direction,
                speed = AnimationSpeed.DEFAULT,
                bounce = AnimationBounce.NORMAL,
                animationDurationMs = 0,
            )
            assertTrue("Expected snap for $direction", profile.snap)
        }
    }

    @Test
    fun `effects are always critically damped`() {
        AnimationBounce.entries.forEach { bounce ->
            val profile = IslandMotionPolicy.profile(
                direction = IslandTransitionDirection.ENTER,
                speed = AnimationSpeed.DEFAULT,
                bounce = bounce,
                animationDurationMs = 220,
            )
            assertEquals(1f, profile.effectDampingRatio, 0f)
        }
    }

    @Test
    fun `exit is more damped than expressive enter`() {
        val enter = IslandMotionPolicy.profile(
            direction = IslandTransitionDirection.ENTER,
            speed = AnimationSpeed.DEFAULT,
            bounce = AnimationBounce.BIG,
            animationDurationMs = 220,
        )
        val exit = IslandMotionPolicy.profile(
            direction = IslandTransitionDirection.EXIT,
            speed = AnimationSpeed.DEFAULT,
            bounce = AnimationBounce.BIG,
            animationDurationMs = 220,
        )

        assertTrue(exit.spatialDampingRatio > enter.spatialDampingRatio)
    }

    @Test
    fun `promotion preserves motion continuity`() {
        val profile = IslandMotionPolicy.profile(
            direction = IslandTransitionDirection.PROMOTE,
            speed = AnimationSpeed.DEFAULT,
            bounce = AnimationBounce.NORMAL,
            animationDurationMs = 220,
        )

        assertTrue(profile.preserveVelocity)
    }

    @Test
    fun `bounce changes spatial damping but not effect damping`() {
        val big = IslandMotionPolicy.profile(
            direction = IslandTransitionDirection.ENTER,
            speed = AnimationSpeed.DEFAULT,
            bounce = AnimationBounce.BIG,
            animationDurationMs = 220,
        )
        val small = IslandMotionPolicy.profile(
            direction = IslandTransitionDirection.ENTER,
            speed = AnimationSpeed.DEFAULT,
            bounce = AnimationBounce.SMALL,
            animationDurationMs = 220,
        )

        assertTrue(big.spatialDampingRatio < small.spatialDampingRatio)
        assertEquals(big.effectDampingRatio, small.effectDampingRatio, 0f)
        assertEquals(big.effectStiffness, small.effectStiffness, 0f)
    }
}
