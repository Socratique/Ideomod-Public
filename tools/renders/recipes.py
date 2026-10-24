"""
Pictures of every Ideomod recipe, drawn in the 1.20.1 crafting table, furnace and
stonecutter windows.

    python tools/renders/recipes.py          # writes docs/media/recipes/*.png

Like gui.py these are reconstructions, not screenshots: the window, font and
vanilla item icons are read from a local 1.20.1 client jar (set MC_CLIENT_JAR if
it is not found; nothing from the jar is copied into the repo except as part of
the finished pictures), the mod's icons from src/main/resources, and block items
are drawn as small isometric blocks the way the inventory shows them. The
recipes themselves are read from data/ideomod/recipes, so the pictures follow
the JSON.
"""
import io
import json
import os
import sys

from PIL import Image

from blocks import FULL, MODELS, C
from gui import Vanilla
from isorender import ROOT, SS, Renderer, Textures, box

OUT = os.path.join(ROOT, "docs", "media", "recipes")
RECIPES = os.path.join(ROOT, "src", "main", "resources", "data", "ideomod", "recipes")
MOD_ITEM_TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "ideomod", "textures", "item")
SCALE = 3
WHITE, SHADOW = (255, 255, 255), (63, 63, 63)
COPPER_NUGGET_TINT = (0xFF, 0x8C, 0x5A)  # ModColorHandlers.COPPER_NUGGET_TINT


# ---------------------------------------------------------------- shapes --

def stairs(tex):
    # inventory stairs: step down towards the viewer
    return [(box(0, 0, 0, 16, 8, 16, tex), False, C), (box(0, 8, 0, 16, 16, 8, tex), False, (0.5, 0.75, 0.25))]


def slab(tex):
    return [(box(0, 0, 0, 16, 8, 16, tex), False, (0.5, 0.25, 0.5))]


def wall(tex):
    return [(box(4, 0, 4, 12, 16, 12, tex), False, C), (box(5, 0, 0, 11, 14, 16, tex), False, (0.5, 0.45, 0.5))]


BLOCKS = {
    # item id -> list of model parts
    "ideomod:machine_block": lambda: MODELS["machine_frame"]({}),
    "ideomod:hollow_stone": lambda: MODELS["hollowstone"]({}),
    "ideomod:iron_frame": lambda: MODELS["frame"]({"mat": "iron"}),
    "ideomod:copper_frame": lambda: MODELS["frame"]({"mat": "copper"}),
    "ideomod:oddstone": lambda: MODELS["oddstone"]({}),
    "ideomod:cobbled_oddstone": lambda: MODELS["cobbled_oddstone"]({}),
    "ideomod:oddstone_bricks": lambda: MODELS["oddstone_bricks"]({}),
    "ideomod:odd_slime": lambda: MODELS["odd_slime"]({"facing": "up"}),
    "ideomod:oddstone_repulser": lambda: MODELS["repulser"]({"facing": "up"}),
    "ideomod:oddstone_attractor": lambda: MODELS["attractor"]({"facing": "up"}),
    "ideomod:drill": lambda: MODELS["drill"]({"facing": "up"}),
    "ideomod:framed_block": lambda: MODELS["framed"]({"mat": "iron", "inner": "stone"}),
    "minecraft:piston": lambda: MODELS["piston"]({"facing": "up"}),
    "minecraft:sticky_piston": lambda: MODELS["piston"]({"facing": "up", "sticky": True}),
    "minecraft:slime_block": lambda: MODELS["slime"]({}),
    "minecraft:stone": lambda: MODELS["stone"]({}),
}
for fam, tex in (("oddstone", "oddstone"), ("cobbled_oddstone", "cobbled_oddstone"), ("oddstone_brick", "oddstone_bricks")):
    BLOCKS["ideomod:%s_stairs" % fam] = (lambda t: lambda: stairs(t))(tex)
    BLOCKS["ideomod:%s_slab" % fam] = (lambda t: lambda: slab(t))(tex)
    BLOCKS["ideomod:%s_wall" % fam] = (lambda t: lambda: wall(t))(tex)

FLAT = {
    # vanilla items whose texture has another name, or that are drawn flat in the inventory
    "minecraft:redstone_torch": "block/redstone_torch",
    "minecraft:clock": "item/clock_00",
    "ideomod:copper_nugget": "item/iron_nugget",
}

