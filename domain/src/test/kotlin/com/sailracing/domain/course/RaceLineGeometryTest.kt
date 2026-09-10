package com.sailracing.domain.course

import com.sailracing.domain.geo.Angles
import com.sailracing.domain.geo.GeoPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A second opinion on [RaceLineFinder], written from docs/RACE_LINE.md: the geometry of the beat it draws
 * and the invariants the specification promises. Every expected number here is worked out on paper in the
 * comment beside it, or recomputed from the points of the line itself, never taken from what the finder
 * happened to print.
 */
class RaceLineGeometryTest {

    // ------------------------------------------------------------------ the problems

    /**
     * A wind of [shift] degrees off the reference in every square, positive = veered. These are tests
     * about geometry, so the wind is handed to the search as it is: the same course at twice the size has
     * to come out the same course, whatever a square would have measured.
     */
    private fun windOf(spec: GridSpec, speedMps: Double = RaceLineFinder.BOAT_SPEED_MPS, shift: (GridCell) -> Double): SailingConditions =
        CourseFixtures.Wind(spec, speedMps, shift)

    /** A position given as a fraction of the width and the height of the area. */
    private fun inside(spec: GridSpec, across: Double, upwind: Double): CoursePosition =
        CoursePosition(spec.leftMeters + across * spec.widthMeters, spec.bottomMeters + upwind * spec.heightMeters)

    // ------------------------------------------------------------------ hand geometry

    private fun distance(from: CoursePosition, to: CoursePosition): Double =
        hypot(to.acrossMeters - from.acrossMeters, to.upwindMeters - from.upwindMeters)

    /** The course from [from] to [to] in the wind-up frame: 0 is straight upwind, 90 is across to the right. */
    private fun course(from: CoursePosition, to: CoursePosition): Double =
        Angles.toDegrees(atan2(to.acrossMeters - from.acrossMeters, to.upwindMeters - from.upwindMeters))

    private fun middle(from: CoursePosition, to: CoursePosition): CoursePosition = CoursePosition(
        (from.acrossMeters + to.acrossMeters) / 2,
        (from.upwindMeters + to.upwindMeters) / 2,
    )

    /** Every square of [spec] whose closed side touches [at]: one inside a square, two on a side, four on a corner. */
    private fun squaresAt(spec: GridSpec, at: CoursePosition): List<GridCell> {
        val slack = spec.cellSizeMeters * 1e-6
        val cells = ArrayList<GridCell>(4)
        for (column in 0 until spec.columns) for (row in 0 until spec.rows) {
            val left = spec.leftMeters + column * spec.cellSizeMeters
            val bottom = spec.bottomMeters + row * spec.cellSizeMeters
            val insideAcross = at.acrossMeters >= left - slack && at.acrossMeters <= left + spec.cellSizeMeters + slack
            val insideUpwind = at.upwindMeters >= bottom - slack && at.upwindMeters <= bottom + spec.cellSizeMeters + slack
            if (insideAcross && insideUpwind) cells += GridCell(column, row)
        }
        return cells
    }

    /**
     * How far a course of [courseDegrees] runs from [at] before it leaves [cell]: the shorter of the two
     * distances to the sides it is heading for. A course within a billionth of a metre per metre of a side
     * runs along it and never leaves through it.
     */
    private fun exitDistance(spec: GridSpec, cell: GridCell, at: CoursePosition, courseDegrees: Double): Double {
        val radians = Angles.toRadians(courseDegrees)
        val left = spec.leftMeters + cell.column * spec.cellSizeMeters
        val bottom = spec.bottomMeters + cell.row * spec.cellSizeMeters
        fun side(origin: Double, direction: Double, low: Double, high: Double): Double = when {
            direction > 1e-9 -> (high - origin) / direction
            direction < -1e-9 -> (low - origin) / direction
            else -> Double.POSITIVE_INFINITY
        }
        return min(
            side(at.acrossMeters, sin(radians), left, left + spec.cellSizeMeters),
            side(at.upwindMeters, cos(radians), bottom, bottom + spec.cellSizeMeters),
        )
    }

