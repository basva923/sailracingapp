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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Ten racing areas, drawn to `docs/raceline` so that the plan can be checked by eye: the mean wind of every
 * big square with how much it wandered, the pressure over the course, the best beat of every simulation
 * that was searched, and the two lines the plan picks out of them.
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
        aSwingingDay()
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
            appendLine("mark **M** at the top. The thin grid is the racing area, the squares the line bends on; the bold")
            appendLine("one is the big squares the wind is worked out in, three across the course and as many rows as it")
            appendLine("is tall. In each big square an arrow points the way its mean wind blows, amber where it is veered")
            appendLine("and blue where it is backed, dim where too little was measured there to call it measured; the pale")
            appendLine("wedge behind the arrow is how far the simulated winds spread there - its own history, the day's and")
            appendLine("chance all told - and the shading of the square is the boat speed measured in it, lighter for more")
            appendLine("pressure. The white dot is the boat, with an arrow for the wind it is measuring right now where it")
            appendLine("has one.")
            appendLine()
            appendLine("The thin grey lines are the fastest beat of each of the ${settings.searched} winds a beat was searched in, out of the")
            appendLine("${settings.runs} every line is then timed through - the spread of opinion. The **green** line is the race line the")
            appendLine("map draws, the one whose bad day is least bad. The **amber dashed** line, where there is one, is the")
            appendLine("flyer: the line that pays most when the wind is kind, drawn only when it is worth at least a tack")
            appendLine("more than the race line on a good day.")
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
            val speed = if (cell.column < 6) 3.0 else if (index % 2 == 0) 4.8 else 1.2
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

    private fun aSwingingDay() {
        // The wind has swung 25 degrees either way all day, everywhere on the course.
        val field = CourseFixtures.field(area, samplesPerCell = 60) { _, index, _ ->
            CourseFixtures.Sample(swing(0.0, 25.0, index), 3.0)
        }
        val plan = gallery(
            "06-a-swinging-day",
            "A day of big shifts",
            "The wind has swung 25 degrees either way all day and it has done it everywhere, so every square " +
                "is drawn out of that same wide histogram. The simulations disagree wildly - look at the fan - " +
                "and no side of the course is a better bet than another, so the line keeps out of the corners, " +
                "where one shift would decide the whole beat.",
            field,
        )
        assertTrue(abs(sideOf(plan.safe)) < 220.0, "the line went to a corner of a course that swings all over: ${sideOf(plan.safe)}")
        assertTrue(plan.safeRisk.riskSeconds > 20.0, "a day of big shifts is a wide spread of times: ${plan.safeRisk}")
        assertTrue(plan.agree, "no side of a course that swings everywhere is the better gamble")
    }

    private fun headedRightNow() {
        val field = CourseFixtures.even(area, samplesPerCell = 60)
        val plan = gallery(
            "07-headed-right-now",
            "Beating on port in a header",
            "Everything measured so far says the wind is up the course, but the boat is on port and measuring " +
                "it 20 degrees backed right now. That header is a third of the simulated winds, over the whole " +
                "course at once, and the rest are the day as it has been - so the plan leans into it without " +
                "betting the beat on one moment: the line comes back sooner than the day's wind alone would " +
                "have it, and starting on starboard would cost a tack.",
            field,
            currentTack = Tack.PORT,
            currentShiftDegrees = -20.0,
        )
        val ignoring = RaceLinePlanner.plan(field, boat, mark, 45, Tack.PORT, null, settings)
        assertEquals(45.0, firstBearing(ignoring.safe), 5.0, "the fixture means to start on port in an even wind")
        // The wind as it stands is the day's wind pulled a third of the way onto the header, and the beat
        // that suits it starts that much higher on port.
        val mean = RaceLineFinder.find(WindSampler.of(field, -20.0).mean, boat, mark, 45, Tack.PORT)
        assertEquals(
            45.0 - 20.0 * WindFieldSettings().currentWindFraction,
            firstBearing(mean),
            3.0,
            "the wind of the moment did not carry a third of the wind the beat is worked out in",
        )
        assertTrue(
            sideOf(plan.safe) < sideOf(ignoring.safe) - 20.0,
            "the header did not pull the beat back: ${sideOf(plan.safe)} against ${sideOf(ignoring.safe)}",
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
        val middle = field.at(GridCell(1, 1))
        val sailed = field.at(GridCell(0, 1))
        assertTrue(!middle.measured && sailed.measured, "the fixture means to leave the middle block unsailed")
        assertEquals(0.0, middle.share, 1e-9, "an unsailed square has nothing of its own to say")
        assertTrue(
            assertNotNull(field.courseWinds.standardDeviation()) > assertNotNull(sailed.winds.standardDeviation()) + 2.0,
            "the wind the middle falls back on should be wider than what the sailed side measured",
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
