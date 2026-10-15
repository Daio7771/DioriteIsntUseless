#!/usr/bin/env python3
"""
Diorite Isn't Useless — map of an abandoned diorite mine, for the README.

It lays out one mine the same way the mod does (MineLayout.kt: the way from the entrance to the
crib room, the false forks, the spawners, the notes) with a fixed seed, and draws it from above,
block by block, with a legend. It is an example: every mine in the game is different.

Usage:
    python generate_mine_map.py                  -> mine_map.png in the current folder
    python generate_mine_map.py --out FILE       -> into FILE
    python generate_mine_map.py --seed N         -> another mine
"""

import argparse
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

# --------------------------------------------------------------------------
# Layout (same rules and numbers as MineLayout.kt)
# --------------------------------------------------------------------------

CELL = 5
GRID = 13
ROOM_START = GRID - 2
MIN_WAY, MAX_WAY, WAY_ATTEMPTS = 30, 60, 40
MIN_BRANCH, MAX_BRANCH, WAY_CELLS_PER_FORK = 2, 6, 4
CRUMB_CHANCE, BEAM_CHANCE, RAIL_CELLS = 0.7, 0.5, 3

ENTRANCE = (-1, 0)
SHAFT = (-2, 0)


def neighbours(c):
    i, j = c
    return [(i + 1, j), (i - 1, j), (i, j + 1), (i, j - 1)]


def in_grid(c):
    return 0 <= c[0] < GRID and 0 <= c[1] < GRID


def in_room(c):
    return c[0] >= ROOM_START and c[1] >= ROOM_START


def search(at, way, seen, rnd):
    way.append(at)
    seen.add(at)
    if any(in_grid(n) and in_room(n) for n in neighbours(at)):
        return way
    nexts = neighbours(at)
    rnd.shuffle(nexts)
    for n in nexts:
        if not in_grid(n) or in_room(n) or n in seen:
            continue
        found = search(n, way, seen, rnd)
        if found:
            return found
    way.pop()
    return None


def find_way(rnd):
    best = None
    for _ in range(WAY_ATTEMPTS):
        way = search((0, 0), [], set(), rnd)
        if MIN_WAY <= len(way) <= MAX_WAY:
            return way
        if best is None or len(way) < len(best):
            best = way
    return best


