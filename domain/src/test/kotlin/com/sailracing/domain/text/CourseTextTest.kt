package com.sailracing.domain.text

import com.sailracing.domain.course.CoursePosition
import com.sailracing.domain.course.LineRisk
import com.sailracing.domain.course.RaceLine
import com.sailracing.domain.course.RaceLinePlan
import kotlin.test.Test
import kotlin.test.assertEquals

class CourseTextTest {

    private val points = listOf(CoursePosition(0.0, 0.0), CoursePosition(50.0, 50.0))

    private fun plan(line: RaceLine, bad: Double) =
        RaceLinePlan(safe = line, safeRisk = LineRisk(line.seconds, line.seconds, bad, 1.0), fast = line, runs = 16)

    @Test
    fun `the race line text counts the tacks and the time to the mark`() {
        assertEquals("", CourseText.raceLine(RaceLinePlan.NONE))
        assertEquals(
            "Race line: no tack · 01:00 to the mark, 01:10 on a bad day",
            CourseText.raceLine(plan(RaceLine(points, 60.0, 0), bad = 70.0)),
        )
        assertEquals(
            "Race line: 1 tack · 02:05 to the mark, 02:30 on a bad day",
            CourseText.raceLine(plan(RaceLine(points, 125.0, 1), bad = 150.0)),
        )
        assertEquals(
            "Race line: 3 tacks · 00:30 to the mark, 00:44 on a bad day",
            CourseText.raceLine(plan(RaceLine(points, 30.0, 3), bad = 44.0)),
        )
    }

    @Test
    fun `the flyer text says what the gamble is worth`() {
        assertEquals("", CourseText.flyer(RaceLinePlan.NONE))
        val safe = RaceLine(points, 120.0, 2)
        val fast = RaceLine(listOf(CoursePosition(0.0, 0.0), CoursePosition(-50.0, 50.0)), 115.0, 1)
        // The same line twice: there is nothing to gamble on, and nothing is drawn beside the race line.
        val settled = RaceLinePlan(safe = safe, safeRisk = LineRisk(120.0, 115.0, 126.0, 4.0), fast = safe, runs = 16)
        assertEquals("No flyer: the same line is the fastest of the 16 winds simulated", CourseText.flyer(settled))

        val gamble = settled.copy(fast = fast, fastRisk = LineRisk(115.0, 95.0, 150.0, 20.0), winFraction = 0.375)
        assertEquals(
            "Flyer: 1 tack · 01:55, 01:35 at best · beats the race line in 6 of 16 winds",
            CourseText.flyer(gamble),
        )
    }

    @Test
    fun `the area text says how big it is and how finely it is cut up`() {
        assertEquals(
            "Area 120 m × 240 m, wind up · 20 m squares",
            CourseText.area(120.0, 240.0, 20.0, chosenCellMeters = 20.0),
        )
        assertEquals(
            "Area 120 m × 240 m, wind up · 20 m squares",
            CourseText.area(120.0, 240.0, 20.0, chosenCellMeters = null),
        )
        // A size the sailor chose is honoured, and the map says so when the area was too big to keep it.
        assertEquals(
            "Area 120 m × 240 m, wind up · 20 m squares, enlarged from 10 m to fit",
            CourseText.area(120.0, 240.0, 20.0, chosenCellMeters = 10.0),
        )
    }
}
