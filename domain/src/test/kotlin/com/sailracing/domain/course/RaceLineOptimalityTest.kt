package com.sailracing.domain.course

import com.sailracing.domain.geo.Angles
import com.sailracing.domain.geo.GeoPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A second opinion on [RaceLineFinder]: not "can this line be sailed" but "is it really the fastest one".
 *
 * The line is checked against [ExhaustiveBeat], an enumeration of every legal beat written from the rules
 * in docs/RACE_LINE.md and nothing else - no frontier, no lattice, no state de-duplication - on areas
 * small enough to exhaust. Where the fastest line can be worked out on the back of an envelope instead (a
 * dead upwind mark in an even wind, a channel too narrow to lay it, a wind exactly on the tack angle) it
 * is checked against that.
 */
class RaceLineOptimalityTest {

    private val frame = CourseFrame(GeoPoint(51.14, 5.83), windDirectionDegrees = 0.0)

    /** 200 x 250 m in 50 m squares: twenty of them, few enough to enumerate every line through them. */
    private val small = GridSpec(-100.0, 0.0, 4, 5, 50.0)

    /** Boat and mark both well inside a square, so which square holds the mark is never in doubt. */
    private val smallBoat = CoursePosition(-25.0, 15.0)
    private val smallMark = CoursePosition(25.0, 235.0)

    /** 150 x 200 m in 50 m squares, and a boat starting on a boundary between two of them. */
    private val tiny = GridSpec(-75.0, 0.0, 3, 4, 50.0)
    private val tinyBoat = CoursePosition(0.0, 10.0)
    private val tinyMark = CoursePosition(-10.0, 185.0)

    /**
     * A wind of [shift] degrees off the reference in every square of [spec], positive = veered: handed to
     * the search as it is, because what is under test is the search and not the measuring of a wind.
     */
    private fun field(spec: GridSpec, shift: (GridCell) -> Double): SailingConditions =
        CourseFixtures.Wind(spec, 3.0, shift)

    /** The winds the search is tried in: even, one side shifted, a gradient, stripes and a seeded jumble. */
    private fun winds(spec: GridSpec): List<Pair<String, SailingConditions>> {
        val jumble = Random(20240607).let { random -> List(spec.cellCount) { random.nextDouble(-25.0, 25.0) } }
        return listOf(
            "an even wind" to field(spec) { 0.0 },
            "the right of the course veered 20" to field(spec) { if (it.column >= spec.columns / 2) 20.0 else 0.0 },
            "a diagonal gradient" to field(spec) { -18.0 + 6.0 * (it.column + it.row) },
            "stripes of 18 either way" to field(spec) { if (it.column % 2 == 0) 18.0 else -18.0 },
            "a seeded jumble" to field(spec) { jumble[spec.index(it)] },
        )
    }

    /**
     * What the lattice the finder compares states on can cost: two states within one slot of it are taken
     * for the same state, so the line can be sent from a point up to a slot away from the best one, and
     * beating that slot back is [slots] tacks worth of sailing at the tack angle.
     */
    private fun latticeSeconds(spec: GridSpec, tackAngleDegrees: Int, slots: Int = 1): Double =
        slots * (spec.cellSizeMeters / RaceLineFinder.EDGE_SLOTS) / (RaceLineFinder.BOAT_SPEED_MPS * cos(Math.toRadians(tackAngleDegrees.toDouble())))

    private fun distance(from: CoursePosition, to: CoursePosition) =
        hypot(to.acrossMeters - from.acrossMeters, to.upwindMeters - from.upwindMeters)

