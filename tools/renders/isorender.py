"""
Tiny isometric block renderer for the Ideomod README illustrations.

Draws Minecraft-style block scenes from the mod's own textures (and, when a
1.20.1 client jar is available, vanilla textures read straight out of the jar;
nothing from the jar is copied into the repo). Scenes are scripted in
scenes.py; this file only knows how to draw boxes and assemble GIFs.

World axes follow Minecraft: +x east, +y up, +z south. The camera looks from
the south-east, so the up, south and east faces of a block are visible.
"""
import glob
import os
import zipfile

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
MOD_TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "ideomod", "textures")

SS = 2  # supersampling factor; frames are drawn at SS x and scaled down

BG = (30, 32, 40)
FLOOR_A = (58, 62, 74)
FLOOR_B = (52, 56, 67)
TEXT = (232, 234, 240)
MUTED = (150, 156, 172)

SHADE = {"up": 1.0, "down": 0.5, "south": 0.82, "north": 0.82, "east": 0.64, "west": 0.64}


# ----------------------------------------------------------------------------
# Textures
# ----------------------------------------------------------------------------

def find_client_jar():
    env = os.environ.get("MC_CLIENT_JAR")
    if env and os.path.isfile(env):
        return env
    home = os.path.expanduser("~")
    pats = [
        os.path.join(home, ".gradle", "caches", "forge_gradle", "minecraft_repo", "versions", "1.20.1", "client.jar"),
        os.path.join(home, "AppData", "Roaming", ".minecraft", "versions", "1.20.1", "1.20.1.jar"),
        os.path.join(home, ".minecraft", "versions", "1.20.1", "1.20.1.jar"),
    ]
    for p in pats:
        for hit in glob.glob(p):
            return hit
    return None


class Textures:
    """Loads 'name' from the mod, or 'mc:name' from a vanilla client jar."""

    def __init__(self, client_jar=None):
        self.cache = {}
        self.jar = None
        client_jar = client_jar or find_client_jar()
        if client_jar:
            self.jar = zipfile.ZipFile(client_jar)

    def get(self, name):
        if name in self.cache:
            return self.cache[name]
        if name.startswith("#"):  # flat colour, e.g. "#ff0000"
            h = name[1:]
            rgb = tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
            a = int(h[6:8], 16) if len(h) >= 8 else 255
            img = Image.new("RGBA", (16, 16), rgb + (a,))
        elif name.startswith("mc:"):
            img = self._vanilla(name[3:])
        else:
            sub = "item" if name.startswith("item/") else "block"
            base = name.split("/")[-1]
            img = Image.open(os.path.join(MOD_TEX, sub, base + ".png")).convert("RGBA")
        if img.height > img.width:  # animated strip: keep the first frame
            img = img.crop((0, 0, img.width, img.width))
        self.cache[name] = img
        return img

    def _vanilla(self, base):
        sub = "item" if base.startswith("item/") else "block"
        base = base.split("/")[-1]
        if self.jar is not None:
            try:
                data = self.jar.open("assets/minecraft/textures/%s/%s.png" % (sub, base))
                return Image.open(data).convert("RGBA")
            except KeyError:
                pass
        # No jar: a flat grey stand-in so the scripts still run.
        v = 90 + (sum(map(ord, base)) % 80)
        return Image.new("RGBA", (16, 16), (v, v, v, 255))


def power_color(p):
    """Vanilla redstone wire colour for power level 0-15."""
    f = p / 15.0
    r = f * 0.6 + (0.4 if p > 0 else 0.3)
    g = min(1.0, max(0.0, f * f * 0.7 - 0.5))
    b = min(1.0, max(0.0, f * f * 0.6 - 0.7))
    return (int(r * 255), int(g * 255), int(b * 255))


# ----------------------------------------------------------------------------
# Geometry: boxes -> faces
# ----------------------------------------------------------------------------

FACE_NAMES = ("up", "down", "north", "south", "east", "west")
NORMALS = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1),
           "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0)}


