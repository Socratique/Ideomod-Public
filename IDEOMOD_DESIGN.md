# IDEOMOD DESIGN DOCUMENT
### A Vanilla+ Minecraft Overhaul
<!-- NOTE: ASCII-only formatting. No emoji, no Unicode arrows/dashes/quotes. Use [DONE] [WIP] [DESIGNED] [IDEA] [HOLD] for status tags, -> for arrows, -- for dashes, x for multiplication. -->
*Last updated: 2026-02-18 (Multiblock Structure Framework Phase 1-2 implemented, Furnace Array working, Structure Debug Stick, Machine Block modular redesign, Hollow Stone, Odd Furnace, Oddserver, Placer/Framer merge, Oddstone sticky piston immunity, Machine Pipe/Fluid Pipe signal-pull mechanics, Machine Anchor, omnidirectional face-locking rule, Machine Clock/Pulser/Toggle)*

*Written by Claude (Anthropic), directed and guided by Sean.*
*This is a living design document. Nothing in it is final -- all features, systems, and decisions are subject to change as the project evolves. Status tags reflect current implementation state and design clarity, not permanence or commitment.*

---

## STATUS KEY

| Symbol | Meaning |
|--------|---------|
| [DONE] | Implemented & working -- but not necessarily final. May be revised, extended, or removed. |
| [WIP] | Partially implemented / in progress |
| [DESIGNED] | Design written out and agreed upon -- NOT a guarantee it will ship as written. |
| [IDEA] | Idea / brainstorm -- needs design |
| [HOLD] | Scrapped or on hold -- may be revisited |

*Note: Even [DONE] features are subject to redesign. This document describes current intent, not final decisions.*

---

## TABLE OF CONTENTS

