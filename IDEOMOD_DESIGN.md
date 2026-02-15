# IDEOMOD DESIGN DOCUMENT
### A Vanilla+ Minecraft Overhaul
<!-- NOTE: ASCII-only formatting. No emoji, no Unicode arrows/dashes/quotes. Use [DONE] [WIP] [DESIGNED] [IDEA] [HOLD] for status tags, -> for arrows, -- for dashes, x for multiplication. -->
*Last updated: 2026-02-15*

---

## STATUS KEY

| Symbol | Meaning |
|--------|---------|
| [DONE] | Implemented & working |
| [WIP] | Partially implemented / in progress |
| [DESIGNED] | Designed, not yet coded |
| [IDEA] | Idea / brainstorm -- needs design |
| [HOLD] | Scrapped or on hold |

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

**Known issues:**
- Sympathetic push chains don't trigger slime stickiness on pushed blocks
- Face search is axis-aligned only (no cone/hemisphere)
- Placeholder texture & recipe

**Future ideas:**
- *[IDEA] Expand face search to half-sphere/cone per face*
- *[IDEA] Non-placeholder texture (purple-grey stone currently)*
- *[IDEA] Oddstone as universal mod economy resource -- gates Portals, Inhibitors, Machine Blocks, Placers, Disenchanting Table (see Systems > Oddstone Economy)*

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

### Machine Block [DONE]
> Mechanical building block with selective stickiness and redstone conduction.

| Property | Value |
|----------|-------|
| ID | `machine_block` |
| Hardness | 3.0 / Blast 6.0 |
| Tool | Pickaxe |
| Sound | Metal |
| Recipe | 4 iron + 4 redstone + 1 slime -> 1 machine block (placeholder) |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Selective stickiness | [DONE] | Sticky only to other Machine Blocks. Exclusive with slime & honey. |
| Redstone conduction | [DONE] | Solid-state redstone dust. Signal decays -1 per block. Whitelist prevents feedback loops. |
| Power color tint | [DONE] | Dark steel grey (0) -> bright red (15). Runtime tint via color handlers. |
| One-way with slime | [DONE] | Slime grabs Machine Block; Machine Block does NOT grab slime. Via mixin. |

**Known issues:**
- Placeholder texture (needs circuit/mechanical pixel art)
- Placeholder recipe

**Future ideas:**
- *[IDEA] Particle effects when powered (redstone sparkles)*

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

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Inner block storage | [DESIGNED] | Block entity stores ResourceLocation of wrapped block. |
| Dynamic rendering | [DESIGNED] | Custom renderer composites inner block texture + frame overlay. 2 overlay textures total (iron, copper). Works with any block including modded. Reference: FramedBlocks mod. |
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

### The Framer [DESIGNED]
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
| Directional placement | [DESIGNED] | Extends DirectionalBlock + FACING property. |
| Redstone activation | [DESIGNED] | POWERED property + neighborChanged(). Pulse -> frame one block. |
| Frame block inventory | [DESIGNED] | Small GUI (dispenser-style, 3x3 grid). Holds Iron Frame and/or Copper Frame blocks. |
| Block conversion | [DESIGNED] | On pulse: check block at FACING position. If frameable, replace with Framed Block entity storing original block ID. Consume 1 Frame Block matching the type used. |
| Frame type selection | [DESIGNED] | Uses first available Frame Block in inventory. Iron Frame -> iron framed. Copper Frame -> copper framed. |

**Conversion flow:**
```
[Framer->] [Oak Planks]  + redstone pulse + Iron Frame block in inventory
                         v
[Framer->] [Iron Framed Oak Planks]  (block entity storing "minecraft:oak_planks")
```

**Edge cases to decide:**
- What blocks CAN'T be framed? (Bedrock, block entities like chests, other framed blocks, air?)
- Can the Framer be pushed by pistons while loaded? (Probably yes -- mobile framing station.)

**Future ideas:**

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
> Directional block that places blocks. Inverse of the Drill. Costs Oddstone to craft.

