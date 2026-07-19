# Repurposed Structures — DI Petshop Compat Datapack

This datapack adds Domestication Innovation petshop buildings to all 11 Repurposed Structures village variants, using the `ctov:petshop_compat` pool element from the CTOV-DI-compat fork.

## Status

**Pool entries and spawn profiles: done.** The JSON files are ready.

**Petshop NBTs: TODO — you need to create these.**

Each village needs a petshop NBT file at:
```
data/ctov/structures/villages/<biome>/petshop.nbt
```

### How to create the NBTs

1. In a creative world, load the existing RS petshop NBTs from the original DI datapack:
   - `domesticationinnovation:villages/bamboo/petshop`
   - `domesticationinnovation:villages/cherry/petshop`
   - etc.
2. For each cage jigsaw block (they target pools like `repurposed_structures:villages/pets_glass_cage`):
   - Break the jigsaw block
   - Place a **structure block** in its place
   - Set mode to **Data**
   - Set metadata to `petshop_cage_0` (the name after `petshop_cage_` doesn't matter — the code matches any `petshop_cage*`)
   - For larger cages that should hold multiple pets, place additional structure blocks at different positions inside the same cage, each with `petshop_cage_0` (or `petshop_cage_1`, etc. — purely organizational)
3. For fishtank jigsaw blocks (targeting `repurposed_structures:villages/pets_fish`):
   - Replace with structure block → Data mode → metadata: `petshop_water`
4. Leave chests as-is (they already work with DI's loot system).
5. Save the structure with:
   - Name: `ctov:villages/<biome>/petshop`
   - Include entities: **off** (entities come from markers, not baked into the NBT)
   - The `.nbt` file will appear in your world's `generated` folder
6. Copy each `.nbt` into the matching folder here

### Biome → Spawn Profile Mapping

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

### Adding modded mobs

Edit the spawn profile JSON at `data/ctov/petshop_spawns/<profile>.json` in the CTOV fork. Add entries like:
```json
{ "entity": "some_mod:cool_pet", "weight": 10, "baby": true, "age": -24000 }
```
Unknown entity IDs are safely skipped at spawn time (no crash).