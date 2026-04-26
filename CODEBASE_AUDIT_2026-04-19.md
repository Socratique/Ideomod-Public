# IDEOMOD CODEBASE AUDIT -- Post-Transmitter / Grabber / Redstone Rod
*Conducted: 2026-04-19 | Verified: 2026-04-19*
*Written by Claude, directed and guided by The Ideomancer.*

> **Resolution status (2026-04-19, end of session):**
> - **3A** OddstoneConfig dead code -- **RESOLVED** (file deleted).
> - **3B** FurnaceArrayDefinition duplicate FRAME_TAG -- **RESOLVED** (consolidated to ModTags.FURNACE_ARRAY_FRAME).
> - **3C** activatedVanillaMetals dimension/cleanup -- **RESOLVED** (entire vanilla-metal-as-conductor feature removed; the set no longer exists).
> - All other findings (regression carryovers + 2A/2B/2C/3D/3E) remain open.

---

## OVERVIEW

This audit follows the 2026-03-26 audit (see `CODEBASE_AUDIT_2026-03-26.md`). Since that pass, the following subsystems were added:

- Machine Transmitter (wireless redstone via Oddstone channels)
- Machine Grabber (toggleable sticky block -- slime + honey variants)
- Piston settlement window (1-tick `pistonSettling` guard on edge-triggered blocks)
- Two-phase spec-item change lifecycle (`onSpecItemChanging` / `onSpecItemChanged`)
- Hollowstone inner-block transit rendering
- Redstone Rod handheld pulse tool

The audit is in three parts:
1. Regression check against the 2026-03-26 findings.
2. New findings specific to the post-March 26 subsystems.
3. New cross-cutting findings missed by the prior audit.

Every finding has been verified against the current source. Items that turned out to be false positives during verification are noted at the end.

---

## PART 1 -- REGRESSION CHECK vs 2026-03-26 AUDIT

| # | Prior Finding | Status (2026-04-19) |
|---|---------------|----------------------|
| 1 | Retraction sticky group scan depth hardcoded at 12 | **FIXED.** `PistonBaseBlockMixin` now uses `MovementScanHelper.getBasePushLimit()` (line 396). |
| 2 | Serialization logic duplicated 3x in `MachineFrameBlockEntity` | **UNCHANGED.** Same blocks at `saveAdditional()` (L453-480), `getUpdateTag()` (L557-585), `saveMovementData()` (L600-638). No shared helper extracted. |
| 3 | Default-value fallbacks duplicated (`PositionBoundContainer` vs `MachineFrameBlockEntity`) | **UNCHANGED.** Still `1`, `1`, `20` in both places with no named constants. |
| 4 | ThreadLocal try-finally missing on piston mixins | **UNCHANGED.** Defensive resets at HEAD still serve as the safety net. |
| 5 | Block update flag magic numbers (18, 68, 82) | **UNCHANGED.** Still inline in `BlockMoveHelper` (L204, 217, 238) and `MachinePistonBlock` (L226). |
| 6 | `POWERED` property defined per-block | **WORSE.** Was 4 blocks; now **8** (added: MachineTransformerBlock L43, MachineGrabberBlock L51, MachineEngineBlock L61, MachineTransmitterBlock L73). Every new specialization re-declares its own copy. |
| E | Transit item orphaning on pipe de-specialization | **FIXED.** `MachineSwapHelper.ejectSecondaryItems()` (L270-287) now clears pipe transit slot and engine fuel slot before swap. |
| D | Toggle reschedule `instanceof` special case in `onMovementRestored` | **PARTIALLY FIXED.** Explicit `instanceof MachineToggleBlock` branch still present, but non-toggle path now uses generic property lookup. |

**Net:** 2 fixes (1, E). 1 partial (D). 1 regressed with volume (6 -- scope grew). 4 unchanged (2, 3, 4, 5).

---

## PART 2 -- NEW FINDINGS IN POST-MARCH 26 SUBSYSTEMS

