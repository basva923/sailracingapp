package com.sailracing.domain.course

import com.sailracing.domain.geo.Angles
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Draws a racing area and the plan for it as a picture, so that the algorithm can be checked with an eye
 * rather than only with an assertion: the mean wind in every big square with how much it wandered there,
 * how much pressure there is, every line the simulations came up with, and the two the plan picked out of
 * them. The thin grid is the racing area itself, the squares the line bends on; the thick one is the big
 * squares the wind is drawn in.
 *
 * This lives in the tests because it is a way of looking at the domain, not a part of it. [RaceLineGalleryTest]
 * writes the whole gallery to `docs/raceline`.
 */
object RaceLineGallery {

    private const val MAP_PIXELS = 940
    private const val MARGIN = 30
    private const val CAPTION_HEIGHT = 210

    private val BACKGROUND = Color(0x0A0A0A)
    private val SURFACE = Color(0x141414)
    private val GRID = Color(0x1E2226)
    private val BLOCK_GRID = Color(0x333A41)
    private val WHITE = Color(0xFFFFFF)
    private val MUTED = Color(0x9AA0A6)
    private val GREEN = Color(0x3DDC84)
    private val AMBER = Color(0xFFC107)
    private val BLUE = Color(0x4FC3F7)

    /** One picture of a plan: the area, the wind, the simulations and the two lines. */
    fun draw(
        file: File,
        title: String,
        expectation: String,
        field: WindField,
        boat: CoursePosition,
        mark: CoursePosition,
        plan: RaceLinePlan,
        settings: RaceLineSettings = RaceLineSettings(),
        currentShiftDegrees: Double? = null,
    ) {
        val area = field.cellSpec
        val spec = field.spec
        val sampler = WindSampler.of(field, currentShiftDegrees)
        val mean = sampler.mean
        val spreads = spreadsOf(sampler, spec)
        val image = BufferedImage(MAP_PIXELS + 2 * MARGIN, MAP_PIXELS + 2 * MARGIN + CAPTION_HEIGHT, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = BACKGROUND
        g.fillRect(0, 0, image.width, image.height)

        // The big squares cover the area, so they are what has to fit the map square.
        val scale = MAP_PIXELS / max(spec.widthMeters, spec.heightMeters)
        val offsetX = MARGIN + (MAP_PIXELS - spec.widthMeters * scale) / 2
        val offsetY = MARGIN + (MAP_PIXELS - spec.heightMeters * scale) / 2
        fun x(acrossMeters: Double) = offsetX + (acrossMeters - spec.leftMeters) * scale
        fun y(upwindMeters: Double) = offsetY + (spec.topMeters - upwindMeters) * scale
        fun px(at: CoursePosition) = doubleArrayOf(x(at.acrossMeters), y(at.upwindMeters))

        val speeds = field.blocks.map { mean.blockSpeedMps(it.cell) }
        val slowest = speeds.min()
        val quickest = speeds.max()

        // Every big square: shaded by how much pressure there is, with the wind drawn around it and how far
        // that wind wandered where it was measured.
        val blockPixels = spec.cellSizeMeters * scale
        for (block in field.blocks) {
            val cell = block.cell
            val shiftDegrees = block.meanShiftDegrees ?: field.courseShiftDegrees ?: 0.0
            val left = x(spec.leftMeters + cell.column * spec.cellSizeMeters)
            val top = y(spec.bottomMeters + (cell.row + 1) * spec.cellSizeMeters)
            g.color = pressureColour(mean.blockSpeedMps(cell), slowest, quickest)
            g.fillRect(left.roundToInt(), top.roundToInt(), blockPixels.roundToInt() + 1, blockPixels.roundToInt() + 1)

            val centreX = left + blockPixels / 2
            val centreY = top + blockPixels / 2
            val colour = shiftColour(shiftDegrees, block.measured)
            // How far the drawn winds here actually spread, as the wedge they come out of: everything the
            // square might blow, its own history and the day's and chance all told.
            val spread = spreads[spec.index(cell)]
            g.color = Color(colour.red, colour.green, colour.blue, 46)
            val radius = blockPixels * 0.36
            g.fill(
                Arc2D.Double(
                    centreX - radius, centreY - radius, radius * 2, radius * 2,
                    // Java measures its arcs anticlockwise from east; the frame's angles are clockwise from up.
                    90.0 - (shiftDegrees + 180.0) - spread, 2 * spread, Arc2D.PIE,
                ),
            )
            arrow(g, centreX, centreY, shiftDegrees + 180.0, blockPixels * 0.62, colour, if (block.measured) 3f else 2f)
        }

        // The racing area under it all: the squares the search bends the line on.
        g.stroke = BasicStroke(1f)
        g.color = GRID
        for (index in 0 until area.cellCount) {
            val cell = area.cellAt(index)
            val left = x(area.leftMeters + cell.column * area.cellSizeMeters)
            val top = y(area.bottomMeters + (cell.row + 1) * area.cellSizeMeters)
            val side = (area.cellSizeMeters * scale).roundToInt()
            g.drawRect(left.roundToInt(), top.roundToInt(), side, side)
        }
        g.stroke = BasicStroke(2f)
        g.color = BLOCK_GRID
        for (index in 0 until spec.cellCount) {
            val cell = spec.cellAt(index)
            val left = x(spec.leftMeters + cell.column * spec.cellSizeMeters)
            val top = y(spec.bottomMeters + (cell.row + 1) * spec.cellSizeMeters)
            g.drawRect(left.roundToInt(), top.roundToInt(), blockPixels.roundToInt(), blockPixels.roundToInt())
        }

        // Every simulation's own best line, faint: where they lie on top of one another the beat has one
        // answer, where they fan out it does not.
        g.stroke = BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.color = Color(0xB0, 0xB8, 0xC0, 70)
        for (line in plan.sampled) g.draw(pathOf(line.points, ::px))

        if (!plan.agree) {
            g.stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, floatArrayOf(12f, 9f), 0f)
            g.color = AMBER
            g.draw(pathOf(plan.fast.points, ::px))
        }
        g.stroke = BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.color = GREEN
        g.draw(pathOf(plan.safe.points, ::px))

        // The start line's origin, the mark, and the boat.
        g.stroke = BasicStroke(2f)
        g.color = MUTED
        val origin = px(CoursePosition(0.0, 0.0))
        g.drawLine((origin[0] - 22).roundToInt(), origin[1].roundToInt(), (origin[0] + 22).roundToInt(), origin[1].roundToInt())
        val at = px(mark)
        g.color = AMBER
        g.stroke = BasicStroke(3f)
        g.draw(Ellipse2D.Double(at[0] - 11, at[1] - 11, 22.0, 22.0))
        g.font = Font(Font.SANS_SERIF, Font.BOLD, 15)
        g.drawString("M", (at[0] - 5).toFloat(), (at[1] + 5).toFloat())
        val from = px(boat)
        g.color = WHITE
        g.fill(Ellipse2D.Double(from[0] - 8, from[1] - 8, 16.0, 16.0))
        currentShiftDegrees?.let { arrow(g, from[0], from[1], it + 180.0, 74.0, WHITE, 2.5f) }

        caption(g, image.width, MAP_PIXELS + 2 * MARGIN, title, expectation, field, mean, spreads, plan, settings, slowest, quickest, currentShiftDegrees)
        g.dispose()
        file.parentFile.mkdirs()
        ImageIO.write(image, "png", file)
    }

