# Architecture

## Layers

```
 GPS / compass / simulation ──RaceEvent──▶ RaceSession ──▶ RaceReducer ──▶ RaceState ──▶ RaceCalculator ──▶ RaceSnapshot ──▶ UI state ──▶ Compose
                                               │                 │
                                            ticker           RaceEffect (PlayCue) ──▶ CuePlayer (tone + vibration)
                                               │
                                         RaceRepository (DataStore): start line, wind, countdown, settings
```

- **domain** (`com.sailracing.domain`) has no Android or coroutine dependency. `RaceReducer.reduce(state, event)`
  is a pure function; `RaceEngine` is a tiny stateful wrapper. Time is always passed in, never read.
- **simulation** (`com.sailracing.simulation`) simulates a boat (`BoatDynamics`, `BoatPolar`), an oscillating wind
  (`WindModel`), helm strategies (`Helm`: go-to, close-hauled, beat with laylines, run with gybes, timed start)
  and scripted legs (`ScenarioRunner`). `StandardRaceScenario` is a complete race; its output is a timeline of
  `RaceEvent`s plus ground truth for assertions.
- **domain** also holds the course and the strategy. `wind.WindReference` is the wind everything is judged
  against: the histogram's weighted centre once it has 30 samples, the set wind until then. `course.CourseFrame`
  is the wind-up metre frame of the map (oriented on the reference), `course.Track` where the boat has been
  (one point per fix second, with the wind estimate where the point was a close-hauled sample).
  `course.CourseModel` is everything the map shows, derived from the track: `GridSpec.covering` fits the racing
  area around the track, the line, the boat and the mark (10 m cells aggregated to a multiple of 10 m so that at
  most about 12 fit along a side), `TrackGrid` bins the track onto it with wind and speed per cell, `WindField`
  gives every cell a wind (measured with 3+ samples, otherwise an inverse-distance estimate from the measured
  cells) and `RouteFinder` finds the fastest line from the boat to the windward mark through that field.
  `strategy.UpwindStrategy` turns the reference, the histogram, the history and the grid into an `UpwindPlan`:
  which tack is lifted, and which side of the course pays.
- **app** wires the domain to Android:
  - `RaceSession` owns the engine, serialises all events on one dispatcher, ticks the clock, plays effects and
    persists changes (line, mark, wind, timer). It is started and ended by the sailor from the Session screen
    (a countdown starts it too); `MainActivity` runs the foreground service while it runs. In simulation mode
    it swaps the sensor source and uses a `ScaledClock`. Snapshots reuse the previous `CourseModel` while the
    track and the other course inputs are unchanged, so the 4 Hz ticks cost nothing on the map.
  - `SensorSource` implementations: `AndroidSensorSource` (LocationManager GPS at 1 Hz, rotation-vector compass
    at a low rate, read for an upright phone by `DeviceHeading`) and `SimulatedSensorSource` (replays a scenario
    at the clock's pace).
  - `RaceService` is a location foreground service that mirrors the countdown in a notification.
  - `DataStoreRaceRepository` persists settings and race data.
  - UI: one `RaceViewModel`; each screen has a pure `XxxUiState.from(snapshot)` mapper so the screen only
    recomposes when visible text changes, even though snapshots arrive four times a second. Screens: Session
    (start, end and clear the session; the landing screen), Start, Wind (tack advice, rose, histogram, wind
    settings), Map (the drawn racing area with the wind arrows, the mark, the optimum line and the side advice)
    and Settings. At the gun the root switches to the Wind screen.

## Key conventions

- Angles are compass degrees, normalised to [0, 360). `Angles.signedDifference(from, to)` is in (-180, 180],
  positive clockwise.
- The wind estimate assumes the boat sails its optimal angle for the tack and point of sail it is on, judged
  against the reference wind (so a roughly set wind does not keep one tack out of the statistics). Samples are
  only taken while moving (speed ≥ 0.5 m/s), not turning fast, and they only feed the histogram and the upwind
  statistics when the true wind angle is within `RaceSettings.upwindMaxTwaDegrees` (60° by default).
- Time to line = distance to the line / approach speed, where the approach speed is the measured average
  upwind VMG (once 30 samples exist) or a fallback/manual value. Time to kill = time to start − time to line.
- Cues are emitted when the whole-seconds-remaining value changes; the reducer remembers the last cued second
  so each cue plays once, even with irregular ticks.
- The tack advice compares the estimated wind with the reference wind, with a 3° dead band. Upwind a veer lifts
  starboard; downwind the port gybe. The histogram window, the shift history, the target headings and the map
  are all relative to the reference too; the set wind is shown as a blue marker where it sits.
- The side advice sums, in degrees: the wind difference between the right and left of the line from the start
  to the windward mark (positive when the right is veered), the speed difference converted at 1.6 % per degree,
  and the trend of the last 10 minutes against the mean. Beyond ±4° a side is favoured. The course model is
  rebuilt from the track whenever the track, the reference, the line, the mark or the boat position changes.
- The optimum line is a shortest-time path (Dijkstra, 16 directions, cells split in two) where crossing a cell
  costs distance / speed factor: 1 when the course can be sailed directly, `cos(tack angle) / cos(twa)` when
  it lies in the upwind no-go cone (the boat has to zigzag), likewise downwind beyond the downwind angle.
  A veer or a back of the local wind both shorten the beat, which is why the line bends into a shifted side.
  A 0.5 % per-metre penalty breaks the ties of an even wind in favour of the straight line.
- The windward mark is set at the boat, or by bearing and distance from the middle of the line (from the boat
  without a line), and persisted; without one the middle of the top edge of the racing area is used.
- The compass heading is the bearing of the back of the phone (device -z axis), which is where the bow points
  when the phone stands on the mast with the screen aft, whatever way up the phone is. Within 17° of flat it
  falls back to the top edge of the phone.

## Extending

- New sensor (e.g. NMEA over Wi-Fi): implement `SensorSource` and choose it in `DefaultAppGraph`.
- New derived value: add it to `RaceSnapshot` in `RaceCalculator` and to the relevant UI state.
- New scenario: compose `Leg`s with helms and terminations; run it with `ScenarioRunner`.