| Property | Value |
|----------|-------|
| ID | `placer` |
| Placement | Directional (faces player look direction, like Drill) |
| Activation | Redstone pulse |
| Behavior | Places a block from its inventory into the space in front of it |
| Recipe | Oddstone + ? (TBD) |

**Features:**

| Feature | Status | Notes |
|---------|--------|-------|
| Block placement | [IDEA] | Dispenser-style inventory. Pulse -> place one block in facing direction. |
| Directional | [IDEA] | Extends DirectionalBlock + FACING property, like Drill. |

**Design notes:**
- Paired with Drill as the other half of automated building (Drill removes, Placer adds).
- Oddstone cost puts it in the mid-game, gated by Oddstone economy.

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

*No standalone items yet. All current items are BlockItems.*

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
- Machine Block -> Slime: **BLOCKED** (Machine Block won't drag slime)
- Machine Block -> Honey: **BLOCKED**
- Machine Block -> Odd Slime: **BLOCKED**
- Slime -> Odd Slime: **BLOCKED** (mutual exclusion via canStickTo)
- Honey -> Odd Slime: **BLOCKED** (mutual exclusion via canStickTo)
- Odd Slime directional rule: stickiness disabled when pushDirection != FACING

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
| Address space | 14 corner types ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢-- 4 positions = 14^4 = 38,416 unique addresses per dimension |
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
| Machine Block | Low-Medium (TBD) | Core mechanical component. Frequent use in builds. |
| Placer Block | Medium (TBD) | Automation tool. |
| Oddstone Repulser/Attractor | Low-Medium (TBD) | Ranged Oddstone-only pistons. Oddstone + piston base. |
| Arcane Repulser/Attractor | Medium (TBD) | Universal ranged pistons. Oddstone + obsidian + piston base. |
| Disenchanting Table | Medium + Heavy Core | One-time craft. Ties into Trial Chambers. |

**Design principle:** Total demand should outpace supply enough to force meaningful choices, but not so much that players feel starved and frustrated. A rough cost table should be sketched out before committing to recipe numbers.

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

### Multiblock Structure Framework [IDEA]
> Registration-based system for detecting, tracking, and evaluating multiblock structures. Structures are world data managed by a Structure Manager -- they are not stored on or owned by any individual block. Ordinary vanilla and modded blocks participate in structures without needing custom code.

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

**Machine Furnace Array:**

| Property | Value |
|----------|-------|
| Core block | Furnace (also blast furnace, smoker) |
| Connectors | Stone, cobblestone, stone bricks, deepslate, etc. (frame material) |
| Contributors | Machine Core block (speedBonus: +0.5x per core), Machine Block (speedBonus: +0.1x) |
| Effectors | The furnaces themselves |
| Ignored | Air (interior space) |
| Spatial validation | Furnaces must have at least 1 clear (air) face |
| Effect | Furnace smelt speed multiplied by total speedBonus |

Build: Place furnaces. Surround with stone frame. Add Machine Cores to the frame for speed. More cores = faster smelting. Furnaces need open faces to work.

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

- Structure formation/deformation: sound effect + particle burst on Core.
- Comparator output from Core: signal strength proportional to structure completeness (0 = invalid, 1-15 = scaled).
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

| Phase | What |
|-------|------|
| 1 | Structure Manager saved data + Scanner utility (flood scan only) + Structure Result data class |
| 2 | First structure: Machine Furnace Array (simplest useful case, uses existing blocks) |
| 3 | Villager Workshop structure (tests spatial validation, complex contributor rules) |
| 4 | Pattern scan mode + a shaped structure to test it |
| 5 | Polish: comparator output, particles, GUI panels, visual debug mode |
| 6 | Data-driven: refactor rulesets from Java to JSON for moddability |

---

**Open questions:**
- *How does the Manager handle chunk boundaries? If a structure spans two chunks and one unloads, structure should probably stay registered but flag as "partially unloaded" and skip effect application until fully loaded.*
- *Should there be a creative/debug tool for visualizing registered structures? (highlight all component blocks, show bounding box, display contributor totals)*
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
