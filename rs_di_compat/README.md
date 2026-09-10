# Repurposed Structures — DI Petshop Compat Datapack

This datapack adds Domestication Innovation petshop buildings to all 11 Repurposed Structures village variants, using the `ctov:petshop_compat` pool element registered by the CTOV-DI-compat fork.

## What this datapack does

For each of the 11 RS village biomes (`badlands`, `bamboo`, `birch`, `cherry`, `dark_forest`, `giant_taiga`, `jungle`, `mountains`, `mushroom`, `oak`, `swamp`), this datapack:

1. Injects one petshop entry into the RS village houses pool (`repurposed_structures:villages/<biome>/houses`).
2. Caps the petshop to one per village via `rs_pieces_spawn_counts_additions`.
3. Provides a starter petshop `.nbt` file at `data/ctov/structures/villages/<biome>/petshop.nbt`.

The injected pool entry uses `ctov:petshop_compat` as the element type, with a `biome_profile` field that selects the spawn table from the fork's `data/ctov/petshop_spawns/<profile>.json`. The chest loot table resolves automatically via DI when DI is loaded.

## Important: RS does NOT ship petshop NBTs

Repurposed Structures itself does **not** ship any petshop structure NBTs. The `/place template repurposed_structures:villages/<biome>/petshop ~ ~ ~` command will therefore return "no such template" — this is expected, not a bug. (Previous versions of this README incorrectly stated that RS ships petshop NBTs and could be used as a source via `/place template`. That was wrong: RS has animal pens, shepherds, etc., but no petshop building.)

To make this datapack work out of the box, this fork ships **starter petshop NBTs** that are copied from the fork's own CTOV petshop NBTs (`data/ctov/structures/village/<ctov_biome>/jobsite/petshop.nbt`) with biome-appropriate mapping. They contain the DI data markers (`petshop_cage_0..3`, `petshop_water`, `petshop_chest`) so the `ctov:petshop_compat` element type will dispatch correctly.

The mapping is:

| RS village biome | Starter NBT source (CTOV) | Reason |
|---|---|---|
| `badlands`         | `ctov:village/mesa/jobsite/petshop`        | Mesa is the legacy name for badlands. |
| `bamboo`           | `ctov:village/jungle/jobsite/petshop`      | Jungle is the closest thematic match for bamboo. |
| `birch`            | `ctov:village/taiga/jobsite/petshop`       | Forested temperate biome. |
| `cherry`           | `ctov:village/plains/jobsite/petshop`      | Blossom biome — plains is the closest neutral starter. |
| `dark_forest`      | `ctov:village/taiga/jobsite/petshop`       | Forested temperate biome. |
| `giant_taiga`      | `ctov:village/taiga/jobsite/petshop`       | Taiga is the closest match. |
| `jungle`           | `ctov:village/jungle/jobsite/petshop`      | Direct match. |
| `mountains`        | `ctov:village/mountain/jobsite/petshop`    | Direct match. |
| `mushroom`         | `ctov:village/mushroom/jobsite/petshop`    | Direct match. |
| `oak`              | `ctov:village/plains/jobsite/petshop`      | Oak is a generic temperate forest — plains is the closest neutral starter. |
| `swamp`            | `ctov:village/swamp/jobsite/petshop`       | Direct match. |

The starter NBTs are **placeholders**. They're CTOV-styled buildings dropped into RS villages — visually they may not perfectly match the RS village aesthetic (e.g. RS bamboo villages use bamboo wood, but the starter NBT is jungle-themed). To get a perfect look, replace each starter NBT with a custom one designed for the target RS biome (see *Replacing the starter NBTs* below).

The spawn logic, cage counts, fishtank décor, and chest loot all work correctly with the starter NBTs because those are driven by the `biome_profile` field in the pool_additions JSONs and the `petshop_*` data markers inside the NBTs — not by the NBT's block palette.

## Two namespaces, on purpose

This datapack uses **two different namespaces** for two different things. Don't confuse them:

| Asset | Namespace | Why |
|---|---|---|
| Pool additions (which house pool gets a petshop entry injected) | `repurposed_structures:` | RS's own pool-additions system reads JSONs from `data/repurposed_structures/pool_additions/...`. That's RS's contract. |
| The structure NBT files themselves | `ctov:` | They're loaded by the `ctov:petshop_compat` element type registered by the CTOV-DI-compat fork. The fork's own petshop NBTs live under `ctov:`, so we keep RS petshops in the same namespace for consistency. |

