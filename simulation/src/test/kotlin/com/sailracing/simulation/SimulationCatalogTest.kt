package com.sailracing.simulation

import com.sailracing.domain.race.RaceEvent
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SimulationCatalogTest {

    @Test
    fun `ten practice sessions are offered beside the full race`() {
        assertEquals(10, SimulationCatalog.practice.size)
        assertEquals(listOf(SimulationCatalog.fullRace) + SimulationCatalog.practice, SimulationCatalog.all)
        assertSame(SimulationCatalog.fullRace, SimulationCatalog.default)
        assertEquals(SimulationCatalog.all.size, SimulationCatalog.all.map { it.id }.distinct().size)
        assertEquals(SimulationCatalog.all.size, SimulationCatalog.all.map { it.title }.distinct().size)
        SimulationCatalog.all.forEach { scenario ->
            assertTrue(scenario.title.contains("kn"), "the title says the wind strength: ${scenario.title}")
            assertTrue(scenario.detail.isNotBlank(), "${scenario.id} says what to look for")
            assertEquals("SimulationScenario(${scenario.id})", scenario.toString())
        }
    }

    @Test
    fun `a stored choice is found again and anything else falls back to the default`() {
        SimulationCatalog.all.forEach { assertSame(it, SimulationCatalog.byId(it.id)) }
        assertSame(SimulationCatalog.default, SimulationCatalog.byId(null))
        assertSame(SimulationCatalog.default, SimulationCatalog.byId("a simulation from a later version"))
    }

    @Test
    fun `the wind of each simulation is the one its title describes`() {
        SimulationCatalog.practice.forEach { scenario ->
            val wind = scenario.wind
            val direction = wind.meanDirectionDegrees.roundToInt().toString().padStart(3, '0')
            assertTrue(scenario.title.contains("$direction°"), "${scenario.title} names its direction")
            assertTrue(scenario.title.contains("${wind.meanSpeedKnots.roundToInt()} kn"), "${scenario.title} names its strength")
        }
        // The four that are about a side of the course rather than about time.
        assertTrue(SimulationCatalog.byId("veering").wind.veerDegreesPerHour > 0.0)
        assertTrue(SimulationCatalog.byId("backing").wind.veerDegreesPerHour < 0.0)
        assertTrue(SimulationCatalog.byId("shore-right").wind.shearDegreesPerMeter > 0.0)
        assertTrue(SimulationCatalog.byId("pressure-left").wind.speedShearKnotsPerMeter < 0.0)
        // Pressure on the left means what the title says at the edges of the racing area.
        val pressure = SimulationCatalog.byId("pressure-left").wind
        assertEquals(15.0, pressure.speedKnotsAt(0.0, acrossMeters = -200.0), 1e-9)
        assertEquals(9.0, pressure.speedKnotsAt(0.0, acrossMeters = 200.0), 1e-9)
        // And the sea breeze really does build from 7 knots to about 16 over the hour.
        val building = SimulationCatalog.byId("building-sea-breeze").wind
        assertEquals(7.0, building.speedKnotsAt(0.0), 1e-9)
        assertEquals(16.0, building.speedKnotsAt(3600.0), 1e-9)
    }

    @Test
    fun `every practice session sails its five laps and then starts`() {
        SimulationCatalog.practice.forEach { scenario ->
            val result = scenario.build()
            for (lap in 1..PracticeScenario.DEFAULT_LAPS) {
                assertTrue(result.milestones.containsKey(PracticeScenario.beatLeg(lap)), "${scenario.id} sails lap $lap")
                assertTrue(result.milestones.containsKey(PracticeScenario.runLeg(lap)), "${scenario.id} runs lap $lap")
            }
            assertTrue(
                result.milestone(PracticeScenario.runLeg(PracticeScenario.DEFAULT_LAPS)) < result.milestone(PracticeScenario.START),
                "${scenario.id} laps before it starts",
            )
            assertEquals(RaceEvent.StopTimer, result.actions().last().event, "${scenario.id} stops the timer")
            assertTrue(result.durationMillis < 2 * 3600_000L, "${scenario.id} took ${result.durationMillis / 60_000} min")
            // The boat sails the whole racing area, both corners included.
            val across = result.truth.map { result.course.acrossMeters(it.boat.position) }
            assertTrue(across.min() < -150.0 && across.max() > 150.0, "${scenario.id} sailed ${across.min()}..${across.max()}")
        }
    }

    @Test
    fun `the full race is the scripted one`() {
        val result = SimulationCatalog.fullRace.build()
        assertTrue(result.milestones.containsKey(StandardRaceScenario.FINISH))
        assertEquals(StandardRaceScenario.Config().wind, SimulationCatalog.fullRace.wind)
    }
}
