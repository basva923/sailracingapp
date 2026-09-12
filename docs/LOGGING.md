# The session log

Every session is written to a file as it happens: every fix, every compass reading, every button the sailor
pressed, every beep, and about once a second everything the app made of it. It is the app's black box - the
way to find out afterwards why it said what it said, without having been there.

Logging is on by default and can be switched off under *Settings → Logging*, where the folder, the number
of sessions kept and their size are shown, and where they can all be deleted.

## Where the files are

```
/sdcard/Android/data/com.sailracing.app/files/sessions/session-20260908-141530Z.jsonl
```

(`com.sailracing.app.debug` for the debug build.) That folder belongs to the app but is reachable over USB
and from a file manager, so a log comes off the boat without root:

```bash
adb pull /sdcard/Android/data/com.sailracing.app/files/sessions ./sessions
```

One file per session, named after the moment it started, in UTC, so they sort by name. The 30 most recent
are kept, and no more than 256 MB in total; the oldest are dropped when the next session starts. An hour of
racing is about 2 MB.

## The format

[JSON lines](https://jsonlines.org): one JSON object per line, so a log can be read by eye, followed with
`tail -f`, filtered with `grep` and parsed by anything. A log of a session that never ended - a crash, a
flat battery - is still a valid log up to the moment it stopped, because every line is flushed as it is
written.

Every line has `t`, the session clock in milliseconds since the epoch (the wall clock, unless a simulation
is speeding it up), and `type`:

| `type` | When | What it holds |
| --- | --- | --- |
| `session` | first line of the file | the app version, the phone, and every setting the session started with |
| `event` | every event that reached the engine, except the clock tick | `event` names it (`FixReceived`, `MarkPinEnd`, `SetWindDirection`, …) and the rest are its values |
| `state` | about once a second | what the app was showing: the boat, the wind it believes in (`referenceWindDegrees`, and `measuredTackAngleDegrees` once both tacks have been sailed), the shift off the last fix (`shiftDegrees`) and the steadied one the advice is judged from (`steadyShiftDegrees`), the advice, the race line |
| `cue` | every beep | which cue was played |
| `end` | last line | the session was ended by the sailor |

A value the app does not have is left out rather than written as null, so a line says only what was true.
Event names are spelled out by the recorder, not read off the classes: a release build shortens those to
a letter. Nothing is written between the end of one session and the start of the next.

```json
{"t":1788876930000,"type":"session","app":"1.0.0","android":31,"device":"samsung SM-G970F","simulation":false,...}
{"t":1788876931000,"type":"event","event":"FixReceived","lat":51.1402,"lon":5.8311,"speedMps":3.1,"courseDegrees":318.4,"accuracyMeters":4.0}
{"t":1788876931000,"type":"state","phase":"COUNTDOWN","remainingMillis":184000,"speedMps":3.1,"referenceWindDegrees":21.4,"advice":"HOLD","favouredSide":"RIGHT",...}
{"t":1788877114000,"type":"cue","cue":"START"}
```

## Reading one

```bash
# the wind the app was working from, once a second
grep '"type":"state"' session-*.jsonl | jq -r '[.t, .referenceWindDegrees, .estimatedWindDegrees, .advice] | @tsv'

# every button the sailor pressed
grep '"type":"event"' session-*.jsonl | jq -r 'select(.event | test("Set|Mark|Clear|Start|Sync")) | [.t, .event] | @tsv'

# what the race line cost as the beat went on
jq -r 'select(.type=="state" and .raceLineSeconds) | [.t, .raceLineTacks, .raceLineSeconds, .raceLineBadSeconds] | @tsv' session-*.jsonl
```

## Replaying one

The `event` lines are everything the app was ever told, in order, and `RaceReducer` is a pure function of
them, so a session can be fed back through the engine and will come out the same. That is what makes the
log worth keeping: a bug seen on the water can be reproduced on a laptop from the file, and the fix can be
checked against the same race.
