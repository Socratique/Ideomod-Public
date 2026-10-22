"""
Block models for the README illustrations.

Each entry of MODELS maps a kind name to a function(props) -> list of parts.
A part is (faces, see_through, centre) where centre is the part's centre in
block units (used for depth sorting). Models mirror the JSON models under
src/main/resources/assets/ideomod/models/block, simplified where the JSON
relies on vanilla parents.
"""
from isorender import box, orient, power_color

FULL = (0, 0, 0, 16, 16, 16)
INNER = (0.05, 0.05, 0.05, 15.95, 15.95, 15.95)
C = (0.5, 0.5, 0.5)
PORTAL_TINT = None  # the mod registers no colour handler for the portal block


def cube(tex, tint=None, alpha=1.0):
    return [(box(*FULL, tex, tint=tint, alpha=alpha), False, C)]


def see_through(tex):
    return [(box(*FULL, tex), True, C)]


def layered(outer, inner, tint=None):
    """Inner cuboid (optionally tinted) seen through the transparent pixels of the outer shell."""
    return [(box(*INNER, inner, tint=tint, uv=(0, 0, 16, 16)), False, C), (box(*FULL, outer), False, C)]


def directional(front, back, side, facing):
    # Authored front-up; side textures keep their top edge toward the front, as in the mod's models.
    faces = box(*FULL, {"up": front, "down": back, "side": side})
    return [(orient(faces, facing), False, C)]


def track_grey(power):
    """Machine Track indicator tint (ModColorHandlers.TRACK_GREYS): 0x30 at power 0 to 0xE0 at 15."""
    v = 0x30 + power * (0xE0 - 0x30) // 15
    return (v, v, v)


def indicator(outer, power):
    return layered(outer, "machine_indicator", tint=power_color(power))


# ---------------------------------------------------------------- vanilla --

def slime(p):
    return [(box(3, 3, 3, 13, 13, 13, "mc:slime_block"), False, C), (box(*FULL, "mc:slime_block"), False, C)]


def honey(p):
    tex = {"up": "mc:honey_block_top", "down": "mc:honey_block_bottom", "side": "mc:honey_block_side"}
    return [(box(1, 1, 1, 15, 15, 15, "mc:honey_block_bottom"), False, C), (box(*FULL, tex), False, C)]


def piston(p):
    """Vanilla piston. props: facing, ext (0..1), sticky."""
    facing = p.get("facing", "up")
    ext = p.get("ext", 0.0)
    top = "mc:piston_top_sticky" if p.get("sticky") else "mc:piston_top"
    side = "mc:piston_side"
    parts = []
    base = box(0, 0, 0, 16, 12, 16, {"up": "mc:piston_inner", "down": "mc:piston_bottom", "side": side},
               uv={"side": (0, 4, 16, 16)})
    parts.append((orient(base, facing), False, _oc((0.5, 0.375, 0.5), facing)))
    e = ext * 16
    if ext > 0:
        arm = box(6, 12, 6, 10, 12 + e, 10, {"side": side}, uv={"side": (0, 0, 16, 4)}, rot={"north": 90, "south": 90, "east": 90, "west": 90})
        parts.append((orient(arm, facing), False, _oc((0.5, (12 + e / 2) / 16.0, 0.5), facing)))
    head = box(0, 12 + e, 0, 16, 16 + e, 16, {"up": top, "down": "mc:piston_top", "side": side},
               uv={"side": (0, 0, 16, 4), "up": (0, 0, 16, 16), "down": (0, 0, 16, 16)})
    parts.append((orient(head, facing), False, _oc((0.5, (14 + e) / 16.0, 0.5), facing)))
    return parts


def _oc(c, facing):
    from isorender import _rot
    x, y, z = _rot((c[0] - 0.5, c[1] - 0.5, c[2] - 0.5), facing)
    return (x + 0.5, y + 0.5, z + 0.5)


def lever(p):
    """Floor lever, drawn as a base plate and a short handle that leans east (on) or west (off)."""
    on = p.get("on", False)
    parts = [(box(5, 0, 4, 11, 3, 12, "mc:cobblestone"), False, (0.5, 0.1, 0.5))]
    lean = 1 if on else -1
    for i in range(4):
        x = 7 + lean * i * 1.1
        parts.append((box(x, 3 + i * 2, 7, x + 2, 5 + i * 2, 9, "mc:lever" if False else "#6b5335"), False,
                      ((x + 1) / 16.0, (4 + i * 2) / 16.0, 0.5)))
    tip = "#ff3b30" if on else "#7a2019"
    x = 7 + lean * 4 * 1.1
    parts.append((box(x, 11, 7, x + 2, 13, 9, tip), False, ((x + 1) / 16.0, 12 / 16.0, 0.5)))
    return parts