    /** The strip under the map: what the picture is, what to look for, and what the plan costs. */
    private fun caption(
        g: Graphics2D,
        width: Int,
        top: Int,
        title: String,
        expectation: String,
        field: WindField,
        mean: SampledConditions,
        spreads: DoubleArray,
        plan: RaceLinePlan,
        settings: RaceLineSettings,
        slowest: Double,
        quickest: Double,
        currentShiftDegrees: Double?,
    ) {
        g.color = SURFACE
        g.fillRect(0, top, width, CAPTION_HEIGHT)
        val body = Font(Font.SANS_SERIF, Font.PLAIN, 15)
        val room = width - 2 * MARGIN
        var line = top + 32

        g.color = WHITE
        g.font = Font(Font.SANS_SERIF, Font.BOLD, 21)
        g.drawString(title, MARGIN.toFloat(), line.toFloat())
        g.font = body
        for (text in wrap(g, expectation, room)) {
            line += 24
            g.color = MUTED
            g.drawString(text, MARGIN.toFloat(), line.toFloat())
        }

        val shifts = field.blocks.map { it.meanShiftDegrees ?: field.courseShiftDegrees ?: 0.0 }
        val rest = listOf(
            WHITE to "Wind: ${degrees(shifts.min())} to ${degrees(shifts.max())} off the reference" +
                ", drawn ${round(spreads.min())}° to ${round(spreads.max())}° wide" +
                " · speed ${round(slowest)} to ${round(quickest)} m/s" +
                (currentShiftDegrees?.let { " · measuring ${degrees(it)} right now" } ?: ""),
            GREEN to "Race line (green): ${plan.safe.tacks} tacks · ${clock(plan.safeRisk.meanSeconds)} on an average day," +
                " ${clock(plan.safeRisk.goodSeconds)} at best, ${clock(plan.safeRisk.badSeconds)} at worst",
            AMBER to if (plan.agree) {
                "No flyer: over ${plan.runs} simulated winds nothing beat the race line by more than a tack"
            } else {
                "Flyer (amber, dashed): ${plan.fast.tacks} tacks · ${clock(plan.fastRisk.meanSeconds)} on average," +
                    " ${clock(plan.fastRisk.goodSeconds)} at best, ${clock(plan.fastRisk.badSeconds)} at worst" +
                    " · beats the race line in ${(plan.winFraction * plan.runs).roundToInt()} of ${plan.runs} winds"
            },
        )
        line += 8
        for ((colour, text) in rest) {
            for (wrapped in wrap(g, text, room)) {
                line += 24
                g.color = colour
                g.drawString(wrapped, MARGIN.toFloat(), line.toFloat())
            }
        }
    }

