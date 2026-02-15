# IDEOMOD TECHNICAL DOCUMENTATION
*Comprehensive reference for the Ideomod codebase Ã¢â‚¬â€ architecture, systems, templates, and implementation details.*
*Last updated: 2026-02-15*

---

## TABLE OF CONTENTS

1. [Project Setup](#1-project-setup)
2. [Package Structure](#2-package-structure)
3. [Registry System](#3-registry-system)
4. [Stickiness System](#4-stickiness-system)
5. [PistonStructureResolver Mixin](#5-pistonstructureresolver-mixin)
6. [Ranged Piston System](#6-ranged-piston-system)
7. [Portal System](#7-portal-system)
8. [Block Specifications](#8-block-specifications)
9. [Judgement Effect](#9-judgement-effect)
10. [Configuration](#10-configuration)
11. [Resource File Templates](#11-resource-file-templates)
12. [Tag Contents](#12-tag-contents)
13. [Known Issues & Technical Debt](#13-known-issues--technical-debt)
14. [Conventions & Patterns](#14-conventions--patterns)
15. [Adding New Content Checklists](#15-adding-new-content-checklists)

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

Mixin config: `ideomod.mixins.json` in resources root. Declares `PistonStructureResolverMixin` in the `mixins` array (common, not client-only). Refmap: `ideomod.refmap.json`. Compatibility level: JAVA_17.

gradle.properties values: `mod_id=ideomod`, `mod_name=Ideomod`, `mod_version=0.1.0`, `mod_group_id=com.ideomancer.ideomod`, `mod_authors=Ideomancer`.

---

## 2. PACKAGE STRUCTURE

```
com.ideomancer.ideomod
  |-- Ideomod.java              Main mod class. Registers DeferredRegisters, event
  |                              buses, and stickiness rules in commonSetup().
  |-- block/
  |     |-- OddstoneBlock.java           Core block: 5 features (see section 8)
  |     |-- MachineBlock.java            Selective stickiness + redstone conduction
  |     |-- OddSlimeBlock.java           Directional slime (FACING-gated stickiness)
  |     |-- DrillBlock.java              3x3 redstone miner [ON HOLD]
  |     |-- StickinessRules.java         Rule registry: grab immunity + directional
  |     |-- RangedPistonUtil.java        Shared resolve-and-move logic (mirrors vanilla)
  |     |-- RangedPistonFlags.java       ThreadLocal for resolver startPos redirect
  |     |-- OddstoneRepulserBlock.java   Pushes Oddstone at range via block events
  |     |-- OddstoneAttractorBlock.java  Pulls Oddstone at range via block events
  |     |-- OddstonePortalBlock.java     Portal interior fill + teleportation logic
  |     |-- OddstonePortalHandler.java   Portal activation/deactivation lifecycle
  |     |-- OddstonePortalFrameDetector.java  Frame scanning, validation, address reading
  |     |-- OddstonePortalSavedData.java      Per-dimension portal registry (NBT)
  |     \-- PortalUtils.java             Corner validation, interior nav, clearing helpers
  |-- client/
  |     \-- ModColorHandlers.java        Block/item color tinting (tintIndex system)
  |-- config/
  |     \-- OddstoneConfig.java          JSON config: sympathetic radius + mob list
  |-- effect/
  |     \-- JudgementEffect.java         Rare mob-stare event (Forge event bus)
  |-- mixin/
  |     \-- PistonStructureResolverMixin.java  4-feature mixin (see section 5)
  \-- registry/
        |-- ModBlocks.java               DeferredRegister<Block> Ã¢â‚¬â€ all block registrations
        |-- ModItems.java                DeferredRegister<Item> Ã¢â‚¬â€ all BlockItems
        |-- ModCreativeTabs.java         Creative tab with mod items
        |-- ModCommands.java             /judgement command (Forge event bus)
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
| `machine_block` | MachineBlock | Hardness 3.0/6.0, pickaxe, metal sound. `isRedstoneConductor=false`. |
| `odd_slime` | OddSlimeBlock | Friction 0.8F (slippery), slime sound, `noOcclusion()`. |
| `oddstone_portal` | OddstonePortalBlock | `noCollission()`, hardness -1 (indestructible), light 11, `PushReaction.BLOCK`, glass sound, `noLootTable()`. |
| `oddstone_repulser` | OddstoneRepulserBlock | Hardness 3.0/6.0, pickaxe, stone sound. |
| `oddstone_attractor` | OddstoneAttractorBlock | Same properties as Repulser. |

### ModItems (DeferredRegister<Item>)

BlockItems for every block EXCEPT `oddstone_portal` (not player-placeable, same pattern as vanilla nether portal). All use `new Item.Properties()` with no special settings.

### ModCreativeTabs

Single tab `ideomod_tab`, icon = IDEOBLOCK. Lists: Ideoblock, Drill, Oddstone, Machine Block, Odd Slime, Repulser, Attractor.

### ModTags

Two custom TagKeys, both for blocks:

| Tag | Purpose | JSON Location |
|-----|---------|---------------|
| `PORTAL_CORNER_BLOCKS` | Valid portal frame corner types (defines addresses) | `data/ideomod/tags/blocks/portal_corner_blocks.json` |
| `REDSTONE_COMPONENTS` | Whitelist for Machine Block signal sources | `data/ideomod/tags/blocks/redstone_components.json` |

See section 12 for the full contents of each tag.

### ModCommands

Registered on the FORGE event bus (not MOD bus) via `@Mod.EventBusSubscriber`. Listens for `RegisterCommandsEvent`.

`/judgement` Ã¢â‚¬â€ requires op level 2. Calls `JudgementEffect.trigger(player)` directly (bypasses the 1/200 chance roll).

---

## 4. STICKINESS SYSTEM

This is the most architecturally complex system in the mod. It enables one-way (asymmetric) and direction-dependent stickiness between blocks when pushed by pistons.

### The Core Problem

Forge checks `canStickTo` **bidirectionally**. When block A tries to stick to block B, BOTH `A.canStickTo(B)` AND `B.canStickTo(A)` must return true. If either returns false, the stick fails *before* any mixin code runs. This means you cannot express one-way stickiness through `canStickTo` alone Ã¢â‚¬â€ if Machine Block says "no" to slime, then slime also can't grab Machine Block.

### Three-Layer Architecture

**Layer 1 Ã¢â‚¬â€ canStickTo() (PERMISSIVE)**
Lives in each block class. Returns true for any block pair where interaction is desired in EITHER direction. Only returns false for true mutual exclusions where neither side should ever stick (like slime vs honey in vanilla, or slime vs odd slime).

**Layer 2 Ã¢â‚¬â€ Grab Immunity Rules (RESTRICTIVE)**
Lives in `StickinessRules.java`, registered in `Ideomod.registerStickinessRules()`, enforced by `PistonStructureResolverMixin`. Each rule is a `BiPredicate<BlockState, BlockState>` that tests `(grabber, target) -> should block?`. Runs after canStickTo passes. Blocks specific grabber->target pairs.

**Layer 3 Ã¢â‚¬â€ Directional Rules (CONDITIONAL)**
Also in `StickinessRules.java`. Each rule is a `BiPredicate<BlockState, Direction>` that tests `(blockState, pushDirection) -> should disable stickiness?`. Used for Odd Slime, which is only sticky when pushed in its FACING direction. When a directional rule fires, the block acts completely non-sticky for that push Ã¢â‚¬â€ it won't grab ANY neighbors.

### The Golden Rule

> `canStickTo` = "CAN these types interact?" (permissive, both sides must agree)
> Grab immunity = "WHO is allowed to initiate?" (restrictive, blocks specific grabbers)
> Directional rules = "WHEN is the block sticky?" (based on push direction)

### Stickiness Matrix

Rows = grabber, columns = target:

|              | Slime | Honey | Odd Slime | Machine | Oddstone | Normal |
|:-------------|:-----:|:-----:|:---------:|:-------:|:--------:|:------:|
| **Slime**    | YES   | no*   | no*       | YES     | no       | YES    |
| **Honey**    | no*   | YES   | no*       | YES     | no       | YES    |
| **Odd Slime**| no*   | no*   | YES (d)   | YES (d) | no       | YES (d)|
| **Machine**  | no    | no    | no        | YES     | no       | no     |
| **Oddstone** | YES   | YES   | YES       | no      | YES      | no     |

Legend: `no*` = mutual canStickTo exclusion (Layer 1) | `no` = grab immunity rule (Layer 2) | `(d)` = only when pushed in FACING direction (Layer 3) | "Normal" = any non-sticky block

### Currently Registered Rules

**Grab immunity rules** (registered in `Ideomod.registerStickinessRules()`):

| # | Grabber | Target | Effect |
|---|---------|--------|--------|
| 1 | SlimeBlock | OddstoneBlock | Slime can't grab Oddstone |
| 2 | HoneyBlock | OddstoneBlock | Honey can't grab Oddstone |
| 3 | OddSlimeBlock | OddstoneBlock | Odd Slime can't grab Oddstone |
| 4 | MachineBlock | SlimeBlock | Machine can't grab Slime |
| 5 | MachineBlock | HoneyBlock | Machine can't grab Honey |
| 6 | MachineBlock | OddSlimeBlock | Machine can't grab Odd Slime |
| 7 | SlimeBlock | OddSlimeBlock | Slime can't grab Odd Slime |
| 8 | HoneyBlock | OddSlimeBlock | Honey can't grab Odd Slime |

**Directional rules:**

| # | Block | Condition | Effect |
|---|-------|-----------|--------|
| 1 | OddSlimeBlock | pushDirection != FACING | All stickiness disabled |

### canStickTo Implementations (Layer 1)

**OddstoneBlock**: Returns true for SlimeBlock, HoneyBlock, OddSlimeBlock, OddstoneBlock. False for everything else (including MachineBlock).

**MachineBlock**: Returns true for MachineBlock, SlimeBlock, HoneyBlock, OddSlimeBlock. False for everything else. The slime/honey/odd slime returns are *permissive* Ã¢â‚¬â€ they let those types grab Machine Block one-way. Machine Block's own grabs toward them are blocked by immunity rules.

**OddSlimeBlock**: Returns false for SlimeBlock, HoneyBlock (mutual exclusion). Returns true for everything else (Oddstone, MachineBlock, OddSlimeBlock, normal blocks). Asymmetric behaviors handled by immunity rules + directional rules.

### StickinessRules.java Ã¢â‚¬â€ Internal Structure

Two static ArrayLists hold the registered rules. Two static check methods iterate them:

`isGrabBlocked(expandingState, neighborState)` Ã¢â‚¬â€ iterates grab immunity rules. Returns true if any rule blocks the grab.

`isDirectionalGrabBlocked(state, pushDirection)` Ã¢â‚¬â€ iterates directional rules. Returns true if the block should be non-sticky for this push direction.

This class lives in `block/` package (not `mixin/`) because both `Ideomod.java` (to register) and the mixin (to check) need to access it. The mixin package is locked down by Sponge Ã¢â‚¬â€ no regular classes allowed there.

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

Shadowed method: `addBlockLine(BlockPos pos, Direction dir)` Ã¢â‚¬â€ the resolver's own method for adding a line of blocks starting from `pos`.

### Custom Fields

`ideomod$inBranching` Ã¢â‚¬â€ `ThreadLocal<Boolean>`, defaults to false. Set true at HEAD of `addBranchingBlocks`, set false at TAIL. Used to distinguish between two phases of the resolver that both call `addBlockLine`:

1. **Line-building phase**: `resolve()` calls `addBlockLine()` directly to build the initial push line. This is passive chain cohesion Ã¢â‚¬â€ blocks already in line stay connected.
2. **Branching phase**: `resolve()` calls `addBranchingBlocks()`, which iterates every sticky block in `toPush` and checks its 6 neighbors. For each neighbor not already in the list, it calls `addBlockLine()`. This is active grabbing Ã¢â‚¬â€ sticky blocks reaching out.

The distinction matters because grab immunity and directional rules should apply at full strength during branching (active grabbing) but need nuance during line-building (see the backward walk section below).

### Feature 1A + 2A: Sideways Grabs (HEAD of addBlockLine)

```
@Inject(method = "addBlockLine", at = @At("HEAD"), cancellable = true)
```

Only fires when `ideomod$inBranching` is true. This catches the moment a sticky block in the push list reaches out to grab a neighbor during branching.

Logic:
1. Calculate grabber position: `pos.relative(dir.getOpposite())` Ã¢â‚¬â€ the block that's doing the grabbing
2. Verify grabber is actually in `toPush` (skip otherwise)
3. Get both block states
4. Skip if neighbor is air
5. Check directional rules on the grabber: is it disabled for this push direction? If yes, cancel with `cir.setReturnValue(true)`.
6. Check grab immunity: does the target resist this grabber? If yes, cancel with `cir.setReturnValue(true)`.

**Critical detail**: The cancel return value is `true`, not `false`. The `addBlockLine` method returns boolean indicating "did this succeed without hitting the push limit." Returning `true` means "everything's fine, just didn't add anything" Ã¢â‚¬â€ it lets the resolver continue checking other neighbors. Returning `false` would mean "push limit exceeded, abort everything" which would break unrelated blocks.

### Feature 1B + 2B: Backward Walk (Redirect on canStickTo)

```
@Redirect(method = "addBlockLine",
          at = @At(value = "INVOKE",
                   target = "Lnet/minecraft/world/level/block/state/BlockState;canStickTo(...)"))
```

This intercepts the `canStickTo` call inside `addBlockLine`'s backward walk. The backward walk checks: "does the block behind me stick to me? If so, pull it into the push line too."

In this context:
- `self` = the "behind" block (the one being checked for addition)
- `other` = the "front" block (the one already in the line, calling backward)

Logic:
1. Run original `canStickTo(self, other)` Ã¢â‚¬â€ if vanilla says no, we say no.
2. Check directional rules on `self` (behind block). If disabled for this push, return false.
3. Check directional rules on `other` (front block). If disabled for this push, return false.
4. **Special case**: Check if `other` is an active OddSlimeBlock (FACING == pushDirection). If yes, skip grab immunity. Rationale: the Odd Slime is the active puller here Ã¢â‚¬â€ it's carrying blocks behind it in its chain. The behind block is being pulled BY the Odd Slime, not grabbing it. Without this exception, Odd Slime couldn't pull anything because grab immunity would block the backward-walk connection.
5. If not the special case, apply normal grab immunity: `isGrabBlocked(self, other)`.

**CRITICAL WARNING**: The Odd Slime exception MUST remain narrowly scoped to `instanceof OddSlimeBlock` with FACING check. Generalizing it to "any sticky block" or "any block without directional rules" would match Oddstone, regular slime, honey, etc. and re-break one-way stickiness. This was the Stage 5 bug Ã¢â‚¬â€ slime pulling Oddstone in the backward walk.

### Feature 3: Sympathetic Movement (TAIL of addBranchingBlocks)

```
@Inject(method = "addBranchingBlocks", at = @At("TAIL"), cancellable = true)
```

After vanilla finishes checking all distance-1 sticky neighbors, this extends the search for Oddstone blocks. For each Oddstone already in `toPush`, scan along all 6 axis directions up to `sympatheticRadius` (configurable, default 5). If another Oddstone is found within range and isn't already in the push list, call `addBlockLine` to add it.

Key details:
- Takes a snapshot of `toPush` before iterating (avoids ConcurrentModificationException as new blocks get added)
- Only checks blocks matching `ModBlocks.ODDSTONE` Ã¢â‚¬â€ other block types don't participate
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
| `addBranchingBlocks` | TAIL | @Inject | Feature 3 | Sympathetic Oddstone search |
| `addBranchingBlocks` | TAIL | @Inject | Phase detection | Set `inBranching = false` |
| `addBlockLine` | HEAD | @Inject (cancellable) | Features 1A+2A | Block sideways grabs |
| `addBlockLine` | canStickTo INVOKE | @Redirect | Features 1B+2B | Block backward walk grabs |

---

## 6. RANGED PISTON SYSTEM

Repulser and Attractor are ranged directional pistons that push/pull Oddstone blocks at a distance. They use vanilla's block event system for client-server synchronization (see BLOCK_EVENT_LESSON.md for the full debugging story).

### Architecture

Both blocks follow the same pattern:

1. `neighborChanged()` Ã¢â‚¬â€ server-only. Detects rising redstone edge. Updates POWERED state. On rising edge, calls `level.blockEvent(pos, this, EVENT_FIRE, facing.get3DDataValue())`.
2. Block event is queued for the block event phase of this tick. Server sends `ClientboundBlockEventPacket` to all tracking clients.
3. `triggerEvent()` Ã¢â‚¬â€ runs on BOTH client and server. Decodes the direction from `param`, calls into `RangedPistonUtil`.

**Why block events?** `MovingPistonBlock.newBlockEntity()` returns null. The client never creates `PistonMovingBlockEntity` through the normal block entity lifecycle. Vanilla creates it by running `moveBlocks()` on both sides via `triggerEvent()`. If movement code only runs server-side, blocks disappear for 2 ticks then pop in with no animation.

**Why no ServerLevel guard in triggerEvent?** The entire point is that both sides run the same code. Guarding with `instanceof ServerLevel` defeats the purpose. Only guard things that genuinely must not run on client (item drops, XP, world data saves).

### RangedPistonUtil Ã¢â‚¬â€ Movement Logic

**Scanning**: `scanForNearest` (Repulser) and `scanForFarthest` (Attractor) walk a line from the device in its FACING direction, up to `SCAN_RANGE` (12 blocks), testing each position against a filter predicate. Currently uses `ODDSTONE_ONLY`. Future Arcane variants will use `ANY_MOVABLE`.

**resolveAndMove()** mirrors vanilla `PistonBaseBlock.moveBlocks()`:

1. **Set ThreadLocal target**: `RangedPistonFlags.setTarget(targetPos)` so the mixin can redirect the resolver's startPos.
2. **Create resolver**: `new PistonStructureResolver(level, devicePos, pushDir, true)`. Uses `devicePos` as pistonPos (safe, far from action). Mixin redirects startPos to actual target.
3. **Call `resolver.resolve()`**: If false, movement is blocked (immovable block, push limit, etc.). Clean up and return.
4. **Clear ThreadLocal**: Always in a `finally` block to prevent stale state.
5. **Safety checks**: Verify toPush is non-empty, contains targetPos, doesn't contain devicePos. Also checks that no block's destination (sourcePos + pushDir) equals devicePos â€” prevents blocks from being pushed/pulled into and overwriting the device.
6. **Snapshot states**: Record all block states for moved and destroyed positions.
7. **Destroy breakables**: Iterate `toDestroy` in reverse. Drop resources, set to air (flag 18).
8. **Place MovingPistonBlock at destinations**: Iterate `toPush` in reverse (farthest first). For each: calculate destPos = sourcePos + pushDir, set `MOVING_PISTON` state (flag 68), create `PistonMovingBlockEntity` via `MovingPistonBlock.newMovingBlockEntity()`. Source blocks still exist at this point Ã¢â‚¬â€ this is correct and matches vanilla.
9. **Clear vacated sources**: Set to air (flag 82). Only positions not overwritten by a destination.
10. **Neighbor updates**: Indirect neighbor shapes for vacated, updateNeighborsAt for destroyed and moved.
11. **Sound**: Piston extend sound from device position.

Flag 68 = SKIP_DROP(4) + NO_BLOCK_ENTITY_DROP(64). Flag 82 = UPDATE_CLIENTS(2) + SKIP_NEIGHBOR(16) + NO_BLOCK_ENTITY_DROP(64).

### RangedPistonFlags Ã¢â‚¬â€ ThreadLocal Coordination

Pattern for passing runtime context from calling code into a mixin:

1. Caller (`RangedPistonUtil`) sets the target: `RangedPistonFlags.setTarget(targetPos)`
2. Caller creates the resolver (which triggers the mixin)
3. Mixin reads the target: `RangedPistonFlags.getTarget()`
4. Caller clears in finally: `RangedPistonFlags.clear()`

Lives in `block/` package (not `mixin/`) so both sides can access it.

### Filter Predicates

`ODDSTONE_ONLY` Ã¢â‚¬â€ `state.is(ModBlocks.ODDSTONE.get())`. Used by current Repulser/Attractor.

`ANY_MOVABLE` Ã¢â‚¬â€ checks not air, destroySpeed >= 0, pushReaction not BLOCK or IGNORE. **Has a bug**: passes null for BlockGetter in `getDestroySpeed()`. Not currently used but needs fixing before Arcane variants.

---

## 7. PORTAL SYSTEM

The Oddstone Portal is an address-based teleportation system using mineral block corners to define destinations.

### Component Responsibilities

| File | Role |
|------|------|
| `OddstonePortalHandler` | Lifecycle coordinator. Called by OddstoneBlock's neighborChanged/onRemove. Decides activate vs deactivate. Static utility class. |
| `OddstonePortalFrameDetector` | Scans from interior outward to find valid frames. Validates structure, reads corner addresses, checks redstone power. |
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
8. Handler calls `OddstonePortalSavedData.registerPortal(address, pos, axis)`
9. Registration returns: REGISTERED_UNLINKED (first portal), REGISTERED_LINKED (second with same address), FAILED_ADDRESS_FULL (address has a pair), FAILED_ALREADY_EXISTS (same position registered)
10. On success: fills interior with `OddstonePortalBlock` states, plays flint-and-steel sound

### Deactivation Strategy

Deactivation uses full frame re-validation rather than reacting to individual block states. When any frame Oddstone receives a `neighborChanged` and is adjacent to a portal block:

1. `OddstonePortalHandler.validateOrDeactivate()` navigates from an adjacent portal block to the frame structure
2. Measures interior dimensions, derives all four corner positions
3. Checks all four corners are still valid corner blocks
4. Walks entire frame perimeter: every Oddstone must be present AND powered
5. Only calls `unregisterAndClear()` if validation fails

This prevents false deactivation from unrelated neighbor updates (levers, redstone dust, block placement near frame exterior). Mirrors the activation path which also validates the entire frame.

**Immediate deactivation** (no re-validation): `OddstoneBlock.onRemove()` calls `OddstonePortalHandler.onBlockRemoved()` when a frame Oddstone is broken. This is immediate because we know a frame block is gone.

**Break effects**: `PortalUtils.clearPortalBlocks()` uses `level.destroyBlock(pos, false)` which produces break particles and the portal block's break sound -- same visual/audio as cascade-breaking via `updateShape`.

`OddstonePortalBlock.updateShape()` also provides cascade breaking -- if any adjacent block in the portal plane isn't a portal block, Oddstone, or corner block, the portal block becomes air. Ã¢â‚¬â€ if any adjacent block in the portal plane isn't a portal block, Oddstone, or corner block, the portal block becomes air.

### Teleportation

`OddstonePortalBlock.entityInside()` Ã¢â‚¬â€ server only, players only.

**Linked portals**: Preserves relative position. Calculates player's fractional offset within source portal bounds (0.0 to 1.0 for both horizontal and vertical), applies same fraction to destination portal bounds. Works even if portals are different sizes.

**Cross-axis rotation**: When source and destination portals have different axes (X vs Z), the player's yaw is rotated 90 degrees to face through the destination portal's plane. X-axis to Z-axis: yaw += 90 (clockwise). Z-axis to X-axis: yaw -= 90 (counterclockwise). Same-axis pairs preserve the player's original facing. Uses `player.teleportTo(level, x, y, z, yaw, pitch)`.

**Unlinked portals**: Scatter teleport. Random angle, distance 200-1000 blocks, places on surface. Deals 10 damage (5 hearts).

**Cooldown**: 80 ticks (4 seconds), uses vanilla's `portalCooldown` field.

**Orphaned portals** (portal block exists but no registration found): treated as unlinked.

### SavedData Internals

Two maps maintain the registry:
- `portalsByPosition`: Map<BlockPos, PortalEntry> Ã¢â‚¬â€ primary store
- `portalsByAddress`: Map<String, BlockPos[2]> Ã¢â‚¬â€ quick lookup, max 2 entries per address

PortalEntry contains: address (String), pos (BlockPos), axis ("X" or "Z"), partnerPos (nullable BlockPos).

Unlinking: when one portal is unregistered, its partner's `partnerPos` is set to null. Address slot is freed.

NBT format: ListTag "portals", each entry has address, x/y/z, axis, linked boolean, and optional px/py/pz for partner position.

---

## 8. BLOCK SPECIFICATIONS

### Oddstone Ã¢â‚¬â€ 5 Features in One Block

| Feature | Implementation |
|---------|---------------|
| **Mob avoidance** | `isPathfindable()` returns false. `getBlockPathType()` returns `DAMAGE_OTHER` for affected mobs (checked against JSON config list). `getAdjacentBlockPathType()` returns `DANGER_OTHER` for the 1-block avoidance radius. |
| **Redstone insulation** | `isRedstoneConductor=false` set in ModBlocks properties lambda. Acts like glass for redstone. |
| **Sympathetic movement** | Handled entirely by PistonStructureResolverMixin Feature 3. Block itself has no sympathetic code. |
| **One-way stickiness** | `isStickyBlock()` returns true. `canStickTo()` returns true for SlimeBlock, HoneyBlock, OddSlimeBlock, OddstoneBlock. Reverse grabs blocked by immunity rules 1-3. |
| **Portal frame** | `neighborChanged()` delegates to `OddstonePortalHandler.onNeighborChanged()`. `onRemove()` delegates to `OddstonePortalHandler.onBlockRemoved()`. |

### Machine Block Ã¢â‚¬â€ 2 Features

| Feature | Implementation |
|---------|---------------|
| **Selective stickiness** | `isStickyBlock()` returns true. Permissive `canStickTo()` (true for MachineBlock + all slime types). Immunity rules 4-6 block Machine Block from grabbing slime types. Net result: only grabs other Machine Blocks, but slime/honey/odd slime CAN grab it. |
| **Redstone conduction** | POWER property (IntegerProperty 0-15). `isSignalSource()` returns true. `getSignal()`/`getDirectSignal()` return current power. `calculatePower()` checks all 6 neighbors Ã¢â‚¬â€ takes signal from Machine Blocks (decayed by 1) or from REDSTONE_COMPONENTS tag members. Static `isUpdating` flag prevents recursion during `neighborChanged()`. Color via tintIndex: `getPowerColor(power)` interpolates RGB from (80,80,80) at 0 to (255,48,48) at 15. |

### Odd Slime Block

Extends `DirectionalBlock`. FACING property, piston-style placement. `isStickyBlock()` returns true. `canStickTo()` returns false for SlimeBlock and HoneyBlock (mutual exclusion), true for everything else. Directional stickiness gating handled entirely by StickinessRules directional rule + mixin.

### Drill Block [ON HOLD]

Extends `DirectionalBlock`. FACING + POWERED properties. On rising redstone edge: mines 3x3 grid in front of drill face. Drops items at drill position. Skips air and indestructible blocks. Uses perpendicular direction calculation for the 3x3 plane.

### Oddstone Repulser / Attractor

Both extend `DirectionalBlock`. FACING + POWERED properties. Block event pattern for client-server sync. Repulser finds nearest Oddstone in line, pushes away. Attractor finds farthest Oddstone, pulls toward device.

---

## 9. JUDGEMENT EFFECT

A rare atmospheric event. When a player kills a non-hostile mob (Animal or AbstractVillager), there's a 1/200 chance all passive mobs within 32 blocks freeze and stare at the player for 5 seconds.

**Trigger**: `LivingDeathEvent` on Forge event bus. Checks: server side, killed by player, victim is non-hostile, no active session for this player, then rolls 1/200.

**Freeze mechanism**: `mob.setNoAi(true)` on each target mob. Same as vanilla `{NoAI:1}` Ã¢â‚¬â€ freezes all AI goals and movement.

**Stare mechanism**: Every server tick, for each frozen mob: calculate vector from mob eye position to player eye position, convert to yaw/pitch via `atan2`, set `yRot`, `xRot`, `yHeadRot`, `yBodyRot`.

**Cleanup**: After 100 ticks (5 seconds) or if player disconnects, calls `mob.setNoAi(false)` on all mobs. Session removed from `activeSessions` map.

**Manual trigger**: `/judgement` command bypasses the chance roll and calls `trigger()` directly.

**Session storage**: `Map<UUID, JudgementSession>` keyed by player UUID. Prevents multiple simultaneous sessions per player.

---

## 10. CONFIGURATION

### Oddstone Config (`config/ideomod/oddstone.json`)

Auto-generated on first launch. Loaded lazily on first access.

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

## 11. RESOURCE FILE TEMPLATES

### Blockstate Ã¢â‚¬â€ Simple (no variants)

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

### Blockstate Ã¢â‚¬â€ Directional (FACING + POWERED)

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

### Block Model Ã¢â‚¬â€ Simple (cube_all)

`assets/ideomod/models/block/oddstone.json`:
```json
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "ideomod:block/oddstone"
  }
}
```

### Block Model Ã¢â‚¬â€ Directional (front/back/side)

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

Same pattern for all block items Ã¢â‚¬â€ just parent the block model.

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

Current entries: ideoblock, drill, oddstone, machine_block, oddstone_portal, odd_slime, oddstone_repulser, oddstone_attractor, plus the creative tab.

---

## 12. TAG CONTENTS

### portal_corner_blocks (14 blocks)

`data/ideomod/tags/blocks/portal_corner_blocks.json`:

minecraft:coal_block, minecraft:iron_block, minecraft:copper_block, minecraft:gold_block, minecraft:redstone_block, minecraft:lapis_block, minecraft:diamond_block, minecraft:emerald_block, minecraft:netherite_block, minecraft:quartz_block, minecraft:amethyst_block, minecraft:raw_iron_block, minecraft:raw_copper_block, minecraft:raw_gold_block.

14 types -> 14^4 = 38,416 unique addresses per dimension.

### redstone_components (whitelist for Machine Block)

`data/ideomod/tags/blocks/redstone_components.json`:

Individual blocks: redstone_wire, lever, repeater, comparator, observer, redstone_block, redstone_torch, redstone_wall_torch, daylight_detector, trapped_chest, tripwire_hook, lectern, target, sculk_sensor, calibrated_sculk_sensor, lightning_rod, detector_rail.

Tag references: #minecraft:buttons, #minecraft:pressure_plates.

This whitelist prevents Machine Block from accepting signal from generic powered solid blocks (which would create feedback loops since Machine Block itself is a signal source).

---

## 13. KNOWN ISSUES & TECHNICAL DEBT

| # | Issue | Severity | Location |
|---|-------|----------|----------|
| 1 | Duplicate javadoc on triggerEvent() | Clutter | OddstoneRepulserBlock, OddstoneAttractorBlock |
| 2 | ANY_MOVABLE passes null BlockGetter | Crash risk (unused) | RangedPistonUtil.java |
| 3 | Repulser/Attractor 90% code duplication | Debt | Could extract AbstractRangedPistonBlock |
| 4 | 8 grab immunity rules could be consolidated | Debt | Ideomod.registerStickinessRules() |
| 5 | 5 portal files in flat block/ package | Organization | Could move to block/portal/ |
| 6 | MachineBlock.isUpdating is static (not ThreadLocal) | Low risk | MachineBlock.java |
| 7 | DrillBlock on hold, still registered | Intentional | N/A |
| 8 | OddstoneConfig.reloadConfig() not wired to command | Missing feature | OddstoneConfig.java |

---

## 14. CONVENTIONS & PATTERNS

**Registration**: DeferredRegister for blocks, items, creative tabs. All wired in Ideomod() constructor.

**Directional blocks**: Extend DirectionalBlock. Add FACING to state definition. Placement: `context.getNearestLookingDirection().getOpposite()`. Always include `rotate()` and `mirror()` overrides.

**Redstone-activated blocks**: Add POWERED (BooleanProperty) to state definition. In `neighborChanged()`: compare `level.hasNeighborSignal(pos)` with `state.getValue(POWERED)`. Update state. On rising edge, do the thing. Server-side only (`if (!level.isClientSide)`).

**Block events** (for client-server sync): Queue in `neighborChanged()` with `level.blockEvent(pos, this, eventId, encodedParam)`. Handle in `triggerEvent()` which runs on BOTH sides. Encode direction as `facing.get3DDataValue()`, decode with `Direction.from3DDataValue(param)`. Do NOT guard meaningful logic with `instanceof ServerLevel`.

**ThreadLocal coordination**: For passing context from caller into mixin. Set before creating the object that triggers the mixin, read inside the mixin, clear in a finally block. Class must live outside the mixin package.

**Stickiness**: canStickTo = permissive (Layer 1). StickinessRules = restrictive (Layer 2+3). Never express one-way behavior in canStickTo alone. See section 4 for the golden rule.

**File naming**: All lowercase. Block classes use PascalCase matching the block concept (OddstoneBlock, MachineBlock). Registry names use snake_case (oddstone, machine_block).

---

## 15. ADDING NEW CONTENT CHECKLISTS

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
2. Write canStickTo() Ã¢â‚¬â€ permissive (true for any type where either direction is wanted)
3. Update OTHER blocks' canStickTo() if they need to interact with yours
4. Register grab immunity rules in Ideomod.registerStickinessRules()
5. Register directional rules if stickiness depends on push direction
6. Update the stickiness matrix in this document AND STICKINESS_SYSTEM.md
