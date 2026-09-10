package com.sailracing.app.log

import com.sailracing.app.data.AppSettings
import com.sailracing.domain.race.ApproachSpeed
import com.sailracing.domain.race.RaceEvent
import com.sailracing.domain.race.RaceSnapshot
import com.sailracing.domain.timer.Cue
import com.sailracing.domain.timer.TimerState

/**
 * Turns a session into lines of log: what went in, and what the app made of it.
 *
 * Two kinds of line are written. Every **event** that reaches the engine is one - every fix, every compass
 * reading, every button the sailor pressed - which is everything the app was ever told, so a session can
 * be replayed through the reducer afterwards and come out the same. On top of that a **state** line is
 * written about once a second: what the app was showing at that moment, so that a log can be read for what
 * happened without replaying anything.
 *
 * The clock ticks four times a second and a fix arrives once a second, so ticks themselves are not logged;
 * the state line is the heartbeat, and it carries the time anyway.
 */
class SessionRecorder(private val stateIntervalMillis: Long = DEFAULT_STATE_INTERVAL_MILLIS) {

    private var lastStateMillis: Long? = null

    /** The head of a log: what was running, and everything it was configured with. */
    fun start(nowMillis: Long, versionName: String, settings: AppSettings): LogRecord {
        val race = settings.race
        val approach = race.approachSpeed
        return LogRecord.of(
            nowMillis,
            "session",
            "app" to versionName,
            "android" to android.os.Build.VERSION.SDK_INT,
            "device" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            "simulation" to settings.simulation.enabled,
            "simulationSpeed" to settings.simulation.speedFactor,
            "simulationAutoPlay" to settings.simulation.autoPlayActions,
            "approachSpeedMps" to when (approach) {
                is ApproachSpeed.Manual -> approach.speedMps
                is ApproachSpeed.AverageUpwindVmg -> approach.fallbackMps
            },
            "approachSpeedManual" to (approach is ApproachSpeed.Manual),
            "upwindMaxTwaDegrees" to race.upwindMaxTwaDegrees,
            "downwindMinTwaDegrees" to race.downwindMinTwaDegrees,
            "compassOffsetDegrees" to race.compassOffsetDegrees,
            "minSailingSpeedMps" to race.minSailingSpeedMps,
            "cellSizeMeters" to race.course.grid.cellSizeMeters,
            "windColumns" to race.course.windField.columns,
            "currentWindFraction" to race.course.windField.currentWindFraction,
            "randomWindFraction" to race.course.windField.randomWindFraction,
            "monteCarloRuns" to race.course.raceLine.runs,
            "monteCarloSearches" to race.course.raceLine.searchRuns,
            "cues" to race.cuePolicy.enabled,
            "vibrate" to settings.vibrate,
        )
    }

    /** The lines one event produces: the event itself, and the state it left behind once a second. */
    fun record(event: RaceEvent, snapshot: RaceSnapshot): List<LogRecord> {
        val now = snapshot.nowMillis
        val records = ArrayList<LogRecord>(2)
        if (event !is RaceEvent.Tick) records += event(event, now)
        val last = lastStateMillis
        if (last == null || now - last >= stateIntervalMillis || now < last) {
            lastStateMillis = now
            records += state(snapshot)
        }
        return records
    }

    fun cue(cue: Cue, nowMillis: Long): LogRecord = LogRecord.of(nowMillis, "cue", "cue" to cue)

    fun end(nowMillis: Long): LogRecord = LogRecord.of(nowMillis, "end")

    /** Everything the app was told, with the values it was told in. */
    private fun event(event: RaceEvent, nowMillis: Long): LogRecord {
        val name = event::class.simpleName ?: "event"
        fun of(vararg values: Pair<String, Any?>) = LogRecord.of(nowMillis, "event", "event" to name, *values)
        return when (event) {
            is RaceEvent.FixReceived -> of(
                "lat" to event.fix.point.latitude,
                "lon" to event.fix.point.longitude,
                "fixMillis" to event.fix.timestampMillis,
                "speedMps" to event.fix.speedMps,
                "courseDegrees" to event.fix.courseDegrees,
                "accuracyMeters" to event.fix.accuracyMeters,
            )
            is RaceEvent.CompassUpdated -> of("headingDegrees" to event.headingDegrees)
            is RaceEvent.Tick -> of()
            is RaceEvent.SetStartLine -> of(
                "pinLat" to event.line.pinEnd?.latitude,
                "pinLon" to event.line.pinEnd?.longitude,
                "boatLat" to event.line.boatEnd?.latitude,
                "boatLon" to event.line.boatEnd?.longitude,
            )
            is RaceEvent.SetWindwardMark -> of("lat" to event.point?.latitude, "lon" to event.point?.longitude)
            is RaceEvent.SetWindwardMarkFromLine -> of("bearingDegrees" to event.bearingDegrees, "distanceMeters" to event.distanceMeters)
            is RaceEvent.StartCountdown -> of("minutes" to event.minutes, "atMillis" to event.nowMillis)
            is RaceEvent.SyncCountdown -> of("atMillis" to event.nowMillis)
            is RaceEvent.SetTimer -> of("startAtMillis" to (event.timer as? TimerState.Running)?.startAtMillis)
            is RaceEvent.SetWindDirection -> of("degrees" to event.degrees)
            is RaceEvent.SetTackAngle -> of("degrees" to event.degrees)
            is RaceEvent.SetDownwindAngle -> of("degrees" to event.degrees)
            is RaceEvent.SetWindSettings -> of(
                "directionDegrees" to event.settings.directionDegrees,
                "tackAngleDegrees" to event.settings.tackAngleDegrees,
                "downwindAngleDegrees" to event.settings.downwindAngleDegrees,
            )
            is RaceEvent.UpdateSettings -> of(
                "upwindMaxTwaDegrees" to event.settings.upwindMaxTwaDegrees,
                "cellSizeMeters" to event.settings.course.grid.cellSizeMeters,
                "cues" to event.settings.cuePolicy.enabled,
            )
            else -> of()
        }
    }