1. [Blocks](#1-blocks)
2. [Items & Tools](#2-items--tools)
3. [Mechanical Systems](#3-mechanical-systems)
4. [Worldgen & Geology](#4-worldgen--geology)
5. [Mobs & AI](#5-mobs--ai)
6. [Magic & Enchanting](#6-magic--enchanting)
7. [Structures](#7-structures)
8. [Recipes & Crafting](#8-recipes--crafting)
9. [Systems & Infrastructure](#9-systems--infrastructure)
10. [Brainstorm Bin](#10-brainstorm-bin)

---

## 1. BLOCKS

### Ideoblock [DONE]
> Decorative stone block. First test block.

| Property | Value |
|----------|-------|
| ID | `ideoblock` |
| Hardness | 1.5 / Blast 6.0 (cobblestone) |
| Tool | Pickaxe |
| Recipe | 9 cobblestone -> 1 ideoblock |
| Notes | Placeholder. May become something or get removed. |

---

### Drill Block [HOLD]
> Directional mining block. Redstone-activated 3x3 excavator. Implemented and functional but on hold pending design review.

| Property | Value |
|----------|-------|
| ID | `drill` |
| Placement | Piston-style (faces player look direction) |
| Activation | Redstone pulse -> mines 3x3 in front |
| Drops | Mined items spawn at drill position |
| Limits | Skips bedrock / indestructible blocks |
| Movable | Yes (pistons can push it) |
| Recipe | Iron + piston + redstone (placeholder) |

**Known issues:**
- None currently

**Future ideas:**
- *[IDEA] Tiered drills? Iron -> Diamond -> Netherite with larger areas or speed*
- *[IDEA] Fortune/Silk Touch compatibility via adjacent enchanted block?*

---

### Oddstone [DONE]
> Strange stone with mob avoidance, redstone insulation, and sympathetic movement.

| Property | Value |
|----------|-------|
| ID | `oddstone` |
| Hardness | 2.0 / Blast 6.0 |
| Tool | Pickaxe |
| Recipe | None -- natural resource only. Must be mined. |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Mob avoidance | [DONE] | `isPathfindable=false` + Forge `DAMAGE_OTHER` hook. JSON config for per-mob control. |
| Redstone insulation | [DONE] | `isRedstoneConductor=false` -- acts like glass for redstone. |
| Sympathetic movement | [DONE] | Wireless slime -- each face searches for nearest Oddstone within radius (default 5). BFS chaining. Push chains count toward 12-block limit. |
| One-way stickiness | [DONE] | Oddstone grabs slime; slime does NOT grab Oddstone. Via mixin + StickinessRules. |
| Sticky piston immunity | [IDEA] | Sticky pistons should NOT be able to grab/pull Oddstone. Thematically, Oddstone is ungrabable -- it decides what it connects to. Currently only slime/honey/odd slime are blocked; vanilla sticky piston retraction is a separate code path that also needs blocking. |

**Known issues:**
- Face search is axis-aligned only (no cone/hemisphere)
- Placeholder texture & recipe

**Future ideas:**
- *[IDEA] Expand face search to half-sphere/cone per face*
- *[IDEA] Non-placeholder texture (purple-grey stone currently)*
- *[IDEA] Oddstone as universal mod economy resource -- gates Portals, Inhibitors, Machine Blocks (via Hollow Stone), Placers, Disenchanting Table (see Systems > Oddstone Economy)*

**Interactions:**
- Lava source blocks adjacent to Oddstone transform into Hollow Stone (Oddstone is NOT consumed). See Hollow Stone entry.

---

### Oddstone Variants [IDEA]
> Full stone-family variants of Oddstone (cobbled, polished, bricks, chiseled, stairs, slabs, walls, etc.). All retain Oddstone behavior but each variant is its own sympathetic frequency.

**Variants (planned):**
- Cobbled Oddstone
- Polished Oddstone
- Oddstone Bricks
- Chiseled Oddstone
- Oddstone Stairs, Slabs, Walls
- (Others as needed -- cracked bricks, mossy, etc.)

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Full Oddstone behavior | [IDEA] | All variants have mob avoidance, redstone insulation, one-way stickiness, and sympathetic movement. |
| Per-variant sympathetic frequency | [IDEA] | Each variant type forms its own sympathetic network. Oddstone links to Oddstone. Oddstone Bricks link to Oddstone Bricks. They do NOT cross-link. |
| Crafting between variants | [IDEA] | Standard stonecutter/crafting recipes to convert between forms (like vanilla stone family). |
| Portal frames | [IDEA] | Can any variant be used in portal frames, or only base Oddstone? TBD -- restricting to base Oddstone keeps portal building simple. |

**Design notes:**
- The frequency system massively expands contraption potential. Players can run multiple independent sympathetic networks in the same area by using different variants. Base Oddstone moves one thing, Oddstone Bricks move another, polished moves a third -- all without interfering.
- This turns a cosmetic choice (which variant looks best) into a functional one (which frequency do I assign to this circuit). Builders and redstone engineers get different value from the same blocks.
- Crafting between variants is free/lossless (stonecutter style). The rare resource is the Oddstone itself, not the shaping.
- All variants still count toward the 12-block piston push limit per group.

**Design questions (open):**
- *How many variants is too many? Each one is a separate sympathetic frequency, which is powerful. But more variants = more assets, more recipes, more registry entries.*

---

### Odd Furnace [IDEA]
> A furnace that cooks blocks in the world behind it. Fuel goes in the front, fire comes out the back.

| Property | Value |
|----------|-------|
| ID | `odd_furnace` |
| Hardness | TBD |
| Tool | Pickaxe |
| Type | Block Entity (single fuel slot) |
| Placement | Directional (faces player look direction) |
| Recipe | TBD (Oddstone + furnace?) |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Single fuel slot | [IDEA] | Right-click to open GUI with one slot. Accepts fuel items (coal, wood, lava bucket, etc. -- same as vanilla furnace fuel). |
| World-block smelting | [IDEA] | When fueled, checks the block directly behind it (opposite of FACING). If that block has a smelting recipe result (sand -> glass, cobblestone -> stone, raw iron -> iron ingot, etc.), converts it in place. |
| Burning particles | [IDEA] | Open-backed visual design. Fire/smoke particles emit from the back face when active, visually "cooking" the block behind it. |
| Fire damage | [IDEA] | Entities (players, mobs) standing directly behind an active Odd Furnace catch fire. Same behavior as standing in a campfire -- fire ticks, not instant damage. Creates trap/defense potential. |
| Fuel consumption | [IDEA] | Consumes fuel at the same rate as a vanilla furnace per block smelted. One coal smelts 8 blocks, etc. |

**Design notes:**
- No input/output item handling -- it works directly on blocks in the world. Place sand behind it, it becomes glass. Place raw iron ore behind it... does it drop iron ingots or place an iron block? Probably drops the smelting result as an item if the result isn't a block.
- The fire damage behind an active Odd Furnace is both thematic (open-backed furnace spitting fire) and functional (trap potential, area denial). Players and mobs alike get burned.
- Creates an interesting automation loop with Machine Pipes and Placers: Placer puts blocks behind the Odd Furnace, Odd Furnace cooks them, results drop or transform in place.
- Could work on a column of blocks behind it (smelts the nearest un-smelted block in a line) or just the single block directly behind it. Single block is simpler and more predictable.
- Thematically Oddstone-adjacent: strange, slightly magical, does familiar things in unfamiliar ways.

**Open questions:**
- *Does it only smelt the one block directly behind it, or scan a line?*
- *What happens when the result is an item not a block (e.g. raw iron ore -> iron ingot)? Drop as item entity? Need a hopper/pipe below to catch it?*
- *Speed: same as vanilla furnace, or faster/slower?*
- *Can it be pushed by pistons while fueled? (Probably yes -- mobile smelter.)*

---

### Oddserver [IDEA]
> Sympathetic observer. Detects block changes like a vanilla observer, but instead of a local redstone pulse, wirelessly triggers other Oddservers within sympathetic range along its face axes.

| Property | Value |
|----------|-------|
| ID | `oddserver` |
| Hardness | TBD |
| Tool | Pickaxe |
| Placement | Directional (faces player look direction) |
| Recipe | TBD (Oddstone + observer?) |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Block update detection | [IDEA] | Detects block changes on its front face, same as vanilla observer. |
| Sympathetic signal output | [IDEA] | Instead of emitting a redstone pulse from its own back, wirelessly triggers the back-face output of other Oddservers that are in line with its faces, within the Oddstone sympathetic movement distance (default 5 blocks). |
| Directional targeting | [IDEA] | Only targets Oddservers along the axis lines of its faces (6 directions). Does not broadcast omnidirectionally. |
| Output behavior | [IDEA] | Triggered Oddservers emit a redstone pulse from their back face, as if they had detected a change themselves. |

**Design notes:**
- Think of it as a wireless observer relay. Place one Oddserver watching a door, place another Oddserver 5 blocks away facing a redstone circuit. When the door opens, the first Oddserver detects the change and wirelessly fires the second Oddserver, which pulses into the circuit.
- The "line of faces" targeting means you have directional control. The Oddserver only talks to other Oddservers it can "see" along its axes, not everything in a radius. This prevents unwanted cross-talk.
- Uses the same sympathetic distance as Oddstone (configurable, default 5). Ties into the Oddstone family thematically and mechanically.
- Does NOT use Oddstone's BFS chaining -- it's a direct line-of-sight check within range, not a chain through intermediate Oddservers. (Or should it chain? TBD -- chaining would be more powerful but harder to predict.)

**Open questions:**
- *Does it chain through intermediate Oddservers? (A triggers B which triggers C?) Chaining is powerful but could create unintended cascades.*
- *Does it trigger ALL Oddservers in range along its axes, or just the nearest one per axis?*
- *Can it detect changes on all faces (like Machine Observer) or just the front face (like vanilla observer)?*
- *Does it follow Oddstone variant frequency rules? (Oddserver made from Oddstone Bricks only talks to other Oddstone Brick Oddservers?)*

---

### Hollow Stone [IDEA]
> Processed stone created when lava meets Oddstone. Required material for Machine Blocks. Also a decorative block with inner-block display.

| Property | Value |
|----------|-------|
| ID | `hollow_stone` |
| Hardness | TBD |
| Tool | Pickaxe |
| Type | Block Entity (stores inner decorative block ID) |
| Recipe | Not crafted directly -- see Creation below |

**Creation:**
When a lava source block is adjacent to Oddstone, the lava source transforms into Hollow Stone. The Oddstone is NOT consumed -- it acts as a catalyst. Hollow Stone can also be found naturally wherever lava sources and Oddstone coexist underground.

**Mining behavior:**
- Without Silk Touch: drops Hollow Stone Shards. If the block contained an inner decorative block, that block is also dropped as an item.
- With Silk Touch: drops itself (preserving inner block if present). TBD.
- Hollow Stone Shards craft back into Hollow Stone blocks (shaped/shapeless recipe TBD).

**Decorative feature:**
Each face of a Hollow Stone block has a small square hole visible in its texture. Right-clicking with a block in hand places that block "inside" the Hollow Stone -- visible through the holes on each face. Uses the same dynamic rendering approach as Framed Blocks (BlockEntityRenderer fetches inner block texture at render time), but inverted: the shell is the Hollow Stone with holes, the inner block shows through them.

**Role in progression:**
Hollow Stone is a required crafting ingredient for Machine Blocks. This creates the progression chain: find Oddstone -> expose to lava (or find naturally) -> collect Hollow Stone Shards -> craft Hollow Stone -> craft Machine Blocks.

**Design notes:**
- Oddstone is not consumed, so a single Oddstone vein near lava becomes a renewable Hollow Stone source (bring lava buckets, place sources adjacent to Oddstone, collect the results).
- The decorative inner-block feature gives Hollow Stone a building use beyond just being a crafting step. Players can use it as a display/accent block.
- Visually distinct from Framed Blocks: Framed Blocks have an external frame with the inner block filling most of the face. Hollow Stone has a solid stone shell with small window-like holes revealing the inner block.

**Open questions:**
- *Exact texture design -- how large are the holes? Centered square per face? Multiple small holes?*
- *Can any block be placed inside, or restrictions (no block entities, no air)?*
- *Does Hollow Stone have any of Oddstone's properties (mob avoidance, redstone insulation)? Probably not -- it's processed/transformed, not Oddstone itself.*

---

### Machine Block [DONE -- REDESIGN PLANNED]
> Modular mechanical chassis. A block entity with a single item slot that determines its specialization. Base behavior: selective stickiness to other Machine Blocks. Insert an item to transform it into a specialized machine type.

| Property | Value |
|----------|-------|
| ID | `machine_block` |
| Hardness | 3.0 / Blast 6.0 |
| Tool | Pickaxe |
| Sound | Metal |
| Type | Block Entity (single item slot + specialization ID) |
| Recipe | Hollow Stone + iron + slime (TBD -- requires Hollow Stone) |

**Base behavior (no item inserted):**
- Selective stickiness: sticky to all other Machine Block variants. Exclusive with slime & honey.
- One-way with slime: slime grabs Machine Block; Machine Block does NOT grab slime. Via mixin.
- Right-click opens a single-slot GUI. Insert an item to specialize. Right-click (or shift-right-click) to retrieve item. Block reverts to blank chassis when empty.

**Current implemented features (to be refactored):**

| Feature | Status | Notes |
|---------|--------|-------|
| Selective stickiness | [DONE] | Sticky only to other Machine Blocks. Exclusive with slime & honey. |
| Redstone conduction | [DONE] | Will become Machine Conductor specialization. |
| Power color tint | [DONE] | Will move to Machine Conductor specialization. |
| One-way with slime | [DONE] | Stays on base chassis. |

**Stickiness rules:**
All Machine Block variants (blank, Conductor, Pipe, Track, Trolley, etc.) stick to all other Machine Block variants. They are all Machine Blocks. The one exception: during a Track-initiated move operation, the Track and Trolley do not stick to each other (the Trolley slides free). In all other contexts (piston push groups, etc.), Tracks and Trolleys stick normally as Machine Blocks.

**Block entity movement:**
When a Machine Block is moved by a piston (or Repulser/Attractor), the block entity data is serialized (save the specialization item ID), the old block entity is destroyed, a new one is created at the destination, and the data is deserialized back. The stored data is minimal (one item ID), so overhead is negligible. Safety checks pause any active specialization behavior (item transfer, signal propagation) during the move tick. Reference implementations: Create mod contraption assembly, Carpet mod movable block entities.

**Visual feedback:**
Each specialization should have a distinct visual indicator so players can identify machine types at a glance. Approach TBD -- options include: overlay texture per specialization on each face (small icon), tint color per type, or subtle model variation. Base chassis (no item) uses the existing dark steel grey appearance.

---

#### Machine Specializations [IDEA]

The following specializations are defined by inserting the corresponding item into a Machine Block's GUI slot. The item sits in the slot and is retrievable at any time. Removing the item reverts the block to a blank chassis.

**Omnidirectional principle:** Several specializations (Piston, Observer, Repeater, Inverter) operate on all 6 faces simultaneously. The rule for these is: when a face is active (receiving signal, detecting change, etc.), only the opposite face is locked/affected. The other 4 faces remain fully independent. This means a single omnidirectional Machine Block can handle up to 3 non-opposing face pairs simultaneously, replacing what would take 3 separate vanilla blocks.

---

**Machine Conductor** (insert: Redstone Dust) [DONE -- needs refactor to specialization system]

Solid-state redstone wire as a full block. Receives and transmits redstone signal. Signal decays by 1 per block (like redstone dust). Whitelist (redstone_components tag) prevents feedback loops since Machine Conductor is itself a signal source. Visual: power color tint from dark steel grey (0) to bright red (15).

This is the existing Machine Block redstone behavior, moved into a specialization.

---

**Machine Pipe** (insert: Hopper) [IDEA]

Signal-triggered item transport. Inert until it receives a redstone signal. On signal, pulls one item from the container on the signal-receiving face, then automatically pushes it to the next connected container or Machine Pipe. Does not pull continuously -- each pulse moves one item.

| Detail | Value |
|--------|-------|
| Trigger | Redstone signal on any face. Pulls from the container on that face. |
| Transfer rate | 1 item per signal pulse |
| Default push direction | Opposite the face that received the signal (and pulled the item) |
| Round-robin | If connected to multiple valid outputs, cycles through them on subsequent pulls, excluding the face it pulled from |
| State tracking | Block entity stores last-output index for round-robin |
| Filtering | None (transfers any item). Filter specialization possible later. |
| Visual | TBD -- pipe icon overlay or subtle model change |

**Transfer flow:**
```
Signal arrives on NORTH face ->
  Check NORTH neighbor for inventory (chest, hopper, other pipe, etc.)
  Pull 1 item from that inventory ->
  Push to SOUTH (opposite face, default first output) ->
    If SOUTH is blocked or no container, try next valid face in round-robin ->
    Exclude NORTH (the pull face) from output candidates
```

**Design notes:**
- Signal-triggered means the pipe is controllable with redstone timing. Clock it with a repeater for steady throughput, pulse it manually for precise transfers.
- Simpler than hoppers: no passive suction, no comparator output (though that could be added). Just "signal in, item moves."
- The default-to-opposite-face behavior means a straight line of pipes works intuitively without configuration.
- Can receive items pushed from another Machine Pipe (acts as a container for the pushing pipe). When it receives an item this way, it pushes forward using the same round-robin logic.

---

**Machine Track** (insert: Powered Rail) [IDEA]

Rail infrastructure for Machine Trolleys. Receives and conducts redstone signal (like Machine Conductor). Special behavior: when a Machine Trolley is adjacent, the Track moves the Trolley in the direction opposite to the redstone signal source.

| Detail | Value |
|--------|-------|
| Signal behavior | Receives and conducts redstone like Machine Conductor |
| Trolley interaction | On receiving signal, checks adjacent blocks for Trolleys. Moves Trolley away from signal source direction. |
| Movement mechanic | Uses Repulser/Attractor-style piston move logic (RangedPistonUtil). Track acts as a context-specific Repulser for Trolleys. |
| Stickiness exception | During a Track-initiated move, the Track and the Trolley it is moving do NOT stick to each other. The Trolley slides free. All other Machine Blocks adjacent to the Trolley stick normally (payload). |

**Design notes:**
- The movement mechanic reuses existing Repulser infrastructure -- no new movement system needed.
- Trolleys can carry payloads: any Machine Blocks (non-Track, non-Trolley) stuck to the Trolley move with it as a piston group.
- Signal direction determines movement direction. Power east side of track line -> trolley moves west.

---

**Machine Trolley** (insert: Minecart) [IDEA]

Mobile platform that rides on Machine Tracks. Has no movement logic of its own -- entirely passive. The Track does the pushing.

| Detail | Value |
|--------|-------|
| Self-movement | None. Trolley is moved by adjacent Tracks. |
| Payload | Other Machine Blocks (not Tracks or Trolleys) stick to the Trolley and move with it as a group. |
| Stickiness | Normal Machine Block stickiness to everything EXCEPT: does not stick to the Track that is actively moving it. |

**Design notes:**
- A Trolley sitting on a line of Tracks is essentially a block-based minecart. No entity, no rails -- just blocks.
- Payload capacity is limited by piston push limits (or Machine Grabber-extended limits).
- Multiple Trolleys in a group? If two Trolleys are stuck together via intermediate Machine Blocks, a Track would move the whole group. Could be useful or chaotic -- worth testing.

---

**Machine Inverter** (insert: Redstone Torch) [IDEA]

NOT gate as a full block. Emits a redstone signal from any face that is NOT receiving a signal. When a face receives a signal, the opposite face stops emitting. Each axis operates independently -- only the opposite face is affected.

| Detail | Value |
|--------|-------|
| Default state | Emitting on all faces (like a redstone torch) |
| When powered on a face | Opposite face stops emitting. Other axes unaffected. |
| Per-axis operation | North/south, east/west, up/down each operate independently. Signal in from north turns off south output, but east/west/up/down outputs remain on. |
| Signal strength | 15 when emitting, 0 when suppressed (TBD -- or should it invert the signal level?) |

**Design notes:**
- Follows the same opposite-face-only rule as other omnidirectional specializations.
- A single Machine Inverter can invert up to 3 independent signal lines simultaneously.
- Default-on behavior means an unpowered Machine Inverter emits from all 6 faces at strength 15. Powering individual faces selectively disables their opposite outputs.

---

**Machine Repeater** (insert: Redstone Repeater) [IDEA]

Signal delay block. GUI includes number buttons to set tick delay (1-4 ticks, like vanilla repeater). Receives signal on any face, outputs on the opposite face after the set delay. Only the opposite face is locked per active signal -- can delay signals on up to 3 independent axes simultaneously.

| Detail | Value |
|--------|-------|
| Delay range | 1-4 ticks (matching vanilla repeater) |
| GUI | Number buttons for delay selection (in addition to the specialization item slot) |
| Directionality | Receives from any face, outputs on opposite face. Each axis operates independently. |
| Locking | Only the opposite face is locked per signal. Can relay on up to 3 non-opposing axes simultaneously. |
| Simultaneous operation | Signal from north (delay -> south) + signal from east (delay -> west) + signal from up (delay -> down) all operate independently with their own delay timers. |

**Design notes:**
- Each face-pair (north/south, east/west, up/down) maintains its own independent delay timer.
- Delay setting in GUI applies to all axes (single global delay value). Per-axis delay would be more powerful but significantly more complex GUI -- probably not worth it.
- A single Machine Repeater at a junction can delay 3 independent signal lines. Extremely space-efficient.

---

**Machine Clock** (insert: Clock) [IDEA]

Programmable pulse generator. When receiving a redstone signal, emits a 1-tick pulse at a configurable interval. GUI has selection buttons to set the tick interval. Stops pulsing when the input signal is removed.

| Detail | Value |
|--------|-------|
| Trigger | Requires continuous redstone input to operate. No input = no pulses. |
| Output | 1-tick redstone pulse at the configured interval |
| Interval | Configurable via GUI buttons (TBD -- range from every 2 ticks to every 40 ticks? Or specific presets?) |
| Omnidirectional | Follows the opposite-face-only rule. Input on one face, output on opposite face. Up to 3 independent clock signals simultaneously. |

**Design notes:**
- Replaces the need for vanilla repeater clock circuits (the classic 2-repeater loop). One block, configurable speed, clean signal.
- The "requires input" design means it's controllable -- feed it a signal to start the clock, cut the signal to stop. No runaway clocks.
- Combined with Machine Pipe, this is the core of automated item transport: Clock pulses the Pipe at whatever rate you want.
- The clock item as the specialization token is thematically perfect -- it's literally a clock.

---

**Machine Pulser** (insert: Button) [IDEA]

Rising-edge detector. Converts any incoming signal into a single 1-tick pulse. Only fires on the rising edge (signal turning on). A constant signal produces exactly one pulse -- the signal must turn off and on again to fire another. Essentially a monostable circuit as a single block.

| Detail | Value |
|--------|-------|
| Trigger | Rising edge of redstone signal (off -> on transition) |
| Output | Single 1-tick pulse on the opposite face |
| Constant signal | Ignored after the initial pulse. Signal must drop to 0 and return to fire again. |
| Omnidirectional | Follows the opposite-face-only rule. Each face-pair tracks its own edge state independently. Up to 3 independent pulse outputs simultaneously. |

**Design notes:**
- Essential for converting sustained signals into clean pulses. Place between a lever and a Machine Piston to get one push per flip instead of a stuck piston.
- Like a Conductor but smarter -- it doesn't just pass the signal through, it shapes it.
- The button as specialization token is thematic: buttons produce a single pulse, so does this block.
- Pairs naturally with Machine Clock: Clock generates periodic pulses from a constant signal, Pulser generates a single pulse from a constant signal.

---

**Machine Toggle** (insert: Lever) [IDEA]

Redstone-controlled toggle switch. Alternates between on and off states each time it receives a rising-edge signal. First pulse turns it on (emits redstone), second pulse turns it off, third turns it on, etc. Essentially a T flip-flop as a single block.

| Detail | Value |
|--------|-------|
| Trigger | Rising edge of redstone signal (off -> on transition) |
| Output | Toggles between emitting signal (on) and not emitting (off) on the opposite face |
| State | Persistent -- remembers on/off state. Survives chunk unloads. |
| Omnidirectional | Follows the opposite-face-only rule. Each face-pair maintains its own independent toggle state. Up to 3 independent toggles simultaneously. |

**Design notes:**
- Replaces the vanilla T flip-flop (which requires multiple blocks -- sticky piston + slime + redstone). One block, clean toggle.
- The lever as specialization token is perfect: a lever is a toggle, so is this block. But this one is redstone-controlled rather than player-controlled.
- Useful for: button -> toggle on/off for doors, machines, lights. Pulse from Machine Observer -> toggle a circuit. Any "press once to start, press again to stop" behavior.
- State persists through chunk unloads and game restarts (stored in block entity NBT).

---

**Machine Observer** (insert: Observer) [IDEA]

Omni-directional block update detector. Watches ALL six faces for block changes. When a change is detected on any face, sends a signal pulse out the opposite face. Only the opposite face is locked during that pulse -- all other faces remain active and can detect/fire independently.

| Detail | Value |
|--------|-------|
| Detection | All 6 faces simultaneously |
| Output | Pulse on the face opposite to the detected change |
| Locking | Only the opposite face is locked during a pulse (the output face cannot simultaneously detect). Other 4 faces remain fully active. |
| Simultaneous events | Can detect changes on up to 3 non-opposing face pairs simultaneously, firing 3 independent output pulses. |
| Pulse duration | 1 redstone tick (TBD) |

**Design notes:**
- Eliminates vanilla observer's biggest limitation: fixed orientation. One block, all directions covered.
- The opposite-face-only lockout means a single Machine Observer can serve up to 3 independent circuits at once (e.g. detect north -> fire south, detect east -> fire west, detect up -> fire down, all simultaneously).
- This makes it an incredibly dense detection hub. One block at an intersection replaces what would normally take 3 separate vanilla observers.

---

**Machine Piston** (insert: Piston or Sticky Piston) [IDEA]

Omni-directional piston. Any face can be the pushing face, determined by which face receives the redstone signal -- pushes out from the opposite face. No piston head needed. Only the opposite face is locked per signal -- can push in up to 3 directions simultaneously.

| Detail | Value |
|--------|-------|
| Push direction | Opposite to the face receiving signal |
| Sticky vs non-sticky | Determined by inserted item: regular Piston = push only, Sticky Piston = push and pull |
| Push limit | Standard (12, or extended by Machine Grabber/Extender if present in group) |
| Movement logic | Standard piston structure resolution via existing mixin infrastructure |
| Locking | Only the opposite face is locked per signal. Receiving signal on north locks south (pushing south), but east/west/up/down remain available. |
| Simultaneous push | Can push in up to 3 non-opposing directions at once (e.g. signal from north + east + up = push south + west + down simultaneously). |

**Design notes:**
- No piston head block. The Machine Piston pushes from its own face directly. This means it's a full cube that doesn't extend, which is a significant space savings over vanilla pistons.
- Simultaneous multi-directional push from a single block is extremely powerful. One Machine Piston at an intersection can serve 3 independent circuits.
- Each push direction resolves independently -- different push groups, different block limits per direction. A push failing in one direction doesn't affect the others.
- Combined with Machine Observer, enables very compact redstone contraptions that would normally require sprawling vanilla builds.

**Open questions:**
- *What happens if the same block is in two different push groups from the same Machine Piston? (e.g. a block that's both south and west of the piston, in both push groups) Probably: first push to resolve wins, second push recalculates with the block already moved.*

---

**Machine Battery** (insert: Redstone Block) [IDEA]

Wireless redstone broadcaster. When receiving a redstone signal, emits that signal to all Machine Blocks within a radius.

| Detail | Value |
|--------|-------|
| Trigger | Must receive a redstone signal to activate (unlike redstone block which is always on) |
| Broadcast radius | TBD (5 blocks to match Oddstone sympathetic range? Or configurable via GUI?) |
| Target | Machine Blocks only (all variants) |
| Signal strength | TBD -- broadcasts at received strength? Fixed 15? Decays with distance? |

**Design notes:**
- Creates wireless redstone clusters without physical wire connections.
- Potential feedback loop concern: if a Battery powers Machine Conductors, and those Conductors feed back to the Battery, infinite loop. May need the same whitelist approach as Machine Conductor, or a rule that Batteries don't trigger other Batteries.

---

**Machine Grabber** (insert: TBD -- possibly Piston + Slime? Or unique item?) [IDEA]

Active mass-capture block. When receiving a redstone signal, grabs a 3x3x3 volume of blocks in front of it, incorporating them into the current push group. Extends the effective piston push limit for the structure it's part of.

| Detail | Value |
|--------|-------|
| Activation | Redstone signal toggles grab state on/off |
| Capture volume | 3x3x3 cube in front of facing direction (27 blocks max) |
| Effect | Captured blocks become part of any piston push group that includes the Grabber |
| Push limit | Adds captured blocks to the group regardless of normal stickiness. Effectively extends push limit by up to 27. |
| Stickiness bypass | Grabs ANY movable block in the volume, not just sticky-connected ones |

**Design notes:**
- Unlike passive stickiness, the Grabber actively claims a volume. This lets it move heterogeneous collections of vanilla blocks that have no sticky properties.
- The 3x3x3 volume is fixed and directional -- the Grabber faces a direction and grabs the cube in front of it.
- When the Grabber is part of a piston group, the grabbed blocks move with it. When it's not being pushed, the grab state is just "ready" -- the blocks aren't held in place or anything.
- Very powerful -- should be expensive to craft and/or require Oddstone.

---

**Machine Fluid Pipe** (insert: Bucket) [IDEA]

Fluid transport. Signal-triggered, same pattern as Machine Pipe. The bucket in the slot is functional -- it fills with whatever fluid the pipe picks up. Deposits fluid out the output face like a dispenser.

| Detail | Value |
|--------|-------|
| Trigger | Redstone signal on any face. Pulls fluid from that face. |
| Fluid source | Picks up source blocks (water, lava) from the world, or pulls from cauldrons and fluid-handling block entities. Consumes the source block. |
| Bucket state | The bucket in the GUI slot fills with the picked-up fluid (empty bucket -> water bucket or lava bucket). Visible in GUI. |
| Output | Deposits fluid out the opposite face (default) or round-robins through output faces. Dispenses like a dispenser -- places a source block or fills a cauldron. |
| Round-robin | Same as Machine Pipe: excludes pull face, cycles through valid outputs. |
| Visual | TBD -- fluid color visible through block? Drip particles matching contained fluid? |

**Fluid interaction (water meets lava):**

When a fluid pipe tries to push fluid into another fluid pipe that already contains the opposing fluid, a reaction occurs:

| Push | Into Pipe Containing | Result |
|------|---------------------|--------|
| Water | Lava (lava bucket) | Lava bucket becomes obsidian. Pipe reverts to Machine Block with obsidian in slot (Machine Anchor). Pushed water is consumed. |
| Lava | Water (water bucket) | Water bucket becomes stone. Pipe reverts to Machine Block with stone in slot (inert). Pushed lava is consumed. |

Both fluids are consumed in the reaction. The receiving pipe loses its fluid pipe specialization because the bucket is destroyed -- replaced by the reaction product (obsidian or stone) sitting in the item slot.

**Design notes:**
- The bucket being functional (not just a specialization token) is key. You can see what fluid the pipe is carrying by checking the GUI.
- Fluid interactions create natural consequences for routing mistakes AND intentional fluid mixing. Water-into-lava producing a Machine Anchor (obsidian in slot) is a discoverable mechanic.
- Dispenser-style output means fluid pipes can fill areas, create water/lava sources, or feed into cauldrons. Useful for irrigation, lava moats, cobblestone generators, etc.
- Pulling from cauldrons enables controlled fluid intake from brewing/alchemy setups.

---

**Machine Anchor** (insert: Obsidian) [IDEA]

Immovable anchor point. A Machine Block with obsidian in its slot becomes unmovable by pistons, Repulsers, Attractors, or any other movement system. Critically, it retains Machine Block stickiness -- so if a piston tries to push a group that includes a Machine Anchor, the entire push fails (same behavior as vanilla obsidian blocking a piston push).

| Detail | Value |
|--------|-------|
| Movability | Immovable. Any push group containing a Machine Anchor fails to move. |
| Stickiness | Normal Machine Block stickiness. Sticks to all Machine Block variants. |
| Net effect | Acts as a brake/anchor. Locks a structure in place. |

**Design notes:**
- Primary use: fixed infrastructure. Anchor one end of a track line so Trolleys don't push the tracks. Anchor a station so moving contraptions dock against it without displacing it.
- Discoverable through the fluid pipe water-into-lava interaction (obsidian ends up in the slot). But can also be crafted intentionally by simply inserting obsidian into a Machine Block.
- The combination of "immovable" + "sticky" is what makes this special. Vanilla obsidian is immovable but not sticky. The Machine Anchor is both, which means it can block movement of entire connected structures, not just itself.
- Counterplay: remove the obsidian from the slot to make it movable again.

---

**Machine Push Limit Extender** (insert: TBD) [IDEA]

Passive push limit increase. When this Machine Block is part of a piston push group, it increases the push limit for that group. Each extender adds a fixed number to the limit (e.g. +6), but occupies one slot itself, creating a net gain with diminishing returns.

| Detail | Value |
|--------|-------|
| Limit increase | +6 per extender (TBD -- balance dependent) |
| Net gain | +5 per extender (gains 6 but uses 1 slot) |
| Passive | No activation needed -- just being in the push group is enough |
| Stacking | Multiple extenders stack additively |

**Design notes:**
- Rewards building with Machine Blocks. Want bigger contraptions? Use more Machine Block material.
- Diminishing returns create a soft ceiling -- you can push larger structures but each additional extender is proportionally less impactful.
- Different philosophy than Machine Grabber: Extender makes connected structures bigger. Grabber captures unconnected volumes.

---

#### Open Specialization Slots

*The following are potential future specializations. Items listed are thematic suggestions -- the system is extensible to any item-to-behavior mapping.*

| Potential Item | Possible Specialization | Notes |
|----------------|------------------------|-------|
| Comparator | Machine Comparator? | Signal measurement / conditional output. Reads inventory levels, signal strength comparison. |
| Dropper | Machine Ejector? | Ejects items into the world or into adjacent blocks. |
| TNT | Machine Launcher? | Explosive push? Launches blocks or entities? |
| Chest | Machine Storage? | Small shared inventory accessible to adjacent Machine Pipes. |
| Furnace | Machine Smelter? | In-line smelting within a pipe network? |
| Sculk Sensor | Machine Vibration Sensor? | Detects vibrations, outputs redstone. Wireless detection. |
| Note Block | Machine Speaker? | Plays sounds, redstone-activated. Programmable notes. |
| Daylight Detector | Machine Light Sensor? | Outputs signal based on light level at the block. |
| Compass | Machine Locator? | Detects or points toward specific block types in range? |
| Tripwire Hook | Machine Tripwire? | Detects entity passage between two Machine Tripwires. |

*This list is a brainstorm -- not all of these will necessarily be implemented. The system is designed to grow organically as new specializations prove useful.*

**Known issues (current implementation):**
- Current Machine Block is a simple block, not a block entity -- needs refactor to block entity with GUI
- Placeholder texture (needs mechanical pixel art showing the item slot)
- Placeholder recipe (will change to require Hollow Stone)
- Redstone conduction currently baked into the base block -- needs extraction to Machine Conductor specialization

---

### Framed Blocks [DESIGNED]
> Any block wrapped in an iron or copper frame. Extends Machine Block stickiness as cheap "body" pieces.

| Property | Value |
|----------|-------|
| ID | `iron_framed_block`, `copper_framed_block` |
| Type | Block Entity (stores inner block ID as ResourceLocation) |
| Hardness | Inherits inner block + frame bonus? TBD |
| Tool | Pickaxe |
| Drops | Normal: original block + frame block. Silk Touch: framed block item (preserves combo). |

**Model approach:**
Two overlapping 16x16x16 cuboids in the same block space:
- Inner cuboid: dynamically uses the texture of whatever block is stored inside. Cannot be a static JSON reference -- must be fetched at render time.
- Outer cuboid: static JSON model using the frame texture (iron or copper). The frame texture is a PNG that is mostly transparent, with opaque pixels only on the edges -- like a picture frame. This cuboid renders on top, its edge pixels overlaying the inner texture.

The outer cuboid IS a normal static JSON model. The inner cuboid requires a BlockEntityRenderer (BESR) -- Java code that runs each render frame, reads the stored block ID from the block entity, looks up that block's texture in Minecraft's texture atlas, and draws it onto the inner cuboid's faces. This is why a pure JSON model cannot achieve the "any block" behavior: JSON texture references are fixed at load time, not per-instance at runtime.

Reference: FramedBlocks mod uses this same pattern (block entity + BESR for dynamic inner texture).

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Inner block storage | [DESIGNED] | Block entity stores ResourceLocation of the wrapped block. |
| Outer frame (static) | [DESIGNED] | Normal JSON model. Frame texture has opaque edge pixels only -- transparent interior so inner texture shows through. 2 frame textures total (iron, copper). |
| Inner texture (dynamic) | [DESIGNED] | BlockEntityRenderer (BESR) fetches the stored block's texture from the atlas at render time and draws it on the inner cuboid faces. Works with any block including modded. |
| Machine-activated stickiness | [DESIGNED] | Framed blocks are NOT sticky by default. When a Machine Block is part of a piston push group, it "activates" adjacent framed blocks, making them sticky for that move. Activation chains through matching framed blocks (iron->iron, copper->copper). Deactivates after move resolves. |
| Material exclusivity | [DESIGNED] | Iron framed sticks to iron framed only. Copper to copper only. Neither type sticks to the other. Both types stick to Machine Blocks when activated. |
| Slime/honey exclusivity | [DESIGNED] | Framed blocks do not interact with slime or honey stickiness. |

**Stickiness matrix (when activated by Machine Block):**

| | Machine Block | Iron Framed | Copper Framed | Slime | Honey |
|---|---|---|---|---|---|
| Iron Framed | [Y] | [Y] | [N] | [N] | [N] |
| Copper Framed | [Y] | [N] | [Y] | [N] | [N] |

**Activation propagation example:**
```
[Piston] [Machine] [IronFramed] [IronFramed] [IronFramed]
  push ->   sticky -> activated  -> chains     -> chains
                     (touches M)  (touches    (touches
                                   activated)  activated)
```
Without a Machine Block in the group, framed blocks are inert and get pushed individually.

**Known issues:**
- Activation chaining requires mixin pre-pass: scan outward from Machine Blocks, mark connected framed blocks as active, then let resolver run, then clear marks.

**Future ideas:**
- *[IDEA] Copper frames oxidize over time? Waxable?*
- *[IDEA] Different blast resistance per frame material?*

---

### Frame Blocks [DESIGNED]
> Transparent decorative blocks -- an empty iron or copper frame. Same stickiness rules as Framed Blocks.

| Property | Value |
|----------|-------|
| ID | `iron_frame`, `copper_frame` |
| Hardness | Iron: ~3.0 / Copper: ~2.5 (TBD) |
| Tool | Pickaxe |
| Transparency | Non-solid, see-through (like iron bars as a full block outline) |
| Recipe | TBD (iron ingots / copper ingots in frame pattern?) |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Decorative transparency | [DESIGNED] | Renders as an open frame/cage outline. Not a full solid block. |
| Machine-activated stickiness | [DESIGNED] | Same rules as Framed Blocks -- activated by Machine Block presence in push group, chains with matching material type. |
| Material exclusivity | [DESIGNED] | Iron frames stick to iron frames and iron framed blocks. Copper to copper. Cross-material does not stick. |

**Notes:** Frame Blocks are essentially "empty" Framed Blocks. They share the same stickiness family. An Iron Frame and an Iron Framed Stone will stick to each other when activated.

---

### The Framer [MERGED -> Placer Block]
> Merged into Placer Block. The Placer handles framing when it attempts to place a Frame Block and there's already a block in front of it.

---

### Inhibitor Block [IDEA]
> Area-of-effect mob suppression block. Crafted from Oddstone. Creates a safe zone that reduces hostile mob spawning and discourages mobs from entering its radius.

| Property | Value |
|----------|-------|
| ID | `inhibitor` |
| Hardness | TBD |
| Tool | Pickaxe |
| Recipe | Oddstone + ? (TBD -- possibly includes ore vein materials to tie into mining loop) |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Spawn suppression (inner radius) | [IDEA] | Strong spawn reduction in close range (~8 blocks?). Exact radius TBD. |
| Mob discouragement (outer radius) | [IDEA] | Probabilistic -- mobs in outer zone (~24 blocks?) have a chance to want to leave. Not absolute. |
| Phantom counter | [IDEA] | Suppresses phantom spawning in range. QoL feature that gives all players a reason to engage with the system. |
| Mining aggro counter | [IDEA] | Counteracts the Mining Threat system (see Systems section). Required for sustained large vein mining. |

**Design notes:**
- Thematic extension of Oddstone's existing mob avoidance (Oddstone = line/surface defense, Inhibitor = area defense).
- Probabilistic, not absolute -- you're never 100% safe, which keeps tension alive.
- Radius needs to be large enough that 1-2 Inhibitors meaningfully protect a mining operation. Placing them is a strategic decision about *where* your safe zone is.
- Should feel like a prerequisite for seriously working a large ore vein, not just a convenience.

**Progression vision:**
- Early: Find large vein -> mine some -> danger ramps up -> retreat with partial haul.
- Mid: Craft Inhibitor(s) -> return to vein -> establish safe perimeter -> mine sustainably.
- Late: Multiple Inhibitors + Drills + Machine Block wiring -> full mining operation.

**Future ideas:**
- *[IDEA] Fuel/maintenance cost? (redstone power, consumable resource, durability?)*
- *[IDEA] Visual indicator of active radius? (particles, subtle glow?)*
- *[IDEA] Stacking multiple Inhibitors -- stronger effect or just wider coverage?*

---

### Placer Block [IDEA]
> Directional block that places blocks. Inverse of the Drill. Also functions as a Framer when placing Frame Blocks onto existing blocks. Costs Oddstone to craft.

| Property | Value |
|----------|-------|
| ID | `placer` |
| Placement | Directional (faces player look direction, like Drill) |
| Activation | Redstone pulse |
| GUI | Dispenser-style inventory (3x3 grid) |
| Behavior | Places a block from its inventory into the space in front of it. Context-sensitive: if placing a Frame Block and a block already exists in front, frames that block instead. |
| Recipe | Oddstone + ? (TBD) |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Block placement | [IDEA] | Dispenser-style inventory. Pulse -> place one block in facing direction. If space in front is air, places the block normally. |
| Framing mode | [IDEA] | If the next item in inventory is a Frame Block (Iron Frame or Copper Frame) AND the space in front is occupied by a frameable block, converts that block into the corresponding Framed Block variant instead. Consumes 1 Frame Block. |
| Directional | [IDEA] | Extends DirectionalBlock + FACING property, like Drill. |

**Placement logic:**
```
Pulse fires ->
  Next item in inventory is a normal block?
    Space in front is air? -> Place the block. Done.
    Space in front is occupied? -> Nothing happens.
  Next item is a Frame Block (Iron or Copper)?
    Space in front is air? -> Place the Frame Block as a block. Done.
    Space in front is a frameable block? -> Convert to Framed Block. Consume Frame Block. Done.
    Space in front is unframeable (bedrock, block entity, already framed)? -> Nothing happens.
```

**Design notes:**
- Paired with Drill as the other half of automated building (Drill removes, Placer adds).
- Framing mode is context-sensitive, not a separate mode toggle. The Placer just does the smart thing based on what it's holding and what's in front of it.
- Oddstone cost puts it in the mid-game, gated by Oddstone economy.
- Can be pushed by pistons while loaded (mobile placement/framing station).

**Edge cases to decide:**
- What blocks CAN'T be framed? (Bedrock, block entities like chests, other framed blocks, air?)
- If inventory has mixed items (some blocks, some frames), does it use whatever's next in sequence, or prioritize one type?

---

### Oddstone Repulser [DONE]
> Directional ranged piston that pushes Oddstone blocks at a distance. Scans a 12-block line in front and pushes any Oddstone found forward by 1.

| Property | Value |
|----------|-------|
| ID | `oddstone_repulser` |
| Placement | Directional (faces player look direction, piston-style) |
| Activation | Redstone pulse (rising edge) |
| Range | 12 blocks |
| Target | Oddstone blocks only |
| Movable | Yes (can be pushed by pistons) |
| Recipe | TBD (Oddstone + piston + ?) |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Directional scan | [DONE] | Scans 12-block line from FACING direction. Finds nearest Oddstone. |
| Ranged push | [DONE] | Pushes found Oddstone forward by 1 block. Uses vanilla piston move logic via RangedPistonUtil -- respects push limits, unmovable blocks, etc. |
| Full structure push | [DONE] | If Oddstone has adjacent blocks that would move as a piston group (sticky connections, sympathetic links), the whole group moves. |
| Block event sync | [DONE] | Uses vanilla block event system for client-server synchronization. Movement runs on both sides for proper animation. |

**Known issues:**
- Placeholder texture
- Placeholder recipe
- ~90% code duplication with Attractor (could extract shared base class)

**Design notes:**
- Range is detection range, not a containment field -- an Oddstone at position 12 can be pushed to 13.
- Sympathetic movement chains on pushed Oddstone should still trigger (creates powerful wireless contraption potential).
- Shares core scan/push logic with Oddstone Attractor, Arcane Repulser, and Arcane Attractor.

---

### Oddstone Attractor [DONE]
> Directional ranged piston that pulls Oddstone blocks toward it. Inverse of Oddstone Repulser.

| Property | Value |
|----------|-------|
| ID | `oddstone_attractor` |
| Placement | Directional (faces player look direction, piston-style) |
| Activation | Redstone pulse (rising edge) |
| Range | 12 blocks |
| Target | Oddstone blocks only |
| Movable | Yes (can be pushed by pistons) |
| Recipe | TBD (Oddstone + piston + ?) |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Directional scan | [DONE] | Scans 12-block line from FACING direction. Finds farthest Oddstone. |
| Ranged pull | [DONE] | Pulls found Oddstone toward the Attractor by 1 block. Uses vanilla piston move logic via RangedPistonUtil. |
| Full structure pull | [DONE] | Same as Repulser -- pulls the whole piston group, not just the Oddstone. |
| Block event sync | [DONE] | Same block event pattern as Repulser for client-server animation. |

**Known issues:**
- Placeholder texture
- Placeholder recipe
- ~90% code duplication with Repulser

**Design notes:**
- Block directly in front of Attractor must be air for closest Oddstone to be pulled.
- Shares core logic with Repulser. Implementation difference is scan direction and move direction.

---

### Arcane Repulser [IDEA]
> Upgraded Oddstone Repulser. Crafted with obsidian -- immovable, but pushes ANY block at range.

| Property | Value |
|----------|-------|
| ID | `arcane_repulser` |
| Placement | Directional (piston-style) |
| Activation | Redstone pulse |
| Range | 12 blocks (TBD -- consider shorter range like 8 as tradeoff for wider targeting) |
| Target | Any block a piston could push |
| Movable | No (immovable, like obsidian) |
| Recipe | TBD (Oddstone Repulser + obsidian, or Oddstone + obsidian + piston) |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Universal targeting | [IDEA] | Pushes any movable block, not just Oddstone. |
| Immovable | [IDEA] | Cannot be pushed by pistons. Obsidian crafting component = thematic consistency. |
| Piston move logic | [IDEA] | Full vanilla piston structure resolution -- push limits, unmovable blocks, group movement all apply. |

**Design notes:**
- Essentially a ranged piston. Very powerful -- range vs targeting tradeoff between Oddstone and Arcane variants may be worth exploring (e.g. Oddstone 16 range, Arcane 8 range).
- Immovability is both a crafting/lore detail and a balance lever -- can't be used as part of a moving contraption itself.

---

### Arcane Attractor [IDEA]
> Upgraded Oddstone Attractor. Crafted with obsidian -- immovable, but pulls ANY block at range.

| Property | Value |
|----------|-------|
| ID | `arcane_attractor` |
| Placement | Directional (piston-style) |
| Activation | Redstone pulse |
| Range | 12 blocks (TBD -- same range considerations as Arcane Repulser) |
| Target | Any block a piston could pull (if sticky) or push |
| Movable | No (immovable) |
| Recipe | TBD (Oddstone Attractor + obsidian) |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Universal targeting | [IDEA] | Pulls any movable block toward it. |
| Immovable | [IDEA] | Cannot be pushed by pistons. |
| Piston move logic | [IDEA] | Full vanilla piston structure resolution for pulled groups. |

**Design notes:**
- Same relationship to Arcane Repulser as Oddstone Attractor to Oddstone Repulser.
- All four blocks (Oddstone Repulser/Attractor, Arcane Repulser/Attractor) share a common scan-and-move utility. Differences are: target filter (Oddstone-only vs any), direction (push vs pull), movability (yes vs no).

**Repulser/Attractor family summary:**

| Variant | Target | Range | Movable | Tier | Status |
|---------|--------|-------|---------|------|--------|
| Oddstone Repulser | Oddstone only | 12 | Yes | Early-mid | [DONE] |
| Oddstone Attractor | Oddstone only | 12 | Yes | Early-mid | [DONE] |
| Arcane Repulser | Any block | 12 (TBD) | No | Mid-late | [IDEA] |
| Arcane Attractor | Any block | 12 (TBD) | No | Mid-late | [IDEA] |

---

### Hardstone [IDEA]
> Dense stone layer found at the deepest and transitional depths. Tougher than stone, just under obsidian.

| Property | Value |
|----------|-------|
| ID | `hardstone` |
| Hardness | ~25.0 / Blast ~600 (between stone 1.5 and obsidian 50/1200) |
| Tool | Pickaxe (copper minimum -- stone pickaxe cannot mine) |
| Drops | Itself (silk touch) or cobbled hardstone? TBD |

**Generation:**

| Layer | Location | Notes |
|-------|----------|-------|
| Deep layer | Just above bedrock (Y -60 to -54 approx) | Thick band. The "floor" before bedrock. |
| Transition layer | Between stone and deepslate (Y 0 to -8 approx) | Thinner band. Marks the boundary between the two stone types. |

**Design notes:**
- Provides a material tier between stone and obsidian -- currently vanilla has nothing in that gap.
- Copper tool gate means players need at least copper before they can dig into the deepest areas. Creates a soft progression gate for early underground exploration.
- Primary crafting material for Mortar & Pestle (see below).
- Could serve as a structural block for builds that want something tougher than stone but obtainable earlier than obsidian.

**Future ideas:**
- *[IDEA] Hardstone variants? (polished, bricks, chiseled) for building*
- *[IDEA] Hardstone tools? Mid-tier between stone and iron -- or is that redundant with copper?*
- *[IDEA] Blast-resistant building material for base defense*

---

### Mortar & Pestle [IDEA]
> Pot-shaped utility block for grinding basic resources. Increases conversion rates for simple material processing.

| Property | Value |
|----------|-------|
| ID | `mortar_and_pestle` |
| Hardness | ~3.0 / Blast 6.0 |
| Tool | Pickaxe |
| Recipe | Hardstone (mortar shape) + cobblestone (pestle) -- shaped recipe TBD |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Simple GUI | [IDEA] | Small interface -- input slot + output slot. No fuel needed. |
| 1.5x conversion rate | [IDEA] | Processes the same recipes as vanilla crafting (flowers->dyes, sugarcane->sugar, etc.) but yields ~1.5x output. Rounds down on odd numbers -- 1 input still gives 1 output, but 2 inputs give 3, etc. |
| New grinding recipes | [IDEA] | Unlocks conversions not possible by hand. Specific recipes TBD -- potential examples: bone->bonemeal (better rate than crafting), gravel->flint (guaranteed), wheat->flour(?), blaze rod->blaze powder (better rate). |
| Brewery prerequisite | [IDEA] | Required contributor block for the Brewery/Alchemy Station structure. |

**Design notes:**
- Early-game utility block. Hardstone requires copper tools to mine, so this is available once the player has copper -- early but not immediate.
- Not Oddstone-gated. This is a stepping stone toward the Brewery, not a mid-game gate.
- Visual: pot/bowl shape sitting on a surface. Cobblestone pestle visible inside or beside it.
- Activation: right-click to open GUI, place items, output appears (instant or short delay TBD).

**Future ideas:**
- *[IDEA] Particle effect when processing? (dust puffs)*
- *[IDEA] Sound effect? (grinding stone sound)*

---

### Branch Block [IDEA]
> Connecting log-like block that forms organic tree/vine structures. Half-thickness centered log that visually joins to adjacent branches in all directions.

| Property | Value |
|----------|-------|
| ID | `branch` |
| Hardness | ~2.0 / Blast 0 (log-equivalent) |
| Tool | Axe |
| Recipe | TBD (logs?) |
| Notes | Decorative/structural. Connects to adjacent branches, forming trees, arbors, scaffolding-like organic shapes. |

**Concept:**
A branch is a centered 8x8 (half-thickness) log core occupying the middle of a block space. When placed adjacent to other branches, they visually join together. The shape the branch displays depends entirely on which of its 6 faces have a neighboring branch.

**Shape Logic:**
Branches use a multipart blockstate system -- the final visible shape is assembled from layered model pieces, not a single model per combination. This keeps the model file count manageable.

Base model pieces (all can be rotated to cover all 6 directions):
- Core: the central 8x8x8 node (always present)
- Arm: a 8x8 segment extending from center to one face edge (one per connected direction)

The game composes: Core + whichever Arms correspond to connected faces. This gives all possible shapes automatically with only a handful of model files.

**Connection directions:**
Branches connect in all 6 directions (up, down, north, south, east, west). A branch connects to any adjacent branch regardless of that neighbor's own orientation.

**Shape examples (for visualization):**
- Isolated: just the central node (a small cube stub)
- Two opposite connections (e.g. up + down): a vertical pillar segment
- Two perpendicular connections (e.g. up + north): an L-bend corner
- Three connections (e.g. up + north + east): a T or corner junction
- Four connections in a plane (e.g. all horizontal): a flat plus/cross
- Six connections: full junction node

**Corner/bend geometry note:**
Unlike fences (where each arm is straight and identical), a branch arm that bends 90 degrees does NOT require unique geometry -- the core node handles the visual join, and each arm is simply a straight half-log segment pointing outward. This means corner shapes are handled naturally by the multipart composition without dedicated corner models. This is a key advantage of the centered-core design.

**Blockstate approach:**
Six boolean properties: north, south, east, west, up, down (same pattern as fence). 2^6 = 64 combinations, all handled automatically by multipart composition. The blockstate JSON applies each arm piece conditionally: "when: {up: true} -> apply: arm_up model".

**Block update behavior:**
On placement and on neighbor change, the branch checks all 6 adjacent positions for other branch blocks and updates its own connection booleans accordingly. Neighbors also update their own states when a branch is placed or removed next to them (standard Minecraft neighbor update pattern -- same as how fences work).

**State space summary:**

| States | Count |
|--------|-------|
| Connection booleans (N/S/E/W/U/D) | 6 x true/false |
| Total combinations | 64 |
| Model files needed | ~7 (core + 1 arm, rotated via blockstate JSON) |

**Design notes:**
- Centered half-thickness means branches visually line up through corners -- no awkward offset like vanilla logs do when placed perpendicular.
- All vanilla log types could potentially have a branch variant, or a single "branch" block could have a wood type property. TBD based on scope.
- Could generate naturally as part of tree feature worldgen (thick canopy trees, dead trees, mangrove-style growth).
- Does NOT need to be waterloggable initially, but worth considering for swamp aesthetics.
- Leaf blocks placed adjacent to branches look natural -- no special behavior needed.

**Open questions:**
- *Single branch block with a wood_type property, or one branch block per wood type (like vanilla logs)?*
- *Does it generate naturally or is it purely player-placed?*
- *Strip-able with axe like vanilla logs?*
- *Should isolated branches (no connections) render as a small stub node, or default to a full pillar for easier placement?*

---

### Bush Block [IDEA]
> Connecting foliage block. A slightly undersized leaf-cube that expands to meet adjacent bushes, forming seamless hedges, shrubs, and ground cover.

| Property | Value |
|----------|-------|
| ID | `bush` |
| Hardness | ~0.2 / Blast 0 (leaf-equivalent) |
| Tool | Shears (drops itself), Axe (drops nothing or berries TBD) |
| Recipe | TBD |
| Notes | Decorative foliage. Connects to adjacent bush blocks visually. Could generate naturally as undergrowth. |

**Concept:**
An isolated bush renders as a 14x14x14 cuboid centered in the block space -- slightly inset from the block edges on all sides (1 unit of gap per face). When a neighboring bush is present, that face extends out to the full 16-unit block edge, seamlessly joining the two together into one continuous mass. This is the inverse of the branch approach: instead of adding arms outward, the bush expands faces outward to close gaps.

**Model approach:**
Each face has two possible states: inset (gap present, no neighbor) or flush (full extent, neighbor present). Rather than 64 models, this can be handled with a multipart blockstate: start with the base 14x14x14 cube, then conditionally apply a thin "extension slab" piece on each face that bridges the gap to the block edge.

Base pieces:
- Core: 14x14x14 centered cube (always present)
- Face extension: a 1x14x14 slab that fills the gap on one face (one per direction, applied when neighbor present)

6 boolean properties (same as branches/fences): north, south, east, west, up, down.

**Visual behavior examples:**
- Isolated: slightly smaller cube with visible gaps on all sides -- a shrub stub
- Two adjacent in a row: seamless rectangular mass, no visible seam
- 3x3 ground-level hedge: completely flush top and sides, looks like a solid hedge
- Corners: the face extensions from two perpendicular bushes overlap cleanly at the corner (both extend to the edge, filling the corner)

**Difference from Branch Block:**
Branches express their connections as arms (additive geometry extending outward from center). Bushes express their connections as face expansions (filling the gap between the inset core and the block edge). Branches are structural/skeletal; bushes are volumetric/mass-filling. Both use the same 6-boolean blockstate pattern and multipart composition strategy.

**Design notes:**
- Leaf-like appearance -- uses a foliage-tinted texture similar to leaves, or a custom denser leaf texture.
- Does not need to be transparent in the same way leaves are (no "fast/fancy" leaf transparency needed) -- solid faces are fine and simpler.
- Could have biome-tinted color like vanilla leaves, or fixed green variants per biome.
- Generates naturally as undergrowth in forests, dense vegetation in jungles, hedgerows in plains.
- Could serve as a player-cuttable resource (shears -> bush block, axe -> nothing or small drop).
- Naturally pairs with Branch block -- branches form the skeleton of a tree or shrub, bushes fill the canopy volume.

**Open questions:**
- *Fixed texture or biome-tinted like vanilla leaves?*
- *Drop when broken without shears -- nothing, leaves, or a unique berry/fruit item?*
- *Should it generate naturally or be purely player-placed initially?*
- *Multiple variants (oak bush, jungle bush, etc.) or a single universal bush block?*

---

### [Template -- Copy for New Blocks]
> *One-line description.*

| Property | Value |
|----------|-------|
| ID | `name` |
| Hardness | ? / Blast ? |
| Tool | ? |
| Recipe | ? |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Feature name | [IDEA] | Description |

---

## 2. ITEMS & TOOLS

### Structure Debug Stick [DONE]
> Debug/testing tool for the multiblock framework. Hold in either hand to see structure register/deregister events in chat.

| Property | Value |
|----------|-------|
| ID | `structure_debug_stick` |
| Type | Simple item, stacksTo 1 |
| Texture | Vanilla stick (placeholder) |
| Recipe | None -- creative only for now |

**Behavior:** When held in mainhand or offhand, StructureManager sends color-coded chat messages on structure formation (green) and removal (red). Shows structure type name, core position, and core count. Zero overhead when nobody holds it.

---

**Future ideas:**
- *[IDEA] Tool/armor modifiers via multiblock crafting stations (see Recipes & Crafting > Crafting Modifiers)*
- *[IDEA] Custom tool materials tied to geology system*

---

### Whistle Bone [IDEA]
> Rare drop from the Whistler mob. A usable item that functions as a whistle. When named on an anvil to match a tamed pet or name-tagged mob, using it teleports that mob to the player regardless of distance.

| Property | Value |
|----------|-------|
| ID | `whistle_bone` |
| Type | Usable item (right-click to activate) |
| Source | Dropped by Whistler mob on death |
| Drop rate | TBD (rare) |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Name matching | [IDEA] | Anvil-name the item to match a mob's name. Using it teleports the matched mob to the player. |
| Sound effect | [IDEA] | Plays a whistle sound on use. Thematic link to the Whistler mob it came from. |

**Design questions (open):**
- *Tamed pets only, or any name-tagged mob? Pets-only is safer balance. Any mob is more fun/chaotic (teleport villagers, iron golems, etc.)*
- *Cross-dimension teleport? Very powerful but very useful. Could be the main draw.*
- *Durability/cooldown? Options: single-use (too punishing?), limited durability (8-16 uses?), cooldown (ender pearl style?), infinite use (rarity is the balance lever).*
- *What happens if multiple mobs share the same name? Teleport closest? Teleport all? Fail?*

---

### Unique Loot Items [IDEA]
> Special items found as world loot. Not craftable by default (some may become craftable via Crafting Schematics). Each has a unique property or behavior that makes it worth seeking out.

**Butcher's Knife:**

| Property | Value |
|----------|-------|
| ID | `butchers_knife` |
| Type | Tool (sword-class or axe-class, TBD) |
| Source | Loot (village butcher chests, pillager outposts, other structure loot) |
| Durability | TBD (iron-tier equivalent?) |

| Feature | Status | Notes |
|---------|--------|-------|
| Innate Looting | [IDEA] | Acts as if it has a minor Looting enchant (~Looting I equivalent). Stacks with actual Looting enchantment. |
| Extended loot tables | [IDEA] | Mobs killed with the Butcher's Knife drop items not in their normal loot table but thematically part of their body. Animals drop bones. Possibly: chickens drop extra feathers, cows drop sinew/tendon (new crafting material?), spiders drop extra eyes, etc. |

**Design notes:**
- The Butcher's Knife is the template for unique loot items -- a tool with a specific identity and niche, not just "better sword."
- Extended loot tables make it a functional tool for specific resource gathering, not just combat. Bone farming from passive mobs is a genuine use case.
- Can be enchanted normally (including with more Looting for stacking).

**(More unique items TBD -- this section will grow as ideas develop.)**

**Future ideas:**
- *[IDEA] Cartographer's Compass -- points toward nearest unexplored structure?*
- *[IDEA] More unique weapons/tools with innate properties, found as rare loot*

---

### Artisan's Ink [IDEA]
> Rare resource used for copying schematics at a Drafting Table. See Recipes & Crafting > Schematics & Drafting Table.

| Property | Value |
|----------|-------|
| ID | `artisans_ink` |
| Type | Crafting material |
| Source | TBD (rare mob drop? Complex recipe? Villager trade?) |

**Design notes:**
- The limiter on schematic copying. Should be obtainable but never trivially abundant.
- Thematically: a special ink for precise technical drawing. Ties to the Drafting Table's "blueprint" aesthetic.

---

### Magic Bottle [IDEA]
> Empty enchanted vessel created at an Enhanced Enchanting Table structure. Can capture environmental effects from the world. See Magic & Enchanting > Enchanting Overhaul > Magic Bottles.

| Property | Value |
|----------|-------|
| ID | `magic_bottle` |
| Type | Usable item (right-click on environment to capture) |
| Source | Glass bottle enchanted at Enhanced Enchanting Table structure |
| Stackable | Yes (16? TBD) |

**Capturable effects (TBD -- examples):**

| Source | Result | Effect |
|--------|--------|--------|
| Campfire smoke | Bottled Smoke | Blinds, slows, damages drinker briefly |
| Nether portal block | Bottled Nether Portal | Teleports drinker to the Nether |
| (more TBD) | | |

---

## 3. MECHANICAL SYSTEMS

### Piston Stickiness Framework [DONE]
> Mixin-based system for asymmetric (one-way) sticky block behavior.

| Component | File | Status |
|-----------|------|--------|
| StickinessRules registry | `StickinessRules.java` | [DONE] |
| PistonStructureResolver mixin | `PistonStructureResolverMixin.java` | [DONE] |
| Rule registration | `Ideomod.java -> commonSetup` | [DONE] |

**How it works:** Blocks register "grab immunity" rules as predicates. The mixin intercepts `addBlockLine()` in the piston resolver and checks rules before allowing a sticky block to grab its neighbor. This enables block A to grab block B without B grabbing A.

**Current rules:**
- Slime -> Oddstone: **BLOCKED** (Oddstone is immune to slime grabs)
- Honey -> Oddstone: **BLOCKED**
- Odd Slime -> Oddstone: **BLOCKED**
- Sticky Piston -> Oddstone: **BLOCKED [IDEA]** (Oddstone cannot be grabbed/retracted by sticky pistons. Oddstone is aesthetically and thematically ungrabable -- it decides what it connects to, nothing else can claim it. Requires mixin to intercept sticky piston retraction logic.)
- Machine Block -> Slime: **BLOCKED** (Machine Block won't drag slime)
- Machine Block -> Honey: **BLOCKED**
- Machine Block -> Odd Slime: **BLOCKED**
- Slime -> Odd Slime: **BLOCKED** (mutual exclusion via canStickTo)
- Honey -> Odd Slime: **BLOCKED** (mutual exclusion via canStickTo)
- Odd Slime directional rule: stickiness disabled when pushDirection != FACING
- Machine Track <-> Machine Trolley during Track move: **CONDITIONAL [IDEA]** (not sticky to each other only during Track-initiated movement. Normal Machine Block stickiness in all other contexts.)

---

### Sympathetic Movement [DONE]
> Wireless slime block system for Oddstone. See Oddstone block entry.

---

### Odd Slime [DONE]
> Directional slime block. Only sticky when pushed in its FACING direction.

| Property | Value |
|----------|-------|
| ID | `odd_slime` |
| Placement | Directional (piston-style, FACING property) |
| Friction | 0.8F (slippery like ice) |
| Sound | Slime |
| Recipe | 1 slime block + 1 oddstone -> 1 odd slime (shapeless) |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Directional stickiness | [DONE] | Only sticky when pushed in FACING direction. Pushed sideways or opposite = acts non-sticky. Enforced by StickinessRules directional rule + mixin. |
| Slime/honey exclusion | [DONE] | canStickTo returns false for SlimeBlock and HoneyBlock (mutual exclusion, same as slime vs honey in vanilla). |
| Oddstone immunity | [DONE] | Grab immunity rule prevents Odd Slime from grabbing Oddstone. |
| Chain pulling | [DONE] | When active (pushed in FACING direction), Odd Slime pulls blocks behind it in the backward walk. Special mixin exemption allows this despite grab immunity. |

**Known issues:**
- Placeholder texture

---

### Repulser/Attractor System [DONE]
> Ranged directional pistons. Scan a line and push/pull blocks at distance using vanilla piston move logic.

**Variants:** Oddstone Repulser [DONE], Oddstone Attractor [DONE], Arcane Repulser [IDEA], Arcane Attractor [IDEA] (see Blocks section for full details).

**Core mechanic:** All four blocks share a common scan-and-move utility (RangedPistonUtil). On redstone pulse, scan a line in FACING direction, find target blocks, and trigger piston-style movement via the block event system for client-server sync. Differences between variants are target filter (Oddstone-only vs any block), scan direction (nearest vs farthest), move direction (push vs pull), and movability (Oddstone variants movable, Arcane variants immovable).

**Architecture:** Uses RangedPistonFlags (ThreadLocal) to pass target position into the PistonStructureResolver via mixin, which redirects the resolver's startPos. Movement logic in RangedPistonUtil mirrors vanilla's PistonBaseBlock.moveBlocks() step for step. Block events ensure MovingPistonBlock entities are created on both client and server for proper sliding animation.

**Interactions:**
- Sympathetic Movement: Pushed/pulled Oddstone still triggers sympathetic chains.
- Piston Stickiness Framework: Moved blocks respect all existing stickiness rules (Machine Block groups, slime/honey, one-way rules).
- Oddstone Economy: All variants gated by Oddstone cost.

---

### Oddstone Portals [DONE]
> Address-based overworld-to-overworld teleportation using Oddstone frames with mineral block corners.

| Property | Value |
|----------|-------|
| Frame | Rectangular ring of Oddstone, min 5x5 outer (3x3 interior) |
| Corners | Crafted mineral blocks in all 4 corners define the "address" |
| Address space | 14 corner types ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¾Ãƒâ€šÃ‚Â¢-- 4 positions = 14^4 = 38,416 unique addresses per dimension |
| Activation | ALL Oddstone in frame must receive redstone signal simultaneously |
| Deactivation | Any frame Oddstone loses power, or any frame/corner block broken |
| Portal block | Light blue tinted nether portal texture, light level 11, no collision, indestructible |
| Travel cooldown | 4 seconds (vanilla portal cooldown) |

**Valid corner blocks:** Coal, Iron, Copper, Gold, Redstone, Lapis, Diamond, Emerald, Netherite, Quartz, Amethyst, Raw Iron, Raw Copper, Raw Gold (crafted *_block forms)

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Frame detection | [DONE] | Scans from interior outward (nether portal style). Works from any frame block. |
| Corner address system | [DONE] | Position-sensitive (TL/TR/BR/BL). Normalized so front/back produce same address. |
| Linked travel | [DONE] | Teleports to partner portal. Relative position mapped as 0.0--1.0 fraction, scales across different-sized portals. |
| Unlinked scatter teleport | [DONE] | Penalty: random 200--1000 blocks, surface landing, 5 hearts magic damage. Looks identical to linked portal. |
| Per-dimension registries | [DONE] | Overworld and Nether store separate address spaces. Uses SavedData, persists across saves. |
| Pair exclusivity | [DONE] | Third portal with same address refuses to light. |
| Redstone-gated activation | [DONE] | All Oddstone must be powered. Prevents partial-frame lighting. |
| Cross-axis rotation | [DONE] | Player yaw rotated 90 degrees when portals have different axes (X vs Z). Preserves "walking through" feeling. |
| Frame re-validation | [DONE] | Deactivation re-validates entire frame instead of reacting to single block state. Prevents false deactivation from unrelated neighbor updates. |

**Known issues:**
- No nether portal screen overlay (vanilla's portalTime hardcoded to nether; needs custom networking)
- No custom particles yet
- Texture is recolored nether portal via tint (could do custom animated .mcmeta)
- Stale saved data from testing can block addresses (clear `ideomod_oddstone_portals.dat`)

**Future ideas:**
- *[IDEA] Custom portal screen tint via network packet*
- *[IDEA] Custom animated portal texture*
- *[IDEA] Portal particles (light blue)*
- *[IDEA] Naturally generated portal structures (see Structures section)*

---

## 4. WORLDGEN & GEOLOGY

### Continental Terrain [IDEA]
> Large-scale terrain generation with elevation layers and realistic geography.

**Design notes:**
- Continental reshaping for more realistic landmasses
- Elevation layer system
- Custom river/lake generation
- Reference: ReTerraForged, TerraBlender

---

### Large Ore Veins [IDEA]
> Massive ore deposits that provide significant resources but attract danger.

**Design notes:**
- Much larger than vanilla ore veins -- finding one is a significant event.
- Tied to Chunky Geology / regional ore distribution (certain regions have certain large veins).
- Primary driver of the Mining Threat system -- large veins are the "honey pot" that makes the risk/reward loop work.
- Should feel like discovering a mine-able location, not just a slightly bigger ore blob.

**Interactions:**
- Mining Threat System: Sustained mining of a large vein ramps up danger.
- Inhibitor Block: Required for safe extended mining of large veins.
- Drill Block: Efficient vein extraction, but ramps threat faster.

---

### Chunky Geology [IDEA]
> Regional geological variation -- ore veins, rock strata, resource geography.

**Design notes:**
- Regional ore distribution (not uniform random)
- Custom rock types / strata layers
- Resource geography -- different regions have different resources

---

## 5. MOBS & AI

### Mob Difficulty Scaling [IDEA]
> Mobs get harder based on distance from spawn / world age / dimension.

---

### Ghost Mobs [IDEA]
> Mobs with phase mechanics -- can pass through blocks under certain conditions.

**Design notes:**
- Primary threat mob for the Mining Threat system -- spawns at high threat levels underground.
- Phase through blocks means standard defenses (walls, sealed rooms) don't work against them.
- Countered by Inhibitor Block's mob discouragement aura.
- Thematically: disturbing the earth awakens spirits.

---

### Whistler [IDEA]
> Non-hostile deep dark mob. When agitated, emits a loud sound that triggers distant sculk sensors -- potentially cascading into Warden spawns. Drops the Whistle Bone item.

| Property | Value |
|----------|-------|
| ID | `whistler` |
| Spawns | Deep dark biome |
| Hostility | Passive (non-hostile) until agitated |
| Drops | Whistle Bone (rare drop, see Items & Tools) |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Passive behavior | [IDEA] | Wanders deep dark. Ambient presence -- creepy but not directly threatening. |
| Agitation | [IDEA] | Triggered by being attacked. Possibly also by vibrations near it (player sprinting, breaking blocks, etc. -- same vibration events sculk sensors detect). Essentially a living sculk sensor that amplifies signals. |
| Whistle | [IDEA] | When agitated, emits a loud sound that activates sculk sensors at long range (50+ blocks?). Much further than normal vibration propagation. Can chain into shriekers and Warden spawns. |
| Whistle Bone drop | [IDEA] | Rare drop on death. See Items & Tools > Whistle Bone. |

**Design notes:**
- The core tension: Whistlers are valuable (Whistle Bone drop) but dangerous to engage. Attacking one risks a sculk cascade. Players must decide if the reward is worth the Warden risk.
- Forces the same sneaking behavior as sculk sensors -- you have to creep around them or accept the consequences.
- Non-hostile but mechanically dangerous is a rare and interesting mob archetype in Minecraft.

**Design questions (open):**
- *Visual design? Skeletal/bony (ties to Whistle Bone drop)? Eyeless (deep dark theme)? Warden-adjacent aesthetics?*
- *Does it flee after whistling, or stand its ground? Fleeing makes it harder to kill for the drop after you've already triggered the danger.*
- *How far does the whistle reach sculk sensors? 50 blocks would be dramatic. 100+ would be terrifying.*
- *Can it be silenced? (wool, sneaking up on it, one-hit kill before it reacts?)*
- *Multiple Whistlers in proximity -- do they chain-agitate each other? That could create truly dangerous deep dark pockets.*

---

## 6. MAGIC & ENCHANTING

### Disenchanting Table [IDEA]
> Removes enchantments from items. Can transfer to books or simply delete them.

| Property | Value |
|----------|-------|
| ID | `disenchanting_table` |
| Type | Block Entity with GUI |
| Recipe | Oddstone + Heavy Core (from Trial Chambers) + ? (TBD) |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Remove enchantment | [IDEA] | Strip enchantments from tools/armor/weapons. |
| Transfer to book | [IDEA] | If book provided, enchantment moves to book. If no book, enchantment is deleted. |
| GUI | [IDEA] | Input slot (enchanted item) + book slot (optional) + output slot(s). |

**Design notes:**
- Heavy Core from Trial Chambers gives that structure more endgame relevance.
- Oddstone cost ties it into the mod's core economy.
- One-time craft -- a permanent station, not a consumable.
- Pairs with Enchanting Overhaul plans.

---

### Brewery / Alchemy Station [IDEA]
> Multiblock structure that enhances brewing. Activates when a player uses a brewing stand that has the right nearby blocks. Unlocks expanded recipes, an additional ingredient slot, and the Potion Infusion system.

**Structure ruleset (Multiblock Framework):**

| Role | Block(s) | Notes |
|------|----------|-------|
| Core | Brewing Stand | Triggers scan when player interacts. |
| Required Contributor | Mortar & Pestle | Must be adjacent/nearby. Without this, structure doesn't form. |
| Required Contributor | Water-filled Cauldron | Must be nearby. |
| Optional Contributor | TBD | Additional blocks that improve infusion success, boost tier chances, etc. |
| Effector | Brewing Stand | GUI changes when structure is active. |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Structure detection | [IDEA] | Uses Multiblock Framework. Brewing stand GUI title updates to "Alchemy Station" (or similar) when structure is valid. |
| Extra ingredient slot | [IDEA] | Active structure adds an additional slot in the brewing GUI where components normally go. Enables recipes that require two ingredients per brew cycle. |
| Expanded recipes | [IDEA] | New potion recipes not available in vanilla brewing. Specific recipes TBD. Some may require the extra slot (two-ingredient brews). |
| Extended vanilla potions | [IDEA] | Allows upgrading past current vanilla potion tiers and durations. Costs more resources for each step beyond vanilla limits. |
| Modified vanilla recipes | [IDEA] | Some existing brewing recipes may be rebalanced or have alternate paths through the Alchemy Station. |

**Design notes:**
- The Brewery is a *place*, not a single block. You build a brewing corner in your base with the right components and it becomes more powerful. Very vanilla+ -- it's still a brewing stand, it just works better in context.
- The extra slot is the key mechanical addition. Two-ingredient brews open up a whole recipe design space without touching the vanilla brewing system.
- Contributor scaling from the Multiblock Framework drives quality: bare-minimum structure works but with limitations. Adding more/better contributor blocks improves outcomes (see Potion Infusion below).
- No Oddstone gate on the basic structure. Mortar & Pestle needs hardstone (copper-tier gate), which feels right for early-mid progression.

**Future ideas:**
- *[IDEA] What optional contributors improve the station? Bookshelves (knowledge)? Amethyst (arcane stability)? Candles (atmosphere/precision)?*
- *[IDEA] Cauldron variants -- lava cauldron for fire-themed potions? Powder snow cauldron for frost potions?*

---

### Potion Infusion [IDEA]
> Extension of the Brewery/Alchemy Station. Allows combining multiple potion effects into a single potion. Risk/reward system where structure quality determines success rate.

**Requires:** Active Brewery/Alchemy Station + an additional block or contributor (Infusion component TBD -- could be an Amethyst block, a custom Alembic block, or simply a higher contributor threshold in the structure).

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Potion as ingredient | [IDEA] | Place a potion in the extra ingredient slot (from Brewery). Brews its effects into the potions in the bottle slots. Result: one potion with multiple effects. |
| Risk of degradation | [IDEA] | Base chance that infusion reduces effect level or duration on one or more effects. E.g. combining Strength II + Speed II might yield Strength I + Speed II. |
| Risk of side effects | [IDEA] | Base chance of gaining a negative effect (Weakness, Slowness, Nausea, etc.) added to the potion alongside the desired effects. |
| Structure quality scaling | [IDEA] | Better Brewery structure (more/better contributors) reduces degradation and side effect chances. A fully built-out station can reliably produce clean multi-effect potions. |
| Bonus outcomes | [IDEA] | High-quality structures have a chance of bonus effects: bumped tier, extended duration, or an extra positive effect. |

**Design notes:**
- This is the endgame brewing mechanic. Multi-effect potions are very powerful (Strength + Speed + Fire Resistance in one bottle), so the risk system and structure investment keep it balanced.
- The progression: basic brewing (vanilla) -> Alchemy Station (expanded recipes, extra slot) -> Potion Infusion (multi-effect, structure-gated quality).
- The structure investment is the balance lever, not raw material cost. You don't need rare ingredients to infuse -- you need a well-built station. This rewards builders and base investment.
- Stacks with the potion stacking change (see Systems > Potion Stacking) -- multi-effect potions that stack to 4 are a genuine loadout option.

**Design questions (open):**
- *How many effects can be stacked into one potion? Cap at 2? 3? Unlimited but with escalating risk per additional effect?*
- *Can you infuse splash/lingering potions, or only drinkable?*
- *Does infusing an already-infused potion work? (Add a third effect to a two-effect potion?) Escalating risk per layer would be interesting.*

---

### Enchanting Overhaul [IDEA]
> Comprehensive rework of the vanilla enchanting system. New point-budget mechanic, catalyst slot, tiered resources, expanded enchantments, and removed restrictions.

**Enchantment Level Budget:**

Every piece of gear has a maximum "enchant level" -- a point budget. Each enchantment costs points equal to its level. The player decides how to spend the budget.

```
Example: Iron Chestplate -- max enchant level: 10

Option A: Protection V (5) + Thorns III (3) + Unbreaking II (2) = 10/10
Option B: Protection X (10) = 10/10
Option C: Protection II (2) + Fire Prot II (2) + Blast Prot II (2) + Unbreaking IV (4) = 10/10
```

- Replaces vanilla's "you get what you get" enchanting with deliberate choices.
- No enchantment incompatibilities -- any enchant can go on any appropriate gear, limited only by the budget and cost/rarity of high-level enchants.
- Enchantment levels can go beyond vanilla caps (Protection V+, Sharpness VI+, etc.) but higher levels cost proportionally more and are rarer.
- Different gear materials / tiers have different max enchant levels. Diamond > iron > stone, etc. Exact numbers TBD.

**Catalyst Slot:**

Addition to the Enhanced Enchanting Table structure (see Multiblock Framework). A new slot in the enchanting GUI for catalyst items.

| Feature | Status | Notes |
|---------|--------|-------|
| Catalyst slot | [IDEA] | New slot in enchanting GUI when structure is active. Accepts catalyst items. |
| Enchantment biasing | [IDEA] | Placing a catalyst makes certain enchantments more likely to appear in the enchanting options. Different catalysts bias toward different enchant families. |
| Catalyst items | [IDEA] | TBD -- could be existing items with thematic links (flint -> Sharpness family, feather -> movement enchants, blaze powder -> fire enchants) or new dedicated catalyst items. |

**Tiered Enchanting Resources:**

| Feature | Status | Notes |
|---------|--------|-------|
| Resource tiers | [IDEA] | Lapis is no longer the only enchanting fuel. Higher-tier enchanting (higher levels, rarer enchants) requires rarer resources. Specific tier list TBD -- could follow a progression like lapis -> amethyst -> Oddstone dust -> something else. |

**Anvil Changes:**

| Feature | Status | Notes |
|---------|--------|-------|
| Remove "too expensive" cap | [IDEA] | Anvil no longer refuses operations. Cost still scales, but the hard cap is gone. If you have the levels, you can do it. |

**Expanded Enchantments & Curses:**

| Feature | Status | Notes |
|---------|--------|-------|
| New enchantments | [IDEA] | Specific list TBD. Goal is to expand the pool so the catalyst system and budget system have more to work with. |
| New curses | [IDEA] | More curses to create risk in enchanting. Curses cost zero budget points but occupy space -- they're penalties, not choices. |

**Magic Bottles:**

Addition to the enchanting structure. A new contributor block or structure extension that enables enchanting non-gear items.

| Feature | Status | Notes |
|---------|--------|-------|
| Bottle enchanting | [IDEA] | Place a glass bottle in the enchanting slot. Structure converts it to a "Magic Bottle" -- an empty vessel that can capture environmental effects. |
| Environmental capture | [IDEA] | Right-click a Magic Bottle on certain blocks/effects to bottle them. Each source creates a different consumable potion-like item. |
| Bottled Smoke | [IDEA] | Capture: Magic Bottle + campfire smoke. Effect: when drunk, briefly blinds, slows, and damages. Throwable as splash variant? |
| Bottled Nether Portal | [IDEA] | Capture: Magic Bottle + nether portal block. Effect: when drunk, teleports the player to the Nether. One-way ticket. |
| More bottled effects | [IDEA] | TBD -- the system is open-ended. Bottled lava? Bottled sculk vibration? Bottled dragon breath already exists in vanilla -- this extends the concept. |

**Design notes:**
- The enchant level budget is the core change. It transforms enchanting from a slot machine into a build system. Players theory-craft loadouts, make tradeoffs, specialize gear.
- Catalysts + budget + tiered resources create three levers for progression: what you can get (catalysts), how much you can fit (budget), and what tier you can access (resources).
- Magic Bottles are deliberately weird and experimental. They turn the enchanting table from "gear enhancer" into "magical workstation." The environmental capture mechanic encourages exploration and experimentation.
- Removing the too-expensive cap is a QoL change that respects player investment. If someone has 80 levels, let them spend 80 levels.

**Design questions (open):**
- *What determines a gear piece's max enchant level? Material tier? Item type? Can it be increased (Arcane modifier -- see Crafting Modifiers)?*
- *Do curses reduce available budget, or do they take zero points but still occupy a "slot"?*
- *Catalyst consumption -- one per enchant? One per session? Durability-based?*
- *Magic Bottle capture -- instant or does it take time/resources?*

---

## 7. STRUCTURES

### Ancient Oddstone Portal Ruins [IDEA]
> Naturally generated structures containing lit or ruined Oddstone portals.

**Design notes:**
- Three discovery types: lit+linked (rarest), lit+unlinked, ruined (partially broken frame)
- Lore books hinting at addresses for other portals
- Ties into Oddstone Portal system (see Mechanical Systems -> Oddstone Portals)
- Discovery-driven gameplay -- exploration rewarded with portal network access

---

## 8. RECIPES & CRAFTING

### Multiblock Crafting Stations [IDEA]
> Advanced crafting through physical multiblock structures. Adds crafting modifiers, schematic support, and expanded crafting capabilities.

**Design notes:**
- Built on the Multiblock Structure Framework (see Systems > Multiblock Structure Framework)
- Likely a shapeless flood scan -- "is there an anvil nearby?" rather than requiring exact placement
- Vanilla workstations (anvil, smithing table, furnace, brewing stand) serve as Core blocks -- no custom blocks needed for basic structures
- Machine Core / Machine Block as contributors to boost output quality or speed

---

### Crafting Modifiers [IDEA]
> Terraria-style light modifiers applied to gear when crafted at a proper multiblock crafting station. Small bonuses that make crafted gear feel special.

**Core mechanic:** When crafting tools, weapons, or armor at an active multiblock crafting station, there's a chance the item receives a modifier -- a small, permanent bonus. Generally one modifier per item. Modifier chance and quality scale with the crafting station's contributor completeness.

**Example modifiers:**

| Modifier | Effect | Applies to |
|----------|--------|------------|
| Hardened | Small increase to armor protection | Armor |
| Tempered | Slight durability increase | Tools, weapons, armor |
| Keen | Small increase to attack damage | Weapons |
| Honed | Small increase to mining efficiency | Tools |
| Arcane | Adds 1-2 extra enchantment level budget to the item | Any enchantable gear |
| (more TBD) | | |

**Design notes:**
- Modifiers are "mini-enchants" -- smaller in impact than real enchantments. They're bonuses, not build-defining.
- The Arcane modifier is the standout -- extra enchant budget means more room for enchantments, making it the most sought-after modifier.
- Crafting at a basic workbench gives no modifier chance. You need a proper station. This rewards players who invest in their crafting setup.
- Modifier names appear as prefixes on the item: "Hardened Iron Chestplate", "Tempered Diamond Pickaxe", etc.
- One modifier per item. No stacking. Simple and readable.

**Design questions (open):**
- *What's the base chance? 10%? 25%? How much does a full station boost it?*
- *Can modifiers appear on non-gear items? (Probably not -- keep it focused on equipment.)*
- *Can you reroll a modifier somehow, or is it permanent? Permanent feels right -- it makes each craft meaningful.*

---

### Schematics & Drafting Table [IDEA]
> Schematic items that guarantee specific crafting outcomes. Used at a Drafting Table, which is a contributor block in crafting multiblock structures.

**Drafting Table:**

| Property | Value |
|----------|-------|
| ID | `drafting_table` |
| Type | Contributor block for crafting multiblock structures |
| Tool | Axe |
| Recipe | TBD (wood + paper + iron? Thematic for a work surface) |

When a Drafting Table is part of an active crafting station structure, it adds a schematic slot to the crafting GUI. It also serves as a standalone station for copying schematics.

**Modifier Schematics:**

| Feature | Status | Notes |
|---------|--------|-------|
| Guaranteed modifier | [IDEA] | Place a modifier schematic in the schematic slot when crafting. The output item is guaranteed to have that specific modifier instead of a random chance. |
| Limited uses | [IDEA] | Schematics have 2-3 uses before being consumed. Not infinite, not single-use. |
| Found as loot | [IDEA] | Schematics are found in dungeon chests, structure loot, etc. Not craftable (except by copying). Encourages exploration. |

**Crafting Schematics:**

| Feature | Status | Notes |
|---------|--------|-------|
| Unique recipes | [IDEA] | Special schematics that unlock crafting of items that are normally uncraftable. E.g. a Trident schematic lets you craft a trident from prismarine + other materials. |
| Schematic slot required | [IDEA] | Must be placed in the schematic slot of an active crafting station with Drafting Table. |
| Limited uses | [IDEA] | Same 2-3 use durability as modifier schematics. |
| Found as loot | [IDEA] | Rare loot from structures, bosses, etc. The schematic IS the reward -- finding a Trident schematic is like finding a trident, but renewable (2-3 times, plus copying). |

**Schematic Copying:**

| Feature | Status | Notes |
|---------|--------|-------|
| Copy at Drafting Table | [IDEA] | Right-click Drafting Table to open copying GUI. Place original schematic + blank schematic + Artisan's Ink. Produces a copy. |
| Artisan's Ink | [IDEA] | Rare crafting resource required for copying. Exact recipe/source TBD -- should be expensive enough that copying is an investment, not trivially repeatable. |
| Copy has full uses | [IDEA] | Copied schematic has the same 2-3 uses as the original. The original is not consumed by copying. |

**Design notes:**
- Schematics create a loot-to-crafting pipeline. You find a schematic, bring it home, and it becomes a limited crafting capability. Copying extends it but costs rare ink.
- The Drafting Table is cheap to build but the schematics and ink are the real gates. The table is infrastructure, not a bottleneck.
- Crafting schematics for normally uncraftable items (tridents, etc.) give those items a path to acquisition beyond luck, without making them trivially available.
- The whole system rewards exploration: find schematics in the world, bring them back to your workshop, use them carefully.

**Design questions (open):**
- *What items get crafting schematics? Trident is a clear candidate. What else -- elytra? Saddle? Enchanted golden apple? Each one needs a recipe that feels fair.*
- *Can you copy a copy? (Probably yes -- the ink cost is the limiter, not copy-generation.)*
- *Artisan's Ink source -- rare mob drop? Complex recipe? Bought from a specific villager type?*

---

### Current Recipes (All Placeholder)

| Output | Recipe | Notes |
|--------|--------|-------|
| Ideoblock | 9 cobblestone | |
| Drill | Iron + piston + redstone | Shaped |
| Oddstone | None (natural resource) | |
| Machine Block | 4 iron + 4 redstone + 1 slime | |
| Odd Slime | 1 slime block + 1 oddstone | Shapeless |

---

## 9. SYSTEMS & INFRASTRUCTURE

### Mining Threat System [IDEA]
> Phantom-style escalating danger system tied to underground mining activity.

**Concept:** The longer a player stays underground and the more ore they mine, the more hostile mobs (especially Ghost Mobs) are likely to spawn nearby. Creates risk/reward tension around large ore veins.

**Tracking (TBD -- brainstorm):**
- Combination of time spent below a Y threshold + ore blocks broken.
- Idle time underground ramps slowly; active mining ramps fast.
- Drill Block mining (3x3 = 9 blocks per pulse) should probably count as more aggressive than hand-mining.

**Escalation (TBD):**
- Low threat: Normal spawn rates.
- Medium threat: Increased hostile spawns in player vicinity.
- High threat: Ghost Mobs begin spawning (phase through walls, can't be walled out).

**Reset/decay (TBD):**
- Does NOT instantly reset (no popping to surface for 2 seconds).
- Slow decay over time? Decay faster on surface? Reset on sleep?
- Design goal: sustained mining requires the Inhibitor, not just surface trips.

**Interactions:**
- Inhibitor Block: Primary counter. Suppresses spawns and discourages mobs in radius.
- Oddstone: Existing mob pathfinding avoidance provides supplementary line defense.
- Ghost Mobs: Signature threat -- phase through blocks, so standard defenses (walls, doors) don't work.

**Design goal:** Large ore veins are a reward, but not a free lunch. Players must *earn* access through preparation and investment (Inhibitors, safe zone setup). Creates a natural progression from "grab and run" to "establish a mining operation."

---

### Oddstone Economy [IDEA]
> Oddstone as the mod's universal rare resource and decision-forcing bottleneck.

**Supply:**
- Rarer than diamond but spawns at most Y levels (wide vertical distribution).
- Players encounter it organically while doing other things, not just at diamond depth.
- Large veins possible? Or always small deposits to keep it precious?

**Demand (all Oddstone-gated systems):**

| System | Oddstone Cost | Notes |
|--------|--------------|-------|
| Oddstone Portals | High (full frame) | Most expensive single use. 5x5 minimum frame = significant investment. |
| Inhibitor Block | Medium (TBD) | Needed for safe mining. Multiple may be needed per vein. |
| Hollow Stone (-> Machine Blocks) | Indirect (catalyst) | Oddstone is not consumed -- lava sources touching Oddstone become Hollow Stone. Oddstone gates access but is not spent. High throughput if player has lava supply. |
| Machine Blocks | Via Hollow Stone | Crafted from Hollow Stone + iron + slime (TBD). Oddstone cost is indirect through Hollow Stone production. |
| Machine Specializations | Via vanilla items | Items inserted into Machine Blocks (redstone, hoppers, pistons, etc.) are retrievable. No Oddstone cost per specialization. |
| Placer Block | Medium (TBD) | Automation tool. |
| Oddstone Repulser/Attractor | Low-Medium (TBD) | Ranged Oddstone-only pistons. Oddstone + piston base. |
| Arcane Repulser/Attractor | Medium (TBD) | Universal ranged pistons. Oddstone + obsidian + piston base. |
| Disenchanting Table | Medium + Heavy Core | One-time craft. Ties into Trial Chambers. |

**Design principle:** Total demand should outpace supply enough to force meaningful choices, but not so much that players feel starved and frustrated. A rough cost table should be sketched out before committing to recipe numbers. Note that Machine Blocks use Oddstone as a catalyst (not consumed), so the bottleneck is finding Oddstone + having lava, not spending Oddstone. This makes Machine Blocks more accessible than portal frames while still requiring engagement with the Oddstone system.

---

### Potion Stacking [IDEA]
> Global change: all potions stack to 4.

| Property | Value |
|----------|-------|
| Stack size | 4 (up from 1) |
| Applies to | All potion types (drinkable, splash, lingering) |
| Implementation | Mixin or item property override on PotionItem |

**Design notes:**
- Vanilla potions are underused largely because they don't stack. Carrying 4 potions costs 4 inventory slots, which makes them a hard sell against food or equipment.
- Stacking to 4 makes potions a viable loadout choice without making them spammable. You can carry a meaningful supply without dedicating half your inventory.
- Global change, not gated behind the Brewery. This is pure QoL that benefits all players from the start.
- Synergizes strongly with Potion Infusion (see Magic & Enchanting) -- multi-effect potions that stack to 4 become a genuine alternative to enchanted gear for temporary boosts.

**Design questions (open):**
- *Does this affect tipped arrows? (Probably not -- they already stack to 64.)*
- *Splash and lingering too, or just drinkable? All three feels consistent.*

---

### Multiblock Structure Framework [WIP]
> Registration-based system for detecting, tracking, and evaluating multiblock structures. Structures are world data managed by a Structure Manager -- they are not stored on or owned by any individual block. Ordinary vanilla and modded blocks participate in structures without needing custom code. Phase 1 (framework) and Phase 2 (furnace array) are implemented and working.

**Core concept:** Structure definitions (rulesets) live in a central **Structure Manager** (world-level saved data, like the portal registry). When a designated trigger block (the "Core") is placed, the Manager scans the surrounding area using that structure type's ruleset. If valid, the structure is **registered** as an independent entry in the Manager. The structure then exists as world data -- no individual block "owns" it. Any block in the structure can be broken, replaced, or added, and the Manager rescans and updates the registration accordingly.

**Key consequence: blocks are just blocks.** A furnace doesn't know it's part of a machine frame. Stone bricks don't know they're forming a workshop wall. The Structure Manager knows -- it holds the ruleset that gives those blocks meaning in context. This means zero custom block code is needed for most structures. You build with vanilla blocks and the framework recognizes the patterns.

---

**Block Roles (assigned per-ruleset, not inherent to blocks):**

| Role | Scan behavior | Counts as component? | Example |
|------|--------------|---------------------|---------|
| Core | Trigger block. Placing it causes the Manager to scan. | Yes | Furnace, workstation, custom core block |
| Connector | Scan flows through it to find more blocks. | Yes | Stone frame, obsidian bridge |
| Contributor | Adds values to the structure's totals. | Yes | Machine Core (speed bonus), bookshelf (enchant power), bed (rest) |
| Effector | Where the structure's effects manifest. | Yes | The furnace being boosted, the villager's assigned station |
| Ignored | Scan flows through but doesn't count as a component. | No | Air, light blocks |

Anything not in the ruleset: scan does not flow through it, doesn't count. Natural termination -- no explicit "wall" role needed.

**A single block can fill multiple roles.** A furnace can be both Core (triggers the scan) and Effector (receives the speed boost). A Machine Core block can be a Contributor (adds speed) in one structure type while being a Core in a different structure type.

---

**Structure Lifecycle:**

```
FORMATION
  1. Player places a Core block (or triggers an existing one).
  2. Structure Manager detects the placement and checks: "Does any
     registered structure type use this block as a Core?"
  3. If yes, Manager runs the scanner with that structure type's ruleset.
  4. Scanner flood-fills outward from the Core, classifying blocks by role.
  5. If the result meets validation rules -> structure is REGISTERED.
     Gets an ID, stored in the Manager's saved data. Persists across saves.

UPDATES
  6. When any block within a registered structure's area is placed/broken,
     the Manager is notified (via Forge block change events).
  7. Manager rescans the affected structure with its ruleset.
  8. If still valid -> update the registration (new component counts, etc.).
  9. If now invalid -> DEREGISTER the structure. Effects stop.

CORE LOSS
  10. If the Core block that originally triggered formation is broken:
      - Manager checks: is there another valid Core of the same type
        still in the structure? If yes -> that one becomes the new
        anchor. Structure persists.
      - If no Core remains -> structure DEREGISTERS.

DEREGISTRATION
  11. Structure entry removed from Manager. Effects stop. All blocks
      revert to being normal blocks. No cleanup needed on the blocks
      themselves since they never stored structure data.
```

**The structure outlives any individual block** (except when no valid Core remains). Breaking and replacing a block causes a rescan, not a full teardown-rebuild. This is critical for the villager workshop case -- replacing a workstation doesn't destroy the room.

---

**Scanning -- How It Works:**

**Flood scan (freeform/shapeless structures):**

1. Start at Core position. Add to "visited" set.
2. Check all 6 adjacent blocks.
3. For each: ask the ruleset "what is this block?"
   - If in the ruleset as CONNECTOR or IGNORED: queue its neighbors for scanning (scan flows through).
   - If in the ruleset as CONTRIBUTOR or EFFECTOR: add to results.
   - If not in the ruleset at all: do nothing. Don't queue neighbors. Scan stops here naturally.
4. Repeat until queue is empty, max distance reached, or max component count reached.
5. Run validation rules. Return Structure Result.

**Pattern scan (shaped structures):**

1. Start at Core position.
2. Ruleset provides a 3D template of expected blocks at specific offsets.
3. Check each position. Positions can accept multiple block types.
4. Track global constraints ("at least 1 of X, at most 2 of Y").
5. Supports rotation (check all 4 horizontal orientations).
6. Return Structure Result.

**Hybrid:** Pattern scan first (is the frame valid?), then flood scan from inside (what's inside the frame?).

---

**Per-Contributor Spatial Validation:**

Some contributors need more than just "are you present?" -- they need "are you placed correctly?" The ruleset can attach a **spatial validator** to specific contributor types.

Example: In the furnace array, furnaces must have clear faces (air adjacent on functional sides). The scanner finds a furnace, then runs its spatial validator: "Does this furnace have at least one air block on a non-frame face?" If yes -> valid contributor. If no -> found but flagged as invalid.

This enables player feedback like: "Furnaces: 3 valid, 1 blocked" -- tells the player exactly what to fix.

---

**Structure Manager (world-level saved data):**

| Responsibility | How |
|---------------|-----|
| Stores all registered structures | SavedData (like OddstonePortalSavedData). Persists across saves/loads. |
| Holds all structure type rulesets | Loaded at mod init. Each structure type is a named ruleset. |
| Listens for Core block placement | Forge block place event -> check if placed block is a Core for any ruleset -> scan. |
| Listens for block changes near structures | Forge neighbor/block change events -> check if position is within any registered structure's area -> rescan that structure. |
| Provides structure data to game systems | Other systems query the Manager: "Is there a valid workshop structure at this location?" "What are its contributor totals?" |

**Performance:** Scans only run on block placement/breaking events, not per-tick. Each registered structure stores a bounding box -- block changes outside all bounding boxes are ignored instantly. Bounding box check is very cheap.

---

**Ruleset Parameters:**

| Parameter | Purpose |
|-----------|---------|
| Structure type name | Unique identifier ("machine_furnace_array", "villager_workshop") |
| Core block(s) | Which block types trigger a scan when placed. Can be multiple types. |
| Scan type | FLOOD / PATTERN / HYBRID |
| Max scan distance | Hard cap on how far from Core the scan can reach |
| Max components | Cap on total counted blocks (performance + balance) |
| Block classification | Map of block type -> role (connector/contributor/effector/ignored) |
| Contribution values | Map of contributor types -> what values they add |
| Spatial validators | Optional per-contributor placement checks |
| Validation rules | Minimum/maximum requirements for the structure to be valid |
| Multi-core allowed | Can the structure contain multiple Core blocks? |
| Effect definition | What the structure does when valid (custom logic per structure type) |

---

**Example Structures:**

**Machine Furnace Array [DONE]:**

| Property | Value |
|----------|-------|
| Core block | Furnace, Blast Furnace, Smoker (all vanilla furnace types) |
| Frame | #ideomod:furnace_array_frame tag (18 stone-family blocks) |
| Max cluster | 3 wide x 2 tall (6 furnaces) |
| Effectors | The furnaces themselves |
| Effect | GUI renamed to "Enhanced Furnace/Blast Furnace/Smoker" + 50% cooking speed boost |

Build: Place furnaces facing the same direction. Surround back, sides, top, and bottom with stone-family frame blocks. Front faces must have air (for item access). Structure auto-detects on any relevant block placement.

Implementation notes:
- Scanner normalizes core position to clusterMin for consistent exclusivity keying
- Frame bounds normalized with Math.min/max to handle all facing directions
- Speed boost via AbstractFurnaceBlockEntityMixin: +1 extra progress on even ticks, clamped to totalTime - 1 (vanilla uses equality check for completion)
- Core detection uses block entity iteration from nearby chunks (cheap, no chunk scanning)

**Villager Workshop:**

| Property | Value |
|----------|-------|
| Core block | Workstation (smithing table, loom, brewing stand, etc.) |
| Connectors | Any solid full block (walls/floor/ceiling material) |
| Contributors | Bed (+rest, required), chest (+storage), composter or food container (+food), door/window (+skyAccess) |
| Effectors | The workstation (villager assignment point) |
| Ignored | Air (living/working space) |
| Spatial validation | Bed must not be exposed to sky (has solid block above it). Structure must have minimum interior air volume (walking space). At least one block with clear path to sky (window/door). |
| Effect | Villager assigned to this workshop gains willingness to trade. Trade quality scales with contributor completeness. Missing components -> missing features (no bed = no overnight restock, no food = no breeding, no sky = mood penalty/worse prices). |

Build: Build a room. Add a workstation, bed, chest, and food source. Make sure there's a window or door to the sky and enough walking space. Villager claims the workshop and starts trading. Better room = better trades.

**Enhanced Enchanting Table:**

| Property | Value |
|----------|-------|
| Core block | Enchanting table |
| Connectors | (none -- air is ignored, so scan flows freely within range) |
| Contributors | Bookshelf (enchantPower: 1), amethyst cluster (luck: 1), candle (stability: 1) |
| Effectors | Enchanting table (gains enhanced options) |
| Ignored | Air |
| Spatial validation | (none beyond standard range) |
| Max distance | 3 |
| Effect | enchantPower works like vanilla (caps at 15). Luck adds chance for better enchantments. Stability reduces "too expensive" penalties. |

---

**Brewery / Alchemy Station:**

| Property | Value |
|----------|-------|
| Core block | Brewing Stand |
| Connectors | Any solid block (room walls/floor) |
| Contributors | Mortar & Pestle (required -- enables structure), Water-filled Cauldron (required), plus optional contributors TBD (bookshelves, amethyst, candles, etc.) |
| Effectors | Brewing Stand (gains extra slot, expanded recipes, infusion capability) |
| Ignored | Air |
| Spatial validation | Mortar & Pestle and Cauldron must be within scan range of brewing stand |
| Effect | Brewing stand GUI updates title, gains additional ingredient slot. Expanded recipe list. Contributor quality scales Potion Infusion success rate. |

Build: Place a brewing stand. Add a mortar & pestle and water-filled cauldron nearby. The brewing stand becomes an Alchemy Station with extra capabilities. Add more contributor blocks to improve Potion Infusion outcomes.

---

**Player Feedback:**

- Structure formation/deformation: sound effect + particle burst on Core. [IDEA]
- Comparator output from Core: signal strength proportional to structure completeness (0 = invalid, 1-15 = scaled). [IDEA]
- Structure Debug Stick: hold in either hand to see color-coded chat messages on register/deregister. Shows structure type, core position, core count. [DONE]
- *[IDEA] Visual debug: hold a specific item and look at a Core to see colored outlines on all recognized structure blocks.*
- *[IDEA] GUI panel on Core right-click showing structure stats: "Furnaces: 3, Machine Cores: 2, Speed Bonus: 2.0x"*
- Villager workshop: villager behavior IS the feedback. They trade or they don't. Prices get better or worse. Observable without any special UI.

---

**Interactions with existing systems:**

| System | Interaction |
|--------|------------|
| Piston movement | Moving a component block triggers rescan. Structures are not mobile -- moving blocks updates or breaks the registration. |
| Sympathetic movement | If Oddstone is a component and gets sympathetically moved, trigger rescan on affected structures. |
| Oddstone Economy | Advanced structure types (machine arrays, arcane workshops) could require Oddstone components. |
| Redstone / Machine Blocks | Machine Blocks as contributors in machine-type structures. Machine Core block sends wireless redstone to Machine Blocks in radius AND participates as contributor in structures. |
| Villager AI | Villager assigned to a registered workshop structure. AI queries the Manager for structure validity and contributor totals. |

---

**Implementation phasing:**

| Phase | What | Status |
|-------|------|--------|
| 1 | Structure Manager saved data + Scanner utility + Structure Result data class + Effect handler interface | [DONE] |
| 2 | First structure: Machine Furnace Array (simplest useful case, uses existing blocks) | [DONE] |
| 3 | Villager Workshop structure (tests spatial validation, complex contributor rules) | [IDEA] |
| 4 | Pattern scan mode + a shaped structure to test it | [IDEA] |
| 5 | Polish: comparator output, particles, sounds, GUI panels, visual debug mode | [IDEA] |
| 6 | Data-driven: refactor rulesets from Java to JSON for moddability | [IDEA] |

---

**Open questions:**
- *How does the Manager handle chunk boundaries? If a structure spans two chunks and one unloads, structure should probably stay registered but flag as "partially unloaded" and skip effect application until fully loaded.* (Current implementation: structures stay registered across chunk boundaries. Effect application checks block existence at runtime. No partial-unload tracking yet.)
- *Should there be a creative/debug tool for visualizing registered structures?* **Partially resolved**: Structure Debug Stick shows register/deregister chat messages. Visual block highlighting (colored outlines on structure blocks) still TBD for Phase 5.
- *Villager workshop: how does villager "assignment" work? Does the villager claim the nearest valid workshop, or does the player assign them? Vanilla uses proximity -- extending that feels more vanilla+.*

---

### Config System [DONE]
> JSON-based config for Oddstone (mob list, sympathetic radius).

| File | Location |
|------|----------|
| `oddstone.json` | `config/ideomod/oddstone.json` |

**Future:** May need a general config system as more features are added.

---

### Mixin Infrastructure [DONE]
> SpongePowered Mixin setup for modifying vanilla internals.

| File | Purpose |
|------|---------|
| `ideomod.mixins.json` | Mixin config |
| `PistonStructureResolverMixin.java` | 4 features: one-way stickiness, directional stickiness (Odd Slime), sympathetic movement, ranged piston startPos redirect |
| `AbstractFurnaceBlockEntityMixin.java` | Furnace array speed boost (50% cooking speed when part of registered structure) |

---

### Registry Pattern [DONE]
> DeferredRegister for blocks, items, creative tabs.

| Registry | File |
|----------|------|
| Blocks | `ModBlocks.java` |
| Items | `ModItems.java` |
| Creative Tabs | `ModCreativeTabs.java` |

---

## 10. BRAINSTORM BIN

*Unorganized ideas. Move to proper sections when they take shape.*

- 
- 
- 

---

## DESIGN PRINCIPLES

1. **Vanilla+** -- Enhance, don't replace. New features should feel like natural extensions of Minecraft.
2. **Interconnected systems** -- Features should interact with each other and with vanilla mechanics.
3. **Emergent gameplay** -- Design for consequences and combinations, not just individual features.
4. **Geological realism** -- The world should feel like it has underlying rules governing its geography and resources.
5. **Discovery-driven** -- Players should find and figure things out, not just follow recipes.

---

*This is a living document. Add ideas freely -- they'll get organized as they develop.*
