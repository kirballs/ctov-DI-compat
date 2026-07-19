# Internal mapping: CTOV biome → petshop spawn profile (3.4.14 port)

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
| `taiga`               | `forest`     | `data/ctov/petshop_spawns/forest.json`     | CTOV 1.20.x has no forest village — `forest` profile maps to taiga. Also covers future forest-village mods. |
| `taiga_fortified`     | `forest`     | (same)                                     | |
| `halloween`           | `forest`     | (same)                                     | Merged per user request. |
| `dark_forest`         | `forest`     | (same)                                     | New in 3.4.14 — modifier JSON exists; profile follows the same forest mapping. |
| `underground`         | (no petshop in CTOV 3.4.14 modifier set) | `data/ctov/petshop_spawns/underground.json` | Profile reserved; not currently referenced by any modifier JSON. |
| `fishtank`            | (shared, not a biome)       | `data/ctov/petshop_spawns/fishtank.json`   | Hard-coded in `PetshopCompatStructurePoolElement.handleWater()` — not selectable via `biome_profile`. |
| `cherry`              | (no CTOV 3.4.14 village)    | `data/ctov/petshop_spawns/cherry.json`     | Profile reserved for future mods. |

## CTOV biome directory → Lithostitched modifier JSON petshop entry count

These are the actual counts found in CTOV v3.4.14's DI modifier JSONs at
`common/src/main/resources/data/ctov/lithostitched/worldgen_modifier/domesticationinnovation/<biome>.json`.
Counts >1 mean the petshop structure can be selected multiple times with different
processor lists (e.g. snowy has both ice and snow house variants).

```
beach:           1 petshop entry
christmas:       1
dark_forest:     1   (new in 3.4.14)
desert:          1
desert_oasis:    1
jungle:          1
jungle_tree:     1
mesa:            1
mesa_fortified:  1
mountain:        1
mountain_alpine: 1
mushroom:        2   (house_brown + house_red processor variants)
plains:          1
plains_fortified:1
savanna:         3   (house_orange + house_red + house_yellow variants)
savanna_na:      1
snowy:           2   (house_ice + house_snow variants)
swamp:           1
swamp_fortified: 1
taiga:           1
taiga_fortified: 1
halloween:       (no separate modifier JSON — not present in 3.4.14's DI set)
```

Total: 21 modifier JSONs, 25 element entries patched. Each element flipped from
`minecraft:single_pool_element` to `ctov:petshop_compat` and given a `biome_profile`
field per the table above.

## Code flow

```
[Lithostitched loads modifier JSON at server start]
    ↓
[Player triggers village generation — chunk load or /locate]
    ↓
[Jigsaw pieces are rolled; ctov:petshop_compat element is selected for petshop slots]
    ↓
[PetshopCompatStructurePoolElement.getSettings(...) is called — same as
 LegacySinglePoolElement, just inherits the vanilla placement logic]
    ↓
[After the .nbt is placed, vanilla calls handleDataMarker(...) once per
 structure-block marker in the .nbt]
    ↓
[PetshopCompatStructurePoolElement.handleDataMarker(...) dispatches on the
 metadata string: petshop_water / petshop_chest / petshop_cage_0..3]
    ↓
[For cage markers: loadProfile(biomeProfile, levelAccessor) reads the JSON
 profile via the server's ResourceManager. pickEntity(random) does a
 weighted roll against the profile's entries. The chosen EntityType is
 created, positioned, finalizeSpawn'd, then setAge()'d if the entry's
 baby flag is true.]
    ↓
[For petshop_water: same flow as cages but using the hard-coded
 FISHTANK_PROFILE_ID ("ctov:fishtank"), then place water/seagrass/coral.]
    ↓
[For petshop_chest: clear the marker block to air, then bind the chest
 below to domesticationinnovation:chests/petshop_chest (defensive — chest
 loot already resolves via CTOV's existing .nbt setup when DI is loaded).]
```

## Age semantics deep-dive

`Mob.setAge(int)` follows `AgeableMob.setAge(int)` semantics:

- `age < 0`  → baby. The mob is rendered at half scale and (for breedable mobs) cannot
  breed until the age counter reaches 0.
- `age == 0` → adult. The mob is rendered at full scale and can breed.
- `age > 0`  → adult, with a breeding cooldown of `age` ticks.
- `Integer.MIN_VALUE` → baby forever (the counter never reaches 0 because it's
  decremented by 1 per tick, and `Integer.MIN_VALUE + 1` is still negative).

