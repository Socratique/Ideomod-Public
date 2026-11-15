#!/usr/bin/env python3
"""Builds the "Continents" world preset (ideomod:continents) from vanilla 1.20.1 worldgen JSON.

The preset keeps the vanilla terrain pipeline and changes its inputs:
  1. Continents: a larger-scale continentalness noise with an ocean bias, so land comes as
     separate continents in one big ocean.
  2. Highlands: an extra large-scale noise, shaped into flat tiers with steep steps, added to
     the terrain height on land only.
  3. Rivers: the valley dip that makes vanilla rivers is removed, and river biomes are not placed.
  4. Scale: erosion, ridges, temperature and vegetation are stretched horizontally.

Nothing in the minecraft: namespace is overridden, so other world types are unchanged.

Usage:  python build_continents.py --vanilla <dir holding vanilla data/minecraft/worldgen> [--out <data dir>]
The vanilla files come from the 1.20.1 server jar (data/minecraft/worldgen/...).

Written by Claude, directed and guided by The Ideomancer.
"""
import argparse
import json
import os
import shutil

# ---------------------------------------------------------------- settings (tune these)
P = dict(
    # continents
    cont_first_octave=-11,          # vanilla -9; each step down doubles the size of landmasses
    cont_amplitudes=[1.0, 1.0, 2.0, 2.0, 2.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0],
    cont_bias=-0.20,                # added to continentalness; lower = more ocean
    deep_from=-0.6,                 # below this, continentalness falls at deep_rate of its normal rate,
    deep_rate=0.4,                  # so mushroom fields (below -1.05) stay small and rare in the bigger oceans
    # highlands
    elev_first_octave=-9,
    elev_amplitudes=[1.0, 1.0, 0.5, 0.25],
    elev_xz_scale=0.2,
    # tiers: (noise value, blocks above normal). Flat between points with the same height.
    # Two shapes of the same tiers: cliffs (narrow steps) and ramps (wide steps). A third noise
    # picks between them from place to place, so some highland edges are sheer and some are slopes.
    elev_cliffs=[(-1.0, 0), (-0.084, 0), (-0.076, 24), (0.216, 24), (0.224, 48), (1.0, 48)],
    elev_ramps=[(-1.0, 0), (-0.16, 0), (0.0, 24), (0.14, 24), (0.30, 48), (1.0, 48)],
    cliff_first_octave=-8,
    cliff_amplitudes=[1.0, 1.0],
    cliff_share=0.15,               # added to the cliff/ramp noise; higher = more cliffs
    elev_gate_start=-0.11,          # continentalness where highlands begin (-0.11 = behind the beach)
    elev_gate_width=0.04,           # smaller = cliffs closer to the shore
    elev_fade_from=0.45,            # vanilla height offset where highlands start fading (peaks)
    elev_fade_to=0.85,              # ... and are gone, so peaks stay under the build limit
    # rivers
    valley_floor=-0.4,              # lowest peaks-and-valleys value the height splines may see
    river_biome_gap=0.06,           # weirdness is pushed this far from 0 so river biomes are never chosen
    # horizontal stretch of terrain character and climate (1.0 = vanilla)
    detail_scale=1.5,
)
BLOCK = 1.0 / 128.0                 # one block of height in offset units (384 blocks span 3.0)
NS = "ideomod:continents/"
MOVED = ["continents", "erosion", "ridges", "ridges_folded", "offset", "factor", "jaggedness",
         "depth", "sloped_cheese"]


def rewrite(obj):
    """Point references to the moved overworld density functions at our copies."""
    if isinstance(obj, str):
        for name in MOVED:
            if obj == "minecraft:overworld/" + name:
                return NS + name
        return obj
    if isinstance(obj, list):
        return [rewrite(v) for v in obj]
    if isinstance(obj, dict):
        return {k: rewrite(v) for k, v in obj.items()}
    return obj


def shifted(noise, xz_scale):
    return {"type": "minecraft:shifted_noise", "noise": noise, "shift_x": "minecraft:shift_x",
            "shift_y": 0.0, "shift_z": "minecraft:shift_z", "xz_scale": xz_scale, "y_scale": 0.0}


def add(a, b):
    return {"type": "minecraft:add", "argument1": a, "argument2": b}


def mul(a, b):
    return {"type": "minecraft:mul", "argument1": a, "argument2": b}


def clamp(x, lo, hi):
    return {"type": "minecraft:clamp", "input": x, "min": lo, "max": hi}


def flat(x):
    return {"type": "minecraft:flat_cache", "argument": x}