    /**
     * The time to sail straight from [at] to [to] in a wind of [windDegrees]: at boat speed when that course
     * can be held, and otherwise, inside the no-go zone, at the speed made good beating - the distance made
     * good along the course to the mark divided by boat speed times the cosine of the tack angle.
     */
    private fun runSeconds(
        windDegrees: Double,
        at: CoursePosition,
        to: CoursePosition,
        tackAngleDegrees: Int,
        speedMps: Double,
    ): Double {
        val length = distance(at, to)
        if (length == 0.0) return 0.0
        val twa = abs(Angles.signedDifference(windDegrees, course(at, to)))
        if (twa >= tackAngleDegrees) return length / speedMps
        return length * cos(Angles.toRadians(twa)) / (speedMps * cos(Angles.toRadians(tackAngleDegrees.toDouble())))
    }

    // ------------------------------------------------------------------ the rules of RACE_LINE.md

    /**
     * Every promise the specification makes about a line, checked on the line's own points: it runs from the
     * boat to the mark; every board but the last crosses exactly one square, is sailed at exactly the tack
     * angle to the wind of that square and ends where that course leaves it; no board leaves the racing area
     * or gives away distance to the mark; the last stretch is the straight run in inside the mark's square;
     * the seconds are the boards plus the tacks plus that run in, and the tacks are the changes of tack.
     */
    private fun assertObeysTheRules(
        field: SailingConditions,
        from: CoursePosition,
        to: CoursePosition,
        tackAngleDegrees: Int,
        line: RaceLine,
        speedMps: Double = RaceLineFinder.BOAT_SPEED_MPS,
        tackLossSeconds: Double = RaceLineFinder.TACK_LOSS_SECONDS,
        what: String = "",
    ) {
        val spec = field.spec
        val slack = spec.cellSizeMeters * 1e-6
        assertTrue(line.points.size >= 2, "$what: a line runs from the boat to the mark: $line")
        assertEquals(from, line.points.first(), "$what: the line does not start at the boat")
        assertEquals(to, line.points.last(), "$what: the line does not end at the mark")
        assertTrue(line.seconds >= 0.0 && line.seconds.isFinite(), "$what: nonsense time in $line")

        val legs = line.points.zipWithNext()
        if (legs.size == 1) {
            // Not a beat: the straight course from the boat to the mark, at the speed made good towards it.
            assertEquals(0, line.tacks, "$what: a straight course is sailed without tacking")
            val wind = field.shiftDegrees(spec.nearestCell(from))
            assertEquals(runSeconds(wind, from, to, tackAngleDegrees, speedMps), line.seconds, 1e-6, "$what: the straight course")
            return
        }

        var sailed = 0.0
        var tacks = 0
        var starboard: Boolean? = null
        for (index in 0 until legs.size - 1) {
            val (at, next) = legs[index]
            val length = distance(at, next)
            assertTrue(length > 0.0, "$what: a board of no length at $at")
            assertTrue(
                next.acrossMeters >= spec.leftMeters - slack && next.acrossMeters <= spec.rightMeters + slack &&
                    next.upwindMeters >= spec.bottomMeters - slack && next.upwindMeters <= spec.topMeters + slack,
                "$what: board $at -> $next leaves the racing area $spec",
            )
            // The board ends on a boundary: one of its two coordinates is a whole number of squares.
            val columns = (next.acrossMeters - spec.leftMeters) / spec.cellSizeMeters
            val rows = (next.upwindMeters - spec.bottomMeters) / spec.cellSizeMeters
            assertTrue(
                abs(columns - columns.roundToLong()) < 1e-6 || abs(rows - rows.roundToLong()) < 1e-6,
                "$what: board $at -> $next does not end on the boundary of a square",
            )
            // Exactly one square explains the board: the boat sailed across it at the tack angle to its wind,
            // from one side to the far side, and the square is the one the middle of the board lies in.
            val bearing = course(at, next)
            val touching = squaresAt(spec, middle(at, next)).intersect(squaresAt(spec, at).toSet())
            val crossed = assertNotNull(
                touching.firstOrNull { cell ->
                    val twa = Angles.signedDifference(field.shiftDegrees(cell), bearing)
                    abs(abs(twa) - tackAngleDegrees) < 1e-6 && abs(exitDistance(spec, cell, at, bearing) - length) < 1e-6
                },
                "$what: board $at -> $next (${length}m on $bearing) is not one close-hauled crossing of one square; " +
                    "squares $touching, winds ${touching.map { field.shiftDegrees(it) }}, " +
                    "exits ${touching.map { exitDistance(spec, it, at, bearing) }}",
            )
            // Progress is measured on what is left to sail, not on the straight line to the mark: closing
            // the layline, a boat that tacks through more than a right angle adds to the straight distance
            // and still shortens its beat. (Corrected after the first run: the rule as first written here,
            // and in the spec, was the straight distance, which only says the same thing at 45 degrees.)
            val wind = field.shiftDegrees(crossed)
            assertTrue(
                runSeconds(wind, next, to, tackAngleDegrees, 1.0) < runSeconds(wind, at, to, tackAngleDegrees, 1.0),
                "$what: board $at -> $next gives away part of the beat still to sail",
            )
            val onStarboard = Angles.signedDifference(field.shiftDegrees(crossed), bearing) < 0.0
            if (starboard != null && starboard != onStarboard) tacks++
            starboard = onStarboard
            sailed += length
        }
        // The run in: what is left inside the mark's own square once a board has reached its boundary.
        val (last, mark) = legs.last()
        val runIn = distance(last, mark)
        val finishing = squaresAt(spec, middle(last, mark)).intersect(squaresAt(spec, last).toSet())
            .ifEmpty { squaresAt(spec, last).toSet() }
        if (spec.cellOf(to) != null) {
            assertTrue(
                finishing.any { it in squaresAt(spec, to) },
                "$what: the line joins the mark from outside the mark's own square: $last -> $mark",
            )
            assertTrue(runIn <= spec.cellSizeMeters * sqrt(2.0) + slack, "$what: the run in is longer than a square: $runIn")
        }
        // A run in the boat can lay is one course on one tack, and costs a tack of its own when that is not
        // the tack the last board arrived on. (Corrected after the first run: the tacks and the seconds both
        // count it, and they did not here.)
        val totals = finishing.map { square ->
            val wind = field.shiftDegrees(square)
            val twa = Angles.signedDifference(wind, course(last, mark))
            val tackedIn = runIn > 0.0 && abs(twa) >= tackAngleDegrees - 1e-9 && (twa < 0.0) != starboard
            val all = tacks + if (tackedIn) 1 else 0
            all to sailed / speedMps + all * tackLossSeconds + runSeconds(wind, last, mark, tackAngleDegrees, speedMps)
        }
        assertTrue(
            totals.any { abs(it.second - line.seconds) < 1e-6 && it.first == line.tacks },
            "$what: the seconds do not add up: ${line.seconds} in ${line.tacks} tacks is none of $totals " +
                "($sailed m of boards, $tacks tacks before the run in, $runIn m of run in)",
        )
    }

