# DI Petshop Compatibility — CTOV Fork

> ChoiceTheorem's Overhauled Village (CTOV) fork — Domestication Innovation (DI) petshop
> compatibility layer. Target: MC 1.21.1 multi-loader (fabric / forge / neoforge / quilt).
>
> Status: implemented. See "Known limitations" at the bottom for caveats.

## 1. Root-cause summary

DI registers its own `domesticationinnovation:petshop` structure pool element type that handles
the `petshop_water`, `petshop_chest`, `petshop_cage_0..3` data markers placed inside petshop
.nbt templates. When a petshop piece is placed via that element type, DI's
`PetshopStructurePoolElement.handleDataMarker(...)` runs and spawns animals / sets chest loot.

The catch: DI only auto-injects its petshop pieces into the **5 vanilla village pools**
(`minecraft:village/{plains,desert,savanna,snowy,taiga}/houses`) via Citadel's
`VillageHouseManager`. CTOV ships **21 village variants** (5 vanilla-aligned + 16 biome-themed
non-vanilla), and CTOV injects its own petshop pieces into all 21 via lithostitched worldgen
modifiers. Those modifiers used `minecraft:single_pool_element` (vanilla), which means:

- DI's marker handler never runs on CTOV petshop pieces.
- The petshop structure generates (you see the building), the chest exists, but **animals never
  spawn** and the chest doesn't bind DI's `domesticationinnovation:chests/petshop_chest` loot
  table.
- For the 16 non-vanilla CTOV variants, DI never even adds a petshop piece — only CTOV does.

This compat layer fixes that by introducing a CTOV-side `ctov:petshop_compat` element type that
mirrors DI's marker handling, and by switching all 21 lithostitched modifier JSONs to use it.

## 2. Architecture

### Before

```
CTOV lithostitched modifier JSON
        │
        ▼
element_type: minecraft:single_pool_element   ◀── vanilla
        │
        ▼
LegacySinglePoolElement.handleDataMarker()     ◀── vanilla, ignores petshop_* markers
        │
        ▼
petshop structure spawns, but markers are NOT processed
(no animals, chest loot table not bound)
```

### After

```
CTOV lithostitched modifier JSON  (load condition: DI / rats / simplycats loaded)
        │
        ▼
element_type: ctov:petshop_compat              ◀── new, registered by CTOV
        │
        ▼
PetshopCompatStructurePoolElement.handleDataMarker()
        │
        ├─ petshop_chest   → bind domesticationinnovation:chests/petshop_chest (mirror DI)
        │
        ├─ petshop_water   → spawn 2 aquatic mobs + water/seagrass/coral decoration
        ├─ petshop_cage_0  → spawn 1–2 small-pet mobs
        ├─ petshop_cage_1  → spawn 2–3 desert-terrarium mobs
        ├─ petshop_cage_2  → spawn 1–2 ice-terrarium mobs
        └─ petshop_cage_3  → spawn 1 tropical-bird mob
        │
        ▼
Resolution order (per variant + marker):
  1. CTOV semantic tag (domesticationinnovation:petshop/ctov/<variant>/<marker>)
  2. Optional profile JSON (ctov:petshop_profiles/<variant>/<marker>.json)
  3. DI fallback tag (domesticationinnovation:petstore_<cage_N | fishtank>)
  4. Safe failure — log + clear marker, no spawn, no crash
```

## 3. Tag policy (corrected)

### DI-owned tags — UNCHANGED

The following tags are owned by DI and **must not be renamed, moved, or modified** by CTOV:

- `domesticationinnovation:petstore_fishtank`
- `domesticationinnovation:petstore_cage_0`
- `domesticationinnovation:petstore_cage_1`
- `domesticationinnovation:petstore_cage_2`
- `domesticationinnovation:petstore_cage_3`

CTOV references these tags by ID at runtime; users can extend them via their own datapacks as
they always could.

### CTOV-specific tags — NEW, under `domesticationinnovation` namespace

