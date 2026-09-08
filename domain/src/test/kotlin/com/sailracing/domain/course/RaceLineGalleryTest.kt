package com.sailracing.domain.course

import com.sailracing.domain.course.RaceLineGallery.reachOf
import com.sailracing.domain.course.RaceLineGallery.sideOf
import com.sailracing.domain.course.RaceLineGallery.swing
import com.sailracing.domain.geo.Angles
import com.sailracing.domain.wind.Tack
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ten racing areas, drawn to `docs/raceline` so that the plan can be checked by eye: the wind measured in
 * every square with how sure of it the model is, the pressure over the course, every simulation's own best
 * beat, and the two lines the plan picks out of them.
 *
 * Each one also asserts what it is a picture of, so that a change to the algorithm that spoils a case fails
 * here rather than quietly redrawing the gallery. The assertions are deliberately about the shape of the
 * answer - which side, how many tacks, whether there is a gamble at all - and never about a particular
 * line, which is the search's business and is pinned down in the tests next to this one.
 */
class RaceLineGalleryTest {

    /** 600 x 600 m of 50 m squares: a beat of a bit over half a kilometre, which is a club race. */
    private val area = GridSpec(-300.0, 0.0, 12, 12, 50.0)
    private val boat = CoursePosition(0.0, 10.0)
    private val mark = CoursePosition(0.0, 590.0)
    private val settings = RaceLineSettings()

    private val drawn = ArrayList<Drawing>()

    private class Drawing(val file: String, val title: String, val expectation: String, val plan: RaceLinePlan)

    private fun tacks(line: RaceLine): String = if (line.tacks == 1) "1 tack" else "${line.tacks} tacks"

    /** Which way the first board of a line goes: 0 is straight upwind, 90 across to the right. */
    private fun firstBearing(line: RaceLine): Double {
        val (from, to) = line.points
        return Angles.toDegrees(atan2(to.acrossMeters - from.acrossMeters, to.upwindMeters - from.upwindMeters))
    }

    /** Plans a beat over [field], draws it, and hands the plan back to be checked. */
    private fun gallery(
        file: String,
        title: String,
        expectation: String,
        field: WindField,
        from: CoursePosition = boat,
        to: CoursePosition = mark,
        currentTack: Tack? = null,
        currentShiftDegrees: Double? = null,
    ): RaceLinePlan {
        val plan = RaceLinePlanner.plan(field, from, to, 45, currentTack, currentShiftDegrees, settings)
        RaceLineGallery.draw(
            file = File(RaceLineGallery.repositoryRoot(), "docs/raceline/$file.png"),
            title = title,
            expectation = expectation,
            field = field,
            boat = from,
            mark = to,
            plan = plan,
            settings = settings,
            currentShiftDegrees = currentShiftDegrees,
        )
        drawn += Drawing("$file.png", title, expectation, plan)
        return plan
    }

    @Test
    fun `the gallery`() {
        evenWind()
        veeredRight()
        oscillatingBands()
        pressureOnTheLeft()
        puffyRight()
        shiftyRight()
        headedRightNow()
        unsailedMiddle()
        narrowCorridor()
        patchyWater()

        // The index, written from the same run that drew the pictures so the two cannot drift apart.
        val page = buildString {
            appendLine("# The race line, in ten pictures")
            appendLine()
            appendLine("Written by `RaceLineGalleryTest`; run `./gradlew :domain:test` to draw them again.")
            appendLine()
            appendLine("Every picture is one racing area, wind up the page, the start at the bottom and the windward")
            appendLine("mark **M** at the top. In each square an arrow points the way the wind there blows, amber where")
            appendLine("it is veered and blue where it is backed, dim where nobody has sailed and it was estimated from")
            appendLine("the squares that were; the pale wedge behind the arrow is how uncertain that wind is, and the")
            appendLine("shading of the square is the boat speed measured in it, lighter for more pressure. The white dot")
            appendLine("is the boat, with an arrow for the wind it is measuring right now where it has one.")
            appendLine()
            appendLine("The thin grey lines are the fastest beat of each of the ${settings.runs} simulated winds - the spread of")
            appendLine("opinion. The **green** line is the race line the map draws, the one whose bad day is least bad. The")
            appendLine("**amber dashed** line, where there is one, is the flyer: the line that pays most when the wind is")
            appendLine("kind, drawn only when it is worth at least a tack more than the race line on a good day.")
            for (drawing in drawn) {
                appendLine()
                appendLine("## ${drawing.title}")
                appendLine()
                appendLine(drawing.expectation)
                appendLine()
                appendLine("![${drawing.title}](raceline/${drawing.file})")
                appendLine()
                val plan = drawing.plan
                appendLine(
                    "Race line: ${tacks(plan.safe)}, ${plan.safeRisk.meanSeconds.roundToInt()} s on an average day " +
                        "(${plan.safeRisk.goodSeconds.roundToInt()}-${plan.safeRisk.badSeconds.roundToInt()} s). " +
                        if (plan.agree) {
                            "No flyer: nothing beat it by more than a tack."
                        } else {
                            "Flyer: ${tacks(plan.fast)}, ${plan.fastRisk.meanSeconds.roundToInt()} s " +
                                "(${plan.fastRisk.goodSeconds.roundToInt()}-${plan.fastRisk.badSeconds.roundToInt()} s), " +
                                "ahead in ${(plan.winFraction * plan.runs).roundToInt()} of ${plan.runs} winds."
                        },
                )
            }
        }
        File(RaceLineGallery.repositoryRoot(), "docs/RACE_LINE_GALLERY.md").writeText(page)

        for (drawing in drawn) {
            val file = File(RaceLineGallery.repositoryRoot(), "docs/raceline/${drawing.file}")
            assertTrue(file.isFile && file.length() > 10_000, "no picture drawn for ${drawing.title}")
        }
    }