    // ------------------------------------------------------------------ the tests

    @Test
    fun `a board ends where its course leaves the square, and not before`() {
        // 600 x 600 m of 50 m squares in a wind veered 45 degrees: with a tack angle of 45 the starboard
        // course is straight up the page and the port course is dead across, which gains nothing on a mark
        // dead upwind. So the whole line is forced: straight up the middle of one column of squares.
        val area = GridSpec(-300.0, 0.0, 12, 12, 50.0)
        val field = windOf(area) { 45.0 }
        val boat = CoursePosition(25.0, 10.0)
        val mark = CoursePosition(25.0, 600.0)

        val line = RaceLineFinder.find(field, boat, mark, 45)

        // 40 m to the first boundary, then a boundary every 50 m, and the last 50 m as the run in.
        val expected = listOf(boat) + (1..11).map { CoursePosition(25.0, it * 50.0) } + mark
        assertEquals(expected.size, line.points.size, "the line does not stop at every boundary it crosses: $line")
        for ((want, got) in expected.zip(line.points)) {
            assertEquals(want.acrossMeters, got.acrossMeters, 1e-6, "in $line")
            assertEquals(want.upwindMeters, got.upwindMeters, 1e-6, "in $line")
        }
        assertEquals(0, line.tacks)
        // 590 m sailed at the tack angle on one tack: no tack loss, and the run in is not inside the no-go zone.
        assertEquals(590.0 / RaceLineFinder.BOAT_SPEED_MPS, line.seconds, 1e-6)
        assertObeysTheRules(field, boat, mark, 45, line)
    }

