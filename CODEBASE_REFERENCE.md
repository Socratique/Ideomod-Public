# IDEOMOD CODEBASE REFERENCE
*Quick-reference documentation for the Ideomod codebase.*
*Last updated: 2026-02-15*

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
  |-- Ideomod.java              Main mod class. Registers buses + stickiness rules.
  |-- block/
  |     |-- OddstoneBlock.java           5-feature core block (see Oddstone section)
  |     |-- MachineBlock.java            Selective stickiness + redstone conduction
  |     |-- OddSlimeBlock.java           Directional slime (sticky only in FACING dir)
  |     |-- DrillBlock.java              3x3 redstone miner [ON HOLD]
  |     |-- StickinessRules.java         Rule registry (grab immunity + directional)
  |     |-- RangedPistonUtil.java        Shared resolve-and-move for Repulser/Attractor
  |     |-- RangedPistonFlags.java       ThreadLocal for resolver startPos redirect
  |     |-- OddstoneRepulserBlock.java   Pushes Oddstone at range
  |     |-- OddstoneAttractorBlock.java  Pulls Oddstone at range
  |     |-- OddstonePortalBlock.java     Portal interior fill block + teleportation
  |     |-- OddstonePortalHandler.java   Activation/deactivation lifecycle (static util)
  |     |-- OddstonePortalFrameDetector.java  Frame scanning + address reading
  |     |-- OddstonePortalSavedData.java      Per-dimension portal registry (SavedData)
  |     \-- PortalUtils.java             Corner validation, interior nav, clearing
  |-- client/
  |     \-- ModColorHandlers.java        Block/item color tinting (Machine Block, Portal)
  |-- config/
  |     \-- OddstoneConfig.java          JSON config: sympathetic radius + mob list
  |-- effect/
  |     \-- JudgementEffect.java         Rare mob-stare event on passive mob kill
  |-- mixin/
  |     \-- PistonStructureResolverMixin.java  Enforces stickiness rules + ranged redirect
  \-- registry/
        |-- ModBlocks.java               DeferredRegister<Block> -- all block registrations
        |-- ModItems.java                DeferredRegister<Item> -- all BlockItems
        |-- ModCreativeTabs.java         Creative tab with all mod items
        |-- ModCommands.java             /judgement command (Forge event bus)
        \-- ModTags.java                 PORTAL_CORNER_BLOCKS, REDSTONE_COMPONENTS tags
