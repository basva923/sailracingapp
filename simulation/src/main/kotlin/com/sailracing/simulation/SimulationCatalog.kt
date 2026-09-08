package com.sailracing.simulation

/**
 * One simulation the app can be handed instead of the GPS.
 *
 * @property id what the choice is stored as; it never changes, the title may.
 * @property title the conditions in a line, as a forecast would put them.
 * @property detail what those conditions should do to the race line, to check the drawn one against.
 * @property wind the wind that defines the simulation.
 */
public class SimulationScenario(
    public val id: String,
    public val title: String,
    public val detail: String,
    public val wind: WindModel,
    private val builder: () -> SimulationResult,
) {
    /** Runs the simulation. It takes a moment, so the caller is expected to keep the result. */
    public fun build(): SimulationResult = builder()

    override fun toString(): String = "SimulationScenario($id)"
}

/**
 * The simulations that can be loaded in the app: a full race, and ten practice sessions - five
 * windward-leeward laps and then a start - each in a different, realistic breeze.
 *
 * The laps are there to fill the racing area with measurements, so that when the boat crosses the line for
 * the last beat the app's race line is drawn from a wind it has really seen everywhere. Every condition
 * here is one that decides races: a steady breeze, oscillations of three sizes, a wind that keeps turning
 * one way, a shore that bends it, pressure on one side, puffs and holes, and a sea breeze that fills in
 * while it veers. [SimulationScenario.detail] says what the race line ought to make of each.
 */
public object SimulationCatalog {

    /** The scripted full race: one lap, from marking the line to the finish. */
    public val fullRace: SimulationScenario = SimulationScenario(
        id = "full-race",
        title = "Full race, 12 kn from 020°, ±8° and veered on the right",
        detail = "One race from the line to the finish, not a practice session: the start, a beat, a run " +
            "and the beat back. The right of the course is veered, so the line should lean that way.",
        wind = StandardRaceScenario.Config().wind,
        builder = { StandardRaceScenario.build() },
    )

    /** The ten practice sessions, in the order they are offered. */
    public val practice: List<SimulationScenario> = listOf(
        practice(
            id = "steady-sea-breeze",
            title = "Steady sea breeze, 12 kn from 235°, hardly a shift",
            detail = "Nothing to play. The race line should be two long boards with a single tack on the " +
                "layline, and the flyer should be worth nothing at all.",
            wind = WindModel(meanDirectionDegrees = 235.0, oscillationDegrees = 3.0, periodSeconds = 420.0, meanSpeedKnots = 12.0),
        ),
        practice(
            id = "oscillating",
            title = "Oscillating breeze, 10 kn from 020°, ±10° every 6 minutes",
            detail = "The classic shifty day. The line should stay near the middle of the course and tack " +
                "on the headers rather than reach out to a corner.",
            wind = WindModel(meanDirectionDegrees = 20.0, oscillationDegrees = 10.0, periodSeconds = 360.0, meanSpeedKnots = 10.0),
        ),
        practice(
            id = "big-slow-shifts",
            title = "Big slow shifts, 12 kn from 300°, ±18° every 12 minutes",
            detail = "Half a beat on one phase. The line should commit to the tack that is lifted now, and " +
                "the flyer should show what the other side is worth if the shift comes back early.",
            wind = WindModel(meanDirectionDegrees = 300.0, oscillationDegrees = 18.0, periodSeconds = 720.0, meanSpeedKnots = 12.0),
        ),
        practice(
            id = "quick-shifts",
            title = "Quick short shifts, 8 kn from 090°, ±6° every 2 minutes",
            detail = "Shifts too quick to chase: they average out over a board. The line should ignore them " +
                "and sail the shortest way, with few tacks.",
            wind = WindModel(meanDirectionDegrees = 90.0, oscillationDegrees = 6.0, periodSeconds = 120.0, meanSpeedKnots = 8.0),
        ),
        practice(
            id = "veering",
            title = "Veering breeze, 12 kn from 180°, going right 20° an hour",
            detail = "A persistent shift to the right. The line should take the boat out to the right first, " +
                "on the headed tack, and come back lifted.",
            wind = WindModel(
                meanDirectionDegrees = 180.0, oscillationDegrees = 6.0, periodSeconds = 300.0,
                veerDegreesPerHour = 20.0, meanSpeedKnots = 12.0,
            ),
        ),
        practice(
            id = "backing",
            title = "Backing breeze, 12 kn from 045°, going left 20° an hour",
            detail = "The same the other way round: the left should pay, and the line should get there " +
                "before the shift does.",
            wind = WindModel(
                meanDirectionDegrees = 45.0, oscillationDegrees = 6.0, periodSeconds = 300.0,
                veerDegreesPerHour = -20.0, meanSpeedKnots = 12.0,
            ),
        ),
        practice(
            id = "shore-right",
            title = "Shore on the right, 14 kn from 270° veered 12° down that side",
            detail = "A bend in the wind that stays where it is. The right side is lifted all afternoon, so " +
                "the line should sail into it early and lay the mark from there.",
            wind = WindModel(
                meanDirectionDegrees = 270.0, oscillationDegrees = 8.0, periodSeconds = 300.0,
                shearDegreesPerMeter = 0.06, meanSpeedKnots = 14.0,
            ),
        ),
        practice(
            id = "pressure-left",
            title = "Pressure on the left, 12 kn from 010°, 15 kn that side and 9 kn on the right",
            detail = "The same wind direction everywhere, but not the same speed. Nothing in the shifts says " +
                "go left; only the boat speed measured over there does, so this is the one that checks the " +
                "speed histograms are being used.",
            wind = WindModel(
                meanDirectionDegrees = 10.0, oscillationDegrees = 8.0, periodSeconds = 300.0,
                meanSpeedKnots = 12.0, speedShearKnotsPerMeter = -0.015,
            ),
        ),
        practice(
            id = "gusty-offshore",
            title = "Gusty offshore breeze, 14 kn from 210° in puffs of 6 kn, shifting ±12°",
            detail = "Puffs and holes over the whole course, with the shifts to match. No side is favoured, " +
                "so the line should stay in the middle and the flyer should be worth little for a lot of risk.",
            wind = WindModel(
                meanDirectionDegrees = 210.0, oscillationDegrees = 12.0, periodSeconds = 300.0,
                meanSpeedKnots = 14.0, gustKnots = 6.0, gustPeriodSeconds = 180.0,
            ),
        ),
        practice(
            id = "building-sea-breeze",
            title = "Building sea breeze, 7 kn from 330° rising to 16 kn and veering 25°",
            detail = "The afternoon of a sea breeze: it fills in and swings right as it does. Boat speed " +
                "climbs through the session, so the line should get quicker as well as leaning right.",
            wind = WindModel(
                meanDirectionDegrees = 330.0, oscillationDegrees = 5.0, periodSeconds = 400.0,
                veerDegreesPerHour = 25.0, meanSpeedKnots = 7.0, buildKnotsPerHour = 9.0,
            ),
        ),
    )

    /** Everything on offer: the full race first, then the practice sessions. */
    public val all: List<SimulationScenario> = listOf(fullRace) + practice

    /** What runs when nothing has been chosen. */
    public val default: SimulationScenario = fullRace

    /** The simulation stored under [id], or the [default] when it is unknown (or nothing was stored). */
    public fun byId(id: String?): SimulationScenario = all.firstOrNull { it.id == id } ?: default

    private fun practice(id: String, title: String, detail: String, wind: WindModel): SimulationScenario =
        SimulationScenario(id, title, detail, wind) { PracticeScenario.build(PracticeScenario.Config(wind = wind)) }
}