    @Test
    fun `the finder is as quick as an exhaustive search of every legal line`() {
        val bound = latticeSeconds(small, 45)
        var worst = 0.0
        for ((name, wind) in winds(small)) {
            val line = RaceLineFinder.find(wind, smallBoat, smallMark, 45)
            val fastest = ExhaustiveBeat(wind, smallMark, 45).fastestSeconds(smallBoat)
            assertTrue(fastest.isFinite(), "no beat exists at all in $name")
            // The finder may not beat what is possible: every line it draws is one the enumeration sailed too.
            assertTrue(line.seconds > fastest - 1e-6, "the finder is quicker than possible in $name: ${line.seconds} vs $fastest")
            val gap = line.seconds - fastest
            println("SMALL 4x5 45deg $name: finder=${line.seconds} exhaustive=$fastest gap=$gap (${100 * gap / fastest}%)")
            worst = max(worst, gap)
        }
        assertTrue(worst <= bound, "the finder gives away $worst s, more than the $bound s the lattice can explain")
    }

    @Test
    fun `the finder is as quick as an exhaustive search on a smaller area and a higher pointing boat`() {
        val bound = latticeSeconds(tiny, 40)
        var worst = 0.0
        for ((name, wind) in winds(tiny)) {
            val line = RaceLineFinder.find(wind, tinyBoat, tinyMark, 40)
            val fastest = ExhaustiveBeat(wind, tinyMark, 40).fastestSeconds(tinyBoat)
            assertTrue(fastest.isFinite(), "no beat exists at all in $name")
            assertTrue(line.seconds > fastest - 1e-6, "the finder is quicker than possible in $name: ${line.seconds} vs $fastest")
            val gap = line.seconds - fastest
            println("TINY 3x4 40deg $name: finder=${line.seconds} exhaustive=$fastest gap=$gap (${100 * gap / fastest}%)")
            worst = max(worst, gap)
        }
        assertTrue(worst <= bound, "the finder gives away $worst s, more than the $bound s the lattice can explain")
    }

    @Test
    fun `an even wind and a dead upwind mark cost the beat plus the one tack it takes`() {
        // Nothing to gain by tacking about: the whole beat is sailed at the tack angle, so it is
        // height / cos(tack angle) of sailing, and a dead upwind mark cannot be laid without one tack.
        val wide = GridSpec(-400.0, 0.0, 16, 8, 50.0)
        val boat = CoursePosition(0.0, 0.0)
        val mark = CoursePosition(0.0, 400.0)
        for (tackAngleDegrees in listOf(30, 40, 50)) {
            val line = RaceLineFinder.find(field(wide) { 0.0 }, boat, mark, tackAngleDegrees)
            val sailing = 400.0 / (RaceLineFinder.BOAT_SPEED_MPS * cos(Math.toRadians(tackAngleDegrees.toDouble())))
            assertEquals(sailing + RaceLineFinder.TACK_LOSS_SECONDS, line.seconds, 1e-6, "at $tackAngleDegrees degrees: $line")
            assertEquals(1, line.tacks, "at $tackAngleDegrees degrees: $line")
        }
    }

    @Test
    fun `a channel two squares wide forces the boat to tack three times`() {
        // 100 m of width, 300 m to climb: the first board reaches the wall after 50 m across and every one
        // after it after 100 m, so the boat crosses the channel 50 + 100 + 100 + 50 and tacks three times.
        // (Corrected after the first run: the mark's square is reached at 250 m, but the last stretch to the
        // mark is on the other tack, and the tack it costs is charged - it was two tacks here before that.)
        val channel = GridSpec(-50.0, 0.0, 2, 6, 50.0)
        val boat = CoursePosition(0.0, 0.0)
        val mark = CoursePosition(0.0, 300.0)
        val line = RaceLineFinder.find(field(channel) { 0.0 }, boat, mark, 45)
        assertEquals(3, line.tacks, "the channel does not force the tacks: $line")
        assertEquals(300.0 * sqrt(2.0) / RaceLineFinder.BOAT_SPEED_MPS + 3 * RaceLineFinder.TACK_LOSS_SECONDS, line.seconds, 1e-6)
        // And the enumeration finds nothing quicker in the same channel.
        val fastest = ExhaustiveBeat(field(channel) { 0.0 }, mark, 45).fastestSeconds(boat)
        assertEquals(fastest, line.seconds, 1e-6, "the channel line is not the fastest one")
    }