Per task spec §3, CTOV-specific tags live under the **`domesticationinnovation`** namespace
(not `ctov_compat`) so users can edit them with the same datapack tooling they already use for
DI's tags. The path namespace `petshop/ctov/<variant>/<marker>` keeps them clearly separated
from DI's flat `petstore_*` names.

Tag ID pattern:

```
domesticationinnovation:petshop/ctov/<variant>/<marker>
```

Examples:

```
domesticationinnovation:petshop/ctov/jungle/petshop_cage_3
domesticationinnovation:petshop/ctov/swamp/petshop_water
domesticationinnovation:petshop/ctov/christmas/petshop_cage_2
domesticationinnovation:petshop/ctov/mesa/petshop_cage_1
```

### Complete list of new CTOV tag IDs

CTOV ships 85 tag files covering 17 variants × 5 markers. Vanilla-aligned variants (plains,
desert, savanna, snowy, taiga) intentionally get NO CTOV tag — they fall through to the DI
base tags, which are already biome-correct.

| Variant | Markers covered |
|---|---|
| `beach` | `petshop_water`, `petshop_cage_0..3` |
| `christmas` | `petshop_water`, `petshop_cage_0..3` |
| `desert_oasis` | `petshop_water`, `petshop_cage_0..3` |
| `halloween` (= dark_forest) | `petshop_water`, `petshop_cage_0..3` |
| `jungle` | `petshop_water`, `petshop_cage_0..3` |
| `jungle_tree` | `petshop_water`, `petshop_cage_0..3` |
| `mesa` | `petshop_water`, `petshop_cage_0..3` |
| `mesa_fortified` | `petshop_water`, `petshop_cage_0..3` |
| `mountain` | `petshop_water`, `petshop_cage_0..3` |
| `mountain_alpine` | `petshop_water`, `petshop_cage_0..3` |
| `mushroom` | `petshop_water`, `petshop_cage_0..3` |
| `plains_fortified` | `petshop_water`, `petshop_cage_0..3` |
| `savanna_na` | `petshop_water`, `petshop_cage_0..3` |
| `snowy_igloo` | `petshop_water`, `petshop_cage_0..3` |
| `swamp` | `petshop_water`, `petshop_cage_0..3` |
| `swamp_fortified` | `petshop_water`, `petshop_cage_0..3` |
| `taiga_fortified` | `petshop_water`, `petshop_cage_0..3` |

File location: `common/src/main/resources/data/domesticationinnovation/tags/entity_types/petshop/ctov/<variant>/<marker>.json`

## 4. Customizing via datapack / KubeJS

### Add an entity to a CTOV variant tag

Drop a datapack at `<world>/datapacks/<your_pack>/data/domesticationinnovation/tags/entity_types/petshop/ctov/jungle/petshop_cage_3.json`:

```json
{
  "replace": false,
  "values": [
    "minecraft:parrot",
    "minecraft:ocelot",
    "minecraft:allay"
  ]
}
```

- `"replace": false` (default in our shipped files) — appends to existing entries.
- `"replace": true` — completely overrides the shipped list.

KubeJS equivalent (in `server_scripts`):

```js
ServerEvents.tags('entity_type', event => {
  event.add('domesticationinnovation:petshop/ctov/jungle/petshop_cage_3', 'minecraft:allay')
  event.add('domesticationinnovation:petshop/ctov/swamp/petshop_water', 'minecraft:pufferfish')
})
```

### Add a brand-new variant tag (no shipped file)

Just create the file — the CTOV compat layer queries the tag at runtime and picks up any
entries the registry returns. No code change required.

### Profile-based spawn overrides (weight, baby, age)

For finer control (weighted picks, baby/age flags), drop a profile JSON at
`<world>/datapacks/<your_pack>/data/ctov/petshop_profiles/<variant>/<marker>.json`:

```json
{
  "ctov_tag": "domesticationinnovation:petshop/ctov/jungle/petshop_cage_3",
  "di_fallback_tag": "domesticationinnovation:petstore_cage_3",
  "entries": [
    { "entity": "minecraft:parrot", "weight": 4 },
    { "entity": "minecraft:ocelot", "weight": 2, "baby": true },
    { "entity": "minecraft:cat", "weight": 1, "age": -60000 }
  ]
}
```

