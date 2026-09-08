# The race line

The map draws two lines up the beat. The green one is **the race line**: how to sail from where the boat is
now to the windward mark, board by board, with the tacks in it. The amber dashed one, when there is one, is
**the flyer**: the way that pays more when the wind is kind, and less when it is not. They are not a corridor
and not an average heading; each is a track that can be steered.

They come out of a Monte Carlo. The wind over the racing area is not one thing that has been measured, it is
a distribution that has been sampled - a mean, a spread and a speed histogram in every square - so the
question "which way is quickest" has no single answer. Sixteen winds are drawn from what has been measured,
the beat is searched in each of them, and every candidate line is then timed through all sixteen. The line
whose bad day is least bad is the one drawn green; the line whose good day is best, when that is worth more
than a tack, is drawn amber.

- `domain/course/WindField.kt` is the wind over the area, square by square, blended out of what was measured.
- `domain/course/WindSampler.kt` draws one wind out of it.
- `domain/course/RaceLineFinder.kt` beats to the mark through one wind, and times any line through any wind.
- `domain/course/RaceLinePlanner.kt` runs the Monte Carlo and picks the two lines.
- `domain/course/WindFieldSettings.kt` and `domain/course/RaceLineSettings.kt` hold every dial (both under
  `CourseSettings`), and `CourseModel` decides when it is all done again.

Ten worked examples, drawn from the algorithm itself, are in [RACE_LINE_GALLERY.md](RACE_LINE_GALLERY.md).

## What it is given

- **The racing area**, a rectangle of square cells in the wind-up course frame (`GridSpec`). It follows the
  track: it is the bounding box of everything sailed, the start line, the boat and the mark, in cells of
  5 m or a multiple of it - chosen to fit, or set by the sailor. Metres, origin at the middle of the start
  line, "up" is the reference wind.
- **A distribution for every square** (`WindField`):
  - the **mean wind**, stored as its *shift*: degrees off the reference wind, positive when veered;
  - the **spread**: how far that wind may be off, which is how much it wandered plus how much of a guess
    the mean still is;
  - the **speed histogram**, close-hauled speeds in quarter-metre bins, which is how a square that is half
    puff and half hole is told apart from a steady one with the same mean.

  Most of a racing area is never sailed through, so a square is not handed the nearest measurement as
  though it had been taken there. Every square - sailed through or not - is a blend of **three** things,
  each weighed by one over how wrong it could be:

  1. **its own samples**, worth `n / wander²`: the more of them and the steadier they were, the more the
     square is simply what it measured;
  2. **the samples around it**, gathered into *one* measurement (the squares weighed by how many samples
     they hold and by `exp(−distance / correlationLengthMeters)`), worth what that measurement is worth
     here - which the distance between them caps at `1 / (spatialSpreadDegrees² · (1 − shared²))`, however
     many squares agree. Weighing a hundred squares of one shift one by one would count one shift as a
     hundred opinions;
  3. **the wind over the whole course**, wherever it was measured, worth `1 / spatialSpreadDegrees²`: what
     is left over, and all that a corner nobody went near ever gets.

  A square with fifty of its own samples keeps its own wind; a square beside one borrows most of it; a
  square in the far corner shows the day's breeze, with the spread to say so. The speed histograms are
  blended with the same three weights, as a *mixture*, so an unsailed square between a puffy corner and a
  steady one is drawn sometimes from the one and sometimes from the other.
- **The boat**: where it is, **which tack it is on**, and **the wind it is measuring right now** - its own
  heading and tack angle, averaged over its last thirty close-hauled samples, so half a minute of sailing
  rather than one wave. Both are null when it is not beating, and then neither is used.
- **The mark**, the windward mark the sailor set or the middle of the top edge of the area until there is one.
- **The tack angle**, the angle between the wind and the closest course the boat holds upwind (45° by
  default, `WindSettings.tackAngleDegrees`).

## The model

Inside one square the wind and the boat speed are constant, so a close-hauled boat has exactly two courses
there:

```
starboard = wind in the square − tack angle        port = wind in the square + tack angle
```

That is the whole boat model: this is a beat, and a boat that is beating sails one of those two courses, at
the speed that square has been measured at. Nothing else is on offer, which is what makes the search finite
and the line a real track. The line bends where the wind is different from one square to the next, on the
same tack, without a tack in it.

A **board** is one straight crossing of one square: from a point on its boundary, along one of the two
courses, to the point where that course leaves the square. That exit point is the start of the next board.
The first board starts at the boat, wherever it is inside its square.

Sketch of a beat through the squares, wind up the page:

