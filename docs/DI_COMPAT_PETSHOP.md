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
        ├─ petshop_cage_0  → spawn 1–2 cage mobs (count matches DI)
        ├─ petshop_cage_1  → spawn 2–3 cage mobs
        ├─ petshop_cage_2  → spawn 1–2 cage mobs
        └─ petshop_cage_3  → spawn 1 cage mob
        │
        ▼
Spawn list lookup:
  1. Resolve variant → biome name (e.g. plains, jungle, swamp — see §3 below)
  2. For petshop_water marker, use biome = "fishtank"
  3. Load ctov:petshop_spawns/<biome>.json
  4. Pick N entities via weighted random selection (N from spawn count table)
  5. If file missing or empty → no spawn, marker cleared to AIR, debug log records reason
```

## 3. Spawn-list scheme (per-village, unified JSON)

Every CTOV village variant maps to **one biome name**, which selects **one JSON file** at
`data/ctov/petshop_spawns/<biome>.json`. All cage markers in a given variant use the same JSON.
The fishtank marker uses a separate shared `fishtank.json` regardless of variant.

### Variant → biome mapping

| CTOV variant | Biome name | Spawn-list file |
|---|---|---|
| `plains`, `plains_fortified`, `taiga`, `taiga_fortified`, `halloween` | `plains` | `ctov:petshop_spawns/plains.json` |
| `desert`, `desert_oasis` | `desert` | `ctov:petshop_spawns/desert.json` |
| `snowy_igloo`, `christmas` | `snowy` | `ctov:petshop_spawns/snowy.json` |
| `savanna`, `savanna_na` | `savanna` | `ctov:petshop_spawns/savanna.json` |
| `beach` | `beach` | `ctov:petshop_spawns/beach.json` |
| `jungle` | `jungle` | `ctov:petshop_spawns/jungle.json` |
| `jungle_tree` | `bamboo` | `ctov:petshop_spawns/bamboo.json` |
| `mesa`, `mesa_fortified` | `badlands` | `ctov:petshop_spawns/badlands.json` |
| `mountain`, `mountain_alpine` | `mountains` | `ctov:petshop_spawns/mountains.json` |
| `mushroom` | `mushroom` | `ctov:petshop_spawns/mushroom.json` |
| `swamp`, `swamp_fortified` | `swamp` | `ctov:petshop_spawns/swamp.json` |
| (reserved, no current variant) | `cherry` | `ctov:petshop_spawns/cherry.json` |
| All variants (for `petshop_water` marker) | `fishtank` | `ctov:petshop_spawns/fishtank.json` |

### Spawn-list JSON format

```json
{
  "_doc": "Optional human-readable description. Ignored by code.",
  "replace": false,
  "entries": [
    { "entity": "minecraft:parrot", "weight": 4 },
    { "entity": "minecraft:ocelot", "weight": 2, "baby": true },
    { "entity": "minecraft:cat", "weight": 1, "age": -60000 }
  ]
}
```

Fields:

| Field | Type | Required | Meaning |
|---|---|---|---|
| `_doc` | string | no | Human-readable note. Ignored by code. |
| `replace` | bool | no (default false) | Currently informational only — same JSON file always replaces the previous list for that biome. Reserved for future merge semantics. |
| `entries[].entity` | resource location | yes | Entity type to spawn (e.g. `minecraft:parrot`) |
| `entries[].weight` | int | no (default 1) | Weighted-pick weight. Must be > 0. |
| `entries[].baby` | bool | no | If true and entity is `AgeableMob`, call `setBaby(true)`. Ignored if `age` is set. |
| `entries[].age` | int | no | If set and entity is `AgeableMob`, call `setAge(value)`. **May be negative** — see §6 for full semantics. |

Unknown entity IDs are skipped with a debug log line (no crash). Non-`AgeableMob` entities
(parrot, tropical_fish, pufferfish, etc.) safely ignore `baby` and `age`.

### What about DI's own tags?

DI's own entity tags (`domesticationinnovation:petstore_cage_0..3`, `petstore_fishtank`) are
**not used** by CTOV's compat layer. They still exist and DI still uses them for any
DI-placed petshops (i.e. vanilla villages), but CTOV's petshops read exclusively from the
`ctov:petshop_spawns/` JSON files. Users who want to affect both CTOV and DI petshops in the
same world will need to edit both DI's tags and CTOV's JSON files. In a modpack where DI's
vanilla tags are overridden or removed, only the CTOV JSON files matter.

## 4. Customizing via datapack / KubeJS

### Edit an existing biome's spawn list

Drop a datapack at
`<world>/datapacks/<your_pack>/data/ctov/petshop_spawns/jungle.json` with your replacement:

```json
{
  "_doc": "My custom jungle petshop — only parrots and pandas, with babies",
  "replace": false,
  "entries": [
    { "entity": "minecraft:parrot", "weight": 5 },
    { "entity": "minecraft:panda", "weight": 3, "baby": true },
    { "entity": "minecraft:ocelot", "weight": 1, "age": -60000 }
  ]
}
```

Datapack resources override mod-bundled resources of the same `namespace:path` — your file at
`data/ctov/petshop_spawns/jungle.json` will replace the shipped file. Reload with `/reload`.

KubeJS equivalent (in `server_scripts`):

```js
ServerEvents.highPriorityData(event => {
  event.addJson('ctov:petshop_spawns/jungle.json', {
    _doc: 'My custom jungle petshop — only parrots and pandas, with babies',
    replace: false,
    entries: [
      { entity: 'minecraft:parrot', weight: 5 },
      { entity: 'minecraft:panda', weight: 3, baby: true },
      { entity: 'minecraft:ocelot', weight: 1, age: -60000 }
    ]
  })
})
```

### Add a brand-new biome spawn list

Just create the file at `data/ctov/petshop_spawns/<your_biome>.json`. To actually use it,
you'd need to also remap a CTOV variant to that biome name — currently the variant → biome
mapping is hardcoded in `PetshopCompatStructurePoolElement.variantToBiome()`. Fork the mod
and edit that switch statement, or submit a PR adding your variant.

## 5. Spawn count per marker

Spawn counts match DI's `PetshopStructurePoolElement.handleDataMarker` exactly:

| Marker | Count |
|---|---|
| `petshop_water` | 2 |
| `petshop_cage_0` | 1 + rand(0..1) |
| `petshop_cage_1` | 2 + rand(0..1) |
| `petshop_cage_2` | 1 + rand(0..1) |
| `petshop_cage_3` | 1 |

## 6. Baby / age field behavior

The `age` field follows vanilla `AgeableMob.setAge(int)` semantics:

| `age` value | Effect |
|---|---|
| Positive (`age > 0`) | Spawn as adult; can't breed for `age` ticks (breeding cooldown) |
| `0` | Spawn as adult (default state) |
| Negative (`age < 0`) | Spawn as baby; grows up when age timer reaches 0 |
| More negative (e.g. `-60000`) | Spawn as baby for a longer time (~50 in-game hours / ~3 real-time hours at 20 TPS) |
| `Integer.MIN_VALUE` (`-2147483648`) | Effectively permanent baby — would take ~3 real-time years to age up |

The `baby: true` shortcut is equivalent to a small negative age (the mob ages up within ~20
minutes). It's a convenience for "spawn as baby that grows up reasonably soon". Use explicit
`age` when you want fine control over how long the baby state lasts.

Design intent (per maintainer): the long-baby feature exists so players can find baby animals
in petshops and spend meaningful time with them before they grow up. `age = -60000` is the
shipped default for "memorable but not permanent" babies; bump it to `-200000` or
`Integer.MIN_VALUE` in your datapack if you want even longer baby state.

| Entity type | `baby: true` | `age: N` (positive) | `age: -N` (negative) |
|---|---|---|---|
| `AgeableMob` (cat, wolf, rabbit, fox, goat, …) | `setBaby(true)` — grows up in ~20 min | `setAge(N)` — breeding cooldown | `setAge(-N)` — baby for N ticks |
| Non-`AgeableMob` (parrot, tropical_fish, pufferfish, …) | Ignored safely | Ignored safely | Ignored safely |

`forcePetshopBabySpawns = true` in config overrides every spawn to baby-state (unless an
explicit `age` was given in the spawn-list entry).

## 7. Config reference

All options live under the `petshop_compat` category (Fabric: in `ctov.toml`; NeoForge/Forge:
in `ctov-common.toml`). Defaults preserve stable gameplay.

| Option | Type | Default | Effect |
|---|---|---|---|
| `enableDiPetshopCompat` | bool | `true` | Master switch. If false, all `petshop_*` markers are cleared to AIR and no animals spawn. Chest loot table is also not bound. |
| `enablePetshopDebugLogging` | bool | `false` | If true, log every marker resolution: `marker=... variant=... biome=... entries=N file=ctov:petshop_spawns/<biome>.json` and every spawn: `marker=... entity=... baby=... age=...`. |
| `forcePetshopBabySpawns` | bool | `false` | If true, every spawned AgeableMob is forced to baby-state (unless the spawn-list entry sets an explicit `age`). |

(Previous versions had `enableCtovPetshopTagResolution` and `enableDiPetshopFallbackTags`
options — these have been removed because the new single-file resolution model has no
layers to toggle.)

## 8. Test checklist (manual / integration)

1. **Vanilla biome villages** — generate villages in plains, desert, savanna, snowy, taiga.
   Verify CTOV petshop pieces spawn and animals appear inside cages / fish tank.
2. **CTOV non-vanilla biome villages** — generate villages in beach, christmas, halloween,
   desert_oasis, jungle, jungle_tree, mesa, mesa_fortified, mountain, mountain_alpine, mushroom,
   plains_fortified, savanna_na, swamp, swamp_fortified, taiga_fortified. Verify each spawns
   biome-appropriate animals.
3. **Spawn-list file lookup** — set `enablePetshopDebugLogging = true`; verify log lines show
   `biome=<biome_name>` and `file=ctov:petshop_spawns/<biome_name>.json` for each marker.
4. **Missing-file safety** — temporarily rename a shipped JSON (e.g. move `jungle.json` out of
   the datapack dir); verify the log shows `spawn list not found: ctov:petshop_spawns/jungle.json`
   and no animals spawn (no crash, marker cleared to AIR).
5. **Animals actually spawn** (not just chest loot) — open the world, find a petshop, count
   mobs in each cage. If `enablePetshopDebugLogging = true`, the log records each
   `spawned marker=... entity=...` line.
6. **`age = -60000` long baby** — add a spawn-list entry with `age: -60000` for an AgeableMob;
   verify the spawned mob stays baby for at least 2 in-game days, then ages up.
7. **Non-ageable safety** — set `baby: true` and `age: -60000` on a `minecraft:parrot` entry;
   verify no exception is logged and the parrot spawns normally.
8. **DI-loaded vs DI-not-loaded** — repeat test 1 with DI loaded (animals spawn, chest loot
   binds) and without DI loaded (petshop structures may still appear from `rats` /
   `simplycats` load conditions, but no animals spawn and chest stays empty — no crash).
9. **Datapack entity additions** — drop a datapack that overrides
   `ctov:petshop_spawns/jungle.json` with a custom list including `minecraft:allay`. Reload
   datapacks (`/reload`). Generate a new jungle village. Verify allays can spawn in the cage.
10. **KubeJS additions** — same as 9 but via `ServerEvents.highPriorityData(...)`.

## 9. Files changed

### Code

| File | Change |
|---|---|
| `common/src/main/java/net/choicetheorem/ctov/worldgen/processor/PetshopCompatStructurePoolElement.java` | Rewritten. Single JSON-file lookup (`ctov:petshop_spawns/<biome>.json`) replaces the previous 3-layer tag → profile → DI-fallback resolution. `variantToBiome()` maps variant → biome name; `markerToBiome()` handles the fishtank special case. `age` field documented correctly (long-lasting baby, not "permanent"). |
| `common/src/main/java/net/choicetheorem/ctov/platform/CTOVConfigHelper.java` | Removed `enableCtovPetshopTagResolution` and `enableDiPetshopFallbackTags` (no longer applicable). |
| `fabric/src/main/java/net/choicetheorem/ctov/platform/fabric/CTOVConfigHelperImpl.java` | Removed the two unused config methods. |
| `fabric/src/main/java/net/choicetheorem/ctov/registry/fabric/CTOVConfigFabric.java` | Removed the two unused config fields. |
| `fabric/src/main/java/net/choicetheorem/ctov/registry/fabric/worldgen/WorldgenRegistry.java` | Registered `PETSHOP_COMPAT` pool element type (unchanged from prior commit). |
| `neoforge/src/main/java/net/choicetheorem/ctov/platform/neoforge/CTOVConfigHelperImpl.java` | Removed the two unused config methods. |
| `neoforge/src/main/java/net/choicetheorem/ctov/registry/neoforge/CTOVConfigNeoForge.java` | Removed the two unused config specs. |
| `neoforge/src/main/java/net/choicetheorem/ctov/registry/neoforge/worldgen/WorldgenRegistry.java` | Registered `PETSHOP_COMPAT` pool element type (unchanged from prior commit). |
| `neoforge/src/main/java/net/choicetheorem/ctov/neoforge/ctovNeo.java` | Wired `POOL_ELEMENTS` register to the mod event bus (unchanged from prior commit). |

### Resources

| Path | Change |
|---|---|
| `common/src/main/resources/data/ctov/lithostitched/worldgen_modifier/domesticationinnovation/*.json` (21 files) | `element_type` switched from `minecraft:single_pool_element` to `ctov:petshop_compat`. Load conditions preserved. |
| `common/src/main/resources/data/ctov/petshop_spawns/<biome>.json` (13 new files) | Unified spawn-list JSONs: `plains`, `desert`, `snowy`, `savanna`, `badlands`, `beach`, `bamboo`, `cherry`, `jungle`, `mountains`, `mushroom`, `swamp`, `fishtank`. Each contains biome-appropriate entity list with `weight`, `baby`, `age` fields. |

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
4. **Single source of truth: CTOV JSON files.** CTOV's petshop spawns are controlled
   exclusively by the 13 `ctov:petshop_spawns/*.json` files. DI's own
   `petstore_cage_0..3` / `petstore_fishtank` tags are not consulted. If a modpack wants
   both CTOV and DI vanilla petshops to use the same entity lists, the user must edit both
   CTOV's JSON files and DI's tags. In practice, modpacks that ship this fork typically
   override or remove DI's vanilla tags anyway, so only CTOV's JSON files matter.
5. **Per-village theming differs from DI's per-marker behavior.** DI's
   `PetshopStructurePoolElement` uses a different tag per cage marker
   (`cage_0` → `petstore_cage_0`, `cage_1` → `petstore_cage_1`, …), giving every vanilla
   petshop a multi-biome zoo layout. CTOV's compat layer uses ONE spawn list per village
   variant (all cage markers in a plains village use `plains.json`, etc.), giving CTOV
   petshops a single-biome theme. This matches the maintainer's requested design but means
   CTOV and DI petshops in the same world will have visibly different cage populations.
6. **`_doc` and `#comment` JSON fields.** The leading `_doc` key in spawn-list JSONs is not
   read by code — it's a documentation convention. The parser ignores unknown keys, so it's
   safe. Same for `replace` (currently informational only — reserved for future merge
   semantics).
7. **Variant → biome mapping is hardcoded.** The `variantToBiome()` switch statement in
   `PetshopCompatStructurePoolElement.java` is compile-time. Adding a new variant requires a
   code change. A future enhancement could move this mapping to a datapack-driven JSON, but
   for the 21 currently-shipped variants this is overkill.
8. **No automated tests.** All verification is manual per the test checklist in §8.
   Compiling and loading the mod in a dev environment is left to the maintainer.
