# The race line

The map draws two lines up the beat. The green one is **the race line**: how to sail from where the boat is
now to the windward mark, board by board, with the tacks in it. The amber dashed one, when there is one, is
**the flyer**: the way that pays more when the wind is kind, and less when it is not. They are not a corridor
and not an average heading; each is a track that can be steered.

They come out of a Monte Carlo. The wind over the racing area is not one thing that has been measured, it is
a distribution that has been sampled - a histogram of wind directions and one of boat speeds in every big
square of the course - so the question "which way is quickest" has no single answer. A thousand winds are
drawn from what has been measured, a beat is searched in sixteen of them, and every candidate line is then
timed through all thousand. The line whose bad day is least bad is the one drawn green; the line whose good
day is best, when that is worth more than a tack, is drawn amber.

- `domain/course/WindField.kt` is what has been measured of the wind, big square by big square.
- `domain/course/WindSampler.kt` draws one whole wind out of it.
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
- **A distribution for every big square** (`WindField`). The wind is *not* worked out per square of that
  grid. A race is sailed up the middle and back down it: cut the area into 50 m squares and almost every
  one of them holds no close-hauled samples at all, and the few that were sailed hold a handful of seconds
  each - which is not a distribution, it is an anecdote. So the wind is kept in **big squares**:
  `columns` (3) of them across the area - the left, the middle and the right of the course, which is the
  question a beat actually asks - and as many rows as it is tall, each of them square. The fine squares are
  still there and still carry what was measured in them; they simply take their wind from the big square
  they lie in, so the *geometry* of a line stays as sharp as the area is cut while its *wind* stays as
  coarse as the evidence for it.

  Each big square holds only what was measured inside it:

  - a **histogram of wind directions**, in one-degree bins, kept as *shifts*: degrees off the reference
    wind, positive when veered. Not a mean and a spread - the shape is the point, because a square that
    swung twenty degrees either way all afternoon and one that sat five degrees veered all day are not the
    same water however close their averages;
  - a **histogram of close-hauled boat speeds**, in quarter-metre bins, with their exact mean beside it,
    which is how a square that is half puff and half hole is told from a steady one with the same average;
  - its **share** of every sample taken on the course, from 0 (nobody sailed here) to 1 (nobody sailed
    anywhere else).

  Nothing is smoothed, blended or interpolated: a square nobody sailed through holds nothing and says so
  with a share of zero. What such a square should *blow* is a question about drawing a wind, not about
  measuring one, and it is answered below.
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

The speed is that of the big square the board crosses: the mean of what was measured there, or one drawn
from its histogram in a simulation, or 3 m/s where nothing has been measured anywhere yet
(`SailingConditions.DEFAULT_BOAT_SPEED_MPS`). So a
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

The inner loop is written for speed rather than for looks, because the Monte Carlo runs it sixteen times over:
every square's wind is turned into a cosine and a sine once per search, the two close-hauled courses come
out of those by the angle-sum identities, "can the boat lay this" and "how long is the beat from here" are
comparisons of a dot product with the cosine of the tack angle, and the best time per state lives in an
open-addressed table of primitives. That is about three milliseconds for a full 12 x 12 area on a desktop,
against twenty-five for the same search written the obvious way.

## The Monte Carlo

One search answers "what is quickest **in this wind**". The wind is not known that well, so the planner asks
it many times.

### Drawing a wind

`WindSampler` draws a whole field at once - a direction and a boat speed for every big square. Which of
**four** things a square blows this time round is decided by the dice:

| | Share | What it is |
| --- | --- | --- |
| the wind of the moment | `currentWindFraction` = 35 % | What the boat is measuring *right now*, from its own heading and tack angle. It is the freshest measurement of the day and a beat is planned in the wind you are in, not the wind you averaged an hour ago. It is drawn **once per simulation and laid over the whole course**: in a third of the winds every square blows it and in the rest none of them does, because a shift the boat is sitting in is a shift over the water and not a scattering of squares that happen to agree. Without one - the boat is not close-hauled - its share goes to the histograms. |
| the square's own history | `measuredFraction` (60 %) × `share` | The histogram measured inside that square. |
| the day's history | `measuredFraction` (60 %) × (1 − `share`) | The histogram of every sample taken anywhere on the course. So a square where half the race was sailed mostly blows its own wind, a square nobody went near blows the day's wind, and nothing had to be interpolated to say so. |
| anything at all | `randomWindFraction` = 5 % | A direction drawn flat around the compass: the shift nobody saw coming. It is what a line has to survive to be called safe. |

A direction out of a histogram is drawn from **exactly** the distribution it holds - a bin in proportion to
its count, and anywhere inside that bin - never from a bell curve fitted over it. A wind that has been
oscillating between two shifts is drawn as one shift or the other, which is what it does, instead of as the
middle it never blows.

