#!/usr/bin/env python3
"""
Diorite Isn't Useless — generador de los pitidos del Morse (OGG Vorbis, mono).

Genera dot.ogg (punto) y dash.ogg (raya): un seno puro y suave, con entrada y
salida en rampa para que no haya chasquidos (regla de oro 5: nada de sonidos
fuertes de golpe). Duran exactamente 1 y 3 unidades del Morse (MorseBeeper usa
una unidad de 2 ticks = 100 ms).

Uso:
    python generate_sounds.py               -> genera en ./out
    python generate_sounds.py --out DIR     -> genera en DIR
    python generate_sounds.py --wav         -> además, copias .wav para escucharlas

Destino en el mod: src/main/resources/assets/dioriteisntuseless/sounds/morse/

Necesita libsndfile con soporte de Vorbis (en Debian/Ubuntu: paquete libsndfile1).
No usa paquetes de Python externos: llama a libsndfile con ctypes.
"""

import argparse
import ctypes
import ctypes.util
import math
import sys
from pathlib import Path

# --------------------------------------------------------------------------
# Sonido
# --------------------------------------------------------------------------

SAMPLE_RATE = 44100
UNIT_SECONDS = 0.100   # 2 ticks: debe coincidir con MorseBeeper.UNIT_TICKS
FREQUENCY = 620.0      # Hz: el tono clásico del Morse, sin ser agudo
AMPLITUDE = 0.30       # pico (1.0 = máximo); el juego lo baja aún más (volumen 0.35)
RAMP_SECONDS = 0.012   # entrada y salida suaves, sin chasquido

SOUNDS = {
    "dot": 1,   # unidades
    "dash": 3,
}


def tone(units: int) -> list[float]:
    """Seno de `units` unidades con rampas de coseno al principio y al final."""
    total = round(SAMPLE_RATE * UNIT_SECONDS * units)
    ramp = round(SAMPLE_RATE * RAMP_SECONDS)
    samples = []
    for i in range(total):
        envelope = 1.0
        if i < ramp:
            envelope = 0.5 - 0.5 * math.cos(math.pi * i / ramp)
        elif i >= total - ramp:
            envelope = 0.5 - 0.5 * math.cos(math.pi * (total - 1 - i) / ramp)
        samples.append(AMPLITUDE * envelope * math.sin(2 * math.pi * FREQUENCY * i / SAMPLE_RATE))
    return samples


# --------------------------------------------------------------------------
# Escritura con libsndfile
# --------------------------------------------------------------------------

SFM_WRITE = 0x20
SF_FORMAT_WAV = 0x010000
SF_FORMAT_PCM_16 = 0x0002
SF_FORMAT_OGG = 0x200000
SF_FORMAT_VORBIS = 0x0060
SFC_SET_VBR_ENCODING_QUALITY = 0x1300
VORBIS_QUALITY = 0.6   # 0..1


class SfInfo(ctypes.Structure):
    _fields_ = [
        ("frames", ctypes.c_int64),
        ("samplerate", ctypes.c_int),
        ("channels", ctypes.c_int),
        ("format", ctypes.c_int),
        ("sections", ctypes.c_int),
        ("seekable", ctypes.c_int),
    ]


def load_libsndfile():
    name = ctypes.util.find_library("sndfile")
    if name is None:
        sys.exit("libsndfile not found. Install it (Debian/Ubuntu: sudo apt install libsndfile1).")
    lib = ctypes.CDLL(name)
    lib.sf_open.restype = ctypes.c_void_p
    lib.sf_open.argtypes = [ctypes.c_char_p, ctypes.c_int, ctypes.POINTER(SfInfo)]
    lib.sf_writef_float.restype = ctypes.c_int64
    lib.sf_writef_float.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_float), ctypes.c_int64]
    lib.sf_command.restype = ctypes.c_int
    lib.sf_command.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_void_p, ctypes.c_int]
    lib.sf_strerror.restype = ctypes.c_char_p
    lib.sf_strerror.argtypes = [ctypes.c_void_p]
    lib.sf_close.argtypes = [ctypes.c_void_p]
    return lib


def write(lib, path: Path, samples: list[float], file_format: int) -> None:
    info = SfInfo(0, SAMPLE_RATE, 1, file_format, 0, 0)
    handle = lib.sf_open(str(path).encode(), SFM_WRITE, ctypes.byref(info))
    if not handle:
        sys.exit(f"Could not create {path}: {lib.sf_strerror(None).decode()}")
    try:
        if file_format & SF_FORMAT_OGG:
            quality = ctypes.c_double(VORBIS_QUALITY)
            lib.sf_command(handle, SFC_SET_VBR_ENCODING_QUALITY, ctypes.byref(quality), ctypes.sizeof(quality))
        data = (ctypes.c_float * len(samples))(*samples)
        written = lib.sf_writef_float(handle, data, len(samples))
        if written != len(samples):
            sys.exit(f"Could not write {path}: {lib.sf_strerror(handle).decode()}")
    finally:
        lib.sf_close(handle)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", type=Path, default=Path("out"), help="carpeta de salida (por defecto ./out)")
    parser.add_argument("--wav", action="store_true", help="genera también .wav para escucharlos")
    args = parser.parse_args()

    lib = load_libsndfile()
    args.out.mkdir(parents=True, exist_ok=True)
    for name, units in SOUNDS.items():
        samples = tone(units)
        write(lib, args.out / f"{name}.ogg", samples, SF_FORMAT_OGG | SF_FORMAT_VORBIS)
        if args.wav:
            write(lib, args.out / f"{name}.wav", samples, SF_FORMAT_WAV | SF_FORMAT_PCM_16)
        print(f"{args.out / (name + '.ogg')}: {len(samples) / SAMPLE_RATE * 1000:.0f} ms")


if __name__ == "__main__":
    main()
