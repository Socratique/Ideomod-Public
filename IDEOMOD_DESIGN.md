# IDEOMOD DESIGN DOCUMENT
### A Vanilla+ Minecraft Overhaul
*Last updated: 2026-02-12*

---

## STATUS KEY

| Symbol | Meaning |
|--------|---------|
| ✅ | Implemented & working |
| 🔧 | Partially implemented / in progress |
| 📝 | Designed, not yet coded |
| 💡 | Idea / brainstorm — needs design |
| ❌ | Scrapped or on hold |

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

### Ideoblock ✅
> Decorative stone block. First test block.

| Property | Value |
|----------|-------|
| ID | `ideoblock` |
| Hardness | 1.5 / Blast 6.0 (cobblestone) |
| Tool | Pickaxe |
| Recipe | 9 cobblestone → 1 ideoblock |
| Notes | Placeholder. May become something or get removed. |

---

### Drill Block ✅
> Directional mining block. Redstone-activated 3×3 excavator.

| Property | Value |
|----------|-------|
| ID | `drill` |
| Placement | Piston-style (faces player look direction) |
| Activation | Redstone pulse → mines 3×3 in front |
| Drops | Mined items spawn at drill position |
| Limits | Skips bedrock / indestructible blocks |
| Movable | Yes (pistons can push it) |
| Recipe | Iron + piston + redstone (placeholder) |

**Known issues:**
- None currently

**Future ideas:**
- *💡 Tiered drills? Iron → Diamond → Netherite with larger areas or speed*
- *💡 Fortune/Silk Touch compatibility via adjacent enchanted block?*

---

### Oddstone ✅
> Strange stone with mob avoidance, redstone insulation, and sympathetic movement.

| Property | Value |
|----------|-------|
| ID | `oddstone` |
| Hardness | 2.0 / Blast 6.0 |
| Tool | Pickaxe |
| Recipe | 4 stone + 1 redstone → 4 oddstone (placeholder) |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Mob avoidance | ✅ | `isPathfindable=false` + Forge `DAMAGE_OTHER` hook. JSON config for per-mob control. |
| Redstone insulation | ✅ | `isRedstoneConductor=false` — acts like glass for redstone. |
| Sympathetic movement | ✅ | Wireless slime — each face searches for nearest Oddstone within radius (default 5). BFS chaining. Push chains count toward 12-block limit. |
| One-way stickiness | ✅ | Oddstone grabs slime; slime does NOT grab Oddstone. Via mixin + StickinessRules. |

**Known issues:**
- Sympathetic push chains don't trigger slime stickiness on pushed blocks
- Face search is axis-aligned only (no cone/hemisphere)
- Placeholder texture & recipe

**Future ideas:**
- *💡 Expand face search to half-sphere/cone per face*
- *💡 Non-placeholder texture (purple-grey stone currently)*
- *💡 Non-placeholder recipe (rarity intended to be greater than diamond — bottleneck for portal construction)*

---

### Machine Block ✅
> Mechanical building block with selective stickiness and redstone conduction.

| Property | Value |
|----------|-------|
| ID | `machine_block` |
| Hardness | 3.0 / Blast 6.0 |
| Tool | Pickaxe |
| Sound | Metal |
| Recipe | 4 iron + 4 redstone + 1 slime → 1 machine block (placeholder) |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Selective stickiness | ✅ | Sticky only to other Machine Blocks. Exclusive with slime & honey. |
| Redstone conduction | ✅ | Solid-state redstone dust. Signal decays -1 per block. Whitelist prevents feedback loops. |
| Power color tint | ✅ | Dark steel grey (0) → bright red (15). Runtime tint via color handlers. |
| One-way with slime | ✅ | Slime grabs Machine Block; Machine Block does NOT grab slime. Via mixin. |

**Known issues:**
- Placeholder texture (needs circuit/mechanical pixel art)
- Placeholder recipe

**Future ideas:**
- *💡 Particle effects when powered (redstone sparkles)*

---

### Framed Blocks 📝
> Any block wrapped in an iron or copper frame. Extends Machine Block stickiness as cheap "body" pieces.

| Property | Value |
|----------|-------|
| ID | `iron_framed_block`, `copper_framed_block` |
| Type | Block Entity (stores inner block ID as ResourceLocation) |
| Hardness | Inherits inner block + frame bonus? TBD |
| Tool | Pickaxe |
| Drops | Normal: original block + frame block. Silk Touch: framed block item (preserves combo). |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Inner block storage | 📝 | Block entity stores ResourceLocation of wrapped block. |
| Dynamic rendering | 📝 | Custom renderer composites inner block texture + frame overlay. 2 overlay textures total (iron, copper). Works with any block including modded. Reference: FramedBlocks mod. |
| Machine-activated stickiness | 📝 | Framed blocks are NOT sticky by default. When a Machine Block is part of a piston push group, it "activates" adjacent framed blocks, making them sticky for that move. Activation chains through matching framed blocks (iron→iron, copper→copper). Deactivates after move resolves. |
| Material exclusivity | 📝 | Iron framed sticks to iron framed only. Copper to copper only. Neither type sticks to the other. Both types stick to Machine Blocks when activated. |
| Slime/honey exclusivity | 📝 | Framed blocks do not interact with slime or honey stickiness. |

