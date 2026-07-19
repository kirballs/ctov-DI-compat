# PR: Port DI petshop compat to CTOV 3.4.14 (multi-loader)

## Summary

Ports the DI petshop compat layer from the v3.3.1 Forge-only branch to the v3.4.14
multi-loader (Architectury) base. CTOV v3.4.14 already ships 21 petshop .nbt files
and 21 Lithostitched modifier JSONs that inject them — but the modifiers use vanilla
`minecraft:single_pool_element`, whose `handleDataMarker` is a no-op, so the cage mobs,
fishtank décor, and water blocks inside the petshop .nbt files never spawn (only the
chest loot resolves, because CTOV's existing .nbt setup handles that when DI is loaded).

This PR fixes that by registering a custom `ctov:petshop_compat`
`StructurePoolElementType` that extends `LegacySinglePoolElement` and overrides
`handleDataMarker` to dispatch on the `petshop_water` / `petshop_chest` /
`petshop_cage_0..3` markers — mirroring DI's own `PetshopStructurePoolElement` but
keyed off a per-biome `biome_profile` field instead of DI's global entity tags.

## What's in the PR

### New Java code (7 files)

- `common/src/main/java/net/ctov/petshop/PetshopSpawnProfile.java` — codec + weighted
  pick helper. Schema: `{ "entries": [ { "entity": RL, "weight": int, "baby": bool,
  "age": int } ] }`.
- `common/src/main/java/net/ctov/petshop/PetshopCompatStructurePoolElement.java` —
  extends `LegacySinglePoolElement`, adds `biome_profile` field to the codec, overrides
  `handleDataMarker` to dispatch on marker strings. Marker dispatch:
  - `petshop_water` → spawn 2 from `fishtank` profile, then place water/seagrass/
    waterlogged-coral (mirrors DI's décor RNG).
  - `petshop_chest` → clear marker, bind chest-below to
    `domesticationinnovation:chests/petshop_chest`. Defensive — chest loot already
    resolves via CTOV's existing .nbt setup when DI is loaded.
  - `petshop_cage_0/1/2/3` → spawn 1-2 / 2-3 / 1-2 / 1 entities from the configured
    `biome_profile`. Cage counts match DI exactly.
  - Age applied AFTER `finalizeSpawn` to avoid being overwritten.
- `common/src/main/java/net/ctov/petshop/PetshopCompatRegistries.java` — common accessor
  that each platform's registry glue publishes to.
- `forge/src/main/java/net/ctov/forge/petshop/PetshopCompatForgeRegistry.java` — Forge
  `DeferredRegister<StructurePoolElementType<?>>` against
  `Registries.STRUCTURE_POOL_ELEMENT` (matches DI's own pattern).
- `neoforge/src/main/java/net/ctov/neoforge/petshop/PetshopCompatNeoRegistry.java` —
  same pattern; NeoForge 20.2.x still uses `net.minecraftforge.registries.*` packages.
- `fabric/src/main/java/net/ctov/fabric/petshop/PetshopCompatFabricRegistry.java` —
  `BuiltInRegistries.register(...)` on `onInitialize`. DI isn't loaded on Fabric, so
  this is defensive only.
- `quilt/src/main/java/net/ctov/quilt/petshop/PetshopCompatQuiltRegistry.java` — same
  as Fabric.

### Updated platform mod entry points (4 files)

- `forge/src/main/java/net/ctov/forge/ctovForge.java` — calls
  `PetshopCompatForgeRegistry.POOL_ELEMENTS.register(modBus)` + `init()`.
- `neoforge/src/main/java/net/ctov/neoforge/ctovNeo.java` — calls
  `PetshopCompatNeoRegistry.POOL_ELEMENTS.register(modBus)` + `init()`.
- `fabric/src/main/java/net/ctov/fabric/ctovFabric.java` — calls
  `PetshopCompatFabricRegistry.init()`.
- `quilt/src/main/java/net/ctov/quilt/ctovQuilt.java` — calls
  `PetshopCompatQuiltRegistry.init()`.

### Patched modifier JSONs (21 files)

`common/src/main/resources/data/ctov/lithostitched/worldgen_modifier/domesticationinnovation/*.json`:
- Flipped `element_type` from `minecraft:single_pool_element` → `ctov:petshop_compat`
- Added `biome_profile` field per the maintainer's biome-merging rules

25 elements across 21 files patched.

### New isolated test template pools (21 files)

`common/src/main/resources/data/ctov/worldgen/template_pool/village/test/petshop/*.json`:
21 single-element template pools (one per biome modifier) for use with the
[CommandStructures](https://www.curseforge.com/minecraft/mc-mods/commandstructures)
mod. Each pool contains exactly ONE petshop element with
`element_type: ctov:petshop_compat`, so spawning it at jigsaw depth 0:

```
/spawnstructure ~ ~ ~ ctov:village/test/petshop/<biome> 0 false false false false
```

places just the petshop .nbt (no village, no jigsaw chain) and fires the full
DI compat `handleDataMarker` logic for every marker inside the .nbt — making
it easy to verify each biome's petshop individually without /locating a
village and waiting on RNG.

### New spawn profile JSONs (14 files)

`common/src/main/resources/data/ctov/petshop_spawns/*.json`:
- plains, desert, snowy, savanna, badlands, beach, jungle, mountain, mushroom, swamp,
  forest, fishtank (shared), underground (reserved), cherry (reserved)

### Updated platform metadata (4 files)

- `forge/src/main/resources/META-INF/mods.toml` — added `domesticationinnovation` as
  soft dep (`mandatory=false`, `versionRange="[1.7.0,)"`, `ordering="AFTER"`).
- `neoforge/src/main/resources/META-INF/mods.toml` — same.
- `fabric/src/main/resources/fabric.mod.json` — added `recommends: { domesticationinnovation: ">=1.7.0" }`.
- `quilt/src/main/resources/quilt.mod.json` — added `recommends: [{ id: domesticationinnovation, version: ">=1.7.0" }]`.

### Docs (2 files)

- `docs/DI_COMPAT_PETSHOP.md` — user-facing: install, schema, biome→profile mapping,
  marker dispatch explanation.
- `docs/internal_mapping.md` — maintainer-facing: mapping table, code flow, age
  semantics deep-dive, per-platform registry details.

## Biome → profile mapping

Per maintainer instruction:

| CTOV village variant(s) | Profile |
|---|---|
| plains, plains_fortified | plains |
| desert, desert_oasis | desert |
| snowy_igloo, christmas | snowy |
| savanna, savanna_na | savanna |
| mesa, mesa_fortified | badlands |
| beach | beach |
| jungle, jungle_tree | jungle (kept separate) |
| mountain, mountain_alpine | mountain |
| mushroom | mushroom |
| swamp, swamp_fortified | swamp (kept separate) |
| taiga, taiga_fortified, halloween, dark_forest | forest |
| (reserved, no village yet) | underground |
| (reserved, no village yet) | cherry |
| All biomes — `petshop_water` marker | fishtank (shared) |

## What this PR does NOT do

- Does not touch the existing chest loot resolution. Per maintainer note, the petshop
  chest already works in stock CTOV+DI because CTOV's .nbt setup references DI's loot
  table id and DI resolves it when loaded. The `petshop_chest` handler is defensive
  only — if for any reason the chest below the marker doesn't already have the loot
  table bound, it binds it. If DI's loot table is unresolved (DI not loaded), the call
  is a no-op.
- Does not move any .nbt files. CTOV 3.4.14 already ships them at the correct location
  in the jar — no movement needed (unlike the v3.3.1 port, which moved them out of a
  separate datapack).
- Does not change any other compat mod's behaviour. Only the 21 DI modifier JSONs are
  patched.
- Does not register a custom `StructureProcessor` — the implementation hooks the
  `handleDataMarker` callback on `LegacySinglePoolElement` instead, which is the
  vanilla-blessed extension point.

## Build & test

```bash
./gradlew :forge:build
./gradlew :neoforge:build
./gradlew :fabric:build
# quilt is commented out of settings.gradle in upstream 3.4.14, skip
```

Each platform jar should land in `<platform>/build/libs/`. Drop the jar into a
Minecraft 1.20.1 instance with Lithostitched and DI installed, /locate a village, and
verify that:
- The petshop building generates (sometimes — it's a weighted roll).
- Cage markers spawn biome-appropriate mobs.
- The fishtank marker spawns 2 aquatic mobs + places water/seagrass/coral.
- The chest marker doesn't crash (chest loot resolves via CTOV's existing setup when
  DI is loaded; our defensive `setLootTable` call is a no-op in that case).

### Fast iteration test with CommandStructures

Install [CommandStructures](https://www.curseforge.com/minecraft/mc-mods/commandstructures)
alongside CTOV + DI. Then, in-game:

```
/spawnstructure ~ ~ ~ ctov:village/test/petshop/plains 0 false false false false
```

This places just the plains petshop .nbt (no village, no jigsaw chain) and fires
the full DI compat `handleDataMarker` logic. Repeat with each of the 21 biome
test pool ids listed in `docs/DI_COMPAT_PETSHOP.md` to verify each biome's
profile individually without waiting on village RNG.

## Comparison to v3.3.1 port

The v3.3.1 Forge-only port is preserved on the archive tag
`archive/di-petshop-compat-1.20.1-20250719`. See `docs/internal_mapping.md` for a full
diff between the two implementations.

## Why base against `di-petshop-compat-1.20.1` and not `master`?

Per maintainer instruction, `master` is being retired (it diverged from upstream years
ago). The fork's active default branch is `di-petshop-compat-1.20.1` (the v3.3.1
Forge-only port). This PR is the multi-loader 3.4.14 port layered on top of upstream's
3.4.14 release (`upstream/1.20` @ `0a72957d`), targeting `di-petshop-compat-1.20.1`
as the merge base.

The diff is large because it includes all 131 upstream commits between v3.3.1 and
v3.4.14 plus this port. Once merged, `di-petshop-compat-1.20.1` becomes the new
canonical head — superseding the v3.3.1 Forge-only port for any future MC 1.20.x
work.
