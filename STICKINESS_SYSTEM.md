# Ideomod Stickiness System

## How Vanilla/Forge Stickiness Works

When a piston pushes or pulls blocks, the `PistonStructureResolver` figures out which blocks move together. It has two phases:

1. **Line building** (`addBlockLine`): Starting from the block directly in front of the piston, walk forward collecting blocks in a line. Then walk backward -- if any block behind is sticky and compatible, pull it along too.

2. **Branching** (`addBranchingBlocks`): For every sticky block already in the push list, check all 6 neighbors. If a neighbor isn't already in the list, call `addBlockLine` to potentially add it and its chain. Repeat until no new blocks are added.

### The Bidirectional canStickTo Check

**This is the single most important thing to understand.**

Forge added `isStickyBlock()` and `canStickTo()` hooks to let modded blocks participate in stickiness. But Forge checks `canStickTo` **bidirectionally**:

```
When block A tries to stick to block B:
  1. A.canStickTo(B) must return true   AND
  2. B.canStickTo(A) must return true
```

If EITHER side returns `false`, the stick fails. This happens **before** our mixin gets a chance to intervene.

**This means: you cannot implement one-way stickiness through `canStickTo` alone.** If Machine Block's `canStickTo` returns `false` for slime, then slime also can't grab Machine Block -- even though slime's own `canStickTo` says yes.

---

## Our Two-Layer System

We use two layers to achieve one-way (asymmetric) stickiness:

### Layer 1: `canStickTo` (permissive)

`canStickTo` should return `true` for **any block pair where either direction of sticking is desired**. Think of it as: "is it physically possible for these two block types to interact at all?"

Only return `false` for TRUE mutual exclusions where neither side should ever stick to the other (like slime vs honey in vanilla).

**Exception: Machine Family inter-specialization isolation.** Track's `canStickTo(Trolley)` returns false because neither direction of sticking is desired. This is a true mutual exclusion between different MachineTypes, handled correctly at Layer 1. Machine Frame (MachineType.FRAME) bridges them.

### Layer 2: Mixin grab immunity rules (restrictive)

`StickinessRules` + the `PistonStructureResolver` mixin handle the one-way part. Grab immunity rules say "when block A is the **grabber**, block B is immune." The mixin checks these rules and cancels grabs that shouldn't happen.

This runs AFTER the `canStickTo` check, so it can enforce asymmetry that `canStickTo` can't express.

### Layer 3: Directional rules (for Odd Slime)

A third layer for blocks whose stickiness depends on the **push direction**, not just block types. Odd Slime is only sticky when pushed in its FACING direction.

---

## The Golden Rule

> **`canStickTo` = "CAN these types interact?"  (permissive, both sides agree)**
> **Grab immunity = "WHO is allowed to initiate?"  (restrictive, blocks specific grabbers)**
> **Directional rules = "WHEN is the block sticky?"  (based on push direction)**

If you want A to grab B but not B to grab A:
1. Both `A.canStickTo(B)` and `B.canStickTo(A)` must return `true`
2. Register a grab immunity rule: `(grabber=B, target=A) -> BLOCKED`

**Never try to express one-way behavior in `canStickTo`.** It will break the other direction.

---

## StickyType Enum (Single Source of Truth)

The `StickyType` enum (`block/movement/StickyType.java`) centralizes all sticky block classification.

**Values:** NONE(0), SLIME(1), HONEY(2), ODD_SLIME(3), ODDSTONE(4). Integer values map directly to FramedBlock's INNER_STICKY blockstate property.

**Key methods:**
- `fromBlock(Block)` -- classifies any block into its StickyType
- `fromInnerStickyValue(int)` -- reverse mapping from INNER_STICKY blockstate value
- `of(BlockState)` -- resolves StickyType from a blockstate (handles both direct blocks and FramedBlock via INNER_STICKY)
- `isExcludedFrom(StickyType other)` -- true if this type and other are mutually exclusive
- `canGrab(StickyType target)` -- true if this type can grab the target
- `isVanillaSticky(Block)` -- replaces scattered instanceof checks

---

## MachineType Enum (Machine Family Stickiness)

