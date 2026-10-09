#!/usr/bin/env python3
"""
Diorite Isn't Useless — item texture generator (16x16).

Each texture is a grid of 16 rows x 16 characters.
Each character is a pixel; its color comes from the item's palette.
'.' = transparent.

Usage:
    python generate_textures.py                 -> generates the PNGs into ./out
    python generate_textures.py --out DIR       -> generates into DIR
    python generate_textures.py --preview DIR   -> also enlarged previews

To touch up a texture: change characters in the grid and run it again.
Style rules:
  - Light from the top left (H/L top-left, D/O bottom-right).
  - The outline is the DARKEST tone of the material itself, not pure black.
  - 4-5 tones per material, no smooth gradients.
"""

import argparse
import sys
from pathlib import Path

from PIL import Image

# --------------------------------------------------------------------------
# Palettes
# --------------------------------------------------------------------------

# Diorite crystal: milky white with a lilac soul (a relative of the ingot,
# but translucent and colder).
CRYSTAL = {
    "O": "#3E3354",  # outline (very dark lilac)
    "D": "#7A6C99",  # shaded face
    "d": "#9A8DB8",  # mid shadow
    "M": "#C2B9D9",  # mid tone
    "L": "#E6E1F0",  # lit face
    "H": "#FFFFFF",  # highlight
    "P": "#F2D7EE",  # pinkish glint on the edge
    "S": "#4A4458",  # diorite speck (a few)
}

# Dioritine ingot: "melted and compacted" diorite. Grayish white
# with the black specks that give it away.
INGOT = {
    "O": "#34313B",  # outline
    "D": "#7D7A85",  # side face (shadow)
    "d": "#9C99A3",  # soft shadow
    "M": "#BEBCC4",  # front face
    "L": "#E3E3E3",  # top face
    "H": "#FAFAFA",  # highlight
    "S": "#3A3A3A",  # dark speck
    "s": "#6E6A78",  # light speck (on shadow)
    "P": "#F5F0FF",  # lilac sparkle
}

# Dioritine axe: head made of the same material as the ingot,
# its own wooden handle and a dark binding.
AXE = {
    # head
    "O": "#34313B",
    "D": "#7D7A85",
    "d": "#9C99A3",
    "M": "#BEBCC4",
    "L": "#E3E3E3",
    "H": "#FAFAFA",
    "S": "#3A3A3A",
    "P": "#F5F0FF",
    # handle
    "w": "#2A1C10",  # wood outline
    "b": "#5A3D22",  # dark wood
    "m": "#7E5832",  # mid wood
    "l": "#A27846",  # light wood
    # binding (leather/cord)
    "r": "#3B2A3F",
    "R": "#5E4566",
}

# --------------------------------------------------------------------------
# Grids (16x16)
# --------------------------------------------------------------------------

CRYSTAL_GRID = [
    "................",
    ".............O..",
    "............OHO.",
    "...........OHLdO",
    "..........OHLPDO",
    ".........OLLPMDO",
    "........OLLPMdDO",
    ".......OLHPMMdO.",
    "......OLLPMMdDO.",
    ".....OLLPMSddO..",
    "....OLLPMMddDO..",
    "...OLLPMMdDDO...",
    "..OLPMMddDDO....",
    "..OMMMddDO......",
    "..OdddDOO.......",
    "...OOOO.........",
]

INGOT_GRID = [
    "................",
    "................",
    "................",
    "................",
    "....OOOOOOOO....",
    "...OHHLLLLSLO...",
    "..OHLLSSLLLLLO..",
    ".OHLLLLLLLLLLDO.",
    ".OPLLLLLLLSLLDDO",
    ".OMMMMMMMMMMMdDO",
    ".OMMSMMMMMMMddDO",
    ".OdMMMMMMsMMddDO",
    ".OddddddddddddO.",
    "..OOOOOOOOOOOO..",
    "................",
    "................",
]

