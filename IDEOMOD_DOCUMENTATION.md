# IDEOMOD TECHNICAL DOCUMENTATION
*Comprehensive reference for the Ideomod codebase -- architecture, systems, templates, and implementation details.*
*Last updated: 2026-03-15b (Removed REDSTONE_COMPONENTS tag -- redundant after Track redstone isolation + Conductor block. Tracks now accept signal from any vanilla source via level.getSignal().)*

*Written by Claude, directed and guided by The Ideomancer.*

---

## TABLE OF CONTENTS

1. [Project Setup](#1-project-setup)
2. [Package Structure](#2-package-structure)
3. [Registry System](#3-registry-system)
4. [Stickiness System](#4-stickiness-system)
5. [PistonStructureResolver Mixin](#5-pistonstructureresolver-mixin)
6. [Block Movement System](#6-block-movement-system)
7. [Portal System](#7-portal-system)
8. [Multiblock Structure Framework](#8-multiblock-structure-framework)
9. [Block Specifications](#9-block-specifications)
10. [Judgement Effect](#10-judgement-effect)
11. [Configuration](#11-configuration)
12. [Resource File Templates](#12-resource-file-templates)
13. [Tag Contents](#13-tag-contents)
14. [Known Issues & Technical Debt](#14-known-issues--technical-debt)
15. [Conventions & Patterns](#15-conventions--patterns)
16. [Adding New Content Checklists](#16-adding-new-content-checklists)

---

## 1. PROJECT SETUP

| Field | Value |
|-------|-------|
| Mod ID | `ideomod` |
| Package | `com.ideomancer.ideomod` |
| Minecraft | 1.20.1 |
| Forge | 47.4.10 |
| Java | 17 |
| Mappings | official 1.20.1 |
| Mixin | SpongePowered 0.7.x via `org.spongepowered.mixin` Gradle plugin |

Build config files: `build.gradle`, `settings.gradle`, `gradle.properties`, `mods.toml`

Mixin config: `ideomod.mixins.json` in resources root. Declares `PistonStructureResolverMixin`, `AbstractFurnaceBlockEntityMixin`, `PistonBaseBlockMixin`, `PistonMovingBlockEntityMixin`, `EntityMovementMixin`, `client.PistonBaseBlockClientMixin`, and `client.PistonMovingBlockEntityRendererMixin` in the `mixins` array. Note: all mixins are in the common array — Forge dev environment does not reliably process the `"client"` JSON array. Client mixins use `isClientSide()` guards or target client-only classes. Refmap: `ideomod.refmap.json`. Compatibility level: JAVA_17.

gradle.properties values: `mod_id=ideomod`, `mod_name=Ideomod`, `mod_version=0.1.0`, `mod_group_id=com.ideomancer.ideomod`, `mod_authors=Ideomancer`.

---

## 2. PACKAGE STRUCTURE

```
com.ideomancer.ideomod
  |-- Ideomod.java              Main mod class. Registers DeferredRegisters, event
  |                              buses, stickiness rules, structure definitions,
  |                              and StructureManager event handler.
  |-- block/
  |     |-- OddstoneBlock.java           Core block: 6 features (see section 9)
  |     |-- OddSlimeBlock.java           Directional slime (FACING-gated stickiness)
  |     |-- DrillBlock.java              3x3 redstone miner [ON HOLD]
  |     |-- frame/
  |     |     |-- IFrameBlock.java             Interface: getFrameMaterial(). Frame Family marker. Static isActive(BlockState) helper.
  |     |     |-- FrameMaterial.java           Data-carrying enum: IRON, COPPER. displayName, registryPrefix, item registry.
  |     |     |-- FrameBlock.java              Base frame block. BooleanProperty ACTIVE + WATERLOGGED.
  |     |     |                                Implements SimpleWaterloggedBlock. Passive conductor.
  |     |     |-- FrameActivation.java         Static utility: batched activation for frame networks.
  |     |     |                                markDirty() + end-of-tick processing via TickHandler.
  |     |     |-- FrameabilityHelper.java      Static checks for whether a block can be framed
  |     |     |-- FramedBlock.java             IFrameBlock + EntityBlock proxy for inner block
  |     |     |-- FramedBlockEntity.java       Stores inner block ResourceLocation. IPistonMovable.
  |     |     |-- FramedBlockItem.java         Dynamic getName() using frameMaterial.getDisplayName(). NBT stack creation via frameMaterial.getFramedBlockItem().
  |     |     |-- FramedBlockRecipe.java       CustomRecipe: frame + block = framed block with NBT
  |     |     \-- FramedBlockLoot.java         Helper for manual drop creation
  |     |-- machine/
  |     |     |-- IMachineFamily.java          Interface: getMachineType() + canMachineFamilyStickTo(type, state).
  |     |     |-- MachineType.java             Enum: FRAME, TRACK, TROLLEY, etc. canStickToMachineType().
  |     |     |-- AbstractMachineBlock.java    Base class for all Machine Family blocks. Shared lifecycle,
  |     |     |                                GUI, drops, stickiness, frame activation markDirty().
  |     |     |-- MachineSwapHelper.java       Swap utility: item<->block registry, checkAndSwap(),
  |     |     |                                createPreloadedStack(), createDrop(), createCloneStack().
  |     |     |                                Client sync via syncToClients() after swap.
  |     |     |-- PositionBoundContainer.java  Position-bound Container. Auto-resolves to current BE.
  |     |     |-- MachineFrameBlock.java       Extends AbstractMachineBlock. MachineType.FRAME. ~40 lines.
  |     |     |-- MachineFrameBlockEntity.java Shared BE for all machine blocks. Single slot + IPistonMovable.
  |     |     |                                Client sync: getUpdatePacket(), getUpdateTag(), syncToClients().
  |     |     |-- MachineFrameMenu.java        Position-bound menu. PositionBoundContainer on both sides.
  |     |     |-- MachineTrackBlock.java       Extends AbstractMachineBlock. MachineType.TRACK. Custom redstone + push.
  |     |     |-- MachineTrolleyBlock.java     Extends AbstractMachineBlock. MachineType.TROLLEY. ~20 lines.
  |     |     \-- MachineConductorBlock.java   Extends AbstractMachineBlock. MachineType.CONDUCTOR. Custom redstone.
  |     |-- movement/
  |     |     |-- IPistonMovable.java          Interface: saveMovementData/loadMovementData
  |     |     |-- MovingBlockEntityData.java   Static registry for BE data in transit during moves
  |     |     |-- BlockMoveHelper.java         General-purpose piston-style block movement
  |     |     |-- StickyType.java              Enum: single source of truth for sticky block classification.
  |     |     |-- MovementScanHelper.java      Utility: block state lookup + movement resolution helpers.
  |     |     \-- StickinessRules.java         Rule registry: grab immunity + directional. Helper methods.
  |     |-- piston/
  |     |     |-- AbstractRangedPistonBlock.java  Shared base for Repulser/Attractor variants
  |     |     |-- OddstoneRepulserBlock.java   Pushes Oddstone at range (extends Abstract)
  |     |     |-- OddstoneAttractorBlock.java  Pulls Oddstone at range (extends Abstract)
  |     |     |-- RangedPistonUtil.java        Scanning + dispatch for Repulser/Attractor
  |     |     \-- RangedPistonFlags.java       ThreadLocal for resolver startPos redirect
  |     \-- portal/
  |           |-- OddstonePortalBlock.java     Portal interior fill + teleportation logic
  |           |-- OddstonePortalHandler.java   Portal activation/deactivation lifecycle
  |           |-- OddstonePortalFrameDetector.java  Frame scanning, validation, address reading
  |           |-- OddstonePortalSavedData.java      Per-dimension portal registry (NBT)
  |           |-- PortalGeometry.java          Portal dimensions, corners, axis data structure.
  |           |-- PortalUtils.java             Corner validation, interior nav, clearing helpers
  |           \-- EnderPearlPortalHandler.java Ender pearl portal activation (Forge event handler)
  |-- client/
  |     |-- ModColorHandlers.java        Block/item color tinting (tintIndex system)
  |     |-- FramedBlockDebugOverlay.java F3 debug overlay for inner block info
  |     |-- gui/
  |     |     \-- MachineFrameScreen.java      GUI screen for Machine Frame
  |     \-- render/
  |           |-- FramedBlockRenderer.java     BESR for framed blocks (inner texture at rest)
  |           |-- FramedBlockItemModel.java    Custom BakedModel for framed items (inner texture in inventory)
  |           |-- FramedBlockRenderTypes.java  Custom RenderType for framed block overlays
  |           \-- MovingFrameData.java         Client-side cache for inner block IDs during piston transit
  |-- config/
  |     \-- OddstoneConfig.java          JSON config: sympathetic radius + mob list
  |-- effect/
  |     \-- JudgementEffect.java         Rare mob-stare event (Forge event bus)
  |-- mixin/
  |     |-- PistonStructureResolverMixin.java    5-feature mixin (see section 5)
  |     |-- PistonBaseBlockMixin.java            Allows IPistonMovable BE movement + saves data + immovable inner
  |     |-- PistonMovingBlockEntityMixin.java    Restores IPistonMovable data after animation
  |     |-- EntityMovementMixin.java             Entity speed/jump factor for framed blocks
  |     |-- AbstractFurnaceBlockEntityMixin.java  Furnace array speed boost (section 8)
  |     \-- client/
  |           |-- PistonBaseBlockClientMixin.java           Captures framed block data for transit rendering
  |           \-- PistonMovingBlockEntityRendererMixin.java  Renders inner block during piston animation
  |-- multiblock/
  |     |-- StructureDefinition.java     Abstract base for structure type rulesets
  |     |-- StructureScanner.java        Strategy-based dispatcher for pattern scanning
  |     |-- ScanStrategy.java            Interface: core + frame scanning implementations
  |     |-- PatternScanStrategy.java     Furnace array 2D pattern scanning implementation
  |     |-- StructureResult.java         Immutable scan output (builder pattern)
  |     |-- StructureRegistration.java   Persistent registration entry (UUID, NBT)
  |     |-- StructureManager.java        Per-dimension SavedData + events + queries
  |     |-- StructureEffectHandler.java  Interface for per-type effects
  |     |-- definitions/
  |     |     \-- FurnaceArrayDefinition.java  Furnace array ruleset (cores, frame, sizing)
  |     \-- effects/
  |           \-- FurnaceArrayEffect.java      GUI rename on formation/removal
  \-- registry/
        |-- ModBlocks.java               DeferredRegister<Block> -- all block registrations
        |-- ModItems.java                DeferredRegister<Item> -- BlockItems + debug stick
        |-- ModCreativeTabs.java         Creative tab with mod items
        |-- ModCommands.java             /judgement command (Forge event bus)
        |-- ModRecipeSerializers.java    DeferredRegister<RecipeSerializer<?>>
        |-- ModBlockEntities.java        DeferredRegister<BlockEntityType<?>>
        |-- ModMenuTypes.java            DeferredRegister<MenuType<?>>
        \-- ModTags.java                 TagKey definitions for custom tags
```

---

## 3. REGISTRY SYSTEM

All registrations use `DeferredRegister` pattern. Registers are created in their respective classes and wired to the mod event bus in `Ideomod()` constructor.

### ModBlocks (DeferredRegister<Block>)

| Registry Name | Class | Key Properties |
|--------------|-------|----------------|
| `ideoblock` | Block | Basic decorative stone. Hardness 1.5/6.0, pickaxe. |
| `drill` | DrillBlock | DirectionalBlock. FACING + POWERED. Hardness 3.5/6.0, pickaxe. **ON HOLD.** |
| `oddstone` | OddstoneBlock | Hardness 2.0/6.0, pickaxe, stone sound. `isRedstoneConductor=false`. |
| `machine_block` | MachineFrameBlock | Block entity. Hardness 3.0/6.0, pickaxe, metal sound. `isRedstoneConductor=false`. `PushReaction.NORMAL` (movable by pistons via PistonBaseBlockMixin). |
| `odd_slime` | OddSlimeBlock | Friction 0.8F (slippery), slime sound, `noOcclusion()`. |
| `oddstone_portal` | OddstonePortalBlock | `noCollission()`, hardness -1 (indestructible), light 11, `PushReaction.BLOCK`, glass sound, `noLootTable()`. |
| `oddstone_repulser` | OddstoneRepulserBlock | Hardness 3.0/6.0, pickaxe, stone sound. Extends AbstractRangedPistonBlock. |
| `oddstone_attractor` | OddstoneAttractorBlock | Same properties as Repulser. Extends AbstractRangedPistonBlock. |
| `machine_track` | MachineTrackBlock | Hardness 3.0/6.0, pickaxe, metal sound. `isRedstoneConductor=false`. Machine Family stickiness + redstone conduction. |
| `machine_trolley` | MachineTrolleyBlock | Hardness 3.0/6.0, pickaxe, metal sound. Machine Family stickiness. No conduction. |
| `machine_conductor` | MachineConductorBlock | Hardness 3.0/6.0, pickaxe, metal sound. `isRedstoneConductor=false`. Machine Family stickiness + vanilla redstone signal (0-15). |
| `iron_frame` | FrameBlock | FrameMaterial.IRON, strength 3.0/6.0, pickaxe, metal sound. `noOcclusion()`. Waterloggable (SimpleWaterloggedBlock). |
| `copper_frame` | FrameBlock | FrameMaterial.COPPER, strength 2.5/6.0, pickaxe, copper sound. `noOcclusion()`. Waterloggable (SimpleWaterloggedBlock). |
| `iron_framed_block` | FramedBlock | FrameMaterial.IRON, shared `FRAMED_REDSTONE_CONDUCTOR` predicate, `lightLevel` reads INNER_LIGHT. EntityBlock. `noOcclusion()`. Properties: ACTIVE, FACING, INNER_STICKY, INNER_CONDUCTOR, INNER_LIGHT. |
| `copper_framed_block` | FramedBlock | FrameMaterial.COPPER, shared `FRAMED_REDSTONE_CONDUCTOR` predicate, `lightLevel` reads INNER_LIGHT. EntityBlock. `noOcclusion()`. Properties: ACTIVE, FACING, INNER_STICKY, INNER_CONDUCTOR, INNER_LIGHT. |

### ModItems (DeferredRegister<Item>)

BlockItems for every block EXCEPT `oddstone_portal` (not player-placeable, same pattern as vanilla nether portal). All use `new Item.Properties()` with no special settings. Includes: ideoblock, drill, oddstone, machine_block, odd_slime, oddstone_repulser, oddstone_attractor, machine_track, machine_trolley, iron_frame, copper_frame.

BlockItem: `MACHINE_CONDUCTOR` for MachineConductorBlock.

Special items: `IRON_FRAMED_BLOCK` and `COPPER_FRAMED_BLOCK` are `FramedBlockItem` instances (custom BlockItem with dynamic getName and NBT support). `STRUCTURE_DEBUG_STICK` is a standalone Item (not a BlockItem), `stacksTo(1)`.

Additionally: `STRUCTURE_DEBUG_STICK` -- standalone Item (not a BlockItem), `stacksTo(1)`. Hold in either hand to see structure register/deregister chat messages.

### ModCreativeTabs

Two tabs:
- `ideomod_tab`: icon = IDEOBLOCK. Lists all standard mod items (Ideoblock, Drill, Oddstone, Machine Block, Odd Slime, Repulser, Attractor, Machine Track, Machine Trolley, Iron Frame, Copper Frame, Structure Debug Stick).
- `framed_blocks_tab`: icon = Iron Framed Stone. Dynamically populated -- caches frameable blocks list on first open (via `FrameabilityHelper.isFrameable()`), creates iron + copper framed variants for each valid block. Includes modded blocks automatically.

### ModTags

Four custom block tags, all with TagKey constants in ModTags.java:

| Tag | Purpose | TagKey in ModTags? | JSON Location |
|-----|---------|-------------------|---------------|
| `PORTAL_CORNER_BLOCKS` | Valid portal frame corner types (defines addresses) | YES | `data/ideomod/tags/blocks/portal_corner_blocks.json` |
| `FURNACE_ARRAY_FRAME` | Valid frame blocks for furnace array multiblock | YES (added 2026-03-13). FurnaceArrayDefinition still uses inline ref — should be updated. | `data/ideomod/tags/blocks/furnace_array_frame.json` |
| `IMMOVABLE_WHEN_FRAMED` | Blocks that should be immovable when wrapped in a FramedBlock. Used by PistonBaseBlockMixin Feature 4. Extensible by datapacks. DestroySpeed == -1 fallback still runs for unlisted indestructible blocks. | YES (added 2026-03-14) | `data/ideomod/tags/blocks/immovable_when_framed.json` |
| `FRAMEABLE_BLOCKS` | Whitelist of blocks that can be wrapped in frames (~310 vanilla + 2 ideomod blocks) | YES | `data/ideomod/tags/blocks/frameable_blocks.json` |

See section 13 for the full contents of each tag.

### ModCommands

Registered on the FORGE event bus (not MOD bus) via `@Mod.EventBusSubscriber`. Listens for `RegisterCommandsEvent`.

`/judgement` -- requires op level 2. Calls `JudgementEffect.trigger(player)` directly (bypasses the 1/200 chance roll).

---

## 4. STICKINESS SYSTEM

This is the most architecturally complex system in the mod. It enables one-way (asymmetric) and direction-dependent stickiness between blocks when pushed by pistons.

### The Core Problem

Forge checks `canStickTo` **bidirectionally**. When block A tries to stick to block B, BOTH `A.canStickTo(B)` AND `B.canStickTo(A)` must return true. If either returns false, the stick fails *before* any mixin code runs. This means you cannot express one-way stickiness through `canStickTo` alone -- if Machine Block says "no" to slime, then slime also can't grab Machine Block.

### Three-Layer Architecture

**Layer 1 -- canStickTo() (PERMISSIVE)**
Lives in each block class. Returns true for any block pair where interaction is desired in EITHER direction. Only returns false for true mutual exclusions where neither side should ever stick (like slime vs honey in vanilla, or slime vs odd slime).

**Layer 2 -- Grab Immunity Rules (RESTRICTIVE)**
Lives in `StickinessRules.java`, registered in `Ideomod.registerStickinessRules()`, enforced by `PistonStructureResolverMixin`. Each rule is a `BiPredicate<BlockState, BlockState>` that tests `(grabber, target) -> should block?`. Runs after canStickTo passes. Blocks specific grabber->target pairs.

**Layer 3 -- Directional Rules (CONDITIONAL)**
Also in `StickinessRules.java`. Each rule is a `BiPredicate<BlockState, Direction>` that tests `(blockState, pushDirection) -> should disable stickiness?`. Used for Odd Slime, which is only sticky when pushed in its FACING direction. When a directional rule fires, the block acts completely non-sticky for that push -- it won't grab ANY neighbors.

### StickyType Enum

The `StickyType` enum (in `block/movement/StickyType.java`) serves as the single source of truth for classifying sticky blocks. Values: NONE, SLIME, HONEY, ODD_SLIME, ODDSTONE. This enum centralizes logic that was previously scattered across multiple block classes and rules. Methods like `StickyType.of(BlockState)`, `StickyType.canGrab(BlockState)`, and `StickyType.canBeGrabbedBy()` encapsulate stickiness behavior, reducing code duplication and making rule logic more maintainable.

### The Golden Rule

> `canStickTo` = "CAN these types interact?" (permissive, both sides must agree)
> Grab immunity = "WHO is allowed to initiate?" (restrictive, blocks specific grabbers)
> Directional rules = "WHEN is the block sticky?" (based on push direction)

### Stickiness Matrix

Rows = grabber, columns = target. "Machine Fam" = Machine Frame, Machine Track, Machine Trolley. "Frame Fam" = Iron Frame, Copper Frame, Iron/Copper Framed Blocks. Frame Family shown as one row for base frame behavior since both materials share the same grab immunity rules.

*Note: This is the overview matrix with collapsed columns. See CODEBASE_REFERENCE.md and STICKINESS_SYSTEM.md for the detailed matrix with Machine Frame / Same-Type Spec / Diff-Type Spec / Vanilla Metal columns separated.*

|              | Slime | Honey | Odd Slime | Machine Fam | Frame Fam | Oddstone | Normal |
|:-------------|:-----:|:-----:|:---------:|:-----------:|:---------:|:--------:|:------:|
| **Slime**    |  YES  |  no*  |    no*    |     YES     |    YES    |    no    |  YES   |
| **Honey**    |  no*  |  YES  |    no*    |     YES     |    YES    |    no    |  YES   |
| **Odd Slime**|  no*  |  no*  |  YES (d)  |   YES (d)   |  YES (d)  |    no    | YES (d)|
| **Machine Fam**| no  |  no   |    no     |     YES     |    YES    |    no    |   no   |
| **Frame Fam (active)**| no | no | no     |     YES     |  YES (m)  |    no    |   no   |
| **Framed Slime (active)**| YES | no* | no* | YES | YES (m) | no | YES |
| **Framed Honey (active)**| no* | YES | no* | YES | YES (m) | no | YES |
| **Framed Odd Slime (active)**| no* | no* | YES (d) | YES | YES (m) | no | YES (d) |
| **Framed Oddstone (active)**| YES | YES | YES | YES | YES (m) | YES | no |
| **Framed Oddstone (inactive)**| YES | YES | YES | no | no | YES | no |
| **Framed Sticky (inactive)**| (i) | (i) | (i) | no | no | no | (i) |
| **Oddstone** |  YES  |  YES  |    YES    |      no     |    no     |   YES    |   no   |

Legend: `no*` = mutual canStickTo exclusion (Layer 1) | `no` = grab immunity rule (Layer 2) | `(d)` = only when pushed in FACING direction (Layer 3) | `(m)` = material-matched (iron<->iron, copper<->copper via canStickTo) | `(i)` = inner sticky behavior only (Framed Slime grabs like slime, Framed Honey like honey, Framed Odd Slime like odd slime directional) | Frame Fam only sticky when ACTIVE=true | Framed Sticky (active) has BOTH inner stickiness + frame stickiness | Framed Sticky (inactive) has inner stickiness only | Framed Oddstone (active) has both oddstone inner stickiness + frame stickiness | Framed Oddstone (inactive) has oddstone inner stickiness only (grabs slime/honey/odd_slime/oddstone but NOT machine fam or frame fam) | "Normal" = any non-sticky block

### Currently Registered Rules

**Grab immunity rules** (registered in `Ideomod.registerStickinessRules()`, consolidated):

| # | Grabber | Target | Effect |
|---|---------|--------|--------|
| 1 | Any non-Oddstone block | OddstoneBlock, FramedBlock(ODDSTONE) | Nothing except Oddstone can grab Oddstone (real or framed). Carve-out A: oddstone-type grabbers (real or framed) exempt. Carve-out B (framed oddstone targets only): Machine Family and ACTIVE same-material IFrameBlock exempt. |
| 2 | Machine Family* | SlimeBlock, HoneyBlock, OddSlimeBlock | Machine Family can't grab sticky blocks. Uses `IMachineFamily.isMachineFamily()`. |
| 3 | SlimeBlock, HoneyBlock | OddSlimeBlock | Slime/honey can't grab Odd Slime (mutual exclusion enforcement) |
| 4 | Frame Family** | SlimeBlock, HoneyBlock, OddSlimeBlock, OddstoneBlock, FramedBlock(ODDSTONE) | Frames can't grab sticky blocks. Carve-outs: (a) grabber INNER_STICKY matches target type, (b) grabber INNER_STICKY=ODDSTONE (universal grabber for slime/honey/odd_slime targets), (c) ACTIVE same-material IFrameBlock for framed oddstone targets. |

*Machine Family = MachineFrameBlock, MachineTrackBlock, MachineTrolleyBlock. All implement `IMachineFamily`.
**Frame Family = Any IFrameBlock (FrameBlock and FramedBlock). Rule 4 uses `StickinessRules.isFrameFamily()`. Rule 4 carve-outs: matching inner type, ODDSTONE universal grabber, and active same-material frame connection for framed oddstone targets.

**Directional rules:**

| # | Block | Condition | Effect |
|---|-------|-----------|--------|
| 1 | OddSlimeBlock | pushDirection != FACING | All stickiness disabled |

### canStickTo Implementations (Layer 1)

**OddstoneBlock**: Returns true for SlimeBlock, HoneyBlock, OddSlimeBlock, OddstoneBlock, and FramedBlock with INNER_STICKY matching any of SLIME/HONEY/ODD_SLIME/ODDSTONE. Now uses `StickyType.of()` to classify blocks and `StickyType.ODDSTONE.canGrab()` for grab decisions. False for everything else (including MachineBlock, Frame Family without sticky inner).

**Machine Family (MachineFrameBlock, MachineTrackBlock, MachineTrolleyBlock)**: All implement IMachineFamily with a MachineType. `canStickTo()` delegates to `IMachineFamily.canMachineFamilyStickTo(myType, other)`. Machine Frame (MachineType.FRAME) returns true for ANY Machine Family block. Specializations return true only for same-type + FRAME. All types return true for Frame Family (via `FrameActivation.isActive(other)`), SlimeBlock, HoneyBlock, OddSlimeBlock. False for everything else. Inter-specialization isolation (Track doesn't stick to Trolley) handled at Layer 1, not by grab immunity.

**OddSlimeBlock**: Returns false for SlimeBlock, HoneyBlock (mutual exclusion). Returns true for everything else (Oddstone, MachineBlock, OddSlimeBlock, normal blocks). Asymmetric behaviors handled by immunity rules + directional rules.

**Frame Family (FrameBlock, FramedBlock)**: Both implement IFrameBlock. FrameBlock.canStickTo() returns true for same-material IFrameBlock, Machine Family, SlimeBlock, HoneyBlock, OddSlimeBlock, and FramedBlock with INNER_STICKY>0 (permissive — allows cross-material framed sticky blocks to grab empty frames). Cross-material IFrameBlock falls through (does NOT short-circuit to false) so inner stickiness permissive entries can still match. FramedBlock.canStickTo() combines two stickiness systems: (1) Frame rules (ACTIVE-gated): same-material IFrameBlock + Machine Family. Cross-material IFrameBlock falls through to inner sticky checks. (2) Inner sticky rules (INNER_STICKY-gated): applies the vanilla stickiness of the inner block type with mutual exclusions (e.g., INNER_STICKY=SLIME excludes honey and odd slime). Now simplified via StickyType: INNER_STICKY=ODDSTONE mirrors OddstoneBlock behavior: grabs all slime types (real+framed), oddstone (real+framed), but NOT normal blocks or machine family via inner. Permissive entries: SlimeBlock, HoneyBlock, OddSlimeBlock, OddstoneBlock, and FramedBlock with INNER_STICKY>0. Grab immunity Rules 1 and 4 enforce one-way behavior with carve-outs for matching inner types, oddstone universal grabber, and active same-material frame connections.

---

## 5. PISTONSTRUCTURERESOLVER MIXIN

File: `com.ideomancer.ideomod.mixin.PistonStructureResolverMixin`
Target: `net.minecraft.world.level.block.piston.PistonStructureResolver`

This is the most complex and sensitive file in the project. It implements four features through targeted injection into vanilla's piston movement resolver.

### Shadowed Fields

| Field | Type | Purpose |
|-------|------|---------|
| `level` | Level | World reference for blockstate lookups |
| `toPush` | List<BlockPos> | The resolver's list of blocks to move |
| `pushDirection` | Direction | Which way the piston is pushing |
| `startPos` | BlockPos | First block to check. Shadowed with `@Mutable` so Feature 4 can override it. |

Shadowed method: `addBlockLine(BlockPos pos, Direction dir)` -- the resolver's own method for adding a line of blocks starting from `pos`.

### Custom Fields

`ideomod$inBranching` -- `ThreadLocal<Boolean>`, defaults to false. Set true at HEAD of `addBranchingBlocks`, set false at TAIL. Used to distinguish between two phases of the resolver that both call `addBlockLine`:

1. **Line-building phase**: `resolve()` calls `addBlockLine()` directly to build the initial push line. This is passive chain cohesion -- blocks already in line stay connected.
2. **Branching phase**: `resolve()` calls `addBranchingBlocks()`, which iterates every sticky block in `toPush` and checks its 6 neighbors. For each neighbor not already in the list, it calls `addBlockLine()`. This is active grabbing -- sticky blocks reaching out.

The distinction matters because grab immunity rules apply at full strength during branching (active grabbing) but only on the first of each call pair during line-building (see the backward walk section below).

`ideomod$firstCanStickToCall` -- `ThreadLocal<Boolean>`, defaults to true. Tracks which `canStickTo` call in a pair we're on during the backward walk. Vanilla's backward walk evaluates `oldState.canStickTo(blockstate) && blockstate.canStickTo(oldState)` -- two calls per iteration. The toggle flips on each call:

- **Call 1** (true): `self=front block (oldState)`, `other=behind block (blockstate)`. The front block is the active grabber reaching backward. Grab immunity IS checked.
- **Call 2** (false): `self=behind block (blockstate)`, `other=front block (oldState)`. The behind block is passively responding. Grab immunity is NOT checked.

Resets to true after each pair completes (both calls done) or when Call 1 returns false (vanilla short-circuits the &&, so Call 2 never fires).

### Feature 1A + 2A: Sideways Grabs (HEAD of addBlockLine)

```
@Inject(method = "addBlockLine", at = @At("HEAD"), cancellable = true)
```

Only fires when `ideomod$inBranching` is true. This catches the moment a sticky block in the push list reaches out to grab a neighbor during branching.

Logic:
1. Calculate grabber position: `pos.relative(dir.getOpposite())` -- the block that's doing the grabbing
2. Verify grabber is actually in `toPush` (skip otherwise)
3. Get both block states
4. Skip if neighbor is air
5. Check directional rules on the grabber: is it disabled for this push direction? If yes, cancel with `cir.setReturnValue(true)`.
6. Check grab immunity: does the target resist this grabber? If yes, cancel with `cir.setReturnValue(true)`.

**Critical detail**: The cancel return value is `true`, not `false`. The `addBlockLine` method returns boolean indicating "did this succeed without hitting the push limit." Returning `true` means "everything's fine, just didn't add anything" -- it lets the resolver continue checking other neighbors. Returning `false` would mean "push limit exceeded, abort everything" which would break unrelated blocks.

### Feature 1B + 2B: Backward Walk (Redirect on canStickTo)

```
@Redirect(method = "addBlockLine",
          at = @At(value = "INVOKE",
                   target = "Lnet/minecraft/world/level/block/state/BlockState;canStickTo(...)"))
```

This intercepts ALL `canStickTo` calls inside `addBlockLine`'s backward walk. Vanilla's backward walk checks `oldState.canStickTo(blockstate) && blockstate.canStickTo(oldState)` -- two calls per iteration, both intercepted by this redirect.

A call-pair toggle (`ideomod$firstCanStickToCall`) tracks which call we're in:

**Call 1** (first=true): `self=front block (oldState)`, `other=behind block (blockstate)`.
The front block is reaching backward to grab the candidate. This is the active grabber.

**Call 2** (first=false): `self=behind block (blockstate)`, `other=front block (oldState)`.
The behind block is passively responding to the grab. Not an active grabber.

Logic for each call:
1. Run original `self.canStickTo(other)` -- if vanilla says no, we say no. If Call 1 fails, reset toggle (vanilla short-circuits &&, Call 2 won't fire).
2. Check directional rules on `self` and `other` (both calls). Directional constraints are physical -- they apply regardless of grabber/responder role.
3. For Framed Odd Slime directional blocks, check frame stickiness as a second chance.
4. **Call 1 only**: Check grab immunity `isGrabBlocked(self=front, other=behind)`. The front block is the active grabber; if its grab is blocked, the chain breaks.
5. **Call 2**: Skip grab immunity. The behind block is passively responding -- its inability to actively grab the front block doesn't matter if the front block already passed.

**Why this matters**: Without the toggle, grab immunity fired on both calls. This broke chains like `[slime]--[oddstone]-->push` where oddstone (front) should hold slime (behind): Call 1 (oddstone->slime) passed, but Call 2 (slime->oddstone) hit Rule 1 (non-oddstone can't grab oddstone). Since vanilla ANDs both results, the chain broke even though oddstone legitimately wanted to hold slime.

Removing grab immunity entirely overcorrected -- slime could then reach backward to oddstone. The toggle is the correct granularity.

**Additional mixin logic for Framed Blocks**:
Both injection points (sideways grabs + backward walk) include directional checks with second-chance logic:
1. Standard directional check (real Odd Slime) -> if blocked, second-chance for Framed Odd Slime frame stickiness
2. Framed Odd Slime directional check -> if blocked, second-chance for frame stickiness

Second-chance logic (`ideomod$wouldFrameStickinessAllow`): when directional gating blocks inner stickiness, check if the neighbor qualifies for frame stickiness instead (ACTIVE + same-material frame or Machine Family). This prevents the directional rule from killing frame stickiness in non-FACING directions.

### Feature 3: Sympathetic Movement (TAIL of addBranchingBlocks)

```
@Inject(method = "addBranchingBlocks", at = @At("TAIL"), cancellable = true)
```

After vanilla finishes checking all distance-1 sticky neighbors, this extends the search for Oddstone blocks. Delegates to `MovementScanHelper.scanSympathetic()` for the actual scanning. For each Oddstone already in `toPush`, scans along all 6 axis directions up to `sympatheticRadius` (configurable, default 5). If another Oddstone is found within range and isn't already in the push list, calls `addBlockLine` to add it.

Key details:
- Takes a snapshot of `toPush` before iterating (avoids ConcurrentModificationException as new blocks get added)
- Only checks blocks matching `ModBlocks.ODDSTONE` -- other block types don't participate
- Scans each direction independently, stops at first Oddstone found per direction (finds nearest)
- If any new blocks were added, sets return value to `true`, which tells the resolver's main loop in `resolve()` to re-run branching. This enables chaining: Oddstone A finds Oddstone B, next iteration B finds Oddstone C, etc.
- All sympathetic blocks count toward the vanilla 12-block push limit

### Feature 4: Ranged Piston startPos Redirect (HEAD of resolve)

```
@Inject(method = "resolve", at = @At("HEAD"))
```

Checks `RangedPistonFlags.getTarget()`. If a ranged piston operation is active (target is not null), overrides `this.startPos` to point at the real target block instead of the default `pistonPos + pushDirection`.

This is needed because ranged pistons pass the device's own position as `pistonPos` (to keep it far from the action zone), but the resolver calculates `startPos = pistonPos.relative(pushDirection)` which would point to the wrong block. The redirect fixes the starting position while keeping `pistonPos` safely distant so it can't collide with blocks in the move group.

### Injection Point Summary

| Method | Location | Type | Feature | Purpose |
|--------|----------|------|---------|---------|
| `resolve` | HEAD | @Inject | Feature 4 | Override startPos for ranged pistons |
| `addBranchingBlocks` | HEAD | @Inject | Phase detection | Set `inBranching = true` |
| `addBranchingBlocks` | TAIL | @Inject | Feature 3, 6, 7 | Sympathetic Oddstone search + vanilla metal conductors + immovable frame anchor check |
| `addBranchingBlocks` | TAIL | @Inject | Phase detection | Set `inBranching = false` |
| `addBlockLine` | HEAD | @Inject (cancellable) | Feature 5 + 1A + 2A | Immovable inner check (all phases) + block sideways grabs (branching only) |
| `addBlockLine` | canStickTo INVOKE | @Redirect | Features 1B+2B | Chain cohesion (call-pair toggle: grab immunity on Call 1 only) |

### Feature 5: Framed Block Immovable Inner (HEAD of addBlockLine)

When `addBlockLine` encounters a FramedBlock whose inner block would be immovable (`#ideomod:immovable_when_framed` tag or destroySpeed -1), returns false to fail the entire resolve. This fires BEFORE the `inBranching` guard so it applies in ALL phases (line-building + branching).

Needed because vanilla's `addBlockLine` returns TRUE (skip) when `isPushable` returns false. For blocks that ARE part of a sticky group via frame activation, skipping is wrong — the immovable block should anchor the group and prevent movement.

Helper: `ideomod$isFramedBlockWithImmovableInner(BlockState, BlockPos)` — checks FramedBlock BE for inner block identity (hardcoded Blocks.OBSIDIAN/CRYING_OBSIDIAN/REINFORCED_DEEPSLATE) and destroySpeed == -1.

### Feature 7: Immovable Frame Anchor Detection (TAIL of addBranchingBlocks)

Post-gathering safety check. After all blocks have been collected into toPush (by branching, Feature 6 vanilla metal conductors, and Feature 3 sympathetic search), scans every block in toPush for adjacent framed blocks with immovable inners that would form a frame stickiness connection. If found, fails the resolve (the immovable block anchors the group).

This exists because vanilla's backward walk silently skips unpushable blocks (isPushable returns false -> walk stops, returns true). Feature 5 at HEAD of addBlockLine never fires for blocks the backward walk skips. Sideways grabs (branching) DO trigger Feature 5, so they work correctly. Feature 7 catches the backward walk gap.

Conditions for anchor detection (ALL must be true):
1. Neighbor is a FramedBlock with immovable inner (`#ideomod:immovable_when_framed` tag or destroySpeed -1)
2. Neighbor is NOT already in toPush (those are handled by Feature 5)
3. Neighbor is ACTIVE (inactive framed obsidian has no frame stickiness connection)
4. Frame stickiness connects the neighbor to the group member (same material, cross-frame mediation via ideomod$wouldFrameStickinessAllow, or Machine Family adjacency)

Helper: `ideomod$hasImmovableFrameAnchor()` — iterates toPush, checks 6 neighbors per block, returns true if any immovable anchor found.

---

## 6. BLOCK MOVEMENT SYSTEM

General-purpose system for moving blocks using vanilla piston mechanics. Used by Repulser/Attractor (ranged pistons) and Machine Track/Trolley (directional push). All callers use vanilla's block event system for client-server synchronization (see BLOCK_EVENT_LESSON.md for the full debugging story).

### MovementScanHelper -- Utility for Block State Lookups and Scans

`MovementScanHelper` provides shared utilities for scanning and classifying blocks during movement operations. Methods include block state lookups with type checks, sympathetic oddstone scanning, and sticky group traversal. Centralizes logic previously scattered across mixins and resolvers, making the codebase more maintainable and reducing duplication.

### BlockMoveHelper -- General-Purpose Movement Utility

`BlockMoveHelper.moveBlockAt(level, initiatorPos, targetPos, pushDir)` is the single entry point for any system that needs piston-style block movement at a distance.

**Parameters:**
- `level` -- Level (not ServerLevel -- must run on both client and server for animation)
- `initiatorPos` -- position of the block causing the move (protected from overwrite)
- `targetPos` -- position of the block to move (can be non-adjacent to initiator)
- `pushDir` -- direction to move

**Safety checks:**
1. toPush must be non-empty and contain targetPos
2. toPush must NOT contain initiatorPos
3. No block's destination (sourcePos + pushDir) may equal initiatorPos

**Movement steps** (mirrors vanilla PistonBaseBlock.moveBlocks()):
1. Set ThreadLocal target via RangedPistonFlags.setTarget(targetPos)
2. Create PistonStructureResolver with initiatorPos as pistonPos (mixin redirects startPos)
3. Resolve full move group (respects push limits, stickiness, etc.)
4. Clear ThreadLocal in finally block
5. Safety checks (above)
6. Snapshot block states
7. Destroy breakables (reverse order, flag 18)
8. Place MovingPistonBlock at destinations (reverse order, flag 68) -- sources still exist
9. Clear vacated sources to air (flag 82)
10. Neighbor updates (indirect shapes, updateNeighborsAt)
11. Piston extend sound from initiator position

Flag 68 = SKIP_DROP(4) + NO_BLOCK_ENTITY_DROP(64). Flag 82 = UPDATE_CLIENTS(2) + SKIP_NEIGHBOR(16) + NO_BLOCK_ENTITY_DROP(64).

**Why block events?** `MovingPistonBlock.newBlockEntity()` returns null. The client never creates `PistonMovingBlockEntity` through the normal block entity lifecycle. Vanilla creates it by running `moveBlocks()` on both sides via `triggerEvent()`. If movement code only runs server-side, blocks disappear for 2 ticks then pop in with no animation.

**Why no ServerLevel guard in triggerEvent?** The entire point is that both sides run the same code. Guarding with `instanceof ServerLevel` defeats the purpose. Only guard things that genuinely must not run on client (item drops, XP, world data saves).

### RangedPistonUtil -- Scanning + Dispatch

Handles line scanning for the Repulser/Attractor family. Delegates actual movement to BlockMoveHelper. Features a unified `tryMove()` method that encapsulates the common scan-and-move pattern.

**Scanning**: `scanForNearest` (Repulser) and `scanForFarthest` (Attractor) walk a line from the device in its FACING direction, up to `SCAN_RANGE` (12 blocks), testing each position against a filter predicate.

**Public API:**
- `tryRepulse(level, devicePos, facing, filter)` -- scan nearest, push away
- `tryAttract(level, devicePos, facing, filter)` -- scan farthest, pull toward device (pushDir = facing.getOpposite())

Both call `BlockMoveHelper.moveBlockAt()` after finding the target.

### AbstractRangedPistonBlock -- Shared Base Class

Handles all shared logic for Repulser/Attractor variants:
- FACING + POWERED blockstate properties
- Directional placement (piston-style, faces player look direction)
- Rising edge detection in neighborChanged() (server-only)
- Block event scheduling: `level.blockEvent(pos, this, EVENT_FIRE, facing.get3DDataValue())`
- Block event dispatch in triggerEvent() (runs BOTH sides): decodes direction, calls `fire()`
- Rotation/mirror support

Subclasses implement `fire(Level level, BlockPos pos, Direction facing)` only. OddstoneRepulserBlock and OddstoneAttractorBlock are each ~15 lines.

### RangedPistonFlags -- ThreadLocal Coordination

Pattern for passing runtime context from calling code into a mixin:

1. Caller (`BlockMoveHelper`) sets the target: `RangedPistonFlags.setTarget(targetPos)`
2. Caller creates the resolver (which triggers the mixin)
3. Mixin reads the target: `RangedPistonFlags.getTarget()`
4. Caller clears in finally: `RangedPistonFlags.clear()`

Lives in `block/` package (not `mixin/`) so both sides can access it.

### MachineTrackBlock -- Direct BlockMoveHelper Caller

Machine Track skips scanning entirely -- it knows where the Trolley is (adjacent). Uses BlockMoveHelper directly from its triggerEvent().

**Signal direction detection**: Track checks each neighbor to find actual signal sources. Does NOT use `level.hasSignal()` because Track emits strong signal via POWER, causing vanilla's weak power to leak through adjacent solid blocks (Trolleys would appear as signal sources). Instead checks:
- Conduit blocks (Machine Block/Track) with POWER higher than this Track = signal source
- Tagged redstone components with nonzero signal = signal source
- Everything else (Trolleys, stone, air) = ignored

**Push scheduling**: For each signal face, pushDir = opposite. Checks ALL 6 neighbors for Trolleys. Packs pushDir + trolleyFace into block event param via bit shifting (bits 0-2: pushDir, bits 3-5: trolleyFace).

**Track self-protection**: Track's position is passed as initiatorPos to BlockMoveHelper. The PistonStructureResolver treats this as pistonPos (immovable origin), so the Track is never dragged along. This is a structural protection, not a stickiness exemption.

### Filter Predicates

`ODDSTONE_ONLY` -- `state.is(ModBlocks.ODDSTONE.get())`. Used by current Repulser/Attractor.

`ANY_MOVABLE` -- checks not air, destroySpeed >= 0, pushReaction not BLOCK or IGNORE. Requires Level parameter (previously had a bug passing null for BlockGetter -- fixed in 2026-02-22f). Not currently used but ready for Arcane variants.

### IPistonMovable -- Block Entity Data Preservation During Piston Moves

Opt-in interface for block entities that need to preserve data through piston movement. Vanilla destroys and recreates block entities during piston moves. This system serializes data before the move and restores it after the 2-tick animation.

**Interface** (`com.ideomancer.ideomod.block.IPistonMovable`):
- `CompoundTag saveMovementData()` -- serialize data before move. Return null to skip.
- `void loadMovementData(CompoundTag data)` -- restore data after move.

**Implementing blocks**: MachineFrameBlockEntity (saves/loads inventory via ContainerHelper), FramedBlockEntity (saves/loads inner block ResourceLocation).

**Data registry** (`MovingBlockEntityData`):
- Static `ConcurrentHashMap<String, CompoundTag>` keyed by `"dimension:x,y,z"` at DESTINATION.
- `store()` adds data. `retrieve()` removes and returns data (destructive read).
- CRITICAL: Only ONE consumer (PistonMovingBlockEntityMixin). Multiple consumers = race condition.
- CRITICAL: Only SERVER-side code should store. Client block entities lack synced inventories.

**Save/Restore Pipeline**:

```
SAVE (server-only):
  Vanilla pistons:  PistonBaseBlockMixin.moveBlocks() at HEAD
  BlockMoveHelper:  Step 2.5 (Repulser/Attractor/Track)
       |
       v
TRANSIT: MovingBlockEntityData holds CompoundTag for ~2 ticks
       |
       v
RESTORE (server-only):
  PistonMovingBlockEntityMixin.tick() at TAIL
  Guards: isClientSide check, MOVING_PISTON check (wait for real block)

CLIENT RENDER DATA (client-only):
  Vanilla pistons:  PistonBaseBlockClientMixin.moveBlocks() at HEAD
  BlockMoveHelper:  Step 2.6 (same toPush list, includes sympathetic)
       |
       v
CACHE: MovingFrameData (ConcurrentHashMap<BlockPos, FrameTransitInfo>)
```

**PistonBaseBlockMixin** (`com.ideomancer.ideomod.mixin.PistonBaseBlockMixin`):

Target: `net.minecraft.world.level.block.piston.PistonBaseBlock`

Feature 1 -- isPushable override (HEAD inject, cancellable):
Vanilla ends `isPushable()` with `return !blockstate.hasBlockEntity()`. Our inject checks `getBlockEntity(pos) instanceof IPistonMovable` and returns true, bypassing the vanilla block entity check. This allows Machine Frames to be pushed and pulled by pistons. Uses `MovementScanHelper` for efficient block state lookups. Also handles Oddstone pull immunity: during retraction, ThreadLocal<BlockPos> `ideomod$pullTargetPos` stores the exact pull position (pistonPos + direction * 2). If block at pos is oddstone (real OddstoneBlock or FramedBlock with INNER_STICKY=ODDSTONE) AND pos matches pullTargetPos, returns false. Position-specific — only the direct pull target is immune, not oddstone elsewhere in a sticky group.

Feature 4 -- Framed block immovable inner (in same HEAD inject):
When a FramedBlock wraps an immovable inner block, isPushable returns false. Uses the `#ideomod:immovable_when_framed` tag (data-driven, extensible by datapacks — default: obsidian, crying_obsidian, reinforced_deepslate), plus `getDestroySpeed == -1.0f` for bedrock-type blocks. Runs BEFORE Feature 1's IPistonMovable check so framed obsidian is blocked despite having an IPistonMovable block entity. Includes diagnostic logging for both success and BE-lookup-failure cases. Key discovery: vanilla does NOT use PushReaction.BLOCK for obsidian — the initial implementation using PushReaction silently failed.

Feature 2 -- moveBlocks data save (HEAD inject):
Saves IPistonMovable block entity data before vanilla destroys the block entities during the move. Server-only (critical -- see Bug 2 below).

| Mode | Method | Notes |
|------|--------|-------|
| Extension | PistonStructureResolver | Standard resolver finds toPush list, save each IPistonMovable |
| Retraction | Direct position scan + BFS + sympathetic | Resolver fails for IPistonMovable blocks (Bug 3). Scans pistonPos+dir*2 directly, then BFS for sticky group, then sympathetic scan for distant oddstone |

Retraction sticky group BFS: Starting from the pulled block, scans all 6 neighbors. For each neighbor: checks if any visited block is sticky to it (both directions via canStickTo). If stuck, saves its data and (if itself sticky) queues its neighbors. Limit: 12 blocks (vanilla push limit). Shares savedPositions set with sympathetic scanner.

Retraction sympathetic scan (scanSympathetic): After BFS, scans from each oddstone/framed oddstone in savedPositions along all 6 axes up to OddstoneConfig.getSympatheticRadius(). Finds distant oddstone blocks (separated by air) and saves their BE data. After saving each found oddstone, also calls scanStickyGroup() on it if sticky — this ensures blocks attached to the remote oddstone via stickiness (machine frames, activated framed blocks) also get their data saved. Chains iteratively — newly found blocks become seeds for next pass. Mirrors PistonStructureResolverMixin's sympathetic search logic.

**PistonMovingBlockEntityMixin** (`com.ideomancer.ideomod.mixin.PistonMovingBlockEntityMixin`):

Target: `net.minecraft.world.level.block.piston.PistonMovingBlockEntity`

TWO restore paths (sole consumer of `MovingBlockEntityData.retrieve()`):

1. `tick()` TAIL — normal 2-tick piston animation. Checks block is no longer MOVING_PISTON before restoring. Server-only guard.
2. `finalTick()` TAIL — 1-tick piston pulses. Vanilla calls `finalTick()` (non-static instance method) instead of `tick()` (static method) for 1-tick pulses. Without this hook, data is lost because `tick()` never sees the "real block placed" condition. Added 2026-02-22.

Note: `finalTick()` inject uses `(PistonMovingBlockEntity)(Object)this` to access the instance since it is non-static, unlike `tick()`.

**BlockMoveHelper integration** (steps 2.5 + 2.6):
BlockMoveHelper has its own save logic in step 2.5 for Repulser/Attractor/Track moves. Wrapped in `if (!level.isClientSide())` guard. Same pattern as PistonBaseBlockMixin but within the BlockMoveHelper flow. Restore uses the same PistonMovingBlockEntityMixin. Step 2.6 handles client-side transit rendering: iterates toPush (which includes sympathetic blocks from the resolver) and populates MovingFrameData for any framed blocks found. This ensures the transit renderer draws inner blocks during BlockMoveHelper-driven moves.

**Three bugs discovered and fixed**:
1. **onPlace timing**: `setBlock()` -> `onPlace()` -> `newBlockEntity()`. Block entity does not exist during onPlace(). Sole consumer must be the mixin.
2. **Client overwrites server**: `moveBlocks()` and BlockMoveHelper run on both sides via block events. Client saves empty data over server's valid data in shared static map. Fix: `isClientSide()` guard on all save paths.
3. **Retraction resolver fails**: `PistonStructureResolver.resolve()` returns false for `extending=false` with IPistonMovable blocks. Fix: bypass resolver, scan directly.

---



## 6b. FRAME ACTIVATION SYSTEM

The Frame Activation system controls when Frame Blocks and Framed Blocks become sticky. It uses a redstone-style signal strength model: Machine Blocks are signal sources, conductors decay the signal, and redstone-powered machines can suppress activation.

### Architecture

**FrameActivation.java** is a static utility class with a batched activation model. All block lifecycle hooks (onPlace, onRemove, neighborChanged) call `markDirty(Level, BlockPos)` which queues the position for end-of-tick processing. At the end of each server tick, `FrameActivation.TickHandler` (registered on Forge event bus via `TickEvent.ServerTickEvent(Phase.END)`) calls `processDirtyNetworks()`, which discovers connected networks from dirty positions, deduplicates overlapping positions, and runs one `recalculate()` per unique network. For time-critical paths (piston resolution), `flushIfDirty(Level)` immediately drains the dirty queue for that dimension.

Public API: `markDirty(Level, BlockPos)` (primary entry point for all callers), `flushIfDirty(Level)` (for piston resolver), `processDirtyNetworks()` (called by TickHandler). Internal: `recalculate(Level, BlockPos)` is package-private.

### Signal Strength Model

**Activation signal**: All Machine Family blocks (Machine Frames, Tracks, Trolleys, future specializations) are activation sources. Machine Frames emit activation at strength 15 only when unpowered (not receiving redstone). All other Machine Family blocks always emit activation at strength 15 regardless of redstone state. Each conductor block decays the signal by 1 (like redstone dust). Maximum activation range: 15 blocks from source. Each block records the strongest signal received from any path (shortest path wins).

**Anti-activation signal**: Only MachineFrameBlock can emit anti-signal -- no other Machine Family member (Track, Trolley, future specializations) ever suppresses frames. A Machine Frame receiving redstone power becomes a SUPPRESSOR. It stops emitting activation entirely and instead emits an anti-signal with strength equal to its redstone input level (1-15). Anti-signal also decays by 1 per conductor. Any block reached by anti-signal (strength > 0) is DEACTIVATED, regardless of how strong the activation signal from other sources is. This is a binary override, not subtraction. All other Machine Family blocks always emit activation at strength 15 regardless of their redstone state.

**Example**: Machine -- 15 frames -- Machine(redstone=5). The left machine sends activation 15, 14, 13... 1 across all 15 frames. The right machine sends anti-signal 5, 4, 3, 2, 1 into the last 5 frames. Result: first 10 frames active, last 5 suppressed.

Signal decay prevents backward activation (same as redstone dust) — no block can send a stronger signal back to its source.

### Conductor Types

| Type | Activation State | Conducts For | When Activated Passes |
|------|-----------------|--------------|----------------------|
| Frame Block (IFrameBlock) | ACTIVE blockstate property | Own frame material | Own material(s) |
| Framed Block (normal) | ACTIVE blockstate property | Frame material + inner conductor (if set) | Own material(s) |
| Framed Block (mediator) | ACTIVE blockstate property | Frame material + inner conductor | ALL materials (universal bridge) |
| Vanilla Metal (iron_block, copper_block) | Ephemeral Set<Long> in FrameActivation | Own metal material | Own material |
| Machine Block (source) | Always active | N/A (source) | ALL materials |

**Mediator**: A framed block whose INNER_CONDUCTOR is a different material than its frame (e.g., copper-framed iron block: frame=COPPER, inner=IRON). When activated, mediators pass ALL materials like a machine block, enabling unlimited material family hops. Same-material inner conductors (iron-framed iron) are NOT mediators.

### The Algorithm

When `recalculate()` is called:

1. **DISCOVER**: BFS from the change point through all connected conductors (IFrameBlock + vanilla metals, any material, active or inactive). Finds the entire network.
2. **WIPE**: Set every discovered IFrameBlock to ACTIVE=false (UPDATE_CLIENTS only, no neighbor cascade). Remove only this network's vanilla metal positions from activatedVanillaMetals set (per-network remove, not global clear).
3. **FIND SOURCES**: For every block in the network AND the change position, check 6 neighbors for Machine Blocks.
4. **PROPAGATE ACTIVATION**: BFS from activation sources, signal strength 15. Machine Frames only activate when unpowered; all other Machine Family blocks always activate. Each entry carries available materials + strength. A conductor accepts if its conductive materials intersect the available materials. Mediators pass ALL materials. Signal decays by 1 per hop. Records max strength per position.
5. **PROPAGATE ANTI-SIGNAL**: From powered Machine Frames ONLY (no other Machine Family blocks), BFS with strength = redstone input level. Same material routing and decay. Records max anti-strength per position.
6. **APPLY**: For each network block: activate if activation > 0 AND anti-signal == 0. IFrameBlocks get ACTIVE=true. Vanilla metals get added to activatedVanillaMetals.

### Vanilla Metal Activation Tracking

Vanilla blocks cannot have custom blockstate properties added (properties are baked into StateDefinition at registration time). Instead, FrameActivation maintains a `ConcurrentHashMap.newKeySet()` backed `Set<Long> activatedVanillaMetals` (packed BlockPos longs) with per-network remove-then-add. Each `recalculate()` removes only the vanilla metal positions belonging to the network it's processing (step 2), then adds back the ones that should be active (step 6). This preserves vanilla metals from other networks.

Thread safety: `ConcurrentHashMap.newKeySet()` provides thread-safe individual add/remove/contains. Concurrent reads from stickiness checks always see consistent per-element state.

History: Previously used volatile atomic-swap of an immutable set (audit fix B, 2026-03-14), which was safe for single-network worlds but caused Issue #15 -- when multiple networks were recalculated in one flush cycle, each recalculate() replaced the ENTIRE global set with only its own network's metals, wiping metals from previously-processed networks (Issue #15 fix, 2026-03-15).

Public queries:
- `isVanillaMetalActivated(BlockPos)` — is this vanilla metal connected to a machine source?
- `isActivatedConductor(BlockState, BlockPos)` — covers both IFrameBlocks (via ACTIVE) and vanilla metals (via set)
- `isActive(BlockState)` — checks IFrameBlock ACTIVE property only (public for cross-package access)

### Stickiness Activation Requirements

Frame-family stickiness requires activation on BOTH sides:
- **FrameBlock.canStickTo()**: Frame family material checks gated behind `IFrameBlock.isActive(other)`. Inactive neighbor falls through to permissive checks (slime/honey can still grab).
- **FramedBlock.canStickTo()**: Same treatment for neighbor IFrameBlock checks within the ACTIVE block.
- **IMachineFamily.canMachineFamilyStickTo()**: Frame family returns `FrameActivation.isActive(other)`. Machine blocks only grab active frames.
- **Vanilla metals**: shouldVanillaMetalStick/shouldVanillaMetalGrabNeighbor check `FrameActivation.isVanillaMetalActivated(metalPos)`.

### Key Types

| Type | Role |
|------|------|
| `IFrameBlock` | Interface. Marker for Frame Family blocks. Provides `getFrameMaterial()`. Has static `isActive(BlockState)` helper. |
| `FrameMaterial` | Data-carrying enum: IRON, COPPER. displayName, registryPrefix, item registry, miningTimeModifier. `fromVanillaBlock()`, `toConductorValue()`, `fromConductorValue()` for conductor mapping. |
| `FrameBlock` | Base block class. ACTIVE + WATERLOGGED properties. `isStickyBlock()` returns ACTIVE value. All lifecycle hooks call `FrameActivation.markDirty()`. |
| `FrameActivation` | Static utility. `markDirty()` is the primary entry point (batched). `recalculate()` is package-private. End-of-tick processing via `TickHandler`. `flushIfDirty()` for piston resolver. Signal strength propagation, anti-signal suppression, vanilla metal tracking. Public queries: `isVanillaMetalActivated()`, `isActivatedConductor()`, `isActive()`, `isFrameFamily()`, `getFrameMaterial()`. |

### Performance

Networks are bounded by signal decay (max 15 blocks from source). Propagation touches each position at most twice per signal type (activation + anti). For a 15-frame chain this is ~30 setBlock calls. The BFS uses `bestQueued` tracking to avoid re-processing positions with weaker signals.

**Batching**: The `markDirty()` + end-of-tick pattern means placing 50 frame blocks triggers 1 network recalculation instead of 50. Dirty positions are stored per-dimension in `ConcurrentHashMap<ServerLevel, Set<BlockPos>>` with `ConcurrentHashMap.newKeySet()`. `processDirtyNetworks()` discovers connected networks from dirty positions and deduplicates — if 10 dirty positions all belong to the same network, only 1 recalculation runs.

**Piston resolution**: `flushIfDirty(Level)` is called at HEAD of `PistonStructureResolver.resolve()` to ensure activation states are up-to-date before stickiness checks run. This is a targeted drain for the current dimension only.

### Rendering

Frame blocks use `RenderType.cutout()` (registered in `Ideomod.clientSetup()`) and `noOcclusion()` (in block properties) so transparent texture pixels render correctly instead of appearing as black.


## 6c. FRAMED BLOCKS SYSTEM

Framed Blocks wrap any valid block with a metal frame, preserving inner block behavior while adding frame-family stickiness. Two registered block IDs (iron_framed_block, copper_framed_block) serve as transparent proxies.

### Architecture

FramedBlockEntity stores the inner block's ResourceLocation. FramedBlock overrides ~19 behavioral methods, each querying the inner block's defaultBlockState() for delegation. Implements IFrameBlock (same interface as FrameBlock) and EntityBlock. IPistonMovable for piston data preservation. Block properties include `noOcclusion()` to prevent neighbor face culling (required for translucent inner blocks like glass, slime, odd slime).

### Blockstate Properties

| Property | Type | Values | Purpose |
|----------|------|--------|---------|
| ACTIVE | BooleanProperty | false (default), true | Frame-family stickiness activation (Machine Block driven) |
| FACING | DirectionProperty | 6 directions (default NORTH) | Inner block orientation + Framed Odd Slime directional stickiness |
| INNER_STICKY | IntegerProperty | 0-4 (default 0) | Inner sticky type from StickyType enum: 0=NONE, 1=SLIME, 2=HONEY, 3=ODD_SLIME, 4=ODDSTONE |
| INNER_CONDUCTOR | IntegerProperty | 0-2 (default 0) | Inner block conductor material: 0=NONE, 1=IRON, 2=COPPER. Enables cross-frame mediation at blockstate level. |
| INNER_LIGHT | IntegerProperty | 0-15 (default 0) | Inner block light emission level. Read by lightLevel predicate in ModBlocks.java so the lighting engine knows emission without block entity access. |

Total blockstate count: 2 x 6 x 5 x 3 x 16 = 2880 states per framed block type.

FACING set from player look direction on placement (piston-style: getNearestLookingDirection().getOpposite()). INNER_STICKY, INNER_CONDUCTOR, and INNER_LIGHT are pre-populated in getStateForPlacement() from item NBT (critical for lighting engine to detect emission on first setBlock), and kept in sync by FramedBlockEntity.updateBlockstateFromInner() for piston restores and chunk loads. INNER_STICKY delegates to `StickyType.fromBlock()`. INNER_CONDUCTOR uses `FrameMaterial.fromVanillaBlock()` + `toConductorValue()`. INNER_LIGHT uses `inner.defaultBlockState().getLightEmission()`. updateBlockstateFromInner() called on setInnerBlock(), onLoad(), load() (when level available), and loadMovementData().

### FrameabilityHelper

Static utility determining whether a block can be framed. Tag-driven whitelist: checks (1) block is in `#ideomod:frameable_blocks` tag (~290 vanilla blocks enumerated), (2) NOT already a FramedBlock, (3) NOT an IFrameBlock. Infested block variants excluded (framing would bypass silverfish emergence mechanic). Extensible by datapacks or other mods via the tag.

### Behavioral Delegation

| Method | Delegation | Notes |
|--------|-----------|-------|
| getDestroyProgress | Per-position | Mining speed matches inner block |
| getSoundType | Per-position | Walk/break sounds match inner |
| getFriction | Per-position | Slime friction preserved |
| getLightEmission | Per-position | Glowstone light preserved |
| getMapColor | Per-position | Map color matches inner |
| isSignalSource | Always true | Conservative -- causes minor extra updates |
| getSignal | Per-position | Redstone block signal preserved |
| getDirectSignal | Per-position | Strong signal preserved |
| isRedstoneConductor | Per-position (lambda) | Via ModBlocks Properties lambda |
| isPathfindable | Per-position | Oddstone mob avoidance preserved |
| getBlockPathType | Per-position | Mob AI pathfinding preserved |
| getAdjacentBlockPathType | Per-position | Adjacent pathfinding preserved |
| getCloneItemStack | Per-position | Middle-click returns proper NBT stack via frameMaterial.getFramedBlockItem() |
| stepOn | Per-position | Magma burn, surface effects |
| fallOn | Per-position | Hay bale damage reduction, slime damage cancel |
| updateEntityAfterFallOn | Per-position (below) | Slime bounce (reverses Y velocity). Uses entity.blockPosition().below() for lookup. |
| entityInside | Per-position | Future: bubble columns, cobweb slowdown |
| getSpeedFactor | Via EntityMovementMixin | Soul sand 0.4, honey 0.4. Block method has no position param; mixin intercepts at Entity level. |
| getJumpFactor | Via EntityMovementMixin | Honey 0.5. Same mixin approach as speed factor. |
| getPistonPushReaction | Via PistonBaseBlockMixin | `#ideomod:immovable_when_framed` tag (obsidian/crying obsidian/reinforced deepslate) + destroySpeed -1 = immovable. Checked in isPushable, not via PushReaction enum. |

**EntityMovementMixin** (`com.ideomancer.ideomod.mixin.EntityMovementMixin`):

Target: `net.minecraft.world.entity.Entity`

Intercepts Entity-level speed and jump factor resolution to support Framed Blocks. Block.getSpeedFactor() and Block.getJumpFactor() take no position parameter, so FramedBlock can't delegate per-position through those methods. This mixin intercepts at the Entity level where position IS available. Features generic `ideomod$getFramedFactor()` helper for encapsulating block entity lookup and factor resolution.

Two injects at RETURN:
1. `getBlockSpeedFactor()` -- checks entity.blockPosition() and belowPos for FramedBlock, returns inner block's speed factor
2. `getBlockJumpFactor()` -- same pattern for jump factor

Both use early-exit: if vanilla already returned a non-default value (not 1.0f), the block at the entity's feet already has a custom factor. Generic helper method `ideomod$getFramedFactor(Function<Block,Float>)` encapsulates the block entity lookup and factor resolution. belowPos calculated as entity Y - 0.5000001 (matches vanilla's below-position calculation).

### Stickiness (Combined Frame + Inner)

FramedBlock has two independent stickiness systems that combine:

**Frame stickiness** (same as Frame Blocks): Requires ACTIVE=true (driven by FrameActivation system). Sticks to same-material IFrameBlock **only when neighbor is also active**. Sticks to Machine Family. Cross-frame mediation: sticks to framed blocks with matching INNER_CONDUCTOR. Sticks to same-material activated vanilla metals. Grab immunity rules 9-11 prevent grabbing slime/honey/odd slime.

**Inner stickiness** (based on INNER_STICKY property): Requires INNER_STICKY > 0. Applies the vanilla stickiness behavior of the inner block type. Framed Slime grabs normal blocks like real slime. Framed Honey grabs like real honey. Framed Odd Slime grabs like real odd slime (directional). Framed Oddstone grabs all slime types and other oddstone like real oddstone (universal grabber). Vanilla mutual exclusions apply (slime vs honey, slime vs odd slime, honey vs odd slime).

**Combined behavior**:
- `isStickyBlock()`: returns true when ACTIVE || INNER_STICKY > 0
- `canStickTo()`: frame rules (ACTIVE-gated) + inner sticky rules (INNER_STICKY-gated)
- Grab immunity carve-outs: Rule 4 exempted when grabber INNER_STICKY matches target type OR when INNER_STICKY=ODDSTONE (universal grabber). Rule 1 expanded to protect framed oddstone with carve-outs for machine family and active same-material frames.
- Framed Odd Slime directional: inner stickiness blocked in non-FACING directions, frame stickiness still works via second-chance mixin check
- Framed Oddstone: grabs all slime types (real+framed) and oddstone. Cannot be grabbed by non-oddstone except via frame network (ACTIVE same-material) and Machine Family.

### NBT Helpers

NBT handling for FramedBlockEntity has been centralized. Helper methods for saving and loading inner block ResourceLocation, combined with FrameMaterial's item registry methods, provide a unified interface for persistence across piston moves, inventory operations, and item rendering. Reduces code duplication across FramedBlockEntity, FramedBlockItem, and recipe handling.

### Rendering (Three-Layer System)

**Layer 1 (BESR -- FramedBlockRenderer)**: Uses BlockRenderDispatcher.renderSingleBlock() to render the inner block. The inner state is constructed with FACING transferred from the framed block's FACING property to the inner block's orientation property (DirectionalBlock.FACING for 6-way, HorizontalDirectionalBlock.FACING for 4-way with vertical clamped to NORTH, RotatedPillarBlock.AXIS mapped from FACING). Rendered at 0.998 scale via poseStack.scale() with centering offset for epsilon inset, preventing z-fighting with the frame overlay. Lighting uses brightest-of-6-neighbors (max block + max sky from all face-direction neighbors). Handles multi-element models (slime nested cubes), translucency (glass, slime alpha blending), and tinting (grass biome colors) natively through renderSingleBlock's model pipeline.

**Layer 2 (JSON model -- RenderShape.MODEL)**: Frame overlay with transparent interior pixels. Active variant uses brighter texture. Full block size. RenderType: cutoutMipped.

**Layer 3 (Transit Renderer -- PistonMovingBlockEntityRendererMixin)**: Client-side mixin into PistonHeadRenderer.render(). Uses same renderSingleBlock() approach as the BESR. Reads FrameTransitInfo from MovingFrameData (includes facing + innerSticky). Constructs inner state with correct FACING from cached data. Animation offset via vanilla's getXOff/getYOff/getZOff. Brightest-of-neighbors lighting blended between source and destination positions by animation progress via Mth.lerp().

**Layer 4 (Item Rendering -- FramedBlockItemModel)**: Custom BakedModel wrapping the standard frame model. ItemOverrides.resolve() reads BlockEntityTag.InnerBlock from ItemStack NBT, fetches inner block's BakedModel, returns CompositeModel merging inner + frame quads. CompositeModel stores and uses the inner block's defaultBlockState() for quad queries (required for multi-element models and state-dependent texture resolution). ConcurrentHashMap cache keyed by inner block ResourceLocation. Registered via ModelEvent.ModifyBakingResult in Ideomod.java. Known limitation: translucent inner blocks render opaque in item display.

### Client-Side Transit Data Pipeline

```
CAPTURE (client-only, runs on Render thread via block events):
  Vanilla pistons:
    PistonBaseBlockClientMixin.moveBlocks() at HEAD
      Extension: PistonStructureResolver finds toPush list (includes sympathetic)
      Retraction: Sticky-group-aware BFS + sympathetic scan + sticky group scan
  BlockMoveHelper (Repulser/Attractor/Track):
    BlockMoveHelper.moveBlockAt() step 2.6
      Iterates toPush list (includes sympathetic from resolver)
       |
       v
CACHE: MovingFrameData (ConcurrentHashMap<BlockPos, FrameTransitInfo>)
  FrameTransitInfo record: innerBlockId, facing, innerSticky
  Cleared at start of each capture pass. Keyed by DESTINATION position.
       |
       v
RENDER (client-only, runs each frame during 2-tick animation):
  PistonMovingBlockEntityRendererMixin.render() at HEAD
  Reads FrameTransitInfo, constructs inner state with FACING, renders via
  renderSingleBlock before vanilla renders frame overlay.
```

### Known Visual Artifacts

- Item model translucency: translucent inner blocks (glass, slime) render opaque in inventory item display due to quad-compositing approach. Purely cosmetic.
- Item model facing: items always show default orientation (no world-facing context in item rendering).

### Recipe System (CustomRecipe): matches 1 frame item + 1 frameable BlockItem. Output is corresponding framed block item with BlockEntityTag.InnerBlock NBT. Single JSON trigger file (data/ideomod/recipes/framed_block.json). Serializer registered via ModRecipeSerializers (DeferredRegister<RecipeSerializer<?>>).

### Creative Tab

FRAMED_BLOCKS_TAB caches the frameable blocks list on first tab open (via FrameabilityHelper.isFrameable()), creates iron + copper ItemStack with NBT for each valid block. Automatically includes modded blocks. Registry iteration happens once, not per tab open.

### F3 Debug Overlay

FramedBlockDebugOverlay subscribes to CustomizeGuiOverlayEvent.DebugText. When F3 is open (`mc.options.renderDebug`) and player is looking at a FramedBlock, appends "Inner Block: minecraft:stone" (or appropriate ID) to right-side debug text. Registered on MinecraftForge.EVENT_BUS in clientSetup.

### Sympathetic Movement Through Frames

PistonStructureResolverMixin's sympathetic search checks for both direct OddstoneBlock AND FramedBlock with inner OddstoneBlock. Framed Oddstone participates in sympathetic movement chains.

## 7. PORTAL SYSTEM

The Oddstone Portal is an address-based teleportation system using mineral block corners to define destinations.

### Component Responsibilities

| File | Role |
|------|------|
| `OddstonePortalHandler` | Lifecycle coordinator. Called by OddstoneBlock's neighborChanged/onRemove. Decides activate vs deactivate. Static utility class. |
| `OddstonePortalFrameDetector` | Scans from interior outward to find valid frames. Validates structure, reads corner addresses, checks redstone power. |
| `PortalGeometry` | Data structure: portal dimensions, corners, axis, interior bounds. Used by validateOrDeactivate() for re-validation. |
| `OddstonePortalSavedData` | Per-dimension SavedData (NBT serialized). Stores portal registrations keyed by position. Manages linking/unlinking. |
| `OddstonePortalBlock` | The interior fill block. Handles teleportation (entityInside), cascade breaking (updateShape), unregistration (onRemove). |
| `PortalUtils` | Shared helpers: corner validation, interior navigation, clearing, axis direction lookup. |

### Frame Structure

A valid frame is a rectangle of Oddstone blocks with crafted mineral blocks at the four corners:

```
[Corner] [Oddstone] [Oddstone] [Corner]   <- top
[Oddst]  [air/portal] [air/portal] [Oddst]
[Oddst]  [air/portal] [air/portal] [Oddst]
[Corner] [Oddstone] [Oddstone] [Corner]   <- bottom
```

Interior dimensions: 3 to 19 blocks wide/tall. Frame can be oriented on X-axis or Z-axis (vertical only, like nether portals). ALL Oddstone blocks in the frame must be receiving a redstone signal for activation.

### Addressing System

The four corner blocks define the portal's address. 14 valid corner types (see section 12) = 14^4 = 38,416 unique addresses per dimension.

**Canonicalization**: The address string is built by reading corners in order TL, TR, BR, BL and comparing with the reverse reading TR, TL, BL, BR. The lexicographically smaller string is used. This ensures both sides of the portal frame produce the same address regardless of which side the player approaches from.

```
front = "TL,TR,BR,BL"
back  = "TR,TL,BL,BR"
address = (front <= back) ? front : back
```

Corner block IDs are full registry names (e.g. `minecraft:diamond_block`).

### Activation Flow

1. `OddstoneBlock.neighborChanged()` fires (server only)
2. Delegates to `OddstonePortalHandler.onNeighborChanged()`
3. Handler checks: has power AND not adjacent to existing portal? -> `tryActivate()`
4. `OddstonePortalFrameDetector.detectFrame()` scans from interior air blocks adjacent to the Oddstone
5. Tries both X and Z axis orientations
6. Validates frame structure, checks ALL Oddstone powered, reads corner address
7. Returns `FrameResult` with address, interior positions, frame positions, corners, axis
8. Handler computes interior geometry (bottom-left, width, height) from FrameResult corner positions
9. Handler calls `OddstonePortalSavedData.registerPortal(address, pos, axis, interiorBL, width, height)` — geometry cached in PortalEntry
10. Registration returns: REGISTERED_UNLINKED (first portal), REGISTERED_LINKED (second with same address), FAILED_ADDRESS_FULL (address has a pair), FAILED_ALREADY_EXISTS (same position registered)
11. On success: fills interior with `OddstonePortalBlock` states, plays flint-and-steel sound

### Deactivation Strategy

Deactivation uses full frame re-validation rather than reacting to individual block states. When any frame Oddstone receives a `neighborChanged` and is adjacent to a portal block:

1. `OddstonePortalHandler.validateOrDeactivate()` navigates from an adjacent portal block to the frame structure
2. Constructs a `PortalGeometry` object with interior dimensions, derived corner positions, and axis orientation
3. Checks all four corners are still valid corner blocks
4. Walks entire frame perimeter: every Oddstone must be present AND powered
5. Only calls `unregisterAndClear()` if validation fails

This prevents false deactivation from unrelated neighbor updates (levers, redstone dust, block placement near frame exterior). Mirrors the activation path which also validates the entire frame.

**Immediate deactivation** (no re-validation): `OddstoneBlock.onRemove()` calls `OddstonePortalHandler.onBlockRemoved()` when a frame Oddstone is broken. This is immediate because we know a frame block is gone.

**Break effects**: `PortalUtils.clearPortalBlocks()` uses `level.destroyBlock(pos, false)` which produces break particles and the portal block's break sound -- same visual/audio as cascade-breaking via `updateShape`.

`OddstonePortalBlock.updateShape()` also provides cascade breaking -- if any adjacent block in the portal plane isn't a portal block, Oddstone, or corner block, the portal block becomes air. -- if any adjacent block in the portal plane isn't a portal block, Oddstone, or corner block, the portal block becomes air.

### Teleportation

`OddstonePortalBlock.entityInside()` -- server only, players only.

**Linked portals**: Preserves relative position. Calculates player's fractional offset within source portal bounds (0.0 to 1.0 for both horizontal and vertical), applies same fraction to destination portal bounds. Works even if portals are different sizes.

**Cross-axis rotation + 180-degree exit**: All teleports apply a 180-degree yaw rotation so the player exits facing AWAY from the destination portal (doorway effect). Additionally, when source and destination portals have different axes (X vs Z), a 90-degree rotation is applied first to align with the destination axis. Same-axis: 0 + 180 = 180 degrees. Cross-axis: +/-90 + 180 degrees. Uses `player.teleportTo(level, x, y, z, yaw, pitch)`.

**Unlinked portals**: Scatter teleport. Random angle, distance 200-1000 blocks, places on surface. Deals 10 damage (5 hearts).

**Cooldown**: 80 ticks (4 seconds), uses vanilla's `portalCooldown` field.

**Orphaned portals** (portal block exists but no registration found): treated as unlinked.

### SavedData Internals

Two maps maintain the registry:
- `portalsByPosition`: Map<BlockPos, PortalEntry> -- primary store
- `portalsByAddress`: Map<String, BlockPos[2]> -- quick lookup, max 2 entries per address

PortalEntry contains: address (String), pos (BlockPos), axis ("X" or "Z"), partnerPos (nullable BlockPos). Has helper method `getAxisAsEnum()` to convert string axis to Direction.Axis enum.

Unlinking: when one portal is unregistered, its partner's `partnerPos` is set to null. Address slot is freed.

NBT format: ListTag "portals", each entry has address, x/y/z, axis, linked boolean, and optional px/py/pz for partner position.

---

## 8. MULTIBLOCK STRUCTURE FRAMEWORK

Registration-based system for detecting, tracking, and evaluating multiblock structures. Structures are world data managed by a central Structure Manager (per-dimension SavedData) -- individual blocks don't know they're part of a structure. The Structure Manager holds all knowledge.

### Architecture Overview

**StructureDefinition** (abstract): Defines a structure type's ruleset -- core blocks, frame tag, scan parameters, max cluster size. Subclassed per structure type.

**StructureScanner**: Stateless dispatcher using the Strategy Pattern. Given a core position and definition, delegates scanning logic to a `ScanStrategy` implementation. `PatternScanStrategy` handles directional pattern scanning for furnace arrays:
- Phase 1: Flood-fill cluster of adjacent same-type cores from the starting position. Bounded by definition's maxClusterWidth/maxClusterHeight.
- Phase 2: Compute frame bounds from cluster bounds + furnace facing direction. Uses Direction.relative() offsets, then normalizes min/max with Math.min/Math.max (directional offsets invert coordinates for NORTH/WEST facing).
- Phase 3: Validate all positions in the 3-layer structure: back wall (all frame), side/top/bottom walls (all frame), front face (air openings required in front of each furnace).

**StructureResult**: Immutable scan output. Contains core position (normalized to clusterMin), facing, cluster bounds, structure bounds, effector positions, frame positions, validity flag. Built via Builder pattern.

**StructureManager** (extends SavedData): Per-dimension singleton. Stores all registered structures, handles block change events, provides query API.

Data structures:
- `structures`: UUID -> StructureRegistration (all registered structures)
- `chunkIndex`: ChunkPos -> Set<UUID> (spatial lookup for block change events)
- `typeIndex`: String -> Set<UUID> (structures grouped by definition name)
- `coreExclusivity`: BlockPos -> Map<String, UUID> (one structure per type per core position)
- `trackedCores`: Set<BlockPos> (event-placed cores not yet in structures; for future non-block-entity cores)

**StructureRegistration**: Persistent entry. UUID, definition name, core position, last valid StructureResult, bounding box. NBT serialization. Null bounds guard on load.

**StructureEffectHandler** (interface): Per-type effect logic. Methods: onFormed(), onRemoved(), onTick(), onUpdated(). Implementations are stateless -- they receive the StructureResult and level.

### Event Flow

```
Block placed:
  1. Check chunk index -> any registered structure nearby? -> rescan affected
  2. Is placed block a core? -> try formation scan from it
  3. Is placed block a frame material? -> search nearby for cores -> try formation

Block broken:
  1. Check chunk index -> any registered structure nearby? -> rescan affected
  2. Search nearby for cores -> try formation (handles clearing blocking blocks)
  3. Clean up trackedCores if broken block was one
```

### Core Detection (Reactive, No Chunk Scanning)

Two complementary approaches, both cheap:

1. **Block entity iteration**: When a relevant block is placed, iterate block entity maps from overlapping chunks (typically 0-10 entries per chunk). Catches all block-entity cores (furnaces) without explicit tracking.

2. **Event-populated trackedCores**: When a core block is placed but doesn't form a structure, its position is added to the set. Checked alongside block entity results. Handles future non-block-entity core types. Cleaned up on break and on successful registration.

The `isRelevantForAnyDefinition()` filter ensures only frame/core block placements trigger the nearby core search. Placing dirt, torches, etc. skips it entirely.

### Current Structure Type: Furnace Array

**FurnaceArrayDefinition**:
- Cores: `minecraft:furnace`, `minecraft:blast_furnace`, `minecraft:smoker`
- Frame tag: `#ideomod:furnace_array_frame` (18 stone-family blocks)
- Max cluster: 3 wide x 2 tall (6 furnaces max)
- Facing: derived from furnace's FACING property (all must face same direction)
- Front face: must have air in front of each furnace (for item access)

**FurnaceArrayEffect**: Sets custom GUI names on formation ("Enhanced Furnace", "Enhanced Blast Furnace", "Enhanced Smoker"). Clears custom names on removal.

**AbstractFurnaceBlockEntityMixin**: Injects at HEAD of `AbstractFurnaceBlockEntity.serverTick()`. When a furnace is part of a registered furnace array:
- Adds +1 extra cooking progress on even game ticks (vanilla already gives +1, so even ticks get +2, odd ticks get +1 = 50% average boost)
- Safety clamp: if `cookingProgress >= cookingTotalTime`, sets to `cookingTotalTime - 1`. Critical because vanilla's completion check is equality (`==`), not `>=`. Without the clamp, progress could overshoot and the smelt would never complete.

### Debug Tooling

**Structure Debug Stick** (`ModItems.STRUCTURE_DEBUG_STICK`): Hold in either hand. Receives color-coded chat messages from both StructureManager and OddstonePortalHandler:

Structure events (from StructureManager):
- Green `[Structure] FORMED:` with type, position, core count
- Red `[Structure] REMOVED:` with type, position

Portal events (from OddstonePortalHandler):
- Yellow `[Portal] ACTIVATED (unlinked)` with position, geometry (WxH), shortened address
- Green `[Portal] ACTIVATED + LINKED` with position, partner position, geometry, address
- Red `[Portal] FAILED: address full` with position
- Red `[Portal] DEACTIVATED` with position and address

Zero overhead when nobody holds it (just a player list scan).

### Adding a New Structure Type

1. Create a class extending `StructureDefinition` in `multiblock/`
2. Define core blocks, frame tag, scan parameters
3. Create a `ScanStrategy` implementation (or use `PatternScanStrategy` if patterns apply)
4. Register the ScanStrategy in the definition
5. Create a `StructureEffectHandler` implementation for the structure's effects
6. Create a block tag JSON for frame blocks
7. Register the definition in `Ideomod.commonSetup()` via `StructureManager.registerDefinition()`
8. If effects need a mixin, add to `ideomod.mixins.json`

---

## 9. BLOCK SPECIFICATIONS

### Oddstone -- 5 Features in One Block

| Feature | Implementation |
|---------|---------------|
| **Mob avoidance** | `isPathfindable()` returns false. `getBlockPathType()` returns `DAMAGE_OTHER` for affected mobs (checked against JSON config list). `getAdjacentBlockPathType()` returns `DANGER_OTHER` for the 1-block avoidance radius. |
| **Redstone insulation** | `isRedstoneConductor=false` set in ModBlocks properties lambda. Acts like glass for redstone. |
| **Sympathetic movement** | Handled entirely by PistonStructureResolverMixin Feature 3. Block itself has no sympathetic code. |
| **One-way stickiness** | `isStickyBlock()` returns true. `canStickTo()` returns true for SlimeBlock, HoneyBlock, OddSlimeBlock, OddstoneBlock. Uses `StickyType.of()` for classification and `StickyType.ODDSTONE.canGrab()` for grab decisions. Reverse grabs blocked by immunity rules 1-3. |
| **Portal frame** | `neighborChanged()` delegates to `OddstonePortalHandler.onNeighborChanged()`. `onRemove()` delegates to `OddstonePortalHandler.onBlockRemoved()`. |

### AbstractMachineBlock -- Shared Base Class

All Machine Family blocks extend `AbstractMachineBlock`, which extends `Block` and implements `EntityBlock` and `IMachineFamily`. Provides default implementations for: `use()` (GUI open), `newBlockEntity()`, `getRenderShape(ENTITYBLOCK_ANIMATED)`, `isStickyBlock(true)`, `canStickTo()` (delegates to `IMachineFamily.canMachineFamilyStickTo()`), `onPlace()`/`onRemove()`/`neighborChanged()` (with `FrameActivation.markDirty()`), `setPlacedBy()` (specialization swap from item NBT), `getDrops()` (conditional BlockEntityTag), `getCloneItemStack()` (registry-based middle-click). Subclasses override only `getMachineType()` for simple specializations, or override lifecycle methods for unique behavior (redstone, push scheduling).

### Machine Frame -- Universal Connector + Machine Family Base

Registry ID: `machine_block` (unchanged for world compatibility). Class: MachineFrameBlock extends AbstractMachineBlock. `MachineType.FRAME`.

| Feature | Implementation |
|---------|---------------|
| **Universal stickiness** | MachineType.FRAME sticks to ALL machine types via `canStickToMachineType()`. Also sticks to Frame Family (active) and is permissive to slime/honey/odd_slime (grab immunity blocks reverse). |
| **Block entity with GUI** | Shares MachineFrameBlockEntity with all specializations. PositionBoundContainer keeps GUI open across swaps. Right-click opens universal machine GUI. |
| **Specialization swap** | When slot contents change, MachineSwapHelper.checkAndSwap() determines target block from item registry. Block replaced in-world, item transferred to new BE via setItemDirect(). |
| **Anti-signal emission** | Only block in Machine Family that can suppress frame networks. Powered Machine Frame emits anti-signal. All other specializations always activate. |
| **Frame activation** | Emits activation (strength 15) when unpowered. Lifecycle hooks call FrameActivation.markDirty(). |
| **Drop preservation** | getDrops() adds BlockEntityTag only when slot has content. Empty frames drop tagless. getCloneItemStack() uses registry-based createCloneStack() (client-safe). |
| **Piston movement** | PushReaction.NORMAL. IPistonMovable preserves slot contents through all move types. |

**Static helpers** (used by other classes):
- `isMachineFamily(Block)` -- delegates to IMachineFamily.isMachineFamily()
- `isRedstoneConduit(Block)` -- true for MachineTrackBlock only

**IMachineFamily interface**: Provides `getMachineType()` for type-aware stickiness. Static `canMachineFamilyStickTo(MachineType myType, BlockState other)` checks: (1) other Machine Family -- types must match or one must be FRAME, (2) Frame Family -- must be active, (3) slime/honey/odd_slime -- permissive for grab immunity.

**MachineSwapHelper**: Central swap utility. Bidirectional registry (Powered Rail <-> Track, Minecart <-> Trolley). `checkAndSwap()` with isSwapping ThreadLocal guard. `createPreloadedStack()` for creative tab. `createDrop()` conditional NBT. `createCloneStack()` registry-based middle-click.

**PositionBoundContainer**: Container backed by Level + BlockPos. Every method call does `level.getBlockEntity(pos)` and forwards to the current MachineFrameBlockEntity. Survives block swaps automatically. Used by MachineFrameMenu on both server and client sides.

### Machine Track -- Isolated Redstone + Trolley Push

Registry ID: `machine_track`. Class: MachineTrackBlock extends AbstractMachineBlock. `MachineType.TRACK`.

Non-directional block. Shares MachineFrameBlockEntity (right-click opens GUI showing powered rail in slot). Maintains internal POWER blockstate (0-15, decay by 1) for Track-to-Track signal chaining. POWER is NOT exposed to vanilla's redstone system (isSignalSource=false, getSignal=0, getDirectSignal=0). Accepts input from conduit blocks (other Tracks, Conductors) via direct blockstate reads and from any vanilla redstone source via level.getSignal(). No input whitelist needed -- since Tracks emit no vanilla signal, there is no feedback risk. On rising edge, pushes ALL adjacent Trolleys opposite to signal source direction. Uses BlockMoveHelper for piston-style movement.

Stickiness: MachineType.TRACK -- sticks to other Tracks + Machine Frames + Frame Family (active). Does NOT stick to Trolleys or other specialization types.

Frame activation: Always emits activation (strength 15). Lifecycle hooks call FrameActivation.markDirty().

### Machine Trolley -- Passive Cargo Carrier

Registry ID: `machine_trolley`. Class: MachineTrolleyBlock extends AbstractMachineBlock. `MachineType.TROLLEY`. ~20 lines.

Non-directional block. Shares MachineFrameBlockEntity (right-click opens GUI showing minecart in slot). No active behavior -- pure cargo. Pushed by Tracks. Stickiness: MachineType.TROLLEY -- sticks to other Trolleys + Machine Frames + Frame Family (active). Does NOT stick to Tracks. Frame activation: always emits (strength 15).

### Machine Conductor -- Solid-Block Redstone Wire

Registry ID: `machine_conductor`. Class: MachineConductorBlock extends AbstractMachineBlock. `MachineType.CONDUCTOR`.

Solid-block redstone wire. Carries vanilla redstone signal (0-15, decays by 1 per block). Connects to vanilla redstone components on all 6 faces. Outputs weak power (getSignal) and strong power (getDirectSignal) on all faces except the input face.

Shares MachineFrameBlockEntity (right-click opens GUI showing redstone dust in slot).

**Input/Output Face Model**: Receives signal on ONE face (input), outputs on other 5. Input face = strongest incoming signal. Stored as `inputFace` (nullable Direction) in MachineFrameBlockEntity. Persists through saves and piston movement.

**Signal types**: Source blocks (levers, repeaters) = no decay. Wire blocks (dust, other Conductors) = -1 decay. Formula: scan all 6 faces, categorize each as source or wire, pick strongest.

**Anti-feedback**: Input face suppression in getSignal/getDirectSignal. Returns 0 when `direction == inputFace.getOpposite()` (querier is on our input side).

**Conductor-to-Conductor**: Uses neighbor's `getSignal()` which respects that neighbor's own input face suppression, then applies -1 decay.

**Dust interaction**: Reads dust POWER directly with -1 decay (dust's getSignal is directional and may return 0 if not "pointing" at us).

Stickiness: MachineType.CONDUCTOR -- sticks to other Conductors + Machine Frames + Frame Family (active). Does NOT stick to Tracks, Trolleys, or other specialization types.

Frame activation: Always emits activation (strength 15). Never suppresses.

**Visual**: Block model has two layers -- base texture (untinted, tintIndex 0) and indicator overlay (tintIndex 1, colored by POWER level via ModColorHandlers). Squared curve gradient: POWER 0 = black, 1-15 = dim-to-bright red.

**Known issues**: (1) Dust sees Conductor strong power through solid blocks (issue #12 -- needs RedStoneWireBlock mixin). ~~(2) Base texture renders black (issue #13 -- FIXED 2026-03-14, cutout render type).~~

---

### Frame Blocks (Iron Frame, Copper Frame)

Transparent decorative blocks with activation-based stickiness. Implements `IFrameBlock` and `SimpleWaterloggedBlock`. `BooleanProperty ACTIVE` (default false). `BooleanProperty WATERLOGGED` (default false, auto-set when placed in water). `isStickyBlock()` returns ACTIVE value. All lifecycle hooks delegate to `FrameActivation.markDirty()`. See section 6b for the activation algorithm.

| Feature | Implementation |
|---------|---------------|
| **Activation-based stickiness** | ACTIVE property driven by FrameActivation. Passive -- frames never decide their own state. Machine Blocks are signal sources. |
| **Material exclusivity** | canStickTo returns true for same-material IFrameBlock. Cross-material IFrameBlock falls through (does not short-circuit to false) so inner stickiness permissive entries can match. |
| **Machine Family interaction** | canStickTo returns true for Machine Family. Bidirectional -- Machine Family's canMachineFamilyStickTo also includes Frame Family. |
| **Slime/honey interaction** | canStickTo returns true for slime/honey/odd slime (permissive). Also permissive to FramedBlock with INNER_STICKY>0 (allows cross-material framed sticky blocks to grab frames). Grab immunity rules block frames from grabbing slime/honey/odd slime. Net: slime can grab active frames, frames cannot grab slime. |
| **Waterlogging** | Implements SimpleWaterloggedBlock. Auto-waterlogs when placed in water. Bucket interaction for add/remove. updateShape() schedules fluid ticks for natural water flow. Water is contained (standard full-cube waterlogged behavior). |
| **Transparency** | RenderType.cutout() + noOcclusion(). Transparent texture pixels render correctly. Inside-face rendering via inverted cuboid in JSON models. |



### Framed Blocks (Iron Framed Block, Copper Framed Block)

Proxy blocks wrapping any valid inner block. Implements `IFrameBlock` + `EntityBlock`. Shared `FramedBlockEntity` stores inner block ResourceLocation. See section 6c for full system documentation.

| Feature | Implementation |
|---------|---------------|
| **Inner block storage** | FramedBlockEntity stores ResourceLocation. IPistonMovable for piston data preservation. Client sync via getUpdateTag/getUpdatePacket. |
| **Dynamic rendering** | Two-layer BESR + JSON model. Inner texture per face from inner block's BakedModel. Epsilon inset prevents z-fighting. Per-face light sampling. |
| **Behavioral delegation** | ~15 methods delegate to inner block's defaultBlockState(). Mining speed, sound, friction, light, redstone, pathfinding all preserved. |
| **Activation-based stickiness** | IFrameBlock with ACTIVE property. Same FrameActivation wipe-and-rebuild system as Frame Blocks. |
| **Material exclusivity** | Iron framed sticks to iron framed/iron frame only. Copper to copper only. Both stick to Machine Family when active. |
| **Crafting** | CustomRecipe: 1 frame item + 1 frameable block = framed block with NBT. |
| **Middle-click** | getCloneItemStack() reads block entity, returns proper NBT ItemStack. |
| **F3 debug** | FramedBlockDebugOverlay shows "Inner Block: minecraft:stone" on F3 right side. |

### Odd Slime Block

Extends `DirectionalBlock`. FACING property, piston-style placement. `isStickyBlock()` returns true. `canStickTo()` returns false for SlimeBlock and HoneyBlock (mutual exclusion), true for everything else. Directional stickiness gating handled entirely by StickinessRules directional rule + mixin.

### Drill Block [ON HOLD]

Extends `DirectionalBlock`. FACING + POWERED properties. On rising redstone edge: mines 3x3 grid in front of drill face. Drops items at drill position. Skips air and indestructible blocks. Uses perpendicular direction calculation for the 3x3 plane.

### Oddstone Repulser / Attractor

Both extend `AbstractRangedPistonBlock`. FACING + POWERED properties. Block event pattern for client-server sync. Repulser finds nearest Oddstone in line, pushes away. Attractor finds farthest Oddstone, pulls toward device. Each is ~15 lines -- all shared logic lives in the base class. Movement delegated to BlockMoveHelper via RangedPistonUtil.


## 10. JUDGEMENT EFFECT

A rare atmospheric event. When a player kills a non-hostile mob (Animal or AbstractVillager), there's a 1/200 chance all passive mobs within 32 blocks freeze and stare at the player for 5 seconds.

**Trigger**: `LivingDeathEvent` on Forge event bus. Checks: server side, killed by player, victim is non-hostile, no active session for this player, then rolls 1/200.

**Freeze mechanism**: `mob.setNoAi(true)` on each target mob. Same as vanilla `{NoAI:1}` -- freezes all AI goals and movement.

**Stare mechanism**: Every server tick, for each frozen mob: calculate vector from mob eye position to player eye position, convert to yaw/pitch via `atan2`, set `yRot`, `xRot`, `yHeadRot`, `yBodyRot`.

**Cleanup**: After 100 ticks (5 seconds) or if player disconnects, calls `mob.setNoAi(false)` on all mobs. Session removed from `activeSessions` map.

**Manual trigger**: `/judgement` command bypasses the chance roll and calls `trigger()` directly.

**Session storage**: `Map<UUID, JudgementSession>` keyed by player UUID. Prevents multiple simultaneous sessions per player.

---

## 11. CONFIGURATION

### Oddstone Config (`config/ideomod/oddstone.json`)

Auto-generated on first launch. Loaded lazily on first access via double-checked locking (thread-safe).

**JSON structure:**
```json
{
  "_comment1": "Oddstone Configuration",
  "_comment2": "sympathetic_radius: How far Oddstone blocks link when pushed",
  "_comment3": "affected_mobs: Mobs that refuse to walk on Oddstone",
  "sympathetic_radius": 5,
  "affected_mobs": [
    "minecraft:axolotl",
    "minecraft:blaze",
    ...sorted alphabetically...
  ]
}
```

**sympathetic_radius**: Integer, default 5, clamped 1-32. How many blocks apart Oddstones can "feel" each other for sympathetic movement.

**affected_mobs**: Array of full registry IDs. Mobs in this list get `DAMAGE_OTHER` pathfinding from Oddstone. Default includes all land-based mobs from vanilla 1.20.1 (49 total). Excludes: purely aquatic (cod, salmon, etc.), purely flying (ghast, phantom, bat, etc.), wither, ender dragon.

**Reload**: `OddstoneConfig.reloadConfig()` clears and reloads. Not currently wired to any in-game command.

---

## 12. RESOURCE FILE TEMPLATES

### Blockstate -- Simple (no variants)

`assets/ideomod/blockstates/oddstone.json`:
```json
{
  "variants": {
    "": {
      "model": "ideomod:block/oddstone"
    }
  }
}
```

### Blockstate -- Directional (FACING + POWERED)

`assets/ideomod/blockstates/oddstone_repulser.json`:

Enumerates all 12 combinations (6 facing x 2 powered). North is the default model orientation. Other facings use `"y"` rotation (90/180/270 for E/S/W) and `"x"` rotation (270 for up, 90 for down). POWERED currently doesn't change the model but each state must be declared.

Pattern:
```json
{
  "variants": {
    "facing=north,powered=false": { "model": "ideomod:block/name" },
    "facing=north,powered=true":  { "model": "ideomod:block/name" },
    "facing=south,powered=false": { "model": "ideomod:block/name", "y": 180 },
    "facing=south,powered=true":  { "model": "ideomod:block/name", "y": 180 },
    "facing=west,powered=false":  { "model": "ideomod:block/name", "y": 270 },
    "facing=west,powered=true":   { "model": "ideomod:block/name", "y": 270 },
    "facing=east,powered=false":  { "model": "ideomod:block/name", "y": 90 },
    "facing=east,powered=true":   { "model": "ideomod:block/name", "y": 90 },
    "facing=up,powered=false":    { "model": "ideomod:block/name", "x": 270 },
    "facing=up,powered=true":     { "model": "ideomod:block/name", "x": 270 },
    "facing=down,powered=false":  { "model": "ideomod:block/name", "x": 90 },
    "facing=down,powered=true":   { "model": "ideomod:block/name", "x": 90 }
  }
}
```

For FACING-only (no POWERED), same pattern with just 6 entries (one per facing, no powered variants).

### Block Model -- Simple (cube_all)

`assets/ideomod/models/block/oddstone.json`:
```json
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "ideomod:block/oddstone"
  }
}
```

### Block Model -- Directional (front/back/side)

`assets/ideomod/models/block/oddstone_repulser.json`:

Uses custom elements instead of a parent, assigning front/back/side textures to faces. North = front (default facing). Side texture gets rotation values on non-front/back faces to maintain visual continuity when the blockstate rotates the model.

```json
{
  "parent": "minecraft:block/block",
  "textures": {
    "front": "ideomod:block/name_front",
    "back": "ideomod:block/name_back",
    "side": "ideomod:block/name_side",
    "particle": "ideomod:block/name_side"
  },
  "elements": [
    {
      "from": [0, 0, 0],
      "to": [16, 16, 16],
      "faces": {
        "north": { "uv": [0,0,16,16], "texture": "#front", "cullface": "north" },
        "south": { "uv": [0,0,16,16], "texture": "#back", "cullface": "south" },
        "up":    { "uv": [0,0,16,16], "texture": "#side", "cullface": "up" },
        "down":  { "uv": [0,0,16,16], "texture": "#side", "cullface": "down", "rotation": 180 },
        "east":  { "uv": [0,0,16,16], "texture": "#side", "cullface": "east", "rotation": 90 },
        "west":  { "uv": [0,0,16,16], "texture": "#side", "cullface": "west", "rotation": 270 }
      }
    }
  ]
}
```

### Item Model (inherits block model)

`assets/ideomod/models/item/oddstone.json`:
```json
{
  "parent": "ideomod:block/oddstone"
}
```

Same pattern for all block items -- just parent the block model.

### Loot Table (standard self-drop)

`data/ideomod/loot_tables/blocks/oddstone.json`:
```json
{
  "type": "minecraft:block",
  "pools": [
    {
      "rolls": 1,
      "bonus_rolls": 0,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "ideomod:oddstone"
        }
      ],
      "conditions": [
        {
          "condition": "minecraft:survives_explosion"
        }
      ]
    }
  ]
}
```

### Recipe (example: shapeless)

`data/ideomod/recipes/odd_slime.json`:
```json
{
  "type": "minecraft:crafting_shapeless",
  "ingredients": [
    { "item": "minecraft:slime_block" },
    { "item": "ideomod:oddstone" }
  ],
  "result": {
    "item": "ideomod:odd_slime",
    "count": 1
  }
}
```

### Language File

`assets/ideomod/lang/en_us.json`:

Pattern: `"block.ideomod.registry_name": "Display Name"` for blocks, `"itemGroup.ideomod.tab_name": "Tab Name"` for creative tabs.

Current entries: ideoblock, drill, oddstone, machine_block, oddstone_portal, odd_slime, oddstone_repulser, oddstone_attractor, machine_track, machine_trolley, iron_frame, copper_frame, iron_framed_block, copper_framed_block, plus creative tabs (ideomod_tab, framed_blocks_tab).

---

## 13. TAG CONTENTS

### portal_corner_blocks (14 blocks)

`data/ideomod/tags/blocks/portal_corner_blocks.json`:

minecraft:coal_block, minecraft:iron_block, minecraft:copper_block, minecraft:gold_block, minecraft:redstone_block, minecraft:lapis_block, minecraft:diamond_block, minecraft:emerald_block, minecraft:netherite_block, minecraft:quartz_block, minecraft:amethyst_block, minecraft:raw_iron_block, minecraft:raw_copper_block, minecraft:raw_gold_block.

14 types -> 14^4 = 38,416 unique addresses per dimension.

### furnace_array_frame (18 blocks)

`data/ideomod/tags/blocks/furnace_array_frame.json`:

minecraft:stone, minecraft:cobblestone, minecraft:stone_bricks, minecraft:mossy_stone_bricks, minecraft:cracked_stone_bricks, minecraft:chiseled_stone_bricks, minecraft:smooth_stone, minecraft:bricks, minecraft:deepslate, minecraft:cobbled_deepslate, minecraft:polished_deepslate, minecraft:deepslate_bricks, minecraft:deepslate_tiles, minecraft:cut_sandstone, minecraft:cut_red_sandstone, minecraft:polished_andesite, minecraft:polished_diorite, minecraft:polished_granite.

Stone-family blocks that form the frame around furnace array multiblocks. Data-driven via tag so datapacks can add/remove valid frame materials.

---

## 14. KNOWN ISSUES & TECHNICAL DEBT

| # | Issue | Severity | Location |
|---|-------|----------|----------|
| 1 | OddstoneConfig.reloadConfig() not wired to command | Missing feature | config/OddstoneConfig.java |
| 2 | Item model translucency | Cosmetic | Translucent inner blocks (glass, slime) render opaque in inventory. Quad-compositing approach can't handle separate render passes. In-world rendering is correct. |
| ~~3~~ | ~~Framed block transit rendering (Trolley/Repulser/Attractor)~~ | ~~Visual bug~~ | ~~FIXED 2026-02-28c. BlockMoveHelper step 2.6 populates MovingFrameData on client side.~~ |
| ~~4~~ | ~~Framed blocks not grabbed by slime during Repulser/Attractor moves~~ | ~~Movement bug~~ | ~~FIXED 2026-03-01. Backward walk grab immunity was firing on both canStickTo calls in the pair, blocking the behind block's passive response. Fixed with call-pair toggle in PistonStructureResolverMixin.~~ |
| ~~5~~ | ~~Frame inside-face rendering~~ | ~~Cosmetic~~ | ~~FIXED. Inverted cuboid elements added to frame JSON models.~~ |
| ~~6~~ | ~~Sympathetic framed oddstone transit rendering~~ | ~~Visual bug~~ | ~~FIXED 2026-02-28c. PistonBaseBlockClientMixin sympathetic scan added to retraction capture.~~ |
| ~~7~~ | ~~Sympathetic retraction with machine-activated framed blocks~~ | ~~Data loss bug~~ | ~~FIXED 2026-02-28c. PistonBaseBlockMixin.scanSympathetic() now calls scanStickyGroup() on each found oddstone.~~ |
| ~~8~~ | ~~Machine Track signal bleeds into attached Machine Blocks via Trolley~~ | ~~Gameplay bug~~ | ~~FIXED 2026-03-08. Two-part fix: (1) MachineTrackBlock redstone isolation -- isSignalSource=false, getSignal=0, getDirectSignal=0. Track POWER is now purely internal bookkeeping read via direct blockstate access in calculatePower(). No vanilla signal leakage through solid blocks. (2) FrameActivation suppression rule -- only MachineFrameBlock can emit anti-signal. All other Machine Family blocks always emit activation regardless of redstone state. Files: MachineTrackBlock.java, FrameActivation.java.~~ |
| ~~9~~ | ~~Repulser/Attractor cannot target Framed Oddstone~~ | ~~Movement bug~~ | ~~FIXED 2026-03-08. ODDSTONE_ONLY predicate expanded to check FramedBlock with INNER_STICKY=ODDSTONE.~~ |
| ~~10~~ | ~~Framed blocks not pulled from behind in push direction~~ | ~~Movement bug~~ | ~~FIXED 2026-03-07. Root cause was vanilla's backward walk silently skipping unpushable blocks (isPushable false -> stop walk, return true) so Feature 5 at HEAD of addBlockLine never fired. Fix: Feature 7 post-gathering anchor scan in TAIL of addBranchingBlocks. Scans toPush neighbors for active framed blocks with immovable inners connected via frame stickiness. If found, fails resolve.~~ |
| ~~11~~ | ~~Framed inner blocks with light emission do not emit light~~ | ~~Missing delegation~~ | ~~FIXED 2026-03-08. Added INNER_LIGHT blockstate property (0-15), lightLevel predicate in ModBlocks.java, and getStateForPlacement() pre-population from item NBT.~~ |
| 12 | Conductor strong power visible to dust through solid blocks | Gameplay bug | MachineConductorBlock.getDirectSignal outputs strong power. Vanilla dust avoids cross-wire through-block power because all dust shares one block instance and shouldSignal suppresses ALL dust during any dust scan. Conductor is a different class so dust's shouldSignal doesn't affect it. Fix requires mixin into RedStoneWireBlock. File: MachineConductorBlock.java. |
| ~~13~~ | ~~Machine Conductor base texture renders black~~ | ~~Visual bug~~ | ~~FIXED 2026-03-14. Root cause: indicator overlay used default "solid" render type, which renders transparent pixels as opaque black. The overlay's tintIndex 1 at power 0 produces color 0x000000, creating a fully opaque black shell. Fix: added `"render_type": "minecraft:cutout"` to model JSON. Also removed unnecessary tintindex from base layer. File: models/block/machine_conductor.json.~~ |
| 14 | Framed redstone blocks don't emit power on frame activation | Gameplay bug | NEEDS VERIFICATION. Appeared to work during 2026-03-15 testing but no intentional fix was applied. May have been incidentally resolved by Cowork BFS refactors (Audit #15) or AbstractMachineBlock extraction (Audit #13). An attempted fix using updateNeighborsAt in recalculate() caused infinite neighbor cascade loops and was reverted. If it resurfaces, correct approach is deferred notification with re-entrancy protection. File: FrameActivation.java. |
| ~~15~~ | ~~Vanilla metal first-push failure after frame network change~~ | ~~Movement bug~~ | ~~FIXED 2026-03-15. Root cause: activatedVanillaMetals global set was atomically swapped per-network, so each recalculate() replaced the ENTIRE set with only its own network's metals. When multiple networks were processed in one flush cycle (e.g., breaking a frame that splits a network), the second recalculate wiped the first's results. Fix: changed to ConcurrentHashMap.newKeySet() with per-network remove-then-add -- each recalculate() only removes/adds entries belonging to its own network. File: FrameActivation.java.~~ |


## 15. CONVENTIONS & PATTERNS

**Registration**: DeferredRegister for blocks, items, creative tabs. All wired in Ideomod() constructor.

**Directional blocks**: Extend DirectionalBlock. Add FACING to state definition. Placement: `context.getNearestLookingDirection().getOpposite()`. Always include `rotate()` and `mirror()` overrides.

**Redstone-activated blocks**: Add POWERED (BooleanProperty) to state definition. In `neighborChanged()`: compare `level.hasNeighborSignal(pos)` with `state.getValue(POWERED)`. Update state. On rising edge, do the thing. Server-side only (`if (!level.isClientSide)`).

**Block events** (for client-server sync): Queue in `neighborChanged()` with `level.blockEvent(pos, this, eventId, encodedParam)`. Handle in `triggerEvent()` which runs on BOTH sides. Encode direction as `facing.get3DDataValue()`, decode with `Direction.from3DDataValue(param)`. Do NOT guard meaningful logic with `instanceof ServerLevel`.

**ThreadLocal coordination**: For passing context from caller into mixin. Set before creating the object that triggers the mixin, read inside the mixin, clear in a finally block. Class must live outside the mixin package.

**Stickiness**: canStickTo = permissive (Layer 1). StickinessRules = restrictive (Layer 2+3). Never express one-way behavior in canStickTo alone. See section 4 for the golden rule.

**File naming**: All lowercase. Block classes use PascalCase matching the block concept (OddstoneBlock, MachineFrameBlock). Registry names use snake_case (oddstone, machine_block).

---

## 16. ADDING NEW CONTENT CHECKLISTS

### New Simple Block

1. Create block class in `block/` (extend Block, add properties)
2. Register in `ModBlocks.java` with DeferredRegister
3. Register BlockItem in `ModItems.java`
4. Add to `ModCreativeTabs.java` displayItems
5. Add translation to `en_us.json`
6. Create blockstate JSON: `assets/ideomod/blockstates/<name>.json`
7. Create block model JSON: `assets/ideomod/models/block/<name>.json`
8. Create item model JSON: `assets/ideomod/models/item/<name>.json` (parent block model)
9. Create loot table: `data/ideomod/loot_tables/blocks/<name>.json`
10. Create recipe (if craftable): `data/ideomod/recipes/<name>.json`
11. Create texture: `assets/ideomod/textures/block/<name>.png`
12. Add to pickaxe mineable tag if applicable: `data/minecraft/tags/blocks/mineable/pickaxe.json`

### New Directional Block

Same as above, plus:
- Extend DirectionalBlock instead of Block
- Add FACING to state definition
- Add getStateForPlacement() with getNearestLookingDirection().getOpposite()
- Add rotate() and mirror() overrides
- Blockstate JSON needs all facing variants with x/y rotations (see template in section 11)
- Block model needs front/back/side textures
- Textures needed: `<name>_front.png`, `<name>_back.png`, `<name>_side.png`

### New Directional + Redstone Block

Same as directional, plus:
- Add POWERED to state definition
- Add neighborChanged() with rising-edge detection
- Set POWERED default in registerDefaultState()
- Set initial POWERED in getStateForPlacement()
- Blockstate JSON needs facing x powered variants (12 entries)

### New Sticky Block

1. Decide relationships with all existing sticky types (see section 4 matrix)
2. Write canStickTo() -- permissive (true for any type where either direction is wanted)
3. Update OTHER blocks' canStickTo() if they need to interact with yours
4. Register grab immunity rules in Ideomod.registerStickinessRules()
5. Register directional rules if stickiness depends on push direction
6. Update the stickiness matrix in this document AND STICKINESS_SYSTEM.md
