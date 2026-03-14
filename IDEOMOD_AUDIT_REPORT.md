# IDEOMOD CODEBASE & DOCUMENTATION AUDIT REPORT
*Comprehensive audit of code quality, documentation consistency, and architectural health.*
*Conducted: 2026-03-12*
*Written by Claude, directed and guided by The Ideomancer.*

---

## EXECUTIVE SUMMARY

This audit examined every Java source file, all five documentation files, all resource files, and cross-referenced documentation claims against actual code. Findings are organized into three categories: **Code Issues** (bugs, performance, fragility), **Documentation Inconsistencies** (docs that don't match code), and **Architectural Recommendations** (patterns that would scale better long-term).

Overall the codebase is impressively well-organized for a mod of this complexity. The three-layer stickiness system, the strategy-pattern scanner, and the IPistonMovable pipeline are all sound designs. The issues below are refinements, not fundamental problems.

---

## PART 1: CODE ISSUES

### 1.1 CRITICAL -- Thread Safety

~~**A. MachineConductorBlock.shouldSignal is an instance boolean, not ThreadLocal**~~

~~The `shouldSignal` field is a plain boolean on the block instance. Blocks in Minecraft are singletons -- there's one `MachineConductorBlock` object shared by every conductor in the world. If two conductors run `getIncomingSignalFromFace()` on different threads (e.g., during chunk loading or parallel neighbor updates), one thread could set `shouldSignal = false` while another is mid-scan, causing incorrect signal reads.~~

~~In vanilla, `RedStoneWireBlock.shouldSignal` has the same pattern and the same bug -- but vanilla only has one wire type. For us, this is a ticking time bomb as we add more conductor-like specializations.~~

*RESOLVED 2026-03-13: Converted to `static final ThreadLocal<Boolean>` with try-finally safety wrapper.*

~~**B. FrameActivation.activatedVanillaMetals is a plain HashSet mutated without synchronization**~~

~~`activatedVanillaMetals` is a `static HashSet<Long>` that gets cleared and rebuilt during every `recalculate()` call, but queried by `isVanillaMetalActivated()` which could be called from stickiness checks on a different code path. While Minecraft is mostly single-threaded, chunk loading and Forge parallel dispatch create edge cases.~~

*RESOLVED 2026-03-14: Changed to volatile reference with atomic swap. New set built locally during recalculate(), swapped in atomically at the end. Readers always see a consistent snapshot.*

~~**C. ThreadLocal cleanup gaps in PistonStructureResolverMixin**~~

~~`ideomod$inBranching` and `ideomod$firstCanStickToCall` are toggled at HEAD/TAIL injection points, but if an exception is thrown between HEAD and TAIL, the ThreadLocal never resets. Subsequent piston operations on that thread would see contaminated state, causing incorrect stickiness calculations. This could manifest as blocks sticking when they shouldn't, or vice versa.~~

*RESOLVED 2026-03-13: Added defensive reset at HEAD of addBranchingBlocks -- if inBranching is already true, warns and resets. Also resets call-pair toggle at HEAD.*

~~**D. ThreadLocal cleanup gap in PistonBaseBlockMixin.pullTargetPos**~~

~~Same pattern: set before retraction logic, cleared at RETURN. If an exception occurs mid-retraction, the ThreadLocal retains a stale BlockPos, causing the next piston retraction to wrongly apply oddstone immunity to a position in the previous operation.~~

*RESOLVED 2026-03-13: Added defensive reset at HEAD of triggerEvent -- if pullTargetPos is non-null when entering, warns and clears before setting new value.*

---

### 1.2 HIGH -- Logic Bug

~~**E. OddstonePortalSavedData.unregisterPortal() has an impossible condition**~~

~~In `unregisterPortal()`, the code checks:~~
```
if (pos.equals(addressEntries[0]) && pos.equals(addressEntries[1]))
```
~~A single BlockPos cannot equal both slots simultaneously (they're different portal positions). This means the "both slots match" cleanup path never executes, leaving stale entries in `portalsByAddress`. Over time, this causes a memory leak as portals are created and destroyed.~~

*RESOLVED 2026-03-13: Rewrote slot cleanup to use || with cleaner branch structure. Single pass checks both slots independently.*

---

### 1.3 HIGH -- Performance

**F. FrameActivation.recalculate() has no debouncing**

Every `neighborChanged`, `onPlace`, and `onRemove` call triggers a full network wipe-and-rebuild. Placing 50 frame blocks in sequence triggers 50 complete BFS recalculations, each discovering the same growing network. For a 15-block-radius network, each recalculation visits hundreds of positions.

This is the single biggest performance concern in the codebase. It won't cause issues in small builds, but a player who builds a large frame network will see noticeable lag during construction.

*Potential fix:* Mark the network as dirty and schedule a single recalculation for the end of the tick using `level.scheduleTick()` or a server tick event. This batches all changes in a single tick into one recalculation.

*RESOLVED 2026-03-14: Root cause was missing `"render_type": "minecraft:cutout"` in model JSON. The indicator overlay's transparent pixels rendered as opaque black in the default "solid" render type, creating a black shell at power 0. Added cutout render type, removed unnecessary tintindex from base layer.*

**G. PistonStructureResolverMixin.ideomod$addVanillaMetalConductors uses O(n^2) fixed-point loop**

The vanilla metal conductor scanning repeatedly snapshots `toPush`, iterates all positions checking all 6 faces, and repeats until no new blocks are found. For a chain of 50 vanilla metals, this loops ~50 times with growing snapshot size. No iteration limit exists.

*Fix:* Replace with a single BFS pass that tracks a frontier of newly-added positions, only checking their neighbors. Add a max iteration cap (e.g., 64) as a safety valve.

**H. MovementScanHelper.scanSympathetic() rebuilds snapshots every iteration**

The sympathetic scan creates a new ArrayList snapshot each pass, re-iterating all known oddstone positions and checking all 6 faces at all distances. With radius 5, each oddstone checks 30 positions per pass. With 12 oddstones chaining, worst case is ~360 checks per pass times multiple passes.

*Fix:* Use a BFS queue approach -- when a new oddstone is found, only check ITS neighbors next pass, not the entire accumulated set.

**I. FramedBlock does redundant block entity lookups**

Nearly every delegated method (mining, sound, friction, light, redstone, pathfinding, stepOn, fallOn, entityInside, etc.) calls `getFramedBE(level, pos)` independently. Methods called in sequence (like during mining) re-fetch the same block entity multiple times per tick.

*Mitigation:* This is minor individually but adds up. A helper that takes a pre-fetched BE or a method that batches the lookups would help, but isn't urgent.

---

### 1.4 MEDIUM -- Missing Validation

~~**J. OddstoneConfig sympathetic radius is unbounded**~~

~~If the config file is edited to set radius = 1000, the sympathetic scan will iterate across an enormous volume, causing a server hang. No cap is applied.~~

*RESOLVED 2026-03-13: Tightened cap from max 32 to max 16. `Math.max(1, Math.min(16, radius))` in parseConfig().*

~~**K. MovingBlockEntityData key collision risk**~~

~~Keys are built by string concatenation: `dimensionKey + ":" + x + "," + y + "," + z`. While dimension names in vanilla don't contain colons or commas, modded dimensions could theoretically create collisions.~~

*RESOLVED 2026-03-14: Changed delimiter to pipe '|' between dimension and coords, semicolons ';' between x/y/z. Neither character is valid in ResourceLocation strings.*

~~**L. StructureManager chunk iteration during block entity search**~~

~~`getBlockEntities().keySet()` is iterated without a defensive copy. If a chunk is modified during iteration (unlikely but possible during rapid block changes), a ConcurrentModificationException could occur.~~

*RESOLVED 2026-03-14: Wrapped in `new HashSet<>(chunk.getBlockEntities().keySet())` before iterating.*

---

### 1.5 LOW -- Code Quality

**M. Machine blocks have extensive code duplication**

`use()`, `getDrops()`, `getCloneItemStack()`, `newBlockEntity()`, and lifecycle hooks are nearly identical across MachineFrameBlock, MachineTrackBlock, MachineTrolleyBlock, and MachineConductorBlock. Each new specialization copies all of these.

*Recommendation:* Extract an abstract `AbstractMachineBlock` base class with default implementations. Specializations only override `getMachineType()`, `canStickTo()`, and their unique behavior. This would cut ~200 lines of duplication and make adding new specializations a 30-line task instead of a 150-line task.

~~**N. Debug logging left at INFO level**~~

~~PistonStructureResolverMixin contains `[BUG10]` logging at INFO level and MachineConductorBlock has debug logging comments. These should be removed or moved to DEBUG before any public release.~~

*RESOLVED 2026-03-13: All 19 BUG10 INFO log statements in PistonStructureResolverMixin demoted to DEBUG.*

~~**O. MachineFrameBlockEntity.inputFace is shared by all specializations**~~

~~The `inputFace` field lives on the shared block entity. If a future specialization (Toggle, Pulser) also needs directional I/O, it will collide with Conductor's inputFace. This isn't a bug today but is a landmine for future work.~~

~~*Recommendation:* When adding the next directional specialization, consider either (a) a Map<String, Direction> on the BE keyed by specialization name, or (b) specialization-specific BE subclasses.~~

*RESOLVED 2026-03-14: inputFace is now reset to null in triggerSwapCheck() on every specialization swap. Each specialization starts with a clean slate and sets its own inputFace when it first receives a signal. The shared field is safe because only one specialization is active at a time.*

~~**P. StructureDefinition ScanMode enum has stub values**~~

~~PROXIMITY and PROXIMITY_WALLED exist as enum values but throw UnsupportedOperationException if used. This could surprise someone who tries to use them.~~

*RESOLVED 2026-03-14: Added per-value javadoc clearly stating which modes are implemented and which will throw UnsupportedOperationException.*

---

## PART 2: DOCUMENTATION INCONSISTENCIES

### 2.1 Missing Files

~~**Q. BLOCK_EVENT_LESSON.md is referenced but doesn't exist**~~

~~CODEBASE_REFERENCE.md references it ("see BLOCK_EVENT_LESSON.md") but the file was not found in the project root or anywhere in the repo. Either it was deleted or never created.~~

*RESOLVED 2026-03-13: File exists in the project. Was present but not included in the audit's file scan.*

### 2.2 Feature Counts

~~**R. Oddstone feature count is inconsistent**~~

~~CODEBASE_REFERENCE.md line 30 says "5-feature core block" but the section header (line 256) says "6 features in one block." OddstoneBlock.java implements 5 features. The DESIGN doc lists "Sticky piston immunity" as [IDEA] (not implemented) but CODEBASE_REFERENCE section 5 describes pull immunity as implemented in PistonBaseBlockMixin. So the actual count depends on whether you count pull immunity as an Oddstone feature or a mixin feature.~~

*RESOLVED 2026-03-13: Settled on 6 features (pull immunity counts -- it's Oddstone behavior even though implemented in PistonBaseBlockMixin). Updated CODEBASE_REFERENCE and DOCUMENTATION to "6-feature" / "6 features". Updated DESIGN doc pull immunity from [IDEA] to [DONE].*

### 2.3 Missing Documentation Entries

~~**S. EnderPearlPortalHandler.java exists in FILETREE but isn't documented**~~

~~The file exists in `block/portal/` and appears in FILETREE.txt but is absent from CODEBASE_REFERENCE.md's package structure listing and from the Documentation's portal system description.~~

*RESOLVED 2026-03-13: Added to package listings in both CODEBASE_REFERENCE.md and IDEOMOD_DOCUMENTATION.md.*

~~**T. FURNACE_ARRAY_FRAME tag is missing from ModTags.java**~~

~~Documentation lists 4 custom tags but ModTags.java only defines 3 TagKey constants (PORTAL_CORNER_BLOCKS, REDSTONE_COMPONENTS, FRAMEABLE_BLOCKS). The FURNACE_ARRAY_FRAME tag exists as a JSON file and works fine (Minecraft loads tags from JSON regardless of Java constants), but it means there's no compile-time reference for it.~~

*RESOLVED 2026-03-13: TagKey constant added to ModTags.java. FurnaceArrayDefinition should be updated to use ModTags.FURNACE_ARRAY_FRAME instead of inline tag reference.*

### 2.4 Stale/Incomplete Information

~~**U. Stickiness matrix in CODEBASE_REFERENCE is incomplete**~~

~~The matrix has 8 rows but STICKINESS_SYSTEM.md and DOCUMENTATION.md have 15 rows (including Framed Slime, Framed Honey, Framed Odd Slime, Framed Oddstone active/inactive, Framed Sticky inactive). The overlapping rows are consistent, but the CODEBASE_REFERENCE version is misleading because it omits the framed block interactions entirely.~~

*RESOLVED 2026-03-13: Expanded CODEBASE_REFERENCE matrix to full 14 rows matching STICKINESS_SYSTEM.md. Added cross-reference note in IDEOMOD_DOCUMENTATION.md pointing to the detailed versions.*

~~**V. Frameable blocks count is stale**~~

~~Documentation says "~290 vanilla blocks" but the actual tag contains 311 minecraft entries + 2 ideomod entries = 313 total.~~

*RESOLVED 2026-03-13: Updated to "~310 vanilla + 2 ideomod blocks" in both CODEBASE_REFERENCE.md and IDEOMOD_DOCUMENTATION.md.*

~~**W. Known Issues #12 and #13 are mentioned inline but not in the Known Issues section**~~

~~The MachineConductorBlock description references issues #12 (dust sees strong power through solid blocks) and #13 (base texture renders black), but these aren't listed in the numbered Known Issues section at the bottom of CODEBASE_REFERENCE.~~

*RESOLVED 2026-03-13: Added #12 and #13 to Known Issues sections in both CODEBASE_REFERENCE.md and IDEOMOD_DOCUMENTATION.md.*

~~**X. Oddstone recipe contradiction**~~

~~DESIGN doc says "Recipe | None -- natural resource only. Must be mined." but `data/ideomod/recipes/oddstone.json` contains a working crafting recipe (Stone corners + Redstone center = 4x Oddstone).~~

*RESOLVED 2026-03-13: Design doc updated to flag the recipe as an auto-generated placeholder from early development that should be removed. Oddstone is confirmed as natural-only resource.*

---

## PART 3: ARCHITECTURAL RECOMMENDATIONS

### 3.1 Introduce an Abstract Machine Block Base Class

**Problem:** Every machine specialization duplicates ~150 lines of boilerplate (GUI opening, drop handling, clone stack, block entity creation, canStickTo delegation, lifecycle hooks calling FrameActivation).

**Recommendation:** Create `AbstractMachineBlock extends Block implements EntityBlock, IMachineFamily` with:
- Default `use()` (open GUI)
- Default `getDrops()` (conditional NBT)
- Default `getCloneItemStack()` (registry-based)
- Default `newBlockEntity()` (return MachineFrameBlockEntity)
- Default `onPlace/onRemove/neighborChanged` (call FrameActivation)
- Default `isStickyBlock()` (return true)
- Default `canStickTo()` (delegate to canMachineFamilyStickTo + permissive entries)
- Abstract `getMachineType()`

Specializations then only override their unique behavior. This cuts the work for each new specialization from ~150 lines to ~30 lines and eliminates the "fix it in 4+ places" problem.

### 3.2 Batch Frame Activation Recalculations

**Problem:** Frame activation is wipe-and-rebuild, triggered per-block-change. Large networks recalculate completely on every single block placed or broken.

**Recommendation:** Implement a "dirty network" system:
1. When a block change triggers recalculation, instead of running immediately, add the change position to a `Set<BlockPos> dirtyPositions`.
2. At the end of the server tick (via `TickEvent.ServerTickEvent`), run one recalculation per dirty network.
3. This naturally batches all changes in a tick into a single pass.

This is the same pattern vanilla redstone uses (schedule updates rather than process immediately) and would dramatically reduce lag during construction.

### 3.3 Replace Fixed-Point Loops with BFS Queues

**Problem:** Both `ideomod$addVanillaMetalConductors` (mixin) and `scanSympathetic` (MovementScanHelper) use snapshot-and-rescan loops that are O(n^2) in the worst case.

**Recommendation:** Refactor both to use a proper BFS queue:
- Maintain a `Queue<BlockPos> frontier` of newly-discovered positions
- Each iteration, only check the frontier's neighbors
- Move checked positions to a `visited` set
- Continue until frontier is empty

This converts both algorithms from O(n^2) to O(n) where n is the number of positions in the result. The code would also be simpler and easier to reason about.

### 3.4 Consider a Tag for Immovable Inner Blocks

~~**Problem:** PistonBaseBlockMixin hardcodes OBSIDIAN, CRYING_OBSIDIAN, REINFORCED_DEEPSLATE as immovable inners. The destroySpeed == -1 check catches most cases, but modded blocks with custom push reactions won't be caught.~~

*RESOLVED 2026-03-14: Created `ideomod:immovable_when_framed` block tag with the three previously hardcoded blocks. PistonBaseBlockMixin now checks `innerState.is(ModTags.IMMOVABLE_WHEN_FRAMED)`. DestroySpeed == -1 fallback still runs for unlisted indestructible blocks. TagKey constant added to ModTags.java.*

### ~~3.5 Portal Geometry Caching~~

~~**Problem:** `OddstonePortalBlock.linkedTeleport()` calls `PortalGeometry.detect()` twice per teleport (once for source, once for destination). For large portals, each detection scans hundreds of block positions.~~

~~**Recommendation:** Cache PortalGeometry in OddstonePortalSavedData alongside the portal entry. Invalidate on deactivation. This eliminates redundant scans during teleportation.~~

*RESOLVED 2026-03-14: Interior geometry (bottom-left, width, height) computed from FrameResult corners at registration time and cached in PortalEntry. Persisted to NBT. Teleportation reads cached values (zero block lookups). Falls back to live PortalGeometry.detect() for old saves.*

### 3.6 Long-Term: Event-Driven vs. Polling for Stickiness

**Current state:** Stickiness is evaluated at piston-push time by the resolver mixin. This is correct and performant.

**Observation:** As the number of stickiness rules grows (each new block type potentially adds canStickTo cases and immunity rules), the evaluation path becomes deeper. Currently there are 4 consolidated grab immunity rules with carve-outs, 1 directional rule, and 6+ canStickTo implementations.

**Recommendation:** No immediate action needed, but consider whether a "stickiness capability" system (where blocks declare their stickiness profile as data rather than code) would be cleaner at scale. Something like:
```
StickinessProfile.builder()
    .type(StickyType.MACHINE_FRAME)
    .sticksTo(MachineFamily.class, FrameFamily.whenActive())
    .permissiveTo(SlimeBlock.class, HoneyBlock.class)
    .build()
```
This would replace scattered canStickTo implementations with declarative profiles, making the stickiness matrix readable directly from code.

---

## PART 4: MECHANICAL OBSERVATIONS

### ~~4.1 Odd Slime Directional Stickiness Clarity~~

~~Current behavior: Odd Slime only grabs when push direction matches its FACING. This is implemented cleanly but may be unintuitive to players. Consider whether a particle effect or visual cue on the "sticky face" would help communicate the mechanic (purely a design thought, not a code issue).~~

*RESOLVED 2026-03-14: Already addressed with a directional texture on the sticky face.*

### ~~4.2 Frame Activation Anti-Signal Power~~

~~The anti-signal system (powered Machine Frames suppress activation) is powerful but has an asymmetry: Machine Frames are the ONLY blocks that emit anti-signal, while ALL Machine Family blocks emit activation. This means you can suppress a frame network by powering a single Machine Frame in the chain, but you can't suppress activation from a Track or Trolley. This is documented and intentional, but it creates a hierarchy where Machine Frame is both the universal connector AND the only suppressible block -- it does a lot of jobs.~~

*RESOLVED 2026-03-14: Intentional design. Anti-signal is Machine Frame's unique function alongside being the universal connector.*

### ~~4.3 Sympathetic Movement + Push Limit Interaction~~

~~Sympathetic chains count toward the 12-block push limit. With radius 5, it's possible to create configurations where the 12-block limit is consumed entirely by sympathetic links, leaving no room for the actual payload. Players might find this frustrating if they build a sympathetic relay chain and discover it can't push anything. Consider whether sympathetic links should have their own separate limit, or whether the 12-block limit should be increased when sympathetic movement is involved.~~

*RESOLVED 2026-03-14: Accepted as intended behavior. The push limit applies uniformly.*

### 4.4 Machine Conductor Known Issues Impact

Issue #12 (dust sees strong power through solid blocks) is noted as known. ~~Issue #13 (base texture renders black)~~ was resolved 2026-03-14 — root cause was missing cutout render type on the two-layer model. Issue #12 could cause confusing behavior for players who place redstone dust adjacent to conductors expecting normal signal behavior. If the Conductor is intended to eventually replace vanilla redstone in certain configurations, this will need a RedStoneWireBlock mixin to fix the strong-power-through-solids issue -- it's not something that can be fixed from the Conductor side alone.

---

## SUMMARY: PRIORITY ACTION LIST

### Immediate (before next testing session)
1. ~~Fix `shouldSignal` thread safety in MachineConductorBlock~~ *DONE 2026-03-13 (ThreadLocal + try-finally)*
2. ~~Fix `unregisterPortal()` impossible condition in OddstonePortalSavedData~~ *DONE 2026-03-13 (&& -> || with cleaner cleanup)*
3. ~~Add sympathetic radius cap in OddstoneConfig~~ *DONE 2026-03-13 (max 32 -> max 16)*
4. ~~Remove or demote BUG10 debug logging~~ *DONE 2026-03-13 (19 INFO -> DEBUG)*

### Short-term (next few sessions)
5. ~~Add defensive ThreadLocal resets in PistonStructureResolverMixin~~ *DONE 2026-03-13 (inBranching + call-pair toggle)*
6. ~~Add defensive ThreadLocal reset in PistonBaseBlockMixin~~ *DONE 2026-03-13 (pullTargetPos)*
7. ~~Add FURNACE_ARRAY_FRAME to ModTags.java~~ *DONE 2026-03-13 (TagKey constant added; FurnaceArrayDefinition still uses inline ref)*
8. ~~Add Known Issues #12, #13 to the Known Issues section~~ *DONE 2026-03-13*
9. ~~Document EnderPearlPortalHandler in CODEBASE_REFERENCE~~ *DONE 2026-03-13*
10. ~~Fix Oddstone feature count inconsistency~~ *DONE 2026-03-13 (settled on 6)*
11. ~~Add BLOCK_EVENT_LESSON.md or remove references~~ *RESOLVED 2026-03-13 (file exists)*
12. ~~Fix Oddstone recipe contradiction in DESIGN doc~~ *DONE 2026-03-13 (flagged as placeholder for removal)*

### Medium-term (as part of next feature work)
13. Extract AbstractMachineBlock base class
14. Implement frame activation batching/debouncing
15. Replace fixed-point loops with BFS in metal conductor scanning
16. ~~Cache PortalGeometry in SavedData~~ *DONE 2026-03-14 (geometry computed from FrameResult corners at registration, cached in PortalEntry, persisted to NBT)*
17. ~~Update stickiness matrix in CODEBASE_REFERENCE~~ *DONE 2026-03-13 (expanded to full 14 rows)*
18. ~~Update frameable blocks count in docs~~ *DONE 2026-03-13 (~290 -> ~310)*

### Long-term (architecture evolution)
19. Consider declarative stickiness profiles
20. ~~Consider tag-based immovable inner blocks~~ *DONE 2026-03-14 (IMMOVABLE_WHEN_FRAMED tag + ModTags constant + PistonBaseBlockMixin updated)*
21. ~~Plan inputFace field strategy for multiple directional specializations~~ *DONE 2026-03-14 (inputFace reset to null on every specialization swap in triggerSwapCheck())*

### Resolved outside priority list (2026-03-14)
- ~~(B) FrameActivation.activatedVanillaMetals thread safety~~ *DONE (volatile + atomic swap)*
- ~~(K) MovingBlockEntityData key collision risk~~ *DONE (pipe + semicolon delimiters)*
- ~~(L) StructureManager defensive copy~~ *DONE (HashSet wrapper)*
- ~~(P) StructureDefinition ScanMode stub docs~~ *DONE (per-value javadoc)*
- ~~(O) inputFace shared field landmine~~ *DONE (reset to null on specialization swap)*
- ~~Issue #13 (conductor black texture)~~ *DONE (cutout render type + tintindex cleanup)*
- ~~4.1 Odd Slime directional clarity~~ *DONE (directional texture already exists)*
- ~~4.2 Anti-signal asymmetry~~ *Accepted as intentional design*
- ~~4.3 Sympathetic push limit~~ *Accepted as intended behavior*

Written by Claude, directed and guided by The Ideomancer.