    @Test
    fun `a mark on the tack angle is laid on one board, whatever the boat has to cross to get there`() {
        // The mark lies 45 degrees off the wind, so port tack points straight at it: no tack, no detour,
        // just the distance at boat speed, however many squares the board crosses on the way.
        val area = GridSpec(-50.0, -50.0, 6, 6, 50.0)
        val boat = CoursePosition(0.0, 0.0)
        val mark = CoursePosition(200.0, 200.0)
        val line = RaceLineFinder.find(field(area) { 0.0 }, boat, mark, 45)
        assertEquals(0, line.tacks, "a laid mark is not laid: $line")
        assertEquals(200.0 * sqrt(2.0) / RaceLineFinder.BOAT_SPEED_MPS, line.seconds, 1e-6)
        assertTrue(line.points.all { abs(it.acrossMeters - it.upwindMeters) < 1e-6 }, "the line wanders off the layline: $line")
        val fastest = ExhaustiveBeat(field(area) { 0.0 }, mark, 45).fastestSeconds(boat)
        assertEquals(fastest, line.seconds, 1e-6, "the straight board is not the fastest line")
    }

    @Test
    fun `a costlier tack never buys more tacks and never a shorter time`() {
        val areas = listOf(
            "a wide area" to Triple(GridSpec(-400.0, 0.0, 16, 8, 50.0), CoursePosition(0.0, 0.0), CoursePosition(0.0, 400.0)),
            "a channel" to Triple(GridSpec(-50.0, 0.0, 2, 6, 50.0), CoursePosition(0.0, 0.0), CoursePosition(0.0, 300.0)),
            "a shifted side" to Triple(small, smallBoat, smallMark),
        )
        for ((name, setup) in areas) {
            val (spec, boat, mark) = setup
            val wind = if (name == "a shifted side") winds(spec)[1].second else field(spec) { 0.0 }
            var previousSeconds = 0.0
            var previousTacks = Int.MAX_VALUE
            for (loss in listOf(0.0, 1.0, RaceLineFinder.TACK_LOSS_SECONDS, 8.0, 20.0, 60.0)) {
                val line = RaceLineFinder.find(wind, boat, mark, 45, tackLossSeconds = loss)
                assertTrue(line.seconds >= previousSeconds - 1e-9, "$name: a dearer tack got quicker at $loss s: $line")
                assertTrue(line.tacks <= previousTacks, "$name: a dearer tack bought more tacks at $loss s: $line")
                previousSeconds = line.seconds
                previousTacks = line.tacks
            }
        }
    }

    @Test
    fun `a boat that points higher is never slower`() {
        val areas = listOf(
            "a wide area" to Triple(GridSpec(-400.0, 0.0, 16, 8, 50.0), CoursePosition(0.0, 0.0), CoursePosition(0.0, 400.0)),
            "a channel" to Triple(GridSpec(-50.0, 0.0, 2, 6, 50.0), CoursePosition(0.0, 0.0), CoursePosition(0.0, 300.0)),
        )
        for ((name, setup) in areas) {
            val (spec, boat, mark) = setup
            val wind = field(spec) { 0.0 }
            var previous = 0.0
            for (tackAngleDegrees in listOf(30, 35, 40, 45, 50)) {
                val line = RaceLineFinder.find(wind, boat, mark, tackAngleDegrees)
                assertTrue(line.seconds >= previous - 1e-9, "$name: pointing lower got quicker at $tackAngleDegrees degrees: $line")
                previous = line.seconds
            }
        }
    }