So:
- `{ "baby": true, "age": -24000 }` — baby for 1 in-game day (24000 ticks = 20 min real
  time). The default.
- `{ "baby": true, "age": -2147483648 }` — permanent baby. Good for "always puppy" pets.
- `{ "baby": true, "age": 0 }` — **wrong**. The `baby: true` flag is meaningless here;
  `setAge(0)` makes the mob an adult immediately.
- `{ "baby": false }` (or omit `baby`) — adult. `age` defaults to 0 (no breeding
  cooldown).

The `ageFor(Entry)` helper in `PetshopSpawnProfile` enforces this: if `baby` is true and
`age >= 0`, it overrides `age` to `-24000` (the safe default). If `baby` is true and
`age < 0`, it uses the provided `age` as-is. If `baby` is false, `age` is ignored.

Age is applied **after** `Mob.finalizeSpawn(...)`. finalizeSpawn resets the mob's age to
0 (adult, no cooldown) in many cases, so applying age before finalizeSpawn would be a
no-op.

## How to add a new biome profile

1. Pick a profile id (e.g. `crimson_forest`).
2. Create
   `common/src/main/resources/data/ctov/petshop_spawns/crimson_forest.json` with the
   standard schema.
3. Add a Lithostitched modifier JSON that injects a petshop element with
   `biome_profile: "ctov:crimson_forest"` into the desired village pool.
4. No Java code changes needed — the profile is loaded by id at worldgen time via
   `PetshopCompatStructurePoolElement.loadProfile(...)`.

## How to add a new village variant that uses an existing profile

Just create a new Lithostitched modifier JSON at
`common/src/main/resources/data/ctov/lithostitched/worldgen_modifier/domesticationinnovation/<biome>.json`
with the same structure as the existing ones, picking an existing profile id for the
`biome_profile` field.

## Per-platform registry details

Each platform's mod entry point publishes its registered `StructurePoolElementType` to
the common `PetshopCompatRegistries` accessor:

- **Forge** (`ctovForge` constructor): `PetshopCompatForgeRegistry.POOL_ELEMENTS.register(modBus)`
  + `PetshopCompatForgeRegistry.init()`. Uses `net.minecraftforge.registries.DeferredRegister`
  against `Registries.STRUCTURE_POOL_ELEMENT`.
- **NeoForge** (`ctovNeo` constructor): `PetshopCompatNeoRegistry.POOL_ELEMENTS.register(modBus)`
  + `PetshopCompatNeoRegistry.init()`. NeoForge 20.2.x still uses
  `net.minecraftforge.registries.DeferredRegister` — the package rename to
  `net.neoforged.neoforge.registries.*` happened in NeoForge 20.4+.
- **Fabric** (`ctovFabric.onInitialize`): `PetshopCompatFabricRegistry.init()`. Uses
  `BuiltInRegistries.register(BuiltInRegistries.STRUCTURE_POOL_ELEMENT, id, type)` —
  Fabric's registry freeze happens after all mod init callbacks, so the type can be
  added directly.
- **Quilt** (`ctovQuilt.onInitialize`): `PetshopCompatQuiltRegistry.init()`. Same as
  Fabric (Quilt is Fabric-compatible).

The common `PetshopCompatStructurePoolElement.getType()` reads from
`PetshopCompatRegistries.PETSHOP_COMPAT_TYPE.get()` — a `Supplier` lookup that defers
the actual registry query until the first worldgen call.

## Comparison to v3.3.1 port

The v3.3.1 port (Forge-only, off tag `v.3.3.1` = commit `a1b36989`) is preserved on the
archive tag `archive/di-petshop-compat-1.20.1-20250719`. Key differences from this port:

- v3.3.1 patched 21 `house.json` template-pool files directly. v3.4.14 patches 21
  Lithostitched modifier JSONs instead — the modifier JSONs are the new injection
  mechanism upstream adopted.
- v3.3.1 was a single Forge `DeferredRegister`. v3.4.14 has 4 platform-specific
  registration classes (Forge + NeoForge `DeferredRegister`, Fabric + Quilt
  `Registry.register`).
- v3.3.1 moved 21 .nbt files from the separate `ctov-domestication-innovation-add-on`
  datapack into the main jar. v3.4.14 doesn't need this — upstream already ships the
  .nbt files at `common/src/main/resources/data/ctov/structures/village/<biome>/jobsite/petshop.nbt`.
- v3.3.1 used package `com.choicetheorem.ctov`. v3.4.14 uses package `net.ctov.petshop`
  (matching the upstream package rename).
