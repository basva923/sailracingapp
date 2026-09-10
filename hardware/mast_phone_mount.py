# /// script
# requires-python = ">=3.10,<3.13"
# dependencies = ["cadquery>=2.4"]
# ///
"""Mast bracket that holds a phone (in a running strap) upright against the mast.

Back  : a concave cradle that sits on the aft face of the Yngling mast, with a
        relief channel down the middle so it clears the sail track.
Front : a flat face the phone rests on, with a lip at the bottom and at the top
        so the phone cannot slide out. The running strap goes around the whole
        bracket and the mast.

Measure the mast side to side at the height you want to mount, then set MAST_W.

    uv run hardware/mast_phone_mount.py            # STEP + STL + SVG previews
    uv run hardware/mast_phone_mount.py --open     # ... and open the preview
"""

from __future__ import annotations

import argparse
import subprocess
from pathlib import Path

import cadquery as cq

# --- Phone, sitting in its running strap -----------------------------------
PHONE_H = 150.0          # height of phone + strap
PHONE_W = 75.0           # width of phone + strap
PHONE_FIT = 1.5          # slack around the phone, per side

# --- Mast -------------------------------------------------------------------
MAST_W = 60.0            # mast width athwartships at the mount height
MAST_FIT = 1.0           # slack so the cradle beds down on paint, not on grit
CRADLE_DEPTH = 8.0       # how deep the mast sinks into the back of the plate
TRACK_W = 20.0           # relief for the sail track / bolt rope groove
TRACK_DEPTH = 3.0

# --- Plate ------------------------------------------------------------------
PLATE_DEPTH = 16.0       # back of the plate to the face the phone rests on
LIP_T = 6.0              # thickness of the bottom and top lip
LIP_OUT_BOTTOM = 14.0    # bottom lip: carries the phone, so it grips deeper
LIP_OUT_TOP = 9.0        # top lip: shallow, so the phone can tilt in and out

# --- Derived ----------------------------------------------------------------
POCKET_H = PHONE_H + 2 * PHONE_FIT          # clear height between the lips
PLATE_H = POCKET_H + 2 * LIP_T
PLATE_W = PHONE_W + 2 * PHONE_FIT
CORE_WALL = PLATE_DEPTH - CRADLE_DEPTH      # material left behind the cradle
MAST_R = MAST_W / 2 + MAST_FIT


def build() -> cq.Workplane:
    """X is fore and aft (+X forward, away from the mast), Y athwartships, Z up."""
    if CORE_WALL - TRACK_DEPTH < 2.0:
        raise ValueError("less than 2 mm of plate left behind the track relief")

    # Side view of the plate: an upright back plate with a lip top and bottom.
    part = (
        cq.Workplane("XZ")
        .polyline(
            [
                (-PLATE_DEPTH, -PLATE_H / 2),
                (LIP_OUT_BOTTOM, -PLATE_H / 2),
                (LIP_OUT_BOTTOM, -PLATE_H / 2 + LIP_T),
                (0, -PLATE_H / 2 + LIP_T),
                (0, PLATE_H / 2 - LIP_T),
                (LIP_OUT_TOP, PLATE_H / 2 - LIP_T),
                (LIP_OUT_TOP, PLATE_H / 2),
                (-PLATE_DEPTH, PLATE_H / 2),
            ]
        )
        .close()
        .extrude(PLATE_W / 2, both=True)
    )

    # The mast itself, hollowed out of the back.
    cradle = (
        cq.Workplane("XY")
        .center(-(CORE_WALL + MAST_R), 0)
        .circle(MAST_R)
        .extrude(PLATE_H, both=True)
    )
    part = part.cut(cradle)

    # Channel down the cradle so the sail track never carries the load.
    track_front = -(CORE_WALL - TRACK_DEPTH)
    track_back = -(PLATE_DEPTH + 10.0)
    relief = (
        cq.Workplane("XY")
        .moveTo((track_front + track_back) / 2, 0)
        .rect(track_front - track_back, TRACK_W)
        .extrude(PLATE_H, both=True)
    )
    return part.cut(relief)


VIEWS = {
    "iso": (1.0, -1.2, 0.6),
    "side": (0.0, -1.0, 0.0),
    "front": (1.0, 0.0, 0.0),
    "top": (0.0, 0.0, 1.0),
}


def write_previews(part: cq.Workplane, out: Path) -> list[Path]:
    written = []
    for name, direction in VIEWS.items():
        path = out / f"mast_phone_mount-{name}.svg"
        cq.exporters.export(
            part,
            str(path),
            opt={
                "width": 900,
                "height": 800,
                "marginLeft": 30,
                "marginTop": 30,
                "projectionDir": direction,
                "showAxes": False,
                "showHidden": True,
                "strokeWidth": 0.35,
                "strokeColor": (30, 30, 30),
                "hiddenColor": (190, 190, 190),
            },
        )
        written.append(path)
    return written


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--out", type=Path, default=Path(__file__).parent / "build",
        help="output directory (default: hardware/build)",
    )
    parser.add_argument(
        "--open", action="store_true", help="open the isometric preview when done",
    )
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)

    part = build()
    step = args.out / "mast_phone_mount.step"
    stl = args.out / "mast_phone_mount.stl"
    cq.exporters.export(part, str(step))
    cq.exporters.export(part, str(stl), tolerance=0.01, angularTolerance=0.1)
    previews = write_previews(part, args.out)

    box = part.val().BoundingBox()
    print(f"outside size  {box.xlen:.1f} x {box.ylen:.1f} x {box.zlen:.1f} mm (deep x wide x high)")
    print(f"phone pocket  {POCKET_H:.1f} mm high, lips {LIP_OUT_BOTTOM:.0f} mm "
          f"(bottom) and {LIP_OUT_TOP:.0f} mm (top) proud")
    print(f"cradle        {MAST_W:.1f} mm mast, {CRADLE_DEPTH:.1f} mm deep")
    print(f"volume        {part.val().Volume() / 1000:.1f} cm3")
    for path in [step, stl, *previews]:
        print(f"wrote {path}")

    if args.open:
        subprocess.run(["xdg-open", str(previews[0])], check=False)


if __name__ == "__main__":
    main()
