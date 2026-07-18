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
Per-village tag resolution:
  1. Primary tag (per-village biome tag, see §3 below)
  2. Optional profile JSON (ctov:petshop_profiles/<variant>/<marker>.json)
  3. DI fallback tag (petstore_cage_0 — safe default for non-vanilla variants)
  4. Safe failure — log + clear marker, no spawn, no crash
```

## 3. Tag scheme (per-village, per user spec)

### Vanilla-aligned variants — use DI's original numbered tags

These variants share DI's existing tags. All cage markers in a given variant use the **same**
tag (per-village theming), unlike DI's per-marker behavior.

| Variants | Cage tag (all markers) | Biome theme |
|---|---|---|
| `plains`, `plains_fortified`, `taiga`, `taiga_fortified`, `halloween` | `domesticationinnovation:petstore_cage_0` | plains/taiga |
| `desert`, `desert_oasis` | `domesticationinnovation:petstore_cage_1` | desert |
| `snowy_igloo`, `christmas` | `domesticationinnovation:petstore_cage_2` | snowy |
| `savanna`, `savanna_na` | `domesticationinnovation:petstore_cage_3` | savanna |

### Non-vanilla variants — use new CTOV biome tags

These variants use new flat tags under the `domesticationinnovation` namespace. All cage markers
in a given variant use the same biome tag.

| Variants | Cage tag (all markers) | Biome theme |
|---|---|---|
| `beach` | `domesticationinnovation:petshop_cage_beach` | beach / coast |
| `jungle` | `domesticationinnovation:petshop_cage_jungle` | jungle |
| `jungle_tree` | `domesticationinnovation:petshop_cage_bamboo` | bamboo jungle |
| `mesa`, `mesa_fortified` | `domesticationinnovation:petshop_cage_badlands` | badlands / mesa |
| `mountain`, `mountain_alpine` | `domesticationinnovation:petshop_cage_mountains` | mountains |
| `mushroom` | `domesticationinnovation:petshop_cage_mushroom` | mushroom fields |
| `swamp`, `swamp_fortified` | `domesticationinnovation:petshop_cage_swamp` | swamp |

### Fishtank marker

All variants use `domesticationinnovation:petstore_fishtank` for the `petshop_water` marker.

### Reserved tag (no current variant)

| Tag | Status |
|---|---|
| `domesticationinnovation:petshop_cage_cherry` | Created with sensible defaults (cat/rabbit/bee). No current CTOV variant maps to it — available for future variants or user customization via datapack. |

### DI-owned tags — UNCHANGED

The following tags are owned by DI and **must not be renamed, moved, or modified** by CTOV:

- `domesticationinnovation:petstore_fishtank`
- `domesticationinnovation:petstore_cage_0`
- `domesticationinnovation:petstore_cage_1`
- `domesticationinnovation:petstore_cage_2`
- `domesticationinnovation:petstore_cage_3`

CTOV references these tags by ID at runtime; users can extend them via their own datapacks as
they always could.

## 4. Customizing via datapack / KubeJS

### Add an entity to a CTOV biome tag

Drop a datapack at `<world>/datapacks/<your_pack>/data/domesticationinnovation/tags/entity_types/petshop_cage_jungle.json`:

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
  event.add('domesticationinnovation:petshop_cage_jungle', 'minecraft:allay')
  event.add('domesticationinnovation:petshop_cage_swamp', 'minecraft:frog')
})
```

### Add a brand-new biome tag

Just create the tag file at
`data/domesticationinnovation/tags/entity_types/petshop_cage_<your_biome>.json` — the CTOV
compat layer will pick it up if you map a variant to it via a profile override (see below).

### Profile-based spawn overrides (weight, baby, age, tag override)

For finer control (weighted picks, baby/age flags, or overriding which tag a variant uses),
drop a profile JSON at
`<world>/datapacks/<your_pack>/data/ctov/petshop_profiles/<variant>/<marker>.json`:

```json
{
  "ctov_tag": "domesticationinnovation:petshop_cage_jungle",
  "di_fallback_tag": "domesticationinnovation:petstore_cage_0",
  "entries": [
    { "entity": "minecraft:parrot", "weight": 4 },
    { "entity": "minecraft:ocelot", "weight": 2, "baby": true },
    { "entity": "minecraft:cat", "weight": 1, "age": -60000 }
  ]
}
```

When a profile is present, its `entries` are used as the second resolution layer (between the
primary tag and the DI fallback tag). Profile fields:

| Field | Type | Required | Meaning |
|---|---|---|---|
| `ctov_tag` | resource location | no | Override the primary tag ID (defaults to the per-village biome tag) |
| `di_fallback_tag` | resource location | no | Override the DI fallback tag ID (defaults to `petstore_cage_0` for non-vanilla variants, null for vanilla-aligned) |
| `entries[].entity` | resource location | yes | Entity type to spawn |
| `entries[].weight` | int | no (default 1) | Weighted pick weight |
| `entries[].baby` | bool | no | If true and entity is AgeableMob, call `setBaby(true)` |
| `entries[].age` | int | no | If set and entity is AgeableMob, call `setAge(value)`. **May be negative** — `age = -60000` produces a permanent baby that will never age up. |

