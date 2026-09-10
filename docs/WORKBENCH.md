# The strategy workbench

A program for the PC that draws a course by hand and asks the app what it makes of it: lay a start line
and a windward mark, set the wind, drag a track up the beat, and get back the route the phone would draw.

```bash
./gradlew :desktop:run
```

It is a window onto `:domain`, the module the Android app is built on. It holds no race logic whatever:
the drawing is turned into the GPS fixes a boat would have produced, those go through the same
`RaceReducer` and `RaceCalculator` the phone runs, and what comes back is a `RaceSnapshot` - the very
value the Race screen is drawn from. A course that plans a bad line here plans the same bad line on the
water, which is the whole point of it.

## What is on the screen

The chart is **north up**, because the drawing is a chart and not the map on the phone: setting another
wind turns the strategy's view of the course and leaves the track where it was drawn. That is why the
squares of the racing area lie at an angle on it - they are the wind's squares, and the wind is not north.

| Drawn | What it is |
| --- | --- |
| Grey line | the track: where the boat has been, which is everything the app knows about the wind |
| Faint squares and arrows | the racing area and the `WindField`: one wind per square, solid where it was measured and faint where it is mostly the day's average |
| Yellow line and dots | the start line: the pin end and the committee boat |
| Orange dot | the windward mark; without one, the middle of the top of the racing area, and it says so |
| **Green line** | the race line: the route to sail, the one whose bad day is least bad |
| **Amber dashed line** | the flyer, when there is one: quicker when the wind is kind, and by how much |
| Blue arrow, top left | the reference wind, with the two close-hauled headings around it |

Underneath is the report: the same sentences the phone shows, out of the same `CourseText` and `PlanText`.

## Drawing

| | |
| --- | --- |
| Left button | draws with the tool in hand: **Track**, **Pin end**, **Committee boat** or **Windward mark** |
| Left button, held | carries on the same gesture - more track, or the mark dragged where it is wanted |
| Right button, held | moves the water about |
| Wheel | zooms on the mouse |
| `F` | fits the chart around everything drawn |
| `Ctrl` + `Z` | takes back the last gesture |
| `Enter`, or *Plan route* | asks for the route again |

The route is planned when a gesture ends and whenever a dial is turned, off the event thread, and an
answer to a course that has changed since is thrown away rather than drawn.

### The track is the wind

There is no separate "set the wind here" tool, and there does not need to be one: the app reads the wind
off the boat's own heading and tack angle, so **a leg drawn at a different angle is a wind that shifted**.
Draw the right of the course fanning clockwise and the right of the course is veered; the strategy cannot
tell that from a real veer, and neither can the sailor. The boat's speed does the other half: a beat drawn
slow on one side and quick on the other is pressure on the other, which is what makes an unsailed corner a
gamble worth taking or not.

Only close-hauled sailing says anything: a leg more than
`RaceSettings.upwindMaxTwaDegrees` (60°) off the wind feeds no wind sample, and the sample at a corner is
thrown away as a tack, exactly as on the water. The report says how much of the track counted.

The wind you set is a *seed*, not a decree. Once the drawn track has 30 close-hauled samples the app judges
by the wind it measured, and the report says which of the two it used - on the workbench exactly as on the
phone.

## Courses on disk

*Save…* writes a course as text, one statement a line, and *Open…* reads it back:

```
sketch 1
name Right-hand veer
origin 52.0000000 5.0000000
wind 0 45 140
runs 16
pin -60.0 0.0
start 60.0 0.0
mark 0.0 600.0
point 0.0 0.0 3.00
point 2.1 2.1 3.00
```

Metres and degrees, blank lines and `#` comments ignored, and any keyword a later version of the format
adds is sailed past by an older one. It is meant to be kept in git beside a test: a course that once got
bad advice is a regression test as soon as it is a file.

Two worked examples ship in [`desktop/courses`](../desktop/courses) - an even breeze and a course whose
right-hand side was sailed twelve degrees veered - and `ExampleCoursesTest` opens and plans both, so they
cannot quietly stop working.

## Where the code is

| | |
| --- | --- |
| `domain/sketch/CourseSketch` | the drawn course: the line, the mark, the wind, and the track in north-up metres |
| `domain/sketch/SketchReplay` | the drawing as the GPS fixes a boat would have produced |
| `domain/sketch/SketchAnalysis` | those fixes through a real `RaceEngine`, and the `RaceSnapshot` that comes out |
| `domain/sketch/CourseSketchFormat` | reading and writing the text above |
| `domain/geo/LocalPlane` | the north-up metre grid the drawing lives on; `CourseFrame` is it turned into the wind |
| `desktop/workbench/Workbench` | the editing itself as a value: a gesture in, a course out |
| `desktop/workbench/Viewport` | metres to pixels and back, with the pan, the zoom and the fit |
| `desktop/workbench/Report` | the words, all of them from `domain/text` |
| `desktop/ui` | the window: Swing, which every JDK already has, so there is nothing to install |

Everything but `desktop/ui` is covered by tests to 100 %, the same gate the domain is held to.
