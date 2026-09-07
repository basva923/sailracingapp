package com.sailracing.app.sensors

import com.sailracing.domain.race.RaceEvent
import kotlinx.coroutines.flow.Flow

/** Anything that produces race events from the outside world: GPS, compass, or a simulation. */
interface SensorSource {
    fun events(): Flow<RaceEvent>
}