    @Test
    fun `a shifted wind field costs what the enumeration says it costs, quicker or slower`() {
        // Written first as "a shift is never slower than an even wind". It is not a theorem, and this
        // fixture disproves it: the enumeration itself makes the right of the course veered 20 cost
        // 107.66 s against 107.31 s in an even wind. A shift only pays if it pays on the way to this
        // mark from this boat inside this area; the boat here has to leave the veered half to reach the
        // mark. So the check is the one that holds: the finder agrees with the enumeration, whichever
        // way the shift went.
        val even = RaceLineFinder.find(field(small) { 0.0 }, smallBoat, smallMark, 45).seconds
        for ((name, wind) in winds(small).drop(1)) {
            val fastest = ExhaustiveBeat(wind, smallMark, 45).fastestSeconds(smallBoat)
            val line = RaceLineFinder.find(wind, smallBoat, smallMark, 45)
            println("SHIFT $name: finder=${line.seconds} exhaustive=$fastest even=$even")
            assertTrue(line.seconds > fastest - 1e-6, "the finder is quicker than possible in $name: $line vs $fastest")
            assertTrue(line.seconds <= fastest + latticeSeconds(small, 45), "the finder gives away too much in $name: $line vs $fastest")
        }
    }

    @Test
    fun `zz diagnostics`() {
        val wide = GridSpec(-400.0, 0.0, 16, 8, 50.0)
        val boat = CoursePosition(0.0, 0.0)
        val mark = CoursePosition(0.0, 400.0)
        for (angle in listOf(30, 35, 40, 45, 50)) {
            val line = RaceLineFinder.find(field(wide) { 0.0 }, boat, mark, angle)
            val sailing = 400.0 / (RaceLineFinder.BOAT_SPEED_MPS * cos(Math.toRadians(angle.toDouble())))
            println("WIDE $angle: seconds=${line.seconds} tacks=${line.tacks} expected=${sailing + RaceLineFinder.TACK_LOSS_SECONDS} boards=${line.points.size - 1}")
            println("WIDE $angle points: ${line.points}")
        }
        val channel = GridSpec(-50.0, 0.0, 2, 6, 50.0)
        val cWind = field(channel) { 0.0 }
        val cMark = CoursePosition(0.0, 300.0)
        val cLine = RaceLineFinder.find(cWind, boat, cMark, 45)
        println("CHANNEL finder=${cLine.seconds} tacks=${cLine.tacks} points=${cLine.points}")
        println("CHANNEL touch=${ExhaustiveBeat(cWind, cMark, 45).fastestSeconds(boat)} strict=${ExhaustiveBeat(cWind, cMark, 45, finishOnTouch = false).fastestSeconds(boat)}")
        // A channel offset so the walls are not on the 50 m lattice of the boards.
        val offset = GridSpec(-60.0, 0.0, 2, 6, 50.0)
        val oWind = field(offset) { 0.0 }
        val oLine = RaceLineFinder.find(oWind, boat, cMark, 45)
        println("OFFSET finder=${oLine.seconds} tacks=${oLine.tacks}")
        println("OFFSET touch=${ExhaustiveBeat(oWind, cMark, 45).fastestSeconds(boat)} strict=${ExhaustiveBeat(oWind, cMark, 45, finishOnTouch = false).fastestSeconds(boat)}")

        for (angle in listOf(30, 40, 44, 45, 46, 48, 50, 55, 60)) {
            val search = ExhaustiveBeat(field(wide) { 0.0 }, mark, angle)
            val touch = search.fastestSeconds(boat)
            val line = RaceLineFinder.find(field(wide) { 0.0 }, boat, mark, angle)
            val ideal = 400.0 / (RaceLineFinder.BOAT_SPEED_MPS * cos(Math.toRadians(angle.toDouble()))) + RaceLineFinder.TACK_LOSS_SECONDS
            println("WIDEEXH $angle: exhaustive=$touch finder=${line.seconds} tacks=${line.tacks} ideal=$ideal")
            if (angle == 50) println("WIDEEXH 50 path: ${search.bestPath}")
        }
        // A symmetric small area with the mark dead upwind: is a shift ever slower than an even wind there?
        val square = GridSpec(-100.0, 0.0, 4, 5, 50.0)
        val sBoat = CoursePosition(0.0, 5.0)
        val sMark = CoursePosition(0.0, 245.0)
        for ((name, wind) in winds(square)) {
            val f = ExhaustiveBeat(wind, sMark, 45).fastestSeconds(sBoat)
            val strict = ExhaustiveBeat(wind, sMark, 45, finishOnTouch = false).fastestSeconds(sBoat)
            val l = RaceLineFinder.find(wind, sBoat, sMark, 45)
            println("SYMMETRIC $name: touch=$f strict=$strict finder=${l.seconds} tacks=${l.tacks}")
        }
        // The 4x5 comparison area again, both ways round the mark.
        for ((name, wind) in winds(small)) {
            val f = ExhaustiveBeat(wind, smallMark, 45).fastestSeconds(smallBoat)
            val strict = ExhaustiveBeat(wind, smallMark, 45, finishOnTouch = false).fastestSeconds(smallBoat)
            println("ASYMMETRIC $name: touch=$f strict=$strict")
        }
        for ((name, wind) in winds(tiny)) {
            val f = ExhaustiveBeat(wind, tinyMark, 40).fastestSeconds(tinyBoat)
            val strict = ExhaustiveBeat(wind, tinyMark, 40, finishOnTouch = false).fastestSeconds(tinyBoat)
            val l = RaceLineFinder.find(wind, tinyBoat, tinyMark, 40)
            println("TINY40 $name: touch=$f strict=$strict finder=${l.seconds} tacks=${l.tacks}")
        }
    }