Every big square is drawn on its own, so one drawn wind is not a single shift laid over the course: it is
the left, the middle and the right each doing their own thing, which is the situation the race line is
there to judge. A block is about a third of the width of the area - the scale a shift actually has - so
treating one as independent of the next is honest, where the same assumption over 20 m squares would have
been a chequerboard.

**The speeds are drawn alongside and independently**: from the square's own speed histogram or the whole
course's, by the same share, so a corner that was half puff and half hole comes out as a puff or a hole and
never as its mean. Only `speedSpreadFactor` (0.6) of the histogram's spread is felt, because the histogram
is of speeds a second apart and a whole board across a square averages a handful of them. A square being
shifted says nothing about it being windy.

The **mean wind** - every square at the mean of that whole mixture, the four weighed exactly as they are
weighed in a draw - is the wind the plain candidate line is searched in.

A thousand of these cost almost nothing: a handful of dice per big square, and the histograms are turned
into cumulative tables once per plan. It is *searching* a beat that is dear, which is why only sixteen of
the thousand get one.

### Picking the two lines

1. `runs` (1000) winds are drawn. A beat is searched in the first `searchRuns` (16) of them and once in the
   mean wind. That gives one candidate line per searched wind: every candidate is the right answer to *some*
   wind the course might have, which is what keeps the list sensible - a candidate is never a line nobody
   would sail. Searching all thousand would only find the same lines again, for sixty times the work. A wind
   drawn wild enough to leave no beating to the mark at all sends the search back with the straight course
   to it, and that is left out of the list: a straight course is charged the ideal beat and never a tack, so
   it would flatter itself against every line that says how the beat is actually sailed.
2. Every candidate is then **sailed through every one of the thousand drawn winds**
   (`RaceLineFinder.secondsToSail`): the same track, timed at each square's drawn speed, with any leg that
   the drawn wind no longer lets the boat lay charged as the beat it has become. A line that overstands its
   layline in one wind is sailing away from the mark in another, and a line that hunts a puff in one is
   sitting in a hole in another; both come out of the timing without a rule about corners or laylines being
   written anywhere.
3. Because all the candidates are timed over the *same* winds, the difference between two of them is a real
   difference and not the noise of two separate draws.
4. The **race line** is the candidate whose bad day (the time it is beaten by in one run in five,
   `riskFraction`) is least bad. The **flyer** is the candidate whose good day (the time it beats in one run
   in five) is best. Both have to better the line through the mean wind - the answer to the wind as it
   stands - by more than a tack, or that plain line stays: up an even beat half a dozen lines are within a
   second of one another and which of them has the best day is the luck of a thousand draws, not a side of
   the course, and a race line has to stay still enough to steer to.

The map shows the mean time over the runs for each, the bad day for the race line, the good day for the
flyer, and how many of the thousand winds the flyer actually won. That last number is the honest size of the
bet: a flyer that wins 300 of 1000 is a flyer.

## The dials

All of them are beliefs about water rather than facts, and the defaults are for a dinghy course of a few
hundred metres in a shifty coastal or inland breeze. What the wind over the area is taken to be like is
`WindFieldSettings`:

| Dial | Default | What it says |
| --- | --- | --- |
| `columns` | 3 | How many big squares the area is cut into across: the left, the middle and the right of the course. The rows follow, because a big square is square. |
| `currentWindFraction` | 0.35 | How many of the simulated winds are simply what the boat is measuring right now, over the whole course. |
| `randomWindFraction` | 0.05 | How many of them are a direction drawn flat around the compass. |
| `minBlockSamples` | 3 | How many of its own samples a big square needs before the map draws its arrow solid. |
| `speedSpreadFactor` | 0.6 | How much of the speed histogram's spread a whole board feels. |
| `minBoatSpeedMps` | 0.5 | The slowest a drawn speed may be, so a line never costs eternity. |

What is left of the 100 % - 60 % by default - is the measured wind, split between the square's own
histogram and the whole course's by how much of the day's evidence the square holds.

How the search makes use of it is `RaceLineSettings`:

| Dial | Default | What it says |
| --- | --- | --- |
| `runs` | 1000 | How many winds are drawn and every candidate timed through. Cheap: turn it up for a steadier answer to "how bad is its bad day". |
| `searchRuns` | 16 | How many of those winds get a beat searched in them. The dear one: a searched beat costs about a hundred drawn winds. |
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
- **An amber dashed line means the two sides are a bet.** It appears where one side is puffier, or where
  the side that pays depends on which way the wind goes: more to win there, and more to lose. The caption
  says how many of the thousand simulated winds it actually won. Sail the green one to keep a place; take
  the amber one when you need one.
- **A big square only speaks for itself as far as its share of the day goes.** Sail three beats up the
  right of the course and the right blows what you measured there; measure a couple of minutes in a corner
  and that corner mostly blows the day's wind, however odd those two minutes were. That is the price of
  never inventing a wind for water nobody has sailed.
- In a narrow racing area the line bounces off the edges of the area, because the area is what has been
  sailed so far. Sail wider and the area grows with the track.