```
              mark
               X
    +----+----+----+----+
    |    |    |   /|    |     /   a board on port tack (wind + tack angle)
    +----+----+--/-+----+     \   a board on starboard tack (wind - tack angle)
    |    |    |/   |    |     *   a tack, where two boards meet
    +----+---/+----+----+
    |    |  /*|    |    |     Every board crosses one square, from the
    +----+-\--+----+----+     boundary it starts on to the boundary it
    |    |  \ |    |    |     leaves by. The wind of that square, and the
    +----+---\+----+----+     tack, fix its direction exactly.
    |    |    |\   |    |
    +----+----+-\--+----+
    |    |    |  \ |    |
    +----+----+---\+----+
                   boat
```

## The rules

1. **A board crosses exactly one square** and ends on that square's boundary.
2. **A board may not leave the racing area.** At the edge of the area the only course left is the other
   tack, so the boat tacks there.
3. **A board must shorten the beat still to sail.** What is left to sail from a point is the distance to
   the mark when the boat can lay it, and `distance / vmgFactor` - the length of the zigzag - while the
   mark lies inside the no-go zone. This is the "no sailing backwards, no sailing away from the mark"
   rule, and it is the layline: a board can be held while the beat still gets shorter, and the board that
   would carry the boat past the point where tacking lays the mark is refused, because from there every
   metre sailed is a metre lost.

   Measuring this on the *straight* distance to the mark instead is the same rule only for a boat that
   tacks through exactly a right angle. A boat with a tack angle over 45 degrees - anything from 46 to 80
   is a legal setting - sails a board near the layline that lengthens the straight distance and still
   shortens its beat, and the straight-distance rule stops it short of its own layline, where it can never
   lay the mark at all and falls into a staircase of extra tacks. This is what the rule looked like first,
   and an independently written test caught it.
4. **Tacking costs time.** Sailing the next board on the other tack costs `TACK_LOSS_SECONDS` on top of the
   time it takes - including the *first* board, when it is not on the tack the boat is on right now. Given
   no tack (the boat is not beating) the line starts on whichever tack suits it, for nothing.
5. **The target is reached** as soon as a board ends at a point that touches the square that holds the
   mark, whichever way the boat is heading at the time. (Of any of them: a mark on a boundary belongs to
   the two, or four, squares that touch it.) Asking instead which square the boat is *heading into* misses
   the boat that arrives at the corner of the mark's square sailing the other way, which is exactly what
   happens in a narrow racing area - another catch from an independently written test.

   What is left is added as the **run in**: the time to sail straight from there to the mark, charged at
   the speed made good beating when the mark is upwind of that point rather than at boat speed, so that
   reaching the mark's square in a far corner is not free, **plus a tack** when the run in can be laid but
   only on the other tack than the board arrived on. Without that last charge a line could stop one board
   early and be handed the tack it still has to make for nothing. A line that ends on the mark itself has
   no run in, and the mark is that last board's end rather than a point repeated after it.
6. **The fastest line wins**, and that is the one drawn. Of two lines that take the same time, the one that
   sails furthest into the mark's square wins, because the boards are searched geometry where the run in is
   only an estimate.

## The cost of a board

```
seconds = length in metres / the boat speed of that square  (+ the tack loss when it is sailed on the other tack)
```

The speed is the square's own: the mean of what was measured there, or one drawn from its histogram in a
simulation, or 3 m/s where nothing has been measured yet (`SailingConditions.DEFAULT_BOAT_SPEED_MPS`). So a
board through a puff is worth more than a board through a hole, and a square in the lee of an island is
sailed through slowly whichever way the wind in it points.

A tack costs `TACK_SECONDS` = 8 s at `TACK_SPEED_FRACTION` = 55 % of full speed, so it throws away
`8 × (1 − 0.55)` = 3.6 s, about 11 m for a dinghy. The times are honest seconds and the map shows them.

## The search

Breadth first over the number of boards, which is what "one square at a time" makes natural:

```
frontier ← { the boat, on the tack it is on, 0 s }
repeat:
    for every state in the frontier:
        for every square touching its point:
            for both tacks:
                work out the course from that square's wind and the tack angle
                cut it off at the boundary of that square         (rule 1, rule 2)
                drop it when it does not gain on the mark         (rule 3)
                add the seconds at that square's speed, and the tack loss if it tacked   (rule 4)
                drop it when the best line so far is already quicker
                drop it when this state has been reached quicker before
                if it arrives at the mark's square: remember it if it is the best finish  (rule 5)
                otherwise put it in the next frontier
    frontier ← the next frontier
until the frontier is empty, or the boards run out
```

A **state** is a point on a boundary *plus the tack the boat is on there*: the same point on starboard and
on port are two states, because what they cost from there differs by a tack.

