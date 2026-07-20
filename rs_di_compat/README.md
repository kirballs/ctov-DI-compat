# Repurposed Structures — DI Petshop Compat Datapack

This datapack adds Domestication Innovation petshop buildings to all 11 Repurposed Structures village variants, using the `ctov:petshop_compat` pool element from the CTOV-DI-compat fork.

## Status

**Pool entries and spawn profiles: done.** The JSON files are ready.

**Petshop NBTs: TODO — you need to create these.**

Each village needs a petshop NBT file at:
```
data/ctov/structures/villages/<biome>/petshop.nbt
```

where `<biome>` is one of: `badlands`, `bamboo`, `birch`, `cherry`, `dark_forest`, `giant_taiga`, `jungle`, `mountains`, `mushroom`, `oak`, `swamp`.

## Two namespaces, on purpose

This datapack uses **two different namespaces** for two different things. Don't confuse them:

| Asset | Namespace | Why |
|---|---|---|
| Pool additions (which house pool gets a petshop entry injected) | `repurposed_structures:` | RS's own pool-additions system reads JSONs from `data/repurposed_structures/pool_additions/...`. That's RS's contract. |
| The structure NBT files themselves | `ctov:` | They're loaded by the `ctov:petshop_compat` element type registered by the CTOV-DI-compat fork. The fork's own petshop NBTs live under `ctov:`, so we keep RS petshops in the same namespace for consistency. |

When you save a structure block, the **Name** field is the resource ID Minecraft writes the file under. So `ctov:villages/bamboo/petshop` lands at `data/ctov/structures/villages/bamboo/petshop.nbt` — which matches the `location` field in `houses.json`. Using `repurposed_structures:` for the save name would write to the wrong folder and the building would never generate.

## How to create the NBTs

### The marker system (read this first)

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

### Step 1 — Start a creative world

