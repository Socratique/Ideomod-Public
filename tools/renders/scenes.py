"""
Scripted illustrations of Ideomod's dynamic behaviours.

    python tools/renders/scenes.py            # render everything into docs/media
    python tools/renders/scenes.py portal     # render one scene

These are illustrations, not captures: each scene replays, by hand, what the
mod's code does in that situation (see the README section each one sits in).
Block textures come from src/main/resources; vanilla textures are read from a
local 1.20.1 client jar if one is found (set MC_CLIENT_JAR to point at one).
"""
import os
import sys

from PIL import Image, ImageDraw

from blocks import MODELS
from isorender import BG, MUTED, ROOT, TEXT, Renderer, Textures, font
from scene import Scene

OUT = os.path.join(ROOT, "docs", "media")

CYAN = (120, 220, 255)
AMBER = (255, 200, 90)
RED = (255, 120, 110)
GREEN = (130, 230, 140)
E, W, S, N, U, D = (1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0), (0, -1, 0)


def press(s, piston, group, d, steps=6):
    """A vanilla piston extends, carrying group one block along d."""
    s.move(group, d, steps=steps, also=lambda t: piston.set(ext=t))


def release(s, piston, steps=5):
    s.tween(lambda t: piston.set(ext=1 - t), steps=steps)


# ----------------------------------------------------------------------------

def sympathetic(tx):
    s = Scene(tx, "Sympathetic movement", look=(3.5, 0, 1.5), floor=((-1, 9), (-1, 4)))
    lever = s.add(0, 0, 0, "lever")
    piston = s.add(1, 0, 0, "piston", facing="south")
    a = s.add(1, 0, 1, "oddstone")
    s.add(2, 0, 1, "stone")
    s.add(3, 0, 1, "stone")
    b = s.add(4, 0, 1, "oddstone")
    c = s.add(6, 0, 1, "cobbled_oddstone")
    s.say("Two Oddstone blocks three apart, with stone between them.", 1900)
    s.links.append((a, b, CYAN))
    s.say("Same block type on the same axis line, within 5 blocks: they are linked.", 2200)
    lever.set(on=True)
    s.caption = "Push one with a piston..."
    s.snap(500)
    press(s, piston, [a, b], S)
    s.say("...and the other moves with it. The stone in between is ignored.", 2200)
    s.labels.append((c, "different channel", AMBER))
    s.say("Cobbled Oddstone only links with Cobbled Oddstone, so it stays put.", 2400)
    lever.set(on=False)
    release(s, piston)
    s.snap(900)
    return s


def one_way(tx):
    s = Scene(tx, "One-way stickiness", look=(1.5, 0, 2.5), floor=((-1, 5), (-2, 7)))
    l1 = s.add(0, 0, 1, "lever")
    p1 = s.add(0, 0, 0, "piston", facing="east")
    o1 = s.add(1, 0, 0, "oddstone")
    sl1 = s.add(1, 1, 0, "slime")
    l2 = s.add(0, 0, 5, "lever")
    p2 = s.add(0, 0, 4, "piston", facing="east")
    sl2 = s.add(1, 0, 4, "slime")
    o2 = s.add(1, 1, 4, "oddstone")
    s.say("Oddstone touching slime, twice. Only the pushed block differs.", 1900)
    l1.set(on=True)
    s.caption = "Push the Oddstone: it grabs the slime."
    s.snap(400)
    press(s, p1, [o1, sl1], E)
    s.labels.append((sl1, "pulled along", GREEN))
    s.snap(1700)
    l2.set(on=True)
    s.caption = "Push the slime: it cannot grab the Oddstone."
    s.snap(400)
    press(s, p2, [sl2], E)
    s.labels.append((o2, "left behind", RED))
    s.snap(2300)
    return s


