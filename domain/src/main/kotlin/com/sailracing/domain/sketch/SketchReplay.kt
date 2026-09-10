package com.sailracing.domain.sketch

import com.sailracing.domain.model.PositionFix
import com.sailracing.domain.race.RaceEvent

/**
 * Sails a drawn track: turns a [CourseSketch] into the stream of events a race session would have seen
 * had a boat sailed it, so that a hand-drawn course reaches the strategy through the app's own front door
 * and not through a back one built for testing.
 *
 * The path is walked at the speed of the point it leaves, and a fix is recorded every
 * [FIX_INTERVAL_MILLIS] - the rate a phone's GPS reports at, and the rate the session samples the wind at.
 * Each fix carries the course over ground of the leg it is on, which is what the app reads the wind off:
 * a leg drawn at 45° to the wind is a boat close-hauled on port, and the wind it implies is what the
 * square it crosses ends up believing. Drawing a beat that fans to the right of the course is drawing a
 * right-hand veer, and the strategy has no way of telling that from a real one.
 *
 * A corner in the drawing is a tack: the heading jumps between one fix and the next, the session sees a
 * turn faster than [com.sailracing.domain.race.RaceSettings.maxSamplingTurnRateDegreesPerSecond] and
 * throws that one sample away, exactly as it does on the water.
 */
public object SketchReplay {

    /** One fix a second, which is what a phone gives and what the session samples at. */
    public const val FIX_INTERVAL_MILLIS: Long = 1_000L

    /** The accuracy a drawn fix claims: a clean fix, since a drawing has no GPS noise to report. */
    public const val ACCURACY_METERS: Double = 3.0

    /**
     * Everything the session needs to know about [sketch], in order: the settings and the wind first, then
     * the line and the mark, then the boat sailing its track from [startTimeMillis].
     */
    public fun events(sketch: CourseSketch, startTimeMillis: Long = 0L): List<RaceEvent> = buildList {
        add(RaceEvent.UpdateSettings(sketch.settings))
        add(RaceEvent.SetWindSettings(sketch.wind))
        add(RaceEvent.SetStartLine(sketch.startLine))
        add(RaceEvent.SetWindwardMark(sketch.windwardMarkGeo))
        for (fix in fixes(sketch, startTimeMillis)) add(RaceEvent.FixReceived(fix))
    }

    /**
     * The drawn track as a boat's GPS would have reported it: a fix a second along the path, and a last
     * one where the drawing stops, so the boat ends exactly where the sailor left it and the route to the
     * mark is asked for from there.
     *
     * Empty for a track of fewer than two points, and for one drawn on the spot: neither is a sail.
     */
    public fun fixes(sketch: CourseSketch, startTimeMillis: Long = 0L): List<PositionFix> {
        val track = sketch.track
        val plane = sketch.plane
        val fixes = mutableListOf<PositionFix>()
        var time = startTimeMillis
        // How far into the leg being sailed the boat is. A leg hands the leftovers of its last second to
        // the next one, so the boat keeps its pace across a corner instead of restarting the clock at it.
        var sailed = 0.0
        var speedMps = 0.0
        var headingDegrees = 0.0
        for (index in 0 until track.size - 1) {
            val from = track[index]
            val to = track[index + 1]
            val length = from.position.distanceTo(to.position)
            if (length <= 0.0) continue
            speedMps = from.speedMps
            headingDegrees = from.position.bearingTo(to.position)
            while (sailed < length) {
                val position = from.position.towards(to.position, sailed / length)
                fixes += PositionFix(plane.toGeo(position), time, speedMps, headingDegrees, ACCURACY_METERS)
                time += FIX_INTERVAL_MILLIS
                sailed += speedMps * (FIX_INTERVAL_MILLIS / 1000.0)
            }
            sailed -= length
        }
        if (fixes.isEmpty()) return emptyList()
        // The last second of a drawing is a short one: what matters is that the boat is where the sailor
        // stopped drawing, because that is where the race line is searched from.
        fixes += PositionFix(plane.toGeo(track.last().position), time, speedMps, headingDegrees, ACCURACY_METERS)
        return fixes
    }
}
