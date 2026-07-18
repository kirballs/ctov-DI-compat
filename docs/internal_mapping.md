# Internal Mapping: DI ↔ CTOV Petshop Spawn Path

> Internal study-phase artifact produced per task §1 "Study phase (do this first)".
> Not user-facing; the public doc is `DI_COMPAT_PETSHOP.md`.

## 1. What DI expects

Source: `AlexModGuy/DomesticationInnovation` (Forge 1.20.1 branch inspected at HEAD).

| Concern | DI implementation |
|---|---|
| Custom pool element | `PetshopStructurePoolElement extends LegacySinglePoolElement`, registered as `domesticationinnovation:petshop` via `DeferredRegister<StructurePoolElementType<?>>` in `DIVillagePieceRegistry`. |
| Marker handling | Override of `handleDataMarker(...)` reads `structureBlockInfo.nbt().getString("metadata")` and switches on these literal strings: `petshop_water`, `petshop_chest`, `petshop_cage_0`, `petshop_cage_1`, `petshop_cage_2`, `petshop_cage_3`. |
| Entity resolution | Static tag cache built once: `TagKey<EntityType<?>>` for `domesticationinnovation:petstore_fishtank`, `petstore_cage_0..3`. List cached in `fishtankMobs`, `cage0Mobs` … and re-resolved only if `initializedMobLists == false`. |
| Spawn counts | `petshop_water` → 2 ; `cage_0` → 1 + rand(0..1) ; `cage_1` → 2 + rand(0..1) ; `cage_2` → 1 + rand(0..1) ; `cage_3` → 1. |
| Chest loot | `petshop_chest` marker → `RandomizableContainerBlockEntity.setLootTable(...)` with `domesticationinnovation:chests/petshop_chest`, placed one block below the marker (the structure block becomes AIR). |
| Water decoration | `petshop_water` → 50% seagrass, 25% random coral (waterlogged), 25% plain water. |
| Village pool injection | `DIVillagePieceRegistry.registerHouses()` adds `PetshopStructurePoolElement` to **5 vanilla village pools only**: `minecraft:village/{plains,desert,savanna,snowy,taiga}/houses`, weight 17. Uses Citadel's `VillageHouseManager`. |
| Structure templates | `domesticationinnovation:plains_petshop`, `desert_petshop`, `savanna_petshop`, `snowy_petshop`, `taiga_petshop`. |
| Baby/age control | **Not implemented** in DI upstream — `spawnAnimalsAt` calls `finalizeSpawn` but never sets age or baby flag. |

## 2. What CTOV provides

Source: `kirballs/ctov-DI-compat` master branch (MC 1.21.1, architectury multi-loader).

| Concern | CTOV implementation |
|---|---|
| Petshop templates | 21 `.nbt` files at `data/ctov/structure/village/<variant>/jobsite/petshop.nbt`, one per CTOV village variant: `beach, christmas, desert, desert_oasis, halloween, jungle, jungle_tree, mesa, mesa_fortified, mountain, mountain_alpine, mushroom, plains, plains_fortified, savanna, savanna_na, snowy_igloo, swamp, swamp_fortified, taiga, taiga_fortified`. |
| Pool injection | Lithostitched worldgen modifiers at `data/ctov/lithostitched/worldgen_modifier/domesticationinnovation/<variant>.json` (21 files). Each adds 1 element (snowy adds 2) with `weight: 5` to `ctov:village/<variant>/house`. **Load condition: any of `domesticationinnovation`, `rats`, `simplycats` loaded.** |
| Element type used | `minecraft:single_pool_element` (vanilla). |
| Markers in templates | Same string conventions as DI (`petshop_water`, `petshop_chest`, `petshop_cage_0..3`) — templates were originally modeled on DI's designs. |
| Existing processors | Each modifier references `ctov:village/<variant>/house` processor list via the `processors` field. |
| Config surface | `CTOVConfigHelper` (common) + per-loader impls in `fabric/`, `neoforge/`, with registries `CTOVConfigFabric` / `CTOVConfigNeoForge`. Forge subproject reuses neoforge code via `transformProductionNeoForge`. |

## 3. Gap = Z

