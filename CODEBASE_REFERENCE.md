# IDEOMOD CODEBASE REFERENCE
*Quick-reference documentation for the Ideomod codebase.*
*Last updated: 2026-03-13 (Post-audit doc cleanup: expanded stickiness matrix, added EnderPearlPortalHandler, added Known Issues #12-#13, updated feature counts, noted FURNACE_ARRAY_FRAME tag gap.)*

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

---

## PACKAGE STRUCTURE

```
com.ideomancer.ideomod
  |-- Ideomod.java              Main mod class. Registers buses, stickiness rules,
  |                              structure definitions, StructureManager event handler.
  |-- block/
  |     |-- OddstoneBlock.java           6-feature core block (see Oddstone section)
  |     |-- OddSlimeBlock.java           Directional slime (sticky only in FACING dir)
  |     |-- DrillBlock.java              3x3 redstone miner [ON HOLD]
  |     |-- frame/
  |     |     |-- IFrameBlock.java             Interface: getFrameMaterial(). Frame Family marker.
  |     |     |                                Static isActive(BlockState) helper.
  |     |     |-- FrameMaterial.java           Data-carrying enum: IRON, COPPER. displayName,
  |     |     |                                registryPrefix, item registry, miningTimeModifier.
  |     |     |                                fromVanillaBlock(), toConductorValue(),
  |     |     |                                fromConductorValue(). Single source for material lookups.
  |     |     |-- FrameBlock.java              Base frame block. BooleanProperty ACTIVE + WATERLOGGED.
  |     |     |                                Implements IFrameBlock + SimpleWaterloggedBlock.
  |     |     |                                Passive conductor. canStickTo: same-material IFrameBlock,
  |     |     |                                Machine Family, slime/honey/odd_slime permissive,
  |     |     |                                FramedBlock(INNER_STICKY>0) permissive. Cross-material
  |     |     |                                IFrameBlock falls through (no short-circuit).
  |     |     |-- FrameActivation.java         Static utility: signal-based activation with strength
  |     |     |                                decay (0-15) and anti-signal suppression. Tracks
  |     |     |                                vanilla metal activation. Single BFS with material flow.
  |     |     |-- FrameabilityHelper.java      Tag-driven whitelist for frameable blocks (#ideomod:frameable_blocks)
  |     |     |-- FramedBlock.java             IFrameBlock + EntityBlock proxy for inner block
  |     |     |-- FramedBlockEntity.java       Stores inner block ResourceLocation. IPistonMovable.
  |     |     |-- FramedBlockItem.java         Dynamic getName(), NBT stack creation
  |     |     |-- FramedBlockRecipe.java       CustomRecipe: frame + block = framed block with NBT
  |     |     \-- FramedBlockLoot.java         Helper for manual drop creation
  |     |-- machine/
  |     |     |-- IMachineFamily.java          Interface: getMachineType() + canMachineFamilyStickTo(type, state).
  |     |     |-- MachineType.java             Enum: FRAME, TRACK, TROLLEY, etc. canStickToMachineType().
  |     |     |-- MachineSwapHelper.java       Swap utility: item<->block registry, checkAndSwap(),
  |     |     |                                createPreloadedStack(), createDrop(), createCloneStack().
  |     |     |-- PositionBoundContainer.java  Position-bound Container. Auto-resolves to current BE.
  |     |     |-- MachineFrameBlock.java       MachineType.FRAME. Universal connector. EntityBlock + GUI.
  |     |     |-- MachineFrameBlockEntity.java Shared BE for all machine blocks. Single slot + IPistonMovable.
  |     |     |-- MachineFrameMenu.java        Position-bound menu. PositionBoundContainer on both sides.
  |     |     |-- MachineTrackBlock.java       MachineType.TRACK. EntityBlock + GUI + Trolley push.
  |     |     |-- MachineTrolleyBlock.java     MachineType.TROLLEY. EntityBlock + GUI + passive cargo.
  |     |     \-- MachineConductorBlock.java   MachineType.CONDUCTOR. Solid-block redstone wire.
  |     |                                      Input/output face model. EntityBlock + GUI.
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
  |     |     \-- MovementScanHelper.java      Shared algorithms: isOddstoneOrFramedOddstone(),
  |     |                                      scanSympathetic(), scanStickyGroup(). Single source
  |     |                                      of truth for sticky group scanning. Vanilla metal
  |     |                                      awareness via isVanillaMetalConnection().
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
  |-- client/
  |     |-- ModColorHandlers.java        Block/item color tinting (Machine Block, Portal)
  |     |-- FramedBlockDebugOverlay.java F3 debug overlay for inner block info
  |     |-- gui/
  |     |     \-- MachineFrameScreen.java      GUI screen for Machine Frame
  |     \-- render/
  |           |-- FramedBlockRenderer.java    BESR for framed blocks (renderSingleBlock with facing transfer)
  |           |-- FramedBlockItemModel.java   Custom BakedModel for framed block items (composites inner + frame quads)
  |           |-- FramedBlockRenderTypes.java Custom RenderType for framed block overlays
  |           \-- MovingFrameData.java        Client-side cache for FrameTransitInfo during piston transit
  |-- config/
  |     \-- OddstoneConfig.java          JSON config: sympathetic radius + mob list
  |-- effect/
  |     \-- JudgementEffect.java         Rare mob-stare event on passive mob kill
  |-- mixin/
  |     |-- PistonStructureResolverMixin.java  Enforces stickiness rules + ranged redirect + immovable inner.
  |     |                                      Call-pair toggle for backward walk grab immunity.
  |     |                                      Vanilla metal conductor scanning (bidirectional).
  |     |-- PistonBaseBlockMixin.java          IPistonMovable BE movement + data save + Oddstone pull
  |     |                                      immunity + immovable inner. Uses MovementScanHelper.
  |     |-- PistonMovingBlockEntityMixin.java  Restores IPistonMovable data after animation
  |     |-- EntityMovementMixin.java           Entity speed/jump factor for framed blocks.
  |     |                                      Generic ideomod$getFramedFactor() helper.
  |     |-- AbstractFurnaceBlockEntityMixin.java  Furnace array speed boost (50%)
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
        |-- ModCreativeTabs.java         Creative tab with all mod items
        |-- ModCommands.java             /judgement command (Forge event bus)
        |-- ModRecipeSerializers.java    DeferredRegister<RecipeSerializer<?>>
        |-- ModBlockEntities.java        DeferredRegister<BlockEntityType<?>>
        |-- ModMenuTypes.java            DeferredRegister<MenuType<?>>
        \-- ModTags.java                 PORTAL_CORNER_BLOCKS, REDSTONE_COMPONENTS,
                                         FRAMEABLE_BLOCKS, FURNACE_ARRAY_FRAME tags.
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

**Stickiness Matrix** (rows = grabber, cols = target). Machine Family uses MachineType-aware stickiness. "Machine Frame" = MachineType.FRAME (universal connector). "Machine Spec" = any specialization (Track, Trolley, Conductor, etc.). "Frame Fam" = all IFrameBlock implementations. "Vanilla Metal" = iron_block, copper_block when activated. See STICKINESS_SYSTEM.md for full pair-by-pair implementation details.

|              | Slime | Honey | Odd Slime | Machine Frame | Same-Type Spec | Diff-Type Spec | Frame Fam (active) | Frame Fam (inactive) | Vanilla Metal (activated) | Oddstone | Normal |
|:-------------|:-----:|:-----:|:---------:|:-------------:|:--------------:|:--------------:|:------------------:|:--------------------:|:-------------------------:|:--------:|:------:|
| Slime        |  YES  |  no*  |    no*    |      YES      |      YES       |      YES       |        YES         |         YES          |            YES            |    no    |  YES   |
| Honey        |  no*  |  YES  |    no*    |      YES      |      YES       |      YES       |        YES         |         YES          |            YES            |    no    |  YES   |
| Odd Slime    |  no*  |  no*  |  YES (d)  |    YES (d)    |    YES (d)     |    YES (d)     |      YES (d)       |       YES (d)        |          YES (d)          |    no    | YES (d)|
| Machine Frame|  no   |  no   |    no     |      YES      |      YES       |      YES       |        YES         |         no           |            no             |    no    |   no   |
| Machine Spec |  no   |  no   |    no     |      YES      |      YES       |       no       |        YES         |         no           |            no             |    no    |   no   |
| Frame Fam (active)|  no   |  no   |    no     |      YES      |      YES       |      YES       |      YES (m)       |         no           |          YES (m)          |    no    |   no   |
| Vanilla Metal (act.)|  no   |  no   |    no     |      YES      |      YES       |      YES       |      YES (m)       |         no           |          YES (m)          |    no    |   no   |
| Framed Slime (act.)|  YES  |  no*  |    no*    |      YES      |      YES       |      YES       |      YES (m)       |         no           |          YES (m)          |    no    |  YES   |
| Framed Honey (act.)|  no*  |  YES  |    no*    |      YES      |      YES       |      YES       |      YES (m)       |         no           |          YES (m)          |    no    |  YES   |
| Framed OddSlime (act.)|  no*  |  no*  |  YES (d)  |      YES      |      YES       |      YES       |      YES (m)       |         no           |          YES (m)          |    no    | YES (d)|
| Framed Oddstone (act.)|  YES  |  YES  |    YES    |      YES      |      YES       |      YES       |      YES (m)       |         no           |          YES (m)          |   YES    |   no   |
| Framed Oddstone (inact.)|  YES  |  YES  |    YES    |       no      |       no       |       no       |        no          |         no           |            no             |   YES    |   no   |
| Framed Sticky (inact.)|  (i)  |  (i)  |    (i)    |       no      |       no       |       no       |        no          |         no           |            no             |    no    |  (i)   |
| Oddstone     |  YES  |  YES  |    YES    |       no      |       no       |       no       |        no          |         no           |            no             |   YES    |   no   |

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

**PistonBaseBlockMixin**: Three features. (1) isPushable HEAD inject returns true for IPistonMovable block entities; also handles Oddstone pull immunity via position-specific ThreadLocal<BlockPos> — during retraction, only the directly pulled oddstone is immune, not oddstone elsewhere in group. (2) moveBlocks HEAD inject saves data. Extension uses resolver. Retraction bypasses resolver (it fails for our blocks), scans pull position directly + BFS for sticky group + sympathetic scan for distant oddstone via scanSympathetic().

**RangedPistonUtil**: Scanning + dispatch for Repulser/Attractor. `scanForNearest` / `scanForFarthest` walk up to 12 blocks. Delegates movement to BlockMoveHelper.

**AbstractRangedPistonBlock**: Shared base for Repulser/Attractor. Handles FACING/POWERED, rising edge, block events, rotation. Subclasses implement `fire()` only (~15 lines each).

**RangedPistonFlags** (ThreadLocal): Redirects the resolver's startPos from (devicePos + pushDir) to the actual target block. Set before `new PistonStructureResolver()`, cleared in finally block.

**MachineTrackBlock**: Uses BlockMoveHelper directly (no scanning). Internal POWER blockstate (0-15) for Track-to-Track chaining only -- NOT exposed to vanilla redstone (isSignalSource=false, getSignal=0, getDirectSignal=0). Reads input from other Tracks via direct blockstate access and from tagged REDSTONE_COMPONENTS via vanilla signal query. Signal direction detection checks actual sources (conduit blocks with higher POWER, tagged redstone components) rather than vanilla `hasSignal()`. Pushes ALL adjacent Trolleys in the computed direction. Block event encodes pushDir + trolleyFace via bit packing. Re-entrancy guard uses ThreadLocal<Boolean>.

**MachineTrolleyBlock**: Passive cargo. No active behavior, just Machine Family stickiness.

**MachineConductorBlock**: Solid-block redstone wire. POWER blockstate (0-15) exposed to vanilla redstone. Input/output face model: receives on one face (strongest signal), outputs on other 5. Source blocks (levers etc.) = no decay. Wire blocks (dust, other Conductors) = -1 decay. getSignal and getDirectSignal return 0 on input face via `direction == inputFace.getOpposite()` check. inputFace stored in shared MachineFrameBlockEntity. Visual: two-layer model with tinted indicator overlay via ModColorHandlers (tintIndex 1, squared red gradient). Known issues: dust sees strong power through solid blocks (#12), base texture renders black (#13).

**Filter predicates**: `ODDSTONE_ONLY` (current), `ANY_MOVABLE` (future Arcane variants).

### 3. Portal System

**Components**: OddstonePortalHandler (lifecycle) -> OddstonePortalFrameDetector (scanning) -> OddstonePortalSavedData (persistence) -> OddstonePortalBlock (teleportation). **PortalGeometry** record (`block/portal/PortalGeometry.java`) consolidates portal dimension scanning and frame iteration that was previously duplicated across 3+ files. Provides `detect()`, `forEachOddstonePosition()`, `parseAxis()`, `getFrameCorners()`.

**Frame rules**: Rectangular frame of Oddstone blocks with corner blocks (crafted mineral blocks from PORTAL_CORNER_BLOCKS tag). Interior 3-19 blocks wide/tall. ALL Oddstone in frame must be redstone-powered.

**Addressing**: 4 corner block types define address. 14 valid corner types = 38,416 unique addresses. Address is canonicalized (front vs back comparison) so both sides of a frame give the same address.

**Linking**: Max 2 portals per address. First lights unlinked, second auto-links to first. Destroying one unlinks the other.

**Teleportation**: Linked portals preserve relative position (fractional offset). All teleports apply 180-degree yaw rotation so player exits facing away from destination portal (doorway effect). Cross-axis portals (X vs Z) additionally rotate 90 degrees to align with destination axis. Unlinked portals scatter-teleport 200-1000 blocks + 5 hearts damage.

**Deactivation strategy**: Full frame re-validation via `validateOrDeactivate()`. When any frame Oddstone gets neighborChanged, navigates from portal block to frame, checks all corners + all Oddstone powered. Only deactivates if validation fails. Prevents false triggers from unrelated neighbor updates. Immediate deactivation via `onBlockRemoved()` when a frame Oddstone is physically broken.

**Break effects**: `PortalUtils.clearPortalBlocks()` uses `level.destroyBlock(pos, false)` for proper break particles and sound on all deactivation paths.

**Registration**: `OddstonePortalSavedData` (per-dimension SavedData). Keyed by frame corner position. Serialized to NBT.

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
3. **Sympathetic movement**: Handled by PistonStructureResolverMixin. BFS within configurable radius (default 5). Chains count toward 12-block limit.
4. **One-way stickiness**: Grabs all slime types (real+framed) + other Oddstone (real+framed). Cannot be grabbed back (grab immunity rules). canStickTo recognizes FramedBlock targets via INNER_STICKY check.
5. **Portal frame**: Delegates to OddstonePortalHandler on neighborChanged/onRemove.
6. **Pull immunity**: Cannot be directly pulled by sticky pistons. PistonBaseBlockMixin position-specific check. Oddstone dragged by own stickiness (e.g., stuck to slime being pulled) still moves correctly.

### 6. Machine Frame (universal connector + Machine Family base)

Registry ID: `machine_block`. Class: MachineFrameBlock. Implements **IMachineFamily** with **MachineType.FRAME** (as do MachineTrackBlock with TRACK and MachineTrolleyBlock with TROLLEY).

1. **Universal stickiness**: MachineType.FRAME sticks to ALL machine types. canStickTo delegates to `IMachineFamily.canMachineFamilyStickTo(MachineType.FRAME, other)`. Permissive to slime/honey/odd_slime (grab immunity handles restriction).
2. **Block entity with GUI**: All machine blocks share MachineFrameBlockEntity (single item slot). PositionBoundContainer keeps GUI open across swaps. MachineSwapHelper handles block replacement when slot changes.
3. **Anti-signal emission**: Only Machine Frame suppresses frame networks (powered = anti-signal). All other specializations always activate.
4. **Drop preservation**: getDrops() override -- conditional BlockEntityTag only when slot has content. getCloneItemStack() uses registry-based createCloneStack() (client-safe).

**IMachineFamily interface** (`block/machine/IMachineFamily.java`): Provides `getMachineType()` and static `canMachineFamilyStickTo(MachineType, BlockState)`. Type check: FRAME sticks to everything, specializations stick to same-type + FRAME. Frame Family requires active. Slime types permissive.

**MachineSwapHelper** (`block/machine/MachineSwapHelper.java`): Central utility. Bidirectional registry (Powered Rail <-> Track, Minecart <-> Trolley). `checkAndSwap()` with isSwapping ThreadLocal. `createPreloadedStack()` for creative tab. `createDrop()` conditional NBT. `createCloneStack()` registry-based middle-click.

**PositionBoundContainer** (`block/machine/PositionBoundContainer.java`): Container backed by Level + BlockPos. Every method call resolves to current BE at position. Menu uses this on both server and client. Survives block swaps without close/reopen.

**Static helpers** (on MachineFrameBlock): `isRedstoneConduit(Block)` -- MachineTrackBlock only.

**Frame activation**: onPlace/onRemove/neighborChanged all call `FrameActivation.recalculate(level, pos)` to trigger frame network wipe-and-rebuild.

### 7. Frame Blocks (activation-based stickiness)

Registry IDs: `iron_frame`, `copper_frame`. Class: FrameBlock (implements IFrameBlock, SimpleWaterloggedBlock).

1. **Activation-based stickiness**: BooleanProperty ACTIVE (default false). `isStickyBlock()` returns ACTIVE value. Frames are passive -- all lifecycle hooks call `FrameActivation.recalculate()`. Machine Blocks are the signal source. Signal strength 15, decays by 1 per conductor. Max range 15 blocks.
2. **Material exclusivity**: canStickTo returns true for same-material IFrameBlock **only when neighbor is also active** (`IFrameBlock.isActive(other)`). Cross-material IFrameBlock falls through (does not short-circuit to false) so inner stickiness permissive entries can match. Also permissive to: Machine Family, SlimeBlock, HoneyBlock, OddSlimeBlock, FramedBlock with INNER_STICKY>0.
3. **Cross-frame mediation**: canStickTo recognizes FramedBlock neighbors with INNER_CONDUCTOR matching this frame's material. E.g., iron frame sticks to copper-framed iron block.
4. **Vanilla metal conductors**: canStickTo returns true for same-material vanilla metal blocks (iron_block, copper_block).
5. **Waterlogging**: BooleanProperty WATERLOGGED (default false). Auto-waterlogs when placed in water. updateShape() schedules fluid ticks.
6. **Grab immunity**: Rules 1-4 (consolidated) prevent specific grabber->target pairs. Rule 4 has carve-outs for matching inner types.

**FrameActivation algorithm** (redstone-style signal strength with anti-signal suppression):
1. DISCOVER: BFS from change point through all conductors (IFrameBlock + vanilla metals, any material/state)
2. WIPE: Set all IFrameBlocks to ACTIVE=false, clear vanilla metal activation set
3. FIND SOURCES: Check all network blocks + change position for adjacent Machine Blocks
4. PROPAGATE ACTIVATION: BFS from activation sources, strength 15 decaying by 1. Machine Frames only activate when unpowered; all other Machine Family blocks always activate regardless of redstone state. Each entry carries available materials + strength. Mediators (framed blocks with cross-material inner conductor) pass ALL materials.
5. PROPAGATE ANTI-SIGNAL: BFS from powered Machine Frames ONLY (no other Machine Family blocks), strength = redstone input level, decaying by 1. Binary override: any block reached = deactivated.
6. APPLY: Block activates only if activation > 0 AND anti-signal == 0. IFrameBlocks get ACTIVE=true, vanilla metals added to activatedVanillaMetals set.

Max range: 15 blocks from source. Public queries: `isVanillaMetalActivated(pos)`, `isActivatedConductor(state, pos)`, `isActive(state)`.

Called by: MachineFrameBlock.onPlace/onRemove/neighborChanged, FrameBlock.onPlace/onRemove/neighborChanged. Redstone input changes trigger via neighborChanged automatically. Single entry point: `FrameActivation.recalculate(level, pos)`.

**Rendering**: RenderType.cutout() registered in Ideomod.clientSetup(). noOcclusion() in block properties. Transparent interior pixels render correctly.


### 7b. Framed Blocks (dynamic block wrapping)

Registry IDs: `iron_framed_block`, `copper_framed_block`. Class: FramedBlock (implements IFrameBlock + EntityBlock).

1. **Inner block proxy**: FramedBlockEntity stores ResourceLocation. ~19 methods delegate to inner block's defaultBlockState(). Mining speed, sound, friction, light, redstone, pathfinding, stepOn, fallOn, updateEntityAfterFallOn, entityInside all preserved. Speed/jump factor via EntityMovementMixin. Push reaction (immovable inners) via PistonBaseBlockMixin Feature 4 + Resolver Feature 5.
2. **Two-layer rendering**: BESR (FramedBlockRenderer) uses BlockRenderDispatcher.renderSingleBlock() at 0.998 scale for epsilon inset. Handles multi-element models (slime), translucency (glass), tinting (grass), directional textures. JSON model draws frame overlay on top. Brightest-of-neighbors lighting.
3. **FACING property** (DirectionProperty): Set from player look direction on placement. Transferred to inner block's orientation property during rendering.
4. **INNER_STICKY property** (IntegerProperty 0-4): Maps to `StickyType` enum values (NONE=0, SLIME=1, HONEY=2, ODD_SLIME=3, ODDSTONE=4). Synced from inner block type by FramedBlockEntity.updateBlockstateFromInner().
5. **INNER_CONDUCTOR property** (IntegerProperty 0-2): Exposes inner block's conductor material at blockstate level (0=none, 1=iron, 2=copper). Synced by FramedBlockEntity.updateBlockstateFromInner() via FrameMaterial.fromVanillaBlock() + toConductorValue(). Enables cross-frame mediation in canStickTo() and activation without BE access.
6. **INNER_LIGHT property** (IntegerProperty 0-15): Inner block's light emission level. Read by lightLevel predicate in ModBlocks.java. Pre-populated in getStateForPlacement() from item NBT so the lighting engine detects emission on the first setBlock. Also synced by updateBlockstateFromInner() for piston restores and chunk loads.
7. **Activation-based stickiness**: Same IFrameBlock + FrameActivation system as Frame Blocks. Material-matched (iron<->iron, copper<->copper). canStickTo requires neighbor IFrameBlock to be active. isStickyBlock() returns true when ACTIVE || INNER_STICKY > 0.
7. **Cross-frame mediation**: When INNER_CONDUCTOR is set to a different material than the frame, the block is a MEDIATOR. In activation: passes ALL materials (universal bridge). In stickiness: canStickTo matches both frame material AND inner conductor material.
8. **Inner sticky behavior**: Framed blocks with sticky inners preserve native stickiness. canStickTo() combines frame rules (ACTIVE-gated) + inner sticky rules (INNER_STICKY-gated with vanilla exclusions).
9. **Framed Odd Slime directional**: Inner stickiness blocked in non-FACING push directions. Frame stickiness still works via second-chance mixin check.
10. **noOcclusion()**: Block properties include noOcclusion() to prevent neighbor face culling.
11. **Crafting**: FramedBlockRecipe (CustomRecipe): 1 frame + 1 frameable block = framed block with BlockEntityTag NBT.
12. **Dynamic creative tab**: FRAMED_BLOCKS_TAB iterates all registered blocks, checks FrameabilityHelper.isFrameable(), creates variants.
13. **F3 debug**: FramedBlockDebugOverlay shows inner block ID on right-side F3 panel.

Blockstate count per framed block type: 2 (ACTIVE) x 6 (FACING) x 5 (INNER_STICKY) x 3 (INNER_CONDUCTOR) x 16 (INNER_LIGHT) = 2880 states.

**FrameabilityHelper**: Tag-driven whitelist. Checks `#ideomod:frameable_blocks` tag (~310 vanilla blocks + 2 ideomod blocks), NOT FramedBlock, NOT IFrameBlock. Infested blocks excluded.

**FramedBlockItem**: Custom BlockItem. Dynamic getName() using `frameMaterial.getDisplayName()`. Static createStack() delegates to `FramedBlockEntity.setInnerBlockIdOnStack()` for NBT setup. `getInnerBlockId()` delegates to `FramedBlockEntity.getInnerBlockIdFromStack()`.

**MovingFrameData**: Client-side ConcurrentHashMap<BlockPos, FrameTransitInfo>. FrameTransitInfo record contains innerBlockId, facing, innerSticky. Populated by PistonBaseBlockClientMixin before vanilla destroys block entities. Read by transit renderer mixin.

### 8. Machine Track (isolated redstone + Trolley push)

Non-directional Machine Family block. Internal POWER blockstate (0-15, decay by 1) for Track-to-Track chaining only. NOT exposed to vanilla redstone system (isSignalSource=false, getSignal=0, getDirectSignal=0). Reads from other Tracks via direct blockstate access and tagged REDSTONE_COMPONENTS via vanilla signal query. On rising edge, pushes adjacent Trolleys opposite to signal source direction. Uses BlockMoveHelper for piston-style animated movement.

### 9. Machine Trolley (passive cargo)

Non-directional Machine Family block. No active behavior. Pushed by Tracks, carries anything stuck to it. Does NOT conduct redstone.

### 10. Odd Slime Block

Directional slime variant. `extends DirectionalBlock`. FACING property set on placement (piston-style). Only sticky when pushed in FACING direction (directional rule in Layer 3). Mutual exclusion with vanilla slime and honey via canStickTo.

### 11. Judgement Effect

Rare event (1/200) when player kills a passive mob. All non-hostile mobs within 32 blocks freeze (`setNoAi(true)`), stare at player for 5 seconds (manual yaw/pitch per tick), then unfreeze. Triggered by LivingDeathEvent. Also available via `/judgement` command (op level 2).

---

## REGISTRIES

**ModBlocks**: IDEOBLOCK, DRILL, ODDSTONE, MACHINE_BLOCK, ODD_SLIME, ODDSTONE_PORTAL, ODDSTONE_REPULSER, ODDSTONE_ATTRACTOR, MACHINE_TRACK, MACHINE_TROLLEY, IRON_FRAME, COPPER_FRAME, IRON_FRAMED_BLOCK, COPPER_FRAMED_BLOCK

**ModItems**: BlockItems for all standard blocks EXCEPT ODDSTONE_PORTAL. FramedBlockItem instances for IRON_FRAMED_BLOCK and COPPER_FRAMED_BLOCK (custom BlockItem with dynamic name + NBT). Plus STRUCTURE_DEBUG_STICK (standalone Item, stacksTo 1).

**ModTags**: PORTAL_CORNER_BLOCKS (14 mineral blocks), REDSTONE_COMPONENTS (redstone devices whitelist), FRAMEABLE_BLOCKS (~310+ blocks whitelist for framing), FURNACE_ARRAY_FRAME (18 stone-family blocks). Note: FurnaceArrayDefinition still uses inline tag reference — should be updated to use ModTags.FURNACE_ARRAY_FRAME.

**ModCreativeTabs**: Two tabs. `ideomod_tab` (icon IDEOBLOCK, all standard items). `framed_blocks_tab` (icon Iron Framed Stone, dynamically populated from cached frameable blocks list built on first tab open via FrameabilityHelper).

---

## CONVENTIONS & PATTERNS

**Block registration**: DeferredRegister, properties set in ModBlocks.java supplier lambdas.

**Directional blocks**: extend DirectionalBlock, FACING property, piston-style placement (`getNearestLookingDirection().getOpposite()`), rotate/mirror overrides.

**Redstone blocks**: POWERED property, rising-edge detection in `neighborChanged()`, `level.setBlock(pos, state.setValue(POWERED, isPowered), 3)`.

**Block events (for client-server sync)**: `level.blockEvent()` in neighborChanged, actual logic in `triggerEvent()` which runs on BOTH sides. Encode direction as `facing.get3DDataValue()` in param.

**ThreadLocal coordination**: Used when mixin needs runtime context from calling code (RangedPistonFlags pattern: set before, read in mixin, clear in finally).

**Stickiness rule pattern**: `canStickTo` = permissive (allow both directions), `StickinessRules` = restrictive (block specific grabbers). Never express one-way behavior in canStickTo alone.

**Frame activation pattern**: All lifecycle hooks call `FrameActivation.recalculate()`. Single entry point, wipe-and-rebuild, no incremental logic. Machine Blocks are sources, frames are passive conductors.

**Multiblock definitions**: Extend StructureDefinition, register with StructureManager in commonSetup(). Use block tags for frame/component blocks. Scanner normalizes bounds and core positions.

**SavedData pattern**: Per-dimension via `level.getDataStorage().computeIfAbsent()`. NBT serialization. Static factory `get(ServerLevel)`. Used by both OddstonePortalSavedData and StructureManager.

---

## KNOWN ISSUES & TECHNICAL DEBT

1. **OddstoneConfig.reloadConfig() not wired to command** -- config can be reloaded programmatically but there's no in-game command to trigger it. Low priority.
2. **Item model translucency** -- Translucent inner blocks (glass, slime) render opaque in inventory item display. Quad-compositing approach can't handle separate render passes. Purely cosmetic; in-world rendering is correct.
3. ~~**Framed block transit rendering (Trolley/Repulser/Attractor)**~~ -- FIXED 2026-02-28c. BlockMoveHelper step 2.6 populates MovingFrameData on client side.
4. ~~**Framed blocks not grabbed by slime during Repulser/Attractor moves**~~ -- FIXED 2026-03-01. Backward walk grab immunity was firing on both canStickTo calls in the pair, blocking the behind block's passive response. Fixed with call-pair toggle in PistonStructureResolverMixin.
5. ~~**Frame inside-face rendering**~~ -- FIXED. Inverted cuboid elements added to frame JSON models.
6. ~~**Sympathetic framed oddstone transit rendering**~~ -- FIXED 2026-02-28c. PistonBaseBlockClientMixin sympathetic scan added to retraction capture.
7. ~~**Sympathetic retraction with machine-activated framed blocks**~~ -- FIXED 2026-02-28c. PistonBaseBlockMixin.scanSympathetic() now calls scanStickyGroup() on each found oddstone.
8. ~~**Machine Track signal bleeds into attached Machine Blocks via Trolley**~~ -- FIXED 2026-03-08. Two-part fix: (1) Track redstone isolation -- isSignalSource=false, getSignal=0, getDirectSignal=0. POWER is internal bookkeeping only, read via direct blockstate access. (2) FrameActivation suppression -- only MachineFrameBlock emits anti-signal; all other Machine Family blocks always activate. File: MachineTrackBlock.java, FrameActivation.java.
9. ~~**Repulser/Attractor cannot target Framed Oddstone**~~ -- FIXED 2026-03-08. ODDSTONE_ONLY predicate expanded to check FramedBlock with INNER_STICKY=ODDSTONE. File: RangedPistonUtil.java.
10. ~~**Framed blocks not pulled from behind in push direction**~~ -- FIXED 2026-03-07. Vanilla backward walk silently skips unpushable blocks (isPushable false -> stop, return true), so Feature 5 never fired. Fix: Feature 7 post-gathering anchor scan in TAIL of addBranchingBlocks checks toPush neighbors for active framed immovable blocks connected via frame stickiness. File: PistonStructureResolverMixin.java.
11. ~~**Framed inner blocks with light emission do not emit light**~~ -- FIXED 2026-03-08. Added INNER_LIGHT blockstate property (0-15), lightLevel predicate in ModBlocks.java, getStateForPlacement() pre-population from item NBT. File: FramedBlock.java, FramedBlockEntity.java, ModBlocks.java.
12. **Conductor strong power visible to dust through solid blocks** -- MachineConductorBlock.getDirectSignal outputs strong power (matching vanilla dust). Vanilla dust avoids cross-wire through-block power because all dust shares one RedStoneWireBlock instance and shouldSignal suppresses ALL dust during any dust scan. Conductor is a different class so dust's shouldSignal doesn't affect it. Fix requires mixin into RedStoneWireBlock to suppress Conductor output during dust's calculateTargetStrength scan. File: MachineConductorBlock.java.
13. **Machine Conductor base texture renders black** -- Two-layer model JSON (base texture + tinted indicator overlay) loads but base texture appears black despite file existing. Indicator overlay tints correctly. May need BESR approach like framed blocks instead of dual-element model. File: models/block/machine_conductor.json.

### Audit-Identified Issues (2026-03-13)

The following issues were identified during a comprehensive codebase audit. See IDEOMOD_AUDIT_REPORT.md for full details and recommended fixes.

| ID | Severity | Issue | Location |
|----|----------|-------|----------|
| A | Critical | MachineConductorBlock.shouldSignal is plain boolean, not ThreadLocal. Singleton block = shared state across all conductors. | MachineConductorBlock.java |
| B | Critical | FrameActivation.activatedVanillaMetals is plain HashSet mutated without synchronization. | FrameActivation.java |
| C | Critical | ThreadLocal cleanup gaps in PistonStructureResolverMixin -- exception between HEAD/TAIL leaves contaminated state. | PistonStructureResolverMixin.java |
| D | Critical | ThreadLocal cleanup gap in PistonBaseBlockMixin.pullTargetPos -- same pattern as C. | PistonBaseBlockMixin.java |
| E | High | OddstonePortalSavedData.unregisterPortal() uses && where || is needed -- impossible condition, causes memory leak. | OddstonePortalSavedData.java |
| F | High | FrameActivation.recalculate() has no debouncing -- large networks cause lag during construction. | FrameActivation.java |
| G | High | ideomod$addVanillaMetalConductors uses O(n^2) fixed-point loop. | PistonStructureResolverMixin.java |
| H | High | MovementScanHelper.scanSympathetic() rebuilds snapshots every iteration. | MovementScanHelper.java |
| J | Medium | OddstoneConfig sympathetic radius is unbounded -- config edit to large value causes server hang. | OddstoneConfig.java |
| N | Low | BUG10 debug logging left at INFO level. | PistonStructureResolverMixin.java |

Written by Claude, directed and guided by The Ideomancer.
