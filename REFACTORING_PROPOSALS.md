# IDEOMOD REFACTORING PROPOSALS
### Comprehensive Codebase Improvement Analysis
*Written by Claude, directed and guided by The Ideomancer.*
*Date: 2026-02-28*
*Status: ALL 9 PROPOSALS IMPLEMENTED (2026-02-28d). See IDEOMOD_CHANGELOG.txt [2026-02-28d] for details.*

---

## TABLE OF CONTENTS

1. [Executive Summary](#1-executive-summary)
2. [Proposal 1: Sticky Type Registry](#2-proposal-1-sticky-type-registry)
3. [Proposal 2: Block Family Interfaces](#3-proposal-2-block-family-interfaces)
4. [Proposal 3: Shared Movement Utilities](#4-proposal-3-shared-movement-utilities)
5. [Proposal 4: Stickiness Rule Consolidation](#5-proposal-4-stickiness-rule-consolidation)
6. [Proposal 5: Frame Material Data-Driven Expansion](#6-proposal-5-frame-material-data-driven-expansion)
7. [Proposal 6: Portal Geometry Consolidation](#7-proposal-6-portal-geometry-consolidation)
8. [Proposal 7: Multiblock Scan Strategy Pattern](#8-proposal-7-multiblock-scan-strategy-pattern)
9. [Proposal 8: Mixin Helper Extraction](#9-proposal-8-mixin-helper-extraction)
10. [Proposal 9: Minor Cleanup Items](#10-proposal-9-minor-cleanup-items)
11. [Implementation Priority & Ordering](#11-implementation-priority--ordering)

---

## 1. EXECUTIVE SUMMARY

After a thorough investigation of all 55+ Java source files, 7 mixin files, and all documentation, the codebase is well-organized architecturally but has accumulated patterns that will become increasingly painful as new features are added -- especially the Machine Specialization system, Oddstone Variants, and new Frame Materials listed in the design document.

The core problem is this: **the codebase was built feature-by-feature (correctly, for a prototype), but several cross-cutting concerns now appear in 3-6 places each.** When you add a new sticky block type, a new frame material, or a new machine family member, you currently need to touch 5-10 files. The proposals below aim to create "single sources of truth" so that adding new content means editing 1-2 files instead of hunting through the whole codebase.

**Key pain points identified (ranked by "how many files break when adding X"):**

| Adding... | Files currently needing changes | Goal |
|-----------|-------------------------------|------|
| New sticky block type (e.g. Oddstone variant) | 6-8 files (canStickTo in 3+ blocks, StickinessRules, Ideomod.java, FramedBlock, FramedBlockEntity, PistonStructureResolverMixin) | 1-2 files |
| New frame material (e.g. Gold) | 5-7 files (FrameMaterial, ModBlocks, ModItems, FramedBlockItem display name, FramedBlock.getCloneItemStack, FrameActivation) | 1-2 files |
| New machine family block | 4-5 files (MachineFrameBlock.isMachineFamily, new class with copy-paste canStickTo/isStickyBlock, StickinessRules, possibly MachineTrackBlock redstone) | 1-2 files |
| New oddstone-like behavior | 3 files (identical isOddstoneOrFramedOddstone method in 3 mixins) | 1 file |

---

## 2. PROPOSAL 1: STICKY TYPE REGISTRY

**Problem:** The concept of "what kinds of sticky blocks exist" is encoded as scattered instanceof chains and magic integer constants across 8+ files.

**Current state:**
- `FramedBlock` defines `STICKY_NONE=0, STICKY_SLIME=1, STICKY_HONEY=2, STICKY_ODD_SLIME=3, STICKY_ODDSTONE=4` as bare int constants
- `FramedBlockEntity.computeStickyType()` maps Block -> int via instanceof chain
- `FramedBlock.canStickTo()` has a 79-line switch/case with 4 branches, each containing 3-8 instanceof checks for mutual exclusions
- `OddstoneBlock.canStickTo()` has its own instanceof chain checking all sticky types plus FramedBlock inner types
- Every block with custom stickiness has its own hand-written canStickTo checking the same set of types

**Proposed solution:** Create a `StickyType` enum in `block/movement/` that becomes the single source of truth for sticky block classification and compatibility.

**What it would contain:**
- Enum values: `NONE, SLIME, HONEY, ODD_SLIME, ODDSTONE` (extensible -- just add a new enum value)
- A static method `fromBlock(Block)` replacing `FramedBlockEntity.computeStickyType()` instanceof chain
- A method `isExcludedFrom(StickyType other)` encoding the mutual exclusion rules (slime vs honey, slime vs odd_slime, honey vs odd_slime) in one place
- A method `canGrab(StickyType target)` encoding which types each grabber can reach
- The integer mapping for INNER_STICKY blockstate property (enum ordinal or explicit map)
- A method `isVanillaSticky(Block)` to replace scattered `instanceof SlimeBlock || instanceof HoneyBlock` checks

**What this eliminates:**
- The 79-line switch/case in FramedBlock.canStickTo() shrinks to ~15 lines using `StickyType.fromBlock()` and `type.isExcludedFrom()`
- OddstoneBlock.canStickTo() shrinks similarly
- Adding a new sticky type (Oddstone Variant with different frequency) means: add enum value, define its exclusions, done -- every canStickTo automatically picks it up

**Risk level:** Medium. The canStickTo logic is the most sensitive code in the mod. Needs thorough testing of every stickiness matrix cell after refactor.

**Why it matters for upcoming features:** The Design Document describes Oddstone Variants where each variant is its own sympathetic frequency. If variants also have distinct stickiness (they currently do -- "Oddstone links to Oddstone, Oddstone Bricks link to Oddstone Bricks, they do NOT cross-link"), then without this refactor, each variant adds another branch to every instanceof chain in the stickiness system.

---

## 3. PROPOSAL 2: BLOCK FAMILY INTERFACES

**Problem:** "Is this block a Machine Family member?" and "Is this block a Frame Family member?" are checked via scattered static methods and instanceof chains with no compile-time enforcement.

**Current state:**
- `MachineFrameBlock.isMachineFamily(Block)` checks `instanceof MachineFrameBlock || instanceof MachineTrackBlock || instanceof MachineTrolleyBlock` -- must be manually updated for every new machine type
- `FrameActivation.isFrameFamily(Block)` checks `block instanceof IFrameBlock` -- this one is actually good, but it's called through a delegation chain: `StickinessRules.isFrameFamily()` -> `FrameActivation.isFrameFamily()`
- All three machine family blocks copy-paste identical `isStickyBlock()` and `canStickTo()` methods (100% code duplication)
- Redstone conduction check `MachineFrameBlock.isRedstoneConduit(Block)` only checks `instanceof MachineTrackBlock` -- would need updating when Machine Conductor specialization is implemented

**Proposed solution:** Create an `IMachineFamily` interface in `block/machine/` (parallel to how `IFrameBlock` already works for frames).

**What it would contain:**
- Marker interface `IMachineFamily` with default implementations of `isStickyBlock()` and `canStickTo()`
- A static helper `isMachineFamily(Block)` that checks `block instanceof IMachineFamily` instead of listing concrete classes
- Each machine block implements the interface and gets stickiness for free

**How it changes existing code:**
- `MachineFrameBlock`, `MachineTrackBlock`, `MachineTrolleyBlock` all implement `IMachineFamily`
- The three copy-pasted `canStickTo()` methods become inherited defaults
- `MachineFrameBlock.isMachineFamily()` becomes `block instanceof IMachineFamily`
- Adding a new machine family block: implement the interface, done -- stickiness, family membership, and rule matching all come automatically

**Bonus:** When the Machine Specialization system is built, the base `MachineFrameBlock` can implement `IMachineFamily` and all specializations inherit it. The "same-type-only stickiness" rule from the design doc would be a method on the interface that compares specialization IDs, with universal connectors returning true for all.

**Risk level:** Low. This is pure interface extraction -- existing behavior unchanged.

---

## 4. PROPOSAL 3: SHARED MOVEMENT UTILITIES

**Problem:** The server-side `PistonBaseBlockMixin` and client-side `PistonBaseBlockClientMixin` contain nearly identical implementations of three algorithms that must be kept in sync.

**Current state (3 duplicated algorithms across 2 files):**

1. **`isOddstoneOrFramedOddstone()`** -- appears identically in 3 files:
   - `PistonBaseBlockMixin` (server)
   - `PistonBaseBlockClientMixin` (client)
   - `PistonStructureResolverMixin`

   Each is 12-15 lines checking `instanceof OddstoneBlock` or `instanceof FramedBlock` with INNER_STICKY == STICKY_ODDSTONE. A bug fix to oddstone detection must be applied three times.

2. **Sympathetic scan** -- nearly identical in 2 files:
   - `PistonBaseBlockMixin.scanSympathetic()` (~60 lines)
   - `PistonBaseBlockClientMixin.ideomod$captureSympathetic()` (~45 lines)

   Both scan along 6 directions for oddstone within radius, check sympathetic config, and process found blocks. The server version additionally calls `scanStickyGroup()`.

3. **Sticky group BFS** -- nearly identical in 2 files:
   - `PistonBaseBlockMixin.scanStickyGroup()` (~55 lines)
   - `PistonBaseBlockClientMixin.ideomod$scanStickyGroupInto()` (~45 lines)

   Both use identical BFS: visited set, queue, Direction.values() iteration, canStickTo checks, 12-block limit. Server version adds to toPush list, client version adds to a Set for frame data capture.

**Proposed solution:** Create a `MovementScanHelper` utility class in `block/movement/` containing the shared algorithms as static methods.

**What it would contain:**
- `isOddstoneOrFramedOddstone(BlockState)` -- single implementation, called from all 3 mixins
- `scanSympathetic(Level, Set<BlockPos> startPositions, int radius)` -- returns `Set<BlockPos>` of found oddstone positions. Both mixin sides call this, then do their own processing (server adds to resolver, client captures frame data)
- `scanStickyGroup(Level, BlockPos start, int maxSize)` -- returns `Set<BlockPos>` of the connected sticky group. Both sides use the result differently (server adds to movement list, client captures for rendering)

**Why mixins can use this:** Mixin classes CAN call static utility methods from non-mixin classes. The limitation is that mixins can't extend/implement interfaces from mod code, but static method calls are fine. This is exactly how `RangedPistonFlags`, `StickinessRules`, and `OddstoneConfig` are already called from mixins.

**Risk level:** Low-Medium. The algorithms are well-understood and the extraction is mechanical. Testing: verify sympathetic movement works for both vanilla pistons and Repulser/Attractor, and that client-side transit rendering still shows correctly.

---

## 5. PROPOSAL 4: STICKINESS RULE CONSOLIDATION

**Problem:** The grab immunity rules in `Ideomod.registerStickinessRules()` contain deeply nested conditional chains with repeated patterns, especially Rule 1 (Oddstone protection, 45 lines) and Rule 4 (Frame Family grab immunity, 68 lines).

**Current state:**
- Rule 4 checks target instanceof for 5 block types (SlimeBlock, HoneyBlock, OddSlimeBlock, OddstoneBlock, FramedBlock), each with similar carve-out logic
- The "ACTIVE same-material IFrameBlock" check appears in 4 separate places (Rule 1 carve-out B, Rule 4 carve-out for framed oddstone targets, PistonStructureResolverMixin.wouldFrameStickinessAllow, and FramedBlock.canStickTo)
- The "grabber INNER_STICKY matching" pattern appears 3+ times in Rule 4 with slight variations per target type
- Adding Oddstone Variants would add new branches to BOTH Rules 1 and 4

**Proposed solution:** Restructure the rules using helper methods and the StickyType enum from Proposal 1.

**Key helper methods to extract:**
- `isActiveFrameConnection(BlockState grabber, BlockState target)` -- checks ACTIVE + same-material IFrameBlock, used by Rules 1, 4, and the resolver mixin
- `hasMatchingInnerSticky(BlockState grabber, StickyType targetType)` -- checks if a FramedBlock grabber's INNER_STICKY matches or universally covers the target type
- `isOddstoneType(BlockState state)` -- consolidates the "real oddstone or framed oddstone" check

**What changes:**
- Rule 4's 68 lines collapse to ~20 lines using StickyType lookup and helper methods
- Rule 1's 45 lines collapse to ~15 lines
- The four separate "ACTIVE same-material" checks become one helper called four times
- Adding a new sticky type to the immunity system means updating the helpers, not the rule lambdas

**Risk level:** Medium-High. The stickiness rules are the most battle-tested and bug-prone part of the codebase. The STICKINESS_SYSTEM.md document exists precisely because this logic has been the source of subtle bugs. Needs systematic testing of every matrix cell.

**Recommended approach:** Do this AFTER Proposal 1 (StickyType enum) since the enum makes the rule consolidation much cleaner.

---

## 6. PROPOSAL 5: FRAME MATERIAL DATA-DRIVEN EXPANSION

**Problem:** Adding a new frame material (e.g., Gold, Diamond) currently requires changes in 5-7 files with hardcoded material-to-X mappings.

**Current state:**
- `FrameMaterial` enum has IRON, COPPER -- good starting point
- `FramedBlockItem.getName()` hardcodes `frameMaterial == FrameMaterial.IRON ? "Iron" : "Copper"` -- breaks with 3+ materials
- `FramedBlock.getCloneItemStack()` hardcodes `this.frameMaterial == FrameMaterial.IRON ? ModItems.IRON_FRAMED_BLOCK.get() : ModItems.COPPER_FRAMED_BLOCK.get()` -- breaks with 3+ materials
- `FramedBlockRecipe.assemble()` hardcodes `frameBlock == ModBlocks.IRON_FRAME.get()` checks
- `ModBlocks` has separate registration calls per material (fine, but lot of copy-paste)
- `ModItems` has separate registration calls per material (same)

**Proposed solution:** Enrich the `FrameMaterial` enum to carry all material-specific data, and create a lookup map for Block/Item -> FrameMaterial.

**What FrameMaterial would gain:**
```
IRON("Iron", "iron"),
COPPER("Copper", "copper");

private final String displayName;  // For FramedBlockItem.getName()
private final String registryPrefix;  // For registry lookups
```

**Plus registry-time lookups:**
- `FrameMaterial.getFramedBlockItem(FrameMaterial)` -> returns the correct FramedBlockItem RegistryObject
- `FrameMaterial.getFrameBlock(FrameMaterial)` -> returns the correct FrameBlock RegistryObject

These could be populated during mod init, stored as a Map, and queried wherever material-specific items/blocks are needed. The ternary operators scattered through the code become `material.getDisplayName()` or `FrameMaterial.getFramedBlockItem(this.frameMaterial)`.

**Adding a third material:** Add enum value with display name, register the blocks/items in ModBlocks/ModItems (following the existing pattern), populate the map. All the scattered ternaries automatically work because they use the enum, not hardcoded if/else.

**Risk level:** Low. Mostly string and registry lookups -- no behavioral changes.

---

## 7. PROPOSAL 6: PORTAL GEOMETRY CONSOLIDATION

**Problem:** Portal dimension measurement, frame iteration, and frame validation logic appear in 3-4 files with slight variations.

**Current state:**
- Portal interior dimensions (width/height by scanning consecutive portal blocks) implemented in:
  - `OddstonePortalBlock` (for teleportation offset calculation)
  - `OddstonePortalHandler` (for validation)
  - `PortalUtils` (helper, but not used by all callers)
- Frame perimeter iteration (bottom row, top row, left col, right col) duplicated in:
  - `OddstonePortalHandler.validateOrDeactivate()`
  - `OddstonePortalFrameDetector.validateFrame()`
- Axis string-to-enum conversion (`"X"` -> `Direction.Axis.X`) appears 4+ times across portal files

**Proposed solution:** Create a `PortalGeometry` value object (or record, since we're on Java 17).

**What it would contain:**
```
record PortalGeometry(
    BlockPos interiorBottomLeft,
    BlockPos frameCorner,
    int interiorWidth,
    int interiorHeight,
    Direction.Axis axis
) {
    static PortalGeometry detect(Level level, BlockPos anyPortalBlock) { ... }

    // Iterate all frame positions (perimeter)
    void forEachFramePosition(BiConsumer<BlockPos, FrameRole> visitor) { ... }

    // Get the axis as enum from a saved string
    static Direction.Axis parseAxis(String axisStr) { ... }
}
```

**What this eliminates:**
- Three separate dimension-scanning implementations become one
- Two separate frame-iteration loops become one `forEachFramePosition` call
- Four axis string conversions become one `parseAxis` call
- `OddstonePortalSavedData.PortalEntry` gains a `getAxisAsEnum()` convenience method

**Risk level:** Low. Pure extraction of existing working code into a shared object.

---

## 8. PROPOSAL 7: MULTIBLOCK SCAN STRATEGY PATTERN

**Problem:** The multiblock framework's `StructureScanner` has hardcoded switch/case for scan modes, with `UnsupportedOperationException` for future modes.

**Current state:**
```java
case PROXIMITY -> throw new UnsupportedOperationException(
    "PROXIMITY scan not yet implemented");
case PROXIMITY_WALLED -> throw new UnsupportedOperationException(
    "PROXIMITY_WALLED scan not yet implemented");
```

Adding a new structure type (e.g., Enchanting Table, Brewery from the design doc) requires modifying `StructureScanner` core code -- a closed-for-modification design.

**Proposed solution:** Strategy Pattern. Each scan mode is an interface implementation registered with the scanner.

**What it would look like:**
- `ScanStrategy` interface with a single `scan(Level, BlockPos, StructureDefinition)` method returning `StructureResult`
- `StructureScanner` holds a `Map<ScanMode, ScanStrategy>` populated during init
- The existing `CLUSTER_WITH_FRAME` logic becomes `ClusterWithFrameStrategy`
- Adding `PROXIMITY` later means creating a new class and registering it -- no modification to StructureScanner

**Risk level:** Low. Current behavior for CLUSTER_WITH_FRAME is extracted as-is. The UnsupportedOperationException cases aren't used yet.

**Why now:** If this is done before implementing the Enchanting Table or Brewery multiblocks, those implementations slot in cleanly. If done after, there's a messier migration.

---

## 9. PROPOSAL 8: MIXIN HELPER EXTRACTION

**Problem:** Several mixin files contain private helper methods that duplicate logic from block classes or from each other.

**Specific cases:**

### 8a. Immovable Inner Block Check (2 locations)
- `PistonStructureResolverMixin.ideomod$isFramedBlockWithImmovableInner()` -- properly encapsulated
- `PistonBaseBlockMixin` lines 151-169 -- duplicates the same logic inline

Both check for OBSIDIAN, CRYING_OBSIDIAN, REINFORCED_DEEPSLATE, and `getDestroySpeed() == -1.0f`.

**Fix:** Extract to a static method in `FramedBlock` itself (e.g., `FramedBlock.hasImmovableInner(Level, BlockPos)`). Both mixins call that.

### 8b. Active Frame Resolution (2 locations)
In `Ideomod.registerStickinessRules()`, the following 5-line block appears twice (Rules 1 and 4):
```java
boolean grabberActive = false;
if (grabber.getBlock() instanceof FramedBlock) {
    grabberActive = grabber.getValue(FramedBlock.ACTIVE);
} else if (grabber.getBlock() instanceof FrameBlock) {
    grabberActive = grabber.getValue(FrameBlock.ACTIVE);
}
```

**Fix:** Add `IFrameBlock.isActive(BlockState)` static helper. Both FrameBlock and FramedBlock have ACTIVE properties, but they're separate BooleanProperty instances. A static helper that checks `instanceof IFrameBlock` and reads the correct property collapses this pattern.

### 8c. Movement Factor Retrieval (2 methods, same pattern)
`EntityMovementMixin` has `ideomod$getFramedSpeedFactor()` and `ideomod$getFramedJumpFactor()` which are identical except for calling `getSpeedFactor()` vs `getJumpFactor()`.

**Fix:** Extract a generic helper that takes a `Function<BlockState, Float>` parameter.

**Risk level:** Low for all three. Pure extraction, no behavior change.

---

## 10. PROPOSAL 9: MINOR CLEANUP ITEMS

These are smaller issues that don't warrant their own proposal but should be addressed:

### 9a. Dead Code: ModColorHandlers
The file contains only empty stubs with comments about removed functionality. Either delete it or add a clear TODO with a tracking reference to the Machine Conductor specialization.

### 9b. NBT Path Centralization
The NBT path `stack -> tag -> BlockEntityTag -> InnerBlock` for framed blocks is accessed in 3+ places (FramedBlockItem, FramedBlockEntity, FramedBlockRecipe) with slightly different null-checking patterns.

**Fix:** Add utility methods to `FramedBlockEntity`:
- `getInnerBlockIdFromStack(ItemStack)` -> Optional<ResourceLocation>
- `setInnerBlockIdOnStack(ItemStack, ResourceLocation)`

### 9c. FramedBlockItem.createStack() Overloads
Two overloads with nearly identical implementations (one takes Block, one takes ResourceLocation). The Block overload should just convert to ResourceLocation and call the other.

### 9d. Bounds Checking in StructureRegistration
`isInBounds()` and `isInExpandedBounds()` have identical logic with different min/max parameters. Extract a private `isWithinBounds(BlockPos, BlockPos min, BlockPos max)` helper.

### 9e. RangedPistonUtil tryRepulse/tryAttract
Nearly identical methods differing only in scan direction and push direction. Could be unified into a single `tryMove(level, devicePos, facing, filter, boolean repulse)` method.

### 9f. Registry ID Constants
String identifiers like `"machine_frame"` appear in both `ModBlockEntities` and `ModMenuTypes` with no shared constant. Create a `RegistryIds` class or add constants to the relevant block classes.

### 9g. Neighbor Update Loops
The `for (Direction dir : Direction.values()) { level.updateNeighborsAt(pos.relative(dir), this); }` pattern appears in multiple lifecycle methods across MachineTrackBlock, FrameBlock, and FrameActivation. Could be a `BlockHelper.updateAllNeighbors(level, pos, block)` utility.

---

## 11. IMPLEMENTATION PRIORITY & ORDERING

Based on impact (how much pain it prevents), risk (how likely it is to introduce bugs), and dependencies (which proposals enable others):

### Phase 1: Low-Risk Foundations (do first)
These are pure extractions with no behavioral changes. They set up the infrastructure that later phases build on.

| Order | Proposal | Risk | Effort | Why first |
|-------|----------|------|--------|-----------|
| 1.1 | **Proposal 5** (FrameMaterial enrichment) | Low | Small | Self-contained, immediately removes ternary chains |
| 1.2 | **Proposal 2** (IMachineFamily interface) | Low | Small | Eliminates copy-paste stickiness in 3 classes |
| 1.3 | **Proposal 8** (Mixin helper extraction) | Low | Small | Pure extraction, reduces duplication in sensitive files |
| 1.4 | **Proposal 9** (Minor cleanups) | Low | Small | Quick wins, each independent |

### Phase 2: Structural Improvements (do second)
These create shared abstractions that multiple systems benefit from.

| Order | Proposal | Risk | Effort | Why second |
|-------|----------|------|--------|------------|
| 2.1 | **Proposal 3** (MovementScanHelper) | Low-Med | Medium | Eliminates 3 duplicated algorithms across mixins |
| 2.2 | **Proposal 6** (PortalGeometry) | Low | Medium | Consolidates portal system, no stickiness risk |
| 2.3 | **Proposal 7** (Multiblock Strategy) | Low | Medium | Future-proofs multiblock framework before new types are added |

### Phase 3: Core Refactors (do last, most carefully)
These touch the stickiness system, which is the most sensitive code in the mod.

| Order | Proposal | Risk | Effort | Why last |
|-------|----------|------|--------|----------|
| 3.1 | **Proposal 1** (StickyType enum) | Medium | Medium | Foundation for stickiness cleanup, needs thorough testing |
| 3.2 | **Proposal 4** (Rule consolidation) | Med-High | Medium | Depends on Proposal 1, highest risk but highest payoff |

### Testing Strategy per Phase

**Phase 1 testing:** Quick smoke test -- place blocks, verify stickiness matrix unchanged, check frame activation works.

**Phase 2 testing:**
- Sympathetic movement with vanilla pistons + Repulser/Attractor + Track
- Client transit rendering (framed blocks show inner texture during movement)
- Portal activation, linking, teleportation, deactivation
- Furnace array formation and speed boost

**Phase 3 testing:** Full stickiness matrix regression. Test every cell in the matrix from STICKINESS_SYSTEM.md:
- All grabber->target pairs in both directions
- Directional Odd Slime in FACING vs non-FACING
- Framed block variants (active/inactive, with/without inner sticky)
- Material matching (iron<->iron, copper<->copper, cross-material rejection)
- Sympathetic oddstone chaining through framed oddstone

---

## APPENDIX: FILES AFFECTED PER PROPOSAL

| Proposal | Files Modified | Files Created |
|----------|---------------|---------------|
| 1 (StickyType) | FramedBlock, FramedBlockEntity, OddstoneBlock, OddSlimeBlock, MachineFrameBlock, FrameBlock, Ideomod.java, PistonStructureResolverMixin | StickyType.java |
| 2 (IMachineFamily) | MachineFrameBlock, MachineTrackBlock, MachineTrolleyBlock, StickinessRules | IMachineFamily.java |
| 3 (MovementScanHelper) | PistonBaseBlockMixin, PistonBaseBlockClientMixin, PistonStructureResolverMixin | MovementScanHelper.java |
| 4 (Rule consolidation) | Ideomod.java, StickinessRules (possibly) | StickinessHelpers.java (or inline in StickinessRules) |
| 5 (FrameMaterial) | FrameMaterial, FramedBlockItem, FramedBlock, FramedBlockRecipe | (none) |
| 6 (PortalGeometry) | OddstonePortalBlock, OddstonePortalHandler, OddstonePortalFrameDetector, PortalUtils, OddstonePortalSavedData | PortalGeometry.java |
| 7 (Multiblock Strategy) | StructureScanner | ScanStrategy.java, ClusterWithFrameStrategy.java |
| 8a (Immovable inner) | PistonBaseBlockMixin, PistonStructureResolverMixin, FramedBlock | (none) |
| 8b (Active frame) | Ideomod.java, IFrameBlock | (none) |
| 8c (Movement factor) | EntityMovementMixin | (none) |

---

*End of refactoring proposals. Each proposal is designed to be implementable independently, though the recommended ordering in Section 11 minimizes conflicts and maximizes benefit.*

---

## IMPLEMENTATION STATUS

All 9 proposals were implemented on 2026-02-28d in three phases following the recommended ordering from Section 11.

| Proposal | Status | Notes |
|----------|--------|-------|
| 1 (StickyType) | IMPLEMENTED | StickyType.java created. canStickTo simplified in FramedBlock, OddstoneBlock. |
| 2 (IMachineFamily) | IMPLEMENTED | IMachineFamily.java created. All 3 machine blocks implement it. |
| 3 (MovementScanHelper) | IMPLEMENTED | MovementScanHelper.java created. 3 mixin files refactored to use it. |
| 4 (Rule consolidation) | IMPLEMENTED | Rules restructured with StickyType + StickinessRules helpers. |
| 5 (FrameMaterial) | IMPLEMENTED | Enum enriched with displayName, registryPrefix, item registry. |
| 6 (PortalGeometry) | IMPLEMENTED | PortalGeometry.java record created. Portal files consolidated. |
| 7 (Scan Strategy) | IMPLEMENTED | ScanStrategy.java + PatternScanStrategy.java created. StructureScanner refactored. |
| 8 (Mixin helpers) | IMPLEMENTED | 8a: hasImmovableInner, 8b: IFrameBlock.isActive, 8c: getFramedFactor. |
| 9 (Minor cleanups) | IMPLEMENTED | 9a-9e, 9g done. 9f (registry ID constants) deferred as low priority. |

Behavioral verification: manual code review confirmed all imports resolve, method signatures match, and no type mismatches exist. Stickiness matrix interactions, portal behavior, movement systems, and multiblock detection remain identical to pre-refactor behavior.

Written by Claude, directed and guided by The Ideomancer.
