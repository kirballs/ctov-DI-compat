#!/usr/bin/env bash
# Packages the rs_di_test_pools/ folder into a standalone datapack zip.
#
# Output: rs_di_test_pools/build/rs_di_test_pools-<version>.zip
#
# This datapack provides 11 single-petshop template pools that let you spawn
# individual petshops from the ORIGINAL Repurposed Structures - Domestication
# Innovation datapack via CommandStructures' /spawnstructure command.
#
# It does NOT require the CTOV-DI-compat fork. Load order:
#   1. Repurposed Structures (mod)
#   2. Domestication Innovation (mod, Forge only)
#   3. Original RS-DI datapack (from TelepathicGrunt/RepurposedStructuresCompatDatapacks)
#   4. CommandStructures (mod)
#   5. This datapack
#
# Then in-game:
#   /spawnstructure ~ ~ ~ ctov:villages/test/rs_di_petshop/<biome> 2 false false false false
#
# The depth 2 resolves the cage/fishtank jigsaws inside the petshop NBT,
# placing actual pet sub-NBTs (wolf, cat, fish, etc.) inside the building.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC_DIR="$REPO_ROOT/rs_di_test_pools"
BUILD_DIR="$SRC_DIR/build"

VERSION="$(awk -F= '/^mod_version=/ {print $2; exit}' "$REPO_ROOT/gradle.properties" | tr -d ' \r')"
if [[ -z "$VERSION" ]]; then
    VERSION="$(date +%Y%m%d%H%M%S)"
fi

OUT_ZIP="$BUILD_DIR/rs_di_test_pools-$VERSION.zip"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

( cd "$SRC_DIR" && find . \
    -type f \
    \! -path "./build/*" \
    \! -path "./README.md" \
    -print0 | sort -z | xargs -0 zip -q -X "$OUT_ZIP" )

echo "Built: $OUT_ZIP"
if command -v unzip >/dev/null 2>&1; then
    unzip -l "$OUT_ZIP" | tail -n +4 | head -n -2 | awk '{print $4}' | sort
    echo "---"
    echo "Total entries: $(unzip -l "$OUT_ZIP" | tail -1 | awk '{print $2}')"
else
    echo "(unzip not installed — skipping contents listing)"
fi