    /** What the app was showing: the boat, the wind it believes in, and the advice it was giving. */
    private fun state(snapshot: RaceSnapshot): LogRecord {
        val course = snapshot.course
        val plan = snapshot.plan
        val racePlan = course?.racePlan
        return LogRecord.of(
            snapshot.nowMillis,
            "state",
            "phase" to snapshot.phase,
            "remainingMillis" to snapshot.remainingMillis,
            "lat" to snapshot.position?.latitude,
            "lon" to snapshot.position?.longitude,
            "fixAgeMillis" to snapshot.fixAgeMillis,
            "accuracyMeters" to snapshot.accuracyMeters,
            "speedMps" to snapshot.speedMps,
            "vmgMps" to snapshot.vmgMps,
            "headingDegrees" to snapshot.headingDegrees,
            "headingSource" to snapshot.headingSource,
            "setWindDegrees" to snapshot.windSettings.directionDegrees,
            "tackAngleDegrees" to snapshot.windSettings.tackAngleDegrees,
            "referenceWindDegrees" to snapshot.windReference.directionDegrees,
            "referenceMeasured" to snapshot.windReference.isMeasured,
            "estimatedWindDegrees" to snapshot.estimatedWindDegrees,
            "shiftDegrees" to snapshot.shiftDegrees,
            "tack" to snapshot.sailing?.tack,
            "pointOfSail" to snapshot.sailing?.pointOfSail,
            "trueWindAngleDegrees" to snapshot.sailing?.trueWindAngleDegrees,
            "histogramSamples" to snapshot.histogram.totalSamples,
            "upwindSpeedMps" to snapshot.speedStats.upwind.mean,
            "upwindVmgMps" to snapshot.speedStats.upwindVmg.mean,
            "distanceToLineMeters" to snapshot.line?.distanceMeters,
            "timeToLineSeconds" to snapshot.timeToLineSeconds,
            "timeToKillSeconds" to snapshot.timeToKillSeconds,
            "lineSide" to snapshot.line?.side,
            "alongLineFraction" to snapshot.line?.alongFraction,
            "advice" to plan.tackAdvice,
            "favouredTack" to plan.favouredTack,
            "favouredSide" to plan.favouredSide,
            "sideScoreDegrees" to plan.sideScoreDegrees,
            "trackPoints" to snapshot.trackPointCount,
            "cells" to course?.spec?.cellCount,
            "cellSizeMeters" to course?.spec?.cellSizeMeters,
            "windBlocks" to course?.windField?.spec?.cellCount,
            "measuredBlocks" to course?.windField?.measuredCount,
            "windSamples" to course?.windField?.samples,
            "markAcrossMeters" to course?.windwardMark?.acrossMeters,
            "markUpwindMeters" to course?.windwardMark?.upwindMeters,
            "markIsSet" to course?.windwardMarkIsSet,
            "raceLineTacks" to racePlan?.safe?.tacks?.takeIf { racePlan.isEmpty.not() },
            "raceLineSeconds" to racePlan?.safe?.seconds?.takeIf { racePlan.isEmpty.not() },
            "raceLineBadSeconds" to racePlan?.safeRisk?.badSeconds?.takeIf { racePlan.isEmpty.not() },
            "flyerSeconds" to racePlan?.fast?.seconds?.takeIf { racePlan?.agree == false },
            "flyerWinFraction" to racePlan?.winFraction?.takeIf { racePlan?.agree == false },
        )
    }

    companion object {
        /** How often the app's own state is written down: often enough to follow, rarely enough to read. */
        const val DEFAULT_STATE_INTERVAL_MILLIS: Long = 1_000L
    }
}