def dust(p):
    """Redstone dust lying on the block below: a flat tinted cross. props: power, dirs."""
    col = "#%02x%02x%02x" % power_color(p.get("power", 0))
    dirs = p.get("dirs", "nsew")
    parts = [(box(6, 0, 6, 10, 0.4, 10, col, faces=("up",)), False, (0.5, 0.0, 0.5))]
    spans = {"n": (7, 0, 0, 9, 0.4, 6), "s": (7, 0, 10, 9, 0.4, 16), "w": (0, 0, 7, 6, 0.4, 9), "e": (10, 0, 7, 16, 0.4, 9)}
    for d in dirs:
        parts.append((box(*spans[d], col, faces=("up",)), False, (0.5, 0.0, 0.5)))
    return parts


def lamp(p):
    return cube("mc:redstone_lamp_on" if p.get("on") else "mc:redstone_lamp")


def furnace(p):
    front = "mc:furnace_front_on" if p.get("lit") else "mc:furnace_front"
    f = p.get("facing", "south")
    tex = {"up": "mc:furnace_top", "down": "mc:furnace_top", "side": "mc:furnace_side", f: front}
    return [(box(*FULL, tex), False, C)]


def chest(p):
    tex = {"up": "#8f6a2a", "down": "#6e4f1d", "side": "#a87b32"}
    return [(box(1, 0, 1, 15, 14, 15, tex), False, (0.5, 0.44, 0.5)),
            (box(7, 7, 15, 9, 11, 16, "#c8c8c8"), False, (0.5, 0.56, 0.97))]


def item_marker(p):
    """A small floating cube standing in for an item stack."""
    return [(box(5, 5, 5, 11, 11, 11, p.get("tex", "mc:iron_block")), False, C)]


def pearl(p):
    return [(box(6, 6, 6, 10, 10, 10, "#1f9a84"), False, C), (box(7, 7, 7, 9, 9, 9, "#7ff0d8"), False, C)]


def fluid(p):
    kind = p.get("fluid", "lava")
    if kind == "lava":
        return [(box(0, 0, 0, 16, 14, 16, "mc:lava_still"), False, (0.5, 0.44, 0.5))]
    return [(box(0, 0, 0, 16, 14, 16, "mc:water_still", tint=(63, 118, 228), alpha=0.8), False, (0.5, 0.44, 0.5))]


# -------------------------------------------------------------------- mod --

def odd_slime(p):
    return directional("odd_slime_front", "odd_slime_back", "odd_slime_side", p.get("facing", "up"))


def repulser(p):
    return directional("oddstone_repulser_front", "oddstone_repulser_back", "oddstone_repulser_side", p.get("facing", "up"))


def attractor(p):
    return directional("oddstone_attractor_front", "oddstone_attractor_back", "oddstone_attractor_side", p.get("facing", "up"))


def drill(p):
    front = "drill_front_on" if p.get("on") else "drill_front"
    return directional(front, "drill_back", "drill_side", p.get("facing", "up"))


def frame(p):
    mat = p.get("mat", "iron")
    return see_through("%s_frame%s" % (mat, "_active" if p.get("active") else ""))


def framed(p):
    mat = p.get("mat", "iron")
    inner = MODELS[p["inner"]](p.get("inner_props", {}))
    shell = (box(*FULL, "%s_frame%s" % (mat, "_active" if p.get("active") else "")), False, C)
    return inner + [shell]


def hollowstone(p):
    inner = p.get("inner")
    if not inner:
        return see_through("hollow_stone")
    return MODELS[inner]({}) + [(box(*FULL, "hollow_stone_filled"), False, C)]


def machine_piston(p):
    """Machine Piston base plus any extended arms. props: sticky, arms={facing: 0..1}."""
    tex = "machine_sticky_piston" if p.get("sticky") else "machine_piston"
    parts = [(box(*FULL, tex), False, C)]
    for facing, ext in (p.get("arms") or {}).items():
        if ext <= 0:
            continue
        e = ext * 16
        arm = box(4, 16, 4, 12, 16 + e, 12, {"up": tex, "side": "machine_piston_arm"},
                  uv={"up": (4, 4, 12, 12), "side": (4, 0, 12, 16)})
        parts.append((orient(arm, facing), False, _oc((0.5, (16 + e / 2) / 16.0, 0.5), facing)))
    return parts


