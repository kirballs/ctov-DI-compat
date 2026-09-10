#!/usr/bin/env bash
# Generates 11 single-petshop template pools under
# rs_di_test_pools/data/ctov/worldgen/template_pool/villages/test/rs_di_petshop/<biome>.json
#
# Each pool has exactly one element: the ORIGINAL RS-DI datapack's petshop NBT
# at domesticationinnovation:villages/<biome>/petshop, using vanilla
# minecraft:legacy_single_pool_element (NOT ctov:petshop_compat).
#
# This means:
#   * The test pools work WITHOUT the CTOV-DI-compat fork installed.
#   * All you need is: RS + original RS-DI datapack + CommandStructures + this datapack.
#   * The petshop's jigsaw blocks (which target repurposed_structures:villages/pets_glass_cage,
#     pets_fish, pets_cat_extra, etc.) WILL resolve when /spawnstructure is called
#     with depth >= 2, so you get a fully-furnished petshop with cage mobs and fishtank.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$REPO_ROOT/rs_di_test_pools/data/ctov/worldgen/template_pool/villages/test/rs_di_petshop"
mkdir -p "$OUT_DIR"

BIOMES=(badlands bamboo birch cherry dark_forest giant_taiga jungle mountains mushroom oak swamp)

for BIOME in "${BIOMES[@]}"; do
    cat > "$OUT_DIR/$BIOME.json" <<EOF
{
  "name": "ctov:villages/test/rs_di_petshop/$BIOME",
  "fallback": "minecraft:empty",
  "elements": [
    {
      "weight": 1,
      "element": {
        "element_type": "minecraft:legacy_single_pool_element",
        "location": "domesticationinnovation:villages/$BIOME/petshop",
        "processors": "minecraft:empty",
        "projection": "rigid"
      }
    }
  ]
}
EOF
done

echo "Generated ${#BIOMES[@]} test pool JSONs in: $OUT_DIR"
ls -1 "$OUT_DIR"