**Stickiness matrix (when activated by Machine Block):**

| | Machine Block | Iron Framed | Copper Framed | Slime | Honey |
|---|---|---|---|---|---|
| Iron Framed | ✓ | ✓ | ✗ | ✗ | ✗ |
| Copper Framed | ✓ | ✗ | ✓ | ✗ | ✗ |

**Activation propagation example:**
```
[Piston] [Machine] [IronFramed] [IronFramed] [IronFramed]
  push →   sticky → activated  → chains     → chains
                     (touches M)  (touches    (touches
                                   activated)  activated)
```
Without a Machine Block in the group, framed blocks are inert and get pushed individually.

**Known issues:**
- Activation chaining requires mixin pre-pass: scan outward from Machine Blocks, mark connected framed blocks as active, then let resolver run, then clear marks.

**Future ideas:**
- *💡 Copper frames oxidize over time? Waxable?*
- *💡 Different blast resistance per frame material?*

---

### Frame Blocks 📝
> Transparent decorative blocks — an empty iron or copper frame. Same stickiness rules as Framed Blocks.

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
| Decorative transparency | 📝 | Renders as an open frame/cage outline. Not a full solid block. |
| Machine-activated stickiness | 📝 | Same rules as Framed Blocks — activated by Machine Block presence in push group, chains with matching material type. |
| Material exclusivity | 📝 | Iron frames stick to iron frames and iron framed blocks. Copper to copper. Cross-material does not stick. |

**Notes:** Frame Blocks are essentially "empty" Framed Blocks. They share the same stickiness family. An Iron Frame and an Iron Framed Stone will stick to each other when activated.

---

### The Framer 📝
> Directional machine that wraps blocks in iron or copper frames.

| Property | Value |
|----------|-------|
| ID | `framer` |
| Placement | Directional (faces player look direction, like Drill) |
| Activation | Redstone pulse |
| GUI | Dispenser-style inventory (holds Frame Blocks) |
| Behavior | Consumes 1 Frame Block from inventory, converts block in front into corresponding framed variant |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Directional placement | 📝 | Extends DirectionalBlock + FACING property. |
| Redstone activation | 📝 | POWERED property + neighborChanged(). Pulse → frame one block. |
| Frame block inventory | 📝 | Small GUI (dispenser-style, 3×3 grid). Holds Iron Frame and/or Copper Frame blocks. |
| Block conversion | 📝 | On pulse: check block at FACING position. If frameable, replace with Framed Block entity storing original block ID. Consume 1 Frame Block matching the type used. |
| Frame type selection | 📝 | Uses first available Frame Block in inventory. Iron Frame → iron framed. Copper Frame → copper framed. |

**Conversion flow:**
```
[Framer→] [Oak Planks]  + redstone pulse + Iron Frame block in inventory
                         ↓
[Framer→] [Iron Framed Oak Planks]  (block entity storing "minecraft:oak_planks")
```

**Edge cases to decide:**
- What blocks CAN'T be framed? (Bedrock, block entities like chests, other framed blocks, air?)
- Can the Framer be pushed by pistons while loaded? (Probably yes — mobile framing station.)

**Future ideas:**

---

### [Template — Copy for New Blocks]
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
| Feature name | 💡 | Description |

---

## 2. ITEMS & TOOLS

*No standalone items yet. All current items are BlockItems.*

**Future ideas:**
- *💡 Tool/armor modifiers via multiblock crafting stations*
- *💡 Custom tool materials tied to geology system*

---

## 3. MECHANICAL SYSTEMS

### Piston Stickiness Framework ✅
> Mixin-based system for asymmetric (one-way) sticky block behavior.

| Component | File | Status |
|-----------|------|--------|
| StickinessRules registry | `StickinessRules.java` | ✅ |
| PistonStructureResolver mixin | `PistonStructureResolverMixin.java` | ✅ |
| Rule registration | `Ideomod.java → commonSetup` | ✅ |

**How it works:** Blocks register "grab immunity" rules as predicates. The mixin intercepts `addBlockLine()` in the piston resolver and checks rules before allowing a sticky block to grab its neighbor. This enables block A to grab block B without B grabbing A.

