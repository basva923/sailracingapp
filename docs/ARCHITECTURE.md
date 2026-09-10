# Architecture

## Layers

```
 GPS / compass / simulation ──RaceEvent──▶ RaceSession ──▶ RaceReducer ──▶ RaceState ──▶ RaceCalculator ──▶ RaceSnapshot ──▶ UI state ──▶ Compose
                                               │                 │
                                            ticker           RaceEffect (PlayCue) ──▶ CuePlayer (tone + vibration)
                                               │                 │
                                         RaceRepository          SessionLog (JSON lines per session)
                                         (DataStore): start line, wind, countdown, settings
```

- **domain** (`com.sailracing.domain`) has no Android or coroutine dependency. `RaceReducer.reduce(state, event)`
  is a pure function; `RaceEngine` is a tiny stateful wrapper. Time is always passed in, never read.
- **simulation** (`com.sailracing.simulation`) simulates a boat (`BoatDynamics`, `BoatPolar`), the weather
  (`WindModel`: a direction that oscillates, keeps veering and bends across the course, and a strength that
  puffs, builds and is stronger on one side - the polar is the boat in 12 kn and scales with the square root
  of the wind where it is), helm strategies (`Helm`: go-to, close-hauled, beat up a corridor anywhere across
  the course, run with gybes, timed start) and scripted legs (`ScenarioRunner`). `StandardRaceScenario` is a
  complete race and `PracticeScenario` is five windward-leeward laps and then a start; their output is a
  timeline of `RaceEvent`s plus ground truth for assertions. `SimulationCatalog` is the eleven simulations
  the app offers - the full race and ten practice sessions, each in a realistic breeze named in its title,
  for judging the race line against a wind that is known ([SIMULATIONS.md](SIMULATIONS.md)).
- **domain** also holds the course and the strategy. `wind.WindReference` is the wind everything is judged
  against: the histogram's weighted centre once it has 30 samples, the set wind until then. `course.CourseFrame`
  is the wind-up metre frame of the map (oriented on the reference), `course.Track` where the boat has been
  (one point per fix second, with the wind estimate where the point was a close-hauled sample).
  `course.CourseModel` is everything the map shows, derived from the track: `GridSpec.covering` fits the racing
  area around the track, the line, the boat and the mark (squares of 5 m or a multiple of it, chosen so that
  at most about 12 fit along a side, or the size in `GridSettings`, capped at 20 squares a side), `TrackGrid` bins the track onto it with the
  wind and a speed histogram per cell, `WindField` gives every cell a distribution - mean wind, how far it may
  be off, and the speeds to expect - by blending three things weighed by their precision: the cell's own
  samples, the samples around it (as one measurement, capped by the distance) and the wind measured over the
  whole course; `WindSampler` draws whole winds out of it and `RaceLinePlanner` searches each of them with
  `RaceLineFinder`, timing every candidate through every drawn wind: out come the race line to sail and the
  flyer to gamble on (see [RACE_LINE.md](RACE_LINE.md)).
  `strategy.UpwindStrategy` turns the reference, the histogram, the history and the grid into an `UpwindPlan`:
  which tack is lifted, and which side of the course pays.
- **domain** also holds the words and the drawn course. `text.Formatters`, `text.PlanText` and
  `text.CourseText` are the sentences every screen shows - the phone's and the workbench's - so the two
  cannot drift apart. `geo.LocalPlane` is the one place the earth is flattened: a north-up metre grid,
  which `course.CourseFrame` is a rotation of into the wind. `sketch.CourseSketch` is a course drawn by
  hand on that plane (a line, a mark, a wind and a track); `sketch.SketchReplay` turns it into the GPS
  fixes a boat sailing it would have produced and `sketch.SketchAnalysis` runs those through a real
  `RaceEngine`, so a drawn course reaches the strategy through the app's own front door and comes back as
  the same `RaceSnapshot` the Race screen is drawn from. `sketch.CourseSketchFormat` keeps one as text
  ([WORKBENCH.md](WORKBENCH.md)).
- **desktop** (`com.sailracing.desktop`) is the strategy workbench: a Swing window for the PC that draws a
  course and asks `SketchAnalysis` for the route up the beat. `workbench.Workbench` is the editing as a
  value (a gesture in, a course out), `workbench.Viewport` is metres to pixels with the pan, zoom and fit,
  `workbench.Report` is the words - all of them out of `domain.text` - and `ui` is the window itself. It
  holds no race logic at all ([WORKBENCH.md](WORKBENCH.md)).
