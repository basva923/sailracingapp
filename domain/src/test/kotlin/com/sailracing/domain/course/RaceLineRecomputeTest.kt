package com.sailracing.domain.course

import com.sailracing.domain.geo.Angles
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.model.PositionFix
import com.sailracing.domain.race.RaceCalculator
import com.sailracing.domain.race.RaceEvent
import com.sailracing.domain.race.RaceReducer
import com.sailracing.domain.race.RaceSnapshot
import com.sailracing.domain.race.RaceState
import com.sailracing.domain.startline.StartLine
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * When the race line is searched again, and what is drawn in between.
 *
 * The rule under test (docs/RACE_LINE.md, "When it is searched again"): the line is searched again as the
 * boat sails into a new square of the racing area, and only then; while it stays in one square the line
 * found from that square is kept and pinned to the boat, so it does not flicker four times a second under
 * a sailor who is trying to steer to it. It is also searched again when the racing area, the mark or the
 * tack angle changes.
 *
 * Nothing here counts calls: a reused line is observable from outside, because pinning only replaces the
 * first point. So `points.drop(1)`, `seconds` and `tacks` of a kept line are those of the line before it,
 * to the last bit, while a searched line is exactly what [RaceLinePlanner.plan] gives for the boat's own
 * position in the model's own wind field. Those two are the assertions used throughout, and where a kept
 * line could look like a searched one by accident, the test also shows that the two really differ.
 */
class RaceLineRecomputeTest {

    private val origin = GeoPoint(51.14, 5.83)
    private val frame = CourseFrame(origin, windDirectionDegrees = 0.0)

    /** A position on the water, written as its place in the wind-up frame in metres. */
    private fun at(across: Double, upwind: Double): GeoPoint = frame.toGeo(CoursePosition(across, upwind))

    private fun point(across: Double, upwind: Double, wind: Double? = null, seconds: Long = 0L): TrackPoint =
        TrackPoint(seconds * 1000, at(across, upwind), speedMps = 3.0, headingDegrees = 0.0, upwindWindDegrees = wind)

    /**
     * Two corners of a sailed track that pin the racing area to 600 x 600 m of 50 m squares,
     * `GridSpec(-300, 0, 12, 12, 50)`, with the mark at the middle of the top edge. They sit a whole
     * sub-grid step inside their squares on purpose: a millimetre of projection rounding on a corner
     * would otherwise snap the area a cell wider and silently change every square in these tests.
     */
    private val area = Track(listOf(point(-295.0, 5.0), point(295.0, 595.0)))

    /** The boat just above the start, in square (6, 0); no mark set, so the mark is the top of the area. */
    private val inputs = CourseInputs(frame, StartLine(), windwardMark = null, boat = at(5.0, 5.0), tackAngleDegrees = 45)

    /** The plan a fresh run gives for a model: same wind field, same boat, mark, tack angle and settings. */
    private fun planned(model: CourseModel, field: WindField = model.windField, boat: CoursePosition = assertNotNull(model.boat)): RaceLinePlan =
        RaceLinePlanner.plan(
            field = field,
            from = boat,
            to = model.windwardMark,
            tackAngleDegrees = model.inputs.tackAngleDegrees,
            currentTack = model.inputs.currentTack,
            currentShiftDegrees = model.inputs.currentWindDegrees?.let {
                Angles.signedDifference(model.inputs.frame.windDirectionDegrees, it)
            },
            settings = model.inputs.settings.raceLine,
        )

    /** The line a fresh run gives for a model: the safe one, which is the one the model draws. */
    private fun searched(model: CourseModel): RaceLine = planned(model).safe

    private fun square(model: CourseModel): GridCell = model.spec.nearestCell(assertNotNull(model.boat))

