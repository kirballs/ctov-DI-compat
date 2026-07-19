# Domestication Innovation × CTOV — Petshop Compat (3.4.14 port)

This branch ports the DI petshop compat layer from CTOV v3.3.1 to CTOV v3.4.14.
The v3.4.14 host project is a multi-loader Architectury project targeting
**Minecraft 1.20.x** (1.20.1 included — see compatibility note below) with Forge 48, NeoForge 20.2, Fabric, and Quilt — so the
compat layer now ships across all four platforms instead of Forge-only.

With this compat layer installed, CTOV villages will:

1. Actually contain DI-style petshop buildings (one per village, when the pool rolls one).
2. Spawn biome-appropriate pets inside the cages (rather than leaving them empty).
3. Stock the petshop chest with DI's loot table (when DI is installed — chest loot
   already resolves automatically via CTOV's existing .nbt setup, this layer is
   defensive in case the marker dispatch ever needs to bind it).
4. Fill the fishtank with biome-appropriate aquatic mobs and underwater decorations.

The implementation is **per-biome configurable** — every CTOV village variant maps to a
named spawn profile, and you can override any profile by dropping a JSON file into a
datapack at the same path.

---

## Why this is needed

CTOV v3.4.14 ships 21 petshop structure NBTs at
`common/src/main/resources/data/ctov/structures/village/<biome>/jobsite/petshop.nbt`.
Those NBTs contain data-marker structure blocks (`petshop_water`, `petshop_chest`,
`petshop_cage_0..3`) that DI's own `PetshopStructurePoolElement` is supposed to process —
turning the markers into real mobs, loot, and decorations.

The problem: CTOV's Lithostitched modifier JSONs at
`data/ctov/lithostitched/worldgen_modifier/domesticationinnovation/<biome>.json`
declare the petshop entries as `minecraft:single_pool_element`. That element type
**does not** process data markers — so the cages ended up empty, the fishtank stayed dry,
and only the chest (which has its loot table resolved separately via CTOV's existing
setup when DI is loaded) worked.

DI's own `PetshopStructurePoolElement` only injects itself into the five vanilla village
pools (`minecraft:village/<biome>/houses`) via Citadel's `VillageHouseManager`. Because
CTOV **replaces** vanilla villages with its own biome-specific pools
(`ctov:village/<biome>/house`), DI's injection never fires for CTOV villages.

This fork closes that gap by:

- Registering a new pool element type, `ctov:petshop_compat`, that extends
  `LegacySinglePoolElement` and overrides `handleDataMarker` to do the same job as DI's
  class. Registered per-platform (Forge `DeferredRegister`, NeoForge `DeferredRegister`,
  Fabric/Quilt `Registry.register`).
- Switching all 21 CTOV DI Lithostitched modifier JSONs from
  `minecraft:single_pool_element` to `ctov:petshop_compat` (with a `biome_profile` field
  that selects the spawn table).
- Reading per-biome spawn profiles from
  `common/src/main/resources/data/ctov/petshop_spawns/<biome>.json` instead of DI's five
  global entity-type tags, so a plains petshop can stock different pets than a snowy
  petshop.

---

## Installation

**Required:**

