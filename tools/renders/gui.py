"""
Pictures of the Machine Frame GUI, one panel per mode.

    python tools/renders/gui.py              # writes docs/media/gui.png

Like the scenes, these are reconstructions, not screenshots. Each panel is put
together from the mod's own GUI sheets (src/main/resources/.../textures/gui)
at the pixel positions MachineFrameScreen uses, with vanilla's font, flame and
item icons read from a local 1.20.1 client jar (set MC_CLIENT_JAR if it is not
found). Without a jar the text and icons are left out.
"""
import io
import os
import sys

from PIL import Image, ImageDraw

from blocks import MODELS
from isorender import BG, ROOT, SS, TEXT, Renderer, Textures, font

OUT = os.path.join(ROOT, "docs", "media")
GUI_TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "ideomod", "textures", "gui")

# Constants copied from MachineFrameScreen / MachineFrameMenu.
W, BORDER, CONTENT, INV = 176, 4, 89, 90
H = BORDER + CONTENT + INV + BORDER
BG_COLOR = (0xC6, 0xC6, 0xC6)
LABEL = (0x40, 0x40, 0x40)
BTN, GAP = 16, 2
TIMER_TICKS = list(range(5, 85, 5))    # config default: 5 to 80, step 5
ENGINE_TICKS = list(range(5, 45, 5))   # config default: 5 to 40, step 5
SCALE = 2
# Slot items that are blocks, drawn as small isometric blocks; everything else is a flat
# vanilla item texture. Named explicitly: some vanilla names (repeater) are also mod models.
BLOCK_ITEMS = {"furnace"}