    /** [line] is [before] kept and only pinned to [boat]: the same boards, the same time, the same tacks. */
    private fun assertPinned(before: RaceLine, line: RaceLine, boat: CoursePosition, why: String = "") {
        assertEquals(boat, line.points.first(), "$why: the line hangs behind the boat")
        assertEquals(before.points.drop(1), line.points.drop(1), "$why: the boards changed, so it was searched again")
        assertEquals(before.seconds, line.seconds, "$why: the time changed, so it was searched again")
        assertEquals(before.tacks, line.tacks, "$why: the tack count changed, so it was searched again")
    }

    /** A line a sailor could steer: it starts on the boat, ends on the mark, and its time is a number. */
    private fun assertUsable(model: CourseModel, why: String) {
        val line = model.raceLine
        assertTrue(line.points.size >= 2, "$why: a line needs at least the boat and the mark, got $line")
        assertEquals(model.boat, line.points.first(), "$why: the line does not start at the boat")
        assertEquals(model.windwardMark, line.points.last(), "$why: the line does not end at the mark")
        assertTrue(line.points.all { it.acrossMeters.isFinite() && it.upwindMeters.isFinite() }, "$why: a point is not a number")
        assertTrue(line.seconds.isFinite() && line.seconds >= 0.0, "$why: nonsense time ${line.seconds}")
        assertTrue(line.tacks >= 0, "$why: negative tacks")
    }

    @Test
    fun `sailing on within one square keeps the line and only pins it to the boat`() {
        val first = CourseModel.build(area, inputs)
        assertEquals(GridSpec(-300.0, 0.0, 12, 12, 50.0), first.spec, "the fixture no longer builds the area it describes")
        assertEquals(GridCell(6, 0), square(first))

        // Nine seconds at 3 m/s, all of it inside the same 50 m square the boat started the line from.
        val on = CourseModel.build(area, inputs.copy(boat = at(20.0, 26.0)), previous = first)
        assertEquals(first.spec, on.spec)
        assertEquals(square(first), square(on))
        assertPinned(first.raceLine, on.raceLine, assertNotNull(on.boat), "inside one square")

        // It really was kept: a search from where the boat is now lays the boards out differently.
        assertNotEquals(searched(on).points.drop(1), on.raceLine.points.drop(1), "the kept line is what a search would give anyway")
    }

    @Test
    fun `a wind measured while crossing a square is only sailed to in the next square`() {
        val first = CourseModel.build(area, inputs)
        assertEquals(0, first.windField.measuredCount, "the fixture starts on the reference wind everywhere")

        // Three close-hauled samples in the square the boat is in: with one measured square the whole field
        // becomes that measurement, a 30 degree veer, and the beat that suits it is a different one.
        val samples = List(WindFieldSettings().minBlockSamples) { point(20.0, 26.0, wind = 30.0, seconds = it + 1L) }
        val measured = Track(area.points + samples)
        val same = CourseModel.build(measured, inputs.copy(boat = at(20.0, 26.0)), previous = first)
        assertEquals(first.spec, same.spec)
        assertEquals(square(first), square(same))
        assertNotEquals(0, same.windField.samples, "the new wind was not measured at all")
        assertPinned(first.raceLine, same.raceLine, assertNotNull(same.boat), "a shift measured mid-square")

        // One square further up it is picked up, and it is not the line the old, even field would give.
        val next = CourseModel.build(measured, inputs.copy(boat = at(20.0, 76.0)), previous = same)
        assertEquals(GridCell(6, 1), square(next))
        assertEquals(searched(next), next.raceLine)
        assertNotEquals(
            planned(next, field = first.windField).safe,
            next.raceLine,
            "the shift measured in the square before was not used",
        )
    }