    @Test
    fun `the enumeration is deep enough and does not depend on where the line is allowed to finish`() {
        // Self-checks on the reference: more boards on offer finds nothing quicker, and the finder's
        // stricter reading of rule 5 - only a board carrying on into the mark's square finishes there -
        // costs nothing over the letter of the rule.
        for ((name, wind) in winds(tiny)) {
            val deep = ExhaustiveBeat(wind, tinyMark, 45, maxBoards = 4 * (tiny.columns + tiny.rows) + 6).fastestSeconds(tinyBoat)
            val fastest = ExhaustiveBeat(wind, tinyMark, 45).fastestSeconds(tinyBoat)
            assertEquals(deep, fastest, 1e-9, "$name: the board cap binds")
            val strict = ExhaustiveBeat(wind, tinyMark, 45, finishOnTouch = false).fastestSeconds(tinyBoat)
            println("FINISH $name: touch=$fastest entering=$strict")
            assertEquals(strict, fastest, 1e-9, "$name: the two readings of rule 5 differ")
        }
    }
}

/**
 * Every legal beat from a boat to a windward mark, enumerated: [fastestSeconds] is the time of the
 * quickest one there is.
 *
 * Depth first over the rules of docs/RACE_LINE.md and nothing else. A board crosses exactly one square
 * from a point on its boundary along one of the two close-hauled courses of that square's wind; it may
 * not leave the racing area, because only squares of the area are ever crossed; it must gain on the mark;
 * a board on the other tack than the one before it costs [tackLossSeconds], and the tack the line starts
 * on is free; the line finishes on the boundary of the mark's own square and the run in from there is
 * charged at the speed made good.
 *
 * No two states are ever taken for one another, so nothing is lost to a lattice. What keeps that
 * affordable is the branch and bound: [remainingSeconds] can never overstate what is left to sail, so
 * dropping a line that already costs the quickest finish cannot throw away a quicker one.
 *
 * @param finishOnTouch rule 5 to the letter: a board that ends anywhere on the boundary of the mark's
 *   square arrives. False is the finder's stricter reading: only a board carrying on into that square.
 */
