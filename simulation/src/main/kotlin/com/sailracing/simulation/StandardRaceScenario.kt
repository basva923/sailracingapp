package com.sailracing.simulation

import com.sailracing.domain.geo.Angles
import com.sailracing.domain.geo.Geo
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.race.RaceEvent
import com.sailracing.domain.wind.Tack

/**
 * A complete race, from marking the line to crossing the finish, exercising every feature of the app:
 *
 * 1. Sail along the line and mark the pin end and the boat end.
 * 2. Enter a deliberately wrong wind direction and tack angle, then correct it from each tack.
 * 3. Start a 5 minute countdown a few seconds late and sync it.
 * 4. Shuttle below the line, hold head-to-wind at a launch point and cross at the gun.
 * 5. Beat to the windward mark in oscillating wind (tacking on the laylines).
 * 6. Run to the leeward mark (gybing) and beat back to the finish.
 * 7. Stop the timer.
 */
public object StandardRaceScenario {

    public const val MARK_PIN: String = "mark pin"
    public const val MARK_BOAT: String = "mark boat"
    public const val WIND_STARBOARD: String = "wind from starboard tack"
    public const val WIND_PORT: String = "wind from port tack"
    public const val TO_PRESTART: String = "to pre-start area"
    public const val COUNTDOWN_SYNC: String = "countdown sync"
    public const val START: String = "start"
    public const val BEAT: String = "beat"
    public const val RUN: String = "run"
    public const val FINISH: String = "finish"

    public const val COUNTDOWN_MINUTES: Int = 5
    public const val LATE_PRESS_SECONDS: Double = 3.0
    public const val PRESTART_STANDOFF_METERS: Double = 60.0

    public data class Config(
        val lineCenter: GeoPoint = GeoPoint(51.14, 5.83),
        val wind: WindModel = WindModel(meanDirectionDegrees = 20.0, oscillationDegrees = 8.0, periodSeconds = 240.0),
        val model: SailingModel = SailingModel(),
        val noise: NoiseModel = NoiseModel.none(),
        val lineLengthMeters: Double = 100.0,
        val beatLengthMeters: Double = 500.0,
        val wrongWindOffsetDegrees: Int = 15,
    )

    public fun course(config: Config = Config()): RaceCourse =
        RaceCourse.square(config.lineCenter, config.wind.meanDirectionDegrees, config.lineLengthMeters, config.beatLengthMeters)

    /** The boat's state at the beginning of the scenario: abeam of the pin, reaching towards it along the line. */
    public fun initialState(config: Config = Config()): BoatState {
        val course = course(config)
        val wind = config.wind.meanDirectionDegrees
        return BoatState(
            timeSeconds = 0.0,
            position = Geo.destination(course.pinEnd, Angles.normalize(wind - 90.0), 60.0),
            headingDegrees = Angles.normalize(wind + 90.0),
            speedMps = config.model.polar.maxSpeedMps,
        )
    }

    /** The scripted legs, in order. */
    public fun legs(config: Config = Config()): List<Leg> {
        val course = course(config)
        val wind = config.wind.meanDirectionDegrees
        val polar = config.model.polar
        val prestartArea = Geo.destination(course.lineCenter, Angles.normalize(wind + 180.0), PRESTART_STANDOFF_METERS)
        val gunAfterSync = COUNTDOWN_MINUTES * 60.0

        return listOf(
            Leg(MARK_PIN, helm = { GoTo(course.pinEnd) }, until = Termination.Within(course.pinEnd, 4.0),
                onEnd = listOf { RaceEvent.MarkPinEnd }),
            Leg(MARK_BOAT, helm = { GoTo(course.boatEnd) }, until = Termination.Within(course.boatEnd, 4.0),
                onEnd = listOf { RaceEvent.MarkBoatEnd }),
            Leg(WIND_STARBOARD, helm = { CloseHauled(Tack.STARBOARD) }, until = Termination.After(40.0),
                onStart = listOf(
                    { RaceEvent.SetWindDirection(Angles.normalize(wind.toInt() + config.wrongWindOffsetDegrees)) },
                    { RaceEvent.SetTackAngle(polar.upwindAngleDegrees.toInt()) },
                    { RaceEvent.SetDownwindAngle(polar.downwindAngleDegrees.toInt()) },
                ),
                onEnd = listOf { RaceEvent.SetWindFromStarboardTack }),
            Leg(WIND_PORT, helm = { CloseHauled(Tack.PORT) }, until = Termination.After(40.0),
                onEnd = listOf { RaceEvent.SetWindFromPortTack }),
            Leg(TO_PRESTART, helm = { GoTo(prestartArea) }, until = Termination.Within(prestartArea, 8.0)),
            Leg(COUNTDOWN_SYNC, helm = { HoldWindAngle(90.0) }, until = Termination.After(LATE_PRESS_SECONDS),
                onStart = listOf { now -> RaceEvent.StartCountdown(COUNTDOWN_MINUTES, now) },
                onEnd = listOf { now -> RaceEvent.SyncCountdown(now) }),
            Leg(START, helm = { legStart -> TimedStart(legStart + gunAfterSync, PRESTART_STANDOFF_METERS) },
                until = Termination.OnCourseSide),
            Leg(BEAT, helm = { BeatTo(course.windwardMark, Tack.STARBOARD) }, until = Termination.Within(course.windwardMark, 15.0)),
            Leg(RUN, helm = { RunTo(course.leewardMark, Tack.STARBOARD) }, until = Termination.Within(course.leewardMark, 15.0)),
            Leg(FINISH, helm = { BeatTo(course.lineCenter, Tack.PORT) }, until = Termination.OnCourseSide,
                onEnd = listOf { RaceEvent.StopTimer }),
        )
    }

    public fun build(config: Config = Config()): SimulationResult =
        ScenarioRunner(course(config), config.wind, config.model, config.noise).run(initialState(config), legs(config))

    /** The simulated time of the starting gun, after the sync. */
    public fun gunMillis(result: SimulationResult): Long =
        result.milestone(COUNTDOWN_SYNC) + COUNTDOWN_MINUTES * 60_000L
}
