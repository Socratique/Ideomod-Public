# IDEOMOD PORTING GUIDE: Forge 1.20.1 → NeoForge 1.21
*Comprehensive migration reference for porting Ideomod from Minecraft Forge 1.20.1 to NeoForge 1.21.*
*Written by Claude, directed and guided by The Ideomancer.*
*Created: 2026-03-22*

---

## TABLE OF CONTENTS

1. [Overview & Scope](#1-overview--scope)
2. [Environment & Build System Changes](#2-environment--build-system-changes)
3. [Java Version Upgrade](#3-java-version-upgrade)
4. [Registry System Migration](#4-registry-system-migration)
5. [Data Components (NBT Replacement)](#5-data-components-nbt-replacement)
6. [Capability System Rework](#6-capability-system-rework)
7. [Event System Changes](#7-event-system-changes)
8. [Networking Rework](#8-networking-rework)
9. [Block & BlockState Changes](#9-block--blockstate-changes)
10. [Block Entity Changes](#10-block-entity-changes)
11. [Menu & GUI Changes](#11-menu--gui-changes)
12. [Rendering Changes](#12-rendering-changes)
13. [Mixin Migration](#13-mixin-migration)
14. [Resource & Data Folder Restructuring](#14-resource--data-folder-restructuring)
15. [Recipe System Changes](#15-recipe-system-changes)
16. [Portal & Dimension Changes](#16-portal--dimension-changes)
17. [SavedData Changes](#17-saveddata-changes)
18. [Enchantment System Overhaul](#18-enchantment-system-overhaul)
19. [ResourceLocation Changes](#19-resourcelocation-changes)
20. [Tag System Changes](#20-tag-system-changes)
21. [Ideomod System-by-System Migration Plan](#21-ideomod-system-by-system-migration-plan)
22. [Files Needed From User](#22-files-needed-from-user)
23. [Reference Repositories](#23-reference-repositories)
24. [Migration Order & Checklist](#24-migration-order--checklist)

---

## 1. OVERVIEW & SCOPE

### What We're Porting

Ideomod is a vanilla+ Minecraft mod currently built on:
- Minecraft 1.20.1
- Forge 47.4.10
- Java 17
- SpongePowered Mixin 0.7.x
- Official mappings 1.20.1

Target platform:
- Minecraft 1.21(.1)
- NeoForge 21.x
- Java 21
- SpongePowered Mixin (NeoForge-managed)
- Official mappings 1.21

### Scope of Changes

This port spans FOUR major version transitions (1.20.1 -> 1.20.4 -> 1.20.5/6 -> 1.21), each with significant breaking changes. The most impactful are:

1. **1.20.4 -> 1.20.5**: Data Components replace NBT on ItemStacks, Block methods become protected, Java 21 required, Codec/StreamCodec serialization
2. **1.20.5 -> 1.21**: ResourceLocation API overhaul, folder restructuring (plural->singular), rendering vertex system rewrite, enchantment system overhaul, portal/dimension rework, recipe input rework
3. **Forge -> NeoForge**: Capability rework (LazyOptional removed), event system changes, registry rework (RegistryObject -> DeferredHolder), networking rework (SimpleChannel -> CustomPacketPayload), mods.toml -> neoforge.mods.toml

### Systems That Will Be Affected

| System | Impact Level | Key Changes |
|--------|-------------|-------------|
| Build system & project structure | HIGH | Complete rebuild: Gradle plugins, neoforge.mods.toml, Java 21 |
| Registry system (ModBlocks, ModItems, etc.) | MEDIUM | RegistryObject -> DeferredHolder, DeferredRegister API tweaks |
| Stickiness system & mixin | HIGH | PistonStructureResolver mixin targets may shift; canStickTo/isStickyBlock API needs verification |
| Framed block NBT system | HIGH | CompoundTag on ItemStacks replaced by Data Components |
| Capability system (IFluidHandler) | HIGH | LazyOptional removed, new BlockCapability query API |
| Networking (RepeaterDelayPacket, TransformerLevelPacket) | HIGH | SimpleChannel completely replaced by CustomPacketPayload |
| Portal system | HIGH | changeDimension/PortalInfo completely reworked |
| Rendering (BESRs, BakedModel, RenderType) | HIGH | Vertex system rewrite, method renames |
| Event handlers | MEDIUM | @Mod.EventBusSubscriber changes, ICancellableEvent replaces @Cancelable |
| GUI/Menu system | MEDIUM | Screen rendering changes, ContainerMenu adjustments |
| Resource/data files | MEDIUM | Folder restructuring (plural->singular), JSON format changes |
| Multiblock/SavedData | LOW-MEDIUM | SavedData API adjustments, DimensionDataStorage changes |
| Recipes | MEDIUM | RecipeInput replaces Container, CraftingInput for grid recipes |
| Tags | MEDIUM | Folder paths change, TagKey API adjustments |

### Remaining Features (Machine Specializations, Oddstone Worldgen, Hollowstone)

These pending features should NOT significantly complicate the port:
- **Machine specializations** (remaining): Follow the same AbstractMachineBlock pattern already ported. Once the base Machine system is ported, new specializations slot in trivially.
- **Oddstone worldgen**: Standard ore generation / feature placement. NeoForge 1.21 worldgen APIs are similar to 1.20.1 with minor adjustments.
- **Hollowstone**: A simple block with lava interaction. Trivial once the block registry system is ported.

---

## 2. ENVIRONMENT & BUILD SYSTEM CHANGES

### Gradle & Build System

**Forge 1.20.1 (current):**
- ForgeGradle plugin
- `build.gradle` with ForgeGradle DSL
- `mods.toml` at `src/main/resources/META-INF/mods.toml`
- MixinGradle plugin (`org.spongepowered.mixin`)

**NeoForge 1.21 (target):**
- ModDevGradle (recommended) or NeoGradle plugin
- `build.gradle` with NeoForge DSL
- `neoforge.mods.toml` at `src/main/resources/META-INF/neoforge.mods.toml`
- Mixin support built into NeoForge (no separate MixinGradle plugin needed)

### Key build.gradle Changes

```groovy
// OLD (Forge 1.20.1)
plugins {
    id 'net.minecraftforge.gradle' version '...'
    id 'org.spongepowered.mixin' version '...'
}
minecraft { mappings channel: 'official', version: '1.20.1' }
dependencies { minecraft 'net.minecraftforge:forge:1.20.1-47.4.10' }

// NEW (NeoForge 1.21 with ModDevGradle)
plugins {
    id 'net.neoforged.moddev' version '...'
}
neoForge {
    version = "21.0.x-beta"  // check latest
    // Mixin config specified here or via neoforge.mods.toml
}
```

### neoforge.mods.toml Structure

The file is similar to mods.toml but with NeoForge-specific keys:
- `modLoader="javafml"` (same)
- `loaderVersion="[4,)"` for NeoForge (was `[47,)` for Forge)
- Dependency type changed from `forge` to `neoforge`
- Mixin configs can be declared here

### gradle.properties Updates

```properties
# Key changes
mod_id=ideomod
minecraft_version=1.21.1
neo_version=21.1.x  # check latest stable
```

---

## 3. JAVA VERSION UPGRADE

**Java 17 -> Java 21**

This is required starting from Minecraft 1.20.5. Key impacts for our codebase:
- Should be mostly transparent -- our code doesn't use deprecated Java features
- Record classes (we use PortalGeometry as a record) remain fully supported
- Switch expressions, text blocks, sealed classes all available
- `ThreadLocal` usage (in PistonStructureResolverMixin) unchanged
- Ensure development JDK is Java 21

---

## 4. REGISTRY SYSTEM MIGRATION

### RegistryObject -> DeferredHolder

**Forge 1.20.1 (current):**
```java
public static final RegistryObject<Block> ODDSTONE = BLOCKS.register("oddstone", () -> new OddstoneBlock(...));
```

**NeoForge 1.21:**
```java
public static final DeferredHolder<Block, OddstoneBlock> ODDSTONE = BLOCKS.register("oddstone", () -> new OddstoneBlock(...));
// OR using Supplier if you don't need Holder functions:
public static final Supplier<OddstoneBlock> ODDSTONE = BLOCKS.register("oddstone", () -> new OddstoneBlock(...));
```

### DeferredRegister Changes

The `DeferredRegister.create()` API is similar but imports change:
- `net.minecraftforge.registries.DeferredRegister` -> `net.neoforged.neoforge.registries.DeferredRegister`
- `net.minecraftforge.registries.ForgeRegistries` -> `net.minecraft.core.registries.BuiltInRegistries` or `net.neoforged.neoforge.registries.NeoForgeRegistries`

### Affected Files

- `ModBlocks.java` -- All RegistryObject<Block> -> DeferredHolder<Block, ?>
- `ModItems.java` -- All RegistryObject<Item> -> DeferredHolder<Item, ?>
- `ModBlockEntities.java` -- All RegistryObject<BlockEntityType<?>> -> DeferredHolder
- `ModMenuTypes.java` -- RegistryObject<MenuType<?>> -> DeferredHolder
- `ModRecipeSerializers.java` -- RegistryObject<RecipeSerializer<?>> -> DeferredHolder
- `ModCreativeTabs.java` -- RegistryObject -> DeferredHolder
- Any code that calls `.get()` on RegistryObject should still work (DeferredHolder extends Supplier)

### Event Bus Registration

```java
// OLD (Forge)
BLOCKS.register(modEventBus);

// NEW (NeoForge) -- same pattern, different import
BLOCKS.register(modEventBus);
```

---

## 5. DATA COMPONENTS (NBT REPLACEMENT)

**This is one of the most impactful changes for Ideomod**, particularly for the Framed Block system.

### What Changed

Starting in 1.20.5, `ItemStack` no longer has a `CompoundTag` (`tag` field). Instead, it uses a `DataComponentMap` with typed, immutable component values.

### Ideomod Impact: FramedBlockItem & FramedBlockEntity

The FramedBlockItem system currently stores the inner block's ResourceLocation as NBT on the ItemStack. This MUST be converted to a Data Component.

**Current (1.20.1):**
```java
// FramedBlockItem -- stores inner block ID in NBT
stack.getOrCreateTag().putString("inner_block", innerBlockId.toString());
// Reading:
String id = stack.getTag().getString("inner_block");
```

**NeoForge 1.21:**
```java
// Define a custom DataComponentType in registration
public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> INNER_BLOCK =
    DATA_COMPONENTS.register("inner_block", () ->
        DataComponentType.<ResourceLocation>builder()
            .persistent(ResourceLocation.CODEC)
            .networkSynchronized(ResourceLocation.STREAM_CODEC)
            .build()
    );

// Writing:
stack.set(ModDataComponents.INNER_BLOCK, innerBlockId);
// Reading:
ResourceLocation id = stack.get(ModDataComponents.INNER_BLOCK);
```

### New Registration File Needed

Create `ModDataComponents.java`:
```java
public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, "ideomod");

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> INNER_BLOCK = ...;
    // Register on mod bus in Ideomod constructor
}
```

### Other NBT Usages to Audit

- `FramedBlockRecipe.java` -- Creates framed block items with NBT
- `MachineSwapHelper.java` -- createPreloadedStack() may use NBT for specialization data
- `FramedBlockEntity.java` -- Block entity NBT (save/load) is UNCHANGED -- only ItemStack NBT changes
- `MachineFrameBlockEntity.java` -- Block entity NBT (save/load) is UNCHANGED
- Any place that calls `stack.getTag()`, `stack.getOrCreateTag()`, or `stack.hasTag()`

### Custom Data Fallback

For arbitrary data that doesn't warrant a formal component, `minecraft:custom_data` component exists as a bridge. However, for our inner block system, a proper typed component is better.

---

## 6. CAPABILITY SYSTEM REWORK

### LazyOptional Removed

**Forge 1.20.1 (current):**
```java
// Block entity exposing capability
@Override
public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
    if (cap == ForgeCapabilities.FLUID_HANDLER) return fluidHandler.cast();
    return super.getCapability(cap, side);
}
```

**NeoForge 1.21:**
```java
// Capabilities are registered externally, not overridden in the BE
// In your mod's event handler or main class:
event.registerBlockEntity(
    Capabilities.FluidHandler.BLOCK,
    ModBlockEntities.MACHINE_FLUID_PIPE.get(),
    (be, side) -> be.getFluidHandler(side)  // returns @Nullable IFluidHandler
);
```

### Affected Files

- **MachineFluidPipeBlockEntity.java** -- Currently implements IFluidHandler capability via LazyOptional. Must be refactored to register capability externally.
- **MachinePipeBlockEntity.java** -- If it exposes IItemHandler (for item transfer), same treatment.
- Any code that queries capabilities from neighboring blocks using `level.getBlockEntity(pos).getCapability(...)` must change to `level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side)`.

### BlockCapabilityCache

For efficient repeated queries (like pipe tick methods checking neighbors), use `BlockCapabilityCache`:
```java
// Instead of querying every tick:
BlockCapabilityCache<IItemHandler, Direction> cache =
    BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, serverLevel, pos, direction);
// Then: cache.getCapability() -- auto-invalidates when BE changes
```

---

## 7. EVENT SYSTEM CHANGES

### @Mod.EventBusSubscriber

**Forge 1.20.1:**
```java
@Mod.EventBusSubscriber(modid = "ideomod", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModCommands { ... }

@Mod.EventBusSubscriber(modid = "ideomod", bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientSetup { ... }
```

**NeoForge 1.21:**
```java
@EventBusSubscriber(modid = "ideomod")
// Bus is auto-detected based on event type -- no explicit bus parameter needed
public class ModCommands { ... }
```

### ICancellableEvent

**Forge 1.20.1:**
```java
@Cancelable annotation on event classes
event.setCanceled(true);
```

**NeoForge 1.21:**
```java
// Events implement ICancellableEvent interface instead
event.setCanceled(true); // method still exists
```

### Affected Files

- `ModCommands.java` -- Uses `@Mod.EventBusSubscriber` with FORGE bus
- `JudgementEffect.java` -- Uses Forge event bus
- `EnderPearlPortalHandler.java` -- Uses Forge event handler
- `Ideomod.java` -- Main mod class event registration
- `ModColorHandlers.java` -- Client-side event registration
- `FramedBlockDebugOverlay.java` -- Client event registration

### Static-ness Enforcement

NeoForge now throws errors if `@SubscribeEvent` methods have mismatching static-ness. Class-based registrations MUST be static; object-based MUST be non-static. Audit all event handlers.

---

## 8. NETWORKING REWORK

### SimpleChannel -> CustomPacketPayload

**Forge 1.20.1 (current):**
```java
// ModNetworking.java -- uses SimpleChannel
private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(...);
CHANNEL.registerMessage(id, RepeaterDelayPacket.class, RepeaterDelayPacket::encode, RepeaterDelayPacket::decode, RepeaterDelayPacket::handle);
```

**NeoForge 1.21:**
```java
// Packets implement CustomPacketPayload
public record RepeaterDelayPacket(BlockPos pos, int delay) implements CustomPacketPayload {
    public static final Type<RepeaterDelayPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ideomod", "repeater_delay"));
    public static final StreamCodec<FriendlyByteBuf, RepeaterDelayPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, RepeaterDelayPacket::pos,
        ByteBufCodecs.INT, RepeaterDelayPacket::delay,
        RepeaterDelayPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

// Registration in RegisterPayloadHandlersEvent
@SubscribeEvent
public static void register(RegisterPayloadHandlersEvent event) {
    PayloadRegistrar registrar = event.registrar("ideomod");
    registrar.playToServer(RepeaterDelayPacket.TYPE, RepeaterDelayPacket.STREAM_CODEC, RepeaterDelayPacket::handle);
}
```

### Affected Files

- `ModNetworking.java` -- Complete rewrite
- `RepeaterDelayPacket.java` -- Rewrite as CustomPacketPayload record
- `TransformerLevelPacket.java` -- Rewrite as CustomPacketPayload record
- Any code that calls `ModNetworking.CHANNEL.sendToServer(...)` -> use `PacketDistributor`

---

## 9. BLOCK & BLOCKSTATE CHANGES

### Protected Block Methods

Starting in 1.20.5, many Block methods became protected and must be accessed through BlockState:

**Methods now protected (partial list relevant to Ideomod):**
- `updateShape` -> access via `BlockState#updateShape`
- `onPlace` -> access via `BlockState#onPlace`
- `onRemove` -> access via `BlockState#onRemove`
- `getShape` / `getCollisionShape` -> access via `BlockState#getShape`
- `neighborChanged` -> access via `BlockState#neighborChanged`
- `isPathfindable` -> signature changed (no longer takes `BlockGetter` parameter)
- `use` / `attack` -> access via `BlockState`

**Important:** Within Block subclass code (our own block classes), calling these methods on `this` still works because they're protected (same class hierarchy). The issue is calling them on OTHER blocks -- those must go through BlockState.

### Affected Files

- `OddstoneBlock.java` -- isPathfindable signature change
- `AbstractMachineBlock.java` -- if it calls methods on other blocks
- `BlockMoveHelper.java` -- if it accesses block methods on arbitrary blocks
- `FrameBlock.java`, `FramedBlock.java` -- updateShape, neighborChanged
- All piston/movement code that interacts with arbitrary block states

### VoxelShape Changes

Many Block subclasses that had public/protected VoxelShapes are now private. If our mixins or code access vanilla shape fields, these may need adjustment.

---

## 10. BLOCK ENTITY CHANGES

### Constructor & Registration

Block entity registration is similar but uses DeferredHolder:

```java
// NeoForge 1.21
public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineFrameBlockEntity>> MACHINE_FRAME =
    BLOCK_ENTITIES.register("machine_frame", () ->
        BlockEntityType.Builder.of(MachineFrameBlockEntity::new,
            ModBlocks.MACHINE_BLOCK.get(), ModBlocks.MACHINE_TRACK.get(), /* etc */)
        .build(null)
    );
```

### Capability Registration (External)

Block entity capabilities are no longer overridden inside the BE class. They're registered in a `RegisterCapabilitiesEvent` handler. See Section 6.

### Client Sync

`getUpdatePacket()` and `getUpdateTag()` patterns remain similar. The `load()` method may have minor signature differences -- verify `HolderLookup.Provider` parameter requirements.

### Affected Files

- `ModBlockEntities.java` -- DeferredHolder migration
- `MachineFrameBlockEntity.java` -- Capability registration externalized
- `MachinePipeBlockEntity.java` -- Capability + sync
- `MachineFluidPipeBlockEntity.java` -- IFluidHandler capability externalized
- `FramedBlockEntity.java` -- save/load may need HolderLookup.Provider
- `OddstonePortalSavedData.java` -- SavedData pattern

---

## 11. MENU & GUI CHANGES

### Menu Registration

MenuType registration uses DeferredHolder. The `IContainerFactory` from Forge becomes NeoForge's equivalent:

```java
// NeoForge 1.21
public static final DeferredHolder<MenuType<?>, MenuType<MachineFrameMenu>> MACHINE_FRAME =
    MENU_TYPES.register("machine_frame", () ->
        IMenuTypeExtension.create(MachineFrameMenu::new)
    );
```

### Screen Rendering

- `Screen#renderBackground` has been split into multiple methods
- `GuiGraphics` replaces some direct rendering calls
- `MachineFrameScreen.java` will need rendering method updates

### Affected Files

- `ModMenuTypes.java` -- DeferredHolder
- `MachineFrameMenu.java` -- ContainerData, slot handling
- `MachineFrameScreen.java` -- Rendering method signatures, background rendering
- `PositionBoundContainer.java` -- Container interface changes (RecipeInput may affect)

---

## 12. RENDERING CHANGES

### Vertex System Rewrite (1.21)

This is a major rendering overhaul:

| Old (1.20.1) | New (1.21) |
|--------------|-----------|
| `BufferBuilder#end()` | `BufferBuilder#buildOrThrow()` |
| `BufferBuilder$RenderedBuffer` | `MeshData` |
| `endVertex()` | Removed (automatic on `addVertex()`) |
| `vertex()` | `addVertex()` |
| `color()` | `setColor()` |
| `uv()` | `setUv()` |
| `overlayCoords()` | `setOverlay()` / `setUv1()` |
| `uv2()` | `setLight()` / `setUv2()` |
| `normal()` | `setNormal()` |
| `RenderType#end()` | `RenderType#draw()` |

### Color Tinting

Model rendering now uses ARGB integer tint instead of four separate float values.

### Affected Files

- `FramedBlockRenderer.java` -- BESR for framed blocks. Must update vertex methods, color handling.
- `MachinePistonArmRenderer.java` -- BESR for piston arms. Same vertex updates.
- `FramedBlockItemModel.java` -- Custom BakedModel. Quad compositing, tint index handling.
- `FramedBlockRenderTypes.java` -- Custom RenderType. Check shader references:
  - `minecraft:position_color_tex` -> `minecraft:position_tex_color`
  - `minecraft:rendertype_glint_direct` -> `minecraft:rendertype_glint`
- `MovingFrameData.java` -- Client-side cache (may be unaffected)
- `ModColorHandlers.java` -- Block/item color tinting (ARGB integer change)
- `PistonMovingBlockEntityRendererMixin.java` -- Client mixin for piston transit rendering

---

## 13. MIXIN MIGRATION

### Mixin Support in NeoForge

NeoForge has built-in mixin support -- no separate MixinGradle plugin needed. Mixin configs are declared in `neoforge.mods.toml` or via the build system.

### Mixin Config

**Current (`ideomod.mixins.json`):**
```json
{
  "required": true,
  "package": "com.ideomancer.ideomod.mixin",
  "compatibilityLevel": "JAVA_17",
  "refmap": "ideomod.refmap.json",
  "mixins": [
    "PistonStructureResolverMixin",
    "PistonBaseBlockMixin",
    "PistonMovingBlockEntityMixin",
    "EntityMovementMixin",
    "AbstractFurnaceBlockEntityMixin",
    "client.PistonBaseBlockClientMixin",
    "client.PistonMovingBlockEntityRendererMixin"
  ]
}
```

**NeoForge 1.21:**
```json
{
  "required": true,
  "package": "com.ideomancer.ideomod.mixin",
  "compatibilityLevel": "JAVA_21",
  "mixins": [
    "PistonStructureResolverMixin",
    "PistonBaseBlockMixin",
    "PistonMovingBlockEntityMixin",
    "EntityMovementMixin",
    "AbstractFurnaceBlockEntityMixin",
    "client.PistonBaseBlockClientMixin",
    "client.PistonMovingBlockEntityRendererMixin"
  ]
}
```

Key changes: `compatibilityLevel` -> `JAVA_21`, `refmap` field may be unnecessary (NeoForge handles refmaps automatically).

### Critical: PistonStructureResolver Mixin Targets

This is the HIGHEST RISK area of the port. The PistonStructureResolver mixin uses:

1. **@Shadow fields**: `level`, `toPush`, `pushDirection`, `startPos` -- verify field names haven't changed in 1.21 mappings
2. **@Shadow method**: `addBlockLine(BlockPos, Direction)` -- verify signature
3. **@Inject into `resolve()`** -- verify method still exists with same signature
4. **@Inject into `addBranchingBlocks()`** -- verify method still exists
5. **@Inject into `addBlockLine()` HEAD** -- verify method structure
6. **@Redirect on `canStickTo` INVOKE** -- CRITICAL: verify the exact `canStickTo` call target string. If vanilla renamed or moved the method, the redirect breaks silently.
7. **@ModifyConstant(constant = @Constant(intValue = 12))** -- verify the push limit constant is still 12 and in the same method

### Verification Steps for Mixins

For EACH mixin, during the port:

1. Open the corresponding vanilla class in NeoForge 1.21 source
2. Verify all @Shadow field names match
3. Verify all @Inject/@Redirect/@ModifyConstant target method names match
4. Verify method descriptors match (parameter types may have changed)
5. Check if NeoForge added its own hooks that could replace some mixin functionality

### canStickTo / isStickyBlock in NeoForge

NeoForge 1.21 still provides `isStickyBlock()` and `canStickTo()` on BlockState. The bidirectional check pattern should be the same. However, verify:
- The exact method signatures
- Whether NeoForge added any additional piston hooks that might conflict with or replace our mixin features
- Whether the `PistonStructureResolver` class has been refactored

### Mixin-by-Mixin Risk Assessment

| Mixin | Risk | Reason |
|-------|------|--------|
| PistonStructureResolverMixin | VERY HIGH | Complex multi-injection mixin targeting vanilla internals. Any vanilla refactor breaks it. |
| PistonBaseBlockMixin | HIGH | Targets piston behavior. Depends on method signatures. |
| PistonMovingBlockEntityMixin | MEDIUM | Data restoration after animation. Relatively stable target. |
| EntityMovementMixin | LOW | Speed/jump factor injection. Entity movement methods are stable. |
| AbstractFurnaceBlockEntityMixin | LOW | Furnace speed boost. Furnace internals rarely change. |
| PistonBaseBlockClientMixin | MEDIUM | Client-side piston data capture. Rendering changes may affect. |
| PistonMovingBlockEntityRendererMixin | HIGH | Client rendering mixin. Vertex system rewrite will affect. |

---

## 14. RESOURCE & DATA FOLDER RESTRUCTURING

### Folder Name Changes (Plural -> Singular)

**This affects ALL data files in the mod:**

| Old Path (1.20.1) | New Path (1.21) |
|-------------------|-----------------|
| `data/ideomod/recipes/` | `data/ideomod/recipe/` |
| `data/ideomod/loot_tables/blocks/` | `data/ideomod/loot_table/block/` |
| `data/ideomod/tags/blocks/` | `data/ideomod/tag/block/` |
| `data/minecraft/tags/blocks/` | `data/minecraft/tag/block/` |
| `data/ideomod/worldgen/` | `data/ideomod/worldgen/` (unchanged) |
| `data/minecraft/tags/worldgen/` | `data/minecraft/tag/worldgen/` |

### Affected Resource Files

ALL of these files need to be moved to new paths:

**Loot tables (22 files):**
- `data/ideomod/loot_tables/blocks/*.json` -> `data/ideomod/loot_table/block/*.json`

**Recipes (8 files):**
- `data/ideomod/recipes/*.json` -> `data/ideomod/recipe/*.json`

**Tags (6 files):**
- `data/ideomod/tags/blocks/*.json` -> `data/ideomod/tag/block/*.json`
- `data/minecraft/tags/blocks/mineable/pickaxe.json` -> `data/minecraft/tag/block/mineable/pickaxe.json`
- `data/minecraft/tags/worldgen/world_preset/*.json` -> `data/minecraft/tag/worldgen/world_preset/*.json`

**Worldgen:**
- `data/ideomod/worldgen/` -- path may be unchanged, but verify subfolders

### Assets (Unchanged)

The `assets/` folder structure (blockstates, models, textures, lang) is NOT affected by the plural->singular change. These stay as-is.

---

## 15. RECIPE SYSTEM CHANGES

### Container -> RecipeInput

**Forge 1.20.1:**
```java
public class FramedBlockRecipe extends CustomRecipe {
    @Override
    public boolean matches(CraftingContainer container, Level level) { ... }
}
```

**NeoForge 1.21:**
```java
public class FramedBlockRecipe extends CustomRecipe {
    @Override
    public boolean matches(CraftingInput input, Level level) { ... }
    // CraftingInput replaces CraftingContainer for recipe matching
    // CraftingInput.of(width, height, items) to create
}
```

### RecipeSerializer Changes

RecipeSerializer now uses Codec and StreamCodec for serialization instead of manual `fromJson`/`fromNetwork`/`toNetwork` methods.

### Affected Files

- `FramedBlockRecipe.java` -- CraftingContainer -> CraftingInput, serializer changes
- `ModRecipeSerializers.java` -- Registration + serializer implementation

---

## 16. PORTAL & DIMENSION CHANGES

### Major Portal System Overhaul

**This is HIGH IMPACT for our Oddstone Portal system.**

| Old (1.20.1) | New (1.21) |
|--------------|-----------|
| `entity.changeDimension(ServerLevel)` | `entity.changeDimension(DimensionTransition)` |
| `PortalInfo` | `DimensionTransition` (contains level, pos, motion, rotation) |
| `findDimensionEntryPoint()` | `Portal#getPortalDestination()` |
| `handleInsidePortal()` | `setAsInsidePortal()` |

### DimensionTransition

```java
// NeoForge 1.21
DimensionTransition transition = new DimensionTransition(
    targetLevel,      // ServerLevel
    targetPos,        // Vec3 position
    Vec3.ZERO,        // motion
    yRot, xRot,       // rotation
    DimensionTransition.DO_NOTHING  // post-transition callback
);
entity.changeDimension(transition);
```

### Portal Interface

Blocks that act as portals may need to implement the `Portal` interface, which provides `getPortalDestination()`.

### Affected Files

- `OddstonePortalBlock.java` -- Teleportation logic, Portal interface
- `OddstonePortalHandler.java` -- Activation/deactivation lifecycle
- `OddstonePortalFrameDetector.java` -- Frame scanning (may be fine)
- `OddstonePortalSavedData.java` -- Registry data (should be fine)
- `EnderPearlPortalHandler.java` -- Ender pearl activation event

---

## 17. SAVEDDATA CHANGES

### API Adjustments

SavedData in NeoForge 1.21 uses `SavedData.Factory` for creation:

```java
// NeoForge 1.21
serverLevel.getDataStorage().computeIfAbsent(
    new SavedData.Factory<>(
        OddstonePortalSavedData::new,       // supplier
        OddstonePortalSavedData::load,       // loader (takes CompoundTag + HolderLookup.Provider)
        null                                  // DataFixTypes (optional)
    ),
    "oddstone_portals"
);
```

### HolderLookup.Provider

The `load` method now receives a `HolderLookup.Provider` parameter for registry lookups during deserialization. The `save` method similarly receives it.

### Affected Files

- `OddstonePortalSavedData.java` -- Factory pattern, HolderLookup.Provider in load/save
- `StructureManager.java` -- Per-dimension SavedData for multiblock structures

---

## 18. ENCHANTMENT SYSTEM OVERHAUL

Enchantments became datapack registry objects in 1.21. Since Ideomod doesn't currently add custom enchantments, this has minimal direct impact. However, any code that references enchantments (e.g., checking for Silk Touch in OddstoneBlock) needs to use `Holder<Enchantment>` instead of direct `Enchantment` references:

```java
// Old
if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool) > 0)

// New -- requires HolderLookup
// Enchantments are now looked up from the registry via holders
```

---

## 19. RESOURCELOCATION CHANGES

### Constructor Removed

`ResourceLocation` is now final with a private constructor:

| Old | New |
|-----|-----|
| `new ResourceLocation("ideomod", "oddstone")` | `ResourceLocation.fromNamespaceAndPath("ideomod", "oddstone")` |
| `new ResourceLocation("minecraft:stone")` | `ResourceLocation.parse("minecraft:stone")` |
| `new ResourceLocation("stone")` | `ResourceLocation.withDefaultNamespace("stone")` |

### Widespread Impact

This affects EVERY file that creates ResourceLocation instances. Search the entire codebase for `new ResourceLocation` and replace.

### Affected Files (non-exhaustive)

- `FramedBlockEntity.java` -- Stores inner block as ResourceLocation
- `FramedBlockItem.java` -- Creates ResourceLocation from string
- `ModTags.java` -- TagKey creation
- `OddstonePortalSavedData.java` -- Portal registry
- `StructureRegistration.java` -- Structure IDs
- Many model/texture reference strings throughout resources

---

## 20. TAG SYSTEM CHANGES

### Folder Path Changes

As noted in Section 14, tag folders change from plural to singular:
- `tags/blocks/` -> `tag/block/`

### TagKey API

The `TagKey.create()` API should remain similar, but imports change for NeoForge.

### Affected Files

- `ModTags.java` -- TagKey definitions
- All JSON files under `data/*/tags/`

---

## 21. IDEOMOD SYSTEM-BY-SYSTEM MIGRATION PLAN

### Phase 1: Project Setup (Do First)

1. Create new NeoForge 1.21 project from MDK template
2. Copy over package structure
3. Configure `neoforge.mods.toml`, `build.gradle`, `gradle.properties`
4. Set up mixin config
5. Verify project compiles (empty mod)

### Phase 2: Registry & Core Infrastructure

1. Port `Ideomod.java` main mod class (@Mod annotation, constructor)
2. Port all registry classes (ModBlocks, ModItems, ModBlockEntities, ModMenuTypes, ModRecipeSerializers, ModCreativeTabs, ModTags)
3. Create `ModDataComponents.java` for framed block inner block data
4. Port `ModNetworking.java` + packet classes to CustomPacketPayload
5. Verify project compiles

### Phase 3: Simple Blocks & Items

1. Port `OddstoneBlock.java` (isPathfindable signature change)
2. Port `OddSlimeBlock.java`
3. Port `DrillBlock.java` (on hold, but port the class)
4. Port basic block properties, verify registrations work
5. Port `IdeomodConfig.java` and `OddstoneConfig.java`

### Phase 4: Frame System

1. Port `IFrameBlock.java`, `FrameMaterial.java`, `FrameBlock.java`
2. Port `FramedBlock.java`, `FramedBlockEntity.java`
3. Port `FramedBlockItem.java` -- **Data Component migration** (critical)
4. Port `FramedBlockRecipe.java` -- RecipeInput + Codec serializer
5. Port `FrameActivation.java`, `FrameabilityHelper.java`
6. Port `FramedBlockLoot.java`

### Phase 5: Movement & Stickiness System

1. Port `StickyType.java`, `StickinessRules.java`, `IPistonMovable.java`
2. Port `MovingBlockEntityData.java`, `MovementScanHelper.java`
3. Port `BlockMoveHelper.java`
4. **CRITICAL: Port PistonStructureResolverMixin** -- requires vanilla source verification
5. Port `PistonBaseBlockMixin.java`
6. Port `PistonMovingBlockEntityMixin.java`
7. Port `EntityMovementMixin.java`

### Phase 6: Machine System

1. Port `IMachineFamily.java`, `MachineType.java`
2. Port `AbstractMachineBlock.java`, `MachineSwapHelper.java`
3. Port `MachineFrameBlock.java`, `MachineFrameBlockEntity.java`
4. Port `MachineRedstoneHelper.java`
5. Port `MachineFrameMenu.java`, `PositionBoundContainer.java`
6. Port all specializations (Track, Trolley, Conductor, Pipe, FluidPipe, Piston, StickyPiston, Brace, Repeater, Toggle, Transformer)
7. Port `MachinePipeBlockEntity.java`, `MachineFluidPipeBlockEntity.java` -- **Capability migration** (critical)

### Phase 7: Portal System

1. Port `PortalGeometry.java`, `PortalUtils.java`
2. Port `OddstonePortalFrameDetector.java`
3. Port `OddstonePortalSavedData.java` -- SavedData.Factory pattern
4. Port `OddstonePortalBlock.java` -- **Portal/DimensionTransition** (critical)
5. Port `OddstonePortalHandler.java`
6. Port `EnderPearlPortalHandler.java`

### Phase 8: Multiblock System

1. Port `StructureDefinition.java`, `StructureScanner.java`, `ScanStrategy.java`, `PatternScanStrategy.java`
2. Port `StructureResult.java`, `StructureRegistration.java`
3. Port `StructureManager.java` -- SavedData changes
4. Port `StructureEffectHandler.java`
5. Port `FurnaceArrayDefinition.java`, `FurnaceArrayEffect.java`
6. Port `AbstractFurnaceBlockEntityMixin.java`

### Phase 9: Client & Rendering

1. Port `ModColorHandlers.java` -- ARGB integer tint
2. Port `FramedBlockRenderer.java` -- **Vertex system rewrite** (critical)
3. Port `MachinePistonArmRenderer.java` -- Vertex system rewrite
4. Port `FramedBlockItemModel.java` -- BakedModel changes
5. Port `FramedBlockRenderTypes.java` -- Shader name changes
6. Port `MovingFrameData.java`
7. Port `MachineFrameScreen.java` -- Screen rendering changes
8. Port `FramedBlockDebugOverlay.java`
9. Port client mixins (PistonBaseBlockClientMixin, PistonMovingBlockEntityRendererMixin)

### Phase 10: Effects, Commands, Config

1. Port `JudgementEffect.java` -- Event handler changes
2. Port `ModCommands.java` -- Event registration changes

### Phase 11: Resources

1. Move all data files to new folder structure (Section 14)
2. Update any JSON format changes in recipes, loot tables, tags
3. Verify all model/blockstate/texture references still work
4. Update `pack.mcmeta` with new pack format version

### Phase 12: Testing & Verification

1. Compile and resolve all errors
2. Launch game, verify all blocks register
3. Test each system individually
4. Test complex interactions (stickiness, framing, piston movement)
5. Test portal system
6. Test multiblock formation
7. Test all machine specializations

---

## 22. FILES NEEDED FROM USER

### Required: NeoForge MDK Reference

To properly set up the project, I will need:

1. **NeoForge 1.21 MDK** -- Download from https://github.com/NeoForgeMDKs/MDK-1.21-ModDevGradle
   - `build.gradle`
   - `settings.gradle`
   - `gradle.properties`
   - `src/main/resources/META-INF/neoforge.mods.toml` (template)
   - `gradle/` wrapper directory
   - This gives us the correct Gradle plugin versions, NeoForge version, and project structure

2. **Current Ideomod source code** -- The complete `src/` directory synced to the project knowledge. Specifically:
   - ALL `.java` files (I'll need the actual source, not just documentation)
   - ALL resource files (`assets/`, `data/`, `META-INF/`)
   - `build.gradle`, `settings.gradle`, `gradle.properties`
   - `ideomod.mixins.json`

### Highly Recommended: NeoForge 1.21 Vanilla Source Access

For mixin verification, it would be extremely helpful to have:

3. **Decompiled vanilla source for key classes** (from NeoForge 1.21 dev environment):
   - `net.minecraft.world.level.block.piston.PistonStructureResolver` -- CRITICAL for mixin target verification
   - `net.minecraft.world.level.block.piston.PistonBaseBlock` -- for mixin verification
   - `net.minecraft.world.level.block.entity.PistonMovingBlockEntity` -- for mixin verification
   - `net.minecraft.world.level.block.state.BlockState` -- to verify canStickTo/isStickyBlock
   - `net.minecraft.world.entity.Entity` -- for EntityMovementMixin targets
   - `net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity` -- for furnace mixin

   **How to get these:** Set up the NeoForge MDK, run `gradle setup` or equivalent, then the decompiled sources will be in `.gradle/` or the IDE's external libraries. Copy the relevant files.

### Optional but Very Useful: Reference Mod Sources

4. **Open source mod that has been ported from Forge 1.20.1 to NeoForge 1.21** -- for reference patterns. See Section 23 for recommendations.

---

## 23. REFERENCE REPOSITORIES

### Primary References (Ported to NeoForge 1.21)

| Repository | URL | Use For |
|-----------|-----|---------|
| **Kaupenjoe's NeoForge Tutorial 1.21.X** | https://github.com/Tutorials-By-Kaupenjoe/NeoForge-Tutorial-1.21.X | General patterns: blocks, items, BEs, menus, recipes, datagen, networking |
| **NeoForge MDK 1.21 (ModDevGradle)** | https://github.com/NeoForgeMDKs/MDK-1.21-ModDevGradle | Build system template |
| **Farmer's Delight** (vectorwing/FarmersDelight) | https://github.com/vectorwing/FarmersDelight | Custom recipes, block entities, data components, food systems. Has 1.21 NeoForge release. |
| **Create** (Creators-of-Create/Create) | https://github.com/Creators-of-Create/Create | Mechanical blocks, piston-like movement, multiblocks, BESRs. NeoForge port available. **Most relevant** for our movement/stickiness system. |

### Secondary References (Patterns & Learning)

| Repository | URL | Use For |
|-----------|-----|---------|
| **Kaupenjoe NeoForge Course Module 1** | https://github.com/Tutorials-By-Kaupenjoe/NeoForge-Course-121-Module-1 | Fundamentals, registration, blocks, items |
| **Kaupenjoe NeoForge Course Module 2** | https://github.com/Tutorials-By-Kaupenjoe/NeoForge-Course-121-Module-2 | Advanced topics: block entities, menus, custom recipes |
| **NeoForge GitHub** | https://github.com/neoforged/NeoForge (1.21.x branch) | Source of truth for NeoForge APIs, capability hooks, event definitions |

### Migration Documentation

| Resource | URL |
|----------|-----|
| **1.20.4 -> 1.20.5 Primer** | https://docs.neoforged.net/primer/docs/1.20.5/ |
| **1.20.6 -> 1.21 Primer** | https://docs.neoforged.net/primer/docs/1.21/ |
| **NeoForge-specific 1.21 Changes** | https://docs.neoforged.net/primer/docs/1.21/neo/ |
| **Capability Rework** | https://neoforged.net/news/20.3capability-rework/ |
| **Networking Rework** | https://neoforged.net/news/20.4networking-rework/ |
| **Registry Rework** | https://neoforged.net/news/20.2registry-rework/ |
| **Event Bus Changes** | https://neoforged.net/news/20.2eventbus-changes/ |
| **Transfer Rework (21.9+)** | https://neoforged.net/news/21.9-transfer-rework/ |

---

## 24. MIGRATION ORDER & CHECKLIST

### Pre-Port Checklist

- [ ] Download NeoForge 1.21 MDK
- [ ] Set up new project from MDK template
- [ ] Install Java 21 JDK
- [ ] Verify MDK project compiles and runs empty
- [ ] Extract decompiled vanilla sources for mixin target classes
- [ ] Clone/download at least one reference repo (Kaupenjoe tutorial recommended)

### Port Execution Checklist

- [ ] **Phase 1**: Project setup, build system, neoforge.mods.toml
- [ ] **Phase 2**: Registry classes (ModBlocks, ModItems, etc.), ModDataComponents, networking
- [ ] **Phase 3**: Simple blocks (Oddstone, OddSlime, Drill)
- [ ] **Phase 4**: Frame system (FrameBlock, FramedBlock, FramedBlockItem with Data Components)
- [ ] **Phase 5**: Movement & stickiness (StickinessRules, mixins -- HIGHEST RISK)
- [ ] **Phase 6**: Machine system (AbstractMachineBlock, all specializations, capabilities)
- [ ] **Phase 7**: Portal system (DimensionTransition rewrite)
- [ ] **Phase 8**: Multiblock system (StructureManager, SavedData)
- [ ] **Phase 9**: Client/rendering (vertex system rewrite, BESRs, BakedModel)
- [ ] **Phase 10**: Effects, commands, config
- [ ] **Phase 11**: Resource files (folder restructuring, JSON updates)
- [ ] **Phase 12**: Testing & verification

### Post-Port Checklist

- [ ] All blocks register and appear in creative tab
- [ ] Framed blocks work (framing, unframing, NBT -> Data Component)
- [ ] Stickiness system works (all matrix combinations)
- [ ] Machine system works (all specializations, GUI, redstone, item/fluid pipes)
- [ ] Portal system works (activation, teleportation, SavedData)
- [ ] Multiblock system works (furnace array formation, effects)
- [ ] Piston movement works (push, pull, sympathetic, ranged)
- [ ] Rendering works (BESRs, framed block overlays, piston arm animation)
- [ ] Networking works (repeater delay, transformer level sync)
- [ ] All mixin features verified individually

### Risk Summary

| Risk Level | Systems |
|-----------|---------|
| **CRITICAL** | PistonStructureResolver mixin, PistonBaseBlock mixin |
| **HIGH** | Data Components (FramedBlockItem), Capability rework (FluidPipe), Portal/Dimension system, Rendering vertex rewrite |
| **MEDIUM** | Networking rewrite, Recipe system, Event handlers, ResourceLocation API |
| **LOW** | Registry migration (mechanical), Folder restructuring (tedious but simple), Config, Commands |

---

*End of porting guide. This document should be provided to Claude alongside the actual source files and NeoForge MDK when the port is ready to execute.*
