# Internal mapping: CTOV biome → petshop spawn profile

This is a maintainer-facing reference. For user-facing docs, see
[`DI_COMPAT_PETSHOP.md`](./DI_COMPAT_PETSHOP.md).

## Mapping table

| CTOV biome directory (`village/<biome>/`) | `biome_profile` value | Spawn profile JSON | Notes |
|-------------------------------------------|------------------------|--------------------|-------|
| `plains`              | `plains`     | `data/ctov/petshop_spawns/plains.json`     | |
| `plains_fortified`    | `plains`     | (same)                                     | |
| `desert`              | `desert`     | `data/ctov/petshop_spawns/desert.json`     | |
| `desert_oasis`        | `desert`     | (same)                                     | |
| `snowy_igloo`         | `snowy`      | `data/ctov/petshop_spawns/snowy.json`      | |
| `christmas`           | `snowy`      | (same)                                     | Merged per user request. |
| `savanna`             | `savanna`    | `data/ctov/petshop_spawns/savanna.json`    | |
| `savanna_na`          | `savanna`    | (same)                                     | |
| `mesa`                | `badlands`   | `data/ctov/petshop_spawns/badlands.json`   | |
| `mesa_fortified`      | `badlands`   | (same)                                     | |
| `beach`               | `beach`      | `data/ctov/petshop_spawns/beach.json`      | |
| `jungle`              | `jungle`     | `data/ctov/petshop_spawns/jungle.json`     | Kept separate per user request. |
| `jungle_tree`         | `jungle`     | (same)                                     | |
| `mountain`            | `mountain`   | `data/ctov/petshop_spawns/mountain.json`   | |
| `mountain_alpine`     | `mountain`   | (same)                                     | |
| `mushroom`            | `mushroom`   | `data/ctov/petshop_spawns/mushroom.json`   | |
| `swamp`               | `swamp`      | `data/ctov/petshop_spawns/swamp.json`      | Kept separate per user request. |
| `swamp_fortified`     | `swamp`      | (same)                                     | |
| `taiga`               | `forest`     | `data/ctov/petshop_spawns/forest.json`     | CTOV 1.20.1 has no forest village — `forest` profile maps to taiga. Also covers future forest-village mods. |
| `taiga_fortified`     | `forest`     | (same)                                     | |
| `halloween`           | `forest`     | (same)                                     | Merged per user request. |
| `underground`         | (no petshop in CTOV v3.3.1) | `data/ctov/petshop_spawns/underground.json` | Profile reserved; not currently referenced by any `house.json`. |
| `fishtank`            | (shared, not a biome)       | `data/ctov/petshop_spawns/fishtank.json`   | Hard-coded in `PetshopCompatStructurePoolElement.handleWater()` — not selectable via `biome_profile`. |
| `cherry`              | (no CTOV v3.3.1 village)    | `data/ctov/petshop_spawns/cherry.json`     | Profile reserved for future mods. |

## CTOV biome directory → `house.json` petshop entry count

These are the actual counts found in CTOV v3.3.1's `house.json` files. Counts >1 mean the
petshop structure can be selected multiple times (different NBT variants or different
weights).

```
beach:           1 petshop entry
christmas:       1
desert:          1
desert_oasis:    1
halloween:       1
jungle:          1
jungle_tree:     1
mesa:            1
mesa_fortified:  1
mountain:        1
mountain_alpine: 1
mushroom:        2
plains:          1
plains_fortified: 3
savanna:         3
savanna_na:      1
snowy_igloo:     2
swamp:           1
swamp_fortified: 1
taiga:           1
taiga_fortified: 1
```

Total: 26 petshop entries across 21 biome `house.json` files. All 26 were swapped from
`minecraft:single_pool_element` to `ctov:petshop_compat`.

## Code flow (quick reference)