    @Test
    fun `sailing into the next square searches the line again`() {
        val first = CourseModel.build(area, inputs)
        val next = CourseModel.build(area, inputs.copy(boat = at(55.0, 5.0)), previous = first)
        assertEquals(first.spec, next.spec, "the area may not change, or this would prove nothing about the square")
        assertEquals(GridCell(7, 0), square(next))

        assertEquals(searched(next), next.raceLine)
        // The shape changed, not just the end that is tied to the boat: the boards leave the squares elsewhere.
        assertNotEquals(first.raceLine.points.drop(1), next.raceLine.points.drop(1), "the line was pinned, not searched")
        assertEquals(next.windwardMark, next.raceLine.points.last())
    }

    @Test
    fun `without a model of the moment before the line is always searched`() {
        for (boat in listOf(at(5.0, 5.0), at(20.0, 26.0), at(-155.0, 355.0), at(275.0, 25.0))) {
            val model = CourseModel.build(area, inputs.copy(boat = boat))
            assertEquals(searched(model), model.raceLine, "a first model must be searched")
        }
        // And the search is deterministic, which is what lets every other test compare against it.
        assertEquals(CourseModel.build(area, inputs).raceLine, CourseModel.build(area, inputs).raceLine)
    }

    @Test
    fun `a line found in another racing area is not reused`() {
        val marked = inputs.copy(windwardMark = at(5.0, 595.0))
        val first = CourseModel.build(area, marked)

        // One point 25 m further out to the left: the area grows and its squares grow with it, 55 m instead
        // of 50 m. The boat has not moved, and by coincidence it is even in the square with the same number.
        val wider = Track(area.points + point(-320.0, 5.0, seconds = 1L))
        val second = CourseModel.build(wider, marked, previous = first)
        assertNotEquals(first.spec, second.spec)
        assertEquals(square(first), square(second), "the fixture meant to keep the cell number and change the grid")
        assertEquals(first.boat, second.boat)
        assertEquals(first.windwardMark, second.windwardMark, "the mark itself did not move")

        assertEquals(searched(second), second.raceLine)
        assertNotEquals(first.raceLine.points.drop(1), second.raceLine.points.drop(1), "the boards still cross the old squares")
    }

    @Test
    fun `a line to another mark is not reused`() {
        val first = CourseModel.build(area, inputs.copy(windwardMark = at(5.0, 595.0)))
        val moved = CourseModel.build(area, inputs.copy(windwardMark = at(-105.0, 595.0)), previous = first)
        assertEquals(first.spec, moved.spec, "the new mark is inside the old area, so only the mark changed")
        assertEquals(first.boat, moved.boat)

        // A kept line would still end where the old mark was: the line would point at nothing.
        assertEquals(moved.windwardMark, moved.raceLine.points.last())
        assertNotEquals(first.windwardMark, moved.raceLine.points.last())
        assertEquals(searched(moved), moved.raceLine)
    }

    @Test
    fun `a line sailed at another tack angle is not reused`() {
        val first = CourseModel.build(area, inputs)
        val higher = CourseModel.build(area, inputs.copy(tackAngleDegrees = 30), previous = first)
        assertEquals(first.spec, higher.spec)
        assertEquals(first.boat, higher.boat)

        assertEquals(searched(higher), higher.raceLine)
        // A boat that points 15 degrees higher gets to the same mark sooner; a kept line would say otherwise.
        assertTrue(
            higher.raceLine.seconds < first.raceLine.seconds,
            "pointing higher did not pay: ${higher.raceLine.seconds} against ${first.raceLine.seconds}",
        )
    }

    @Test
    fun `a model built without a boat has no line to hand on`() {
        val blind = CourseModel.build(area, inputs.copy(boat = null))
        assertTrue(blind.raceLine.isEmpty, "there is nothing to sail from without a position")

        val fixed = CourseModel.build(area, inputs, previous = blind)
        assertEquals(searched(fixed), fixed.raceLine)
        assertTrue(fixed.raceLine.points.size > 2, "600 m of beat is more than one board: ${fixed.raceLine}")
    }

