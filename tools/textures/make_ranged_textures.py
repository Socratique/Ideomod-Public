"""
Re-skins the Oddstone Repulser and Attractor onto the current Oddstone texture
(2026-04-26), keeping the Ideomancer's frame, front patterns and arrows.

  - cyan (the original Oddstone colour)  ->  the Oddstone pixel at the same spot
                                              (on the fronts, a calmer, darker version
                                              of it, so the grey pattern stands out)
  - grey frame and front patterns         ->  unchanged
  - the dark arrows on the side faces     ->  light lavender, so they still read
                                              on the darker stone

Reads the cyan-era textures from git (commit 8f8832c) so it can be re-run.

    python tools/textures/make_ranged_textures.py [preview.png]
"""
import io
import os
import subprocess
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, "..", ".."))
TEX = os.path.join(REPO, "src", "main", "resources", "assets", "ideomod", "textures", "block")
SOURCE_COMMIT = "8f8832c"
NAMES = [d + "_" + f for d in ("oddstone_repulser", "oddstone_attractor") for f in ("front", "side", "back")]
ARROW = (176, 168, 212)
FRONT_CALM = 0.6        # fronts: how far each stone pixel is pulled toward the darkest stone tone


def original(name):
    data = subprocess.run(["git", "-C", REPO, "show", "%s:src/main/resources/assets/ideomod/textures/block/%s.png" % (SOURCE_COMMIT, name)],
                          capture_output=True, check=True).stdout
    return Image.open(io.BytesIO(data)).convert("RGBA")


def is_cyan(p):
    r, g, b, a = p
    return b > r + 60


def convert(name, stone):
    src = original(name)
    s, o = src.load(), stone.load()
    dark = min(stone.getdata(), key=lambda q: sum(q[:3]))
    out = src.copy()
    px = out.load()
    for y in range(16):
        for x in range(16):
            p = s[x, y]
            if is_cyan(p):
                q = o[x, y]
                if name.endswith("_front"):
                    q = tuple(int(q[k] + (dark[k] - q[k]) * FRONT_CALM) for k in range(3)) + (255,)
                px[x, y] = q
            elif name.endswith("_side") and sum(p[:3]) // 3 < 60 and 2 <= x <= 13 and 2 <= y <= 13:
                near = [s[x + dx, y + dy] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
                if sum(is_cyan(q) for q in near) >= 2:    # arrow lines, not frame edges
                    px[x, y] = ARROW + (255,)
    return out


def main():
    stone = Image.open(os.path.join(TEX, "oddstone.png")).convert("RGBA").resize((16, 16), Image.NEAREST)
    made = []
    for n in NAMES:
        im = convert(n, stone)
        im.save(os.path.join(TEX, n + ".png"))
        made.append((n, im))
    if len(sys.argv) > 1:   # preview: old above new, enlarged
        W = 144
        prev = Image.new("RGB", (6 * (W + 6), 2 * (W + 6)), (30, 30, 30))
        for i, (n, im) in enumerate(made):
            prev.paste(original(n).convert("RGB").resize((W, W), Image.NEAREST), (i * (W + 6), 0))
            prev.paste(im.convert("RGB").resize((W, W), Image.NEAREST), (i * (W + 6), W + 6))
        prev.save(sys.argv[1])
    print("wrote", ", ".join(n for n, _ in made))


if __name__ == "__main__":
    main()