def odd_slime(tx):
    s = Scene(tx, "Odd Slime: sticky in one direction only", look=(1.5, 0, 2.5), floor=((-1, 5), (-2, 7)))
    l1 = s.add(0, 0, 1, "lever")
    p1 = s.add(0, 0, 0, "piston", facing="east")
    a = s.add(1, 0, 0, "odd_slime", facing="east")
    st1 = s.add(1, 1, 0, "stone")
    l2 = s.add(0, 0, 5, "lever")
    p2 = s.add(0, 0, 4, "piston", facing="east")
    b = s.add(1, 0, 4, "odd_slime", facing="south")
    st2 = s.add(1, 1, 4, "stone")
    s.labels.append((st1, "faces east", CYAN))
    s.labels.append((st2, "faces south", CYAN))
    s.say("Odd Slime is only sticky when pushed the way its arrow points.", 2300)
    l1.set(on=True)
    s.caption = "Pushed east, facing east: sticky on every side, the stone comes along."
    s.snap(400)
    del s.labels[:]
    press(s, p1, [a, st1], E)
    s.snap(1900)
    l2.set(on=True)
    s.caption = "Pushed east, facing south: not sticky at all, the stone stays."
    s.snap(400)
    press(s, p2, [b], E)
    s.snap(2400)
    return s


def ranged(tx):
    s = Scene(tx, "Oddstone Repulser and Attractor", look=(3.5, 0, 1.5), floor=((-2, 9), (-1, 5)))
    l1 = s.add(-1, 0, 0, "lever")
    rep = s.add(0, 0, 0, "repulser", facing="east")
    o1 = s.add(2, 0, 0, "oddstone")
    l2 = s.add(-1, 0, 3, "lever")
    att = s.add(0, 0, 3, "attractor", facing="east")
    o2 = s.add(6, 0, 3, "oddstone")
    s.labels.append((rep, "Repulser", CYAN))
    s.labels.append((att, "Attractor", CYAN))
    s.say("Both act along the line they face, up to 12 blocks away.", 2000)
    for i in range(2):
        l1.set(on=True)
        s.links.append((rep, o1, CYAN))
        s.caption = "Repulser: each redstone pulse pushes the nearest Oddstone one block away."
        s.snap(350)
        s.move([o1], E)
        s.links.pop()
        l1.set(on=False)
        s.snap(700)
    for i in range(2):
        l2.set(on=True)
        s.links.append((att, o2, CYAN))
        s.caption = "Attractor: each pulse pulls the farthest Oddstone one block closer."
        s.snap(350)
        s.move([o2], W)
        s.links.pop()
        l2.set(on=False)
        s.snap(700)
    s.snap(1200)
    return s


def frames(tx):
    s = Scene(tx, "Frames: stickiness you can switch", look=(2.5, 0, 2.2), floor=((-2, 7), (-1, 5)))

    def build():
        del s.objs[:]
        del s.labels[:]
        o = {}
        o["stone"] = s.add(-1, 0, 2, "stone")
        o["lever"] = s.add(-1, 1, 2, "lever")
        o["m"] = s.add(0, 0, 2, "machine_frame")
        o["f"] = [s.add(x, 0, 2, "frame", active=True) for x in range(1, 6)]
        o["piston"] = s.add(3, 0, 3, "piston", facing="north")
        o["pl"] = s.add(4, 0, 3, "lever")
        return o

    o = build()
    for i, f in enumerate(o["f"]):
        s.labels.append((f, str(15 - i), AMBER))
    s.labels.append((o["m"], "Machine Frame", CYAN))
    s.say("A Machine Frame activates Iron Frames next to it: strength 15, minus 1 per frame.", 2600)
    del s.labels[:]
    o["pl"].set(on=True)
    s.caption = "Active frames stick to each other and to machine blocks: one rigid piece."
    s.snap(500)
    press(s, o["piston"], [o["m"]] + o["f"], N)
    s.snap(2000)

    o = build()
    s.say("Same build again. This time, power the Machine Frame first.", 1700)
    o["lever"].set(on=True)
    o["m"].set(powered=True)
    for f in o["f"]:
        f.set(active=False)
    s.say("A powered Machine Frame switches the frames it reaches off.", 2000)
    o["pl"].set(on=True)
    s.caption = "Inactive frames are not sticky: the piston moves one frame and nothing else."
    s.snap(500)
    press(s, o["piston"], [o["f"][2]], N)
    s.snap(2400)
    return s


