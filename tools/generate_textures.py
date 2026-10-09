#!/usr/bin/env python3
"""
Diorite Isn't Useless — generador de texturas de ítems (16x16).

Cada textura es una cuadrícula de 16 filas x 16 caracteres.
Cada carácter es un píxel; su color sale de la paleta del ítem.
'.' = transparente.

Uso:
    python generate_textures.py                 -> genera los PNG en ./out
    python generate_textures.py --out DIR       -> genera en DIR
    python generate_textures.py --preview DIR   -> además, previews ampliados

Para retocar una textura: cambia caracteres en la cuadrícula y vuelve a ejecutar.
Reglas de estilo:
  - Luz desde arriba a la izquierda (H/L arriba-izquierda, D/O abajo-derecha).
  - El contorno es el tono MÁS OSCURO del propio material, no negro puro.
  - 4-5 tonos por material, sin degradados suaves.
"""

import argparse
import sys
from pathlib import Path

from PIL import Image

# --------------------------------------------------------------------------
# Paletas
# --------------------------------------------------------------------------

# Cristal de diorita: blanco lechoso con alma lila (pariente del lingote,
# pero translúcido y más frío).
CRYSTAL = {
    "O": "#3E3354",  # contorno (lila muy oscuro)
    "D": "#7A6C99",  # cara en sombra
    "d": "#9A8DB8",  # sombra intermedia
    "M": "#C2B9D9",  # tono medio
    "L": "#E6E1F0",  # cara iluminada
    "H": "#FFFFFF",  # brillo
    "P": "#F2D7EE",  # reflejo rosado en la arista
    "S": "#4A4458",  # mota de diorita (pocas)
}

# Lingote de dioritina: diorita "fundida y compactada". Blanco grisáceo
# con las motas negras que la delatan.
INGOT = {
    "O": "#34313B",  # contorno
    "D": "#7D7A85",  # cara lateral (sombra)
    "d": "#9C99A3",  # sombra suave
    "M": "#BEBCC4",  # cara frontal
    "L": "#E3E3E3",  # cara superior
    "H": "#FAFAFA",  # brillo
    "S": "#3A3A3A",  # mota oscura
    "s": "#6E6A78",  # mota clara (sobre sombra)
    "P": "#F5F0FF",  # destello lila
}

# Hacha de dioritina: cabeza del mismo material que el lingote,
# mango de madera propio y una atadura oscura.
AXE = {
    # cabeza
    "O": "#34313B",
    "D": "#7D7A85",
    "d": "#9C99A3",
    "M": "#BEBCC4",
    "L": "#E3E3E3",
    "H": "#FAFAFA",
    "S": "#3A3A3A",
    "P": "#F5F0FF",
    # mango
    "w": "#2A1C10",  # contorno madera
    "b": "#5A3D22",  # madera oscura
    "m": "#7E5832",  # madera media
    "l": "#A27846",  # madera clara
    # atadura (cuero/cordel)
    "r": "#3B2A3F",
    "R": "#5E4566",
}

# --------------------------------------------------------------------------
# Cuadrículas (16x16)
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

# Pico de dioritina: los mismos materiales que el hacha. La cabeza es simétrica respecto al mango
# (la diagonal x + y = 15); las sombras no, porque la luz viene de arriba a la izquierda.
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
        raise ValueError(f"{name}: la cuadrícula tiene {len(grid)} filas, deben ser 16")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(grid):
        if len(row) != 16:
            raise ValueError(f"{name}: la fila {y} tiene {len(row)} caracteres, deben ser 16")
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if ch not in palette:
                raise ValueError(f"{name}: carácter '{ch}' en ({x},{y}) no está en la paleta")
            px[x, y] = hex_to_rgba(palette[ch])
    return img


def preview(img: Image.Image, scale: int = 24) -> Image.Image:
    """Ampliado sin suavizado sobre un tablero gris, como en un editor."""
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
    ap.add_argument("--out", default="out", help="carpeta de salida de los PNG 16x16")
    ap.add_argument("--preview", default=None, help="carpeta para previews ampliados")
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
        # hoja comparativa: las tres ampliadas + tamaño real x2 abajo
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
