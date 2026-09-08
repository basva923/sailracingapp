# The simulations

The app can be handed a simulated boat instead of the GPS: *Settings → Simulation → Simulate a race*, and
*Which race* to load one of eleven. Ten of them are **practice sessions**: the boat sails **five
windward-leeward laps** and only then starts a race, so that by the time it crosses the line the app has
measured the wind over the whole racing area and the race line it draws can be judged against a wind that
is known exactly. The eleventh is the scripted full race the app has always had.

Every practice session is the same sailing in a different breeze, and the breeze is what the title says.
Pick the one whose conditions you want to check the advice in, watch the map on the race screen, and
compare the green line with what the title says the wind is doing.

## What a practice session sails

| | |
| --- | --- |
| Course | A 400 m beat, a 100 m line square to the mean wind, the leeward mark 80 m below it |
| Boat | The dinghy polar: a 45° tack angle, a 140° downwind angle, and 3.0 m/s flat out in 12 kn of breeze, scaled by the square root of the wind |
| 1 | Sail along the line, mark the pin end and the boat end |
| 2 | Enter a wind 15° wrong, then correct it from each tack; enter the mark by bearing and distance |
| 3 | **Five laps**: beat to the windward mark, run back to the leeward mark |
| 4 | Drop below the line, start a 5-minute countdown three seconds late, sync it |
| 5 | Cross at the gun and beat to the windward mark - **the beat to watch** |

The five laps do not all beat up the same water. Each has a corridor of its own, and the boat tacks at its
edge: up the middle, wider up the middle, up the left, up the right, and up the middle again. That is what
fills every square of the racing area with real measurements - including the corners, which otherwise stay
a guess and make the race line treat them as the gamble they are.

A session is about 50 minutes of sailing in a middling breeze, longer in light air. Raise
*Settings → Simulation → Simulation speed* to 10× or 20× to watch one in a few minutes. Switching to
another simulation clears the session by itself, so nothing of the last one is left in the track or the
statistics.

## The ten winds, and what the race line should make of them

| # | Title | What is in it | What to look for |
| --- | --- | --- | --- |
| 1 | Steady sea breeze, 12 kn from 235°, hardly a shift | ±3° over 7 minutes, nothing else | Two long boards, one tack on the layline, no flyer worth drawing |
| 2 | Oscillating breeze, 10 kn from 020°, ±10° every 6 minutes | The classic shifty day | The line stays near the middle and tacks on the headers |
| 3 | Big slow shifts, 12 kn from 300°, ±18° every 12 minutes | Half a beat on one phase | It commits to the lifted tack; the flyer shows what the other side is worth |
| 4 | Quick short shifts, 8 kn from 090°, ±6° every 2 minutes | Shifts shorter than a board | They average out: few tacks, the shortest way up |
| 5 | Veering breeze, 12 kn from 180°, going right 20° an hour | A persistent shift right | Out to the right first, on the headed tack, back lifted |
| 6 | Backing breeze, 12 kn from 045°, going left 20° an hour | A persistent shift left | The mirror image: the left pays |
| 7 | Shore on the right, 14 kn from 270° veered 12° down that side | A bend in the wind that stays put | Into the right early, laying the mark from there |
| 8 | Pressure on the left, 12 kn from 010°, 15 kn that side and 9 kn on the right | Speed, not direction | Only the measured boat speed says go left: this checks the speed histograms are used |
| 9 | Gusty offshore breeze, 14 kn from 210° in puffs of 6 kn, shifting ±12° | Puffs and holes everywhere, no favoured side | Down the middle; the flyer should be little gain for a lot of risk |
| 10 | Building sea breeze, 7 kn from 330° rising to 16 kn and veering 25° | The afternoon of a sea breeze | Leaning right, and quicker as the breeze fills in |

## What the wind model can do

`simulation/WindModel.kt` is the whole weather of a simulation. A direction and a strength, each of which
changes with time and with where on the course it is measured:

| Dial | What it is |
| --- | --- |
| `meanDirectionDegrees`, `oscillationDegrees`, `periodSeconds` | the wind and the shifts that come back |
| `veerDegreesPerHour` | the shift that does not come back (positive veers, negative backs) |
| `shearDegreesPerMeter` | how much the wind bends across the course, as a shore bends it |
| `meanSpeedKnots`, `gustKnots`, `gustPeriodSeconds` | the strength, and the puffs and holes in it |
| `speedShearKnotsPerMeter` | pressure on one side of the course (negative: on the left) |
| `buildKnotsPerHour` | a breeze that fills in, or dies |

Boat speed follows the wind: the polar in `BoatPolar` is the boat in 12 knots, and it is scaled by the
square root of the wind ratio (capped at 1.4×, since a boat at hull speed does not keep gaining). So a
scenario with pressure on one side really is sailed faster on that side, which is exactly what the app's
per-square speed histograms are meant to pick up.

There is no GPS noise in these simulations: the fixes are the truth. That is deliberate - what is being
checked is the routing, not the filtering. `PracticeScenario.Config(noise = NoiseModel.typicalGps(seed))`
turns real GPS scatter on for a run that should be harder.

## Adding one

Add an entry to `SimulationCatalog.practice` with an id, a title that says what the wind does, a line about
what the race line should make of it, and the `WindModel`. It appears in the app's list by itself.
`SimulationCatalogTest` checks that every title names the wind it is built from, and that every session
really sails its five laps and both corners of the course.