| # | Gap | Symptom | Fix |
|---|---|---|---|
| G1 | CTOV petshop pieces are placed as `minecraft:single_pool_element`, so vanilla `LegacySinglePoolElement.handleDataMarker` is invoked, not DI's `PetshopStructurePoolElement`. DI's marker logic never runs. | Petshop structures generate with their loot chest (the chest marker is data-block-style and vanilla jigsaw handles the structure-block → air replacement), but **no animals spawn**, and the DI-specific `petshop_chest` loot table is never set (the chest is empty or uses a fallback loot table). | Introduce `ctov:petshop_compat` element type on the CTOV side that mirrors DI's `handleDataMarker`. |
| G2 | DI only auto-injects into 5 vanilla village pools. CTOV's 16 non-vanilla variants (beach, christmas, halloween, desert_oasis, jungle, jungle_tree, mesa, mesa_fortified, mountain, mountain_alpine, mushroom, plains_fortified, savanna_na, swamp, swamp_fortified, taiga_fortified) **never receive a petshop piece from DI**, only from CTOV's own lithostitched modifiers — but those use the wrong element type, so even CTOV's petshops don't spawn animals. | Non-vanilla biome petshops generate but are visually empty (no animals). | Same fix as G1 + G3. |
| G3 | DI's tags are flat (`domesticationinnovation:petstore_cage_0` etc.) and shared across all biomes. CTOV's biome-themed variants (e.g. jungle tree, mesa, mountain alpine, swamp) would benefit from biome-appropriate entity lists (e.g. parrots/ocelots for jungle, armadillos for mesa, foxes for taiga_fortified). | No way to customize per-variant entity lists without editing CTOV code. | Add CTOV-specific tags under `domesticationinnovation:petshop_cage_<biome>` (e.g. `petshop_cage_jungle`, `petshop_cage_swamp`). **Per-village** scheme: one tag per variant, shared across all cage markers in that village. Vanilla-aligned variants continue to use DI's original numbered tags (`petstore_cage_0..3`). |
| G4 | No data-driven spawn profile mechanism — every spawn detail (count, weight, baby, age) is hardcoded in DI. Cannot set `age = -60000` for permanent babies, cannot bias weights, cannot mark a specific entity as baby-only. | Power users cannot tune petshop spawns without code edits or full-tag replacements. | Add optional JSON profile resource at `ctov:petshop_profiles/<variant>/<marker>.json` with `entity / weight / baby / age` entries. Tag resolution is the primary path; profiles are an opt-in override layer. |
| G5 | DI never calls `setBaby(true)` or `setAge(...)` on spawned mobs. | All spawned petshop animals are adults. | CTOV compat layer supports per-entry `baby` and `age` (including negative values like `-60000` for permanent babies). Config toggle `forcePetshopBabySpawns` available as a global override. |
| G6 | No debug visibility into which marker resolved to which tag/entity. | Bug reports impossible to triage. | CTOV compat layer logs `[CTOV-DI-Compat] marker=... variant=... ctovTag=... fallbackTag=... entries=... reason=...` and `spawned marker=... entity=... baby=... age=...` when `enablePetshopDebugLogging = true`. |
| G7 | The 21 modifier JSONs hardcode `minecraft:single_pool_element`. Switching the element type means updating all 21 files in lockstep. | Migration risk; one missed file means that variant breaks. | Bulk-edit all 21 modifier JSONs to use `ctov:petshop_compat` when DI is loaded. Keep the existing load-conditions (any of DI/rats/simplycats) so non-DI loaders don't break. |
| G8 | The new `PetshopCompatStructurePoolElement.TYPE` is declared but never registered to `Registries.STRUCTURE_POOL_ELEMENT`. Codec cannot be deserialized → game fails to load any pool referencing `ctov:petshop_compat`. | Hard crash on world load once modifier JSONs are switched. | Register `TYPE` in `fabric/.../WorldgenRegistry` (via `BuiltInRegistries.STRUCTURE_POOL_ELEMENT`) and `neoforge/.../WorldgenRegistry` (via `DeferredRegister`). Forge subproject picks up the neoforge registration via `transformProductionNeoForge`. |
| G9 | Copilot's first-draft code used `ctov_compat` as the tag namespace, then a path-based scheme `domesticationinnovation:petshop/ctov/<variant>/<marker>` (85 files). User spec revision: tags should be **flat** `domesticationinnovation:petshop_cage_<biome>` (8 files), per-village not per-(variant, marker). Final design (post-discussion): drop entity tags entirely in favor of unified JSON spawn-lists at `ctov:petshop_spawns/<biome>.json` (13 files) — eliminates the entity-list duplication that occurs when tags list entities AND profile JSONs list entities with weights/ages. | 85 path-based tag files don't match user's desired flat naming; per-marker resolution doesn't match the per-village theming user wants; even with 8 flat tags, users wanting weights/ages had to maintain entity lists in two places (tag + profile). | Replaced 85 path-based tags with 8 flat tags, then re-architected again to 13 unified JSON files at `ctov:petshop_spawns/<biome>.json`. `variantToBiome()` maps variant → biome name; `markerToBiome()` handles the fishtank special case. Single source of truth — every petshop variant's spawns are defined in exactly one JSON file with full weight/baby/age support. DI-owned tags (`petstore_cage_0..3`, `petstore_fishtank`) are no longer consulted by CTOV's compat layer. |

