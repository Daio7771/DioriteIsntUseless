#!/usr/bin/env python3
"""
Diorite Isn't Useless — skin de The Miner (64x64, formato de skin de jugador, brazos de 4 px)


Uso:
    python generate_miner_skin.py --out DIR [--preview DIR]

Genera:
    the_miner.png          skin del NPC

Cada cara de cada parte del cuerpo se pinta por separado (ver LAYOUT).
"""

import argparse
import random
from pathlib import Path

from PIL import Image

W = H = 64

# ---------------------------------------------------------------- paleta
P = {
    # piel envejecida
    "skin":   (176, 132, 102), "skinD": (146, 104, 80), "skinDD": (118, 82, 64),
    "skinL":  (196, 154, 124),
    "wrink":  (128, 92, 72),
    "beard":  (150, 146, 140), "beardD": (112, 108, 104),
    "eyeW":   (226, 222, 214), "eye": (54, 70, 64), "bag": (120, 86, 70),
    "brow":   (120, 116, 110),
    "mouth":  (92, 58, 48),
    # casco
    "helm":   (196, 150, 40), "helmD": (150, 108, 28), "helmL": (222, 184, 74),
    "helmS":  (110, 80, 24),
    "lamp":   (250, 236, 170), "lampR": (90, 90, 96), "lampD": (60, 60, 66),
    # ropa
    "shirt":  (92, 78, 64), "shirtD": (70, 58, 48), "shirtL": (112, 96, 80),
    "strap":  (60, 44, 32), "buckle": (150, 140, 120),
    "pants":  (64, 66, 74), "pantsD": (48, 50, 58), "pantsL": (80, 82, 90),
    "boot":   (54, 40, 30), "bootD": (38, 28, 22),
    "dust":   (214, 212, 208), "dustD": (176, 174, 170),
}

rng = random.Random(7771)  # semilla fija: siempre la misma skin

img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()


def put(x, y, c):
    px[x, y] = P[c] + (255,) if isinstance(c, str) else c


def fill(x, y, w, h, c):
    for j in range(h):
        for i in range(w):
            put(x + i, y + j, c)


def noise(x, y, w, h, base, alts, p=0.18):
    """Relleno con ruido sutil (textura de tela/piel), sin parpadeo: es estático."""
    for j in range(h):
        for i in range(w):
            put(x + i, y + j, rng.choice(alts) if rng.random() < p else base)


def dust(x, y, w, h, n):
    """Motas de polvo de diorita."""
    for _ in range(n):
        put(x + rng.randrange(w), y + rng.randrange(h), rng.choice(["dust", "dustD"]))


# ---------------------------------------------------------------- CABEZA (capa base)
# top, bottom, right, front, left, back
noise(8, 0, 8, 8, "skinD", ["skinDD"])          # top (tapado por el casco)
noise(16, 0, 8, 8, "beard", ["beardD"])         # bottom (barba)
noise(0, 8, 8, 8, "skin", ["skinD"])            # right
noise(16, 8, 8, 8, "skin", ["skinD"])           # left
noise(24, 8, 8, 8, "skinD", ["skinDD"])         # back (nuca)

# cara (front) 8x8 en (8,8)
fx, fy = 8, 8
face = [
    # fila 0-2 quedan bajo el ala del casco: frente arrugada
    "LLLLLLLL",
    "sWsssWss",
    "bbsssbbs",   # cejas canosas
    "ewEsseEw",   # ojos: blanco + iris, cansados
    "sgsNNsgs",   # ojeras + nariz ancha (estilo aldeano)
    "dBsNNsBd",
    "BBBmmBBB",   # bigote + boca
    "BBBBBBBB",   # barba
]
key = {
    "L": "skinL", "s": "skin", "W": "wrink", "b": "brow", "e": "eyeW", "E": "eye",
    "w": "eyeW", "g": "bag", "N": "skinDD", "d": "skinD", "B": "beard", "m": "mouth",
}
for j, row in enumerate(face):
    for i, ch in enumerate(row):
        put(fx + i, fy + j, key[ch])
# iris en el lado correcto de cada ojo
put(fx + 1, fy + 3, "eye"); put(fx + 6, fy + 3, "eye")
put(fx + 0, fy + 3, "eyeW"); put(fx + 7, fy + 3, "eyeW")
# barba en los laterales
for side_x in (0, 16):
    fill(side_x, 14, 8, 2, "beard")
    for i in range(8):
        if rng.random() < 0.35:
            put(side_x + i, 14, "skin")

# ---------------------------------------------------------------- CASCO (capa sombrero, +32 en x)
noise(40, 0, 8, 8, "helm", ["helmD", "helmL"], 0.25)   # top
for x in (32, 48, 56):                                  # right, left, back
    fill(x, 8, 8, 3, "helm")
    fill(x, 10, 8, 1, "helmD")                          # borde inferior
    for i in range(8):
        if rng.random() < 0.3:
            put(x + i, 8 + rng.randrange(2), "helmL")
    put(x + rng.randrange(8), 9, "helmS")               # rozadura
# frente del casco con linterna
fill(40, 8, 8, 2, "helm")
fill(40, 10, 8, 1, "helmS")                             # ala del casco
put(43, 8, "lampR"); put(44, 8, "lampR")
put(42, 9, "lampR"); put(43, 9, "lamp"); put(44, 9, "lamp"); put(45, 9, "lampR")
put(41, 8, "helmL"); put(46, 8, "helmD")

