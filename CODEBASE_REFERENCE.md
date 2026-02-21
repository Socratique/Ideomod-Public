# IDEOMOD CODEBASE REFERENCE
*Quick-reference documentation for the Ideomod codebase.*
*Last updated: 2026-02-21*

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
  |     |-- OddstoneBlock.java           5-feature core block (see Oddstone section)
  |     |-- MachineFrameBlock.java       Machine Family base: stickiness + GUI (renamed from MachineBlock)
  |     |-- MachineFrameBlockEntity.java Block entity: single item slot, IPistonMovable
  |     |-- MachineFrameMenu.java        Container menu for Machine Frame GUI
  |     |-- IPistonMovable.java          Interface: saveMovementData/loadMovementData
  |     |-- MovingBlockEntityData.java   Static registry for BE data in transit during moves
  |     |-- MachineTrackBlock.java       Pushes adjacent Trolleys on redstone signal
  |     |-- MachineTrolleyBlock.java     Passive Machine Family cargo carrier
  |     |-- OddSlimeBlock.java           Directional slime (sticky only in FACING dir)
  |     |-- DrillBlock.java              3x3 redstone miner [ON HOLD]
  |     |-- StickinessRules.java         Rule registry (grab immunity + directional)
  |     |-- BlockMoveHelper.java         General-purpose piston-style block movement
  |     |-- RangedPistonUtil.java        Scanning + dispatch for Repulser/Attractor
  |     |-- RangedPistonFlags.java       ThreadLocal for resolver startPos redirect
  |     |-- AbstractRangedPistonBlock.java  Shared base for Repulser/Attractor variants
  |     |-- OddstoneRepulserBlock.java   Pushes Oddstone at range (extends Abstract)
  |     |-- OddstoneAttractorBlock.java  Pulls Oddstone at range (extends Abstract)
  |     |-- OddstonePortalBlock.java     Portal interior fill block + teleportation
  |     |-- OddstonePortalHandler.java   Activation/deactivation lifecycle (static util)
  |     |-- OddstonePortalFrameDetector.java  Frame scanning + address reading
  |     |-- OddstonePortalSavedData.java      Per-dimension portal registry (SavedData)
  |     \-- PortalUtils.java             Corner validation, interior nav, clearing
  |-- client/
  |     |-- ModColorHandlers.java        Block/item color tinting (Machine Block, Portal)
  |     \-- MachineFrameScreen.java      GUI screen for Machine Frame
  |-- config/
  |     \-- OddstoneConfig.java          JSON config: sympathetic radius + mob list
  |-- effect/
  |     \-- JudgementEffect.java         Rare mob-stare event on passive mob kill
  |-- mixin/
  |     |-- PistonStructureResolverMixin.java  Enforces stickiness rules + ranged redirect
  |     |-- PistonBaseBlockMixin.java          Allows IPistonMovable BE movement + saves data
  |     |-- PistonMovingBlockEntityMixin.java  Restores IPistonMovable data after animation
  |     \-- AbstractFurnaceBlockEntityMixin.java  Furnace array speed boost (50%)
  |-- multiblock/
  |     |-- StructureDefinition.java     Abstract base for structure type rulesets
  |     |-- FurnaceArrayDefinition.java  Furnace array ruleset (cores, frame tag, sizing)
  |     |-- StructureScanner.java        Directional pattern scanner (cluster + frame)
  |     |-- StructureResult.java         Immutable scan output (builder pattern)
  |     |-- StructureRegistration.java   Persistent registration entry (UUID, NBT)
  |     |-- StructureManager.java        Per-dimension SavedData + event handling + queries
  |     |-- StructureEffectHandler.java  Interface for per-type effects
  |     \-- FurnaceArrayEffect.java      GUI rename on formation/removal
  \-- registry/
        |-- ModBlocks.java               DeferredRegister<Block> -- all block registrations
        |-- ModItems.java                DeferredRegister<Item> -- BlockItems + debug stick
        |-- ModCreativeTabs.java         Creative tab with all mod items
        |-- ModCommands.java             /judgement command (Forge event bus)
        \-- ModTags.java                 PORTAL_CORNER_BLOCKS, REDSTONE_COMPONENTS,
                                         FURNACE_ARRAY_FRAME tags
