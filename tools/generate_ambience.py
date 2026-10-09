#!/usr/bin/env python3
"""
Diorite Isn't Useless — generator of the unsettling Abuse Mode background (OGG Vorbis, stereo).

Generates one 32 s loop per level: level1.ogg to level4.ogg and final.ogg (the ending: phase B
and after). The client plays them in a loop and crossfades from one to the next (AbuseAmbience).
They are the previews approved by Daio: number 1 (phase B) and number 2 (level progression).

Layers, mixed in different amounts at each level (LEVELS):
- drone:  bass at 55 Hz with detuned copies that beat slowly, an octave and some "breathing".
- rumble: filtered low noise, like a distant underground wind, rising and falling.
- whine:  an almost inaudible high note with vibrato. From level 3.
- clash:  a tritone and a minor second that clash with the bass. From level 4.

The loop has no seam: every frequency and modulation makes a whole number of cycles in 32 s and
the noise is synthesized in the frequency domain, so it is periodic by construction. Everything
fades in and out slowly (golden rule: no loud, sudden sounds); the fades when starting and when
changing level are done by the game.

Usage:
    python generate_ambience.py               -> generates into ./out
    python generate_ambience.py --out DIR     -> generates into DIR
    python generate_ambience.py --wav         -> also writes .wav copies to listen to

Destination in the mod: src/main/resources/assets/dioriteisntuseless/sounds/ambience/

Needs numpy and soundfile (pip install numpy soundfile; soundfile ships libsndfile with Vorbis).
"""

import argparse
from pathlib import Path

import numpy as np
import soundfile as sf

SAMPLE_RATE = 44100
LOOP_SECONDS = 32.0                 # every frequency is a multiple of 1 / 32 Hz
FRAMES = int(SAMPLE_RATE * LOOP_SECONDS)
PEAK_DB = -3.0                      # peak of the loudest loop (the ending)
VORBIS_QUALITY = 0.8                # 0..1; any lower and the compression leaves a click at the seam
WRITE_BLOCK = 4096                  # samples per write when encoding
NOISE_SEED = 1234

t = np.arange(FRAMES) / SAMPLE_RATE

# Mix of each level: (volume, drone, rumble, whine, clash). The volume is relative: the ending
# is normalized to PEAK_DB and every other loop gets the same factor.
LEVELS = {
    "level1": (0.18, 1.0, 0.25, 0.0, 0.0),
    "level2": (0.30, 1.0, 0.55, 0.0, 0.0),
    "level3": (0.45, 1.0, 0.75, 0.10, 0.0),
    "level4": (0.62, 1.0, 0.90, 0.16, 0.20),
    "final": (0.85, 1.0, 1.00, 0.22, 0.75),
}


def sine(freq: float, phase: float = 0.0) -> np.ndarray:
    assert abs(freq * LOOP_SECONDS - round(freq * LOOP_SECONDS)) < 1e-9, f"{freq} Hz does not close the loop"
    return np.sin(2 * np.pi * freq * t + phase)


def lfo(period: float, phase: float = 0.0) -> np.ndarray:
    """From 0 to 1 and back, every `period` seconds (a divisor of LOOP_SECONDS)."""
    assert abs(LOOP_SECONDS / period - round(LOOP_SECONDS / period)) < 1e-9, f"{period} s does not close the loop"
    return 0.5 - 0.5 * np.cos(2 * np.pi * t / period + phase)


def shaped_noise(rng: np.random.Generator, lowpass: float, highpass: float, resonance) -> np.ndarray:
    """Filtered pink noise, synthesized in the frequency domain with random phases: periodic in the loop."""
    freqs = np.fft.rfftfreq(FRAMES, 1 / SAMPLE_RATE)
    mag = np.zeros_like(freqs)
    band = freqs > 0
    mag[band] = 1 / np.sqrt(freqs[band])
    mag *= 1 / np.sqrt(1 + (freqs / lowpass) ** 8)
    mag *= 1 / np.sqrt(1 + (highpass / np.maximum(freqs, 1e-9)) ** 4)
    f0, q, gain = resonance
    mag *= 1 + gain * np.exp(-0.5 * ((freqs - f0) / (f0 / q)) ** 2)
    noise = np.fft.irfft(mag * np.exp(2j * np.pi * rng.random(len(freqs))), FRAMES)
    return noise / np.max(np.abs(noise))


