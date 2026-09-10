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
- **Race screen**: built for a helm who looks at it for a second between the telltales and the water, so
  nothing on it scrolls. The map with the race line fills the top of the screen (the left half on a phone
  on its side), with what the line costs written along its bottom edge. Under it the glance panel:
  *HOLD* or *TACK* and *GO LEFT* / *GO RIGHT*, in big coloured words with the reason under each, then the
  speed against the day's average on that point of sail (green faster, red slower) and the shift from the
  mean wind with a strip of the histogram showing where the wind sits now. A bar under that carries the
  race time and *More*, which swaps the map for everything else - the compass rose, all the wind numbers
  and averages, the wind and mark buttons, the full histogram, the statistics and the map's legend - while
  the glance panel stays put; *Map* brings the map back. A *Fit* button appears on the map while it is zoomed.
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
  At the starting gun the app switches to the race screen by itself.
- **Map**: a drawn (not downloaded) wind-up map whose racing area follows the track: its bounding box
  (with the line, the boat and the mark) in squares of 5 m or a multiple of it - automatically, or the
  size set under *Settings → Map* (5 m to 200 m). It is drawn as big as the room allows, the whole area at
  one scale; pinch to zoom, drag to move, double tap (or the *Fit* button) to fit the area again. Over that
  fine grid lie the **big squares the wind is worked out in** - three across the course and as many rows as
  it is tall - each with an arrow of the wind measured there, solid where enough of it was measured and
  faint where nobody has sailed; amber veered, blue backed.
- **The wind is kept in big squares, not in the little ones.** A race is sailed up the middle and back down
  it, so most 50 m squares of a course hold no close-hauled samples at all and the sailed ones hold a
  handful of seconds each - an anecdote, not a distribution. Every big square instead keeps a *histogram* of
  the wind shifts sailed through it and one of the boat speeds, plus its share of the day's samples. Nothing
  is smoothed or interpolated: a square nobody sailed through holds nothing, and when a wind is drawn for it
  the whole course's histogram is what it blows.
- **Windward mark, the race line and the flyer**: set the mark at your position or, as the committee posts
  it, by bearing and distance from the line; without one the middle of the top edge of the area is used.
  The beat is not searched once but through a thousand simulated winds, each of them drawn big square by
  big square: 35 % of the time the wind the boat is measuring right now (over the whole course at once),
  60 % split between the square's own histogram and the whole course's by how much of the day's evidence
  the square holds, and 5 % anything at all - the shift nobody saw coming. A beat is searched in sixteen of
  those winds and every candidate line is then timed through all thousand.
  The **green line** is the one to sail: board by board with the tacks in it, the line whose bad day is
  least bad. Where one side of the course is a bet - puffier, or paying only if the wind goes one way - an
  **amber dashed line** appears beside it: the flyer, quicker when the wind is kind. The map says how many
  tacks each holds, how long it takes, how bad its bad day is and how many of the thousand winds the flyer
  won.
  It is worked out again when the boat sails into a new square or the wind it is measuring moves 5°.
  [docs/RACE_LINE.md](docs/RACE_LINE.md) has the algorithm, and
  [docs/RACE_LINE_GALLERY.md](docs/RACE_LINE_GALLERY.md) has ten worked examples in pictures.
- **Best side**: the app compares the wind and boat speed measured on the left and right of the line from
  the start to the mark, adds the recent trend of the wind, and says *GO LEFT*, *GO RIGHT* or *SIDES EVEN*,
  with the numbers behind it. Sail towards the side the wind is shifted to.
- **Simulation mode**: a scripted boat sails instead of the GPS, so every feature can be tried on the couch.
  Eleven of them to choose from in *Settings*: the complete race the app has always had, and ten **practice
  sessions** that sail **five windward-leeward laps before they start**, each in a different realistic
  breeze named in its title - steady, oscillating big or small, a wind that keeps veering or backing, a
  shore bending the breeze down one side, pressure on the left, puffs and holes, a sea breeze filling in.
  Five laps leave no square of the racing area unmeasured, so the race line drawn on the last beat can be
  judged against a wind that is known exactly. [docs/SIMULATIONS.md](docs/SIMULATIONS.md) says what to look
  for in each.