# casco también en la capa base (por si la capa exterior está oculta o no se dibuja)
noise(8, 0, 8, 8, "helm", ["helmD", "helmL"], 0.25)         # top
for x in (0, 16, 24):                                       # right, left, back
    fill(x, 8, 8, 2, "helm"); fill(x, 10, 8, 1, "helmD")
fill(8, 8, 8, 2, "helm"); fill(8, 10, 8, 1, "helmS")         # frente + ala
put(11, 8, "lampR"); put(12, 8, "lampR")
put(10, 9, "lampR"); put(11, 9, "lamp"); put(12, 9, "lamp"); put(13, 9, "lampR")

# ---------------------------------------------------------------- CUERPO
noise(20, 16, 8, 4, "shirtD", ["shirt"])     # top
noise(28, 16, 8, 4, "shirtD", ["shirt"])     # bottom
noise(16, 20, 4, 12, "shirt", ["shirtD", "shirtL"])   # right
noise(28, 20, 4, 12, "shirt", ["shirtD", "shirtL"])   # left
noise(32, 20, 8, 12, "shirtD", ["shirt"])             # back
# frente: camisa + tirantes + cinturón
noise(20, 20, 8, 12, "shirt", ["shirtD", "shirtL"])
for y in range(20, 30):
    put(21, y, "strap"); put(26, y, "strap")
put(21, 22, "buckle"); put(26, 22, "buckle")
fill(20, 29, 8, 1, "strap"); put(23, 29, "buckle"); put(24, 29, "buckle")
fill(20, 30, 8, 2, "pants")
# tirantes también por detrás
for y in range(20, 30):
    put(33, y, "strap"); put(38, y, "strap")
dust(20, 20, 8, 9, 5); dust(32, 20, 8, 9, 3)


# ---------------------------------------------------------------- BRAZOS
def arm(ox, oy):
    # top, bottom
    noise(ox + 4, oy, 4, 4, "shirtD", ["shirt"])
    fill(ox + 8, oy, 4, 4, "skinD")
    # 4 caras laterales: manga + mano (3 filas de abajo)
    for face_x in range(4):
        x = ox + face_x * 4
        noise(x, oy + 4, 4, 9, "shirt", ["shirtD", "shirtL"])
        fill(x, oy + 13, 4, 3, "skin")
        put(x + rng.randrange(4), oy + 13, "skinD")
        fill(x, oy + 12, 4, 1, "shirtD")   # puño de la manga
    dust(ox, oy + 4, 16, 8, 3)


arm(40, 16)   # brazo derecho
arm(32, 48)   # brazo izquierdo


# ---------------------------------------------------------------- PIERNAS
def leg(ox, oy):
    noise(ox + 4, oy, 4, 4, "pantsD", ["pants"])
    fill(ox + 8, oy, 4, 4, "bootD")
    for face_x in range(4):
        x = ox + face_x * 4
        noise(x, oy + 4, 4, 9, "pants", ["pantsD", "pantsL"])
        fill(x, oy + 13, 4, 3, "boot")
        fill(x, oy + 15, 4, 1, "bootD")
    dust(ox, oy + 4, 16, 9, 1)


leg(0, 16)    # pierna derecha
leg(16, 48)   # pierna izquierda


# ---------------------------------------------------------------- PREVIEW (vista frontal)
def front_view(skin: Image.Image, scale=12) -> Image.Image:
    v = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    v.alpha_composite(skin.crop((8, 8, 16, 16)), (4, 0))     # cara
    v.alpha_composite(skin.crop((40, 8, 48, 16)), (4, 0))    # casco encima
    v.alpha_composite(skin.crop((20, 20, 28, 32)), (4, 8))   # torso
    v.alpha_composite(skin.crop((44, 20, 48, 32)), (0, 8))   # brazo der.
    v.alpha_composite(skin.crop((36, 52, 40, 64)), (12, 8))  # brazo izq.
    v.alpha_composite(skin.crop((4, 20, 8, 32)), (4, 20))    # pierna der.
    v.alpha_composite(skin.crop((20, 52, 24, 64)), (8, 20))  # pierna izq.
    return v.resize((16 * scale, 32 * scale), Image.NEAREST)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="out")
    ap.add_argument("--preview", default=None)
    a = ap.parse_args()
    out = Path(a.out); out.mkdir(parents=True, exist_ok=True)
    img.save(out / "the_miner.png")
    print("OK", out / "the_miner.png")
    if a.preview:
        pv = Path(a.preview); pv.mkdir(parents=True, exist_ok=True)
        f1 = front_view(img)
        sheet = Image.new("RGBA", (f1.width + 32 + 64 * 6, max(f1.height, 64 * 6) + 32), (40, 40, 46, 255))
        sheet.alpha_composite(f1, (16, 16))
        sheet.alpha_composite(img.resize((64 * 6, 64 * 6), Image.NEAREST), (32 + f1.width, 16))
        sheet.save(pv / "the_miner_sheet.png")
        print("OK", pv / "the_miner_sheet.png")


if __name__ == "__main__":
    main()