def track(tx):
    s = Scene(tx, "Machine Track and Trolley", look=(3.5, 0.5, 1), floor=((-1, 9), (-1, 3)))
    lever = s.add(0, 0, 1, "lever")
    tracks = s.fill(range(1, 8), [0], [1], "track")
    trolley = s.add(1, 1, 1, "trolley")
    cargo = s.add(1, 2, 1, "machine_frame")
    s.labels.append((tracks[4], "Track line", CYAN))
    s.labels.append((cargo, "Trolley + cargo", CYAN))
    s.say("A line of Tracks, with a Trolley sitting on top carrying a Machine Frame.", 2400)
    del s.labels[:]
    s.say("A Track pushes the Trolleys touching it, away from where its signal came from.", 2400)
    for i in range(3):
        lever.set(on=True)
        s.caption = "Pulse %d: the signal runs down the line and the Trolley moves one block." % (i + 1)
        s.snap(350)
        s.move([trolley, cargo], E)
        s.snap(500)
        lever.set(on=False)
        s.snap(350)
    s.say("One block per pulse. The Trolley carries whatever is stuck to it.", 2400)
    return s


def machine_piston(tx):
    s = Scene(tx, "Machine Piston: every face is a piston face", look=(2, 0, 2), floor=((-1, 6), (-1, 5)))
    mp = s.add(2, 0, 2, "machine_piston", arms={})
    lw = s.add(1, 0, 2, "lever")
    ls = s.add(2, 0, 3, "lever")
    b1 = s.add(3, 0, 2, "stone")
    b2 = s.add(2, 0, 1, "cobblestone")
    s.say("A signal on one face extends an arm from the opposite face.", 2000)
    lw.set(on=True)
    s.caption = "Signal from the west: the arm goes east."
    s.snap(400)
    s.move([b1], E, steps=5, also=lambda t: mp.set(arms={"east": t}))
    s.snap(1300)
    ls.set(on=True)
    s.caption = "Signal from the south as well: a second arm goes north."
    s.snap(400)
    s.move([b2], N, steps=5, also=lambda t: mp.set(arms={"east": 1.0, "north": t}))
    s.snap(1700)
    lw.set(on=False)
    ls.set(on=False)
    s.caption = "Arms retract when their signal goes."
    s.tween(lambda t: mp.set(arms={"east": 1 - t, "north": 1 - t}), steps=5)
    s.snap(1500)
    return s


def brace(tx):
    s = Scene(tx, "Machine Brace: a longer push", size=(760, 400), tile=46, look=(7, 0, 0.5), floor=((-1, 17), (-1, 2)))

    def build(with_brace):
        del s.objs[:]
        lever = s.add(0, 0, -1, "lever")
        piston = s.add(0, 0, 0, "piston", facing="east")
        row = [s.add(x, 0, 0, "brace" if (with_brace and x == 1) else "stone") for x in range(1, 14)]
        return lever, piston, row

    lever, piston, row = build(False)
    s.say("13 blocks in front of a piston. The vanilla limit is 12.", 1800)
    lever.set(on=True)
    s.say("Nothing moves.", 1500)
    lever, piston, row = build(True)
    s.labels.append((row[0], "Brace", CYAN))
    s.say("Swap one block for a Machine Brace: +6 to the limit (up to 60 with eight).", 2400)
    del s.labels[:]
    lever.set(on=True)
    s.caption = "Now the same piston moves all 13."
    s.snap(400)
    press(s, piston, row, E)
    s.snap(2200)
    return s


