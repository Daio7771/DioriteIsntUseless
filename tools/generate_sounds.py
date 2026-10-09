#!/usr/bin/env python3
"""
Diorite Isn't Useless — generator of the Morse beeps (OGG Vorbis, mono).

Generates dot.ogg and dash.ogg: a pure, soft sine wave, ramped in and out so there
are no clicks (golden rule 5: no loud, sudden sounds). They last exactly 1 and 3
Morse units (MorseBeeper uses a unit of 2 ticks = 100 ms).

Usage:
    python generate_sounds.py               -> generates into ./out
    python generate_sounds.py --out DIR     -> generates into DIR
    python generate_sounds.py --wav         -> also writes .wav copies to listen to

Destination in the mod: src/main/resources/assets/dioriteisntuseless/sounds/morse/

Needs libsndfile with Vorbis support (on Debian/Ubuntu: package libsndfile1).
It uses no external Python packages: it calls libsndfile through ctypes.
"""

import argparse
import ctypes
import ctypes.util
import math
import sys
from pathlib import Path

# --------------------------------------------------------------------------
# Sound
# --------------------------------------------------------------------------

SAMPLE_RATE = 44100
UNIT_SECONDS = 0.100   # 2 ticks: must match MorseBeeper.UNIT_TICKS
FREQUENCY = 620.0      # Hz: the classic Morse tone, without being shrill
AMPLITUDE = 0.30       # peak (1.0 = maximum); the game lowers it even more (volume 0.35)
RAMP_SECONDS = 0.012   # soft fade in and out, no click

SOUNDS = {
    "dot": 1,   # units
    "dash": 3,
}


def tone(units: int) -> list[float]:
    """Sine wave lasting `units` units, with cosine ramps at the start and at the end."""
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
# Writing with libsndfile
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
    parser.add_argument("--out", type=Path, default=Path("out"), help="output folder (./out by default)")
    parser.add_argument("--wav", action="store_true", help="also generate .wav files to listen to")
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