```
ctov.java
  └─ constructor registers CTOVRegistry.POOL_ELEMENTS (DeferredRegister<StructurePoolElementType<?>>)
       on the mod event bus.

CTOVRegistry
  └─ PETSHOP_COMPAT_POOL_ELEMENT → "ctov:petshop_compat" → PetshopCompatStructurePoolElement.CODEC

PetshopCompatStructurePoolElement extends LegacySinglePoolElement
  ├─ Codec adds a 4th field "biome_profile" (string) to the standard 3-field
  │   LegacySinglePoolElement codec (template, processors, projection).
  ├─ handleDataMarker() dispatches on the marker's "metadata" string:
  │     petshop_water    → handleWater() → spawns from "fishtank" profile, places water/seagrass/coral
  │     petshop_chest    → handleChest()  → clears marker, binds chest-below to DI's loot table
  │     petshop_cage*    → handleCage(count = 1) — matches any marker starting with "petshop_cage"
  │                          Cage density is controlled by how many markers the NBT places per cage.
  ├─ handleCage() calls spawnFromProfile(biomeProfile) which:
  │     1. Looks up cached profile via ProfileLoader.get(profile)
  │     2. Rolls weighted entry from profile.entries
  │     3. Resolves EntityType via ForgeRegistries.ENTITY_TYPES
  │     4. Creates entity, sets position/rotation
  │     5. If Mob: setPersistenceRequired + finalizeSpawn(STRUCTURE)
  │     6. If baby=true and AgeableMob: setAge(entry.age) — applied AFTER finalizeSpawn
  │     7. serverLevel.addFreshEntityWithPassengers(entity)
  └─ ReloadListener (Forge bus) hooks AddReloadListenerEvent to invalidate the cache on /reload.
```

## Why `age` semantics are documented loudly

The `age` field in the spawn profile JSON is passed verbatim to
`AgeableMob.setAge(int)`. Mojang's contract:

- `age > 0`  → adult, breeding cooldown (in ticks).
- `age == 0` → adult, can breed.
- `age < 0`  → baby for `|age|` ticks. After that, grows up.

So `"baby": true, "age": 0` is a footgun — the `baby: true` flag is overridden by the
`age = 0` adulthood state, and the mob spawns as an adult anyway.

The default `-24000` (= 1 Minecraft day) was chosen to match the natural "stays a baby
for about a day" expectation that most users have for cage-display pets. For an
"effectively permanent" baby, use `-2147483648` (Integer.MIN_VALUE) — that's about 3.7
real-time years at 20 TPS, which no server will ever reach in a single session.

The default value is `Entry.DEFAULT_AGE = -24000` in `PetshopSpawnProfile.java`.

## Repurposed Structures biome mapping (rs_di_compat datapack)

| RS village biome | `biome_profile` | Spawn profile JSON |
|---|---|---|
| `badlands` | `badlands` | `data/ctov/petshop_spawns/badlands.json` |
| `bamboo` | `bamboo` | `data/ctov/petshop_spawns/bamboo.json` |
| `birch` | `forest` | `data/ctov/petshop_spawns/forest.json` |
| `cherry` | `cherry` | `data/ctov/petshop_spawns/cherry.json` |
| `dark_forest` | `forest` | (same) |
| `giant_taiga` | `forest` | (same) |
| `jungle` | `jungle` | `data/ctov/petshop_spawns/jungle.json` |
| `mountains` | `mountain` | `data/ctov/petshop_spawns/mountain.json` |
| `mushroom` | `mushroom` | `data/ctov/petshop_spawns/mushroom.json` |
| `oak` | `forest` | (same) |
| `swamp` | `swamp` | `data/ctov/petshop_spawns/swamp.json` |

## Adding a new biome profile (for future mods)

To wire up a new mod that adds a new village biome (e.g. `some_mod:cherry_grove_village`)
to use this compat:

1. Create `data/ctov/petshop_spawns/cherry.json` (or whatever name you want). Use the
   schema documented in `DI_COMPAT_PETSHOP.md`.
2. In the new village's `house.json` pool file, add an entry like:
   ```json
   {
     "weight": 5,
     "element": {
       "element_type": "ctov:petshop_compat",
       "projection": "rigid",
       "location": "some_mod:village/cherry/jobsite/petshop",
       "processors": "minecraft:empty",
       "biome_profile": "cherry"
     }
   }
   ```
3. Ship the structure NBT at `data/some_mod/structures/village/cherry/jobsite/petshop.nbt`
   with the same data-marker conventions (`petshop_water`, `petshop_chest`,
   `petshop_cage*`). Each `petshop_cage*` marker spawns exactly one entity;
   place multiple markers inside larger cages for multi-pet cages.

No Java code changes needed — the codec and dispatch are generic.

## Files NOT modified

These directories in the CTOV source tree are intentionally left untouched:

- `src/main/ctov-domestication-innovation-add-on/` — original DI add-on datapack. Kept
  for backward compat; its NBTs are identical to those now shipped in the main jar.
- `src/main/ctov-*-add-on/` — all other CTOV add-on datapacks. Not in scope.
- All non-petshop entries in `house.json` files. Only petshop entries were swapped.
- All non-`house.json` pool files (e.g. `decos.json`, `terminators.json`, `streets.json`).
- The Forge `build.gradle`, `settings.gradle`, and `gradle.properties` — only the
  `mod_version` was bumped from `3.3.0` to `3.3.1-di-petshop-compat`. All other build
  settings match CTOV v3.3.1 exactly.