When you save a structure block, the **Name** field is the resource ID Minecraft writes the file under. So `ctov:villages/bamboo/petshop` lands at `data/ctov/structures/villages/bamboo/petshop.nbt` — which matches the `location` field in `houses.json`. Using `repurposed_structures:` for the save name would write to the wrong folder and the building would never generate.

## Installation

### Option A — Use the prebuilt zip from CI

1. Download the `rs-di-compat-datapack` artifact from the latest GitHub Actions run on this fork.
2. Unzip the artifact locally — you'll get `rs_di_compat-<version>.zip`.
3. Drop `rs_di_compat-<version>.zip` into your world's `datapacks/` folder.
4. In-game, run `/reload` (or restart the world).

### Option B — Build the zip yourself

```bash
# From the repo root:
bash scripts/package_rs_di_compat.sh
```

This produces `rs_di_compat/build/rs_di_compat-<mod_version>.zip`. Drop that zip into your world's `datapacks/` folder.

### Option C — Use the folder directly (development only)

For development, you can symlink or copy the `rs_di_compat/` folder itself into `datapacks/`. Minecraft accepts unzipped datapack folders. Rename it to `rs_di_compat` (no version suffix) for cleanliness.

## Required companion mods

- **CTOV-DI-compat fork jar** (Forge / NeoForge / Fabric / Quilt — same loader as your game). Registers the `ctov:petshop_compat` pool element type and the per-biome spawn profiles.
- **Repurposed Structures** (1.20.1 branch, RS 7.1+). The pool_additions and rs_pieces_spawn_counts_additions JSONs are read by RS — without RS, this datapack is inert.
- **Domestication Innovation** (Forge / NeoForge, 1.7+). Provides the `domesticationinnovation:chests/petshop_chest` loot table that the `petshop_chest` marker binds to. Without DI, petshops still generate but the chest stays empty.
- **Lithostitched** (transitively, via CTOV).

## The marker system (read this if you're replacing NBTs)

Each `.nbt` file must contain **Data-mode structure blocks** with specific metadata names. The `ctov:petshop_compat` element type reads these markers at worldgen to know where to spawn pets, place water, and bind the chest loot table. Without them, the building generates empty.

There are six marker types. The number after `petshop_cage_` is a **spawn-count selector**, not a positional ID — multiple cages can share the same marker name.

| Marker name (structure block "Data" metadata) | What it does at gen time | Where to place it |
|---|---|---|
| `petshop_cage_0` | Spawns 1–2 pets from the biome profile | On the floor inside a small cage |
| `petshop_cage_1` | Spawns 2–3 pets from the biome profile | On the floor inside a larger cage (recommended default) |
| `petshop_cage_2` | Spawns 1–2 pets from the biome profile | On the floor inside a second terrarium |
| `petshop_cage_3` | Spawns 1 pet from the biome profile | On a perch inside a 1-block cage |
| `petshop_water` | Spawns 2 fishtank mobs, then places water/seagrass/coral | At the water-surface block inside the fishtank |
| `petshop_chest` | Clears the marker block, binds the chest below to DI's `chests/petshop_chest` loot table | One block above an existing chest |

At gen time, the marker block is always cleared to air (for cages/chest) or replaced with water/seagrass/coral (for fishtank). Pets spawn at the marker's exact position, centered with a `+0.5` offset.

### Minimum marker set per building

| Marker | Required? | Typical count |
|---|---|---|
| `petshop_cage_1` (or `petshop_cage_3` for 1-block cages) | **Yes** — at least one | 1–4 (one per cage) |
| `petshop_chest` | **Yes** — at least one | 1 |
| `petshop_water` | Optional — only if the building has a fishtank | 0 or 1 |

A minimal functional petshop is: **one `petshop_cage_1` on a cage floor + one `petshop_chest` directly above a chest block.**

## Replacing the starter NBTs

The committed starter NBTs are designed to make the datapack usable immediately. To replace one with a custom design:

### Step 1 — Start a creative world

Create a new flat creative world with cheats enabled. You need:
- **CTOV-DI-compat fork** installed (registers `ctov:petshop_compat`)
- **Domestication Innovation** installed (provides the `chests/petshop_chest` loot table)
- **Repurposed Structures** installed (so you can visit an RS village of the target biome for inspiration)

You do **not** need the CommandStructures mod — vanilla `/place template` is enough.

### Step 2 — Spawn a starter petshop as your starting point

You have two good options for a starting NBT:

**Option A: Spawn the existing starter NBT for this biome.** This is the fastest path — you start with a working petshop and just adapt its blocks to match the RS biome aesthetic.

```
/place template ctov:villages/<biome>/petshop ~ ~ ~
```

For example:
```
/place template ctov:villages/bamboo/petshop ~ ~ ~
```