```

---

## CORE SYSTEMS

### 1. Stickiness System (see STICKINESS_SYSTEM.md for full details)

Three-layer architecture for asymmetric and directional stickiness:

**Layer 1 -- canStickTo()** (in each block class): Permissive type-compatibility check. Must return true for ANY pair where either direction of interaction is wanted, because Forge checks bidirectionally (both A->B AND B->A must agree).

**Layer 2 -- Grab Immunity** (StickinessRules + mixin): Blocks specific grabber->target pairs. E.g. "Slime cannot grab Oddstone." Runs after canStickTo passes.

**Layer 3 -- Directional Rules** (StickinessRules + mixin): Blocks ALL grabs by a block when push direction doesn't match (Odd Slime only sticky when pushed in FACING dir).

Rules registered in `Ideomod.registerStickinessRules()`. Enforced by `PistonStructureResolverMixin`.

**Stickiness Matrix** (rows = grabber, cols = target). "Machine Fam" = Machine Block, Machine Track, Machine Trolley:

|              | Slime | Honey | Odd Slime | Machine Fam | Oddstone | Normal |
|:-------------|:-----:|:-----:|:---------:|:-----------:|:--------:|:------:|
| Slime        | YES   | no*   | no*       | YES         | no       | YES    |
| Honey        | no*   | YES   | no*       | YES         | no       | YES    |
| Odd Slime    | no*   | no*   | YES (d)   | YES (d)     | no       | YES (d)|
| Machine Fam  | no    | no    | no        | YES         | no       | no     |
| Oddstone     | YES   | YES   | YES       | no          | YES      | no     |

`no*` = mutual canStickTo exclusion | `no` = grab immunity rule | `(d)` = directional

### 2. Block Movement System (BlockMoveHelper + Ranged Pistons + Track/Trolley + IPistonMovable)

**Architecture**: Three-layer system. BlockMoveHelper provides general-purpose piston-style movement. RangedPistonUtil adds scanning. AbstractRangedPistonBlock adds shared block logic. All use vanilla's block event system for client-server sync (see BLOCK_EVENT_LESSON.md).

**BlockMoveHelper.moveBlockAt(level, initiatorPos, targetPos, pushDir)**: Single entry point for any system needing piston-style block movement. Mirrors vanilla `PistonBaseBlock.moveBlocks()` exactly. Steps: set ThreadLocal target -> create resolver -> resolve -> safety checks -> snapshot states -> save IPistonMovable data (server-only, step 2.5) -> destroy breakables -> place MovingPistonBlock at destinations (flag 68) -> clear vacated sources (flag 82) -> neighbor updates -> sound.

**Safety checks**: (1) toPush non-empty and contains target, (2) toPush doesn't contain initiator, (3) no destination equals initiator position.

**IPistonMovable pipeline**: Opt-in block entity data preservation during piston moves. Save phase: PistonBaseBlockMixin (vanilla pistons) or BlockMoveHelper step 2.5 (our systems). Both server-only -- client block entities lack synced inventories and would overwrite server data. Transit: MovingBlockEntityData static ConcurrentHashMap. Restore: PistonMovingBlockEntityMixin tick() TAIL (server-only, waits for real block). Single consumer pattern -- retrieve() is destructive.

**PistonBaseBlockMixin**: Two features. (1) isPushable HEAD inject returns true for IPistonMovable block entities. (2) moveBlocks HEAD inject saves data. Extension uses resolver. Retraction bypasses resolver (it fails for our blocks), scans pull position directly + BFS for sticky group.

**RangedPistonUtil**: Scanning + dispatch for Repulser/Attractor. `scanForNearest` / `scanForFarthest` walk up to 12 blocks. Delegates movement to BlockMoveHelper.

**AbstractRangedPistonBlock**: Shared base for Repulser/Attractor. Handles FACING/POWERED, rising edge, block events, rotation. Subclasses implement `fire()` only (~15 lines each).

**RangedPistonFlags** (ThreadLocal): Redirects the resolver's startPos from (devicePos + pushDir) to the actual target block. Set before `new PistonStructureResolver()`, cleared in finally block.

**MachineTrackBlock**: Uses BlockMoveHelper directly (no scanning). Signal direction detection checks actual sources (conduit blocks with higher POWER, tagged redstone components) rather than vanilla `hasSignal()` to avoid weak power misfires. Pushes ALL adjacent Trolleys in the computed direction. Block event encodes pushDir + trolleyFace via bit packing.

**MachineTrolleyBlock**: Passive cargo. No active behavior, just Machine Family stickiness.

**Filter predicates**: `ODDSTONE_ONLY` (current), `ANY_MOVABLE` (future Arcane variants).

### 3. Portal System

**Components**: OddstonePortalHandler (lifecycle) -> OddstonePortalFrameDetector (scanning) -> OddstonePortalSavedData (persistence) -> OddstonePortalBlock (teleportation).

**Frame rules**: Rectangular frame of Oddstone blocks with corner blocks (crafted mineral blocks from PORTAL_CORNER_BLOCKS tag). Interior 3-19 blocks wide/tall. ALL Oddstone in frame must be redstone-powered.

**Addressing**: 4 corner block types define address. 14 valid corner types = 38,416 unique addresses. Address is canonicalized (front vs back comparison) so both sides of a frame give the same address.

**Linking**: Max 2 portals per address. First lights unlinked, second auto-links to first. Destroying one unlinks the other.

**Teleportation**: Linked portals preserve relative position (fractional offset). Cross-axis portals (X vs Z) rotate player yaw 90 degrees to face through the destination portal. Unlinked portals scatter-teleport 200-1000 blocks + 5 hearts damage.

**Deactivation strategy**: Full frame re-validation via `validateOrDeactivate()`. When any frame Oddstone gets neighborChanged, navigates from portal block to frame, checks all corners + all Oddstone powered. Only deactivates if validation fails. Prevents false triggers from unrelated neighbor updates. Immediate deactivation via `onBlockRemoved()` when a frame Oddstone is physically broken.

**Break effects**: `PortalUtils.clearPortalBlocks()` uses `level.destroyBlock(pos, false)` for proper break particles and sound on all deactivation paths.

**Registration**: `OddstonePortalSavedData` (per-dimension SavedData). Keyed by frame corner position. Serialized to NBT.

### 4. Multiblock Structure Framework [WIP]

**Architecture**: Registration-based system. Structures are world data managed by StructureManager (per-dimension SavedData). Individual blocks don't know they're part of a structure.

**Components**: StructureDefinition (abstract ruleset) -> StructureScanner (pattern matching) -> StructureResult (scan output) -> StructureManager (registration + events) -> StructureRegistration (persistent entry) -> StructureEffectHandler (per-type effects).

**StructureManager data structures**:
- `structures`: UUID -> StructureRegistration (all registered structures)
- `chunkIndex`: ChunkPos -> Set<UUID> (which structures overlap each chunk)
- `typeIndex`: String -> Set<UUID> (structures grouped by definition name)
- `coreExclusivity`: BlockPos -> Map<String, UUID> (one structure per type per core)
- `trackedCores`: Set<BlockPos> (event-placed cores not yet in structures, for future non-BE cores)

**Event flow**: Block placed/broken -> check chunk index for nearby structures -> rescan affected -> check if placed block is a core -> try new formation -> check if placed block is frame material -> search nearby for cores via block entity maps + trackedCores -> try formation.

**Core detection**: Two complementary approaches: (1) block entity map iteration from nearby chunks (cheap, catches furnaces), (2) event-populated trackedCores set (catches future non-block-entity cores). No chunk scanning on load.

**Scanner**: Directional pattern scanner. Phase 1: flood-fill cluster of adjacent same-type cores. Phase 2: compute frame bounds from cluster + facing. Phase 3: validate frame layers (back wall, sides, top, bottom) + front face air openings. Normalizes bounds and core position to clusterMin.

**Current structure type -- Furnace Array**:
- Cores: furnace, blast_furnace, smoker (all vanilla furnace types)
- Frame: #ideomod:furnace_array_frame tag (18 stone-family blocks)
- Max cluster: 3 wide x 2 tall (6 furnaces)
- Facing: derived from furnace's FACING property
- Effects: GUI rename ("Enhanced Furnace" etc.) + 50% cooking speed boost
- Speed boost: +1 extra progress on even ticks via AbstractFurnaceBlockEntityMixin, clamped to totalTime - 1

### 5. Oddstone (5 features in one block)

1. **Mob avoidance**: `isPathfindable=false` + `BlockPathTypes.DAMAGE_OTHER`. JSON config per-mob whitelist.
2. **Redstone insulation**: `isRedstoneConductor=false` in properties.
3. **Sympathetic movement**: Handled by PistonStructureResolverMixin. BFS within configurable radius (default 5). Chains count toward 12-block limit.
4. **One-way stickiness**: Grabs all slime types + other Oddstone. Cannot be grabbed back (grab immunity rules).
5. **Portal frame**: Delegates to OddstonePortalHandler on neighborChanged/onRemove.

### 6. Machine Frame (3 features + Machine Family base)

Registry ID: `machine_block` (unchanged for world compatibility). Class: MachineFrameBlock.

1. **Selective stickiness**: Only actively grabs other Machine Family blocks (MachineFrameBlock, MachineTrackBlock, MachineTrolleyBlock). Permissive canStickTo via `canMachineFamilyStickTo()` lets slime/honey/odd slime grab it (one-way via immunity rules).
2. **Redstone conduction**: Solid-state redstone dust. POWER property 0-15, decays -1 per block. Only accepts signal from conduit blocks (`isRedstoneConduit()`) or REDSTONE_COMPONENTS tag (whitelist prevents feedback). Color gradient via tintIndex (dark steel grey -> bright red).
3. **Block entity with GUI**: MachineFrameBlockEntity with single item slot. IPistonMovable implementation preserves inventory through all piston move types. onRemove() drops contents only when broken (movedByPiston guard).

**Static helpers**: `isMachineFamily(Block)`, `isRedstoneConduit(Block)`, `canMachineFamilyStickTo(BlockState)` -- used by all Machine Family blocks.

### 7. Machine Track (conduction + Trolley push)

Non-directional Machine Family block. Conducts redstone like Machine Block (POWER 0-15, decay by 1, interoperable chains). On rising edge, pushes adjacent Trolleys opposite to signal source direction. Uses BlockMoveHelper for piston-style animated movement. Signal direction detected via direct source interrogation (not vanilla hasSignal).

### 8. Machine Trolley (passive cargo)

Non-directional Machine Family block. No active behavior. Pushed by Tracks, carries anything stuck to it. Does NOT conduct redstone.

### 9. Odd Slime Block

Directional slime variant. `extends DirectionalBlock`. FACING property set on placement (piston-style). Only sticky when pushed in FACING direction (directional rule in Layer 3). Mutual exclusion with vanilla slime and honey via canStickTo.

### 10. Judgement Effect

Rare event (1/200) when player kills a passive mob. All non-hostile mobs within 32 blocks freeze (`setNoAi(true)`), stare at player for 5 seconds (manual yaw/pitch per tick), then unfreeze. Triggered by LivingDeathEvent. Also available via `/judgement` command (op level 2).

---

## REGISTRIES

**ModBlocks**: IDEOBLOCK, DRILL, ODDSTONE, MACHINE_BLOCK, ODD_SLIME, ODDSTONE_PORTAL, ODDSTONE_REPULSER, ODDSTONE_ATTRACTOR, MACHINE_TRACK, MACHINE_TROLLEY

**ModItems**: BlockItems for all blocks EXCEPT ODDSTONE_PORTAL (not player-placeable). Plus STRUCTURE_DEBUG_STICK (standalone Item, stacksTo 1).

**ModTags**: PORTAL_CORNER_BLOCKS (14 mineral blocks), REDSTONE_COMPONENTS (redstone devices whitelist), FURNACE_ARRAY_FRAME (18 stone-family blocks).

**ModCreativeTabs**: Single tab "ideomod_tab" with icon IDEOBLOCK. Lists all BlockItems + Structure Debug Stick.

---

## CONVENTIONS & PATTERNS

**Block registration**: DeferredRegister, properties set in ModBlocks.java supplier lambdas.

**Directional blocks**: extend DirectionalBlock, FACING property, piston-style placement (`getNearestLookingDirection().getOpposite()`), rotate/mirror overrides.

**Redstone blocks**: POWERED property, rising-edge detection in `neighborChanged()`, `level.setBlock(pos, state.setValue(POWERED, isPowered), 3)`.

**Block events (for client-server sync)**: `level.blockEvent()` in neighborChanged, actual logic in `triggerEvent()` which runs on BOTH sides. Encode direction as `facing.get3DDataValue()` in param.

**ThreadLocal coordination**: Used when mixin needs runtime context from calling code (RangedPistonFlags pattern: set before, read in mixin, clear in finally).

**Stickiness rule pattern**: `canStickTo` = permissive (allow both directions), `StickinessRules` = restrictive (block specific grabbers). Never express one-way behavior in canStickTo alone.

**Multiblock definitions**: Extend StructureDefinition, register with StructureManager in commonSetup(). Use block tags for frame/component blocks. Scanner normalizes bounds and core positions.

**SavedData pattern**: Per-dimension via `level.getDataStorage().computeIfAbsent()`. NBT serialization. Static factory `get(ServerLevel)`. Used by both OddstonePortalSavedData and StructureManager.

---

## KNOWN ISSUES & TECHNICAL DEBT

1. **MachineBlock.isUpdating is static boolean** -- works for single-threaded server ticks but is not thread-safe. Same for MachineTrackBlock.isUpdating. Consider ThreadLocal if concurrency ever matters.
2. **RangedPistonUtil.ANY_MOVABLE passes null BlockGetter** -- `state.getDestroySpeed(null, BlockPos.ZERO)` could NPE on blocks that dereference the level parameter. Needs a level reference or a different check.
3. **Portal files in flat block/ package** -- 5+ portal-related files could live in `block/portal/` subpackage.
4. **Stickiness rules could be consolidated** -- multiple rules checking "X cannot grab Oddstone" could be one rule: `if (target instanceof OddstoneBlock && !(grabber instanceof OddstoneBlock))`.
5. **DrillBlock is on hold** -- still registered and taking up registry space. Fine for staging.
6. **Furnace array has no particle/sound on formation** -- structures register silently (debug stick only). Planned for Phase 5.
7. **Track/Trolley use machine_block.png placeholder texture** -- need unique art.
