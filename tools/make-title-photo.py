#!/usr/bin/env python3
"""Builds the title screen's photo (res/drawable-nodpi/title_mountains.webp) from its original.

Source: "Sanqing Mountain is surrounded by clouds and mists" by Huangdan2060 (CC0), Wikimedia Commons:
https://commons.wikimedia.org/wiki/File:Sanqing_Mountain_is_surrounded_by_clouds_and_mists1.jpg
kept at full resolution in tools/sources/title-sanqing-mountain-commons.jpg (4032 x 3024).

The photo is cropped without distortion and darkened with the game's grade: a per-channel tone curve
(tools/sources/title-grade-curve.json) that sinks the forest almost to black and keeps the mist soft.
Film grain, the drifting fog and the shade behind the menu are drawn by the app at run time, so a new
crop gets those for free.

Usage:
  tools/make-title-photo.py                       the portrait title image, as shipped
  tools/make-title-photo.py W H [focus] [out]     another frame: W x H pixels, centred on the
                                                  horizontal fraction `focus` of the photo (0..1)
"""
import json
import sys
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parent.parent
src = root / "tools/sources/title-sanqing-mountain-commons.jpg"
curve = json.loads((root / "tools/sources/title-grade-curve.json").read_text())

img = Image.open(src).convert("RGB")
if len(sys.argv) >= 3:
    w, h = int(sys.argv[1]), int(sys.argv[2])
    focus = float(sys.argv[3]) if len(sys.argv) >= 4 else 0.38
    out = Path(sys.argv[4]) if len(sys.argv) >= 5 else root / f"build/title_{w}x{h}.webp"
    # The largest frame of that shape the photo holds, centred on the focus where it can be
    scale = min(img.width / w, img.height / h)
    cw, ch = w * scale, h * scale
    x0 = min(max(focus * img.width - cw / 2, 0), img.width - cw)
    y0 = (img.height - ch) / 2
    box = (x0, y0, x0 + cw, y0 + ch)
else:
    # The shipped portrait frame: the lone pine and the mist above it
    w, h = 1080, 2412
    box = (588, 2, 588 + 1353.13, 2 + 3022)
    out = root / "app/src/main/res/drawable-nodpi/title_mountains.webp"

frame = img.resize((w, h), Image.LANCZOS, box=box)
graded = Image.merge("RGB", [band.point(curve[c]) for band, c in zip(frame.split(), "rgb")])
out.parent.mkdir(parents=True, exist_ok=True)
graded.save(out, "WEBP", quality=90, method=6)
print(f"{out}: {w}x{h} from {tuple(round(v) for v in box)}")