    @Test
    fun `a model with a boat always has a line, so there is never an empty one to keep`() {
        // The guard on an empty previous line cannot be reached through build: CourseModel's constructor is
        // private, and every model that has a boat has at least the straight course to the mark. That is the
        // invariant worth pinning down, in the corners where a search finds nothing at all.
        val cases = mapOf(
            "a beat up the whole area" to CourseModel.build(area, inputs),
            "an empty track" to CourseModel.build(Track(), inputs.copy(boat = at(3.0, 4.0))),
            "the boat on the mark" to CourseModel.build(area, inputs.copy(windwardMark = at(5.0, 595.0), boat = at(5.0, 595.0))),
            "the mark abeam" to CourseModel.build(area, inputs.copy(windwardMark = at(295.0, 15.0), boat = at(-295.0, 15.0))),
            "the mark downwind" to CourseModel.build(area, inputs.copy(windwardMark = at(5.0, 15.0), boat = at(5.0, 585.0))),
        )
        for ((why, model) in cases) {
            assertTrue(!model.raceLine.isEmpty, "$why: no line at all")
            assertUsable(model, why)
        }
    }

    @Test
    fun `up a whole beat the line hangs on the boat and reaches the mark, and is searched once per square`() {
        // Up the left on starboard, tack at 145 m out, back to the mark on port; two of the steps are short
        // enough to stay in the square the boat is already in, the other seven cross into a new one.
        val beat = listOf(
            5.0 to 5.0, 25.0 to 25.0, -15.0 to 65.0, -35.0 to 85.0, -65.0 to 115.0,
            -145.0 to 195.0, -125.0 to 245.0, -75.0 to 295.0, -25.0 to 345.0, 25.0 to 395.0,
        )
        var previous: CourseModel? = null
        var kept = 0
        var searches = 0
        for ((across, upwind) in beat) {
            val model = CourseModel.build(area, inputs.copy(boat = at(across, upwind)), previous = previous)
            val boat = assertNotNull(model.boat)
            val where = "at $across, $upwind"
            assertUsable(model, where)
            val before = previous
            if (before != null) {
                assertEquals(before.spec, model.spec, "$where: the area moved under the test")
                if (square(before) == square(model)) {
                    assertPinned(before.raceLine, model.raceLine, boat, where)
                    kept++
                } else {
                    assertEquals(searched(model), model.raceLine, "$where: the line was not searched again")
                    searches++
                }
            }
            previous = model
        }
        assertEquals(2, kept)
        assertEquals(7, searches)
    }

    @Test
    fun `snapshot after snapshot the line stays on the boat and is searched only in a new square`() {
        // A 100 m start line and a mark 400 m up: the area is pinned by them, so it does not grow under the
        // boat as the track does. The fixes carry no course over ground, so no wind is measured and the
        // reference wind - and with it the frame and the wind field - stays put; this is about the squares.
        val start = RaceState(startLine = StartLine(at(-50.0, 0.0), at(50.0, 0.0)))
        var state = RaceReducer.reduce(start, RaceEvent.SetWindwardMark(at(0.0, 400.0))).state
        val step = 3.0 / sqrt(2.0) // 3 m/s close-hauled at 45 degrees, one fix a second

        var snapshot: RaceSnapshot? = null
        var previous: CourseModel? = null
        var kept = 0
        val searches = ArrayList<Int>()
        for (second in 1..30) {
            val boat = at(-45.0 + second * step, 5.0 + second * step)
            val millis = second * 1000L
            state = RaceReducer.reduce(state, RaceEvent.FixReceived(PositionFix(boat, millis, speedMps = 3.0))).state
            snapshot = RaceCalculator.snapshot(state, nowMillis = millis, previous = snapshot)
            val model = assertNotNull(snapshot.course)
            assertUsable(model, "second $second")
            val before = previous
            if (before != null) {
                assertEquals(before.spec, model.spec, "second $second: the area moved under the test")
                if (square(before) == square(model)) {
                    assertPinned(before.raceLine, model.raceLine, assertNotNull(model.boat), "second $second")
                    kept++
                } else {
                    assertEquals(searched(model), model.raceLine, "second $second: the line was not searched again")
                    searches += second
                }
            }
            previous = model
        }
        // The map was drawn 30 times and the beat searched three times: the boat crosses into the next
        // column of 35 m squares after 5 s, into the next row after 15 s and into the next column again
        // after 22 s. Three searches for 30 fixes is the whole point of the rule.
        assertEquals(listOf(5, 15, 22), searches)
        assertEquals(26, kept)
    }