def transmitter(tx):
    s = Scene(tx, "Machine Transmitter: wireless redstone", look=(3, 0, 3), floor=((-1, 8), (0, 7)))
    lever = s.add(0, 0, 1, "lever")
    src = s.add(1, 0, 1, "transmitter", power=0)
    rx = s.add(5, 0, 1, "transmitter", power=0)
    lamp1 = s.add(6, 0, 1, "lamp")
    other = s.add(1, 0, 5, "transmitter", power=0)
    lamp2 = s.add(2, 0, 5, "lamp")
    s.labels.append((src, "Oddstone", CYAN))
    s.labels.append((rx, "Oddstone", CYAN))
    s.labels.append((other, "Cobbled Oddstone", AMBER))
    s.say("The Oddstone variant in a Transmitter's slot is its channel.", 2200)
    lever.set(on=True)
    src.set(power=15)
    s.caption = "A Transmitter that gets redstone becomes the source..."
    s.snap(900)
    s.links.append((src, rx, CYAN))
    rx.set(power=14)
    lamp1.set(on=True)
    s.say("...and same-channel Transmitters within 5 blocks output its signal, minus 1.", 2600)
    s.say("The Cobbled Oddstone one is on another channel and stays off.", 2200)
    lever.set(on=False)
    src.set(power=0)
    rx.set(power=0)
    lamp1.set(on=False)
    del s.links[:]
    s.say("Source off, receivers off.", 1400)
    return s


def pipes(tx):
    s = Scene(tx, "Machine Pipe: push-only item transport", look=(2, 0, 1.5), floor=((-1, 6), (0, 5)))
    p = {k: s.add(x, 0, z, "pipe") for k, (x, z) in {"a": (0, 1), "b": (1, 1), "c": (2, 1), "d": (3, 1), "e": (2, 2)}.items()}
    c1 = s.add(4, 0, 1, "chest")
    c2 = s.add(2, 0, 3, "chest")
    count = {"a": 0, "b": 0, "c": 0, "d": 0, "e": 0, "c1": 0, "c2": 0}
    nxt = {"a": ["b"], "b": ["c"], "c": ["d", "e"], "d": ["c1"], "e": ["c2"]}
    rr = {"c": 0}

    def labels():
        del s.labels[:]
        s.labels.append((c1, str(count["c1"]), AMBER))
        s.labels.append((c2, str(count["c2"]), AMBER))

    labels()
    s.say("Pipes pass items along one at a time, at hopper speed.", 1900)
    it = s.add(0, 2.2, 1, "item", tex="mc:iron_block")
    s.caption = "Six items go into the first pipe."
    s.tween(lambda t: setattr(it, "pos", [0, 2.2 - 1.6 * t, 1]), steps=5)
    s.remove(it)
    count["a"] = 6
    p["a"].set(filled=True)
    s.snap(700)
    s.caption = "Each pipe pushes forward. At a junction it alternates between its outputs."
    for step in range(11):
        for k in ("e", "d", "c", "b", "a"):   # downstream first: one hop per step
            if count[k] > 0:
                outs = nxt[k]
                if len(outs) > 1:
                    tgt = outs[rr[k] % len(outs)]
                    rr[k] += 1
                else:
                    tgt = outs[0]
                count[k] -= 1
                count[tgt] += 1
        for k in p:
            p[k].set(filled=count[k] > 0)
        labels()
        s.snap(380)
    s.say("Three items in each chest. Pipes never pull, and a redstone signal locks them.", 2600)
    return s


def fluid_pipe(tx):
    s = Scene(tx, "Machine Fluid Pipe: pump and depositor", look=(2.5, 0, 1), floor=((-1, 7), (-1, 4)))
    s.add(0, 0, 0, "stone")
    s.add(-1, 0, 1, "stone")
    s.add(0, 0, 2, "stone")
    lava = s.add(0, 0, 1, "fluid", fluid="lava")
    f1 = s.add(1, 0, 1, "fluid_pipe")
    f2 = s.add(2, 0, 1, "fluid_pipe")
    f3 = s.add(3, 0, 1, "fluid_pipe")
    lever = s.add(1, 0, 2, "lever")
    s.say("A lava source, three Fluid Pipes, and open air at the far end.", 1900)
    lever.set(on=True)
    s.caption = "Powered and empty, a Fluid Pipe takes an adjacent source block."
    s.snap(900)
    s.remove(lava)
    f1.set(fluid="lava")
    s.snap(1300)
    s.caption = "A pipe holding fluid passes it to the next empty Fluid Pipe..."
    f1.set(fluid=None)
    f2.set(fluid="lava")
    s.snap(900)
    f2.set(fluid=None)
    f3.set(fluid="lava")
    s.snap(900)
    f3.set(fluid=None)
    s.add(4, 0, 1, "fluid", fluid="lava")
    s.say("...and the last one places it back in the world as a source block.", 2500)
    return s


