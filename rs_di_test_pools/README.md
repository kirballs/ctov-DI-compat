# RS-DI Petshop Test Pools (for CommandStructures)

This is a tiny standalone datapack that provides 11 single-petshop template pools — one per RS village biome — so you can spawn individual petshops from the **original** [Repurposed Structures - Domestication Innovation](https://github.com/TelepathicGrunt/RepurposedStructuresCompatDatapacks) datapack via [CommandStructures](https://www.curseforge.com/minecraft/mc-mods/commandstructures)' `/spawnstructure` command.

It does **not** require the CTOV-DI-compat fork. It only requires RS, DI, the original RS-DI datapack, and CommandStructures.

## Why this exists

The original RS-DI datapack stores petshop NBTs at:
```
data/domesticationinnovation/structures/villages/<biome>/petshop.nbt
```

That means the vanilla `/place template` command to spawn one is:
```
/place template domesticationinnovation:villages/<biome>/petshop ~ ~ ~
```

(Not `repurposed_structures:villages/<biome>/petshop` — that path doesn't exist. The NBTs live under the `domesticationinnovation:` namespace, not `repurposed_structures:`.)

**The catch:** vanilla `/place template` does not resolve jigsaws. The original RS-DI petshop NBTs contain jigsaw blocks inside the cages and fishtank that target RS's pet sub-pools (`repurposed_structures:villages/pets_glass_cage`, `pets_fish`, `pets_cat_extra`, etc.). Those sub-pools are what actually contain the pet NBTs (wolf, cat, fish, etc.).

So with vanilla `/place template`, you get the petshop building shell with visible jigsaw blocks where the cages/fishtank should be, and **no pets inside**.

CommandStructures' `/spawnstructure` command DOES resolve jigsaws (when depth > 0). But it takes a template_pool id, not a single NBT. So you need a wrapper template_pool that contains just the petshop as its only entry — which is what this datapack provides.

## The template pools

11 pools, one per RS village biome, at:
```
data/ctov/worldgen/template_pool/villages/test/rs_di_petshop/<biome>.json
```

Each pool has exactly one element: the original RS-DI datapack's petshop NBT at `domesticationinnovation:villages/<biome>/petshop`, using vanilla `minecraft:legacy_single_pool_element`.

| Biome | Template pool id | Petshop NBT |
|---|---|---|
| badlands    | `ctov:villages/test/rs_di_petshop/badlands`    | `domesticationinnovation:villages/badlands/petshop`    |
| bamboo      | `ctov:villages/test/rs_di_petshop/bamboo`      | `domesticationinnovation:villages/bamboo/petshop`      |
| birch       | `ctov:villages/test/rs_di_petshop/birch`       | `domesticationinnovation:villages/birch/petshop`       |
| cherry      | `ctov:villages/test/rs_di_petshop/cherry`      | `domesticationinnovation:villages/cherry/petshop`      |
| dark_forest | `ctov:villages/test/rs_di_petshop/dark_forest` | `domesticationinnovation:villages/dark_forest/petshop` |
| giant_taiga | `ctov:villages/test/rs_di_petshop/giant_taiga` | `domesticationinnovation:villages/giant_taiga/petshop` |
| jungle      | `ctov:villages/test/rs_di_petshop/jungle`      | `domesticationinnovation:villages/jungle/petshop`      |
| mountains   | `ctov:villages/test/rs_di_petshop/mountains`   | `domesticationinnovation:villages/mountains/petshop`   |
| mushroom    | `ctov:villages/test/rs_di_petshop/mushroom`    | `domesticationinnovation:villages/mushroom/petshop`    |
| oak         | `ctov:villages/test/rs_di_petshop/oak`         | `domesticationinnovation:villages/oak/petshop`         |
| swamp       | `ctov:villages/test/rs_di_petshop/swamp`       | `domesticationinnovation:villages/swamp/petshop`       |

The `ctov:` namespace is used for the test pool ids purely for consistency with the CTOV-DI-compat fork's own test pools. This datapack does NOT require the fork to be installed — `ctov:` is just a namespace, and template pools don't need their namespace mod to be loaded.

## Installation

### Required companion mods/datapacks

1. **Repurposed Structures** (mod, 1.20.1 branch, 7.1+)
2. **Domestication Innovation** (mod, Forge/NeoForge, 1.7+)
3. **Original RS-DI datapack** — download from [TelepathicGrunt/RepurposedStructuresCompatDatapacks](https://github.com/TelepathicGrunt/RepurposedStructuresCompatDatapacks), specifically the `Compat_Domestication_Innovation/built/Repurposed_Structures-Domestication_Innovation_v4.zip` file (or build the latest from source).
4. **CommandStructures** (mod, [CurseForge](https://www.curseforge.com/minecraft/mc-mods/commandstructures))
5. **This datapack**

### Option A — Use the prebuilt zip from CI

Download the `rs-di-test-pools-datapack` artifact from the latest GitHub Actions run on this fork. Unzip the artifact locally to get `rs_di_test_pools-<version>.zip`. Drop that zip into your world's `datapacks/` folder.

### Option B — Build the zip yourself

```bash
bash scripts/package_rs_di_test_pools.sh
```

Produces `rs_di_test_pools/build/rs_di_test_pools-<version>.zip`. Drop into your world's `datapacks/` folder.

### Option C — Use the folder directly (development only)

Symlink or copy the `rs_di_test_pools/` folder into `datapacks/`. Minecraft accepts unzipped datapack folders.

## Usage

Once everything is installed, run `/reload` once (or restart the world), then:

```
/spawnstructure ~ ~ ~ ctov:villages/test/rs_di_petshop/<biome> 2 false false false false
```

For example:
```
/spawnstructure ~ ~ ~ ctov:villages/test/rs_di_petshop/bamboo 2 false false false false
```

### Command syntax breakdown

`/spawnstructure <x> <y> <z> <template_pool> <depth> <ignoreTrees> <ignoreBounds> <placeAir> <randomRotation>`

| Argument | Value | Why |
|---|---|---|
| `x y z` | `~ ~ ~` | Your position. |
| `template_pool` | `ctov:villages/test/rs_di_petshop/<biome>` | The test pool — contains exactly one entry: the petshop NBT. |
| `depth` | `2` | **Critical.** Depth 0 = no jigsaw resolution (same as vanilla `/place template`). Depth 1 = resolve the cage/fishtank jigsaws in the petshop. Depth 2 = resolve jigsaws in the resolved sub-NBTs too (harmless — pet sub-NBTs don't have jigsaws). Use 2 to be safe. |
| `ignoreTrees` | `false` | Don't ignore trees (default behaviour). |
| `ignoreBounds` | `false` | Don't ignore structure bounds (default behaviour). |
| `placeAir` | `false` | Don't place air blocks (default behaviour — preserves terrain). |
| `randomRotation` | `false` | No random rotation — place with the NBT's default facing. Set to `true` if you want a random rotation. |

### What you should see

A fully-furnished petshop building from the original RS-DI datapack, with:
- The building shell (walls, floor, roof, counter, etc.)
- Cage enclosures with pets inside (wolf, cat, rabbit, fox, parrot, turtle, frog, etc. — depends on biome)
- A fishtank with fish (pufferfish, tropical fish, axolotl, tadpole)
- A chest with DI's petshop loot

The exact pet selection depends on the biome — RS defines which pet sub-pools each biome's petshop uses via the jigsaw targets inside the NBT.

### Troubleshooting

**"Unknown structure pool" error** — the test pool isn't loaded. Check:
1. Is `rs_di_test_pools-<version>.zip` (or the unzipped folder) in `datapacks/`?
2. Did you run `/reload` after installing it?
3. Does `pack.mcmeta` exist inside the zip's root? (Unzip and check if unsure.)

**Petshop spawns but cages are empty (jigsaw blocks visible)** — depth was 0. Re-run with depth 2:
```
/spawnstructure ~ ~ ~ ctov:villages/test/rs_di_petshop/bamboo 2 false false false false
```

**"Unable to load structure" error** — the original RS-DI datapack isn't installed. The test pool references `domesticationinnovation:villages/<biome>/petshop`, which is provided by that datapack's NBT files. Install `Repurposed_Structures-Domestication_Innovation_v4.zip` (or newer) into `datapacks/` and run `/reload`.

**Pets spawn but immediately wander off** — expected. The pet sub-NBTs from the original RS-DI datapack bake entities directly into the structure (no `PersistenceRequired` flag). They'll wander around and may despawn. For persistent pets, use the CTOV-DI-compat fork's `ctov:petshop_compat` system instead — that uses data markers + programmatic spawning with `Mob.setPersistenceRequired()`.

## Comparison with the fork's rs_di_compat datapack

| Feature | This datapack (test_pools) | Fork's rs_di_compat datapack |
|---|---|---|
| Requires CTOV-DI-compat fork jar? | No | Yes |
| Requires original RS-DI datapack? | Yes (for the petshop NBTs) | No (ships its own starter NBTs) |
| Pet spawning mechanism | Jigsaw resolution (vanilla) | `ctov:petshop_compat` data markers + programmatic spawn |
| Per-biome spawn profiles? | No (uses RS's baked-in pet selection) | Yes (fork's `petshop_spawns/<profile>.json`) |
| Persistence of spawned pets? | No (entities wander and may despawn) | Yes (`Mob.setPersistenceRequired()`) |
| Fishtank décor? | Yes (baked into the jigsaw sub-NBT) | Yes (programmatic — water/seagrass/coral) |
| Chest loot? | Yes (baked into the NBT) | Yes (bound to `domesticationinnovation:chests/petshop_chest` via marker) |
| Use case | Quick visual inspection of the original RS-DI petshops | Production replacement with per-biome spawn profiles |

If you just want to see what the original RS-DI petshops look like, use this test_pools datapack. If you want a production-ready RS × DI petshop system with per-biome spawn profiles and persistent pets, use the fork's rs_di_compat datapack (and install the fork's CTOV jar).

## File layout

```
rs_di_test_pools/
├── README.md                                                            ← this file
├── pack.mcmeta                                                          ← datapack manifest
├── data/
│   └── ctov/
│       └── worldgen/
│           └── template_pool/
│               └── villages/
│                   └── test/
│                       └── rs_di_petshop/
│                           ├── badlands.json                            ← single-petshop test pool
│                           ├── bamboo.json
│                           ├── birch.json
│                           ├── cherry.json
│                           ├── dark_forest.json
│                           ├── giant_taiga.json
│                           ├── jungle.json
│                           ├── mountains.json
│                           ├── mushroom.json
│                           ├── oak.json
│                           └── swamp.json
└── build/                                                               ← produced by scripts/package_rs_di_test_pools.sh
    └── rs_di_test_pools-<version>.zip                                   ← drop into world's datapacks/
```