The `MachineType` enum (`block/machine/MachineType.java`) identifies each Machine Family specialization for stickiness grouping.

**Values:** FRAME, TRACK, TROLLEY, CONDUCTOR, PIPE, REPEATER, CLOCK, PULSER, TOGGLE, PISTON, STICKY_PISTON.

**Key method:** `canStickToMachineType(MachineType other)` -- FRAME sticks to everything, all others stick to same-type + FRAME only.

**IMachineFamily interface** provides `getMachineType()` and static `canMachineFamilyStickTo(MachineType myType, BlockState other)`.

---

## Current Block Relationships

### Stickiness Matrix

Who grabs whom (rows = grabber, columns = target). "Machine Frame" = MachineType.FRAME. "Machine Spec" = any specialization. "Same-Type Spec" = same MachineType. "Diff-Type Spec" = different MachineType.

|              | Slime | Honey | Odd Slime | Machine Frame | Same-Type Spec | Diff-Type Spec | Frame Fam (active) | Frame Fam (inactive) | Vanilla Metal (activated) | Oddstone | Normal |
|:-------------|:-----:|:-----:|:---------:|:-------------:|:--------------:|:--------------:|:------------------:|:--------------------:|:-------------------------:|:--------:|:------:|
| **Slime**    |  YES  |  no*  |    no*    |      YES      |      YES       |      YES       |        YES         |         YES          |            YES            |    no    |  YES   |
| **Honey**    |  no*  |  YES  |    no*    |      YES      |      YES       |      YES       |        YES         |         YES          |            YES            |    no    |  YES   |
| **Odd Slime**|  no*  |  no*  |  YES (d)  |    YES (d)    |    YES (d)     |    YES (d)     |      YES (d)       |       YES (d)        |          YES (d)          |    no    | YES (d)|
| **Machine Frame**|no |  no   |    no     |      YES      |      YES       |      YES       |        YES         |         no           |            no             |    no    |   no   |
| **Machine Spec**|no  |  no   |    no     |      YES      |      YES       |       no       |        YES         |         no           |            no             |    no    |   no   |
| **Frame Fam (active)**| no | no | no     |      YES      |      YES       |      YES       |      YES (m)       |         no           |          YES (m)          |    no    |   no   |
| **Vanilla Metal (act.)**| no | no | no   |      YES      |      YES       |      YES       |      YES (m)       |         no           |          YES (m)          |    no    |   no   |
| **Framed Slime (act.)**| YES | no* | no* |      YES      |      YES       |      YES       |      YES (m)       |         no           |          YES (m)          |    no    |  YES   |
| **Framed Honey (act.)**| no* | YES | no* |      YES      |      YES       |      YES       |      YES (m)       |         no           |          YES (m)          |    no    |  YES   |
| **Framed OddSlime (act.)**| no* | no* | YES (d) | YES  |      YES       |      YES       |      YES (m)       |         no           |          YES (m)          |    no    | YES (d)|
| **Framed Oddstone (act.)**| YES | YES | YES |    YES      |      YES       |      YES       |      YES (m)       |         no           |          YES (m)          |   YES    |   no   |
| **Framed Oddstone (inact.)**| YES | YES | YES |  no      |       no       |       no       |         no         |         no           |            no             |   YES    |   no   |
| **Framed Sticky (inact.)**| (i) | (i) | (i) |   no      |       no       |       no       |         no         |         no           |            no             |    no    |  (i)   |
| **Oddstone** |  YES  |  YES  |    YES    |       no      |       no       |       no       |        no          |         no           |            no             |   YES    |   no   |

Legend: `no*` = mutual canStickTo exclusion | `no` = grab immunity or MachineType rejection | `(d)` = directional | `(m)` = material-matched | `(i)` = inner sticky only
Machine Frame (FRAME) bridges all specialization types. Diff-Type Spec rejected at Layer 1 (canStickTo false).

### How Each Pair Is Implemented

**Machine Family -> Machine Family**: TYPE-AWARE. `canMachineFamilyStickTo(myType, other)` checks `myType.canStickToMachineType(otherType)`. MachineType.FRAME sticks to all types (universal connector). Specializations stick to same-type + FRAME only. Track does NOT stick to Trolley. Machine Frame bridges different specialization types. This is Layer 1 (canStickTo), not grab immunity.

