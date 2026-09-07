# Sail Racing

A native Android app (Kotlin, Jetpack Compose) that helps a sailor through a race: mark the start line,
set the wind, run the start countdown with beeps and a live *time to kill*, then steer to the wind with
shift and speed statistics. See [Design.md](Design.md) for the original feature design.

## Features

- **Start line**: mark the pin and boat ends at your position; the line is stored across restarts.
- **Wind**: enter it manually, or sail close-hauled and press *Starboard tack* / *Port tack* to derive it
  from your heading and the configured tack angle. While sailing upwind the app builds a histogram of
  the estimated wind direction, a shift history and average upwind/downwind speed and VMG.
- **Countdown**: 5, 4 or 1 minute starts, *Sync* to the nearest minute, beeps every minute, every 10 s in the
  last two minutes, every second in the last 10 s and a burst at the gun (with vibration).
- **Start**: distance to the line, time to kill (green = early, red = late) and an *over the line* warning.
- **Racing**: a heading-up compass rose with wind, estimated wind and the close-hauled/downwind target
  headings; a steering hint; speed, VMG and the averages.
- **Simulation mode**: a scripted boat sails a complete race so every feature can be tried on the couch.
- Pure black AMOLED theme, big glove-friendly buttons, text that scales to the screen, landscape support,
  a foreground service so GPS and beeps keep running with the screen off.

## Project layout

| Module | What it is | Tests |
| --- | --- | --- |
| `domain` | Pure Kotlin race logic: geo math, start line, countdown and cues, wind estimation and statistics, the `RaceReducer` state machine and the `RaceCalculator` that derives everything shown. No Android dependency. | JUnit 5, 100 % line coverage enforced by Kover |
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
simulation speed. The scripted race marks the line, sets the wind from both tacks, starts a 5-minute
countdown three seconds late and syncs it, crosses the line at the gun, beats to the windward mark in an
oscillating wind, runs to the leeward mark and finishes.

## Conventions

- Wind direction is where the wind comes *from*, compass degrees.
- Starboard tack: wind over the starboard side, so heading = wind - angle; port: heading = wind + angle.
- A positive shift is a veer (clockwise). Positive time to kill means you are early.
- Speeds are metres per second internally and knots on screen.
