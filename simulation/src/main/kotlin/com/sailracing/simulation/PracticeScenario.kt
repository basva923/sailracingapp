package com.sailracing.simulation

import com.sailracing.domain.geo.Angles
import com.sailracing.domain.geo.Geo
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.race.RaceEvent
import com.sailracing.domain.wind.Tack
import kotlin.math.roundToInt

/**
 * A practice session on a windward-leeward course, for judging the race line against a wind that is known.
 *
 * The boat first sails [Config.laps] laps up and down the course - five by default - each one in a corridor
 * of its own width and starting on the other tack, so both sides of the racing area get sailed and every
 * square of it gets measured. Only then does it start: a countdown, a timed start, and one last beat to the
 * windward mark with the whole afternoon's wind behind the app's advice. That last beat is the one to watch,
 * because by then the race line is drawn from a wind field that has actually been measured everywhere.
 *
 * 1. Sail along the line and mark the pin end and the boat end.
 * 2. Enter a deliberately wrong wind direction and tack angle, then correct it from each tack, and enter the
 *    windward mark as the committee posted it: a bearing and a distance from the line.
 * 3. Beat to the windward mark and run back to the leeward mark, [Config.laps] times over.
 * 4. Drop below the line, start the countdown a few seconds late and sync it.
 * 5. Cross at the gun and beat to the windward mark.
 */
public object PracticeScenario {

    public const val MARK_PIN: String = "mark pin"
    public const val MARK_BOAT: String = "mark boat"
    public const val WIND_STARBOARD: String = "wind from starboard tack"
    public const val WIND_PORT: String = "wind from port tack"
    public const val TO_PRESTART: String = "to pre-start area"
    public const val COUNTDOWN_SYNC: String = "countdown sync"
    public const val START: String = "start"
    public const val RACE_BEAT: String = "race beat"

    /** The milestone of the beat of lap [lap] (1-based), and of its run back. */
    public fun beatLeg(lap: Int): String = "beat $lap"
    public fun runLeg(lap: Int): String = "run $lap"

    public const val DEFAULT_LAPS: Int = 5
    public const val LATE_PRESS_SECONDS: Double = 3.0

    /** Far enough below the line that the boat can bear away to it from the leeward mark. */
    public const val PRESTART_STANDOFF_METERS: Double = 100.0

    /** The stretch of the course a lap beats up: [halfWidthMeters] either side of [centerMeters]. */
    public data class LapCorridor(val halfWidthMeters: Double, val centerMeters: Double = 0.0)

    /**
     * Which stretch of the course each lap beats up, lap by lap (cycled when there are more laps than
     * corridors): up the middle, wider up the middle, up the left, up the right, and up the middle again.
     * Five laps like that leave no square of the racing area unmeasured, and both corners sailed through
     * rather than guessed at.
     */
    public val DEFAULT_CORRIDORS: List<LapCorridor> = listOf(
        LapCorridor(halfWidthMeters = 80.0),
        LapCorridor(halfWidthMeters = 140.0),
        LapCorridor(halfWidthMeters = 100.0, centerMeters = -100.0),
        LapCorridor(halfWidthMeters = 100.0, centerMeters = 100.0),
        LapCorridor(halfWidthMeters = 140.0),
    )

    /**
     * @property wind the wind the whole scenario is defined by.
     * @property laps how many windward-leeward laps are sailed before the start.
     * @property corridors the stretch of the course each lap beats up, in turn.
     * @property raceCorridorHalfWidthMeters the corridor of the beat after the start; null to sail out
     *   to the laylines in one board.
     */
    public data class Config(
        val wind: WindModel,
        val lineCenter: GeoPoint = GeoPoint(51.14, 5.83),
        val model: SailingModel = SailingModel(),
        val noise: NoiseModel = NoiseModel.none(),
        val lineLengthMeters: Double = 100.0,
        val beatLengthMeters: Double = 400.0,
        val laps: Int = DEFAULT_LAPS,
        val corridors: List<LapCorridor> = DEFAULT_CORRIDORS,
        val raceCorridorHalfWidthMeters: Double? = 150.0,
        val countdownMinutes: Int = 5,
        val wrongWindOffsetDegrees: Int = 15,
    ) {
        init {
            require(laps >= 1) { "a practice session needs at least one lap: $laps" }
            require(corridors.isNotEmpty()) { "a lap needs a corridor to beat up" }
        }

        /** The corridor lap [lap] (1-based) beats up. */
        public fun corridorFor(lap: Int): LapCorridor = corridors[(lap - 1) % corridors.size]
    }

    public fun course(config: Config): RaceCourse =
        RaceCourse.square(config.lineCenter, config.wind.meanDirectionDegrees, config.lineLengthMeters, config.beatLengthMeters)

