# IDEOMOD CODEBASE REFERENCE
*Quick-reference documentation for the Ideomod codebase.*
*Last updated: 2026-04-19 (Frame Family simplification: removed raw vanilla iron/copper as conductors and sticky members; removed cross-frame mediation and INNER_CONDUCTOR property. Oddstone silk-touch loot fix. OddstoneConfig deleted. FurnaceArray frame tag consolidated.)*

---

## PROJECT SETUP

| Field | Value |
|-------|-------|
| Mod ID | `ideomod` |
| Package | `com.ideomancer.ideomod` |
| Minecraft | 1.20.1 |
| Forge | 47.4.10 |
| Java | 17 |
| Mappings | official 1.20.1 |
| Mixin | SpongePowered 0.7.x |

Build files: `build.gradle`, `settings.gradle`, `gradle.properties`, `mods.toml`

**Texture scripts**: `tools/textures/make_portal_texture.py` (the animated portal), `tools/textures/make_ranged_textures.py` (Repulser/Attractor re-skinned from the cyan-era originals onto the current Oddstone). `tools/renders` draws the README pictures from the mod's textures.

**Testing**: in-world GameTests in `gametest/IdeomodGameTests.java` (template `data/ideomod/structures/empty.nbt`, an empty 7x5x7 that each test builds its own setup in). Run `./gradlew runGameTestServer`: a headless server runs them all and exits 0 (all passed) or 1 (failures named in the log). CI runs them on every push, then `scripts/check_jar.sh` boots the built jar on a real Forge server. Tests cover Conductor/dust (#3), framed redstone (#4), Pulser ring moved by a piston at every pulse phase (#6), frames not carrying redstone, a copper frame beside an iron one still activating (mixed metals), machine blocks reading a Transmitter's real strength, a linked Oddstone over the push limit left behind and one pushing over it failing the move (own batch: they lower the limit to 3), the Machine Frame's POWERED look, Hollowstone's full-block rule, and the shapes of the Machine Frame, Iron/Copper Frame, Repulser, Attractor and copper nugget recipes (read from the recipe manager, so a mistyped item id fails the test instead of silently dropping the recipe). `./gradlew runClient -PquickPlay="<world folder>"` opens a world in run/saves straight away.

---

## PACKAGE STRUCTURE

```
com.ideomancer.ideomod
  |-- Ideomod.java              Main mod class. Registers buses, stickiness rules,
  |                              structure definitions, StructureManager event handler.
  |-- block/
  |     |-- OddstoneBlock.java           6-feature core block (see Oddstone section)
  |     |-- OddSlimeBlock.java           Directional slime (sticky only in FACING dir)
  |     |-- ISympathetic.java            Interface: getSympatheticChannel(). Channel marker.
  |     |-- CobbledOddstoneBlock.java    Cobbled oddstone full block. Channel "cobbled_oddstone".
  |     |-- OddstoneBricksBlock.java     Oddstone bricks full block. Channel "oddstone_bricks".
  |     |-- SympatheticWallBlock.java    WallBlock + ISympathetic. isStickyBlock=true, canStickTo=false.
  |     |-- SympatheticStairBlock.java   StairBlock + ISympathetic. isStickyBlock=true, canStickTo=false.
  |     |-- SympatheticSlabBlock.java    SlabBlock + ISympathetic. isStickyBlock=true, canStickTo=false.
  |     |-- DrillBlock.java              3x3 redstone miner [ON HOLD]
  |     |-- frame/
  |     |     |-- IFrameBlock.java             Interface: getFrameMaterial(). Frame Family marker.
  |     |     |                                Static isActive(BlockState) helper.
  |     |     |-- FrameMaterial.java           Data-carrying enum: IRON, COPPER. displayName,
  |     |     |                                registryPrefix, item registry, miningTimeModifier.
  |     |     |                                fromVanillaBlock() (used only by FramedBlockRecipe
  |     |     |                                to reject same-material crafting). Single source
  |     |     |                                for material lookups.
  |     |     |-- FrameBlock.java              Base frame block. BooleanProperty ACTIVE + WATERLOGGED.
  |     |     |                                Implements IFrameBlock + SimpleWaterloggedBlock.
  |     |     |                                Passive conductor. canStickTo: same-material IFrameBlock
  |     |     |                                (both must be ACTIVE), Machine Family, slime/honey/
  |     |     |                                odd_slime permissive, FramedBlock(INNER_STICKY>0)
  |     |     |                                permissive. Cross-material IFrameBlock falls through
  |     |     |                                (no short-circuit).
  |     |     |-- FrameActivation.java         Static utility: batched signal-based activation.
  |     |     |                                markDirty() + end-of-tick TickHandler. flushIfDirty()
  |     |     |                                for piston resolver. Vanilla metal tracking.
  |     |     |-- FrameabilityHelper.java      Tag-driven whitelist for frameable blocks (#ideomod:frameable_blocks)
  |     |     |-- FramedBlock.java             IFrameBlock + EntityBlock proxy for inner block
  |     |     |-- FramedBlockEntity.java       Stores inner block ResourceLocation. IPistonMovable.
  |     |     |-- FramedBlockItem.java         Dynamic getName(), NBT stack creation
  |     |     |-- FramedBlockRecipe.java       CustomRecipe: frame + block = framed block with NBT
  |     |     \-- FramedBlockLoot.java         Helper for manual drop creation
  |     |-- machine/
  |     |     |-- IMachineFamily.java          Interface: getMachineType() + canMachineFamilyStickTo(type, state).
  |     |     |-- MachineType.java             Enum: FRAME, TRACK, TROLLEY, etc. canStickToMachineType().
  |     |     |-- AbstractMachineBlock.java    Base class for all Machine Family blocks. Shared lifecycle,
  |     |     |                                GUI, drops, stickiness, frame activation markDirty().
  |     |     |                                getRedstoneOutput(BlockState) for MachineRedstoneHelper.
  |     |     |                                onSpecItemChanging()/onSpecItemChanged() virtual callbacks
  |     |     |                                for pre/post specialization item change lifecycle.
  |     |     |-- MachineRedstoneHelper.java   Shared signal detection utility. Single source of truth
  |     |     |                                for getIncomingSignalFromFace(), scanAllFaces(),
  |     |     |                                getFaceFromSourcePos(). Generic device detection via
  |     |     |                                AbstractMachineBlock.getRedstoneOutput();
  |     |     |                                Transmitters via getSignal (real BE strength).
  |     |     |-- MachineSwapHelper.java       Swap utility: item<->block registry, checkAndSwap(),
  |     |     |                                createPreloadedStack(), createDrop(), createCloneStack().
  |     |     |                                Client sync via syncToClients() after swap.
  |     |     |-- PositionBoundContainer.java  Position-bound Container. Auto-resolves to current BE.
  |     |     |                                isPipe(), isRepeater(), isTransformer(), isTimer(),
  |     |     |                                isEngine(). Dynamic slots. getBlockContentHeight()
  |     |     |                                reads GUI height from current block class.
  |     |     |-- MachineFrameBlock.java       Extends AbstractMachineBlock. MachineType.FRAME. POWERED state (display only: Sean's active/inactive textures; powered shows the red indicator).
  |     |     |-- MachineFrameBlockEntity.java Shared BE. Single slot + IPistonMovable + client sync.
  |     |     |                                Protected constructor for subclass BE types.
  |     |     |                                (getUpdatePacket, getUpdateTag, syncToClients).
  |     |     |                                transmitterSignalStrength for wireless redstone.
  |     |     |                                pistonSettling flag + isPistonSettling()/setPistonSettling().
  |     |     |                                onDataPacket() for client-side tint refresh.
  |     |     |                                Two-phase setItem: onSpecItemChanging before swap,
  |     |     |                                onSpecItemChanged after swap.
  |     |     |-- MachineFrameMenu.java        Unified menu. SpecSlot + TransitSlot. Fixed content height
  |     |     |                                (89px for all machines). ContainerData size 10.
  |     |     |-- MachineTrackBlock.java       Extends AbstractMachineBlock. MachineType.TRACK. Custom redstone + push.
  |     |     |-- MachineTrolleyBlock.java     Extends AbstractMachineBlock. MachineType.TROLLEY. ~20 lines.
  |     |     |-- MachineConductorBlock.java   Extends AbstractMachineBlock. MachineType.CONDUCTOR.
  |     |     |-- MachinePipeBlock.java        Extends AbstractMachineBlock. MachineType.PIPE. Ticker + push.
  |     |     |-- MachinePipeBlockEntity.java  Extends MachineFrameBlockEntity. Own BE type. Transit slot.
  |     |     |                                Custom redstone wire. Input/output face model.
  |     |     |-- MachineFluidPipeBlock.java   Extends AbstractMachineBlock. MachineType.FLUID_PIPE.
  |     |     |                                Fluid vacuum/depositor. Ticker. Pull/push/deposit.
  |     |     |-- MachineFluidPipeBlockEntity.java  Extends MachineFrameBlockEntity. Own BE type.
  |     |     |                                Fluid type tracking. IFluidHandler capability.
  |     |     |                                Bucket state sync. Water+lava collision.
  |     |     |-- MachinePistonBlock.java      Extends AbstractMachineBlock. MachineType.PISTON.
  |     |     |                                Omnidirectional extend/retract. BESR animation.
  |     |     |                                Ticker for arm progress. BlockMoveHelper for push.
  |     |     |-- MachineStickyPistonBlock.java  Extends MachinePistonBlock. MachineType.STICKY_PISTON.
  |     |     |                                isSticky()=true. Pulls on retract.
  |     |     |-- MachinePistonHeadBlock.java  DirectionalBlock. FACING + STICKY. 8x8x16 shape.
  |     |     |                                PushReaction.BLOCK. Parent survival check. No drops.
  |     |     |-- MachineBraceBlock.java      Extends AbstractMachineBlock. MachineType.BRACE.
  |     |     |                                Structural reinforcement. Push limit from IdeomodConfig.
  |     |     |-- MachineRepeaterBlock.java   Extends AbstractMachineBlock. MachineType.REPEATER.
  |     |     |                                Omnidirectional redstone repeater. Configurable delay.
  |     |     |                                Signal detection via MachineRedstoneHelper.
  |     |     |-- MachineToggleBlock.java     Extends AbstractMachineBlock. MachineType.TOGGLE.
  |     |     |                                Edge-triggered flip-flop. INPUT_FACE blockstate.
  |     |     |                                Signal detection via MachineRedstoneHelper.
  |     |     |                                Piston settlement window (1-tick pistonSettling).
  |     |     |-- MachineTransformerBlock.java Extends AbstractMachineBlock. MachineType.TRANSFORMER.
  |     |     |                                Configurable signal level setter (1-15). OUTPUT_LEVEL
  |     |     |                                blockstate. Signal detection via MachineRedstoneHelper.
  |     |     |-- MachineTimerBlock.java       Extends AbstractMachineBlock. MachineType.TIMER.
  |     |     |                                Edge-triggered timed output. POWERED blockstate.
  |     |     |                                Self-rescheduling countdown. Signal detection via
  |     |     |                                MachineRedstoneHelper.
  |     |     |-- MachineEngineBlock.java      Extends AbstractMachineBlock. MachineType.ENGINE.
  |     |     |                                Fuel-burning pulse clock. LIT + POWERED blockstates.
  |     |     |                                2-game-tick pulse via scheduleTick. Anti-feedback
  |     |     |                                5-layer architecture. Signal via MachineRedstoneHelper.
  |     |     |-- MachineEngineBlockEntity.java Extends MachineFrameBlockEntity. Own BE type
  |     |     |                                (MACHINE_ENGINE). Fuel slot (slot 1), litTime,
  |     |     |                                litDuration, pulseInterval, pulseCounter. Server
  |     |     |                                ticker. IItemHandler capability. Fuel freezes when
  |     |     |                                unpowered.
  |     |     |-- MachineGrabberBlock.java    Extends AbstractMachineBlock. MachineType.SLIME_GRABBER
  |     |     |                                / HONEY_GRABBER. Edge-triggered toggleable sticky.
  |     |     |                                POWERED blockstate. Piston settlement window.
  |     |     |-- MachineTransmitterBlock.java Extends AbstractMachineBlock. MachineType.TRANSMITTER.
  |     |     |                                Wireless redstone via Oddstone channels. POWERED + SOURCE
  |     |     |                                blockstates. Signal attenuation (output = source - 1).
  |     |     |                                shouldSignal + updatingPos ThreadLocals for anti-feedback.
  |     |     \-- FluidType.java               StringRepresentable enum: EMPTY, WATER, LAVA.
  |     |-- movement/
  |     |     |-- IPistonMovable.java          Interface: saveMovementData/loadMovementData
  |     |     |-- MovingBlockEntityData.java   Static registry for BE data in transit during moves
  |     |     |-- BlockMoveHelper.java         General-purpose piston-style block movement.
  |     |     |                                updateAllNeighbors() utility.
  |     |     |-- StickinessRules.java         Rule registry (grab immunity + directional).
  |     |     |                                Helper methods: isOddstoneType(), isFramedOddstone(),
  |     |     |                                isActiveFrameConnection(), hasMatchingInnerSticky().
  |     |     |-- StickyType.java              Enum: NONE, SLIME, HONEY, ODD_SLIME, ODDSTONE.
  |     |     |                                Single source of truth for sticky classification.
  |     |     |                                fromBlock(), of(), isExcludedFrom(), canGrab().
  |     |     \-- MovementScanHelper.java      Shared algorithms: getSympatheticChannel(),
  |     |                                      scanSympatheticChannel(), scanAllChannels(),
  |     |                                      scanStickyGroup(). Channel-aware sympathetic
  |     |                                      scanning.
  |     |-- piston/
  |     |     |-- AbstractRangedPistonBlock.java  Shared base for Repulser/Attractor variants
  |     |     |-- OddstoneRepulserBlock.java   Pushes Oddstone at range (extends Abstract)
  |     |     |-- OddstoneAttractorBlock.java  Pulls Oddstone at range (extends Abstract)
  |     |     |-- RangedPistonUtil.java        Scanning + dispatch for Repulser/Attractor.
  |     |     |                                Unified tryMove() internal method.
  |     |     \-- RangedPistonFlags.java       ThreadLocal for resolver startPos redirect
  |     \-- portal/
  |           |-- OddstonePortalBlock.java     Portal interior fill block + teleportation
  |           |-- OddstonePortalHandler.java   Activation/deactivation lifecycle (static util)
  |           |-- OddstonePortalFrameDetector.java  Frame scanning + address reading
  |           |-- OddstonePortalSavedData.java      Per-dimension portal registry (SavedData)
  |           |-- PortalGeometry.java          Java record: portal dimensions + frame iteration.
  |           |                                detect(), forEachOddstonePosition(), parseAxis().
  |           |-- PortalUtils.java             Corner validation, interior nav, clearing
  |           \-- EnderPearlPortalHandler.java Ender pearl portal activation (Forge event)
  |-- block/
  |     |-- HollowstoneBlock.java        Transparent shell block. FILLED BooleanProperty.
  |     |                                EntityBlock. Right-click fills with block items.
  |     |                                Mining drops shards; filled variant leaves inner
  |     |                                block in world. markForInnerBlockPlacement() flag
  |     |                                coordinates playerWillDestroy + onRemove.
  |     \-- HollowstoneBlockEntity.java  Stores inner block ResourceLocation. Lazy-cached
  |                                      Block instance. NBT key "InnerBlock". Client sync
  |                                      via getUpdateTag()/getUpdatePacket(). placeInnerOnRemove
  |                                      flag for break behavior.
  |-- worldgen/
  |     |-- ModFeatures.java             DeferredRegister<Feature<?>> + DeferredRegister<Codec<? extends ChunkGenerator>>.
  |     |                                Registers HOLLOWSTONE_LAVA_FORMATION feature and
  |     |                                ORE_ONLY chunk generator codec.
  |     |-- HollowstoneLavaFormation.java  Custom Feature<NoneFeatureConfiguration>. Scans
  |     |                                radius 8 for lava source -> finds shore blocks
  |     |                                (BASE_STONE_OVERWORLD) -> places 2-3 Oddstone ->
  |     |                                spreads Hollowstone into adjacent lava sources.
  |     \-- OreOnlyChunkGenerator.java   Debug ChunkGenerator extending NoiseBasedChunkGenerator.
  |                                      No-ops buildSurface, runs vanilla decoration then
  |                                      strips all non-ore blocks (forge:ores tag + oddstone
  |                                      + hollowstone). Registered codec "ideomod:ore_only".
  |-- client/
  |     |-- ModColorHandlers.java        Block/item color tinting (Conductor power indicator,
  |     |                                Fluid Pipe biome water tint, Transmitter level-aware
  |     |                                signal strength tint via BE read + client sync)
  |     |-- FramedBlockDebugOverlay.java F3 debug overlay for inner block info
  |     |-- gui/
  |     |     \-- MachineFrameScreen.java      Dynamic GUI from ideomod_gui.png spritesheet. All machines.
  |     \-- render/
  |           |-- FramedBlockRenderer.java    BESR for framed blocks (renderSingleBlock with facing transfer)
  |           |-- FramedBlockItemModel.java   Custom BakedModel for framed block items (composites inner + frame quads)
  |           |-- FramedBlockRenderTypes.java Custom RenderType for framed block overlays
  |           |-- MovingFrameData.java        Client-side cache for FrameTransitInfo during piston transit
  |           |-- HollowstoneRenderer.java   BESR for Hollowstone. Simplified FramedBlockRenderer.
  |           |                              No FACING transfer. Renders inner block at 0.998
  |           |                              scale with best-of-6-neighbors lighting.
  |           \-- MachinePistonArmRenderer.java  BESR for Machine Piston arm animation.
  |                                              Renders head model at interpolated offset.
  |                                              Registered for MACHINE_FRAME BE type.
  |-- config/
  |     \-- IdeomodConfig.java           Centralized JSON config for all mod systems.
  |                                      Sections: oddstone, pistons, machine_braces, pipes,
  |                                      portals, judgement, furnace_array, timer, engine,
  |                                      pulser. Lazy-load with double-checked locking.
  |                                      /ideomod reload support.
  |-- effect/
  |     \-- JudgementEffect.java         Rare mob-stare event on passive mob kill
  |-- item/
  |     |-- RedstoneRodItem.java         Handheld pulse tool. onItemUseFirst() bypasses
  |     |                                block GUIs. Injects phantom signal via
  |     |                                RedstoneRodSignalManager. Plays lever click
  |     |                                sound + spawns red dust particles on clicked face.
  |     \-- RedstoneRodSignalManager.java  Static pulse registry. ConcurrentHashMap keyed
  |                                        by (dimension, sourcePos, direction). injectPulse()
  |                                        stores pulse + triggers neighbor updates.
  |                                        getInjectedSignal() returns 15 if active pulse.
  |                                        Server tick handler for pulse expiry.
  |                                        @Mod.EventBusSubscriber for auto-registration.
  |-- gametest/
  |     \-- IdeomodGameTests.java        In-world regression tests (runGameTestServer), see PROJECT SETUP.
  |-- mixin/
  |     |-- PistonStructureResolverMixin.java  Enforces stickiness rules + ranged redirect + immovable inner.
  |     |                                      Call-pair toggle for backward walk grab immunity.
  |     |                                      Vanilla metal conductor scanning (bidirectional).
  |     |-- PistonBaseBlockMixin.java          IPistonMovable BE movement + data save + Oddstone pull
  |     |                                      immunity + immovable inner + Machine Piston immovability.
  |     |                                      Retraction: removes piston head before resolver (timing fix).
  |     |-- PistonMovingBlockEntityMixin.java  Restores IPistonMovable data after animation
  |     |-- EntityMovementMixin.java           Entity speed/jump factor for framed blocks.
  |     |                                      Generic ideomod$getFramedFactor() helper.
  |     |-- AbstractFurnaceBlockEntityMixin.java  Furnace array speed boost (50%)
  |     |-- LevelSignalMixin.java            Redstone Rod phantom signal injection.
  |     |-- RedStoneWireBlockMixin.java      Hides Conductor strong power while dust computes its power.
  |     |                                      @Mixin(BlockBehaviour.BlockStateBase.class).
  |     |                                      @Inject on getSignal() + getDirectSignal() at
  |     |                                      HEAD. Returns 15 from RedstoneRodSignalManager
  |     |                                      when active pulse matches (pos, direction).
  |     \-- client/
  |           |-- PistonBaseBlockClientMixin.java           Captures framed block data for transit rendering
  |           \-- PistonMovingBlockEntityRendererMixin.java  Renders inner block during piston animation
  |-- multiblock/
  |     |-- StructureDefinition.java     Abstract base for structure type rulesets
  |     |-- StructureScanner.java        Strategy-based dispatcher. Map<ScanMode, ScanStrategy>.
  |     |-- ScanStrategy.java            @FunctionalInterface for scan mode implementations.
  |     |-- PatternScanStrategy.java     PATTERN scan mode (extracted from StructureScanner).
  |     |-- StructureResult.java         Immutable scan output (builder pattern)
  |     |-- StructureRegistration.java   Persistent registration entry (UUID, NBT).
  |     |                                isWithinBounds() helper.
  |     |-- StructureManager.java        Per-dimension SavedData + event handling + queries
  |     |-- StructureEffectHandler.java  Interface for per-type effects
  |     |-- definitions/
  |     |     \-- FurnaceArrayDefinition.java  Furnace array ruleset (cores, frame tag, sizing)
  |     \-- effects/
  |           \-- FurnaceArrayEffect.java      GUI rename on formation/removal
  \-- registry/
        |-- ModBlocks.java               DeferredRegister<Block> -- all block registrations
        |-- ModItems.java                DeferredRegister<Item> -- BlockItems + debug stick
        |                                + RedstoneRodItem (stacksTo 1)
        |-- ModCreativeTabs.java         Creative tab with all mod items
        |-- ModCommands.java             /judgement + /ideomod reload commands (Forge event bus)
        |-- ModRecipeSerializers.java    DeferredRegister<RecipeSerializer<?>>
        |-- ModBlockEntities.java        DeferredRegister<BlockEntityType<?>>
        |                                MACHINE_FRAME (shared: Frame, Track, Trolley, Conductor,
        |                                Piston, Sticky Piston, Brace, Repeater, Toggle,
        |                                Transformer, Timer, Pulser, Slime Grabber, Honey Grabber,
        |                                Transmitter), MACHINE_PIPE (pipe subclass),
        |                                MACHINE_FLUID_PIPE (fluid pipe subclass),
        |                                MACHINE_ENGINE (engine subclass),
        |                                FRAMED_BLOCK (shared iron/copper),
        |                                HOLLOW_STONE (HollowstoneBlockEntity).
        |-- ModMenuTypes.java            DeferredRegister<MenuType<?>>. MACHINE_FRAME only
        |                                (unified for all machine types).
        \-- ModTags.java                 PORTAL_CORNER_BLOCKS, FRAMEABLE_BLOCKS,
                                         FURNACE_ARRAY_FRAME, IMMOVABLE_WHEN_FRAMED tags.
                                         NOTE: FurnaceArrayDefinition still uses
                                         inline tag reference -- should be updated
                                         to use ModTags.FURNACE_ARRAY_FRAME.
```

---

## CORE SYSTEMS

### 1. Stickiness System (see STICKINESS_SYSTEM.md for full details)

Three-layer architecture for asymmetric and directional stickiness. **StickyType enum** (`block/movement/StickyType.java`) is the single source of truth for sticky block classification -- values NONE(0), SLIME(1), HONEY(2), ODD_SLIME(3), ODDSTONE(4). Provides `fromBlock()`, `of()`, `isExcludedFrom()`, `canGrab()` replacing scattered instanceof chains and magic int constants.

**Layer 1 -- canStickTo()** (in each block class): Permissive type-compatibility check. Now uses `StickyType.of()` and `StickyType.isExcludedFrom()` for mutual exclusion logic. Must return true for ANY pair where either direction of interaction is wanted, because Forge checks bidirectionally (both A->B AND B->A must agree).

**Layer 2 -- Grab Immunity** (StickinessRules + mixin): Blocks specific grabber->target pairs. E.g. "Slime cannot grab Oddstone." Runs after canStickTo passes. Rules consolidated using `StickinessRules` helper methods (`isOddstoneType()`, `isFramedOddstone()`, `isActiveFrameConnection()`, `hasMatchingInnerSticky()`).

**Layer 3 -- Directional Rules** (StickinessRules + mixin): Blocks ALL grabs by a block when push direction doesn't match (Odd Slime only sticky when pushed in FACING dir).

Rules registered in `Ideomod.registerStickinessRules()` (4 consolidated grab immunity + 1 directional). Enforced by `PistonStructureResolverMixin` at two points: (A) HEAD of addBlockLine during branching — full grab immunity + directional checks; (B) @Redirect on canStickTo in backward walk — directional checks on both calls, grab immunity on Call 1 only (front block reaching back) via call-pair toggle.

**Stickiness Matrix** (rows = grabber, cols = target). Machine Family uses MachineType-aware stickiness. "Machine Frame" = MachineType.FRAME (universal connector). "Machine Spec" = any specialization (Track, Trolley, Conductor, etc.). "Frame Fam" = all IFrameBlock implementations. See STICKINESS_SYSTEM.md for full pair-by-pair implementation details. (Raw vanilla iron_block / copper_block were removed from frame-family stickiness on 2026-04-19. Cross-frame mediation via INNER_CONDUCTOR was also removed -- frames now require exact same-material match.)

|              | Slime | Honey | Odd Slime | Machine Frame | Same-Type Spec | Diff-Type Spec | Frame Fam (active) | Frame Fam (inactive) | Oddstone | Normal |
|:-------------|:-----:|:-----:|:---------:|:-------------:|:--------------:|:--------------:|:------------------:|:--------------------:|:--------:|:------:|
| Slime        |  YES  |  no*  |    no*    |      YES      |      YES       |      YES       |        YES         |         YES          |    no    |  YES   |
| Honey        |  no*  |  YES  |    no*    |      YES      |      YES       |      YES       |        YES         |         YES          |    no    |  YES   |
| Odd Slime    |  no*  |  no*  |  YES (d)  |    YES (d)    |    YES (d)     |    YES (d)     |      YES (d)       |       YES (d)        |    no    | YES (d)|
| Machine Frame|  no   |  no   |    no     |      YES      |      YES       |      YES       |        YES         |         no           |    no    |   no   |
| Machine Spec |  no   |  no   |    no     |      YES      |      YES       |       no       |        YES         |         no           |    no    |   no   |
| Frame Fam (active)|  no   |  no   |    no     |      YES      |      YES       |      YES       |      YES (m)       |         no           |    no    |   no   |
| Framed Slime (act.)|  YES  |  no*  |    no*    |      YES      |      YES       |      YES       |      YES (m)       |         no           |    no    |  YES   |
| Framed Honey (act.)|  no*  |  YES  |    no*    |      YES      |      YES       |      YES       |      YES (m)       |         no           |    no    |  YES   |
| Framed OddSlime (act.)|  no*  |  no*  |  YES (d)  |      YES      |      YES       |      YES       |      YES (m)       |         no           |    no    | YES (d)|
| Framed Oddstone (act.)|  YES  |  YES  |    YES    |      YES      |      YES       |      YES       |      YES (m)       |         no           |   YES    |   no   |
| Framed Oddstone (inact.)|  YES  |  YES  |    YES    |       no      |       no       |       no       |        no          |         no           |   YES    |   no   |
| Framed Sticky (inact.)|  (i)  |  (i)  |    (i)    |       no      |       no       |       no       |        no          |         no           |    no    |  (i)   |
| Oddstone     |  YES  |  YES  |    YES    |       no      |       no       |       no       |        no          |         no           |   YES    |   no   |

`no*` = mutual canStickTo exclusion | `no` = grab immunity or canStickTo rejection | `(d)` = directional (only when pushed in FACING direction) | `(m)` = material-matched (iron<->iron, copper<->copper) | `(i)` = inner sticky behavior only (Framed Slime grabs like slime, Framed Honey like honey, Framed Odd Slime like odd slime directional)
"Same-Type Spec" = same MachineType (Track<->Track). "Diff-Type Spec" = different MachineType (Track<->Trolley) -- canStickTo returns false at Layer 1.
Machine Frame (MachineType.FRAME) is the universal bridge between all specialization types.
Frame Fam stickiness requires ACTIVE on BOTH sides. Machine Family only grabs active frames.
Framed Sticky (active) has BOTH inner stickiness + frame stickiness. Framed Sticky (inactive) has inner stickiness only.
Framed Oddstone (active) has both oddstone inner stickiness + frame stickiness. Framed Oddstone (inactive) has oddstone inner stickiness only (grabs slime/honey/odd_slime/oddstone but NOT machine fam or frame fam).

### 2. Block Movement System (BlockMoveHelper + Ranged Pistons + Track/Trolley + IPistonMovable)

**Architecture**: Three-layer system. BlockMoveHelper provides general-purpose piston-style movement. RangedPistonUtil adds scanning. AbstractRangedPistonBlock adds shared block logic. All use vanilla's block event system for client-server sync (see BLOCK_EVENT_LESSON.md). **MovementScanHelper** (`block/movement/MovementScanHelper.java`) provides shared scanning algorithms used by all three mixin files -- `isOddstoneOrFramedOddstone()`, `scanSympathetic()`, `scanStickyGroup()` -- eliminating 3x code duplication.

**BlockMoveHelper.moveBlockAt(level, initiatorPos, targetPos, pushDir)**: Single entry point for any system needing piston-style block movement. Mirrors vanilla `PistonBaseBlock.moveBlocks()` exactly. Steps: set ThreadLocal target -> create resolver -> resolve -> safety checks -> snapshot states -> save IPistonMovable data (server-only, step 2.5) -> capture MovingFrameData for transit rendering (client-only, step 2.6) -> destroy breakables -> place MovingPistonBlock at destinations (flag 68) -> clear vacated sources (flag 82) -> neighbor updates -> sound.

**Safety checks**: (1) toPush non-empty and contains target, (2) toPush doesn't contain initiator, (3) no destination equals initiator position.

**IPistonMovable pipeline**: Opt-in block entity data preservation during piston moves. Implementing blocks: MachineFrameBlockEntity, FramedBlockEntity. Save phase: PistonBaseBlockMixin (vanilla pistons) or BlockMoveHelper step 2.5 (our systems). Both server-only. Transit: MovingBlockEntityData static ConcurrentHashMap. Restore: PistonMovingBlockEntityMixin via TWO paths -- tick() TAIL (normal 2-tick animation) and finalTick() TAIL (1-tick pulses). Single consumer pattern -- retrieve() is destructive.

**PistonBaseBlockMixin**: Four features. (1) isPushable HEAD inject returns true for IPistonMovable block entities; also handles Oddstone pull immunity via position-specific ThreadLocal<BlockPos> — during retraction, only the directly pulled oddstone is immune, not oddstone elsewhere in group. (2) moveBlocks HEAD inject saves data. Extension uses resolver. Retraction bypasses resolver (it fails for our blocks), scans pull position directly + BFS for sticky group + sympathetic scan for distant oddstone via scanSympathetic(). (4) Framed block inner push reaction: checks `#ideomod:immovable_when_framed` tag (data-driven, extensible by datapacks) + destroySpeed == -1 fallback for bedrock-type blocks.

**RangedPistonUtil**: Scanning + dispatch for Repulser/Attractor. `scanForNearest` / `scanForFarthest` walk up to configurable range (default 12, via IdeomodConfig.getRangedPistonScanRange()). Delegates movement to BlockMoveHelper.

**AbstractRangedPistonBlock**: Shared base for Repulser/Attractor. Handles FACING/POWERED, rising edge, block events, rotation. Subclasses implement `fire()` only (~15 lines each).

**Scheduled ticks across a move** (`MachineFrameBlockEntity`): `saveMovementData()` records `MovementPowered` (output > 0) and `MovementPendingTick` (a scheduled tick was waiting); `onMovementRestored()` restores POWERED and re-schedules a tick only for blocks that were powered (delay 2, mid-countdown) or had a tick pending (delay 1). Idle blocks get none and react through normal `neighborChanged()` edge detection. FIX 2026-10-22: every machine block used to get a tick, and since an unpowered Pulser's `tick()` fires whenever its input is lit, idle Pulsers in a moved chain fired in a same-tick cascade (Known Issue #6, the "pulse jump").

**RangedPistonFlags** (ThreadLocal): Redirects the resolver's startPos from (devicePos + pushDir) to the actual target block. Set before `new PistonStructureResolver()`, cleared in finally block.

**MachineTrackBlock**: Uses BlockMoveHelper directly (no scanning). Internal POWER blockstate (0-15) for Track-to-Track chaining only -- NOT exposed to vanilla redstone (isSignalSource=false, getSignal=0, getDirectSignal=0). Reads input from other Tracks/Conductors via direct blockstate access and from any vanilla redstone source via level.getSignal() (no whitelist needed -- Track emits no signal so no feedback risk). Signal direction detection checks actual sources (conduit blocks with higher POWER, any vanilla source with signal > 0) rather than vanilla `hasSignal()`. Pushes ALL adjacent Trolleys in the computed direction. Block event encodes pushDir + trolleyFace via bit packing. Re-entrancy guard uses ThreadLocal<Boolean>.

**MachineTrolleyBlock**: Passive cargo. No active behavior, just Machine Family stickiness.

**MachineBraceBlock**: Structural reinforcement. No active behavior. Push limit increase implemented in PistonStructureResolverMixin Feature 8 via @ModifyConstant -- each Brace in toPush adds to the push limit. All values configurable via IdeomodConfig: push_limit_per_brace (default 6), max_braces (default 8), max_push_limit (default 60), base_push_limit (default 12). Cascading: limit grows during block gathering as Braces are discovered. A Brace must be within the current limit to count. Machine Frame item as specialization trigger via lazy mapping (ensureBraceMapping).

**MachineConductorBlock**: Solid-block redstone wire. POWER blockstate (0-15) exposed to vanilla redstone. Input/output face model: receives on one face (strongest signal), outputs on other 5. Source blocks (levers etc.) = no decay. Wire blocks (dust, other Conductors) = -1 decay. getSignal and getDirectSignal return 0 on input face via `direction == inputFace.getOpposite()` check. inputFace stored in shared MachineFrameBlockEntity (reset to null on specialization swap to prevent stale directional data). Visual: dual-cuboid model with cutout render type — outer shell with a transparent 4x4 window over an inner underlay cuboid using the shared machine_indicator texture, tinted via ModColorHandlers (tintIndex 1, squared red gradient). Known issue: dust sees strong power through solid blocks (#12).

**MachinePipeBlock**: Push-only item transport. Own BE subclass (MachinePipeBlockEntity, BlockEntityType MACHINE_PIPE). FILLED BooleanProperty drives texture. Server ticker pushes 1 item per 8 ticks via IItemHandler capability. Round-robin output relative to input face (forward, right, left, up, down). Redstone locks. No pulling. Input face tracked via: player GUI = UP, hopper/automation = ThreadLocal pendingInputFace in canPlaceItemThroughFace, pipe-to-pipe = onItemReceived() called by sender. Piston safety: movedByPiston guard in onRemove prevents duplication; minimum cooldown in loadMovementData prevents immediate push after landing. updateFilledState guarded against isSwapping. Comparator output scales transit fullness.

**Filter predicates**: `ODDSTONE_ONLY` (current), `ANY_MOVABLE` (future Arcane variants).

### 3. Portal System

**Components**: OddstonePortalHandler (lifecycle + debug stick notifications) -> OddstonePortalFrameDetector (scanning) -> OddstonePortalSavedData (persistence + cached geometry) -> OddstonePortalBlock (teleportation). **PortalGeometry** record (`block/portal/PortalGeometry.java`) consolidates portal dimension scanning and frame iteration that was previously duplicated across 3+ files. Provides `detect()`, `forEachOddstonePosition()`, `parseAxis()`, `getFrameCorners()`.

**Frame rules**: Rectangular frame of Oddstone blocks with corner blocks (crafted mineral blocks from PORTAL_CORNER_BLOCKS tag). Interior 3-19 blocks wide/tall. ALL Oddstone in frame must be redstone-powered.

**Addressing**: 4 corner block types define address. 14 valid corner types = 38,416 unique addresses. Address is canonicalized (front vs back comparison) so both sides of a frame give the same address.

**Linking**: Max 2 portals per address. First lights unlinked, second auto-links to first. Destroying one unlinks the other.

**Teleportation**: Linked portals preserve relative position (fractional offset). All teleports apply 180-degree yaw rotation so player exits facing away from destination portal (doorway effect). Cross-axis portals (X vs Z) additionally rotate 90 degrees to align with destination axis. Unlinked portals scatter-teleport 200-1000 blocks + 5 hearts damage.

**Deactivation strategy**: Full frame re-validation via `validateOrDeactivate()`. When any frame Oddstone gets neighborChanged, navigates from portal block to frame, checks all corners + all Oddstone powered. Only deactivates if validation fails. Prevents false triggers from unrelated neighbor updates. Immediate deactivation via `onBlockRemoved()` when a frame Oddstone is physically broken.

**Look and feel** (2026-10-22/24): own animated texture `oddstone_portal.png` (32 frames, frametime 2, interpolated; drawn by `tools/textures/make_portal_texture.py`, original art, light blue with white sparks), models `render_type: translucent`, block `noOcclusion()`. Particle type `ideomod:oddstone_portal` (`registry/ModParticles`; client: `client/OddstonePortalParticle` extends vanilla `PortalParticle`, light-blue tint, registered in `ClientSetup.onRegisterParticles`). `OddstonePortalBlock.animateTick()` (client): 4 of those per tick drifting out of both faces along the AXIS value (the pane is thin along AXIS: axis=x uses the shape thin in x), an occasional near-white `DustParticleOptions` spark, and a 1/100 per-tick `PORTAL_AMBIENT` hum at pitch 1.2-1.6.

**Break effects**: `PortalUtils.clearPortalBlocks()` uses `level.destroyBlock(pos, false)` for proper break particles and sound on all deactivation paths.

**Registration**: `OddstonePortalSavedData` (per-dimension SavedData). Keyed by frame corner position. Serialized to NBT. Portal geometry (interior bottom-left, width, height) cached in PortalEntry at registration time — computed from FrameResult corners, avoids PortalGeometry.detect() during teleport. Old saves without cached geometry fall back to live detection.

### 4. Multiblock Structure Framework [WIP]

**Architecture**: Registration-based system. Structures are world data managed by StructureManager (per-dimension SavedData). Individual blocks don't know they're part of a structure. Uses **Strategy Pattern** for scan modes -- each scan algorithm is a `ScanStrategy` implementation registered with StructureScanner.

**Components**: StructureDefinition (abstract ruleset) -> StructureScanner (strategy dispatcher) -> ScanStrategy (interface) -> PatternScanStrategy (PATTERN mode impl) -> StructureResult (scan output) -> StructureManager (registration + events) -> StructureRegistration (persistent entry) -> StructureEffectHandler (per-type effects).

**StructureManager data structures**:
- `structures`: UUID -> StructureRegistration (all registered structures)
- `chunkIndex`: ChunkPos -> Set<UUID> (which structures overlap each chunk)
- `typeIndex`: String -> Set<UUID> (structures grouped by definition name)
- `coreExclusivity`: BlockPos -> Map<String, UUID> (one structure per type per core)
- `trackedCores`: Set<BlockPos> (event-placed cores not yet in structures, for future non-BE cores)

**Event flow**: Block placed/broken -> check chunk index for nearby structures -> rescan affected -> check if placed block is a core -> try new formation -> check if placed block is frame material -> search nearby for cores via block entity maps + trackedCores -> try formation.

**Core detection**: Two complementary approaches: (1) block entity map iteration from nearby chunks (cheap, catches furnaces), (2) event-populated trackedCores set (catches future non-block-entity cores). No chunk scanning on load.

**Scanner**: Strategy-based dispatch via `Map<ScanMode, ScanStrategy>`. Current implementation: `PatternScanStrategy` (extracted from old monolithic scanner). Phase 1: flood-fill cluster of adjacent same-type cores. Phase 2: compute frame bounds from cluster + facing. Phase 3: validate frame layers (back wall, sides, top, bottom) + front face air openings. Normalizes bounds and core position to clusterMin. Adding new scan modes (PROXIMITY, PROXIMITY_WALLED) requires only a new ScanStrategy implementation -- no modification to StructureScanner.

**Current structure type -- Furnace Array**:
- Cores: furnace, blast_furnace, smoker (all vanilla furnace types)
- Frame: #ideomod:furnace_array_frame tag (18 stone-family blocks)
- Max cluster: 3 wide x 2 tall (6 furnaces)
- Facing: derived from furnace's FACING property
- Effects: GUI rename ("Enhanced Furnace" etc.) + 50% cooking speed boost
- Speed boost: +1 extra progress on even ticks via AbstractFurnaceBlockEntityMixin, clamped to totalTime - 1

### 5. Oddstone (6 features in one block)

1. **Mob avoidance**: `isPathfindable=false` + `BlockPathTypes.DAMAGE_OTHER`. JSON config per-mob whitelist.
2. **Redstone insulation**: `isRedstoneConductor=false` in properties.
3. **Sympathetic movement**: Handled by PistonStructureResolverMixin. BFS within configurable radius (default 12; was 5 until 2026-10-25). Chains count toward 12-block limit. A linked block is skipped if toPush is already at the limit; otherwise its addBlockLine result is honoured, and false (its push/pull over the limit, or blocked) fails the whole resolve (2026-10-25; the result used to be ignored).
4. **One-way stickiness**: Grabs all slime types (real+framed) via `StickyType.ODDSTONE.canGrab()`, which returns true only for SLIME, HONEY, ODD_SLIME. Cannot grab other Oddstone blocks (real or framed) -- all oddstone-to-oddstone connections resolve purely through sympathetic movement channels. Cannot be grabbed back (grab immunity rules). canStickTo recognizes FramedBlock targets via INNER_STICKY check.
5. **Portal frame**: Delegates to OddstonePortalHandler on neighborChanged/onRemove.
6. **Pull immunity**: Cannot be directly pulled by sticky pistons. PistonBaseBlockMixin position-specific check. Oddstone dragged by own stickiness (e.g., stuck to slime being pulled) still moves correctly.

### 6. Machine Frame (universal connector + Machine Family base)

Registry ID: `machine_block`. Class: MachineFrameBlock extends **AbstractMachineBlock**. All Machine Family blocks extend AbstractMachineBlock (shared lifecycle, GUI, drops, stickiness, frame activation via markDirty()). MachineType.FRAME (universal connector). Specializations: MachineTrackBlock (TRACK), MachineTrolleyBlock (TROLLEY), MachineConductorBlock (CONDUCTOR), MachinePipeBlock (PIPE, own BE subclass), MachineFluidPipeBlock (FLUID_PIPE, own BE subclass), MachinePistonBlock (PISTON), MachineStickyPistonBlock (STICKY_PISTON), MachineBraceBlock (BRACE), MachineRepeaterBlock (REPEATER), MachineToggleBlock (TOGGLE), MachineTransformerBlock (TRANSFORMER), MachineTimerBlock (TIMER), MachinePulserBlock (PULSER), MachineEngineBlock (ENGINE), MachineGrabberBlock (SLIME_GRABBER, HONEY_GRABBER), MachineTransmitterBlock (TRANSMITTER).

1. **Universal stickiness**: MachineType.FRAME sticks to ALL machine types. canStickTo delegates to `IMachineFamily.canMachineFamilyStickTo(MachineType.FRAME, other)`. Permissive to slime/honey/odd_slime (grab immunity handles restriction).
2. **Block entity with GUI**: Most machine blocks share MachineFrameBlockEntity (single item slot). Pipe uses MachinePipeBlockEntity subclass (adds transit slot, own BE type). Unified MachineFrameMenu with dynamic TransitSlot. PositionBoundContainer keeps GUI open across swaps. MachineSwapHelper handles block replacement when slot changes. GUI renders from ideomod_gui.png spritesheet (187px fixed height, 89px content area).
3. **Anti-signal emission**: Only Machine Frame suppresses frame networks (powered = anti-signal). All other specializations always activate.
4. **Drop preservation**: getDrops() override -- conditional BlockEntityTag only when slot has content. getCloneItemStack() uses registry-based createCloneStack() (client-safe).

**IMachineFamily interface** (`block/machine/IMachineFamily.java`): Provides `getMachineType()` and static `canMachineFamilyStickTo(MachineType, BlockState)`. Type check: FRAME sticks to everything, specializations stick to same-type + FRAME. Frame Family requires active. Slime types permissive.

**MachineSwapHelper** (`block/machine/MachineSwapHelper.java`): Central utility. Bidirectional registry (Powered Rail <-> Track, Minecart <-> Trolley, Redstone <-> Conductor, Hopper <-> Pipe). `checkAndSwap()` with isSwapping ThreadLocal. `createPreloadedStack()` for creative tab. `createDrop()` conditional NBT. `createCloneStack()` registry-based middle-click.

**PositionBoundContainer** (`block/machine/PositionBoundContainer.java`): Container backed by Level + BlockPos. Reports 2 slots. Slot 0 resolves to any MachineFrameBlockEntity. Slot 1 delegates to MachinePipeBlockEntity (returns empty for non-pipes). isPipe(), isRepeater(), isTransformer(), isTimer() for live type checking. getTimerDuration(), getRepeaterDelay(), getTransformerLevel() for ContainerData sync. Survives block swaps without close/reopen.

**Static helpers** (on MachineFrameBlock): `isRedstoneConduit(Block)` -- MachineTrackBlock only.

**Frame activation**: onPlace/onRemove/neighborChanged all call `FrameActivation.markDirty(level, pos)` to queue frame network recalculation at end-of-tick. Piston resolver calls `flushIfDirty(level)` at HEAD of resolve() for immediate processing.

### 7. Frame Blocks (activation-based stickiness)

Registry IDs: `iron_frame`, `copper_frame`. Class: FrameBlock (implements IFrameBlock, SimpleWaterloggedBlock).

1. **Activation-based stickiness**: BooleanProperty ACTIVE (default false). `isStickyBlock()` returns ACTIVE value. Frames are passive -- all lifecycle hooks call `FrameActivation.markDirty()`. Machine Blocks are the signal source. Signal strength 15, decays by 1 per conductor. Max range 15 blocks.
2. **Material exclusivity**: canStickTo returns true for same-material IFrameBlock **only when neighbor is also active** (`IFrameBlock.isActive(other)`). Cross-material IFrameBlock falls through (does not short-circuit to false) so inner stickiness permissive entries can match. Also permissive to: Machine Family, SlimeBlock, HoneyBlock, OddSlimeBlock, FramedBlock with INNER_STICKY>0.
3. **Waterlogging**: BooleanProperty WATERLOGGED (default false). Auto-waterlogs when placed in water. updateShape() schedules fluid ticks.
5. **No redstone through frames**: `isRedstoneConductor` is false for both frames (registered in ModBlocks, 2026-10-22), like glass. Framed Blocks follow their inner block instead; the Machine Frame (solid) still conducts.
4. **Grab immunity**: Rules (consolidated) prevent specific grabber->target pairs. Carve-outs for matching inner types.

(Removed 2026-04-19: vanilla iron_block / copper_block as conductors and sticky members; cross-frame mediation via INNER_CONDUCTOR. Frames now require exact same-material match.)

**FrameActivation algorithm** (redstone-style signal strength with anti-signal suppression):
1. DISCOVER: BFS from change point through all IFrameBlock conductors
2. WIPE: Set all IFrameBlocks to ACTIVE=false
3. FIND SOURCES: Check all network blocks + change position for adjacent Machine Blocks
4. PROPAGATE ACTIVATION: BFS from activation sources, strength 15 decaying by 1. A neighbour is queued only if it shares a metal with the signal (2026-10-25 fix: queuing an incompatible frame marked its spot reached and blocked a same-metal signal of equal strength). Machine Frames only activate when unpowered; all other Machine Family blocks always activate regardless of redstone state. Each conductor accepts and outputs only its own frame material -- iron-only through iron frames, copper-only through copper frames. No cross-material mediation.
5. PROPAGATE ANTI-SIGNAL: BFS from powered Machine Frames ONLY (no other Machine Family blocks), strength = redstone input level, decaying by 1. Binary override: any block reached = deactivated.
6. APPLY: IFrameBlocks get ACTIVE=true only if activation > 0 AND anti-signal == 0.

Max range: 15 blocks from source. Public queries: `isActivatedConductor(state, pos)`, `isActive(state)`.

Called by: All Machine Family blocks (via AbstractMachineBlock) and FrameBlock onPlace/onRemove/neighborChanged. Redstone input changes trigger via neighborChanged automatically. Primary entry point: `FrameActivation.markDirty(level, pos)`. Internal: `recalculate()` is package-private, called by `processDirtyNetworks()` and `flushIfDirty()`.

**Rendering**: RenderType.cutout() registered in Ideomod.clientSetup(). noOcclusion() in block properties. Transparent interior pixels render correctly.


### 7b. Framed Blocks (dynamic block wrapping)

Registry IDs: `iron_framed_block`, `copper_framed_block`. Class: FramedBlock (implements IFrameBlock + EntityBlock).

1. **Inner block proxy**: FramedBlockEntity stores ResourceLocation. ~19 methods delegate to inner block's defaultBlockState(). Mining speed, sound, friction, light, redstone, pathfinding, stepOn, fallOn, updateEntityAfterFallOn, entityInside all preserved. Speed/jump factor via EntityMovementMixin. Push reaction (immovable inners) via PistonBaseBlockMixin Feature 4 (tag-based: `#ideomod:immovable_when_framed` + destroySpeed == -1 fallback) + Resolver Feature 5.
2. **Two-layer rendering**: BESR (FramedBlockRenderer) uses BlockRenderDispatcher.renderSingleBlock() at 0.998 scale for epsilon inset. Handles multi-element models (slime), translucency (glass), tinting (grass), directional textures. JSON model draws frame overlay on top. Brightest-of-neighbors lighting.
3. **FACING property** (DirectionProperty): Set from player look direction on placement. Transferred to inner block's orientation property during rendering.
4. **INNER_STICKY property** (IntegerProperty 0-4): Maps to `StickyType` enum values (NONE=0, SLIME=1, HONEY=2, ODD_SLIME=3, ODDSTONE=4). Synced from inner block type by FramedBlockEntity.updateBlockstateFromInner().
5. **INNER_LIGHT property** (IntegerProperty 0-15): Inner block's light emission level. Read by lightLevel predicate in ModBlocks.java. Pre-populated in getStateForPlacement() from item NBT so the lighting engine detects emission on the first setBlock. Also synced by updateBlockstateFromInner() for piston restores and chunk loads.
6. **Activation-based stickiness**: Same IFrameBlock + FrameActivation system as Frame Blocks. Material-matched (iron<->iron, copper<->copper). canStickTo requires neighbor IFrameBlock to be active AND the same frame material. isStickyBlock() returns true when ACTIVE || INNER_STICKY > 0. (Cross-frame mediation via INNER_CONDUCTOR was removed 2026-04-19.)
7. **Inner sticky behavior**: Framed blocks with sticky inners preserve native stickiness. canStickTo() combines frame rules (ACTIVE-gated, same-material) + inner sticky rules (INNER_STICKY-gated with vanilla exclusions).
8. **Framed Odd Slime directional**: Inner stickiness blocked in non-FACING push directions. Frame stickiness still works via second-chance mixin check.
9. **noOcclusion()**: Block properties include noOcclusion() to prevent neighbor face culling.
10. **Crafting**: FramedBlockRecipe (CustomRecipe): 1 frame + 1 frameable block = framed block with BlockEntityTag NBT. Same-material combinations (iron frame + iron_block, copper frame + copper_block) rejected as redundant.
11. **Dynamic creative tab**: FRAMED_BLOCKS_TAB iterates all registered blocks, checks FrameabilityHelper.isFrameable(), creates variants.
12. **F3 debug**: FramedBlockDebugOverlay shows inner block ID on right-side F3 panel.

Blockstate count per framed block type: 2 (ACTIVE) x 6 (FACING) x 5 (INNER_STICKY) x 16 (INNER_LIGHT) = 960 states. (Was 2880 before INNER_CONDUCTOR removal on 2026-04-19.)

**FrameabilityHelper**: Tag-driven whitelist. Checks `#ideomod:frameable_blocks` tag (~310 vanilla blocks + 2 ideomod blocks), NOT FramedBlock, NOT IFrameBlock. Infested blocks excluded.

**FramedBlockItem**: Custom BlockItem. Dynamic getName() using `frameMaterial.getDisplayName()`. Static createStack() delegates to `FramedBlockEntity.setInnerBlockIdOnStack()` for NBT setup. `getInnerBlockId()` delegates to `FramedBlockEntity.getInnerBlockIdFromStack()`.

**MovingFrameData**: Client-side ConcurrentHashMap<BlockPos, FrameTransitInfo>. FrameTransitInfo record contains innerBlockId, facing, innerSticky. Populated by PistonBaseBlockClientMixin before vanilla destroys block entities. Read by transit renderer mixin.

### 8. Machine Track (isolated redstone + Trolley push)

Non-directional Machine Family block. Internal POWER blockstate (0-15, decay by 1) for Track-to-Track chaining only. NOT exposed to vanilla redstone system (isSignalSource=false, getSignal=0, getDirectSignal=0). Reads from other Tracks/Conductors via direct blockstate access and from any vanilla redstone source via level.getSignal() (no whitelist needed -- Track emits no signal so no feedback risk). On rising edge, pushes adjacent Trolleys opposite to signal source direction. Uses BlockMoveHelper for piston-style animated movement.

**Indicator** (2026-10-22): two-layer model like the redstone machines. `machine_track.png` has a transparent 4x4 window in the middle of each face over the inner cuboid (shared `machine_indicator.png`, tintIndex 1), tinted GREY by POWER in ModColorHandlers (`TRACK_GREYS`: 0x303030 at 0 to 0xE0E0E0 at 15). Grey because Track power is internal, not vanilla redstone; it fades by 1 per Track, so a pulse shows moving along a line.

### 9. Machine Trolley (passive cargo)

Non-directional Machine Family block. No active behavior. Pushed by Tracks, carries anything stuck to it. Does NOT conduct redstone.

### 9a. Machine Fluid Pipe (fluid vacuum/depositor)

Non-directional Machine Family block. Own BE subclass (MachineFluidPipeBlockEntity, BlockEntityType MACHINE_FLUID_PIPE). Extends AbstractMachineBlock. MachineType.FLUID_PIPE. Server ticker pulls fluid from adjacent sources, stores type (EMPTY, WATER, LAVA), and deposits into adjacent containers. IFluidHandler capability support. Bucket state syncing. Water+lava collision detection and handling.

### 9b. Machine Pipe (push-only item transport)

Non-directional Machine Family block. Own BE subclass (MachinePipeBlockEntity, BlockEntityType MACHINE_PIPE). FILLED BooleanProperty drives texture swap. Server ticker pushes 1 item per 8 ticks to adjacent containers via IItemHandler. Round-robin output relative to input face (forward, right, left, up, down). Redstone locks (powered = frozen). No pulling. Input face tracked via: player GUI = UP, hopper/automation = ThreadLocal pendingInputFace, pipe-to-pipe = onItemReceived(). Piston-safe: movedByPiston guard in onRemove, minimum cooldown after landing. Comparator output scales transit fullness (0-15).

### 9c. Machine Piston (omnidirectional push/pull)

Extends AbstractMachineBlock. MachineType.PISTON. Omnidirectional extend/retract controlled by redstone. BESR animation via MachinePistonArmRenderer. Server ticker manages arm progress state. BlockMoveHelper executes piston-style movement with proper push limit handling via PistonStructureResolverMixin.

### 9d. Machine Sticky Piston (omnidirectional with pull)

Extends MachinePistonBlock. MachineType.STICKY_PISTON. isSticky()=true. Pulls adjacent blocks on retract in addition to pushing on extend. Inherits animation and ticker from parent Machine Piston.

### 9e. Machine Brace (push limit reinforcement)

Extends AbstractMachineBlock. MachineType.BRACE. Structural reinforcement block. No active behavior. Increases push limit via dynamic push limit system: each Brace in toPush adds +6 to the base push limit (base 12, cap 60, max 8 Braces per push). Limit grows cascadingly during block gathering as Braces are discovered.

### 9f. Machine Repeater (omnidirectional redstone repeater)

Extends AbstractMachineBlock. MachineType.REPEATER. Omnidirectional redstone repeater. Receives signal on one face (input), outputs full-strength 15 on all other 5 faces with configurable delay (1-4 redstone ticks). POWERED + DELAY blockstates. 5-layer anti-feedback architecture (shouldSignal ThreadLocal, output-face early-return, protected inputFace, input-face-first tick, updatingPos ThreadLocal). Signal detection via MachineRedstoneHelper. getRedstoneOutput() returns 15 when POWERED. GUI buttons from `ideomod_numbered_buttons.png`. RepeaterDelayPacket via Forge SimpleChannel. Dual-cuboid model with tinted indicator overlay.

### 9g. Machine Toggle (omnidirectional flip-flop)

Extends AbstractMachineBlock. MachineType.TOGGLE. Edge-triggered flip-flop / T-latch. Receives signal on one face, toggles between ON and OFF on each rising edge. POWERED + INPUT_FACE (EnumProperty: none + 6 directions) blockstates. Specialization item: vanilla Lever. 4-layer anti-feedback (shouldSignal ThreadLocal, output-face early-return, updatingPos ThreadLocal, absorbing ThreadLocal). Edge detection via `inputWasLow` boolean in MachineFrameBlockEntity. Placement signal absorption prevents activation from pre-existing signals. INPUT_FACE blockstate drives model rotation (2 models, 14 variants). Signal detection via MachineRedstoneHelper. getRedstoneOutput() returns 15 when POWERED. Dual-cuboid model with tinted inner indicator (shared machine_indicator.png). Input face shows off texture while other 5 show on texture when powered.

**Machine Grabber (Slime/Honey):** [DONE]
Edge-triggered toggleable sticky block. Two variants: MachineType.SLIME_GRABBER (Slime Block item) and HONEY_GRABBER (Honey Block item). POWERED=false: Machine Family stickiness only. POWERED=true: additionally acts like corresponding vanilla sticky block (mutual exclusions apply). Rising-edge toggle via inputWasLow. No redstone output. Piston settlement window: 1-tick pistonSettling flag suppresses neighborChanged during push group placement, preventing spurious toggles. BESR renders inner block through transparent outer shell.

**Machine Transmitter:** [DONE]
Wireless redstone via Oddstone channels. SOURCE mode (external redstone → broadcast wirelessly) or RECEIVER mode (wireless → redstone output at sourceStrength-1). Channel = registry name of oddstone variant in spec slot. Cubic range from config. Immediate activation in neighborChanged. Anti-feedback: SOURCE blockstate, shouldSignal/updatingPos ThreadLocals, receiver self-read guard, source-first power-off ordering. Spec item change lifecycle via onSpecItemChanging/onSpecItemChanged. Level-aware indicator tint reads from BE, synced via onDataPacket. No GUI controls beyond the spec slot.

### 9h. Machine Transformer (signal level setter)

Extends AbstractMachineBlock. MachineType.TRANSFORMER. Omnidirectional signal level setter. Receives signal on one face, outputs user-configured strength (1-15) on all other 5 faces. POWERED + OUTPUT_LEVEL (IntegerProperty 1-15) blockstates. Specialization item: vanilla Comparator. 5-layer anti-feedback (same as Repeater). No delay (1-tick response). Signal detection via MachineRedstoneHelper. getRedstoneOutput() returns OUTPUT_LEVEL when POWERED. GUI shows 15 buttons in 2 rows (8+7). Fixed content height (89px, shared by all machines). TransformerLevelPacket via Forge SimpleChannel (packet ID 1). Dual-cuboid model with level-aware tinted indicator (POWER_COLORS[outputLevel] when powered). Single model for all 30 blockstate variants.

### 9i. Machine Timer (edge-triggered timed output)

Extends AbstractMachineBlock. MachineType.TIMER. Edge-triggered timed output. Receives signal on one face, emits full-strength 15 on all other 5 faces for a player-configured duration in ticks, then turns off. Requires fresh signal (drop + rise) after timer expires to re-trigger — same edge detection as Toggle (inputWasLow). POWERED blockstate. Specialization item: vanilla Clock. 5-layer anti-feedback (same as Repeater/Transformer). Self-rescheduling countdown: each game tick decrements timerTicksRemaining, turns off at 0. Signal detection via MachineRedstoneHelper. getRedstoneOutput() returns 15 when POWERED. GUI shows "Pulse Tick Duration" label + 16 buttons in 2 rows (8+8), values from config timer.durations array. TimerDurationPacket via Forge SimpleChannel (packet ID 2). Fixed content height (89px, shared by all machines). Dual-cuboid model with binary tinted indicator (dark maroon off, bright red on). MachineFrameBlockEntity fields: timerDuration (configurable output length), timerTicksRemaining (countdown). Placement signal absorption (same as Toggle).

### 9i-b. Machine Pulser (edge-triggered fixed-duration pulse)

Extends AbstractMachineBlock. MachineType.PULSER. Edge-triggered fixed-duration pulse emitter. Receives signal on one face, emits full-strength 15 on all other 5 faces for a fixed duration (config `pulser.pulse_duration`, default 2 game ticks), then turns off. Requires fresh signal (drop + rise) to fire again — same edge detection as Toggle/Timer (inputWasLow). POWERED blockstate. Specialization item: none (raw machine block). 5-layer anti-feedback (shouldSignal ThreadLocal, output-face early-return, protected inputFace, updatingPos ThreadLocal, absorbing ThreadLocal). Self-rescheduling countdown: each game tick decrements pulserTicksRemaining, turns off at 0. neighborChanged POWERED path only tracks arming (inputWasLow), does NOT affect countdown — pulse always runs to completion. Signal detection via MachineRedstoneHelper. getRedstoneOutput() returns 15 when POWERED. Dual-cuboid model with binary tinted indicator. MachineFrameBlockEntity fields: pulserTicksRemaining (countdown). Placement signal absorption (same as Toggle). Post-movement scan guard: tick() UNPOWERED path scans all faces when inputFace is null (recovery after piston movement where inputFace was cleared during clean expiry).

### 9j. Machine Engine (fuel-burning pulse clock)

Extends AbstractMachineBlock. MachineType.ENGINE. Fuel-burning pulse clock. Burns fuel items (Forge burn times) and emits periodic 2-game-tick (1 redstone tick) redstone pulses at a player-configurable interval. LIT + POWERED blockstates (LIT = sustained burning+powered state, POWERED = transient pulse output). Specialization item: vanilla Furnace. Own BE subclass: MachineEngineBlockEntity (BlockEntityType: MACHINE_ENGINE). Extends MachineFrameBlockEntity with fuel slot (slot 1), litTime/litDuration, pulseInterval, pulseCounter. Server-side ticker (not scheduled ticks — uses BlockEntityTicker). 5-layer anti-feedback (same as Repeater/Transformer/Timer). Signal detection via MachineRedstoneHelper. getRedstoneOutput() returns 15 when POWERED. Fuel freezes when unpowered (litTime paused, resumes on re-power). Force-clears POWERED+LIT when unpowered (safety net for high-rate stale state). GUI shows fuel slot + flame indicator (vanilla furnace textures) + "Pulse Interval" label + 8 buttons in 1 row, values from config engine.durations array (increments of 5). EnginePulseIntervalPacket via Forge SimpleChannel. Comparator output scales fuel slot fullness (0-15). Particles: flame on all 6 faces + campfire cosy smoke from top (only when LIT). Piston-safe: movedByPiston + isSwapping guards in onRemove. ejectSecondaryItems on specialization removal. IItemHandler capability for hopper/pipe automation. WorldlyContainer: fuel slot (1) external, specialization slot (0) GUI-only. Dual-cuboid model with binary tinted indicator (dark maroon off, bright red on).

### 10. Hollowstone (transparent shell block)

Registry ID: `hollow_stone`. Class: HollowstoneBlock (implements EntityBlock). Block entity: HollowstoneBlockEntity.

1. **Transparent shell**: BooleanProperty FILLED (default false). `noOcclusion()` in properties. `cutoutMipped` render layer for transparent center pixels in filled texture.
2. **Fill interaction**: Right-click with a block item fills the shell (sets FILLED=true, stores inner block in BE). Sound + hand swing feedback. Refused: air, blocks with a block entity, and anything whose collision shape isn't a full cube (buttons, levers, torches, slabs; fixed 2026-10-22).
3. **Mining behavior**: Drops 5-7 Hollowstone Shards (fortune-affected, explosion-safe). Silk touch drops the block itself. Filled variant: mining leaves the inner block in-world via coordinated `playerWillDestroy()` + `onRemove()` using `markForInnerBlockPlacement()` flag.
4. **Rendering**: HollowstoneRenderer BESR. Simplified FramedBlockRenderer — no FACING transfer, no property delegation. Renders inner block at 0.998 scale with best-of-6-neighbors lighting. Only renders when `hasInnerBlock()` is true.
5. **Crafting**: 9 Hollowstone Shards → 1 Hollowstone (shaped recipe).
6. **Loot table**: `alternatives` with silk touch → block, else → 5-7 shards with fortune `ore_drops` and `explosion_decay`.

### 10a. World Generation

**Oddstone Ore**: JSON data-driven via configured/placed features + Forge biome modifiers. Configured feature `oddstone_ore` uses `minecraft:ore` type, size 7, `discard_chance_on_air_exposure: 0.0`, targets `stone_ore_replaceables` and `deepslate_ore_replaceables`. Two placed features: `oddstone_ore_deep` (5 attempts, uniform Y-64 to Y0) and `oddstone_ore_upper` (3 attempts, uniform Y0 to Y63). Total: 8 attempts/chunk at size 7 — between lapis and redstone rarity. Biome modifiers add to `#minecraft:is_overworld`, step `underground_ores`.

**Hollowstone Lava Formation**: Custom `Feature<NoneFeatureConfiguration>` registered as `ideomod:hollowstone_lava_formation`. Algorithm: scan radius 8 for lava source → find shore blocks (`BASE_STONE_OVERWORLD` tag) → place 2-3 Oddstone → spread Hollowstone into adjacent lava sources (2 per Oddstone + 50% secondary spread). Placed feature: 3 attempts, uniform Y-64 to Y63. Biome modifier: `#minecraft:is_overworld`, step `underground_decoration`.

**OddstoneBlock Feature 6 — Lava Transformation**: `neighborChanged()` and `onPlace()` call `convertAdjacentLava()` — converts adjacent lava SOURCE blocks (not flowing) to Hollowstone with sound + steam particles.

**Ore-Only Debug World Type**: Custom `OreOnlyChunkGenerator` extending `NoiseBasedChunkGenerator`. No-ops `buildSurface` (no grass/dirt/sand). Overrides `applyBiomeDecoration` to run vanilla decoration (placing ores), then strips all non-ore blocks using `forge:ores` tag + explicit oddstone/hollowstone checks. Codec registered via `DeferredRegister<Codec<? extends ChunkGenerator>>` against `Registries.CHUNK_GENERATOR`. World preset at `data/ideomod/worldgen/world_preset/ore_only.json`. Added to `data/minecraft/tags/worldgen/world_preset/normal.json` so it appears in the World Type button cycle. Translation key: `generator.ideomod.ore_only` → "Ore Only (Debug)".

### 11. Odd Slime Block

Directional slime variant. `extends DirectionalBlock`. FACING property set on placement (piston-style). Only sticky when pushed in FACING direction (directional rule in Layer 3). Mutual exclusion with vanilla slime and honey via canStickTo.

### 12. Judgement Effect

Rare event (1/200) when player kills a passive mob. All non-hostile mobs within 32 blocks freeze (`setNoAi(true)`), stare at player for 5 seconds (manual yaw/pitch per tick), then unfreeze. Triggered by LivingDeathEvent. Also available via `/judgement` command (op level 2).

---

## REGISTRIES

**ModBlocks**: IDEOBLOCK, DRILL, ODDSTONE, MACHINE_BLOCK, ODD_SLIME, ODDSTONE_PORTAL, ODDSTONE_REPULSER, ODDSTONE_ATTRACTOR, MACHINE_TRACK, MACHINE_TROLLEY, MACHINE_CONDUCTOR, MACHINE_PIPE, MACHINE_FLUID_PIPE, MACHINE_PISTON, MACHINE_STICKY_PISTON, MACHINE_PISTON_HEAD, MACHINE_BRACE, MACHINE_REPEATER, MACHINE_TOGGLE, MACHINE_TRANSFORMER, IRON_FRAME, COPPER_FRAME, IRON_FRAMED_BLOCK, COPPER_FRAMED_BLOCK, HOLLOW_STONE, + oddstone variants (cobbled, bricks, walls, stairs, slabs)

**ModItems**: BlockItems for all standard blocks EXCEPT ODDSTONE_PORTAL. Includes MACHINE_CONDUCTOR, MACHINE_PIPE, MACHINE_FLUID_PIPE, MACHINE_PISTON, MACHINE_STICKY_PISTON, MACHINE_BRACE, MACHINE_REPEATER, MACHINE_TOGGLE, and MACHINE_TRANSFORMER. FramedBlockItem instances for IRON_FRAMED_BLOCK and COPPER_FRAMED_BLOCK (custom BlockItem with dynamic name + NBT). Plus STRUCTURE_DEBUG_STICK (standalone Item, stacksTo 1), HOLLOW_STONE (BlockItem), HOLLOW_STONE_SHARD (plain Item), COPPER_NUGGET (plain Item; vanilla 1.20.1 has none; model reuses `minecraft:item/iron_nugget`, tinted copper in `ModColorHandlers.registerItemColors`; tagged `forge:nuggets/copper` and `forge:nuggets` in `data/forge/tags/items/`, and the copper recipes take the tag so other mods' nuggets work).

**ModFeatures**: HOLLOWSTONE_LAVA_FORMATION (Feature), ORE_ONLY (Codec<? extends ChunkGenerator>).

**ModTags**: PORTAL_CORNER_BLOCKS (14 mineral blocks), FRAMEABLE_BLOCKS (~310+ blocks whitelist for framing), FURNACE_ARRAY_FRAME (18 stone-family blocks), IMMOVABLE_WHEN_FRAMED (blocks that should be immovable when wrapped in frames — obsidian, crying_obsidian, reinforced_deepslate). Note: FurnaceArrayDefinition still uses inline tag reference — should be updated to use ModTags.FURNACE_ARRAY_FRAME.

**ModCreativeTabs**: Two tabs. `ideomod_tab` (icon IDEOBLOCK, all standard items). `framed_blocks_tab` (icon Iron Framed Stone, dynamically populated from cached frameable blocks list built on first tab open via FrameabilityHelper).

---

## CONVENTIONS & PATTERNS

**Block registration**: DeferredRegister, properties set in ModBlocks.java supplier lambdas.

**Directional blocks**: extend DirectionalBlock, FACING property, piston-style placement (`getNearestLookingDirection().getOpposite()`), rotate/mirror overrides.

**Redstone blocks**: POWERED property, rising-edge detection in `neighborChanged()`, `level.setBlock(pos, state.setValue(POWERED, isPowered), 3)`.

**Block events (for client-server sync)**: `level.blockEvent()` in neighborChanged, actual logic in `triggerEvent()` which runs on BOTH sides. Encode direction as `facing.get3DDataValue()` in param.

**ThreadLocal coordination**: Used when mixin needs runtime context from calling code (RangedPistonFlags pattern: set before, read in mixin, clear in finally).

**Stickiness rule pattern**: `canStickTo` = permissive (allow both directions), `StickinessRules` = restrictive (block specific grabbers). Never express one-way behavior in canStickTo alone.

**Frame activation pattern**: All lifecycle hooks call `FrameActivation.markDirty()`. End-of-tick batching via `TickHandler` (registered on Forge event bus). `flushIfDirty()` for piston resolver. Wipe-and-rebuild per unique network, no incremental logic. Machine Blocks are sources, frames are passive conductors.

**Multiblock definitions**: Extend StructureDefinition, register with StructureManager in commonSetup(). Use block tags for frame/component blocks. Scanner normalizes bounds and core positions.

**SavedData pattern**: Per-dimension via `level.getDataStorage().computeIfAbsent()`. NBT serialization. Static factory `get(ServerLevel)`. Used by both OddstonePortalSavedData and StructureManager.

---

## KNOWN ISSUES & TECHNICAL DEBT
1. **Item model translucency** -- Translucent inner blocks (glass, slime) render opaque in inventory item display. Quad-compositing approach can't handle separate render passes. Purely cosmetic; in-world rendering is correct.
2. ~~**Conductor strong power visible to dust through solid blocks**~~ **FIXED** (2026-10-22). `RedStoneWireBlockMixin` sets `MachineConductorBlock.setDustScanning()` around dust's `calculateTargetStrength()`; Conductor strong power is hidden from dust only, as vanilla dust hides its own.
3. ~~**Framed redstone blocks don't emit power on frame activation**~~ **VERIFIED NOT A BUG** (2026-10-22). FramedBlock delegates signal to the inner block regardless of ACTIVE; GameTest `framedRedstonePowersOnPlaceAndActivation` confirms.
4. **Toggle indicator tint uniform on all faces** -- Cosmetic. Vanilla block color handlers don't receive face direction, so all 6 inner indicator faces get the same tint. Input face distinguished by outer texture swap only. Fix options: BESR, split-element model, or opaque off-texture window. Files: MachineToggleBlock.java, ModColorHandlers.java.
5. ~~**Pulse jump on piston movement of signal chains**~~ **FIXED** (2026-10-22). The 2026-03-31d/e stagger was not enough: GameTest showed 5 Pulsers firing in one tick when a ring moved at one phase. `onMovementRestored` now re-schedules only powered blocks and blocks that had a pending tick (saved as `MovementPendingTick`).

Written by Claude, directed and guided by The Ideomancer.
