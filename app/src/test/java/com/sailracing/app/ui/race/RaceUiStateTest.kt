package com.sailracing.app.ui.race

import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.model.PositionFix
import com.sailracing.domain.race.RaceEngine
import com.sailracing.domain.race.RaceEvent
import com.sailracing.domain.wind.Tack
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RaceUiStateTest {

    private fun engineHeading(heading: Double, speed: Double = 3.0): RaceEngine {
        val engine = RaceEngine()
        engine.dispatch(RaceEvent.SetWindDirection(0))
        engine.dispatch(RaceEvent.FixReceived(PositionFix(GeoPoint(51.14, 5.83), 1_000, speed, heading, 3.0)))
        return engine
    }

    @Test
    fun emptyState() {
        val state = RaceUiState.from(RaceEngine().snapshot(0))
        assertNull(state.headingDegrees)
        assertEquals("---", state.heading)
        assertEquals("---", state.target)
        assertEquals("---", state.speed)
        assertEquals("---", state.vmg)
        assertEquals(Steer.NONE, state.steer)
        assertEquals("", state.steerText)
        assertEquals("", state.raceTime)
        assertEquals("---", state.averageUpwind)
        assertFalse(state.canSetFromHeading)
        assertEquals(315.0, state.targets.starboardUpwind)
    }

    @Test
    fun onTargetAndSteeringHints() {
        val onTarget = RaceUiState.from(engineHeading(317.0).snapshot(1_000))
        assertEquals(Steer.ON_TARGET, onTarget.steer)
        assertEquals("On target", onTarget.steerText)
        assertEquals("317°", onTarget.heading)
        assertEquals("315°", onTarget.target)
        assertEquals(Tack.STARBOARD, onTarget.tack)
        assertEquals("Starboard upwind", onTarget.tackLabel)
        assertEquals("5.8", onTarget.speed)
        assertEquals("+2°", onTarget.shift)
        assertTrue(onTarget.canSetFromHeading)
        assertEquals("5.8 kn", onTarget.averageUpwind)
        assertEquals("---", onTarget.averageDownwind)

        // Starboard upwind, heading 325 is 10 degrees right of 315 = towards the wind: bear away.
        val pinching = RaceUiState.from(engineHeading(325.0).snapshot(1_000))
        assertEquals(Steer.BEAR_AWAY, pinching.steer)
        assertEquals("Bear away 10°", pinching.steerText)

        // Starboard upwind, heading 305 is left of the target = away from the wind: head up.
        val footing = RaceUiState.from(engineHeading(305.0).snapshot(1_000))
        assertEquals(Steer.HEAD_UP, footing.steer)
        assertEquals("Head up 10°", footing.steerText)

        // Port upwind (target 45): heading 55 is right = away from the wind: head up; 35 is towards: bear away.
        assertEquals(Steer.HEAD_UP, RaceUiState.from(engineHeading(55.0).snapshot(1_000)).steer)
        assertEquals(Steer.BEAR_AWAY, RaceUiState.from(engineHeading(35.0).snapshot(1_000)).steer)

        // Starboard downwind (target 220): heading 230 is right = further from the wind: bear away; 210: head up.
        assertEquals(Steer.BEAR_AWAY, RaceUiState.from(engineHeading(230.0).snapshot(1_000)).steer)
        assertEquals(Steer.HEAD_UP, RaceUiState.from(engineHeading(210.0).snapshot(1_000)).steer)
        // Port downwind (target 140): heading 130 is left = further from the wind: bear away; 150: head up.
        assertEquals(Steer.BEAR_AWAY, RaceUiState.from(engineHeading(130.0).snapshot(1_000)).steer)
        assertEquals(Steer.HEAD_UP, RaceUiState.from(engineHeading(150.0).snapshot(1_000)).steer)
    }

    @Test
    fun raceTimeFollowsThePhase() {
        val engine = engineHeading(315.0)
        engine.dispatch(RaceEvent.StartCountdown(1, nowMillis = 1_000))
        assertEquals("Start in 00:50", RaceUiState.from(engine.snapshot(11_000)).raceTime)
        assertEquals("Racing +00:05", RaceUiState.from(engine.snapshot(66_000)).raceTime)
        val downwind = engineHeading(220.0)
        val state = RaceUiState.from(downwind.snapshot(1_000))
        assertEquals("5.8 kn", state.averageDownwind)
        assertEquals("---", state.averageUpwindVmg)
        assertTrue(state.averageDownwindVmg.endsWith("kn"))
    }
}
