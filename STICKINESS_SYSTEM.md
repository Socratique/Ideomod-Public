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

## Current Block Relationships

### Stickiness Matrix

Who grabs whom (rows = grabber, columns = target). "Machine Fam" = Machine Frame, Machine Track, Machine Trolley. "Frame Fam" = Iron Frame, Copper Frame (and future Framed Block variants). Frame Family shown as one row since both materials share the same grab immunity rules -- material exclusivity is handled by canStickTo (Layer 1).

|              | Slime | Honey | Odd Slime | Machine Fam | Frame Fam | Oddstone | Normal |
|:-------------|:-----:|:-----:|:---------:|:-----------:|:---------:|:--------:|:------:|
| **Slime**    |  YES  |  no*  |    no*    |     YES     |    YES    |    no    |  YES   |
| **Honey**    |  no*  |  YES  |    no*    |     YES     |    YES    |    no    |  YES   |
| **Odd Slime**|  no*  |  no*  |  YES (d)  |   YES (d)   |  YES (d)  |    no    | YES (d)|
| **Machine Fam**| no  |  no   |    no     |     YES     |    YES    |    no    |   no   |
| **Frame Fam (active)**| no | no | no     |     YES     |  YES (m)  |    no    |   no   |
| **Framed Slime (active)**| YES | no* | no* | YES | YES (m) | no | YES |
| **Framed Honey (active)**| no* | YES | no* | YES | YES (m) | no | YES |
| **Framed Odd Slime (active)**| no* | no* | YES (d) | YES | YES (m) | no | YES (d) |
| **Framed Sticky (inactive)**| (i) | (i) | (i) | no | no | no | (i) |
| **Oddstone** |  YES  |  YES  |    YES    |      no     |    no     |   YES    |   no   |

- `no*` = mutual exclusion (canStickTo returns false on both sides)
- `no` = blocked by grab immunity rule (canStickTo says yes, mixin says no)
- `(d)` = directional -- only when pushed in FACING direction
- `(m)` = material-matched -- iron sticks to iron only, copper to copper only (Layer 1 canStickTo)
- `(i)` = inner sticky behavior only -- Framed Slime grabs like slime, Framed Honey like honey, Framed Odd Slime like odd slime (directional). No frame stickiness when inactive.
- Frame Fam (active) = only sticky when BooleanProperty ACTIVE=true (driven by FrameActivation)
- Framed Sticky (active) = has BOTH inner stickiness + frame stickiness simultaneously
- Framed Sticky (inactive) = inner stickiness only, no frame stickiness
- When inactive and INNER_STICKY=0, isStickyBlock()=false — behaves as normal non-sticky block
- Normal = any non-sticky block (stone, dirt, etc.)

### Inner Sticky Carve-Out (rules 9-11)

Frame Family normally can't grab slime/honey/odd slime (rules 9-11). But when a FramedBlock's INNER_STICKY matches the target type, the immunity is lifted:
- Rule 9 exempted when grabber INNER_STICKY == SLIME (Framed Slime CAN grab normal blocks like real slime)
- Rule 10 exempted when grabber INNER_STICKY == HONEY
- Rule 11 exempted when grabber INNER_STICKY == ODD_SLIME

### Framed Odd Slime Directional Behavior

Framed Odd Slime does NOT use a blanket directional rule. A blanket rule would disable ALL stickiness including frame stickiness when pushed in non-FACING directions. Instead, the directional gating is handled in PistonStructureResolverMixin:

1. ideomod$isFramedOddSlimeDirectionallyBlocked() — checks if inner stickiness should be blocked by direction
2. ideomod$wouldFrameStickinessAllow() — second-chance: if ACTIVE and neighbor is same-material frame or machine family, allow despite directional block
3. Result: pushed in FACING dir → both inner + frame stickiness work. Pushed in other dir → only frame stickiness works (if ACTIVE).

### How Each Pair Is Implemented

**Slime <-> Honey**: Vanilla mutual exclusion. Both `canStickTo` return `false`.

**Slime -> Oddstone**: BLOCKED. `canStickTo` returns `true` (permissive). Grab immunity rule: slime grabber + oddstone target = blocked.

**Oddstone -> Slime**: ALLOWED. `canStickTo` returns `true`. No grab immunity rule.

**Slime -> Machine Family**: ALLOWED. Both `canStickTo` return `true`. No grab immunity rule.

**Machine Family -> Slime**: BLOCKED. Both `canStickTo` return `true` (permissive). Grab immunity rule: machine family grabber + slime target = blocked. Uses `MachineFrameBlock.isMachineFamily()` to cover MachineFrameBlock, MachineTrackBlock, and MachineTrolleyBlock with one rule.

**Machine Family -> Machine Family**: ALLOWED. `canMachineFamilyStickTo()` returns true for all Machine Family types. No grab immunity rule. Machine Block, Track, and Trolley all stick to each other.

