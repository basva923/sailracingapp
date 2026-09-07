package com.sailracing.app.sensors

import com.sailracing.app.fakes.SchedulerClock
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.model.PositionFix
import com.sailracing.domain.race.RaceEvent
import com.sailracing.simulation.StandardRaceScenario
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedSensorSourceTest {

    private val result = StandardRaceScenario.build()

    @Test
    fun replaysFixesAtTheClocksPaceWithShiftedTimestamps() = runTest {
        val clock = SchedulerClock(this)
        val start = clock.nowMillis()
        val source = SimulatedSensorSource(result, clock, includeActions = false, loop = false)
        val events = source.events().take(5).toList()
        assertEquals(5, events.size)
        assertTrue(events.all { it is RaceEvent.FixReceived })
        val fixes = events.map { (it as RaceEvent.FixReceived).fix }
        assertEquals(start, fixes.first().timestampMillis)
        assertEquals(start + 4_000, fixes.last().timestampMillis)
        // Virtual time advanced to the last fix.
        assertEquals(4_000, testScheduler.currentTime)
    }

    @Test
    fun replaysActionsWhenAskedAndLoops() = runTest {
        val clock = SchedulerClock(this)
        val source = SimulatedSensorSource(result, clock, includeActions = true, loop = true)
        val total = result.timeline.size
        val events = source.events().take(total + 1).toList()
        val actions = events.filter { it !is RaceEvent.FixReceived && it !is RaceEvent.Tick }
        assertTrue(actions.any { it == RaceEvent.MarkPinEnd })
        assertTrue(actions.any { it is RaceEvent.StartCountdown })
        // The countdown event carries the shifted (virtual epoch) time.
        val countdown = actions.first { it is RaceEvent.StartCountdown } as RaceEvent.StartCountdown
        assertTrue(countdown.nowMillis >= 1_700_000_000_000L)
        // After the whole timeline it starts again from the first fix.
        assertTrue(events.last() is RaceEvent.FixReceived)
    }

    @Test
    fun shiftingMovesEveryTimestamp() {
        val fix = PositionFix(GeoPoint(1.0, 2.0), 10, null, null, null)
        assertEquals(RaceEvent.FixReceived(fix.copy(timestampMillis = 110)), RaceEvent.FixReceived(fix).shiftedBy(100))
        assertEquals(RaceEvent.Tick(105), RaceEvent.Tick(5).shiftedBy(100))
        assertEquals(RaceEvent.StartCountdown(5, 101), RaceEvent.StartCountdown(5, 1).shiftedBy(100))
        assertEquals(RaceEvent.SyncCountdown(102), RaceEvent.SyncCountdown(2).shiftedBy(100))
        assertEquals(RaceEvent.MarkPinEnd, RaceEvent.MarkPinEnd.shiftedBy(100))
    }
}
