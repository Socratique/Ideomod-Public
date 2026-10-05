"""Scene scripting on top of isorender: objects, tweened moves, captions, GIF frames."""
from PIL import Image, ImageDraw

from blocks import MODELS
from isorender import BG, SS, TEXT, Renderer, font, save_gif

BAR = 46  # caption bar height


class Obj:
    def __init__(self, pos, kind, props):
        self.pos = [float(c) for c in pos]
        self.kind = kind
        self.props = dict(props)
        self.dim = 1.0
        self.hidden = False

    def set(self, **props):
        self.props.update(props)
        return self


class Scene:
    def __init__(self, tx, title, size=(760, 440), tile=72, look=(0, 0, 0), floor=None):
        """look = the world point that sits at the centre of the picture."""
        self.r = Renderer(tx, size[0], size[1] - BAR, tile=tile, origin=(0, 0))
        px, py = self.r.proj(look)
        self.r.ox = self.r.w * SS / 2.0 - px
        self.r.oy = self.r.h * SS / 2.0 - py + 10 * SS
        self.size = size
        self.title = title
        self.floor = floor
        self.objs = []
        self.caption = ""
        self.links = []    # (obj_a, obj_b, colour)
        self.labels = []   # (obj_or_pos, text, colour)
        self.frames, self.durations = [], []
        self.f_title = font(17, bold=True)
        self.f_cap = font(15)
        self.f_lab = font(13, bold=True)

    # -- world ---------------------------------------------------------
    def add(self, x, y, z, kind, **props):
        o = Obj((x, y, z), kind, props)
        self.objs.append(o)
        return o

    def fill(self, xs, ys, zs, kind, **props):
        return [self.add(x, y, z, kind, **props) for x in xs for y in ys for z in zs]

    def remove(self, *objs):
        for o in objs:
            if o in self.objs:
                self.objs.remove(o)

    # -- frames --------------------------------------------------------
    def _compose(self):
        dr = []
        for o in self.objs:
            if o.hidden:
                continue
            for faces, see, c in MODELS[o.kind](o.props):
                dr.append((tuple(o.pos), faces, see, o.dim, o.pos[0] + o.pos[1] + o.pos[2] + c[0] + c[1] + c[2]))
        scene = self.r.render(dr, floor=self.floor)
        img = Image.new("RGB", self.size, BG)
        img.paste(scene, (0, 0))
        d = ImageDraw.Draw(img, "RGBA")
        for a, b, col in self.links:
            pa, pb = self._centre(a), self._centre(b)
            self._dashed(d, pa, pb, col)
            for p in (pa, pb):
                d.ellipse((p[0] - 4, p[1] - 4, p[0] + 4, p[1] + 4), fill=col + (255,), outline=(20, 20, 26, 255))
        for target, text, col in self.labels:
            p = self._centre(target, top=True)
            w = d.textlength(text, font=self.f_lab)
            d.rounded_rectangle((p[0] - w / 2 - 5, p[1] - 22, p[0] + w / 2 + 5, p[1] - 3), 5, fill=(16, 17, 22, 215))
            d.text((p[0] - w / 2, p[1] - 21), text, font=self.f_lab, fill=col + (255,))
        d.text((14, 10), self.title, font=self.f_title, fill=TEXT + (255,))
        d.rectangle((0, self.size[1] - BAR, self.size[0], self.size[1]), fill=(22, 23, 30, 255))
        d.text((14, self.size[1] - BAR + 12), self.caption, font=self.f_cap, fill=TEXT + (255,))
        return img

    def _centre(self, target, top=False):
        pos = target.pos if isinstance(target, Obj) else target
        return self.r.to_screen((pos[0] + 0.5, pos[1] + (1.15 if top else 0.5), pos[2] + 0.5))

    @staticmethod
    def _dashed(d, a, b, col, dash=7, gap=5):
        dx, dy = b[0] - a[0], b[1] - a[1]
        n = (dx * dx + dy * dy) ** 0.5
        if n < 1:
            return
        ux, uy = dx / n, dy / n
        t = 0.0
        while t < n:
            e = min(n, t + dash)
            d.line((a[0] + ux * t, a[1] + uy * t, a[0] + ux * e, a[1] + uy * e), fill=col + (255,), width=3)
            t += dash + gap

    def snap(self, ms=70):
        self.frames.append(self._compose())
        self.durations.append(ms)

    def say(self, text, ms=1500):
        """Set the caption and hold on the current state."""
        self.caption = text
        self.snap(ms)

    def move(self, objs, d, steps=6, ms=55, also=None):
        """Slide objs one block along d, like a piston move. also(t) is called with t in (0, 1]."""
        start = [list(o.pos) for o in objs]
        for i in range(1, steps + 1):
            t = i / float(steps)
            for o, s in zip(objs, start):
                o.pos = [s[k] + d[k] * t for k in range(3)]
            if also:
                also(t)
            self.snap(ms)
        for o, s in zip(objs, start):
            o.pos = [float(round(s[k] + d[k])) for k in range(3)]

    def tween(self, fn, steps=6, ms=55):
        for i in range(1, steps + 1):
            fn(i / float(steps))
            self.snap(ms)

    def save(self, path, colors=128):
        save_gif(self.frames, self.durations, path, colors=colors)
        return path
