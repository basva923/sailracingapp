package com.sailracing.domain.course

import com.sailracing.domain.geo.Angles
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.wind.Tack
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RaceLineFinderTest {

    private val frame = CourseFrame(GeoPoint(51.14, 5.83), windDirectionDegrees = 0.0)

    /** 600 x 600 m of 50 m squares, the start at the bottom middle, the mark at the top middle. */
    private val area = GridSpec(-300.0, 0.0, 12, 12, 50.0)
    private val start = CoursePosition(0.0, 0.0)
    private val mark = CoursePosition(0.0, 600.0)

    /** A wind measured in every square: [shift] degrees off the reference wind, positive = veered. */
    private fun field(spec: GridSpec = area, speedMps: Double = RaceLineFinder.BOAT_SPEED_MPS, shift: (GridCell) -> Double): WindField {
        val points = ArrayList<TrackPoint>()
        for (index in 0 until spec.cellCount) {
            val cell = spec.cellAt(index)
            val centre = spec.center(cell)
            repeat(SAMPLES_PER_CELL) {
                points += TrackPoint(0L, frame.toGeo(centre), speedMps = speedMps, headingDegrees = 0.0, upwindWindDegrees = Angles.normalize(shift(cell)))
            }
        }
        return WindField.build(TrackGrid.build(Track(points, capacity = points.size + 1), frame, spec, mark), referenceDegrees = 0.0)
    }

    private val even = field { 0.0 }

    /**
     * Enough samples in every square that its own wind is what it measured: the wind field blends a
     * square's samples with the water around it, and these fixtures are about the search, not the blend.
     */
    private companion object {
        const val SAMPLES_PER_CELL = 60
    }

    private fun distance(from: CoursePosition, to: CoursePosition) =
        hypot(to.acrossMeters - from.acrossMeters, to.upwindMeters - from.upwindMeters)

    /** What is left to sail to the mark from [at] in a wind of [windDegrees]: the beat, not the straight line. */
    private fun toGo(windDegrees: Double, at: CoursePosition, to: CoursePosition, tackAngleDegrees: Int): Double {
        val bearing = Angles.toDegrees(atan2(to.acrossMeters - at.acrossMeters, to.upwindMeters - at.upwindMeters))
        return distance(at, to) / RaceLineFinder.vmgFactor(Angles.signedDifference(windDegrees, bearing), tackAngleDegrees)
    }

    /**
     * Everything a race line promises: it runs from the boat to the mark, every board but the last is
     * sailed close-hauled in the wind of the one square it crosses, no board leaves the racing area or
     * gives away any of the beat still to sail, and the time is the boards plus the tacks.
     */
    private fun assertSailable(
        field: WindField,
        line: RaceLine,
        tackAngleDegrees: Int,
        from: CoursePosition = start,
        to: CoursePosition = mark,
        speedMps: Double = RaceLineFinder.BOAT_SPEED_MPS,
        tackLossSeconds: Double = RaceLineFinder.TACK_LOSS_SECONDS,
        startTack: Tack? = null,
    ) {
        val spec = field.spec
        assertEquals(from, line.points.first())
        assertEquals(to, line.points.last())
        val boards = line.points.zipWithNext()
        var seconds = 0.0
        var tacks = 0
        // The tack the boat is on before the first board, when it is on one: leaving on the other costs a tack.
        var previous: Boolean? = startTack?.let { it == Tack.STARBOARD }
        for ((index, board) in boards.withIndex()) {
            val (at, next) = board
            assertTrue(next.acrossMeters in spec.leftMeters..spec.rightMeters, "board ends outside the area: $next")
            assertTrue(next.upwindMeters in spec.bottomMeters..spec.topMeters, "board ends outside the area: $next")
            val middle = CoursePosition((at.acrossMeters + next.acrossMeters) / 2, (at.upwindMeters + next.upwindMeters) / 2)
            val bearing = Angles.toDegrees(atan2(next.acrossMeters - at.acrossMeters, next.upwindMeters - at.upwindMeters))
            // The last board can end on the mark itself, leaving nothing of the run in to check.
            if (index == boards.size - 1 && distance(at, next) == 0.0) continue
            val cell = spec.cellOf(middle) ?: spec.nearestCell(middle)
            val wind = field.at(cell).shiftDegrees
            val twa = Angles.signedDifference(wind, bearing)
            // The beat still to sail has to get shorter: a wide-angled boat closing the layline sails a
            // board that adds to the straight-line distance and still gains, which is the rule that counts.
            assertTrue(
                toGo(wind, next, to, tackAngleDegrees) < toGo(wind, at, to, tackAngleDegrees),
                "board $at -> $next gives away part of the beat",
            )
            seconds += distance(at, next) / speedMps
            if (index == boards.size - 1) {
                // The last board is the run in to the mark, from the boundary of its own square, not a beat.
                assertTrue(distance(at, next) <= spec.cellSizeMeters * sqrt(2.0) + 1e-9, "the line joins the mark from outside its square: $at -> $next")
                seconds += distance(at, next) * (1 / RaceLineFinder.vmgFactor(twa, tackAngleDegrees) - 1) / speedMps
                continue
            }
            assertEquals(tackAngleDegrees.toDouble(), abs(twa), 1e-6, "board $at -> $next is not close-hauled")
            val starboard = twa < 0.0
            if (previous != null && previous != starboard) {
                tacks++
                seconds += tackLossSeconds
            }
            previous = starboard
        }
        assertEquals(tacks, line.tacks, "tacks in $line")
        assertEquals(seconds, line.seconds, 1e-6, "seconds in $line")
    }

    @Test
    fun `speed made good along a course`() {
        assertEquals(cos(Math.toRadians(45.0)), RaceLineFinder.vmgFactor(0.0, 45), 1e-9)
        assertEquals(cos(Math.toRadians(45.0)) / cos(Math.toRadians(30.0)), RaceLineFinder.vmgFactor(-30.0, 45), 1e-9)
        assertEquals(1.0, RaceLineFinder.vmgFactor(45.0, 45))
        assertEquals(1.0, RaceLineFinder.vmgFactor(120.0, 45))
        assertEquals(1.0, RaceLineFinder.vmgFactor(-180.0, 45))
    }

    @Test
    fun `in an even wind the line is one board out to the layline and one home`() {
        val line = RaceLineFinder.find(even, start, mark, 45)
        assertEquals(1, line.tacks)
        // The two laylines from a 600 m beat meet 300 m to the side, at the corner of this area.
        val corner = line.points.maxByOrNull { abs(it.acrossMeters) }!!
        assertEquals(300.0, abs(corner.acrossMeters), 1e-6)
        assertEquals(300.0, corner.upwindMeters, 1e-6)
        // 600 m of beat at 45 degrees is 600 * sqrt 2 sailed, plus the one tack.
        assertEquals(600.0 * sqrt(2.0) / RaceLineFinder.BOAT_SPEED_MPS + RaceLineFinder.TACK_LOSS_SECONDS, line.seconds, 1e-6)
        assertSailable(even, line, 45)
    }

    @Test
    fun `the line tacks off the edge of the racing area`() {
        val narrow = GridSpec(-100.0, 0.0, 4, 12, 50.0)
        val line = RaceLineFinder.find(field(narrow) { 0.0 }, start, mark, 45)
        assertEquals(3, line.tacks)
        assertTrue(line.points.any { abs(it.acrossMeters) == 100.0 }, "the line does not touch a wall: $line")
        assertSailable(field(narrow) { 0.0 }, line, 45)
        // Nothing is gained by the detour: the same 600 m beat, three tacks instead of one.
        assertEquals(600.0 * sqrt(2.0) / RaceLineFinder.BOAT_SPEED_MPS + 3 * RaceLineFinder.TACK_LOSS_SECONDS, line.seconds, 1e-6)
    }

    @Test
    fun `the line stays on the side the wind is shifted to, and gets there sooner`() {
        val evenSeconds = RaceLineFinder.find(even, start, mark, 45).seconds

        val veeredRight = field { if (it.column >= area.columns / 2) 20.0 else 0.0 }
        val right = RaceLineFinder.find(veeredRight, start, mark, 45)
        assertTrue(right.points.all { it.acrossMeters >= -1e-9 }, "the line leaves the veered side: $right")
        assertTrue(right.seconds < evenSeconds, "the shift is not used: ${right.seconds} vs $evenSeconds")
        assertSailable(veeredRight, right, 45)

        val backedLeft = field { if (it.column < area.columns / 2) -20.0 else 0.0 }
        val left = RaceLineFinder.find(backedLeft, start, mark, 45)
        assertTrue(left.points.all { it.acrossMeters <= 1e-9 }, "the line leaves the backed side: $left")
        assertTrue(left.seconds < evenSeconds, "the shift is not used: ${left.seconds} vs $evenSeconds")
        assertSailable(backedLeft, left, 45)
    }

    @Test
    fun `a wind exactly on the tack angle lets the boat lay the mark`() {
        val onTheAngle = field { 45.0 }
        val line = RaceLineFinder.find(onTheAngle, start, mark, 45)
        assertEquals(0, line.tacks)
        assertTrue(line.points.all { abs(it.acrossMeters) < 1e-9 }, "the line wanders: $line")
        assertEquals(600.0 / RaceLineFinder.BOAT_SPEED_MPS, line.seconds, 1e-6)
        assertSailable(onTheAngle, line, 45)
    }

    @Test
    fun `tacks cost time, so a costly tack buys a longer board`() {
        val free = RaceLineFinder.find(even, start, mark, 45, tackLossSeconds = 0.0)
        assertEquals(600.0 * sqrt(2.0) / RaceLineFinder.BOAT_SPEED_MPS, free.seconds, 1e-6)
        assertTrue(free.tacks >= 1, "a beat needs at least one tack: $free")
        assertSailable(even, free, 45, tackLossSeconds = 0.0)

        val expensive = RaceLineFinder.find(even, start, mark, 45, tackLossSeconds = 120.0)
        assertEquals(1, expensive.tacks)
        assertEquals(600.0 * sqrt(2.0) / RaceLineFinder.BOAT_SPEED_MPS + 120.0, expensive.seconds, 1e-6)
    }

    @Test
    fun `a boat that points higher gets there sooner`() {
        val high = RaceLineFinder.find(even, start, mark, 30)
        val low = RaceLineFinder.find(even, start, mark, 60)
        assertSailable(even, high, 30)
        assertSailable(even, low, 60)
        assertTrue(high.seconds < RaceLineFinder.find(even, start, mark, 45).seconds)
        assertTrue(low.seconds > RaceLineFinder.find(even, start, mark, 45).seconds)
    }

    @Test
    fun `a faster boat sails the same line in less time`() {
        val slow = RaceLineFinder.find(field(speedMps = 2.0) { 0.0 }, start, mark, 45, tackLossSeconds = 0.0)
        val fast = RaceLineFinder.find(field(speedMps = 6.0) { 0.0 }, start, mark, 45, tackLossSeconds = 0.0)
        assertEquals(slow.points, fast.points, "the same wind gave a different line to a faster boat")
        assertEquals(3.0, slow.seconds / fast.seconds, 1e-9)
    }

    @Test
    fun `a mark that cannot be beaten to gives the straight course to it`() {
        // Abeam: no close-hauled board gains on it, so there is nothing to search.
        val abeam = CoursePosition(250.0, 300.0)
        val reach = RaceLineFinder.find(even, CoursePosition(0.0, 300.0), abeam, 45)
        assertEquals(listOf(CoursePosition(0.0, 300.0), abeam), reach.points)
        assertEquals(0, reach.tacks)
        assertEquals(250.0 / RaceLineFinder.BOAT_SPEED_MPS, reach.seconds, 1e-9)

        // Downwind: the same, the mark is behind the boat.
        val down = RaceLineFinder.find(even, mark, start, 45)
        assertEquals(listOf(mark, start), down.points)
        assertEquals(600.0 / RaceLineFinder.BOAT_SPEED_MPS, down.seconds, 1e-9)

        // Outside the racing area there is no square to cross.
        val outside = CoursePosition(-900.0, -900.0)
        assertEquals(listOf(outside, mark), RaceLineFinder.find(even, outside, mark, 45).points)
    }

    @Test
    fun `inside the mark's own square the line is the last stretch of the beat`() {
        val close = CoursePosition(0.0, 560.0)
        val line = RaceLineFinder.find(even, close, mark, 45)
        assertEquals(listOf(close, mark), line.points)
        assertEquals(0, line.tacks)
        // Dead upwind: 40 m of beating is 40 / cos(45) sailed.
        assertEquals(40.0 / (RaceLineFinder.BOAT_SPEED_MPS * cos(Math.toRadians(45.0))), line.seconds, 1e-9)
    }

    @Test
    fun `whichever tack the boat is on costs nothing in an even wind, because the beat mirrors`() {
        val free = RaceLineFinder.find(even, start, mark, 45)
        val port = RaceLineFinder.find(even, start, mark, 45, startTack = Tack.PORT)
        val starboard = RaceLineFinder.find(even, start, mark, 45, startTack = Tack.STARBOARD)
        assertSailable(even, port, 45)
        assertSailable(even, starboard, 45)
        // Up the middle of the course the two beats are each other's mirror image and cost the same, so the
        // boat sails on from whichever tack it is on rather than tacking onto the other one.
        assertEquals(free.seconds, port.seconds, 1e-9)
        assertEquals(free.seconds, starboard.seconds, 1e-9)
        for ((left, right) in port.points.zip(starboard.points)) {
            assertEquals(-left.acrossMeters, right.acrossMeters, 1e-9, "the two beats are not mirror images")
            assertEquals(left.upwindMeters, right.upwindMeters, 1e-9)
        }
        assertEquals(Tack.PORT, firstTack(port))
        assertEquals(Tack.STARBOARD, firstTack(starboard))
    }

    @Test
    fun `a boat with only one way to go pays for being on the other tack`() {
        // Hard against the left edge of the racing area: the starboard board would sail straight out of it,
        // so the beat can only leave on port and a boat that is on starboard has to tack onto it first.
        val edge = CoursePosition(-299.0, 5.0)
        val onPort = RaceLineFinder.find(even, edge, mark, 45, startTack = Tack.PORT)
        val onStarboard = RaceLineFinder.find(even, edge, mark, 45, startTack = Tack.STARBOARD)
        assertSailable(even, onPort, 45, from = edge, startTack = Tack.PORT)
        assertSailable(even, onStarboard, 45, from = edge, startTack = Tack.STARBOARD)
        assertEquals(Tack.PORT, firstTack(onPort))
        assertEquals(
            onPort.seconds + RaceLineFinder.TACK_LOSS_SECONDS,
            onStarboard.seconds,
            1e-9,
            "the boat on starboard sailed away without paying for the tack it has to make",
        )
        assertEquals(onPort.points, onStarboard.points, "the same beat, one tack later")
        assertEquals(onPort.tacks + 1, onStarboard.tacks)

        // Without a tack given the line is free to leave on whichever tack suits it, and picks the cheaper.
        assertEquals(onPort.seconds, RaceLineFinder.find(even, edge, mark, 45).seconds, 1e-9)
    }

    /** Which tack the first board of a line is sailed on, in a wind that is up the frame on average. */
    private fun firstTack(line: RaceLine): Tack =
        if (line.points[1].acrossMeters < line.points[0].acrossMeters) Tack.STARBOARD else Tack.PORT

    @Test
    fun `sailing a line through the wind it was found in costs exactly what the search said`() {
        val fields = mapOf(
            "even" to even,
            "shifted right" to field { if (it.column >= 6) 12.0 else -4.0 },
            "banded" to field { if ((it.row / 2) % 2 == 0) 10.0 else -10.0 },
            "faster on the left" to field(speedMps = 3.5) { 0.0 },
        )
        for ((what, field) in fields) {
            for (angle in listOf(35, 45, 60)) {
                for (tack in listOf(null, Tack.PORT, Tack.STARBOARD)) {
                    val line = RaceLineFinder.find(field, start, mark, angle, startTack = tack)
                    assertEquals(
                        line.seconds,
                        RaceLineFinder.secondsToSail(field, line, angle, startTack = tack),
                        1e-9,
                        "$what at $angle degrees from $tack",
                    )
                }
            }
        }
    }

    @Test
    fun `a line sailed in a wind that is not the one it was found in costs more`() {
        val backed = field { -12.0 }
        val line = RaceLineFinder.find(backed, start, mark, 45)
        // The same track through a wind veered the other way: what was close-hauled is now pinched or free,
        // and the legs the boat can no longer lay are charged as the beat they have become.
        val veered = field { 12.0 }
        assertTrue(
            RaceLineFinder.secondsToSail(veered, line, 45) > line.seconds + 10.0,
            "a 24 degree header cost nothing: ${RaceLineFinder.secondsToSail(veered, line, 45)} against ${line.seconds}",
        )
        // And in a wind where every square is slower, it simply takes longer in proportion.
        val slow = field(speedMps = 1.5) { -12.0 }
        assertEquals(2.0 * line.seconds, RaceLineFinder.secondsToSail(slow, line, 45) + RaceLineFinder.TACK_LOSS_SECONDS * (2 * line.tacks - line.tacks), 1e-6)
    }

    @Test
    fun `a board is sailed at the speed of the square it crosses`() {
        // The same wind everywhere, but a lane of pressure up the left third of the course.
        val lane = field { 0.0 }
        val pressure = field(speedMps = 4.5) { 0.0 }
        val mixed = object : SailingConditions {
            override val spec: GridSpec get() = area
            override fun shiftDegrees(cell: GridCell): Double = 0.0
            override fun speedMps(cell: GridCell): Double =
                if (cell.column < 4) pressure.speedMps(cell) else lane.speedMps(cell)
        }
        val line = RaceLineFinder.find(mixed, start, mark, 45)
        assertTrue(line.points.any { it.acrossMeters <= -100.0 }, "the line ignored the pressure on the left: $line")
        assertTrue(
            line.seconds < RaceLineFinder.find(lane, start, mark, 45).seconds,
            "sailing through the pressure was not quicker",
        )
    }

    @Test
    fun `a line is empty until there is a boat, and follows it inside its square`() {
        assertTrue(RaceLine.NONE.isEmpty)
        assertEquals(RaceLine.NONE, RaceLine.NONE.anchoredAt(start))

        val line = RaceLineFinder.find(even, start, mark, 45)
        val moved = line.anchoredAt(CoursePosition(10.0, 20.0))
        assertEquals(CoursePosition(10.0, 20.0), moved.points.first())
        assertEquals(line.points.drop(1), moved.points.drop(1))
        assertEquals(line.seconds, moved.seconds)
        assertEquals(line.tacks, moved.tacks)
    }
}