def layout(seed):
    rnd = random.Random(seed)
    way = find_way(rnd)
    used = set(way)
    links = set()

    def link(a, b):
        links.add((a, b))
        links.add((b, a))

    for a, b in zip(way, way[1:]):
        link(a, b)
    link(ENTRANCE, way[0])
    link(SHAFT, ENTRANCE)
    room_door = next(n for n in neighbours(way[-1]) if in_grid(n) and in_room(n))
    link(way[-1], room_door)

    def free(c):
        return in_grid(c) and not in_room(c) and c not in used

    forks = []
    for _ in range(len(way) // WAY_CELLS_PER_FORK + rnd.randrange(3)):
        starts = [c for c in way[1:-1] + forks if any(free(n) for n in neighbours(c))]
        if not starts:
            continue
        at = rnd.choice(starts)
        for _ in range(rnd.randint(MIN_BRANCH, MAX_BRANCH)):
            options = [n for n in neighbours(at) if free(n)]
            if not options:
                break
            nxt = rnd.choice(options)
            used.add(nxt)
            forks.append(nxt)
            link(at, nxt)
            at = nxt

    def openings(c):
        return [n for n in neighbours(c) if (c, n) in links]

    dead_ends = [c for c in forks if len(openings(c)) == 1]
    middle = way[2:max(2, len(way) - 2)]
    pool = rnd.sample(forks, len(forks)) + rnd.sample(middle, len(middle))
    spawners = list(dict.fromkeys(pool))[:2]

    # Notes: one at the entrance, one near the room, maybe one halfway and one in a dead end.
    notes = {ENTRANCE: "entrance"}
    near = [c for c in way[-3:] if len(openings(c)) < 4]
    if near:
        notes[rnd.choice(near)] = "center"
    extra = rnd.randrange(3)
    third = len(way) // 3
    if extra >= 1:
        mid = [c for c in way[third:len(way) - third] if c not in notes]
        if mid:
            notes[rnd.choice(mid)] = "middle"
    if extra >= 2:
        ends = [c for c in dead_ends if c not in notes]
        if ends:
            notes[rnd.choice(ends)] = "entrance"

    crumbs = {c for c in dead_ends if rnd.random() < CRUMB_CHANCE}
    cells = {}
    for c in [SHAFT, ENTRANCE] + sorted(used):
        ops = openings(c)
        straight = len(ops) == 2 and ops[0][0] + ops[1][0] == 2 * c[0] and ops[0][1] + ops[1][1] == 2 * c[1]
        cells[c] = {
            "openings": ops,
            "beam": c == ENTRANCE or (straight and rnd.random() < BEAM_CHANCE),
            "rails": c == ENTRANCE or (straight and c in way[:RAIL_CELLS]),
        }
    return cells, way, room_door, spawners, notes, crumbs


# --------------------------------------------------------------------------
# Drawing
# --------------------------------------------------------------------------

PX = 12  # pixels per block
MARGIN = 24
LEGEND_W = 300

COLORS = {
    "bg": "#1E1D22",
    "stone": "#55535C",
    "stone_edge": "#47454D",
    "floor": "#C9C4B8",
    "way": "#E8E1CF",
    "room": "#F2EEF7",
    "room_edge": "#B7AECB",
    "beam": "#6B4A2B",
    "rail": "#8C8C8C",
    "spawner": "#C0392B",
    "chest": "#D4A017",
    "crib": "#FFFFFF",
    "lantern": "#F5C542",
    "note": "#E9D46A",
    "crumb": "#9A97A0",
    "shaft": "#3B2A1A",
    "ladder": "#B8894B",
    "text": "#EDEBF2",
    "muted": "#A7A3B0",
}


def font(size):
    for name in ("DejaVuSans.ttf", "DejaVuSans-Bold.ttf", "LiberationSans-Regular.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def draw_map(seed):
    cells, way, room_door, spawners, notes, crumbs = layout(seed)
    blocks_w = (GRID + 2) * CELL  # two cells to the west: entrance and shaft
    blocks_h = GRID * CELL
    map_w, map_h = blocks_w * PX, blocks_h * PX
    img = Image.new("RGB", (map_w + MARGIN * 3 + LEGEND_W, map_h + MARGIN * 2 + 40), COLORS["bg"])
    d = ImageDraw.Draw(img)
    ox, oy = MARGIN, MARGIN + 40

    def block(bx, by, color, inset=0):
        x = ox + (bx + 2 * CELL) * PX
        y = oy + by * PX
        d.rectangle([x + inset, y + inset, x + PX - 1 - inset, y + PX - 1 - inset], fill=color)

    def cell_origin(c):
        return c[0] * CELL, c[1] * CELL

    # Stone everywhere, with a faint block grid.
    for by in range(blocks_h):
        for bx in range(-2 * CELL, GRID * CELL):
            block(bx, by, COLORS["stone_edge"])
            block(bx, by, COLORS["stone"], inset=1)

    way_set = set(way)
    for c, info in cells.items():
        x0, y0 = cell_origin(c)
        color = COLORS["way"] if c in way_set or c == ENTRANCE else COLORS["floor"]
        if c == SHAFT:
            color = COLORS["shaft"]
        for bx in range(1, CELL - 1):
            for by in range(1, CELL - 1):
                block(x0 + bx, y0 + by, color)
        for n in info["openings"]:
            dx, dy = n[0] - c[0], n[1] - c[1]
            for k in range(1, CELL - 1):
                if dx:
                    block(x0 + (CELL - 1 if dx > 0 else 0), y0 + k, color if c != SHAFT else COLORS["way"])
                else:
                    block(x0 + k, y0 + (CELL - 1 if dy > 0 else 0), color if c != SHAFT else COLORS["way"])
        horizontal = info["openings"] and info["openings"][0][1] == c[1]
        if info["rails"] and c != SHAFT:
            for k in range(CELL):
                if (k * 7 + x0 * 3 + y0) % 5 < 3:  # broken: gaps
                    bx, by = (x0 + k, y0 + 2) if horizontal else (x0 + 2, y0 + k)
                    x = ox + (bx + 2 * CELL) * PX
                    y = oy + by * PX
                    if horizontal:
                        d.line([x, y + 3, x + PX, y + 3], fill=COLORS["rail"], width=2)
                        d.line([x, y + PX - 4, x + PX, y + PX - 4], fill=COLORS["rail"], width=2)
                    else:
                        d.line([x + 3, y, x + 3, y + PX], fill=COLORS["rail"], width=2)
                        d.line([x + PX - 4, y, x + PX - 4, y + PX], fill=COLORS["rail"], width=2)
        if info["beam"] and c != SHAFT:
            for k in (1, 3):
                bx, by = (x0 + 2, y0 + k) if horizontal else (x0 + k, y0 + 2)
                block(bx, by, COLORS["beam"], inset=2)

    # The shaft: a ladder against the wall away from the corridor.
    sx, sy = cell_origin(SHAFT)
    for k in range(1, CELL - 1):
        block(sx + 1, sy + k, COLORS["shaft"])
    x = ox + (sx + 1 + 2 * CELL) * PX
    for r in range(3):
        y = oy + (sy + 1 + r) * PX
        d.line([x + 2, y + PX // 2, x + PX - 3, y + PX // 2], fill=COLORS["ladder"], width=2)
        d.line([x + 2, y, x + 2, y + PX], fill=COLORS["ladder"], width=1)
        d.line([x + PX - 3, y, x + PX - 3, y + PX], fill=COLORS["ladder"], width=1)

    # The crib room (2x2 cells at the far corner): door towards the last cell of the way.
    rx, ry = ROOM_START * CELL, ROOM_START * CELL
    for bx in range(1, 2 * CELL - 1):
        for by in range(1, 2 * CELL - 1):
            block(rx + bx, ry + by, COLORS["room"])
    dx, dy = way[-1][0] - room_door[0], way[-1][1] - room_door[1]
    door_x, door_y = cell_origin(room_door)
    for k in range(1, CELL - 1):
        if dx:
            block(rx + (0 if dx < 0 else 2 * CELL - 1), door_y + k, COLORS["room"])
        else:
            block(door_x + k, ry + (0 if dy < 0 else 2 * CELL - 1), COLORS["room"])
    # Back wall opposite the door: the big crib with the chest; the small cribs along a side wall.
    if dx:
        back_x = rx + (2 * CELL - 2 if dx < 0 else 1)
        chest = (back_x, ry + 4)
        cribs = [(rx + 3 + 2 * k, ry + 1) for k in range(3)]
        posts = [(back_x, ry + 3), (back_x, ry + 5)]
    else:
        back_y = ry + (2 * CELL - 2 if dy < 0 else 1)
        chest = (rx + 4, back_y)
        cribs = [(rx + 1, ry + 3 + 2 * k) for k in range(3)]
        posts = [(rx + 3, back_y), (rx + 5, back_y)]
    for p in posts:
        block(*p, COLORS["crib"], inset=3)
    block(*chest, COLORS["chest"], inset=1)
    for p in cribs:
        block(*p, COLORS["crib"], inset=2)
    lx = ox + (rx + 4 + 2 * CELL) * PX + PX // 2
    ly = oy + (ry + 4) * PX + PX // 2
    d.ellipse([lx - 9, ly - 9, lx + 9, ly + 9], outline=COLORS["lantern"], width=2)
    d.ellipse([lx - 4, ly - 4, lx + 4, ly + 4], fill=COLORS["lantern"])
    d.rectangle(
        [ox + (rx + 2 * CELL) * PX, oy + ry * PX, ox + (rx + 4 * CELL) * PX - 1, oy + (ry + 2 * CELL) * PX - 1],
        outline=COLORS["room_edge"], width=2,
    )

    # Spawners, notes and crumbs, at the middle of their cells.
    small = font(11)
    for c in spawners:
        x0, y0 = cell_origin(c)
        block(x0 + 2, y0 + 2, COLORS["spawner"], inset=1)
    for c in notes:
        x0, y0 = cell_origin(c)
        block(x0 + 1, y0 + 1, COLORS["note"], inset=2)
    for c in crumbs:
        x0, y0 = cell_origin(c)
        n = cells[c]["openings"][0]
        bx, by = x0 + 2 - (n[0] - c[0]), y0 + 2 - (n[1] - c[1])
        block(bx, by, COLORS["crumb"], inset=3)

    # Titles.
    title = font(22)
    d.text((MARGIN, MARGIN - 4), "Abandoned diorite mine (example, seen from above)", fill=COLORS["text"], font=title)
    d.text((ox + 2, oy + map_h + 6), f"~{GRID * CELL}x{GRID * CELL} blocks, one floor, between Y 0 and 40. "
                                      "Every mine is different.", fill=COLORS["muted"], font=small)

    # Legend.
    lx0 = ox + map_w + MARGIN
    ly0 = oy
    items = [
        ("way", "The way to the crib room"),
        ("floor", "False forks and dead ends"),
        ("room", "Crib room"),
        ("chest", "Chest (in the big crib)"),
        ("crib", "Small diorite cribs"),
        ("lantern", "Lantern (steady light)"),
        ("shaft", "Shaft up to the surface"),
        ("ladder", "Ladder"),
        ("beam", "Wooden beams"),
        ("rail", "Broken rails"),
        ("spawner", "Small spawner (zombie, skeleton)"),
        ("note", "Sign with a note"),
        ("crumb", "Crumbs (diorite, torch, pickaxe)"),
        ("stone", "Solid stone"),
    ]
    label = font(14)
    for k, (key, text) in enumerate(items):
        y = ly0 + k * 28
        d.rectangle([lx0, y, lx0 + 18, y + 18], fill=COLORS[key], outline=COLORS["stone_edge"])
        d.text((lx0 + 28, y + 1), text, fill=COLORS["text"], font=label)
    return img


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", default="mine_map.png", help="output PNG")
    ap.add_argument("--seed", type=int, default=7771, help="which mine to draw")
    args = ap.parse_args()
    img = draw_map(args.seed)
    out = Path(args.out)
    img.save(out)
    print(f"OK  {out}  ({img.size[0]}x{img.size[1]})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
