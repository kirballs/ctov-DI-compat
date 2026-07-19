# PR #3: DI petshop compat (MC 1.20.1 / Forge)

> Branch: `di-petshop-compat-1.20.1`
> Base: `master` (on this fork, which itself is rebased off upstream CTOV tag `v.3.3.1`)
> Status: **Draft** — needs local `./gradlew build` with Java 17 to produce the jar (no JDK available in the sandbox used to author this PR).

---

## What this does

This PR adds native Domestication Innovation (DI) petshop support to CTOV villages on
**Minecraft 1.20.1 / Forge**, replacing the previous "petshop NBTs shipped as a separate
datapack but never actually functioned" arrangement with a self-contained, per-biome
implementation.

With this PR, every CTOV village variant that has a petshop entry in its `house.json` pool
will now actually:

- Spawn biome-appropriate pets inside the cages (instead of leaving them empty).
- Fill the fishtank with aquatic mobs + underwater decoration.
- Bind the chest to DI's `domesticationinnovation:chests/petshop_chest` loot table (when
  DI is installed).
- Process the data markers (`petshop_water`, `petshop_chest`, `petshop_cage_0..3`)
  embedded in the structure NBTs.

## Why the previous setup didn't work

CTOV v3.3.1 already ships 21 petshop structure NBTs and references them from the
biome-specific `house.json` pool files — but using `minecraft:single_pool_element` as the
element type. `SinglePoolElement.handleDataMarker()` is a no-op, so the cage markers stay
as `minecraft:structure_block` data blocks (or get cleared to air by jigsaw replacement)
and no entities spawn.

DI's own `PetshopStructurePoolElement` only injects into the **vanilla** village pools
(`minecraft:village/<biome>/houses`) via Citadel's `VillageHouseManager`. Because CTOV
**replaces** those vanilla pools with its own `ctov:village/<biome>/house` pools, DI's
injection never fires for CTOV villages.

## What's in this PR

### New Java code

- `src/main/java/com/choicetheorem/ctov/worldgen/PetshopCompatStructurePoolElement.java`
  — extends `LegacySinglePoolElement`, overrides `handleDataMarker(...)` to dispatch on
  the marker's `metadata` string. Same dispatch table as DI's class
  (`petshop_water` → fishtank spawn + décor, `petshop_chest` → DI loot table binding,
  `petshop_cage_0..3` → biome-profile entity spawn with the same per-cage counts DI uses).
- `src/main/java/com/choicetheorem/ctov/worldgen/PetshopSpawnProfile.java` — codec +
  weighted-pick helper for the `petshop_spawns/<biome>.json` format.
- `src/main/java/com/choicetheorem/ctov/registry/CTOVRegistry.java` — Forge
  `DeferredRegister<StructurePoolElementType<?>>` that registers `ctov:petshop_compat`.
- `src/main/java/com/choicetheorem/ctov/ctov.java` — wires the DeferredRegister onto the
  mod event bus in the constructor.

### New data files

- 14 `data/ctov/petshop_spawns/*.json` files — one per spawn profile (plains, desert,
  snowy, savanna, badlands, beach, jungle, mountain, mushroom, swamp, forest,
  underground, fishtank, cherry).
- The 21 petshop structure NBTs have been **moved** from the
  `src/main/ctov-domestication-innovation-add-on/` datapack directory into
  `src/main/resources/data/ctov/structures/village/<biome>/jobsite/petshop.nbt` so the
  mod is self-contained — no separate datapack required.

### Patched data files

- All 21 `data/ctov/worldgen/template_pool/village/<biome>/house.json` files have had
  their petshop entries swapped from `minecraft:single_pool_element` to
  `ctov:petshop_compat`, with an added `biome_profile` field that selects the spawn
  table. Total: 26 petshop entries across 21 files.

### Docs

- `docs/DI_COMPAT_PETSHOP.md` — user-facing feature documentation, JSON schema,
  installation, biome-to-profile mapping.
- `docs/internal_mapping.md` — maintainer-facing reference for the biome→profile mapping,
  code flow, and how to add new biome profiles for future mods.
- `docs/PR_DESCRIPTION.md` — this file.
- `README.md` — marks Domestication Innovation as integrated (`[✓]`) in the planned-mods
  list and links to the new docs file.

### Metadata changes

- `gradle.properties` — bumped `mod_version` from `3.3.0` to `3.3.1-di-petshop-compat`.
- `src/main/resources/META-INF/mods.toml` — added `domesticationinnovation` as a soft
  dependency (`mandatory=false`, version range `[1.7.0,)`). The mod works without DI;
  DI only provides the petshop chest loot table.