**Machine Family -> Slime**: BLOCKED. Both `canStickTo` return `true` (permissive). Grab immunity rule: machine family grabber + slime target = blocked.

**Slime -> Machine Family**: ALLOWED. Both `canStickTo` return `true`. No grab immunity rule.

**Machine Family -> Frame Family**: ALLOWED only when frame is active. canMachineFamilyStickTo() checks `FrameActivation.isActive(other)`. All MachineTypes grab active frames.

**Frame Family -> Machine Family**: ALLOWED when active. canStickTo returns true for Machine Family. No immunity rule.

(All other pair implementations unchanged from previous version -- see full pair list in code.)

---

## Where The Code Lives

| Component | File | Role |
|-----------|------|------|
| `canStickTo` overrides | Each block class | Layer 1: type compatibility. Machine Family uses MachineType-aware check. |
| Grab immunity rules | `StickinessRules.java` | Layer 2: blocks specific grabber->target pairs |
| Directional rules | `StickinessRules.java` | Layer 3: blocks grabs based on push direction |
| Rule registration | `Ideomod.java -> registerStickinessRules()` | Registers all rules at startup |
| Rule enforcement | `PistonStructureResolverMixin.java` | Intercepts resolver to apply rules. Vanilla metal bidirectional scan. |
| Frame activation | `FrameActivation.java` | Signal-based activation (0-15) with anti-signal suppression. |
| `StickyType enum` | `StickyType.java` | Single source of truth for sticky block classification |
| `MachineType enum` | `MachineType.java` | Machine specialization types. FRAME sticks to all, others to same+FRAME. |
| `IMachineFamily` | `IMachineFamily.java` | getMachineType(). canMachineFamilyStickTo(type, state). |
| `MachineSwapHelper` | `MachineSwapHelper.java` | Central swap utility. Item<->Block registry, checkAndSwap(). |
| `PositionBoundContainer` | `PositionBoundContainer.java` | Position-bound Container for seamless GUI across swaps. |
| `FrameMaterial` | `FrameMaterial.java` | Material lookups for conductor mapping |
| Rule helpers | `StickinessRules.java` | isOddstoneType(), isFramedOddstone(), isActiveFrameConnection(), hasMatchingInnerSticky() |
| MovementScanHelper | `MovementScanHelper.java` | Shared scanning. scanStickyGroup() with settled-set BFS tracking. |

### Mixin Injection Points

The mixin enforces rules at **two** points in `PistonStructureResolver`:

1. **HEAD of `addBlockLine`** (during branching only): Catches sideways grabs. Full grab immunity + directional checks.

2. **@Redirect on `canStickTo`** (inside `addBlockLine`): Catches backward walk. Call-pair toggle: Call 1 = full checks, Call 2 = directional only (passive response, grab immunity skipped).

---

## Adding a New Machine Specialization -- Checklist

1. **Add MachineType enum value** in `MachineType.java`.
2. **Create the block class** implementing `IMachineFamily` + `EntityBlock`. Return new type from `getMachineType()`. Return `MachineFrameBlockEntity` from `newBlockEntity()`.
3. **Register** in ModBlocks, ModItems, ModBlockEntities MACHINE_FRAME validBlocks. Add to creative tab via `MachineSwapHelper.createPreloadedStack()`.
4. **Add item mapping** in `MachineSwapHelper.registerMappings()`.
5. **Stickiness is automatic**: canStickTo delegates to canMachineFamilyStickTo. Grab immunity Rule 2 covers all IMachineFamily.
6. **Frame activation is automatic**: All IMachineFamily blocks are sources.
7. **Update this document's matrix**.

### Common Mistake

[X] "I want Machine Block to not grab slime, so I'll make `MachineBlock.canStickTo(slime)` return `false`."

This also prevents slime from grabbing Machine Block. Use a grab immunity rule instead:

[OK] `canStickTo` returns `true` for slime. Register rule: `(grabber=MachineBlock, target=Slime) -> BLOCKED`.

Written by Claude, directed and guided by The Ideomancer.