The search remembers the best time it has seen for every state and only carries a state forward when that
time improved on it, so a state that is reached again by a longer but quicker route is expanded again.
That makes the whole thing a relaxation over the number of boards, in the Bellman-Ford sense, and the times
it ends up with are the optimal ones. The cut-off is `MAX_BOARDS_PER_CELL` (4) boards per row and column of
the area, many times what a beat up an area of that size needs.

Two points on a boundary are the same state when they are within one slot of a lattice of `EDGE_SLOTS`
(32) slots per square side - 0.3 m in a 10 m square, 1.6 m in a 50 m one. Without that the state space is
continuous and the search would never close; with it, a few tens of thousands of states cover a whole
racing area. The lattice is only used to *compare* states: the geometry of the line that comes out is the
exact intersection arithmetic, never snapped to it.

It is the one approximation in the search, and how coarse it is, is the one dial. Measured on sixty
mirrored beats, 8 slots per side let the two mirror images of the same course come out up to 2.0 s apart,
16 slots up to 0.6 s, and 32 slots agree to the last decimal - for about twice the work of 8. Against an
exhaustive enumeration of every legal line over a 4 x 5 and a 3 x 4 area, in five wind fields each (even,
one side shifted, a diagonal gradient, stripes and a seeded jumble), the search at 32 slots finds the same
time to the last decimal in all ten.

Two lines can take exactly the same time - in an even wind, a beat and its mirror image do - and then the
one the search reaches first wins. That is deterministic between runs, but it favours neither side.

The inner loop is written for speed rather than for looks, because the Monte Carlo runs it sixteen times:
every square's wind is turned into a cosine and a sine once per search, the two close-hauled courses come
out of those by the angle-sum identities, "can the boat lay this" and "how long is the beat from here" are
comparisons of a dot product with the cosine of the tack angle, and the best time per state lives in an
open-addressed table of primitives. That is about three milliseconds for a full 12 x 12 area on a desktop,
against twenty-five for the same search written the obvious way.

## The Monte Carlo

One search answers "what is quickest **in this wind**". The wind is not known that well, so the planner asks
it many times.

### Drawing a wind

`WindSampler` draws a whole field at once - a direction and a boat speed for every square:

- Every square already has a **mean** and a **spread** from the blend above, floored at `minSpreadDegrees`
  (no wind is known better than 3°: the tack angle it is read from is a model, the compass wanders, the
  helm steers) and capped at `maxSpreadDegrees`.
- **The wind the boat is measuring right now** moves those means and narrows those spreads. In the square
  the boat is in, the mean is pulled `currentWindWeight` (0.8) of the way onto it and the spread is cut by
  `√(1 − weight)`, because that wind has just been measured. The weight fades as `exp(−distance / range)`
  with `currentWindRangeMeters` (200 m), so the header the boat is in now says a lot about the water just
  ahead and almost nothing about the far corner.
- **The squares do not come out as a chequerboard.** Wind arrives in shifts hundreds of metres wide, so the
  draws are correlated: independent normal deviates are run through a first order filter across the area and
  then up it, which leaves every square with exactly the spread it should have and any two of them
  correlated by `exp(−distance / correlationLengthMeters)` (150 m), counted in squares along and up. A drawn
  wind is a few broad shifts lying over the course.
- **The speeds are drawn from the histograms**, not from a bell curve, through the same correlated field:
  a square that is half puff and half hole is drawn as a puff or as a hole and never as its mean. Only
  `speedSpreadFactor` (0.6) of the histogram's spread is felt, because the histogram is of speeds a second
  apart and a whole board across a square averages a handful of them.

### Picking the two lines

1. The beat is searched in each of the `runs` (16) drawn winds, and once in the mean wind. That gives one
   candidate line per wind: every candidate is the right answer to *some* wind the course might have, which
   is what keeps the list sensible - a candidate is never a line nobody would sail.
2. Every candidate is then **sailed through every drawn wind** (`RaceLineFinder.secondsToSail`): the same
   track, timed at each square's drawn speed, with any leg that the drawn wind no longer lets the boat lay
   charged as the beat it has become. A line that overstands its layline in one wind is sailing away from
   the mark in another, and a line that hunts a puff in one is sitting in a hole in another; both come out
   of the timing without a rule about corners or laylines being written anywhere.
3. Because all the candidates are timed over the *same* winds, the difference between two of them is a real
   difference and not the noise of two separate draws.
4. The **race line** is the candidate whose bad day (the time it is beaten by in one run in five,
   `riskFraction`) is least bad. The **flyer** is the candidate whose good day (the time it beats in one run
   in five) is best - but only when that is worth more than a tack, because in a settled wind a dozen lines
   are within a second of each other and the chance of the draw would decide which of them is "fastest".
   Otherwise there is no flyer and the map says so.

