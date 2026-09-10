package com.sailracing.domain.sketch

import com.sailracing.domain.course.GridSettings
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.geo.PlanePosition
import com.sailracing.domain.wind.WindSettings
import java.util.Locale

/** A sketch that could not be read, with the line that stopped it. */
public class SketchFormatException(message: String) : IllegalArgumentException(message)

/**
 * Reading and writing a [CourseSketch] as text, so that a course worth arguing about can be kept, sent to
 * somebody, put beside a test and read in a diff a year later.
 *
 * One statement a line, a keyword and its values, metres and degrees:
 *
 * ```
 * sketch 1
 * name Pressure on the left
 * origin 52.0000000 5.0000000
 * wind 225 45 140
 * cell 20.0
 * runs 16
 * pin -60.0 0.0
 * start 60.0 0.0
 * mark 0.0 600.0
 * point -55.0 4.0 3.00
 * point -48.0 18.0 3.00
 * ```
 *
 * Blank lines and lines opening with `#` are ignored, and so is any keyword this version does not know:
 * a sketch written by a later one still reads here, minus whatever it learned to say in between. What is
 * kept is what makes a course a course - the line, the mark, the wind, the track and the two dials that
 * change what the strategy does with them - and everything else is left at its default.
 */
public object CourseSketchFormat {

    /** The first line of every sketch: what it is and which version of this format wrote it. */
    public const val HEADER: String = "sketch 1"

    public fun write(sketch: CourseSketch): String = buildString {
        appendLine(HEADER)
        appendLine("name ${sketch.name}")
        appendLine("origin ${degrees(sketch.origin.latitude)} ${degrees(sketch.origin.longitude)}")
        appendLine("wind ${sketch.wind.directionDegrees} ${sketch.wind.tackAngleDegrees} ${sketch.wind.downwindAngleDegrees}")
        sketch.settings.course.grid.cellSizeMeters?.let { appendLine("cell ${meters(it)}") }
        appendLine("runs ${sketch.settings.course.raceLine.runs}")
        sketch.pinEnd?.let { appendLine("pin ${position(it)}") }
        sketch.boatEnd?.let { appendLine("start ${position(it)}") }
        sketch.windwardMark?.let { appendLine("mark ${position(it)}") }
        for (point in sketch.track) appendLine("point ${position(point.position)} ${speed(point.speedMps)}")
    }

    public fun read(text: String): CourseSketch {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
        val header = lines.firstOrNull() ?: throw SketchFormatException("an empty file is not a sketch")
        if (header != HEADER) throw SketchFormatException("expected '$HEADER' on the first line, found '$header'")

        var sketch = CourseSketch()
        var cellSizeMeters: Double? = null
        var runs = sketch.settings.course.raceLine.runs
        val track = mutableListOf<SketchPoint>()
        for (line in lines.drop(1)) {
            val words = line.split(WHITESPACE)
            when (words[0]) {
                "name" -> sketch = sketch.copy(name = line.removePrefix("name").trim())
                "origin" -> sketch = sketch.copy(origin = GeoPoint(number(words, 1, line), number(words, 2, line)))
                "wind" -> sketch = sketch.copy(
                    wind = WindSettings(
                        directionDegrees = number(words, 1, line).toInt(),
                        tackAngleDegrees = number(words, 2, line).toInt(),
                        downwindAngleDegrees = number(words, 3, line).toInt(),
                    ),
                )
                "cell" -> cellSizeMeters = number(words, 1, line)
                "runs" -> runs = number(words, 1, line).toInt()
                "pin" -> sketch = sketch.copy(pinEnd = plane(words, line))
                "start" -> sketch = sketch.copy(boatEnd = plane(words, line))
                "mark" -> sketch = sketch.copy(windwardMark = plane(words, line))
                "point" -> track += SketchPoint(plane(words, line), number(words, 3, line))
                // A keyword from a later version of the format: whatever it says, this one sails without it.
                else -> Unit
            }
        }
        val course = sketch.settings.course
        return sketch.copy(
            track = track,
            settings = sketch.settings.copy(
                course = course.copy(
                    grid = GridSettings(cellSizeMeters, course.grid.maxCellsPerSide),
                    raceLine = course.raceLine.copy(runs = runs),
                ),
            ),
        )
    }

    private val WHITESPACE = Regex("\\s+")

    private fun plane(words: List<String>, line: String): PlanePosition =
        PlanePosition(number(words, 1, line), number(words, 2, line))

    private fun number(words: List<String>, index: Int, line: String): Double {
        val word = words.getOrNull(index) ?: throw SketchFormatException("'$line' needs ${index + 1} values")
        return word.toDoubleOrNull() ?: throw SketchFormatException("'$word' is not a number, in '$line'")
    }

    private fun degrees(value: Double): String = String.format(Locale.ROOT, "%.7f", value)

    private fun meters(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

    private fun speed(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

    private fun position(position: PlanePosition): String =
        "${meters(position.eastMeters)} ${meters(position.northMeters)}"
}
