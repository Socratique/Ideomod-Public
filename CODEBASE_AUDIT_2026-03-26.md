# IDEOMOD CODEBASE AUDIT — Machine Blocks & Flying Machine Edge Cases
*Conducted: 2026-03-26 | Verified: 2026-03-26*
*Written by Claude, directed and guided by The Ideomancer.*

---

## OVERVIEW

This audit examines the Ideomod codebase for "one source of truth" violations, duplicated logic, and edge cases that could cause issues with machine blocks in complex flying machine configurations. Each finding has been verified against the actual source code and changelog. Findings that were initially flagged but turned out to be already handled are noted as such.

---

## VERIFIED FINDINGS — THINGS THAT ACTUALLY NEED ATTENTION

### 1. Retraction Sticky Group Scan Depth Capped at 12 (PistonBaseBlockMixin)

**Severity:** HIGH — data loss in brace-enhanced machines

**What it is:** `PistonBaseBlockMixin` calls `MovementScanHelper.scanStickyGroup(level, startPos, 12)` with a hardcoded `12` during sticky piston retraction. This is NOT the push limit itself — `PistonStructureResolverMixin` correctly reads the dynamic limit from `IdeomodConfig.getBasePushLimit()` plus braces. However, the retraction *save scan* that preserves block entity data uses a separate hardcoded `12`.

**Why it matters:** If Machine Braces increase the push limit beyond 12, the resolver accepts the larger group, but the retraction save only scans 12 blocks deep. Any block entities beyond position 12 in the sticky group (repeater delays, transformer levels, pipe contents, toggle states, timer durations) lose their data when retracted by a sticky piston.

**Fix:** Replace the hardcoded `12` with `IdeomodConfig.getBasePushLimit()` plus brace calculation, or pass the calculated limit from the resolver context.

---

### 2. Serialization Logic Duplicated Three Times (MachineFrameBlockEntity)

**Severity:** MEDIUM — maintenance risk

**What it is:** `MachineFrameBlockEntity` contains nearly identical field-saving logic in three methods: `saveAdditional()`, `getUpdateTag()`, and `saveMovementData()`. Each writes the same fields (inputFace, repeaterDelay, transformerLevel, timerDuration, toggleState, timerRemaining, etc.) to a CompoundTag using the same TAG_ string constants.

**Verified:** All three methods write the same conditional pattern:
```
if (this.repeaterDelay != 1) tag.putInt(TAG_REPEATER_DELAY, ...)
if (this.transformerLevel != 1) tag.putInt(TAG_TRANSFORMER_LEVEL, ...)
...
```

**Fix:** Extract a shared `private void saveCommonFields(CompoundTag tag)` method called by all three.

---

### 3. Default Value Fallbacks in PositionBoundContainer (MachineFrameBlockEntity)

**Severity:** MEDIUM — maintenance risk

**What it is:** `PositionBoundContainer` has null-guard fallback returns for when the block entity can't be resolved: `getRepeaterDelay()` falls back to `1`, `getTransformerLevel()` falls back to `1`, `getTimerDuration()` falls back to `20`. These same defaults are defined as field initializers in `MachineFrameBlockEntity` but as bare inline numbers, not named constants.

**Why it matters:** If any default changes (say, timer default moves from 20 ticks to 40 ticks), both `MachineFrameBlockEntity`'s field initializer AND `PositionBoundContainer`'s fallback must be updated. No compiler or runtime check catches a mismatch.

**Fix:** Define `public static final` constants in `MachineFrameBlockEntity` (`DEFAULT_REPEATER_DELAY = 1`, `DEFAULT_TRANSFORMER_LEVEL = 1`, `DEFAULT_TIMER_DURATION = 20`) and reference them from `PositionBoundContainer`.

---

### 4. ThreadLocal Exception Safety (PistonStructureResolverMixin, PistonBaseBlockMixin)

**Severity:** LOW (practical) / MEDIUM (architectural)

**What it is:** ThreadLocal flags (`ideomod$inBranching`, `ideomod$pullTargetPos`, `ideomod$firstCanStickToCall`) are set at HEAD injections and cleared at TAIL/RETURN injections. No try-finally wrapping exists. If an exception is thrown between set and clear, the flag persists on the thread.

**Mitigating factors already in place:**
- Defensive reset checks at HEAD: both mixins log warnings and force-reset if a stale value is detected from a prior failure
- The vanilla methods being injected into (`addBranchingBlocks`, `triggerEvent`) rarely throw exceptions in practice
- The defensive resets mean a stale flag only persists for ONE subsequent call before being caught and corrected