def fluid_pipe(p):
    kind = p.get("fluid")
    if kind == "water":
        return layered("machine_fluid_pipe", "mc:water_still", tint=(63, 118, 228))
    if kind == "lava":
        return layered("machine_fluid_pipe", "mc:lava_still")
    return layered("machine_fluid_pipe", "machine_fluid_pipe_inner_empty")


def grabber(p):
    inner = "mc:slime_block" if p.get("variant", "slime") == "slime" else "mc:honey_block_side"
    outer = "machine_grabber_on" if p.get("on") else "machine_grabber"
    return [(box(2, 2, 2, 14, 14, 14, inner), False, C), (box(*FULL, outer), False, C)]


def toggle(p):
    return indicator("machine_toggle_on" if p.get("on") else "machine_toggle", 15 if p.get("on") else 0)


def portal(p):
    """Oddstone portal fill: a thin translucent pane. props: axis ('x' = pane spans x)."""
    tex, tint = "mc:nether_portal", PORTAL_TINT
    if p.get("axis", "x") == "x":
        return [(box(0, 0, 6, 16, 16, 10, tex, tint=tint, uv=(0, 0, 16, 16), faces=("north", "south")), True, C)]
    return [(box(6, 0, 0, 10, 16, 16, tex, tint=tint, uv=(0, 0, 16, 16), faces=("east", "west")), True, C)]


MODELS = {
    # vanilla
    "stone": lambda p: cube("mc:stone"),
    "cobblestone": lambda p: cube("mc:cobblestone"),
    "smooth_stone": lambda p: cube("mc:smooth_stone"),
    "stone_bricks": lambda p: cube("mc:stone_bricks"),
    "ghost_bricks": lambda p: cube("mc:stone_bricks", alpha=0.38),
    "planks": lambda p: cube("mc:oak_planks"),
    "obsidian": lambda p: cube("mc:obsidian"),
    "glass": lambda p: see_through("mc:glass"),
    "iron_block": lambda p: cube("mc:iron_block"),
    "gold_block": lambda p: cube("mc:gold_block"),
    "diamond_block": lambda p: cube("mc:diamond_block"),
    "emerald_block": lambda p: cube("mc:emerald_block"),
    "redstone_block": lambda p: cube("mc:redstone_block"),
    "slime": slime, "honey": honey, "piston": piston, "lever": lever, "dust": dust, "lamp": lamp,
    "furnace": furnace, "chest": chest, "item": item_marker, "pearl": pearl, "fluid": fluid,
    # mod
    "oddstone": lambda p: cube("oddstone"),
    "cobbled_oddstone": lambda p: cube("cobbled_oddstone"),
    "oddstone_bricks": lambda p: cube("oddstone_bricks"),
    "odd_slime": odd_slime, "repulser": repulser, "attractor": attractor, "drill": drill,
    "frame": frame, "framed": framed, "hollowstone": hollowstone, "portal": portal,
    "machine_frame": lambda p: cube("machine_block"),
    "track": lambda p: layered("machine_track", "machine_indicator", tint=track_grey(p.get("power", 0))),
    "trolley": lambda p: cube("machine_trolley"),
    "brace": lambda p: cube("machine_extender"),
    "conductor": lambda p: indicator("machine_conductor", p.get("power", 0)),
    "repeater": lambda p: indicator("machine_repeater_%d" % p.get("delay", 1), 15 if p.get("on") else 0),
    "toggle": toggle,
    "transformer": lambda p: indicator("machine_transformer", p.get("power", 0)),
    "timer": lambda p: indicator("machine_timer", 15 if p.get("on") else 0),
    "pulser": lambda p: indicator("machine_pulser", 15 if p.get("on") else 0),
    "engine": lambda p: indicator("machine_engine_lit" if p.get("lit") else "machine_engine", 15 if p.get("on") else 0),
    "transmitter": lambda p: indicator("machine_transmitter", p.get("power", 0)),
    "pipe": lambda p: cube("machine_pipe_filled" if p.get("filled") else "machine_pipe"),
    "fluid_pipe": fluid_pipe, "grabber": grabber, "machine_piston": machine_piston,
}