    @Test
    fun `a boat exactly on the boundary between two squares still gets a line`() {
        // A boundary belongs to the square above and to the right of it, but the projection puts the boat a
        // few nanometres on one side or the other, so which square it counts as is not worth asserting.
        // What has to hold is that there is a line either way, and that it is one of the two honest answers.
        val corner = CourseModel.build(area, inputs.copy(boat = at(50.0, 50.0)))
        assertUsable(corner, "on the corner of four squares")
        assertEquals(searched(corner), corner.raceLine)

        val inside = CourseModel.build(area, inputs.copy(boat = at(49.0, 49.0)))
        val onto = CourseModel.build(area, inputs.copy(boat = at(50.0, 50.0)), previous = inside)
        assertUsable(onto, "crossing onto the boundary")
        val boat = assertNotNull(onto.boat)
        assertTrue(
            onto.raceLine == searched(onto) || onto.raceLine.points.drop(1) == inside.raceLine.points.drop(1),
            "the line on the boundary is neither searched nor kept: ${onto.raceLine}",
        )
        assertEquals(boat, onto.raceLine.points.first())
    }

    @Test
    fun `a boat sitting on the mark gets a line of no length and no time`() {
        val mark = at(5.0, 595.0)
        val model = CourseModel.build(area, inputs.copy(windwardMark = mark, boat = mark))
        val boat = assertNotNull(model.boat)
        assertEquals(model.windwardMark, boat)
        assertEquals(listOf(boat, boat), model.raceLine.points)
        assertEquals(0.0, model.raceLine.seconds, "arriving takes no time, and is not a NaN either")
        assertEquals(0, model.raceLine.tacks)

        // Drifting 10 m off the mark stays inside the mark's own square, so the line is only pinned to the
        // boat and still says the beat takes no time at all. That is the reuse rule doing what it says, and
        // the error is well inside the one square the spec allows: a search would charge 4.7 s for the 10 m.
        val off = CourseModel.build(area, inputs.copy(windwardMark = mark, boat = at(5.0, 585.0)), previous = model)
        assertUsable(off, "10 m below the mark")
        assertPinned(model.raceLine, off.raceLine, assertNotNull(off.boat), "10 m below the mark")
        assertEquals(0.0, off.raceLine.seconds)

        // A square further down it is searched again and costs what it costs.
        val below = CourseModel.build(area, inputs.copy(windwardMark = mark, boat = at(5.0, 525.0)), previous = off)
        assertNotEquals(square(off), square(below))
        assertEquals(searched(below), below.raceLine)
        assertTrue(below.raceLine.seconds > 0.0, "the last 70 m of the beat are free: ${below.raceLine}")
    }

    @Test
    fun `an empty track still gives a line from the boat to the mark`() {
        val single = CourseModel.build(Track(), inputs.copy(boat = at(3.0, 4.0)))
        assertEquals(GridSpec(0.0, 0.0, 1, 1, 5.0), single.spec, "nothing sailed yet: one square around the start")
        assertUsable(single, "an empty track and no mark")
        assertEquals(2, single.raceLine.points.size, "inside the mark's own square the line is straight")

        // With a mark set and nothing sailed the area is one column wide and the beat bounces off both walls.
        val corridor = CourseModel.build(Track(), inputs.copy(windwardMark = at(5.0, 595.0)))
        assertEquals(1, corridor.spec.columns)
        assertUsable(corridor, "an empty track and a mark 600 m up")
        assertTrue(corridor.raceLine.tacks > 1, "a beat up a 50 m corridor tacks off its walls: ${corridor.raceLine}")
        assertEquals(searched(corridor), corridor.raceLine)
    }