**Why it's still worth noting:** The defensive resets are a safety net, not a guarantee. A rapid double-fire (two pistons in same tick) where the first throws could leave the second operating on stale state before the reset has a chance to fire. In practice this is extremely unlikely, but the architectural fix (try-finally) is simple and would make the safety net unnecessary.

**Fix:** Wrap the TAIL/RETURN clear logic in try-finally. Low urgency given the existing defensive resets.

---

### 5. Block Update Flag Magic Numbers (BlockMoveHelper, MachinePistonBlock)

**Severity:** LOW — readability only

**What it is:** `BlockMoveHelper` and `MachinePistonBlock` use hardcoded integer flags for `level.setBlock()` calls: `18`, `68`, `82`. These are bitfield combinations of vanilla's `Block.UPDATE_*` constants, copied from `PistonBaseBlock` without explanation.

**Verified locations:**
- `BlockMoveHelper.java` line 192: `18` (destroy + notify)
- `BlockMoveHelper.java` line 205: `68` (move + update)
- `BlockMoveHelper.java` line 226: `82` (vacate + update)
- `MachinePistonBlock.java` line 226: `18` (head removal)

**Fix:** Extract to named constants with comments explaining the bitfield composition. Pure readability improvement, no behavioral change.

---

### 6. POWERED Property Defined Per-Block (5 blocks)

**Severity:** LOW — maintenance note

**What it is:** Each redstone machine specialization defines its own `public static final BooleanProperty POWERED = BooleanProperty.create("powered")`. MachineToggleBlock, MachineRepeaterBlock, MachineTimerBlock, MachinePulserBlock each define identical copies. MachineConductorBlock uses a different `IntegerProperty POWER` (0-15 signal strength).

**Why it's low severity:** Since `BooleanProperty.create("powered")` returns identical objects (same string key), these are functionally interchangeable. No behavioral bug exists. The Conductor's `POWER` is intentionally different (signal strength vs. boolean).

**Fix (optional):** Define once in `AbstractMachineBlock` and reference from subclasses. Or leave as-is — it's a style preference.

---

## FINDINGS THAT WERE WRONG OR ALREADY HANDLED

### ~~Dimension Key Format Duplication~~ — ALREADY CENTRALIZED

**Original claim:** Dimension key format built independently in 4 files.

**Reality:** `MovingBlockEntityData` already has a private `makeKey(String dimensionKey, BlockPos pos)` method that is the single source of truth for key construction. The other files (`BlockMoveHelper`, `PistonBaseBlockMixin`, `PistonMovingBlockEntityMixin`) only build the `dimensionKey` string (`level.dimension().location().toString()`) and pass it to `MovingBlockEntityData.store()` / `retrieve()`. The actual `|` and `;` delimited key format lives in one place only.

### ~~Signal Decay Scattered~~ — ALREADY CENTRALIZED

**Original claim:** Signal decay `- 1` appears inline in multiple files.

**Reality:** Signal decay logic is centralized in `MachineRedstoneHelper` as the single source of truth (documented in its javadoc). Individual machine blocks call into the helper for signal detection. The `Math.max(0, signal - 1)` pattern in `MachineRedstoneHelper` is the canonical decay logic. Other blocks reference it, not duplicate it.

### ~~Piston Animation Speed Is a Magic Number~~ — SHARED VIA PROGRESS FIELD

**Original claim:** `0.5f` increment duplicated between block and renderer.

**Reality:** `MachinePistonBlock` writes `progress += 0.5f` to the block entity field. `MachinePistonArmRenderer` reads the interpolated progress via `be.getArmRenderProgress()`. The value is shared through the BE's progress field — the renderer never has its own `0.5f`. No duplication.

### ~~Hardcoded Push Limit 12~~ — MISLABELED

**Original claim:** Hardcoded `12` is the push limit, mismatching the config.

**Reality:** The `12` in `PistonBaseBlockMixin.scanStickyGroup()` is the sticky group scan depth, not the push limit. The actual push limit IS dynamically replaced in `PistonStructureResolverMixin` via `@ModifyConstant`, reading from `IdeomodConfig.getBasePushLimit()` + brace bonuses. However, the scan depth `12` still matters — see Finding #1 above for the corrected version of this concern.

---

## FLYING MACHINE EDGE CASES — VERIFIED

### A. Retraction Sympathetic Scan Performance

**Status:** CONFIRMED — O(n * m) where n=oddstones, m=group size

`PistonBaseBlockMixin.scanSympathetic()` calls `scanStickyGroup()` once per sympathetically-linked Oddstone found. For machines with many Oddstones, this is multiplicative but **functionally correct** — each oddstone's attached sticky group must be scanned independently to preserve all block entity data.

