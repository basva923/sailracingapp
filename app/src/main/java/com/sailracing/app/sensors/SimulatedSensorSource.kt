package com.sailracing.app.sensors

import com.sailracing.app.time.Clock
import com.sailracing.domain.race.RaceEvent
import com.sailracing.simulation.SimulationResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Replays a simulated race as if it were happening now: every timeline event is emitted when the
 * [clock] reaches its (shifted) time. Use a scaled clock to run faster than real time.
 *
 * @param includeActions also replay the scripted sailor actions (marking the line, starting the timer...),
 *   giving a hands-free demo; otherwise only the GPS fixes are replayed and the user presses the buttons.
 * @param loop restart from the beginning when the race is over.
 */
class SimulatedSensorSource(
    private val result: SimulationResult,
    private val clock: Clock,
    private val includeActions: Boolean,
    private val loop: Boolean = true,
    private val pollMillis: Long = 100L,
) : SensorSource {

    override fun events(): Flow<RaceEvent> = flow {
        do {
            val offset = clock.nowMillis()
            for (timed in result.timeline) {
                if (!includeActions && timed.event !is RaceEvent.FixReceived) continue
                val due = timed.timeMillis + offset
                while (clock.nowMillis() < due) delay(pollMillis)
                emit(timed.event.shiftedBy(offset))
            }
        } while (loop)
    }
}

/** Moves every timestamp carried by an event by [offsetMillis]. Events without a timestamp are unchanged. */
fun RaceEvent.shiftedBy(offsetMillis: Long): RaceEvent = when (this) {
    is RaceEvent.FixReceived -> RaceEvent.FixReceived(fix.copy(timestampMillis = fix.timestampMillis + offsetMillis))
    is RaceEvent.Tick -> RaceEvent.Tick(nowMillis + offsetMillis)
    is RaceEvent.StartCountdown -> copy(nowMillis = nowMillis + offsetMillis)
    is RaceEvent.SyncCountdown -> copy(nowMillis = nowMillis + offsetMillis)
    else -> this
}