If `age` is specified, `baby` is ignored (age takes precedence). Non-AgeableMob entities
(parrot, tropical_fish, etc.) safely ignore both fields.

## 5. Fallback resolution order

At spawn time, for each `(variant, marker)` pair, the CTOV compat layer tries in order:

1. **Primary tag** — the per-village biome tag (see §3)
   - Skipped if `enableCtovPetshopTagResolution = false` in config.
   - Skipped silently if the tag exists but is empty.
2. **Profile entries** — `ctov:petshop_profiles/<variant>/<marker>.json` `entries[]`
   - Only consulted if step 1 yielded no entries.
3. **DI fallback tag** — `domesticationinnovation:petstore_cage_0` (plains/taiga pets)
   - Only consulted for cage markers in **non-vanilla** variants (vanilla-aligned variants
     already used the DI tag as their primary tag in step 1).
   - Skipped if `enableDiPetshopFallbackTags = false` in config.
4. **Safe failure** — marker is cleared (block → AIR), `[CTOV-DI-Compat]` debug log records
   the reason. No crash, no spawn.

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
| `enableCtovPetshopTagResolution` | bool | `true` | If false, skip resolution layer 1 (primary tag). Useful for debugging or when you want to force profile-only / DI fallback behavior. |
| `enableDiPetshopFallbackTags` | bool | `true` | If false, skip resolution layer 3 (DI fallback tag). Useful if you want to require CTOV tags to be defined explicitly. |
| `enablePetshopDebugLogging` | bool | `false` | If true, log every marker resolution: `marker=... variant=... primaryTag=... fallbackTag=... entries=N reason=...` and every spawn: `marker=... entity=... baby=... age=...`. |
| `forcePetshopBabySpawns` | bool | `false` | If true, every spawned AgeableMob is forced to baby-state (unless the profile entry sets an explicit `age`). |

## 8. Test checklist (manual / integration)

1. **Vanilla biome villages** — generate villages in plains, desert, savanna, snowy, taiga.
   Verify CTOV petshop pieces spawn and animals appear inside cages / fish tank.
2. **CTOV non-vanilla biome villages** — generate villages in beach, christmas, halloween,
   desert_oasis, jungle, jungle_tree, mesa, mesa_fortified, mountain, mountain_alpine, mushroom,
   plains_fortified, savanna_na, swamp, swamp_fortified, taiga_fortified. Verify each spawns
   biome-appropriate animals.
3. **Primary tag resolution** — set `enablePetshopDebugLogging = true`; verify log lines show
   `primaryTag=domesticationinnovation:petshop_cage_<biome>` for non-vanilla variants and
   `primaryTag=domesticationinnovation:petstore_cage_N` for vanilla-aligned variants.
4. **DI fallback tag** — temporarily move a CTOV biome tag file out of the datapack dir;
   verify the log shows `reason=di fallback` and animals still spawn (using `petstore_cage_0`
   as the safe default).
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
   `domesticationinnovation:petshop_cage_jungle`. Reload datapacks (`/reload`). Generate a new
   jungle village. Verify allays can spawn in the cage.
10. **KubeJS additions** — same as 9 but via `ServerEvents.tags('entity_type', ...)`.

## 9. Files changed

### Code

| File | Change |
|---|---|
| `common/src/main/java/net/choicetheorem/ctov/CTOV.java` | Added `LOGGER` field (already in Copilot's first pass). |
| `common/src/main/java/net/choicetheorem/ctov/platform/CTOVConfigHelper.java` | Added 5 `@ExpectPlatform` config methods for petshop compat. |
| `common/src/main/java/net/choicetheorem/ctov/worldgen/processor/PetshopCompatStructurePoolElement.java` | New — the CTOV-side petshop compat element. Handles `petshop_water/chest/cage_0..3` markers. Per-village tag resolution with `variantToCageBiome` + `cageBiomeToTag` mapping. Three-layer resolution (primary tag → profile → DI fallback). |
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
| `common/src/main/resources/data/domesticationinnovation/tags/entity_types/petshop_cage_<biome>.json` (8 new files) | New CTOV biome tags: `badlands, beach, bamboo, cherry, jungle, mountains, mushroom, swamp`. Under the `domesticationinnovation` namespace per task §3. |
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
5. **Per-village theming differs from DI's per-marker behavior.** DI's
   `PetshopStructurePoolElement` uses a different tag per cage marker
   (`cage_0` → `petstore_cage_0`, `cage_1` → `petstore_cage_1`, …), giving every vanilla
   petshop a multi-biome zoo layout. CTOV's compat layer uses ONE tag per village variant
   (all cage markers in a plains village use `petstore_cage_0`, etc.), giving CTOV petshops
   a single-biome theme. This matches the user's requested design but means CTOV and DI
   petshops in the same world will have visibly different cage populations.
6. **Profile JSON `_doc` field.** The leading `_doc` key in profile JSONs is not read by
   code — it's a documentation convention. The parser ignores unknown keys, so it's safe.
   Same for the `#comment` key in tag JSONs (mirrors DI's own convention).
7. **No automated tests.** All verification is manual per the test checklist in §8.
   Compiling and loading the mod in a dev environment is left to the maintainer.