**Practical impact:** Only relevant for machines with many Oddstones in sympathetic chains. A machine with 6 Oddstones each attached to 12-block groups would trigger 6 separate BFS scans. This is noticeable but not game-breaking given typical machine sizes.

**Recommendation:** Optimization opportunity (single frontier-based scan), not a bug. Low priority unless players report tick lag with Oddstone-heavy machines.

---

### B. canStickTo Call-Pair Toggle Fragility

**Status:** CONFIRMED — works in practice, fragile in principle

The `ideomod$firstCanStickToCall` boolean toggle assumes vanilla calls `canStickTo()` exactly twice per `addBlockLine` backward walk iteration. Verification confirms vanilla DOES call it exactly twice (once for each direction of the bidirectional check). The toggle is reset to `true` at the start of each branching phase.

**Risk:** If a future Minecraft update changes the call pattern, or if another mod's mixin alters `addBlockLine`, the toggle could desync. In practice, this has been stable.

**Recommendation:** Document the assumption explicitly in the code. Consider adding a counter-based approach if stability concerns arise.

---

### C. Block Entity Data Consumed Before Existence Check

**Status:** CONFIRMED — intentional design, documented as "single consumer"

`MovingBlockEntityData.retrieve()` removes data from the map atomically. If the block entity doesn't exist at the destination, the data is logged as a warning and lost. This is intentional — it prevents double-restore bugs and ensures exactly-once semantics.

**Risk:** In rapid multi-step flying machines, if the animation completion and next movement overlap (possible with 1-tick pulses), the destination BE might not exist yet. The 2-tick animation window mitigates this for normal pistons. Machine Pistons also use 2-tick animations.

**Recommendation:** Document as known limitation. No code change needed unless data loss is observed in practice.

---

### D. Scheduled Ticks After Movement — Toggle Special Case

**Status:** CONFIRMED — handled but via instanceof check

`MachineFrameBlockEntity.onMovementRestored()` reschedules ticks for Timer and Repeater but skips Toggle (because re-ticking a powered Toggle would flip it off). This is handled with an explicit `instanceof MachineToggleBlock` check.

**Recommendation:** If future specializations need similar behavior, add a `shouldRescheduleAfterMovement()` method to `AbstractMachineBlock`. Low priority — only needed when a new specialization is added.

---

### E. Transit Item Orphaning in Pipes

**Status:** NEEDS VERIFICATION — could not confirm pipe-to-frame swap clears transit slot

When a Pipe is de-specialized (hopper removed from spec slot), the transit slot (slot 1) may retain its item in NBT. The item becomes inaccessible until re-specialization. This needs testing in-game.

**Recommendation:** Verify in-game. If confirmed, clear transit slot in `MachineSwapHelper.checkAndSwap()` when source is Pipe and target is not.

---

### F. Open GUI During Movement — WORKING CORRECTLY

`PositionBoundContainer` correctly resolves to the BE at the current world position. GUI stays valid during movement to loaded chunks, closes gracefully if block moves to unloaded chunk. No fix needed.

---

### G. Frame Activation Timing — MITIGATED

`FrameActivation.flushIfDirty()` is called at the start of resolution. Mid-resolve activation changes are theoretically possible but practically require a very specific machine layout (frame moving next to conductor during the same piston push that's being resolved). No reports of this causing issues.

---

## REVISED SUMMARY OF RECOMMENDATIONS

**Actual bugs to fix:**
1. **Retraction scan depth hardcoded at 12** — should track configured push limit for brace compatibility (Finding #1)

**Worthwhile refactors (one source of truth):**
2. **Extract shared serialization helper** in `MachineFrameBlockEntity` — three identical save blocks (Finding #2)
3. **Extract default value constants** in `MachineFrameBlockEntity`, reference from `PositionBoundContainer` (Finding #3)

**Nice-to-haves:**
4. Add try-finally to ThreadLocal patterns (Finding #4) — defensive resets already catch most cases
5. Name the block update flag magic numbers (Finding #5) — readability only
6. Shared POWERED property in AbstractMachineBlock (Finding #6) — style preference
7. Verify transit item orphaning on pipe de-specialization (Edge Case E) — needs in-game test

**Already working well (no action needed):**
- Dimension key format — centralized in `MovingBlockEntityData.makeKey()`
- Signal decay — centralized in `MachineRedstoneHelper`
- Push limit — dynamically calculated in `PistonStructureResolverMixin`
- Animation timing — shared via BE progress field
- GUI during movement — `PositionBoundContainer` handles correctly
- Block entity restore semantics — intentional single-consumer design

---

*Written by Claude, directed and guided by The Ideomancer.*