The map shows the mean time over the runs for each, the bad day for the race line, the good day for the
flyer, and how many of the winds the flyer actually won. That last number is the honest size of the bet: a
flyer that wins 5 of 16 is a flyer.

## The dials

All of them are beliefs about water rather than facts, and the defaults are for a dinghy course of a few
hundred metres in a shifty coastal or inland breeze. What the wind over the area is taken to be like is
`WindFieldSettings`:

| Dial | Default | What it says |
| --- | --- | --- |
| `minCellSamples` | 3 | How many of its own samples a square needs before the map draws its arrow solid. |
| `correlationLengthMeters` | 150 | How far the wind hangs together: how much of a neighbour's wind is this square's, and how smooth a drawn field is. |
| `neighbourhoodRadii` | 3 | How many correlation lengths away a square still listens. |
| `spatialSpreadDegrees` | 12 | How much the mean wind differs from one part of the course to another: what an unsailed corner is left with. |
| `sampleSpreadDegrees` | 8 | How much the wind is assumed to wander where that cannot be measured yet. |
| `minSpreadDegrees` | 3 | No square's wind is known better than this. |
| `maxSpreadDegrees` | 30 | No square is a lottery either. |
| `minWeightFraction` | 0.005 | A neighbour worth less than this is left out of the speed blend. |

How the search makes use of it is `RaceLineSettings`:

| Dial | Default | What it says |
| --- | --- | --- |
| `runs` | 16 | How many winds are tried. Twice as many is twice the work and a slightly steadier answer. |
| `currentWindWeight` | 0.8 | How much the wind measured right now overrules the square's own mean. |
| `currentWindRangeMeters` | 200 | How far that belief reaches; a third of it is left one range away. |
| `speedSpreadFactor` | 0.6 | How much of the speed histogram's spread a whole board feels. |
| `minBoatSpeedMps` | 0.5 | The slowest a drawn speed may be, so a line never costs eternity. |
| `riskFraction` | 0.2 | Which end of the spread counts as luck: one run in five, either way. |
| `recomputeShiftDegrees` | 5 | How far the wind of the moment moves before the whole plan is done again. |
| `seed` | fixed | The same course always gives the same advice. |

## When it is worked out again

Only when the boat sails into a new square of the racing area, when the wind it is measuring has moved more
than `recomputeShiftDegrees`, or when the area, the mark, the tack angle or the tack the boat is on changes.
In between, the plan found from the square the boat is in is kept and its first point is moved onto the
boat, so it stays attached to the boat on the map without being searched four times a second; the error that
introduces is at most the width of one square. `CourseModel.build` does this, with the model of the moment
before.

The wind field keeps being measured while the boat crosses a square; a change in it is picked up at the next
square. That is the point of recomputing per square: a line that is re-searched every second flickers
between equally quick alternatives, and a sailor cannot steer to a line that moves.

## What it deliberately does not model

- **Current.** There is none in the model; over ground and through the water are the same thing here.
- **The wind changing while the boat sails.** A drawn wind is one wind, held still for the whole beat. The
  simulations are over *which* wind the course has, not over how it changes while the beat is sailed, so an
  oscillation that will come back is not projected forward; the tack advice on the race screen is what
  watches the oscillation.
- **Other boats, tide lines, obstructions, the layline traffic.** None of it exists in the model.
- **Sailing the last bit inside the mark's square.** It is charged as a straight run in at the speed made
  good, plus the tack it costs if it has to be sailed on the other tack, and drawn straight rather than
  searched. It is at most one square long, and a line that can end exactly on the mark does.
- **The boat's speed being the boat's own fault.** A square where the sailor happened to sail badly is a
  slow square, and the line will avoid it. Over a beat or two the histograms fill up and that washes out.

## Reading it on the water

- In an even wind the race line is one long board to the layline and one home: with nothing to gain by
  tacking, the fewest tacks wins, and there is no flyer.
- Where a side of the course is shifted, the line stays in it: sailing the lifted tack in the shifted air is
  quicker, and the line shows it as a beat that hugs that side. Where the wind comes in bands, it tacks on
  them and stays in the middle.
- Where one side has more wind, the line goes there even with the wind straight up the course: that is the
  speed histograms talking.
- **An amber dashed line means the two sides are a bet.** It appears where one side is shiftier, puffier or
  simply less sailed than the other: more to win there, and more to lose. The caption says how many of the
  simulated winds it actually won. Sail the green one to keep a place; take the amber one when you need one.
- In a narrow racing area the line bounces off the edges of the area, because the area is what has been
  sailed so far. Sail wider and the area grows with the track.