(Yes — `ctov:villages/<biome>/petshop` works once this datapack is installed, because the starter NBTs live at `data/ctov/structures/villages/<biome>/petshop.nbt` in this datapack.)

**Option B: Spawn a CTOV petshop directly.** Useful if you want to start from a different CTOV biome's petshop than the one the starter NBT was based on.

```
/place template ctov:village/<ctov_biome>/jobsite/petshop ~ ~ ~
```

For example:
```
/place template ctov:village/jungle/jobsite/petshop ~ ~ ~
```

Either way, the placed building will already have working DI markers — you're not starting from scratch.

### Step 3 — Adapt the building blocks to the biome

Swap the wood, planks, and terrain blocks to match the target RS biome's materials. For example, for `bamboo`, swap any jungle wood for bamboo wood, jungle planks for bamboo planks, etc.

This is purely cosmetic — the markers don't care about the building's blocks.

### Step 4 — (Optional) Add or remove cages

If you want more cages: place additional Data-mode structure blocks on cage floors with metadata `petshop_cage_1` (or `petshop_cage_0`/`petshop_cage_2`/`petshop_cage_3` for different spawn counts).

If you want fewer: break the structure blocks you don't want.

### Step 5 — Save the structure

1. Place a new structure block at one corner of the building, just outside it (so it doesn't overlap a marker).
2. Open the structure block UI:
   - **Mode:** Save
   - **Name:** `ctov:villages/<biome>/petshop` — for example `ctov:villages/bamboo/petshop`
     - The name MUST match the `location` field in your `houses.json` exactly (character-for-character, including the `ctov:` prefix and the `villages/` plural)
   - **Include entities:** OFF (entities come from markers, not baked into the NBT)
   - **Ignore entities:** ON (same effect — ensures no wandering mobs get baked in)
3. Adjust the bounding box size offsets so the box fully covers your building.
4. Click **Save**.

The `.nbt` file will appear at:
```
<saves>/<world_name>/generated/ctov/structures/villages/<biome>/petshop.nbt
```

### Step 6 — Copy the NBT into the datapack

Copy the file to:
```
rs_di_compat/data/ctov/structures/villages/<biome>/petshop.nbt
```

The path mirrors the save name: `ctov:` → `data/ctov/`, `villages/<biome>/petshop` → `structures/villages/<biome>/petshop.nbt` (Minecraft adds the `structures/` prefix automatically when saving).

### Step 7 — Rebuild the zip and reinstall

```bash
bash scripts/package_rs_di_compat.sh
```

Then drop the new `rs_di_compat-<version>.zip` into your world's `datapacks/` folder and run `/reload`.

### Step 8 — Verify

Quick structural check:
1. The `.nbt` exists at the expected path.
2. The filename is `petshop.nbt` (lowercase, no `.nbt.nbt`).
3. The biome folder name matches one of the 11 biomes listed at the top.

For full in-world verification, find an RS village of each biome and check that petshop buildings spawn with pets — but that's a final integration test, not a per-NBT check.

You can also spot-check a single NBT in isolation with:
```
/place template ctov:villages/<biome>/petshop ~ ~ ~
```

This places one building at your position. No jigsaw resolution happens, so the cage markers and chest marker are visible as structure blocks (until `ctov:petshop_compat` processes them during real village gen — `/place template` uses vanilla `single_pool_element` behaviour, so it just places the NBT raw and leaves the markers as structure blocks). Walk inside, confirm the markers are at the right spots, then break them and re-save if needed.

## Common pitfalls

- **Save name with `repurposed_structures:`** → file lands in the wrong namespace, the `houses.json` `location: ctov:...` won't find it, petshop never generates. Always use `ctov:`.
- **Save name using `village/` (singular)** instead of `villages/` (plural) → file lands at `data/ctov/structures/village/<biome>/petshop.nbt` instead of `data/ctov/structures/villages/<biome>/petshop.nbt`, the `location` field won't find it. Always use `villages/` (plural) to match the `location` field in your JSONs.
- **Forgetting to remove the load-mode structure block** you used to spawn the building (if you used `/place template` this isn't an issue — vanilla `/place template` doesn't leave a structure block behind — but if you loaded via a structure block in Load mode, break it before saving).
- **Including entities ON** → any mobs wandering around the building (e.g. randomly spawned sheep) get baked into the NBT and will duplicate the marker-spawned ones. Always leave it OFF.
- **Structure block placed on a wall or ceiling** → pets spawn in the wrong spot (inside a wall, or falling from the ceiling). Always place cage markers on the cage floor.
- **Trying `/place template repurposed_structures:villages/<biome>/petshop`** → returns "no such template". RS doesn't ship petshop NBTs. Use `ctov:villages/<biome>/petshop` (this datapack) or `ctov:village/<ctov_biome>/jobsite/petshop` (the fork's main jar) instead.