## Biome-to-profile mapping

Per the maintainer's biome-merging instructions, the 21 CTOV v3.3.1 village variants map
onto 14 spawn profiles:

| Profile       | CTOV biome variants                                  | Notes |
|---------------|------------------------------------------------------|-------|
| `plains`      | plains, plains_fortified                             | |
| `desert`      | desert, desert_oasis                                 | |
| `snowy`       | snowy_igloo, christmas                               | Christmas merged into snowy. |
| `savanna`     | savanna, savanna_na                                  | |
| `badlands`    | mesa, mesa_fortified                                 | |
| `beach`       | beach                                                | |
| `jungle`      | jungle, jungle_tree                                  | Kept separate per request. |
| `mountain`    | mountain, mountain_alpine                            | |
| `mushroom`    | mushroom                                             | |
| `swamp`       | swamp, swamp_fortified                               | Kept separate per request. |
| `forest`      | taiga, taiga_fortified, halloween                    | Halloween merged into forest. Reserved for future forest-village mods. |
| `underground` | (no petshop currently)                               | Profile reserved for future use. |
| `fishtank`    | (shared across all biomes)                           | Hard-coded for `petshop_water` marker. |
| `cherry`      | (no CTOV v3.3.1 village)                             | Reserved for future cherry-blossom village mods. |

## JSON schema (for `petshop_spawns/<biome>.json`)

```json
{
  "entries": [
    {
      "entity": "minecraft:wolf",
      "weight": 30,
      "baby": true,
      "age": -24000
    }
  ]
}
```

- `entity` — required. Any registered entity ID. Unknown IDs are logged and skipped at
  spawn time (no crash).
- `weight` — required, int ≥ 1. Probability = `weight / sum(all weights)`.
- `baby` — optional, default `false`. If `true`, applies `AgeableMob.setAge(age)`.
- `age` — optional, default `-24000`. **Negative = baby; more negative = longer baby
  duration.** `Integer.MIN_VALUE` ≈ permanent baby. One Minecraft day = 24000 ticks.

See `docs/DI_COMPAT_PETSHOP.md` for the full schema and override instructions.

## Build instructions

The branch builds against ForgeGradle 6, MC 1.20.1, Forge 47.2.0, Java 17.
No build-system changes were made beyond bumping the MC / Forge coordinates
to align with the actual supported target.

To produce the jar locally:

```bash
git checkout di-petshop-compat-1.20.1
./gradlew build
# Output: build/libs/ctov-3.3.1-di-petshop-compat.jar
```

Java 17 is required. The sandbox used to author this PR does not have a JDK installed,
so the jar was not built here — please build locally before merging or distributing.

## Backward compatibility

- The original `src/main/ctov-domestication-innovation-add-on/` datapack is left
  untouched. If a user has it installed alongside this jar, the datapack's NBTs will
  simply override the jar's NBTs (identical content), and the patched `house.json` files
  will pick them up the same way. No conflict.
- DI itself remains optional. Without DI, petshops still spawn entities and fishtank
  décor; only the chest loot table is missing.
- No existing CTOV village JSON behavior is changed beyond the petshop entry swap.

## Testing checklist (please verify before merge)

- [ ] `./gradlew build` produces a jar without errors.
- [ ] Loading the jar alone (no DI) on a fresh world: `/locate structure
  ctov:village_plains`, fly to the village, find a petshop, confirm cages contain
  biome-appropriate entities and the fishtank has fish.
- [ ] Loading the jar + DI 1.7.x: confirm the chest inside the petshop is bound to DI's
  loot table and contains pet-food / collar / pet-bed items.
- [ ] Confirm `/reload` picks up custom `data/ctov/petshop_spawns/<biome>.json`
  overrides from a datapack.
- [ ] Confirm `/give @s ctov:spawn_egg` for any modded entity listed in a profile, then
  confirm that modded entities spawn in cages when their mod is installed.
- [ ] Confirm no log spam on world generation (the `LOGGER.debug` lines for missing
  profiles / unknown entities should only fire when something is actually missing).

## Out of scope for this PR

- Porting to MC 1.21.x. This PR targets MC 1.20.1 / Forge only — DI itself is only on
  1.20.1, so a 1.21 port would have to wait until DI releases a 1.21 build.
- Fabric / Quilt / NeoForge support. CTOV v3.3.1 ships as Forge-only; matching that.
- Adding petshops to the `underground` and `cherry` profiles' actual village pools — the
  profiles exist and are pre-populated, but CTOV v3.3.1's `underground/house.json` has
  no petshop entry, and there is no `cherry` village variant yet. These profiles are
  placeholders for future village-adding mods.