### 2A. MachineTransmitterBlock ThreadLocal try-finally coverage

**Severity:** MEDIUM

**What it is:** The transmitter's anti-feedback architecture (described in the changelog) uses two ThreadLocals: `shouldSignal` (self-read suppression) and `updatingPos` (re-entrancy guard). Read sites correctly use try-finally (e.g., `getExternalSignalStrength` at L164-171 wraps set/clear). Write-flow sites that call `reevaluateAfterChange()` do not uniformly wrap set/clear around the entire callee's body -- several paths set `updatingPos` then call into code that could throw before the clear.

**Why it matters:** Same class of hazard as Finding #4 of the prior audit, but the transmitter is more exposed because it runs in `neighborChanged` paths that other mods' mixins may extend. A stale `updatingPos` would cause a future tick to silently suppress a legitimate read on a neighbouring transmitter.

**Mitigation in place:** The same defensive-reset pattern used on the piston mixins is NOT used here; recovery depends solely on the try-finally blocks being exhaustive.

**Fix:** Standardize on try-finally for every `updatingPos.set(...)` call, or introduce a defensive reset like the piston mixins.

---

### 2B. Spec-item lifecycle: `onSpecItemChanged` sees the post-swap block

**Severity:** LOW (observation / documentation)

**What it is:** `MachineFrameBlockEntity.setItem()` calls `onSpecItemChanging(oldItem)` on the pre-swap block, `triggerSwapCheck(newItem)`, then `onSpecItemChanged(newItem)` resolved against `level.getBlockState(worldPosition).getBlock()` -- the post-swap block. This is the documented design, and it is correct for the common case. What is not documented is that a cross-block swap means `onSpecItemChanged` is dispatched on a *different* class than `onSpecItemChanging`.

**Why it matters:** A future contributor adding matched cleanup + initialization logic may assume the two callbacks run on the same block instance. They do not.

**Fix:** Add a javadoc note on both virtuals clarifying that, on a cross-block swap, the two callbacks are dispatched on different block classes.

---

### 2C. RedstoneRodSignalManager: no world-unload cleanup

**Severity:** LOW

**What it is:** `RedstoneRodSignalManager.ACTIVE_PULSES` is a static `ConcurrentHashMap` keyed by `(dimension, pos, direction)`. `clearAll()` is wired to `ServerStoppingEvent` only (Ideomod.java). There is no `LevelEvent.Unload` handler, so pulses targeted at a dimension that unloads mid-pulse remain in the map until the server stops.

**Why it matters:** Practical impact is minimal -- pulses are 2 ticks, so leaked entries age out of meaning before the dimension could plausibly reload. This is a cleanliness issue, not a correctness one.

**Fix:** Add a `LevelEvent.Unload` subscriber that filters `ACTIVE_PULSES` by dimension key.

---

### 2D. `MachineTransmitterBlock.POWERED` and `SOURCE` are unique to Transmitter, but eight `POWERED` declarations now exist

