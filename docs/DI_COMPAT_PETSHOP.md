# Domestication Innovation × CTOV — Petshop Compat

This fork of **ChoiceTheorem's Overhauled Village (CTOV)** adds native support for
**Domestication Innovation (DI)** petshop structures in every CTOV village variant
on **Minecraft 1.20.1 / Forge**.

With this compat layer installed, CTOV villages will:

1. Actually contain DI-style petshop buildings (one per village, when the pool rolls one).
2. Spawn biome-appropriate pets inside the cages (rather than leaving them empty).
3. Stock the petshop chest with DI's loot table (when DI is installed).
4. Fill the fishtank with biome-appropriate aquatic mobs and underwater decorations.

The implementation is **per-biome configurable** — every CTOV village variant maps to a
named spawn profile, and you can override any profile by dropping a JSON file into a
datapack at the same path.

---

## Why this is needed

CTOV v3.3.1 ships 21 petshop structure NBTs (one per village variant) as part of a
separate `ctov-domestication-innovation-add-on` datapack. Those NBTs contain data-marker
structure blocks (`petshop_water`, `petshop_chest`, `petshop_cage_0..3`) that DI's own
`PetshopStructurePoolElement` is supposed to process — turning the markers into real mobs,
loot, and decorations.

The problem: CTOV's `house.json` pool files declared the petshop entries as
`minecraft:single_pool_element`. That element type **does not** process data markers —
so the cages ended up empty, the chest stayed empty, and the fishtank stayed dry.

DI's own `PetshopStructurePoolElement` only injects itself into the five vanilla village
pools (`minecraft:village/<biome>/houses`) via Citadel's `VillageHouseManager`. Because
CTOV **replaces** vanilla villages with its own biome-specific pools
(`ctov:village/<biome>/house`), DI's injection never fires for CTOV villages.

This fork closes that gap by:

- Registering a new pool element type, `ctov:petshop_compat`, that extends
  `LegacySinglePoolElement` and overrides `handleDataMarker` to do the same job as DI's
  class.
- Switching all 21 CTOV `house.json` petshop entries from `minecraft:single_pool_element`
  to `ctov:petshop_compat` (with a `biome_profile` field that selects the spawn table).
- Bundling the 21 petshop NBTs into the main jar so the mod is self-contained — no
  separate datapack required.
- Reading per-biome spawn profiles from `data/ctov/petshop_spawns/<biome>.json` instead of
  DI's five global entity-type tags, so a plains petshop can stock different pets than a
  snowy petshop.

---

## Installation

**Required:**

- Minecraft 1.20.1
- Forge 47.2.0 (or any 47.x — Forge 47 is the 1.20.1 line)
- ChoiceTheorem's Overhauled Village — this fork's jar (replaces the upstream CTOV jar)

**Recommended:**