When a profile is present, its `entries` are used as the second resolution layer (between the
CTOV semantic tag and the DI fallback tag). Profile fields:

| Field | Type | Required | Meaning |
|---|---|---|---|
| `ctov_tag` | resource location | no | Override the CTOV semantic tag ID (defaults to `domesticationinnovation:petshop/ctov/<variant>/<marker>`) |
| `di_fallback_tag` | resource location | no | Override the DI fallback tag ID (defaults to `petstore_<marker>` mapping) |
| `entries[].entity` | resource location | yes | Entity type to spawn |
| `entries[].weight` | int | no (default 1) | Weighted pick weight |
| `entries[].baby` | bool | no | If true and entity is AgeableMob, call `setBaby(true)` |
| `entries[].age` | int | no | If set and entity is AgeableMob, call `setAge(value)`. **May be negative** — `age = -60000` produces a permanent baby that will never age up. |

If `age` is specified, `baby` is ignored (age takes precedence). Non-AgeableMob entities
(parrot, tropical_fish, etc.) safely ignore both fields.

## 5. Fallback resolution order

At spawn time, for each `(variant, marker)` pair, the CTOV compat layer tries in order:

1. **CTOV semantic tag** — `domesticationinnovation:petshop/ctov/<variant>/<marker>`
   - Skipped if `enableCtovPetshopTagResolution = false` in config.
   - Skipped silently if the tag exists but is empty.
2. **Profile entries** — `ctov:petshop_profiles/<variant>/<marker>.json` `entries[]`
   - Only consulted if step 1 yielded no entries.
3. **DI fallback tag** — `domesticationinnovation:petstore_<cage_N | fishtank>`
   - Skipped if `enableDiPetshopFallbackTags = false` in config.
   - Skipped silently if the tag is missing or empty (e.g. DI not loaded).
4. **Safe failure** — marker is cleared (block → AIR), `[CTOV-DI-Compat]` debug log records
   the reason. No crash, no spawn.