## Biome → Spawn Profile Mapping

The `biome_profile` field in each `houses.json` selects which spawn profile the petshop uses. The spawn profiles themselves live in the fork's main jar at `data/ctov/petshop_spawns/<profile>.json`. Override them by dropping a JSON at `<datapack>/data/ctov/petshop_spawns/<profile>.json`.

| Village Biome | Profile | Entities |
|---|---|---|
| badlands | `badlands` | (from CTOV's badlands profile) |
| bamboo | `bamboo` | parrot, rabbit, wolf, cat, panda |
| birch | `forest` | wolf, fox, cat, rabbit, parrot |
| cherry | `cherry` | cat, rabbit, bee, parrot |
| dark_forest | `forest` | wolf, fox, cat, rabbit, parrot |
| giant_taiga | `forest` | wolf, fox, cat, rabbit, parrot |
| jungle | `jungle` | (from CTOV's jungle profile) |
| mountains | `mountain` | (from CTOV's mountain profile) |
| mushroom | `mushroom` | (from CTOV's mushroom profile) |
| oak | `forest` | wolf, fox, cat, rabbit, parrot |
| swamp | `swamp` | (from CTOV's swamp profile) |

## Adding modded mobs

Edit the spawn profile JSON at `data/ctov/petshop_spawns/<profile>.json` in a separate datapack (or override the fork's defaults). Add entries like:
```json
{ "entity": "some_mod:cool_pet", "weight": 10, "baby": true, "age": -24000 }
```
Unknown entity IDs are safely skipped at spawn time (no crash).

## File layout

```
rs_di_compat/
├── README.md                                            ← this file
├── pack.mcmeta                                          ← datapack manifest
├── data/
│   ├── ctov/
│   │   └── structures/
│   │       └── villages/
│   │           ├── badlands/petshop.nbt                 ← starter NBT (CTOV mesa design)
│   │           ├── bamboo/petshop.nbt                   ← starter NBT (CTOV jungle design)
│   │           ├── birch/petshop.nbt                    ← starter NBT (CTOV taiga design)
│   │           ├── cherry/petshop.nbt                   ← starter NBT (CTOV plains design)
│   │           ├── dark_forest/petshop.nbt              ← starter NBT (CTOV taiga design)
│   │           ├── giant_taiga/petshop.nbt              ← starter NBT (CTOV taiga design)
│   │           ├── jungle/petshop.nbt                   ← starter NBT (CTOV jungle design)
│   │           ├── mountains/petshop.nbt                ← starter NBT (CTOV mountain design)
│   │           ├── mushroom/petshop.nbt                 ← starter NBT (CTOV mushroom design)
│   │           ├── oak/petshop.nbt                      ← starter NBT (CTOV plains design)
│   │           └── swamp/petshop.nbt                    ← starter NBT (CTOV swamp design)
│   └── repurposed_structures/
│       ├── pool_additions/
│       │   └── villages/
│       │       ├── <biome>/houses.json                  ← injects petshop into RS houses pool
│       │       └── ... (11 biomes)
│       └── rs_pieces_spawn_counts_additions/
│           ├── village_<biome>.json                     ← caps petshop at 1 per village
│           └── ... (11 biomes)
└── build/                                                ← produced by scripts/package_rs_di_compat.sh
    └── rs_di_compat-<version>.zip                       ← drop into world's datapacks/
```

## Why this is a separate datapack (and not part of the jar)

The fork's main CTOV jar is intentionally kept free of any RS-specific assets. RS integration is delivered exclusively through this datapack for three reasons:

1. **Build isolation.** The jar can be built and used without any RS-side NBT files being present at build time. The CI workflow builds the jar and the datapack zip as independent artifacts.
2. **Loader neutrality.** The datapack is pure JSON + NBT — it works on Forge, NeoForge, Fabric, and Quilt without per-loader variants.
3. **Iterability.** You can swap petshop NBTs in your world's `datapacks/` folder without rebuilding or replacing the CTOV jar. Drop in a new `rs_di_compat-<version>.zip`, run `/reload`, and the next village gen uses your new NBTs.

The fork's main jar provides the **engine** (`ctov:petshop_compat` element type + spawn profiles). This datapack provides the **RS-side wiring** (pool additions + NBTs). They're orthogonal — you can use the fork's jar without this datapack (CTOV villages still get petshops), and you can use this datapack without DI installed (petshops generate but chest stays empty).
