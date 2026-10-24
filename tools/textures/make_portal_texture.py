"""
Draws the Oddstone portal's animated texture (original art, no vanilla pixels):
32 frames of 16x16, a slow swirl from deep blue through sky blue to near-white,
with white sparks on the brightest crests. Writes oddstone_portal.png (16x512) and
its .mcmeta next to the other block textures.

    python tools/textures/make_portal_texture.py [preview.png]
"""
import json
import math
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "ideomod", "textures", "block")
FRAMES = 32
# light blue (Sean, 2026-10-24): deep blue -> sky blue -> near-white, white sparks
RAMP = [(22, 52, 96), (36, 82, 140), (58, 118, 184), (86, 156, 218), (124, 192, 238), (170, 222, 250), (214, 242, 255)]
CYAN = (240, 252, 255)   # spark colour
ALPHA = 200   # see-through, like a nether portal


def ramp(v):
    v = max(0.0, min(1.0, v)) * (len(RAMP) - 1)
    i = min(int(v), len(RAMP) - 2)
    f = v - i
    return tuple(int(RAMP[i][k] + (RAMP[i + 1][k] - RAMP[i][k]) * f) for k in range(3))


def hash01(x, y, s):
    n = (x * 374761393 + y * 668265263 + s * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return (n & 0xFFFF) / 65535.0


def frame(f):
    t = 2 * math.pi * f / FRAMES
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            # tiles seamlessly: everything is built from sin/cos of x and y over one 16-pixel period
            a = 2 * math.pi * x / 16
            b = 2 * math.pi * y / 16
            swirl = (math.sin(a + 2 * math.sin(b + t) + t)
                     + math.sin(2 * b - a + math.cos(a - t))
                     + 0.6 * math.sin(3 * a + b - 2 * t))
            v = 0.5 + swirl / 5.2 + 0.08 * (hash01(x, y, f // 4) - 0.5)
            c = ramp(v)
            # spark flicker on the brightest crests
            if v > 0.86 and hash01(x, y, f) > 0.55:
                c = tuple((c[k] + CYAN[k]) // 2 for k in range(3))
            px[x, y] = c + (ALPHA,)
    return im


def main():
    strip = Image.new("RGBA", (16, 16 * FRAMES))
    for f in range(FRAMES):
        strip.paste(frame(f), (0, 16 * f))
    strip.save(os.path.join(OUT, "oddstone_portal.png"))
    with open(os.path.join(OUT, "oddstone_portal.png.mcmeta"), "w", encoding="utf-8") as fh:
        json.dump({"animation": {"frametime": 2, "interpolate": True}}, fh, indent=2)
        fh.write("\n")
    if len(sys.argv) > 1:   # preview: 8 frames, enlarged and tiled 2x2 to show the seams
        prev = Image.new("RGB", (8 * 140, 140), (20, 20, 20))
        for i in range(8):
            fr = frame(i * 4)
            tile = Image.new("RGBA", (32, 32))
            for dx in (0, 16):
                for dy in (0, 16):
                    tile.paste(fr, (dx, dy))
            bg = Image.new("RGBA", (32, 32), (20, 20, 20, 255))
            bg.alpha_composite(tile)
            prev.paste(bg.convert("RGB").resize((128, 128), Image.NEAREST), (i * 140 + 6, 6))
        prev.save(sys.argv[1])
    print("wrote oddstone_portal.png (%d frames) + .mcmeta" % FRAMES)


if __name__ == "__main__":
    main()