def portal(tx):
    s = Scene(tx, "Oddstone Portal", size=(760, 480), tile=50, look=(6, 2.2, 1), floor=((-1, 14), (-1, 5)))
    corners = {(0, 0): "diamond_block", (4, 0): "gold_block", (0, 4): "iron_block", (4, 4): "emerald_block"}

    def frame_at(x0):
        for x in range(5):
            for y in range(5):
                if x in (0, 4) or y in (0, 4):
                    s.add(x0 + x, y, 1, corners.get((x, y), "oddstone"))

    def light(x0):
        return [s.add(x0 + x, y, 1, "portal", axis="x") for x in (1, 2, 3) for y in (1, 2, 3)]

    def throw(x0):
        pearl = s.add(x0 + 2, 2.5, 5, "pearl")
        s.tween(lambda t: setattr(pearl, "pos", [x0 + 2, 2.5 - 2.3 * t * t, 5 - 3 * t]), steps=6, ms=50)
        s.remove(pearl)

    frame_at(0)
    frame_at(8)
    s.say("An upright Oddstone rectangle with a mineral block at each corner.", 2200)
    s.caption = "Throw an ender pearl at the frame to light it."
    throw(0)
    a = light(0)
    s.snap(1300)
    s.say("The four corner blocks are the portal's address.", 1900)
    s.caption = "A second portal with the same corners..."
    throw(8)
    b = light(8)
    s.snap(600)
    s.links.append((a[4], b[4], CYAN))
    s.say("...links to the first. Walk through one, step out of the other.", 2600)
    s.say("A portal with no partner throws you 200 to 1000 blocks away instead.", 2400)
    return s


def hollowstone(tx):
    s = Scene(tx, "Hollowstone", look=(1.5, 0, 1.5), floor=((-2, 6), (-2, 5)))
    for x, z in ((-1, 0), (-1, 1), (-1, 2), (0, -1), (1, -1), (2, -1), (0, 3), (1, 3)):
        s.add(x, 0, z, "stone")
    lava = {(x, z): s.add(x, 0, z, "fluid", fluid="lava") for x in (0, 1, 2) for z in (0, 1, 2)}
    s.say("A pool of lava source blocks.", 1500)
    s.add(3, 0, 1, "oddstone")
    s.caption = "Place Oddstone beside it..."
    s.snap(900)
    s.remove(lava[(2, 1)])
    h = s.add(2, 0, 1, "hollowstone")
    s.say("...and each lava source touching it cools into Hollowstone.", 2300)
    it = s.add(2, 2.4, 1, "item", tex="mc:gold_block")
    s.caption = "Right-click Hollowstone with a block to set that block inside it."
    s.tween(lambda t: setattr(it, "pos", [2, 2.4 - 1.5 * t, 1]), steps=5)
    s.remove(it)
    h.set(inner="gold_block")
    s.snap(2600)
    return s


