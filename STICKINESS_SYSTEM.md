# Ideomod Stickiness System

## How Vanilla/Forge Stickiness Works

When a piston pushes or pulls blocks, the `PistonStructureResolver` figures out which blocks move together. It has two phases:

1. **Line building** (`addBlockLine`): Starting from the block directly in front of the piston, walk forward collecting blocks in a line. Then walk backward — if any block behind is sticky and compatible, pull it along too.

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

**This means: you cannot implement one-way stickiness through `canStickTo` alone.** If Machine Block's `canStickTo` returns `false` for slime, then slime also can't grab Machine Block — even though slime's own `canStickTo` says yes.

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
2. Register a grab immunity rule: `(grabber=B, target=A) → BLOCKED`

**Never try to express one-way behavior in `canStickTo`.** It will break the other direction.

---

## Current Block Relationships

### Stickiness Matrix

Who grabs whom (rows = grabber, columns = target):

|              | Slime | Honey | Odd Slime | Machine | Oddstone | Normal |
|:-------------|:-----:|:-----:|:---------:|:-------:|:--------:|:------:|
| **Slime**    |  YES  |  no*  |    no     |   YES   |    no    |  YES   |
| **Honey**    |  no*  |  YES  |    no     |   YES   |    no    |  YES   |
| **Odd Slime**|  no*  |  no*  |  YES (d)  | YES (d) |    no    | YES (d)|
| **Machine**  |  no   |  no   |    no     |   YES   |    no    |   no   |
| **Oddstone** |  YES  |  YES  |    YES    |    no   |   YES    |   no   |

- `no*` = mutual exclusion (canStickTo returns false on both sides)
- `no` = blocked by grab immunity rule (canStickTo says yes, mixin says no)
- `(d)` = directional — only when pushed in FACING direction
- Normal = any non-sticky block (stone, dirt, etc.)

### How Each Pair Is Implemented

**Slime ↔ Honey**: Vanilla mutual exclusion. Both `canStickTo` return `false`.

**Slime → Oddstone**: BLOCKED. `canStickTo` returns `true` (permissive). Grab immunity rule: slime grabber + oddstone target = blocked.

**Oddstone → Slime**: ALLOWED. `canStickTo` returns `true`. No grab immunity rule.

**Slime → Machine Block**: ALLOWED. Both `canStickTo` return `true`. No grab immunity rule.

**Machine Block → Slime**: BLOCKED. Both `canStickTo` return `true` (permissive). Grab immunity rule: machine grabber + slime target = blocked.

**Slime ↔ Odd Slime**: Mutual exclusion. Both `canStickTo` return `false`.

**Honey ↔ Odd Slime**: Mutual exclusion. Both `canStickTo` return `false`.

**Odd Slime → Oddstone**: BLOCKED. `canStickTo` returns `true` (permissive). Grab immunity rule: odd slime grabber + oddstone target = blocked.

**Oddstone → Odd Slime**: ALLOWED. Both `canStickTo` return `true`.

**Odd Slime → Machine Block**: ALLOWED (when active). Both `canStickTo` return `true`. Directional rule gates activation.

**Machine Block → Odd Slime**: BLOCKED. Both `canStickTo` return `true` (permissive). Grab immunity rule: machine grabber + odd slime target = blocked.

**Odd Slime → anything (wrong direction)**: BLOCKED by directional rule. Push direction ≠ FACING → all grabs disabled.

---

## Where The Code Lives

| Component | File | Role |
|-----------|------|------|
| `canStickTo` overrides | Each block class | Layer 1: type compatibility (permissive) |
| Grab immunity rules | `StickinessRules.java` | Layer 2: blocks specific grabber→target pairs |
| Directional rules | `StickinessRules.java` | Layer 3: blocks grabs based on push direction |
| Rule registration | `Ideomod.java → registerStickinessRules()` | Registers all rules at startup |
| Rule enforcement | `PistonStructureResolverMixin.java` | Intercepts resolver to apply rules |

### Mixin Injection Points

The mixin enforces rules at **two** points in `PistonStructureResolver`:

1. **HEAD of `addBlockLine`** (during branching only): Catches sideways grabs when `addBranchingBlocks` iterates sticky blocks and checks their neighbors. Checks both directional rules and grab immunity.

2. **@Redirect on `canStickTo`** (inside `addBlockLine`): Catches the backward walk where the resolver checks if blocks behind should be pulled along. Intercepts the `canStickTo` call, runs the original, then applies our rules on top.

---

## Adding a New Sticky Block — Checklist

1. **Decide the relationships**: For each existing sticky type, determine: mutual grab? one-way? excluded?

2. **Write `canStickTo`**: Return `true` for any type where interaction goes in EITHER direction. Only `false` for true mutual exclusions.

3. **Update OTHER blocks' `canStickTo`**: If your new block needs to be grabbable by existing blocks, those blocks' `canStickTo` must also return `true` for your type.

4. **Register grab immunity rules** in `Ideomod.registerStickinessRules()`: For each one-way pair, register `(grabber=X, target=Y) → BLOCKED`.

5. **Register directional rules** if the block's stickiness depends on push direction.

6. **Update this document's matrix**.

### Common Mistake

❌ "I want Machine Block to not grab slime, so I'll make `MachineBlock.canStickTo(slime)` return `false`."

This also prevents slime from grabbing Machine Block. Use a grab immunity rule instead:

✅ `canStickTo` returns `true` for slime. Register rule: `(grabber=MachineBlock, target=Slime) → BLOCKED`.