- **Session log**: everything - every fix, every compass reading, every button, every beep, and once a
  second everything the app made of them - is written to a file per session in
  `Android/data/com.sailracing.app/files/sessions`, to go through afterwards or replay through the engine.
  See [docs/LOGGING.md](docs/LOGGING.md); it can be switched off, sized up and deleted in *Settings*.
- Pure black AMOLED theme, big glove-friendly buttons, text that scales to the screen, landscape support,
  a foreground service so GPS and beeps keep running with the screen off.
- The compass expects the phone to stand upright on the mast with the screen facing aft; a flat phone
  falls back to its top edge.

## Project layout

| Module | What it is | Tests |
| --- | --- | --- |
| `domain` | Pure Kotlin race logic: geo math, start line, countdown and cues, wind estimation and statistics with the `WindReference`, the `CourseModel` (the track-following area, its grid, the interpolated `WindField` of wind and speed distributions, and the `RaceLinePlanner`'s Monte Carlo over the `RaceLineFinder`), the `UpwindStrategy` (tack advice and favoured side), the `RaceReducer` state machine and the `RaceCalculator` that derives everything shown. No Android dependency. | JUnit 5, 100 % line coverage enforced by Kover |
| `simulation` | Deterministic boat, wind and course simulator, `StandardRaceScenario` (a scripted full race that emits GPS fixes and the sailor's button presses), `PracticeScenario` (five windward-leeward laps and then a start) and the `SimulationCatalog` of eleven simulations the app can load. | JUnit 5, 100 % line coverage enforced; `FullRaceEndToEndTest` drives the real engine through the race and checks every feature against ground truth |
| `app` | The Android app: sensors, DataStore persistence, `RaceSession` (engine + sensors + ticker + persistence + session log), the foreground service and the Compose UI. | JUnit 4 + Robolectric + Compose UI tests, including a full simulated race through the session in virtual time |

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
demo, or switch it off to press the buttons yourself as the scripted boat sails), pick one under *Which
race* and optionally raise the simulation speed, then start a session on the *Session* screen. The scripted
race marks the line, sets the wind from both tacks, enters the windward mark by bearing and distance,
starts a 5-minute countdown three seconds late and syncs it, crosses the line at the gun, beats to the
windward mark up a corridor around the course axis in an oscillating wind with more veer on the right, runs
to the leeward mark and finishes.

To judge the race line, pick one of the ten practice sessions instead: the boat sails five laps up and down
the course - up the middle, out to the left, out to the right - before it starts, so the whole racing area
has been measured by the time it beats up for the last time. Each is a different breeze, named in its
title; raise the simulation speed to 10× or 20× to see one out in a few minutes. Switching to another one
clears the session by itself. [docs/SIMULATIONS.md](docs/SIMULATIONS.md) lists the ten and what the race
line should make of each.

## Conventions

- Wind direction is where the wind comes *from*, compass degrees. The *reference wind* is the histogram's
  weighted centre once it has 30 samples, the set wind until then.
- Starboard tack: wind over the starboard side, so heading = wind - angle; port: heading = wind + angle.
- A positive shift is a veer (clockwise). Positive time to kill means you are early.
- Upwind a veer lifts starboard and heads port; downwind it is the other way round.
- The map frame is wind up (the reference wind) with its origin at the middle of the start line; "right"
  is to the right when looking upwind. The racing area is the bounding box of the track, not a setting; the
  size of the squares it is cut into is one (*Settings → Map*), and nothing measured is lost by changing it.
- Speeds are metres per second internally and knots on screen.