- Minecraft 1.20.1 (the mod is built against 1.20.2 mappings via the upstream `gradle.properties`, but CTOV's own `minecraft_version_range=[1.20,1.21)` covers all 1.20.x releases; 1.20.1 is the primary target since DI 1.7.x is Forge-only on 1.20.1)
- A supported mod loader (pick one):
  - Forge 48.1.0 or newer
  - NeoForge 20.2.88 or newer
  - Fabric Loader 0.15.11 or newer (with Fabric API)
  - Quilt Loader 0.26.0 or newer (with Quilted Fabric API)
- ChoiceTheorem's Overhauled Village — this fork's jar (replaces the upstream CTOV jar)
- Lithostitched 1.4 or newer (hard dependency — CTOV itself depends on this)

**Recommended:**

- [Domestication Innovation](https://www.curseforge.com/minecraft/mc-mods/domestication-innovation)
  1.7.0 or newer — DI is Forge-only on 1.20.x, so this only applies on the Forge and
  NeoForge builds. The chest loot table resolves automatically via CTOV's existing .nbt
  setup when DI is loaded; the custom `ctov:petshop_compat` element type is what makes the
  cage mobs and fishtank décor actually spawn.

> **Note on Fabric/Quilt:** DI does not ship a Fabric build, so on those loaders the
> Lithostitched modifier JSONs (which gate on `mod_loaded:domesticationinnovation`)
> never inject petshop buildings in the first place. The custom element type is
> registered on Fabric/Quilt defensively so the modifier JSONs parse cleanly if
> Lithostitched ever validates them, but it is never actually used at worldgen time
> on those platforms.

Drop both jars into your `mods/` folder. No further configuration is needed.

---

## Per-biome spawn profiles

Each spawn profile lives at `data/ctov/petshop_spawns/<profile>.json` and follows this
schema:

```json
{
  "entries": [
    {
      "entity": "minecraft:wolf",
      "weight": 30,
      "baby": true,
      "age": -24000
    },
    {
      "entity": "minecraft:cat",
      "weight": 25
    }
  ]
}
```

### Field reference

| Field      | Type    | Required | Default     | Notes |
|------------|---------|----------|-------------|-------|
| `entity`   | string  | yes      | —           | A registered entity ID, e.g. `minecraft:wolf` or `some_mod:foo`. Unknown IDs are silently skipped at spawn time (no crash). |
| `weight`   | int ≥ 1 | no       | `1`         | Relative weight. The probability of an entry being picked is `weight / sum(all weights)`. |
| `baby`     | bool    | no       | `false`     | If `true`, the spawned entity is aged down via `AgeableMob.setAge(age)`. No-op for non-`AgeableMob` entities (e.g. parrots, bats). |
| `age`      | int     | no       | `-24000`    | Only used when `baby: true`. **Negative = baby; more negative = longer baby duration.** `Integer.MIN_VALUE` (−2147483648) ≈ permanent baby. `0` is NOT baby — it forces immediate adulthood. One Minecraft day = 24000 ticks, so the default `-24000` means "baby for one day". |

> **Footgun warning:** Do NOT combine `baby: true` with `age: 0`. That produces an adult
> mob that *looks* like it was supposed to be a baby. Either omit `age` (defaults to
> `-24000`) or set it to a clearly negative value when `baby` is true.

### Biome → profile mapping

CTOV ships 21 village variants. They map to 14 spawn profiles (some biomes share a
profile per the maintainer's biome-merging rules):

| CTOV village variant(s)                                   | Spawn profile |
|-----------------------------------------------------------|---------------|
| `plains`, `plains_fortified`                              | `plains`      |
| `desert`, `desert_oasis`                                  | `desert`      |
| `snowy_igloo`, `christmas`                                | `snowy`       |
| `savanna`, `savanna_na`                                   | `savanna`     |
| `mesa`, `mesa_fortified`                                  | `badlands`    |
| `beach`                                                   | `beach`       |
| `jungle`, `jungle_tree`                                   | `jungle`      |
| `mountain`, `mountain_alpine`                             | `mountain`    |
| `mushroom`                                                | `mushroom`    |
| `swamp`, `swamp_fortified`                                | `swamp`       |
| `taiga`, `taiga_fortified`, `halloween`, `dark_forest`    | `forest`      |
| (reserved — no CTOV 1.20.x village yet)                  | `underground` |
| (reserved — no CTOV 1.20.x village yet)                  | `cherry`      |
| All biomes — used for `petshop_water` marker              | `fishtank`    |

To override any profile, drop a JSON file at
`<datapack>/data/ctov/petshop_spawns/<profile>.json` with the same schema.

---

## Marker dispatch

Inside every `petshop.nbt` structure file are 6 data-marker structure blocks (saved with
`"metadata": "<marker_name>"`). Vanilla `single_pool_element` clears them all to air.
`ctov:petshop_compat` dispatches on the marker name:

| Marker          | Behaviour                                                                                                                       |
|-----------------|---------------------------------------------------------------------------------------------------------------------------------|
| `petshop_water` | Spawn 2 from the shared `fishtank` profile, then place water / seagrass / waterlogged coral at the marker position.             |
| `petshop_chest` | Clear the marker (already done by vanilla). Defensive: bind the chest-below to `domesticationinnovation:chests/petshop_chest`.  |
| `petshop_cage_0`| Spawn 1–2 from the biome profile.                                                                                                |
| `petshop_cage_1`| Spawn 2–3 from the biome profile.                                                                                                |
| `petshop_cage_2`| Spawn 1–2 from the biome profile.                                                                                                |
| `petshop_cage_3`| Spawn 1 from the biome profile.                                                                                                  |

The chest loot table works in stock CTOV+DI without our compat layer (per maintainer
observation) — CTOV's .nbt files reference DI's loot table and DI resolves it when
loaded. Our `petshop_chest` handler is defensive: if the chest below the marker doesn't
already have the loot table bound, we bind it ourselves. If DI's loot table is
unresolved (DI not loaded), the call is a no-op — the chest stays empty, no crash.

Age is applied **after** `Mob.finalizeSpawn(...)` to avoid being overwritten by
finalizeSpawn's own age reset.

---

## Architecture (3.4.14 multi-loader port)

The compat layer is split across the Architectury modules:

```
common/src/main/java/net/ctov/petshop/
    PetshopSpawnProfile.java              — codec + weighted-pick helper (pure data)
    PetshopCompatStructurePoolElement.java — extends LegacySinglePoolElement,
                                              adds biome_profile field, dispatches
                                              on marker strings
    PetshopCompatRegistries.java          — common accessor: each platform publishes
                                              its registered type via setTypeSupplier()

forge/src/main/java/net/ctov/forge/petshop/
    PetshopCompatForgeRegistry.java       — DeferredRegister + RegistryObject,
                                              publishes to PetshopCompatRegistries

neoforge/src/main/java/net/ctov/neoforge/petshop/
    PetshopCompatNeoRegistry.java         — same pattern, NeoForge 20.2.x still uses
                                              net.minecraftforge.registries packages

fabric/src/main/java/net/ctov/fabric/petshop/
    PetshopCompatFabricRegistry.java      — Registry.register on onInitialize

quilt/src/main/java/net/ctov/quilt/petshop/
    PetshopCompatQuiltRegistry.java       — same as Fabric, different entry point
```

The platform mod entry points (`ctovForge`, `ctovNeo`, `ctovFabric`, `ctovQuilt`) each
call their platform's `init()` method to register the type and publish it to the common
accessor.

---

## Files modified (vs upstream CTOV 3.4.14)

- 21 Lithostitched modifier JSONs at
  `common/src/main/resources/data/ctov/lithostitched/worldgen_modifier/domesticationinnovation/*.json`:
  flipped `element_type` from `minecraft:single_pool_element` to `ctov:petshop_compat`
  + added `biome_profile` field.
- 14 new spawn profile JSONs at
  `common/src/main/resources/data/ctov/petshop_spawns/*.json`.
- 21 new isolated test template pools at
  `common/src/main/resources/data/ctov/worldgen/template_pool/village/test/petshop/*.json`
  — for use with the [CommandStructures](https://www.curseforge.com/minecraft/mc-mods/commandstructures)
  mod to spawn individual petshops without spawning the whole village (see
  Testing section below).
- 7 new Java files (3 common, 4 platform-specific).
- 4 platform mod entry points updated to call the registry glue.
- 4 platform metadata files (`mods.toml` × 2, `fabric.mod.json`, `quilt.mod.json`)
  updated to declare `domesticationinnovation` as a soft dependency.
- This doc + `docs/internal_mapping.md`.

---

## Testing with CommandStructures

The [CommandStructures](https://www.curseforge.com/minecraft/mc-mods/commandstructures)
mod lets you spawn individual NBT structures at a position via
`/spawnstructure`. CTOV ships 21 isolated test template pools at
`data/ctov/worldgen/template_pool/village/test/petshop/<biome>.json` — each
contains exactly ONE petshop element with `element_type: ctov:petshop_compat`.

To spawn just a petshop (no village) for testing:

```
/spawnstructure ~ ~ ~ ctov:village/test/petshop/<biome> 0 false false false false
```

The `0` (jigsaw depth) means no jigsaw chain — only the single petshop .nbt
is placed. The `ctov:petshop_compat` element type fires its
`handleDataMarker(...)` for every structure-block marker inside the .nbt,
so you get the full DI compat logic (cage mobs, fishtank décor, water blocks,
chest marker) without needing to /locate a village.

### Available test pools

| Biome | Pool id |
|---|---|
| Beach | `ctov:village/test/petshop/beach` |
| Christmas | `ctov:village/test/petshop/christmas` |
| Dark Forest (uses halloween .nbt) | `ctov:village/test/petshop/dark_forest` |
| Desert | `ctov:village/test/petshop/desert` |
| Desert Oasis | `ctov:village/test/petshop/desert_oasis` |
| Jungle | `ctov:village/test/petshop/jungle` |
| Jungle Tree | `ctov:village/test/petshop/jungle_tree` |
| Mesa (Badlands) | `ctov:village/test/petshop/mesa` |
| Mesa Fortified | `ctov:village/test/petshop/mesa_fortified` |
| Mountain | `ctov:village/test/petshop/mountain` |
| Mountain Alpine | `ctov:village/test/petshop/mountain_alpine` |
| Mushroom | `ctov:village/test/petshop/mushroom` |
| Plains | `ctov:village/test/petshop/plains` |
| Plains Fortified | `ctov:village/test/petshop/plains_fortified` |
| Savanna | `ctov:village/test/petshop/savanna` |
| Savanna NA | `ctov:village/test/petshop/savanna_na` |
| Snowy | `ctov:village/test/petshop/snowy` |
| Swamp | `ctov:village/test/petshop/swamp` |
| Swamp Fortified | `ctov:village/test/petshop/swamp_fortified` |
| Taiga | `ctov:village/test/petshop/taiga` |
| Taiga Fortified | `ctov:village/test/petshop/taiga_fortified` |

For biomes with multiple processor variants (mushroom, savanna, snowy), the
test pool uses the first variant. To test another variant, edit the test
pool's `processors` field — e.g. to test the red savanna house instead of
orange, change `ctov:village/savanna/house_orange` to
`ctov:village/savanna/house_red` in
`common/src/main/resources/data/ctov/worldgen/template_pool/village/test/petshop/savanna.json`
and rebuild.

---

## Comparison to the v3.3.1 port

The v3.3.1 port (Forge-only) is preserved on the archive tag
`archive/di-petshop-compat-1.20.1-20250719`. The 3.4.14 port differs in:

- **Build system:** Architectury multi-loader (Forge, NeoForge, Fabric, Quilt) instead
  of Forge-only ForgeGradle 6.
- **Minecraft version:** still 1.20.x — the upstream `gradle.properties` pins to 1.20.2 mappings, but CTOV's `minecraft_version_range=[1.20,1.21)` keeps it 1.20.1-compatible. DI 1.7.x is Forge-only on 1.20.1, so the primary test target is still 1.20.1 + Forge 47/48.
- **Compat delivery:** Lithostitched modifier JSONs (was 21 patched `house.json` files).
- **Petshop .nbt files:** already bundled in the jar by upstream CTOV (was a separate
  datapack in v3.3.1, the v3.3.1 port moved them into the jar — not needed here).
- **Java package:** `net.ctov.petshop` (was `com.choicetheorem.ctov`).
- **Per-platform registry glue:** Forge + NeoForge `DeferredRegister`, Fabric + Quilt
  `Registry.register` (was a single Forge `DeferredRegister`).
