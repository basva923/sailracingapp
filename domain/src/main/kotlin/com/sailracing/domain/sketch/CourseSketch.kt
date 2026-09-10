package com.sailracing.domain.sketch

import com.sailracing.domain.course.CourseSettings
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.geo.LocalPlane
import com.sailracing.domain.geo.PlanePosition
import com.sailracing.domain.race.RaceSettings
import com.sailracing.domain.startline.StartLine
import com.sailracing.domain.wind.WindSettings

/**
 * One point of a drawn track: where the boat was, and how fast it was going there.
 *
 * The speed is per point rather than per sketch because it is half of what a course says: a drawn beat
 * that is slow on the left and quick on the right is pressure on the right, and that is exactly the kind
 * of course worth asking the strategy about.
 */
public data class SketchPoint(
    val position: PlanePosition,
    val speedMps: Double = CourseSketch.DEFAULT_BOAT_SPEED_MPS,
) {
    init {
        require(speedMps > 0.0) { "a boat that does not move leaves no track: $speedMps" }
    }
}

/**
 * A course drawn by hand: a start line, a windward mark, a wind, and the track of a boat that sailed it.
 *
 * It is the input to the strategy without a boat, a phone or a day on the water - the thing the desktop
 * workbench draws and [SketchAnalysis] runs through the app's own race engine. Everything on it is in
 * metres on a north-up [LocalPlane] around [origin], so setting another wind turns the strategy's view of
 * the course and leaves the drawing where it was drawn.
 *
 * @property origin where on earth the drawing sits. Nothing tactical depends on it - the strategy works in
 *   metres - but a sketch is projected onto real coordinates to be fed to the engine, and a sketch given
 *   the coordinates of a real race area can be compared with a real track from it.
 * @property wind the wind the sailor sets: the direction it blows from, the boat's tack angle and its
 *   downwind angle. It is the *set* wind, which the app replaces with the measured mean once the track has
 *   given it enough samples; [SketchAnalysis.reference] is the one the strategy actually judged by.
 * @property track where the boat has been, in order. The boat is at the end of it, which is where a route
 *   to the mark is asked for.
 * @property settings the app's own tunables, including how the racing area is cut into squares and how
 *   hard the race line is searched for ([RaceSettings.course]).
 */
public data class CourseSketch(
    val name: String = DEFAULT_NAME,
    val origin: GeoPoint = DEFAULT_ORIGIN,
    val wind: WindSettings = WindSettings(),
    val pinEnd: PlanePosition? = null,
    val boatEnd: PlanePosition? = null,
    val windwardMark: PlanePosition? = null,
    val track: List<SketchPoint> = emptyList(),
    val settings: RaceSettings = RaceSettings(),
) {
    /** The north-up metre grid the drawing lives on. */
    public val plane: LocalPlane = LocalPlane(origin)

    /** The drawn line as the app knows it. Either end may be missing, exactly as on the water. */
    public val startLine: StartLine
        get() = StartLine(pinEnd?.let(plane::toGeo), boatEnd?.let(plane::toGeo))

    /** The set mark on the earth, or null when none is drawn and the top of the racing area stands in. */
    public val windwardMarkGeo: GeoPoint?
        get() = windwardMark?.let(plane::toGeo)

    /** Where the boat is: the end of the track it has sailed. */
    public val boat: PlanePosition?
        get() = track.lastOrNull()?.position

    /** The same sketch with one more point sailed. */
    public fun plusPoint(position: PlanePosition, speedMps: Double = DEFAULT_BOAT_SPEED_MPS): CourseSketch =
        copy(track = track + SketchPoint(position, speedMps))

    /** The same sketch, unsailed: the line, the mark and the wind stay. */
    public fun withoutTrack(): CourseSketch = copy(track = emptyList())

    /**
     * The same sketch with the course dials turned: how the racing area is cut into squares, what its
     * wind is believed to be like, and how hard the race line is searched for.
     */
    public fun withCourseSettings(transform: CourseSettings.() -> CourseSettings): CourseSketch =
        copy(settings = settings.copy(course = settings.course.transform()))

    public companion object {
        public const val DEFAULT_NAME: String = "Untitled course"

        /** A dinghy beating in a working breeze: the speed a freshly drawn track is sailed at. */
        public const val DEFAULT_BOAT_SPEED_MPS: Double = 3.0

        /**
         * Where a sketch sits until it is told otherwise: open water at 52° N. Only the latitude does
         * anything at all - it is what a metre of longitude is worth - and a degree either way of it moves
         * a 500 m beat by less than a centimetre.
         */
        public val DEFAULT_ORIGIN: GeoPoint = GeoPoint(52.0, 5.0)
    }
}