    @Test
    fun `in an even wind the line is one board out to the layline and one home, whatever the tack angle`() {
        // 600 x 400 m. The two laylines of a 400 m beat cross halfway up it, 200 * tan(angle) to the side,
        // which is inside this area for every tack angle up to 45 degrees.
        val area = GridSpec(-300.0, 0.0, 12, 8, 50.0)
        val even = windOf(area) { 0.0 }
        val boat = CoursePosition(0.0, 0.0)
        val mark = CoursePosition(0.0, 400.0)
        for (angle in listOf(20, 30, 40, 45)) {
            val line = RaceLineFinder.find(even, boat, mark, angle)
            assertObeysTheRules(even, boat, mark, angle, line, what = "tack angle $angle")
            assertEquals(1, line.tacks, "a beat with room for its laylines is one tack: $line")
            // Every metre sailed at the tack angle gains cos(angle) of the 400 m to windward, plus one tack.
            val expected = 400.0 / (cos(Angles.toRadians(angle.toDouble())) * RaceLineFinder.BOAT_SPEED_MPS) +
                RaceLineFinder.TACK_LOSS_SECONDS
            assertEquals(expected, line.seconds, 1e-6, "tack angle $angle: $line")
            // Where the tack falls is not pinned down: the run in is charged at the speed made good, which is
            // the same rate as beating, so every one-tack line up this beat costs exactly the same.
            assertTrue(
                line.points.all { abs(it.acrossMeters) <= 200.0 * tan(Angles.toRadians(angle.toDouble())) + 1e-6 },
                "tack angle $angle: the line sails past the layline crossing: $line",
            )
        }
    }

    // DISAGREEMENT: see the report. Rule 3 says a board "can be held until tacking would lay the mark, and
    // not one metre further", but it is implemented as "the end of the board must be closer to the mark than
    // its start", and those two are only the same rule at a tack angle of exactly 45 degrees. The layline is
    // where the mark bears 2 * tack angle off the course; the distance to the mark stops falling where it
    // bears 90. So a boat that tacks through more than a right angle - every tack angle from 46 to 80, all
    // of them legal in WindSettings.TACK_ANGLE_RANGE - is stopped short of its own layline and can never lay
    // the mark, however much room it has. The beat then falls apart into a staircase of extra tacks.
    @Test
    fun `a boat that tacks through more than a right angle still lays the mark in an even wind`() {
        // The laylines of this 400 m beat lie 200 * tan(tack angle) to the side: 549 m at 70 degrees, so
        // the area has to be 1200 m wide for the widest boat here to reach them. (Corrected after the first
        // run: at 800 m wide the boat hits the side of the area at 70 degrees and is forced into an extra
        // tack, which is right, and nothing to do with the layline this test is about.)
        val area = GridSpec(-600.0, 0.0, 24, 8, 50.0)
        val even = windOf(area) { 0.0 }
        val boat = CoursePosition(0.0, 0.0)
        val mark = CoursePosition(0.0, 400.0)
        val beats = listOf(50, 55, 60, 70).map { angle ->
            val line = RaceLineFinder.find(even, boat, mark, angle)
            assertObeysTheRules(even, boat, mark, angle, line, what = "tack angle $angle")
            Triple(
                angle,
                line,
                400.0 / (cos(Angles.toRadians(angle.toDouble())) * RaceLineFinder.BOAT_SPEED_MPS) + RaceLineFinder.TACK_LOSS_SECONDS,
            )
        }
        val summary = beats.joinToString("; ") { (angle, line, wanted) ->
            "$angle deg: ${line.tacks} tacks in ${line.seconds} s, where one tack in $wanted s would do"
        }
        for ((angle, line, wanted) in beats) {
            assertEquals(1, line.tacks, "a tack angle of $angle never reaches its layline - $summary")
            assertEquals(wanted, line.seconds, 1e-6, "a tack angle of $angle never reaches its layline - $summary")
        }
    }

    @Test
    fun `a mark on the corner of four squares is laid at the corner itself`() {
        // 200 x 200 m of 50 m squares, the mark on the corner shared by four of them, 100 m dead upwind of
        // the boat. One board from (0,0) to (50,50) and one from there to the mark: 100 * sqrt 2 sailed.
        // (Rule 5 read to the letter would let the boat stop at (50,50) - which is on the boundary of the
        // mark's square - and be handed the last 70 m without paying for the tack it has to make there.)
        val area = GridSpec(-100.0, 0.0, 4, 4, 50.0)
        val even = windOf(area) { 0.0 }
        val boat = CoursePosition(0.0, 0.0)
        val mark = CoursePosition(0.0, 100.0)

        val line = RaceLineFinder.find(even, boat, mark, 45)

        assertObeysTheRules(even, boat, mark, 45, line)
        assertEquals(1, line.tacks)
        assertEquals(
            100.0 * sqrt(2.0) / RaceLineFinder.BOAT_SPEED_MPS + RaceLineFinder.TACK_LOSS_SECONDS,
            line.seconds,
            1e-6,
        )
        // The last board ends on the mark, so nothing is left of the run in and the mark is that board's end
        // rather than a point of its own.
        assertEquals(3, line.points.size, "in $line")
        assertEquals(0.0, distance(line.points.last(), mark), 1e-6, "in $line")
        assertEquals(50.0, abs(line.points[1].acrossMeters), 1e-6, "the tack is at the layline crossing: $line")
        assertEquals(50.0, line.points[1].upwindMeters, 1e-6, "the tack is at the layline crossing: $line")
    }