**Current rules:**
- Slime → Oddstone: **BLOCKED** (Oddstone is immune to slime grabs)
- Honey → Oddstone: **BLOCKED**
- Machine Block → Slime: **BLOCKED** (Machine Block won't drag slime)
- Machine Block → Honey: **BLOCKED**

---

### Sympathetic Movement ✅
> Wireless slime block system for Oddstone. See Oddstone block entry.

---

### Odd Slime 📝
> *Slime variant with directional grabbing behavior.*

**Design notes:**
- *Grabs only in specific directions?*
- *Needs design work*

---

### Oddstone Portals ✅
> Address-based overworld-to-overworld teleportation using Oddstone frames with mineral block corners.

| Property | Value |
|----------|-------|
| Frame | Rectangular ring of Oddstone, min 5×5 outer (3×3 interior) |
| Corners | Crafted mineral blocks in all 4 corners define the "address" |
| Address space | 14 corner types Ã— 4 positions = 14⁴ = 38,416 unique addresses per dimension |
| Activation | ALL Oddstone in frame must receive redstone signal simultaneously |
| Deactivation | Any frame Oddstone loses power, or any frame/corner block broken |
| Portal block | Light blue tinted nether portal texture, light level 11, no collision, indestructible |
| Travel cooldown | 4 seconds (vanilla portal cooldown) |

**Valid corner blocks:** Coal, Iron, Copper, Gold, Redstone, Lapis, Diamond, Emerald, Netherite, Quartz, Amethyst, Raw Iron, Raw Copper, Raw Gold (crafted *_block forms)

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Frame detection | ✅ | Scans from interior outward (nether portal style). Works from any frame block. |
| Corner address system | ✅ | Position-sensitive (TL/TR/BR/BL). Normalized so front/back produce same address. |
| Linked travel | ✅ | Teleports to partner portal. Relative position mapped as 0.0–1.0 fraction, scales across different-sized portals. |
| Unlinked scatter teleport | ✅ | Penalty: random 200–1000 blocks, surface landing, 5 hearts magic damage. Looks identical to linked portal. |
| Per-dimension registries | ✅ | Overworld and Nether store separate address spaces. Uses SavedData, persists across saves. |
| Pair exclusivity | ✅ | Third portal with same address refuses to light. |
| Redstone-gated activation | ✅ | All Oddstone must be powered. Prevents partial-frame lighting. |

**Known issues:**
- No nether portal screen overlay (vanilla's portalTime hardcoded to nether; needs custom networking)
- No custom particles yet
- Texture is recolored nether portal via tint (could do custom animated .mcmeta)
- Stale saved data from testing can block addresses (clear `ideomod_oddstone_portals.dat`)

**Future ideas:**
- *💡 Custom portal screen tint via network packet*
- *💡 Custom animated portal texture*
- *💡 Portal particles (light blue)*
- *💡 Naturally generated portal structures (see Structures section)*

---

## 4. WORLDGEN & GEOLOGY

### Continental Terrain 💡
> Large-scale terrain generation with elevation layers and realistic geography.

**Design notes:**
- Continental reshaping for more realistic landmasses
- Elevation layer system
- Custom river/lake generation
- Reference: ReTerraForged, TerraBlender

---

### Chunky Geology 💡
> Regional geological variation — ore veins, rock strata, resource geography.

**Design notes:**
- Regional ore distribution (not uniform random)
- Custom rock types / strata layers
- Resource geography — different regions have different resources

---

## 5. MOBS & AI

### Mob Difficulty Scaling 💡
> Mobs get harder based on distance from spawn / world age / dimension.

---

### Ghost Mobs 💡
> Mobs with phase mechanics — can pass through blocks under certain conditions.

---

## 6. MAGIC & ENCHANTING

### Enchanting Overhaul 💡
> Comprehensive rework of the vanilla enchanting system.

**Design notes:**
- *Details TBD*

---

## 7. STRUCTURES

### Ancient Oddstone Portal Ruins 💡
> Naturally generated structures containing lit or ruined Oddstone portals.

**Design notes:**
- Three discovery types: lit+linked (rarest), lit+unlinked, ruined (partially broken frame)
- Lore books hinting at addresses for other portals
- Ties into Oddstone Portal system (see Mechanical Systems → Oddstone Portals)
- Discovery-driven gameplay — exploration rewarded with portal network access

---

## 8. RECIPES & CRAFTING

### Multiblock Crafting Stations 💡
> Advanced crafting through physical multiblock structures.

**Design notes:**
- Tool/armor modifiers
- Ties into mechanical systems?

---

### Current Recipes (All Placeholder)

| Output | Recipe | Notes |
|--------|--------|-------|
| Ideoblock | 9 cobblestone | |
| Drill | Iron + piston + redstone | Shaped |
| Oddstone | 4 stone + 1 redstone → 4 | |
| Machine Block | 4 iron + 4 redstone + 1 slime | |

---

## 9. SYSTEMS & INFRASTRUCTURE

### Config System ✅
> JSON-based config for Oddstone (mob list, sympathetic radius).

| File | Location |
|------|----------|
| `oddstone.json` | `config/ideomod/oddstone.json` |

**Future:** May need a general config system as more features are added.

---

### Mixin Infrastructure ✅
> SpongePowered Mixin setup for modifying vanilla internals.

| File | Purpose |
|------|---------|
| `ideomod.mixins.json` | Mixin config |
| `PistonStructureResolverMixin.java` | One-way stickiness |

---

### Registry Pattern ✅
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

1. **Vanilla+** — Enhance, don't replace. New features should feel like natural extensions of Minecraft.
2. **Interconnected systems** — Features should interact with each other and with vanilla mechanics.
3. **Emergent gameplay** — Design for consequences and combinations, not just individual features.
4. **Geological realism** — The world should feel like it has underlying rules governing its geography and resources.
5. **Discovery-driven** — Players should find and figure things out, not just follow recipes.

---

*This is a living document. Add ideas freely — they'll get organized as they develop.*