Create a new flat creative world with cheats enabled. You need:
- **CTOV-DI-compat fork** installed (registers `ctov:petshop_compat`)
- **Domestication Innovation** installed (provides the `chests/petshop_chest` loot table)
- **Repurposed Structures** installed (provides the source petshop NBTs you'll adapt)

You do **not** need the CommandStructures mod for this workflow — vanilla `/place template` is sufficient and faster for spawning single pre-baked buildings.

### Step 2 — Spawn an RS petshop as your starting point

RS ships its own petshop NBTs for each village biome. Load one into the world with vanilla `/place template`:

```
/place template repurposed_structures:villages/<biome>/petshop ~ ~ ~
```

For example:
```
/place template repurposed_structures:villages/bamboo/petshop ~ ~ ~
```

This places one building at your position. No jigsaw resolution happens, so the cage jigsaws and any leftover structure blocks from RS's build process will be visible inside the building — that's expected, you'll clean them up in the next step.

### Step 3 — Clean up the old RS markers (IMPORTANT)

RS petshops use a stale, pre-baked pet system: each cage has a **jigsaw block** that targets a pool like `repurposed_structures:villages/pets_glass_cage`, which resolves at village-gen time to a sub-NBT with literal entities baked in (one dog, two cats, etc.). There may also be leftover RS structure blocks with metadata our element type doesn't recognise.

**You must break ALL of these and replace them with our markers.** If you leave them, two things go wrong:
- Leftover jigsaws resolve during village gen and re-introduce the stale NBTs alongside your marker-spawned pets — duplicate animals.
- Leftover RS structure blocks hit the `default` case in `handleDataMarker` and just get cleared to air (no spawn, no crash, but wasted space).

#### Per-cage cleanup

For each cage in the building:

1. Find every block inside the cage volume that is a **jigsaw block** or a **structure block**.
2. **Break all of them.**
3. Place **one** new structure block on the cage floor, centered (where you want pets to stand — not on a wall, not floating, not below the floor).
4. Open the structure block UI:
   - **Mode:** Data
   - **Custom Data Name (metadata):** `petshop_cage_1` (for cages that should hold 2–3 pets — your standard choice) or `petshop_cage_3` (for 1-block cages where only 1 pet fits)
5. Repeat for every cage. Multiple cages can all use `petshop_cage_1` — the number is a spawn-count selector, not a unique ID.

You do not need to use `petshop_cage_0` or `petshop_cage_2`. You do not need more than one structure block per cage.

#### Fishtank cleanup (if the building has one)

RS petshops have a jigsaw block targeting `repurposed_structures:villages/pets_fish` for the fishtank. Same treatment:

1. Break that jigsaw block.
2. Place one Data-mode structure block at the position where the water surface should be.
3. Metadata: `petshop_water`

The code will spawn 2 fishtank mobs there and replace the marker with water/seagrass/coral at gen time.

#### Chest cleanup

RS petshops may have a chest without any marker above it (RS uses its own loot system, not DI's). To get DI's `petshop_chest` loot table:

1. Find the chest block.
2. Place one Data-mode structure block **directly above** the chest (at `chest_pos + 1` on the Y axis).
3. Metadata: `petshop_chest`

The code clears the marker block (turns it to air) and binds the chest below to `domesticationinnovation:chests/petshop_chest`. Make sure there's nothing important above the chest, because that block will become air.

If the chest already has a structure block above it (from RS or DI), break that one and place your own with `petshop_chest` metadata — don't trust the existing one.

#### Jigsaw blocks outside cages

The building might have a front-door jigsaw block (targeting the village's streets/houses pool to connect to the path). **Break that too.** Our `ctov:petshop_compat` element type places the NBT directly and doesn't process jigsaw blocks, so any jigsaw left in the NBT would just sit there as a visible jigsaw block in the generated village. CTOV fork's own petshop NBTs don't have any jigsaw blocks, so yours shouldn't either.

### Step 4 — (Optional) Adapt the building blocks to the biome

If you started from an RS petshop that doesn't perfectly match the target biome (e.g. you started from `repurposed_structures:villages/birch/petshop` but want to use it for `oak`), swap the wood, planks, and terrain blocks to match the target biome's materials. This is purely cosmetic — the markers don't care about the building's blocks.

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

### Step 7 — Verify

Quick structural check:
1. The `.nbt` exists at the expected path.
2. The filename is `petshop.nbt` (lowercase, no `.nbt.nbt`).
3. The biome folder name matches one of the 11 biomes listed at the top.

For full in-world verification, find an RS village of each biome and check that petshop buildings spawn with pets — but that's a final integration test, not a per-NBT check.

## Common pitfalls

- **Save name with `repurposed_structures:`** → file lands in the wrong namespace, the `houses.json` `location: ctov:...` won't find it, petshop never generates. Always use `ctov:`.
- **Save name using `village/` (singular)** instead of `villages/` (plural) → file lands at `data/ctov/structures/village/<biome>/petshop.nbt` instead of `data/ctov/structures/villages/<biome>/petshop.nbt`, the `location` field won't find it. Always use `villages/` (plural) to match the `location` field in your JSONs.
- **Forgetting to remove the load-mode structure block** you used to spawn the building (if you used `/place template` this isn't an issue — vanilla `/place template` doesn't leave a structure block behind — but if you loaded via a structure block in Load mode, break it before saving).
- **Including entities ON** → any mobs wandering around the building (e.g. randomly spawned sheep) get baked into the NBT and will duplicate the marker-spawned ones. Always leave it OFF.
- **Leaving RS jigsaw blocks in cages** → stale pre-baked pets spawn alongside your marker-spawned pets. Break them all.
- **Structure block placed on a wall or ceiling** → pets spawn in the wrong spot (inside a wall, or falling from the ceiling). Always place cage markers on the cage floor.

## Biome → Spawn Profile Mapping

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

Edit the spawn profile JSON at `data/ctov/petshop_spawns/<profile>.json` in the CTOV fork. Add entries like:
```json
{ "entity": "some_mod:cool_pet", "weight": 10, "baby": true, "age": -24000 }
```
Unknown entity IDs are safely skipped at spawn time (no crash).