- **app** wires the domain to Android:
  - `RaceSession` owns the engine, serialises all events on one dispatcher, ticks the clock, plays effects and
    persists changes (line, mark, wind, timer). It is started and ended by the sailor from the Session screen
    (a countdown starts it too); `MainActivity` runs the foreground service while it runs. In simulation mode
    it swaps the sensor source and uses a `ScaledClock`; loading another simulation restarts the sensor
    source and clears the session, since the old track belongs to another day on another course. Snapshots reuse the previous `CourseModel` while the
    track and the other course inputs are unchanged, so the 4 Hz ticks cost nothing on the map.
  - `SensorSource` implementations: `AndroidSensorSource` (LocationManager GPS at 1 Hz, rotation-vector compass
    at a low rate, read for an upright phone by `DeviceHeading`) and `SimulatedSensorSource` (replays a scenario
    at the clock's pace).
  - `RaceService` is a location foreground service that mirrors the countdown in a notification.
  - `DataStoreRaceRepository` persists settings and race data. `FileSessionLog` writes the session itself -
    every event in, the state once a second, every cue - as JSON lines in the app's external files folder,
    one file per session, on its own IO coroutine so the engine never waits for a disk
    ([LOGGING.md](LOGGING.md)).
  - UI: one `RaceViewModel`; each screen has a pure `XxxUiState.from(snapshot)` mapper so the screen only
    recomposes when visible text changes, even though snapshots arrive four times a second. Screens: Session
    (start, end and clear the session; the landing screen), Start, Race and Settings. The Race screen is the
    map (`CourseMap`, the whole racing area scaled to the room it has, given the height that area can use)
    with everything else scrolling underneath it: the tack advice, the side, what the race line costs, the
    numbers, the wind buttons, the mark and the statistics. It is fed by both `MapUiState` and
    `WindUiState`. At the gun the root switches to it.
  - The map's fingers: `MapCamera` is the pure value (zoom, and how far the middle has been moved, clamped
    so the area cannot be pushed off the screen) and `MapProjection` turns course metres into pixels and
    back. A pinch is one coroutine fed a stream of little movements, each of which has to build on the last,
    so the screen holds a `MapCameraState` and the map reads it as it is now - while drawing, so a pinch
    redraws without recomposing. Handing the camera in as a value would leave every step of a gesture
    transforming the camera the pinch started from, and only the last flick of it would show.

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
- The race line is a breadth-first search over boards, each one crossing a single square of the racing area
  at the tack angle to the wind of that square and at the speed measured there, never leaving the area and
  never giving away any of the beat still to sail (which is what makes the boat tack on the layline, at any
  tack angle), with a tack charged the time it throws away - including the first board, when it is not the
  tack the boat is on. That search is run through 16 winds drawn from the per-cell distributions, spread out
  from the wind the boat is measuring right now and correlated over about 150 m, and every candidate line is
  then timed through all 16: the line with the least bad bad day is drawn green, and the one with the best
  good day, when that is worth more than a tack, amber. It is all done again when the boat enters a new
  square or the wind it is measuring moves 5°. [RACE_LINE.md](RACE_LINE.md) is the whole algorithm, and
  [RACE_LINE_GALLERY.md](RACE_LINE_GALLERY.md) shows ten courses it was checked against by eye.
- Every square of the racing area gets a wind, sailed through or not: its own samples (worth `n / wander²`),
  the samples around it (gathered into one measurement, weighed by `exp(-distance / correlationLength)` and
  capped by what that distance can be worth) and the wind measured over the whole course (worth
  `1 / spatialSpread²`), blended by precision. A square's spread is how much its wind wandered plus how
  uncertain its mean still is, which is what makes an unsailed corner a gamble in the Monte Carlo and a
  faint arrow on the map. The dials are `WindFieldSettings`, under `RaceSettings.course`.
- The windward mark is set at the boat, or by bearing and distance from the middle of the line (from the boat
  without a line), and persisted; without one the middle of the top edge of the racing area is used.
- The compass heading is the bearing of the back of the phone (device -z axis), which is where the bow points
  when the phone stands on the mast with the screen aft, whatever way up the phone is. Within 17° of flat it
  falls back to the top edge of the phone.

## Extending

- New sensor (e.g. NMEA over Wi-Fi): implement `SensorSource` and choose it in `DefaultAppGraph`.
- New logged value: add it to the record in `SessionRecorder`; the format is described in [LOGGING.md](LOGGING.md).
- New derived value: add it to `RaceSnapshot` in `RaceCalculator` and to the relevant UI state.
- New scenario: compose `Leg`s with helms and terminations; run it with `ScenarioRunner`. A new simulation to
  load in the app is one entry in `SimulationCatalog` ([SIMULATIONS.md](SIMULATIONS.md)).
- New course to try the strategy on: draw it in the workbench (`./gradlew :desktop:run`) and save it beside
  the others in `desktop/courses`, where `ExampleCoursesTest` opens and plans every one of them.
- New sentence on a screen: put it in `domain.text` if both the phone and the workbench should say it.
