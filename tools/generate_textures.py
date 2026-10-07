# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Genera las texturas provisionales (16x16) de los ítems del mod.

Cada textura se define como una cuadrícula de 16x16 caracteres; cada carácter
es un color de su paleta ("." = transparente). Para retocar una textura, edita
su cuadrícula y vuelve a ejecutar el script.

Reglas de estilo:
  - Luz desde arriba a la izquierda: caras de arriba/izquierda claras,
    abajo/derecha oscuras. Tonos por material: 1 (claro), 2 (medio), 3 (oscuro),
    más W (brillo). Un segundo material en la misma textura usa 4/5/6.
  - Contorno continuo de 1 px: O (arriba/izquierda) y X (abajo/derecha, más
    oscuro). El script falla si algún píxel interior toca el vacío o el borde.

Uso (desde la raíz del repo):
    uv run tools/generate_textures.py
    # o bien: pip install pillow && python tools/generate_textures.py

Opcional: --preview DIR guarda además copias ampliadas x16 sobre el gris de una
ranura de inventario (una por textura y una hoja con todas).
"""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image

SIZE = 16
PREVIEW_SCALE = 16
PREVIEW_BACKGROUND = "#8B8B8B"  # gris de las ranuras del inventario
OUTPUT_DIR = (
    Path(__file__).resolve().parent.parent
    / "src/main/resources/assets/dioriteisntuseless/textures/item"
)

TRANSPARENT = "."
OUTLINE = {"O", "X"}

COMMON: dict[str, str] = {
    "O": "#2E2B33",  # contorno (lado iluminado)
    "X": "#18161C",  # contorno abajo/derecha, más oscuro
    "W": "#F5F0FF",  # brillo (tinte lila)
}

# Cristal: grises con tinte lila para que se lea como cristal y no como piedra.
CRYSTAL: dict[str, str] = {
    "1": "#DDD7EA",  # cara iluminada
    "2": "#B6AECB",  # cara de rotura de la base
    "3": "#8C84A3",  # cara en sombra
}

# Lingote: grises neutros de la diorita.
INGOT: dict[str, str] = {
    "1": "#E3E3E3",  # cara superior (diorita base)
    "2": "#C4C4C4",  # cara frontal
    "3": "#A8A8A8",  # cara lateral (sombra)
    "m": "#3A3A3A",  # motas
}

# Hacha: cabeza con los tonos del lingote (1-3, motas) y mango de madera (4-6).
AXE: dict[str, str] = {
    **INGOT,
    "4": "#B88A57",  # madera clara
    "5": "#966A3F",  # madera media (extremo y sombra bajo la cabeza)
    "6": "#6E4A2B",  # madera oscura
}

TEXTURES: dict[str, tuple[dict[str, str], list[str]]] = {
    # Esquirla alargada en diagonal: cara clara arriba-izquierda y oscura abajo-derecha
    # separadas por una arista con dos píxeles de brillo; punta en aguja; base rota.
    # Al lado, una esquirla más pequeña.
    "diorite_crystal": (CRYSTAL, [
        ".............O..",
        "...........OO1X.",
        ".........OO11X..",
        "........O1113X..",
        ".......O11W3X...",
        "......O11W33X...",
        ".....O11133X....",
        "....O11133X...O.",
        "...O11133X..OO1X",
        "..O11133X..O11X.",
        ".O11133X..O113X.",
        "O22133X..O113X..",
        ".X223X..O213X...",
        "..X2X...O22X....",
        "...X.....XX.....",
        "................",
    ]),
    # Lingote en perspectiva: cara superior clara con brillo en la arista,
    # frontal media y lateral oscura; cuatro motas repartidas.
    "dioritine_ingot": (INGOT, [
        "................",
        "................",
        "................",
        "................",
        "......OOOOOOO...",
        ".....OW111111X..",
        "....OW111m113X..",
        "...O1111111333X.",
        "..O22222222333X.",
        "..O22m222223m3X.",
        ".O22222222233X..",
        ".O222222m223X...",
        "..XXXXXXXXXX....",
        "................",
        "................",
        "................",
    ]),
    # Hacha: mango en diagonal que atraviesa el ojo y asoma por arriba; hoja abierta
    # hacia el filo (izquierda) con cuernos arriba y abajo, brillo en el filo y tres motas.
    "dioritine_axe": (AXE, [
        "...OOO..........",
        "..O111XO.....OO.",
        ".O111122XOOOO46X",
        ".OW11m22233346X.",
        ".OW122223m336X..",
        ".O12m2233336X...",
        ".O222233X46X....",
        "..X223XO56X.....",
        "...XXXO56X......",
        ".....O46X.......",
        "....O46X........",
        "...O46X.........",
        "..O46X..........",
        ".O46X...........",
        "O56X............",
        ".XX.............",
    ]),
}


def hex_to_rgba(color: str) -> tuple[int, int, int, int]:
    value = color.lstrip("#")
    return int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), 255


def check_outline(name: str, rows: list[str]) -> None:
    """Todo píxel interior debe estar rodeado (en cruz) por interior o contorno."""
    for y, row in enumerate(rows):
        for x, char in enumerate(row):
            if char == TRANSPARENT or char in OUTLINE:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if not (0 <= nx < SIZE and 0 <= ny < SIZE) or rows[ny][nx] == TRANSPARENT:
                    raise ValueError(f"{name}: hueco en el contorno junto a ({x}, {y})")


def render(name: str, palette: dict[str, str], rows: list[str]) -> Image.Image:
    if len(rows) != SIZE or any(len(row) != SIZE for row in rows):
        raise ValueError(f"{name}: la cuadrícula debe ser de {SIZE}x{SIZE}")
    check_outline(name, rows)
    colors = {**COMMON, **palette}
    image = Image.new("RGBA", (SIZE, SIZE))
    for y, row in enumerate(rows):
        for x, char in enumerate(row):
            if char == TRANSPARENT:
                continue
            if char not in colors:
                raise ValueError(f"{name}: carácter desconocido {char!r} en ({x}, {y})")
            image.putpixel((x, y), hex_to_rgba(colors[char]))
    return image


def enlarge(image: Image.Image) -> Image.Image:
    # NEAREST mantiene los píxeles exactos al ampliar.
    big = image.resize((SIZE * PREVIEW_SCALE,) * 2, Image.NEAREST)
    background = Image.new("RGBA", big.size, hex_to_rgba(PREVIEW_BACKGROUND))
    return Image.alpha_composite(background, big)


def save_previews(directory: Path, images: dict[str, Image.Image]) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    gap = PREVIEW_SCALE
    tile = SIZE * PREVIEW_SCALE
    sheet = Image.new(
        "RGBA",
        (gap + len(images) * (tile + gap), tile + 2 * gap),
        hex_to_rgba(PREVIEW_BACKGROUND),
    )
    for i, (name, image) in enumerate(images.items()):
        big = enlarge(image)
        big.save(directory / f"{name}.png")
        sheet.paste(big, (gap + i * (tile + gap), gap))
    sheet.save(directory / "all.png")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--preview", type=Path, help="carpeta para las copias ampliadas")
    args = parser.parse_args()

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    images = {}
    for name, (palette, rows) in TEXTURES.items():
        images[name] = render(name, palette, rows)
        images[name].save(OUTPUT_DIR / f"{name}.png")
        print(f"OK {name}.png")

    if args.preview:
        save_previews(args.preview, images)
        print(f"Vista previa en {args.preview}")


if __name__ == "__main__":
    main()