def main():
    ap = argparse.ArgumentParser()
    here = os.path.dirname(os.path.abspath(__file__))
    ap.add_argument("--vanilla", required=True)
    ap.add_argument("--out", default=os.path.join(here, "..", "..", "src", "main", "resources", "data"))
    ap.add_argument("--set", action="append", default=[], help="override a setting, e.g. --set cont_bias=-0.3")
    a = ap.parse_args()
    for kv in a.set:
        k, v = kv.split("=", 1)
        if k not in P:
            raise SystemExit("unknown setting " + k)
        P[k] = json.loads(v)
    V, OUT = a.vanilla, os.path.abspath(a.out)

    def vload(rel):
        with open(os.path.join(V, rel), encoding="utf-8") as f:
            return json.load(f)

    def write(rel, obj):
        path = os.path.join(OUT, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(obj, f, indent=1)
            f.write("\n")

    df_dir = "ideomod/worldgen/density_function/continents"
    shutil.rmtree(os.path.join(OUT, df_dir), ignore_errors=True)

    def df(name):
        return f"{df_dir}/{name}.json"

    s = 0.25 / P["detail_scale"]

    # noises
    write("ideomod/worldgen/noise/continents/continentalness.json",
          {"firstOctave": P["cont_first_octave"], "amplitudes": P["cont_amplitudes"]})
    write("ideomod/worldgen/noise/continents/elevation.json",
          {"firstOctave": P["elev_first_octave"], "amplitudes": P["elev_amplitudes"]})

    write("ideomod/worldgen/noise/continents/cliffness.json",
          {"firstOctave": P["cliff_first_octave"], "amplitudes": P["cliff_amplitudes"]})

    # 1. continents: bigger noise + ocean bias
    raw = add(P["cont_bias"], shifted(NS + "continentalness", 0.25))
    write(df("continents_raw"), flat(raw))
    deep = add(P["deep_from"], mul(P["deep_rate"], add(-P["deep_from"], NS + "continents_raw")))
    write(df("continents"), flat({"type": "minecraft:range_choice", "input": NS + "continents_raw",
                                  "min_inclusive": P["deep_from"], "max_exclusive": 1000.0,
                                  "when_in_range": NS + "continents_raw", "when_out_of_range": deep}))

    # 4. stretched terrain character
    write(df("erosion"), flat(shifted("minecraft:erosion", s)))
    write(df("ridges"), flat(shifted("minecraft:ridge", s)))

    # 3. no river valleys: the height splines never see the valley end of peaks-and-valleys
    folded = rewrite(vload("density_function/overworld/ridges_folded.json"))
    write(df("ridges_folded"), {"type": "minecraft:max", "argument1": P["valley_floor"], "argument2": folded})

    # 2. highlands
    def tier_spline(points):
        return {"type": "minecraft:spline", "spline": {
            "coordinate": NS + "elevation_noise",
            "points": [{"location": loc, "value": blocks * BLOCK, "derivative": 0.0} for loc, blocks in points]}}

    write(df("elevation_noise"), flat(shifted(NS + "elevation", P["elev_xz_scale"])))
    cliffs, ramps = tier_spline(P["elev_cliffs"]), tier_spline(P["elev_ramps"])
    mix = clamp(add(0.5 + P["cliff_share"], mul(4.0, shifted(NS + "cliffness", 0.25))), 0.0, 1.0)
    tiers = add(ramps, mul(mix, add(cliffs, mul(-1.0, ramps))))
    gate = clamp(mul(1.0 / P["elev_gate_width"], add(-P["elev_gate_start"], NS + "continents")), 0.0, 1.0)

    offset = rewrite(vload("density_function/overworld/offset.json"))
    inner = offset["argument"]["argument"]["argument2"]   # (-0.50375 + spline) * blend_alpha
    base = inner["argument1"]
    assert base["type"] == "minecraft:add" and base["argument2"]["type"] == "minecraft:spline", \
        "offset.json layout changed"
    write(df("offset_base"), flat({"type": "minecraft:cache_2d", "argument": base}))
    span = P["elev_fade_to"] - P["elev_fade_from"]
    fade = clamp(mul(-1.0 / span, add(-P["elev_fade_to"], NS + "offset_base")), 0.0, 1.0)
    write(df("elevation"), flat({"type": "minecraft:cache_2d", "argument": mul(tiers, mul(gate, fade))}))
    inner["argument1"] = add(NS + "offset_base", NS + "elevation")
    write(df("offset"), offset)

    for name in ("factor", "jaggedness", "depth", "sloped_cheese"):
        write(df(name), rewrite(vload(f"density_function/overworld/{name}.json")))

    # noise settings: vanilla overworld with the router pointed at the copies
    ns = vload("noise_settings/overworld.json")
    r = ns["noise_router"] = rewrite(ns["noise_router"])
    g = P["river_biome_gap"]
    r["ridges"] = flat({"type": "minecraft:range_choice", "input": NS + "ridges",
                        "min_inclusive": 0.0, "max_exclusive": 1000.0,
                        "when_in_range": add(g, NS + "ridges"), "when_out_of_range": add(-g, NS + "ridges")})
    r["temperature"] = shifted("minecraft:temperature", s)
    r["vegetation"] = shifted("minecraft:vegetation", s)
    write("ideomod/worldgen/noise_settings/continents.json", ns)

    # world preset
    preset = vload("world_preset/normal.json")
    preset["dimensions"]["minecraft:overworld"]["generator"]["settings"] = "ideomod:continents"
    write("ideomod/worldgen/world_preset/continents.json", preset)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