    @Test
    fun `a mark far outside the sailed track is taken into the racing area, and one outside it is still aimed at`() {
        val model = CourseModel.build(area, inputs.copy(windwardMark = at(5.0, 1995.0)))
        assertUsable(model, "a mark 2 km up")
        val mark = model.windwardMark
        val spec = model.spec
        assertTrue(
            mark.acrossMeters in spec.leftMeters..spec.rightMeters && mark.upwindMeters in spec.bottomMeters..spec.topMeters,
            "the racing area does not contain the mark: $mark in $spec",
        )

        // The finder itself does not depend on that: from outside the field the mark is aimed at from the
        // nearest square, so a caller that hands it one cannot make it throw or lose the mark.
        val outside = CoursePosition(0.0, 5_000.0)
        val line = RaceLineFinder.find(WindSampler.of(model.windField).mean, assertNotNull(model.boat), outside, 45)
        assertEquals(model.boat, line.points.first())
        assertEquals(outside, line.points.last())
        assertTrue(line.seconds > 0.0 && line.seconds.isFinite())
    }

    @Test
    fun `a line found against another reference wind is not reused`() {
        // The area is a ring of track 295 m round the start, so it snaps to the same 600 x 600 m of 50 m
        // squares whichever way the reference wind points, and the boat sits on the start itself, the one
        // place a turn of the frame leaves alone. Everything CourseModel compares is therefore unchanged,
        // while the water is not: the wind measured at 20 degrees is 20 off a frame that points north and
        // 70 off one that points east, and the mark at the top of the area is a different mark.
        val ring = ArrayList<TrackPoint>()
        for (tenth in 0 until 36) {
            val bearing = Angles.toRadians(tenth * 10.0)
            val place = frame.toGeo(CoursePosition(295.0 * sin(bearing), 295.0 * cos(bearing)))
            repeat(WindFieldSettings().minBlockSamples) {
                ring += TrackPoint(0L, place, speedMps = 3.0, headingDegrees = 0.0, upwindWindDegrees = 20.0)
            }
        }
        val sailed = Track(ring)
        val north = CourseInputs(frame, StartLine(), windwardMark = null, boat = origin, tackAngleDegrees = 45)
        val east = north.copy(frame = CourseFrame(origin, windDirectionDegrees = 90.0))

        val before = CourseModel.build(sailed, north)
        val after = CourseModel.build(sailed, east, previous = before)
        assertEquals(before.spec, after.spec, "the fixture meant to keep the area identical through the turn")
        assertEquals(before.windwardMark, after.windwardMark, "the fixture meant to keep the mark's numbers identical")
        assertEquals(before.boat, after.boat, "the fixture meant to keep the boat on the origin")
        assertNotEquals(before.windField, after.windField, "the wind should now lie 70 degrees off the frame, not 20")

        // DISAGREEMENT: this fails. CourseModel.reusableLine compares the area, the mark and the boat's
        // square, all of them numbers in the course frame, but never the frame itself. When the reference
        // wind changes - the sailor sets another wind, or the measured mean swings - every one of those
        // numbers can stay the same while the water they describe has turned. The kept line is then the
        // beat for the old wind: here a 600 m beat of several boards against a mark that is now a reach
        // away, where the line to sail is the straight course to it. The spec allows a kept line to be up
        // to one square out; a turned frame is not bounded by that at all. One term in `same` fixes it:
        // `inputs.frame == frame`.
        assertEquals(searched(after), after.raceLine, "the line was kept although the whole frame turned")
    }
}
