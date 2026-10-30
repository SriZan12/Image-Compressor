#!/usr/bin/env python3
"""Generate reproducible PNG fixtures using only the Python standard library."""

import argparse
import hashlib
import json
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DESTINATION = ROOT / "app/src/androidTest/assets/compression"


def chunk(kind, payload):
    return (struct.pack(">I", len(payload)) + kind + payload
            + struct.pack(">I", zlib.crc32(kind + payload)))


def write_png(path, width, height, pattern):
    encoder = zlib.compressobj(level=9)
    with path.open("wb") as stream:
        stream.write(b"\x89PNG\r\n\x1a\n")
        stream.write(chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)))
        for y in range(height):
            row = bytearray([0])  # PNG filter: none; RGBA samples.
            for x in range(width):
                if pattern == "alpha":
                    rgba = (220, 40 + y * 150 // height, 60, x * 255 // (width - 1))
                elif pattern == "noise":
                    value = ((y * width + x + 1) * 2654435761) & 0xFFFFFFFF
                    value ^= value >> 13
                    value = (value * 2246822519) & 0xFFFFFFFF
                    rgba = (value & 255, value >> 8 & 255, value >> 16 & 255, 255)
                elif pattern == "chart":
                    # Unequal dimensions and asymmetric colored quadrants reveal flips/rotations.
                    colors = [(240, 20, 20, 255), (20, 210, 20, 255),
                              (20, 20, 240, 255), (230, 210, 20, 255)]
                    rgba = colors[(2 if y >= height // 2 else 0) + (1 if x >= width // 2 else 0)]
                    if x % 32 == 0 or y % 32 == 0:
                        rgba = (16, 16, 16, 255)
                else:
                    # Large, compressible generated texture; row-at-a-time generation avoids
                    # allocating a 48MP bitmap on the host simply to construct the test input.
                    rgba = (x * 255 // width, y * 255 // height, ((x // 16 + y // 16) % 2) * 180, 255)
                row.extend(rgba)
            encoded = encoder.compress(row)
            if encoded:
                stream.write(chunk(b"IDAT", encoded))
        stream.write(chunk(b"IDAT", encoder.flush()))
        stream.write(chunk(b"IEND", b""))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--large", action="store_true", help="Also generate 12MP and 48MP stress inputs")
    args = parser.parse_args()
    DESTINATION.mkdir(parents=True, exist_ok=True)
    fixtures = [("chart.png", 320, 192, "chart"), ("alpha.png", 320, 240, "alpha"),
                ("noise.png", 320, 240, "noise")]
    if args.large:
        fixtures += [("large-12mp.png", 4000, 3000, "texture"),
                     ("large-48mp.png", 8000, 6000, "texture")]
    for name, width, height, pattern in fixtures:
        write_png(DESTINATION / name, width, height, pattern)
    (DESTINATION / "corrupt.png").write_bytes(b"not an image\n")
    (DESTINATION / "truncated.png").write_bytes((DESTINATION / "chart.png").read_bytes()[:32])
    manifest = []
    for path in sorted(DESTINATION.glob("*.png")):
        data = path.read_bytes()
        manifest.append({"file": path.name, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()})
    (DESTINATION / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Recorded {len(manifest)} fixtures in {DESTINATION / 'manifest.json'}")


if __name__ == "__main__":
    main()