private class ExhaustiveBeat(
    private val field: SailingConditions,
    private val mark: CoursePosition,
    private val tackAngleDegrees: Int,
    private val speedMps: Double = RaceLineFinder.BOAT_SPEED_MPS,
    private val tackLossSeconds: Double = RaceLineFinder.TACK_LOSS_SECONDS,
    private val maxBoards: Int = RaceLineFinder.MAX_BOARDS_PER_CELL * (field.spec.columns + field.spec.rows),
    private val finishOnTouch: Boolean = true,
) {

    private val spec = field.spec

    /** The squares the mark lies in or on the edge of. */
    private val goals = squaresAt(mark).ifEmpty { listOf(spec.nearestCell(mark)) }

    /** How close to straight upwind any course in this field can point: the tack angle less the biggest shift. */
    private val closestDegrees = max(0.0, tackAngleDegrees - (0 until field.spec.cellCount).maxOf { abs(field.shiftDegrees(field.spec.cellAt(it))) })

    private var best = Double.POSITIVE_INFINITY

    /** How many boards were tried: what exhaustion cost, worth reporting when a comparison fails. */
    var boards: Long = 0
        private set

    var bestPath: List<CoursePosition> = emptyList()
        private set
    private val trail = ArrayList<CoursePosition>()

    fun fastestSeconds(from: CoursePosition): Double {
        best = Double.POSITIVE_INFINITY
        boards = 0
        trail.clear()
        trail += from
        sail(from, tack = 0, seconds = 0.0, depth = 0)
        return best
    }

    /** One board out of a state, before it is sailed. */
    private class Leg(val end: CoursePosition, val tack: Int, val seconds: Double, val finishSeconds: Double)

    /** Every board out of [at] on [tack] after [seconds], and everything that can follow them. */
    private fun sail(at: CoursePosition, tack: Int, seconds: Double, depth: Int) {
        if (depth >= maxBoards) return
        val legs = ArrayList<Leg>(8)
        for (square in squaresAt(at)) {
            val wind = field.shiftDegrees(square)
            for (side in TACKS) {
                val course = Math.toRadians(wind + side * tackAngleDegrees)
                val across = sin(course)
                val upwind = cos(course)
                val length = insideLength(square, at, across, upwind)
                if (length <= TOLERANCE_METERS) continue // the course points out of the square: no board
                val end = CoursePosition(at.acrossMeters + across * length, at.upwindMeters + upwind * length)
                // Rule 3: a board has to shorten the beat still to sail, which is the straight distance
                // only for a boat that tacks through exactly a right angle.
                if (toGoMeters(end, wind) > toGoMeters(at, wind) - GAIN_METERS) continue
                val cost = seconds + length / speedMps + if (tack != 0 && tack != side) tackLossSeconds else 0.0
                legs += Leg(end, side, cost, finishSeconds(end, across, upwind, cost, side))
            }
        }
        // The most promising board first, so the bound has something to cut against as early as possible.
        legs.sortBy { it.seconds + remainingSeconds(it.end) }
        for (leg in legs) {
            boards++
            if (leg.seconds + remainingSeconds(leg.end) >= best) break // sorted: nothing behind it can win either
            if (leg.finishSeconds < best) {
                best = leg.finishSeconds
                bestPath = trail + leg.end
            }
            trail += leg.end
            sail(leg.end, leg.tack, leg.seconds, depth + 1)
            trail.removeAt(trail.size - 1)
        }
    }

    /** Rule 5: what a line ending with this board costs in all, or infinity when it is not at the mark yet. */
    private fun finishSeconds(end: CoursePosition, across: Double, upwind: Double, seconds: Double, arrivedOn: Int): Double {
        val arrived = if (finishOnTouch) {
            squaresAt(end).filter { it in goals }
        } else {
            val ahead = CoursePosition(end.acrossMeters + across * NUDGE_METERS, end.upwindMeters + upwind * NUDGE_METERS)
            listOfNotNull(spec.cellOf(ahead)).filter { it in goals }
        }
        if (arrived.isEmpty()) return Double.POSITIVE_INFINITY
        // A mark on a boundary belongs to several squares; the slowest of their winds is charged for the
        // run in, so that the enumeration never flatters itself against the finder.
        return seconds + arrived.maxOf { approachSeconds(end, field.shiftDegrees(it), arrivedOn) }
    }

    /**
     * The run in: straight to the mark, at the speed made good when it lies inside the no go zone, and
     * costing a tack when the boat can lay the mark but only on the other tack than it arrived on.
     */
    private fun approachSeconds(from: CoursePosition, windDegrees: Double, arrivedOn: Int): Double {
        val bearing = Math.toDegrees(atan2(mark.acrossMeters - from.acrossMeters, mark.upwindMeters - from.upwindMeters))
        val twa = wrapped(bearing - windDegrees)
        val laid = abs(twa) >= tackAngleDegrees - 1e-9
        val side = if (twa < 0.0) -1 else 1
        val tack = if (laid && arrivedOn != 0 && side != arrivedOn) tackLossSeconds else 0.0
        return toGoMeters(from, windDegrees) / speedMps + tack
    }

    /** What is left to sail to the mark from [from]: the distance when it can be laid, the beat when not. */
    private fun toGoMeters(from: CoursePosition, windDegrees: Double): Double {
        val bearing = Math.toDegrees(atan2(mark.acrossMeters - from.acrossMeters, mark.upwindMeters - from.upwindMeters))
        val twa = abs(wrapped(bearing - windDegrees))
        val vmg = if (twa >= tackAngleDegrees) 1.0 else cos(Math.toRadians(tackAngleDegrees.toDouble())) / cos(Math.toRadians(twa))
        return toMark(from) / vmg
    }

    /**
     * A time nothing sailed from [at] can beat: the boat closes on the mark at no more than its speed,
     * and it climbs at no more than speed x cos(tack angle less the biggest shift in the field). The
     * second holds for the run in as well, because that is charged at the speed made good beating.
     */
    private fun remainingSeconds(at: CoursePosition): Double {
        val climb = mark.upwindMeters - at.upwindMeters
        val beating = if (climb > 0.0) climb / (speedMps * cos(Math.toRadians(closestDegrees))) else 0.0
        return max(toMark(at) / speedMps, beating)
    }

    /** How far a course from [at] runs inside [square] before it leaves it; zero when it points straight out. */
    private fun insideLength(square: GridCell, at: CoursePosition, across: Double, upwind: Double): Double {
        val left = spec.leftMeters + square.column * spec.cellSizeMeters
        val bottom = spec.bottomMeters + square.row * spec.cellSizeMeters
        return min(
            leaves(at.acrossMeters, across, left, left + spec.cellSizeMeters),
            leaves(at.upwindMeters, upwind, bottom, bottom + spec.cellSizeMeters),
        )
    }

    private fun leaves(from: Double, direction: Double, low: Double, high: Double): Double = when {
        direction > PARALLEL -> (high - from) / direction
        direction < -PARALLEL -> (low - from) / direction
        else -> Double.POSITIVE_INFINITY
    }

    /** The squares whose closed boundary touches [at]: one inside a square, two on a side, four on a corner. */
    private fun squaresAt(at: CoursePosition): List<GridCell> {
        val columns = touching(at.acrossMeters - spec.leftMeters, spec.columns)
        val rows = touching(at.upwindMeters - spec.bottomMeters, spec.rows)
        return columns.flatMap { column -> rows.map { row -> GridCell(column, row) } }
    }

    private fun touching(offsetMeters: Double, count: Int): List<Int> {
        val exact = offsetMeters / spec.cellSizeMeters
        return (floor(exact - EDGE_CELLS).toInt()..floor(exact + EDGE_CELLS).toInt()).filter { it in 0 until count }
    }

    private fun toMark(from: CoursePosition): Double =
        hypot(mark.acrossMeters - from.acrossMeters, mark.upwindMeters - from.upwindMeters)

    /** Any angle onto (-180, 180]. */
    private fun wrapped(degrees: Double): Double {
        val remainder = degrees % 360.0
        return when {
            remainder > 180.0 -> remainder - 360.0
            remainder <= -180.0 -> remainder + 360.0
            else -> remainder
        }
    }

    private companion object {
        /** Starboard is the wind less the tack angle, port the wind plus it. */
        val TACKS = intArrayOf(-1, 1)
        const val TOLERANCE_METERS = 1e-6
        const val GAIN_METERS = 1e-6
        const val PARALLEL = 1e-9
        const val NUDGE_METERS = 1e-3
        const val EDGE_CELLS = 1e-9
    }
}
