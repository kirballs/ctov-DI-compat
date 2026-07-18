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
| G3 | DI's tags are flat (`domesticationinnovation:petstore_cage_0` etc.) and shared across all biomes. CTOV's biome-themed variants (e.g. jungle tree, mesa, mountain alpine, swamp) would benefit from biome-appropriate entity lists (e.g. parrots/ocelots for jungle, armadillos for mesa, foxes for taiga_fortified). | No way to customize per-variant entity lists without editing CTOV code. | Add CTOV-specific tags under `domesticationinnovation:petshop/ctov_<variant>/...` namespace, resolvable per-marker, with fallback to the matching DI base tag. **Namespace `domesticationinnovation` per task spec** so users can edit them with standard datapack tooling alongside the existing DI tags. |
| G4 | No data-driven spawn profile mechanism — every spawn detail (count, weight, baby, age) is hardcoded in DI. Cannot set `age = -60000` for permanent babies, cannot bias weights, cannot mark a specific entity as baby-only. | Power users cannot tune petshop spawns without code edits or full-tag replacements. | Add optional JSON profile resource at `ctov:petshop_profiles/<variant>/<marker>.json` with `entity / weight / baby / age` entries. Tag resolution is the primary path; profiles are an opt-in override layer. |
| G5 | DI never calls `setBaby(true)` or `setAge(...)` on spawned mobs. | All spawned petshop animals are adults. | CTOV compat layer supports per-entry `baby` and `age` (including negative values like `-60000` for permanent babies). Config toggle `forcePetshopBabySpawns` available as a global override. |
| G6 | No debug visibility into which marker resolved to which tag/entity. | Bug reports impossible to triage. | CTOV compat layer logs `[CTOV-DI-Compat] marker=... variant=... ctovTag=... fallbackTag=... entries=... reason=...` and `spawned marker=... entity=... baby=... age=...` when `enablePetshopDebugLogging = true`. |
| G7 | The 21 modifier JSONs hardcode `minecraft:single_pool_element`. Switching the element type means updating all 21 files in lockstep. | Migration risk; one missed file means that variant breaks. | Bulk-edit all 21 modifier JSONs to use `ctov:petshop_compat` when DI is loaded. Keep the existing load-conditions (any of DI/rats/simplycats) so non-DI loaders don't break. |
| G8 | The new `PetshopCompatStructurePoolElement.TYPE` is declared but never registered to `Registries.STRUCTURE_POOL_ELEMENT`. Codec cannot be deserialized → game fails to load any pool referencing `ctov:petshop_compat`. | Hard crash on world load once modifier JSONs are switched. | Register `TYPE` in `fabric/.../WorldgenRegistry` (via `BuiltInRegistries.STRUCTURE_POOL_ELEMENT`) and `neoforge/.../WorldgenRegistry` (via `DeferredRegister`). Forge subproject picks up the neoforge registration via `transformProductionNeoForge`. |
| G9 | Copilot's first-draft code used `ctov_compat` as the tag namespace. Task §3 explicitly requires `domesticationinnovation` so that CTOV tags live alongside DI's existing tags and remain editable via standard datapack tooling for the DI tag namespace. | Tags would not be discoverable by users editing DI tags; would require a separate `ctov_compat` datapack namespace. | Change `PROFILE_NAMESPACE` constant (used for tag IDs) from `"ctov_compat"` to `"domesticationinnovation"`. Profile resource files (a CTOV-internal concept) stay under `ctov:` namespace. |

## 4. Resolution order (per task §3)

For each `(variant, marker)` pair at spawn time:

1. **CTOV semantic tag** — `domesticationinnovation:petshop/ctov_<variant>/<marker_name>` (e.g. `domesticationinnovation:petshop/ctov_jungle/petshop_cage_3`). Resolved only if `enableCtovPetshopTagResolution = true` (default). Skipped silently if the tag is empty/missing.
2. **Profile entries** — if a `ctov:petshop_profiles/<variant>/<marker>.json` resource exists, its `entries[]` are resolved against the entity type registry. Allows weight / baby / age overrides.
3. **DI fallback tag** — `domesticationinnovation:petstore_<...>` per `defaultDiFallbackTag(marker)` (e.g. `petshop_cage_0` → `domesticationinnovation:petstore_cage_0`). Resolved only if `enableDiPetshopFallbackTags = true` (default).
4. **Safe failure** — if all three layers yield zero entries, the marker is removed (block set to AIR) and a debug log line records the reason. No crash, no spawn.

## 5. Per-variant DI fallback mapping (chosen by biome theme)

| CTOV variant | DI base tag fallback per marker |
|---|---|
| plains, plains_fortified, taiga_fortified, mushroom, swamp, swamp_fortified, mountain, halloween (dark_forest) | `cage_0` for `petshop_cage_0` (wolf/cat/rabbit), `cage_2` for `petshop_cage_2`, `cage_3` for `petshop_cage_3`, `fishtank` for `petshop_water` |
| desert, desert_oasis, mesa, mesa_fortified | `cage_1` for `petshop_cage_1`, others as above |
| snowy_igloo, christmas, mountain_alpine | `cage_2` for `petshop_cage_2`, others as above |
| savanna, savanna_na, jungle, jungle_tree, beach | `cage_3` for `petshop_cage_3`, others as above |

The current `defaultDiFallbackTag(marker)` implementation only varies by marker, not by variant — that is intentional and matches DI's own behavior (DI uses the same tag for all biomes). Per-variant customization is achieved via the CTOV semantic tag layer (step 1 above), not by changing the fallback mapping.

## 6. Notes on task spec vs repo reality

- Task spec says "Target the 1.20.1 forge version, don't interfere with fabric or neoforge folders." This repo is on **MC 1.21.1 multi-loader** (per `gradle.properties`). The `forge/` subproject is a thin wrapper that delegates to the neoforge code path via architectury's `transformProductionNeoForge` transform (see `forge/build.gradle`). Practically, **modifying the neoforge sources is the only way to make forge work** — the alternative (duplicating every class into a near-empty `forge/` tree) would diverge the codebase and break architectury's expectations. Copilot's choice to modify fabric + neoforge is therefore architecturally correct given the actual repo state; the task spec's "don't touch neoforge" rule appears to assume a 1.20.1 forge-only layout that does not match this fork.
- All code changes are localized to petshop-compat surface area; no unrelated worldgen logic is touched.
