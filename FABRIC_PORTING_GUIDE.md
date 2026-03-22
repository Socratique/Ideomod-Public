# IDEOMOD FABRIC PORTING GUIDE: Forge 1.20.1 → Fabric 1.20.1 & Fabric 1.21
*Comprehensive migration reference for porting Ideomod from Minecraft Forge to Fabric Loader.*
*Written by Claude, directed and guided by The Ideomancer.*
*Created: 2026-03-22*

---

## TABLE OF CONTENTS

1. [Overview & Strategy](#1-overview--strategy)
2. [Forge vs Fabric: Fundamental Architecture Differences](#2-forge-vs-fabric-fundamental-architecture-differences)
3. [The Stickiness Problem: Fabric's Biggest Challenge](#3-the-stickiness-problem-fabrics-biggest-challenge)
4. [Build System & Project Structure](#4-build-system--project-structure)
5. [Java Version & Mappings](#5-java-version--mappings)
6. [Registration System](#6-registration-system)
7. [Event System](#7-event-system)
8. [Capability Replacement: Fabric Transfer API & Lookup API](#8-capability-replacement-fabric-transfer-api--lookup-api)
9. [Networking](#9-networking)
10. [Mixin Strategy: What Stays, What Changes](#10-mixin-strategy-what-stays-what-changes)
11. [Block & BlockState Differences](#11-block--blockstate-differences)
12. [Block Entity Differences](#12-block-entity-differences)
13. [Menu & GUI Differences](#13-menu--gui-differences)
14. [Rendering Differences](#14-rendering-differences)
15. [Data Components (1.21 Only)](#15-data-components-121-only)
16. [Resource & Data Folder Structure](#16-resource--data-folder-structure)
17. [Recipe System](#17-recipe-system)
18. [Portal & Dimension System](#18-portal--dimension-system)
19. [SavedData / PersistentState](#19-saveddata--persistentstate)
20. [Tags](#20-tags)
21. [Ideomod System-by-System Fabric Migration Plan](#21-ideomod-system-by-system-fabric-migration-plan)
22. [1.20.1 vs 1.21 Fabric: Differences Between Target Versions](#22-1201-vs-121-fabric-differences-between-target-versions)
23. [Porting-Lib: The Create Team's Bridge Layer](#23-porting-lib-the-create-teams-bridge-layer)
24. [Files Needed From User](#24-files-needed-from-user)
25. [Reference Repositories](#25-reference-repositories)
26. [Migration Order & Checklist](#26-migration-order--checklist)
27. [Risk Assessment & Recommendations](#27-risk-assessment--recommendations)

---

## 1. OVERVIEW & STRATEGY

### What We're Doing

Porting Ideomod from Forge to Fabric, targeting TWO versions:
- **Fabric 1.20.1** -- Same Minecraft version as our current Forge build. Allows a direct feature-parity comparison.
- **Fabric 1.21** -- Latest target, matching the NeoForge 1.21 port.

### Why Fabric is a Very Different Beast from NeoForge

The NeoForge port (see PORTING_GUIDE_1.21.md) is largely a "rename and adjust" exercise -- the APIs are evolutionary descendants of Forge's APIs. Fabric is a **fundamentally different modding philosophy**:

| Aspect | Forge/NeoForge | Fabric |
|--------|---------------|--------|
| API Style | Heavyweight, many built-in hooks | Lightweight, mixin-first |
| Piston/Stickiness | `canStickTo()` / `isStickyBlock()` hooks | **No equivalent -- must use mixins** |
| Capabilities | `LazyOptional<T>` / `BlockCapability<T>` | Fabric Lookup API + Transfer API |
| Events | `@SubscribeEvent` annotation system | Callback-based `Event<T>` instances |
| Registry | `DeferredRegister` pattern | Direct `Registry.register()` calls |
| Networking | `SimpleChannel` / `CustomPacketPayload` | `ClientPlayNetworking` / `ServerPlayNetworking` |
| Config | Forge Config API | No built-in config (use Cloth Config, etc.) |
| Mod metadata | `mods.toml` | `fabric.mod.json` |
| Build tool | ForgeGradle / NeoGradle | Fabric Loom |
| Mixins | Supported (via MixinGradle) | **Native, first-class** |

### The Core Challenge

Fabric's philosophy is "use mixins for everything the API doesn't cover." Forge provides dozens of hooks via its event bus and block/item method overrides. Fabric provides a smaller, cleaner API surface and expects modders to use mixins directly for anything beyond that.

**For Ideomod specifically, this is actually partially advantageous**: our most complex system (PistonStructureResolver mixin) already IS a mixin. Since Fabric is built on the same SpongePowered Mixin framework, our mixin code is highly portable. The challenge is everything AROUND the mixin -- events, capabilities, registration patterns, and the stickiness API hooks.

### Recommended Approach: Port 1.20.1 First, Then 1.21

Since our Forge codebase is 1.20.1, the Fabric 1.20.1 port eliminates Minecraft version changes from the equation -- we ONLY deal with Forge→Fabric API differences. Then the 1.20.1→1.21 Fabric jump is a smaller, well-documented step (Fabric updates very quickly and has good version migration support).

---

## 2. FORGE VS FABRIC: FUNDAMENTAL ARCHITECTURE DIFFERENCES

### Mod Initialization

**Forge:**
```java
@Mod("ideomod")
public class Ideomod {
    public Ideomod() {
        // Register DeferredRegisters to MOD bus
        // Register event handlers
    }
}
```

**Fabric:**
```java
// fabric.mod.json declares entrypoints
public class Ideomod implements ModInitializer {
    @Override
    public void onInitialize() {
        // Register everything directly
        // Register event callbacks
    }
}

// Separate client entrypoint
public class IdeomodClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Client-only registration
    }
}
```

### Key Structural Differences

1. **No event bus** -- Fabric uses static callback registrations (`ServerLifecycleEvents.SERVER_STARTING.register(...)`)
2. **No `@Mod.EventBusSubscriber`** -- event handlers are explicitly registered in initializer
3. **Client/server separation** -- enforced by separate entrypoints, not `@OnlyIn` or dist checks
4. **No automatic mod scanning** -- everything is explicit
5. **No Forge config system** -- must use a third-party library or roll your own

---

## 3. THE STICKINESS PROBLEM: FABRIC'S BIGGEST CHALLENGE

### The Issue

Forge provides two critical hooks on `Block`/`BlockState` that our entire stickiness system depends on:
- `isStickyBlock(BlockState)` -- "Does this block have sticky properties?"
- `canStickTo(BlockState, BlockState)` -- "Can this block stick to that block?"

**Fabric has NO equivalent hooks.** The vanilla Minecraft code only hardcodes checks for `SlimeBlock` and `HoneyBlock` using `instanceof`. There is no pluggable API.

### What This Means for Ideomod

Our three-layer stickiness architecture is built on top of Forge's `canStickTo`:
- **Layer 1 (canStickTo)** -- Permissive block-pair interaction rules
- **Layer 2 (Grab Immunity)** -- Mixin-enforced one-way restrictions
- **Layer 3 (Directional)** -- Direction-dependent stickiness

Layers 2 and 3 already live in our mixin and would transfer directly. But Layer 1 currently lives in Block class overrides (`OddstoneBlock.canStickTo()`, `FrameBlock.canStickTo()`, etc.) that don't exist in Fabric.

### Solution: Expand the Mixin

We need to create **additional mixin injections into PistonStructureResolver** (and/or PistonBaseBlock) to intercept the vanilla `instanceof SlimeBlock` / `instanceof HoneyBlock` checks and replace them with our own pluggable system.

#### Approach A: Redirect instanceof Checks

Vanilla's `PistonStructureResolver.addBlockLine()` contains checks like:
```java
if (blockstate.isStickyBlock()) { ... }  // This is Forge-added
// In vanilla (Fabric), it's more like:
if (block instanceof SlimeBlock || block instanceof HoneyBlock) { ... }
```

In Fabric, we would need to:
1. **@Redirect** or **@Inject** at every point where vanilla checks for sticky blocks
2. Replace the hardcoded `instanceof` checks with calls to our own `StickyType.of(state)` system
3. Replace the hardcoded slime-vs-honey exclusion with our full `canStickTo` logic

#### Approach B: Use Porting-Lib's Blocks Module

The **Porting-Lib** project (created by the Create Fabric team) includes a `blocks` module that may reimplement `isStickyBlock`/`canStickTo` as interface hooks via mixin. If it does, we could depend on Porting-Lib and use its hooks directly, saving significant mixin work.

#### Approach C: Interface Injection (Fabric API Feature)

Fabric supports "interface injection" -- adding methods to vanilla classes via mixin. We could:
1. Create an `IStickyBlock` interface with `isStickyBlock()` and `canStickTo()` methods
2. Inject it into `Block` via mixin with default implementations returning `false` / delegating to vanilla logic
3. Have our blocks implement the interface
4. Hook PistonStructureResolver to call our interface methods

**Recommendation: Approach C** gives us the cleanest architecture and doesn't create a dependency on Porting-Lib. It essentially recreates Forge's hooks ourselves.

### Stickiness Mixin Injection Points (Vanilla/Fabric)

These are the specific places in vanilla's PistonStructureResolver where sticky logic lives. ALL of these need interception:

| Location | Vanilla Logic | Our Replacement |
|----------|--------------|-----------------|
| `addBlockLine` backward walk | `block instanceof SlimeBlock \|\| block instanceof HoneyBlock` | `StickyType.of(state) != NONE` |
| `addBlockLine` backward walk | `slime.canStickTo(honey)` equivalent (hardcoded exclusion) | Our full `canStickTo` + grab immunity |
| `addBranchingBlocks` | `block instanceof SlimeBlock \|\| block instanceof HoneyBlock` | `StickyType.of(state) != NONE` |
| `addBranchingBlocks` neighbor check | Hardcoded slime/honey sticking | Our full canStickTo logic |

### Impact Assessment

This is **the single most complex part of the Fabric port** and adds significant mixin work beyond what we already have. Our existing PistonStructureResolverMixin adds features ON TOP of Forge's canStickTo. In Fabric, we need to also REPLACE what canStickTo provides.

**Estimated additional mixin work: 100-200 lines of injection code** targeting vanilla's hardcoded sticky checks.

---

## 4. BUILD SYSTEM & PROJECT STRUCTURE

### Fabric Loom

Fabric uses **Fabric Loom** as its Gradle plugin (replacing ForgeGradle):

```groovy
// build.gradle
plugins {
    id 'fabric-loom' version '1.x'  // check latest
}

dependencies {
    minecraft "com.mojang:minecraft:1.20.1"  // or 1.21
    mappings "net.fabricmc:yarn:1.20.1+build.x:v2"  // or official via loom
    modImplementation "net.fabricmc:fabric-loader:0.x.x"
    modImplementation "net.fabricmc.fabric-api:fabric-api:0.x.x+1.20.1"
}
```

### fabric.mod.json

Replaces `mods.toml`:

```json
{
    "schemaVersion": 1,
    "id": "ideomod",
    "version": "${version}",
    "name": "Ideomod",
    "description": "A vanilla+ overhaul mod",
    "authors": ["Ideomancer"],
    "contact": {},
    "license": "All Rights Reserved",
    "icon": "assets/ideomod/icon.png",
    "environment": "*",
    "entrypoints": {
        "main": ["com.ideomancer.ideomod.Ideomod"],
        "client": ["com.ideomancer.ideomod.client.IdeomodClient"]
    },
    "mixins": ["ideomod.mixins.json"],
    "depends": {
        "fabricloader": ">=0.15.0",
        "minecraft": "~1.20.1",
        "java": ">=17",
        "fabric-api": "*"
    }
}
```

### Mappings Choice

Fabric traditionally uses **Yarn** mappings (community-maintained), but also supports **Mojang Official** mappings via Loom. Since our Forge codebase uses official mappings, **we should use official mappings on Fabric too** to minimize renaming work:

```groovy
mappings loom.officialMojangMappings()
```

This means most class/method/field names stay identical between our Forge and Fabric builds.

### Project Structure

The directory layout is nearly identical. Key differences:
- `META-INF/mods.toml` -> `fabric.mod.json` (in resources root, NOT in META-INF)
- Mixin config stays at `ideomod.mixins.json` in resources root (same location)
- No `pack.mcmeta` changes needed for Fabric 1.20.1 (may need version bump for 1.21)

---

## 5. JAVA VERSION & MAPPINGS

### 1.20.1 Fabric

- Java 17 (same as Forge 1.20.1)
- Mojang official mappings available via Loom

### 1.21 Fabric

- Java 21 (same as NeoForge 1.21)
- Mojang official mappings available via Loom

### Mapping Considerations

Using official Mojang mappings means the same class/method names as Forge. The ONLY namespace differences will be in Fabric API classes (which have their own `net.fabricmc.fabric.api.*` package) and in how we access Forge-specific things (events, capabilities, etc.) that need Fabric replacements.

---

## 6. REGISTRATION SYSTEM

### Forge DeferredRegister -> Fabric Direct Registration

**Forge (current):**
```java
public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "ideomod");
public static final RegistryObject<Block> ODDSTONE = BLOCKS.register("oddstone", () -> new OddstoneBlock(...));
// In constructor: BLOCKS.register(modEventBus);
```

**Fabric:**
```java
public class ModBlocks {
    public static final Block ODDSTONE = register("oddstone", new OddstoneBlock(...));

    private static Block register(String name, Block block) {
        return Registry.register(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("ideomod", name), block);
    }

    public static void init() {} // Called from ModInitializer to classload
}
```

### Key Differences

1. **No lazy loading** -- objects are created immediately during registration, not via Supplier
2. **No RegistryObject wrapper** -- you get the object directly
3. **Registration happens on classload** -- call `ModBlocks.init()` from your `onInitialize()`
4. **Order matters** -- blocks must be registered before items (items reference blocks)

### Affected Registry Classes

| Forge Class | Fabric Equivalent |
|------------|-------------------|
| `ModBlocks.java` | Rewrite: `Registry.register(Registries.BLOCK, ...)` |
| `ModItems.java` | Rewrite: `Registry.register(Registries.ITEM, ...)` |
| `ModBlockEntities.java` | Rewrite: `Registry.register(Registries.BLOCK_ENTITY_TYPE, ...)` |
| `ModMenuTypes.java` | Rewrite: `Registry.register(Registries.MENU, ...)` |
| `ModRecipeSerializers.java` | Rewrite: `Registry.register(Registries.RECIPE_SERIALIZER, ...)` |
| `ModCreativeTabs.java` | Use Fabric API: `ItemGroupEvents.modifyEntriesEvent()` or `FabricItemGroup.builder()` |
| `ModTags.java` | `TagKey.create()` works the same (vanilla API) |

### Creative Tab Registration (Fabric-Specific)

Fabric API provides `FabricItemGroup` for creating creative tabs:
```java
public static final ItemGroup IDEOMOD_TAB = FabricItemGroup.builder()
    .icon(() -> new ItemStack(ModBlocks.IDEOBLOCK))
    .displayItems((ctx, entries) -> {
        entries.accept(ModItems.IDEOBLOCK);
        entries.accept(ModItems.ODDSTONE);
        // ... etc
    })
    .build();

// Register: Registry.register(Registries.CREATIVE_MODE_TAB, id, IDEOMOD_TAB);
```

---

## 7. EVENT SYSTEM

### Forge Events -> Fabric Callbacks

Fabric uses a callback-based event system. Events are static instances that you register listeners on:

**Forge:**
```java
@Mod.EventBusSubscriber(modid = "ideomod", bus = Bus.FORGE)
public class ModCommands {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) { ... }
}
```

**Fabric:**
```java
// In your ModInitializer.onInitialize():
CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
    ModCommands.register(dispatcher);
});
```

### Event Mapping Table

| Forge Event | Fabric Equivalent |
|------------|-------------------|
| `RegisterCommandsEvent` | `CommandRegistrationCallback.EVENT` |
| `RegisterColorHandlersEvent.Block` | `ColorProviderRegistry.BLOCK.register()` |
| `RegisterColorHandlersEvent.Item` | `ColorProviderRegistry.ITEM.register()` |
| `EntityRenderersEvent.RegisterRenderers` | `BlockEntityRendererFactories.register()` in client init |
| `FMLClientSetupEvent` | `ClientModInitializer.onInitializeClient()` |
| `FMLCommonSetupEvent` | `ModInitializer.onInitialize()` (or use lifecycle events) |
| `LivingChangeTargetEvent` | **No direct equivalent** -- use mixin on `Mob.setTarget()` |
| `LivingDeathEvent` | `ServerLivingEntityEvents.AFTER_DEATH` |
| `PlayerInteractEvent.EntityInteract` | `UseEntityCallback.EVENT` |
| `ProjectileImpactEvent` | **No direct equivalent** -- use mixin |
| `ServerLifecycleEvents.SERVER_STARTING` | `ServerLifecycleEvents.SERVER_STARTING` |
| `LevelEvent.Load` | `ServerWorldEvents.LOAD` |
| `TickEvent.ServerTickEvent` | `ServerTickEvents.END_SERVER_TICK` |
| `BlockEvent.NeighborNotifyEvent` | **No direct equivalent** -- use mixin on `Block.updateNeighbourShapes()` |

### Ideomod-Specific Event Migration

| Ideomod File | Forge Event Used | Fabric Replacement |
|-------------|-----------------|-------------------|
| `ModCommands.java` | `RegisterCommandsEvent` | `CommandRegistrationCallback.EVENT` |
| `JudgementEffect.java` | `LivingDeathEvent` + `LivingChangeTargetEvent` | `ServerLivingEntityEvents.AFTER_DEATH` + mixin for target change |
| `EnderPearlPortalHandler.java` | `ProjectileImpactEvent` | Mixin on `ThrownEnderpearl.onHit()` or equivalent |
| `ModColorHandlers.java` | `RegisterColorHandlersEvent` | `ColorProviderRegistry.BLOCK/ITEM.register()` |
| `FramedBlockDebugOverlay.java` | `RenderGuiOverlayEvent` | `HudRenderCallback.EVENT` |
| `StructureManager.java` | `LevelEvent.Load` / Tick events | `ServerWorldEvents.LOAD` / `ServerTickEvents` |
| `FrameActivation.java` | Tick events (for end-of-tick processing) | `ServerTickEvents.END_SERVER_TICK` |

---

## 8. CAPABILITY REPLACEMENT: FABRIC TRANSFER API & LOOKUP API

### Forge Capabilities Have No Direct Equivalent

Forge's capability system (`LazyOptional<IItemHandler>`, `LazyOptional<IFluidHandler>`, etc.) does not exist in Fabric. Instead, Fabric provides two complementary systems:

### Fabric Lookup API (fabric-api-lookup-api-v1)

General-purpose API for querying "what does this block/entity provide?":

```java
// Define a lookup (usually done once, shared)
public static final BlockApiLookup<Storage<FluidVariant>, Direction> FLUID_STORAGE =
    BlockApiLookup.get(new ResourceLocation("fabric", "fluid_storage"), Storage.asClass(), Direction.class);

// Register a provider for our block entity
FLUID_STORAGE.registerForBlockEntity((be, dir) -> be.getFluidStorage(dir), ModBlockEntities.MACHINE_FLUID_PIPE);

// Query from another block
Storage<FluidVariant> storage = FLUID_STORAGE.find(level, pos, direction);
if (storage != null) { ... }
```

### Fabric Transfer API (fabric-transfer-api-v1)

Unified item and fluid transfer with a transaction system:

- **`Storage<ItemVariant>`** replaces `IItemHandler`
- **`Storage<FluidVariant>`** replaces `IFluidHandler`
- **Transaction system** for atomic multi-step transfers (simulate then commit/abort)
- **Fluid units**: 1 bucket = 81000 droplets (NOT Forge's 1000 mB)

### Impact on Ideomod

| Component | Forge API | Fabric Replacement |
|-----------|----------|-------------------|
| `MachineFluidPipeBlockEntity` IFluidHandler | `LazyOptional<IFluidHandler>` | Implement `Storage<FluidVariant>`, register via `FluidStorage.SIDED` |
| `MachinePipeBlockEntity` item transfer | Direct inventory access / `IItemHandler` | `Storage<ItemVariant>` via `ItemStorage.SIDED` |
| Neighbor capability queries | `level.getBlockEntity(pos).getCapability(cap, side)` | `ItemStorage.SIDED.find(level, pos, side)` / `FluidStorage.SIDED.find(level, pos, side)` |

### Fluid Unit Conversion

**Critical**: Forge uses millibuckets (1 bucket = 1000 mB). Fabric Transfer API uses droplets (1 bucket = 81000 droplets). Any hardcoded fluid amounts need conversion.

---

## 9. NETWORKING

### Forge SimpleChannel -> Fabric Networking API

**Forge (current):**
```java
SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(...);
CHANNEL.registerMessage(0, RepeaterDelayPacket.class, ...);
CHANNEL.sendToServer(new RepeaterDelayPacket(...));
```

**Fabric 1.20.1:**
```java
// Define packet identifier
public static final ResourceLocation REPEATER_DELAY = new ResourceLocation("ideomod", "repeater_delay");

// Register server receiver
ServerPlayNetworking.registerGlobalReceiver(REPEATER_DELAY, (server, player, handler, buf, responseSender) -> {
    BlockPos pos = buf.readBlockPos();
    int delay = buf.readInt();
    server.execute(() -> { /* handle on main thread */ });
});

// Send from client
PacketByteBuf buf = PacketByteBufs.create();
buf.writeBlockPos(pos);
buf.writeInt(delay);
ClientPlayNetworking.send(REPEATER_DELAY, buf);
```

**Fabric 1.21 (uses CustomPayload like NeoForge):**
```java
// Define payload record
public record RepeaterDelayPayload(BlockPos pos, int delay) implements CustomPacketPayload {
    public static final Id<RepeaterDelayPayload> ID = new Id<>(ResourceLocation.fromNamespaceAndPath("ideomod", "repeater_delay"));
    public static final PacketCodec<PacketByteBuf, RepeaterDelayPayload> CODEC = ...;
    @Override public Id<? extends CustomPacketPayload> getId() { return ID; }
}

// Register
PayloadTypeRegistry.playC2S().register(RepeaterDelayPayload.ID, RepeaterDelayPayload.CODEC);
ServerPlayNetworking.registerGlobalReceiver(RepeaterDelayPayload.ID, (payload, context) -> { ... });

// Send
ClientPlayNetworking.send(new RepeaterDelayPayload(pos, delay));
```

### Affected Files

- `ModNetworking.java` -- Complete rewrite for either version
- `RepeaterDelayPacket.java` -- Rewrite to Fabric networking pattern
- `TransformerLevelPacket.java` -- Rewrite to Fabric networking pattern

---

## 10. MIXIN STRATEGY: WHAT STAYS, WHAT CHANGES

### Good News: Mixins Are Native to Fabric

Since Fabric is built on SpongePowered Mixin, ALL of our existing mixins are conceptually portable. The mixin framework, annotations, and injection types (`@Inject`, `@Redirect`, `@ModifyConstant`, `@Shadow`) are identical.

### What Changes

The **target method signatures and internal names** may differ slightly between Forge's decompiled source and Fabric's (even with official mappings, Forge adds patched methods). Specifically:

#### PistonStructureResolverMixin (Our Most Complex)

| Feature | Forge Target | Fabric Target | Change Needed? |
|---------|-------------|---------------|----------------|
| @Shadow fields | Same names (official mappings) | Same names | **No** |
| @Inject resolve() HEAD | `resolve()` | `resolve()` | **No** |
| @Inject addBranchingBlocks HEAD/TAIL | `addBranchingBlocks()` | `addBranchingBlocks()` | **No** |
| @Inject addBlockLine HEAD | `addBlockLine(BlockPos, Direction)` | `addBlockLine(BlockPos, Direction)` | **No** |
| **@Redirect canStickTo INVOKE** | `BlockState.canStickTo(BlockState)` | **DOES NOT EXIST** -- vanilla uses `instanceof` checks | **YES -- MAJOR REWRITE** |
| @ModifyConstant(12) | Push limit constant | Same constant | **No** |

The `canStickTo` redirect is the crux: in Forge, `canStickTo` is a method Forge added to `BlockState`. In vanilla/Fabric, this method doesn't exist. Instead, vanilla has hardcoded `instanceof SlimeBlock` and `block == Blocks.HONEY_BLOCK` checks.

#### New Mixin Injections Needed for Fabric

We need to ADD mixin code that doesn't exist in the Forge version:

1. **Replace `isStickyBlock` check** -- Vanilla checks `block instanceof SlimeBlock || block == Blocks.HONEY_BLOCK`. We need to @Redirect or @Inject to also return true for OddstoneBlock, OddSlimeBlock, FrameBlock(active), FramedBlock(active+inner_sticky), MachineBlock, etc.

2. **Replace `canStickTo` check** -- The backward walk's compatibility check (slime doesn't stick to honey) is hardcoded as `!(slimeBlock && honeyBlock)`. We need to replace this with our full canStickTo matrix logic.

3. **Replace branching sticky check** -- Same pattern in `addBranchingBlocks`.

#### Other Mixins

| Mixin | Portability | Notes |
|-------|------------|-------|
| `PistonBaseBlockMixin` | HIGH | May target Forge-patched methods; verify against vanilla |
| `PistonMovingBlockEntityMixin` | HIGH | Data restoration; vanilla class is similar |
| `EntityMovementMixin` | HIGH | Speed/jump factors; vanilla methods |
| `AbstractFurnaceBlockEntityMixin` | HIGH | Furnace internals; vanilla class |
| `PistonBaseBlockClientMixin` | MEDIUM | Client mixin; verify vanilla client class |
| `PistonMovingBlockEntityRendererMixin` | MEDIUM | Rendering mixin; vanilla renderer class |

### Mixin Config Differences

Fabric mixin configs support a proper `"client"` array (unlike Forge where we put everything in `"mixins"`). We can properly separate:

```json
{
    "required": true,
    "package": "com.ideomancer.ideomod.mixin",
    "compatibilityLevel": "JAVA_17",
    "mixins": [
        "PistonStructureResolverMixin",
        "PistonBaseBlockMixin",
        "PistonMovingBlockEntityMixin",
        "EntityMovementMixin",
        "AbstractFurnaceBlockEntityMixin",
        "StickyBlockMixin"
    ],
    "client": [
        "client.PistonBaseBlockClientMixin",
        "client.PistonMovingBlockEntityRendererMixin"
    ]
}
```

Note the addition of `StickyBlockMixin` -- this is the new mixin needed to implement the stickiness hooks.

---

## 11. BLOCK & BLOCKSTATE DIFFERENCES

### Block Properties

**Forge:**
```java
BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0f, 6.0f).requiresCorrectToolForDrops()
```

**Fabric 1.20.1:**
```java
// Identical! Block properties are vanilla API.
FabricBlockSettings.of().mapColor(MapColor.STONE).strength(2.0f, 6.0f).requiresCorrectToolForDrops()
// OR just use vanilla: BlockBehaviour.Properties.of()...
```

Fabric provides `FabricBlockSettings` as an extension, but vanilla `BlockBehaviour.Properties` works fine for most things.

### canStickTo / isStickyBlock -- NOT AVAILABLE

As discussed in Section 3, these are Forge-added methods. Our blocks that override these (OddstoneBlock, OddSlimeBlock, FrameBlock, FramedBlock, all Machine Family blocks) will need their stickiness logic moved into the new StickyBlockMixin system.

### PushReaction

`PushReaction` is vanilla API and works the same. Our `PushReaction.BLOCK` on portal blocks, `PushReaction.NORMAL` on machine blocks, etc., are unchanged.

---

## 12. BLOCK ENTITY DIFFERENCES

### Registration

**Forge:**
```java
public static final RegistryObject<BlockEntityType<MachineFrameBlockEntity>> MACHINE_FRAME =
    BLOCK_ENTITIES.register("machine_frame", () ->
        BlockEntityType.Builder.of(MachineFrameBlockEntity::new, blocks...).build(null));
```

**Fabric:**
```java
public static final BlockEntityType<MachineFrameBlockEntity> MACHINE_FRAME =
    Registry.register(Registries.BLOCK_ENTITY_TYPE, new ResourceLocation("ideomod", "machine_frame"),
        FabricBlockEntityTypeBuilder.create(MachineFrameBlockEntity::new, blocks...).build(null));
```

### Client Sync

`getUpdatePacket()` / `getUpdateTag()` / `onDataPacket()` are vanilla APIs and work the same.

### Capabilities -> Lookup API

Block entity capability exposure changes from overriding `getCapability()` to registering providers externally. See Section 8.

---

## 13. MENU & GUI DIFFERENCES

### Menu Registration

**Forge:** Uses `IContainerFactory` (Forge extension).

**Fabric:** Uses `ExtendedScreenHandlerType` from Fabric API:
```java
public static final MenuType<MachineFrameMenu> MACHINE_FRAME_MENU =
    Registry.register(Registries.MENU,
        new ResourceLocation("ideomod", "machine_frame"),
        new ExtendedScreenHandlerType<>(MachineFrameMenu::new));
```

### Screen Registration

**Forge:** `MenuScreens.register()` in `FMLClientSetupEvent`.

**Fabric:** `HandledScreens.register()` in `ClientModInitializer.onInitializeClient()`:
```java
HandledScreens.register(ModMenuTypes.MACHINE_FRAME_MENU, MachineFrameScreen::new);
```

### Screen Rendering

The `Screen` and `AbstractContainerScreen` classes are vanilla. Method signatures should be the same between Forge and Fabric on the same MC version. On 1.21, both platforms share the same vanilla rendering changes.

---

## 14. RENDERING DIFFERENCES

### Block Entity Renderers (BESRs)

**Forge:**
```java
// Registered in EntityRenderersEvent.RegisterRenderers
event.registerBlockEntityRenderer(ModBlockEntities.MACHINE_FRAME.get(), MachinePistonArmRenderer::new);
```

**Fabric:**
```java
// Registered in ClientModInitializer
BlockEntityRendererFactories.register(ModBlockEntities.MACHINE_FRAME, MachinePistonArmRenderer::new);
```

The actual renderer code (`BlockEntityRenderer<T>` implementation) is vanilla API and ports directly.

### Custom BakedModel (FramedBlockItemModel)

Fabric provides `FabricBakedModel` interface for custom model rendering. Our `FramedBlockItemModel` will need to implement this interface instead of/in addition to `BakedModel`:

```java
public class FramedBlockItemModel implements BakedModel, FabricBakedModel {
    @Override
    public void emitBlockQuads(BlockRenderView blockView, BlockState state, BlockPos pos,
                                Supplier<Random> randomSupplier, RenderContext context) {
        // Custom quad emission using Fabric Renderer API
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<Random> randomSupplier, RenderContext context) {
        // Custom item rendering
    }
}
```

### Custom RenderType (FramedBlockRenderTypes)

`RenderType` is vanilla API. Our custom render type creation should work the same. Fabric provides `BlockRenderLayerMap` for mapping blocks to render layers:
```java
BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.IRON_FRAME, RenderType.cutout());
```

### Color Handlers

**Forge:** `RegisterColorHandlersEvent.Block/Item`

**Fabric:**
```java
ColorProviderRegistry.BLOCK.register((state, view, pos, tintIndex) -> { ... }, ModBlocks.CONDUCTOR);
ColorProviderRegistry.ITEM.register((stack, tintIndex) -> { ... }, ModItems.CONDUCTOR);
```

---

## 15. DATA COMPONENTS (1.21 ONLY)

Data Components are a **vanilla Minecraft 1.21 change**, not mod-loader-specific. The same Data Component migration described in PORTING_GUIDE_1.21.md Section 5 applies identically to Fabric 1.21.

For Fabric 1.20.1, ItemStack NBT still works normally (no changes needed from our Forge 1.20.1 codebase).

---

## 16. RESOURCE & DATA FOLDER STRUCTURE

### Fabric 1.20.1

Resource and data folder structure is identical to Forge 1.20.1:
- `assets/ideomod/blockstates/`, `models/`, `textures/`, `lang/` -- same
- `data/ideomod/recipes/`, `loot_tables/`, `tags/` -- same
- `data/minecraft/tags/` -- same

### Fabric 1.21

Same plural→singular folder restructuring as NeoForge 1.21 (this is a vanilla change):
- `data/ideomod/recipes/` -> `data/ideomod/recipe/`
- `data/ideomod/loot_tables/blocks/` -> `data/ideomod/loot_table/block/`
- `data/ideomod/tags/blocks/` -> `data/ideomod/tag/block/`

### Mining Tags (Tool Requirements)

**Forge:** Uses `data/minecraft/tags/blocks/mineable/pickaxe.json`

**Fabric 1.20.1:** Same location, PLUS needs `data/minecraft/tags/blocks/needs_stone_tool.json` (etc.) for harvest level. Forge handles harvest levels differently via tag conventions.

**Fabric 1.21:** `data/minecraft/tag/block/mineable/pickaxe.json` (singular)

---

## 17. RECIPE SYSTEM

### Fabric 1.20.1

Recipe system is vanilla API -- our `FramedBlockRecipe` extends `CustomRecipe` which is vanilla. The `RecipeSerializer` registration changes to Fabric's direct registration pattern, but the recipe logic itself is the same.

### Fabric 1.21

Same changes as NeoForge 1.21: `CraftingContainer` -> `CraftingInput`, Codec-based serializers.

---

## 18. PORTAL & DIMENSION SYSTEM

### Fabric 1.20.1

Portal and dimension code is vanilla API. Our portal system should port directly with minimal changes. The main difference is:
- `EnderPearlPortalHandler.java` uses Forge's `ProjectileImpactEvent` -> needs a mixin replacement

### Fabric 1.21

Same `DimensionTransition` changes as NeoForge 1.21 (vanilla change). See PORTING_GUIDE_1.21.md Section 16.

---

## 19. SAVEDDATA / PERSISTENTSTATE

### Fabric 1.20.1

`SavedData` is vanilla API and works identically:
```java
OddstonePortalSavedData data = level.getDataStorage().computeIfAbsent(
    OddstonePortalSavedData::load, OddstonePortalSavedData::new, "oddstone_portals");
```

### Fabric 1.21

Same `SavedData.Factory` pattern and `HolderLookup.Provider` changes as NeoForge 1.21 (vanilla change).

---

## 20. TAGS

### Fabric 1.20.1

`TagKey.create()` is vanilla API -- works identically. Tag JSON files are in the same locations.

### Fabric 1.21

Same plural→singular folder restructuring as NeoForge 1.21.

---

## 21. IDEOMOD SYSTEM-BY-SYSTEM FABRIC MIGRATION PLAN

### Phase 1: Project Setup

1. Create Fabric project from template (fabric-example-mod)
2. Configure `build.gradle` with Fabric Loom, official mappings
3. Create `fabric.mod.json` with entrypoints
4. Set up mixin config (`ideomod.mixins.json`)
5. Create `ModInitializer` and `ClientModInitializer` classes
6. Verify empty mod loads

### Phase 2: Stickiness Infrastructure (DO THIS FIRST)

**This is Fabric-specific work that doesn't exist in the NeoForge port.**

1. Study vanilla PistonStructureResolver source (Fabric-decompiled) to identify all `instanceof SlimeBlock` / `instanceof HoneyBlock` checks
2. Create `StickyBlockMixin` (or expand PistonStructureResolverMixin) with @Redirect injections for:
   - `isStickyBlock` equivalent (replace instanceof checks)
   - `canStickTo` equivalent (replace hardcoded slime/honey exclusion)
   - Branching sticky neighbor checks
3. Create `IStickyBlock` interface with default methods
4. Implement interface on: OddstoneBlock, OddSlimeBlock, FrameBlock, FramedBlock, all Machine Family blocks
5. Wire mixin to call IStickyBlock methods
6. Test basic slime + honey still work, then test custom sticky blocks

### Phase 3: Registry & Core Infrastructure

1. Rewrite all registry classes to use `Registry.register()` pattern
2. Port `Ideomod.java` as `ModInitializer`
3. Create `IdeomodClient.java` as `ClientModInitializer`
4. Port networking to Fabric Networking API
5. Port event handlers to Fabric callbacks + mixins where needed

### Phase 4: Simple Blocks

1. Port block classes (mostly unchanged -- vanilla API)
2. Remove `canStickTo`/`isStickyBlock` overrides (logic now in mixin)
3. Port configs (use Cloth Config API or simple JSON config)
4. Verify blocks register and appear in game

### Phase 5: Frame System

1. Port frame blocks (FrameBlock, FramedBlock, FramedBlockEntity, FramedBlockItem)
2. Port FramedBlockRecipe
3. For Fabric 1.20.1: NBT on items works as-is
4. For Fabric 1.21: Implement Data Components (same as NeoForge)
5. Port FrameActivation tick handler to Fabric tick events

### Phase 6: Movement & Stickiness System

1. Port StickyType, StickinessRules, IPistonMovable, MovingBlockEntityData
2. Port MovementScanHelper, BlockMoveHelper
3. Port PistonStructureResolverMixin -- **REWRITE** canStickTo redirects for vanilla targets
4. Port PistonBaseBlockMixin -- verify against vanilla (not Forge-patched) source
5. Port PistonMovingBlockEntityMixin
6. Port EntityMovementMixin
7. **Extensive testing** of all stickiness matrix combinations

### Phase 7: Machine System

1. Port AbstractMachineBlock, MachineType, IMachineFamily
2. Port MachineFrameBlockEntity, MachineSwapHelper, MachineRedstoneHelper
3. Port MachineFrameMenu, PositionBoundContainer (use Fabric's `ExtendedScreenHandlerType`)
4. Port all specializations
5. Port MachinePipeBlockEntity -- implement `Storage<ItemVariant>` for Transfer API
6. Port MachineFluidPipeBlockEntity -- implement `Storage<FluidVariant>` for Transfer API
7. **Convert fluid units** from Forge mB to Fabric droplets (×81)

### Phase 8: Portal System

1. Port portal blocks and handlers
2. Replace `EnderPearlPortalHandler` Forge event with mixin on projectile impact
3. Port SavedData (vanilla API, should be direct)
4. For Fabric 1.21: Implement DimensionTransition changes

### Phase 9: Multiblock System

1. Port StructureDefinition, Scanner, Manager (mostly vanilla API)
2. Port FurnaceArrayDefinition, FurnaceArrayEffect
3. Port AbstractFurnaceBlockEntityMixin

### Phase 10: Client & Rendering

1. Port ModColorHandlers to Fabric `ColorProviderRegistry`
2. Port FramedBlockRenderer BESR
3. Port MachinePistonArmRenderer BESR
4. Port FramedBlockItemModel -- implement `FabricBakedModel` interface
5. Port FramedBlockRenderTypes
6. Port MachineFrameScreen
7. Port client mixins
8. Register renderers in ClientModInitializer
9. Set up block render layers via `BlockRenderLayerMap`

### Phase 11: Effects, Commands, Config

1. Port JudgementEffect (replace Forge events with Fabric callbacks/mixins)
2. Port ModCommands via `CommandRegistrationCallback`
3. Port config system (Cloth Config API recommended, or simple JSON)

### Phase 12: Resources & Testing

1. Verify all resource files are in correct locations
2. For Fabric 1.21: restructure folders (plural→singular)
3. Comprehensive testing

---

## 22. 1.20.1 VS 1.21 FABRIC: DIFFERENCES BETWEEN TARGET VERSIONS

When porting for BOTH versions, here's what changes between them on the Fabric side:

| System | Fabric 1.20.1 | Fabric 1.21 |
|--------|--------------|-------------|
| Java | 17 | 21 |
| ItemStack data | CompoundTag (NBT) | Data Components |
| ResourceLocation | `new ResourceLocation(ns, path)` | `ResourceLocation.fromNamespaceAndPath(ns, path)` |
| Data folders | Plural (`recipes/`, `tags/blocks/`) | Singular (`recipe/`, `tag/block/`) |
| Recipes | `CraftingContainer` | `CraftingInput` |
| Portals | `changeDimension(ServerLevel)` | `changeDimension(DimensionTransition)` |
| Rendering vertices | `vertex()`, `color()`, `uv()` | `addVertex()`, `setColor()`, `setUv()` |
| Networking | ResourceLocation-based packets | CustomPayload records |
| SavedData | Direct constructor | `SavedData.Factory` + HolderLookup.Provider |
| Block methods | Public | Many now protected |
| Enchantments | Registry objects | Datapack registry (Holder-wrapped) |
| AttributeModifier | UUID-based | ResourceLocation-based |

**Recommendation**: Build for 1.20.1 first (simpler), then branch for 1.21. Most of the 1.20.1→1.21 changes are the same vanilla changes documented in PORTING_GUIDE_1.21.md.

---

## 23. PORTING-LIB: THE CREATE TEAM'S BRIDGE LAYER

### What Is It?

[Porting-Lib](https://github.com/Fabricators-of-Create/Porting-Lib) is a modular library created by the Create Fabric team that reimplements many Forge APIs for Fabric. It has **30 modules** covering:

- **`blocks`** -- Block extensions (may include sticky block hooks)
- **`base`** -- Core utilities
- **`registry`** -- DeferredRegister/DeferredHolder reimplementation for Fabric
- **`models` / `model_data` / `model_loader`** -- Forge model system
- **`fluids`** -- Fluid handling
- **`transfer`** -- Transfer API bridges
- **`tags`** -- Tag utilities
- **`loot`** -- Loot table extensions
- **`client_events` / `level_events`** -- Event bridges

### Should We Use It?

**Pros:**
- Dramatically reduces porting effort for Forge-specific APIs
- Battle-tested by the Create Fabric team
- Modular -- only include what you need
- Available for both 1.20.1 and 1.21.1 branches

**Cons:**
- Adds a runtime dependency (players need Porting-Lib installed)
- May include more than we need
- Less "pure Fabric" -- some Fabric purists avoid it
- If Porting-Lib stops being maintained, we're stuck

**Recommendation for Ideomod:**

Use Porting-Lib **selectively** for the hardest parts:
- **`blocks` module** -- if it provides `canStickTo`/`isStickyBlock` hooks, this saves our biggest headache
- **`registry` module** -- DeferredRegister pattern keeps code similar to Forge version
- **`model_data` module** -- if our FramedBlockItemModel needs Forge-style model data

Skip Porting-Lib for things Fabric API handles well natively:
- Networking (Fabric Networking API is clean)
- Events (Fabric callbacks are straightforward)
- Transfer API (native Fabric Transfer API is well-designed)

### Alternative: Cardinal Components API

For capability-like data attachment, [Cardinal Components API](https://github.com/OnyxStudios/Cardinal-Components-API) is the Fabric ecosystem's answer. We likely don't need it since our capabilities are simple (fluid/item storage on block entities), but it's worth knowing about for future features.

---

## 24. FILES NEEDED FROM USER

### For Fabric 1.20.1 Port

1. **Fabric Example Mod template** -- https://github.com/FabricMC/fabric-example-mod (branch: 1.20)
   - `build.gradle`, `settings.gradle`, `gradle.properties`
   - `src/main/resources/fabric.mod.json`

2. **Current Ideomod source code** -- Complete `src/` directory (same as NeoForge port)

3. **Vanilla 1.20.1 decompiled source** (from Fabric dev environment):
   - `net.minecraft.world.level.block.piston.PistonStructureResolver` -- **CRITICAL** for sticky block mixin work
   - `net.minecraft.world.level.block.piston.PistonBaseBlock`
   - `net.minecraft.world.level.block.SlimeBlock` -- to understand vanilla sticky logic
   - `net.minecraft.world.level.block.HoneyBlock` -- same reason

4. **Porting-Lib `blocks` module source** (optional but very helpful):
   - https://github.com/Fabricators-of-Create/Porting-Lib (branch: 1.20.1)
   - Specifically the `blocks/` module to see if/how they implement sticky hooks

### For Fabric 1.21 Port (Additional)

5. **Fabric Example Mod 1.21** -- https://github.com/FabricMC/fabric-example-mod (branch: 1.21)

6. **Vanilla 1.21 decompiled source** -- Same classes as above, 1.21 versions

### Reference Mods (See Section 25)

Having access to a Fabric mod that implements custom piston stickiness would be incredibly valuable. The Create Fabric source code is the best candidate.

---

## 25. REFERENCE REPOSITORIES

### Primary References

| Repository | URL | Branch | Use For |
|-----------|-----|--------|---------|
| **Fabric Example Mod** | https://github.com/FabricMC/fabric-example-mod | 1.20 / 1.21 | Project template, build system |
| **Fabric API** | https://github.com/FabricMC/fabric-api | 1.20.1 / 1.21 | API source, Transfer API examples, event patterns |
| **Porting-Lib** | https://github.com/Fabricators-of-Create/Porting-Lib | 1.20.1 / 1.21.1 | Forge→Fabric bridge, sticky block hooks, model system |
| **Create Fabric** | https://github.com/Fabricators-of-Create/Create | fabric-1.20.1 | Complex Fabric mod with piston/movement mechanics, BESRs, block entities |

### Secondary References

| Repository | URL | Use For |
|-----------|-----|---------|
| **Farmer's Delight Fabric** | https://github.com/newhoryzon/farmers-delight-fabric | Custom recipes, block entities, Fabric patterns |
| **Kaupenjoe Fabric Tutorial** | https://github.com/Kaupenjoe/Fabric-Tutorial-1.20.X | Basic Fabric 1.20.1 patterns |
| **Kaupenjoe Fabric Tutorial 1.21** | https://github.com/Kaupenjoe/Fabric-Tutorial-1.21.X | Basic Fabric 1.21 patterns |
| **Carpet Mod** | https://github.com/gnembon/fabric-carpet | Piston mechanics, block entity movement |
| **Piston Control** | Search on CurseForge/Modrinth | Custom piston behavior on Fabric |

### Documentation

| Resource | URL |
|----------|-----|
| **Fabric Documentation** | https://docs.fabricmc.net/develop/ |
| **Fabric Wiki** | https://wiki.fabricmc.net/ |
| **Fabric API Javadoc** | https://maven.fabricmc.net/docs/fabric-api-{version}/ |
| **Transfer API Wiki** | https://wiki.fabricmc.net/tutorial:transfer-api |
| **Fabric Events Index** | https://wiki.fabricmc.net/tutorial:event_index |

---

## 26. MIGRATION ORDER & CHECKLIST

### Pre-Port Checklist

- [ ] Download Fabric Example Mod template for target version(s)
- [ ] Install correct Java JDK (17 for 1.20.1, 21 for 1.21)
- [ ] Set up Fabric dev environment with official Mojang mappings
- [ ] Extract decompiled vanilla PistonStructureResolver source
- [ ] Study vanilla sticky block logic in detail (SlimeBlock, HoneyBlock, resolver)
- [ ] Download Porting-Lib source for reference (especially `blocks` module)
- [ ] Clone Create Fabric for reference

### Port Execution Checklist (Fabric 1.20.1)

- [ ] **Phase 1**: Project setup (Loom, fabric.mod.json, entrypoints)
- [ ] **Phase 2**: Stickiness infrastructure (**FABRIC-UNIQUE WORK** -- IStickyBlock interface, StickyBlockMixin)
- [ ] **Phase 3**: Registry & core infrastructure
- [ ] **Phase 4**: Simple blocks
- [ ] **Phase 5**: Frame system
- [ ] **Phase 6**: Movement & stickiness system (expanded mixin)
- [ ] **Phase 7**: Machine system (Transfer API for pipes)
- [ ] **Phase 8**: Portal system
- [ ] **Phase 9**: Multiblock system
- [ ] **Phase 10**: Client & rendering (FabricBakedModel)
- [ ] **Phase 11**: Effects, commands, config
- [ ] **Phase 12**: Resources & testing

### Additional Steps for Fabric 1.21 (After 1.20.1 port)

- [ ] Update build.gradle for 1.21 (MC version, Fabric API version, Java 21)
- [ ] ResourceLocation constructor changes
- [ ] Data Components for FramedBlockItem (same as NeoForge)
- [ ] Folder restructuring (plural→singular)
- [ ] Recipe system update (CraftingInput)
- [ ] Portal system update (DimensionTransition)
- [ ] Rendering vertex API updates
- [ ] Networking update (CustomPayload records)
- [ ] SavedData Factory pattern
- [ ] Block method visibility changes
- [ ] Testing

### Post-Port Checklist

- [ ] Vanilla slime/honey stickiness still works correctly
- [ ] ALL Ideomod sticky combinations work per stickiness matrix
- [ ] Framed blocks work (framing, drops, NBT/data components)
- [ ] Machine system works (all specializations, GUI, redstone)
- [ ] Item pipes work with vanilla containers (chests, hoppers)
- [ ] Fluid pipes work with Transfer API
- [ ] Portal system works
- [ ] Multiblock formation works
- [ ] Piston movement + sympathetic oddstone works
- [ ] Rendering works (BESRs, framed overlays, piston arms)
- [ ] No Fabric API deprecation warnings

---

## 27. RISK ASSESSMENT & RECOMMENDATIONS

### Risk Matrix (Fabric Port)

| Risk Level | Systems | Notes |
|-----------|---------|-------|
| **CRITICAL** | Stickiness system (canStickTo/isStickyBlock replacement) | Must rewrite from scratch using mixins. Single biggest piece of new work. |
| **HIGH** | PistonStructureResolverMixin canStickTo redirect | The Redirect target doesn't exist in vanilla. Need new injection strategy. |
| **HIGH** | Transfer API (FluidPipe) | Different unit system (droplets vs mB), different API shape. |
| **MEDIUM** | Rendering (FabricBakedModel) | Different custom model interface. Quad emission pattern differs. |
| **MEDIUM** | Event replacement | Some events have no Fabric equivalent and need mixin replacements. |
| **LOW** | Registry migration | Mechanical but straightforward. |
| **LOW** | Networking | Fabric networking is clean and well-documented. |
| **LOW** | Resources/data files | Same folder structure (1.20.1) or same changes as NeoForge (1.21). |

### Estimated Effort Comparison

| Task | NeoForge Port | Fabric Port |
|------|--------------|-------------|
| Build system / project setup | 2-3 hours | 2-3 hours |
| Registry migration | 2-3 hours | 3-4 hours |
| **Stickiness system** | **1-2 hours** (rename/adjust) | **8-16 hours** (new mixin work) |
| Capability → Transfer API | 3-4 hours | 5-8 hours |
| Networking | 2-3 hours | 2-3 hours |
| Events → Callbacks | 2-3 hours | 4-6 hours |
| Rendering | 4-6 hours | 6-8 hours |
| Portal system (1.21) | 3-4 hours | 3-4 hours |
| **Total estimated** | **20-30 hours** | **35-55 hours** |

### Recommendations

1. **Port to NeoForge 1.21 FIRST** -- It's significantly less work and validates that our game logic works on 1.21 before adding the Fabric complexity.

2. **Port to Fabric 1.20.1 SECOND** -- Same MC version as current Forge build. Isolates Forge→Fabric API changes from MC version changes.

3. **Port to Fabric 1.21 LAST** -- Combine the working Fabric 1.20.1 codebase with the vanilla 1.21 changes we already solved in the NeoForge port.

4. **Investigate Porting-Lib's `blocks` module before starting** -- If it provides canStickTo/isStickyBlock hooks, it could save 8-16 hours of the most complex mixin work.

5. **Consider a shared "common" module** -- Many modders use a multi-loader architecture (one `common/` source set, separate `forge/` and `fabric/` source sets for platform-specific code). Libraries like **Architectury** can help with this, though they add complexity.

---

*End of Fabric porting guide. Use alongside PORTING_GUIDE_1.21.md for complete multi-loader coverage.*