    @Test
    fun `a racing area of one square is never a beat`() {
        // The boat is inside the mark's own square, so there is nothing to search: the straight run in.
        val area = GridSpec(0.0, 0.0, 1, 1, 50.0)
        val even = windOf(area) { 0.0 }
        val boat = CoursePosition(10.0, 10.0)
        val mark = CoursePosition(40.0, 45.0)

        val line = RaceLineFinder.find(even, boat, mark, 45)

        assertEquals(listOf(boat, mark), line.points)
        assertEquals(0, line.tacks)
        // 30 m across and 35 m up is 40.6 degrees off the wind: inside the no-go zone, so it is charged as
        // 35 m made good to windward at the speed made good beating.
        assertEquals(35.0 / (RaceLineFinder.BOAT_SPEED_MPS * cos(Angles.toRadians(45.0))), line.seconds, 1e-6)
        assertObeysTheRules(even, boat, mark, 45, line)
    }

    @Test
    fun `a mark above the racing area is beaten to through the nearest square`() {
        // The mark is 150 m above the top edge, so the search aims at the square nearest to it and the run
        // in leaves the area. Nothing else changes: the boards themselves stay inside it.
        val area = GridSpec(-300.0, 0.0, 12, 12, 50.0)
        val even = windOf(area) { 0.0 }
        val boat = CoursePosition(0.0, 0.0)
        val mark = CoursePosition(0.0, 750.0)

        val line = RaceLineFinder.find(even, boat, mark, 45)

        assertObeysTheRules(even, boat, mark, 45, line)
        assertTrue(line.points.size > 2, "the beat was given up on: $line")
        for (point in line.points.dropLast(1)) {
            assertTrue(point.acrossMeters in -300.0..300.0 && point.upwindMeters in 0.0..600.0, "$point is outside the area")
        }
        // Nothing gains on the mark faster than the speed made good to windward, so 750 m of beating cannot
        // be done in less than 750 / (3 * cos 45) seconds, whatever route is taken.
        val floor = 750.0 / (RaceLineFinder.BOAT_SPEED_MPS * cos(Angles.toRadians(45.0)))
        assertTrue(line.seconds >= floor - 1e-6, "${line.seconds} beats the speed made good bound $floor")
        assertTrue(line.tacks >= 1, "the mark is dead upwind, so the line has to tack: $line")
        // The last point before the mark is on the boundary of the square nearest to it.
        assertTrue(
            area.nearestCell(mark) in squaresAt(area, line.points[line.points.size - 2]),
            "the line leaves for the mark from ${line.points[line.points.size - 2]}, not from the square nearest it",
        )
    }

    @Test
    fun `a boat on the corner of four squares still starts its line there`() {
        val area = GridSpec(-300.0, 0.0, 12, 12, 50.0)
        val field = windOf(area) { if (it.column < 6) -15.0 else 10.0 }
        val boat = CoursePosition(0.0, 300.0)
        val mark = CoursePosition(-25.0, 575.0)

        val line = RaceLineFinder.find(field, boat, mark, 45)

        assertEquals(boat, line.points.first())
        assertObeysTheRules(field, boat, mark, 45, line, what = "from a corner")
        assertEquals(line, RaceLineFinder.find(field, boat, mark, 45), "the same problem gave two different lines")
    }