**Severity:** LOW (reiterates prior Finding #6)

**What it is:** See regression row #6 above. Worth separating out: `SOURCE` is legitimately transmitter-specific, but `POWERED` is not. Moving `POWERED` into `AbstractMachineBlock` (or a shared holder) would remove 7 redundant declarations without affecting transmitter's `SOURCE`.

---

## PART 3 -- NEW CROSS-CUTTING FINDINGS

### 3A. `OddstoneConfig` is fully dead code

**Severity:** HIGH (maintenance trap)

**What it is:** `config/OddstoneConfig.java` is marked `@Deprecated` with a comment saying "replaced by IdeomodConfig, safe to delete." Verified: zero external references in the source tree (`Grep OddstoneConfig` returns only the file itself). It still contains a full init lifecycle: static lock, JSON file I/O, `reloadConfig()`, per-mob avoidance flags, sympathetic radius.

**Why it matters:** The class is self-contained and would happily create and read `config/ideomod/oddstone.json` if anyone ever called it. A future contributor who greps for "sympathetic radius" will find two answers (real: `IdeomodConfig.getSympatheticRadius()`, ghost: `OddstoneConfig.getSympatheticRadius()`). The deprecation comment says to delete the file; that has not happened.

**Fix:** Delete `OddstoneConfig.java`. No code depends on it.

---

### 3B. `FurnaceArrayDefinition.FRAME_TAG` duplicates `ModTags.FURNACE_ARRAY_FRAME`

**Severity:** MEDIUM

**What it is:** Two `TagKey<Block>` objects reference the same tag resource (`ideomod:furnace_array_frame`):
- `ModTags.FURNACE_ARRAY_FRAME` (L58-59) -- the intended central registry, **never referenced anywhere** (verified: `Grep ModTags\.FURNACE_ARRAY_FRAME` returns zero matches).
- `FurnaceArrayDefinition.FRAME_TAG` (L34-36) -- the actual in-use key, referenced at L48.

`CODEBASE_REFERENCE.md` flags this as a known inconsistency, but the code has not been reconciled.

**Why it matters:** Two `TagKey` singletons for the same logical tag are hash-equal but separate instances. If the tag path changes, both must be updated. The orphan in `ModTags` misleads readers about where tags are registered.

**Fix:** Replace `FurnaceArrayDefinition.FRAME_TAG` with `ModTags.FURNACE_ARRAY_FRAME`. Delete the duplicate.

---

### 3C. `FrameActivation.activatedVanillaMetals` has no dimension scoping or cross-session cleanup

**Severity:** LOW

**What it is:** `activatedVanillaMetals` is a `Set<Long>` of packed BlockPos values (L248). Positions are added on frame activation and removed on individual frame deactivation (L304). There is no `clearAll()`, no server-stop hook, and no dimension key in the packed long.

**Why it matters:** Two issues. (1) Positions from dimension A can technically match positions in dimension B -- `BlockPos.asLong()` ignores dimension. If a vanilla metal block sits at the same coordinates in both the overworld and the nether, activation state can bleed. (2) Leaked positions persist across world reloads within the same JVM (e.g., singleplayer: quit to menu, load a different save). Static set is not reset.

**Fix:** Either (a) add dimension to the key and a `LevelEvent.Unload` cleanup hook, or (b) at minimum add a `clearAll()` and call it on `ServerStoppingEvent` / `ClientPlayerNetworkEvent.LoggingOut`.

---

### 3D. `DrillBlock` is registered but not wired into the Machine Family swap system

**Severity:** LOW (intentional per design doc, but undocumented in code)

**What it is:** `DrillBlock` is registered via `ModBlocks.DRILL` and has a blockstate/model/loot table. The design doc marks it `[HOLD]` ("implemented and functional but on hold pending design review"). No comment in `DrillBlock.java` or the registrations explains this. A newcomer reading the code cannot tell whether the missing swap mapping is deliberate.

**Fix:** Add a `// [HOLD] -- see IDEOMOD_DESIGN.md "Drill Block"` comment at the registration site and on the class.

---

### 3E. `MachineFrameScreen.java` TODO is orphaned

**Severity:** LOW

**What it is:** The only TODO in the codebase (`MachineFrameScreen.java:46-47`) refers to a planned label improvement for Repeater/Transformer GUIs. No tracking reference.

**Fix:** Either do it or move to a tracking system. Not actionable from audit alone.

---

## FLYING MACHINE / EDGE CASES -- NEW SINCE PRIOR AUDIT

### Hollowstone Inner Block Transit

**Status:** WORKING CORRECTLY. Client-side capture in `BlockMoveHelper` (L162-188) stores hollowstone inner block IDs into `MovingFrameData` with `facing=NORTH, innerSticky=0`. Renderer reads from the same map.

### Piston Settlement Window (1 tick)

**Status:** VERIFIED WORKING for standard single-block push/pull cycles. Flag is set on `onPlace(movedByPiston=true)` and cleared on the scheduled tick one game tick later. The concern about multi-block push groups is theoretical: vanilla places all moved blocks atomically in one tick inside `PistonBaseBlock.moveBlocks`, so each settlement flag is set and cleared on the same shared timeline. No data-backed failure case was identified.

### Transmitter Anti-Feedback (5-layer architecture)

**Status:** VERIFIED. All five layers described in the changelog are present and load-bearing:
1. SOURCE blockstate mode exclusivity -- L115-117, L368-369
2. `shouldSignal` ThreadLocal self-read suppression -- L134, L147
3. `updatingPos` ThreadLocal re-entrancy guard -- L363
4. Receiver self-read guard in `tick()` -- L443
5. Source-power-off ordering (receivers off before source notifies) -- L493-497

The remaining concern is 2A above: try-finally coverage is not uniform for layer 3.

---

## REVISED SUMMARY OF RECOMMENDATIONS

**Highest priority (dead code, maintenance trap):**
1. **Delete `OddstoneConfig.java`** (3A) -- zero references, confusing presence.
2. **Consolidate `FurnaceArrayDefinition.FRAME_TAG` into `ModTags.FURNACE_ARRAY_FRAME`** (3B) -- one of them is orphaned.

**Carryover from prior audit (still worth doing):**
3. Extract shared serialization helper in `MachineFrameBlockEntity` (Prior #2).
4. Extract default-value constants in `MachineFrameBlockEntity` (Prior #3).
5. Move `POWERED` into `AbstractMachineBlock` -- now 8 duplicates (Prior #6 / 2D).
6. Name the block update flag magic numbers (Prior #5).

**New-subsystem polish:**
7. Standardize `updatingPos` try-finally in `MachineTransmitterBlock` (2A).
8. Add `LevelEvent.Unload` cleanup for `RedstoneRodSignalManager` and `FrameActivation.activatedVanillaMetals` (2C, 3C).
9. Document cross-block dispatch of `onSpecItemChanging` / `onSpecItemChanged` (2B).

**Nice-to-have:**
10. Annotate `DrillBlock` registration with its `[HOLD]` status (3D).
11. Add try-finally to piston mixins' ThreadLocals (Prior #4) -- defensive resets still cover most cases.

**Working correctly (verified):**
- Transmitter 5-layer anti-feedback architecture.
- Hollowstone transit rendering.
- Piston settlement window for standard cases.
- Redstone Rod signal injection via `LevelSignalMixin` (both `getSignal` and `getDirectSignal` covered).
- `MovingFrameData` cleanup -- `clearAll()` IS called at `BlockMoveHelper:167` and `PistonBaseBlockClientMixin:64` (a subagent incorrectly flagged this as never-called; verified false).

---

## FINDINGS THAT WERE DOWNGRADED OR DROPPED DURING VERIFICATION

- **`MovingFrameData.clearAll()` never called** -- FALSE. Verified calls at `BlockMoveHelper:167` and `PistonBaseBlockClientMixin:64`.
- **Piston settlement window fails on multi-block push groups** -- SPECULATIVE. Vanilla piston resolution places all blocks atomically in one tick; no failure case reproduced.
- **Spec-item-change re-entrancy race via observer chains** -- SPECULATIVE. No concrete path found where the pre-swap BE's state could leak into a re-swapped transmitter within the same tick. Retained as documentation-only (2B).
- **`ModColorHandlers` missing `@OnlyIn(Dist.CLIENT)`** -- NIT. The `value = Dist.CLIENT` on `@Mod.EventBusSubscriber` already prevents registration on a dedicated server; the class is in the `client/` package, and `@OnlyIn` on top of `Dist.CLIENT` subscribers is stylistic, not corrective.
- **`FrameActivation.flushIfDirty` incomplete call-site coverage** -- Only `PistonStructureResolverMixin.resolve()` reads frame activation state mid-tick. No other reader needs a flush. The current single call site is sufficient.

---

*Written by Claude, directed and guided by The Ideomancer.*