Marker → DI fallback tag mapping (matches DI's own behavior):

| Marker | DI fallback tag |
|---|---|
| `petshop_water` | `domesticationinnovation:petstore_fishtank` |
| `petshop_cage_0` | `domesticationinnovation:petstore_cage_0` |
| `petshop_cage_1` | `domesticationinnovation:petstore_cage_1` |
| `petshop_cage_2` | `domesticationinnovation:petstore_cage_2` |
| `petshop_cage_3` | `domesticationinnovation:petstore_cage_3` |
| `petshop_chest` | (no entity tag — chest loot table binding only) |

Spawn counts per marker (matches DI's `PetshopStructurePoolElement.handleDataMarker`):

| Marker | Count |
|---|---|
| `petshop_water` | 2 |
| `petshop_cage_0` | 1 + rand(0..1) |
| `petshop_cage_1` | 2 + rand(0..1) |
| `petshop_cage_2` | 1 + rand(0..1) |
| `petshop_cage_3` | 1 |

## 6. Baby / age field behavior

| Entity type | `baby: true` | `age: N` (positive) | `age: -60000` |
|---|---|---|---|
| `AgeableMob` (cat, wolf, rabbit, fox, goat, …) | `setBaby(true)` — grows up over time | `setAge(N)` — grows up after N ticks | `setAge(-60000)` — **permanent baby** (age timer stays far below 0, never reaches the grow-up threshold) |
| Non-`AgeableMob` (parrot, tropical_fish, pufferfish, …) | Ignored safely | Ignored safely | Ignored safely |

`forcePetshopBabySpawns = true` in config overrides every spawn to baby-state (unless an
explicit `age` was given in the profile entry).

## 7. Config reference

All options live under the `petshop_compat` category (Fabric: in `ctov.toml`; NeoForge/Forge:
in `ctov-common.toml`). Defaults preserve stable gameplay.

| Option | Type | Default | Effect |
|---|---|---|---|
| `enableDiPetshopCompat` | bool | `true` | Master switch. If false, all `petshop_*` markers are cleared to AIR and no animals spawn. Chest loot table is also not bound. |
| `enableCtovPetshopTagResolution` | bool | `true` | If false, skip resolution layer 1 (CTOV semantic tags). Useful for debugging or when you want to force DI fallback behavior. |
| `enableDiPetshopFallbackTags` | bool | `true` | If false, skip resolution layer 3 (DI base tags). Useful if you want to require CTOV tags to be defined explicitly. |
| `enablePetshopDebugLogging` | bool | `false` | If true, log every marker resolution: `marker=... variant=... ctovTag=... fallbackTag=... entries=N reason=...` and every spawn: `marker=... entity=... baby=... age=...`. |
| `forcePetshopBabySpawns` | bool | `false` | If true, every spawned AgeableMob is forced to baby-state (unless the profile entry sets an explicit `age`). |

## 8. Test checklist (manual / integration)

1. **Vanilla biome villages** — generate villages in plains, desert, savanna, snowy, taiga.
   Verify CTOV petshop pieces spawn and animals appear inside cages / fish tank.
2. **CTOV non-vanilla biome villages** — generate villages in beach, christmas, dark_forest
   (halloween), desert_oasis, jungle, jungle_tree, mesa, mesa_fortified, mountain,
   mountain_alpine, mushroom, plains_fortified, savanna_na, swamp, swamp_fortified,
   taiga_fortified. Verify each spawns biome-appropriate animals.
3. **CTOV semantic tag resolution** — set `enablePetshopDebugLogging = true`; verify log lines
   show `ctovTag=domesticationinnovation:petshop/ctov/<variant>/<marker>` and a non-zero
   `entries` count for at least one non-vanilla variant.
4. **DI fallback tag** — temporarily move the CTOV tag file out of the datapack dir; verify
   the log shows `reason=di fallback` and animals still spawn (using the DI base tag).
5. **Animals actually spawn** (not just chest loot) — open the world, find a petshop, count
   mobs in each cage. If `enablePetshopDebugLogging = true`, the log records each `spawned
   marker=... entity=...` line.
6. **`age = -60000` permanent baby** — add a profile entry with `age: -60000` for an
   AgeableMob; verify the spawned mob stays baby indefinitely (wait > 1 in-game day).
7. **Non-ageable safety** — set `baby: true` and `age: -60000` on a `minecraft:parrot` entry;
   verify no exception is logged and the parrot spawns normally.
8. **DI-loaded vs DI-not-loaded** — repeat test 1 with DI loaded (animals spawn, chest loot
   binds) and without DI loaded (petshop structures may still appear from `rats` /
   `simplycats` load conditions, but no animals spawn and chest stays empty — no crash).
9. **Datapack entity additions** — drop a datapack that adds `minecraft:allay` to
   `domesticationinnovation:petshop/ctov/jungle/petshop_cage_3`. Reload datapacks
   (`/reload`). Generate a new jungle village. Verify allays can spawn in the cage.
10. **KubeJS additions** — same as 9 but via `ServerEvents.tags('entity_type', ...)`.

## 9. Files changed

### Code

| File | Change |
|---|---|
| `common/src/main/java/net/choicetheorem/ctov/CTOV.java` | Added `LOGGER` field (already in Copilot's first pass). |
| `common/src/main/java/net/choicetheorem/ctov/platform/CTOVConfigHelper.java` | Added 5 `@ExpectPlatform` config methods for petshop compat. |
| `common/src/main/java/net/choicetheorem/ctov/worldgen/processor/PetshopCompatStructurePoolElement.java` | New — the CTOV-side petshop compat element. Handles `petshop_water/chest/cage_0..3` markers. Three-layer entity resolution. Namespace fix from `ctov_compat` → `domesticationinnovation`. |
| `fabric/src/main/java/net/choicetheorem/ctov/platform/fabric/CTOVConfigHelperImpl.java` | Config impls for the 5 new options. |
| `fabric/src/main/java/net/choicetheorem/ctov/registry/fabric/CTOVConfigFabric.java` | Added `PetshopCompat` config section. |
| `fabric/src/main/java/net/choicetheorem/ctov/registry/fabric/worldgen/WorldgenRegistry.java` | Registered `PETSHOP_COMPAT` pool element type. |
| `neoforge/src/main/java/net/choicetheorem/ctov/platform/neoforge/CTOVConfigHelperImpl.java` | Config impls for the 5 new options. |
| `neoforge/src/main/java/net/choicetheorem/ctov/registry/neoforge/CTOVConfigNeoForge.java` | Added `petshop_compat` config category. |
| `neoforge/src/main/java/net/choicetheorem/ctov/registry/neoforge/worldgen/WorldgenRegistry.java` | Registered `PETSHOP_COMPAT` pool element type via `DeferredRegister`. |
| `neoforge/src/main/java/net/choicetheorem/ctov/neoforge/ctovNeo.java` | Wired `POOL_ELEMENTS` register to the mod event bus. |

### Resources

| Path | Change |
|---|---|
| `common/src/main/resources/data/ctov/lithostitched/worldgen_modifier/domesticationinnovation/*.json` (21 files) | `element_type` switched from `minecraft:single_pool_element` to `ctov:petshop_compat`. Load conditions preserved. |
| `common/src/main/resources/data/domesticationinnovation/tags/entity_types/petshop/ctov/<variant>/<marker>.json` (85 new files) | CTOV-specific entity tags under the `domesticationinnovation` namespace per task §3. |
| `common/src/main/resources/data/ctov/petshop_profiles/jungle/petshop_cage_3.json` (new) | Sample data-driven spawn profile demonstrating `weight`, `baby`, `age=-60000`. |
| `common/src/main/resources/data/ctov/petshop_profiles/swamp/petshop_water.json` (new) | Sample data-driven spawn profile for aquatic mobs. |

### Docs

| Path | Change |
|---|---|
| `docs/DI_COMPAT_PETSHOP.md` | This file. |
| `docs/internal_mapping.md` | Internal study-phase artifact (DI expects / CTOV provides / gap). |
| `README.md` | Updated to reflect fork scope; removed upstream Modrinth link per task. |

## 10. Known limitations

1. **Forge subproject is non-functional in this fork.** The `forge/` source tree contains
   only a stub `ctovForge.java` that imports a `net.ctov.ctov` class which doesn't exist
   anywhere in the common module. This is a pre-existing issue, not introduced by this
   compat work. The `neoforge/` build (which the `forge/` subproject's gradle config
   transforms via `transformProductionNeoForge`) is fully functional and is what Forge
   users actually get when architectury builds the forge JAR. Fixing the forge stub is
   out of scope for this task.
2. **Task spec mentioned MC 1.20.1 / Forge-only.** This fork is on MC 1.21.1 multi-loader
   (per `gradle.properties`). The compat layer is implemented for the actual repo target,
   not the spec's MC version. No 1.20.1 backport is provided.
3. **`rats` and `simplycats` load conditions.** The existing CTOV modifier JSONs trigger
   when any of `domesticationinnovation` / `rats` / `simplycats` is loaded. The CTOV compat
   element handles this gracefully (no entities spawn if DI isn't loaded), but the chest
   loot table binding will silently fail (chest stays empty) when only `rats` or
   `simplycats` is loaded. Pre-existing behavior — not made worse by this change.
4. **DI's petshop tags are static-cached.** DI caches `fishtankMobs` / `cageNMobs` arrays
   once per `initializedMobLists` flag and never refreshes. CTOV's compat layer re-resolves
   tags on every spawn, so it picks up datapack reloads correctly. This is strictly better
   than DI's behavior, but means CTOV and DI can briefly disagree on entity lists if a
   datapack is reloaded mid-world — DI will keep using the stale cache until restart.
5. **Profile JSON `_doc` field.** The leading `_doc` key in profile JSONs is not read by
   code — it's a documentation convention. The parser ignores unknown keys, so it's safe.
   Same for the `#comment` key in tag JSONs (mirrors DI's own convention).
6. **No automated tests.** All verification is manual per the test checklist in §8.
   Compiling and loading the mod in a dev environment is left to the maintainer.
