# IDEOMOD DESIGN DOCUMENT
### A Vanilla+ Minecraft Overhaul
<!-- NOTE: ASCII-only formatting. No emoji, no Unicode arrows/dashes/quotes. Use [DONE] [WIP] [DESIGNED] [IDEA] [HOLD] for status tags, -> for arrows, -- for dashes, x for multiplication. -->
*Last updated: 2026-03-17a (Machine Brace specialization implemented. Dynamic push limit via @ModifyConstant. Lazy swap mapping for mod-item triggers.)*

*Written by Claude, directed and guided by The Ideomancer.*
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
10. [Lore & World Design](#10-lore--world-design)
11. [Brainstorm Bin](#11-brainstorm-bin)

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
| Recipe | None -- natural resource only. Must be mined. **NOTE:** A crafting recipe (Stone corners + Redstone center = 4x Oddstone) exists in `data/ideomod/recipes/oddstone.json` as an auto-generated placeholder from early development. This recipe should be removed -- Oddstone is intended as a natural-only resource. |
| Mining | Drops itself (raw Oddstone) by default. Silk Touch drops Cobbled Oddstone. Inverse of vanilla stone (stone -> cobble, Oddstone -> Oddstone raw, silk touch flips). |
| Smelting | Oddstone in furnace -> Cobbled Oddstone. Another inversion of stone (stone cooks from cobble; Oddstone cooks INTO cobble). |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Mob avoidance | [DONE] | `isPathfindable=false` + Forge `DAMAGE_OTHER` hook. JSON config for per-mob control. |
| Redstone insulation | [DONE] | `isRedstoneConductor=false` -- acts like glass for redstone. |
| Sympathetic movement | [DONE] | Wireless slime -- each face searches for nearest Oddstone within radius (default 5). BFS chaining. Push chains count toward 12-block limit. |
| One-way stickiness | [DONE] | Oddstone grabs slime; slime does NOT grab Oddstone. Via mixin + StickinessRules. |
| Sticky piston immunity | [DONE] | Sticky pistons cannot directly pull Oddstone. Implemented via PistonBaseBlockMixin position-specific ThreadLocal check -- during retraction, only the block at the exact pull position is checked. Oddstone dragged by its own stickiness (e.g., stuck to slime being pulled) still moves correctly. |

**Known issues:**
- Face search is axis-aligned only (no cone/hemisphere)

**Future ideas:**
- *[IDEA] Expand face search to half-sphere/cone per face*
- *[IDEA] Oddstone as universal mod economy resource -- gates Portals, Inhibitors, Machine Blocks (via Hollow Stone), Placers, Disenchanting Table (see Systems > Oddstone Economy)*

**Interactions:**
- Lava source blocks adjacent to Oddstone transform into Hollow Stone (Oddstone is NOT consumed). See Hollow Stone entry.

---

### Oddstone Variants [IDEA]
> Full stone-family variants of Oddstone (cobbled, polished, bricks, chiseled, stairs, slabs, walls, etc.). All retain Oddstone behavior but each variant is its own sympathetic frequency. Also serve as wireless channel selectors for Machine Linker (see Machine Linker specialization).

**Entry point:** Cobbled Oddstone is obtained by smelting raw Oddstone in a furnace or mining with Silk Touch. This is the inverse of vanilla stone's relationship with cobblestone. From Cobbled Oddstone, standard stonecutter/crafting recipes produce all other variants.

**Variants (planned):**
- Cobbled Oddstone (base processed form -- entry to all other variants)
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

### Machine Block [DONE]
> Modular mechanical chassis. Every Machine Family block shares a single-slot block entity (MachineFrameBlockEntity). Insert an item into the slot to swap the block to a specialization. Remove the item to revert to Machine Frame. Each specialization is a unique registered block. Now called "Machine Frame" in code (MachineFrameBlock), registry ID stays `machine_block` for world compatibility.

| Property | Value |
|----------|-------|
| ID | `machine_block` |
| Class | MachineFrameBlock (extends AbstractMachineBlock) |
| Hardness | 5.0 / Blast 6.0 |
| Tool | Pickaxe |
| Type | Block Entity (single item slot, shared by all specializations) |
| Placement | Non-directional (base form) |
| Redstone | Accepts redstone signal. Only Machine Frames emit anti-signal to suppress frame activation. |
| Recipe | 4 iron + 4 redstone + 1 slime (placeholder) |

**Specializations (insert item to transform):**

| Item Inserted | Specialization | Block Class | MachineType | Status |
|---------------|----------------|-------------|-------------|--------|
| Powered Rail | Track | MachineTrackBlock | TRACK | [DONE] |
| Minecart | Trolley | MachineTrolleyBlock | TROLLEY | [DONE] |
| Redstone dust | Conductor | MachineConductorBlock | CONDUCTOR | [WIP] |
| Hopper | Pipe | MachinePipeBlock | PIPE | [DONE] |
| Bucket | Fluid Pipe | MachineFluidPipeBlock | FLUID_PIPE | [DONE] |
| Machine Frame (item) | Brace | MachineBraceBlock | BRACE | [DONE] |
| Comparator | Repeater | TBD | REPEATER | [IDEA] |
| Clock (item TBD) | Clock | TBD | CLOCK | [IDEA] |
| Redstone torch | Pulser | TBD | PULSER | [IDEA] |
| Lever | Toggle | TBD | TOGGLE | [IDEA] |
| Piston | Machine Piston | MachinePistonBlock | PISTON | [DONE] |
| Sticky Piston | Machine Sticky Piston | MachineStickyPistonBlock | STICKY_PISTON | [DONE] |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Machine Family stickiness | [DONE] | MachineType-aware. Machine Frame (FRAME) sticks to ALL machine types. Specializations stick to same-type + FRAME only. All types stick to Frame Family (active) and are permissive to slime/honey/odd_slime. |
| Anti-signal emission | [DONE] | Only Machine Frames emit anti-signal. All other Machine Family blocks always emit activation. |
| Frame network activation | [DONE] | All Machine Family blocks emit activation signal (strength 15). Machine Frames only when unpowered; specializations always. |
| Item slot + GUI | [DONE] | All machine blocks share MachineFrameBlockEntity. Right-click opens GUI. Insert item to specialize, remove to de-specialize. Any item accepted; only mapped items trigger swap. |
| Block swap system | [DONE] | MachineSwapHelper central utility. PositionBoundContainer keeps GUI open across swaps. Client sync via syncToClients() after swap. |
| Drop preservation | [DONE] | getDrops() adds BlockEntityTag only when slot has content. Empty blocks drop tagless. |
| Piston data preservation | [DONE] | All specializations implement IPistonMovable via shared block entity. |
| AbstractMachineBlock | [DONE] | Shared base class for all Machine Family blocks. Default lifecycle, GUI, drops, stickiness. MachineFrameBlock/MachineTrolleyBlock ~20 lines each. |
| Unified dynamic GUI | [DONE] | All Machine Family blocks share one menu type (MACHINE_FRAME), one screen, one PositionBoundContainer. GUI renders from ideomod_gui.png spritesheet. Transit slot dynamically enabled for pipes. Fixed 151px height. |
| Pipe subclass BE | [DONE] | MachinePipeBlockEntity extends MachineFrameBlockEntity with its own BlockEntityType. Transit slot, cooldown, round-robin, input face tracking. IItemHandler capability. |
| Fluid Pipe subclass BE | [DONE] | MachineFluidPipeBlockEntity extends MachineFrameBlockEntity with its own BlockEntityType. Fluid type tracking, cooldown, round-robin, input face tracking. IFluidHandler capability. Biome water tint via ModColorHandlers. Lava light emission (level 10). |
| Piston extension/retraction | [DONE] | Omnidirectional. BESR animation. Head placed on extension completion, removed on retraction start. |
| Piston pushing | [DONE] | Via BlockMoveHelper. 12-block base limit (extendable by Machine Brace). Full sticky group. Sound deduplication. |
| Sticky pull | [DONE] | Removes head, pulls block behind head via BlockMoveHelper. |
| Piston immovability | [DONE] | PistonBaseBlockMixin Feature 5. Extended pistons cannot be pushed. |
| Piston head block | [DONE] | MachinePistonHeadBlock. 8x16 shape. FACING+STICKY. Parent survival check. No drops. |
| BESR arm animation | [DONE] | MachinePistonArmRenderer. Per-face interpolated animation. 2-tick duration. Transient state in MachineFrameBlockEntity. |
| Brace push limit increase | [DONE] | Each Machine Brace in a push group adds +6 to the push limit (base 12), up to 60. Cascading: limit grows as Braces are discovered. Implemented via @ModifyConstant in PistonStructureResolverMixin. |
| Lazy swap mapping | [DONE] | Specializations using mod items as triggers (Brace uses Machine Frame item) register lazily via ensureBraceMapping() to avoid DeferredRegister timing issues. |

---

### Machine Specializations [WIP]
> Functional variants created by inserting an item into any Machine Family block's GUI slot. Each specialization is a unique registered block. Most share MachineFrameBlockEntity; specializations with extra state (like Pipe and Fluid Pipe) use their own BE subclass with a separate BlockEntityType. Track, Trolley, Pipe, Fluid Pipe, Piston, and Sticky Piston are implemented; remaining specializations are designed but not yet built.

**Stickiness model:** Each specialization has a MachineType. Specializations only stick to same-type blocks + Machine Frame (MachineType.FRAME, universal bridge) + Frame Family (when active). Different specialization types do NOT stick to each other without a Machine Frame bridging them. This is handled by canStickTo (Layer 1) via MachineType.canStickToMachineType(), NOT by grab immunity rules.

**Machine Conductor:** [WIP]
Solid-block redstone wire. Carries vanilla redstone signal (0-15, decays by 1) through a full block. Connects to vanilla redstone on all 6 faces. Input/output face model: receives on one face, outputs on other 5. Sources (levers) = no decay, wire (dust/conductors) = -1 decay. Strong power output (getDirectSignal) matches vanilla dust. Known issues: dust sees strong power through solid blocks (needs RedStoneWireBlock mixin). ~~Base texture renders black~~ (FIXED 2026-03-14, cutout render type).

**Machine Pipe:** [DONE]
Push-only item transport. Hopper specialization item. Own BE subclass (MachinePipeBlockEntity) with transit slot (1 stack, slot 1), transfer cooldown (8 ticks), round-robin output index, and input face tracking. FILLED BooleanProperty drives texture swap. No pulling -- items enter via player hand, hopper, or pipe-to-pipe push. Redstone locks the pipe (powered = frozen). Output priority relative to input face: forward, right, left, up, down. Round-robin resets on input face change. Pipe-to-pipe push notifies receiver of input face. Piston-safe: movedByPiston guard in onRemove, minimum cooldown after landing. Comparator reads transit slot fullness. ThreadLocal pendingInputFace in WorldlyContainer bridges face parameter to setItem for automation input tracking.

**Machine Fluid Pipe:** [DONE]
Fluid vacuum and depositor. Bucket specialization item. Own BE subclass (MachineFluidPipeBlockEntity) with fluid type tracking (FluidType enum: EMPTY/WATER/LAVA), transfer cooldown (8 ticks), round-robin output index, and input face tracking. FLUID_TYPE EnumProperty drives inner cuboid texture via blockstate variants. Redstone behavior is OPPOSITE of item Pipe: powered = actively pulling (pump metaphor), unpowered = idle (still auto-pushes if holding fluid). Pull scans all 6 faces for water/lava source blocks (only source blocks -- flowing ignored, infinite water sources survive) and full cauldrons. Push sends fluid to adjacent empty Fluid Pipes via round-robin, or deposits into world (air -> source block, cauldron -> fills completely per vanilla bucket behavior). Water+lava collision: receiving pipe's bucket replaced with obsidian -> MachineSwapHelper auto-reverts to Machine Frame (no special case code). Bucket item in slot 0 swaps to match fluid state (water_bucket/lava_bucket/bucket) via setItemDirect(). No transit slot -- fluid state is BE data, not a slot item. IFluidHandler capability for mod interop (1000mB = 1 bucket). Comparator output: 0=empty, 15=has fluid. Lava light emission: level 10 (reduced from 15 for small window). Dual-cuboid block model with cutout render type: outer texture has 4x4 transparent window, inner cuboid shows dark grey (empty), animated water_still with biome tint (water), or animated lava_still (lava). Water tint via ModColorHandlers block color handler (tintIndex 0 -> BiomeColors.getAverageWaterColor).

**Machine Track:** [DONE]
Pushes adjacent Machine Trolleys one block in facing direction per redstone pulse. Internal POWER blockstate (0-15) for Track-to-Track chaining only -- isolated from vanilla redstone system (isSignalSource=false, no signal output to non-Track blocks). Track does NOT stick to Trolley (different MachineTypes) -- Machine Frame bridges them.

**Machine Repeater:**
Three-axis redstone repeater. Receives signal on any face, outputs on the opposite. No locking (removed -- timing/strength disambiguation broken for three-axis; players who need locking use vanilla repeaters).

**Machine Clock:**
Emits a configurable redstone pulse at a set interval. Right-click to cycle intervals.

**Machine Pulser:**
Emits a single redstone pulse on placement or activation. One-shot signal source.

**Machine Toggle:**
Flip-flop. First activation = ON, second = OFF. Maintains state.

**Machine Piston / Machine Sticky Piston:** [DONE]
Omnidirectional pistons as Machine Family members. No FACING property on the base block — signal on any face extends a head from the opposite face. Multiple heads can extend simultaneously. Pushes blocks via BlockMoveHelper (12-block limit, full sticky group resolution). Immovable while any head is extended (PistonBaseBlockMixin Feature 5). Head block (MachinePistonHeadBlock) is a real block in the world with 8×8×16 collision shape, FACING + STICKY properties. Breaking the head causes the piston to recognize that face is free (vanilla-like behavior — if signal is still present, re-extends immediately).

Animation uses a custom BESR system (MachinePistonArmRenderer) with per-face animation state in MachineFrameBlockEntity, NOT vanilla's MovingPistonBlock system (which has hardcoded assumptions about vanilla piston blocks). Extension: arm slides outward from piston face over 2 ticks, then real head block placed. Retraction: head removed immediately, arm slides back over 2 ticks (purely visual).

Sticky variant pulls the block in front of the head on retraction via BlockMoveHelper. The pulled block gets vanilla MovingPistonBlock animation (from BlockMoveHelper).

---

### Machine Trolley [DONE]
> Passive Machine Family cargo carrier. Pushed by Machine Tracks.

| Property | Value |
|----------|-------|
| ID | `machine_trolley` |
| Class | MachineTrolleyBlock |
| MachineType | TROLLEY |
| Placement | Non-directional |
| Activation | None (passive) |
| Movable | Yes (pushed by Tracks, pistons) |
| Redstone | None (does not conduct) |
| Recipe | TBD |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Cargo carrying | [DONE] | Carries anything stuck to it via Machine Family or slime stickiness. |
| Machine Family stickiness | [DONE] | MachineType.TROLLEY. Sticks to other Trolleys + Machine Frames + Frame Family (active). |
| Block entity + GUI | [DONE] | Shares MachineFrameBlockEntity. Right-click opens GUI with minecart in slot. |
| Frame activation | [DONE] | Always emits activation (strength 15). Never suppresses. |

**Design notes:**
- No active behavior. Pure cargo block.
- Created by inserting a Minecart into a Machine Frame's GUI slot.

---

### Framed Blocks [IMPLEMENTED]
> Any block wrapped in an iron or copper frame. Extends Machine Block stickiness as cheap "body" pieces.

| Property | Value |
|----------|-------|
| ID | `iron_framed_block`, `copper_framed_block` |
| Type | Block Entity (stores inner block ID as ResourceLocation) |
| Hardness | Dynamic: inner block hardness + frame material modifier (iron: +0.75, copper: +0.5) |
| Tool | Pickaxe (always). Harvest tier from inner block's tier tags. |
| Drops | Framed block item with inner block NBT preserved. No decrafting. |

**Model approach:**
Two overlapping 16x16x16 cuboids in the same block space:
- Inner cuboid: dynamically uses the texture of whatever block is stored inside. Cannot be a static JSON reference -- must be fetched at render time.
- Outer cuboid: static JSON model using the frame texture (iron or copper). The frame texture is a PNG that is mostly transparent, with opaque pixels only on the edges -- like a picture frame. This cuboid renders on top, its edge pixels overlaying the inner texture.

The outer cuboid IS a normal static JSON model. The inner cuboid requires a BlockEntityRenderer (BESR) -- Java code that runs each render frame, reads the stored block ID from the block entity, looks up that block's texture in Minecraft's texture atlas, and draws it onto the inner cuboid's faces. This is why a pure JSON model cannot achieve the "any block" behavior: JSON texture references are fixed at load time, not per-instance at runtime.

Reference: FramedBlocks mod uses this same pattern (block entity + BESR for dynamic inner texture).

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Inner block storage | [DONE] | Block entity stores ResourceLocation of the wrapped block. |
| Outer frame (static) | [DONE] | Normal JSON model. Frame texture has opaque edge pixels only -- transparent interior so inner texture shows through. 2 frame textures total (iron, copper). noOcclusion() prevents neighbor face culling. |
| Inner texture (dynamic) | [DONE] | BESR uses BlockRenderDispatcher.renderSingleBlock() at 0.998 scale. Handles multi-element models (slime), translucency (glass), tinting (grass), directional textures. |
| FACING property | [DONE] | DirectionProperty set from player look direction. Transferred to inner block orientation during rendering (DirectionalBlock.FACING, HorizontalDirectionalBlock.FACING, RotatedPillarBlock.AXIS). |
| INNER_STICKY property | [DONE] | IntegerProperty 0-4 (none/slime/honey/odd_slime/oddstone). Synced from inner block type by FramedBlockEntity.updateBlockstateFromInner(). |
| Piston transit rendering | [DONE] | Client-side mixin renders inner block during 2-tick piston animation. Brightest-of-neighbors light sampling blended by progress. FrameTransitInfo carries facing + innerSticky. |
| Item/inventory rendering | [DONE] | Custom BakedModel (FramedBlockItemModel) reads inner block from NBT, composites inner + frame quads. Items show correct inner texture. Translucent inners render opaque (cosmetic limitation). |
| Machine-activated stickiness | [DONE] | Uses Frame Activation system (redstone-style batched wipe-and-rebuild (markDirty + end-of-tick processing)). BooleanProperty ACTIVE set by FrameActivation.markDirty(). Implements IFrameBlock interface. isStickyBlock() ACTIVE-gated for frame stickiness. |
| Inner block stickiness | [DONE] | INNER_STICKY-gated. Framed Slime grabs like real slime, Framed Honey like honey, Framed Odd Slime like odd slime (directional), Framed Oddstone grabs all slime types + oddstone (universal grabber). Grab immunity Rules 1 and 4 have carve-outs for matching inner types and oddstone universal grabber. |
| Material exclusivity | [DONE] | Iron framed sticks to iron framed only. Copper to copper only. Neither type sticks to the other. Both types stick to Machine Blocks when activated. |
| Base behavior preservation | [DONE] | Framed blocks retain the functional behavior of their inner block. A Framed Redstone Block still conducts redstone. A Framed Slime Block is still sticky like slime. A Framed Oddstone retains mob avoidance and sympathetic movement. The frame adds Machine-activated stickiness ON TOP of whatever the inner block already does -- augmentation, not replacement. |

**Stickiness matrix (when activated by Machine Block):**

Frame-only stickiness (no inner sticky block):
| | Machine Block | Iron Framed | Copper Framed | Iron Frame | Copper Frame | Slime | Honey | Odd Slime |
|---|---|---|---|---|---|---|---|---|
| Iron Framed | [Y] | [Y] | [N] | [Y] | [N] | [N] | [N] | [N] |
| Copper Framed | [Y] | [N] | [Y] | [N] | [Y] | [N] | [N] | [N] |

With inner sticky block (e.g., Framed Slime): inherits inner block's grab behavior in addition to frame stickiness above. Framed Slime grabs Normal blocks. Framed Odd Slime grabs directionally.

**Activation model (redstone-style signal strength with anti-signal suppression):**
Framed Blocks use the same FrameActivation system as Frame Blocks. Signal strength 15, decays by 1 per conductor. Mediators (framed blocks with cross-material inner conductor) pass ALL materials as universal bridges. See Frame Blocks section for full algorithm description.

**Known issues:**
- "Any block" framing via automation requires Placer Block (not yet implemented). Manual crafting works.
- Item model translucency: translucent inner blocks (glass, slime) render opaque in inventory. Cosmetic only.

**Completed improvements (2026-02-22f):**

*[DONE] Inner block stickiness override:*
Implemented via INNER_STICKY blockstate property (IntegerProperty 0-4: none/slime/honey/odd_slime/oddstone). isStickyBlock() returns true when ACTIVE || INNER_STICKY > 0. canStickTo() combines frame rules (ACTIVE-gated) + inner sticky rules (INNER_STICKY-gated with vanilla exclusions). ODDSTONE case mirrors real oddstone: grabs all slime types + oddstone. Grab immunity Rules 1 and 4 have carve-outs: matching inner type, oddstone universal grabber, and active same-material frame connections for framed oddstone targets.

*[DONE] Facing/orientation properties:*
FACING (DirectionProperty) set from player look direction on placement. Transferred to inner block's orientation property during rendering via buildInnerState() -- handles DirectionalBlock.FACING (6-way), HorizontalDirectionalBlock.FACING (4-way, vertical clamped to NORTH), RotatedPillarBlock.AXIS (mapped from FACING). Also controls Framed Odd Slime directional stickiness.

*[DONE] Always-sticky bug fix:*
isStickyBlock() now returns ACTIVE || INNER_STICKY > 0 instead of unconditional true. Frame stickiness properly ACTIVE-gated. Inner stickiness properly INNER_STICKY-gated.

*[DONE] See-through-world rendering fix:*
Added noOcclusion() to framed block properties in ModBlocks.java. Prevents neighbor face culling for translucent inner blocks. Renderer rewritten to use BlockRenderDispatcher.renderSingleBlock() with FACING transfer, handling multi-element models, translucency, and tinting natively.

**Planned improvements:**

*[DONE] Frameability whitelist tag system:*
Replaced heuristic-based frameability checks with a tag-driven whitelist (`#ideomod:frameable_blocks`). ~290 vanilla blocks explicitly enumerated. Infested block variants excluded (framing would bypass silverfish emergence). Interactive blocks, mechanical blocks, and other complex behavior blocks excluded by omission from the tag. Extensible by datapacks or other mods.

*[DONE] Behavioral delegation (entity interaction):*
stepOn, fallOn, updateEntityAfterFallOn, entityInside overrides in FramedBlock. Framed magma burns, framed hay reduces fall damage, framed slime bounces. SlimeBlock bounce requires BOTH fallOn (damage cancel) AND updateEntityAfterFallOn (velocity reversal). Known limitation: honey wall-sliding requires inset collision shape that framed blocks don't replicate.

*[DONE] Behavioral delegation (speed/jump factor):*
EntityMovementMixin intercepts Entity.getBlockSpeedFactor() and getBlockJumpFactor() at RETURN. Block-level methods have no position param; mixin intercepts at Entity level where position IS available. Framed soul sand: speed 0.4. Framed honey: speed 0.4, jump 0.5.

*[DONE] Framed obsidian immovable:*
PistonBaseBlockMixin Feature 4: isPushable returns false when inner block is obsidian, crying obsidian, reinforced deepslate, or destroySpeed -1. Mirrors vanilla's hardcoded checks (NOT PushReaction.BLOCK, which obsidian doesn't use). PistonStructureResolverMixin Feature 5: fails entire resolve when addBlockLine encounters FramedBlock with immovable inner, preventing "left behind" behavior. Framed obsidian anchors sticky groups.

*[DONE] Mining overhaul:*
Dynamic mining system: getDestroyProgress() combines inner block hardness + frame material modifier (iron: 0.75, copper: 0.5). canHarvestBlock() checks inner block's tier tags (needs_diamond_tool, etc.) via tool.isCorrectToolForDrops(). getDrops() returns framed block item with inner block NBT preserved. FrameMaterial gains miningTimeModifier field. Removed requiresCorrectToolForDrops() from framed block registrations (now dynamic).

*[DONE] Ice melt to waterlogged frame:*
isRandomlyTicking() returns true. randomTick() checks if inner block is Blocks.ICE with vanilla melt condition (block light > 11 - ice emission). Melts to waterlogged empty frame of same material, preserving ACTIVE state. Schedules water tick for natural flow.

*[DONE] Inside-face rendering:*
All 8 frame/framed block JSON models updated with inverted cuboid element (from [16,16,16] to [0,0,0]) for inward-facing normals. Frame interior visible through transparent inner blocks (glass, ice, etc.). Pure JSON change.

**Future ideas:**
- *[IDEA] Copper frames oxidize over time? Waxable? -- See Iron/Copper Registry Substitution below.*
- *[IDEA] Different blast resistance per frame material?*

---

### Frame Blocks [DONE]
> Transparent decorative blocks -- an empty iron or copper frame. Same stickiness family as Framed Blocks.

| Property | Value |
|----------|-------|
| ID | `iron_frame`, `copper_frame` |
| Class | FrameBlock (extends Block, implements IFrameBlock, SimpleWaterloggedBlock) |
| Hardness | Iron: 3.0 / Copper: 2.5 (blast 6.0) |
| Tool | Pickaxe |
| Transparency | Non-solid, see-through. Uses RenderType.cutout() and noOcclusion(). Inside-face rendering via inverted cuboid in JSON models. |
| Recipe | 4 iron/copper ingots in diamond pattern -> 4 frames |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Decorative transparency | [DONE] | Renders with transparent interior. RenderType.cutout() registered in clientSetup(). noOcclusion() in block properties. Inside-face rendering via inverted cuboid element in JSON models. |
| Machine-activated stickiness | [DONE] | BooleanProperty ACTIVE (default false). isStickyBlock() returns ACTIVE value. Activation driven by FrameActivation.markDirty() -- redstone-style batched wipe-and-rebuild (markDirty + end-of-tick processing) from Machine Block sources. |
| Material exclusivity | [DONE] | Iron frames stick to iron frames only. Copper to copper only. Cross-material IFrameBlock check falls through (does not short-circuit to false) so inner stickiness permissive entries can still match. Both materials stick to Machine Family when active. |
| Slime/honey exclusivity | [DONE] | canStickTo permissive (lets slime/honey grab frames). Also permissive to FramedBlock with INNER_STICKY>0 (allows cross-material framed sticky blocks to grab frames). Grab immunity rules prevent frames from grabbing slime/honey/odd slime. |
| Activation chaining | [DONE] | Signal strength 15, decays by 1 per conductor block. Max range 15 blocks from Machine Family source. Machine Frames only activate when unpowered; all other Machine Family blocks always activate. Cross-material stops propagation unless via mediator. Anti-signal suppression via redstone-powered Machine Frames only. |
| Vanilla metal conductors | [DONE] | iron_block and copper_block participate in frame activation networks. Activation tracked ephemerally in FrameActivation. Only sticky when activated. |
| Cross-frame mediation | [DONE] | Framed blocks with cross-material inner conductor (e.g., copper-framed iron) are mediators -- pass ALL materials when activated. Unlimited mediator hops. |
| Activation-gated stickiness | [DONE] | Frame-to-frame stickiness requires ACTIVE on both sides. Machine blocks only grab active frames. |
| Waterlogging | [DONE] | Implements SimpleWaterloggedBlock. BooleanProperty WATERLOGGED (default false). Auto-waterlogs when placed in water. Bucket interaction. updateShape() schedules fluid ticks. Water contained within frame (standard full-cube waterlogged behavior). |

**Activation model (redstone-style signal strength with anti-signal suppression):**
Frames are passive like redstone dust. They never decide their own activation state.
All lifecycle hooks (onPlace, onRemove, neighborChanged) call FrameActivation.markDirty(level, pos).
1. DISCOVER: BFS from change point through all connected conductors (any material, active or not)
2. WIPE: Set all discovered IFrameBlocks to active=false, clear vanilla metal activation set
3. FIND SOURCES: Check all network blocks + change position for adjacent Machine Blocks
4. PROPAGATE ACTIVATION: BFS from activation sources, strength 15 decaying by 1. Machine Frames only activate when unpowered; all other Machine Family blocks always activate. Material routing: conductors only accept matching materials. Mediators pass ALL materials.
5. PROPAGATE ANTI-SIGNAL: BFS from powered Machine Frames ONLY (no other Machine Family blocks), strength = redstone level, decaying by 1. Binary override.
6. APPLY: Block activates only if activation > 0 AND anti-signal == 0.

Machine Blocks also call FrameActivation.markDirty() in their own lifecycle hooks.
Redstone input changes trigger via neighborChanged automatically.

**Stickiness matrix (when active, requires active on BOTH sides for frame-to-frame):**

| | Machine Fam | Iron Frame (active) | Copper Frame (active) | Iron Frame (inactive) | Vanilla Iron (activated) | Vanilla Copper (activated) | Slime | Honey | Odd Slime | Oddstone | Normal |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Iron Frame (active) | YES | YES | no* | no | YES | no | no | no | no | no | no |
| Copper Frame (active) | YES | no* | YES | no | no | YES | no | no | no | no | no |

- `no*` = mutual canStickTo exclusion (cross-material)
- `no` = grab immunity rule (rules 9-11) or canStickTo returns false or inactive
- When inactive: isStickyBlock() returns false, block is non-sticky
- Machine Fam only grabs active frames (IMachineFamily.canMachineFamilyStickTo checks isActive)

**Known issues:**
- Model is cube_all -- should be custom frame shape

**Notes:** Frame Blocks share the IFrameBlock interface and FrameMaterial enum with future Framed Blocks. An Iron Frame and an Iron Framed Stone will stick to each other when activated (same material family).

---

### The Framer [MERGED -> Placer Block]
> Merged into Placer Block. The Placer handles framing when it attempts to place a Frame Block and there's already a block in front of it.

---

### Iron/Copper Block Registry Substitution [DESIGNED]
> Replacing vanilla iron_block and copper_block with custom versions that own their blockstate properties. Required to add ACTIVE visual state, rust stages (iron), and full frame family membership to these blocks.

**Why substitution is necessary:**
Vanilla blocks cannot have new blockstate properties added to them after registration. We need to add ACTIVE (visual change when part of an active frame network) and RUST_STAGE (iron only) as real blockstate properties. The only way to achieve this cleanly is to own the block registration entirely -- replacing vanilla's entry with our own via Forge registry substitution (the Quark pattern).

From the player's perspective, nothing changes: same craft recipe, same name, same item, same everything. Our code runs underneath.

**What our iron block gains:**
- RUST_STAGE (0-3) -- drives texture. Stage 0 = vanilla. Stages 1-3 = exposed/weathered/rusted.
- ACTIVE (boolean) -- drives texture when part of an active frame network.
- Wax interaction (honeycomb locks rust stage).
- Grindstone reversal (pick up rusted block, use in grindstone, drops previous stage).

**What our copper block gains:**
- ACTIVE (boolean) -- drives texture when part of an active frame network.
- The four vanilla oxidation stages become ours to control.
- Wax interaction inherited from vanilla.

**Rust system design:**

| Stage | Name | Visual |
|-------|------|--------|
| 0 | Iron Block | Clean vanilla |
| 1 | Exposed Iron | Light orange flecks, surface discoloration |
| 2 | Weathered Iron | Spreading rust patches |
| 3 | Rusted Iron | Deep red-brown, heavily corroded |

Progression triggers (moisture exposure):
- Rain with sky access -- rains and the block can see the sky = accumulate moisture ticks.
- Adjacent water source block -- constant exposure, faster than rain.
- Submerged -- fastest rust rate.
- Biome modifier -- swamp/jungle/beach/ocean: accelerated. Desert/mesa: halted.

Reversal: pick up the block (mine it), put in a grindstone. Output is the block at the previous stage. Wax (honeycomb) locks the current stage and prevents further progression.

**Implementation note:**
Registry substitution is the highest-risk operation in modding. Can cause save compatibility issues if done carelessly. Planned for a dedicated session after current systems reach a clean boundary. Test only on fresh worlds initially.

**Deferred to:** Future dedicated session.

---

### Ghost Block [DESIGNED]
> Ethereal, translucent block crafted from ectoplasm. Passable by walking players (slow slide-through). Impassable when crouching. Swappable with real blocks via right-click. Component in the Arcane Projector recipe.

| Property | Value |
|----------|-------|
| ID | `ghost_block` |
| Hardness | TBD (probably very low or instant-break) |
| Tool | Any / none |
| Recipe | TBD (ectoplasm-based) |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Translucent appearance | [IDEA] | Ethereal, ghostlike visual. Semi-transparent, slight glow or shimmer. |
| Walk-through passable | [IDEA] | Non-crouching players pass through at reduced speed (slow slide, like walking through cobwebs but without damage or sticking). |
| Crouch-impassable | [IDEA] | Crouching players cannot pass through. Solid collision when sneaking. |
| Block swap | [IDEA] | Right-click a placed ghost block with any real block in hand: ghost block returns to your inventory, real block takes its place. Clean swap, no dropping. |
| Arcane Projector component | [IDEA] | Required crafting ingredient for the Arcane Projector. Ghost Block is necessary for the projector to function -- thematically, projection uses the same ephemeral material. |

**Design notes:**
- The walk-through mechanic makes ghost blocks useful as one-way barriers, slow traps, decorative hazards, or secret passages.
- The right-click swap mechanic is the gameplay hook -- use ghost blocks as placeholder markers while building, then swap them for real blocks one by one when you're happy with the layout.
- Source: crafted from ectoplasm dropped by ghosts. Ghost hunting becomes a gathering loop for a useful building material.

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
| Obsidian multiblock base | [IDEA] | Beacon-like pyramid of obsidian blocks beneath the Inhibitor. Lapis corners increase effectiveness (stronger suppression). Diamond corners increase range. Same structural grammar as Beacon Rework, Promoter, World Anchor, Magic Core. |

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

### Promoter Block [IDEA]
> Inverse of Inhibitor. Area-of-effect mob spawn booster. Increases hostile mob spawning in its radius. Part of the Odd Family.

| Property | Value |
|----------|-------|
| ID | `promoter` |
| Hardness | TBD |
| Tool | Pickaxe |
| Recipe | Oddstone + ? (TBD -- mirror of Inhibitor recipe with different modifier) |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Spawn increase (inner radius) | [IDEA] | Strong spawn rate increase in close range (~8 blocks?). Exact radius TBD. |
| Spawn attraction (outer radius) | [IDEA] | Mobs in outer zone are drawn toward the Promoter's area, concentrating them. |
| Phantom summoning? | [IDEA] | Could force phantom spawns regardless of player sleep status. Creates a targeted aerial threat zone. |

**Design notes:**
- Primary use: mob farms. Players who want to farm mob drops can invest Oddstone into a Promoter to dramatically increase spawn rates in a controlled area.
- Creates an interesting resource tension with Inhibitors: both consume Oddstone, so players choose between "safer mining" and "better farming."
- Dangerous to leave uncontrolled -- placing a Promoter without containment is asking for trouble. Rewards players who build proper mob farm infrastructure.
- Could use the same obsidian multiblock base structure as the Inhibitor: beacon-like pyramid with mineral block corners for range/effectiveness scaling.
- Part of the Odd Family thematically -- strange stone that calls to mobs instead of repelling them.

**Multiblock structure (shared with Inhibitor):**
- Base: Beacon-like pyramid of obsidian blocks beneath the Promoter/Inhibitor
- Lapis corners: increase effectiveness (stronger spawn suppression/boost)
- Diamond corners: increase range
- Each additional tier of the pyramid increases the structure's power
- See also: Beacon Rework (same structural grammar)

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
- None currently

**Design notes:**
- Now extends AbstractRangedPistonBlock (~15 lines). Movement delegated to BlockMoveHelper.
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
- None currently

**Design notes:**
- Now extends AbstractRangedPistonBlock (~15 lines). Movement delegated to BlockMoveHelper.
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

### Thematic Block Families [IDEA]
> Three thematic families organize the mod's blocks by crafting material, aesthetic identity, and gameplay niche.

| Family | Primary Material | Aesthetic | Gameplay Niche |
|--------|-----------------|-----------|----------------|
| **Machine Family** | Hollow Stone + Iron | Dark steel, mechanical | Redstone, automation, engineering |
| **Odd Family** | Oddstone variants | Purple-grey, strange | Wireless mechanics, mob interaction, portals |
| **Magic Family** | Obsidian | Dark, arcane | Transmutation, enchantment, projection |

**Design notes:**
- All three families feed back into the Oddstone economy: Machine Blocks require Hollow Stone (Oddstone catalyst), Odd blocks are crafted directly from Oddstone, Magic blocks use obsidian (which interacts with the Oddstone/lava system).
- Each family has its own visual language. Players should be able to tell at a glance whether a block is mechanical, odd, or magical.
- Families are not exclusive -- blocks can interact across families (Machine Linker uses Oddstone variants, Framed Blocks can wrap any block type, etc.).

---

### Transmuter [IDEA]
> Magic block that transmutes a block in front of it into a different type. Part of the Magic Family (obsidian-crafted).

| Property | Value |
|----------|-------|
| ID | `transmuter` |
| Hardness | TBD |
| Tool | Pickaxe |
| Placement | Directional (faces player look direction) |
| Recipe | TBD (obsidian + ? -- Magic Family crafting) |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Block transmutation | [IDEA] | When activated, converts the block directly in front of it (in FACING direction) into a different block type. |
| Activation | [IDEA] | TBD -- redstone pulse? Continuous while powered? Fuel-based? |

**Design notes:**
- Intentionally open-ended for now. The core concept is "magic block that changes one block type into another."
- Possible directions: recipe-based (specific input -> specific output, like cobble -> stone, sand -> clay), fuel-based (consumes XP or a catalyst), or tied to the alchemy/brewing system.
- Could be a simpler, directional alternative to the Odd Furnace (which smelts in-world). Transmuter does non-smelting transformations -- things you can't do in a furnace.
- Part of the Magic Family -- obsidian-crafted, arcane aesthetic.
- Pairs with the Projector for a "magic automation" toolkit alongside the Machine Family's "mechanical automation" toolkit.

**Open questions:**
- *What determines the output? Fixed recipe table? Catalyst item in a slot? Random within a set?*
- *Fuel/cost per transmutation? Free is too powerful. XP cost? Lapis/amethyst consumption?*
- *Can it transmute block entities (chests, furnaces)? Probably not -- only simple blocks.*
- *Does it work on Oddstone or obsidian? Probably restricted to prevent exploits.*

---

### Projector [IDEA]
> Magic block with a 3x3 GUI grid. When powered, "projects" the configured block pattern at a distance determined by redstone signal strength. Projected blocks are temporary -- they exist only while the Projector is powered. Part of the Magic Family.

| Property | Value |
|----------|-------|
| ID | `projector` |
| Hardness | TBD |
| Tool | Pickaxe |
| Type | Block Entity (3x3 item grid + FACING) |
| Placement | Directional (faces player look direction) |
| Recipe | TBD (obsidian + glass + ghost block -- Magic Family crafting) |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| 3x3 block grid GUI | [IDEA] | Right-click opens a 3x3 grid. Place individual blocks in slots to define the projected pattern. Empty slots project nothing (air). |
| Signal-distance projection | [IDEA] | When powered, projects the 3x3 pattern at a distance equal to (or inversely proportional to) the redstone signal strength. Signal 1 = far, signal 15 = close? Or signal 1 = close, signal 15 = far? TBD. |
| Temporary placement | [IDEA] | Projected blocks are real, physical, solid blocks in the world. They exist only while the Projector is powered. Removing power causes all projected blocks to vanish instantly. |
| Collision with existing blocks | [IDEA] | If a target position is already occupied, that slot simply fails to project -- the existing block is not affected. Partial patterns are valid. |
| Block consumption | [IDEA] | TBD -- does the Projector consume the blocks from its grid when projecting? Or are they reusable (the grid is a template, not a supply)? Template is more powerful but more interesting for building and traps. |

**Design notes:**
- Think of it as a magic holographic projector. Place blocks in the pattern grid, power it, and those blocks appear at a distance. Cut power and they vanish.
- Ghost Block required in the recipe -- thematically the projector manifests ephemeral matter, and ghost blocks are already that.
- Primary uses: temporary bridges, traps (floor appears solid then vanishes), dynamic walls, construction scaffolding, redstone-controlled architecture.
- The 3x3 grid means projected structures are small -- this is precision placement, not large-scale construction. For larger structures, use multiple Projectors.
- Signal-distance control means you can sweep a projected platform across space by varying redstone signal strength. Combined with Machine Clock for gradual stepping, this enables moving platforms.
- Projected blocks should probably not be pushable by pistons (they're projections, not real structures). They break if their Projector is moved or unpowered.
- Part of the Magic Family -- arcane aesthetic, obsidian-crafted. The "magic" counterpart to the Machine Family's mechanical block placement (Placer Block).

**Open questions:**
- *Signal distance mapping: linear (signal 1 = 1 block, signal 15 = 15 blocks) or inverse (signal 15 = 1 block, signal 1 = 15 blocks)?*
- *Max projection distance? Probably capped at 15-16 to match redstone signal range.*
- *Can projected blocks be interacted with normally (mined, walked on, placed against)? They should be solid and walkable. Mining probably shouldn't drop anything -- they're projections.*
- *What happens if the Projector itself is moved (by piston)? Projected blocks probably break/vanish.*
- *Visual distinction? Projected blocks could have a subtle shimmer, transparency, or particle effect to distinguish them from "real" blocks. Or they could be indistinguishable for trap potential.*
- *Does the pattern project in the FACING direction only, or can it be aimed?*

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

### Bramble Block [IDEA]
> A damaging variant of the Bush Block. Visually similar to bushes but deals damage when walked through. Spawns naturally in Haunted Forest biomes replacing normal bush/undergrowth.

| Property | Value |
|----------|-------|
| ID | `bramble` |
| Hardness | ~0.2 / Blast 0 (leaf-equivalent) |
| Tool | Shears |
| Notes | Connecting foliage like Bush Block. Same 6-boolean blockstate pattern. Deals contact damage to entities passing through. |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Contact damage | [IDEA] | Deals damage to players and mobs that move through or stand inside the block (similar to cacti/sweet berry bushes). |
| Connecting shape | [IDEA] | Inherits Bush Block's multipart model approach. Visually distinct texture -- darker, thornier appearance. |
| Haunted Forest spawn | [IDEA] | Primary undergrowth of the Haunted Forest biome. Replaces or supplements normal bush/shrub placement. |

**Design notes:**
- A darker reskin of the Bush Block with damage behavior. The Haunted Forest becomes a place where getting through the undergrowth costs you.
- Makes travel in Haunted Forests feel appropriately hostile without being impassable.
- Pairs naturally with the ghost spawning in that biome -- the world itself pushes back.

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

### Ectoplasm [IDEA]
> Rare drop from ghost-type mobs. Primary crafting material for Ghost Blocks and potion ingredients.

| Property | Value |
|----------|-------|
| ID | `ectoplasm` |
| Type | Crafting material |
| Source | Dropped by ghost mobs (overworld ghosts, After dimension ghosts, etc.) |
| Drop rate | TBD (moderate -- ghosts are common in certain contexts, so shouldn't be too rare) |

**Uses:**
- Ghost Block (primary use)
- Phasing Potion ingredient
- Flight Potion ingredient (with phantom membrane)

**Design notes:**
- Ghost hunting loop: find ghosts, kill them, collect ectoplasm, craft ghost blocks or potions.
- In the Haunted Forest biome (higher ghost spawn rate), ectoplasm becomes more easily farmable.
- In The After dimension, ghosts are the primary inhabitants, making ectoplasm highly available there.

---

### Lore Books [IDEA]
> Written books found as loot in structures. Detail the history of the Old World from different perspectives. Not craftable. Each serves as both world-building and player guidance.

**Book types:**

*Engineer's Diary:*
Written from the perspective of an Old World crafter/builder. Technical in tone. Describes machines, systems, and infrastructure built by the advanced civilization. Guides players toward Ideomod mechanical content (machine blocks, portals, etc.) while hinting at the war and its causes. Found in: workshop ruins, underground structures, city ruins in The After.

*Explorer's Manual:*
Written by an Old World expedition member. Describes dimensional exploration and discovery. Guides players toward dimension-related content (portals, The After). Contains partial portal addresses, hints about dimension-specific dangers and resources, observations about the state of the world. Found in: portal ruins, surface ruins, The After libraries.

*Familiar Notebooks/Diaries:*
Written by an unnamed person -- the player character's past self (Steve, though never named explicitly). Personal, fragmented, unreliable narrator. Details the events of the war, the immortality procedure, the fate of the civilization, and oblique references to "my brother" (Herobrine). Found in: unique structures, very rare loot. Multiple volumes, each revealing more. Written in a voice that feels intimate and strange -- the player is reading their own past.

**Design notes:**
- Books are loot, not crafted. Finding one is a discovery event.
- They hint and imply rather than state directly. Players piece together the lore themselves.
- Engineer's Diary and Explorer's Manual are more common -- functional guides with lore layered on top.
- Familiar Notebooks are rare and treated as significant finds. Each one reveals a piece of Steve's history.

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
- Machine Track <-> Machine Trolley: **NOT STICKY** (different MachineTypes. Machine Frame bridges them. Track pushes Trolley via BlockMoveHelper, not stickiness.)

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
- None currently

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
| Address space | 14 corner types ---- 4 positions = 14^4 = 38,416 unique addresses per dimension |
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
| 180-degree exit rotation | [DONE] | All teleports rotate player 180 degrees so they exit facing away from destination portal -- doorway effect. Stacks with cross-axis rotation. Same-axis: 180 degrees. Cross-axis: 90 + 180 degrees. |
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

### Desolate Biome [IDEA]
> Post-war surface scarring. Barren wastelands that hint at catastrophic destruction.

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Surface composition | [IDEA] | Sand and gravel only. No living plant life. No grass, trees, bushes, or flowers. |
| Ore replacement | [IDEA] | All ore blocks in this biome are replaced with gravel. The earth has been stripped of value. |
| Visual feel | [IDEA] | Flat to rolling dunes. Occasional ruins. Oppressive emptiness. |

**Design notes:**
- A biome that tells a story through its existence. Something terrible happened here.
- The ore-to-gravel replacement is a meaningful gameplay signal: this is not a place to mine.
- Pairs with the Ruined City structures (see Structures) -- desolate biomes are where the ruins are most common.
- Named TBD -- "Desolation", "Wastes", "Scarred Plains"?

---

### Haunted Forest Biome [IDEA]
> Dark, oppressive forest biome with elevated ghost spawning and dangerous undergrowth.

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Ghost spawn rate | [IDEA] | Higher baseline ghost mob spawn rate than other biomes. Best overworld source of ectoplasm. |
| Bramble undergrowth | [IDEA] | Normal bushes replaced with Bramble blocks -- same visual shape, deal damage on contact. Travel through is costly. |
| Atmosphere | [IDEA] | Dark sky/fog tint? Permanently overcast? Eerie ambient sounds? Visual feel TBD. |
| Tree type | [IDEA] | Dark oak or new variant? Twisted, gnarled appearance. |

**Design notes:**
- The Haunted Forest is the overworld's haunted zone. Worth visiting for ectoplasm, hostile to casual travel.
- Brambles making movement through it painful fits the biome's theme: the forest doesn't want you here.
- Pairs with ghost lore -- the war left lingering spiritual residue in certain places.

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
- Drop: Ectoplasm (primary source).
- Spawn locations: underground (Mining Threat), Haunted Forest biome (overworld), The After dimension (common).

---

### Revenant [IDEA]
> Armored zombie-like ghost. Exclusive to The After dimension. Strong hostile mob -- piglin brute tier.

| Property | Value |
|----------|-------|
| ID | `revenant` |
| Spawns | The After dimension -- city structures primarily |
| Hostility | Always hostile |
| Strength | Piglin brute tier (high health, high damage) |
| Drops | TBD (gear they're wearing? ectoplasm? After-exclusive loot?) |

**Design notes:**
- Revenants are the inhabitants of The After's cities. They were people once.
- The combination of armor+gear makes them visually distinct from vanilla zombies -- these are soldiers, guards, civilians preserved in a ghostly state.
- The After's cities should feel dangerous because of them, not just atmospheric.
- Lore tie-in: they're the echoes of the Old World's population, still "living" in the memory-dimension.

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

### Warg [IDEA]
> Hostile, oversized wolf variant. Naturally spawning predator. Also a transformation state -- wolves struck by lightning become Wargs.

| Property | Value |
|----------|-------|
| ID | `warg` |
| Spawns | Forests (natural spawn, rarer than wolves) |
| Hostility | Always hostile to players |
| Size | Larger than a wolf -- visually distinct, more threatening |
| Lightning transform | Normal wolves struck by lightning become Wargs |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Natural spawn | [IDEA] | Rare spawn in forest biomes. More threatening than wolves by default. |
| Lightning conversion | [IDEA] | When a vanilla wolf is struck by lightning, it converts to a Warg. Similar mechanic to pig -> zombie piglin. |
| Aggression | [IDEA] | Hostile to players on sight. May also attack passive mobs (not just defensive like wolves). |
| Tamed wolf protection | [IDEA] | Does a Warg attack tamed wolves? Probably yes -- makes lightning storms dangerous for wolf owners. |

**Design notes:**
- The lightning conversion mechanic means thunderstorms become a minor threat to wolf owners -- a natural, emergent tension.
- Wargs as the "what wolves could be if something went wrong" -- takes a familiar mob and makes it dangerous.
- Lore tie-in: engineered war animal? Or just a natural predator that got pushed to the margins? Ambiguous -- the lore can support either.

---

### Untamed Ravager [IDEA]
> Wild ravager variant. Spawns in certain biomes. Has a proximity hostility timer -- becomes hostile after the player is nearby too long, or instantly hostile if directly adjacent.

| Property | Value |
|----------|-------|
| ID | `untamed_ravager` (or variant state of vanilla ravager?) |
| Spawns | Certain overworld biomes -- plains, savanna? TBD |
| Hostility | Semi-hostile (timer-based) |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Proximity timer | [IDEA] | When a player comes within a medium radius, a hidden timer starts. If the player remains within range for the full duration, the ravager becomes hostile. If the player backs away in time, the timer resets. |
| Adjacent instant aggro | [IDEA] | If a player steps directly next to the ravager (1-block distance), it becomes instantly hostile regardless of timer. Personal space violation. |
| Pillager supply chain | [IDEA] | Lore explanation for where pillagers get their ravagers -- they capture and domesticate these wild ones. |

**Design notes:**
- This creates a mob you can observe and navigate around, unlike most Minecraft mobs which are binary (passive or always-hostile).
- Players learn to give untamed ravagers a wide berth. Experienced players learn exactly how close is safe.
- The "backs away resets timer" mechanic means careful players can coexist near them. Reckless players get charged.
- The adjacent instant-aggro rule means you can never truly sneak up on one without triggering it.

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
| Expanded recipes | [IDEA] | New potion recipes not available in vanilla brewing. Some may require the extra slot (two-ingredient brews). |
| Extended vanilla potions | [IDEA] | Allows upgrading past current vanilla potion tiers and durations. |
| Glittering Potion base | [IDEA] | New potion base brewed with diamond and gold. Stronger than awkward potion. Required base for Flight Potion and other high-tier potions. |

**Design notes:**
- The Brewery is a *place*, not a single block. You build a brewing corner in your base with the right components and it becomes more powerful. Very vanilla+ -- it's still a brewing stand, it just works better in context.
- The extra slot is the key mechanical addition. Two-ingredient brews open up a whole recipe design space without touching the vanilla brewing system.
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
- This is the endgame brewing mechanic. Multi-effect potions are very powerful, so the risk system and structure investment keep it balanced.
- The progression: basic brewing (vanilla) -> Alchemy Station (expanded recipes, extra slot) -> Potion Infusion (multi-effect, structure-gated quality).
- Stacks with the potion stacking change (see Systems > Potion Stacking) -- multi-effect potions that stack to 4 are a genuine loadout option.

---

### New Potions [IDEA]
> New potion types unlocked through the Alchemy Station. Several require upgraded ingredients or the Glittering Potion base.

**Glittering Potion:**

| Property | Value |
|----------|-------|
| Type | Potion base (like Awkward Potion) |
| Brew | Diamond + gold at Alchemy Station |
| Role | Higher-tier base for powerful new potions. Cannot be drunk itself. |

A new alternative to Awkward Potion as a brewing base, unlocking a tier of potions unavailable from standard ingredients. Requires the upgraded Alchemy Station to brew.

**Flight Potion:**

| Property | Value |
|----------|-------|
| Base | Glittering Potion |
| Ingredients | Ectoplasm + Phantom Membrane |
| Effect | Grants creative-style flight. |
| Duration | Very short. A few seconds to half a minute -- a burst of flight, not sustained wings. |
| Cost note | Requires Glittering Potion (diamond + gold), ectoplasm (ghost farming), and phantom membrane (phantom killing). Intentionally high cost for the power level. |

**Phasing Potion:**

| Property | Value |
|----------|-------|
| Base | Awkward Potion (standard) |
| Ingredients | Ectoplasm |
| Effect | Player becomes ghostlike -- can pass through blocks. Player model becomes translucent. Nametag hidden. |
| Duration | TBD (moderate -- long enough to be useful, short enough to require management) |

**Flower Potion:**

| Property | Value |
|----------|-------|
| Base | Awkward Potion |
| Ingredients | Flowers (any? specific type?) |
| Effect (drinking) | Potion particle effects replaced with flower petals. Flowers grow on grass blocks the player walks over. |
| Effect (splash) | Grows a patch of flowers on impact area. |
| Duration | Medium. Flower-growing effect lasts for the duration. |
| Tone | Purely fun/cosmetic. No combat use. |

**Design notes on new potions:**
- The Glittering Potion base creates a gated tier: cheap potions from awkward base, expensive/powerful potions from glittering base. Flight especially earns its cost.
- Phasing and Flight together create a ghost-theme potion pair sourced from ghost materials -- hunting ghosts gives you ghost-like powers.
- Flower Potion is deliberately useless for combat. Some potions should just be fun. The splash version has niche utility as a decorating tool.

---

### Enchanting Overhaul [IDEA]
> Comprehensive rework of the vanilla enchanting system. New point-budget mechanic, catalyst slot, tiered resources, expanded enchantments, and removed restrictions.

**How Vanilla Enchanting Works (reference for design decisions):**

The vanilla system has several layers that all feed into a single number -- the "modified level" -- which determines everything:

1. The button shows a base level (set by bookshelf count, capped at 15 bookshelves -> ~level 30 on bottom slot).
2. Enchantability (a per-material stat) inflates that number by a random bonus (most likely enchantability/4). Gold has the highest enchantability (22-25), diamond is low (10).
3. A small random +/-15% nudge is applied. The result is the modified level.
4. Every enchantment has a defined level range per tier (e.g. Sharpness III appears at modified levels 23-43). The modified level determines which tiers are in the candidate pool.
5. Enchantments are selected by weighted random from the pool. Common enchants (Protection, Sharpness, Efficiency) have weight 10. Rare ones (Silk Touch, Thorns, Infinity) have weight 1-2.
6. After the first enchant, bonus enchants roll at (modified level + 1) / 50 probability. Modified level halves each roll. This cascades until it fails or runs out.

Key insight: enchantability secretly does three jobs -- inflates effective level, unlocks higher tiers, and increases bonus enchant probability. We are keeping this system largely intact and building on top of it.

**What We Keep (Vanilla Mechanics Preserved):**
- Bookshelf-based level scaling (bookshelves raise the button numbers, which feed everything else)
- Enchantability per material (gold stays magically receptive; high enchantability = higher effective level = better tiers more likely)
- Weighted random enchant selection (weights stay, catalyst slot biases them)
- Tier ranges per enchantment (we extend ranges upward for new max levels, not replace them)
- Bonus enchant chain rolls

**What We Change:**
- Budget cap replaces "you get what you get" -- player deliberately assembles their loadout
- Catalyst slot biases the weight pool toward desired enchant families
- Level caps raised beyond vanilla (new tier ranges added above vanilla max)
- Expanded bookshelf range via obsidian ring structure (more bookshelves = higher modified levels = new tiers reachable)
- Levelless enchants get flat point costs (see Budget section)
- Curses subtract from budget cost (see Curses section)
- Anvil "too expensive" cap removed

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
- Enchantment levels can go beyond vanilla caps (Protection V+, Sharpness VI+, etc.) but higher levels cost proportionally more and are rarer, requiring higher modified levels to appear.
- Different gear materials / tiers have different max enchant levels. Exact numbers TBD, but sword is the highest-demand item (fully loaded vanilla sword costs ~19 points), so budget ceiling should accommodate that.

**Levelless Enchants -- Flat Point Costs:**

Some enchantments have no level (they are simply on or off). These are assigned a flat point cost reflecting their power rather than defaulting to 1.

| Enchantment | Flat Cost | Notes |
|-------------|-----------|-------|
| Mending | 4 | Most powerful passive enchant in the game -- infinite durability |
| Infinity | 3 | Effectively infinite arrows -- high value |
| Silk Touch | 2 | Situational but powerful -- changes tool function entirely |
| Aqua Affinity | 1 | Minor utility |
| Flame | 2 | Consistent fire damage on bow |
| (others TBD) | TBD | Assign based on perceived power level |

**Curses -- Negative Point Costs:**

Curses subtract points from the budget, freeing up space for more enchantments. This transforms curses from pure downside into a meaningful tradeoff. The curse IS the balancing mechanism -- no additional point floor or ceiling is needed, because stacking curses means living with stacked curses.

| Enchantment | Point Value | Notes |
|-------------|-------------|-------|
| Curse of Vanishing | -2 | Item deletes on death -- significant risk |
| Curse of Binding | -2 | Cannot remove armor -- situational risk |
| (new curses TBD) | -1 to -3 | Scaled to severity of the curse's downside |

Design note: No artificial cap on curse stacking. A player who puts 4 curses on a sword to free up budget has to live with 4 curses. The gameplay consequence is self-regulating.

**Catalyst Slot:**

Addition to the Enhanced Enchanting Table structure (see Multiblock Framework). A new slot in the enchanting GUI for catalyst items. Catalysts work by multiplying the weight of certain enchantments in the selection pool -- they do not guarantee outcomes, they bias the lottery.

| Feature | Status | Notes |
|---------|--------|-------|
| Catalyst slot | [IDEA] | New slot in enchanting GUI when structure has lapis corners active. Accepts catalyst items. |
| Enchantment biasing | [IDEA] | Placing a catalyst multiplies the weight of enchantments in that family. E.g. blaze powder -> Fire Aspect and Flame get heavier weights. Sharpness at weight 10 can still beat a boosted Looting at weight 2x2=4. |
| Bias strength | [IDEA] | TBD -- 2x feels like a nudge, 5x feels like a near-guarantee. Sweet spot probably around 3x. Too weak = pointless, too strong = vending machine. |
| Catalyst consumed? | [IDEA] | TBD -- consumed per enchant feels like a per-decision cost; reusable feels like a permanent station upgrade. Both valid, different play feel. |
| Catalyst items | [IDEA] | TBD -- thematic links: flint -> Sharpness family, feather -> movement enchants, blaze powder -> fire enchants, rabbit's foot -> luck/looting, etc. |
| Structure amplification | [IDEA] | Does a better-built structure amplify the catalyst effect? Would tie structure investment and catalyst slot together. |

**Tiered Enchanting Resources:**

| Feature | Status | Notes |
|---------|--------|-------|
| Resource tiers | [IDEA] | Lapis is no longer the only enchanting fuel. Higher-tier enchanting requires rarer resources. Specific tier list TBD -- could follow lapis -> amethyst -> Oddstone dust -> something else. |

**Raising the Level Ceiling:**

To unlock enchantment tiers beyond vanilla caps, players need modified levels higher than vanilla allows (~30 max). This is achieved by:
- Obsidian ring expansions allowing more bookshelves to count (more rings = more bookshelves = higher base level)
- The vanilla bookshelf cap (15) is lifted by the ring structure -- each ring allows more bookshelves in valid positions
- New enchantment tier ranges are defined above vanilla maximums (e.g. Sharpness VI at modified level 56-76, requiring a modified level vanilla cannot reach)
- Gold's high enchantability still inflates further on top, so gold gear benefits most from high-level tables

**Anvil Changes:**

| Feature | Status | Notes |
|---------|--------|-------|
| Remove "too expensive" cap | [IDEA] | Anvil no longer refuses operations. Cost still scales with prior work, but the hard 39-level cap is gone. If you have the levels, you can do it. |

**Expanded Enchantments:**

New enchantments designed to give the catalyst system, budget system, and higher level ranges more to work with. Goal is a richer pool, not a bloated one. A few targeted additions.

| Enchantment | Type | Applies To | Effect | Notes |
|-------------|------|-----------|--------|-------|
| Weight | Curse (optional) | Armor | Increases fall speed and fall damage taken. When landing on a mob, deals damage to it proportional to fall distance. | Player choice -- accept the downside, weaponize the fall. Works with high-fall playstyles. |
| Aspect of [Tool] | Enchantment | Tools | The enchanted tool also functions as the named tool type. E.g. Aspect of Bow on a pickaxe: the pickaxe can also shoot arrows. | Discovery-driven -- players find wild combinations. Implementation complexity TBD. Multiple variants (Aspect of Bow, Aspect of Axe, etc.) each as separate enchants. |
| (more TBD) | -- | -- | -- | Expand as enchanting system develops |

**New Curses:**

| Curse | Point Value | Effect | Notes |
|-------|-------------|--------|-------|
| Curse of Conductivity | -2 | During thunderstorms, the player has an elevated chance of being struck by lightning while wearing the cursed item. | Thematic risk -- weather becomes a genuine hazard. High-value tradeoff in non-storm conditions. |
| (more TBD) | -- | -- | Curses should always have a clear, observable downside that the player can understand and weigh against the budget gain. |

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
- Catalysts + budget + tiered resources create three levers for progression: what you can get (catalysts), how much you can fit (budget), and what tier you can access (structure investment + resources).
- Curses as negative-cost budget items recontextualizes something players currently avoid into a genuine strategic resource.
- Weight is a curse that doubles as a weapon -- classic cursed-item fantasy trope, emergent combat potential.
- Aspect of [Tool] enchants are discovery-driven. Players find combinations that feel like secrets. Implementation complexity is a concern -- flag for design review when we get there.
- Curse of Conductivity makes weather a meaningful gameplay variable instead of aesthetic background.
- Removing the too-expensive cap respects player investment. If someone has 80 levels, they earned 80 levels.

**Open questions:**
- *Budget numbers per material tier -- exact values TBD. Anchor: fully loaded vanilla sword is ~19 points, so sword budget ceiling needs to reach at least that.*
- *Does enchantability affect the budget at all, or only the enchant selection process? Gold's enchantability bonus already gives it better rolls -- does it also get a larger budget, or is the enchantability bonus enough?*
- *Catalyst consumed per use or reusable?*
- *Catalyst bias strength multiplier (2x? 3x? 5x?)*
- *Does structure quality amplify catalyst effect?*
- *How many Aspect of [Tool] variants? What tools are worth it?*
- *Weight curse: is it a curse (negative budget cost) or a regular enchant with a built-in downside? Leaning curse.*

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

### Ruined Cities [IDEA]
> Partially buried overworld structures. Remnants of the Old World's surface civilization.

**Design notes:**
- Made primarily of stone and brick variants (cobblestone, stone bricks, mossy variants, cracked).
- Partially buried -- foundations sunk into the earth, upper stories exposed. Feels like something that sank or was covered over time.
- Scattered across the surface, more common in Desolate Biomes.
- Loot: lore books, rare materials, schematic fragments.
- Hints at a civilization that built on the surface before the war made it uninhabitable.
- Underground continuation possible: some city ruins connect to deeper underground structures.

---

### Ruined Bridges [IDEA]
> Massive single columns rising from the terrain. Rare remnants of world-spanning infrastructure.

**Design notes:**
- Single column structures -- what remains when a colossal bridge spanning the world is mostly destroyed. Only the support pillars remain.
- Enormous scale. Towers above the terrain. The remaining stub implies a structure that once dwarfed anything the player could build.
- Rare. Finding one is a notable event. They should feel like landmarks.
- Made of stone, bricks, iron/copper block variants (or the rusted/oxidized versions once the substitution is done).
- No loot necessarily -- the structure IS the discovery. Context and atmosphere.
- Lore implication: the Old World built infrastructure at a scale the player can barely comprehend.

---

### Underground War Structures [IDEA]
> Hidden underground structures hinting at the war, engineered mobs, and the underground civilization.

**Design notes:**
- Various types: laboratories, barracks, storage facilities, creature pens.
- Creature pens imply where creepers, ravagers, phantoms were engineered/bred. Degraded equipment, broken spawn infrastructure, notes left by engineers.
- Connects to the Familiar Notebooks lore -- Steve/the player may have been involved in this.
- Loot: Engineer's Diaries, schematics, rare materials, mob-related drops.
- Found at various underground depths, distinct from vanilla structures (strongholds, ancient cities).
- The stronghold and ancient city already exist -- these new structures fill in the gaps and add context to why those structures exist underground.

---

### The After -- City Structures [IDEA]
> City-like clusters of structures in The After dimension. Primary spawn location for Revenants.

**Design notes:**
- Made of After-dimension stone variants, brick, and the ghostly aesthetic of the dimension.
- Denser than overworld ruins -- The After preserves the memory of the city more completely.
- Revenants patrol and inhabit these areas. High danger.
- Best source of After-exclusive loot and lore books.
- Visually: the city AS IT WAS, but rendered in the After's ethereal palette.

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
| Promoter Block | Medium (TBD) | Inverse of Inhibitor. Mob farm booster. Same Oddstone cost creates tension with Inhibitors. |
| Hollow Stone (-> Machine Blocks) | Indirect (catalyst) | Oddstone is not consumed -- lava sources touching Oddstone become Hollow Stone. Oddstone gates access but is not spent. High throughput if player has lava supply. |
| Machine Blocks | Via Hollow Stone | Crafted from Hollow Stone + iron + slime (TBD). Oddstone cost is indirect through Hollow Stone production. |
| Machine Linker | Via Oddstone variants | Requires Oddstone variant in slot. Variant determines wireless channel. Oddstone not consumed (stays in slot). |
| Machine Specializations | Via vanilla items | Items inserted into Machine Blocks (redstone, hoppers, pistons, etc.) are retrievable. No Oddstone cost per specialization. |
| Placer Block | Medium (TBD) | Automation tool. |
| Oddstone Repulser/Attractor | Low-Medium (TBD) | Ranged Oddstone-only pistons. Oddstone + piston base. |
| Arcane Repulser/Attractor | Medium (TBD) | Universal ranged pistons. Oddstone + obsidian + piston base. |
| Disenchanting Table | Medium + Heavy Core | One-time craft. Ties into Trial Chambers. |
| Oddstone Variants | Free (processing) | Smelting/stonecutting Oddstone into variants is free. The rare resource is the Oddstone itself. |

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

---

### Multiblock Structure Framework [WIP]
> Registration-based system for detecting, tracking, and evaluating multiblock structures. Structures are world data managed by a Structure Manager -- they are not stored on or owned by any individual block. Ordinary vanilla and modded blocks participate in structures without needing custom code. Phase 1 (framework) and Phase 2 (furnace array) are implemented and working.

**Core concept:** Structure definitions (rulesets) live in a central **Structure Manager** (world-level saved data, like the portal registry). When a designated trigger block (the "Core") is placed, the Manager scans the surrounding area using that structure type's ruleset. If valid, the structure is **registered** as an independent entry in the Manager. The structure then exists as world data -- no individual block "owns" it. Any block in the structure can be broken, replaced, or added, and the Manager rescans and updates the registration accordingly.

**Key consequence: blocks are just blocks.** A furnace doesn't know it's part of a machine frame. Stone bricks don't know they're forming a workshop wall. The Structure Manager knows -- it holds the ruleset that gives those blocks meaning in context. This means zero custom block code is needed for most structures. You build with vanilla blocks and the framework recognizes the patterns.

---

**Block Roles (assigned per-ruleset, not inherent to blocks):**

| Role | Scan behavior | Counts as component? | Example |
|------|--------------|---------------------|---------|
| Core | Triggers scan | Yes | Furnace, Enchanting Table, Brewing Stand |
| Connector | Expands scan to neighbors | Yes | Stone bricks (workshop walls/floor) |
| Contributor | Counted, enables/boosts effects | Yes | Machine Block, Mortar & Pestle, Bookshelf |
| Effector | Receives the structure's effect | Yes (usually Core) | The Core block itself |
| Ignored | Present in scan area, doesn't affect result | No | Air, torches, decorative items |

**Structure types (planned):**

| Structure | Core | Status |
|-----------|------|--------|
| Machine Furnace Array | Furnace / Blast Furnace / Smoker | [DONE] |
| Villager Workshop | Various workstations | [IDEA] |
| Enhanced Enchanting Table | Enchanting Table | [IDEA] |
| Brewery / Alchemy Station | Brewing Stand | [IDEA] |

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

**Enhanced Enchanting Table Structure:**

```
Ring 0 (3x3 base):          Ring 1 (5x5):               Ring 2 (7x7):

  O O O              . . . . .                      . . . . . . .
  O [E] O            . O O O .                      . . . . . . .
  O O O              . O [E] O .                    . . O O O . .
                     . O O O .                      . . O [E] O . .
                     . . . . .                      . . O O O . .
                                                    . . . . . . .
                                                    . . . . . . .

[E] = Enchanting Table    O = Obsidian    . = Valid bookshelf zone
```

- Ring 0 alone: bookshelves must be within 2 blocks of table (vanilla distance)
- Ring 1: bookshelves can be up to 4 blocks away and still count
- Ring 2: bookshelves can be up to 6 blocks away
- Each ring must be a complete, connected square of obsidian with the table at the center
- Rings must connect back to the base via obsidian (no floating rings)
- More rings = wider valid bookshelf area = room for more bookshelves = higher chance of better/multiple enchants

**Corner modifier rules:**
- Only the 3x3 base ring has functional corners (4 corner positions)
- If you want BOTH lapis (catalyst slot) AND diamond (increased range), you need to build two "tiers" -- a base ring with one modifier and a second ring with the other. Or split corners (e.g. 2 lapis + 2 diamond on the base).
- All 4 corners same type = maximum effect for that modifier
- Mixed corners = partial effect of each

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

### Beacon Rework [IDEA]
> Overhaul of the vanilla beacon system. Mineral block composition determines range. Inserted ore/ingot acts as fuel that depletes over time.

**Range by mineral block:**
The beacon's effective range is determined by what mineral blocks the pyramid is built from. Different mineral types provide different range multipliers. A pyramid can mix blocks -- total range is averaged or weighted by composition.

| Mineral Block | Range Tier | Notes |
|---------------|-----------|-------|
| Coal Block | Smallest | Budget option, wide availability |
| Iron Block | Low | Common, cheap |
| Copper Block | Low | Alternative to iron |
| Gold Block | Medium | Current vanilla material |
| Redstone Block | Medium | Alternative path |
| Lapis Block | Medium | |
| Amethyst Block | Medium-High | Harder to obtain in quantity |
| Diamond Block | High | Expensive but powerful |
| Emerald Block | High | Trade-gated |
| Netherite Block | Maximum | Extremely expensive, maximum range far exceeding current vanilla beacon |

**Fuel system:**
The ore/ingot inserted to activate the beacon is consumed as fuel over time. The beacon runs for a duration based on the fuel tier, then shuts off until refueled.

| Fuel Tier | Approximate Duration | Notes |
|-----------|---------------------|-------|
| Coal / Iron / Copper | Short (1-2 in-game days?) | Cheap, frequent replacement |
| Gold / Redstone / Lapis | Medium (3-5 days?) | Moderate investment |
| Diamond / Emerald | Long (10-15 days?) | Expensive, infrequent |
| Netherite Ingot | Very Long (25-30 days?) | Maximum duration |

**Design notes:**
- Transforms the beacon from "build and forget" into a maintenance system with ongoing resource demand.
- Creates meaningful material choices: cheap pyramid + cheap fuel (small range, frequent maintenance) vs expensive pyramid + expensive fuel (huge range, rare maintenance).
- Netherite beacon with netherite fuel is the ultimate flex -- massive range, runs for a month, costs a fortune.
- The fuel depletion should have visual/audio feedback: beam flickering, dimming light, warning sound when fuel is low. Grace period before shutoff so players can react.
- Compatible with the obsidian multiblock structural grammar -- the beacon pyramid IS the structure, mineral blocks ARE the modifiers.

---

### World Anchor [IDEA]
> Chunk-loading block. Wraps the ender pearl chunk-loading mechanic into a dedicated block with the obsidian multiblock structure for range scaling.

| Property | Value |
|----------|-------|
| ID | `world_anchor` |
| Hardness | TBD (obsidian-tier?) |
| Tool | Pickaxe (diamond minimum?) |
| Recipe | TBD (ender pearl + obsidian + ?) |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Chunk loading | [IDEA] | Keeps its chunk (and adjacent chunks based on structure size) loaded when placed and active. |
| Obsidian multiblock base | [IDEA] | Same structural grammar as Beacon Rework and Inhibitor/Promoter. Beacon-like obsidian pyramid beneath the World Anchor. More tiers = more chunks loaded. |
| Fuel/activation | [IDEA] | TBD -- ender pearls as fuel? Always active? Redstone-gated? |

**Structure scaling:**

| Structure Size | Chunks Loaded |
|----------------|--------------|
| No structure (bare block) | 1 chunk (the one it's in) |
| 1-tier obsidian base | 3x3 chunk area |
| 2-tier base | 5x5 chunk area |
| 3-tier base | 7x7 chunk area? |

---

### Magic Core [IDEA]
> Passive area-of-effect buff structure. When active, increases the effective level of certain enchantments on gear and extends the duration of active potion effects for players within its range. Part of the Magic Family.

| Property | Value |
|----------|-------|
| ID | `magic_core` |
| Hardness | TBD |
| Tool | Pickaxe |
| Recipe | TBD (obsidian + enchanted book + lapis/diamond? -- high-cost Magic Family craft) |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Enchantment boost | [IDEA] | While in range, certain enchantments on the player's equipped gear function at +1 level (or more, scaling with structure). E.g. Protection IV acts as Protection V. |
| Potion duration extension | [IDEA] | Active potion effects on the player decay slower (or not at all?) while in range. Extends effective potion duration without consuming more potions. |
| Obsidian multiblock base | [IDEA] | Same structural grammar as Beacon, Inhibitor/Promoter, World Anchor. Beacon-like obsidian pyramid with mineral block corners for range/power scaling. |
| Passive operation | [IDEA] | Always active once structure is valid. No fuel, no redstone. The structure IS the investment. |

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

## 10. LORE & WORLD DESIGN

*The overarching narrative of the mod world. Not delivered through cutscenes or text walls -- told through structures, books found as loot, mob behavior, biome conditions, and the state of the world itself. Players piece it together.*

---

### The Old World

An advanced civilization once existed. They were engineers, builders, explorers -- they mastered machines, magic, and dimensional travel. At their height, they built infrastructure spanning the entire world (the Ruined Bridges are what remain) and expanded into multiple dimensions.

Then they went to war.

The cause was the search for immortality. Different factions sought different methods. The war escalated until the surface became uninhabitable -- hence the stronghold, the ancient city, and the new underground structures we'll add being built deep underground, away from the devastation above.

Mobs as we know them were engineered weapons: creepers (living explosives), ravagers (war mounts, bred from wild stock), phantoms (aerial attackers), and others. Evidence of this is found in underground creature-pen structures -- degraded equipment, broken spawn infrastructure, fragments of technical documentation.

---

### Steve

Steve is the player character. He is a lich -- an immortal being who bound his own life force to the world itself, making the world his phylactery. He was one of the Old World's most advanced crafters. When his entire civilization destroyed itself, he succeeded where everyone else failed: he found immortality.

Then he went to sleep. For a very long time.

The player waking up at the start of a new world is Steve waking from this sleep. This explains why the player knows so much, can do so much, and starts from nothing: Steve remembers how to do everything, but the world has moved on. He has to rebuild.

The Familiar Notebooks/Diaries are Steve's own journals from before the sleep. Reading them is the player encountering their own past self -- a strange, unreliable narrator who is also you. The voice should feel intimate and slightly wrong, like reading a diary you don't remember writing.

---

### Herobrine

Steve had a brother. During the search for immortality, Steve attempted the procedure on his brother as well. It failed -- not completely, but wrongly. His brother was trapped between life and death: not alive, not dead, not corporeal, not gone. The diaries refer to him obliquely. Never by name. Steve's writing about his brother is the most fragmented and difficult section of the Familiar Notebooks -- grief, guilt, and something that might be fear.

Herobrine's nature (if he appears in the mod at all) is left ambiguous. He is a presence, a lore element, not necessarily a mob or encounter.

---

### The After

The After is a dimension that is a memory of the Old World. It looks like the overworld but with an ethereal filter -- ghostly colors, dark sky, the feel of somewhere that echoes what it once was. The After preserves what the overworld lost.

Blocks in The After do not drop themselves. Mining anything in The After drops Ghost Blocks or ectoplasm -- the world is made of memory, not matter. Ghost blocks CAN be brought back to the overworld and used as building material.

Ghosts are the common mob. In the cities, Revenants patrol -- armored, gear-carrying zombie-like ghosts at piglin brute strength. They are the echoes of the Old World's people, still occupying the spaces they lived in.

**Accessing The After:** A portal, similar in nature to the Nether portal. Construction method and materials TBD.

**The After as a resource dimension:**
- Ectoplasm farming (ghosts are common)
- Ghost block collection (mining yields ghost blocks)
- Lore (the best libraries and archives are in the After's cities)
- Danger (Revenants in city zones, hostile environment)

---

### Narrative Delivery (Books & Structures)

The lore is never stated directly in a loading screen or wiki entry. It is assembled by the player from:

- **Engineer's Diary** -- Technical perspective. Found in workshop ruins and underground structures. Talks about machines, the war effort, hints at what the mobs were built for.
- **Explorer's Manual** -- Expedition perspective. Found in portal ruins, The After. Talks about dimensional exploration, partial portal addresses, observations about what The After is.
- **Familiar Notebooks** -- Steve's own journals. Rare. Found in unique structures. Fragmented and personal. Reveals the most about the true history. Multiple volumes.

The books guide players toward content while layering lore on top. An Engineer's Diary might describe a machine and also mention "the council's decision to repurpose the production lines." An Explorer's Manual might give a portal address and also note that the address leads somewhere that "no longer exists the way I remember."

---

## 11. BRAINSTORM BIN

*Unorganized ideas. Move to proper sections when they take shape.*

-

---

## DESIGN PRINCIPLES

1. **Vanilla+** -- Enhance, don't replace. New features should feel like natural extensions of Minecraft.
2. **Interconnected systems** -- Features should interact with each other and with vanilla mechanics.
3. **Emergent gameplay** -- Design for consequences and combinations, not just individual features.
4. **Geological realism** -- The world should feel like it has underlying rules governing its geography and resources.
5. **Discovery-driven** -- Players should find and figure things out, not just follow recipes.
6. **Structural grammar** -- Consistent design language across infrastructure blocks. Obsidian = arcane base material. Mineral block corners = modifiers (lapis = utility, diamond = power/range). Concentric expansion = scaling. Players learn the pattern once, apply it everywhere (enchanting table, beacon, inhibitor, promoter, world anchor, magic core).
7. **Thematic families** -- Blocks are organized into Machine (iron/engineering), Odd (oddstone/wireless), and Magic (obsidian/arcane) families. Each has its own resource chain, aesthetic, and gameplay niche.
8. **World as story** -- The state of the world tells its own history. Biomes, structures, mob behavior, and the blocks themselves hint at what happened. No exposition dumps -- players read the world.

---

*This is a living document. Add ideas freely -- they'll get organized as they develop.*

*Written by Claude, directed and guided by The Ideomancer.*
