# ChoiceTheorem's Overhauled Village — DI Compat Fork

[![GitHub Badge](https://img.shields.io/badge/fork-DI%20compat%20build-black?logo=github)](https://github.com/kirballs/ctov-DI-compat)
![Environment](https://img.shields.io/badge/environment-server-orangered?style=flat-square)
![MC Version](https://img.shields.io/badge/minecraft-1.20.1-blue?style=flat-square)
![Loaders](https://img.shields.io/badge/loaders-fabric%20%7C%20forge%20%7C%20neoforge%20%7C%20quilt-blueviolet?style=flat-square)

> **This is a fork** of [ChoiceTheorem's Overhauled Village](https://github.com/ChoiceTheorem/ChoiceTheorem-s-overhauled-village)
> focused on robust compatibility with **Domestication Innovation** pet shops. It is not
> affiliated with the upstream project. The upstream Modrinth page is intentionally not
> linked here — please direct bug reports for this fork to
> [this repo's Issues](https://github.com/kirballs/ctov-DI-compat/issues), not to upstream.

## Scope of this fork

The fork adds a CTOV-side compatibility layer that makes DI pet shops spawn animals
correctly across **all 21 CTOV village variants**, including the 16 non-vanilla biome
variants (beach, christmas, dark_forest/halloween, desert_oasis, jungle, jungle_tree,
mesa, mesa_fortified, mountain, mountain_alpine, mushroom, plains_fortified, savanna_na,
swamp, swamp_fortified, taiga_fortified) that DI does not natively inject into.

Highlights:

- New `ctov:petshop_compat` structure pool element type that mirrors DI's marker-driven
  spawning (`petshop_water`, `petshop_chest`, `petshop_cage_0..3`).
- 13 unified spawn-list JSONs at `data/ctov/petshop_spawns/<biome>.json` (one per biome
  family: plains, desert, snowy, savanna, badlands, beach, bamboo, cherry, jungle,
  mountains, mushroom, swamp, fishtank). Each entry has inline `weight`, `baby`, `age`
  fields — single source of truth for petshop spawns, no separate tag files to maintain.
  `age = -60000` produces a baby that lasts ~50 in-game hours / ~3 real-time hours (long
  enough to find and bond with the baby before it grows up).
- 3 config toggles for safe rollout and debugging.
- DI-owned tags (`petstore_cage_0..3`, `petstore_fishtank`) are **not consulted** by CTOV's
  compat layer — they remain for DI's own vanilla-village petshops. Modpacks that want to
  override everything just edit CTOV's JSON files.

Full design docs: [`docs/DI_COMPAT_PETSHOP.md`](docs/DI_COMPAT_PETSHOP.md).

## Description

ChoiceTheorem's Overhauled Village is a structure datapack packaged as a mod for Fabric,
Forge, NeoForge and Quilt that enhances and creates new villages and pillager outpost
variants. This pack adds 20+ village variants and pillager outpost variants that fit
naturally into your Minecraft worlds. The existing villages are rebuilt from the ground up
and each biome type can generate two types of structures. In total, there are 23 village
variants and 14 pillager outpost variants tailored to suit the terrain, theme and biomes.

The mod targets Minecraft 1.21.1.

<details><summary>FAQs</summary>

**1. Is it safe to update CTOV to a newer version?**
Yes. If any serious problems arise because of that, please open an issue.

**2. Is it safe to add CTOV to an already existing world?**
Yes. Just note that the new structures will only spawn in newly generated chunks.

**3. Is this mod for Forge or Fabric/Quilt?**
All of them. This is a multi-loader build via Architectury.

**4. How can I locate the new structures?**
`/locate structure ctov:[structure_from_list]` (1.19+).

**5. Does CTOV modify existing vanilla structures?**
The only structures modified by CTOV are **vanilla villages** in older versions.

**6. What about the loot of these structures?**
The vast majority of structures use vanilla loot tables for better mod compatibility, but
some custom loot tables are used to better integrate structures into the world. You will
still find pillager outpost loot in pillager outposts, profession chests in villages, as
well as bells, workstations, etc., but also new stuff like food, armour, and other goodies.

**7. How can I report bugs/issues/suggestions?**
Please open an issue on [this fork's GitHub repo](https://github.com/kirballs/ctov-DI-compat/issues).
For upstream CTOV issues, use the upstream repo.

**8. Can I include CTOV in my modpack?**
Yes, just make sure to give credit and a link to the project page.

**9. Can I have CTOV for 1.x.x, please?**
This fork targets 1.21.1 only. No backports are planned.

**10. Does this fork break compatibility with the upstream CTOV?**
No. The fork is purely additive — new files (the petshop compat element, spawn-list JSONs,
docs) plus two-line registrations in the existing worldgen registries. The 21
lithostitched modifier JSONs have their `element_type` switched from
`minecraft:single_pool_element` to `ctov:petshop_compat`, but this only takes effect when
DI / rats / simplycats is loaded (load conditions are unchanged). Without those mods, the
fork behaves identically to upstream.
</details>

<details><summary>Images</summary>

Some fantastic footage from the mod. All pictures are taken with complementary shaders or
complementary reimagined shaders.
![](https://i.imgur.com/MBILgX5.png)
![](https://i.imgur.com/CpLSx3M.png)
</details>

## Credits

- **ChoiceTheorem** — original CTOV mod, datapack design, village templates.
- **Vichy0623** — codesigning the upstream builds.
- **Robified** — converting the original datapack into a mod.
- **AlexModGuy** — Domestication Innovation, whose `PetshopStructurePoolElement` design
  this fork mirrors for CTOV's petshop pieces.
- **Copilot** — initial scaffolding for the petshop compat layer (config + element class)
  on branch `copilot/update-readme-change-links`; continued and finalized in this fork.

<details><summary>Compatible mods</summary>

+ Domestication Innovation (primary compat target — see `docs/DI_COMPAT_PETSHOP.md`).
+ Rats, SimplyCats (existing CTOV load conditions also accept these as petshop providers).
+ Most world generation mods like Terralith, Oh The Biomes You'll Go, Biomes O'Plenty.
+ Various structure mods like Town & Tower and Repurposed Structures.
+ Any other structure packs by ChoiceTheorem like Immersive Structures.

</details>

You can suggest more mods in the GitHub Issues section.
