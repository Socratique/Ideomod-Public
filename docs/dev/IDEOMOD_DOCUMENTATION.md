# IDEOMOD TECHNICAL DOCUMENTATION
*Comprehensive reference for the Ideomod codebase -- architecture, systems, templates, and implementation details.*
*Last updated: 2026-04-19 (Frame Family simplification: removed raw vanilla iron/copper as conductors and sticky members; removed cross-frame mediation and INNER_CONDUCTOR property)*

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

Mixin config: `ideomod.mixins.json` in resources root. The `mixins` array declares `PistonStructureResolverMixin`, `AbstractFurnaceBlockEntityMixin`, `PistonBaseBlockMixin`, `PistonMovingBlockEntityMixin`, `EntityMovementMixin`, `LevelSignalMixin`, `RedStoneWireBlockMixin` and `client.PistonBaseBlockClientMixin`; the `client` array declares `client.PistonMovingBlockEntityRendererMixin` (moved there 2026-10-13: it targets a client-only class and crashed dedicated servers). Refmap: `ideomod.refmap.json`. Compatibility level: JAVA_17.

gradle.properties values: `mod_id=ideomod`, `mod_name=Ideomod`, `mod_version=0.1.1`, `mod_group_id=com.ideomancer.ideomod`, `mod_authors=Ideomancer`.

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
  |     |-- HollowstoneBlock.java        Transparent shell block (FILLED, EntityBlock)
  |     |-- HollowstoneBlockEntity.java  Inner block storage + client sync
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
  |     |     |                                getRedstoneOutput(BlockState) for MachineRedstoneHelper.
  |     |     |                                onSpecItemChanging/onSpecItemChanged callbacks for
  |     |     |                                pre/post specialization item change lifecycle.
  |     |     |-- MachineRedstoneHelper.java   Shared signal detection utility. Single source of truth
  |     |     |                                for getIncomingSignalFromFace(), scanAllFaces(),
  |     |     |                                getFaceFromSourcePos(). Generic device detection via
  |     |     |                                AbstractMachineBlock.getRedstoneOutput().
  |     |     |-- MachineSwapHelper.java       Swap utility: item<->block registry, checkAndSwap(),
  |     |     |                                createPreloadedStack(), createDrop(), createCloneStack().
  |     |     |                                Client sync via syncToClients() after swap.
  |     |     |-- PositionBoundContainer.java  Position-bound Container. Auto-resolves to current BE.
  |     |     |                                isPipe(), isRepeater(), isTransformer(). Dynamic slots.
  |     |     |-- MachineFrameBlock.java       Extends AbstractMachineBlock. MachineType.FRAME. ~40 lines.
  |     |     |-- MachineFrameBlockEntity.java Shared BE for all machine blocks. Single slot + IPistonMovable.
  |     |     |                                Protected constructor for subclass BE types.
  |     |     |                                transmitterSignalStrength for wireless redstone.
  |     |     |                                pistonSettling flag for 1-tick settlement window.
  |     |     |                                onDataPacket() for client-side tint updates.
  |     |     |-- MachinePipeBlock.java        Pipe specialization. Push-only item transport + ticker.
  |     |     |-- MachinePipeBlockEntity.java  Pipe BE subclass. Transit slot + cooldown + round-robin.
  |     |     |                                Client sync: getUpdatePacket(), getUpdateTag(), syncToClients().
  |     |     |-- MachineFluidPipeBlock.java   Fluid pipe specialization. Fluid vacuum/depositor + ticker.
  |     |     |-- MachineFluidPipeBlockEntity.java  Fluid pipe BE subclass. Fluid type + cooldown + round-robin.
  |     |     |                                IFluidHandler capability. Bucket state sync in setFluidType().
  |     |     |-- FluidType.java               StringRepresentable enum: EMPTY, WATER, LAVA. Blockstate property.
  |     |     |-- MachinePistonBlock.java      Piston specialization. Omnidirectional extend/retract.
  |     |     |                                BESR animation via MachineFrameBlockEntity arm state.
  |     |     |                                Ticker advances animation. BlockMoveHelper for push/pull.
  |     |     |-- MachineStickyPistonBlock.java  Extends MachinePistonBlock. isSticky()=true. Pulls on retract.
  |     |     |-- MachinePistonHeadBlock.java  8x8x16 arm block. FACING + STICKY. PushReaction.BLOCK.
  |     |     |                                updateShape validates parent piston. No drops.
  |     |     |-- MachineBraceBlock.java      Extends AbstractMachineBlock. MachineType.BRACE.
  |     |     |                                Structural reinforcement. Push limit constants.
  |     |     |-- MachineFrameMenu.java        Position-bound menu. Fixed content height (89px for
  |     |     |                                all machines). ContainerData size 6.
  |     |     |-- MachineTrackBlock.java       Extends AbstractMachineBlock. MachineType.TRACK. Custom redstone + push.
  |     |     |-- MachineTrolleyBlock.java     Extends AbstractMachineBlock. MachineType.TROLLEY. ~20 lines.
  |     |     |-- MachineConductorBlock.java   Extends AbstractMachineBlock. MachineType.CONDUCTOR. Custom redstone.
  |     |     |-- MachineRepeaterBlock.java   Extends AbstractMachineBlock. MachineType.REPEATER. Custom redstone.
  |     |     |                                Signal detection via MachineRedstoneHelper.
  |     |     |-- MachineToggleBlock.java     Extends AbstractMachineBlock. MachineType.TOGGLE. Edge-triggered flip-flop.
  |     |     |                                Signal detection via MachineRedstoneHelper.
  |     |     |-- MachineTransformerBlock.java Extends AbstractMachineBlock. MachineType.TRANSFORMER.
  |     |     |                                Configurable signal level setter. Signal detection via
  |     |     |                                MachineRedstoneHelper.
  |     |     |-- MachineTimerBlock.java       Extends AbstractMachineBlock. MachineType.TIMER.
  |     |     |                                Edge-triggered timed output. Signal detection via
  |     |     |                                MachineRedstoneHelper.
  |     |     |-- MachineGrabberBlock.java    Extends AbstractMachineBlock. MachineType.SLIME_GRABBER / HONEY_GRABBER.
  |     |     |                                Edge-triggered toggleable sticky block. POWERED blockstate.
  |     |     |                                Piston settlement (1-tick pistonSettling window).
  |     |     \-- MachineTransmitterBlock.java Extends AbstractMachineBlock. MachineType.TRANSMITTER.
  |     |                                      Wireless redstone via Oddstone channels. POWERED + SOURCE
  |     |                                      blockstates. Signal attenuation. Anti-feedback architecture.
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
  |     |-- ModColorHandlers.java        Block/item color tinting (Conductor power indicator,
  |     |                                Fluid Pipe biome water tint, Transmitter level-aware
  |     |                                signal strength tint via BE read)
  |     |-- FramedBlockDebugOverlay.java F3 debug overlay for inner block info
  |     |-- gui/
  |     |     \-- MachineFrameScreen.java      Dynamic GUI screen for all Machine Family (spritesheet)
  |     \-- render/
  |           |-- FramedBlockRenderer.java     BESR for framed blocks (inner texture at rest)
  |           |-- FramedBlockItemModel.java    Custom BakedModel for framed items (inner texture in inventory)
  |           |-- FramedBlockRenderTypes.java  Custom RenderType for framed block overlays
  |           |-- MovingFrameData.java         Client-side cache for inner block IDs during piston transit
  |           |-- HollowstoneRenderer.java     BESR for Hollowstone (inner block at 0.998 scale)
  |           \-- MachinePistonArmRenderer.java  BESR for piston arm animation. Renders head model
  |                                              at interpolated offset during 2-tick extend/retract.
  |                                              Registered for MACHINE_FRAME BE type.
  |-- worldgen/
  |     |-- ModFeatures.java             DeferredRegister for Features + ChunkGenerator codecs
  |     |-- HollowstoneLavaFormation.java  Custom Feature: lava shore oddstone + hollowstone
  |     \-- OreOnlyChunkGenerator.java   Debug ChunkGenerator: strips non-ore blocks
  |-- config/
  |     \-- OddstoneConfig.java          JSON config: sympathetic radius + mob list
  |-- effect/
  |     \-- JudgementEffect.java         Rare mob-stare event (Forge event bus)
  |-- gametest/
  |     \-- IdeomodGameTests.java                In-world regression tests (./gradlew runGameTestServer)
  |-- mixin/
  |     |-- PistonStructureResolverMixin.java    8-feature mixin (see section 5)
  |     |-- PistonBaseBlockMixin.java            Allows IPistonMovable BE movement + saves data + immovable inner
  |     |-- PistonMovingBlockEntityMixin.java    Restores IPistonMovable data after animation
  |     |-- EntityMovementMixin.java             Entity speed/jump factor for framed blocks
  |     |-- AbstractFurnaceBlockEntityMixin.java  Furnace array speed boost (section 8)
  |     |-- LevelSignalMixin.java                Redstone Rod phantom signal injection
  |     |-- RedStoneWireBlockMixin.java          Hides Conductor strong power while dust computes its power (Known Issue #3)
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
| `cobbled_oddstone` | CobbledOddstoneBlock | Hardness 2.0/6.0, pickaxe, stone sound. `isRedstoneConductor=false`. Full block, sympathetic channel "cobbled_oddstone". |
| `oddstone_bricks` | OddstoneBricksBlock | Hardness 2.0/6.0, pickaxe, stone sound. `isRedstoneConductor=false`. Full block, sympathetic channel "oddstone_bricks". |
| `oddstone_wall` | SympatheticWallBlock | Wall variant, sympathetic channel "oddstone_wall". `isStickyBlock=true`, `canStickTo=false`. |
| `oddstone_stairs` | SympatheticStairBlock | Stair variant, sympathetic channel "oddstone_stairs". `isStickyBlock=true`, `canStickTo=false`. |
| `oddstone_slab` | SympatheticSlabBlock | Slab variant, sympathetic channel "oddstone_slab". `isStickyBlock=true`, `canStickTo=false`. |
| `cobbled_oddstone_wall` | SympatheticWallBlock | Wall variant, sympathetic channel "cobbled_oddstone_wall". `isStickyBlock=true`, `canStickTo=false`. |
| `cobbled_oddstone_stairs` | SympatheticStairBlock | Stair variant, sympathetic channel "cobbled_oddstone_stairs". `isStickyBlock=true`, `canStickTo=false`. |
| `cobbled_oddstone_slab` | SympatheticSlabBlock | Slab variant, sympathetic channel "cobbled_oddstone_slab". `isStickyBlock=true`, `canStickTo=false`. |
| `oddstone_brick_wall` | SympatheticWallBlock | Wall variant, sympathetic channel "oddstone_brick_wall". `isStickyBlock=true`, `canStickTo=false`. |
| `oddstone_brick_stairs` | SympatheticStairBlock | Stair variant, sympathetic channel "oddstone_brick_stairs". `isStickyBlock=true`, `canStickTo=false`. |
| `oddstone_brick_slab` | SympatheticSlabBlock | Slab variant, sympathetic channel "oddstone_brick_slab". `isStickyBlock=true`, `canStickTo=false`. |
| `machine_block` | MachineFrameBlock | Block entity. Hardness 3.0/6.0, pickaxe, metal sound. `isRedstoneConductor=false`. `PushReaction.NORMAL` (movable by pistons via PistonBaseBlockMixin). |
| `odd_slime` | OddSlimeBlock | Friction 0.8F (slippery), slime sound, `noOcclusion()`. |
| `oddstone_portal` | OddstonePortalBlock | `noCollission()`, `noOcclusion()`, hardness -1 (indestructible), light 11, `PushReaction.BLOCK`, glass sound, `noLootTable()`. |
| `oddstone_repulser` | OddstoneRepulserBlock | Hardness 3.0/6.0, pickaxe, stone sound. Extends AbstractRangedPistonBlock. |
| `oddstone_attractor` | OddstoneAttractorBlock | Same properties as Repulser. Extends AbstractRangedPistonBlock. |
| `machine_track` | MachineTrackBlock | Hardness 3.0/6.0, pickaxe, metal sound. `isRedstoneConductor=false`. Machine Family stickiness + redstone conduction. |
| `machine_trolley` | MachineTrolleyBlock | Hardness 3.0/6.0, pickaxe, metal sound. Machine Family stickiness. No conduction. |
| `machine_conductor` | MachineConductorBlock | Hardness 3.0/6.0, pickaxe, metal sound. `isRedstoneConductor=false`. Machine Family stickiness + vanilla redstone signal (0-15). |
| `machine_pipe` | MachinePipeBlock | Hardness 3.0/6.0, pickaxe, metal sound. `PushReaction.NORMAL`. FILLED BooleanProperty. Own BE type (MACHINE_PIPE). Ticker for item transfer. |
| `machine_fluid_pipe` | MachineFluidPipeBlock | Hardness 3.0/6.0, pickaxe, metal sound. `PushReaction.NORMAL`. FLUID_TYPE EnumProperty. Own BE type (MACHINE_FLUID_PIPE). Ticker. Light 10 for lava. |
| `machine_piston` | MachinePistonBlock | Hardness 3.0/6.0, pickaxe, metal sound. `PushReaction.NORMAL`. MachineType.PISTON. Omnidirectional. Ticker for arm animation. |
| `machine_sticky_piston` | MachineStickyPistonBlock | Same properties as Machine Piston. MachineType.STICKY_PISTON. Sticky pull on retraction. |
| `machine_piston_head` | MachinePistonHeadBlock | Hardness 0.5, metal sound. `PushReaction.BLOCK`. `noLootTable()`, `noOcclusion()`. FACING + STICKY. Not player-placeable. 8x8x16 collision shape. |
| `machine_brace` | MachineBraceBlock | Hardness 3.0/6.0, pickaxe, metal sound. `PushReaction.NORMAL`. MachineType.BRACE. Structural reinforcement -- push limit increase via PistonStructureResolverMixin. |
| `machine_repeater` | MachineRepeaterBlock | Hardness 3.0/6.0, pickaxe, metal sound. `isRedstoneConductor=false`. `PushReaction.NORMAL`. MachineType.REPEATER. Omnidirectional redstone repeater. POWERED + DELAY (1-4). |
| `machine_toggle` | MachineToggleBlock | Hardness 3.0/6.0, pickaxe, metal sound. `isRedstoneConductor=false`. `PushReaction.NORMAL`. MachineType.TOGGLE. Edge-triggered flip-flop. POWERED + INPUT_FACE. |
| `machine_transformer` | MachineTransformerBlock | Hardness 3.0/6.0, pickaxe, metal sound. `isRedstoneConductor=false`. `PushReaction.NORMAL`. MachineType.TRANSFORMER. Configurable signal level setter. POWERED + OUTPUT_LEVEL (1-15). |
| `machine_timer` | MachineTimerBlock | Hardness 3.0/6.0, pickaxe, metal sound. `isRedstoneConductor=false`. `PushReaction.NORMAL`. MachineType.TIMER. Edge-triggered timed output. POWERED. |
| `machine_engine` | MachineEngineBlock | Hardness 3.0/6.0, pickaxe, metal sound. `isRedstoneConductor=false`. `PushReaction.NORMAL`. MachineType.ENGINE. Fuel-burning pulse clock. LIT + POWERED. Own BE type (MACHINE_ENGINE). |
| `machine_slime_grabber` | MachineGrabberBlock | Hardness 3.0/6.0, pickaxe, metal sound. `PushReaction.NORMAL`. MachineType.SLIME_GRABBER. Edge-triggered toggleable sticky block. POWERED. |
| `machine_honey_grabber` | MachineGrabberBlock | Hardness 3.0/6.0, pickaxe, metal sound. `PushReaction.NORMAL`. MachineType.HONEY_GRABBER. Edge-triggered toggleable sticky block. POWERED. |
| `machine_transmitter` | MachineTransmitterBlock | Hardness 3.0/6.0, pickaxe, metal sound. `isRedstoneConductor=false`. `PushReaction.NORMAL`. MachineType.TRANSMITTER. Wireless redstone. POWERED + SOURCE. |
| `iron_frame` | FrameBlock | FrameMaterial.IRON, strength 3.0/6.0, pickaxe, metal sound. `noOcclusion()`. Waterloggable (SimpleWaterloggedBlock). |
| `copper_frame` | FrameBlock | FrameMaterial.COPPER, strength 2.5/6.0, pickaxe, copper sound. `noOcclusion()`. Waterloggable (SimpleWaterloggedBlock). |
| `iron_framed_block` | FramedBlock | FrameMaterial.IRON, shared `FRAMED_REDSTONE_CONDUCTOR` predicate, `lightLevel` reads INNER_LIGHT. EntityBlock. `noOcclusion()`. Properties: ACTIVE, FACING, INNER_STICKY, INNER_LIGHT. |
| `copper_framed_block` | FramedBlock | FrameMaterial.COPPER, shared `FRAMED_REDSTONE_CONDUCTOR` predicate, `lightLevel` reads INNER_LIGHT. EntityBlock. `noOcclusion()`. Properties: ACTIVE, FACING, INNER_STICKY, INNER_LIGHT. |
| `hollow_stone` | HollowstoneBlock | MapColor.STONE, strength 1.5/6.0, pickaxe, stone sound. `noOcclusion()`. EntityBlock. FILLED BooleanProperty. BE type: HOLLOW_STONE. |

### ModItems (DeferredRegister<Item>)

BlockItems for every block EXCEPT `oddstone_portal` (not player-placeable, same pattern as vanilla nether portal). All use `new Item.Properties()` with no special settings. Includes: ideoblock, drill, oddstone, cobbled_oddstone, oddstone_bricks, oddstone_wall, oddstone_stairs, oddstone_slab, cobbled_oddstone_wall, cobbled_oddstone_stairs, cobbled_oddstone_slab, oddstone_brick_wall, oddstone_brick_stairs, oddstone_brick_slab, machine_block, odd_slime, oddstone_repulser, oddstone_attractor, machine_track, machine_trolley, iron_frame, copper_frame.

BlockItems: `MACHINE_CONDUCTOR` for MachineConductorBlock, `MACHINE_PIPE` for MachinePipeBlock, `MACHINE_PISTON` for MachinePistonBlock, `MACHINE_STICKY_PISTON` for MachineStickyPistonBlock, `MACHINE_BRACE` for MachineBraceBlock, `MACHINE_REPEATER` for MachineRepeaterBlock, `MACHINE_TOGGLE` for MachineToggleBlock, `MACHINE_TRANSFORMER` for MachineTransformerBlock, `MACHINE_TIMER` for MachineTimerBlock, `MACHINE_ENGINE` for MachineEngineBlock, `MACHINE_SLIME_GRABBER` for MachineGrabberBlock, `MACHINE_HONEY_GRABBER` for MachineGrabberBlock, `MACHINE_TRANSMITTER` for MachineTransmitterBlock. No item for `machine_piston_head` (not player-placeable -- middle-click returns parent piston item).

Special items: `IRON_FRAMED_BLOCK` and `COPPER_FRAMED_BLOCK` are `FramedBlockItem` instances (custom BlockItem with dynamic getName and NBT support). `STRUCTURE_DEBUG_STICK` is a standalone Item (not a BlockItem), `stacksTo(1)`. `REDSTONE_ROD` is a `RedstoneRodItem` (custom Item subclass), `stacksTo(1)`. Uses `onItemUseFirst()` to bypass block GUIs and inject phantom redstone pulses via `RedstoneRodSignalManager`. `HOLLOW_STONE` is a standard BlockItem. `HOLLOW_STONE_SHARD` is a plain Item (crafting ingredient, 9 shards -> 1 hollowstone). `COPPER_NUGGET` is a plain Item (1 copper ingot <-> 9 nuggets; edges of the Copper Frame recipe), tagged `forge:nuggets/copper`.

### ModFeatures (DeferredRegister<Feature<?>> + DeferredRegister<Codec<? extends ChunkGenerator>>)

Two deferred registers in `worldgen/ModFeatures.java`:
- `FEATURES`: Registers `HOLLOWSTONE_LAVA_FORMATION` — custom `Feature<NoneFeatureConfiguration>` for lava shore oddstone/hollowstone placement.
- `CHUNK_GENERATORS`: Registers `ORE_ONLY` — `Codec<OreOnlyChunkGenerator>` for the debug ore-only world type. Registered against `Registries.CHUNK_GENERATOR`.

### ModCreativeTabs

Two tabs:
- `ideomod_tab`: icon = IDEOBLOCK. Lists all standard mod items (Ideoblock, Drill, Oddstone, Machine Block, Odd Slime, Repulser, Attractor, Machine Track, Machine Trolley, Machine Conductor, Machine Pipe, Machine Fluid Pipe, Machine Piston, Machine Sticky Piston, Machine Brace, Machine Repeater, Machine Toggle, Machine Transformer, Machine Timer, Machine Engine, Machine Slime Grabber, Machine Honey Grabber, Machine Transmitter, Iron Frame, Copper Frame, Structure Debug Stick). Machine specializations use pre-loaded stacks.
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

**ODDSTONE grab changes**: `StickyType.ODDSTONE.canGrab()` now returns true ONLY for SLIME, HONEY, ODD_SLIME -- NOT for ODDSTONE targets. All oddstone-to-oddstone connections resolve through sympathetic movement channels, not direct stickiness.

### The Golden Rule

> `canStickTo` = "CAN these types interact?" (permissive, both sides must agree)
> Grab immunity = "WHO is allowed to initiate?" (restrictive, blocks specific grabbers)
> Directional rules = "WHEN is the block sticky?" (based on push direction)

### Stickiness Matrix

Rows = grabber, columns = target. "Machine Fam" = Machine Frame, Machine Track, Machine Trolley. "Frame Fam" = Iron Frame, Copper Frame, Iron/Copper Framed Blocks. Frame Family shown as one row for base frame behavior since both materials share the same grab immunity rules.

*Note: This is the overview matrix with collapsed columns. See CODEBASE_REFERENCE.md and STICKINESS_SYSTEM.md for the detailed matrix with Machine Frame / Same-Type Spec / Diff-Type Spec columns separated.*

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

**OddstoneBlock**: Now uses `StickyType.ODDSTONE.canGrab()` which returns true only for SLIME, HONEY, ODD_SLIME (not ODDSTONE). Also returns true for FramedBlock with INNER_STICKY matching any of SLIME/HONEY/ODD_SLIME/ODDSTONE. Oddstone-to-oddstone connections no longer happen via direct stickiness -- all resolve through sympathetic channels. False for everything else (including MachineBlock, Frame Family without sticky inner, OddstoneBlock targets).

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

After vanilla finishes checking all distance-1 sticky neighbors, extends the search for sympathetic blocks. Blocks declare their channel via the `ISympathetic` interface's `getSympatheticChannel()` method. Delegates to `MovementScanHelper.scanAllChannels()` which groups blocks by channel and scans each independently.

**Channel system**: 12 channels total, one for each full block type + shape variant:
- Oddstone family: `oddstone` (full), `oddstone_wall`, `oddstone_stairs`, `oddstone_slab`
- Cobbled Oddstone family: `cobbled_oddstone` (full), `cobbled_oddstone_wall`, `cobbled_oddstone_stairs`, `cobbled_oddstone_slab`
- Oddstone Bricks family: `oddstone_bricks` (full), `oddstone_brick_wall`, `oddstone_brick_stairs`, `oddstone_brick_slab`

Blocks only link sympathetically with others on the SAME channel. This allows multiple independent sympathetic networks in the same area using different block types.

**Key details**:
- Groups toPush entries by channel (via `ISympathetic.getSympatheticChannel()` or FramedBlock inner block check)
- For each channel group, scans along all 6 axis directions up to `sympatheticRadius` (configurable, default 5) at distance 1, finding adjacent same-channel blocks
- MovementScanHelper also checks FramedBlock inner blocks for ISympathetic interface
- Wall/stair/slab blocks set `isStickyBlock()=true` to trigger vanilla's `addBranchingBlocks` call + `canStickTo()=false` to prevent direct-contact grabbing (only sympathetic search via mixin at TAIL applies)
- Takes a snapshot of `toPush` before iterating (avoids ConcurrentModificationException as new blocks get added)
- If any new blocks were added, sets return value to `true`, enabling chaining: Block A finds Block B, next iteration B finds Block C, etc.
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
| `addBlockLine` | constant 12 | @ModifyConstant | Feature 8 | Dynamic push limit from Machine Braces |

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
4. Frame stickiness connects the neighbor to the group member (same frame material via ideomod$wouldFrameStickinessAllow, or Machine Family adjacency)

Helper: `ideomod$hasImmovableFrameAnchor()` — iterates toPush, checks 6 neighbors per block, returns true if any immovable anchor found.

### Feature 8: Machine Brace Dynamic Push Limit (@ModifyConstant on addBlockLine)

```
@ModifyConstant(method = "addBlockLine", constant = @Constant(intValue = 12))
```

Replaces the hardcoded push limit constant (12) in addBlockLine() with a dynamic value based on Machine Braces in the toPush list. Each MachineBraceBlock adds +6, capped at 60 (8 Braces max).

Helper: `ideomod$calculatePushLimit()` — iterates toPush, counts MachineBraceBlock instances (early-exits at MAX_BRACES=8), returns `12 + (count * 6)` capped at MAX_PUSH_LIMIT=60. Called every time vanilla checks the push limit during block gathering.

The cascading behavior is automatic: as the resolver gathers blocks via addBlockLine's forward+backward walk and addBranchingBlocks' sideways grabs, each Brace discovered increases the limit for subsequent blocks. A Brace only counts if it was reachable within the limit at the time of discovery — a Brace at position 13 in a line won't be found unless an earlier Brace already raised the limit past 12.

Performance: iterates toPush (max 60 entries) per limit check. Lightweight — no world lookups, just instanceof checks on cached blockstates.

Constants defined in MachineBraceBlock:
- PUSH_LIMIT_INCREASE_PER_BRACE = 6
- MAX_PUSH_LIMIT = 60
- MAX_BRACES = 8

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
- `void onMovementRestored(Level, BlockPos)` -- called after loadMovementData. Schedules re-evaluation ticks and restores blockstate properties that vanilla's movedState might not cover.

**Implementing blocks**: MachineFrameBlockEntity (saves/loads inventory, inputFace, repeaterDelay, transformerLevel, inputWasLow, timerDuration, timerTicksRemaining, pulserTicksRemaining, movementPowered via ContainerHelper; protected constructor for subclass BE types), MachinePipeBlockEntity (extends MachineFrameBlockEntity; transit slot + cooldown + round-robin + IItemHandler capability), MachineEngineBlockEntity (extends MachineFrameBlockEntity; fuel slot + litTime + litDuration + pulseInterval + pulseCounter + IItemHandler capability), FramedBlockEntity (saves/loads inner block ResourceLocation).

**POWERED state preservation**: `movementPowered` (transient boolean in MachineFrameBlockEntity) captures whether the block was powered before movement. Saved in `saveMovementData()` via `getRedstoneOutput() > 0`, restored in `loadMovementData()`. In `onMovementRestored()`:

- **All machine blocks** with a "powered" BooleanProperty get their POWERED blockstate restored generically via state definition property lookup. This prevents powered blocks from arriving at their destination with POWERED=false even though vanilla's movedState should preserve it (neighbor updates from piston head removal can cause transient state changes).
- **Toggle special case**: POWERED + INPUT_FACE restored, then the re-evaluation tick is SKIPPED (the Toggle's tick() treats any tick on a POWERED toggle as "toggle OFF", which would immediately undo the latch).
- **Only blocks that need it get a tick** (FIX 2026-10-22, Known Issue #6): `saveMovementData()` also records `MovementPendingTick` (a scheduled tick was waiting when the block was moved). After the move, a tick is scheduled only for POWERED blocks and blocks with a pending tick. Idle blocks used to get one too; an unpowered Pulser's tick() fires whenever its input is lit, so idle Pulsers in a moved chain fired in a same-tick cascade.
- **Staggered tick delays**: POWERED blocks get their re-evaluation tick at delay **2**, UNPOWERED blocks at delay **1**. This preserves pulse cascades through movement — the receiving (UNPOWERED) block gets a 1-tick head start to detect the sender's signal before the sender's countdown expires. Without this stagger, same-delay tick ordering is non-deterministic and the sender can expire before the receiver processes.
- **Post-movement scan recovery**: All edge-triggered machine blocks (Pulser, Timer, Toggle) have a scan guard in their tick() UNPOWERED path. If inputFace is null after movement (pulse expired "clean" before the move), the tick does a full `scanAllFaces()` to recover the input direction. In normal gameplay this path is unreachable (neighborChanged always sets inputFace before scheduling tick). MachineRepeaterBlock already does an unconditional scan so no guard is needed.

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

Feature 5 -- Machine Piston immovability (in same HEAD inject, runs FIRST):
When the block is a `MachinePistonBlock` and `hasExtendedHead()` returns true (any neighbor is a `MachinePistonHeadBlock` pointing back at the piston position), returns false (immovable). Matches vanilla piston behavior where extended pistons cannot be pushed. Runs before all other feature checks (oddstone pull immunity, framed immovable inner, IPistonMovable bypass).

Feature 1 -- isPushable override (HEAD inject, cancellable):
Vanilla ends `isPushable()` with `return !blockstate.hasBlockEntity()`. Our inject checks `getBlockEntity(pos) instanceof IPistonMovable` and returns true, bypassing the vanilla block entity check. This allows Machine Frames to be pushed and pulled by pistons. Uses `MovementScanHelper` for efficient block state lookups. Also handles Oddstone pull immunity: during retraction, ThreadLocal<BlockPos> `ideomod$pullTargetPos` stores the exact pull position (pistonPos + direction * 2). If block at pos is oddstone (real OddstoneBlock or FramedBlock with INNER_STICKY=ODDSTONE) AND pos matches pullTargetPos, returns false. Position-specific — only the direct pull target is immune, not oddstone elsewhere in a sticky group.

Feature 4 -- Framed block immovable inner (in same HEAD inject):
When a FramedBlock wraps an immovable inner block, isPushable returns false. Uses the `#ideomod:immovable_when_framed` tag (data-driven, extensible by datapacks — default: obsidian, crying_obsidian, reinforced_deepslate), plus `getDestroySpeed == -1.0f` for bedrock-type blocks. Runs BEFORE Feature 1's IPistonMovable check so framed obsidian is blocked despite having an IPistonMovable block entity. Includes diagnostic logging for both success and BE-lookup-failure cases. Key discovery: vanilla does NOT use PushReaction.BLOCK for obsidian — the initial implementation using PushReaction silently failed.

Feature 2 -- moveBlocks data save (HEAD inject):
Saves IPistonMovable block entity data before vanilla destroys the block entities during the move. Server-only (critical -- see Bug 2 below).

| Mode | Method | Notes |
|------|--------|-------|
| Extension | PistonStructureResolver | Standard resolver finds toPush list, save each IPistonMovable |
| Retraction | Piston head removal + PistonStructureResolver | Remove piston head before resolver (mirroring vanilla's timing), then standard resolver finds toPush list |

**Retraction timing fix** (2026-03-31c): Vanilla's `moveBlocks()` removes the piston head BEFORE creating its resolver. Our mixin injects at HEAD, which runs BEFORE that removal. Without intervention, our resolver's forward walk from startPos hits the still-present piston head (not pushable), causing `addBlockLine` → `resolve()` to return false, skipping the save entirely. Fix: explicitly remove the piston head before creating our resolver via `level.removeBlock(headPos, false)` (flag 3, matching vanilla's exact call). Safe because vanilla has already set the piston base to EXTENDED=false in `triggerEvent` before calling `moveBlocks`, so `PistonHeadBlock.onRemove`'s EXTENDED check won't destroy the piston base. If our resolver fails, we restore the head so vanilla finds the expected world state.

**Historical note**: Prior to 2026-03-31c, retraction used a fallback path (direct position scan + BFS + sympathetic scan) because the resolver was failing due to this timing bug. The fallback masked the real problem. It was removed in 2026-03-31b (which was then superseded by 2026-03-31c). The resolver is now the sole save path for both extension and retraction.

**PistonMovingBlockEntityMixin** (`com.ideomancer.ideomod.mixin.PistonMovingBlockEntityMixin`):

Target: `net.minecraft.world.level.block.piston.PistonMovingBlockEntity`

TWO restore paths (sole consumer of `MovingBlockEntityData.retrieve()`):

1. `tick()` TAIL — normal 2-tick piston animation. Checks block is no longer MOVING_PISTON before restoring. Server-only guard.
2. `finalTick()` TAIL — 1-tick piston pulses. Vanilla calls `finalTick()` (non-static instance method) instead of `tick()` (static method) for 1-tick pulses. Without this hook, data is lost because `tick()` never sees the "real block placed" condition. Added 2026-02-22.

Note: `finalTick()` inject uses `(PistonMovingBlockEntity)(Object)this` to access the instance since it is non-static, unlike `tick()`.

**BlockMoveHelper integration** (steps 2.5 + 2.6):
BlockMoveHelper has its own save logic in step 2.5 for Repulser/Attractor/Track moves. Wrapped in `if (!level.isClientSide())` guard. Same pattern as PistonBaseBlockMixin but within the BlockMoveHelper flow. Restore uses the same PistonMovingBlockEntityMixin. Step 2.6 handles client-side transit rendering: iterates toPush (which includes sympathetic blocks from the resolver) and populates MovingFrameData for any framed blocks or hollowstone blocks found. FramedBlock entries store innerBlockId + facing + innerSticky. Hollowstone entries store innerBlockId with dummy facing=NORTH and innerSticky=0. This ensures the transit renderer draws inner blocks during BlockMoveHelper-driven moves.

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
| Frame Block (IFrameBlock) | ACTIVE blockstate property | Own frame material | Own material |
| Framed Block | ACTIVE blockstate property | Own frame material | Own material |
| Machine Block (source) | Always active | N/A (source) | All materials |

(Removed 2026-04-19: raw vanilla iron_block / copper_block as conductors -- the feature had no visual indication on the block. Cross-frame mediation via INNER_CONDUCTOR was also removed -- each conductor now passes only its own frame material. Iron chains conduct through iron only, copper through copper.)

### The Algorithm

When `recalculate()` is called:

1. **DISCOVER**: BFS from the change point through all connected IFrameBlock conductors (any material, active or inactive). Finds the entire network.
2. **WIPE**: Set every discovered IFrameBlock to ACTIVE=false (UPDATE_CLIENTS only, no neighbor cascade).
3. **FIND SOURCES**: For every block in the network AND the change position, check 6 neighbors for Machine Blocks.
4. **PROPAGATE ACTIVATION**: BFS from activation sources, signal strength 15. Machine Frames only activate when unpowered; all other Machine Family blocks always activate. Each entry carries available materials + strength. A conductor accepts and outputs only its own frame material -- no cross-material mediation. Signal decays by 1 per hop. Records max strength per position.
5. **PROPAGATE ANTI-SIGNAL**: From powered Machine Frames ONLY (no other Machine Family blocks), BFS with strength = redstone input level. Same material routing and decay. Records max anti-strength per position.
6. **APPLY**: For each network block: IFrameBlocks get ACTIVE=true if activation > 0 AND anti-signal == 0.

Public queries:
- `isActivatedConductor(BlockState, BlockPos)` — checks IFrameBlock ACTIVE property
- `isActive(BlockState)` — checks IFrameBlock ACTIVE property only (public for cross-package access)

### Stickiness Activation Requirements

Frame-family stickiness requires activation on BOTH sides AND exact same frame material:
- **FrameBlock.canStickTo()**: Frame family material checks gated behind `IFrameBlock.isActive(other)` AND same `getFrameMaterial()`. Cross-material or inactive neighbor falls through to permissive checks (slime/honey can still grab).
- **FramedBlock.canStickTo()**: Same treatment for neighbor IFrameBlock checks within the ACTIVE block.
- **IMachineFamily.canMachineFamilyStickTo()**: Frame family returns `FrameActivation.isActive(other)`. Machine blocks only grab active frames (any material).

### Key Types

| Type | Role |
|------|------|
| `IFrameBlock` | Interface. Marker for Frame Family blocks. Provides `getFrameMaterial()`. Has static `isActive(BlockState)` helper. |
| `FrameMaterial` | Data-carrying enum: IRON, COPPER. displayName, registryPrefix, item registry, miningTimeModifier. `fromVanillaBlock()` (used only by FramedBlockRecipe to reject same-material crafting). |
| `FrameBlock` | Base block class. ACTIVE + WATERLOGGED properties. `isStickyBlock()` returns ACTIVE value. All lifecycle hooks call `FrameActivation.markDirty()`. |
| `FrameActivation` | Static utility. `markDirty()` is the primary entry point (batched). `recalculate()` is package-private. End-of-tick processing via `TickHandler`. `flushIfDirty()` for piston resolver. Signal strength propagation, anti-signal suppression. Public queries: `isActivatedConductor()`, `isActive()`, `isFrameFamily()`, `getFrameMaterial()`. |

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
| INNER_LIGHT | IntegerProperty | 0-15 (default 0) | Inner block light emission level. Read by lightLevel predicate in ModBlocks.java so the lighting engine knows emission without block entity access. |

Total blockstate count: 2 x 6 x 5 x 16 = 960 states per framed block type. (INNER_CONDUCTOR property removed 2026-04-19 alongside cross-frame mediation; was 2880 states.)

FACING set from player look direction on placement (piston-style: getNearestLookingDirection().getOpposite()). INNER_STICKY and INNER_LIGHT are pre-populated in getStateForPlacement() from item NBT (critical for lighting engine to detect emission on first setBlock), and kept in sync by FramedBlockEntity.updateBlockstateFromInner() for piston restores and chunk loads. INNER_STICKY delegates to `StickyType.fromBlock()`. INNER_LIGHT uses `inner.defaultBlockState().getLightEmission()`. updateBlockstateFromInner() called on setInnerBlock(), onLoad(), load() (when level available), and loadMovementData().

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

**Frame stickiness** (same as Frame Blocks): Requires ACTIVE=true (driven by FrameActivation system). Sticks to same-material IFrameBlock **only when neighbor is also active and the frame materials match exactly** (no cross-material mediation since 2026-04-19). Sticks to Machine Family (any material, when active). Grab immunity rules prevent grabbing slime/honey/odd slime.

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

### Look and Feel

Own animated texture `textures/block/oddstone_portal.png` + `.mcmeta` (32 frames of 16x16, frametime 2, interpolated), original art from `tools/textures/make_portal_texture.py`: a seamless light-blue swirl (deep blue, sky blue, near-white) with white sparks, alpha 200. Both models (`oddstone_portal_ew/ns.json`) are `render_type: minecraft:translucent`, no tint. Until 2026-10-22 they used vanilla `nether_portal` with a tintindex and no registered tint (so it looked exactly like a nether portal) and no render type (so it drew solid).

`animateTick()` (client only): 4 `ideomod:oddstone_portal` particles per tick (vanilla's portal particle behaviour and sprites, tinted light blue: `client/OddstonePortalParticle`, registered in `ClientSetup`), starting 0.25 out from the pane on either side and drifting away along the axis the pane is thin in (the AXIS value: axis=x is thin in x); 1 in 6 ticks a near-white `DustParticleOptions(0.90, 0.97, 1.00)` spark rising slowly; 1 in 100 ticks `SoundEvents.PORTAL_AMBIENT` at volume 0.5, pitch 1.2-1.6.

### Component Responsibilities

| File | Role |
|------|------|
| `OddstonePortalHandler` | Lifecycle coordinator. Called by OddstoneBlock's neighborChanged/onRemove. Decides activate vs deactivate. Static utility class. |
| `OddstonePortalFrameDetector` | Scans from interior outward to find valid frames. Validates structure, reads corner addresses, checks redstone power. |
| `PortalGeometry` | Data structure: portal dimensions, corners, axis, interior bounds. Used by validateOrDeactivate() for re-validation. |
| `OddstonePortalSavedData` | Per-dimension SavedData (NBT serialized). Stores portal registrations keyed by position. Manages linking/unlinking. |
| `OddstonePortalBlock` | The interior fill block. Handles teleportation (entityInside), cascade breaking (updateShape), unregistration (onRemove), and the client ambience (animateTick: portal particles out of both faces, cyan dust sparks, the portal hum). |
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

### Oddstone -- 6 Features in One Block

| Feature | Implementation |
|---------|---------------|
| **Mob avoidance** | `isPathfindable()` returns false. `getBlockPathType()` returns `DAMAGE_OTHER` for affected mobs (checked against JSON config list). `getAdjacentBlockPathType()` returns `DANGER_OTHER` for the 1-block avoidance radius. |
| **Redstone insulation** | `isRedstoneConductor=false` set in ModBlocks properties lambda. Acts like glass for redstone. |
| **Sympathetic movement** | Handled by PistonStructureResolverMixin Feature 3. Declares channel "oddstone" via `ISympathetic` interface. Block itself has no sympathetic code. Blocks only link with same-channel blocks. |
| **One-way stickiness** | `isStickyBlock()` returns true. `canStickTo()` now returns true only for SlimeBlock, HoneyBlock, OddSlimeBlock (NOT OddstoneBlock). Uses `StickyType.ODDSTONE.canGrab()` which excludes ODDSTONE targets. Oddstone-to-oddstone grabs disabled -- all such connections resolve through sympathetic channels. Reverse grabs blocked by immunity rules 1-3. |
| **Portal frame** | `neighborChanged()` delegates to `OddstonePortalHandler.onNeighborChanged()`. `onRemove()` delegates to `OddstonePortalHandler.onBlockRemoved()`. |

### AbstractMachineBlock -- Shared Base Class

All Machine Family blocks extend `AbstractMachineBlock`, which extends `Block` and implements `EntityBlock` and `IMachineFamily`. Provides default implementations for: `use()` (GUI open), `newBlockEntity()`, `getRenderShape(ENTITYBLOCK_ANIMATED)`, `isStickyBlock(true)`, `canStickTo()` (delegates to `IMachineFamily.canMachineFamilyStickTo()`), `onPlace()`/`onRemove()`/`neighborChanged()` (with `FrameActivation.markDirty()`), `setPlacedBy()` (specialization swap from item NBT), `getDrops()` (conditional BlockEntityTag), `getCloneItemStack()` (registry-based middle-click). Subclasses override only `getMachineType()` for simple specializations, or override lifecycle methods for unique behavior (redstone, push scheduling).

### Machine Frame -- Universal Connector + Machine Family Base

Registry ID: `machine_block` (unchanged for world compatibility). Class: MachineFrameBlock extends AbstractMachineBlock. `MachineType.FRAME`.

| Feature | Implementation |
|---------|---------------|
| **Universal stickiness** | MachineType.FRAME sticks to ALL machine types via `canStickToMachineType()`. Also sticks to Frame Family (active) and is permissive to slime/honey/odd_slime (grab immunity blocks reverse). |
| **Block entity with GUI** | Most machine blocks share MachineFrameBlockEntity. Pipe uses MachinePipeBlockEntity subclass (adds transit slot, own BE type). Fluid Pipe uses MachineFluidPipeBlockEntity subclass (adds fluid type tracking, IFluidHandler, own BE type). Unified MachineFrameMenu with dynamic TransitSlot. PositionBoundContainer keeps GUI open across swaps. GUI renders from ideomod_gui.png spritesheet (151px fixed height). |
| **Specialization swap** | When slot contents change, MachineSwapHelper.checkAndSwap() determines target block from item registry. Block replaced in-world, item transferred to new BE via setItemDirect(). |
| **Anti-signal emission** | Only block in Machine Family that can suppress frame networks. Powered Machine Frame emits anti-signal. All other specializations always activate. |
| **Frame activation** | Emits activation (strength 15) when unpowered. Lifecycle hooks call FrameActivation.markDirty(). |
| **Drop preservation** | getDrops() adds BlockEntityTag only when slot has content. Empty frames drop tagless. getCloneItemStack() uses registry-based createCloneStack() (client-safe). |
| **Piston movement** | PushReaction.NORMAL. IPistonMovable preserves slot contents through all move types. |

**Static helpers** (used by other classes):
- `isMachineFamily(Block)` -- delegates to IMachineFamily.isMachineFamily()
- `isRedstoneConduit(Block)` -- true for MachineTrackBlock only

**IMachineFamily interface**: Provides `getMachineType()` for type-aware stickiness. Static `canMachineFamilyStickTo(MachineType myType, BlockState other)` checks: (1) other Machine Family -- types must match or one must be FRAME, (2) Frame Family -- must be active, (3) slime/honey/odd_slime -- permissive for grab immunity.

**MachineSwapHelper**: Central swap utility. Bidirectional registry (Powered Rail <-> Track, Minecart <-> Trolley, Bucket <-> Fluid Pipe). `checkAndSwap()` with isSwapping ThreadLocal guard. `createPreloadedStack()` for creative tab. `createDrop()` conditional NBT. `createCloneStack()` registry-based middle-click.

**PositionBoundContainer**: Container backed by Level + BlockPos. Every method call does `level.getBlockEntity(pos)` and forwards to the current MachineFrameBlockEntity (or subclass). Reports container size 2: slot 0 works with any MachineFrameBlockEntity, slot 1 delegates to MachinePipeBlockEntity when present (returns empty otherwise). isPipe() method enables live checking. Used by MachineFrameMenu on both server and client sides. Survives block swaps automatically.

**MachineFrameMenu**: Unified menu for all Machine Family blocks. Two custom slot types: SpecSlot (max 1) and TransitSlot. TransitSlot overrides isActive()/mayPlace()/mayPickup() to disable when BE is not a pipe. Fixed CONTENT_HEIGHT=89px for all machines (sized for tallest specialization: Timer). Shorter specializations have extra grey background space. Player inventory Y = 4 + 89 + 12 = 105. Hotbar Y = 4 + 89 + 70 = 163. Total GUI height = 187px always. ContainerData size 6: repeater delay (0), isRepeater (1), transformer level (2), isTransformer (3), timer duration (4), isTimer (5).

**MachineFrameScreen**: Renders GUI dynamically from ideomod_gui.png spritesheet (256x256). Assembly: top border (0,110 176x4) + content area (edges + BG fill + slot tiles) + player inv section (0,20 176x90) + bottom border (0,114 176x4). Slot tile at (0,0 18x18). Transit slot tile only rendered when isPipe(). Dynamic title reads block name each frame.

**ideomod_gui.png spritesheet** (`textures/gui/`): General-purpose GUI spritesheet for all Ideomod GUIs. Clean pixel-perfect rendering from known Minecraft color constants (BG=198, slot=139, dark=55, white=255). Layout: slot tile (0,0) 18x18, left edge (0,18) 4x1, right edge (4,18) 4x1, BG fill (8,18) 1x1, player inv (0,20) 176x90, top border (0,110) 176x4, bottom border (0,114) 176x4. Reusable for future GUIs (enchanting catalyst slot, engine fuel slot, etc).

### Machine Track -- Isolated Redstone + Trolley Push

Registry ID: `machine_track`. Class: MachineTrackBlock extends AbstractMachineBlock. `MachineType.TRACK`.

Non-directional block. Shares MachineFrameBlockEntity (right-click opens GUI showing powered rail in slot). Maintains internal POWER blockstate (0-15, decay by 1) for Track-to-Track signal chaining. POWER is NOT exposed to vanilla's redstone system (isSignalSource=false, getSignal=0, getDirectSignal=0). Accepts input from conduit blocks (other Tracks, Conductors) via direct blockstate reads and from any vanilla redstone source via level.getSignal(). No input whitelist needed -- since Tracks emit no vanilla signal, there is no feedback risk. On rising edge, pushes ALL adjacent Trolleys opposite to signal source direction. Uses BlockMoveHelper for piston-style movement.

Stickiness: MachineType.TRACK -- sticks to other Tracks + Machine Frames + Frame Family (active). Does NOT stick to Trolleys or other specialization types.

**Visual** (2026-10-22): two-layer model (Repeater pattern). Outer `machine_track.png` has a transparent 4x4 window in the middle of each face; inner underlay cuboid (0.05 inset) uses the shared `machine_indicator.png`, tintIndex 1, tinted GREY by POWER (`ModColorHandlers.TRACK_GREYS`, 0x303030 at 0 to 0xE0E0E0 at 15). Grey, not red: Track power is internal and invisible to vanilla redstone. Power drops by 1 per Track, so a pulse shows fading along a line.

Frame activation: Always emits activation (strength 15). Lifecycle hooks call FrameActivation.markDirty(). onPlace and onRemove guarded by `!movedByPiston` to skip neighbor updates and FrameActivation during piston movement.

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

**Visual**: Dual-cuboid model (same pattern as Repeater/Toggle/Transformer). Outer shell `machine_conductor.png` (untinted) has a transparent 4x4 window in the centre of each face; inner underlay cuboid (0.05 inset) uses the shared `machine_indicator.png` tinted via tintIndex 1 by POWER level (ModColorHandlers). Vanilla-style gradient: POWER 0 = dark maroon (~0x4C0000), 1-15 = progressively brighter red.

**Known issues**: Dust sees Conductor strong power through solid blocks (issue #3 in Known Issues -- needs RedStoneWireBlock mixin).

### Machine Repeater -- Omnidirectional Redstone Repeater

Registry ID: `machine_repeater`. Class: MachineRepeaterBlock extends AbstractMachineBlock. `MachineType.REPEATER`.

Omnidirectional redstone repeater. Receives signal on one face (input), outputs full-strength 15 on all other 5 faces with configurable delay. Specialization item: vanilla Repeater.

Shares MachineFrameBlockEntity (right-click opens GUI showing repeater in slot + 4 delay buttons).

| Property | Value |
|----------|-------|
| **Blockstate** | POWERED (BooleanProperty) + DELAY (IntegerProperty 1-4) |
| **Output** | Always strength 15 when POWERED, on all non-input faces |
| **Delay** | 1-4 redstone ticks (2-8 game ticks). Default 1. Set via GUI buttons. |
| **Locking** | None (three-axis makes locking impractical) |

**Input/Output Face Model**: Same concept as Conductor -- receives on one face, outputs on other 5. Input face = strongest incoming signal. Stored as `inputFace` in MachineFrameBlockEntity.

**Anti-feedback Architecture** (5 layers, derived from vanilla DiodeBlock analysis):

1. getSignal/getDirectSignal: return 0 toward input face (`direction == inputFace.getOpposite()`), 15 on all other faces. `shouldSignal` ThreadLocal<BlockPos> (static on block class) stores the position of the instance currently scanning. getSignal/getDirectSignal return 0 ONLY when `pos.equals(shouldSignal.get())` — this prevents self-reads through solid blocks while allowing other instances of the same class to output normally. `isSignalSource` always returns true (position-based suppression handled in getSignal/getDirectSignal). FIX 2026-03-29: changed from ThreadLocal<Boolean> to ThreadLocal<BlockPos> to enable same-type power through solid blocks.

2. neighborChanged output-face early-return: when POWERED and update comes from a non-input face (determined via `getFaceFromSourcePos(pos, sourcePos)`), return immediately. Only input-face updates proceed to logic.

3. Protected inputFace: neighborChanged NEVER modifies inputFace when powered. When powered and input-face update arrives, only checks that single face's signal. If signal gone, schedules tick. Does NOT full-scan (output-face dust would give false positives).

4. Input-face-first tick: when powered, tick() checks only current inputFace. If still has signal, stays on. If signal gone, turns off (inputFace set to null). Full-scan only happens when unpowered (safe because no output-face dust has power from us).

5. updatingPos ThreadLocal: during tick()'s setBlock(flag 3), stores current BlockPos. neighborChanged at THIS position returns immediately (prevents stale dust reads during synchronous cascade). Adjacent repeaters at different positions proceed normally.

**Signal Detection**: Delegated to `MachineRedstoneHelper.getIncomingSignalFromFace()` -- shared single source of truth for all Machine Family redstone specializations. Handles: MachineConductorBlock (getSignal with -1 decay), any AbstractMachineBlock with getRedstoneOutput() > 0 (device neighbor, input-face check), RedStoneWireBlock (POWER with -1 decay), everything else (vanilla level.getSignal with shouldSignal set to scanning position for self-read prevention).

**Tick Scheduling**: neighborChanged schedules tick via `level.scheduleTick()` at configured delay (DELAY * 2 game ticks). Uses `willTickThisTick()` guard. tick() uses `setBlock(pos, state, 3)` (UPDATE_NEIGHBORS | UPDATE_CLIENTS). On POWERED state change, also calls `updateNeighborsAt(pos.relative(dir))` for all 6 directions to propagate power through solid blocks (matching vanilla repeater behavior). onRemove guarded by `!movedByPiston` to skip neighbor updates during piston movement.

**GUI Buttons**: 4 numbered buttons from `ideomod_numbered_buttons.png` rendered in transit slot zone (Y=37, same space as pipe transit slot). `MachineFrameScreen.mouseClicked()` sends `RepeaterDelayPacket`. `MachineFrameMenu` uses split constructor pattern with `ContainerData` for cross-side delay sync.

**Networking**: `RepeaterDelayPacket` via Forge SimpleChannel (`ModNetworking.java`). Client-to-server. Validates delay range (1-4), player distance (64 blocks), block type.

**Visual**: Dual-cuboid model (Fluid Pipe pattern). Inner underlay cuboid (0.05 inset) uses `machine_indicator.png` (solid white) tinted via tintIndex 1 -- dark maroon when unpowered, bright red (POWER_COLORS[15]) when powered. Outer shell uses 4 delay-variant textures with transparent windows. 4 block models (tint handles on/off). Blockstate maps both powered states to same model per delay.

Stickiness: MachineType.REPEATER -- sticks to other Repeaters + Machine Frames + Frame Family (active). Does NOT stick to Conductors, Tracks, Pipes, or other specialization types.

Frame activation: Always emits activation (strength 15). Never suppresses.

### Machine Toggle -- Omnidirectional Flip-Flop

Registry ID: `machine_toggle`. Class: MachineToggleBlock extends AbstractMachineBlock. `MachineType.TOGGLE`.

Omnidirectional edge-triggered flip-flop / T-latch. Receives signal on one face (input), toggles between ON and OFF on each rising edge. Specialization item: vanilla Lever.

Shares MachineFrameBlockEntity (right-click opens GUI showing lever in slot). Uses `inputFace` field for redstone direction and `inputWasLow` field for edge detection.

| Property | Value |
|----------|-------|
| **Blockstate** | POWERED (BooleanProperty) + INPUT_FACE (EnumProperty: none/north/south/east/west/up/down) |
| **Output** | Always strength 15 when POWERED, on all non-input faces |
| **Trigger** | Rising edge only (signal must drop to 0 then rise again) |
| **No GUI controls** | Just the specialization slot |

**Input/Output Face Model**: Same concept as Conductor/Repeater -- receives on one face, outputs on other 5. Input face stored as `inputFace` in MachineFrameBlockEntity AND as `INPUT_FACE` blockstate property (drives model rotation). INPUT_FACE is an EnumProperty with 7 values (NONE + 6 directions) defined as an inner enum `MachineToggleBlock.InputFace` implementing `StringRepresentable`.

**Edge Detection**: `inputWasLow` boolean in MachineFrameBlockEntity tracks whether the input signal has been seen at 0 since the last state change. Toggle cycle: (1) signal arrives → ON, inputWasLow=false; (2) signal drops → inputWasLow=true, stays ON; (3) signal rises → OFF. After toggle-off, if signal still present, absorbed (inputWasLow=false, stays OFF until drop-and-rise).

**Placement Signal Absorption**: When placed next to an already-active signal, onPlace() snapshots the signal landscape and absorbs it: sets inputFace to strongest face, inputWasLow=false, stays OFF. `absorbing` ThreadLocal prevents neighborChanged from scheduling a turn-on tick during placement. Signal must drop and rise before Toggle responds. This prevents unwanted activation when building binary counters or placing near active redstone. Note: during piston movement, onPlace fires before loadMovementData restores the entity, so absorption writes into a fresh entity -- but loadMovementData overwrites those values shortly after, so no harm done.

**Piston Movement**: Latch state is preserved through piston moves. Vanilla preserves the original blockstate (POWERED + INPUT_FACE) via MovingPistonBlockEntity's movedState. Entity data (inputFace, inputWasLow) is preserved via IPistonMovable save/loadMovementData. Critically, onMovementRestored does NOT schedule a re-evaluation tick for powered Toggles -- the tick() method's POWERED branch unconditionally sets POWERED=false (it's the "toggle OFF" handler), which would immediately undo the latch. The movementPowered field in MachineFrameBlockEntity also restores POWERED + INPUT_FACE blockstate as a safety net.

**Piston Settlement Window**: Same 1-tick pistonSettling mechanism as Grabber. When moved by piston, sets `pistonSettling=true` and schedules a 1-tick settle tick. During settlement, neighborChanged is suppressed (returns immediately if `be.isPistonSettling()`). The settle tick clears the flag and re-snapshots signal state: if powered, scans input face only; if unpowered, scans all faces. This prevents spurious toggles from sequential block placement in push groups.

**Anti-feedback Architecture** (4 layers, simplified from Repeater):

1. getSignal/getDirectSignal: return 0 toward input face (`direction == inputFace.getOpposite()`), 15 on all other faces. INPUT_FACE read from blockstate (not BE) for BlockGetter compatibility. `shouldSignal` ThreadLocal<BlockPos> stores scanning position — suppresses only at that position (same-type power through solid blocks enabled).

2. neighborChanged output-face early-return: when POWERED and update comes from a non-input face, return immediately.

3. updatingPos ThreadLocal: during tick()'s setBlock(flag 3), stores current BlockPos. neighborChanged at THIS position returns immediately.

4. absorbing ThreadLocal: during onPlace signal snapshot, prevents neighborChanged from scheduling ticks.

**Signal Detection**: Delegated to `MachineRedstoneHelper.getIncomingSignalFromFace()` -- shared single source of truth for all Machine Family redstone specializations. Same handler set as Repeater/Transformer. `getRedstoneOutput()` returns 15 when POWERED.

**Tick Scheduling**: neighborChanged schedules tick via `level.scheduleTick()` at 2 game ticks (1 redstone tick). tick() is the ONLY place where POWERED state changes. Uses `setBlock(pos, state, 3)` (UPDATE_NEIGHBORS | UPDATE_CLIENTS). On POWERED state change (both ON and OFF transitions), also calls `updateNeighborsAt(pos.relative(dir))` for all 6 directions to propagate power through solid blocks. onRemove guarded by `!movedByPiston`.

**Visual**: Dual-cuboid model (Repeater pattern). Inner underlay cuboid (0.05 inset) uses `machine_indicator.png` (shared white texture) tinted via tintIndex 1 -- dark maroon when unpowered, bright red when powered. Outer shell uses 2 model files: `machine_toggle_off` (all faces use machine_toggle texture) and `machine_toggle_on` (north face = machine_toggle, other 5 = machine_toggle_on). Blockstate rotates the on model via x/y to point input face at correct direction. 14 blockstate variants (7 input_face values x 2 powered states), 2 model files.

When powered, input face shows the "off" texture (machine_toggle) while other 5 faces show the "on" texture (machine_toggle_on), visually indicating which face is the input.

**Known limitation**: Vanilla block color handlers don't receive face direction, so all 6 faces of the inner indicator get the same tint color. The input face is distinguished by the outer texture only (off vs on). Future improvement: investigate per-face indicator suppression via BESR or split-element model approach (see Known Issue #5).

Stickiness: MachineType.TOGGLE -- sticks to other Toggles + Machine Frames + Frame Family (active). Does NOT stick to Conductors, Repeaters, Tracks, Pipes, or other specialization types.

Frame activation: Always emits activation (strength 15). Never suppresses.

### Machine Transformer -- Configurable Signal Level Setter

Registry ID: `machine_transformer`. Class: MachineTransformerBlock extends AbstractMachineBlock. `MachineType.TRANSFORMER`.

Omnidirectional redstone signal level setter. Receives signal on one face (input), outputs a user-configured signal strength (1-15) on all other 5 faces. Input signal acts purely as an on/off gate -- the output level is always whatever the player sets in the GUI, regardless of input strength. Specialization item: vanilla Comparator.

Shares MachineFrameBlockEntity (right-click opens GUI showing comparator in slot + 15 level buttons in two rows).

| Property | Value |
|----------|-------|
| **Blockstate** | POWERED (BooleanProperty) + OUTPUT_LEVEL (IntegerProperty 1-15) |
| **Output** | OUTPUT_LEVEL on all non-input faces when POWERED, 0 when not |
| **Delay** | None -- responds on 1-tick schedule (same as Toggle) |
| **Default level** | 1 (minimum) |

**Input/Output Face Model**: Same concept as Conductor/Repeater/Toggle -- receives on one face, outputs on other 5. Input face = strongest incoming signal. Stored as `inputFace` in MachineFrameBlockEntity.

**Anti-feedback Architecture**: Same 5 layers as Repeater. Output value is OUTPUT_LEVEL (read from blockstate) instead of fixed 15.

**Signal Detection**: Delegated to `MachineRedstoneHelper.getIncomingSignalFromFace()` -- shared single source of truth for all Machine Family redstone specializations. `getRedstoneOutput()` returns OUTPUT_LEVEL when POWERED.

**Tick Scheduling**: neighborChanged schedules tick via `level.scheduleTick()` at 2 game ticks (1 redstone tick). tick() is the ONLY place where POWERED state changes. Uses `setBlock(pos, state, 3)` (UPDATE_NEIGHBORS | UPDATE_CLIENTS). On POWERED or OUTPUT_LEVEL state change, also calls `updateNeighborsAt(pos.relative(dir))` for all 6 directions to propagate power through solid blocks. onRemove guarded by `!movedByPiston`.

**GUI Buttons**: 15 numbered buttons from `ideomod_numbered_buttons.png` in two rows: row 1 = buttons 1-8 (centered at X=17), row 2 = buttons 9-15 (centered at X=26). Row 1 at Y=40, row 2 at Y=58. `MachineFrameScreen.mouseClicked()` sends `TransformerLevelPacket`. Fixed content height (89px, shared by all machines).

**Networking**: `TransformerLevelPacket` via Forge SimpleChannel (`ModNetworking.java`). Client-to-server. Validates level range (1-15), player distance (64 blocks), block type. Packet ID 1.

**MachineFrameBlockEntity fields**: `transformerLevel` (int, 1-15, default 1). Saved in saveAdditional, load, getUpdateTag, saveMovementData, loadMovementData. Reset to 1 in triggerSwapCheck(). `setTransformerLevel()` syncs OUTPUT_LEVEL blockstate with setBlock flag 3.

**ContainerData**: MachineFrameMenu ContainerData size 6: DATA_REPEATER_DELAY=0, DATA_IS_REPEATER=1, DATA_TRANSFORMER_LEVEL=2, DATA_IS_TRANSFORMER=3, DATA_TIMER_DURATION=4, DATA_IS_TIMER=5.

**Visual**: Dual-cuboid model (same pattern as Repeater/Toggle). Inner underlay cuboid (0.05 inset) uses shared `machine_indicator.png` (solid white) tinted via tintIndex 1. Level-aware tint: unpowered = POWER_COLORS[0] (dark maroon), powered = POWER_COLORS[outputLevel] (dimmer red at low levels, brighter at high). Outer shell uses `machine_transformer.png` texture with transparent indicator windows. Single block model for all 30 blockstate variants (15 levels x 2 powered states) -- tint handles visual differentiation.

Stickiness: MachineType.TRANSFORMER -- sticks to other Transformers + Machine Frames + Frame Family (active). Does NOT stick to Conductors, Repeaters, Toggles, Tracks, Pipes, or other specialization types.

Frame activation: Always emits activation (strength 15). Never suppresses.

### Machine Timer -- Edge-Triggered Timed Output

Registry ID: `machine_timer`. Class: MachineTimerBlock extends AbstractMachineBlock. `MachineType.TIMER`.

Edge-triggered timed output. Receives signal on one face (input), emits full-strength 15 on all other 5 faces for a player-configured duration in ticks, then turns off. Requires a fresh signal (drop to 0, then rise) after the timer expires to re-trigger. Specialization item: vanilla Clock.

Shares MachineFrameBlockEntity (right-click opens GUI showing clock in slot + "Pulse Tick Duration" label + 16 duration buttons in 2 rows).

| Property | Value |
|----------|-------|
| **Blockstate** | POWERED (BooleanProperty) |
| **Output** | Always strength 15 when POWERED, on all non-input faces |
| **Trigger** | Rising edge only (same as Toggle). Signal must drop to 0 and timer must have expired before re-triggering. |
| **Duration** | Player-configurable via GUI buttons. 16 presets from config (default 5-80 ticks in steps of 5). |
| **Default duration** | 20 ticks (config timer.default_index = 3) |

**Input/Output Face Model**: Same concept as Conductor/Repeater/Toggle/Transformer -- receives on one face, outputs on other 5. Input face stored as `inputFace` in MachineFrameBlockEntity (NOT in blockstate -- Timer has no directional model variants, unlike Toggle's INPUT_FACE property).

**Edge Detection**: Same pattern as MachineToggleBlock. `inputWasLow` boolean in MachineFrameBlockEntity tracks whether the input signal has dropped to 0 since the last activation. Timer cycle: (1) signal arrives → start timer, POWERED=true, inputWasLow=false; (2) timer counts down each tick; (3) timer expires → POWERED=false; (4) if signal still present, absorbed (inputWasLow=false); (5) signal drops → inputWasLow=true (armed); (6) signal rises → new timer starts.

While the timer is running, input signal changes are tracked for arming but don't affect the countdown -- the timer always runs to completion.

**Placement Signal Absorption**: Same pattern as Toggle. When placed next to an already-active signal, onPlace() snapshots the signal landscape and absorbs it via `absorbing` ThreadLocal. Signal must drop and rise before Timer responds.

**Anti-feedback Architecture** (5 layers, same as Repeater/Transformer):

1. getSignal/getDirectSignal: return 0 toward input face, 15 on others. Input face read from BE via Level cast (not blockstate). `shouldSignal` ThreadLocal<BlockPos> stores scanning position — suppresses only at that position (same-type power through solid blocks enabled).

2. neighborChanged output-face early-return: when POWERED and update comes from a non-input face, return immediately.

3. updatingPos ThreadLocal: during tick()'s setBlock(flag 3), stores current BlockPos. neighborChanged at THIS position returns immediately.

4. absorbing ThreadLocal: during onPlace signal snapshot, prevents neighborChanged from scheduling ticks.

5. Self-rescheduling countdown: when POWERED, tick() decrements timerTicksRemaining and schedules next tick at 1 game tick. When remaining reaches 0, sets POWERED=false.

**Signal Detection**: Delegated to `MachineRedstoneHelper.getIncomingSignalFromFace()` -- shared single source of truth. `getRedstoneOutput()` returns 15 when POWERED.

**Tick Scheduling**: neighborChanged schedules initial tick via `level.scheduleTick()` at 2 game ticks. Once POWERED, tick() self-reschedules every 1 game tick for countdown. tick() is the ONLY place where POWERED state changes. On POWERED state change (both ON and timer-expired OFF), also calls `updateNeighborsAt(pos.relative(dir))` for all 6 directions to propagate power through solid blocks. onRemove guarded by `!movedByPiston`.

**GUI**: "Pulse Tick Duration" label centered above 16 buttons in 2 rows (8+8). Each button shows the tick value from IdeomodConfig.getTimerDurations(). Selected duration shows pressed state. Click sends `TimerDurationPacket`. Fixed content height (89px, shared by all machines).

**Networking**: `TimerDurationPacket` via Forge SimpleChannel (`ModNetworking.java`). Client-to-server. Validates duration index against config array, player distance (64 blocks), block type. Packet ID 2.

**MachineFrameBlockEntity fields**: `timerDuration` (int, default from config, persisted in all 4 NBT paths), `timerTicksRemaining` (int, countdown, persisted). Both reset in triggerSwapCheck().

**ContainerData**: MachineFrameMenu ContainerData size 6: DATA_TIMER_DURATION=4, DATA_IS_TIMER=5 (added to existing 0-3).

**Visual**: Dual-cuboid model (same pattern as Repeater/Toggle/Transformer). Inner underlay cuboid (0.05 inset) uses shared `machine_indicator.png` tinted via tintIndex 1. Binary tint: dark maroon when unpowered, bright red (POWER_COLORS[15]) when powered. Outer shell uses `machine_timer.png` texture (placeholder -- needs user art). Single block model for both powered variants.

Stickiness: MachineType.TIMER -- sticks to other Timers + Machine Frames + Frame Family (active). Does NOT stick to Conductors, Repeaters, Toggles, Transformers, Tracks, Pipes, or other specialization types.

Frame activation: Always emits activation (strength 15). Never suppresses.

### Machine Engine -- Fuel-Burning Pulse Clock

Registry ID: `machine_engine`. Class: MachineEngineBlock extends AbstractMachineBlock. `MachineType.ENGINE`.

Fuel-burning pulse clock. Burns fuel items (using Forge burn times) and emits periodic redstone pulses at a player-configurable interval. Specialization item: vanilla Furnace (Items.FURNACE). Own BE subclass: MachineEngineBlockEntity (BlockEntityType: MACHINE_ENGINE). Extends MachineFrameBlockEntity with fuel slot (slot 1), burn state (litTime/litDuration), pulse interval, and pulse counter.

| Property | Value |
|----------|-------|
| **Block entity** | MachineEngineBlockEntity extends MachineFrameBlockEntity |
| **BE type** | ModBlockEntities.MACHINE_ENGINE (separate from MACHINE_FRAME) |
| **Specialization item** | Furnace |
| **Blockstate properties** | LIT (BooleanProperty), POWERED (BooleanProperty) |
| **Container size** | 2 (slot 0 = specialization, slot 1 = fuel) |

**Blockstate semantics**: LIT = sustained (burning fuel AND receiving redstone signal, drives outer texture swap and particle visibility). POWERED = transient (1-redstone-tick pulse output state, drives indicator tint and redstone signal). These are independent states: LIT can be true with POWERED false (between pulses), POWERED is briefly true during each pulse.

**Three prerequisites to pulse**: (1) Redstone signal on at least one face (input face), (2) Fuel burning (litTime > 0), (3) Pulse interval elapsed (pulseCounter >= pulseInterval).

**Server tick logic** (MachineEngineBlockEntity.serverTick, runs every game tick):
1. Fuel burn: litTime decrements ONLY when powered (isPowered = inputFace != null). Fuel timer freezes when the engine loses signal and resumes when re-powered. This means toggling the signal on/off preserves remaining fuel.
2. Auto-feed: when litTime reaches 0 and fuel slot has a valid fuel item and engine is powered, consume one fuel item (using ForgeHooks.getBurnTime), set litTime = litDuration = burn time. Lava bucket leaves empty bucket.
3. Pulse: when isLit (litTime > 0) AND isPowered, increment pulseCounter. When pulseCounter >= pulseInterval, reset counter and call MachineEngineBlock.emitPulse().
4. Safety clear: when NOT powered, reset pulseCounter to 0 and force-clear both POWERED and LIT blockstates to false. This belt-and-suspenders approach prevents stale visual state at high pulse rates where rapid POWERED toggling could race with the signal drop.
5. LIT update (powered path only): when powered, set LIT = (litTime > 0). When unpowered, handled by step 4.

**Pulse emission** (MachineEngineBlock.emitPulse): if POWERED is currently false, sets POWERED=true via level.setBlock(flag 3), then calls `updateNeighborsAt(pos.relative(dir))` for all 6 directions to propagate power through solid blocks, then schedules a tick at +2 game ticks (1 redstone tick) with TickPriority.HIGH. The scheduled tick (MachineEngineBlock.tick) sets POWERED=false and also calls `updateNeighborsAt` for all 6 directions. Pulse duration of 2 game ticks is the minimum for Minecraft's client-side rendering to visually register state changes on dust and color-tinted blocks.

**Anti-feedback Architecture** (5 layers, same as Repeater/Transformer/Timer):

1. getSignal/getDirectSignal: return 0 toward input face, 15 on others. Input face read from BE via Level cast (not blockstate). `shouldSignal` ThreadLocal<BlockPos> stores scanning position — suppresses only at that position (same-type power through solid blocks enabled).

2. neighborChanged output-face early-return: when POWERED and update comes from a non-input face, return immediately.

3. updatingPos ThreadLocal: during emitPulse/tick setBlock(flag 3), stores current BlockPos. neighborChanged at THIS position returns immediately.

4. absorbing ThreadLocal: during onPlace signal snapshot, prevents neighborChanged from processing.

5. Signal detection delegated to MachineRedstoneHelper.scanAllFaces().

**Fuel slot automation**: WorldlyContainer exposes only fuel slot (slot 1) to external faces via getSlotsForFace. canPlaceItemThroughFace accepts items with burn time > 0. canTakeItemThroughFace allows extraction only for non-fuel items (empty buckets after lava bucket consumption). IItemHandler capability via SidedInvWrapper for Forge-compatible automation (pipes, hoppers).

**Item preservation on specialization removal**: When the furnace is removed from slot 0 (triggering a swap back to Machine Frame), MachineSwapHelper.ejectSecondaryItems() drops the fuel slot contents as item entities at the block position before the swap replaces the BE. MachineEngineBlock.onRemove() has `!MachineSwapHelper.isSwapping()` guard to avoid double-drop.

**Piston safety**: onRemove checks `!movedByPiston` -- skips fuel item drop when moved by piston (items preserved via IPistonMovable save/loadMovementData). MachineEngineBlockEntity overrides saveMovementData/loadMovementData to persist fuel item, litTime, litDuration, pulseInterval, pulseCounter through piston moves.

**Comparator output**: Scales fuel slot stack fullness to 0-15. Empty = 0, any items = 1 + (count * 14 / maxStackSize).

**Particles**: Flame particles on all 6 faces + campfire cosy smoke (ParticleTypes.CAMPFIRE_COSY_SMOKE) rising from top face. Only visible when LIT blockstate is true (powered AND burning). animateTick checks state.getValue(LIT), not the BE's litTime directly, so particles correctly disappear when signal is cut (even though litTime is frozen, not zeroed).

**GUI**: Uses unified MachineFrameMenu with dynamic fuel slot. Fuel slot rendered at position matching vanilla furnace fuel slot. Flame indicator uses vanilla furnace GUI texture: empty flame outline at UV(56,36) always visible, lit flame at UV(176,0) overlaid proportionally from bottom up based on litTime/litDuration. "Pulse Interval" label centered above 8 buttons in 1 row. Each button shows the tick value from IdeomodConfig.getEngineDurations(). Selected interval shows pressed state. Click sends `EnginePulseIntervalPacket`. Fixed content height (89px, shared by all machines).

**Networking**: `EnginePulseIntervalPacket` via Forge SimpleChannel (`ModNetworking.java`). Client-to-server. Validates interval index against config array, player distance (64 blocks), block type.

**ContainerData**: MachineFrameMenu ContainerData size 10: DATA_ENGINE_LIT_TIME=6, DATA_ENGINE_LIT_DURATION=7, DATA_ENGINE_INTERVAL=8, DATA_IS_ENGINE=9 (added to existing 0-5).

**Visual**: Dual-cuboid model (same pattern as Repeater/Toggle/Transformer/Timer). Inner underlay cuboid (0.05 inset) uses shared `machine_indicator.png` tinted via tintIndex 1. Binary tint: dark maroon when POWERED=false, bright red (POWER_COLORS[15]) when POWERED=true. Outer shell uses `machine_engine.png` / `machine_engine_lit.png` textures (placeholder -- needs user art). LIT blockstate drives texture variant swap.

Stickiness: MachineType.ENGINE -- sticks to other Engines + Machine Frames + Frame Family (active). Does NOT stick to Conductors, Repeaters, Toggles, Transformers, Timers, Pipes, or other specialization types.

Frame activation: Always emits activation (strength 15). Never suppresses.

### Machine Grabber -- Toggleable Sticky Block

Registry IDs: `machine_slime_grabber`, `machine_honey_grabber`. Class: MachineGrabberBlock extends AbstractMachineBlock. `MachineType.SLIME_GRABBER` / `MachineType.HONEY_GRABBER`.

Two variants of a toggleable sticky block. When unpowered, standard Machine Family stickiness only. When powered, additionally behaves like the corresponding vanilla sticky block (Slime or Honey). Specialization items: Slime Block or Honey Block respectively.

Shares MachineFrameBlockEntity (right-click opens GUI showing slime/honey block in slot). Uses `inputWasLow` field for edge detection (same pattern as Toggle).

| Property | Value |
|----------|-------|
| **Blockstate** | POWERED (BooleanProperty) |
| **Output** | None -- no redstone output |
| **Trigger** | Rising edge only (signal must drop to 0 then rise to toggle) |
| **Sticky behavior** | When POWERED: slime variant grabs like SlimeBlock (excludes honey, odd slime). Honey variant grabs like HoneyBlock (excludes slime, odd slime). |

**Edge Detection**: Same pattern as MachineToggleBlock. `inputWasLow` boolean in MachineFrameBlockEntity. Rising edge flips POWERED. Signal must drop and rise between toggles. Placement signal absorption: onPlace() snapshots signal landscape (sets inputWasLow based on existing signal) so pre-existing signals don't cause immediate toggle.

**No Anti-Feedback**: Grabber has no redstone output, so no feedback loops are possible. No shouldSignal, no updatingPos, no output-face filtering needed.

**Piston Settlement Window**: When moved by piston (`movedByPiston=true` in onPlace), sets `pistonSettling=true` on the block entity and schedules a 1-tick settle tick. During the settlement window, neighborChanged is completely suppressed (returns immediately if `be.isPistonSettling()`). This prevents spurious toggles caused by sequential block placement in push groups -- blocks land one at a time, so a grabber may see a temporary signal drop (adjacent transmitter not placed yet) followed by a rise (transmitter lands), which would falsely trigger a toggle. The settle tick clears the flag and re-snapshots the signal landscape (`be.setInputWasLow(!hasSignal)`).

**Stickiness**: MachineType.SLIME_GRABBER / HONEY_GRABBER. When POWERED=false: sticks to same-type + Machine Frames + Frame Family (active) only. When POWERED=true: additionally sticks like the corresponding vanilla sticky block. canStickTo is permissive (Layer 1); grab immunity carve-out in StickinessRules Rule 2 exempts powered grabbers matching the target's sticky type.

**Visual**: Dual-cuboid model. Inner cuboid renders the slime/honey block via BESR (same approach as FramedBlockRenderer). Outer shell uses `machine_grabber` (off) / `machine_grabber_on` (on) textures with transparent centers revealing the inner block. No indicator tint -- on/off distinguished by outer texture only.

Frame activation: Always emits activation (strength 15). Never suppresses.

### Machine Transmitter -- Wireless Redstone via Oddstone Channels

Registry ID: `machine_transmitter`. Class: MachineTransmitterBlock extends AbstractMachineBlock. `MachineType.TRANSMITTER`.

Wireless redstone link. Transmitters operate in one of two mutually exclusive modes: SOURCE (receiving external redstone, broadcasting wirelessly) or RECEIVER (receiving wireless signal, outputting redstone). The wireless channel is determined by the Oddstone variant in the spec slot -- two transmitters match if and only if they have the exact same Oddstone type. Specialization item: any Oddstone block or variant (oddstone, cobbled_oddstone, oddstone_bricks, etc.).

Shares MachineFrameBlockEntity (right-click opens GUI showing oddstone item in slot). Uses `transmitterSignalStrength` field in MachineFrameBlockEntity for both source strength storage and receiver output level.

| Property | Value |
|----------|-------|
| **Blockstate** | POWERED (BooleanProperty) + SOURCE (BooleanProperty) |
| **Output** | Receivers: stored signal strength on all faces. Sources: 0 (no output). |
| **Attenuation** | Receiver outputs `sourceStrength - 1`, simulating one block of distance. |
| **Range** | Cubic radius from config (`oddstone.sympathetic_radius`, default 5). |
| **Activation** | SOURCE mode: immediate in neighborChanged (no tick delay). |
| **Re-evaluation** | Via scheduled tick (2 game ticks). |

**Signal Flow (SOURCE mode)**: External redstone signal detected → immediately set POWERED=true, SOURCE=true in neighborChanged (no tick delay, so even 1-tick pulses transmit). Store received signal strength in BE (`transmitterSignalStrength`). Notify all matching-channel transmitters in range via scheduled tick so they can become receivers. When external signal changes strength, update BE and re-notify receivers. When external signal drops to 0, schedule tick to re-evaluate.

**Signal Flow (RECEIVER mode)**: Tick fires, no external signal → scan for matching SOURCE in range → read source's stored `transmitterSignalStrength` from its BE → set own BE's `transmitterSignalStrength` to `sourceStrength - 1` → set POWERED=true, SOURCE=false → output signal on all faces. When source turns off, receivers are turned off FIRST (before source notifies its own neighbors) to prevent stale receiver output feeding back through dust.

**Signal Strength Changes**: When a source's external signal changes strength while already powered, the source updates its stored strength and notifies receivers. Receivers re-read the source's strength and update their output. If the blockstate doesn't change (still POWERED/!SOURCE), the receiver calls `level.updateNeighborsAt(pos, this)` followed by `updateNeighborsAt(pos.relative(dir))` for all 6 directions to ensure direct neighbors (dust) and indirect neighbors (through solid blocks) all update.

**Anti-Feedback Architecture** (3 layers):

1. SOURCE blockstate: getSignal/getDirectSignal return 0 when SOURCE=true (sources never output redstone). Receivers ignore neighborChanged entirely (early return when POWERED && !SOURCE). This prevents circular evaluation between source output and receiver input.

2. shouldSignal ThreadLocal<BlockPos>: Position-based self-read suppression. During getExternalSignalStrength(), stores own position; getSignal/getDirectSignal return 0 at the scanning position. Prevents feedback through solid block conductors.

3. updatingPos ThreadLocal<BlockPos>: During setBlock calls in tick() and neighborChanged, stores own position. neighborChanged at THIS position returns immediately, preventing stale reads during state transitions.

4. Receiver self-read guard in tick(): Receivers skip external signal check entirely (`externalStrength = 0` when `wasPowered && !wasSource`). Without this guard, a receiver sitting next to dust it is powering would read its own output back through getBestNeighborSignal(), flip to SOURCE (output=0), and collapse the circuit.

5. Source power-off ordering: When a source turns off, `turnOffMatchingReceivers()` is called BEFORE the source updates its own blockstate and notifies neighbors. This ensures stale receiver output can't feed back through dust during the transition.

**Channel System**: The channel is the registry name of the item in slot 0 (e.g., "minecraft:oddstone", "ideomod:cobbled_oddstone"). `getChannel(level, pos)` reads from BE. `getChannelFromItem(ItemStack)` derives from an item directly (used during spec item changes where the old item is no longer in the slot). All scanning methods (getWirelessSignalStrength, notifyMatchingTransmitters, turnOffMatchingReceivers) iterate a cubic volume (radius from config) and match channels.

**Spec Item Change Lifecycle**: Two-phase callback system via AbstractMachineBlock virtual methods:

1. `onSpecItemChanging(Level, BlockPos, ItemStack oldItem)` — called BEFORE triggerSwapCheck. For transmitters: turns off receivers on OLD channel, notifies OLD channel peers, resets blockstate to POWERED=false/SOURCE=false. Uses `getChannelFromItem(oldItem)` since the old item is still available as a parameter.

2. `onSpecItemChanged(Level, BlockPos, ItemStack newItem)` — called AFTER triggerSwapCheck when the block type stayed the same (oddstone variant swaps). Calls `reevaluateAfterChange()` which checks external signal first (become SOURCE), then wireless signal (become RECEIVER).

For cross-block swaps (e.g., conductor→transmitter), the old block's `onSpecItemChanging` fires, then `checkAndSwap` replaces the block. The new block's `onSpecItemChanged` fires on whatever block is at the position after the swap (covers both same-block and cross-block cases).

**Placement**: Normal placement calls `reevaluateAfterChange()` to check for external signal (become SOURCE) or wireless signal from active sources (become RECEIVER). During `MachineSwapHelper.isSwapping()`, re-evaluation is SKIPPED in onPlace because the spec item hasn't been written to the new BE yet — `onSpecItemChanged` handles evaluation after `setItemDirect` puts the item in.

**Removal**: onRemove turns off matching receivers (if source), notifies own neighbors, and notifies matching transmitters. Guarded by `!movedByPiston` and `!level.isClientSide()`.

**Visual**: Dual-cuboid model. Inner underlay cuboid (0.05 inset) uses shared `machine_indicator.png` tinted via tintIndex 1. Level-aware tint: reads `transmitterSignalStrength` from MachineFrameBlockEntity (not from blockstate), returns `POWER_COLORS[strength]` where 0 = dark maroon and 15 = bright red. This provides a continuous visual gradient reflecting the actual signal level being transmitted/received. Client-side BE sync via `syncToClients()` after strength changes; `onDataPacket()` compares old/new strength and calls `requestModelDataUpdate()` to trigger chunk re-render for tint update. Outer shell uses `machine_transmitter.png` texture (placeholder).

Stickiness: MachineType.TRANSMITTER -- sticks to other Transmitters + Machine Frames + Frame Family (active). Does NOT stick to Conductors, Repeaters, Toggles, Transformers, Timers, Engines, Pipes, Grabbers, or other specialization types.

Frame activation: Always emits activation (strength 15). Never suppresses.

### Machine Pipe -- Push-Only Item Transport

Registry ID: `machine_pipe`. Class: MachinePipeBlock extends AbstractMachineBlock. `MachineType.PIPE`.

Non-directional Machine Family block. Own BE subclass: MachinePipeBlockEntity (BlockEntityType: MACHINE_PIPE). Extends MachineFrameBlockEntity with transit slot (slot 1, max 64), transfer cooldown (8 ticks), round-robin index, last input face, and Forge IItemHandler capability.

| Property | Value |
|----------|-------|
| **Block entity** | MachinePipeBlockEntity extends MachineFrameBlockEntity |
| **BE type** | ModBlockEntities.MACHINE_PIPE (separate from MACHINE_FRAME) |
| **Specialization item** | Hopper |
| **Blockstate properties** | FILLED (BooleanProperty) |
| **Container size** | 2 (slot 0 = specialization, slot 1 = transit) |

**Behaviour**: Push-only item transport at hopper speed (1 item per 8 ticks). No pulling. Items enter via player hand (input face = UP), hopper/automation (face tracked via ThreadLocal pendingInputFace), or pipe-to-pipe push (sender calls onItemReceived on receiver). Redstone signal locks the pipe (no transfers while powered, like vanilla hoppers).

**Output priority** (relative to input face): forward (opposite), right, left, up, down. Round-robin cycles through valid outputs, skipping faces with no valid receiver. Resets when input face changes. For vertical input (UP/DOWN), NORTH is the reference anchor for right/left.

**FILLED blockstate**: Set to true when transit slot has items, drives texture swap between machine_pipe and machine_pipe_filled models. updateFilledState() guarded with MachineSwapHelper.isSwapping() check to prevent re-placing block during removal.

**Piston safety**: onRemove checks `!movedByPiston` -- skips transit item drop when moved by piston (items preserved via IPistonMovable save/loadMovementData). Also checks `!MachineSwapHelper.isSwapping()` to avoid double-drop during specialization removal (ejectSecondaryItems handles that case). loadMovementData enforces minimum TRANSFER_COOLDOWN after piston landing to prevent immediate push at new position.

**Item preservation on specialization removal**: When the hopper is removed from slot 0, MachineSwapHelper.ejectSecondaryItems() drops the transit slot contents as item entities before the swap.

**Automation interaction**: WorldlyContainer exposes only transit slot (slot 1) to external faces. Specialization slot (0) is GUI-only. canPlaceItemThroughFace records the insertion direction in a ThreadLocal which setItem reads to call onItemReceived with the correct face.

**Comparator output**: Scales transit slot fullness to 0-15. Empty = 0, any items = 1-15 proportional to stack size.

**GUI**: Uses unified MachineFrameMenu with dynamic TransitSlot. TransitSlot.isActive() returns true only when BE is MachinePipeBlockEntity. Screen renders slot tile at (79,37) only when isPipe().

Stickiness: MachineType.PIPE -- sticks to other Pipes + Machine Frames + Frame Family (active). Does NOT stick to Tracks, Trolleys, Conductors, or other specialization types.

Frame activation: Always emits activation (strength 15). Never suppresses.

### Machine Fluid Pipe -- Fluid Vacuum and Depositor

Registry ID: `machine_fluid_pipe`. Class: MachineFluidPipeBlock extends AbstractMachineBlock. `MachineType.FLUID_PIPE`.

Non-directional Machine Family block. Own BE subclass: MachineFluidPipeBlockEntity (BlockEntityType: MACHINE_FLUID_PIPE). Extends MachineFrameBlockEntity with fluid type tracking (FluidType enum), transfer cooldown (8 ticks), round-robin index, last input face, and Forge IFluidHandler capability.

| Property | Value |
|----------|-------|
| **Block entity** | MachineFluidPipeBlockEntity extends MachineFrameBlockEntity |
| **BE type** | ModBlockEntities.MACHINE_FLUID_PIPE (separate from MACHINE_FRAME) |
| **Specialization item** | Bucket (empty) |
| **Blockstate properties** | FLUID_TYPE (EnumProperty: empty, water, lava) |
| **Container size** | 1 (slot 0 = specialization only, no transit slot) |

**Behaviour**: Fluid vacuum and depositor. Redstone powered = actively pulling fluid from adjacent source blocks/cauldrons (opposite of item Pipe -- "pump" metaphor). Unpowered = idle (still auto-pushes if holding fluid). Pull only takes source blocks (flowing water/lava ignored -- infinite water sources survive). Push sends to adjacent empty Fluid Pipes via round-robin, or deposits into world (air -> source block, cauldron -> fills completely). Water+lava collision: bucket in slot 0 replaced with obsidian -> MachineSwapHelper auto-reverts to Machine Frame (no special case code needed).

**Bucket state sync**: setFluidType() swaps slot 0 item to match fluid state: EMPTY -> Items.BUCKET, WATER -> Items.WATER_BUCKET, LAVA -> Items.LAVA_BUCKET. Uses setItemDirect() to avoid triggering swap check (water/lava buckets are not registered specialization items and would cause revert).

**Output priority** (relative to input face): Same as item Pipe -- forward (opposite), right, left, up, down. Round-robin resets on input face change. For vertical input (UP/DOWN), NORTH is the reference anchor.

**Cauldron interaction**: Pull from full water/lava cauldron (empties completely). Push into empty/partial cauldron (fills to max -- vanilla bucket behavior, one bucket = full). Lava cauldrons are always full or empty (no level property in vanilla).

**IFluidHandler capability**: Exposes pipe's fluid state as a Forge IFluidHandler. Reports 1000mB capacity (1 bucket). Accepts water and lava only. Maps fill/drain operations to internal FluidType state. For future mod interop -- other mods querying for fluid handlers will see the pipe's contents.

**Visual**: Dual-cuboid block model with `"render_type": "minecraft:cutout"`. Outer cuboid: pipe texture with 4x4 pixel transparent window in center of each face (player-made texture with glass-like border pixels). Inner cuboid (0.05 inset to avoid z-fighting): shows fluid state. Three inner textures: `machine_fluid_pipe_inner_empty.png` (dark grey #303030), `minecraft:block/water_still` (animated, with tintIndex 0 for biome water color), `minecraft:block/lava_still` (animated, no tint needed). Blockstate variants swap the entire model based on FLUID_TYPE.

**Water tint**: Vanilla water_still.png is greyscale -- color applied at render time. Block color handler registered in ModColorHandlers: when fluid_type=water and tintIndex=0, returns BiomeColors.getAverageWaterColor(level, pos) or default 0x3F76E4 fallback. Item color handler returns 0xFFFFFF (no tint).

**Light emission**: Level 10 when fluid_type=lava (via lightLevel predicate in ModBlocks registration). Reduced from lava's full 15 to represent the small exposed surface area. Level 0 for empty and water.

**Comparator output**: 0 = empty, 15 = has fluid (water or lava).

**Piston safety**: IPistonMovable inherited from MachineFrameBlockEntity preserves fluid type, cooldown, round-robin, and input face through piston moves. loadMovementData enforces minimum TRANSFER_COOLDOWN after landing.

Stickiness: MachineType.FLUID_PIPE -- sticks to other Fluid Pipes + Machine Frames + Frame Family (active). Does NOT stick to item Pipes, Tracks, Trolleys, Conductors, or other specialization types.

Frame activation: Always emits activation (strength 15). Never suppresses.

### Machine Piston -- Omnidirectional Pusher

Registry ID: `machine_piston`. Class: MachinePistonBlock extends AbstractMachineBlock. `MachineType.PISTON`.

Non-directional block (omnidirectional -- no FACING property on the base). Shares MachineFrameBlockEntity (right-click opens GUI showing piston in slot). Signal on any face causes head extension from the opposite face. Multiple heads can extend simultaneously from different faces.

| Feature | Implementation |
|---------|---------------|
| **Extension** | Signal detected in neighborChanged (server only). Block event queued per signal face. triggerEvent runs on BOTH sides: pushes blocks via BlockMoveHelper if needed, starts BESR animation on BE. Ticker advances progress 0.5/tick (2 ticks total). On completion, places real MachinePistonHeadBlock. |
| **Retraction** | Signal loss detected in neighborChanged. Block event queued. triggerEvent removes head immediately (level.setBlock to AIR), starts visual-only retraction animation on BE. Sticky variant: removes head, then pulls block behind head via BlockMoveHelper. |
| **Animation** | BESR-based (MachinePistonArmRenderer registered for MACHINE_FRAME BE type). Per-face animation state in MachineFrameBlockEntity: armProgress[6], armProgressO[6] (previous tick for interpolation), armAnimDirection[6] (+1 extending, -1 retracting, 0 idle). Renderer interpolates with partialTick for smooth sub-tick motion. Extension: arm grows from face outward (offset 0→1). Retraction: arm shrinks from extended to face (offset 1→0). Transient state -- not saved to NBT (animation is only 2 ticks). |
| **Push mechanics** | Uses BlockMoveHelper for pushing blocks. 12-block vanilla push limit. Full sticky group resolution. Sound only plays when no blocks pushed/pulled (BlockMoveHelper plays its own sound). |
| **Immovability** | Cannot be pushed while any head is extended (PistonBaseBlockMixin Feature 5). hasExtendedHead() checks all 6 neighbors for MachinePistonHeadBlock pointing back at piston. |
| **Head block** | MachinePistonHeadBlock: 8×8×16 cuboid, FACING + STICKY properties. PushReaction.BLOCK. Breaks to AIR if parent piston missing (updateShape). onRemove notifies parent. No drops. Middle-click returns parent piston item. |
| **Re-entry guard** | isProcessing ThreadLocal prevents neighborChanged from re-triggering during block events. Animation-in-progress skip prevents new events while animation running on a face. |

Stickiness: MachineType.PISTON -- sticks to other Pistons + Machine Frames + Frame Family (active). Does NOT stick to Sticky Pistons, Tracks, Trolleys, Conductors, Pipes, or other specialization types.

Frame activation: Always emits activation (strength 15). Never suppresses.

### Machine Sticky Piston -- Omnidirectional Pusher/Puller

Registry ID: `machine_sticky_piston`. Class: MachineStickyPistonBlock extends MachinePistonBlock. `MachineType.STICKY_PISTON`.

Extends MachinePistonBlock with isSticky()=true. On retraction, pulls the block in front of the head one space backward before the head disappears. Pull skips blocks that are air, destroyable, immovable, or already moving. All other behavior inherited from MachinePistonBlock.

Stickiness: MachineType.STICKY_PISTON -- sticks to other Sticky Pistons + Machine Frames + Frame Family (active). Does NOT stick to regular Pistons or other specialization types.

Frame activation: Always emits activation (strength 15). Never suppresses.

### Machine Brace -- Structural Push Limit Reinforcement

Registry ID: `machine_brace`. Class: MachineBraceBlock extends AbstractMachineBlock. `MachineType.BRACE`. ~35 lines.

Non-directional block. Shares MachineFrameBlockEntity (right-click opens GUI showing Machine Frame item in slot). No active behavior -- pure structural reinforcement.

| Feature | Implementation |
|---------|---------------|
| **Push limit increase** | Each Brace in a push group adds +6 to the push limit (base 12). @ModifyConstant in PistonStructureResolverMixin replaces the hardcoded 12 with ideomod$calculatePushLimit(). Cascading: limit grows as Braces are gathered. Cap: 60 blocks (8 Braces). |
| **Specialization item** | Machine Frame item (our own mod item). Lazy registration via ensureBraceMapping() in MachineSwapHelper -- first specialization to use a mod item as trigger key. |

Stickiness: MachineType.BRACE -- sticks to other Braces + Machine Frames + Frame Family (active). Does NOT stick to Tracks, Trolleys, Conductors, Pipes, Pistons, or other specialization types.

Frame activation: Always emits activation (strength 15). Never suppresses.

### Machine Piston Head -- Extended Arm Block

Registry ID: `machine_piston_head`. Class: MachinePistonHeadBlock extends DirectionalBlock.

Not a Machine Family member (does NOT implement IMachineFamily). Not an EntityBlock. Placed by MachinePistonBlock when extension animation completes. Removed by MachinePistonBlock on retraction.

| Property | Type | Values | Purpose |
|----------|------|--------|---------|
| FACING | DirectionProperty | 6 directions | Direction the arm points (away from piston base) |
| STICKY | BooleanProperty | false, true | Texture variant (normal vs sticky front face) |

VoxelShape: 8×8×16 cuboid centered on the block face, oriented by FACING. Full block length in extension direction, half-width in perpendicular axes.

Model: Single 8×8×16 element. North face (front) UV-crops central 8×8 pixels from machine_piston.png (or machine_sticky_piston.png for sticky=true). Side faces use machine_piston_arm.png with rotation=90 on east/west for correct orientation after blockstate Y-rotation. Top/bottom use same arm texture with vertical UV strip.

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
| **No redstone** | `isRedstoneConductor` false (2026-10-22), like glass: a powered frame powers nothing behind it. Framed Blocks follow their inner block; the Machine Frame still conducts. GameTest `framesDoNotCarryRedstone`. |



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

**Textures** (re-skinned 2026-10-22): `oddstone_{repulser,attractor}_{front,side,back}.png`, 16x16. The Ideomancer's original faces (grey frame, front patterns, side arrows) on the current Oddstone instead of the original cyan. Made by `tools/textures/make_ranged_textures.py` from the cyan-era files in git: cyan -> Oddstone pixel (fronts: pulled 60% toward the darkest stone tone so the pattern reads), side arrows -> light lavender (176,168,212).

Both extend `AbstractRangedPistonBlock`. FACING + POWERED properties. Block event pattern for client-server sync. Repulser finds nearest Oddstone in line, pushes away. Attractor finds farthest Oddstone, pulls toward device. Each is ~15 lines -- all shared logic lives in the base class. Movement delegated to BlockMoveHelper via RangedPistonUtil.

### Hollowstone Block

Registry ID: `hollow_stone`. Class: `HollowstoneBlock` (implements `EntityBlock`). Block entity: `HollowstoneBlockEntity`. Properties: `MapColor.STONE`, strength 1.5/6.0, `requiresCorrectToolForDrops()`, `SoundType.STONE`, `noOcclusion()`.

**Blockstate**: BooleanProperty `FILLED` (default false). Two variants: `filled=false` → hollow_stone model, `filled=true` → hollow_stone_filled model (transparent center pixels showing inner block).

**Fill interaction**: Right-click with a `BlockItem` whose block is a full cube (collision shape) and has no block entity, when `FILLED=false` (buttons, levers, torches, slabs are refused; fixed 2026-10-22, GameTest `hollowstoneTakesOnlyFullBlocks`) → sets `FILLED=true`, stores inner block ID in `HollowstoneBlockEntity`, consumes 1 item from player hand. Sound + hand swing feedback.

**Mining behavior**: Drops 5-7 Hollowstone Shards (fortune `ore_drops` bonus, `explosion_decay`). Silk touch → drops the block itself. Filled variant: `playerWillDestroy()` sets `markForInnerBlockPlacement()` flag → `onRemove()` reads flag and places inner block at the position. This coordination prevents the inner block from being lost.

**Block entity** (`HollowstoneBlockEntity`): Stores inner block `ResourceLocation`. Lazy-cached `Block` instance. NBT key `"InnerBlock"`. Client sync via `getUpdateTag()`/`getUpdatePacket()`/`ClientboundBlockEntityDataPacket`. `placeInnerOnRemove` flag consumed once during coordinated break.

**Rendering**: `HollowstoneRenderer` BESR — simplified `FramedBlockRenderer`. No FACING transfer, no property delegation. Renders inner block at 0.998 scale with best-of-6-neighbors lighting via `BlockRenderDispatcher.renderSingleBlock()`. Only renders when `hasInnerBlock()` is true. Registered in `Ideomod.clientSetup()`. Render layer: `cutoutMipped` for transparent texture pixels.

**Crafting**: 9 Hollowstone Shards (3x3) → 1 Hollowstone (shaped recipe at `data/ideomod/recipes/hollow_stone.json`). Hollowstone is the centre of the Machine Frame recipe (`machine_block.json`: Hollowstone centre, copper ingots left/right, redstone top/bottom, iron ingots in the corners → 1), as the design intends.

**Loot table**: `data/ideomod/loot_tables/blocks/hollow_stone.json`. Uses `minecraft:alternatives`: silk touch → block drop, else → 5-7 shards with fortune `ore_drops` and `explosion_decay`.

**Tags**: Added to `minecraft:mineable/pickaxe`.

### Redstone Rod -- Handheld Pulse Tool

Item class: `RedstoneRodItem` (`item/RedstoneRodItem.java`). Extends `Item`, overrides `onItemUseFirst()` (Forge hook that fires before `Block.use()`, preventing machine GUIs from opening).

**Signal injection**: `RedstoneRodSignalManager` (`item/RedstoneRodSignalManager.java`). Static class with `@Mod.EventBusSubscriber`. Manages a `ConcurrentHashMap<PulseKey, PulseData>` where PulseKey = (dimension, sourcePos, direction) and PulseData = (ticksRemaining, targetPos). `injectPulse()` stores the pulse, triggers `neighborChanged` on the target block, `updateNeighborsAt` on the source position (covers blocks using `hasNeighborSignal` like Repulsers/Attractors), and `updateNeighborsAt` on the target position (covers indirect neighbors). `getInjectedSignal()` returns 15 if an active pulse matches, 0 otherwise (empty-map fast-path for zero overhead). Server tick handler (`TickEvent.ServerTickEvent` END phase) counts down and triggers "signal off" updates when pulses expire. `clearAll()` called on `ServerStoppingEvent` via listener in `Ideomod.java`.

**Mixin**: `LevelSignalMixin` (`mixin/LevelSignalMixin.java`). Targets `BlockBehaviour.BlockStateBase.class`. Two `@Inject` at HEAD on `getSignal()` (weak power path) and `getDirectSignal()` (strong power path through solid blocks). Both check `RedstoneRodSignalManager.getInjectedSignal()` and return 15 if a pulse is active. `instanceof Level` guard ensures only game-level queries are intercepted.

**Machine Family integration**: `MachineRedstoneHelper.getIncomingSignalFromFace()` and `MachineConductorBlock.getIncomingSignalFromFace()` both have a `RedstoneRodSignalManager.getInjectedSignal()` check at the top (before special-case paths for Conductor, Machine device, and RedStoneWireBlock that bypass `BlockStateBase.getSignal()`). This ensures rod signals are visible on any face regardless of what block occupies the source position.

**Registration**: `ModItems.REDSTONE_ROD` via DeferredRegister, `stacksTo(1)`. Added to `ideomod_tab` creative tab (before debug stick). Language key: `item.ideomod.redstone_rod` = "Redstone Rod". Mixin registered in `ideomod.mixins.json`.

**Recipe**: Shaped, `data/ideomod/recipes/redstone_rod.json`. Pattern: diagonal (stick bottom-left, iron ingot center, redstone torch top-right).

**Resources**: Model at `assets/ideomod/models/item/redstone_rod.json` (parent: `minecraft:item/handheld`, layer0 texture). Texture at `assets/ideomod/textures/item/redstone_rod.png` (16x16 generated via Python/Pillow -- wood handle, iron ferrule, redstone tip with glow).

### World Generation

#### Oddstone Ore Generation

JSON data-driven via configured features, placed features, and Forge biome modifiers.

| Parameter | Value |
|-----------|-------|
| Configured feature | `ideomod:oddstone_ore` — `minecraft:ore` type |
| Vein size | 7 |
| Air exposure discard | 0.0 (no culling near caves) |
| Targets | `minecraft:stone_ore_replaceables`, `minecraft:deepslate_ore_replaceables` |
| Deep placement | `oddstone_ore_deep` — 5 attempts/chunk, uniform Y-64 to Y0 |
| Upper placement | `oddstone_ore_upper` — 3 attempts/chunk, uniform Y0 to Y63 |
| Total | **8 attempts/chunk** at size 7 (between lapis and redstone rarity) |
| Biome modifier | `forge:add_features`, `#minecraft:is_overworld`, step `underground_ores` |

#### Hollowstone Lava Formation

Custom `Feature<NoneFeatureConfiguration>` class: `HollowstoneLavaFormation`. Registered as `ideomod:hollowstone_lava_formation` via `ModFeatures`.

**Algorithm**: Picks random position → scans radius 8 for lava source blocks → identifies shore blocks adjacent to lava (must be in `BASE_STONE_OVERWORLD` tag) → places 2-3 Oddstone on shore → for each Oddstone placed, spreads Hollowstone into up to 2 adjacent lava source blocks + 50% chance of secondary spread into neighbors of those.

| Parameter | Value |
|-----------|-------|
| Placed feature | `hollowstone_lava_formation` — 3 attempts/chunk, uniform Y-64 to Y63 |
| Biome modifier | `forge:add_features`, `#minecraft:is_overworld`, step `underground_decoration` |

**OddstoneBlock Feature 6 — Lava Transformation**: `neighborChanged()` and `onPlace()` in `OddstoneBlock` call `convertAdjacentLava()` — converts adjacent lava SOURCE blocks (not flowing) to Hollowstone with sound effect + steam particles. This means player-placed Oddstone near lava also creates Hollowstone.

#### Ore-Only Debug World Type

Custom `OreOnlyChunkGenerator` extending `NoiseBasedChunkGenerator`. For visualizing ore distribution.

**Behavior**:
- `buildSurface()` → no-op (skips grass, dirt, sand surface rules)
- `applyBiomeDecoration()` → calls super (vanilla feature placement including ores), then iterates every block in the chunk and replaces non-ore blocks with air
- Ore identification: `forge:ores` tag (all vanilla + mod-tagged ores) + explicit checks for `ideomod:oddstone` and `ideomod:hollow_stone`

**Registration**: Codec registered via `DeferredRegister<Codec<? extends ChunkGenerator>>` in `ModFeatures` against `Registries.CHUNK_GENERATOR`. World preset JSON at `data/ideomod/worldgen/world_preset/ore_only.json`. Added to `data/minecraft/tags/worldgen/world_preset/normal.json` tag so it appears in the World Type button cycle during world creation.

**Translation**: `generator.ideomod.ore_only` → "Ore Only (Debug)"

**Files**:
- `worldgen/OreOnlyChunkGenerator.java` — chunk generator class
- `data/ideomod/worldgen/world_preset/ore_only.json` — world preset (overworld uses `ideomod:ore_only` generator, nether/end standard)
- `data/minecraft/tags/worldgen/world_preset/normal.json` — tag append


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

### Ideomod Config (`config/ideomod/ideomod.json`)

Centralized JSON config for all mod systems. Auto-generated on first launch with sensible defaults. Loaded eagerly in `commonSetup()`, with lazy double-checked locking as fallback. All values are clamped to safe ranges on load. Hot-reload via `/ideomod reload` command (op level 2).

Class: `config/IdeomodConfig.java`. Replaces the old single-purpose `OddstoneConfig.java` (deprecated, safe to delete).

**JSON structure** (8 sections):

```json
{
  "_comment": "Ideomod Configuration — edit values below to tune gameplay. Delete this file to regenerate defaults.",
  "oddstone": {
    "sympathetic_radius": 5,
    "affected_mobs": ["minecraft:axolotl", "minecraft:bee", ...]
  },
  "pistons": {
    "ranged_scan_range": 12,
    "base_push_limit": 12
  },
  "machine_braces": {
    "push_limit_per_brace": 6,
    "max_braces": 8,
    "max_push_limit": 60
  },
  "pipes": {
    "item_transfer_cooldown": 8,
    "fluid_transfer_cooldown": 8
  },
  "portals": {
    "teleport_cooldown": 80,
    "scatter_distance_min": 200,
    "scatter_distance_max": 1000,
    "scatter_damage": 10.0
  },
  "judgement": {
    "chance": 200,
    "radius": 32.0,
    "duration_ticks": 100
  },
  "furnace_array": {
    "max_width": 5,
    "max_height": 5
  },
  "timer": {
    "durations": [5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80],
    "default_index": 3
  }
}
```

**Section: oddstone**

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `sympathetic_radius` | int | 5 | 1-16 | How many blocks apart Oddstones can "feel" each other for sympathetic movement. Scans through solid blocks. |
| `affected_mobs` | string[] | 62 mobs | — | Full registry IDs of mobs repelled by Oddstone pathfinding (`DAMAGE_OTHER`). Default includes all land-based vanilla 1.20.1 mobs. Excludes purely aquatic, purely flying, wither, ender dragon. |

**Section: pistons**

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `ranged_scan_range` | int | 12 | 1-64 | How far Repulser/Attractor devices scan for targets (in blocks). |
| `base_push_limit` | int | 12 | 1-128 | Base piston push limit (vanilla default 12). Machine Braces add on top of this. |

**Section: machine_braces**

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `push_limit_per_brace` | int | 6 | 1-64 | Additional push limit each Machine Brace adds. |
| `max_braces` | int | 8 | 0-64 | Maximum number of Braces that contribute to push limit. |
| `max_push_limit` | int | 60 | 1-1024 | Hard cap on total push limit. Formula: `base + (braces × per_brace)`, capped at this value. |

**Section: pipes**

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `item_transfer_cooldown` | int | 8 | 1-200 | Item pipe transfer cooldown in ticks. Vanilla hopper = 8 ticks. Lower = faster. |
| `fluid_transfer_cooldown` | int | 8 | 1-200 | Fluid pipe transfer cooldown in ticks. |

**Section: portals**

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `teleport_cooldown` | int | 80 | 1-1200 | Ticks between portal uses (80 = 4 seconds). |
| `scatter_distance_min` | int | 200 | 1-10000 | Minimum scatter teleport distance for unlinked portals (blocks). |
| `scatter_distance_max` | int | 1000 | min-30000 | Maximum scatter teleport distance. |
| `scatter_damage` | float | 10.0 | 0-100 | Damage from scatter teleport (half-hearts, 10 = 5 hearts). |

**Section: judgement**

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `chance` | int | 200 | 1-10000 | 1-in-N chance Judgement triggers on passive mob kill. |
| `radius` | double | 32.0 | 1-256 | Search radius for mobs when Judgement triggers (blocks). |
| `duration_ticks` | int | 100 | 1-6000 | How long the stare lasts (100 = 5 seconds). |

**Section: furnace_array**

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `max_width` | int | 5 | 1-16 | Maximum furnace array cluster width. |
| `max_height` | int | 5 | 1-16 | Maximum furnace array cluster height. |

**Section: timer**

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `durations` | int[] | [5,10,15,...,80] | each 1-6000 | Available duration presets for Machine Timer GUI buttons, in game ticks. Array can be any length (GUI shows up to 16 in 2 rows). |
| `default_index` | int | 3 | 0 to len-1 | Index into durations array for the pre-selected default when a new Timer is created. Index 3 = 20 ticks. |

**Section: engine**

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `durations` | int[] | [5,10,15,...,40] | each 1-6000 | Available pulse interval presets for Machine Engine GUI buttons, in game ticks. Increments of 5. Array can be any length (GUI shows up to 8 in 1 row). |
| `default_index` | int | 3 | 0 to len-1 | Index into durations array for the pre-selected default when a new Engine is created. Index 3 = 20 ticks. |

**Section: pulser**

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `pulse_duration` | int | 2 | 1-100 | Fixed pulse duration for the Machine Pulser, in game ticks (2 = 1 redstone tick). |

**Reload**: `/ideomod reload` command (op level 2) calls `IdeomodConfig.reloadConfig()`. Also available programmatically for future use.

**Missing sections**: If a section is absent from the JSON, defaults are used. If the entire file is missing, it's regenerated. Individual missing keys within a section fall back to defaults.

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

### Oddstone Family Recipes

Oddstone is NOT craftable (natural resource only). The placeholder `oddstone.json` recipe has been removed.

**Smelting**: `oddstone_smelting.json` -- oddstone -> cobbled_oddstone (200 ticks, 0.1 XP).

**Crafting recipes** (11 total):
- `oddstone_bricks.json` -- 2x2 oddstone -> 4 oddstone bricks (shaped, like vanilla stone bricks)
- Stairs (3): `oddstone_stairs.json`, `cobbled_oddstone_stairs.json`, `oddstone_brick_stairs.json` -- standard stair pattern (6 -> 4)
- Slabs (3): `oddstone_slab.json`, `cobbled_oddstone_slab.json`, `oddstone_brick_slab.json` -- 3 in a row (3 -> 6)
- Walls (3): `oddstone_wall.json`, `cobbled_oddstone_wall.json`, `oddstone_brick_wall.json` -- 2 rows of 3 (6 -> 6)

**Stonecutter recipes** (13 total):
- From oddstone (7): stairs (1), slab (2), wall (1), bricks (1), brick stairs (1), brick slab (2), brick wall (1)
- From cobbled_oddstone (3): stairs (1), slab (2), wall (1)
- From oddstone_bricks (3): brick stairs (1), brick slab (2), brick wall (1)

Stonecutter naming: `<output>_from_<input>_stonecutting.json` (e.g. `oddstone_brick_stairs_from_oddstone_stonecutting.json`). Slabs produce 2 per block via stonecutter; all others produce 1.

### Language File

`assets/ideomod/lang/en_us.json`:

Pattern: `"block.ideomod.registry_name": "Display Name"` for blocks, `"itemGroup.ideomod.tab_name": "Tab Name"` for creative tabs.

Current entries: ideoblock, drill, oddstone, machine_block, oddstone_portal, odd_slime, oddstone_repulser, oddstone_attractor, machine_track, machine_trolley, machine_conductor, machine_pipe, machine_fluid_pipe, machine_piston, machine_sticky_piston, machine_brace, machine_repeater, machine_toggle, machine_transformer, machine_timer, iron_frame, copper_frame, iron_framed_block, copper_framed_block, plus creative tabs (ideomod_tab, framed_blocks_tab).

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

### immovable_when_framed

`data/ideomod/tags/blocks/immovable_when_framed.json`:

Blocks that should be immovable when wrapped in a FramedBlock. Default: obsidian, crying_obsidian, reinforced_deepslate. Extensible by datapacks. Used by PistonBaseBlockMixin Feature 4 and PistonStructureResolverMixin Features 5 and 7.

### frameable_blocks

`data/ideomod/tags/blocks/frameable_blocks.json`:

Whitelist of blocks that can be wrapped in frames. Contains approximately 310 vanilla blocks plus Ideomod blocks (oddstone, odd_slime, cobbled_oddstone, oddstone_bricks). Extensible by datapacks or other mods. Used by FrameabilityHelper to determine valid wrapping targets.

---

## 14. KNOWN ISSUES & TECHNICAL DEBT

| # | Issue | Severity | Location |
|---|-------|----------|----------|
| 1 | ~~OddstoneConfig.reloadConfig() not wired to command~~ | ~~Missing feature~~ | **FIXED** (by 2026-04-19). The config is now IdeomodConfig; `/ideomod reload` (op level 2) calls `IdeomodConfig.reloadConfig()` (registry/ModCommands.java). |
| 2 | Item model translucency | Cosmetic | Translucent inner blocks (glass, slime) render opaque in inventory. Quad-compositing approach can't handle separate render passes. In-world rendering is correct. |
| 3 | ~~Conductor strong power visible to dust through solid blocks~~ | ~~Gameplay bug~~ | **FIXED [2026-10-22]**. New `RedStoneWireBlockMixin` raises `MachineConductorBlock.setDustScanning(true)` around `RedStoneWireBlock.calculateTargetStrength()`; Conductor `getDirectSignal()` returns 0 while any dust scans, the same window in which vanilla dust hides its own strong power. A Conductor still strongly powers a solid block for lamps, pistons and repeaters; dust touching a Conductor is still powered. GameTest: `conductorDoesNotPowerDustThroughBlock`. |
| 4 | ~~Framed redstone blocks don't emit power on frame activation~~ | ~~Gameplay bug~~ | **VERIFIED NOT A BUG [2026-10-22]**. `FramedBlock.getSignal/getDirectSignal` delegate to the inner block regardless of ACTIVE, so activation can't change the output. GameTest `framedRedstonePowersOnPlaceAndActivation` places a Framed Redstone Block through the real item path: the neighbouring lamp lights at once and stays lit after a Machine Brace activates the frame. |
| 5 | Toggle indicator tint uniform on all faces | Cosmetic | Vanilla block color handlers don't receive face direction — all 6 faces of the inner indicator cuboid get the same tint color via tintIndex 1. The input face is visually distinguished by the outer texture swap (off vs on texture), but the indicator glow is the same on all faces including the input face. Fix options: BESR for per-face tint, split inner cuboid into separate elements with different tintIndex values per face (would require per-direction model variants), or make the off texture's window opaque so indicator doesn't show through on input face. File: MachineToggleBlock.java, ModColorHandlers.java. |
| 6 | ~~Pulse jump on piston movement of signal chains~~ | ~~Gameplay bug~~ | **FIXED [2026-10-22]**. Reproduced by GameTest: a ring of 8 Pulsers shoved by a piston at tick 8 fired 5 Pulsers in one tick. Cause: `onMovementRestored` scheduled a tick for every machine block, including idle ones; `tick()` turns an unpowered block on whenever its input is lit, so idle Pulsers woke together and each lit the next. Fix: `saveMovementData` records whether the block had a scheduled tick (`MovementPendingTick`); `onMovementRestored` re-schedules only powered blocks and those with a pending tick. Idle blocks react through normal `neighborChanged` edge detection. GameTests: `pulseRingMovedByPistonAtTick8..11` (every phase of the 2-tick pulse). |
| 7 | ~~Toggle resets to OFF on piston movement~~ | ~~Gameplay bug~~ | **FIXED [2026-03-25]**. The Toggle's latch state (POWERED=true) was lost during piston movement because `onMovementRestored` scheduled a re-evaluation tick, and `tick()`'s POWERED branch unconditionally sets POWERED=false. Fix: `onMovementRestored` now skips scheduling a tick for powered Toggles. Also added `movementPowered` field to MachineFrameBlockEntity to preserve powered state through movement data and restore POWERED + INPUT_FACE blockstate as a safety net. |

---

## 15. CONVENTIONS & PATTERNS

**Registration**: DeferredRegister for blocks, items, creative tabs. All wired in Ideomod() constructor.

**Directional blocks**: Extend DirectionalBlock. Add FACING to state definition. Placement: `context.getNearestLookingDirection().getOpposite()`. Always include `rotate()` and `mirror()` overrides.

**Redstone-activated blocks**: Add POWERED (BooleanProperty) to state definition. In `neighborChanged()`: compare `level.hasNeighborSignal(pos)` with `state.getValue(POWERED)`. Update state. On rising edge, do the thing. Server-side only (`if (!level.isClientSide)`).

**Block events** (for client-server sync): Queue in `neighborChanged()` with `level.blockEvent(pos, this, eventId, encodedParam)`. Handle in `triggerEvent()` which runs on BOTH sides. Encode direction as `facing.get3DDataValue()`, decode with `Direction.from3DDataValue(param)`. Do NOT guard meaningful logic with `instanceof ServerLevel`.

**ThreadLocal coordination**: For passing context from caller into mixin. Set before creating the object that triggers the mixin, read inside the mixin, clear in a finally block. Class must live outside the mixin package.

**Stickiness**: canStickTo = permissive (Layer 1). StickinessRules = restrictive (Layer 2+3). Never express one-way behavior in canStickTo alone. See section 4 for the golden rule.

**Lazy swap mapping**: Specializations that use mod items as trigger keys (rather than vanilla items) must register lazily because DeferredRegister RegistryObjects are not resolved during mod construction. Pattern: skip the mapping in registerMappings(), add an ensureXxxMapping() method called from getSpecializationBlock() and ensureBlockToItem(). See MachineSwapHelper.ensureBraceMapping() as the reference implementation.

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