    @Test
    fun `mirroring the wind and the course mirrors the line and takes exactly as long`() {
        // The area is symmetric about the axis of the course, so a wind field reflected in it, with every
        // shift reversed, is the same problem seen in a mirror: the same beat, sailed the other way round.
        val area = GridSpec(-300.0, 0.0, 12, 12, 50.0)
        fun shift(cell: GridCell) = when {
            cell.column < 4 -> -25.0
            cell.column < 8 -> 5.0
            else -> 20.0
        } + 2.0 * cell.row
        val field = windOf(area) { shift(it) }
        val mirrored = windOf(area) { -shift(GridCell(area.columns - 1 - it.column, it.row)) }
        fun mirror(at: CoursePosition) = CoursePosition(-at.acrossMeters, at.upwindMeters)
        val boat = CoursePosition(-115.0, 15.0)
        val mark = CoursePosition(65.0, 585.0)

        val line = RaceLineFinder.find(field, boat, mark, 45)
        val other = RaceLineFinder.find(mirrored, mirror(boat), mirror(mark), 45)

        assertObeysTheRules(field, boat, mark, 45, line, what = "the beat")
        assertObeysTheRules(mirrored, mirror(boat), mirror(mark), 45, other, what = "its mirror image")
        assertEquals(
            line.seconds,
            other.seconds,
            1e-9,
            "a mirrored beat takes a different time: ${line.tacks} tacks in $line, mirrored ${other.tacks} tacks in $other",
        )
        assertEquals(line.tacks, other.tacks, "a mirrored beat has a different number of tacks")
        assertEquals(line.points.size, other.points.size, "a mirrored beat has a different number of boards")
        for ((want, got) in line.points.map(::mirror).zip(other.points)) {
            assertEquals(want.acrossMeters, got.acrossMeters, 1e-6, "$line mirrored is not $other")
            assertEquals(want.upwindMeters, got.upwindMeters, 1e-6, "$line mirrored is not $other")
        }
    }

    @Test
    fun `turning the whole course through a right angle changes nothing`() {
        // The frame is wind up, so a square area with every wind veered by 90 degrees and every position
        // turned with it is the same problem drawn on its side, and it must come out the same.
        val area = GridSpec(-200.0, -200.0, 8, 8, 50.0)
        fun shift(cell: GridCell) = -20.0 + 6.0 * cell.column + 2.0 * cell.row
        val field = windOf(area) { shift(it) }
        val turnedField = windOf(area) { shift(GridCell(area.rows - 1 - it.row, it.column)) + 90.0 }
        fun turn(at: CoursePosition) = CoursePosition(at.upwindMeters, -at.acrossMeters)
        val boat = CoursePosition(-25.0, -175.0)
        val mark = CoursePosition(25.0, 175.0)

        val line = RaceLineFinder.find(field, boat, mark, 40)
        val turned = RaceLineFinder.find(turnedField, turn(boat), turn(mark), 40)

        assertObeysTheRules(field, boat, mark, 40, line, what = "the beat")
        assertObeysTheRules(turnedField, turn(boat), turn(mark), 40, turned, what = "the beat on its side")
        assertEquals(line.seconds, turned.seconds, 1e-6, "the same beat takes a different time on its side")
        assertEquals(line.tacks, turned.tacks)
        assertEquals(line.points.size, turned.points.size)
        for ((want, got) in line.points.map(::turn).zip(turned.points)) {
            assertEquals(want.acrossMeters, got.acrossMeters, 1e-6, "$line turned is not $turned")
            assertEquals(want.upwindMeters, got.upwindMeters, 1e-6, "$line turned is not $turned")
        }
    }

    @Test
    fun `the same beat at twice the size takes twice as long`() {
        // Metres only enter the model through the cost of a board, so doubling every distance and the cost
        // of a tack with them is the same race, sailed twice as far.
        fun shift(cell: GridCell) = -18.0 + 5.0 * cell.column - 3.0 * cell.row
        val small = GridSpec(-300.0, 0.0, 12, 12, 50.0)
        val large = GridSpec(-600.0, 0.0, 12, 12, 100.0)
        val boat = CoursePosition(10.0, 25.0)
        val mark = CoursePosition(-40.0, 575.0)
        fun twice(at: CoursePosition) = CoursePosition(at.acrossMeters * 2, at.upwindMeters * 2)

        val line = RaceLineFinder.find(windOf(small) { shift(it) }, boat, mark, 45)
        val big = RaceLineFinder.find(
            windOf(large) { shift(it) },
            twice(boat),
            twice(mark),
            45,
            tackLossSeconds = RaceLineFinder.TACK_LOSS_SECONDS * 2,
        )

        assertObeysTheRules(windOf(small) { shift(it) }, boat, mark, 45, line, what = "the small course")
        assertEquals(2 * line.seconds, big.seconds, 1e-6)
        assertEquals(line.tacks, big.tacks)
        assertEquals(line.points.size, big.points.size)
        for ((want, got) in line.points.map(::twice).zip(big.points)) {
            assertEquals(want.acrossMeters, got.acrossMeters, 1e-6, "$line doubled is not $big")
            assertEquals(want.upwindMeters, got.upwindMeters, 1e-6, "$line doubled is not $big")
        }
    }