Note: During Track-initiated pushes, Track is not dragged because its position is the resolver's pistonPos (immovable origin), not via a stickiness exemption.

**Slime <-> Odd Slime**: Mutual exclusion. Both `canStickTo` return `false`.

**Honey <-> Odd Slime**: Mutual exclusion. Both `canStickTo` return `false`.

**Odd Slime -> Oddstone**: BLOCKED. `canStickTo` returns `true` (permissive). Grab immunity rule: odd slime grabber + oddstone target = blocked.

**Oddstone -> Odd Slime**: ALLOWED. Both `canStickTo` return `true`.

**Odd Slime -> Machine Family**: ALLOWED (when active). Both `canStickTo` return `true`. Directional rule gates activation.

**Machine Family -> Odd Slime**: BLOCKED. Both `canStickTo` return `true` (permissive). Grab immunity rule: machine family grabber + odd slime target = blocked.

**Odd Slime -> anything (wrong direction)**: BLOCKED by directional rule. Push direction != FACING -> all grabs disabled.

**Slime -> Frame Family**: ALLOWED. Slime sticks to everything except honey. Frame's canStickTo returns true for slime (permissive). No grab immunity rule blocks this direction.

**Frame Family -> Slime**: BLOCKED. canStickTo returns true (permissive). Grab immunity rule 9: frame family grabber + slime target = blocked.

**Honey -> Frame Family**: ALLOWED. Honey sticks to everything except slime. Frame's canStickTo returns true (permissive).

**Frame Family -> Honey**: BLOCKED. Grab immunity rule 10.

**Odd Slime -> Frame Family**: ALLOWED (directional). Both canStickTo return true. Directional rule gates Odd Slime activation.

**Frame Family -> Odd Slime**: BLOCKED. Grab immunity rule 11.

**Machine Family -> Frame Family**: ALLOWED. canMachineFamilyStickTo() returns true for frame family. No immunity rule.

**Frame Family -> Machine Family**: ALLOWED when active. canStickTo returns true for Machine Family. No immunity rule.

**Frame Family -> Frame Family (same material)**: ALLOWED when active. canStickTo returns true for same-material IFrameBlock. No immunity rule.

**Frame Family -> Frame Family (cross-material)**: BLOCKED at Layer 1. canStickTo returns false for different-material IFrameBlock. Mutual exclusion.

**Frame Family <-> Oddstone**: NOT POSSIBLE. Both canStickTo return false for each other. No interaction.

---

## Where The Code Lives

| Component | File | Role |
|-----------|------|------|
| `canStickTo` overrides | Each block class | Layer 1: type compatibility (permissive) |
| Grab immunity rules | `StickinessRules.java` | Layer 2: blocks specific grabber->target pairs |
| Directional rules | `StickinessRules.java` | Layer 3: blocks grabs based on push direction |
| Rule registration | `Ideomod.java -> registerStickinessRules()` | Registers all rules at startup |
| Rule enforcement | `PistonStructureResolverMixin.java` | Intercepts resolver to apply rules |
| Frame activation | `FrameActivation.java` | Wipe-and-rebuild activation from Machine Block sources |
| Frame type checks | `StickinessRules.isFrameFamily()` | Helper for grab immunity rule predicates |

### Mixin Injection Points

The mixin enforces rules at **two** points in `PistonStructureResolver`:

1. **HEAD of `addBlockLine`** (during branching only): Catches sideways grabs when `addBranchingBlocks` iterates sticky blocks and checks their neighbors. Checks both directional rules and grab immunity.

2. **@Redirect on `canStickTo`** (inside `addBlockLine`): Catches the backward walk where the resolver checks if blocks behind should be pulled along. Intercepts the `canStickTo` call, runs the original, then applies our rules on top.

---

## Adding a New Sticky Block -- Checklist

1. **Decide the relationships**: For each existing sticky type, determine: mutual grab? one-way? excluded?

2. **Write `canStickTo`**: Return `true` for any type where interaction goes in EITHER direction. Only `false` for true mutual exclusions.

3. **Update OTHER blocks' `canStickTo`**: If your new block needs to be grabbable by existing blocks, those blocks' `canStickTo` must also return `true` for your type.

4. **Register grab immunity rules** in `Ideomod.registerStickinessRules()`: For each one-way pair, register `(grabber=X, target=Y) -> BLOCKED`.

5. **Register directional rules** if the block's stickiness depends on push direction.

6. **Update this document's matrix**.

### Common Mistake

[X] "I want Machine Block to not grab slime, so I'll make `MachineBlock.canStickTo(slime)` return `false`."

This also prevents slime from grabbing Machine Block. Use a grab immunity rule instead:

[OK] `canStickTo` returns `true` for slime. Register rule: `(grabber=MachineBlock, target=Slime) -> BLOCKED`.

Written by Claude, directed and guided by The Ideomancer.
