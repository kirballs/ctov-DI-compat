# **ChoiceTheorem's Overhauled Village — DI Petshop Compat Fork**

[![GitHub Badge](https://img.shields.io/badge/fork%20of-ChoiceTheorem%2FCTOV-black?logo=github)](https://github.com/ChoiceTheorem/ChoiceTheorem-s-overhauled-village)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1%20%7C%201.20.2-62b85a?logo=minecraft)](https://www.minecraft.net/)
[![Mod Loaders](https://img.shields.io/badge/loaders-Forge%2048%20%7C%20NeoForge%2020.2%20%7C%20Fabric%20%7C%20Quilt-blueviolet)](https://modrinth.com/mod/ctov)
![Environment](https://img.shields.io/badge/environment-server-orangered?style=flat-square)
![CTOV Version](https://img.shields.io/badge/CTOV-3.4.14-orange)

This is a **fork** of [ChoiceTheorem's Overhauled Village (CTOV)](https://github.com/ChoiceTheorem/ChoiceTheorem-s-overhauled-village)
v3.4.14 that adds a complete, working **Domestication Innovation (DI) petshop compatibility
layer** for all 21 CTOV village variants — across all four supported mod loaders.

The fork ships as a drop-in replacement for the upstream CTOV jar: same villages, same
outposts, same everything else, plus working petshops.

---

## What this fork adds

### 1. Native DI petshop compat for all 21 CTOV village variants

Upstream CTOV 3.4.14 ships 21 petshop `.nbt` structure files and registers them via
Lithostitched modifier JSONs — but the modifier JSONs use `minecraft:single_pool_element`,
whose `handleDataMarker()` is a no-op. That means the data-marker structure blocks
inside the petshop `.nbt`s (`petshop_cage_0..3`, `petshop_water`, `petshop_chest`) are
all silently cleared to air: **cages spawn no mobs, fishtank has no fish, the chest
marker does nothing.** DI's own `PetshopStructurePoolElement` only injects itself into
the five vanilla village pools, so it never fires for CTOV's biome-specific pools
(`ctov:village/<biome>/house`).

This fork closes that gap by:

- Registering a new pool element type **`ctov:petshop_compat`** that extends vanilla
  `LegacySinglePoolElement` and overrides `handleDataMarker(...)` to dispatch on the
  marker name — doing the same job as DI's own class.
- Switching all 21 CTOV DI Lithostitched modifier JSONs from
  `minecraft:single_pool_element` to `ctov:petshop_compat` (with a new `biome_profile`
  field that selects the spawn table).
- Restoring the 21 village `petshop.nbt` files to versions that contain the DI data
  markers (upstream 3.4.14 had replaced them with `rats:rat_cage` compat — see
  *Files modified* below).

The result: every CTOV village that rolls a petshop will now actually spawn
biome-appropriate pets in the cages, fill the fishtank with aquatic mobs + décor, and
bind the chest loot table to DI's `domesticationinnovation:chests/petshop_chest`.

### 2. Per-biome spawn profiles

Instead of DI's five global entity-type tags, the fork reads 14 named spawn profiles
from `data/ctov/petshop_spawns/<profile>.json`. Every CTOV village variant maps to one
profile, so a plains petshop stocks different pets than a snowy or jungle petshop.

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
| `bamboo` (RS only)                                        | `bamboo`      |
| `cherry` (RS only — reserved)                             | `cherry`      |
| `underground` (reserved)                                  | `underground` |
| All biomes — used by `petshop_water` marker               | `fishtank`    |

Override any profile by dropping a JSON file at
`<datapack>/data/ctov/petshop_spawns/<profile>.json` with the same schema:

```json
{
  "entries": [
    { "entity": "minecraft:wolf", "weight": 30, "baby": true, "age": -24000 },
    { "entity": "minecraft:cat",  "weight": 25 }
  ]
}
```

Unknown entity IDs are silently skipped at spawn time (no crash), so adding modded mobs
to the profiles is safe.

### 3. Marker dispatch — what each DI marker does

| Marker          | Behaviour                                                                                                                       |
|-----------------|---------------------------------------------------------------------------------------------------------------------------------|
| `petshop_water` | Spawn 2 from the shared `fishtank` profile, then place water / seagrass / waterlogged coral at the marker position.             |
| `petshop_chest` | Clear the marker (already done by vanilla). Defensive: bind the chest-below to `domesticationinnovation:chests/petshop_chest`.  |
| `petshop_cage_0`| Spawn 1–2 from the biome profile.                                                                                                |
| `petshop_cage_1`| Spawn 2–3 from the biome profile.                                                                                                |
| `petshop_cage_2`| Spawn 1–2 from the biome profile.                                                                                                |
| `petshop_cage_3`| Spawn 1 from the biome profile.                                                                                                  |

The chest loot table resolves automatically via CTOV's existing `.nbt` setup when DI is
loaded — the `petshop_chest` handler is defensive (no-op if DI is absent).

### 4. Repurposed Structures × DI petshop compat datapack

The `rs_di_compat/` folder is a standalone datapack (with its own `pack.mcmeta`) that
adds DI petshop buildings to all 11 Repurposed Structures village variants
(badlands, bamboo, birch, cherry, dark_forest, giant_taiga, jungle, mountains,
mushroom, oak, swamp). It uses the same `ctov:petshop_compat` pool element type, so
RS villages get the same cage mobs / fishtank / chest behaviour as CTOV villages.

See [`rs_di_compat/README.md`](rs_di_compat/README.md) for installation steps and the
per-biome spawn profile mapping.

### 5. Isolated test template pools for CommandStructures

The fork ships 21 isolated test template pools at
`data/ctov/worldgen/template_pool/village/test/petshop/<biome>.json`. Each contains
exactly one petshop entry with `element_type: ctov:petshop_compat`.

With the [CommandStructures](https://www.curseforge.com/minecraft/mc-mods/commandstructures)
mod installed, you can spawn a single petshop (no village) for fast iteration testing:

```
/spawnstructure ~ ~ ~ ctov:village/test/petshop/plains 0 false false false false
```

The `0` (jigsaw depth) means no jigsaw chain — only the single petshop `.nbt` is placed,
and the full DI compat logic fires (cage mobs, fishtank décor, water blocks, chest marker).

### 6. Multi-loader: Forge, NeoForge, Fabric, Quilt

The compat layer is split across the Architectury modules so it ships on all four
loaders that CTOV 3.4.14 supports:

```
common/src/main/java/net/ctov/petshop/      — codec, element type, common accessor
forge/src/main/java/net/ctov/forge/petshop/ — DeferredRegister
neoforge/src/main/java/net/ctov/neoforge/petshop/ — DeferredRegister
fabric/src/main/java/net/ctov/fabric/petshop/ — Registry.register
quilt/src/main/java/net/ctov/quilt/petshop/ — Registry.register
```

DI itself is Forge-only on 1.20.x, so the chest loot and cage mobs only fire on the
Forge and NeoForge builds. The Fabric/Quilt builds register the `ctov:petshop_compat`
type defensively so the Lithostitched modifier JSONs still parse cleanly — but the
modifier JSONs gate on `mod_loaded:domesticationinnovation`, so they never inject
petshop buildings on Fabric/Quilt in the first place.

---

## Compatibility

### Minecraft version

- **Primary target: Minecraft 1.20.1** (Forge 47/48 + DI 1.7.x is Forge-only on 1.20.1).
- **Also runs on: Minecraft 1.20.2** (CTOV 3.4.14's `gradle.properties` pins 1.20.2
  mappings; CTOV's `minecraft_version_range=[1.20,1.21)` covers both).

### Mod loader support

| Loader    | Version                  | DI petshop compat?        |
|-----------|--------------------------|---------------------------|
| Forge     | 48.1.0 or newer          | **Yes** — fully functional |
| NeoForge  | 20.2.88 or newer         | Yes (DI is Forge-compatible) |
| Fabric    | Loader 0.15.11 + API     | Defensive only (DI absent) |
| Quilt     | Loader 0.26.0 + QFA      | Defensive only (DI absent) |

### Required dependencies

- **Lithostitched 1.4+** (hard dep — CTOV itself depends on this).
- One of the four supported mod loaders above.
- This fork's CTOV jar (replaces the upstream CTOV jar).

### Recommended companion mods

- [**Domestication Innovation**](https://www.curseforge.com/minecraft/mc-mods/domestication-innovation) 1.7.0+
  — without DI, the petshop buildings still generate (the .nbt files are in the jar),
  but the chest loot table won't resolve and the cage marker dispatch has nothing to
  look up. The fork's compat layer is a no-op when DI is absent (no crash).
- [**Repurposed Structures**](https://www.curseforge.com/minecraft/mc-mods/repurposed-structures)
  — enables the RS DI petshop compat datapack in `rs_di_compat/`. Install as a datapack
  (see `rs_di_compat/README.md`).
- [**CommandStructures**](https://www.curseforge.com/minecraft/mc-mods/commandstructures)
  — optional, for `/spawnstructure` testing of individual petshops.

### Modpacks

Yes, you can include this fork in modpacks. Please credit both ChoiceTheorem (original
CTOV) and this fork. The original CTOV license (BY-NC-ND-4.0) applies.

---

## Installation

1. Install Minecraft 1.20.1 (or 1.20.2) with one of: Forge 48, NeoForge 20.2, Fabric,
   or Quilt.
2. Install [Lithostitched](https://modrinth.com/mod/lithostitched) 1.4+.
3. (Recommended) Install [Domestication Innovation](https://www.curseforge.com/minecraft/mc-mods/domestication-innovation)
   1.7.0+ — Forge only.
4. Drop this fork's CTOV jar (replaces upstream CTOV) into your `mods/` folder.
5. (Optional, for RS support) Drop the `rs_di_compat/` folder as a datapack into your
   world's `datapacks/` folder.
6. (Optional, for testing) Install CommandStructures and use
   `/spawnstructure ~ ~ ~ ctov:village/test/petshop/<biome> 0 false false false false`.

No further configuration is needed. The fork auto-detects DI via Lithostitched's
`mod_loaded` conditions.

---

## Building from source

This is a standard Architectury multi-loader project. Building requires JDK 17 and
Gradle 8.7+ (the wrapper is included).

```bash
# Build all four loader jars (Forge, NeoForge, Fabric, Quilt)
./gradlew build

# Or build a single loader
./gradlew :forge:build
./gradlew :neoforge:build
./gradlew :fabric:build
./gradlew :quilt:build
```

The built jars will be in `<loader>/build/libs/`. Use the `-dev` jar if you want
unobfuscated builds (for development); use the regular jar for production.

### GitHub Actions

The fork includes a `.github/workflows/Build.yml` workflow that builds all four loader
jars on push and PR. The workflow uses JDK 17 and Gradle 8.7.

---

## Files modified (vs upstream CTOV 3.4.14)

- **21 Lithostitched modifier JSONs** at
  `common/src/main/resources/data/ctov/lithostitched/worldgen_modifier/domesticationinnovation/*.json`:
  flipped `element_type` from `minecraft:single_pool_element` to `ctov:petshop_compat`
  + added `biome_profile` field.
- **21 village `petshop.nbt` files** at
  `common/src/main/resources/data/ctov/structures/village/<biome>/jobsite/petshop.nbt`:
  restored to versions that contain the DI data markers (upstream 3.4.14 had replaced
  them with `rats:rat_cage` compat — without DI markers, the compat layer would have
  been a no-op).
- **15 spawn profile JSONs** at
  `common/src/main/resources/data/ctov/petshop_spawns/*.json` (14 biome profiles +
  `fishtank.json`).
- **21 isolated test template pools** at
  `common/src/main/resources/data/ctov/worldgen/template_pool/village/test/petshop/*.json`.
- **7 new Java files** (3 common, 4 platform-specific).
- **4 platform mod entry points** updated to call the registry glue.
- **4 platform metadata files** (`mods.toml` × 2, `fabric.mod.json`, `quilt.mod.json`)
  updated to declare `domesticationinnovation` as a soft dependency.
- **`rs_di_compat/`** — standalone datapack for Repurposed Structures × DI compat
  (24 files: 11 pool additions, 11 spawn-count additions, README, `pack.mcmeta`).
- **3 docs** at `docs/` (`DI_COMPAT_PETSHOP.md`, `internal_mapping.md`,
  `PR_DESCRIPTION.md`).

See [`docs/DI_COMPAT_PETSHOP.md`](docs/DI_COMPAT_PETSHOP.md) for the full technical
write-up of the compat layer, and [`docs/internal_mapping.md`](docs/internal_mapping.md)
for the maintainer-facing code flow + per-platform registry details.

---

## About CTOV

ChoiceTheorem's Overhauled Village is a structure mod that enhances and creates new
village and pillager outpost variants. It adds 23 village variants and 14 pillager
outpost variants tailored to suit the terrain, theme and biomes. The existing villages
are rebuilt from the ground up and each biome type can generate two types of structures.

The fork inherits all upstream CTOV features (compat with Terralith, Oh Biome You'll
Go, Biomes O' Plenty, Town & Tower, Repurposed Structures, etc.) plus the DI petshop
compat layer described above. Upstream CTOV's full list of compat datapacks
(Waystone, Farmer's Delight, More Villagers, Croptopia, Bountiful, Friend and Foe,
Immersive Engineering, Incubation, Chef's Delight, Beautify, Vampirism, Wizards,
Villager Plus, Advanced Peripheral, BYG, Savage and Ravage, Vending Machine) is
preserved — install them the same way as upstream.

<details><summary>Upstream CTOV FAQs</summary>

**1. Is it safe to update CTOV to a newer version?** Yes.

**2. Is it safe to add CTOV to an already existing world?** Yes — new structures will
only spawn in newly generated chunks.

**3. Is this mod for Forge or Fabric/Quilt?** All of them — Forge, NeoForge, Fabric,
and Quilt are all supported by this fork (multi-loader Architectury build).

**4. How can I locate the new structures?**
`/locate structure ctov:<structure_from_list>`

**5. Does CTOV replace existing vanilla structures?** The only structures modified by
CTOV are **vanilla villages** in older versions.

**6. What about the loot of these structures?** The vast majority of structures use
vanilla loot tables for better mod compatibility, but they also use some custom loot
tables to better integrate said structures into the world.

**7. How can I report bugs/issues/suggestions?** Please open an issue on this fork's
GitHub repo. For upstream CTOV issues, please use the upstream repo.

**8. Can I include CTOV in my modpack?** Sure, but make sure to give credit and a link
to the upstream CTOV page.

</details>

<details><summary>Compatible mods (inherited from upstream CTOV)</summary>

+ Most world generation mods like Terralith, Oh Biome you'll go, Biomes O'plenty.
+ Various structure mods like Town & Tower and Repurposed structures.
+ Any other structure packs by ChoiceTheorem like Immersive structures.
+ [Domestication Innovation](https://www.curseforge.com/minecraft/mc-mods/domestication-innovation)
  — **first-class compat in this fork** (per-biome spawn profiles, working cage mobs,
  fishtank décor, chest loot table).
+ [Repurposed Structures](https://www.curseforge.com/minecraft/mc-mods/repurposed-structures)
  — **first-class RS × DI petshop compat** in this fork (see `rs_di_compat/`).
+ [CommandStructures](https://www.curseforge.com/minecraft/mc-mods/commandstructures)
  — **first-class test pools** in this fork (see Testing section above).

</details>

---

## Credits

- **ChoiceTheorem** — original CTOV mod, on which this fork is based. All village/outpost
  structure design, worldgen, and most of the code is upstream CTOV's work.
- **Vichy0623** — codesigning the upstream builds.
- **Robified** — converting the original CTOV datapack into a mod.
- **The DI team** — Domestication Innovation mod, whose `PetshopStructurePoolElement`
  logic this fork's `ctov:petshop_compat` element type reimplements for the multi-loader
  CTOV context.
- This fork's DI petshop compat layer is licensed under the same license as upstream CTOV
  (BY-NC-ND-4.0).

---

## Reporting issues

- **Issues specific to this fork** (DI petshop compat, RS DI compat, test pools,
  multi-loader build): open an issue on
  [this fork's GitHub](https://github.com/kirballs/ctov-DI-compat/issues).
- **Upstream CTOV issues** (village/outpost structure design, worldgen bugs not related
  to petshops): open an issue on
  [upstream CTOV's GitHub](https://github.com/ChoiceTheorem/ChoiceTheorem-s-overhauled-village/issues).

When reporting a fork-specific issue, please include:
- Mod loader and version (Forge 48 / NeoForge 20.2 / Fabric / Quilt)
- Minecraft version (1.20.1 or 1.20.2)
- Whether DI is installed (and which version)
- Whether RS is installed (and whether the `rs_di_compat` datapack is enabled)
- A screenshot or `/locate` output showing the petshop building
- The relevant `latest.log` excerpt if there's a stack trace