class Vanilla:
    """The few vanilla assets the GUI uses: font, furnace flame, item icons."""

    def __init__(self, tx):
        self.tx = tx
        self.jar = tx.jar
        self.glyphs = {}
        if self.jar is not None:
            sheet = self._png("assets/minecraft/textures/font/ascii.png")
            for code in range(32, 127):
                g = sheet.crop(((code % 16) * 8, (code // 16) * 8, (code % 16) * 8 + 8, (code // 16) * 8 + 8))
                box = g.getchannel("A").getbbox()
                self.glyphs[chr(code)] = (g, (box[2] if box else 0) + 1)
            self.glyphs[" "] = (Image.new("RGBA", (8, 8)), 4)

    def _png(self, path):
        return Image.open(io.BytesIO(self.jar.read(path))).convert("RGBA")

    def width(self, text):
        return sum(self.glyphs.get(c, (None, 6))[1] for c in text)

    def text(self, img, xy, text, colour=LABEL):
        if not self.glyphs:
            return
        x, y = xy
        for c in text:
            g, adv = self.glyphs.get(c, self.glyphs["?"])
            tint = Image.new("RGBA", g.size, colour + (255,))
            tint.putalpha(g.getchannel("A"))
            img.alpha_composite(tint, (x, y))
            x += adv

    def furnace_gui(self):
        return self._png("assets/minecraft/textures/gui/container/furnace.png") if self.jar else None

    def item(self, name):
        """A 16x16 item icon: a flat item texture, or a small isometric block for block items."""
        if self.jar is None:
            return None
        if name in BLOCK_ITEMS:
            r = Renderer(self.tx, 16, 16, tile=14, origin=(0, 0))
            px, py = r.proj((0.5, 0.5, 0.5))
            r.ox, r.oy = 8 * SS - px, 8 * SS - py
            parts = MODELS[name]({"facing": "south"})
            dr = [((0, 0, 0), faces, see, 1.0, sum(c)) for faces, see, c in parts]
            cell = r.render(dr).convert("RGBA")
            # the renderer paints its background; key it out
            px_ = cell.load()
            for yy in range(16):
                for xx in range(16):
                    if px_[xx, yy][:3] == BG:
                        px_[xx, yy] = (0, 0, 0, 0)
            return cell
        return self._png("assets/minecraft/textures/item/%s.png" % name)


def panel(v, sheet, buttons, title, spec=None, transit=None, mode=None, sel=None, flame=0.0):
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    img.alpha_composite(sheet.crop((0, 110, W, 110 + BORDER)), (0, 0))
    for row in range(CONTENT):
        y = BORDER + row
        img.alpha_composite(sheet.crop((0, 18, 4, 19)), (0, y))
        ImageDraw.Draw(img).rectangle((4, y, W - 5, y), fill=BG_COLOR + (255,))
        img.alpha_composite(sheet.crop((4, 18, 8, 19)), (W - 4, y))
    slot = sheet.crop((0, 0, 18, 18))
    img.alpha_composite(slot, (79, 19))
    if mode in ("pipe", "engine"):
        img.alpha_composite(slot, (79, 37))

    def button(x, y, n, selected):
        if n <= 64:
            u, vv = (n - 1) % 16 * BTN, (n - 1) // 16 * BTN
        else:
            u, vv = (n - 65) % 16 * BTN, 128 + (n - 65) // 16 * BTN
        if selected:
            vv += 64
        img.alpha_composite(buttons.crop((u, vv, u + BTN, vv + BTN)), (x, y))

    def centred(text, y):
        v.text(img, ((W - v.width(text)) // 2, y), text)

    if mode == "repeater":
        for i in range(4):
            button(53 + i * (BTN + GAP), 37, i + 1, i + 1 == sel)
    elif mode == "transformer":
        for i in range(8):
            button(17 + i * (BTN + GAP), 40, i + 1, i + 1 == sel)
        for i in range(7):
            button(26 + i * (BTN + GAP), 58, i + 9, i + 9 == sel)
    elif mode == "timer":
        centred("Pulse Tick Duration", 40)
        for i, t in enumerate(TIMER_TICKS[:8]):
            button(17 + i * (BTN + GAP), 52, t, t == sel)
        row2 = TIMER_TICKS[8:16]
        x0 = (W - (len(row2) * BTN + (len(row2) - 1) * GAP)) // 2
        for i, t in enumerate(row2):
            button(x0 + i * (BTN + GAP), 70, t, t == sel)
    elif mode == "engine":
        fg = v.furnace_gui()
        if fg is not None:
            img.alpha_composite(fg.crop((56, 36, 70, 50)), (63, 40))
            h = int(flame * 14)
            if h > 0:
                img.alpha_composite(fg.crop((176, 14 - h, 190, 14)), (63, 40 + 14 - h))
        centred("Pulse Interval", 58)
        for i, t in enumerate(ENGINE_TICKS):
            button(17 + i * (BTN + GAP), 70, t, t == sel)

    img.alpha_composite(sheet.crop((0, 20, W, 20 + INV)), (0, BORDER + CONTENT))
    img.alpha_composite(sheet.crop((0, 114, W, 114 + BORDER)), (0, H - BORDER))
    for item, (x, y) in ((spec, (80, 20)), (transit, (80, 38))):
        icon = v.item(item) if item else None
        if icon is not None:
            img.alpha_composite(icon, (x, y))
    v.text(img, (8, 6), title)
    v.text(img, (8, BORDER + CONTENT + 1), "Inventory")
    return img


PANELS = [
    ("Empty slot: a plain Machine Frame", dict(title="Machine Frame")),
    ("Repeater: delay, 1 to 4 redstone ticks", dict(title="Machine Repeater", spec="repeater", mode="repeater", sel=2)),
    ("Comparator: output strength, 1 to 15", dict(title="Machine Transformer", spec="comparator", mode="transformer", sel=8)),
    ("Clock: pulse length, in ticks", dict(title="Machine Timer", spec="clock_00", mode="timer", sel=20)),
    ("Furnace: fuel slot, flame, pulse interval", dict(title="Machine Engine", spec="furnace", transit="coal", mode="engine", sel=20, flame=0.6)),
    ("Hopper: the item passing through", dict(title="Machine Pipe", spec="hopper", transit="iron_ingot", mode="pipe")),
]


def main(argv):
    tx = Textures()
    if tx.jar is None:
        print("note: no 1.20.1 client jar found; text and item icons are left out (set MC_CLIENT_JAR)")
    v = Vanilla(tx)
    sheet = Image.open(os.path.join(GUI_TEX, "ideomod_gui.png")).convert("RGBA")
    buttons = Image.open(os.path.join(GUI_TEX, "ideomod_numbered_buttons.png")).convert("RGBA")
    cols, pad, cap = 3, 24, 30
    cw, ch = W * SCALE, H * SCALE
    rows = (len(PANELS) + cols - 1) // cols
    out = Image.new("RGB", (cols * cw + (cols + 1) * pad, rows * (ch + cap) + (rows + 1) * pad - 6), BG)
    d = ImageDraw.Draw(out)
    f = font(15)
    for i, (caption, kw) in enumerate(PANELS):
        p = panel(v, sheet, buttons, **kw).resize((cw, ch), Image.NEAREST)
        x = pad + (i % cols) * (cw + pad)
        y = pad + (i // cols) * (ch + cap + pad)
        out.paste(p, (x, y), p)
        w = d.textlength(caption, font=f)
        d.text((x + (cw - w) / 2, y + ch + 8), caption, font=f, fill=TEXT)
    path = os.path.join(OUT, "gui.png")
    out.save(path, optimize=True)
    print("%s  %dx%d  %d KB" % (path, out.width, out.height, os.path.getsize(path) // 1024))


if __name__ == "__main__":
    main(sys.argv[1:])