def vibrato_tone(freq: float, depth: float, period: float) -> np.ndarray:
    """Sine wave at `freq` Hz with a vibrato of ±`depth` Hz every `period` s, without breaking the loop."""
    return np.sin(2 * np.pi * freq * t - depth * period * np.cos(2 * np.pi * t / period))


def layers() -> dict[str, np.ndarray]:
    """The four layers, with the left and right channels slightly different to give some width."""
    rng = np.random.default_rng(NOISE_SEED)
    out = {}
    for ch, beat in (("L", 55.25), ("R", 55.3125)):
        drone = sine(55.0) + 0.8 * sine(beat, 1.0) + 0.35 * sine(110.15625, 2.0) + 0.15 * sine(27.5)
        drone *= 0.65 + 0.35 * lfo(16.0, 0.0 if ch == "L" else 0.6)
        out[f"drone{ch}"] = np.tanh(1.4 * drone) / np.tanh(1.4 * 2.3)

        rumble = shaped_noise(rng, 260.0, 30.0, (95.0, 3.0, 2.0))
        out[f"rumble{ch}"] = rumble * (0.35 + 0.65 * lfo(32.0, 0.0 if ch == "L" else 1.3))

        whine = vibrato_tone(2960.0 if ch == "L" else 2963.0, 4.0, 8.0)
        out[f"whine{ch}"] = whine * lfo(32.0, 2.0 if ch == "L" else 2.4) ** 2

        clash = sine(77.78125) + 0.7 * sine(58.28125, 0.5) + 0.4 * sine(155.5625, 1.5)
        clash *= lfo(8.0, 0.0 if ch == "L" else 1.0) ** 1.5
        out[f"clash{ch}"] = np.tanh(1.2 * clash) / np.tanh(1.2 * 2.1)
    return out


def mix(parts: dict[str, np.ndarray], level: str) -> np.ndarray:
    volume, drone, rumble, whine, clash = LEVELS[level]
    channels = [
        volume * (drone * parts[f"drone{ch}"] + rumble * 0.6 * parts[f"rumble{ch}"]
                  + whine * 0.05 * parts[f"whine{ch}"] + clash * 0.5 * parts[f"clash{ch}"])
        for ch in "LR"
    ]
    return np.stack(channels, axis=1)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", type=Path, default=Path("out"), help="output folder (./out by default)")
    parser.add_argument("--wav", action="store_true", help="also generate .wav files to listen to")
    args = parser.parse_args()

    parts = layers()
    loops = {level: mix(parts, level) for level in LEVELS}
    scale = 10 ** (PEAK_DB / 20) / np.max(np.abs(loops["final"]))
    args.out.mkdir(parents=True, exist_ok=True)
    for level, loop in loops.items():
        samples = (loop * scale).astype(np.float32)
        path = args.out / f"{level}.ogg"
        with sf.SoundFile(path, "w", SAMPLE_RATE, 2, format="OGG", subtype="VORBIS",
                          compression_level=1 - VORBIS_QUALITY) as ogg:
            # In blocks: libsndfile's Vorbis encoder overflows the stack with large blocks.
            for start in range(0, FRAMES, WRITE_BLOCK):
                ogg.write(samples[start:start + WRITE_BLOCK])
        if args.wav:
            sf.write(args.out / f"{level}.wav", samples, SAMPLE_RATE, subtype="PCM_16")
        rms = 20 * np.log10(np.sqrt(np.mean(samples.astype(np.float64) ** 2)))
        print(f"{path}: {LOOP_SECONDS:.0f} s, {rms:.1f} dBFS RMS, {path.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