AXE_GRID = [
    "................",
    ".......OO.......",
    "......OHMO......",
    ".....OHMLLO..ww.",
    "....OPMLSLMOwlbw",
    "...OHMLLLLMMMdO.",
    "...OPMLLMMSMddO.",
    "....OHddMMddDDO.",
    ".....OOOwlbwOOO.",
    ".......wlbw.....",
    "......wmbw......",
    ".....wlbw.......",
    "....wlbw........",
    "...wmbw.........",
    "..wlbw..........",
    ".wwww...........",
]

# Dioritine pickaxe: the same materials as the axe. The head is symmetric about the handle
# (the diagonal x + y = 15); the shading is not, because the light comes from the top left.
PICKAXE = AXE

PICKAXE_GRID = [
    "................",
    "...OOOOOO.......",
    "..OHHLSLMOO.....",
    ".OPddddMLLLOO...",
    ".OdOOOOOdMMLO...",
    ".OO.....OOMMLO..",
    "........wlOLdO..",
    ".......wlbOLMdO.",
    "......wlbw.OLdO.",
    ".....wlbw..OLdO.",
    "....wlbw...OSdO.",
    "...wlbw....OLdO.",
    "..wlbw.....OMdO.",
    ".wlbw.....OMDO..",
    "wlbw......OOO...",
    "www.............",
]

ITEMS = {
    "diorite_crystal": (CRYSTAL_GRID, CRYSTAL),
    "dioritine_ingot": (INGOT_GRID, INGOT),
    "dioritine_axe": (AXE_GRID, AXE),
    "dioritine_pickaxe": (PICKAXE_GRID, PICKAXE),
}


# --------------------------------------------------------------------------
# Render
# --------------------------------------------------------------------------

def hex_to_rgba(h: str) -> tuple:
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def render(grid: list, palette: dict, name: str) -> Image.Image:
    if len(grid) != 16:
        raise ValueError(f"{name}: the grid has {len(grid)} rows, it must have 16")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(grid):
        if len(row) != 16:
            raise ValueError(f"{name}: row {y} has {len(row)} characters, it must have 16")
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if ch not in palette:
                raise ValueError(f"{name}: character '{ch}' at ({x},{y}) is not in the palette")
            px[x, y] = hex_to_rgba(palette[ch])
    return img


def preview(img: Image.Image, scale: int = 24) -> Image.Image:
    """Enlarged without smoothing over a gray checkerboard, like in an editor."""
    big = img.resize((16 * scale, 16 * scale), Image.NEAREST)
    bg = Image.new("RGBA", big.size, (0, 0, 0, 255))
    bpx = bg.load()
    for y in range(big.size[1]):
        for x in range(big.size[0]):
            c = 0x9A if ((x // scale) + (y // scale)) % 2 == 0 else 0x8A
            bpx[x, y] = (c, c, c, 255)
    bg.alpha_composite(big)
    return bg


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", default="out", help="output folder for the 16x16 PNGs")
    ap.add_argument("--preview", default=None, help="folder for enlarged previews")
    args = ap.parse_args()

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    rendered = {}
    for name, (grid, pal) in ITEMS.items():
        img = render(grid, pal, name)
        img.save(out / f"{name}.png")
        rendered[name] = img
        print(f"OK  {out / (name + '.png')}")

    if args.preview:
        pv = Path(args.preview)
        pv.mkdir(parents=True, exist_ok=True)
        tiles = []
        for name, img in rendered.items():
            p = preview(img)
            p.save(pv / f"{name}_preview.png")
            tiles.append(p)
        # comparison sheet: every texture enlarged + real size x2 below
        gap = 16
        w = sum(t.size[0] for t in tiles) + gap * (len(tiles) + 1)
        h = tiles[0].size[1] + gap * 3 + 32
        sheet = Image.new("RGBA", (w, h), (40, 40, 46, 255))
        x = gap
        for name, t in zip(rendered, tiles):
            sheet.alpha_composite(t, (x, gap))
            small = rendered[name].resize((32, 32), Image.NEAREST)
            sheet.alpha_composite(small, (x + t.size[0] // 2 - 16, gap * 2 + t.size[1]))
            x += t.size[0] + gap
        sheet.save(pv / "sheet.png")
        print(f"OK  {pv / 'sheet.png'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