def logic(tx):
    s = Scene(tx, "Machine logic blocks", look=(3, 0, 2), floor=((-1, 7), (0, 5)))
    lever = s.add(0, 0, 1, "lever")
    cond = [s.add(x, 0, 1, "conductor", power=0) for x in range(1, 6)]
    tog = s.add(1, 0, 2, "toggle")
    tim = s.add(3, 0, 2, "timer")
    pul = s.add(5, 0, 2, "pulser")
    lamps = [s.add(x, 0, 3, "lamp") for x in (1, 3, 5)]
    s.labels.append((tog, "Toggle", CYAN))
    s.labels.append((tim, "Timer: 20 ticks", CYAN))
    s.labels.append((pul, "Pulser", CYAN))
    s.say("A lever feeds a line of Conductors (redstone wire as solid blocks).", 2200)
    # One frame = one redstone tick (2 game ticks). Devices answer a rising edge one redstone tick later.
    state = {"tog": False, "tim": 0, "pul": 0}
    script = {0: True, 14: False, 22: True, 36: False}
    caps = {0: "Lever on: the Toggle flips on, the Timer runs its 20 ticks, the Pulser blinks once.",
            14: "Lever off: the Toggle stays on.",
            22: "Lever on again: the Toggle flips off. Timer and Pulser fire again.",
            36: "Each device takes its input on one face and outputs on the other five."}
    on, rise_at = False, None
    for f in range(44):
        if f in script:
            new = script[f]
            if new and not on:
                rise_at = f
            on = new
            lever.set(on=on)
            for i, c in enumerate(cond):
                c.set(power=(15 - i) if on else 0)
            s.caption = caps[f]
        if rise_at is not None and f == rise_at + 1:
            state["tog"] = not state["tog"]
            state["tim"] = 10
            state["pul"] = 1
        tog.set(on=state["tog"])
        tim.set(on=state["tim"] > 0)
        pul.set(on=state["pul"] > 0)
        for lamp, v in zip(lamps, (state["tog"], state["tim"] > 0, state["pul"] > 0)):
            lamp.set(on=v)
        s.snap(170 if f not in script else 600)
        state["tim"] = max(0, state["tim"] - 1)
        state["pul"] = max(0, state["pul"] - 1)
    s.snap(1500)
    return s


def furnace_array(tx):
    # A 3x2 core in a 5x4 shell. The core has to be this big for the furnaces to show:
    # from this camera a one-block recess is hidden by the shell above and to the right of it.
    core = [(x, y) for x in (1, 2, 3) for y in (1, 2)]
    s = Scene(tx, "Furnace Array multiblock", look=(2.5, 1.8, 1.5), floor=((-1, 7), (-1, 5)))
    s.say("Any stone-type block from the furnace_array_frame tag works as the shell.", 2000)
    s.caption = "Back layer: solid."
    s.fill(range(5), range(4), [0], "stone_bricks")
    s.snap(1200)
    s.caption = "Middle layer: the furnaces, all facing the same way, ringed by shell blocks."
    for x in range(5):
        for y in range(4):
            if (x, y) not in core:
                s.add(x, y, 1, "stone_bricks")
    fs = [s.add(x, y, 1, "furnace", facing="south") for x, y in core]
    s.snap(1500)
    s.caption = "Front layer: a ring, with air in front of every furnace so you can reach it."
    for x in range(5):
        for y in range(4):
            if (x, y) not in core:
                s.add(x, y, 2, "stone_bricks")
    s.snap(1600)
    for f in fs:
        f.set(lit=True)
    s.labels.append(((2, 4.1, 2), "Enhanced Furnaces: cook 50% faster", GREEN))
    s.say("The furnaces become Enhanced Furnaces for as long as the shell is intact.", 2800)
    return s


def grabber(tx):
    s = Scene(tx, "Machine Grabber: slime you can switch", look=(1.5, 0, 2), floor=((-1, 5), (0, 5)))

    def build():
        del s.objs[:]
        del s.labels[:]
        o = {}
        o["pl"] = s.add(0, 0, 3, "lever")
        o["piston"] = s.add(0, 0, 2, "piston", facing="east")
        o["g"] = s.add(1, 0, 2, "grabber", on=False)
        o["gl"] = s.add(1, 0, 3, "lever")
        o["stone"] = s.add(1, 1, 2, "stone")
        return o

    o = build()
    s.labels.append((o["stone"], "Slime Grabber: off", CYAN))
    s.say("Off, a Grabber only sticks to machine blocks and active frames.", 2100)
    o["pl"].set(on=True)
    del s.labels[:]
    s.caption = "Pushed while off: the stone stays."
    s.snap(400)
    press(s, o["piston"], [o["g"]], E)
    s.snap(1700)
    o = build()
    s.caption = "Each redstone pulse flips it."
    o["gl"].set(on=True)
    o["g"].set(on=True)
    s.snap(600)
    o["gl"].set(on=False)
    s.labels.append((o["stone"], "Slime Grabber: on", GREEN))
    s.snap(1300)
    o["pl"].set(on=True)
    del s.labels[:]
    s.caption = "On, it grabs like a slime block: the stone comes along."
    s.snap(400)
    press(s, o["piston"], [o["g"], o["stone"]], E)
    s.snap(2400)
    return s