def box(x0, y0, z0, x1, y1, z1, tex, uv=None, tint=None, alpha=1.0, faces=FACE_NAMES, rot=None):
    """A cuboid in 0..16 block units. tex is one name or a dict per face
    ('side' and 'all' are accepted as fallbacks). uv defaults to the
    Minecraft rule (taken from the box's own coordinates)."""
    out = []
    for f in faces:
        if isinstance(tex, dict):
            t = tex.get(f)
            if t is None and f in ("north", "south", "east", "west"):
                t = tex.get("side")
            if t is None:
                t = tex.get("all")
            if t is None:
                continue
        else:
            t = tex
        if f == "up":
            p0, pu, pv = (x0, y1, z0), (x1, y1, z0), (x0, y1, z1)
            auto = (x0, z0, x1, z1)
        elif f == "down":
            p0, pu, pv = (x0, y0, z1), (x1, y0, z1), (x0, y0, z0)
            auto = (x0, 16 - z1, x1, 16 - z0)
        elif f == "south":
            p0, pu, pv = (x0, y1, z1), (x1, y1, z1), (x0, y0, z1)
            auto = (x0, 16 - y1, x1, 16 - y0)
        elif f == "north":
            p0, pu, pv = (x1, y1, z0), (x0, y1, z0), (x1, y0, z0)
            auto = (16 - x1, 16 - y1, 16 - x0, 16 - y0)
        elif f == "east":
            p0, pu, pv = (x1, y1, z1), (x1, y1, z0), (x1, y0, z1)
            auto = (16 - z1, 16 - y1, 16 - z0, 16 - y0)
        else:  # west
            p0, pu, pv = (x0, y1, z0), (x0, y1, z1), (x0, y0, z0)
            auto = (z0, 16 - y1, z1, 16 - y0)
        fuv = auto
        if isinstance(uv, dict):
            fuv = uv.get(f, uv.get("side", auto) if f in ("north", "south", "east", "west") else auto)
        elif uv is not None:
            fuv = uv
        frot = rot.get(f, 0) if isinstance(rot, dict) else (rot or 0)
        if frot:  # rotate the texture on the face in 90-degree steps, like the "rotation" model key
            p1 = tuple(pu[i] + pv[i] - p0[i] for i in range(3))
            for _ in range((frot // 90) % 4):
                p0, pu, pv, p1 = pu, p1, p0, pv
        tn = tint.get(f) if isinstance(tint, dict) else tint
        out.append({
            "p0": tuple(c / 16.0 for c in p0), "pu": tuple(c / 16.0 for c in pu),
            "pv": tuple(c / 16.0 for c in pv), "n": NORMALS[f], "tex": t, "uv": fuv,
            "tint": tn, "alpha": alpha,
        })
    return out


def _rot(v, facing):
    """Rotate a direction so that canonical 'up' ends up pointing along facing."""
    x, y, z = v
    if facing == "up":
        return (x, y, z)
    if facing == "down":
        return (x, -y, -z)
    if facing == "south":
        return (x, -z, y)
    if facing == "north":
        return (-x, z, -y)
    if facing == "east":
        return (y, -x, z)
    if facing == "west":
        return (-y, x, z)
    raise ValueError(facing)


def orient(faces, facing):
    """Models are authored with their front pointing up; turn them to face 'facing'."""
    if facing == "up":
        return faces
    out = []
    for f in faces:
        g = dict(f)
        for k in ("p0", "pu", "pv"):
            x, y, z = f[k]
            rx, ry, rz = _rot((x - 0.5, y - 0.5, z - 0.5), facing)
            g[k] = (rx + 0.5, ry + 0.5, rz + 0.5)
        g["n"] = _rot(f["n"], facing)
        out.append(g)
    return out


def shade_for(n):
    nx, ny, nz = n
    if ny > 0.5:
        return SHADE["up"]
    if ny < -0.5:
        return SHADE["down"]
    if abs(nz) > 0.5:
        return SHADE["south"]
    return SHADE["east"]


# ----------------------------------------------------------------------------
# Renderer
# ----------------------------------------------------------------------------

def _font(size, bold=False):
    names = (["segoeuib.ttf", "arialbd.ttf", "DejaVuSans-Bold.ttf"] if bold
             else ["segoeui.ttf", "arial.ttf", "DejaVuSans.ttf"])
    for n in names:
        for d in ("C:/Windows/Fonts", "/usr/share/fonts/truetype/dejavu", ""):
            try:
                return ImageFont.truetype(os.path.join(d, n), size)
            except OSError:
                continue
    return ImageFont.load_default()


class Renderer:
    def __init__(self, textures, width, height, tile=56, origin=(0.5, 0.5)):
        """tile = on-screen width of one block in final pixels."""
        self.tx = textures
        self.w, self.h = width, height
        self.hw = tile / 2.0 * SS           # half tile width
        self.hh = tile / 4.0 * SS           # half tile depth (2:1 dimetric)
        self.vh = tile * 0.58 * SS          # on-screen height of one block
        self.ox = width * origin[0] * SS
        self.oy = height * origin[1] * SS
        self.patches = {}

    def proj(self, p):
        x, y, z = p
        return (self.ox + (x - z) * self.hw, self.oy + (x + z) * self.hh - y * self.vh)

    def unproj_scale(self):
        return SS

    def _patch(self, face, U, V, shade):
        key = (face["tex"], face["uv"], round(U[0], 2), round(U[1], 2), round(V[0], 2), round(V[1], 2),
               shade, face["tint"], face["alpha"])
        hit = self.patches.get(key)
        if hit is not None:
            return hit
        tex = self.tx.get(face["tex"])
        k = tex.width / 16.0
        u0, v0, u1, v1 = face["uv"]
        fl_u, fl_v = u1 < u0, v1 < v0
        cu0, cu1 = sorted((u0, u1))
        cv0, cv1 = sorted((v0, v1))
        src = tex.crop((int(round(cu0 * k)), int(round(cv0 * k)),
                        max(int(round(cu1 * k)), int(round(cu0 * k)) + 1),
                        max(int(round(cv1 * k)), int(round(cv0 * k)) + 1)))
        if fl_u:
            src = src.transpose(Image.FLIP_LEFT_RIGHT)
        if fl_v:
            src = src.transpose(Image.FLIP_TOP_BOTTOM)
        tint = face["tint"] or (255, 255, 255)
        mr, mg, mb = (tint[0] / 255.0 * shade, tint[1] / 255.0 * shade, tint[2] / 255.0 * shade)
        r, g, b, a = src.split()
        r = r.point(lambda v: int(v * mr))
        g = g.point(lambda v: int(v * mg))
        b = b.point(lambda v: int(v * mb))
        if face["alpha"] < 1.0:
            al = face["alpha"]
            a = a.point(lambda v: int(v * al))
        src = Image.merge("RGBA", (r, g, b, a))
        # Replicate the edge texels outwards so neighbouring faces overlap by a
        # hair instead of leaving background-coloured seams.
        pad = 1
        big = Image.new("RGBA", (src.width + 2 * pad, src.height + 2 * pad))
        big.paste(src.resize(big.size, Image.NEAREST), (0, 0))
        big.paste(src, (pad, pad))
        sw, sh = src.width, src.height
        xs = (0, U[0], V[0], U[0] + V[0])
        ys = (0, U[1], V[1], U[1] + V[1])
        e = 0.75  # overdraw in supersampled pixels
        bx, by = int(min(xs) - e - 1), int(min(ys) - e - 1)
        pw, ph = int(max(xs) + e + 2) - bx, int(max(ys) + e + 2) - by
        det = U[0] * V[1] - U[1] * V[0]
        if abs(det) < 1e-6 or pw <= 0 or ph <= 0:
            self.patches[key] = None
            return None
        i00, i01 = V[1] / det, -V[0] / det
        i10, i11 = -U[1] / det, U[0] / det
        # shrink the mapping slightly about the face centre -> the face grows by ~e px
        lu = (U[0] ** 2 + U[1] ** 2) ** 0.5
        lv = (V[0] ** 2 + V[1] ** 2) ** 0.5
        gu, gv = lu / (lu + 2 * e), lv / (lv + 2 * e)
        a_ = sw * i00 * gu
        b_ = sw * i01 * gu
        d_ = sh * i10 * gv
        e_ = sh * i11 * gv
        c_ = a_ * bx + b_ * by + sw * (1 - gu) / 2.0 + pad
        f_ = d_ * bx + e_ * by + sh * (1 - gv) / 2.0 + pad
        patch = big.transform((pw, ph), Image.AFFINE, (a_, b_, c_, d_, e_, f_), resample=Image.NEAREST)
        # cut away everything that fell on the replicated border beyond the overdraw
        hit = (patch, bx, by)
        self.patches[key] = hit
        return hit

    def draw_face(self, canvas, face, pos, dim=1.0):
        px, py, pz = pos
        A = self.proj((face["p0"][0] + px, face["p0"][1] + py, face["p0"][2] + pz))
        B = self.proj((face["pu"][0] + px, face["pu"][1] + py, face["pu"][2] + pz))
        C = self.proj((face["pv"][0] + px, face["pv"][1] + py, face["pv"][2] + pz))
        U = (B[0] - A[0], B[1] - A[1])
        V = (C[0] - A[0], C[1] - A[1])
        hit = self._patch(face, U, V, round(shade_for(face["n"]) * dim, 3))
        if hit is None:
            return
        patch, bx, by = hit
        x, y = int(round(A[0])) + bx, int(round(A[1])) + by
        cw, ch = canvas.size
        sx0, sy0 = max(0, -x), max(0, -y)
        sx1, sy1 = min(patch.width, cw - x), min(patch.height, ch - y)
        if sx1 <= sx0 or sy1 <= sy0:
            return
        if (sx0, sy0, sx1, sy1) != (0, 0, patch.width, patch.height):
            patch = patch.crop((sx0, sy0, sx1, sy1))
        canvas.alpha_composite(patch, (x + sx0, y + sy0))

    def floor(self, canvas, xr, zr, y=0.0):
        d = ImageDraw.Draw(canvas)
        for x in range(xr[0], xr[1]):
            for z in range(zr[0], zr[1]):
                pts = [self.proj(p) for p in ((x, y, z), (x + 1, y, z), (x + 1, y, z + 1), (x, y, z + 1))]
                d.polygon(pts, fill=(FLOOR_A if (x + z) % 2 == 0 else FLOOR_B) + (255,))

    def render(self, drawables, floor=None):
        """drawables: list of (pos, faces, see_through, dim). Returns an RGB frame at final size."""
        canvas = Image.new("RGBA", (self.w * SS, self.h * SS), BG + (255,))
        if floor:
            self.floor(canvas, *floor)
        order = sorted(range(len(drawables)), key=lambda i: (round(drawables[i][4], 4), i))
        for i in order:
            pos, faces, see_through, dim, _ = drawables[i]
            if see_through:
                for f in faces:
                    if f["n"][0] + f["n"][1] + f["n"][2] < 0:
                        self.draw_face(canvas, f, pos, dim * 0.8)
            for f in faces:
                if f["n"][0] + f["n"][1] + f["n"][2] > 0:
                    self.draw_face(canvas, f, pos, dim)
        return canvas.resize((self.w, self.h), Image.LANCZOS).convert("RGB")

    def to_screen(self, p):
        x, y = self.proj(p)
        return (x / SS, y / SS)


def save_gif(frames, durations, path, colors=128):
    """Quantise every frame against one shared palette (no flicker) and write a looping GIF."""
    step = max(1, len(frames) // 12)
    sample = frames[::step]
    w, h = frames[0].size
    sheet = Image.new("RGB", (w, h * len(sample)))
    for i, f in enumerate(sample):
        sheet.paste(f, (0, i * h))
    pal = sheet.quantize(colors=colors, method=Image.MEDIANCUT, dither=Image.NONE)
    q = [f.quantize(palette=pal, dither=Image.NONE) for f in frames]
    q[0].save(path, save_all=True, append_images=q[1:], duration=durations, loop=0, optimize=False, disposal=1)


def font(size, bold=False):
    return _font(size, bold)