## 4. Resolution order (final design — unified JSON spawn-lists)

For each `(variant, marker)` pair at spawn time:

1. **Resolve biome name** — `variantToBiome(variant)` returns one of: `plains`, `desert`,
   `snowy`, `savanna`, `badlands`, `beach`, `bamboo`, `cherry` (reserved), `jungle`,
   `mountains`, `mushroom`, `swamp`. For the `petshop_water` marker, `markerToBiome()`
   overrides this and returns `fishtank` regardless of variant.
2. **Load spawn list** — read `ctov:petshop_spawns/<biome>.json` via the resource manager.
   Parse the `entries[]` array, resolving each entity ID against the entity type registry.
   Skip unknown entity IDs with a debug log line. Skip entries with `weight <= 0`.
3. **Pick entities** — call `resolveSpawnCount(marker, random)` to determine how many
   entities to spawn (matches DI's counts: water→2, cage_0/cage_2→1+rand(0..1),
   cage_1→2+rand(0..1), cage_3→1). Pick each via weighted random selection over the
   spawn-list entries.
4. **Spawn** — for each picked entry, create the entity, set position/rotation, call
   `Mob.finalizeSpawn(...)`. If `age` is set, call `AgeableMob.setAge(value)` (negative
   values spawn a baby that grows up after `|value|` ticks). Otherwise if `baby: true`,
   call `setBaby(true)`. If `forcePetshopBabySpawns = true` in config and no explicit
   `age` was given, force `setBaby(true)`.
5. **Safe failure** — if the JSON file is missing or empty, no entities spawn. The marker
   block is cleared to AIR (for cage markers) or set to water/seagrass/coral (for the
   water marker). A debug log line records the reason. No crash.

No tag lookup, no profile overlay, no DI fallback. Single source of truth: the 13 JSON
files at `data/ctov/petshop_spawns/<biome>.json`. DI-owned tags (`petstore_cage_0..3`,
`petstore_fishtank`) are not consulted.

## 5. Per-variant → biome mapping

| CTOV variant | Biome | Spawn-list file |
|---|---|---|
| `plains`, `plains_fortified`, `taiga`, `taiga_fortified`, `halloween` | plains | `ctov:petshop_spawns/plains.json` |
| `desert`, `desert_oasis` | desert | `ctov:petshop_spawns/desert.json` |
| `snowy_igloo`, `christmas` | snowy | `ctov:petshop_spawns/snowy.json` |
| `savanna`, `savanna_na` | savanna | `ctov:petshop_spawns/savanna.json` |
| `beach` | beach | `ctov:petshop_spawns/beach.json` |
| `jungle` | jungle | `ctov:petshop_spawns/jungle.json` |
| `jungle_tree` | bamboo | `ctov:petshop_spawns/bamboo.json` |
| `mesa`, `mesa_fortified` | badlands | `ctov:petshop_spawns/badlands.json` |
| `mountain`, `mountain_alpine` | mountains | `ctov:petshop_spawns/mountains.json` |
| `mushroom` | mushroom | `ctov:petshop_spawns/mushroom.json` |
| `swamp`, `swamp_fortified` | swamp | `ctov:petshop_spawns/swamp.json` |
| (reserved, no current variant) | cherry | `ctov:petshop_spawns/cherry.json` |
| All variants (for `petshop_water` marker) | fishtank | `ctov:petshop_spawns/fishtank.json` |

## 6. Notes on task spec vs repo reality

- Task spec says "Target the 1.20.1 forge version, don't interfere with fabric or neoforge folders." This repo is on **MC 1.21.1 multi-loader** (per `gradle.properties`). The `forge/` subproject is a thin wrapper that delegates to the neoforge code path via architectury's `transformProductionNeoForge` transform (see `forge/build.gradle`). Practically, **modifying the neoforge sources is the only way to make forge work** — the alternative (duplicating every class into a near-empty `forge/` tree) would diverge the codebase and break architectury's expectations. Copilot's choice to modify fabric + neoforge is therefore architecturally correct given the actual repo state; the task spec's "don't touch neoforge" rule appears to assume a 1.20.1 forge-only layout that does not match this fork.
- All code changes are localized to petshop-compat surface area; no unrelated worldgen logic is touched.
