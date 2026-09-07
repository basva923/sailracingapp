# Sail Racing

A native Android app (Kotlin, Jetpack Compose) that helps a sailor through a race: mark the start line,
set the wind, run the start countdown with beeps and a live *time to kill*, then decide when to tack and
which side of the course pays from the wind statistics and a drawn map of the racing area.
See [Design.md](Design.md) for the original feature design.

## Features

- **Start line**: mark the pin and boat ends at your position; the line is stored across restarts.
- **Session**: the app opens on the Session screen, the one place to start a session (GPS, wind
  statistics and countdown on), end it (GPS off, data kept) and clear it (line, mark, track and
  statistics forgotten for the next race), with an overview of what the session holds.
- **Wind**: enter a rough direction manually, or sail close-hauled and press *Starboard tack* / *Port tack*
  to derive it from your heading and the configured tack angle. While sailing upwind the app builds a
  histogram of the estimated wind direction, a shift history and average upwind/downwind speed and VMG.
  The set wind is only a seed: once the histogram has 30 samples its weighted centre is the *reference
  wind* for everything (which tack you are on, the advice, the target headings, the map).
- **Countdown**: 5, 4 or 1 minute starts, *Sync* to the nearest minute, beeps every minute, every 10 s in the
  last two minutes, every second in the last 10 s and a burst at the gun (with vibration).
- **Start**: distance to the line, time to kill (green = early, red = late) and an *over the line* warning.
- **Tack advice**: the wind is assumed to keep oscillating over the histogram, so its weighted centre is the
  wind to beat against. On the lifted tack the screen says *HOLD*, when headed it says *TACK* (or *GYBE*
  downwind), with the shift in degrees. The helm steers to the sails; the app only calls the tacks.
  At the starting gun the app switches to the wind screen by itself.
- **Map**: a drawn (not downloaded) wind-up map whose racing area follows the track: its bounding box
  (with the line, the boat and the mark) in 10 m cells, aggregated into bigger cells when more than a
  dozen would fit along a side. Every cell carries an arrow of the mean wind there: bright when measured
  in that cell, dim when estimated from the measured cells around it; amber veered, blue backed.
- **Windward mark and the optimum line**: set the mark at your position or, as the committee posts it,
  by bearing and distance from the line; without one the middle of the top edge of the area is used.
  A green line shows the fastest course from the boat to the mark through the measured wind field
  (the corridor to sail up, not the tacks): it bends into a veered or backed side and is straight in an
  even wind.
- **Best side**: the app compares the wind and boat speed measured on the left and right of the line from
  the start to the mark, adds the recent trend of the wind, and says *GO LEFT*, *GO RIGHT* or *SIDES EVEN*,
  with the numbers behind it. Sail towards the side the wind is shifted to.
- **Simulation mode**: a scripted boat sails a complete race, in an oscillating wind that is a little veered
  on the right of the course, entering the mark by bearing and distance, so every feature can be tried on
  the couch.
- Pure black AMOLED theme, big glove-friendly buttons, text that scales to the screen, landscape support,
  a foreground service so GPS and beeps keep running with the screen off.
- The compass expects the phone to stand upright on the mast with the screen facing aft; a flat phone
  falls back to its top edge.

## Project layout

| Module | What it is | Tests |
| --- | --- | --- |
| `domain` | Pure Kotlin race logic: geo math, start line, countdown and cues, wind estimation and statistics with the `WindReference`, the `CourseModel` (the track-following area, its grid, the interpolated `WindField` and the `RouteFinder`), the `UpwindStrategy` (tack advice and favoured side), the `RaceReducer` state machine and the `RaceCalculator` that derives everything shown. No Android dependency. | JUnit 5, 100 % line coverage enforced by Kover |
| `simulation` | Deterministic boat, wind and course simulator plus `StandardRaceScenario`, a scripted full race that emits GPS fixes and the sailor's button presses. | JUnit 5, 100 % line coverage enforced; `FullRaceEndToEndTest` drives the real engine through the race and checks every feature against ground truth |
| `app` | The Android app: sensors, DataStore persistence, `RaceSession` (engine + sensors + ticker + persistence), the foreground service and the Compose UI. | JUnit 4 + Robolectric + Compose UI tests, including a full simulated race through the session in virtual time |

Architecture in one line: sensors and the clock produce `RaceEvent`s, the pure `RaceReducer` folds them
into an immutable `RaceState` and returns `RaceEffect`s (beeps), `RaceCalculator` derives a `RaceSnapshot`
for display, and the UI maps snapshots to small display states. Everything with logic is testable on the JVM.

## Building

Requirements: JDK 17+ (the build runs on JDK 21 and targets 17), Android SDK 35. Versions are pinned in
`gradle/libs.versions.toml`.

```bash
./gradlew check koverVerify     # all tests and the coverage gates
./gradlew koverHtmlReport       # merged coverage report in build/reports/kover/html
./gradlew :app:assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug     # install on a connected device or emulator
```

## Trying it without a boat

Open *Settings*, switch on *Simulate a race* (leave *Press the buttons automatically* on for a hands-free
demo, or switch it off to press the buttons yourself as the scripted boat sails) and optionally raise the
simulation speed, then start a session on the *Session* screen. The scripted race marks the line, sets the
wind from both tacks, enters the windward mark by bearing and distance, starts a 5-minute countdown three
seconds late and syncs it, crosses the line at the gun, beats to the windward mark up a corridor around
the course axis in an oscillating wind with more veer on the right, runs to the leeward mark and finishes.

## Conventions

- Wind direction is where the wind comes *from*, compass degrees. The *reference wind* is the histogram's
  weighted centre once it has 30 samples, the set wind until then.
- Starboard tack: wind over the starboard side, so heading = wind - angle; port: heading = wind + angle.
- A positive shift is a veer (clockwise). Positive time to kill means you are early.
- Upwind a veer lifts starboard and heads port; downwind it is the other way round.
- The map frame is wind up (the reference wind) with its origin at the middle of the start line; "right"
  is to the right when looking upwind. The racing area is the bounding box of the track, not a setting.
- Speeds are metres per second internally and knots on screen.