    @Test
    fun `only the metres a tack costs decide the line, and speed only scales the clock`() {
        // A tack costs boat speed times tack loss in metres. Keep that product and the fastest line cannot
        // change; double the boat speed and the same line simply takes half the time.
        val area = GridSpec(-300.0, 0.0, 12, 12, 50.0)
        val shift = { cell: GridCell -> if (cell.row < 6) 12.0 else -12.0 }
        val field = windOf(area, speedMps = 3.0, shift = shift)
        val quicker = windOf(area, speedMps = 6.0, shift = shift)
        val boat = CoursePosition(-35.0, 5.0)
        val mark = CoursePosition(45.0, 595.0)

        val slow = RaceLineFinder.find(field, boat, mark, 45, tackLossSeconds = 3.6)
        val fast = RaceLineFinder.find(quicker, boat, mark, 45, tackLossSeconds = 1.8)

        assertObeysTheRules(field, boat, mark, 45, slow, speedMps = 3.0, tackLossSeconds = 3.6, what = "the slow boat")
        assertObeysTheRules(quicker, boat, mark, 45, fast, speedMps = 6.0, tackLossSeconds = 1.8, what = "the fast boat")
        assertEquals(slow.points, fast.points, "the same course gave a different line to a faster boat")
        assertEquals(slow.tacks, fast.tacks)
        assertEquals(slow.seconds / 2, fast.seconds, 1e-9)
    }

    @Test
    fun `every line keeps the rules, over many winds, areas, positions and tack angles`() {
        val areas = listOf(
            GridSpec(-300.0, 0.0, 12, 12, 50.0),
            GridSpec(-100.0, 0.0, 4, 12, 50.0),
            GridSpec(0.0, 0.0, 6, 5, 10.0),
            GridSpec(-75.0, -25.0, 5, 7, 25.0),
            GridSpec(-400.0, 100.0, 8, 8, 100.0),
        )
        val winds: List<Pair<String, (GridSpec, GridCell) -> Double>> = listOf(
            "an even wind" to { _: GridSpec, _: GridCell -> 0.0 },
            "a right hand shift" to { spec: GridSpec, cell: GridCell -> if (cell.column * 2 >= spec.columns) 25.0 else -5.0 },
            "a gradient across the course" to { spec: GridSpec, cell: GridCell -> -30.0 + 60.0 * cell.column / spec.columns },
            "a gradient up the beat" to { spec: GridSpec, cell: GridCell -> 20.0 - 40.0 * cell.row / spec.rows },
            "a chequerboard" to { _: GridSpec, cell: GridCell -> if ((cell.column + cell.row) % 2 == 0) 14.0 else -18.0 },
            "a scattered wind" to { _: GridSpec, cell: GridCell -> ((cell.column * 37 + cell.row * 17) % 9 - 4) * 10.0 },
        )
        val boats = listOf(0.15 to 0.05, 0.5 to 0.02, 0.85 to 0.12, 0.5 to 0.35)
        val marks = listOf(0.5 to 0.99, 0.22 to 0.9, 0.9 to 0.72)
        val angles = listOf(20, 30, 40, 45, 50, 60, 70, 80)

        var n = 0
        for (area in areas) {
            for ((name, wind) in winds) {
                val field = windOf(area) { wind(area, it) }
                repeat(3) {
                    val boat = inside(area, boats[n % boats.size].first, boats[n % boats.size].second)
                    val mark = inside(area, marks[n % marks.size].first, marks[n % marks.size].second)
                    val angle = angles[n % angles.size]
                    n++
                    val line = RaceLineFinder.find(field, boat, mark, angle)
                    assertObeysTheRules(
                        field, boat, mark, angle, line,
                        what = "$name over $area, boat $boat, mark $mark, tack angle $angle",
                    )
                }
            }
        }
        assertEquals(90, n, "the sweep did not run every case")
    }
}
