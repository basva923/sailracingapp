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
- **app** wires the domain to Android:
  - `RaceSession` owns the engine, serialises all events on one dispatcher, ticks the clock, plays effects and
    persists changes. In simulation mode it swaps the sensor source and uses a `ScaledClock`.
  - `SensorSource` implementations: `AndroidSensorSource` (LocationManager GPS at 1 Hz, rotation-vector compass
    at a low rate) and `SimulatedSensorSource` (replays a scenario at the clock's pace).
  - `RaceService` is a location foreground service that mirrors the countdown in a notification.
  - `DataStoreRaceRepository` persists settings and race data.
  - UI: one `RaceViewModel`; each screen has a pure `XxxUiState.from(snapshot)` mapper so the screen only
    recomposes when visible text changes, even though snapshots arrive four times a second.

## Key conventions

- Angles are compass degrees, normalised to [0, 360). `Angles.signedDifference(from, to)` is in (-180, 180],
  positive clockwise.
- The wind estimate assumes the boat sails its optimal angle for the tack and point of sail it is on (judged
  against the configured wind). Samples are only taken while moving (speed ≥ 0.5 m/s), not turning fast, and
  they only feed the histogram and the upwind statistics when the true wind angle is within
  `RaceSettings.upwindMaxTwaDegrees` (60° by default).
- Time to line = distance to the line / approach speed, where the approach speed is the measured average
  upwind VMG (once 30 samples exist) or a fallback/manual value. Time to kill = time to start − time to line.
- Cues are emitted when the whole-seconds-remaining value changes; the reducer remembers the last cued second
  so each cue plays once, even with irregular ticks.

## Extending

- New sensor (e.g. NMEA over Wi-Fi): implement `SensorSource` and choose it in `DefaultAppGraph`.
- New derived value: add it to `RaceSnapshot` in `RaceCalculator` and to the relevant UI state.
- New scenario: compose `Leg`s with helms and terminations; run it with `ScenarioRunner`.