    /** The boat's state at the beginning: abeam of the pin, reaching towards it along the line. */
    public fun initialState(config: Config): BoatState {
        val course = course(config)
        val wind = config.wind.meanDirectionDegrees
        return BoatState(
            timeSeconds = 0.0,
            position = Geo.destination(course.pinEnd, Angles.normalize(wind - 90.0), 60.0),
            headingDegrees = Angles.normalize(wind + 90.0),
            speedMps = config.model.polar.speedMps(90.0, config.wind.speedKnotsAt(0.0)),
        )
    }

    /** The scripted legs, in order. */
    public fun legs(config: Config): List<Leg> {
        val course = course(config)
        val wind = config.wind.meanDirectionDegrees
        val polar = config.model.polar
        val prestartArea = Geo.destination(course.lineCenter, Angles.normalize(wind + 180.0), PRESTART_STANDOFF_METERS)
        val gunAfterSync = config.countdownMinutes * 60.0

        return buildList {
            add(
                Leg(MARK_PIN, helm = { GoTo(course.pinEnd) }, until = Termination.Within(course.pinEnd, 4.0),
                    onEnd = listOf { RaceEvent.MarkPinEnd }),
            )
            add(
                Leg(MARK_BOAT, helm = { GoTo(course.boatEnd) }, until = Termination.Within(course.boatEnd, 4.0),
                    onEnd = listOf { RaceEvent.MarkBoatEnd }),
            )
            add(
                Leg(WIND_STARBOARD, helm = { CloseHauled(Tack.STARBOARD) }, until = Termination.After(40.0),
                    onStart = listOf(
                        { RaceEvent.SetWindDirection(Angles.normalize(wind.toInt() + config.wrongWindOffsetDegrees)) },
                        { RaceEvent.SetTackAngle(polar.upwindAngleDegrees.toInt()) },
                        { RaceEvent.SetDownwindAngle(polar.downwindAngleDegrees.toInt()) },
                    ),
                    onEnd = listOf { RaceEvent.SetWindFromStarboardTack }),
            )
            add(
                Leg(WIND_PORT, helm = { CloseHauled(Tack.PORT) }, until = Termination.After(40.0),
                    onEnd = listOf(
                        { RaceEvent.SetWindFromPortTack },
                        { RaceEvent.SetWindwardMarkFromLine(wind.roundToInt(), config.beatLengthMeters) },
                    )),
            )
            // The laps: the whole point of the scenario, and where the wind field is measured.
            for (lap in 1..config.laps) {
                val tack = if (lap % 2 == 1) Tack.STARBOARD else Tack.PORT
                val corridor = config.corridorFor(lap)
                add(
                    Leg(beatLeg(lap), helm = { BeatTo(course.windwardMark, tack, corridor.halfWidthMeters, corridor.centerMeters) },
                        until = Termination.Within(course.windwardMark, MARK_RADIUS_METERS)),
                )
                add(
                    Leg(runLeg(lap), helm = { RunTo(course.leewardMark, tack) },
                        until = Termination.Within(course.leewardMark, MARK_RADIUS_METERS)),
                )
            }
            add(Leg(TO_PRESTART, helm = { GoTo(prestartArea) }, until = Termination.Within(prestartArea, 8.0)))
            add(
                Leg(COUNTDOWN_SYNC, helm = { HoldWindAngle(90.0) }, until = Termination.After(LATE_PRESS_SECONDS),
                    onStart = listOf { now -> RaceEvent.StartCountdown(config.countdownMinutes, now) },
                    onEnd = listOf { now -> RaceEvent.SyncCountdown(now) }),
            )
            add(
                Leg(START, helm = { legStart -> TimedStart(legStart + gunAfterSync, PRESTART_STANDOFF_METERS) },
                    until = Termination.OnCourseSide),
            )
            add(
                Leg(RACE_BEAT, helm = { BeatTo(course.windwardMark, Tack.STARBOARD, config.raceCorridorHalfWidthMeters) },
                    until = Termination.Within(course.windwardMark, MARK_RADIUS_METERS),
                    onEnd = listOf { RaceEvent.StopTimer }),
            )
        }
    }

    public fun build(config: Config): SimulationResult =
        ScenarioRunner(course(config), config.wind, config.model, config.noise).run(initialState(config), legs(config))

    /** The simulated time of the starting gun, after the sync. */
    public fun gunMillis(result: SimulationResult, config: Config): Long =
        result.milestone(COUNTDOWN_SYNC) + config.countdownMinutes * 60_000L

    /** A mark is rounded when the boat is this close to it. */
    private const val MARK_RADIUS_METERS = 15.0
}
