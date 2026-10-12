#!/usr/bin/env python3
"""
Diorite Isn't Useless — skin de The "Useful" Owner (64x64, modelo de jugador, brazos de 4 px).

No es humano: un cuerpo entero de diorita, sin ropa, sin cara.
Solo dos huecos oscuros donde irían los ojos y grietas de algo muy antiguo.

Uso:
    python generate_owner_skin.py --out DIR [--preview DIR]
"""

import argparse
import random
from pathlib import Path

from PIL import Image

W = H = 64
rng = random.Random(4040)  # semilla fija

# Paleta de diorita (la misma familia que los ítems del mod)
BASE = [(227, 227, 227), (214, 214, 216), (238, 238, 238)]   # cuerpo
SHADE = [(190, 190, 194), (176, 176, 180)]                   # caras en sombra
SPECK = [(58, 58, 58), (82, 80, 86)]                          # motas negras
CRACK = (96, 94, 100)
CRACK_D = (60, 58, 64)
VOID = (16, 14, 18)        # cuencas de los ojos
VOID_RIM = (44, 42, 48)

img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()


def c(rgb):
    return rgb + (255,)


def stone(x, y, w, h, shaded=False, speck=0.07):
    """Superficie de diorita: base con ruido + motas negras."""
    pal = SHADE if shaded else BASE
    for j in range(h):
        for i in range(w):
            col = rng.choice(pal)
            if rng.random() < speck:
                col = rng.choice(SPECK)
            px[x + i, y + j] = c(col)


def crack(x, y, w, h, length):
    """Grieta que baja serpenteando dentro de la cara (x, y, w, h)."""
    cx, cy = x + rng.randrange(w), y + rng.randrange(max(1, h // 3))
    for _ in range(length):
        if not (x <= cx < x + w and y <= cy < y + h):
            break
        px[cx, cy] = c(CRACK_D if rng.random() < 0.4 else CRACK)
        cy += 1
        cx += rng.choice([-1, 0, 0, 1])


# Cada parte: (top, bottom, right, front, left, back) como (x, y, w, h)
def part(faces, cracks=1):
    names = ["top", "bottom", "right", "front", "left", "back"]
    for name, (x, y, w, h) in zip(names, faces):
        stone(x, y, w, h, shaded=name in ("bottom", "back", "left"))
        if name in ("front", "back", "right", "left") and h >= 8:
            for _ in range(cracks):
                if rng.random() < 0.8:
                    crack(x, y, w, h, rng.randrange(3, h))


HEAD = [(8, 0, 8, 8), (16, 0, 8, 8), (0, 8, 8, 8), (8, 8, 8, 8), (16, 8, 8, 8), (24, 8, 8, 8)]
BODY = [(20, 16, 8, 4), (28, 16, 8, 4), (16, 20, 4, 12), (20, 20, 8, 12), (28, 20, 4, 12), (32, 20, 8, 12)]
R_ARM = [(44, 16, 4, 4), (48, 16, 4, 4), (40, 20, 4, 12), (44, 20, 4, 12), (48, 20, 4, 12), (52, 20, 4, 12)]
L_ARM = [(36, 48, 4, 4), (40, 48, 4, 4), (32, 52, 4, 12), (36, 52, 4, 12), (40, 52, 4, 12), (44, 52, 4, 12)]
R_LEG = [(4, 16, 4, 4), (8, 16, 4, 4), (0, 20, 4, 12), (4, 20, 4, 12), (8, 20, 4, 12), (12, 20, 4, 12)]
L_LEG = [(20, 48, 4, 4), (24, 48, 4, 4), (16, 52, 4, 12), (20, 52, 4, 12), (24, 52, 4, 12), (28, 52, 4, 12)]

part(HEAD, cracks=1)
part(BODY, cracks=2)
for limb in (R_ARM, L_ARM, R_LEG, L_LEG):
    part(limb, cracks=1)

# ---------------------------------------------------------------- CARA: sin rasgos, solo dos huecos
fx, fy = 8, 8
# limpiar la cara: piedra lisa, casi sin motas (la ausencia de rasgos es lo que inquieta)
stone(fx, fy, 8, 8, speck=0.02)
for (ex, ey) in ((fx + 1, fy + 3), (fx + 5, fy + 3)):   # ojo izquierdo y derecho, 2x2
    for j in range(2):
        for i in range(2):
            px[ex + i, ey + j] = c(VOID)
    # borde de la cuenca, solo arriba y en el lado exterior: hueco hundido, no pintado
    for i in range(2):
        px[ex + i, ey - 1] = c(VOID_RIM)
# una sola grieta que sale de un ojo hacia abajo, como una lágrima seca
gx, gy = fx + 6, fy + 5
for k in range(3):
    if gy + k < fy + 8:
        px[gx, gy + k] = c(CRACK)


# ---------------------------------------------------------------- PREVIEW
def front_view(skin, scale=12):
    v = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    v.alpha_composite(skin.crop((8, 8, 16, 16)), (4, 0))
    v.alpha_composite(skin.crop((20, 20, 28, 32)), (4, 8))
    v.alpha_composite(skin.crop((44, 20, 48, 32)), (0, 8))
    v.alpha_composite(skin.crop((36, 52, 40, 64)), (12, 8))
    v.alpha_composite(skin.crop((4, 20, 8, 32)), (4, 20))
    v.alpha_composite(skin.crop((20, 52, 24, 64)), (8, 20))
    return v.resize((16 * scale, 32 * scale), Image.NEAREST)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="out")
    ap.add_argument("--preview", default=None)
    a = ap.parse_args()
    out = Path(a.out); out.mkdir(parents=True, exist_ok=True)
    img.save(out / "useful_owner.png")
    print("OK", out / "useful_owner.png")
    if a.preview:
        pv = Path(a.preview); pv.mkdir(parents=True, exist_ok=True)
        f = front_view(img)
        sheet = Image.new("RGBA", (f.width + 32 + 64 * 6, max(f.height, 64 * 6) + 32), (40, 40, 46, 255))
        sheet.alpha_composite(f, (16, 16))
        sheet.alpha_composite(img.resize((64 * 6, 64 * 6), Image.NEAREST), (32 + f.width, 16))
        sheet.save(pv / "useful_owner_preview.png")
        print("OK", pv / "useful_owner_preview.png")


if __name__ == "__main__":
    main()