TITLES = {
    "ideomod:machine_block": "Machine Frame", "ideomod:hollow_stone": "Hollowstone",
    "ideomod:oddstone_repulser": "Repulser", "ideomod:oddstone_attractor": "Attractor",
    "ideomod:framed_block": "Framed Block",
}


class Picture:
    """A GUI picture in three layers, the way the game stacks them: the window
    and flat item icons (drawn at 1x, scaled up pixel for pixel), block icons
    (drawn straight at the final size, as the game draws 3D items at GUI scale
    SCALE), then stack counts on top."""

    def __init__(self, base):
        self.base = base
        self.blocks = []
        self.over = Image.new("RGBA", base.size, (0, 0, 0, 0))

    def final(self):
        img = self.base.resize((self.base.width * SCALE, self.base.height * SCALE), Image.NEAREST)
        for icon, (x, y) in self.blocks:
            img.alpha_composite(icon, (x * SCALE, y * SCALE))
        img.alpha_composite(self.over.resize(img.size, Image.NEAREST))
        return img


class Icons:
    def __init__(self, tx, v):
        self.tx, self.v, self.cache = tx, v, {}

    def block(self, parts):
        """An inventory-style block, SCALE x 16 pixels, on a transparent background."""
        size = 16 * SCALE
        r = Renderer(self.tx, size, size, tile=14 * SCALE, origin=(0, 0))
        px, py = r.proj((0.5, 0.5, 0.5))
        r.ox, r.oy = size / 2 * SS - px, size / 2 * SS - py
        canvas = Image.new("RGBA", (size * SS, size * SS), (0, 0, 0, 0))
        drawables = [((0, 0, 0), faces, see, 1.0, sum(c)) for faces, see, c in parts]
        for i in sorted(range(len(drawables)), key=lambda i: (round(drawables[i][4], 4), i)):
            pos, faces, see, dim, _ = drawables[i]
            if see:
                for f in faces:
                    if f["n"][0] + f["n"][1] + f["n"][2] < 0:
                        r.draw_face(canvas, f, pos, dim * 0.8)
            for f in faces:
                if f["n"][0] + f["n"][1] + f["n"][2] > 0:
                    r.draw_face(canvas, f, pos, dim)
        return canvas.resize((size, size), Image.BOX)

    def put(self, pic, item, x, y):
        img = self.get(item)
        if img.width > 16:
            pic.blocks.append((img, (x, y)))
        else:
            pic.base.alpha_composite(img, (x, y))

    def get(self, item):
        if item in self.cache:
            return self.cache[item]
        ns, name = item.split(":")
        if item in BLOCKS:
            img = self.block(BLOCKS[item]())
        elif ns == "ideomod" and item not in FLAT:
            img = Image.open(os.path.join(MOD_ITEM_TEX, name + ".png")).convert("RGBA")
        else:
            img = self.v._png("assets/minecraft/textures/%s.png" % FLAT.get(item, "item/" + name))
        if item == "ideomod:copper_nugget":
            r, g, b, a = img.split()
            img = Image.merge("RGBA", [ch.point(lambda val, k=k: val * k // 255) for ch, k in zip((r, g, b), COPPER_NUGGET_TINT)] + [a])
        self.cache[item] = img
        return img


def count_text(v, pic, x, y, n):
    """Stack count, bottom-right of a slot, white with vanilla's drop shadow, above the icon."""
    if n <= 1:
        return
    s = str(n)
    tx = x + 17 - v.width(s)
    v.text(pic.over, (tx + 1, y + 10), s, SHADOW)
    v.text(pic.over, (tx, y + 9), s, WHITE)


def window(v, path, h):
    """Top part of a container GUI (rows 0..h), closed off with the sheet's own bottom edge."""
    sheet = v._png(path)
    img = Image.new("RGBA", (176, h + 4), (0, 0, 0, 0))
    img.alpha_composite(sheet.crop((0, 0, 176, h)), (0, 0))
    img.alpha_composite(sheet.crop((0, 162, 176, 166)), (0, h))
    return Picture(img), sheet


def crafting(v, icons, slots, result, count, title="Crafting"):
    pic, _ = window(v, "assets/minecraft/textures/gui/container/crafting_table.png", 80)
    v.text(pic.base, (28, 6), title)
    for i, item in enumerate(slots):
        if item:
            icons.put(pic, item, 30 + (i % 3) * 18, 17 + (i // 3) * 18)
    icons.put(pic, result, 124, 35)
    count_text(v, pic, 124, 35, count)
    return pic


def furnace(v, icons, src, result):
    pic, sheet = window(v, "assets/minecraft/textures/gui/container/furnace.png", 80)
    v.text(pic.base, ((176 - v.width("Furnace")) // 2, 6), "Furnace")
    pic.base.alpha_composite(sheet.crop((176, 0, 190, 14)), (56, 36))          # lit flame
    pic.base.alpha_composite(sheet.crop((176, 14, 176 + 24, 31)), (79, 34))    # progress arrow, full
    icons.put(pic, src, 56, 17)
    icons.put(pic, "minecraft:coal", 56, 53)
    icons.put(pic, result, 116, 35)
    return pic


def stonecutter(v, icons, src, outputs):
    """Input on the left, every option in the list (first one selected) and its result."""
    pic, sheet = window(v, "assets/minecraft/textures/gui/container/stonecutter.png", 72)
    img = pic.base
    v.text(img, (8, 4), "Stonecutter")
    btn_n, btn_sel = sheet.crop((0, 166, 16, 184)), sheet.crop((0, 184, 16, 202))
    img.alpha_composite(sheet.crop((176, 0, 188, 15)), (119, 15))        # scroller
    for i, (item, n) in enumerate(outputs[:12]):
        x, y = 52 + (i % 4) * 16, 14 + (i // 4) * 18
        img.alpha_composite(btn_sel if i == 0 else btn_n, (x, y))
        icons.put(pic, item, x, y + 1)
    icons.put(pic, src, 20, 33)
    icons.put(pic, outputs[0][0], 143, 33)
    count_text(v, pic, 143, 33, outputs[0][1])
    return pic


def item_of(ing):
    if "tag" in ing:
        return {"forge:nuggets/copper": "ideomod:copper_nugget"}[ing["tag"]]
    return ing["item"]


def main(argv):
    tx = Textures()
    if tx.jar is None:
        sys.exit("needs a 1.20.1 client jar (set MC_CLIENT_JAR)")
    v = Vanilla(tx)
    icons = Icons(tx, v)
    os.makedirs(OUT, exist_ok=True)
    cutting = {}
    written = []

    def save(name, pic):
        path = os.path.join(OUT, name + ".png")
        pic.final().save(path, optimize=True)
        written.append(name)

    for f in sorted(os.listdir(RECIPES)):
        r = json.load(open(os.path.join(RECIPES, f), encoding="utf-8"))
        name, t = f[:-5], r["type"]
        if t == "minecraft:crafting_shaped":
            rows = [row.ljust(3) for row in r["pattern"]] + ["   "] * (3 - len(r["pattern"]))
            slots = [item_of(r["key"][ch]) if ch != " " else None for row in rows for ch in row[:3]]
            res = r["result"]
            save(name, crafting(v, icons, slots, res["item"], res.get("count", 1)))
        elif t == "minecraft:crafting_shapeless":
            slots = [item_of(i) for i in r["ingredients"]] + [None] * (9 - len(r["ingredients"]))
            res = r["result"]
            save(name, crafting(v, icons, slots, res["item"], res.get("count", 1)))
        elif t == "ideomod:framed_block":
            save(name, crafting(v, icons, ["ideomod:iron_frame", "minecraft:stone"] + [None] * 7, "ideomod:framed_block", 1))
        elif t == "minecraft:smelting":
            save(name, furnace(v, icons, item_of(r["ingredient"]), r["result"]))
        elif t == "minecraft:stonecutting":
            cutting.setdefault(item_of(r["ingredient"]), []).append((r["result"], r.get("count", 1)))
    order = {"stairs": 0, "slab": 1, "wall": 2}
    for src, outs in cutting.items():
        outs.sort(key=lambda o: (order.get(o[0].rsplit("_", 1)[-1], -1), o[0]))
        save("stonecutter_" + src.split(":")[1], stonecutter(v, icons, src, outs))
    print("%d pictures -> %s" % (len(written), OUT))


if __name__ == "__main__":
    main(sys.argv[1:])