    private fun evenWind() {
        val field = CourseFixtures.even(area, samplesPerCell = 60)
        val plan = gallery(
            "01-even-wind",
            "An even wind",
            "Nothing to choose: one long board out to the layline and one home, and no flyer worth drawing.",
            field,
        )
        assertTrue(plan.agree, "an even wind should leave nothing to gamble on")
        assertTrue(plan.safe.tacks <= 2, "an even wind is beaten with the fewest tacks: ${plan.safe.tacks}")
    }

    private fun veeredRight() {
        // A wind that veers steadily across the course: 13 degrees backed on the left, 13 veered on the right.
        val field = CourseFixtures.field(area, samplesPerCell = 60) { cell, index, _ ->
            CourseFixtures.Sample(swing((cell.column - 5.5) * 2.4, 3.0, index), 3.0)
        }
        val plan = gallery(
            "02-veered-to-the-right",
            "A wind veered across the course",
            "The right of the course is veered and the left backed. The beat leans into the veer on the right, " +
                "where port tack is headed and starboard pays, and the line comes back on the lift.",
            field,
        )
        assertTrue(reachOf(plan.safe) > 100.0, "the line ignored the veer on the right: ${reachOf(plan.safe)}")
    }

    private fun oscillatingBands() {
        // Bands of 150 m across the course, the wind swinging 9 degrees either way from one to the next.
        val field = CourseFixtures.field(area, samplesPerCell = 60) { cell, index, _ ->
            CourseFixtures.Sample(swing(if ((cell.row / 3) % 2 == 0) 9.0 else -9.0, 2.0, index), 3.0)
        }
        val plan = gallery(
            "03-oscillating-bands",
            "Bands of shifted wind",
            "The wind swings one way and then the other every 150 m up the course. The line tacks on the shifts, " +
                "sailing the lifted tack through each band rather than laying off across them.",
            field,
        )
        assertTrue(plan.safe.tacks >= 2, "a banded course should be tacked up: ${plan.safe.tacks}")
        assertTrue(abs(sideOf(plan.safe)) < 220.0, "the line went to a corner instead of up the shifts: ${sideOf(plan.safe)}")
    }

    private fun pressureOnTheLeft() {
        // One wind everywhere, but the left of the course has half a knot more in it.
        val field = CourseFixtures.field(area, samplesPerCell = 60) { cell, index, _ ->
            CourseFixtures.Sample(swing(0.0, 2.0, index), if (cell.column < 6) 3.5 else 2.7)
        }
        val plan = gallery(
            "04-pressure-on-the-left",
            "More wind on the left",
            "The wind is the same direction everywhere; the boat simply goes faster on the left, which the speed " +
                "histogram of every square records. The line goes and gets that pressure.",
            field,
        )
        assertTrue(sideOf(plan.safe) < -60.0, "the line did not go for the pressure: ${sideOf(plan.safe)}")
    }

    private fun puffyRight() {
        // Both halves average 3 m/s; the right half gets there by being half puff and half hole.
        val field = CourseFixtures.field(area, samplesPerCell = 60) { cell, index, _ ->
            val speed = if (cell.column < 6) 3.0 else if (index % 2 == 0) 4.2 else 1.8
            CourseFixtures.Sample(swing(0.0, 2.0, index), speed)
        }
        val plan = gallery(
            "05-puffy-on-the-right",
            "A puffy right, a steady left",
            "Both halves of the course average exactly the same boat speed, but the right is half puff and half " +
                "hole. Only the histogram can tell them apart: the race line stays in the steady half and the " +
                "flyer goes hunting the puffs.",
            field,
        )
        assertTrue(!plan.agree, "the same mean speed with a very different spread is still a bet")
        assertTrue(sideOf(plan.fast) > sideOf(plan.safe), "the flyer should be the one in the puffs")
    }