- [Domestication Innovation](https://www.curseforge.com/minecraft/mc-mods/domestication-innovation)
  1.7.0 or newer — required only for the petshop chest loot table. Without DI installed,
  petshops will still spawn entities and fishtank decorations; only the chest will be
  empty.

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
| `entity`   | string  | yes      | —           | A registered entity ID, e.g. `minecraft:wolf` or `some_mod:foo`. Unknown IDs are logged and skipped at spawn time (no crash). |
| `weight`   | int ≥ 1 | yes      | —           | Relative weight. The probability of an entry being picked is `weight / sum(all weights)`. |
| `baby`     | bool    | no       | `false`     | If `true`, the spawned entity is aged down via `AgeableMob.setAge(age)`. No-op for non-`AgeableMob` entities (e.g. parrots, bats). |
| `age`      | int     | no       | `-24000`    | Only used when `baby: true`. **Negative = baby; more negative = longer baby duration.** `Integer.MIN_VALUE` (−2147483648) ≈ permanent baby. `0` is NOT baby — it forces immediate adulthood. One Minecraft day = 24000 ticks, so the default `-24000` means "baby for one day". |

### Important: how `age` actually works

The `age` field is passed directly to `AgeableMob.setAge(int)`. Minecraft's internal
contract is:

- **Positive** → adult, with a countdown timer until breeding is allowed again (positive
  ticks).
- **Zero** → adult, ready to breed.
- **Negative** → baby. The magnitude is how many ticks the mob stays a baby before growing
  up. `-24000` = 1 in-game day. `-72000` = 3 in-game days.

So:

- `"baby": true, "age": -24000` → baby for 1 day (default)
- `"baby": true, "age": -72000` → baby for 3 days
- `"baby": true, "age": -2147483648` → effectively permanent baby (won't grow up in any
  reasonable playtime)
- `"baby": true, "age": 0` → instantly grows up (baby flag is immediately overridden by
  the age=0 adulthood state). Don't do this — just omit `baby` instead.

The `baby: true` flag is a convenience that mirrors the natural-language meaning, but the
actual lifetime is governed entirely by `age`.

### Overriding profiles via datapack

To customize a profile, create a datapack with a file at
`data/ctov/petshop_spawns/<profile>.json` containing your replacement entries. The
profile is reloaded automatically when datapacks are reloaded (`/reload`).

You can also add **new** profiles this way and reference them from a custom `house.json`
pool entry by setting `biome_profile` to your new profile name.

---

## Biome-to-profile mapping

The 21 CTOV v3.3.1 village variants map onto 14 named profiles. The merging was chosen
to keep biomes with thematic overlap together while preserving biome distinctions the
community cared about.

| Profile       | CTOV biome variants                                  | Notes |
|---------------|------------------------------------------------------|-------|
| `plains`      | plains, plains_fortified                             | |
| `desert`      | desert, desert_oasis                                 | |
| `snowy`       | snowy_igloo, christmas                               | Christmas merged into snowy per user request. |
| `savanna`     | savanna, savanna_na                                  | |
| `badlands`    | mesa, mesa_fortified                                 | |
| `beach`       | beach                                                | |
| `jungle`      | jungle, jungle_tree                                  | Kept separate per user request. |
| `mountain`    | mountain, mountain_alpine                            | |
| `mushroom`    | mushroom                                             | |
| `swamp`       | swamp, swamp_fortified                               | Kept separate per user request. |
| `forest`      | taiga, taiga_fortified, halloween                    | Halloween merged into forest per user request. **Reserved** for future mods that add forest-village biomes (oak/birch/dark-forest). |
| `underground` | (no petshop in CTOV v3.3.1)                          | Profile reserved for completeness; will activate if a future CTOV build adds an underground petshop. |
| `fishtank`    | (shared across all biomes — used by `petshop_water`) | Not a biome profile; selected automatically for the fishtank marker. |
| `cherry`      | (no CTOV v3.3.1 village)                             | **Reserved** for future mods that add cherry-blossom villages. |

---

## How a petshop actually gets built

When a CTOV village generates and the village's `house.json` pool rolls a petshop entry,
this is what happens, in order:

1. **Pool element selection.** The pool entry's `element_type` is `ctov:petshop_compat`,
   so the Forge registry instantiates a `PetshopCompatStructurePoolElement` with the
   `biome_profile` field from the JSON.
2. **NBT placement.** The element places the biome's `petshop.nbt` structure file into
   the world, just like `single_pool_element` would. The structure contains
   `minecraft:structure_block` markers with data-mode NBT.
3. **Marker processing.** Once the structure is placed, the game calls
   `handleDataMarker(...)` for each marker. Our override dispatches on the marker's
   `metadata` string:
   - `petshop_water` → spawns 1–2 entities from the `fishtank` profile, then replaces the
     marker with either water, seagrass, or a waterlogged coral (randomized 50% / 25% /
     25%). Mirrors DI's fishtank décor.
   - `petshop_chest` → clears the marker block, then binds the block below it to DI's
     `domesticationinnovation:chests/petshop_chest` loot table. If DI is not installed,
     the chest is simply empty (no crash).
   - `petshop_cage_*` → **any marker whose name starts with `petshop_cage`** spawns
     exactly **1** entity from the active biome profile. Cage density (how many mobs
     appear in a single cage) is controlled by how many `petshop_cage_*` markers the
     structure .nbt places inside that cage — not by a random roll in code. This avoids
     entity clipping in small 1-block cages and gives .nbt builders direct control over
     per-cage pet count.
4. **Entity finalization.** Each spawned entity is given persistence (so it doesn't
   despawn), has `MobSpawnType.STRUCTURE` finalized, and has its age applied (if `baby:
   true` was set in the profile entry).

The fishtank decoration RNG mirrors DI's reference implementation so the visual result
is identical to a vanilla DI petshop.

---

## Soft-dependency on DI

This compat is a **soft dependency** on DI — declared in `mods.toml` as
`mandatory=false`. The mod loads and works without DI installed; you just won't get
the chest loot table.

This is intentional: it lets users run CTOV alone with biome-themed petshops as a
standalone feature, and it lets modpack makers opt into the full DI experience by simply
adding DI to the mod list.

---

## In-game testing without spawning a village

Testing the petshop used to require finding a CTOV village in survival or generating one
via `/locate`. With the bundled **test template pools**, you can spawn a single,
fully-populated petshop at your feet using the
[CommandStructures](https://www.curseforge.com/minecraft/mc-mods/commandstructures)
mod by telepathicgrunt.

### Setup

Install CommandStructures alongside CTOV + DI (all three mods loaded). The test pools are
shipped inside the CTOV jar itself — no extra datapack required.

### Spawn one petshop

The test pools live under the resource path `ctov:village/test/petshop/<biome>` — one per
CTOV village variant (21 total). Each pool contains exactly one element: the petshop .nbt
for that biome, wired up with `ctov:petshop_compat` so the cages, chest, and fishtank are
all populated just like they would be in a real village.

Run CommandStructures' `/spawnstructure` command with **depth 0** so only the start piece
generates (no jigsaw branching):

```
/spawnstructure ~ ~ ~ ctov:village/test/petshop/<biome> 0 false false false false
```

Argument breakdown (after the pool ID):

| # | Arg | Value | Why |
|---|-----|-------|-----|
| 3 | depth | `0` | Only the petshop piece — no child jigsaw pieces. |
| 4 | heightmapsnap | `false` | Spawn at the player's exact Y, not snapped to terrain. |
| 5 | legacyboundingboxrule | `false` | This is required `true` for full villages; for a single piece, `false`. |
| 6 | disableprocessors | `false` | Keep the detailing processors (moss, cracking, biome tinting) so the test reflects production. |
| 7 | sendchunklightingpacket | `false` | Relight chunks naturally — fine for static structures like the petshop. |

### Examples

Spawn the plains petshop at your feet (cages will contain cows, sheep, pigs, chickens;
chest will have DI loot; fishtank will have tropical fish):

```
/spawnstructure ~ ~ ~ ctov:village/test/petshop/plains 0 false false false false
```

Spawn the swamp variant (cages will have slimes, witches, cats):

```
/spawnstructure ~ ~ ~ ctov:village/test/petshop/swamp 0 false false false false
```

Spawn a fortified variant — these share a biome profile with their non-fortified
counterpart, so the **spawns will be identical**; only the building geometry differs:

```
/spawnstructure ~ ~ ~ ctov:village/test/petshop/swamp_fortified 0 false false false false
```

### Available test pools

All 21 village variants have a test pool — substitute any of these for `<biome>` above:

| Profile | Variants (test pool IDs) |
|---------|--------------------------|
| plains | `plains`, `plains_fortified` |
| desert | `desert`, `desert_oasis` |
| snowy | `snowy_igloo`, `christmas` |
| savanna | `savanna`, `savanna_na` |
| badlands | `mesa`, `mesa_fortified` |
| beach | `beach` |
| jungle | `jungle`, `jungle_tree` |
| mountain | `mountain`, `mountain_alpine` |
| mushroom | `mushroom` |
| swamp | `swamp`, `swamp_fortified` |
| forest | `taiga`, `taiga_fortified`, `halloween` |

### What spawns in each petshop

The `biome_profile` field on each test pool entry selects the spawn table from
`data/ctov/petshop_spawns/<profile>.json` — see the **Per-biome spawn profiles** section
above for the full list of mobs per profile. The cage counts per marker are fixed by
`PetshopCompatStructurePoolElement.handleDataMarker()`:

- `petshop_cage_*` → exactly 1 mob per marker (cage density is controlled by how many markers the .nbt places per cage)
- `petshop_water` → 2 fish + seagrass/coral decoration
- `petshop_chest` → DI loot table (only when DI is installed)

### Why `/spawnpieces` won't work for this

CommandStructures also has a `/spawnpieces` command that places a raw .nbt file directly.
**Don't use it for testing the DI compat** — `/spawnpieces` bypasses the jigsaw pool
system, so the .nbt gets placed by vanilla's `single_pool_element` logic, which does not
process the `petshop_*` data markers. You'd get the building geometry (walls, cages,
water, chest) but cages would be empty, chest would have no loot, and the fishtank would
have no fish. `/spawnstructure` is the right tool because it runs the jigsaw pool system,
which fires `ctov:petshop_compat`'s `handleDataMarker` override.

### Isolation

The 21 test pools live under `data/ctov/worldgen/template_pool/village/test/petshop/` and
are **never referenced by any village's `house.json`**. They exist solely as targets for
CommandStructures' `/spawnstructure` command. Normal village generation is completely
unaffected — villages will continue to roll petshops from their patched `house.json`
files exactly as before.

---

## File layout (reference)

```
src/main/
├── java/com/choicetheorem/ctov/
│   ├── ctov.java                                  ← mod entry; registers pool-element DeferredRegister
│   ├── registry/
│   │   └── CTOVRegistry.java                      ← DeferredRegister for ctov:petshop_compat
│   └── worldgen/
│       ├── PetshopCompatStructurePoolElement.java ← the pool element class (handleDataMarker dispatch)
│       └── PetshopSpawnProfile.java               ← JSON schema + weighted-pick helper
└── resources/data/ctov/
    ├── petshop_spawns/                            ← 14 spawn profile JSONs
    │   ├── plains.json
    │   ├── desert.json
    │   ├── snowy.json
    │   ├── savanna.json
    │   ├── badlands.json
    │   ├── beach.json
    │   ├── jungle.json
    │   ├── mountain.json
    │   ├── mushroom.json
    │   ├── swamp.json
    │   ├── forest.json
    │   ├── underground.json
    │   ├── fishtank.json
    │   └── cherry.json
    ├── structures/village/<biome>/jobsite/
    │   └── petshop.nbt                            ← 21 NBTs (moved out of the add-on datapack)
    └── worldgen/template_pool/village/
        ├── <biome>/house.json                     ← 21 patched pool files (petshop entries now use ctov:petshop_compat)
        └── test/petshop/<biome>.json              ← 21 isolated test pools for CommandStructures /spawnstructure
```

The `src/main/ctov-domestication-innovation-add-on/` datapack directory is left untouched
for backward compatibility — if a user still has the old datapack installed, its NBTs
will simply override the jar's NBTs (identical content), and the patched `house.json`
files will pick them up the same way.