    /**
     * How far the winds drawn in each big square spread, in degrees: the circular deviation of a few
     * hundred draws. It is what the wedge behind an arrow shows - not what the square measured, but what
     * the simulations are actually going to sail through, which is the square's own history, the day's and
     * chance all mixed.
     */
    private fun spreadsOf(sampler: WindSampler, spec: GridSpec, runs: Int = 400): DoubleArray {
        val cosines = DoubleArray(spec.cellCount)
        val sines = DoubleArray(spec.cellCount)
        val random = Random(20260908L)
        repeat(runs) {
            val wind = sampler.draw(random)
            for (index in 0 until spec.cellCount) {
                val radians = Angles.toRadians(wind.blockShiftDegrees(spec.cellAt(index)))
                cosines[index] += cos(radians)
                sines[index] += sin(radians)
            }
        }
        return DoubleArray(spec.cellCount) { index ->
            val resultant = (sqrt(cosines[index] * cosines[index] + sines[index] * sines[index]) / runs).coerceIn(1e-9, 1.0)
            Angles.toDegrees(sqrt(-2.0 * ln(resultant)))
        }
    }

    /** [text] broken into lines that fit [room] pixels in the graphics' current font. */
    private fun wrap(g: Graphics2D, text: String, room: Int): List<String> {
        val metrics = g.fontMetrics
        val lines = ArrayList<String>()
        var line = StringBuilder()
        for (word in text.split(" ")) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (metrics.stringWidth(candidate) <= room) {
                line = StringBuilder(candidate)
            } else {
                lines += line.toString()
                line = StringBuilder(word)
            }
        }
        if (line.isNotEmpty()) lines += line.toString()
        return lines
    }

    /** Darker where the boat was slow, lighter where it was quick: the pressure over the course. */
    private fun pressureColour(speedMps: Double, slowest: Double, quickest: Double): Color {
        val fraction = if (quickest - slowest < 1e-9) 0.5 else (speedMps - slowest) / (quickest - slowest)
        return Color(
            (0x10 + 0x10 * fraction).roundToInt(),
            (0x14 + 0x1C * fraction).roundToInt(),
            (0x18 + 0x3A * fraction).roundToInt(),
        )
    }

    /** Amber where the wind is veered, blue where it is backed, dim where it was never measured. */
    private fun shiftColour(shiftDegrees: Double, measured: Boolean): Color {
        val base = when {
            shiftDegrees > 1.5 -> AMBER
            shiftDegrees < -1.5 -> BLUE
            else -> WHITE
        }
        return if (measured) base else Color(base.red, base.green, base.blue, 110)
    }

    private fun arrow(g: Graphics2D, x: Double, y: Double, degrees: Double, length: Double, colour: Color, width: Float) {
        val angle = Angles.toRadians(degrees - 90.0)
        val dx = cos(angle)
        val dy = sin(angle)
        g.color = colour
        g.stroke = BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val tailX = x - dx * length / 2
        val tailY = y - dy * length / 2
        val tipX = x + dx * length / 2
        val tipY = y + dy * length / 2
        g.drawLine(tailX.roundToInt(), tailY.roundToInt(), tipX.roundToInt(), tipY.roundToInt())
        for (wing in listOf(-1, 1)) {
            val direction = angle + Math.PI + wing * Angles.toRadians(26.0)
            g.drawLine(
                tipX.roundToInt(),
                tipY.roundToInt(),
                (tipX + length * 0.3 * cos(direction)).roundToInt(),
                (tipY + length * 0.3 * sin(direction)).roundToInt(),
            )
        }
    }

    private fun pathOf(points: List<CoursePosition>, px: (CoursePosition) -> DoubleArray): Path2D.Double {
        val path = Path2D.Double()
        points.forEachIndexed { index, point ->
            val at = px(point)
            if (index == 0) path.moveTo(at[0], at[1]) else path.lineTo(at[0], at[1])
        }
        return path
    }

    private fun degrees(value: Double): String = "${if (value > 0) "+" else ""}${value.roundToInt()}°"

    private fun round(value: Double): String = ((value * 10).roundToInt() / 10.0).toString()

    private fun clock(seconds: Double): String = "${(seconds / 60).toInt()}:${"%02d".format((seconds % 60).roundToInt().coerceAtMost(59))}"

    /** The repository root, found by walking up from wherever the tests happen to run. */
    fun repositoryRoot(): File {
        var directory: File? = File(System.getProperty("user.dir")).absoluteFile
        while (directory != null && !File(directory, "settings.gradle.kts").isFile) directory = directory.parentFile
        return checkNotNull(directory) { "no repository root above ${System.getProperty("user.dir")}" }
    }

    /** How much of the line lies to one side: the mean across-position of its points, in metres. */
    fun sideOf(line: RaceLine): Double = line.points.map { it.acrossMeters }.average()

    /** The extreme the line reaches, left (negative) or right (positive). */
    fun reachOf(line: RaceLine): Double {
        val left = line.points.minOf { it.acrossMeters }
        val right = line.points.maxOf { it.acrossMeters }
        return if (-left > right) left else right
    }

    /** A wind that swings [swingDegrees] either side of [meanDegrees], sample by sample. */
    fun swing(meanDegrees: Double, swingDegrees: Double, index: Int): Double =
        meanDegrees + if (index % 2 == 0) swingDegrees else -swingDegrees
}