```

---

## CORE SYSTEMS

### 1. Stickiness System (see STICKINESS_SYSTEM.md for full details)

Three-layer architecture for asymmetric and directional stickiness:

**Layer 1 -- canStickTo()** (in each block class): Permissive type-compatibility check. Must return true for ANY pair where either direction of interaction is wanted, because Forge checks bidirectionally (both A->B AND B->A must agree).

**Layer 2 -- Grab Immunity** (StickinessRules + mixin): Blocks specific grabber->target pairs. E.g. "Slime cannot grab Oddstone." Runs after canStickTo passes.

**Layer 3 -- Directional Rules** (StickinessRules + mixin): Blocks ALL grabs by a block when push direction doesn't match (Odd Slime only sticky when pushed in FACING dir).

Rules registered in `Ideomod.registerStickinessRules()`. Enforced by `PistonStructureResolverMixin`.

**Stickiness Matrix** (rows = grabber, cols = target):

|              | Slime | Honey | Odd Slime | Machine | Oddstone | Normal |
|:-------------|:-----:|:-----:|:---------:|:-------:|:--------:|:------:|
| Slime        | YES   | no*   | no*       | YES     | no       | YES    |
| Honey        | no*   | YES   | no*       | YES     | no       | YES    |
| Odd Slime    | no*   | no*   | YES (d)   | YES (d) | no       | YES (d)|
| Machine      | no    | no    | no        | YES     | no       | no     |
| Oddstone     | YES   | YES   | YES       | no      | YES      | no     |

`no*` = mutual canStickTo exclusion | `no` = grab immunity rule | `(d)` = directional

### 2. Ranged Piston System (Repulser/Attractor)

**Architecture**: Uses vanilla's block event system for client-server sync (see BLOCK_EVENT_LESSON.md).

**Flow**: `neighborChanged()` -> `level.blockEvent()` -> `triggerEvent()` (runs BOTH sides) -> `RangedPistonUtil.tryRepulse/tryAttract()`

**RangedPistonUtil.resolveAndMove()**: Mirrors vanilla `PistonBaseBlock.moveBlocks()` exactly. Steps: snapshot states -> destroy breakables -> place MovingPistonBlock at destinations (flag 68) -> clear vacated sources (flag 82) -> neighbor updates -> sound.

**RangedPistonFlags** (ThreadLocal): Redirects the resolver's startPos from (devicePos + pushDir) to the actual target block. Prevents pistonPos from overlapping real blocks. Set before `new PistonStructureResolver()`, cleared in finally block.

**Scan range**: 12 blocks. Repulser finds nearest target, Attractor finds farthest.

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

### 4. Oddstone (5 features in one block)

1. **Mob avoidance**: `isPathfindable=false` + `BlockPathTypes.DAMAGE_OTHER`. JSON config per-mob whitelist.
2. **Redstone insulation**: `isRedstoneConductor=false` in properties.
3. **Sympathetic movement**: Handled by PistonStructureResolverMixin. BFS within configurable radius (default 5). Chains count toward 12-block limit.
4. **One-way stickiness**: Grabs all slime types + other Oddstone. Cannot be grabbed back (grab immunity rules).
5. **Portal frame**: Delegates to OddstonePortalHandler on neighborChanged/onRemove.

### 5. Machine Block (2 features)

1. **Selective stickiness**: Only actively grabs other Machine Blocks. Permissive canStickTo lets slime/honey/odd slime grab it (one-way via immunity rules).
2. **Redstone conduction**: Solid-state redstone dust. POWER property 0-15, decays -1 per block. Only accepts signal from REDSTONE_COMPONENTS tag (whitelist prevents feedback). Color gradient via tintIndex (dark steel grey -> bright red).

### 6. Odd Slime Block

Directional slime variant. `extends DirectionalBlock`. FACING property set on placement (piston-style). Only sticky when pushed in FACING direction (directional rule in Layer 3). Mutual exclusion with vanilla slime and honey via canStickTo.

### 7. Judgement Effect

Rare event (1/200) when player kills a passive mob. All non-hostile mobs within 32 blocks freeze (`setNoAi(true)`), stare at player for 5 seconds (manual yaw/pitch per tick), then unfreeze. Triggered by LivingDeathEvent. Also available via `/judgement` command (op level 2).

---

## REGISTRIES

**ModBlocks**: IDEOBLOCK, DRILL, ODDSTONE, MACHINE_BLOCK, ODD_SLIME, ODDSTONE_PORTAL, ODDSTONE_REPULSER, ODDSTONE_ATTRACTOR

**ModItems**: BlockItems for all blocks EXCEPT ODDSTONE_PORTAL (not player-placeable).

**ModTags**: PORTAL_CORNER_BLOCKS (14 mineral blocks), REDSTONE_COMPONENTS (redstone devices whitelist).

**ModCreativeTabs**: Single tab "ideomod_tab" with icon IDEOBLOCK.

---

## CONVENTIONS & PATTERNS

**Block registration**: DeferredRegister, properties set in ModBlocks.java supplier lambdas.

**Directional blocks**: extend DirectionalBlock, FACING property, piston-style placement (`getNearestLookingDirection().getOpposite()`), rotate/mirror overrides.

**Redstone blocks**: POWERED property, rising-edge detection in `neighborChanged()`, `level.setBlock(pos, state.setValue(POWERED, isPowered), 3)`.

**Block events (for client-server sync)**: `level.blockEvent()` in neighborChanged, actual logic in `triggerEvent()` which runs on BOTH sides. Encode direction as `facing.get3DDataValue()` in param.

**ThreadLocal coordination**: Used when mixin needs runtime context from calling code (RangedPistonFlags pattern: set before, read in mixin, clear in finally).

**Stickiness rule pattern**: `canStickTo` = permissive (allow both directions), `StickinessRules` = restrictive (block specific grabbers). Never express one-way behavior in canStickTo alone.

---

## KNOWN ISSUES & TECHNICAL DEBT

1. **MachineBlock.isUpdating is static boolean** -- works for single-threaded server ticks but is not thread-safe. Consider ThreadLocal if concurrency ever matters.
2. **Duplicate javadoc on triggerEvent()** -- both Repulser and Attractor have two consecutive comment blocks above triggerEvent(). Remove the first one on each.
3. **RangedPistonUtil.ANY_MOVABLE passes null BlockGetter** -- `state.getDestroySpeed(null, BlockPos.ZERO)` could NPE on blocks that dereference the level parameter. Needs a level reference or a different check.
4. **Repulser/Attractor code duplication** -- 90% identical. Could extract `AbstractRangedPistonBlock` base class.
5. **Portal files in flat block/ package** -- 5+ portal-related files could live in `block/portal/` subpackage.
6. **Stickiness rules could be consolidated** -- multiple rules checking "X cannot grab Oddstone" could be one rule: `if (target instanceof OddstoneBlock && !(grabber instanceof OddstoneBlock))`.
7. **DrillBlock is on hold** -- still registered and taking up registry space. Fine for staging.
