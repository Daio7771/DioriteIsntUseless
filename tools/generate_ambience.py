#!/usr/bin/env python3
"""
Diorite Isn't Useless — generador del fondo inquietante del Abuse Mode (OGG Vorbis, estéreo).

Genera un bucle de 32 s por nivel: level1.ogg a level4.ogg y final.ogg (el final: fase B y
después). El cliente los reproduce en bucle y pasa de uno a otro con fundidos (AbuseAmbience).
Son los previews aprobados por Daio: el 1 (fase B) y el 2 (progresión de niveles).

Capas, mezcladas en cada nivel con cantidades distintas (LEVELS):
- drone:  graves a 55 Hz con copias desafinadas que laten despacio, octava y "respiración".
- rumble: ruido grave filtrado, como un viento lejano bajo tierra, que sube y baja.
- whine:  una nota aguda casi inaudible con vibrato. Desde el nivel 3.
- clash:  tritono y segunda menor que chocan con el grave. Desde el nivel 4.

El bucle no tiene costura: todas las frecuencias y modulaciones dan un número entero de vueltas
en 32 s y el ruido se sintetiza en frecuencia, así que es periódico por construcción. Todo entra
y sale despacio (regla de oro: nada de sonidos fuertes de golpe); los fundidos al empezar y al
cambiar de nivel los hace el juego.

Uso:
    python generate_ambience.py               -> genera en ./out
    python generate_ambience.py --out DIR     -> genera en DIR
    python generate_ambience.py --wav         -> además, copias .wav para escucharlas

Destino en el mod: src/main/resources/assets/dioriteisntuseless/sounds/ambience/

Necesita numpy y soundfile (pip install numpy soundfile; soundfile trae libsndfile con Vorbis).
"""

import argparse
from pathlib import Path

import numpy as np
import soundfile as sf

SAMPLE_RATE = 44100
LOOP_SECONDS = 32.0                 # todas las frecuencias son múltiplo de 1 / 32 Hz
FRAMES = int(SAMPLE_RATE * LOOP_SECONDS)
PEAK_DB = -3.0                      # pico del bucle más fuerte (el final)
VORBIS_QUALITY = 0.8                # 0..1; con menos, la compresión deja un clic en la costura
WRITE_BLOCK = 4096                  # muestras por escritura al codificar
NOISE_SEED = 1234

t = np.arange(FRAMES) / SAMPLE_RATE

# Mezcla de cada nivel: (volumen, drone, rumble, whine, clash). El volumen es relativo: el final
# se normaliza a PEAK_DB y todos los demás llevan el mismo factor.
LEVELS = {
    "level1": (0.18, 1.0, 0.25, 0.0, 0.0),
    "level2": (0.30, 1.0, 0.55, 0.0, 0.0),
    "level3": (0.45, 1.0, 0.75, 0.10, 0.0),
    "level4": (0.62, 1.0, 0.90, 0.16, 0.20),
    "final": (0.85, 1.0, 1.00, 0.22, 0.75),
}


def sine(freq: float, phase: float = 0.0) -> np.ndarray:
    assert abs(freq * LOOP_SECONDS - round(freq * LOOP_SECONDS)) < 1e-9, f"{freq} Hz no cierra el bucle"
    return np.sin(2 * np.pi * freq * t + phase)


def lfo(period: float, phase: float = 0.0) -> np.ndarray:
    """De 0 a 1 y vuelta, cada `period` segundos (divisor de LOOP_SECONDS)."""
    assert abs(LOOP_SECONDS / period - round(LOOP_SECONDS / period)) < 1e-9, f"{period} s no cierra el bucle"
    return 0.5 - 0.5 * np.cos(2 * np.pi * t / period + phase)


def shaped_noise(rng: np.random.Generator, lowpass: float, highpass: float, resonance) -> np.ndarray:
    """Ruido rosa filtrado, sintetizado en frecuencia con fases al azar: periódico en el bucle."""
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
    """Seno de `freq` Hz con vibrato de ±`depth` Hz cada `period` s, sin romper el bucle."""
    return np.sin(2 * np.pi * freq * t - depth * period * np.cos(2 * np.pi * t / period))


def layers() -> dict[str, np.ndarray]:
    """Las cuatro capas, con los canales izquierdo y derecho algo distintos para dar anchura."""
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
    parser.add_argument("--out", type=Path, default=Path("out"), help="carpeta de salida (por defecto ./out)")
    parser.add_argument("--wav", action="store_true", help="genera también .wav para escucharlos")
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
            # Por bloques: el codificador Vorbis de libsndfile desborda la pila con bloques grandes.
            for start in range(0, FRAMES, WRITE_BLOCK):
                ogg.write(samples[start:start + WRITE_BLOCK])
        if args.wav:
            sf.write(args.out / f"{level}.wav", samples, SAMPLE_RATE, subtype="PCM_16")
        rms = 20 * np.log10(np.sqrt(np.mean(samples.astype(np.float64) ** 2)))
        print(f"{path}: {LOOP_SECONDS:.0f} s, {rms:.1f} dBFS RMS, {path.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