# ----------------------------------------------------------------------------

CATALOGUE = [
    ("Oddstone", "oddstone", {}), ("Cobbled Oddstone", "cobbled_oddstone", {}), ("Oddstone Bricks", "oddstone_bricks", {}),
    ("Hollowstone", "hollowstone", {}), ("Odd Slime", "odd_slime", {"facing": "east"}),
    ("Oddstone Repulser", "repulser", {"facing": "south"}), ("Oddstone Attractor", "attractor", {"facing": "south"}),
    ("Iron Frame", "frame", {"mat": "iron"}), ("Copper Frame", "frame", {"mat": "copper"}),
    ("Iron Framed Stone", "framed", {"mat": "iron", "inner": "stone"}),
    ("Machine Frame", "machine_frame", {}), ("Machine Track", "track", {}), ("Machine Trolley", "trolley", {}),
    ("Machine Conductor", "conductor", {"power": 15}), ("Machine Repeater", "repeater", {"on": True}),
    ("Machine Toggle", "toggle", {"on": True}), ("Machine Transformer", "transformer", {"power": 8}),
    ("Machine Timer", "timer", {"on": True}), ("Machine Engine", "engine", {"lit": True, "on": True}),
    ("Machine Pulser", "pulser", {"on": True}), ("Slime Grabber", "grabber", {"on": True}),
    ("Honey Grabber", "grabber", {"variant": "honey", "on": True}), ("Machine Transmitter", "transmitter", {"power": 12}),
    ("Machine Pipe", "pipe", {}), ("Machine Fluid Pipe", "fluid_pipe", {"fluid": "water"}),
    ("Machine Piston", "machine_piston", {"arms": {"up": 1.0}}), ("Machine Sticky Piston", "machine_piston", {"sticky": True}),
    ("Machine Brace", "brace", {}), ("Drill", "drill", {"facing": "south"}),
]


def catalogue(tx, path):
    cols, cw, ch = 6, 150, 150
    rows = (len(CATALOGUE) + cols - 1) // cols
    img = Image.new("RGB", (cols * cw, rows * ch + 8), BG)
    d = ImageDraw.Draw(img)
    f = font(13)
    for i, (name, kind, props) in enumerate(CATALOGUE):
        r = Renderer(tx, cw, 118, tile=72, origin=(0, 0))
        px, py = r.proj((0.5, 0.45 if kind != "machine_piston" or not props.get("arms") else 0.9, 0.5))
        r.ox, r.oy = cw - px, 118 - py
        dr = [((0, 0, 0), faces, see, 1.0, sum(c)) for faces, see, c in MODELS[kind](props)]
        cell = r.render(dr)
        x, y = (i % cols) * cw, (i // cols) * ch + 4
        img.paste(cell, (x, y))
        w = d.textlength(name, font=f)
        d.text((x + (cw - w) / 2, y + 122), name, font=f, fill=TEXT)
    img.save(path)
    return path


SCENES = [sympathetic, one_way, odd_slime, ranged, frames, track, machine_piston, brace, transmitter,
          pipes, fluid_pipe, portal, hollowstone, logic, furnace_array, grabber]


def main(argv):
    os.makedirs(OUT, exist_ok=True)
    tx = Textures()
    if tx.jar is None:
        print("note: no 1.20.1 client jar found; vanilla blocks will be flat grey (set MC_CLIENT_JAR)")
    want = set(argv)
    if not want or "catalogue" in want:
        print(catalogue(tx, os.path.join(OUT, "blocks.png")))
    for fn in SCENES:
        if want and fn.__name__ not in want:
            continue
        s = fn(tx)
        path = s.save(os.path.join(OUT, fn.__name__ + ".gif"))
        print("%s  %d frames  %.1fs  %d KB" % (path, len(s.frames), sum(s.durations) / 1000.0, os.path.getsize(path) // 1024))


if __name__ == "__main__":
    main(sys.argv[1:])