    private fun shiftyRight() {
        // The same mean wind either side, but the right half swings 20 degrees and the left barely moves.
        val field = CourseFixtures.field(area, samplesPerCell = 60) { cell, index, _ ->
            CourseFixtures.Sample(swing(0.0, if (cell.column >= 6) 20.0 else 2.0, index), 3.0)
        }
        val plan = gallery(
            "06-shifty-right",
            "A shifty right, a steady left",
            "The wind averages the same on both sides, but nobody can be sure of the right: it has been swinging " +
                "20 degrees either way. The race line keeps out of it; the flyer goes there, and the fan of grey " +
                "lines shows how much the simulations disagree about it.",
            field,
        )
        assertTrue(!plan.agree, "an uncertain side of the course is a gamble worth naming")
        assertTrue(sideOf(plan.fast) > sideOf(plan.safe), "the flyer should go into the shifty half")
        assertTrue(plan.fastRisk.spreadSeconds > plan.safeRisk.spreadSeconds)
    }

    private fun headedRightNow() {
        val field = CourseFixtures.even(area, samplesPerCell = 60)
        val plan = gallery(
            "07-headed-right-now",
            "Beating on port in a header",
            "The course has averaged one steady wind, but the boat is on port tack and measuring 20 degrees of " +
                "backed wind right now. That reading carries the squares around the boat and fades over a couple " +
                "of hundred metres, so the line leaves on the lifted tack and straightens out higher up.",
            field,
            currentTack = Tack.PORT,
            currentShiftDegrees = -20.0,
        )
        val ignoring = RaceLinePlanner.plan(field, boat, mark, 45, Tack.PORT, null, settings)
        // Port tack in a wind backed 20 degrees points 20 degrees higher than port tack in the mean wind,
        // less what the square's own long-run mean pulls back: the first board is the thing to look at.
        assertEquals(45.0, firstBearing(ignoring.safe), 5.0, "the fixture means to start on port in an even wind")
        assertEquals(
            45.0 - 20.0 * settings.currentWindWeight,
            firstBearing(plan.safe),
            8.0,
            "the wind measured right now did not carry the squares around the boat",
        )
        assertTrue(
            firstBearing(plan.safe) < firstBearing(ignoring.safe) - 8.0,
            "the header did not lift the first board: ${firstBearing(plan.safe)} against ${firstBearing(ignoring.safe)}",
        )
    }

    private fun unsailedMiddle() {
        // The fleet has beaten up both sides of the course and nobody has been up the middle of it.
        val field = CourseFixtures.field(area, samplesPerCell = 60) { cell, index, _ ->
            when {
                cell.column <= 2 -> CourseFixtures.Sample(swing(-10.0, 3.0, index), 3.0)
                cell.column >= 9 -> CourseFixtures.Sample(swing(10.0, 3.0, index), 3.2)
                else -> null
            }
        }
        val plan = gallery(
            "08-nobody-up-the-middle",
            "Nobody has sailed the middle",
            "Only the two sides of the course have been sailed. The middle is an interpolation, and the model " +
                "knows it: those squares are drawn with a far wider wedge, so the simulations disagree most there.",
            field,
        )
        val middle = field.at(GridCell(5, 6))
        val sailed = field.at(GridCell(1, 6))
        assertTrue(!middle.measured && sailed.measured)
        assertTrue(
            middle.spreadDegrees > sailed.spreadDegrees + 2.0,
            "the middle should be the least certain part of the course",
        )
        assertTrue(plan.safe.points.last() == mark)
    }

    private fun narrowCorridor() {
        // A river or a channel: 120 m wide and 480 m long, and the beat has to zigzag up it.
        val corridor = GridSpec(-60.0, 0.0, 3, 12, 40.0)
        val field = CourseFixtures.field(corridor, samplesPerCell = 60) { _, index, _ ->
            CourseFixtures.Sample(swing(0.0, 3.0, index), 3.0)
        }
        val plan = gallery(
            "09-narrow-corridor",
            "A narrow course",
            "The racing area is only as wide as what has been sailed. In a 120 m corridor the beat bounces off " +
                "both walls, and every simulation agrees about it because there is nowhere else to go.",
            field,
            from = CoursePosition(0.0, 10.0),
            to = CoursePosition(0.0, 470.0),
        )
        assertTrue(plan.safe.tacks >= 4, "a corridor has to be tacked up: ${plan.safe.tacks}")
        assertTrue(plan.safe.points.all { it.acrossMeters in -60.0..60.0 }, "the line left the corridor")
    }

    private fun patchyWater() {
        // Neither one thing nor the other: patches of shift and pressure over the whole course.
        val field = CourseFixtures.field(area, samplesPerCell = 60, seed = 20260908L) { cell, index, random ->
            val patch = (cell.column / 4) - (cell.row / 4)
            CourseFixtures.Sample(
                swing(patch * 7.0 + random.nextDouble(-3.0, 3.0), 6.0, index),
                2.7 + patch * 0.15 + random.nextDouble(-0.2, 0.2),
            )
        }
        val plan = gallery(
            "10-patchy-water",
            "Patchy water",
            "Shifts and pressure in patches, none of them decisive. This is what most of a race looks like: the " +
                "line picks its way through, and the grey fan shows how much of that is worth trusting.",
            field,
        )
        assertTrue(plan.safe.points.size >= 3, "a patchy course is not one board: ${plan.safe}")
        assertTrue(plan.safeRisk.badSeconds > plan.safeRisk.goodSeconds, "a patchy course has a good day and a bad one")
    }
}
